package com.exanira.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists in-progress event state per player to the world's SavedData.
 * This survives server restarts (including singleplayer world exits), so players
 * can rejoin and resume an event they were mid-way through.
 *
 * Stored in: world/data/exanira_active_events.dat
 */
public class ExaniraEventSavedData extends SavedData {

    private static final String DATA_NAME = "exanira_active_events";
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Minimal snapshot needed to reconstruct an ActiveEvent on reconnect. */
    public record PendingEvent(String eventId, String currentSceneId) {}

    private final Map<UUID, PendingEvent> pendingEvents = new HashMap<>();

    // ── Factory ──────────────────────────────────────────────────────────────

    public static ExaniraEventSavedData getOrCreate(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                ExaniraEventSavedData::load,
                ExaniraEventSavedData::new,
                DATA_NAME
        );
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag) {
        LOGGER.info("[Exanira] SavedData serializing {} pending event(s) to disk", pendingEvents.size());

        ListTag list = new ListTag();

        pendingEvents.forEach((uuid, pending) -> {
            LOGGER.info("[Exanira]   writing: player={} event={} scene={}",
                    uuid, pending.eventId(), pending.currentSceneId());

            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", uuid);
            entry.putString("eventId", pending.eventId());
            entry.putString("currentSceneId", pending.currentSceneId());

            list.add(entry);
        });

        tag.put("events", list);
        return tag;
    }

    public static ExaniraEventSavedData load(CompoundTag tag) {
        ExaniraEventSavedData data = new ExaniraEventSavedData();

        ListTag list = tag.getList("events", Tag.TAG_COMPOUND);

        LOGGER.info("[Exanira] SavedData loading {} pending event(s) from disk", list.size());

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);

            UUID uuid = entry.getUUID("player");
            String eventId = entry.getString("eventId");
            String sceneId = entry.getString("currentSceneId");

            LOGGER.info("[Exanira]   loaded: player={} event={} scene={}",
                    uuid, eventId, sceneId);

            data.pendingEvents.put(uuid, new PendingEvent(eventId, sceneId));
        }

        return data;
    }

    // ── API ───────────────────────────────────────────────────────────────────

    public void save(UUID playerId, String eventId, String sceneId) {
        LOGGER.info("[Exanira] SavedData.save called: player={} event={} scene={}",
                playerId, eventId, sceneId);

        pendingEvents.put(playerId, new PendingEvent(eventId, sceneId));
        setDirty();
    }

    public void remove(UUID playerId) {
        boolean removed = pendingEvents.remove(playerId) != null;

        LOGGER.info("[Exanira] SavedData.remove called: player={} entryExisted={} pendingAfter={}",
                playerId, removed, pendingEvents.size());

        if (removed) setDirty();
    }

    public Optional<PendingEvent> get(UUID playerId) {
        return Optional.ofNullable(pendingEvents.get(playerId));
    }
}