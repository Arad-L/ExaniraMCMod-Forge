package com.exanira.client;

import com.exanira.ExaniraMod;
import com.exanira.character.CompletedSideEventRecord;
import com.exanira.network.AcceptInvitePacket;
import com.exanira.network.CharacterSheetSyncPacket;
import com.exanira.network.PartyStatusPacket;
import com.exanira.network.RequestPartyStatusPacket;
import com.exanira.network.SendInvitePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Three-tab Radio Menu screen (Shift + Right-click on Radio).
 *
 * Tabs:
 *   0 â€” Side Events  (current season + previous season completion history)
 *   1 â€” Main Quest   (ordered main story events; lock / available / complete)
 *   2 â€” Party        (party members, vote status, invite box)
 *
 * Forge 1.18.2: uses PoseStack + fill(). Tab rebuild uses
 * {@code this.init(minecraft, width, height)} (the 1.18.2 equivalent of rebuildWidgets).
 */
@OnlyIn(Dist.CLIENT)
public class RadioMenuScreen extends net.minecraft.client.gui.screens.Screen {

    private static final int TAB_H  = 22;
    private static final int PAD    = 12;
    private static final int LINE_H = 13;

    private static final String[] TAB_LABELS = {"Side Events", "Main Quest", "Party"};

    // Dynamic dimensions — recalculated in init() to fit any GUI scale / window size
    private int panelX, panelY, panelW, panelH;
    private int activeTab = 0;

    // Party-tab widgets (created in init when activeTab == 2)
    private EditBox inviteBox;

    // Click areas for "Accept" buttons rendered in renderPartyTab().
    // Each entry: { x1, y1, x2, y2, inviteIndex }
    private final List<int[]> acceptAreas = new ArrayList<>();

    public RadioMenuScreen() {
        super(new TranslatableComponent("gui.exanira.radio_menu"));
    }

    // ── Init ─────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        // Clamp panel to fit the screen at any GUI scale
        panelW = Math.min(380, (int)(width  * 0.94f));
        panelH = Math.min(260, (int)(height * 0.92f));
        panelX = (width  - panelW) / 2;
        panelY = (height - panelH) / 2;
        inviteBox = null;
        acceptAreas.clear();

        // Tab buttons — switching a tab calls init(mc, w, h) to rebuild widgets cleanly
        int tabW = panelW / TAB_LABELS.length;
        for (int i = 0; i < TAB_LABELS.length; i++) {
            final int idx = i;
            addRenderableWidget(new ExaniraButton(
                    panelX + i * tabW, panelY, tabW, TAB_H,
                    new TextComponent(TAB_LABELS[i]),
                    btn -> {
                        activeTab = idx;
                        if (idx == 2) requestPartyStatus();
                        this.init(this.minecraft, this.width, this.height);
                    }));
        }

        // Party tab: invite EditBox + button
        if (activeTab == 2) {
            boolean canInvite = ClientEventState.isActive();
            int boxY = panelY + panelH - 40;
            int boxW = panelW - 2 * PAD - 60;
            inviteBox = new EditBox(font,
                    panelX + PAD, boxY, boxW, 18,
                    new TextComponent("Player name"));
            inviteBox.setMaxLength(64);
            inviteBox.setSuggestion("Player name...");
            inviteBox.active = canInvite;
            addRenderableWidget(inviteBox);

            ExaniraButton inviteBtn = new ExaniraButton(
                    panelX + PAD + boxW + 4, boxY, 56, 18,
                    new TextComponent("Invite"),
                    btn -> {
                        String name = inviteBox.getValue().trim();
                        if (!name.isEmpty() && canInvite) {
                            ExaniraMod.CHANNEL.send(
                                    PacketDistributor.SERVER.noArg(),
                                    new SendInvitePacket(name));
                            inviteBox.setValue("");
                        }
                    });
            inviteBtn.active = canInvite;
            addRenderableWidget(inviteBtn);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        // Show placeholder text only when the field is empty; hide it once the user types
        if (inviteBox != null) {
            inviteBox.setSuggestion(inviteBox.getValue().isEmpty() ? "Player name..." : "");
        }
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && activeTab == 2) {
            for (int[] area : acceptAreas) {
                if (mx >= area[0] && mx <= area[2] && my >= area[1] && my <= area[3]) {
                    List<ClientEventState.PendingInvite> invites = ClientEventState.getPendingInvites();
                    int idx = area[4];
                    if (idx < invites.size()) {
                        ClientEventState.PendingInvite invite = invites.get(idx);
                        ExaniraMod.CHANNEL.send(
                                PacketDistributor.SERVER.noArg(),
                                new AcceptInvitePacket(invite.instanceKey()));
                        ClientEventState.removePendingInvite(invite.instanceKey());
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    // â”€â”€ Rendering â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    public void render(PoseStack ps, int mouseX, int mouseY, float partialTick) {
        renderBackground(ps);
        drawPanel(ps);
        drawTabUnderline(ps);
        super.render(ps, mouseX, mouseY, partialTick);

        int contentY    = panelY + TAB_H + PAD;
        int contentMaxY = panelY + panelH - PAD;

        switch (activeTab) {
            case 0 -> renderSideEventsTab(ps, contentY, contentMaxY);
            case 1 -> renderMainQuestTab(ps, contentY, contentMaxY);
            case 2 -> renderPartyTab(ps, contentY, contentMaxY);
        }
    }

    private void drawPanel(PoseStack ps) {
        fill(ps, panelX,               panelY,               panelX + panelW,  panelY + panelH,   0xEE000000);
        fill(ps, panelX,               panelY,               panelX + panelW,  panelY + 1,        0xFF888888);
        fill(ps, panelX,               panelY + panelH - 1,  panelX + panelW,  panelY + panelH,   0xFF888888);
        fill(ps, panelX,               panelY,               panelX + 1,       panelY + panelH,   0xFF888888);
        fill(ps, panelX + panelW - 1,  panelY,               panelX + panelW,  panelY + panelH,   0xFF888888);
        fill(ps, panelX,               panelY + TAB_H,       panelX + panelW,  panelY + TAB_H + 1, 0xFF555555);
    }

    private void drawTabUnderline(PoseStack ps) {
        int tabW = panelW / TAB_LABELS.length;
        int tx = panelX + activeTab * tabW;
        fill(ps, tx, panelY + TAB_H - 2, tx + tabW, panelY + TAB_H, 0xFFFFDD44);
    }

    // â”€â”€ Side Events Tab â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void renderSideEventsTab(PoseStack ps, int y, int maxY) {
        int currentSeason = ClientCharacterData.getCurrentSeason();
        List<CharacterSheetSyncPacket.EventSummary> all = ClientCharacterData.getEventSummaries();
        List<CompletedSideEventRecord> completed = ClientCharacterData.getCompletedSideEvents();

        // TODO: Season display name from en_us.json or a seasons.json data file
        drawString(ps, font, "SEASON " + currentSeason, panelX + PAD, y, 0xFFFFDD44);
        y += LINE_H + 2;

        List<CharacterSheetSyncPacket.EventSummary> curSide = all.stream()
                .filter(s -> "SIDE".equals(s.type()) && s.season() == currentSeason)
                .sorted(Comparator.comparingInt(CharacterSheetSyncPacket.EventSummary::order))
                .toList();

        if (curSide.isEmpty()) {
            drawString(ps, font, "  No side events this season.", panelX + PAD, y, 0xFF888888);
            y += LINE_H;
        } else {
            for (CharacterSheetSyncPacket.EventSummary ev : curSide) {
                if (y + LINE_H > maxY) break;
                CompletedSideEventRecord rec = completed.stream()
                        .filter(r -> r.eventId().equals(ev.id())).reduce((a, b) -> b).orElse(null);
                String statusStr  = rec != null ? buildStars(rec.starRating()) : "Not Attempted";
                int    statusColor = rec != null ? 0xFFFFDD44 : 0xFF777777;
                drawString(ps, font, "  " + toDisplayName(ev.id()), panelX + PAD, y, 0xFFCCCCCC);
                drawString(ps, font, statusStr, panelX + panelW - PAD - font.width(statusStr), y, statusColor);
                y += LINE_H;
            }
        }

        // Previous season archive
        if (currentSeason > 1) {
            y += 5;
            if (y + LINE_H > maxY) return;
            int prev = currentSeason - 1;
            drawString(ps, font, "PREVIOUS SEASON (" + prev + ")", panelX + PAD, y, 0xFF999999);
            y += LINE_H + 2;

            List<CharacterSheetSyncPacket.EventSummary> prevSide = all.stream()
                    .filter(s -> "SIDE".equals(s.type()) && s.season() == prev)
                    .sorted(Comparator.comparingInt(CharacterSheetSyncPacket.EventSummary::order))
                    .toList();

            for (CharacterSheetSyncPacket.EventSummary ev : prevSide) {
                if (y + LINE_H > maxY) break;
                // TODO (Phase 5+): checkReplayEligible(ev.id()) â€” stub always returns true
                CompletedSideEventRecord rec = completed.stream()
                        .filter(r -> r.eventId().equals(ev.id())).reduce((a, b) -> b).orElse(null);
                String statusStr  = rec != null ? buildStars(rec.starRating()) : "Not Attempted";
                int    statusColor = rec != null ? 0xFF999966 : 0xFF777777;
                drawString(ps, font, "  " + toDisplayName(ev.id()), panelX + PAD, y, 0xFF999999);
                drawString(ps, font, statusStr, panelX + panelW - PAD - font.width(statusStr), y, statusColor);
                y += LINE_H;
            }
        }
    }

    // â”€â”€ Main Quest Tab â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void renderMainQuestTab(PoseStack ps, int y, int maxY) {
        int currentSeason    = ClientCharacterData.getCurrentSeason();
        List<CharacterSheetSyncPacket.EventSummary> all = ClientCharacterData.getEventSummaries();
        List<String> doneMain = ClientCharacterData.getCompletedMainEvents();

        // TODO: Season display name from en_us.json or seasons.json
        drawString(ps, font, "Season " + currentSeason + " \u2014 TODO: Define season names",
                panelX + PAD, y, 0xFFFFDD44);
        y += LINE_H + 4;

        List<CharacterSheetSyncPacket.EventSummary> mainEvents = all.stream()
                .filter(s -> "MAIN".equals(s.type()) && s.season() == currentSeason)
                .sorted(Comparator.comparingInt(CharacterSheetSyncPacket.EventSummary::order))
                .toList();

        if (mainEvents.isEmpty()) {
            drawString(ps, font, "  No new stories this season yet.", panelX + PAD, y, 0xFF555555);
            y += LINE_H;
        } else {
            for (CharacterSheetSyncPacket.EventSummary ev : mainEvents) {
                if (y + LINE_H > maxY) break;
                boolean done       = doneMain.contains(ev.id());
                boolean prereqsMet = ev.unlockRequires().stream().allMatch(doneMain::contains);

                String icon;
                int nameColor;
                if (done) {
                    icon = "[OK]"; nameColor = 0xFF88FF88;
                } else if (!prereqsMet) {
                    icon = "[L] "; nameColor = 0xFF777777;
                } else {
                    icon = "[ ] "; nameColor = 0xFFCCCCCC;
                }
                drawString(ps, font, icon + " " + toDisplayName(ev.id()), panelX + PAD, y, nameColor);
                y += LINE_H;
            }
        }

        // Story So Far â€” previous seasons (read-only)
        if (currentSeason > 1) {
            y += 5;
            if (y + LINE_H > maxY) return;
            drawString(ps, font, "--- Story So Far ---", panelX + PAD, y, 0xFF555555);
            y += LINE_H + 2;
            for (int s = 1; s < currentSeason; s++) {
                if (y + LINE_H > maxY) break;
                // TODO: season display name
                drawString(ps, font, "  Season " + s + " — Complete", panelX + PAD, y, 0xFF667766);
                y += LINE_H;
            }
        }
    }

    // â”€â”€ Party Tab â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void renderPartyTab(PoseStack ps, int y, int maxY) {
        // Reserve bottom space for the invite box and its label
        int inviteAreaTop  = panelY + panelH - 40 - LINE_H - 6;
        int effectiveMaxY  = Math.min(maxY, inviteAreaTop);
        acceptAreas.clear();

        // ── Section: pending incoming invites ────────────────────────────────
        List<ClientEventState.PendingInvite> pendingInvites = ClientEventState.getPendingInvites();
        if (!pendingInvites.isEmpty()) {
            drawString(ps, font, "Pending Invitations:", panelX + PAD, y, 0xFFFFDD44);
            y += LINE_H + 2;
            int btnW = font.width("Accept") + 8;
            for (int i = 0; i < pendingInvites.size() && y + LINE_H <= effectiveMaxY; i++) {
                ClientEventState.PendingInvite inv = pendingInvites.get(i);
                String invLine = toDisplayName(inv.eventId()) + " \u2014 from " + inv.inviterName();
                int maxTextW = panelW - 2 * PAD - btnW - 6;
                String display = font.plainSubstrByWidth(invLine, maxTextW);
                drawString(ps, font, display, panelX + PAD, y, 0xFFCCCCCC);
                int btnX = panelX + panelW - PAD - btnW;
                // Button box: 2px padding around the 9px font height → y-2 to y+11, centered with the text
                fill(ps, btnX - 1, y - 3, btnX + btnW + 1, y + 12, 0xFF336633); // 1px border
                fill(ps, btnX,     y - 2, btnX + btnW,     y + 11, 0xFF1A441A); // background
                drawCenteredString(ps, font, "Accept", btnX + btnW / 2, y, 0xFF88FF88);
                acceptAreas.add(new int[]{btnX - 1, y - 3, btnX + btnW + 1, y + 12, i});
                y += LINE_H + 3;
            }
            // Separator line
            if (y + 4 <= effectiveMaxY) {
                fill(ps, panelX + PAD, y + 1, panelX + panelW - PAD, y + 2, 0xFF333333);
                y += 6;
            }
        }

        // ── Section: current party ───────────────────────────────────────────
        PartyStatusPacket status = ClientEventState.getLastPartyStatus();
        if (status == null || status.instanceKey().isEmpty() || status.members().isEmpty()) {
            if (pendingInvites.isEmpty()) {
                drawString(ps, font, "No pending invitations.", panelX + PAD, y, 0xFF555555);
                y += LINE_H + 2;
            }
            drawString(ps, font, "Not currently in a party.", panelX + PAD, y, 0xFF888888);
            y += LINE_H + 4;
            if (!ClientEventState.isActive()) {
                drawString(ps, font, "Start an event to invite others.", panelX + PAD, y, 0xFF555555);
            }
        } else {
            drawString(ps, font, "Party:", panelX + PAD, y, 0xFF999999);
            y += LINE_H + 2;

            boolean voteInProgress = status.voteInProgress();
            for (PartyStatusPacket.MemberStatus m : status.members()) {
                if (y + LINE_H > effectiveMaxY) break;
                String voteLabel;
                int    voteColor;
                if (!m.isOnline()) {
                    voteLabel = "[Offline]"; voteColor = 0xFF888888;
                } else if (voteInProgress) {
                    voteLabel = m.voteStatus() == PartyStatusPacket.VoteStatus.VOTED ? "[Voted]" : "[Waiting]";
                    voteColor = m.voteStatus() == PartyStatusPacket.VoteStatus.VOTED ? 0xFF88FF88 : 0xFFFFDD44;
                } else {
                    voteLabel = "[In Party]"; voteColor = 0xFF88FF88;
                }
                drawString(ps, font, "  " + m.name(), panelX + PAD, y, m.isOnline() ? 0xFFCCCCCC : 0xFF888888);
                drawString(ps, font, voteLabel, panelX + panelW - PAD - font.width(voteLabel), y, voteColor);
                y += LINE_H;
            }

            if (status.timerRemainingSeconds() >= 0) {
                y += 4;
                if (y + LINE_H <= effectiveMaxY) {
                    drawString(ps, font, "Disconnect timer: " + status.timerRemainingSeconds() + "s",
                            panelX + PAD, y, 0xFFFF8844);
                }
            }
        }

        // Label for the invite box widget (rendered by init())
        int labelY = panelY + panelH - 40 - LINE_H - 2;
        drawString(ps, font, "Invite player:", panelX + PAD, labelY, 0xFF888888);
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void requestPartyStatus() {
        ExaniraMod.CHANNEL.send(PacketDistributor.SERVER.noArg(), new RequestPartyStatusPacket());
    }

    private static String toDisplayName(String id) {
        String[] parts = id.replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    private static String buildStars(int rating) {
        int r = Math.max(1, Math.min(3, rating));
        return "\u2605".repeat(r) + "\u2606".repeat(3 - r);
    }
}
