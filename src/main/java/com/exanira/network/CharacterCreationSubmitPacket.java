package com.exanira.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Sent by the client when the player finishes the character creation form. */
public record CharacterCreationSubmitPacket(int professionOrdinal, List<Integer> lifestyleChoices) {

    public static void encode(CharacterCreationSubmitPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.professionOrdinal());
        buf.writeVarInt(pkt.lifestyleChoices().size());
        for (int choice : pkt.lifestyleChoices()) {
            buf.writeVarInt(choice);
        }
    }

    public static CharacterCreationSubmitPacket decode(FriendlyByteBuf buf) {
        int profOrd = buf.readVarInt();
        int size = buf.readVarInt();
        List<Integer> choices = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            choices.add(buf.readVarInt());
        }
        return new CharacterCreationSubmitPacket(profOrd, List.copyOf(choices));
    }
}
