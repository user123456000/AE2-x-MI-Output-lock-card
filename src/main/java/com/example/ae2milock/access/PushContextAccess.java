package com.example.ae2milock.access;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;

/**
 * Implemented by {@code PatternProviderLogic} (through a mixin). Exposes which pattern the provider is currently
 * pushing.
 */
public interface PushContextAccess {
    /**
     * @return the pattern handled by the current {@code pushPattern} call, or {@code null} when not inside
     *         {@code pushPattern}.
     */
    @Nullable
    IPatternDetails ae2milock$getCurrentPattern();
}
