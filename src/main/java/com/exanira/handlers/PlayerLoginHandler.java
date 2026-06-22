package com.exanira.handlers;

import com.exanira.ExaniraMod;
import com.exanira.character.CharacterSheetCapability;
import com.exanira.event.EventQueueManager;
import com.exanira.item.ExaniraItems;
import com.exanira.network.CharacterSheetSyncPacket;
import com.exanira.network.OpenCharacterCreationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Handles server-side player login.
 * Registered on MinecraftForge.EVENT_BUS from ExaniraMod constructor.
 */
public class PlayerLoginHandler {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        CharacterSheetCapability.get(player).ifPresent(sheet -> {
            if (!sheet.isInitialized()) {
                // First login — prompt character creation
                ExaniraMod.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new OpenCharacterCreationPacket()
                );
            } else {
                // Returning player — restore items, resume events, sync sheet
                ExaniraItems.ensureRadio(player);
                EventQueueManager.INSTANCE.resyncPlayerIfMidEvent(player);
                ExaniraMod.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new CharacterSheetSyncPacket(sheet)
                );
            }
        });
    }
}
