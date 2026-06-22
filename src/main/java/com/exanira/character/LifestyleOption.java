package com.exanira.character;

import java.util.Map;

/**
 * A single selectable option within a LifestyleQuestion.
 * buttonText  — short label shown on the button in CharacterCreationScreen.
 * backstoryText — phrase inserted into the generated backstory sentence.
 * statBonuses — stat(s) that receive +1 when this option is chosen.
 */
public record LifestyleOption(String buttonText, String backstoryText, Map<Stat, Integer> statBonuses) {

    /** Convenience factory for the common case of a single-stat +1 bonus. */
    public static LifestyleOption of(String buttonText, String backstoryText, Stat stat) {
        return new LifestyleOption(buttonText, backstoryText, Map.of(stat, 1));
    }
}
