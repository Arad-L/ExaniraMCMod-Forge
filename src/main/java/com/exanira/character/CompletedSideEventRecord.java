package com.exanira.character;

import net.minecraft.nbt.CompoundTag;

/**
 * Per-player record of a completed side event outcome.
 * Stored in {@link CharacterSheet} NBT under key "completedSideEvents".
 * Capped at {@value #MAX_RECORDS} entries to prevent unbounded NBT growth.
 */
public record CompletedSideEventRecord(
        String eventId,
        int season,
        int starRating,   // 1, 2, or 3
        long completedAt  // System.currentTimeMillis() at resolution
) {
    public static final int MAX_RECORDS = 200;

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("eventId", eventId);
        tag.putInt("season", season);
        tag.putInt("starRating", starRating);
        tag.putLong("completedAt", completedAt);
        return tag;
    }

    public static CompletedSideEventRecord fromNbt(CompoundTag tag) {
        return new CompletedSideEventRecord(
                tag.getString("eventId"),
                tag.getInt("season"),
                tag.getInt("starRating"),
                tag.getLong("completedAt")
        );
    }
}
