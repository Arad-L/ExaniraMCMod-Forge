package com.exanira.network;

import net.minecraft.network.FriendlyByteBuf;

/** Sent by the client when the player selects a choice inside an active event. */
public record EventChoicePacket(String instanceKey, int choiceIndex) {

    public static void encode(EventChoicePacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.instanceKey());
        buf.writeVarInt(pkt.choiceIndex());
    }

    public static EventChoicePacket decode(FriendlyByteBuf buf) {
        String key = buf.readUtf();
        int choice = buf.readVarInt();
        return new EventChoicePacket(key, choice);
    }
}
