package com.exanira.event;

import com.exanira.ExaniraMod;
import com.exanira.character.CharacterSheet;
import com.exanira.character.CharacterSheetCapability;
import com.exanira.character.Stat;
import com.exanira.item.ExaniraItems;
import com.exanira.network.EventEndPacket;
import com.exanira.network.EventStartPacket;
import com.exanira.network.PartyVoteStatePacket;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.Util;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class EventQueueManager {

    public static final EventQueueManager INSTANCE = new EventQueueManager();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<String, ActiveEvent> activeEvents      = new ConcurrentHashMap<>();
    private final Map<UUID, String>        playerToEvent     = new ConcurrentHashMap<>();
    private final Map<UUID, String>        pendingInvitations = new ConcurrentHashMap<>();
    private Map<String, EventDefinition>   loadedEvents      = Map.of();
    
    // For persistence
    private ActiveEventSavedData savedEventData;

    private EventQueueManager() {}

    // ───────────────────────── INVITES ──────────────────────────────────────

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

    // ───────────────────────── LOAD ─────────────────────────────────────────

    public void loadEvents(Map<String, EventDefinition> events) {
        this.loadedEvents = Map.copyOf(events);
    }

    public Optional<EventDefinition> getDefinition(String id) {
        return Optional.ofNullable(loadedEvents.get(id));
    }

    // ───────────────────────── START ────────────────────────────────────────

    public boolean startEvent(String eventId, ServerPlayer player) {
        EventDefinition def = loadedEvents.get(eventId);
        if (def == null) return false;

        UUID playerId = player.getUUID();
        if (playerToEvent.containsKey(playerId)) return false; // already in event

        String instanceKey = eventId + "_" + UUID.randomUUID();
        ActiveEvent active = new ActiveEvent(def, playerId);
        activeEvents.put(instanceKey, active);
        active.addParticipant(playerId);
        playerToEvent.put(playerId, instanceKey);

        EventScene scene = active.currentScene();
        if (scene == null) return false;

        PendingEventCapability.get(player).ifPresent(p ->
                p.set(instanceKey, eventId, active.currentSceneId())
        );

        setRadioActive(player, true);
        persist(instanceKey, active);      // <-- save event creation immediately
        broadcastScene(instanceKey, active, scene);
        return true;
    }

    // ───────────────────────── JOIN EXISTING ────────────────────────────────

    public boolean joinEvent(String instanceKey, ServerPlayer player) {
        ActiveEvent active = activeEvents.get(instanceKey);
        if (active == null || active.isResolved()) return false;

        // Can only join at the very first scene
        if (!active.currentSceneId().equals(active.definition().startScene())) return false;

        UUID id = player.getUUID();
        if (playerToEvent.containsKey(id)) return false;

        active.addParticipant(id);
        playerToEvent.put(id, instanceKey);

        // A new participant who hasn't voted yet invalidates the "all connected voted" condition.
        // Cancel all per-player grace-period timers.
        if (active.hasAnyAbandonmentTimer()) {
            active.cancelAllAbandonmentTimers();
            broadcastToParty(active, new TextComponent("A new player has joined the event. All abandonment timers have been cancelled.")
                    .withStyle(ChatFormatting.GREEN));
        }

        EventScene scene = active.currentScene();
        if (scene != null) {
            PendingEventCapability.get(player).ifPresent(p ->
                    p.set(instanceKey, active.definition().id(), active.currentSceneId())
            );
            persist(instanceKey, active);  // <-- save participant addition immediately
            sendScene(player, instanceKey, scene);
            setRadioActive(player, true);
        }
        return true;
    }

    // ───────────────────────── INVITE ACCEPT ────────────────────────────────

    public boolean acceptInvite(ServerPlayer player) {
        UUID id = player.getUUID();
        String instanceKey = pendingInvitations.remove(id);
        if (instanceKey == null) return false;
        return joinEvent(instanceKey, player);
    }

    // ───────────────────────── CHOICE / VOTING ──────────────────────────────

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
                player.sendMessage(
                        new TextComponent("Already voted this scene.").withStyle(ChatFormatting.GRAY), Util.NIL_UUID);
                return;
            }

            // Validate choice before recording vote
            if (choiceIndex >= 0 && choiceIndex < scene.choices().size()) {
                EventChoice candidate = scene.choices().get(choiceIndex);
                CharacterSheet voteSheet = CharacterSheetCapability.get(player)
                        .orElseGet(CharacterSheet::new);
                if (!meetsRequirements(candidate, voteSheet)) {
                    LOGGER.warn("[Exanira] Party player {} attempted a locked choice ({})",
                            player.getName().getString(), choiceIndex);
                    player.sendMessage(
                            new TextComponent("You don't meet the requirements for that choice.")
                                    .withStyle(ChatFormatting.RED), Util.NIL_UUID);
                    return;
                }
            }

            active.recordVote(playerId, choiceIndex);
            persist(instanceKey, active);  // <-- save vote immediately

            // Start individual grace-period timers for disconnected players who haven't voted,
            // now that all connected participants have cast their votes.
            Set<UUID> newTimers = active.startTimersForEligibleDisconnected();
            if (!newTimers.isEmpty()) {
                persist(instanceKey, active);
                broadcastToParty(active, new TextComponent("All online party members have voted. Disconnected member(s) have 5 minutes to return or the event will continue without them.")
                        .withStyle(ChatFormatting.YELLOW));
            }

            // Broadcast vote state to all participants
            List<PartyVoteStatePacket.VoteData> voteData = new ArrayList<>();
            for (UUID participantId : active.participants()) {
                boolean voted = active.hasVoted(participantId);
                int participantChoice = active.votes().getOrDefault(participantId, -1);
                voteData.add(new PartyVoteStatePacket.VoteData(voted, participantChoice));
            }

            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                for (UUID participantId : active.participants()) {
                    ServerPlayer p = server.getPlayerList().getPlayer(participantId);
                    if (p != null) {
                        int localChoiceIndex = active.votes().getOrDefault(participantId, -1);
                        ExaniraMod.CHANNEL.send(
                                PacketDistributor.PLAYER.with(() -> p),
                                new PartyVoteStatePacket(instanceKey, voteData, localChoiceIndex)
                        );
                    }
                }
            }

            if (!active.allVoted()) {
                player.sendMessage(
                        new TextComponent("Vote recorded. Waiting for party…")
                                .withStyle(ChatFormatting.GRAY), Util.NIL_UUID);
                return;
            }

            int finalChoice = active.resolveMajorityChoice();
            broadcastToParty(active,
                    new TextComponent("Party vote resolved: choice " + finalChoice)
                            .withStyle(ChatFormatting.YELLOW));
            applyChoice(instanceKey, active, def, finalChoice);
            return;
        }

        // Single-player path — validate requirements
        if (choiceIndex >= 0 && choiceIndex < scene.choices().size()) {
            EventChoice candidate = scene.choices().get(choiceIndex);
            CharacterSheet sheet = CharacterSheetCapability.get(player)
                    .orElseGet(CharacterSheet::new);
            if (!meetsRequirements(candidate, sheet)) {
                LOGGER.warn("[Exanira] Player {} attempted a locked choice ({}) in event '{}'",
                        player.getName().getString(), choiceIndex, def.id());
                return;
            }
        }

        applyChoice(instanceKey, active, def, choiceIndex);
    }

    // ───────────────────────── APPLY CHOICE ─────────────────────────────────

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
            persist(instanceKey, active);  // <-- save scene change before broadcast
            broadcastScene(instanceKey, active, next);
        } else {
            active.markResolved();
            endEvent(instanceKey, choice.successEvent());
        }
    }

    // ───────────────────────── BROADCAST SCENE ──────────────────────────────

    private void broadcastScene(String instanceKey, ActiveEvent active, EventScene scene) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (UUID participantId : active.participants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(participantId);
            if (player == null) continue;

            // Keep persisted scene current so reconnect lands on the right scene
            PendingEventCapability.get(player).ifPresent(p ->
                    p.set(instanceKey, active.definition().id(), active.currentSceneId())
            );

            sendScene(player, instanceKey, scene);
            setRadioActive(player, true);
        }
    }

    // ───────────────────────── END EVENT ────────────────────────────────────

private void endEvent(String instanceKey, String nextEventId) {
        ActiveEvent active = activeEvents.remove(instanceKey);
        if (active == null) return;

        // Event is finished — remove from persistent storage so it is not restored on restart.
        removeSaved(instanceKey);

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        UUID instigatorId = active.participants().isEmpty() ? null
                : active.participants().iterator().next();

        for (UUID participantId : active.participants()) {
            playerToEvent.remove(participantId);

            if (server != null) {
                ServerPlayer p = server.getPlayerList().getPlayer(participantId);
                if (p != null) {
                    PendingEventCapability.get(p).ifPresent(PendingEventAttachment::clear);
                    setRadioActive(p, false);
                    ExaniraMod.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> p),
                            new EventEndPacket(instanceKey)
                    );
                }
            }
        }

        // Chain: start next event for the instigator
        if (nextEventId != null && server != null && instigatorId != null) {
            ServerPlayer instigator = server.getPlayerList().getPlayer(instigatorId);
            if (instigator != null) startEvent(nextEventId, instigator);
        }
    }

    // ───────────────────────── CLEAR / SHUTDOWN ─────────────────────────────

    public void clear() {
        activeEvents.clear();
        playerToEvent.clear();
        pendingInvitations.clear();
    }

    public void shutdownAll(MinecraftServer server) {
        for (Map.Entry<String, ActiveEvent> entry : activeEvents.entrySet()) {
            String instanceKey = entry.getKey();
            ActiveEvent active = entry.getValue();

            // Persist state before shutdown so the event can be restored on next start.
            persist(instanceKey, active);

            for (UUID id : active.participants()) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    PendingEventCapability.get(player).ifPresent(PendingEventAttachment::clear);
                    setRadioActive(player, false);
                    ExaniraMod.CHANNEL.send(
                            PacketDistributor.PLAYER.with(() -> player),
                            new EventEndPacket(instanceKey)
                    );
                }
            }
        }

        activeEvents.clear();
        playerToEvent.clear();
        pendingInvitations.clear();
    }

    // ───────────────────────── RESYNC ───────────────────────────────────────

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
                markPlayerReconnected(id); // handles disconnectedParticipants, timer, messages
                return;
            }
            playerToEvent.remove(id); // stale entry
        }

        // In-memory maps are cold (server restarted / world reloaded).
        PendingEventAttachment pending = PendingEventCapability.get(player).orElse(null);
        if (pending == null) { setRadioActive(player, false); return; }

        LOGGER.info("[Exanira] resync: player={} hasPending={} event={} scene={} instanceKey={}",
                player.getName().getString(), pending.hasPendingEvent(),
                pending.getEventId(), pending.getSceneId(), pending.getInstanceKey());

        if (!pending.hasPendingEvent()) {
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
            scene   = def.scenes().get(sceneId);
        }
        if (scene == null) {
            LOGGER.error("[Exanira] startScene missing from event '{}' — clearing", def.id());
            pending.clear();
            setRadioActive(player, false);
            return;
        }

        // Reconstruct using the stored instanceKey so party members share the same instance
        String restoredKey = pending.getInstanceKey() != null
                ? pending.getInstanceKey()
                : def.id() + "_" + UUID.randomUUID();

        ActiveEvent active = activeEvents.get(restoredKey);
        if (active == null) {
            // Event is no longer running — clear the stale capability so the player
            // is not stuck in a ghost event on every future login.
            LOGGER.info("[Exanira] Event '{}' no longer active for reconnecting player {} — clearing stale capability",
                    restoredKey, player.getName().getString());
            pending.clear();
            setRadioActive(player, false);
            return;
        }
        if (!active.participants().contains(id)) {
            // Player was removed from this event (abandoned while offline) — discard
            // the stale capability so they cannot get stuck or rejoin.
            LOGGER.info("[Exanira] Player {} is not a participant in event '{}' (abandoned while offline) — clearing capability",
                    player.getName().getString(), restoredKey);
            pending.clear();
            setRadioActive(player, false);
            return;
        }

        playerToEvent.put(id, restoredKey);
        sendScene(player, restoredKey, scene);
        setRadioActive(player, true);
        LOGGER.info("[Exanira] Restored event '{}' (scene: '{}') for reconnecting player {}",
                def.id(), sceneId, player.getName().getString());
    }

    // ───────────────────────── HELPERS ──────────────────────────────────────

    private void sendScene(ServerPlayer player, String instanceKey, EventScene scene) {
        CharacterSheet sheet = CharacterSheetCapability.get(player)
                .orElseGet(CharacterSheet::new);

        List<EventStartPacket.ChoiceData> choices = buildChoiceData(scene.choices(), sheet);

        ExaniraMod.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new EventStartPacket(instanceKey, scene.dialogue(), choices)
        );

        // Resend vote state so reconnecting players see the current tally
        ActiveEvent active = activeEvents.get(instanceKey);
        if (active == null || active.participants().size() <= 1) return;

        List<PartyVoteStatePacket.VoteData> voteData = new ArrayList<>();
        for (UUID participantId : active.participants()) {
            boolean voted     = active.hasVoted(participantId);
            int choiceIndex   = active.votes().getOrDefault(participantId, -1);
            voteData.add(new PartyVoteStatePacket.VoteData(voted, choiceIndex));
        }

        int localChoiceIndex = active.votes().getOrDefault(player.getUUID(), -1);
        ExaniraMod.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PartyVoteStatePacket(instanceKey, voteData, localChoiceIndex)
        );
    }

    private List<EventStartPacket.ChoiceData> buildChoiceData(
            List<EventChoice> choices, CharacterSheet sheet) {
        return choices.stream()
                .map(c -> new EventStartPacket.ChoiceData(
                        c.text(),
                        meetsRequirements(c, sheet),          // available
                        c.lockedText() == null ? "Locked" : c.lockedText(),
                        buildRequirementText(c)
                ))
                .toList();
    }

    /** Writes the "active" boolean flag directly into the ItemStack's NBT tag. */
    private void setRadioActive(ServerPlayer player, boolean active) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ExaniraItems.RADIO.get())) {
                stack.getOrCreateTag().putBoolean("active", active);
                break;
            }
        }
    }

    private void broadcastToParty(ActiveEvent active, Component msg) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (UUID id : active.participants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) player.sendMessage(msg, Util.NIL_UUID);
        }
    }

    private String buildRequirementText(EventChoice choice) {
        if (choice.requires() == null || choice.requires().isEmpty()) return "";
        return choice.requires().entrySet().stream()
                .map(e -> "[" + e.getKey().substring(0, 3).toUpperCase() + " " + e.getValue() + "+]")
                .collect(Collectors.joining(" "));
    }

    private boolean meetsRequirements(EventChoice choice, CharacterSheet sheet) {
        if (choice.requires() == null || choice.requires().isEmpty()) return true;
        for (var e : choice.requires().entrySet()) {
            try {
                Stat stat = Stat.valueOf(e.getKey().toUpperCase());
                if (sheet.getStat(stat) < e.getValue()) return false;
            } catch (IllegalArgumentException ignored) {}
        }
        return true;
    }

    // ───────────────────────── QUERY API ────────────────────────────────────

    public Optional<String> getPlayerEventKey(UUID playerId) {
        return Optional.ofNullable(playerToEvent.get(playerId));
    }

    public boolean isPlayerInEvent(UUID playerId) {
        return playerToEvent.containsKey(playerId);
    }

public Optional<ActiveEvent> getActiveEvent(String instanceKey) {
        return Optional.ofNullable(activeEvents.get(instanceKey));
    }
    
// ───────────────────────── PERSISTENCE HELPERS ──────────────────────────

    /** Snapshot the given event into SavedData immediately (marks dirty). */
    private void persist(String instanceKey, ActiveEvent active) {
        if (savedEventData == null) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            savedEventData = ActiveEventSavedData.getOrCreate(server);
        }
        savedEventData.saveActiveEvent(instanceKey, new ActiveEventSavedData.ActiveEventState(
                active.definition().id(),
                instanceKey,
                active.currentSceneId(),
                active.participants(),
                active.disconnectedParticipants(),
                active.votes(),
                active.playerAbandonmentTimers(),
                active.isResolved()
        ));
    }

    /** Remove a finished/cancelled event from SavedData so it is not restored. */
    private void removeSaved(String instanceKey) {
        if (savedEventData == null) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            savedEventData = ActiveEventSavedData.getOrCreate(server);
        }
        savedEventData.removeActiveEvent(instanceKey);
    }

    /**
     * Rebuild in-memory event state from SavedData after a server restart.
     * Must be called after resource loading so {@link #loadedEvents} is populated.
     */
    public void restoreEventsFromSave() {
        if (savedEventData == null) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                savedEventData = ActiveEventSavedData.getOrCreate(server);
            } else {
                return;
            }
        }

        int restored = 0;
        for (String instanceKey : new ArrayList<>(savedEventData.getAllInstanceKeys())) {
            ActiveEventSavedData.ActiveEventState state = savedEventData.get(instanceKey).orElse(null);
            if (state == null) continue;

            // Discard already-resolved entries that were somehow not cleaned up.
            if (state.resolved()) {
                savedEventData.removeActiveEvent(instanceKey);
                continue;
            }

            EventDefinition def = loadedEvents.get(state.eventId());
            if (def == null) {
                LOGGER.warn("[Exanira] Saved event '{}' references unknown definition '{}' \u2014 removing",
                        instanceKey, state.eventId());
                savedEventData.removeActiveEvent(instanceKey);
                continue;
            }

            // Fall back to start scene if the saved scene no longer exists.
            String sceneId = state.currentSceneId();
            if (def.scenes().get(sceneId) == null) {
                LOGGER.warn("[Exanira] Scene '{}' not found in '{}' \u2014 resetting to start scene",
                        sceneId, def.id());
                sceneId = def.startScene();
            }

            ActiveEvent active = new ActiveEvent(
                    def, sceneId,
                    state.participants(), state.votes(),
                    state.disconnectedParticipants(),
                    state.playerAbandonmentTimers(), false);

            activeEvents.put(instanceKey, active);
            for (UUID participantId : state.participants()) {
                playerToEvent.put(participantId, instanceKey);
            }
            restored++;
            LOGGER.info("[Exanira] Restored event '{}' (scene: '{}', {} participant(s))",
                    def.id(), sceneId, state.participants().size());
        }
        LOGGER.info("[Exanira] Restored {} active event(s) from SavedData", restored);
    }

public boolean forceStopEvent(ServerPlayer player) {
        UUID playerId = player.getUUID();
        String instanceKey = playerToEvent.get(playerId);
        if (instanceKey == null) return false;

        ActiveEvent active = activeEvents.remove(instanceKey);
        if (active != null) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            for (UUID participantId : active.participants()) {
                playerToEvent.remove(participantId);
                pendingInvitations.remove(participantId);

                if (server != null) {
                    ServerPlayer participant = server.getPlayerList().getPlayer(participantId);
                    if (participant != null) {
                        PendingEventCapability.get(participant)
                                .ifPresent(PendingEventAttachment::clear);
                        setRadioActive(participant, false);
                        ExaniraMod.CHANNEL.send(
                                PacketDistributor.PLAYER.with(() -> participant),
                                new EventEndPacket(instanceKey)
                        );
                    }
                }
            }
        }
        return true;
    }
    
    /**
     * Mark a player as disconnected from any active events they're participating in
     */
public void markPlayerDisconnected(UUID playerId) {
        String instanceKey = playerToEvent.get(playerId);
        if (instanceKey != null) {
            ActiveEvent active = activeEvents.get(instanceKey);
            if (active != null && !active.isResolved()) {
                active.markPlayerDisconnected(playerId);

                // Start the grace-period for this player (and any others without a timer yet)
                // if all remaining connected participants have already voted.
                Set<UUID> newTimers = active.startTimersForEligibleDisconnected();
                if (!newTimers.isEmpty()) {
                    broadcastToParty(active, new TextComponent("All online party members have voted. Disconnected member(s) have 5 minutes to return or the event will continue without them.")
                            .withStyle(ChatFormatting.YELLOW));
                    LOGGER.info("[Exanira] Started abandonment timers for {} player(s) in event {}",
                               newTimers.size(), instanceKey);
                }

                persist(instanceKey, active);  // <-- save disconnect + optional timer start
            }
        }
    }
    
    /**
     * Check all events for expired abandonments
     */
    public void checkAllEventsForExpiredAbandonments() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        for (String instanceKey : new ArrayList<>(activeEvents.keySet())) {
            ActiveEvent active = activeEvents.get(instanceKey);
            if (active == null || active.isResolved()) continue;
            if (!active.hasAnyAbandonmentTimer()) continue;

            Set<UUID> abandoned = active.checkAndResolveExpiredAbandonments();
            persist(instanceKey, active);

            // Fully remove abandoned players from both server and client.
            for (UUID abandonedId : abandoned) {
                playerToEvent.remove(abandonedId);   // prevent resync re-adding them
                if (server != null) {
                    ServerPlayer p = server.getPlayerList().getPlayer(abandonedId);
                    if (p != null) {
                        // Player is still online — close their screen, clear state, deactivate radio.
                        PendingEventCapability.get(p).ifPresent(PendingEventAttachment::clear);
                        setRadioActive(p, false);
                        ExaniraMod.CHANNEL.send(
                                PacketDistributor.PLAYER.with(() -> p),
                                new EventEndPacket(instanceKey)
                        );
                        LOGGER.info("[Exanira] Sent EventEndPacket to abandoned online player {} in event {}",
                                abandonedId, instanceKey);
                    }
                    // If the player is offline, their PendingEventCapability is cleared when they
                    // reconnect via the guard in resyncPlayerIfMidEvent (participants check).
                }
            }

            // If fallback votes now satisfy all remaining participants, advance the event.
            if (!active.isResolved() && active.allVoted()) {
                int choice = active.resolveMajorityChoice();
                broadcastToParty(active, new TextComponent("Disconnected player(s) timed out. Proceeding with party vote.")
                        .withStyle(ChatFormatting.YELLOW));
                applyChoice(instanceKey, active, active.definition(), choice);
            }
        }
    }
    
    /**
     * Mark a player as reconnected to any active events they're participating in
     */
    public void markPlayerReconnected(UUID playerId) {
        String instanceKey = playerToEvent.get(playerId);
        if (instanceKey == null) return;
        ActiveEvent active = activeEvents.get(instanceKey);
        if (active == null || active.isResolved()) return;

        // Removes from disconnectedParticipants AND cancels their individual timer.
        boolean wasDisconnected = active.markPlayerReconnected(playerId);
        if (!wasDisconnected) return;

        LOGGER.info("[Exanira] Player {} reconnected to event {}", playerId, instanceKey);

        // Only announce when every disconnected player is back — no partial messages.
        if (active.disconnectedParticipants().isEmpty()) {
            broadcastToParty(active, new TextComponent("All party members have returned! The event continues.")
                    .withStyle(ChatFormatting.GREEN));
        }

        persist(instanceKey, active);
    }
}
