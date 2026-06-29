package com.exanira.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;

/**
 * Persists active event state to the world's SavedData.
 * This survives server restarts so events can resume properly after shutdown.
 *
 * Stored in: world/data/exanira_active_events_state.dat
 */
public class ActiveEventSavedData extends SavedData {
    
    private static final String DATA_NAME = "exanira_active_events_state";
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /** Full snapshot of an active event state that can be restored after server restart. */
    public static class ActiveEventState {
        private final String eventId;
        private final String instanceKey;
        private final String currentSceneId;
        private final Set<UUID> participants;
        private final Set<UUID> disconnectedParticipants;
        private final Map<UUID, Integer> votes;
        private final Map<UUID, Long> playerAbandonmentTimers;
        private final boolean resolved;
        
        public ActiveEventState(String eventId, String instanceKey, String currentSceneId,
                               Set<UUID> participants, Set<UUID> disconnectedParticipants,
                               Map<UUID, Integer> votes, Map<UUID, Long> playerAbandonmentTimers, boolean resolved) {
            this.eventId = eventId;
            this.instanceKey = instanceKey;
            this.currentSceneId = currentSceneId;
            this.participants = new HashSet<>(participants);
            this.disconnectedParticipants = new HashSet<>(disconnectedParticipants);
            this.votes = new HashMap<>(votes);
            this.playerAbandonmentTimers = new HashMap<>(playerAbandonmentTimers);
            this.resolved = resolved;
        }
        
        // Getters
        public String eventId() { return eventId; }
        public String instanceKey() { return instanceKey; }
        public String currentSceneId() { return currentSceneId; }
        public Set<UUID> participants() { return Collections.unmodifiableSet(participants); }
        public Set<UUID> disconnectedParticipants() { return Collections.unmodifiableSet(disconnectedParticipants); }
        public Map<UUID, Integer> votes() { return Collections.unmodifiableMap(votes); }
        public Map<UUID, Long> playerAbandonmentTimers() { return Collections.unmodifiableMap(playerAbandonmentTimers); }
        public boolean resolved() { return resolved; }
    }
    
    private final Map<String, ActiveEventState> activeEvents = new HashMap<>();
    
    // ── Factory ──────────────────────────────────────────────────────────────
    
    public static ActiveEventSavedData getOrCreate(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ActiveEventSavedData::load,
                ActiveEventSavedData::new,
                DATA_NAME
        );
    }
    
    // ── Serialization ─────────────────────────────────────────────────────────
    
    @Override
    public CompoundTag save(CompoundTag tag) {
        LOGGER.info("[Exanira] SavedData serializing {} active event(s) to disk", activeEvents.size());
        
        ListTag list = new ListTag();
        
        activeEvents.forEach((instanceKey, state) -> {
            LOGGER.info("[Exanira]   writing: instanceKey={} event={} scene={}",
                    instanceKey, state.eventId(), state.currentSceneId());
            
            CompoundTag entry = new CompoundTag();
            entry.putString("instanceKey", instanceKey);
            entry.putString("eventId", state.eventId());
            entry.putString("currentSceneId", state.currentSceneId());
            CompoundTag timerMapTag = new CompoundTag();
            for (Map.Entry<UUID, Long> timerEntry : state.playerAbandonmentTimers().entrySet()) {
                timerMapTag.putLong(timerEntry.getKey().toString(), timerEntry.getValue());
            }
            entry.put("playerAbandonmentTimers", timerMapTag);
            entry.putBoolean("resolved", state.resolved());
            
            // Serialize participants
            ListTag participantsList = new ListTag();
            for (UUID participant : state.participants()) {
                CompoundTag uuidTag = new CompoundTag();
                uuidTag.putUUID("uuid", participant);
                participantsList.add(uuidTag);
            }
            entry.put("participants", participantsList);
            
            // Serialize disconnected participants
            ListTag disconnectedList = new ListTag();
            for (UUID disconnected : state.disconnectedParticipants()) {
                CompoundTag uuidTag = new CompoundTag();
                uuidTag.putUUID("uuid", disconnected);
                disconnectedList.add(uuidTag);
            }
            entry.put("disconnectedParticipants", disconnectedList);
            
            // Serialize votes
            ListTag votesList = new ListTag();
            for (Map.Entry<UUID, Integer> vote : state.votes().entrySet()) {
                CompoundTag voteTag = new CompoundTag();
                voteTag.putUUID("player", vote.getKey());
                voteTag.putInt("choice", vote.getValue());
                votesList.add(voteTag);
            }
            entry.put("votes", votesList);
            
            list.add(entry);
        });
        
        tag.put("events", list);
        return tag;
    }
    
    public static ActiveEventSavedData load(CompoundTag tag) {
        ActiveEventSavedData data = new ActiveEventSavedData();
        
        ListTag list = tag.getList("events", Tag.TAG_COMPOUND);
        
        LOGGER.info("[Exanira] SavedData loading {} active event(s) from disk", list.size());
        
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            
            String instanceKey = entry.getString("instanceKey");
            String eventId = entry.getString("eventId");
            String sceneId = entry.getString("currentSceneId");
            Map<UUID, Long> playerAbandonmentTimers = new HashMap<>();
            if (entry.contains("playerAbandonmentTimers", Tag.TAG_COMPOUND)) {
                CompoundTag timerMapTag = entry.getCompound("playerAbandonmentTimers");
                for (String uuidStr : timerMapTag.getAllKeys()) {
                    try { playerAbandonmentTimers.put(UUID.fromString(uuidStr), timerMapTag.getLong(uuidStr)); }
                    catch (IllegalArgumentException ignored) {}
                }
            }
            boolean resolved = entry.getBoolean("resolved");
            
            // Deserialize participants
            Set<UUID> participants = new HashSet<>();
            ListTag participantsList = entry.getList("participants", Tag.TAG_COMPOUND);
            for (int j = 0; j < participantsList.size(); j++) {
                CompoundTag uuidTag = participantsList.getCompound(j);
                participants.add(uuidTag.getUUID("uuid"));
            }
            
            // Deserialize disconnected participants
            Set<UUID> disconnectedParticipants = new HashSet<>();
            ListTag disconnectedList = entry.getList("disconnectedParticipants", Tag.TAG_COMPOUND);
            for (int j = 0; j < disconnectedList.size(); j++) {
                CompoundTag uuidTag = disconnectedList.getCompound(j);
                disconnectedParticipants.add(uuidTag.getUUID("uuid"));
            }
            
            // Deserialize votes
            Map<UUID, Integer> votes = new HashMap<>();
            ListTag votesList = entry.getList("votes", Tag.TAG_COMPOUND);
            for (int j = 0; j < votesList.size(); j++) {
                CompoundTag voteTag = votesList.getCompound(j);
                UUID player = voteTag.getUUID("player");
                int choice = voteTag.getInt("choice");
                votes.put(player, choice);
            }
            
            LOGGER.info("[Exanira]   loaded: instanceKey={} event={} scene={}",
                    instanceKey, eventId, sceneId);
            
            data.activeEvents.put(instanceKey, new ActiveEventState(eventId, instanceKey, sceneId,
                    participants, disconnectedParticipants, votes, playerAbandonmentTimers, resolved));
        }
        
        return data;
    }
    
    // ── API ───────────────────────────────────────────────────────────────────
    
    public void saveActiveEvent(String instanceKey, ActiveEventState state) {
        LOGGER.info("[Exanira] SavedData.saveActiveEvent called: instanceKey={} event={}",
                instanceKey, state.eventId());
        
        activeEvents.put(instanceKey, state);
        setDirty();
    }
    
    public void removeActiveEvent(String instanceKey) {
        boolean removed = activeEvents.remove(instanceKey) != null;
        
        LOGGER.info("[Exanira] SavedData.removeActiveEvent called: instanceKey={} entryExisted={} eventsAfter={}",
                instanceKey, removed, activeEvents.size());
        
        if (removed) setDirty();
    }
    
    public Optional<ActiveEventState> get(String instanceKey) {
        return Optional.ofNullable(activeEvents.get(instanceKey));
    }
    
    public Set<String> getAllInstanceKeys() {
        return Collections.unmodifiableSet(activeEvents.keySet());
    }
}