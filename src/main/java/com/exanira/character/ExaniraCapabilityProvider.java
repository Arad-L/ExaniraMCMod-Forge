package com.exanira.character;

import com.exanira.event.PendingEventAttachment;
import com.exanira.event.PendingEventCapability;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Single capability provider attached to every Player entity.
 * Hosts both CharacterSheet and PendingEventAttachment in one NBT block.
 *
 * NBT layout:
 *   { "character": { ... CharacterSheet ... },
 *     "event":     { ... PendingEventAttachment ... } }
 */
public class ExaniraCapabilityProvider implements ICapabilitySerializable<CompoundTag> {

    private final CharacterSheet characterSheet = new CharacterSheet();
    private final PendingEventAttachment pendingEvent = new PendingEventAttachment();

    private final LazyOptional<CharacterSheet> characterSheetOpt = LazyOptional.of(() -> characterSheet);
    private final LazyOptional<PendingEventAttachment> pendingEventOpt = LazyOptional.of(() -> pendingEvent);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == CharacterSheetCapability.INSTANCE) {
            return characterSheetOpt.cast();
        }
        if (cap == PendingEventCapability.INSTANCE) {
            return pendingEventOpt.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("character", characterSheet.serializeNBT());
        tag.put("event", pendingEvent.serializeNBT());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag.contains("character")) {
            characterSheet.deserializeNBT(tag.getCompound("character"));
        }
        if (tag.contains("event")) {
            pendingEvent.deserializeNBT(tag.getCompound("event"));
        }
    }
}
