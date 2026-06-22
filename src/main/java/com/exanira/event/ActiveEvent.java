package com.exanira.event;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;

public class ActiveEvent {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final EventDefinition definition;

    private final Set<UUID> participants = new HashSet<>();
    private final Map<UUID, Integer> votes = new HashMap<>();

    private String currentSceneId;
    private boolean resolved = false;

    public ActiveEvent(EventDefinition definition, UUID startingPlayer) {
        this.definition = definition;
        this.participants.add(startingPlayer);
        this.currentSceneId = definition.startScene();
    }

    public EventDefinition definition() {
        return definition;
    }

    public Set<UUID> participants() {
        return Collections.unmodifiableSet(participants);
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

    public void recordVote(UUID playerId, int choiceIndex) {
        if (!participants.contains(playerId)) return;
        votes.put(playerId, choiceIndex);
    }

    public boolean hasVoted(UUID playerId) {
        return votes.containsKey(playerId);
    }

    public boolean allVoted() {
        return votes.size() >= participants.size();
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