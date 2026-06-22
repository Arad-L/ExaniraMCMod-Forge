package com.exanira.client;

import com.exanira.network.EventStartPacket;
import com.exanira.network.PartyVoteStatePacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class ClientEventState {

    private static String activeInstanceKey = null;
    private static List<String> activeDialogue = List.of();
    private static List<EventStartPacket.ChoiceData> choices = List.of();
    private static List<PartyVoteStatePacket.VoteData> voteData = List.of();
    private static int localChoiceIndex = -1;
    private static EventScreen currentEventScreen = null;

    private ClientEventState() {}

    public static boolean isActive()                                 { return activeInstanceKey != null; }
    public static String getInstanceKey()                            { return activeInstanceKey; }
    public static List<String> getDialogue()                         { return activeDialogue; }
    public static List<EventStartPacket.ChoiceData> getChoices()     { return choices; }
    public static List<PartyVoteStatePacket.VoteData> getVoteData()  { return voteData; }
    public static int getLocalChoiceIndex()                          { return localChoiceIndex; }

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
    }

    public static void updateVoteData(String instanceKey,
                                       List<PartyVoteStatePacket.VoteData> newVoteData,
                                       int newLocalChoiceIndex) {
        if (instanceKey.equals(activeInstanceKey)) {
            voteData         = newVoteData;
            localChoiceIndex = newLocalChoiceIndex;
        }
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