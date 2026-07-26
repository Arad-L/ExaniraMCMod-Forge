package com.exanira.network;

import com.exanira.ExaniraMod;
import com.exanira.event.ActiveEvent;
import com.exanira.event.EventQueueManager;
import com.exanira.event.EventScene;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → Server. Zero-payload request for the current party status.
 * Sent when the player opens the Party tab in the Radio Menu.
 * Server responds with a {@link PartyStatusPacket}.
 */
public class RequestPartyStatusPacket {

    public static void encode(RequestPartyStatusPacket pkt, FriendlyByteBuf buf) {}

    public static RequestPartyStatusPacket decode(FriendlyByteBuf buf) {
        return new RequestPartyStatusPacket();
    }

    public static void handle(RequestPartyStatusPacket pkt, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            PartyStatusPacket response = buildPartyStatus(player);
            ExaniraMod.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), response);
        });
        ctx.setPacketHandled(true);
    }

    /** Builds a PartyStatusPacket snapshot for the requesting player. */
    static PartyStatusPacket buildPartyStatus(ServerPlayer player) {
        Optional<String> instanceKeyOpt = EventQueueManager.INSTANCE.getPlayerEventKey(player.getUUID());
        if (instanceKeyOpt.isEmpty()) {
            return new PartyStatusPacket(List.of(), -1, "", false);
        }

        String instanceKey = instanceKeyOpt.get();
        Optional<ActiveEvent> activeOpt = EventQueueManager.INSTANCE.getActiveEvent(instanceKey);
        if (activeOpt.isEmpty()) {
            return new PartyStatusPacket(List.of(), -1, "", false);
        }

        ActiveEvent active = activeOpt.get();
        MinecraftServer server = player.getServer();

        List<PartyStatusPacket.MemberStatus> members = new ArrayList<>();
        for (UUID participantId : active.participants()) {
            ServerPlayer p = server != null ? server.getPlayerList().getPlayer(participantId) : null;
            boolean disconnected = active.disconnectedParticipants().contains(participantId);
            boolean online = p != null && !disconnected;

            PartyStatusPacket.VoteStatus status;
            if (!online) {
                status = PartyStatusPacket.VoteStatus.OFFLINE;
            } else if (active.hasVoted(participantId)) {
                status = PartyStatusPacket.VoteStatus.VOTED;
            } else {
                status = PartyStatusPacket.VoteStatus.PENDING;
            }

            String name;
            if (p != null) {
                name = p.getName().getString();
            } else if (server != null) {
                name = server.getProfileCache().get(participantId)
                        .map(profile -> profile.getName())
                        .orElse(participantId.toString().substring(0, 8));
            } else {
                name = participantId.toString().substring(0, 8);
            }

            members.add(new PartyStatusPacket.MemberStatus(name, participantId, status, online));
        }

        int timerSeconds = active.getTimerRemainingSeconds();
        EventScene currentScene = active.currentScene();
        boolean voteInProgress = currentScene != null && !currentScene.choices().isEmpty();
        return new PartyStatusPacket(members, timerSeconds, instanceKey, voteInProgress);
    }
}
