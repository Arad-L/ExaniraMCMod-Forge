package com.exanira;

import com.exanira.command.ExaniraCommands;
import com.exanira.event.EventLoader;
import com.exanira.event.EventQueueManager;
import com.exanira.handlers.CharacterCreationHandler;
import com.exanira.handlers.EventChoiceHandler;
import com.exanira.handlers.PlayerCapabilityHandler;
import com.exanira.handlers.PlayerLoginHandler;
import com.exanira.handlers.PlayerLogoutHandler;
import com.exanira.item.ExaniraItems;
import com.exanira.network.CharacterCreationSubmitPacket;
import com.exanira.network.CharacterSheetSyncPacket;
import com.exanira.network.EventChoicePacket;
import com.exanira.network.EventEndPacket;
import com.exanira.network.EventStartPacket;
import com.exanira.network.OpenCharacterCreationPacket;
import com.exanira.network.PartyVotePacket;
import com.exanira.network.PartyVoteStatePacket;

// ==========================================
// NEOFORGE 1.21.1 IMPORTS (COMMENTED OUT)
// ==========================================
// import net.neoforged.api.distmarker.Dist;
// import net.neoforged.bus.api.IEventBus;
// import net.neoforged.fml.common.Mod;
// import net.neoforged.fml.loading.FMLEnvironment;
// import net.neoforged.neoforge.common.NeoForge;
// import net.neoforged.neoforge.event.AddReloadListenerEvent;
// import net.neoforged.neoforge.event.RegisterCommandsEvent;
// import net.neoforged.neoforge.event.server.ServerStoppedEvent;
// import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// ==========================================
// FORGE 1.18.2 IMPORTS (NEW)
// ==========================================
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import java.util.Optional;
// ==========================================

@Mod(ExaniraMod.MODID)
public class ExaniraMod {

    public static final String MODID = "exanira";

    // ==========================================
    // FORGE 1.18.2 NETWORKING (NEW)
    // ==========================================
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    // ==========================================

    public ExaniraMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ExaniraItems.ITEMS.register(modEventBus);

        // 1.21.1: NeoForge.EVENT_BUS.register(PlayerLoginHandler.class);
MinecraftForge.EVENT_BUS.register(PlayerLoginHandler.class); // FORGE 1.18.2 REPLACEMENT
        MinecraftForge.EVENT_BUS.register(PlayerLogoutHandler.class);
        MinecraftForge.EVENT_BUS.register(PlayerCapabilityHandler.class);

        // 1.21.1: modEventBus.addListener(ExaniraMod::onRegisterPayloadHandlers);
        modEventBus.addListener(this::commonSetup); // FORGE 1.18.2 REPLACEMENT (For packet registration)

        // 1.21.1: NeoForge.EVENT_BUS.addListener(ExaniraMod::onAddReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(ExaniraMod::onAddReloadListeners); // FORGE 1.18.2 REPLACEMENT

        // 1.21.1: NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> ...
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) ->
                ExaniraCommands.register(e.getDispatcher())
        ); // FORGE 1.18.2 REPLACEMENT

// SAFE shutdown handling (FIXED)
        // 1.21.1: NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> { // FORGE 1.18.2 REPLACEMENT
            if (e.getServer() != null) {
                EventQueueManager.INSTANCE.shutdownAll(e.getServer());
            } else {
                // fallback safety wipe (should rarely happen)
                EventQueueManager.INSTANCE.clear();
            }
        });
        
// Add server start handler to restore events from save data
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStartedEvent e) -> {
            if (e.getServer() != null) {
                EventQueueManager.INSTANCE.restoreEventsFromSave();
            }
        });

        // Tick handler: checks abandonment grace-period timers once per second.
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                EventQueueManager.INSTANCE.checkAllEventsForExpiredAbandonments();
            }
        });

        // Client-only init
        if (FMLEnvironment.dist == Dist.CLIENT) {
            new ExaniraModClient(modEventBus);
        }
    }

    // ==========================================
    // FORGE 1.18.2 NETWORKING REGISTER (NEW)
    // ==========================================
    private void commonSetup(final FMLCommonSetupEvent event) {
        int id = 0;
        
        // You will need to adapt your packets to use standard 1.18.2 decoder/encoder/consumer setups
        CHANNEL.registerMessage(id++, 
                CharacterCreationSubmitPacket.class, 
                CharacterCreationSubmitPacket::encode, 
                CharacterCreationSubmitPacket::decode, 
                CharacterCreationHandler::handle
        );

        CHANNEL.registerMessage(id++,
                EventChoicePacket.class,
                EventChoicePacket::encode,
                EventChoicePacket::decode,
                EventChoiceHandler::handle
        );

        CHANNEL.registerMessage(id++,
                PartyVotePacket.class,
                PartyVotePacket::encode,
                PartyVotePacket::decode,
                PartyVotePacket::handle
        );

        // Server → Client packets
        CHANNEL.registerMessage(id++,
                OpenCharacterCreationPacket.class,
                OpenCharacterCreationPacket::encode,
                OpenCharacterCreationPacket::decode,
                OpenCharacterCreationPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        CHANNEL.registerMessage(id++,
                CharacterSheetSyncPacket.class,
                CharacterSheetSyncPacket::encode,
                CharacterSheetSyncPacket::decode,
                CharacterSheetSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        CHANNEL.registerMessage(id++,
                EventStartPacket.class,
                EventStartPacket::encode,
                EventStartPacket::decode,
                EventStartPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        CHANNEL.registerMessage(id++,
                EventEndPacket.class,
                EventEndPacket::encode,
                EventEndPacket::decode,
                EventEndPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
        CHANNEL.registerMessage(id++,
                PartyVoteStatePacket.class,
                PartyVoteStatePacket::encode,
                PartyVoteStatePacket::decode,
                PartyVoteStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }
    // ==========================================

    // ==========================================
    // NEOFORGE 1.21.1 PAYLOAD METHOD (COMMENTED OUT)
    // ==========================================
    // private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
    //     var registrar = event.registrar("1");
    //
    //     registrar.playToServer(
    //             CharacterCreationSubmitPacket.TYPE,
    //             CharacterCreationSubmitPacket.STREAM_CODEC,
    //             CharacterCreationHandler::handle
    //     );
    //
    //     registrar.playToServer(
    //             EventChoicePacket.TYPE,
    //             EventChoicePacket.STREAM_CODEC,
    //             EventChoiceHandler::handle
    //     );
    // }
    // ==========================================

    private static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new EventLoader());
    }
}