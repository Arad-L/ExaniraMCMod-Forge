package com.exanira.network;

import com.exanira.event.EventQueueManager;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server.
 * Sent when the player clicks "Accept" on a specific invite in the Radio Menu Party tab.
 * The server validates and calls {@link EventQueueManager#joinEvent}.
 */
public class AcceptInvitePacket {

    private final String instanceKey;

    public AcceptInvitePacket(String instanceKey) {
        this.instanceKey = instanceKey;
    }

    public static void encode(AcceptInvitePacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.instanceKey, 128);
    }

    public static AcceptInvitePacket decode(FriendlyByteBuf buf) {
        return new AcceptInvitePacket(buf.readUtf(128));
    }

    public static void handle(AcceptInvitePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            // Clear any pending invite on the server side for this player
            EventQueueManager.INSTANCE.removePendingInvitation(player.getUUID());

            EventQueueManager.JoinResult result = EventQueueManager.INSTANCE.joinEvent(pkt.instanceKey, player);
            if (result == EventQueueManager.JoinResult.PREREQ_FAILED) {
                // joinEvent() already sent the prerequisite-failure message — nothing more to do
                return;
            }
            if (result != EventQueueManager.JoinResult.SUCCESS) {
                player.sendMessage(
                        new TextComponent("Could not join — the event may have moved past the first scene or ended.")
                                .withStyle(ChatFormatting.RED),
                        Util.NIL_UUID);
            }
        });
        ctx.setPacketHandled(true);
    }
}
