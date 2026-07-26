package com.exanira.network;

import com.exanira.client.ClientEventState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server → Client.
 * Sends a snapshot of the player's current party state for display in the Radio Menu Party tab.
 * Sent in response to {@link RequestPartyStatusPacket} and on vote/disconnect updates.
 */
public class PartyStatusPacket {

    public enum VoteStatus { VOTED, PENDING, OFFLINE }

    public record MemberStatus(String name, UUID uuid, VoteStatus voteStatus, boolean isOnline) {}

    private final List<MemberStatus> members;
    /** Seconds remaining on the abandonment timer, or -1 if no timer is active. */
    private final int timerRemainingSeconds;
    /** The instance key for this party; empty string if the player is not in an event. */
    private final String instanceKey;
    /** True when the current scene has choices and a vote is in progress. */
    private final boolean voteInProgress;

    public PartyStatusPacket(List<MemberStatus> members, int timerRemainingSeconds, String instanceKey, boolean voteInProgress) {
        this.members               = members;
        this.timerRemainingSeconds = timerRemainingSeconds;
        this.instanceKey           = instanceKey;
        this.voteInProgress        = voteInProgress;
    }

    public List<MemberStatus> members()           { return members; }
    public int timerRemainingSeconds()             { return timerRemainingSeconds; }
    public String instanceKey()                    { return instanceKey; }
    public boolean voteInProgress()                { return voteInProgress; }

    public static void encode(PartyStatusPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.instanceKey);
        buf.writeVarInt(pkt.timerRemainingSeconds);
        buf.writeBoolean(pkt.voteInProgress);
        buf.writeVarInt(pkt.members.size());
        for (MemberStatus m : pkt.members) {
            buf.writeUtf(m.name());
            buf.writeUUID(m.uuid());
            buf.writeVarInt(m.voteStatus().ordinal());
            buf.writeBoolean(m.isOnline());
        }
    }

    public static PartyStatusPacket decode(FriendlyByteBuf buf) {
        String instanceKey          = buf.readUtf();
        int timerRemaining          = buf.readVarInt();
        boolean voteInProgress      = buf.readBoolean();
        int size                    = buf.readVarInt();
        List<MemberStatus> members  = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String name        = buf.readUtf();
            UUID uuid          = buf.readUUID();
            VoteStatus status  = VoteStatus.values()[buf.readVarInt()];
            boolean online     = buf.readBoolean();
            members.add(new MemberStatus(name, uuid, status, online));
        }
        return new PartyStatusPacket(members, timerRemaining, instanceKey, voteInProgress);
    }

    public static void handle(PartyStatusPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        ctxSupplier.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientEventState.updatePartyStatus(pkt)
                )
        );
        ctxSupplier.get().setPacketHandled(true);
    }
}
