package com.exanira.event;

import java.util.Map;

/**
 * A single selectable choice within a scene.
 * {@code requires} maps lowercase stat names to minimum values for the hard-gate check.
 *
 * Exactly one of {@code nextScene} or {@code successEvent} should be set (or neither):
 *  - {@code nextScene}    — advance to a named scene within the same event
 *  - {@code successEvent} — end this event and chain to a different event
 *  - neither              — end this event with no chain
 */
public record EventChoice(
        String text,
        Map<String, Integer> requires,
        String checkType,
        String nextScene,
        String successEvent,
        String lockedText,
        String outcome
) {}
