package com.example.ae2milock.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.PatternProviderMenu;

import com.example.ae2milock.access.LockUpgradeAccess;

/**
 * Adds upgrade slots to the Pattern Provider menu (standard AE2 slots with {@code UPGRADE} semantics).
 * For providers that cannot accept the card, the upgrade inventory is empty and no slots are created.
 */
@Mixin(PatternProviderMenu.class)
public abstract class PatternProviderMenuMixin extends AEBaseMenu implements LockUpgradeAccess {
    @Shadow
    @Final
    protected PatternProviderLogic logic;

    // Constructor is only needed for compilation: Mixin discards mixin class constructors.
    protected PatternProviderMenuMixin(MenuType<?> menuType, int id, Inventory playerInventory, Object host) {
        super(menuType, id, playerInventory, host);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ae2milock$addUpgradeSlots(MenuType<? extends PatternProviderMenu> menuType, int id,
            Inventory playerInventory, PatternProviderLogicHost host, CallbackInfo ci) {
        this.setupUpgrades(this.ae2milock$getUpgrades());
    }

    @Unique
    @Override
    public IUpgradeInventory ae2milock$getUpgrades() {
        return ((LockUpgradeAccess) this.logic).ae2milock$getUpgrades();
    }
}
