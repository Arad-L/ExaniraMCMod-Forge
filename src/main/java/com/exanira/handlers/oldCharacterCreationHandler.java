package com.exanira.handlers;

import com.exanira.character.CharacterAttachment;
import com.exanira.character.CharacterSheet;
import com.exanira.item.ExaniraItems;
import com.exanira.network.CharacterCreationSubmitPacket;
import com.exanira.network.CharacterSheetSyncPacket;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side handler for CharacterCreationSubmitPacket.
 * Registered in ExaniraMod via RegisterPayloadHandlersEvent (playToServer).
 */
public final class CharacterCreationHandler {

    private CharacterCreationHandler() {}

    public static void handle(CharacterCreationSubmitPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            CharacterSheet sheet = player.getData(CharacterAttachment.CHARACTER_SHEET.get());

            // Guard against duplicate submissions (e.g. packet replay)
            if (sheet.isInitialized()) return;

            sheet.initialize(packet.professionOrdinal(), packet.lifestyleChoices());

            // Give the radio — first item they receive, immediately after creation
            ExaniraItems.ensureRadio(player);

            // Sync the resolved sheet back to the client so the GUI can display it
            PacketDistributor.sendToPlayer(player, new CharacterSheetSyncPacket(sheet));
        });
    }
}
