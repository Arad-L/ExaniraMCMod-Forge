package com.exanira.handlers;

import com.exanira.event.EventQueueManager;
import com.exanira.network.EventChoicePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-side handler for EventChoicePacket (client → server). */
public final class EventChoiceHandler {

    private EventChoiceHandler() {}

    public static void handle(EventChoicePacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            EventQueueManager.INSTANCE.resolveChoice(
                    player.getUUID(), packet.instanceKey(), packet.choiceIndex(), player
            );
        });
        ctx.setPacketHandled(true);
    }
}
