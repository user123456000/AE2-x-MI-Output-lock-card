package com.example.ae2milock.mixin;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;

import com.example.ae2milock.access.LockUpgradeAccess;
import com.example.ae2milock.access.PushContextAccess;
import com.example.ae2milock.lock.LockUpgrades;
import com.example.ae2milock.lock.OutputLockingTarget;

/**
 * Adds to the Pattern Provider:
 * <ul>
 * <li>an upgrade inventory (one card slot) with NBT persistence, drops on break and clearing;</li>
 * <li>interception of the push target, so that an installed card locks the machine's outputs.</li>
 * </ul>
 */
@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicMixin implements LockUpgradeAccess, PushContextAccess {
    @Shadow
    @Final
    private PatternProviderLogicHost host;

    @Unique
    private IUpgradeInventory ae2milock$upgrades;

    @Unique
    private IPatternDetails ae2milock$currentPattern;

    // ------------------------------------------------------------------------------------------------------------
    // Upgrade inventory
    // ------------------------------------------------------------------------------------------------------------

    @Unique
    @Override
    public IUpgradeInventory ae2milock$getUpgrades() {
        IUpgradeInventory upgrades = this.ae2milock$upgrades;
        if (upgrades == null) {
            upgrades = LockUpgrades.create(this.host);
            // Do not cache the empty placeholder (for incompatible providers).
            if (upgrades.size() > 0) {
                this.ae2milock$upgrades = upgrades;
            }
        }
        return upgrades;
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void ae2milock$writeUpgrades(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        IUpgradeInventory upgrades = this.ae2milock$getUpgrades();
        if (upgrades.size() > 0) {
            upgrades.writeToNBT(tag, LockUpgrades.NBT_KEY, registries);
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void ae2milock$readUpgrades(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        IUpgradeInventory upgrades = this.ae2milock$getUpgrades();
        if (upgrades.size() > 0 && tag.contains(LockUpgrades.NBT_KEY)) {
            upgrades.readFromNBT(tag, LockUpgrades.NBT_KEY, registries);
        }
    }

    @Inject(method = "addDrops", at = @At("TAIL"))
    private void ae2milock$dropUpgrades(List<ItemStack> drops, CallbackInfo ci) {
        for (ItemStack stack : this.ae2milock$getUpgrades()) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void ae2milock$clearUpgrades(CallbackInfo ci) {
        this.ae2milock$getUpgrades().clear();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Output locking when pushing a pattern
    // ------------------------------------------------------------------------------------------------------------

    @Unique
    @Override
    @Nullable
    public IPatternDetails ae2milock$getCurrentPattern() {
        return this.ae2milock$currentPattern;
    }

    @Inject(method = "pushPattern", at = @At("HEAD"))
    private void ae2milock$beginPush(IPatternDetails patternDetails, KeyCounter[] inputHolder,
            CallbackInfoReturnable<Boolean> cir) {
        this.ae2milock$currentPattern = patternDetails;
    }

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void ae2milock$endPush(IPatternDetails patternDetails, KeyCounter[] inputHolder,
            CallbackInfoReturnable<Boolean> cir) {
        this.ae2milock$currentPattern = null;
    }

    /**
     * {@code sendStacksOut} is called both from {@code pushPattern} (already after a successful insert) and on a
     * timer to flush leftovers. In both cases the locks are already set, so clear the pattern context.
     */
    @Inject(method = "sendStacksOut", at = @At("HEAD"))
    private void ae2milock$beforeSendStacksOut(CallbackInfoReturnable<Boolean> cir) {
        this.ae2milock$currentPattern = null;
    }

    /**
     * All pattern input inserts into the target go through {@code findAdapter}. If the provider has the card and we
     * are inside {@code pushPattern}, wrap the target to lock the machine's outputs before the insert.
     */
    @Inject(method = "findAdapter", at = @At("RETURN"), cancellable = true)
    private void ae2milock$wrapAdapter(Direction side, CallbackInfoReturnable<PatternProviderTarget> cir) {
        PatternProviderTarget adapter = cir.getReturnValue();
        IPatternDetails pattern = this.ae2milock$currentPattern;
        if (adapter == null || pattern == null || !LockUpgrades.isCardInstalled(this.ae2milock$getUpgrades())) {
            return;
        }

        BlockEntity blockEntity = this.host.getBlockEntity();
        if (blockEntity.getLevel() instanceof ServerLevel level) {
            cir.setReturnValue(new OutputLockingTarget(adapter, level, blockEntity.getBlockPos().relative(side), pattern));
        }
    }
}
