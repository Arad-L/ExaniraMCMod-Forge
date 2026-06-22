package com.exanira.network;

import com.exanira.client.ClientEventState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → Client. Signals that the named event instance has ended. */
public class EventEndPacket {

    private final String instanceKey;

    public EventEndPacket(String instanceKey) {
        this.instanceKey = instanceKey;
    }

    public String instanceKey() { return instanceKey; }

    public static void encode(EventEndPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.instanceKey());
    }

    public static EventEndPacket decode(FriendlyByteBuf buf) {
        return new EventEndPacket(buf.readUtf());
    }

    public static void handle(EventEndPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        ctxSupplier.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    ClientEventState.endEvent(pkt.instanceKey());
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.screen instanceof com.exanira.client.EventScreen) mc.setScreen(null);
                })
        );
        ctxSupplier.get().setPacketHandled(true);
    }
}
