package com.example.ae2milock;

import java.util.List;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import appeng.api.ids.AECreativeTabIds;

/**
 * Mod entry point.
 * <p>
 * Adds the "Output Lock Card" upgrade for AE2's Pattern Provider. The output-slot locking logic for Modern
 * Industrialization machines lives in {@code com.example.ae2milock.lock}; the AE2 hooks live in
 * {@code com.example.ae2milock.mixin}.
 */
@Mod(Ae2MiLock.MOD_ID)
public final class Ae2MiLock {
    public static final String MOD_ID = "ae2milock";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Ae2MiLock(IEventBus modEventBus) {
        ModItems.ITEMS.register(modEventBus);

        modEventBus.addListener(Ae2MiLock::onCommonSetup);
        modEventBus.addListener(Ae2MiLock::onBuildCreativeTab);

        // Tooltip events only fire on the client, so no side check is needed.
        NeoForge.EVENT_BUS.addListener(Ae2MiLock::onItemTooltip);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        // Upgrades.add is not thread-safe, so run it on the main thread.
        event.enqueueWork(ModItems::registerUpgrades);
    }

    private static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(AECreativeTabIds.MAIN)) {
            event.accept(ModItems.OUTPUT_LOCK_CARD);
        }
    }

    private static void onItemTooltip(ItemTooltipEvent event) {
        if (!event.getItemStack().is(ModItems.OUTPUT_LOCK_CARD.get())) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        tooltip.add(Component.translatable("item.ae2milock.output_lock_card.tooltip.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.ae2milock.output_lock_card.tooltip.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.ae2milock.output_lock_card.tooltip.3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
