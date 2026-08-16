package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.WeaponDefinition.Launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stage-17.5E physical ammunition consumption over the existing central consumable state.
 *
 * <p>This runtime deliberately does not create a parallel magazine counter. A fired round is
 * removed from the same {@link ConsumableState} that contributes ammunition mass to the central
 * derived-ship calculator, so firing naturally changes carried mass and ammunition endurance.</p>
 */
public final class AmmunitionRuntime {
    private static final double EPSILON = 1e-9d;

    /** Stable reason why one requested physical round cannot be removed from the feed. */
    public enum Failure {
        /** One physical round can be consumed. */ NONE,
        /** No matching ammunition load exists on the requested mount/interface. */ FEED_NOT_FOUND,
        /** Matching load has no itemized round remaining. */ ROUND_COUNT_EXHAUSTED,
        /** Matching load lacks the authored interface-native amount required for one shot. */ INTERFACE_AMOUNT_EXHAUSTED,
        /** Matching load lacks the physical mass required by the requested round definition. */ PHYSICAL_MASS_EXHAUSTED
    }

    /**
     * Immutable result of checking one launcher against the current physical load state.
     *
     * @param allowed whether one round can be consumed
     * @param failure stable rejection reason or NONE
     * @param mountId requested launcher mount
     * @param interfaceId requested module-local ammunition interface
     * @param remainingRoundCount current itemized rounds in the matching load
     * @param remainingAmount current interface-native amount in the matching load
     * @param remainingMassKg current physical ammunition mass in the matching load
     */
    public record ConsumptionPlan(
            boolean allowed,
            Failure failure,
            String mountId,
            String interfaceId,
            long remainingRoundCount,
            double remainingAmount,
            double remainingMassKg) {
        /**
         * Validates one immutable ammunition plan.
         *
         * @param allowed whether one round can be consumed
         * @param failure stable rejection reason or NONE
         * @param mountId requested launcher mount
         * @param interfaceId requested module-local ammunition interface
         * @param remainingRoundCount current itemized rounds in the matching load
         * @param remainingAmount current interface-native amount in the matching load
         * @param remainingMassKg current physical ammunition mass in the matching load
         */
        public ConsumptionPlan {
            Objects.requireNonNull(failure, "failure");
            requireNonBlank(mountId, "mountId");
            requireNonBlank(interfaceId, "interfaceId");
            if (remainingRoundCount < 0L) {
                throw new IllegalArgumentException("remainingRoundCount must be non-negative");
            }
            requireNonNegativeFinite(remainingAmount, "remainingAmount");
            requireNonNegativeFinite(remainingMassKg, "remainingMassKg");
            if (allowed != (failure == Failure.NONE)) {
                throw new IllegalArgumentException("allowed and failure must agree");
            }
        }
    }

    /**
     * Result of committing one physical ammunition item.
     *
     * @param consumables next central consumable state after removing one round
     * @param consumedMassKg physical mass removed from the ship
     * @param consumedAmount interface-native amount removed from the feed
     */
    public record ConsumptionResult(
            ConsumableState consumables,
            double consumedMassKg,
            double consumedAmount) {
        /**
         * Validates a committed physical ammunition result.
         *
         * @param consumables next central consumable state
         * @param consumedMassKg physical mass removed from the ship
         * @param consumedAmount interface-native amount removed from the feed
         */
        public ConsumptionResult {
            Objects.requireNonNull(consumables, "consumables");
            requirePositiveFinite(consumedMassKg, "consumedMassKg");
            requirePositiveFinite(consumedAmount, "consumedAmount");
        }
    }

    /**
     * Checks whether one physical round can be consumed from a launcher feed.
     *
     * @param consumables current central physical load state
     * @param mountId fitted launcher/weapon mount
     * @param launcher physical launcher/feed definition
     * @param roundMassKg physical mass of the requested round
     * @return deterministic feed plan or stable rejection reason
     */
    public ConsumptionPlan planOne(
            ConsumableState consumables,
            String mountId,
            Launcher launcher,
            double roundMassKg) {
        ConsumableState checked = Objects.requireNonNull(consumables, "consumables");
        requireNonBlank(mountId, "mountId");
        Launcher checkedLauncher = Objects.requireNonNull(launcher, "launcher");
        requirePositiveFinite(roundMassKg, "roundMassKg");

        ConsumableLoad load = findLoad(checked, mountId, checkedLauncher.ammunitionInterfaceId());
        if (load == null) {
            return rejected(mountId, checkedLauncher.ammunitionInterfaceId(), Failure.FEED_NOT_FOUND);
        }
        if (load.itemCount() < 1L) {
            return plan(false, Failure.ROUND_COUNT_EXHAUSTED, load);
        }
        if (load.amount() + EPSILON < checkedLauncher.ammunitionAmountPerShot()) {
            return plan(false, Failure.INTERFACE_AMOUNT_EXHAUSTED, load);
        }
        if (load.massKg() + EPSILON < roundMassKg) {
            return plan(false, Failure.PHYSICAL_MASS_EXHAUSTED, load);
        }
        return plan(true, Failure.NONE, load);
    }

    /**
     * Removes exactly one physical round from the existing central consumable state.
     *
     * @param consumables current central physical load state
     * @param mountId fitted launcher/weapon mount
     * @param launcher physical launcher/feed definition
     * @param roundMassKg physical mass of the fired round
     * @return next central consumable state and physical quantities consumed
     */
    public ConsumptionResult consumeOne(
            ConsumableState consumables,
            String mountId,
            Launcher launcher,
            double roundMassKg) {
        ConsumptionPlan plan = planOne(consumables, mountId, launcher, roundMassKg);
        if (!plan.allowed()) {
            throw new IllegalStateException("ammunition consumption rejected: " + plan.failure());
        }

        List<ConsumableLoad> loads = new ArrayList<>();
        boolean consumed = false;
        for (ConsumableLoad load : consumables.interfaceLoads()) {
            if (!consumed
                    && load.kind() == InterfaceKind.AMMUNITION
                    && load.mountId().equals(mountId)
                    && load.interfaceId().equals(launcher.ammunitionInterfaceId())) {
                double nextAmount = canonicalZero(load.amount() - launcher.ammunitionAmountPerShot());
                double nextMass = canonicalZero(load.massKg() - roundMassKg);
                loads.add(new ConsumableLoad(
                        load.mountId(),
                        load.interfaceId(),
                        load.kind(),
                        nextAmount,
                        nextMass,
                        load.itemCount() - 1L));
                consumed = true;
            } else {
                loads.add(load);
            }
        }
        if (!consumed) {
            throw new IllegalStateException("accepted ammunition feed disappeared before commit");
        }
        ConsumableState next = new ConsumableState(
                consumables.cargoMassKg(),
                consumables.storesMassKg(),
                consumables.missionPayloadMassKg(),
                consumables.missionIntegrationVolumeM3(),
                loads);
        return new ConsumptionResult(next, roundMassKg, launcher.ammunitionAmountPerShot());
    }

    private static ConsumableLoad findLoad(ConsumableState consumables, String mountId, String interfaceId) {
        for (ConsumableLoad load : consumables.interfaceLoads()) {
            if (load.kind() == InterfaceKind.AMMUNITION
                    && load.mountId().equals(mountId)
                    && load.interfaceId().equals(interfaceId)) {
                return load;
            }
        }
        return null;
    }

    private static ConsumptionPlan plan(boolean allowed, Failure failure, ConsumableLoad load) {
        return new ConsumptionPlan(
                allowed,
                failure,
                load.mountId(),
                load.interfaceId(),
                load.itemCount(),
                load.amount(),
                load.massKg());
    }

    private static ConsumptionPlan rejected(String mountId, String interfaceId, Failure failure) {
        return new ConsumptionPlan(false, failure, mountId, interfaceId, 0L, 0d, 0d);
    }

    private static double canonicalZero(double value) {
        return Math.abs(value) <= EPSILON ? 0d : value;
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }

    private static void requirePositiveFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}
