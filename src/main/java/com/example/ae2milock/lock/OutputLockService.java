package com.example.ae2milock.lock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import com.example.ae2milock.Ae2MiLock;
import com.example.ae2milock.lock.OutputLockPlanner.Plan;
import com.example.ae2milock.lock.OutputLockPlanner.Status;

/**
 * Bridges AE2 and Modern Industrialization: takes the pattern outputs, finds the machine's output slots and builds
 * a lock plan.
 * <p>
 * The pattern is never matched against real recipes - exactly what the player put in the pattern is locked,
 * even if no such recipe exists in the game.
 */
public final class OutputLockService {
    private OutputLockService() {
    }

    /** A prepared (not yet applied) lock for a single pattern push. */
    public static final class Prepared {
        private final boolean blocked;
        @Nullable
        private final Plan<Item> items;
        @Nullable
        private final Plan<Fluid> fluids;
        private final List<BlockEntity> dirty;

        private Prepared(boolean blocked, @Nullable Plan<Item> items, @Nullable Plan<Fluid> fluids,
                List<BlockEntity> dirty) {
            this.blocked = blocked;
            this.items = items;
            this.fluids = fluids;
            this.dirty = dirty;
        }

        /** @return {@code true} if the pattern cannot be pushed right now (the machine is busy with another recipe or clogged). */
        public boolean isBlocked() {
            return blocked;
        }

        /** Applies the locks to the output slots. Safe to call repeatedly. */
        public void apply() {
            if (blocked || items == null || fluids == null) {
                return;
            }
            boolean changed = items.apply();
            changed |= fluids.apply();
            if (changed) {
                for (BlockEntity blockEntity : dirty) {
                    blockEntity.setChanged();
                }
            }
        }
    }

    /**
     * Last message per target position, to keep the log free of repeats (the provider may try to push a pattern
     * every tick).
     */
    private static final Map<String, String> LAST_MESSAGES = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 256;
        }
    };

    /**
     * @return {@code null} if no lock is needed/possible (the target is not an MI crafting machine, or the machine
     *         has fewer slots than the pattern has products - then we behave as if the card were absent); otherwise
     *         the prepared lock, possibly already marked as {@linkplain Prepared#isBlocked() blocking the push}
     */
    @Nullable
    public static Prepared prepare(ServerLevel level, BlockPos targetPos, IPatternDetails pattern) {
        MiMachineResolver.Resolved machine = MiMachineResolver.resolve(level, targetPos);
        if (machine == null) {
            return null;
        }

        // Pattern outputs. Keys of other types (e.g. addon chemicals) cannot be produced by MI machines.
        Map<Item, Long> wantedItems = new LinkedHashMap<>();
        Map<Fluid, Long> wantedFluids = new LinkedHashMap<>();
        for (GenericStack output : pattern.getOutputs()) {
            if (output == null) {
                continue;
            }
            AEKey what = output.what();
            if (what instanceof AEItemKey itemKey) {
                wantedItems.merge(itemKey.getItem(), output.amount(), Long::sum);
            } else if (what instanceof AEFluidKey fluidKey) {
                wantedFluids.merge(fluidKey.getFluid(), output.amount(), Long::sum);
            }
        }

        if (wantedItems.isEmpty() && wantedFluids.isEmpty()) {
            return null; // the pattern has neither item nor fluid outputs - nothing to lock
        }

        Plan<Item> items = OutputLockPlanner.plan(machine.itemOutputs(), wantedItems, Items.AIR, true,
                item -> String.valueOf(BuiltInRegistries.ITEM.getKey(item)));
        Plan<Fluid> fluids = OutputLockPlanner.plan(machine.fluidOutputs(), wantedFluids, Fluids.EMPTY, false,
                fluid -> String.valueOf(BuiltInRegistries.FLUID.getKey(fluid)));

        String key = level.dimension().location() + "@" + targetPos.asLong();

        if (items.status() == Status.INSUFFICIENT || fluids.status() == Status.INSUFFICIENT) {
            String reason = items.status() == Status.INSUFFICIENT ? items.message() : fluids.message();
            log(key, targetPos, "not locking outputs: " + reason);
            return null;
        }
        if (items.status() == Status.CONFLICT || fluids.status() == Status.CONFLICT) {
            String reason = items.status() == Status.CONFLICT ? items.message() : fluids.message();
            log(key, targetPos, "pattern push postponed: " + reason);
            return new Prepared(true, null, null, machine.dirty());
        }
        if (items.size() == 0 && fluids.size() == 0) {
            return null;
        }

        synchronized (LAST_MESSAGES) {
            LAST_MESSAGES.remove(key);
        }
        return new Prepared(false, items, fluids, machine.dirty());
    }

    private static void log(String key, BlockPos pos, String message) {
        boolean changed;
        synchronized (LAST_MESSAGES) {
            changed = !message.equals(LAST_MESSAGES.put(key, message));
        }
        if (changed) {
            Ae2MiLock.LOGGER.debug("[Output Lock Card] machine at {}: {}", pos, message);
        }
    }
}
