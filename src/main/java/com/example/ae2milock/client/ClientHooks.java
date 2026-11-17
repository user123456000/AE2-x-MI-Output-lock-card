package com.example.ae2milock.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.Upgrades;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;

import com.example.ae2milock.access.LockUpgradeAccess;

/**
 * Client side: the upgrade slot panel on the Pattern Provider screen.
 */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void addUpgradePanel(WidgetContainer widgets, PatternProviderMenu menu) {
        List<Slot> upgradeSlots = menu.getSlots(SlotSemantics.UPGRADE);
        // The "upgrades" widget is not present in every style (e.g. screens from other addons), so add it only
        // when the menu actually has upgrade slots.
        if (upgradeSlots.isEmpty()) {
            return;
        }

        IUpgradeInventory upgrades = ((LockUpgradeAccess) menu).ae2milock$getUpgrades();
        widgets.add("upgrades", new UpgradesPanel(upgradeSlots, () -> compatibleUpgradesTooltip(upgrades)));
    }

    private static List<Component> compatibleUpgradesTooltip(IUpgradeInventory upgrades) {
        List<Component> lines = new ArrayList<>();
        lines.add(GuiText.CompatibleUpgrades.text());
        lines.addAll(Upgrades.getTooltipLinesForMachine(upgrades.getUpgradableItem()));
        return lines;
    }
}
