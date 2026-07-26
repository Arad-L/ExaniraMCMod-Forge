package com.exanira.character;

import com.exanira.backstory.BackstoryGenerator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.*;

public class CharacterSheet implements INBTSerializable<CompoundTag> {

    /** Hard cap for any stat value. Starting values come from profession + lifestyle choices. */
    public static final int MAX_STAT = 10;

    private final EnumMap<Stat, Integer> stats = new EnumMap<>(Stat.class);
    private String backstory = "";
    private boolean initialized = false;

    // ── Phase 4: Per-player story progression ────────────────────────────────
    /** 1-indexed. Increments when the player completes a season finale main event. */
    private int currentSeason = 1;
    /** IDs of main story events this player has personally completed. */
    private final Set<String> completedMainEvents = new HashSet<>();
    /** IDs of main story events this player has personally started or participated in. */
    private final Set<String> witnessedMainEvents = new HashSet<>();
    /** NPC/object outcome flags written by endEvent() via setsPersonalFlags. */
    private final Map<String, Boolean> personalFlags = new HashMap<>();
    /** Completed side event IDs grouped by past season (for archive display). */
    private final Map<Integer, Set<String>> seasonHistory = new HashMap<>();
    /** Side event completion records, capped at CompletedSideEventRecord.MAX_RECORDS. */
    private final List<CompletedSideEventRecord> completedSideEvents = new ArrayList<>();

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

    /**
     * Increments a stat by {@code amount}, clamping silently at {@value #MAX_STAT}.
     * Used by endEvent() when grantsStatBoost is set.
     */
    public void incrementStat(Stat stat, int amount) {
        stats.merge(stat, amount, (current, delta) -> Math.min(MAX_STAT, current + delta));
    }

    public String getBackstory() {
        return backstory;
    }

    // ── Phase 4 accessors ────────────────────────────────────────────────────

    public int getCurrentSeason() { return currentSeason; }
    public void setCurrentSeason(int season) { this.currentSeason = season; }

    public Set<String> getCompletedMainEvents()  { return Collections.unmodifiableSet(completedMainEvents); }
    public Set<String> getWitnessedMainEvents()  { return Collections.unmodifiableSet(witnessedMainEvents); }

    public void addCompletedMainEvent(String eventId) {
        completedMainEvents.add(eventId);
        witnessedMainEvents.add(eventId);
    }

    public void addWitnessedMainEvent(String eventId) {
        witnessedMainEvents.add(eventId);
    }

    public Map<String, Boolean> getPersonalFlags() { return Collections.unmodifiableMap(personalFlags); }

    public void setPersonalFlag(String key, boolean value) { personalFlags.put(key, value); }

    public Map<Integer, Set<String>> getSeasonHistory() { return Collections.unmodifiableMap(seasonHistory); }

    /**
     * Archives the completed side event IDs for {@code season} into seasonHistory.
     * Called when the player completes the season finale.
     */
    public void archiveSeasonSideEvents(int season) {
        Set<String> archived = new HashSet<>();
        for (CompletedSideEventRecord record : completedSideEvents) {
            if (record.season() == season) {
                archived.add(record.eventId());
            }
        }
        seasonHistory.put(season, Collections.unmodifiableSet(archived));
    }

    public List<CompletedSideEventRecord> getCompletedSideEvents() {
        return Collections.unmodifiableList(completedSideEvents);
    }

    /**
     * Adds a side event completion record, capping the list at
     * {@link CompletedSideEventRecord#MAX_RECORDS} to prevent unbounded NBT growth.
     */
    public void addCompletedSideEvent(CompletedSideEventRecord record) {
        completedSideEvents.add(record);
        if (completedSideEvents.size() > CompletedSideEventRecord.MAX_RECORDS) {
            completedSideEvents.remove(0);
        }
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

        // Phase 4 progression
        tag.putInt("currentSeason", currentSeason);

        ListTag completedMainTag = new ListTag();
        for (String id : completedMainEvents) completedMainTag.add(StringTag.valueOf(id));
        tag.put("completedMainEvents", completedMainTag);

        ListTag witnessedMainTag = new ListTag();
        for (String id : witnessedMainEvents) witnessedMainTag.add(StringTag.valueOf(id));
        tag.put("witnessedMainEvents", witnessedMainTag);

        CompoundTag flagsTag = new CompoundTag();
        for (Map.Entry<String, Boolean> entry : personalFlags.entrySet()) {
            flagsTag.putBoolean(entry.getKey(), entry.getValue());
        }
        tag.put("personalFlags", flagsTag);

        CompoundTag historyTag = new CompoundTag();
        for (Map.Entry<Integer, Set<String>> entry : seasonHistory.entrySet()) {
            ListTag seasonList = new ListTag();
            for (String id : entry.getValue()) seasonList.add(StringTag.valueOf(id));
            historyTag.put(String.valueOf(entry.getKey()), seasonList);
        }
        tag.put("seasonHistory", historyTag);

        ListTag sideEventsTag = new ListTag();
        for (CompletedSideEventRecord record : completedSideEvents) {
            sideEventsTag.add(record.toNbt());
        }
        tag.put("completedSideEvents", sideEventsTag);

        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        initialized = tag.getBoolean("initialized");
        backstory   = tag.getString("backstory");

        CompoundTag statsTag = tag.getCompound("stats");
        for (Stat stat : Stat.values()) {
            if (statsTag.contains(stat.name())) {
                stats.put(stat, statsTag.getInt(stat.name()));
            }
        }

        // Phase 4 progression
        currentSeason = tag.contains("currentSeason") ? tag.getInt("currentSeason") : 1;

        completedMainEvents.clear();
        if (tag.contains("completedMainEvents", Tag.TAG_LIST)) {
            for (Tag t : tag.getList("completedMainEvents", Tag.TAG_STRING)) {
                completedMainEvents.add(t.getAsString());
            }
        }

        witnessedMainEvents.clear();
        if (tag.contains("witnessedMainEvents", Tag.TAG_LIST)) {
            for (Tag t : tag.getList("witnessedMainEvents", Tag.TAG_STRING)) {
                witnessedMainEvents.add(t.getAsString());
            }
        }

        personalFlags.clear();
        if (tag.contains("personalFlags", Tag.TAG_COMPOUND)) {
            CompoundTag flagsTag = tag.getCompound("personalFlags");
            for (String key : flagsTag.getAllKeys()) {
                personalFlags.put(key, flagsTag.getBoolean(key));
            }
        }

        seasonHistory.clear();
        if (tag.contains("seasonHistory", Tag.TAG_COMPOUND)) {
            CompoundTag historyTag = tag.getCompound("seasonHistory");
            for (String key : historyTag.getAllKeys()) {
                Set<String> ids = new HashSet<>();
                for (Tag t : historyTag.getList(key, Tag.TAG_STRING)) {
                    ids.add(t.getAsString());
                }
                try {
                    seasonHistory.put(Integer.parseInt(key), ids);
                } catch (NumberFormatException ignored) {}
            }
        }

        completedSideEvents.clear();
        if (tag.contains("completedSideEvents", Tag.TAG_LIST)) {
            for (Tag t : tag.getList("completedSideEvents", Tag.TAG_COMPOUND)) {
                completedSideEvents.add(CompletedSideEventRecord.fromNbt((CompoundTag) t));
            }
        }
    }
}