package com.exanira.backstory;

import java.util.List;

/**
 * Random flavor pools used in backstory generation.
 * These are seeded by player UUID so results are reproducible per player.
 * Profession and lifestyle-specific text live in CharacterCreationDefs / LifestyleOption.
 */
public final class BackstoryPools {

    public static final List<String> LOCATIONS = List.of(
            "a coastal city", "a small farm town", "a military base", "the suburbs",
            "a mountain village", "an industrial district", "a university campus"
    );

    public static final List<String> LOSSES = List.of(
            "your family", "your crew", "your sense of purpose", "your home",
            "your closest friend", "your faith", "your future plans"
    );

    public static final List<String> DISTRUST_TARGETS = List.of(
            "strangers", "authority", "luck", "technology", "medicine", "other survivors"
    );

    private BackstoryPools() {}
}
