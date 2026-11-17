package com.example.ae2milock.lock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import aztech.modern_industrialization.inventory.AbstractConfigurableStack;
import aztech.modern_industrialization.util.Simulation;

/**
 * Output slot lock planner.
 * <p>
 * Works with Modern Industrialization's abstract slots ({@link AbstractConfigurableStack}), so it fits both item
 * ({@code ConfigurableItemStack}) and fluid ({@code ConfigurableFluidStack}) slots. The algorithm does not depend
 * on recipes: it only needs the set of "allowed" products.
 *
 * <h2>Rules</h2>
 * <ul>
 * <li>every allowed product gets at least one free slot locked to it;</li>
 * <li>for items, if the pattern amount does not fit into one slot, the required number of slots is reserved;</li>
 * <li>all other slots are locked to "nothing" ({@code Items.AIR} / {@code Fluids.EMPTY}) - nothing can be put
 * there;</li>
 * <li>slots the player cannot lock ({@code canPlayerLock() == false}) are ignored - the machine controls them;</li>
 * <li>if a slot already holds a product from the pattern, that slot is locked;</li>
 * <li>if a lock cannot be placed safely (a slot holds a "foreign" resource or is reserved by the machine for a
 * running recipe with a different result), the plan is marked {@link Status#CONFLICT} - the caller must wait
 * rather than break the machine's current work.</li>
 * </ul>
 */
public final class OutputLockPlanner {
    private OutputLockPlanner() {
    }

    public enum Status {
        /** The plan is ready and can be applied. */
        OK,
        /** The lock cannot be applied right now: the machine is busy with another recipe or the output holds "foreign" resources. */
        CONFLICT,
        /** The machine has fewer output slots than the pattern has distinct products - the result cannot be guaranteed. */
        INSUFFICIENT
    }

    /**
     * Planning result for one resource kind (items or fluids).
     *
     * @param <T> resource type ({@code Item} or {@code Fluid})
     */
    public static final class Plan<T> {
        private final Status status;
        private final String message;
        private final List<AbstractConfigurableStack<T, ?>> slots;
        private final List<T> targets;

        private Plan(Status status, String message, List<AbstractConfigurableStack<T, ?>> slots, List<T> targets) {
            this.status = status;
            this.message = message;
            this.slots = slots;
            this.targets = targets;
        }

        private static <T> Plan<T> failure(Status status, String message) {
            return new Plan<>(status, message, List.of(), List.of());
        }

        public Status status() {
            return status;
        }

        /** Human-readable reason when the status is not {@link Status#OK}. */
        public String message() {
            return message;
        }

        /** @return the number of slots covered by the plan. */
        public int size() {
            return slots.size();
        }

        /**
         * Applies the plan to the slots.
         *
         * @return {@code true} if at least one slot changed
         */
        public boolean apply() {
            boolean changed = false;
            for (int i = 0; i < slots.size(); i++) {
                AbstractConfigurableStack<T, ?> slot = slots.get(i);
                T target = targets.get(i);

                if (slot.isPlayerLocked() && slot.getLockedInstance() == target) {
                    continue; // already locked correctly
                }
                if (slot.isPlayerLocked()) {
                    // Remove the player's old lock. A machine lock (if any) is left untouched.
                    slot.togglePlayerLock();
                }
                // This always succeeds for an empty slot, a slot with the same contents, and a slot reserved by the
                // machine for the same product (see the planner checks).
                if (slot.playerLock(target, Simulation.ACT)) {
                    changed = true;
                }
            }
            return changed;
        }
    }

    /**
     * Builds a lock plan.
     *
     * @param outputSlots   all machine output slots of this kind (items or fluids)
     * @param wanted        allowed products (key) and their total amount per cycle (value); iteration order sets
     *                      the priority for slot assignment
     * @param emptyInstance the "empty" resource ({@code Items.AIR} / {@code Fluids.EMPTY}) used to lock spare slots
     * @param capacityAware whether to account for slot capacity when reserving (useful for items: 200 items do not
     *                      fit into one slot of 64)
     * @param describer     turns a resource into a string for error messages
     */
    public static <T> Plan<T> plan(List<? extends AbstractConfigurableStack<T, ?>> outputSlots,
            Map<T, Long> wanted,
            T emptyInstance,
            boolean capacityAware,
            Function<T, String> describer) {
        // 1. Collect slots that can be managed at all.
        List<AbstractConfigurableStack<T, ?>> slots = new ArrayList<>(outputSlots.size());
        for (AbstractConfigurableStack<T, ?> slot : outputSlots) {
            if (slot.canPlayerLock()) {
                slots.add(slot);
            }
        }
        int slotCount = slots.size();

        List<T> targets = new ArrayList<>(Collections.<T>nCopies(slotCount, null));
        Map<T, Integer> assigned = new LinkedHashMap<>();

        // 2. Slots whose purpose is already determined by the machine state.
        for (int i = 0; i < slotCount; i++) {
            AbstractConfigurableStack<T, ?> slot = slots.get(i);
            T bound = null;

            if (slot.isMachineLocked()) {
                // A recipe is running: the machine reserved the slot for its result. Such a slot must not be changed.
                bound = slot.getLockedInstance();
                if (bound == null || !wanted.containsKey(bound)) {
                    return Plan.failure(Status.CONFLICT, "output slot #" + i
                            + " is reserved by a running recipe for " + describe(describer, bound));
                }
            } else if (!slot.isResourceBlank()) {
                // The slot holds a resource. It cannot be locked to "nothing".
                bound = slot.getResource().getObject();
                if (!wanted.containsKey(bound)) {
                    return Plan.failure(Status.CONFLICT, "output slot #" + i + " still contains "
                            + describe(describer, bound) + ", which is not a product of the pattern");
                }
            }

            if (bound != null) {
                targets.set(i, bound);
                assigned.merge(bound, 1, Integer::sum);
            }
        }

        // 3. At least one slot per product.
        for (T type : wanted.keySet()) {
            if (assigned.containsKey(type)) {
                continue;
            }
            int index = pickFreeSlot(slots, targets, type);
            if (index < 0) {
                return Plan.failure(Status.INSUFFICIENT, "the machine has " + slotCount
                        + " lockable output slot(s), which is not enough for " + wanted.size()
                        + " distinct product(s) of the pattern");
            }
            targets.set(index, type);
            assigned.put(type, 1);
        }

        // 4. Extra slots for amounts that do not fit into one slot (where possible).
        if (capacityAware) {
            for (Map.Entry<T, Long> entry : wanted.entrySet()) {
                T type = entry.getKey();
                long amount = entry.getValue();

                long perSlot = 0;
                for (AbstractConfigurableStack<T, ?> slot : slots) {
                    perSlot = Math.max(perSlot, slot.getTotalCapacityFor(type));
                }
                if (perSlot <= 0 || amount <= perSlot) {
                    continue;
                }

                long needed = (amount + perSlot - 1) / perSlot;
                while (assigned.get(type) < needed) {
                    int index = pickFreeSlot(slots, targets, type);
                    if (index < 0) {
                        break;
                    }
                    targets.set(index, type);
                    assigned.merge(type, 1, Integer::sum);
                }
            }
        }

        // 5. Lock everything else to "nothing": nothing but pattern products can enter the output.
        for (int i = 0; i < slotCount; i++) {
            if (targets.get(i) == null) {
                targets.set(i, emptyInstance);
            }
        }

        return new Plan<>(Status.OK, "", slots, targets);
    }

    /**
     * Picks a not-yet-assigned slot for {@code wanted}. Such slots are guaranteed to be empty and not owned by the
     * machine (otherwise they would have been assigned in step 2 or the plan would have ended in a conflict).
     * <p>
     * Priority: a slot already locked to this resource (no change needed) &gt; an unlocked slot &gt; any other.
     *
     * @return slot index, or {@code -1} if no free slots are left
     */
    private static <T> int pickFreeSlot(List<AbstractConfigurableStack<T, ?>> slots, List<T> targets, T wanted) {
        int unlocked = -1;
        int any = -1;
        for (int i = 0; i < slots.size(); i++) {
            if (targets.get(i) != null) {
                continue;
            }
            AbstractConfigurableStack<T, ?> slot = slots.get(i);
            if (slot.isPlayerLocked() && slot.getLockedInstance() == wanted) {
                return i;
            }
            if (!slot.isPlayerLocked() && unlocked < 0) {
                unlocked = i;
            }
            if (any < 0) {
                any = i;
            }
        }
        return unlocked >= 0 ? unlocked : any;
    }

    private static <T> String describe(Function<T, String> describer, T value) {
        return value == null ? "<unknown>" : describer.apply(value);
    }
}
