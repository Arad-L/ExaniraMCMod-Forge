package com.exanira.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A Button subclass that renders:
 *   - the choice label (left-aligned, truncated to fit)
 *   - a vote-count badge (right-aligned, cyan)
 *   - a selection highlight overlay when this player has voted for this choice
 *
 * Ported to Forge 1.18.2 (PoseStack rendering, no GuiGraphics).
 */
@OnlyIn(Dist.CLIENT)
public class EventChoiceButton extends Button {

    private static final int LEFT_MARGIN  = 8;
    private static final int RIGHT_MARGIN = 8;
    private static final int TEXT_GAP     = 8;

    private final String label;
    private final Supplier<Integer> voteSupplier;
    private final Supplier<Boolean> selectedSupplier;
    @Nullable private final String lockedTooltip;

    public EventChoiceButton(
            int x, int y, int width, int height,
            String label,
            @Nullable String lockedTooltip,
            Supplier<Integer> voteSupplier,
            Supplier<Boolean> selectedSupplier,
            Runnable onPress
    ) {
        super(x, y, width, height, new TextComponent(label), b -> onPress.run());
        this.label            = label;
        this.lockedTooltip    = lockedTooltip;
        this.voteSupplier     = voteSupplier;
        this.selectedSupplier = selectedSupplier;
        // Clear the message so the vanilla renderer draws nothing; we draw everything ourselves.
        this.setMessage(new TextComponent(""));
    }

    /** Non-null when the choice is locked; contains the human-readable reason. */
    @Nullable
    public String getLockedTooltip() {
        return lockedTooltip;
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        // Draw the vanilla button background (hover / active / inactive state colouring).
        super.renderButton(poseStack, mouseX, mouseY, partialTick);

        Font font = Minecraft.getInstance().font;

        int voteCount  = Math.max(0, voteSupplier.get());
        String voteText = voteCount > 0 ? String.valueOf(voteCount) : "";

        int centerY = this.y + (this.height - 8) / 2;
        int labelX  = this.x + LEFT_MARGIN;
        int voteWidth = voteText.isEmpty() ? 0 : font.width(voteText);
        int voteX   = this.x + this.width - RIGHT_MARGIN - voteWidth;

        int labelMaxWidth = Math.max(10, voteX - labelX - (voteText.isEmpty() ? 0 : TEXT_GAP));
        String visibleLabel = font.plainSubstrByWidth(label, labelMaxWidth);

        int baseTextColor = this.active ? 0xFFFFFFFF : 0xFFA0A0A0;

        poseStack.pushPose();
        poseStack.translate(0, 0, 100);   // bring text above button background
        font.draw(poseStack, visibleLabel, labelX, centerY, baseTextColor);

        if (!voteText.isEmpty()) {
            int voteColor = this.active ? 0xFF7FDBFF : 0xFF5D7C88;
            font.draw(poseStack, voteText, voteX, centerY, voteColor);
        }
        poseStack.popPose();

        // Selection highlight overlay
        if (selectedSupplier.get()) {
            int fillColor    = this.active ? 0x3329B6F6 : 0x22444444;
            int outlineColor = this.active ? 0xFF29B6F6 : 0xFF777777;

            fill(poseStack, this.x, this.y, this.x + this.width, this.y + this.height, fillColor);
            // 1-px border
            fill(poseStack, this.x,                  this.y,                  this.x + this.width, this.y + 1,               outlineColor);
            fill(poseStack, this.x,                  this.y + this.height - 1, this.x + this.width, this.y + this.height,     outlineColor);
            fill(poseStack, this.x,                  this.y,                  this.x + 1,           this.y + this.height,     outlineColor);
            fill(poseStack, this.x + this.width - 1, this.y,                  this.x + this.width,  this.y + this.height,     outlineColor);
        }
    }
}
