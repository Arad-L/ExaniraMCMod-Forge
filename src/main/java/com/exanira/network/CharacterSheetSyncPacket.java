package com.exanira.network;

import com.exanira.character.Stat;
import com.exanira.client.ClientCharacterData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/** Server -> Client. Synchronises the player's resolved CharacterSheet. */
public class CharacterSheetSyncPacket {

    private final Map<Stat, Integer> stats;
    private final String backstory;

    public CharacterSheetSyncPacket(com.exanira.character.CharacterSheet sheet) {
        Map<Stat, Integer> map = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) map.put(s, sheet.getStat(s));
        this.stats    = map;
        this.backstory = sheet.getBackstory();
    }

    private CharacterSheetSyncPacket(Map<Stat, Integer> stats, String backstory) {
        this.stats    = stats;
        this.backstory = backstory;
    }

    /** Record-style accessors matching the original 1.21.1 API. */
    public Map<Stat, Integer> stats()  { return stats; }
    public String backstory()          { return backstory; }

    public static void encode(CharacterSheetSyncPacket pkt, FriendlyByteBuf buf) {
        for (Stat s : Stat.values()) buf.writeVarInt(pkt.stats.getOrDefault(s, 1));
        buf.writeUtf(pkt.backstory);
    }

    public static CharacterSheetSyncPacket decode(FriendlyByteBuf buf) {
        Map<Stat, Integer> stats = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) stats.put(s, buf.readVarInt());
        String backstory = buf.readUtf();
        return new CharacterSheetSyncPacket(stats, backstory);
    }

    public static void handle(CharacterSheetSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        ctxSupplier.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientCharacterData.update(pkt.stats(), pkt.backstory())
                )
        );
        ctxSupplier.get().setPacketHandled(true);
    }
}