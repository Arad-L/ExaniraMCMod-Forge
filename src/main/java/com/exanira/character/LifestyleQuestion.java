package com.exanira.character;

import java.util.List;

/**
 * A single lifestyle question shown during character creation (Steps 2–6).
 * title    — short title displayed at the top of the screen for this step.
 * bodyText — longer explanatory text shown below the title.
 * options  — exactly 3 selectable options; each grants +1 to a stat.
 */
public record LifestyleQuestion(String title, String bodyText, List<LifestyleOption> options) {}
