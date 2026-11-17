package com.example.ae2milock.lock;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderTarget;

import com.example.ae2milock.Ae2MiLock;

/**
 * Wrapper around the target the Pattern Provider pushes pattern inputs into.
 * <p>
 * AE2 first checks whether the target accepts all inputs ({@code Actionable.SIMULATE}) and only then inserts them
 * for real ({@code Actionable.MODULATE}). The wrapper hooks both steps:
 * <ul>
 * <li>on the first check, an output lock plan is built. If it cannot be applied right now (the machine is busy
 * with another recipe or the output holds "foreign" resources), the target reports that it accepts nothing -
 * the provider simply retries later, breaking nothing;</li>
 * <li>right before the first real insert, the output slots are locked - by the time the machine receives the
 * inputs, it can only produce the pattern's products.</li>
 * </ul>
 * Errors in the locking logic must not break the provider itself, so on any unexpected exception the feature is
 * disabled (and logged) while the provider keeps working as usual.
 */
public final class OutputLockingTarget implements PatternProviderTarget {
    private static volatile boolean disabled;

    private final PatternProviderTarget delegate;
    private final ServerLevel level;
    private final BlockPos targetPos;
    private final IPatternDetails pattern;

    private boolean evaluated;
    @Nullable
    private OutputLockService.Prepared prepared;
    private boolean applied;

    public OutputLockingTarget(PatternProviderTarget delegate, ServerLevel level, BlockPos targetPos,
            IPatternDetails pattern) {
        this.delegate = delegate;
        this.level = level;
        this.targetPos = targetPos;
        this.pattern = pattern;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable type) {
        if (!applied && !disabled) {
            try {
                if (!evaluated) {
                    evaluated = true;
                    prepared = OutputLockService.prepare(level, targetPos, pattern);
                }
                if (prepared != null) {
                    if (prepared.isBlocked()) {
                        return 0;
                    }
                    if (type == Actionable.MODULATE) {
                        prepared.apply();
                        applied = true;
                    }
                }
            } catch (RuntimeException | LinkageError e) {
                disabled = true;
                prepared = null;
                Ae2MiLock.LOGGER.error("Output Lock Card failed unexpectedly and has been disabled until restart. "
                        + "Pattern providers keep working without it. Please report this (AE2/MI version mismatch?)", e);
            }
        }
        return delegate.insert(what, amount, type);
    }

    @Override
    public boolean containsPatternInput(Set<AEKey> patternInputs) {
        return delegate.containsPatternInput(patternInputs);
    }
}
