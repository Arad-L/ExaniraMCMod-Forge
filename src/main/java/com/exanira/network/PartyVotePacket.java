package com.exanira.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server (legacy / party vote path).
 * The server uses EventChoicePacket for both solo and party votes; this packet is
 * kept for forward compatibility and completeness.
 */
public record PartyVotePacket(String instanceKey, int choiceIndex) {

    public static void encode(PartyVotePacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.instanceKey());
        buf.writeVarInt(pkt.choiceIndex());
    }

    public static PartyVotePacket decode(FriendlyByteBuf buf) {
        return new PartyVotePacket(buf.readUtf(), buf.readVarInt());
    }

    public static void handle(PartyVotePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        // Delegate to the unified choice handler
        ctxSupplier.get().enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer player = ctxSupplier.get().getSender();
            if (player == null) return;
            com.exanira.event.EventQueueManager.INSTANCE.resolveChoice(
                    player.getUUID(), pkt.instanceKey(), pkt.choiceIndex(), player);
        });
        ctxSupplier.get().setPacketHandled(true);
    }
}
