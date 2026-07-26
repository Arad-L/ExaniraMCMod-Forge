package com.exanira.network;

import com.exanira.character.CompletedSideEventRecord;
import com.exanira.character.Stat;
import com.exanira.client.ClientCharacterData;
import com.exanira.event.EventDefinition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

/** Server -> Client. Synchronises the player's resolved CharacterSheet plus event registry. */
public class CharacterSheetSyncPacket {

    /** Compact event metadata sent to the client for Radio Menu display. */
    public record EventSummary(String id, String type, int season, int order, List<String> unlockRequires) {}

    private final Map<Stat, Integer> stats;
    private final String backstory;
    private final int currentSeason;
    private final List<String> completedMainEvents;
    private final List<String> witnessedMainEvents;
    private final List<CompletedSideEventRecord> completedSideEvents;
    private final List<EventSummary> eventSummaries;

    public CharacterSheetSyncPacket(com.exanira.character.CharacterSheet sheet,
                                    Collection<EventDefinition> definitions) {
        Map<Stat, Integer> map = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) map.put(s, sheet.getStat(s));
        this.stats = map;
        this.backstory = sheet.getBackstory();
        this.currentSeason = sheet.getCurrentSeason();
        this.completedMainEvents = List.copyOf(sheet.getCompletedMainEvents());
        this.witnessedMainEvents = List.copyOf(sheet.getWitnessedMainEvents());
        this.completedSideEvents = List.copyOf(sheet.getCompletedSideEvents());
        List<EventSummary> summaries = new ArrayList<>();
        for (EventDefinition def : definitions) {
            summaries.add(new EventSummary(
                    def.id(), def.type().name(), def.season(), def.order(),
                    List.copyOf(def.unlockRequires())
            ));
        }
        this.eventSummaries = List.copyOf(summaries);
    }

    private CharacterSheetSyncPacket(Map<Stat, Integer> stats, String backstory,
                                     int currentSeason,
                                     List<String> completedMainEvents,
                                     List<String> witnessedMainEvents,
                                     List<CompletedSideEventRecord> completedSideEvents,
                                     List<EventSummary> eventSummaries) {
        this.stats = stats;
        this.backstory = backstory;
        this.currentSeason = currentSeason;
        this.completedMainEvents = completedMainEvents;
        this.witnessedMainEvents = witnessedMainEvents;
        this.completedSideEvents = completedSideEvents;
        this.eventSummaries = eventSummaries;
    }

    public Map<Stat, Integer> stats()                           { return stats; }
    public String backstory()                                   { return backstory; }
    public int currentSeason()                                  { return currentSeason; }
    public List<String> completedMainEvents()                   { return completedMainEvents; }
    public List<String> witnessedMainEvents()                   { return witnessedMainEvents; }
    public List<CompletedSideEventRecord> completedSideEvents() { return completedSideEvents; }
    public List<EventSummary> eventSummaries()                  { return eventSummaries; }

    public static void encode(CharacterSheetSyncPacket pkt, FriendlyByteBuf buf) {
        for (Stat s : Stat.values()) buf.writeVarInt(pkt.stats.getOrDefault(s, 1));
        buf.writeUtf(pkt.backstory);
        buf.writeVarInt(pkt.currentSeason);
        buf.writeVarInt(pkt.completedMainEvents.size());
        for (String id : pkt.completedMainEvents) buf.writeUtf(id);
        buf.writeVarInt(pkt.witnessedMainEvents.size());
        for (String id : pkt.witnessedMainEvents) buf.writeUtf(id);
        buf.writeVarInt(pkt.completedSideEvents.size());
        for (CompletedSideEventRecord r : pkt.completedSideEvents) {
            buf.writeUtf(r.eventId());
            buf.writeVarInt(r.season());
            buf.writeVarInt(r.starRating());
            buf.writeLong(r.completedAt());
        }
        buf.writeVarInt(pkt.eventSummaries.size());
        for (EventSummary s : pkt.eventSummaries) {
            buf.writeUtf(s.id());
            buf.writeUtf(s.type());
            buf.writeVarInt(s.season());
            buf.writeVarInt(s.order());
            buf.writeVarInt(s.unlockRequires().size());
            for (String req : s.unlockRequires()) buf.writeUtf(req);
        }
    }

    public static CharacterSheetSyncPacket decode(FriendlyByteBuf buf) {
        Map<Stat, Integer> stats = new EnumMap<>(Stat.class);
        for (Stat s : Stat.values()) stats.put(s, buf.readVarInt());
        String backstory = buf.readUtf();
        int currentSeason = buf.readVarInt();
        int cmeSize = buf.readVarInt();
        List<String> completedMain = new ArrayList<>(cmeSize);
        for (int i = 0; i < cmeSize; i++) completedMain.add(buf.readUtf());
        int wmeSize = buf.readVarInt();
        List<String> witnessedMain = new ArrayList<>(wmeSize);
        for (int i = 0; i < wmeSize; i++) witnessedMain.add(buf.readUtf());
        int cseSize = buf.readVarInt();
        List<CompletedSideEventRecord> sideEvents = new ArrayList<>(cseSize);
        for (int i = 0; i < cseSize; i++) {
            sideEvents.add(new CompletedSideEventRecord(
                    buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readLong()));
        }
        int esSize = buf.readVarInt();
        List<EventSummary> summaries = new ArrayList<>(esSize);
        for (int i = 0; i < esSize; i++) {
            String id = buf.readUtf();
            String type = buf.readUtf();
            int season = buf.readVarInt();
            int order = buf.readVarInt();
            int reqSize = buf.readVarInt();
            List<String> reqs = new ArrayList<>(reqSize);
            for (int j = 0; j < reqSize; j++) reqs.add(buf.readUtf());
            summaries.add(new EventSummary(id, type, season, order, reqs));
        }
        return new CharacterSheetSyncPacket(stats, backstory, currentSeason,
                completedMain, witnessedMain, sideEvents, summaries);
    }

    public static void handle(CharacterSheetSyncPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        ctxSupplier.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientCharacterData.update(pkt.stats(), pkt.backstory(),
                                pkt.currentSeason(), pkt.completedMainEvents(),
                                pkt.witnessedMainEvents(), pkt.completedSideEvents(),
                                pkt.eventSummaries())
                )
        );
        ctxSupplier.get().setPacketHandled(true);
    }
}