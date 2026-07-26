package com.exanira.client;

import com.exanira.network.EventStartPacket;
import com.exanira.network.PartyStatusPacket;
import com.exanira.network.PartyVoteStatePacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class ClientEventState {

    /** Immutable record representing an incoming party invite. */
    public record PendingInvite(String inviterName, String instanceKey, String eventId) {}

    private static String activeInstanceKey = null;
    private static List<String> activeDialogue = List.of();
    private static List<EventStartPacket.ChoiceData> choices = List.of();
    private static List<PartyVoteStatePacket.VoteData> voteData = List.of();
    private static int localChoiceIndex = -1;
    private static EventScreen currentEventScreen = null;
    /** Cached from the most recent PartyStatusPacket. Null if never received. */
    private static PartyStatusPacket lastPartyStatus = null;
    /** Pending party invites displayed in the Radio Menu Party tab. */
    private static final List<PendingInvite> pendingInvites = new ArrayList<>();

    private ClientEventState() {}

    public static boolean isActive()                                 { return activeInstanceKey != null; }
    public static String getInstanceKey()                            { return activeInstanceKey; }
    public static List<String> getDialogue()                         { return activeDialogue; }
    public static List<EventStartPacket.ChoiceData> getChoices()     { return choices; }
    public static List<PartyVoteStatePacket.VoteData> getVoteData()  { return voteData; }
    public static int getLocalChoiceIndex()                          { return localChoiceIndex; }
    public static PartyStatusPacket getLastPartyStatus()             { return lastPartyStatus; }
    public static List<PendingInvite> getPendingInvites()            { return Collections.unmodifiableList(pendingInvites); }

    public static void addPendingInvite(PendingInvite invite) {
        // Deduplicate by instanceKey
        pendingInvites.removeIf(i -> i.instanceKey().equals(invite.instanceKey()));
        pendingInvites.add(invite);
    }

    public static void removePendingInvite(String instanceKey) {
        pendingInvites.removeIf(i -> i.instanceKey().equals(instanceKey));
    }

    public static void startEvent(String instanceKey, List<String> dialogue,
                                   List<EventStartPacket.ChoiceData> c) {
        activeInstanceKey = instanceKey;
        activeDialogue    = List.copyOf(dialogue);
        choices           = List.copyOf(c);
        voteData          = List.of();
        localChoiceIndex  = -1;
    }

    public static void endEvent(String instanceKey) {
        if (instanceKey.equals(activeInstanceKey)) {
            activeInstanceKey  = null;
            activeDialogue     = List.of();
            choices            = List.of();
            voteData           = List.of();
            localChoiceIndex   = -1;
            currentEventScreen = null;
        }
    }

    public static void clear() {
        activeInstanceKey  = null;
        activeDialogue     = List.of();
        choices            = List.of();
        voteData           = List.of();
        localChoiceIndex   = -1;
        currentEventScreen = null;
        lastPartyStatus    = null;
        pendingInvites.clear();
    }

    public static void updateVoteData(String instanceKey,
                                       List<PartyVoteStatePacket.VoteData> newVoteData,
                                       int newLocalChoiceIndex) {
        if (instanceKey.equals(activeInstanceKey)) {
            voteData         = newVoteData;
            localChoiceIndex = newLocalChoiceIndex;
        }
    }

    public static void updatePartyStatus(PartyStatusPacket packet) {
        lastPartyStatus = packet;
        // If a RadioMenuScreen is open, notify it to re-render the Party tab
        // (handled by the screen polling lastPartyStatus on each render tick)
    }

    public static void setCurrentEventScreen(EventScreen screen) {
        currentEventScreen = screen;
    }

    public static void notifyVoteDataChanged() {
        if (currentEventScreen != null) {
            currentEventScreen.updateVoteCounts();
        }
    }
}
