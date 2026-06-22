package com.exanira;

import com.exanira.client.ClientEventHandler;
import com.exanira.client.KeyBindings;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only init. Instantiated from ExaniraMod only when dist == CLIENT.
 * Ported from 1.21.1: RegisterKeyMappingsEvent -> ClientRegistry (Forge 1.18.2).
 * Disconnect cleanup is handled in ClientEventHandler via @SubscribeEvent.
 */
@OnlyIn(Dist.CLIENT)
public class ExaniraModClient {

    public ExaniraModClient(IEventBus modEventBus) {
        MinecraftForge.EVENT_BUS.register(ClientEventHandler.class);
        modEventBus.addListener(this::clientSetup);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                ClientRegistry.registerKeyBinding(KeyBindings.OPEN_CHARACTER_SHEET));
    }
}