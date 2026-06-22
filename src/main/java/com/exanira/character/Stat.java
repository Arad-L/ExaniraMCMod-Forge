package com.exanira.character;

public enum Stat {
    STRENGTH("Strength"),
    AGILITY("Agility"),
    INTELLIGENCE("Intelligence"),
    PERCEPTION("Perception"),
    LEADERSHIP("Leadership"),
    SURVIVAL("Survival");

    private final String displayName;

    Stat(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
