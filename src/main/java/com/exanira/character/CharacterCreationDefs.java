package com.exanira.character;

import java.util.List;

/**
 * Static definitions for all character creation content: the 5 lifestyle questions
 * and their options. These are referenced by both the server (stat resolution) and
 * the client (CharacterCreationScreen rendering).
 */
public final class CharacterCreationDefs {

    public static final List<LifestyleQuestion> QUESTIONS = List.of(

        new LifestyleQuestion(
            "How did you stay fit?",
            "Before everything fell apart, how did you keep yourself in shape?",
            List.of(
                LifestyleOption.of("Gym and weightlifting",
                    "kept yourself sharp through heavy lifting and gym routines",
                    Stat.STRENGTH),
                LifestyleOption.of("Running and sports",
                    "stayed fit through running and competitive sports",
                    Stat.AGILITY),
                LifestyleOption.of("Hiking and outdoors",
                    "spent weekends hiking and testing yourself against the wilderness",
                    Stat.SURVIVAL)
            )
        ),

        new LifestyleQuestion(
            "What sharpened your mind?",
            "Where did your mental edge come from?",
            List.of(
                LifestyleOption.of("Reading and research",
                    "always sought knowledge through books and research",
                    Stat.INTELLIGENCE),
                LifestyleOption.of("Observation",
                    "had a talent for reading people and noticing what others missed",
                    Stat.PERCEPTION),
                LifestyleOption.of("Leading others",
                    "naturally organized teams and led people through problems",
                    Stat.LEADERSHIP)
            )
        ),

        new LifestyleQuestion(
            "What did you do in your spare time?",
            "When you weren't working, you were...",
            List.of(
                LifestyleOption.of("Gaming and tech",
                    "spent time gaming and tinkering with technology",
                    Stat.INTELLIGENCE),
                LifestyleOption.of("Hunting and foraging",
                    "went hunting, fishing, or foraging whenever you could",
                    Stat.SURVIVAL),
                LifestyleOption.of("Sports and manual work",
                    "pushed yourself with competitive sports and physical work",
                    Stat.STRENGTH)
            )
        ),

        new LifestyleQuestion(
            "When conflict arose, you...",
            "When things got tense, what was your instinct?",
            List.of(
                LifestyleOption.of("Stepped up and led",
                    "were the first to step in and take charge",
                    Stat.LEADERSHIP),
                LifestyleOption.of("Assessed carefully",
                    "held back, read the situation, then moved decisively",
                    Stat.PERCEPTION),
                LifestyleOption.of("Moved fast",
                    "trusted your instincts and acted before others could think",
                    Stat.AGILITY)
            )
        ),

        new LifestyleQuestion(
            "What would others say about you?",
            "If someone who knew you had to describe you in one sentence...",
            List.of(
                LifestyleOption.of("The strongest around",
                    "that you were the strongest person they had ever met",
                    Stat.STRENGTH),
                LifestyleOption.of("Never missed a thing",
                    "that you never missed a detail and always saw what others overlooked",
                    Stat.PERCEPTION),
                LifestyleOption.of("Built to survive",
                    "that no matter what happened, you would always find a way to survive",
                    Stat.SURVIVAL)
            )
        )
    );

    private CharacterCreationDefs() {}
}
