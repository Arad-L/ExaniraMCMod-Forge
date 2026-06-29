package com.exanira.event;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;

public class ActiveEvent {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final EventDefinition definition;

    private final Set<UUID> participants = new HashSet<>();
    private final Map<UUID, Integer> votes = new HashMap<>();
    private final Set<UUID> disconnectedParticipants = new HashSet<>();

    private String currentSceneId;
    private boolean resolved = false;
    
    // Abandonment tracking — one timer entry per disconnected player
    private final Map<UUID, Long> playerAbandonmentTimers = new HashMap<>();
    private static final long ABANDONMENT_GRACE_PERIOD_MS = 5 * 60 * 1000L;

    public ActiveEvent(EventDefinition definition, UUID startingPlayer) {
        this.definition = definition;
        this.participants.add(startingPlayer);
        this.currentSceneId = definition.startScene();
    }

    /**
     * Restoration constructor — rebuilds an event from persisted SavedData state.
     * Used by {@link com.exanira.event.EventQueueManager#restoreEventsFromSave()}.
     */
    ActiveEvent(EventDefinition definition, String sceneId,
                Set<UUID> participants, Map<UUID, Integer> votes,
                Set<UUID> disconnectedParticipants,
                Map<UUID, Long> playerAbandonmentTimers, boolean resolved) {
        this.definition = definition;
        this.currentSceneId = sceneId;
        this.participants.addAll(participants);
        this.votes.putAll(votes);
        this.disconnectedParticipants.addAll(disconnectedParticipants);
        this.playerAbandonmentTimers.putAll(playerAbandonmentTimers);
        this.resolved = resolved;
    }

    public EventDefinition definition() {
        return definition;
    }

    public Set<UUID> participants() {
        return Collections.unmodifiableSet(participants);
    }
    
    public Set<UUID> disconnectedParticipants() {
        return Collections.unmodifiableSet(disconnectedParticipants);
    }

    public EventScene currentScene() {
        return definition.scenes().get(currentSceneId);
    }

    public String currentSceneId() {
        return currentSceneId;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void markResolved() {
        this.resolved = true;
    }

    public void setCurrentScene(String sceneId) {
        this.currentSceneId = sceneId;
        this.votes.clear();
    }

    void addParticipant(UUID playerId) {
        participants.add(playerId);
        LOGGER.info("[Exanira] Added participant {} (total={})",
                playerId, participants.size());
    }
    
    public void markPlayerDisconnected(UUID playerId) {
        if (participants.contains(playerId)) {
            disconnectedParticipants.add(playerId);
            LOGGER.info("[Exanira] Marked player {} as disconnected", playerId);
        }
    }

    /**
     * @return true if the player was actually in the disconnected set (and timer was reset).
     */
    public boolean markPlayerReconnected(UUID playerId) {
        if (disconnectedParticipants.remove(playerId)) {
            playerAbandonmentTimers.remove(playerId);  // cancel their individual timer
            LOGGER.info("[Exanira] Marked player {} as reconnected, timer cancelled", playerId);
            return true;
        }
        return false;
    }

    /** Cancel all running abandonment timers (e.g. when a new participant joins). */
    public void cancelAllAbandonmentTimers() {
        playerAbandonmentTimers.clear();
    }

    /** Whether any per-player abandonment timer is currently running. */
    public boolean hasAnyAbandonmentTimer() {
        return !playerAbandonmentTimers.isEmpty();
    }

    /** Read-only view of per-player timers, used for persistence. */
    public Map<UUID, Long> playerAbandonmentTimers() {
        return Collections.unmodifiableMap(playerAbandonmentTimers);
    }

    /**
     * Start grace-period timers for every disconnected player that does not yet have one,
     * provided all currently-connected participants have already voted.
     *
     * @return the set of players for whom a new timer was started (empty if conditions not met)
     */
    public Set<UUID> startTimersForEligibleDisconnected() {
        if (!allConnectedVoted() || allVoted()) return Collections.emptySet();
        Set<UUID> newlyTimered = new HashSet<>();
        for (UUID id : disconnectedParticipants) {
            if (playerAbandonmentTimers.putIfAbsent(id, System.currentTimeMillis()) == null) {
                newlyTimered.add(id);
            }
        }
        return newlyTimered;
    }

    public void recordVote(UUID playerId, int choiceIndex) {
        if (!participants.contains(playerId)) return;
        votes.put(playerId, choiceIndex);
    }

    public boolean hasVoted(UUID playerId) {
        return votes.containsKey(playerId);
    }
    
    /**
     * Check if all connected participants have voted
     */
    public boolean allConnectedVoted() {
        int connectedParticipants = participants.size() - disconnectedParticipants.size();
        return votes.size() >= connectedParticipants;
    }

    public boolean allVoted() {
        // Original behavior: check against all participants (including disconnected)
        return votes.size() >= participants.size();
    }
    
/**
     * Remove disconnected player from participants and apply offline fallback
     */
    public void resolveAbandonedPlayer(UUID playerId) {
        if (disconnectedParticipants.contains(playerId)) {
            // Apply the offline fallback strategy to this player's vote
            String fallbackStrategy = definition.offlineFallback();
            
            int fallbackVote = -1;
            if ("majority".equalsIgnoreCase(fallbackStrategy)) {
                fallbackVote = resolveMajorityChoice();
            } else if ("default".equalsIgnoreCase(fallbackStrategy)) {
                // Default choice would be set to the first valid option or a specific default
                EventScene scene = currentScene();
                if (scene != null && !scene.choices().isEmpty()) {
                    fallbackVote = 0; // Default to first choice
                }
            } else if ("random".equalsIgnoreCase(fallbackStrategy)) {
                // Random vote from available choices
                EventScene scene = currentScene();
                if (scene != null && !scene.choices().isEmpty()) {
                    fallbackVote = new Random().nextInt(scene.choices().size());
                }
            } else if ("ignore".equalsIgnoreCase(fallbackStrategy) || "none".equalsIgnoreCase(fallbackStrategy)) {
                // Ignore strategy - don't vote, just remove the player
                LOGGER.info("[Exanira] Ignoring abandoned player {} (offlineFallback=ignore)", playerId);
            }
            
            // If we have a valid fallback vote, record it for this player
            if (fallbackVote >= 0) {
                votes.put(playerId, fallbackVote);
                LOGGER.info("[Exanira] Applied offline fallback vote {} to abandoned player {}", 
                           fallbackVote, playerId);
            } else if ("ignore".equalsIgnoreCase(fallbackStrategy) || "none".equalsIgnoreCase(fallbackStrategy)) {
                // For ignore strategy, we still want to remove the player from disconnected list
                LOGGER.info("[Exanira] Removed abandoned player {} (ignored)", playerId);
            }
            
            // Remove the disconnected player from participants list
            disconnectedParticipants.remove(playerId);
            participants.remove(playerId);
            LOGGER.info("[Exanira] Removed abandoned player {} from event", playerId);
        }
    }
    
    /**
     * Resolve all disconnected players whose abandonment grace period has expired.
     * Called from EventQueueManager's tick handler.
     *
     * @return the UUIDs of every player that was abandoned and removed from this event,
     *         so the caller can clean up server-side maps and send {@code EventEndPacket}.
     */
    /**
     * Check per-player timers and resolve only those whose individual grace period has elapsed.
     * Other disconnected players keep their own timers running untouched.
     *
     * @return the set of players that were abandoned and removed this tick
     */
    public Set<UUID> checkAndResolveExpiredAbandonments() {
        if (playerAbandonmentTimers.isEmpty()) return Collections.emptySet();
        long now = System.currentTimeMillis();
        Set<UUID> expired = new HashSet<>();
        for (Map.Entry<UUID, Long> entry : playerAbandonmentTimers.entrySet()) {
            if (now - entry.getValue() >= ABANDONMENT_GRACE_PERIOD_MS) {
                expired.add(entry.getKey());
            }
        }
        if (expired.isEmpty()) return Collections.emptySet();
        // Remove their timers first, then apply fallback and evict.
        for (UUID playerId : expired) {
            playerAbandonmentTimers.remove(playerId);
            resolveAbandonedPlayer(playerId);
        }
        return expired;
    }

    public Map<Integer, Integer> getVoteCounts() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int choice : votes.values()) {
            counts.put(choice, counts.getOrDefault(choice, 0) + 1);
        }
        return counts;
    }

    public int resolveMajorityChoice() {
        if (votes.isEmpty()) return -1;

        return getVoteCounts().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(-1);
    }

    public Map<UUID, Integer> votes() {
        return Collections.unmodifiableMap(votes);
    }

    public String debugState() {
        return "Scene=" + currentSceneId +
                ", participants=" + participants.size() +
                ", votes=" + votes.size();
    }
}
