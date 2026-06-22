package com.exanira.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client.
 * Zero-payload marker that tells the client to open the character creation screen.
 */
public class OpenCharacterCreationPacket {

    public OpenCharacterCreationPacket() {}

    public static void encode(OpenCharacterCreationPacket pkt, FriendlyByteBuf buf) {
        // no payload
    }

    public static OpenCharacterCreationPacket decode(FriendlyByteBuf buf) {
        return new OpenCharacterCreationPacket();
    }

    public static void handle(OpenCharacterCreationPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        ctxSupplier.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        net.minecraft.client.Minecraft.getInstance()
                                .setScreen(new com.exanira.client.CharacterCreationScreen())
                )
        );
        ctxSupplier.get().setPacketHandled(true);
    }
}
