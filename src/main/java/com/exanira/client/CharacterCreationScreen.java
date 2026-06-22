package com.exanira.client;

import com.exanira.ExaniraMod;
import com.exanira.character.CharacterCreationDefs;
import com.exanira.character.LifestyleOption;
import com.exanira.character.LifestyleQuestion;
import com.exanira.character.Profession;
import com.exanira.network.CharacterCreationSubmitPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-step character creation screen shown on first login.
 *
 * Step 0     : Profession selection (8 options in a 2-column grid)
 * Steps 1-N  : Lifestyle questions (3 options each, one per question)
 * Step N+1   : Confirmation -- "Begin Your Story" button submits to server
 *
 * Escape is blocked; the screen must be completed to proceed.
 *
 * Ported from 1.21.1 to Forge 1.18.2:
 *   - GuiGraphics -> PoseStack
 *   - Button.builder -> new Button(...)
 *   - addRenderableOnly -> draw directly in render()
 *   - rebuildWidgets() -> this.init(minecraft, width, height)
 *   - PacketDistributor.sendToServer -> CHANNEL.sendToServer
 *   - Component.translatable -> new TranslatableComponent
 */
@OnlyIn(Dist.CLIENT)
public class CharacterCreationScreen extends Screen {

    private static final int BG_W = 300;
    private static final int BG_H = 240;
    private static final int TOTAL_STEPS = CharacterCreationDefs.QUESTIONS.size(); // 5

    private int step = 0;
    private int professionChoice = -1;
    private final List<Integer> lifestyleChoices = new ArrayList<>();

    public CharacterCreationScreen() {
        super(new TranslatableComponent("gui.exanira.character_creation"));
    }

    // -------------------------------------------------------------------------
    // Widget setup
    // -------------------------------------------------------------------------

    @Override
    protected void init() {
        int bx = (this.width  - BG_W) / 2;
        int by = (this.height - BG_H) / 2;

        if (step == 0) {
            addProfessionButtons(bx, by);
        } else if (step >= 1 && step <= TOTAL_STEPS) {
            addLifestyleButtons(bx, by);
        } else {
            addConfirmButton(bx, by);
        }
    }

    private void addProfessionButtons(int bx, int by) {
        int btnW = 135, btnH = 20, btnGap = 4, colGap = 10;
        int startX = bx + 10;
        int startY = by + 40;
        Profession[] profs = Profession.values();
        for (int i = 0; i < profs.length; i++) {
            final int idx = i;
            int col = i % 2;
            int row = i / 2;
            int x = startX + col * (btnW + colGap);
            int y = startY + row * (btnH + btnGap);
            addRenderableWidget(new Button(x, y, btnW, btnH,
                    new TextComponent(profs[i].displayName()),
                    btn -> selectProfession(idx)));
        }
    }

    private void addLifestyleButtons(int bx, int by) {
        LifestyleQuestion q = CharacterCreationDefs.QUESTIONS.get(step - 1);
        int btnW = 260, btnH = 26, btnGap = 8;
        int startX = bx + (BG_W - btnW) / 2;
        int startY = by + 92;
        List<LifestyleOption> opts = q.options();
        for (int i = 0; i < opts.size(); i++) {
            final int optIdx = i;
            addRenderableWidget(new Button(startX, startY + i * (btnH + btnGap), btnW, btnH,
                    new TextComponent(opts.get(i).buttonText()),
                    btn -> selectLifestyle(optIdx)));
        }
    }

    private void addConfirmButton(int bx, int by) {
        int btnW = 160, btnH = 24;
        addRenderableWidget(new Button(
                bx + (BG_W - btnW) / 2, by + 168, btnW, btnH,
                new TranslatableComponent("gui.exanira.character_creation.begin"),
                btn -> confirm()));
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    private void selectProfession(int idx) {
        professionChoice = idx;
        step = 1;
        this.init(this.minecraft, this.width, this.height);
    }

    private void selectLifestyle(int optIdx) {
        lifestyleChoices.add(optIdx);
        step++;
        this.init(this.minecraft, this.width, this.height);
    }

    private void confirm() {
        ExaniraMod.CHANNEL.sendToServer(
                new CharacterCreationSubmitPacket(professionChoice, new ArrayList<>(lifestyleChoices)));
        this.onClose();
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);

        int bx = (this.width  - BG_W) / 2;
        int by = (this.height - BG_H) / 2;

        drawPanel(poseStack, bx, by);
        renderLabels(poseStack, bx, by);

        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    private void drawPanel(PoseStack ps, int bx, int by) {
        fill(ps, bx, by, bx + BG_W, by + BG_H, 0xCC000000);
        // Outline
        fill(ps, bx,           by,            bx + BG_W, by + 1,        0xFFFFFFFF);
        fill(ps, bx,           by + BG_H - 1, bx + BG_W, by + BG_H,    0xFFFFFFFF);
        fill(ps, bx,           by,            bx + 1,    by + BG_H,    0xFFFFFFFF);
        fill(ps, bx + BG_W - 1, by,           bx + BG_W, by + BG_H,    0xFFFFFFFF);
        // Title divider
        fill(ps, bx + 8, by + 35, bx + BG_W - 8, by + 36, 0xFF555555);
    }

    private void renderLabels(PoseStack ps, int bx, int by) {
        int cx = this.width / 2;

        if (step == 0) {
            drawCenteredString(ps, this.font, "Who were you?",        cx, by + 10, 0xFFFFFF);
            drawCenteredString(ps, this.font, "Choose your profession.", cx, by + 24, 0xAAAAAA);

        } else if (step >= 1 && step <= TOTAL_STEPS) {
            LifestyleQuestion q = CharacterCreationDefs.QUESTIONS.get(step - 1);
            drawCenteredString(ps, this.font, q.title(), cx, by + 10, 0xFFFFFF);
            drawCenteredString(ps, this.font, "Question " + step + " of " + TOTAL_STEPS,
                    cx, by + 24, 0x888888);
            // Wrapped body text
            List<FormattedCharSequence> lines = font.split(
                    new TextComponent(q.bodyText()), BG_W - 24);
            int ty = by + 44;
            for (FormattedCharSequence line : lines) {
                drawCenteredString(ps, this.font, line, cx, ty, 0xAAAAAA);
                ty += 10;
            }

        } else {
            // Confirmation step
            drawCenteredString(ps, this.font, "Your story has been written.", cx, by + 100, 0xFFFFFF);
            drawCenteredString(ps, this.font, "Step into the world.",         cx, by + 118, 0xAAAAAA);
        }
    }

    // -------------------------------------------------------------------------
    // Behaviour
    // -------------------------------------------------------------------------

    @Override
    public boolean isPauseScreen() { return true; }

    /** Block Escape -- the player must complete character creation to proceed. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}