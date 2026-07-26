package com.exanira.event;

import java.util.List;

/**
 * A single named step within an event.
 * A scene with an empty {@code choices} list is terminal — it auto-presents a
 * "Continue" button that dismisses the event.
 *
 * {@code starRating} (1–3) is recorded when the event ends on this scene.
 * Defaults to 0 (absent); treated as 1★ if 0.
 */
public record EventScene(
        String id,
        List<String> dialogue,
        List<EventChoice> choices,
        /** Optional: if set on a terminal scene (no choices), chains to this event after dismiss. */
        String successEvent,
        /** Optional star rating (1–3) recorded when the event resolves on this terminal scene. */
        int starRating
) {}
