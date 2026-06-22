package com.exanira.character;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.LazyOptional;

public class CharacterSheetCapability {

    public static final Capability<CharacterSheet> INSTANCE =
            CapabilityManager.get(new CapabilityToken<CharacterSheet>(){});

    public static LazyOptional<CharacterSheet> get(Player player) {
        return player.getCapability(INSTANCE);
    }
}