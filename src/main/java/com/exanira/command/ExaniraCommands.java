package com.exanira.command;

import com.exanira.event.ActiveEvent;
import com.exanira.event.EventDefinition;
import com.exanira.event.EventQueueManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Registers the /exanira command tree.
 * Requires permission level 2 (op).
 *
 * /exanira event start <eventId>
 * /exanira event invite <player>
 * /exanira event accept
 * /exanira event stop [player]
 */
public class ExaniraCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ExaniraCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("exanira")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("event")
                    .then(Commands.literal("start")
                        .then(Commands.argument("eventId", StringArgumentType.string())
                            .executes(ExaniraCommands::executeEventStart)))
                    .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(ExaniraCommands::executeEventInvite)))
                    .then(Commands.literal("accept")
                        .executes(ExaniraCommands::executeEventAccept))
                    .then(Commands.literal("stop")
                        .executes(ExaniraCommands::executeEventStopSelf)
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(ExaniraCommands::executeEventStopTarget))))
        );
    }

    // -------------------------------------------------------------------------

    private static int executeEventStart(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {

        String id = StringArgumentType.getString(ctx, "eventId");
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        Optional<EventDefinition> def = EventQueueManager.INSTANCE.getDefinition(id);
        if (def.isEmpty()) {
            ctx.getSource().sendFailure(new TextComponent("Unknown event id: '" + id + "'"));
            return 0;
        }

        boolean started = EventQueueManager.INSTANCE.startEvent(id, player);
        if (!started) {
            ctx.getSource().sendFailure(
                    new TextComponent("Could not start event -- player may already be in an event."));
            return 0;
        }

        ctx.getSource().sendSuccess(new TextComponent("Started event '" + id + "'."), false);
        return 1;
    }

    private static int executeEventStopSelf(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {

        ServerPlayer player = ctx.getSource().getPlayerOrException();
        boolean stopped = EventQueueManager.INSTANCE.forceStopEvent(player);
        if (!stopped) {
            ctx.getSource().sendFailure(new TextComponent("You are not currently in any event."));
            return 0;
        }
        ctx.getSource().sendSuccess(new TextComponent("Event stopped."), false);
        return 1;
    }

    private static int executeEventStopTarget(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {

        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        boolean stopped = EventQueueManager.INSTANCE.forceStopEvent(target);
        if (!stopped) {
            ctx.getSource().sendFailure(
                    new TextComponent(target.getName().getString() + " is not currently in any event."));
            return 0;
        }
        ctx.getSource().sendSuccess(
                new TextComponent("Event stopped for " + target.getName().getString() + "."), true);
        return 1;
    }

    private static int executeEventInvite(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {

        ServerPlayer inviter = ctx.getSource().getPlayerOrException();
        ServerPlayer invitee = EntityArgument.getPlayer(ctx, "player");

        String instanceKey = EventQueueManager.INSTANCE
                .getPlayerEventKey(inviter.getUUID()).orElse(null);

        if (instanceKey == null) {
            ctx.getSource().sendFailure(
                    new TextComponent("You must be in an event to invite others."));
            return 0;
        }

        if (EventQueueManager.INSTANCE.isPlayerInEvent(invitee.getUUID())) {
            ctx.getSource().sendFailure(
                    new TextComponent(invitee.getName().getString() + " is already in an event."));
            return 0;
        }

        Optional<ActiveEvent> activeEventOpt =
                EventQueueManager.INSTANCE.getActiveEvent(instanceKey);

        if (activeEventOpt.isEmpty() || activeEventOpt.get().isResolved()) {
            ctx.getSource().sendFailure(new TextComponent("The event has already ended."));
            return 0;
        }

        ActiveEvent activeEvent = activeEventOpt.get();

        if (!activeEvent.currentSceneId().equals(activeEvent.definition().startScene())) {
            ctx.getSource().sendFailure(
                    new TextComponent("Invites can only be sent from the first scene of the event."));
            return 0;
        }

        if (EventQueueManager.INSTANCE.hasPendingInvitationForEvent(invitee.getUUID(), instanceKey)) {
            ctx.getSource().sendFailure(
                    new TextComponent(invitee.getName().getString()
                            + " already has your invitation for this event."));
            return 0;
        }

        EventQueueManager.INSTANCE.setPendingInvitation(invitee.getUUID(), instanceKey);

        LOGGER.info("[Exanira] Invitation set for player {} to join event {}",
                invitee.getUUID(), instanceKey);

        invitee.sendMessage(new TextComponent(
                inviter.getName().getString()
                        + " has invited you to join their event! "
                        + "Use /exanira event accept to join."
        ).withStyle(ChatFormatting.AQUA), Util.NIL_UUID);

        ctx.getSource().sendSuccess(
                new TextComponent("Invited " + invitee.getName().getString() + " to join your event."),
                false);
        return 1;
    }

    private static int executeEventAccept(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {

        ServerPlayer player = ctx.getSource().getPlayerOrException();

        if (EventQueueManager.INSTANCE.isPlayerInEvent(player.getUUID())) {
            ctx.getSource().sendFailure(new TextComponent("You are already in an event."));
            return 0;
        }

        String instanceKey = EventQueueManager.INSTANCE.getPendingInvitation(player.getUUID());
        if (instanceKey == null) {
            ctx.getSource().sendFailure(new TextComponent("You have no pending invitations."));
            return 0;
        }

        boolean joined = EventQueueManager.INSTANCE.joinEvent(instanceKey, player);
        if (!joined) {
            ctx.getSource().sendFailure(
                    new TextComponent("Could not join the event. "
                            + "The event may have progressed or ended."));
            return 0;
        }

        ctx.getSource().sendSuccess(new TextComponent("You have accepted the invitation."), false);
        return 1;
    }
}