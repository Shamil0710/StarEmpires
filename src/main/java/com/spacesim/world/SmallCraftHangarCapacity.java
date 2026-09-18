package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;

import java.util.Arrays;
import java.util.Objects;

/**
 * Pure M22.8B physical capacity model shared by ship and station small-craft bays.
 *
 * <p>Capacity is expressed only in SI mass, volume, three-dimensional envelope and current physical
 * condition. There is deliberately no arbitrary craft-class count. All occupancy states consume the
 * same physical storage envelope; M22.8C later adds launch/recovery timing and service throughput.</p>
 */
public final class SmallCraftHangarCapacity {
    private static final double EPSILON = 1e-9d;

    private SmallCraftHangarCapacity() {
        throw new AssertionError("utility class");
    }

    /** Host families that may expose the same physical bay model. */
    public enum HostKind {
        /** Fitted spacecraft carrying a Stage-17.5 hangar module. */ SHIP,
        /** Station or outpost exposing an equivalent authored physical bay. */ STATION
    }

    /** Finite physical occupancy state of an embarked individual craft. */
    public enum OccupancyState {
        /** Stored and inactive. */ PARKED,
        /** Under ordinary service/maintenance work. */ SERVICING,
        /** Fully staged for future launch scheduling. */ READY,
        /** Occupying launch handling space; timing belongs to M22.8C. */ LAUNCHING,
        /** Occupying recovery handling space; timing belongs to M22.8C. */ RECOVERING
    }

    /**
     * Stable bay identity.
     *
     * @param hostStableId persistent host identity
     * @param bayStableId host-local bay or installed-module identity
     */
    public record BayId(String hostStableId, String bayStableId) implements Comparable<BayId> {
        /** Validates one stable physical bay identity.
         * @param hostStableId persistent host identity
         * @param bayStableId host-local bay identity
         */
        public BayId {
            hostStableId = requireText(hostStableId, "hostStableId");
            bayStableId = requireText(bayStableId, "bayStableId");
        }

        /** {@inheritDoc} */
        @Override
        public int compareTo(BayId other) {
            int host = hostStableId.compareTo(Objects.requireNonNull(other, "other").hostStableId);
            return host != 0 ? host : bayStableId.compareTo(other.bayStableId);
        }
    }

    /**
     * Current physical capacity of one bay.
     *
     * @param id stable bay identity
     * @param hostKind physical host family
     * @param singleCraftEnvelopeM maximum axis-aligned craft envelope accepted after arbitrary 90-degree orientation
     * @param pristineUsableVolumeM3 pristine internal volume available to embarked craft
     * @param pristineSupportedMassKg pristine total supported embarked-craft mass
     * @param conditionFraction current physical bay/module integrity in {@code [0,1]}
     */
    public record BayDefinition(
            BayId id,
            HostKind hostKind,
            Dimensions3d singleCraftEnvelopeM,
            double pristineUsableVolumeM3,
            double pristineSupportedMassKg,
            double conditionFraction) {
        /** Validates finite positive pristine capacity and bounded current condition.
         * @param id stable bay identity
         * @param hostKind physical host family
         * @param singleCraftEnvelopeM maximum accepted craft envelope
         * @param pristineUsableVolumeM3 pristine physical craft volume
         * @param pristineSupportedMassKg pristine physical craft mass
         * @param conditionFraction current physical condition
         */
        public BayDefinition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(hostKind, "hostKind");
            requireDimensions(singleCraftEnvelopeM, "singleCraftEnvelopeM");
            requirePositive(pristineUsableVolumeM3, "pristineUsableVolumeM3");
            requirePositive(pristineSupportedMassKg, "pristineSupportedMassKg");
            requireFraction(conditionFraction, "conditionFraction");
        }

        /** @return currently usable aggregate craft volume after physical damage */
        public double effectiveUsableVolumeM3() {
            return pristineUsableVolumeM3 * conditionFraction;
        }

        /** @return currently supported aggregate craft mass after physical damage */
        public double effectiveSupportedMassKg() {
            return pristineSupportedMassKg * conditionFraction;
        }

        /**
         * Tests one real hull bounding envelope against the bay door/storage envelope.
         *
         * <p>The comparison permits orthogonal orientation by sorting the three dimensions. No
         * fictional size class is involved.</p>
         *
         * @param craftEnvelopeM real craft hull bounding dimensions
         * @return whether the physical envelope can fit
         */
        public boolean acceptsEnvelope(Dimensions3d craftEnvelopeM) {
            requireDimensions(craftEnvelopeM, "craftEnvelopeM");
            double[] bay = sorted(singleCraftEnvelopeM);
            double[] craft = sorted(craftEnvelopeM);
            return craft[0] <= bay[0] + EPSILON
                    && craft[1] <= bay[1] + EPSILON
                    && craft[2] <= bay[2] + EPSILON;
        }
    }

    /**
     * Physical footprint of one individual craft for bay accounting.
     *
     * @param id stable craft identity
     * @param envelopeM authoritative hull bounding dimensions
     * @param currentMassKg current fitted/loaded physical mass
     */
    public record CraftFootprint(
            SmallCraftId id,
            Dimensions3d envelopeM,
            double currentMassKg) {
        /** Validates one finite physical craft footprint.
         * @param id stable craft identity
         * @param envelopeM hull bounding dimensions
         * @param currentMassKg current physical mass
         */
        public CraftFootprint {
            Objects.requireNonNull(id, "id");
            requireDimensions(envelopeM, "envelopeM");
            requirePositive(currentMassKg, "currentMassKg");
        }

        /** @return axis-aligned bounding volume used for deterministic storage accounting */
        public double envelopeVolumeM3() {
            return envelopeM.lengthM() * envelopeM.widthM() * envelopeM.heightM();
        }
    }

    /**
     * Aggregate occupied physical resources in one bay.
     *
     * @param craftCount individual embarked craft count for diagnostics only
     * @param occupiedMassKg actual current craft mass
     * @param occupiedEnvelopeVolumeM3 summed physical bounding-envelope volume
     */
    public record Usage(int craftCount, double occupiedMassKg, double occupiedEnvelopeVolumeM3) {
        /** Validates one non-negative usage snapshot.
         * @param craftCount diagnostic individual-craft count
         * @param occupiedMassKg aggregate current mass
         * @param occupiedEnvelopeVolumeM3 aggregate envelope volume
         */
        public Usage {
            if (craftCount < 0) {
                throw new IllegalArgumentException("craftCount cannot be negative");
            }
            requireNonNegative(occupiedMassKg, "occupiedMassKg");
            requireNonNegative(occupiedEnvelopeVolumeM3, "occupiedEnvelopeVolumeM3");
        }

        /** @return empty physical bay usage */
        public static Usage empty() {
            return new Usage(0, 0d, 0d);
        }

        /**
         * Adds one individual craft footprint.
         *
         * @param footprint physical craft footprint
         * @return accumulated immutable usage
         */
        public Usage plus(CraftFootprint footprint) {
            CraftFootprint checked = Objects.requireNonNull(footprint, "footprint");
            return new Usage(
                    Math.addExact(craftCount, 1),
                    occupiedMassKg + checked.currentMassKg(),
                    occupiedEnvelopeVolumeM3 + checked.envelopeVolumeM3());
        }
    }

    /** Stable capacity health classification for current occupancy. */
    public enum CapacityStatus {
        /** Current occupancy fits current damaged capacity. */ WITHIN_CAPACITY,
        /** Bay is physically inoperable at zero integrity. */ INOPERABLE,
        /** Existing craft mass exceeds damaged support capacity. */ OVER_MASS,
        /** Existing craft envelope volume exceeds damaged usable volume. */ OVER_VOLUME,
        /** Both mass and volume exceed damaged capacity. */ OVER_MASS_AND_VOLUME
    }

    /**
     * Classifies existing occupancy without deleting or teleporting craft after bay damage.
     *
     * @param bay current bay capacity
     * @param usage current physical occupancy
     * @return deterministic capacity health
     */
    public static CapacityStatus status(BayDefinition bay, Usage usage) {
        BayDefinition checkedBay = Objects.requireNonNull(bay, "bay");
        Usage checkedUsage = Objects.requireNonNull(usage, "usage");
        if (checkedBay.conditionFraction() <= EPSILON && checkedUsage.craftCount() > 0) {
            return CapacityStatus.INOPERABLE;
        }
        boolean mass = checkedUsage.occupiedMassKg() > checkedBay.effectiveSupportedMassKg() + EPSILON;
        boolean volume = checkedUsage.occupiedEnvelopeVolumeM3()
                > checkedBay.effectiveUsableVolumeM3() + EPSILON;
        if (mass && volume) {
            return CapacityStatus.OVER_MASS_AND_VOLUME;
        }
        if (mass) {
            return CapacityStatus.OVER_MASS;
        }
        if (volume) {
            return CapacityStatus.OVER_VOLUME;
        }
        return CapacityStatus.WITHIN_CAPACITY;
    }

    /**
     * Checks whether adding one craft is physically admissible at the current bay condition.
     *
     * @param bay current bay capacity
     * @param usage existing occupancy
     * @param craft candidate physical craft
     * @return whether envelope, mass and volume all fit without exceeding current capacity
     */
    public static boolean canAccept(BayDefinition bay, Usage usage, CraftFootprint craft) {
        BayDefinition checkedBay = Objects.requireNonNull(bay, "bay");
        Usage checkedUsage = Objects.requireNonNull(usage, "usage");
        CraftFootprint checkedCraft = Objects.requireNonNull(craft, "craft");
        if (checkedBay.conditionFraction() <= EPSILON || !checkedBay.acceptsEnvelope(checkedCraft.envelopeM())) {
            return false;
        }
        Usage candidate = checkedUsage.plus(checkedCraft);
        return status(checkedBay, candidate) == CapacityStatus.WITHIN_CAPACITY;
    }

    private static double[] sorted(Dimensions3d dimensions) {
        double[] values = {dimensions.lengthM(), dimensions.widthM(), dimensions.heightM()};
        Arrays.sort(values);
        return values;
    }

    private static void requireDimensions(Dimensions3d value, String label) {
        Dimensions3d checked = Objects.requireNonNull(value, label);
        requirePositive(checked.lengthM(), label + ".lengthM");
        requirePositive(checked.widthM(), label + ".widthM");
        requirePositive(checked.heightM(), label + ".heightM");
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }

    private static void requirePositive(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }

    private static void requireFraction(double value, String label) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(label + " must be in [0,1]");
        }
    }
}
