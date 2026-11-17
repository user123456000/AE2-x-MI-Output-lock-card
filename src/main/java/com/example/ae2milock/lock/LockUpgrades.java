package com.example.ae2milock.lock;

import net.minecraft.world.item.Item;

import appeng.api.stacks.AEItemKey;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.upgrades.Upgrades;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

import com.example.ae2milock.ModItems;

/**
 * Upgrade inventory for the Pattern Provider.
 */
public final class LockUpgrades {
    /** NBT key under which the upgrade inventory is saved in the provider's data. */
    public static final String NBT_KEY = "ae2milock_upgrades";

    private LockUpgrades() {
    }

    /**
     * Creates a single-slot inventory if the card can be inserted into this provider (i.e. {@code Upgrades.add} has
     * a binding for its item). Otherwise returns an empty zero-slot inventory, so we do not break or clutter the
     * GUI of providers from other addons.
     */
    public static IUpgradeInventory create(PatternProviderLogicHost host) {
        AEItemKey icon = host.getTerminalIcon();
        if (icon == null) {
            return UpgradeInventories.empty();
        }
        Item machineItem = icon.getItem();
        if (Upgrades.getMaxInstallable(ModItems.OUTPUT_LOCK_CARD.get(), machineItem) <= 0) {
            return UpgradeInventories.empty();
        }
        return UpgradeInventories.forMachine(machineItem, 1, host::saveChanges);
    }

    public static boolean isCardInstalled(IUpgradeInventory upgrades) {
        return upgrades.size() > 0 && upgrades.isInstalled(ModItems.OUTPUT_LOCK_CARD.get());
    }
}
