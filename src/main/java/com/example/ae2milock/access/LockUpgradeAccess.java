package com.example.ae2milock.access;

import appeng.api.upgrades.IUpgradeInventory;

/**
 * Duck interface implemented by {@code PatternProviderLogic} and {@code PatternProviderMenu} through mixins.
 * It must live outside the mixin package: classes in the mixin package cannot be referenced directly.
 */
public interface LockUpgradeAccess {
    /**
     * @return the provider's upgrade inventory. Providers that cannot accept the card get an empty inventory
     *         (0 slots).
     */
    IUpgradeInventory ae2milock$getUpgrades();
}
