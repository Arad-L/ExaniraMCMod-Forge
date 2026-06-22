package com.exanira.client;

import com.exanira.character.Stat;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.EnumMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class ClientCharacterData {

    private static Map<Stat, Integer> stats = defaultStats();
    private static String backstory = "";

    private ClientCharacterData() {}

    private static Map<Stat, Integer> defaultStats() {
        Map<Stat, Integer> map = new EnumMap<>(Stat.class);
        for (Stat stat : Stat.values()) map.put(stat, 0);
        return map;
    }

    public static void update(Map<Stat, Integer> newStats, String newBackstory) {
        stats    = new EnumMap<>(newStats);
        backstory = newBackstory;
    }

    public static int getStat(Stat stat)        { return stats.getOrDefault(stat, 0); }
    public static String getBackstory()         { return backstory; }
    public static Map<Stat, Integer> getStats() { return Map.copyOf(stats); }
}