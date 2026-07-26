package com.exanira.client;

import com.exanira.character.CompletedSideEventRecord;
import com.exanira.character.Stat;
import com.exanira.network.CharacterSheetSyncPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class ClientCharacterData {

    private static Map<Stat, Integer> stats = defaultStats();
    private static String backstory = "";
    private static int currentSeason = 1;
    private static List<String> completedMainEvents = List.of();
    private static List<String> witnessedMainEvents = List.of();
    private static List<CompletedSideEventRecord> completedSideEvents = List.of();
    private static List<CharacterSheetSyncPacket.EventSummary> eventSummaries = List.of();

    private ClientCharacterData() {}

    private static Map<Stat, Integer> defaultStats() {
        Map<Stat, Integer> map = new EnumMap<>(Stat.class);
        for (Stat stat : Stat.values()) map.put(stat, 0);
        return map;
    }

    public static void update(Map<Stat, Integer> newStats, String newBackstory,
                              int newCurrentSeason,
                              List<String> newCompletedMain,
                              List<String> newWitnessedMain,
                              List<CompletedSideEventRecord> newCompletedSide,
                              List<CharacterSheetSyncPacket.EventSummary> newEventSummaries) {
        stats               = new EnumMap<>(newStats);
        backstory           = newBackstory;
        currentSeason       = newCurrentSeason;
        completedMainEvents = List.copyOf(newCompletedMain);
        witnessedMainEvents = List.copyOf(newWitnessedMain);
        completedSideEvents = List.copyOf(newCompletedSide);
        eventSummaries      = List.copyOf(newEventSummaries);
    }

    public static int getStat(Stat stat)                                          { return stats.getOrDefault(stat, 0); }
    public static String getBackstory()                                           { return backstory; }
    public static Map<Stat, Integer> getStats()                                   { return Map.copyOf(stats); }
    public static int getCurrentSeason()                                          { return currentSeason; }
    public static List<String> getCompletedMainEvents()                           { return completedMainEvents; }
    public static List<String> getWitnessedMainEvents()                           { return witnessedMainEvents; }
    public static List<CompletedSideEventRecord> getCompletedSideEvents()         { return completedSideEvents; }
    public static List<CharacterSheetSyncPacket.EventSummary> getEventSummaries() { return eventSummaries; }
}
