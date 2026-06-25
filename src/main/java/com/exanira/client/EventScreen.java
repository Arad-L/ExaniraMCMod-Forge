package com.exanira.client;

import com.exanira.ExaniraMod;
import com.exanira.network.EventChoicePacket;
import com.exanira.network.EventStartPacket;
import com.exanira.network.PartyVoteStatePacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Full event interaction screen with scrollable dialogue panel and
 * {@link EventChoiceButton} rows with live vote counts.
 *
 * Ported from 1.21.1 to Forge 1.18.2:
 *   - GuiGraphics -> PoseStack
 *   - addRenderableOnly -> render() directly
 *   - rebuildWidgets() -> this.init(minecraft, width, height)
 *   - mouseScrolled(4 args) -> mouseScrolled(3 args)
 *   - Button.builder -> new Button(...)
 */
@OnlyIn(Dist.CLIENT)
public class EventScreen extends Screen {

    private static final int PANEL_W    = 370;
    private static final int DIALOGUE_H = 90;
    private static final int BUTTON_H   = 24;
    private static final int BUTTON_GAP = 5;
    private static final int PAD        = 12;
    private static final int SCROLLBAR_W = 6;

    private int panelX, panelY, panelH;

    private int scrollLines    = 0;
    private int maxScrollLines = 0;

    private int selectedChoiceIndex = -1;

    /** Tracked so render() can check hover for locked-choice tooltips. */
    private final java.util.List<EventChoiceButton> choiceButtons = new java.util.ArrayList<>();

    public EventScreen() {
        super(new TranslatableComponent("gui.exanira.event"));
    }

    // --- Init ----------------------------------------------------------------

    @Override
    protected void init() {
        ClientEventState.setCurrentEventScreen(this);
        choiceButtons.clear();

        // Persist the authoritative vote highlight across close/reopen
        selectedChoiceIndex = ClientEventState.getLocalChoiceIndex();

        List<EventStartPacket.ChoiceData> choices = ClientEventState.getChoices();

        panelH = DIALOGUE_H
                + PAD
                + choices.size() * (BUTTON_H + BUTTON_GAP)
                - (choices.isEmpty() ? 0 : BUTTON_GAP)
                + PAD;

        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH) / 2;

        List<FormattedCharSequence> wrapped = getWrappedDialogue();
        int visibleLines = (DIALOGUE_H - 30) / (font.lineHeight + 2);
        maxScrollLines   = Math.max(0, wrapped.size() - visibleLines);
        scrollLines      = Math.min(scrollLines, maxScrollLines);

        int btnW      = PANEL_W - PAD * 2;
        int btnStartY = panelY + DIALOGUE_H + PAD;

        if (choices.isEmpty()) {
            Button continueBtn = new ExaniraButton(
                    panelX + PAD, btnStartY, btnW, BUTTON_H,
                    new TextComponent("[ Continue ]"),
                    b -> onChoiceClicked(-1)
            );
            addRenderableWidget(continueBtn);
        } else {
            List<PartyVoteStatePacket.VoteData> voteData = ClientEventState.getVoteData();
            int[] voteCounts = new int[choices.size()];
            for (PartyVoteStatePacket.VoteData data : voteData) {
                if (data.choiceIndex() >= 0 && data.choiceIndex() < choices.size()) {
                    voteCounts[data.choiceIndex()]++;
                }
            }

            for (int i = 0; i < choices.size(); i++) {
                EventStartPacket.ChoiceData c = choices.get(i);
                int by    = btnStartY + i * (BUTTON_H + BUTTON_GAP);
                String label = buildLabel(c);
                final int idx = i;

                EventChoiceButton btn = new EventChoiceButton(
                        panelX + PAD, by, btnW, BUTTON_H,
                        label,
                        (!c.available() && c.lockedText() != null && !c.lockedText().isEmpty())
                                ? c.lockedText() : null,
                        () -> voteCounts[idx],
                        () -> selectedChoiceIndex == idx,
                        () -> onChoiceClicked(idx)
                );
                // Once local vote is locked keep only the chosen button active
                btn.active = c.available() && (selectedChoiceIndex < 0 || selectedChoiceIndex == idx);

                addRenderableWidget(btn);
                choiceButtons.add(btn);
            }
        }
    }

    // --- Rendering -----------------------------------------------------------

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        drawPanel(poseStack);
        renderDialogue(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);

        // Render tooltip for hovered locked-choice buttons.
        // Cannot use btn.isMouseOver() here because AbstractWidget.isMouseOver() returns
        // false when this.active == false (locked buttons are inactive), so we check
        // the mouse bounds manually instead.
        for (EventChoiceButton btn : choiceButtons) {
            String tip = btn.getLockedTooltip();
            if (tip != null
                    && mouseX >= btn.x && mouseX < btn.x + (PANEL_W - PAD * 2)
                    && mouseY >= btn.y && mouseY < btn.y + BUTTON_H) {
                renderTooltip(poseStack, new TextComponent(tip), mouseX, mouseY);
                break;
            }
        }
    }

    private void drawPanel(PoseStack ps) {
        fill(ps, panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xEE000000);
        // 1-px border
        fill(ps, panelX,               panelY,              panelX + PANEL_W, panelY + 1,              0xFF888888);
        fill(ps, panelX,               panelY + panelH - 1, panelX + PANEL_W, panelY + panelH,         0xFF888888);
        fill(ps, panelX,               panelY,              panelX + 1,       panelY + panelH,         0xFF888888);
        fill(ps, panelX + PANEL_W - 1, panelY,              panelX + PANEL_W, panelY + panelH,         0xFF888888);
        // Title divider
        fill(ps, panelX + 8, panelY + 18, panelX + PANEL_W - 8, panelY + 19, 0xFF444444);
        // Choices divider
        fill(ps, panelX + 8, panelY + DIALOGUE_H - 2, panelX + PANEL_W - 8, panelY + DIALOGUE_H - 1, 0xFF444444);
    }

    private void renderDialogue(PoseStack ps) {
        drawCenteredString(ps, font, "\u00a7b\u00a7l\u2709 INCOMING TRANSMISSION",
                width / 2, panelY + 5, 0xFFFFFFFF);

        List<FormattedCharSequence> wrapped = getWrappedDialogue();
        int startY      = panelY + 24;
        int lineHeight  = font.lineHeight + 2;
        int visibleLines = (DIALOGUE_H - 30) / lineHeight;
        int end          = Math.min(wrapped.size(), scrollLines + visibleLines);

        int y = startY;
        for (int i = scrollLines; i < end; i++) {
            font.draw(ps, wrapped.get(i), panelX + PAD, y, 0xFFCCCCCC);
            y += lineHeight;
        }

        drawScrollbar(ps, wrapped.size(), visibleLines);
    }

    private void drawScrollbar(PoseStack ps, int totalLines, int visibleLines) {
        if (totalLines <= visibleLines) return;

        int trackX = panelX + PANEL_W - PAD;
        int trackY = panelY + 24;
        int trackH = DIALOGUE_H - 30;

        fill(ps, trackX, trackY, trackX + SCROLLBAR_W, trackY + trackH, 0xFF222222);

        int thumbH = Math.max(12, (trackH * visibleLines) / totalLines);
        int thumbY = trackY + ((trackH - thumbH) * scrollLines) / Math.max(1, maxScrollLines);

        fill(ps, trackX, thumbY, trackX + SCROLLBAR_W, thumbY + thumbH, 0xFFAAAAAA);
    }

    // --- Input ---------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        if (maxScrollLines > 0) {
            scrollLines -= (int) Math.signum(scrollDelta);
            scrollLines  = Math.max(0, Math.min(maxScrollLines, scrollLines));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollDelta);
    }

    // --- Helpers -------------------------------------------------------------

    private String buildLabel(EventStartPacket.ChoiceData c) {
        String req = c.requirementText();
        return (req == null || req.isEmpty()) ? c.text() : req + " " + c.text();
    }

    private List<FormattedCharSequence> getWrappedDialogue() {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String text : ClientEventState.getDialogue()) {
            lines.addAll(font.split(
                    new net.minecraft.network.chat.TextComponent(text),
                    PANEL_W - PAD * 2 - SCROLLBAR_W - 4
            ));
        }
        return lines;
    }

    private void onChoiceClicked(int index) {
        if (index >= 0 && selectedChoiceIndex >= 0) return; // already voted

        if (index >= 0) {
            selectedChoiceIndex = index; // immediate local feedback
        }

        ExaniraMod.CHANNEL.sendToServer(
                new EventChoicePacket(ClientEventState.getInstanceKey(), index));

        if (index == -1) onClose();
    }

    // --- Public API (called by packet handlers) ------------------------------

    /** Called when a new EventStartPacket arrives mid-scene (scene advance). */
    public void refresh() {
        selectedChoiceIndex = ClientEventState.getLocalChoiceIndex();
        scrollLines    = 0;
        maxScrollLines = 0;
        this.init(this.minecraft, this.width, this.height);
    }

    /** Called by ClientEventState.notifyVoteDataChanged() when vote state updates. */
    public void updateVoteCounts() {
        selectedChoiceIndex = ClientEventState.getLocalChoiceIndex();
        this.init(this.minecraft, this.width, this.height);
    }

    // --- Behaviour -----------------------------------------------------------

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    @Override
    public void removed() {
        super.removed();
        ClientEventState.setCurrentEventScreen(null);
    }
}