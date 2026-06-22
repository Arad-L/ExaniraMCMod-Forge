package com.exanira.event;

import com.exanira.character.CharacterAttachment;
import com.exanira.character.CharacterSheet;
import com.exanira.character.Stat;
import com.exanira.item.ExaniraItems;
import com.exanira.network.EventEndPacket;
import com.exanira.network.EventStartPacket;
import com.exanira.network.PartyVoteStatePacket;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import java.util.stream.Collectors;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EventQueueManager {

    public static final EventQueueManager INSTANCE = new EventQueueManager();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<String, ActiveEvent> activeEvents = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerToEvent = new ConcurrentHashMap<>();
    private Map<String, EventDefinition> loadedEvents = Map.of();
    private final Map<UUID, String> pendingInvitations = new ConcurrentHashMap<>();

    private EventQueueManager() {}

    // ───────────────────────── INVITES ─────────────────────────

    public String getPendingInvitation(UUID playerId) {
        return pendingInvitations.get(playerId);
    }

    public boolean hasPendingInvitationForEvent(UUID playerId, String instanceKey) {
        return instanceKey != null && instanceKey.equals(pendingInvitations.get(playerId));
    }

    public void setPendingInvitation(UUID playerId, String instanceKey) {
        pendingInvitations.put(playerId, instanceKey);
    }

    public String removePendingInvitation(UUID playerId) {
        return pendingInvitations.remove(playerId);
    }

    public boolean hasPendingInvitation(UUID playerId) {
        return pendingInvitations.containsKey(playerId);
    }

    // ───────────────────────── LOAD ─────────────────────────

    public void loadEvents(Map<String, EventDefinition> events) {
        this.loadedEvents = Map.copyOf(events);
    }

    public Optional<EventDefinition> getDefinition(String id) {
        return Optional.ofNullable(loadedEvents.get(id));
    }

    // ───────────────────────── START (CREATE INSTANCE) ─────────────────────────

    public boolean startEvent(String eventId, ServerPlayer player) {

        EventDefinition def = loadedEvents.get(eventId);
        if (def == null) return false;

        UUID playerId = player.getUUID();

        if (playerToEvent.containsKey(playerId)) {
            return false; // already in event
        }

        // ✅ FIX: UNIQUE INSTANCE PER EVENT START
        String instanceKey = eventId + "_" + UUID.randomUUID();

        ActiveEvent active = new ActiveEvent(def, playerId);
        activeEvents.put(instanceKey, active);

        active.addParticipant(playerId);
        playerToEvent.put(playerId, instanceKey);

        EventScene scene = active.currentScene();
        if (scene == null) return false;

        // Persist: store instanceKey + eventId separately so reconnect can reconstruct
        player.getData(CharacterAttachment.PENDING_EVENT.get())
                .set(instanceKey, eventId, active.currentSceneId());

        setRadioActive(player, true);
        broadcastScene(instanceKey, active, scene);

        return true;
    }

    // ───────────────────────── JOIN EXISTING INSTANCE ─────────────────────────

    public boolean joinEvent(String instanceKey, ServerPlayer player) {

        ActiveEvent active = activeEvents.get(instanceKey);
        if (active == null || active.isResolved()) return false;

        if (!active.currentSceneId().equals(active.definition().startScene())) {
            return false;
        }

        UUID id = player.getUUID();

        if (playerToEvent.containsKey(id)) return false;

        active.addParticipant(id);
        playerToEvent.put(id, instanceKey);

        EventScene scene = active.currentScene();
        if (scene != null) {
            // Persist for this joiner so they can reconnect mid-event
            player.getData(CharacterAttachment.PENDING_EVENT.get())
                    .set(instanceKey, active.definition().id(), active.currentSceneId());
            sendScene(player, instanceKey, scene);
            setRadioActive(player, true);
        }

        return true;
    }

    // ───────────────────────── INVITE ACCEPT ─────────────────────────

    public boolean acceptInvite(ServerPlayer player) {

        UUID id = player.getUUID();

        String instanceKey = pendingInvitations.remove(id);
        if (instanceKey == null) return false;

        return joinEvent(instanceKey, player);
    }

    // ───────────────────────── CHOICE / VOTING ─────────────────────────

    public void resolveChoice(UUID playerId, String instanceKey, int choiceIndex, ServerPlayer player) {

        ActiveEvent active = activeEvents.get(instanceKey);
        if (active == null || active.isResolved()) return;
        if (!active.participants().contains(playerId)) return;

        EventDefinition def = active.definition();
        EventScene scene = active.currentScene();
        if (scene == null) return;

        boolean isParty = active.participants().size() > 1;

        if (isParty) {

            if (active.hasVoted(playerId)) {
                player.sendSystemMessage(Component.literal("Already voted this scene.")
                        .withStyle(ChatFormatting.GRAY));
                return;
            }

            // ── Stat validation before recording vote (Bug F fix) ─────────
            if (choiceIndex >= 0 && choiceIndex < scene.choices().size()) {
                EventChoice candidate = scene.choices().get(choiceIndex);
                CharacterSheet voteSheet = player.getData(CharacterAttachment.CHARACTER_SHEET.get());
                if (!meetsRequirements(candidate, voteSheet)) {
                    LOGGER.warn("[Exanira] Party player {} attempted a locked choice ({})",
                            player.getName().getString(), choiceIndex);
                    player.sendSystemMessage(Component.literal("You don't meet the requirements for that choice.")
                            .withStyle(ChatFormatting.RED));
                    return;
                }
            }

            active.recordVote(playerId, choiceIndex);

            // Broadcast vote state to all participants
            List<PartyVoteStatePacket.VoteData> voteData = new ArrayList<>();
            for (UUID participant : active.participants()) {
                Boolean voted = active.hasVoted(participant);
                Integer choiceIndexForParticipant = active.votes().get(participant);
                voteData.add(new PartyVoteStatePacket.VoteData(voted, choiceIndexForParticipant != null ? choiceIndexForParticipant : -1));
            }

            MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                for (UUID participantId : active.participants()) {
                    ServerPlayer p = server.getPlayerList().getPlayer(participantId);
                    if (p != null) {
                        int localChoiceIndex = active.votes().getOrDefault(participantId, -1);
                        PartyVoteStatePacket packet = new PartyVoteStatePacket(instanceKey, voteData, localChoiceIndex);
                        PacketDistributor.sendToPlayer(p, packet);
                    }
                }
            }

            if (!active.allVoted()) {
                player.sendSystemMessage(Component.literal("Vote recorded. Waiting for party...")
                        .withStyle(ChatFormatting.GRAY));
                return;
            }

            int finalChoice = active.resolveMajorityChoice();

            broadcastToParty(active,
                    Component.literal("Party vote resolved: choice " + finalChoice)
                            .withStyle(ChatFormatting.YELLOW));

            applyChoice(instanceKey, active, def, finalChoice);
            return;
        }

        // ── Single-player: stat validation (Bug F fix) ─────────────────
        if (choiceIndex >= 0 && choiceIndex < scene.choices().size()) {
            EventChoice candidate = scene.choices().get(choiceIndex);
            CharacterSheet sheet = player.getData(CharacterAttachment.CHARACTER_SHEET.get());
            if (!meetsRequirements(candidate, sheet)) {
                LOGGER.warn("[Exanira] Player {} attempted a locked choice ({}) in event '{}'",
                        player.getName().getString(), choiceIndex, def.id());
                return;
            }
        }

        applyChoice(instanceKey, active, def, choiceIndex);
    }

    // ───────────────────────── APPLY CHOICE ─────────────────────────

    private void applyChoice(String instanceKey, ActiveEvent active, EventDefinition def, int choiceIndex) {

        EventScene scene = active.currentScene();

        if (choiceIndex == -1) {
            if (!scene.choices().isEmpty()) return;
            active.markResolved();
            endEvent(instanceKey, scene.successEvent());
            return;
        }

        if (choiceIndex < 0 || choiceIndex >= scene.choices().size()) return;

        EventChoice choice = scene.choices().get(choiceIndex);

        if (choice.nextScene() != null) {

            EventScene next = def.scenes().get(choice.nextScene());
            if (next == null) {
                active.markResolved();
                endEvent(instanceKey, null);
                return;
            }

            active.setCurrentScene(choice.nextScene());
            broadcastScene(instanceKey, active, next);

        } else {
            active.markResolved();
            endEvent(instanceKey, choice.successEvent());
        }
    }

    // ───────────────────────── BROADCAST ─────────────────────────

    private void broadcastScene(String instanceKey, ActiveEvent active, EventScene scene) {

        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (UUID participantId : active.participants()) {

            ServerPlayer player = server.getPlayerList().getPlayer(participantId);
            if (player == null) continue;

            // Bug D fix: update persisted scene on every advance so reconnect lands on the right scene
            player.getData(CharacterAttachment.PENDING_EVENT.get())
                    .set(instanceKey, active.definition().id(), active.currentSceneId());

            sendScene(player, instanceKey, scene);
            setRadioActive(player, true);
        }
    }

    // ───────────────────────── END EVENT ─────────────────────────

    // Bug A fix: removed the `ServerPlayer player` parameter that was being passed as null.
    // Now always looks up all participants from the live server so cleanup is guaranteed.
    private void endEvent(String instanceKey, String nextEventId) {

        ActiveEvent active = activeEvents.remove(instanceKey);
        if (active == null) return;

        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();

        UUID instigatorId = active.participants().isEmpty() ? null : active.participants().iterator().next();

        for (UUID participantId : active.participants()) {
            playerToEvent.remove(participantId);

            if (server != null) {
                ServerPlayer p = server.getPlayerList().getPlayer(participantId);
                if (p != null) {
                    p.getData(CharacterAttachment.PENDING_EVENT.get()).clear();
                    setRadioActive(p, false);
                    PacketDistributor.sendToPlayer(p, new EventEndPacket(instanceKey));
                }
            }
        }

        // successEvent chaining: start the next event for the instigator (solo or party leader)
        if (nextEventId != null && server != null && instigatorId != null) {
            ServerPlayer instigator = server.getPlayerList().getPlayer(instigatorId);
            if (instigator != null) {
                startEvent(nextEventId, instigator);
            }
        }
    }

    public void clear() {
        activeEvents.clear();
        playerToEvent.clear();
        pendingInvitations.clear();
    }

    public void shutdownAll(MinecraftServer server) {

        for (Map.Entry<String, ActiveEvent> entry : activeEvents.entrySet()) {

            String instanceKey = entry.getKey();
            ActiveEvent active = entry.getValue();

            for (UUID id : active.participants()) {

                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {

                    player.getData(CharacterAttachment.PENDING_EVENT.get()).clear();

                    setRadioActive(player, false);

                    PacketDistributor.sendToPlayer(
                            player,
                            new EventEndPacket(instanceKey)
                    );
                }
            }
        }

        activeEvents.clear();
        playerToEvent.clear();
        pendingInvitations.clear();
    }

    // ───────────────────────── RESYNC ─────────────────────────

    public void resyncPlayerIfMidEvent(ServerPlayer player) {

        UUID id = player.getUUID();
        String instanceKey = playerToEvent.get(id);

        if (instanceKey != null) {
            // In-memory maps are warm (quick reconnect without server restart)
            ActiveEvent active = activeEvents.get(instanceKey);
            if (active != null && !active.isResolved()) {
                EventScene scene = active.currentScene();
                if (scene != null) {
                    sendScene(player, instanceKey, scene);
                    setRadioActive(player, true);
                }
                return;
            }
            // Stale in-memory entry — clean up
            playerToEvent.remove(id);
        }

        // In-memory maps are empty (server restarted / world reloaded).
        // Bug B+C fix: read the three-field attachment (instanceKey, eventId, sceneId)
        PendingEventAttachment pending = player.getData(CharacterAttachment.PENDING_EVENT.get());
        LOGGER.info("[Exanira] resync: player={} hasPending={} event={} scene={} instanceKey={}",
                player.getName().getString(), pending.hasPendingEvent(),
                pending.getEventId(), pending.getSceneId(), pending.getInstanceKey());

        if (!pending.hasPendingEvent()) {
            // No active event — clear any stale radio glow
            setRadioActive(player, false);
            return;
        }

        EventDefinition def = loadedEvents.get(pending.getEventId());
        if (def == null) {
            LOGGER.warn("[Exanira] Attachment references unknown event '{}' for player {} — clearing",
                    pending.getEventId(), player.getName().getString());
            pending.clear();
            setRadioActive(player, false);
            return;
        }

        String sceneId = pending.getSceneId() != null ? pending.getSceneId() : def.startScene();
        EventScene scene = def.scenes().get(sceneId);
        if (scene == null) {
            LOGGER.warn("[Exanira] Saved scene '{}' not found — restarting from beginning", sceneId);
            sceneId = def.startScene();
            scene = def.scenes().get(sceneId);
        }
        if (scene == null) {
            LOGGER.error("[Exanira] startScene missing from event '{}' — clearing", def.id());
            pending.clear();
            setRadioActive(player, false);
            return;
        }

        // Reconstruct using the stored instanceKey so party members share the same instance
        String restoredKey = pending.getInstanceKey() != null ? pending.getInstanceKey()
                : def.id() + "_" + UUID.randomUUID();

        // If another party member already restored this instance, just join it
        ActiveEvent active = activeEvents.get(restoredKey);
        if (active == null) {
            active = new ActiveEvent(def, id);
            active.setCurrentScene(sceneId);
            activeEvents.put(restoredKey, active);
        } else {
            active.addParticipant(id);
        }

        playerToEvent.put(id, restoredKey);
        sendScene(player, restoredKey, scene);
        setRadioActive(player, true);
        LOGGER.info("[Exanira] Reconstructed event '{}' (scene: '{}') for reconnecting player {}",
                def.id(), sceneId, player.getName().getString());
    }

    // ───────────────────────── HELPERS ─────────────────────────

    private void sendScene(ServerPlayer player, String instanceKey, EventScene scene) {

        CharacterSheet sheet =
                player.getData(CharacterAttachment.CHARACTER_SHEET.get());

        List<EventStartPacket.ChoiceData> choices =
                buildChoiceData(scene.choices(), sheet);

        PacketDistributor.sendToPlayer(
                player,
                new EventStartPacket(instanceKey, scene.dialogue(), choices)
        );

        // Also send current vote state so reconnect/reopen restores locked choice highlight.
        ActiveEvent active = activeEvents.get(instanceKey);
        if (active == null || active.participants().size() <= 1) return;

        List<PartyVoteStatePacket.VoteData> voteData = new ArrayList<>();
        for (UUID participantId : active.participants()) {
            boolean voted = active.hasVoted(participantId);
            int choiceIndex = active.votes().getOrDefault(participantId, -1);
            voteData.add(new PartyVoteStatePacket.VoteData(voted, choiceIndex));
        }

        int localChoiceIndex = active.votes().getOrDefault(player.getUUID(), -1);
        PacketDistributor.sendToPlayer(
            player,
            new PartyVoteStatePacket(instanceKey, voteData, localChoiceIndex)
        );
    }

    private List<EventStartPacket.ChoiceData> buildChoiceData(
            List<EventChoice> choices,
            CharacterSheet sheet
    ) {
        return choices.stream()
                .map(c -> new EventStartPacket.ChoiceData(
                        c.text(),
                        meetsRequirements(c, sheet),
                        c.lockedText() == null ? "Locked" : c.lockedText(),
                        buildRequirementText(c)
                ))
                .toList();
    }

    private void setRadioActive(ServerPlayer player, boolean active) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ExaniraItems.RADIO.get())) {
                CompoundTag tag = stack.getOrDefault(
                        DataComponents.CUSTOM_DATA,
                        CustomData.EMPTY
                ).copyTag();

                tag.putBoolean("active", active);
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                break;
            }
        }
    }

    private void broadcastToParty(ActiveEvent active, Component msg) {
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (UUID id : active.participants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.sendSystemMessage(msg);
            }
        }
    }

    private String buildRequirementText(EventChoice choice) {
        if (choice.requires() == null) return "";
        return choice.requires().entrySet().stream()
                .map(e -> "[" + e.getKey().substring(0, 3).toUpperCase()
                        + " " + e.getValue() + "+]")
                .collect(Collectors.joining(" "));
    }

    private boolean meetsRequirements(EventChoice choice, CharacterSheet sheet) {
        if (choice.requires() == null) return true;

        for (var e : choice.requires().entrySet()) {
            try {
                Stat stat = Stat.valueOf(e.getKey().toUpperCase());
                if (sheet.getStat(stat) < e.getValue()) return false;
            } catch (Exception ignored) {}
        }
        return true;
    }

    public Optional<String> getPlayerEventKey(UUID playerId) {
        return Optional.ofNullable(playerToEvent.get(playerId));
    }

    public boolean isPlayerInEvent(UUID playerId) {
        return playerToEvent.containsKey(playerId);
    }

    public Optional<ActiveEvent> getActiveEvent(String instanceKey) {
        return Optional.ofNullable(activeEvents.get(instanceKey));
    }

    public boolean forceStopEvent(ServerPlayer player) {

        UUID playerId = player.getUUID();
        String instanceKey = playerToEvent.get(playerId);

        if (instanceKey == null) {
            return false;
        }

        ActiveEvent active = activeEvents.remove(instanceKey);

        if (active != null) {

            MinecraftServer server =
                    net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();

            for (UUID participantId : active.participants()) {

                playerToEvent.remove(participantId);
                pendingInvitations.remove(participantId);

                if (server != null) {

                    ServerPlayer participant =
                            server.getPlayerList().getPlayer(participantId);

                    if (participant != null) {

                        participant.getData(
                                CharacterAttachment.PENDING_EVENT.get()
                        ).clear();

                        setRadioActive(participant, false);

                        PacketDistributor.sendToPlayer(
                                participant,
                                new EventEndPacket(instanceKey)
                        );
                    }
                }
            }
        }

        return true;
    }
}