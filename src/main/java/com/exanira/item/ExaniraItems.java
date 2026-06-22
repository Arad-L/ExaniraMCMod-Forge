package com.exanira.item;

import com.exanira.ExaniraMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;

public class ExaniraItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExaniraMod.MODID);

    public static final RegistryObject<RadioItem> RADIO =
            ITEMS.register("radio", () -> new RadioItem(new Item.Properties().stacksTo(1)));

    /**
     * Gives the player a radio if they don't already have one in any inventory slot.
     * Safe to call server-side at login or after character creation.
     */
    public static void ensureRadio(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(RADIO.get())) return;
        }
        player.getInventory().add(new ItemStack(RADIO.get()));
    }
}