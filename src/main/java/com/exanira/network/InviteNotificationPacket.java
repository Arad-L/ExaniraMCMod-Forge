package com.exanira.network;

import com.exanira.client.ClientEventState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client.
 * Delivered to the invitee when they receive a party invite.
 * The client stores it in {@link ClientEventState} and displays it in the Radio Menu Party tab.
 */
public class InviteNotificationPacket {

    private final String inviterName;
    private final String instanceKey;
    private final String eventId;

    public InviteNotificationPacket(String inviterName, String instanceKey, String eventId) {
        this.inviterName = inviterName;
        this.instanceKey = instanceKey;
        this.eventId     = eventId;
    }

    public String inviterName() { return inviterName; }
    public String instanceKey() { return instanceKey; }
    public String eventId()     { return eventId; }

    public static void encode(InviteNotificationPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.inviterName, 32);
        buf.writeUtf(pkt.instanceKey, 128);
        buf.writeUtf(pkt.eventId, 128);
    }

    public static InviteNotificationPacket decode(FriendlyByteBuf buf) {
        return new InviteNotificationPacket(
                buf.readUtf(32),
                buf.readUtf(128),
                buf.readUtf(128)
        );
    }

    public static void handle(InviteNotificationPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        ctxSupplier.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientEventState.addPendingInvite(
                                new ClientEventState.PendingInvite(
                                        pkt.inviterName, pkt.instanceKey, pkt.eventId))
                )
        );
        ctxSupplier.get().setPacketHandled(true);
    }
}
