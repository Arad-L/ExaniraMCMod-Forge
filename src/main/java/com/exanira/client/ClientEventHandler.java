package com.exanira.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Handles client-side game events.
 * Registered on MinecraftForge.EVENT_BUS from ExaniraModClient (client-only).
 */
@OnlyIn(Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();

        // Clear stale event state when the player disconnects (mc.player becomes null).
        if (mc.player == null) {
            if (ClientEventState.isActive()) ClientEventState.clear();
            return;
        }

        if (KeyBindings.OPEN_CHARACTER_SHEET.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new CharacterSheetScreen());
            }
        }
    }
}
