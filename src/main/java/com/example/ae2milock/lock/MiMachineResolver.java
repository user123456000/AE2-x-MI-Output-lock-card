package com.example.ae2milock.lock;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import aztech.modern_industrialization.api.machine.holder.CrafterComponentHolder;
import aztech.modern_industrialization.inventory.ConfigurableFluidStack;
import aztech.modern_industrialization.inventory.ConfigurableItemStack;
import aztech.modern_industrialization.inventory.MIInventory;
import aztech.modern_industrialization.machines.components.CrafterComponent;
import aztech.modern_industrialization.machines.multiblocks.HatchBlockEntity;

/**
 * Determines which output slots to lock when a Pattern Provider faces a given block.
 * <p>
 * Uses only general Modern Industrialization concepts, not concrete machine classes:
 * <ul>
 * <li>any crafting machine (including addon and KubeJS machines) implements {@link CrafterComponentHolder} and
 * works through {@link CrafterComponent} - we lock its output slot list, so these are guaranteed to be the same
 * slots the machine puts recipe results into;</li>
 * <li>if the provider faces a multiblock {@link HatchBlockEntity}, the controller is found among nearby block
 * entities by shared slot objects: hatch slots are the same objects on the hatch and on the controller
 * (through its {@code MultiblockInventoryComponent}). This makes output hatches (separate blocks) lock
 * automatically.</li>
 * </ul>
 */
public final class MiMachineResolver {
    private MiMachineResolver() {
    }

    /**
     * Output slots found for a target.
     *
     * @param itemOutputs  item output slots (live lists - slot changes are immediately visible to the machine)
     * @param fluidOutputs fluid output slots
     * @param dirty        block entities to mark changed after locks are modified
     */
    public record Resolved(List<ConfigurableItemStack> itemOutputs, List<ConfigurableFluidStack> fluidOutputs,
            List<BlockEntity> dirty) {
    }

    /** Radius (in chunks) to search for the multiblock controller around a hatch. */
    private static final int SCAN_CHUNK_RADIUS = 3;
    /** How long (in ticks) to remember that no controller was found for a hatch (structure not assembled). */
    private static final long NEGATIVE_TTL_TICKS = 40;
    private static final int MAX_CACHE_ENTRIES = 512;

    private record CacheEntry(@Nullable BlockPos controller, long expiresAt) {
    }

    /** Cache of "hatch -> controller" per level. Entries with a controller are always re-validated on use. */
    private static final Map<ServerLevel, Map<BlockPos, CacheEntry>> CACHE = new WeakHashMap<>();

    /**
     * @return output slots of the machine the provider faces, or {@code null} if this is not an MI crafting machine
     *         (or the hatch is not currently part of an assembled multiblock)
     */
    @Nullable
    public static Resolved resolve(ServerLevel level, BlockPos targetPos) {
        BlockEntity target = loadedBlockEntity(level, targetPos);
        if (target == null) {
            return null;
        }

        // A regular machine (or the controller itself, if the provider faces it directly).
        CrafterComponent crafter = crafterOf(target);
        if (crafter != null) {
            return describe(crafter, List.of(target));
        }

        // Multiblock hatch: lock the controller's slots, i.e. the output hatches.
        if (target instanceof HatchBlockEntity hatch) {
            BlockEntity controller = findController(level, hatch);
            if (controller != null) {
                CrafterComponent controllerCrafter = crafterOf(controller);
                if (controllerCrafter != null) {
                    return describe(controllerCrafter, List.of(controller, hatch));
                }
            }
        }
        return null;
    }

    private static Resolved describe(CrafterComponent crafter, List<BlockEntity> dirty) {
        CrafterComponent.Inventory inventory = crafter.getInventory();
        return new Resolved(inventory.getItemOutputs(), inventory.getFluidOutputs(), dirty);
    }

    @Nullable
    private static CrafterComponent crafterOf(BlockEntity blockEntity) {
        if (blockEntity instanceof CrafterComponentHolder holder
                && holder.getCrafterComponent() instanceof CrafterComponent crafter) {
            return crafter;
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Finding the multiblock controller for a hatch
    // ------------------------------------------------------------------------------------------------------------

    @Nullable
    private static BlockEntity findController(ServerLevel level, HatchBlockEntity hatch) {
        Set<Object> hatchStacks = stacksOf(hatch);
        if (hatchStacks.isEmpty()) {
            return null; // the hatch has no item/fluid slots (e.g. an energy hatch) - nothing to link by
        }

        BlockPos hatchPos = hatch.getBlockPos();
        long now = level.getGameTime();
        Map<BlockPos, CacheEntry> levelCache = CACHE.computeIfAbsent(level, l -> new HashMap<>());

        CacheEntry cached = levelCache.get(hatchPos);
        if (cached != null) {
            if (cached.controller() != null) {
                BlockEntity candidate = loadedBlockEntity(level, cached.controller());
                if (candidate != null && candidate != hatch && isLinked(candidate, hatchStacks)) {
                    return candidate;
                }
                levelCache.remove(hatchPos); // structure was rebuilt or destroyed
            } else if (now < cached.expiresAt()) {
                return null; // already searched recently and found nothing
            }
        }

        BlockEntity found = scan(level, hatch, hatchStacks);

        if (levelCache.size() >= MAX_CACHE_ENTRIES) {
            levelCache.clear();
        }
        levelCache.put(hatchPos.immutable(), found == null
                ? new CacheEntry(null, now + NEGATIVE_TTL_TICKS)
                : new CacheEntry(found.getBlockPos().immutable(), Long.MAX_VALUE));
        return found;
    }

    /** Iterates over block entities in loaded chunks around the hatch, nearest chunks first. */
    @Nullable
    private static BlockEntity scan(ServerLevel level, HatchBlockEntity hatch, Set<Object> hatchStacks) {
        BlockPos hatchPos = hatch.getBlockPos();
        int chunkX = hatchPos.getX() >> 4;
        int chunkZ = hatchPos.getZ() >> 4;

        for (int radius = 0; radius <= SCAN_CHUNK_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue; // only the ring at the current radius
                    }
                    LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX + dx, chunkZ + dz);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity candidate : chunk.getBlockEntities().values()) {
                        if (candidate != hatch && isLinked(candidate, hatchStacks)) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Hatch slots; compared strictly by reference, hence the identity set. */
    private static Set<Object> stacksOf(HatchBlockEntity hatch) {
        Set<Object> stacks = Collections.newSetFromMap(new IdentityHashMap<>());
        MIInventory inventory = hatch.getInventory();
        stacks.addAll(inventory.getItemStacks());
        stacks.addAll(inventory.getFluidStacks());
        return stacks;
    }

    private static boolean isLinked(BlockEntity candidate, Set<Object> hatchStacks) {
        CrafterComponent crafter = crafterOf(candidate);
        if (crafter == null) {
            return false;
        }
        CrafterComponent.Inventory inventory = crafter.getInventory();
        return containsAny(inventory.getItemInputs(), hatchStacks)
                || containsAny(inventory.getItemOutputs(), hatchStacks)
                || containsAny(inventory.getFluidInputs(), hatchStacks)
                || containsAny(inventory.getFluidOutputs(), hatchStacks);
    }

    private static boolean containsAny(List<?> stacks, Set<Object> identitySet) {
        for (Object stack : stacks) {
            if (identitySet.contains(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Block entity, but only if the chunk is already loaded: the lookup must not load or generate chunks. */
    @Nullable
    private static BlockEntity loadedBlockEntity(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockEntity(pos);
    }
}
