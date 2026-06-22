package com.exanira.handlers;

import com.exanira.ExaniraMod;
import com.exanira.character.CharacterSheetCapability;
import com.exanira.item.ExaniraItems;
import com.exanira.network.CharacterCreationSubmitPacket;
import com.exanira.network.CharacterSheetSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Server-side handler for CharacterCreationSubmitPacket (client → server). */
public final class CharacterCreationHandler {

    private CharacterCreationHandler() {}

    public static void handle(CharacterCreationSubmitPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            CharacterSheetCapability.get(player).ifPresent(sheet -> {
                // Guard against duplicate submissions (e.g. packet replay)
                if (sheet.isInitialized()) return;

                sheet.initialize(packet.professionOrdinal(), packet.lifestyleChoices());

                // Give the radio — first item the player receives, immediately after creation
                ExaniraItems.ensureRadio(player);

                // Sync the resolved sheet back so the client can display it
                ExaniraMod.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new CharacterSheetSyncPacket(sheet)
                );
            });
        });
        ctx.setPacketHandled(true);
    }
}
