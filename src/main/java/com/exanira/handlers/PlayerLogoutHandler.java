package com.exanira.handlers;

import com.exanira.event.EventQueueManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Handles server-side player logout.
 * Registered on MinecraftForge.EVENT_BUS from ExaniraMod constructor.
 */
public class PlayerLogoutHandler {

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        // Mark the player as disconnected in any active events they're participating in
        EventQueueManager.INSTANCE.markPlayerDisconnected(player.getUUID());
    }
}