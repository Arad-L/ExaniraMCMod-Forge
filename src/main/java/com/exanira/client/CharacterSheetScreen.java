package com.exanira.client;

import com.exanira.character.Stat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Displays the player's CharacterSheet stats and backstory.
 * Opened by pressing the [C] keybinding (KeyBindings.OPEN_CHARACTER_SHEET).
 * Ported from 1.21.1: GuiGraphics → PoseStack rendering.
 */
@OnlyIn(Dist.CLIENT)
public class CharacterSheetScreen extends Screen {

    private static final int WIDTH  = 210;
    private static final int HEIGHT = 240;

    public CharacterSheetScreen() {
        super(new TranslatableComponent("gui.exanira.character_sheet"));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);

        int x = (this.width  - WIDTH)  / 2;
        int y = (this.height - HEIGHT) / 2;

        // Background
        fill(poseStack, x, y, x + WIDTH, y + HEIGHT, 0xCC000000);
        // Outline (1-px border)
        fill(poseStack, x,          y,              x + WIDTH, y + 1,          0xFFFFFFFF);
        fill(poseStack, x,          y + HEIGHT - 1, x + WIDTH, y + HEIGHT,     0xFFFFFFFF);
        fill(poseStack, x,          y,              x + 1,     y + HEIGHT,     0xFFFFFFFF);
        fill(poseStack, x + WIDTH - 1, y,           x + WIDTH, y + HEIGHT,     0xFFFFFFFF);

        // Title
        drawCenteredString(poseStack, this.font, this.title.getString(), this.width / 2, y + 8, 0xFFFFFF);

        // Divider under title
        fill(poseStack, x + 8, y + 18, x + WIDTH - 8, y + 19, 0xFF555555);

        // Stats
        int curY = y + 24;
        for (Stat stat : Stat.values()) {
            String line = stat.displayName() + ":  " + ClientCharacterData.getStat(stat);
            this.font.draw(poseStack, line, x + 12, curY, 0xDDDDDD);
            curY += 12;
        }

        // Divider above backstory
        curY += 3;
        fill(poseStack, x + 8, curY, x + WIDTH - 8, curY + 1, 0xFF555555);
        curY += 5;

        // Backstory label
        this.font.draw(poseStack, "Backstory:", x + 12, curY, 0xFFFF88);
        curY += 12;

        // Backstory text (word-wrapped)
        List<FormattedCharSequence> lines = this.font.split(
                new TextComponent(ClientCharacterData.getBackstory()), WIDTH - 24);
        for (FormattedCharSequence line : lines) {
            this.font.draw(poseStack, line, x + 12, curY, 0xCCCCCC);
            curY += 10;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
