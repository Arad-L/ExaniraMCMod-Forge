package com.exanira.item;

import com.exanira.client.ClientEventState;
import com.exanira.client.EventScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class RadioItem extends Item {

    public RadioItem(Properties properties) {
        super(properties);
    }

    /**
     * Glows (enchantment foil) when the server has flagged this stack as having
     * an active event. The flag is stored in CUSTOM_DATA so it persists in NBT
     * and syncs to the client automatically via the normal inventory update path.
     */
    @Override
    public boolean isFoil(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean("active");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            openScreenIfActive();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @OnlyIn(Dist.CLIENT)
    private static void openScreenIfActive() {
        if (ClientEventState.isActive()) {
            Minecraft.getInstance().setScreen(new EventScreen());
        }
    }
}
