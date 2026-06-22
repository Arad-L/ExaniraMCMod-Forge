package com.exanira.event;

import java.util.List;

/**
 * A single named step within an event.
 * A scene with an empty {@code choices} list is terminal — it auto-presents a
 * "Continue" button that dismisses the event.
 */
public record EventScene(
        String id,
        List<String> dialogue,
        List<EventChoice> choices,
        /** Optional: if set on a terminal scene (no choices), chains to this event after dismiss. */
        String successEvent
) {}
