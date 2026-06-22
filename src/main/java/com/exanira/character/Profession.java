package com.exanira.character;

/**
 * Selectable professions during character creation.
 * Each profession distributes exactly 20 stat points across the 6 stats.
 * Array order matches Stat.values(): STR, AGI, INT, PER, LEAD, SUR.
 */
public enum Profession {
    SOLDIER      ("Soldier",        "Combat and leadership under pressure.",      5, 3, 2, 3, 4, 3),
    NURSE        ("Nurse",          "Care, knowledge, and calm under pressure.",  2, 3, 5, 4, 3, 3),
    MECHANIC     ("Mechanic",       "Hands-on problem solving and resourcefulness.", 4, 3, 4, 2, 2, 5),
    TEACHER      ("Teacher",        "Communication, leadership, and broad knowledge.", 2, 2, 5, 3, 5, 3),
    SCAVENGER    ("Scavenger",      "Speed, awareness, and street smarts.",       3, 5, 3, 4, 2, 3),
    FARMER       ("Farmer",         "Endurance, self-sufficiency, and land skills.", 4, 2, 2, 3, 2, 7),
    RADIO_OPERATOR("Radio Operator","Information, pattern recognition, and patience.", 2, 2, 4, 5, 3, 4),
    FIREFIGHTER  ("Firefighter",    "Strength, agility, and command under crisis.", 4, 4, 2, 3, 4, 3);

    private final String displayName;
    private final String description;
    private final int[] baseStats; // indexed by Stat.ordinal()

    Profession(String displayName, String description,
               int str, int agi, int intel, int per, int lead, int sur) {
        this.displayName = displayName;
        this.description = description;
        this.baseStats = new int[]{str, agi, intel, per, lead, sur};
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public int getBaseStat(Stat stat) {
        return baseStats[stat.ordinal()];
    }
}
