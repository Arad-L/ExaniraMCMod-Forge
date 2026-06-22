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
 * Updates all party members with the current vote tally after any participant votes.
 */
public class PartyVoteStatePacket {

    public record VoteData(boolean voted, int choiceIndex) {}

    private final String instanceKey;
    private final List<VoteData> voteData;
    private final int localChoiceIndex;

    public PartyVoteStatePacket(String instanceKey, List<VoteData> voteData, int localChoiceIndex) {
        this.instanceKey      = instanceKey;
        this.voteData         = voteData;
        this.localChoiceIndex = localChoiceIndex;
    }

    public String instanceKey()    { return instanceKey; }
    public List<VoteData> voteData() { return voteData; }
    public int localChoiceIndex()  { return localChoiceIndex; }

    public static void encode(PartyVoteStatePacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.instanceKey());
        buf.writeVarInt(pkt.voteData().size());
        for (VoteData vd : pkt.voteData()) {
            buf.writeBoolean(vd.voted());
            buf.writeVarInt(vd.choiceIndex());
        }
        buf.writeVarInt(pkt.localChoiceIndex());
    }

    public static PartyVoteStatePacket decode(FriendlyByteBuf buf) {
        String key  = buf.readUtf();
        int size    = buf.readVarInt();
        List<VoteData> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            boolean voted    = buf.readBoolean();
            int choiceIdx    = buf.readVarInt();
            list.add(new VoteData(voted, choiceIdx));
        }
        int localChoice = buf.readVarInt();
        return new PartyVoteStatePacket(key, list, localChoice);
    }

    public static void handle(PartyVoteStatePacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        ctxSupplier.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    ClientEventState.updateVoteData(
                            pkt.instanceKey(), pkt.voteData(), pkt.localChoiceIndex());
                    ClientEventState.notifyVoteDataChanged();
                })
        );
        ctxSupplier.get().setPacketHandled(true);
    }
}
