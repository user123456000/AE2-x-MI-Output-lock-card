package com.example.ae2milock;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Ae2MiLock.MOD_ID);

    /**
     * Upgrade card. Created with AE2's standard factory, so AE2 treats it as a regular upgrade card: upgrade slots
     * accept it, it gets the standard "Compatible with ..." tooltip, and it works with {@code Upgrades.add}.
     */
    public static final DeferredItem<Item> OUTPUT_LOCK_CARD = ITEMS.register("output_lock_card",
            () -> Upgrades.createUpgradeCardItem(new Item.Properties()));

    private ModItems() {
    }

    /**
     * Tells AE2 which devices accept the card: the Pattern Provider block and the cable part.
     * To support providers from other addons, add another
     * {@code Upgrades.add(OUTPUT_LOCK_CARD, <provider item>, 1)} call here if their logic is based on
     * {@code PatternProviderLogic}.
     */
    public static void registerUpgrades() {
        Upgrades.add(OUTPUT_LOCK_CARD, AEBlocks.PATTERN_PROVIDER, 1);
        Upgrades.add(OUTPUT_LOCK_CARD, AEParts.PATTERN_PROVIDER, 1);
    }
}
