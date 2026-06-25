package com.exanira.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Drop-in replacement for vanilla Button that renders with fill()-based
 * background instead of a texture blit.
 *
 * The vanilla renderButton() blits the widgets.png button texture using
 * source_height == this.height.  Because the texture states are stacked at
 * 20 px intervals, any button whose height != 20 reads pixels from the wrong
 * state row, producing a visible artefact bar.  fill() is height-agnostic.
 *
 * All custom screens in this mod use ExaniraButton (or a subclass) so the
 * fix is applied in one place.
 */
@OnlyIn(Dist.CLIENT)
public class ExaniraButton extends Button {

    public ExaniraButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress);
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        int bgColor;
        if (!this.active) {
            bgColor = 0xFF3A3A3A;
        } else if (this.isHovered) {
            bgColor = 0xFF6A6A6A;
        } else {
            bgColor = 0xFF505050;
        }

        fill(poseStack, this.x, this.y, this.x + this.width, this.y + this.height, bgColor);

        int borderColor = this.active ? 0xFF888888 : 0xFF555555;
        fill(poseStack, this.x,                  this.y,                   this.x + this.width, this.y + 1,               borderColor);
        fill(poseStack, this.x,                  this.y + this.height - 1, this.x + this.width, this.y + this.height,     borderColor);
        fill(poseStack, this.x,                  this.y,                   this.x + 1,           this.y + this.height,     borderColor);
        fill(poseStack, this.x + this.width - 1, this.y,                   this.x + this.width,  this.y + this.height,     borderColor);

        int textColor = this.active ? 0xFFFFFFFF : 0xFFA0A0A0;
        drawCenteredString(poseStack, Minecraft.getInstance().font, this.getMessage(),
                this.x + this.width / 2, this.y + (this.height - 8) / 2, textColor);
    }
}
