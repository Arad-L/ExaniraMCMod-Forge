package com.exanira.network;

import com.exanira.client.ClientEventState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → Client.
 * Delivers dialogue lines and available choices for the current event scene.
 */
public class EventStartPacket {

    public record ChoiceData(String text, boolean available, String lockedText, String requirementText) {}

    private final String instanceKey;
    private final List<String> dialogue;
    private final List<ChoiceData> choices;

    public EventStartPacket(String instanceKey, List<String> dialogue, List<ChoiceData> choices) {
        this.instanceKey = instanceKey;
        this.dialogue = dialogue;
        this.choices = choices;
    }

    public String instanceKey()      { return instanceKey; }
    public List<String> dialogue()   { return dialogue; }
    public List<ChoiceData> choices() { return choices; }

    public static void encode(EventStartPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.instanceKey());
        buf.writeVarInt(pkt.dialogue().size());
        for (String line : pkt.dialogue()) buf.writeUtf(line);
        buf.writeVarInt(pkt.choices().size());
        for (ChoiceData cd : pkt.choices()) {
            buf.writeUtf(cd.text());
            buf.writeBoolean(cd.available());
            buf.writeUtf(cd.lockedText()      != null ? cd.lockedText()      : "");
            buf.writeUtf(cd.requirementText() != null ? cd.requirementText() : "");
        }
    }

    public static EventStartPacket decode(FriendlyByteBuf buf) {
        String key = buf.readUtf();
        int dSize = buf.readVarInt();
        List<String> dialogue = new ArrayList<>(dSize);
        for (int i = 0; i < dSize; i++) dialogue.add(buf.readUtf());
        int cSize = buf.readVarInt();
        List<ChoiceData> choices = new ArrayList<>(cSize);
        for (int i = 0; i < cSize; i++) {
            String text        = buf.readUtf();
            boolean unlocked   = buf.readBoolean();
            String lockedText  = buf.readUtf();
            String reqText     = buf.readUtf();
            choices.add(new ChoiceData(
                    text, unlocked,
                    lockedText.isEmpty() ? null : lockedText,
                    reqText.isEmpty()    ? null : reqText
            ));
        }
        return new EventStartPacket(key, dialogue, choices);
    }

    public static void handle(EventStartPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        ctxSupplier.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    ClientEventState.startEvent(pkt.instanceKey(), pkt.dialogue(), pkt.choices());
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc.screen instanceof com.exanira.client.EventScreen eventScreen) {
                        eventScreen.refresh();
                    }
                })
        );
        ctxSupplier.get().setPacketHandled(true);
    }
}
