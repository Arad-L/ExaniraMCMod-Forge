package com.exanira.event;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;

public class PendingEventCapability {

    public static final Capability<PendingEventAttachment> INSTANCE =
            CapabilityManager.get(new CapabilityToken<PendingEventAttachment>(){});

    public static LazyOptional<PendingEventAttachment> get(Player player) {
        return player.getCapability(INSTANCE);
    }
}