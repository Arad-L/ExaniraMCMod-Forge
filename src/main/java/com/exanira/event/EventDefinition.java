package com.exanira.event;

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
        Map<String, EventScene> scenes
) {}
