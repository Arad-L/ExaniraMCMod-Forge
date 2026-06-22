package com.exanira.character;

import com.exanira.backstory.BackstoryGenerator;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class CharacterSheet implements INBTSerializable<CompoundTag> {

    /** Hard cap for any stat value. Starting values come from profession + lifestyle choices. */
    public static final int MAX_STAT = 10;

    private final EnumMap<Stat, Integer> stats = new EnumMap<>(Stat.class);
    private String backstory = "";
    private boolean initialized = false;

    public CharacterSheet() {
        for (Stat stat : Stat.values()) {
            stats.put(stat, 1);
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Resolves the character sheet from the player's creation choices.
     * Called server-side when CharacterCreationSubmitPacket is received.
     *
     * @param professionOrdinal index into Profession.values()
     * @param lifestyleChoices  list of selected option indices, one per LifestyleQuestion
     */
    public void initialize(int professionOrdinal, List<Integer> lifestyleChoices) {
        Profession profession = Profession.values()[professionOrdinal];

        // Set base stats from profession preset
        for (Stat stat : Stat.values()) {
            stats.put(stat, profession.getBaseStat(stat));
        }

        // Apply +1 per lifestyle choice, capped at MAX_STAT
        List<LifestyleQuestion> questions = CharacterCreationDefs.QUESTIONS;
        for (int i = 0; i < Math.min(lifestyleChoices.size(), questions.size()); i++) {
            LifestyleOption opt = questions.get(i).options().get(lifestyleChoices.get(i));
            for (Map.Entry<Stat, Integer> bonus : opt.statBonuses().entrySet()) {
                stats.merge(
                        bonus.getKey(),
                        bonus.getValue(),
                        (current, delta) -> Math.min(MAX_STAT, current + delta)
                );
            }
        }

        backstory = BackstoryGenerator.generate(professionOrdinal, lifestyleChoices);
        initialized = true;
    }

    public int getStat(Stat stat) {
        return stats.getOrDefault(stat, 1);
    }

    public void setStat(Stat stat, int value) {
        stats.put(stat, Math.min(MAX_STAT, Math.max(1, value)));
    }

    public String getBackstory() {
        return backstory;
    }

    // --- Derived values ---

    /** Agility * 2 */
    public int getStealthEffectiveness() {
        return getStat(Stat.AGILITY) * 2;
    }

    /** Perception + Intelligence */
    public int getLootQualityBonus() {
        return getStat(Stat.PERCEPTION) + getStat(Stat.INTELLIGENCE);
    }

    /** Perception * 3 blocks */
    public int getHordeDetectionRange() {
        return getStat(Stat.PERCEPTION) * 3;
    }

    // Leadership derived value deferred to Phase 3 (party system).

    // --- NBT serialization ---

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();

        tag.putBoolean("initialized", initialized);
        tag.putString("backstory", backstory);

        CompoundTag statsTag = new CompoundTag();
        for (Map.Entry<Stat, Integer> entry : stats.entrySet()) {
            statsTag.putInt(entry.getKey().name(), entry.getValue());
        }

        tag.put("stats", statsTag);

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        initialized = tag.getBoolean("initialized");
        backstory = tag.getString("backstory");

        CompoundTag statsTag = tag.getCompound("stats");

        for (Stat stat : Stat.values()) {
            if (statsTag.contains(stat.name())) {
                stats.put(stat, statsTag.getInt(stat.name()));
            }
        }
    }
}