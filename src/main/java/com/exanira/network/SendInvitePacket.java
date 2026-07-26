package com.exanira.network;

import com.exanira.event.EventQueueManager;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server.
 * Sent when the player uses the Invite input in the Radio Menu Party tab.
 * Server validates and processes the invite, then sends {@link InviteNotificationPacket}
 * to the target player.
 */
public class SendInvitePacket {

    private final String inviteeName;

    public SendInvitePacket(String inviteeName) {
        this.inviteeName = inviteeName;
    }

    public static void encode(SendInvitePacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.inviteeName, 64);
    }

    public static SendInvitePacket decode(FriendlyByteBuf buf) {
        return new SendInvitePacket(buf.readUtf(64));
    }

    public static void handle(SendInvitePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;

            MinecraftServer server = sender.getServer();
            if (server == null) return;

            ServerPlayer invitee = server.getPlayerList().getPlayerByName(pkt.inviteeName);
            if (invitee == null) {
                sender.sendMessage(
                        new TextComponent("'" + pkt.inviteeName + "' is not online.")
                                .withStyle(ChatFormatting.RED),
                        Util.NIL_UUID);
                return;
            }

            EventQueueManager.INSTANCE.processInvite(sender, invitee);
        });
        ctx.setPacketHandled(true);
    }
}
