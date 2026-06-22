package com.exanira.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;

/**
 * Stores an in-progress event on the player's own NBT (via AttachmentType).
 *
 * Because this is an attachment with a serializer, it is written to the player's
 * save file (saves/WorldName/playerdata/UUID.dat) synchronously alongside the
 * player's inventory and stats — no async IO, no temp-file race conditions.
 *
 * The data is per-world: each singleplayer world has its own playerdata folder,
 * so switching worlds always gives a fresh attachment for a new player.
 */
public class PendingEventAttachment implements INBTSerializable<CompoundTag> {

    @Nullable private String instanceKey = null;  // full runtime key (eventId + "_" + uuid)
    @Nullable private String eventId     = null;  // just the event definition id, for reconstruction
    @Nullable private String sceneId     = null;  // current scene within the event

    public PendingEventAttachment() {}

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean hasPendingEvent() {
        return eventId != null;
    }

    @Nullable public String getInstanceKey() { return instanceKey; }
    @Nullable public String getEventId()     { return eventId;     }
    @Nullable public String getSceneId()     { return sceneId;     }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void set(String instanceKey, String eventId, String sceneId) {
        this.instanceKey = instanceKey;
        this.eventId     = eventId;
        this.sceneId     = sceneId;
    }

    public void clear() {
        this.instanceKey = null;
        this.eventId     = null;
        this.sceneId     = null;
    }

    // ── INBTSerializable ─────────────────────────────────────────────────────

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if (eventId != null) {
            tag.putString("instanceKey", instanceKey != null ? instanceKey : "");
            tag.putString("eventId",     eventId);
            tag.putString("sceneId",     sceneId != null ? sceneId : "");
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag.contains("eventId")) {
            this.eventId     = tag.getString("eventId");
            this.instanceKey = tag.getString("instanceKey");
            this.sceneId     = tag.getString("sceneId");
            if (this.instanceKey.isEmpty()) this.instanceKey = null;
            if (this.sceneId.isEmpty())     this.sceneId     = null;
        } else {
            this.instanceKey = null;
            this.eventId     = null;
            this.sceneId     = null;
        }
    }
}
