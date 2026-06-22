package com.exanira.handlers;

import com.exanira.ExaniraMod;
import com.exanira.character.CharacterSheetCapability;
import com.exanira.character.ExaniraCapabilityProvider;
import com.exanira.event.PendingEventCapability;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class PlayerCapabilityHandler {

    private static final ResourceLocation CAPABILITY_KEY =
            new ResourceLocation(ExaniraMod.MODID, "player_capabilities");

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(CAPABILITY_KEY, new ExaniraCapabilityProvider());
        }
    }

    /**
     * Copies capability data from the old player to the new player on respawn or
     * dimension change. The old player's caps may already be invalidated after
     * death, so reviveCaps()/invalidateCaps() bracket the access.
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player oldPlayer = event.getOriginal();
        Player newPlayer = (Player) event.getEntity();

        oldPlayer.reviveCaps();

        oldPlayer.getCapability(CharacterSheetCapability.INSTANCE).ifPresent(oldSheet ->
                newPlayer.getCapability(CharacterSheetCapability.INSTANCE).ifPresent(newSheet ->
                        newSheet.deserializeNBT(oldSheet.serializeNBT())
                )
        );

        oldPlayer.getCapability(PendingEventCapability.INSTANCE).ifPresent(oldPending ->
                newPlayer.getCapability(PendingEventCapability.INSTANCE).ifPresent(newPending ->
                        newPending.deserializeNBT(oldPending.serializeNBT())
                )
        );

        oldPlayer.invalidateCaps();
    }
}
