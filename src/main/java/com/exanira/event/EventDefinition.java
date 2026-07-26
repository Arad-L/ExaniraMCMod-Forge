package com.exanira.event;

import com.exanira.character.Stat;

import java.util.List;
import java.util.Map;

/**
 * Immutable data class representing a fully parsed event definition.
 * Loaded from {@code data/exanira/events/<id>.json}.
 *
 * Events are composed of named {@link EventScene}s. Play begins at {@code startScene}.
 * Each scene's choices point to the next scene by name, or terminate the event.
 */
public record EventDefinition(
        String id,
        EventType type,
        String npc,
        String offlineFallback,
        String startScene,
        Map<String, EventScene> scenes,
        // Phase 4 fields
        int season,
        int order,
        List<String> unlockRequires,
        Map<String, Boolean> setsPersonalFlags,
        boolean seasonFinale,
        StatBoost grantsStatBoost
) {
    /**
     * Optional stat bonus awarded to all online participants when the event resolves.
     * Applied in {@code EventQueueManager.endEvent()}.
     */
    public record StatBoost(Stat stat, int amount) {}
}
