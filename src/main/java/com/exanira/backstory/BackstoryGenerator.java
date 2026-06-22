package com.exanira.backstory;

import com.exanira.character.CharacterCreationDefs;
import com.exanira.character.LifestyleOption;
import com.exanira.character.Profession;

import java.util.List;
import java.util.Random;

/**
 * Generates a player's backstory from their character creation choices.
 * Profession and lifestyle selections produce deterministic narrative phrases.
 * Flavor details (location, loss, distrust) are drawn randomly so each
 * character creation produces a distinct story.
 */
public final class BackstoryGenerator {

    private BackstoryGenerator() {}

    /**
     * @param professionOrdinal  index into Profession.values()
     * @param lifestyleChoices   selected option index per question (indices 0, 2 used for flavor)
     */
    public static String generate(int professionOrdinal, List<Integer> lifestyleChoices) {
        Random random = new Random();

        Profession profession = Profession.values()[professionOrdinal];

        // Q1 (index 0) — physical background
        String physicalBg = getOptionText(
                lifestyleChoices,
                0,
                "stayed active in whatever way you could"
        );

        // Q3 (index 2) — spare-time hobby
        String hobby = getOptionText(
                lifestyleChoices,
                2,
                "kept busy in your spare time"
        );

        String location = pick(BackstoryPools.LOCATIONS, random);
        String loss     = pick(BackstoryPools.LOSSES, random);
        String distrust = pick(BackstoryPools.DISTRUST_TARGETS, random);

        return String.format(
                "You were a %s in %s. You %s. When you weren't working, you %s. " +
                "When everything fell apart, you lost %s. Now you trust %s the least.",
                profession.displayName().toLowerCase(),
                location,
                physicalBg,
                hobby,
                loss,
                distrust
        );
    }

    private static String getOptionText(
            List<Integer> choices,
            int questionIndex,
            String fallback
    ) {
        if (questionIndex >= choices.size()) return fallback;

        List<LifestyleOption> options =
                CharacterCreationDefs.QUESTIONS.get(questionIndex).options();

        int choiceIdx = choices.get(questionIndex);

        if (choiceIdx < 0 || choiceIdx >= options.size()) {
            return fallback;
        }

        return options.get(choiceIdx).backstoryText();
    }

    private static <T> T pick(List<T> list, Random random) {
        return list.get(random.nextInt(list.size()));
    }
}