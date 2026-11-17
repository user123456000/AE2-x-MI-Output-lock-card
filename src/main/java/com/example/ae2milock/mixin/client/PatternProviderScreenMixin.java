package com.example.ae2milock.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.menu.implementations.PatternProviderMenu;

import com.example.ae2milock.client.ClientHooks;

/**
 * Adds AE2's standard upgrade panel to the Pattern Provider screen. The "upgrades" widget style is already defined
 * in {@code common/common.json}, which {@code pattern_provider.json} includes, so no resources need overriding.
 */
@Mixin(PatternProviderScreen.class)
public abstract class PatternProviderScreenMixin<C extends PatternProviderMenu> extends AEBaseScreen<C> {
    // Constructor is only needed for compilation: Mixin discards mixin class constructors.
    protected PatternProviderScreenMixin(C menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void ae2milock$addUpgradePanel(C menu, Inventory playerInventory, Component title, ScreenStyle style,
            CallbackInfo ci) {
        ClientHooks.addUpgradePanel(this.widgets, menu);
    }
}
