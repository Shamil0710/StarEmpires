package com.spacesim.world.calibration;

import com.spacesim.ship.TacticalFormationPlanner.FormationMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Versioned Stage-20A.6 formation and station spatial-calibration evidence.
 *
 * <p>Stage-19 formation distances remain authored tactical probes. Stage-18 station capacity and
 * throughput remain industrial authority and are never converted into station dimensions. Station
 * geometry may be supplied only by an explicit physical design authority with preserved provenance.</p>
 *
 * @param version stable profile version
 * @param formationSamples deterministic formation calibration samples
 * @param stationGeometrySamples deterministic station-geometry inventory
 * @param shipyardBerthSamples production-authoritative shipyard berth evidence
 * @param unresolvedConstraints machine-visible unresolved physical closure
 */
public record Stage20FormationStationSpatialCalibrationProfile(
        String version,
        List<FormationProbeSample> formationSamples,
        List<StationGeometrySample> stationGeometrySamples,
        List<ShipyardBerthSample> shipyardBerthSamples,
        List<String> unresolvedConstraints) {
    /** Current Stage-20A.6 profile version. */
    public static final String CURRENT_VERSION = "stage20a.formation-station-spatial.v2";

    /**
     * Creates a deterministically ordered immutable profile.
     *
     * @param version stable profile version
     * @param formationSamples deterministic formation calibration samples
     * @param stationGeometrySamples deterministic station-geometry inventory
     * @param shipyardBerthSamples production-authoritative shipyard berth evidence
     * @param unresolvedConstraints machine-visible unresolved physical closure
     */
    public Stage20FormationStationSpatialCalibrationProfile {
        requireText(version, "version");
        formationSamples = sortedCopy(
                formationSamples,
                Comparator.comparing(FormationProbeSample::probeId),
                "formationSamples");
        stationGeometrySamples = sortedCopy(
                stationGeometrySamples,
                Comparator.comparing(StationGeometrySample::stationArchetypeId),
                "stationGeometrySamples");
        shipyardBerthSamples = sortedCopy(
                shipyardBerthSamples,
                Comparator.comparing(ShipyardBerthSample::yardId),
                "shipyardBerthSamples");
        unresolvedConstraints = sortedStrings(unresolvedConstraints, "unresolvedConstraints");
    }

    /** Authority/provenance class for one spatial value. */
    public enum SpatialAuthority {
        /** Existing production content/runtime owns the physical value. */
        PRODUCTION_AUTHORITATIVE,
        /** Explicit Stage-20 provisional physical design input pending Stage-22 promotion/review. */
        PROVISIONAL_STAGE20_DESIGN_REFERENCE,
        /** Existing Stage-19 scenario geometry is retained only as a calibration probe. */
        PROVISIONAL_STAGE19_TACTICAL_PROBE,
        /** Required spatial value is absent from current authoritative content. */
        UNRESOLVED
    }

    /**
     * One formation probe derived from authored Stage-19 geometry plus physical acceleration.
     *
     * @param probeId stable Stage-20 probe ID
     * @param authority explicit authority of the authored spacing geometry
     * @param source exact source/provenance description
     * @param mode Stage-19 authored formation mode
     * @param shipCount ships assigned to one line
     * @param spacingM center-to-center authored slot spacing
     * @param slotToleranceM Stage-19 slot tolerance
     * @param breakDistanceM Stage-19 observable break distance
     * @param lineSpanM distance between outermost slot centers
     * @param outerSlotOffsetM absolute outermost slot-center offset from line center
     * @param accelerationMps2 physically derived representative acceleration used for recovery evidence
     * @param recoveryDistanceToToleranceM distance from break threshold to slot tolerance
     * @param idealRestToToleranceRecoveryTimeS symmetric full-acceleration lower-bound recovery time
     */
    public record FormationProbeSample(
            String probeId,
            SpatialAuthority authority,
            String source,
            FormationMode mode,
            int shipCount,
            double spacingM,
            double slotToleranceM,
            double breakDistanceM,
            double lineSpanM,
            double outerSlotOffsetM,
            double accelerationMps2,
            double recoveryDistanceToToleranceM,
            double idealRestToToleranceRecoveryTimeS) {
        /**
         * Validates one formation calibration sample.
         *
         * @param probeId stable Stage-20 probe ID
         * @param authority authority of the authored spacing geometry
         * @param source exact source/provenance description
         * @param mode Stage-19 authored formation mode
         * @param shipCount ships assigned to the calibrated line
         * @param spacingM center-to-center authored slot spacing
         * @param slotToleranceM Stage-19 slot tolerance
         * @param breakDistanceM Stage-19 observable break distance
         * @param lineSpanM distance between outermost slot centers
         * @param outerSlotOffsetM absolute outermost slot-center offset from line center
         * @param accelerationMps2 physically derived representative acceleration
         * @param recoveryDistanceToToleranceM distance from break threshold to slot tolerance
         * @param idealRestToToleranceRecoveryTimeS symmetric full-acceleration lower-bound recovery time
         */
        public FormationProbeSample {
            requireText(probeId, "probeId");
            Objects.requireNonNull(authority, "authority");
            requireText(source, "source");
            Objects.requireNonNull(mode, "mode");
            if (shipCount <= 0) {
                throw new IllegalArgumentException("shipCount must be positive");
            }
            requirePositive(spacingM, "spacingM");
            requirePositive(slotToleranceM, "slotToleranceM");
            requirePositive(breakDistanceM, "breakDistanceM");
            requireNonNegative(lineSpanM, "lineSpanM");
            requireNonNegative(outerSlotOffsetM, "outerSlotOffsetM");
            requirePositive(accelerationMps2, "accelerationMps2");
            requireNonNegative(recoveryDistanceToToleranceM, "recoveryDistanceToToleranceM");
            requireNonNegative(idealRestToToleranceRecoveryTimeS, "idealRestToToleranceRecoveryTimeS");
        }
    }

    /**
     * Machine-visible station placement geometry inventory.
     *
     * <p>Empty optionals represent unresolved physical closure. Capacity, facility count or transfer
     * throughput must never be used as a fallback dimension.</p>
     *
     * @param stationArchetypeId stable Stage-18 station archetype ID
     * @param authority authority of the spatial values
     * @param source exact content/runtime/design provenance
     * @param footprintLengthM physical station length when authored
     * @param footprintWidthM physical station width when authored
     * @param dockingApproachClearanceM required docking-approach clearance when authored
     * @param trafficClearanceM required traffic clearance when authored
     * @param unresolvedReasons explicit reasons that placement geometry remains incomplete
     */
    public record StationGeometrySample(
            String stationArchetypeId,
            SpatialAuthority authority,
            String source,
            OptionalDouble footprintLengthM,
            OptionalDouble footprintWidthM,
            OptionalDouble dockingApproachClearanceM,
            OptionalDouble trafficClearanceM,
            List<String> unresolvedReasons) {
        /**
         * Validates and freezes one station geometry inventory entry.
         *
         * @param stationArchetypeId stable Stage-18 station archetype ID
         * @param authority authority of the spatial values
         * @param source exact content/runtime/design provenance
         * @param footprintLengthM physical station length when authored
         * @param footprintWidthM physical station width when authored
         * @param dockingApproachClearanceM required docking-approach clearance when authored
         * @param trafficClearanceM required traffic clearance when authored
         * @param unresolvedReasons explicit reasons that placement geometry remains incomplete
         */
        public StationGeometrySample {
            requireText(stationArchetypeId, "stationArchetypeId");
            Objects.requireNonNull(authority, "authority");
            requireText(source, "source");
            footprintLengthM = checkedOptional(footprintLengthM, "footprintLengthM");
            footprintWidthM = checkedOptional(footprintWidthM, "footprintWidthM");
            dockingApproachClearanceM = checkedOptional(dockingApproachClearanceM, "dockingApproachClearanceM");
            trafficClearanceM = checkedOptional(trafficClearanceM, "trafficClearanceM");
            unresolvedReasons = sortedStrings(unresolvedReasons, "unresolvedReasons");
            if (placementValuesComplete(
                    footprintLengthM,
                    footprintWidthM,
                    dockingApproachClearanceM,
                    trafficClearanceM)
                    && authority == SpatialAuthority.UNRESOLVED) {
                throw new IllegalArgumentException("complete station geometry cannot have UNRESOLVED authority");
            }
        }

        /** @return whether this entry contains enough geometry for a conservative placement envelope */
        public boolean placementReady() {
            return placementValuesComplete(
                    footprintLengthM,
                    footprintWidthM,
                    dockingApproachClearanceM,
                    trafficClearanceM);
        }
    }

    /**
     * Existing physical berth envelope; it is not promoted to full station footprint.
     *
     * @param yardId stable Stage-18 shipyard ID
     * @param authority authority of the berth dimensions
     * @param source exact content provenance
     * @param berthLengthM physical berth length
     * @param berthWidthM physical berth width
     * @param berthHeightM physical berth height
     */
    public record ShipyardBerthSample(
            String yardId,
            SpatialAuthority authority,
            String source,
            double berthLengthM,
            double berthWidthM,
            double berthHeightM) {
        /**
         * Validates one production-authoritative physical berth sample.
         *
         * @param yardId stable Stage-18 shipyard ID
         * @param authority authority of the berth dimensions
         * @param source exact content provenance
         * @param berthLengthM physical berth length
         * @param berthWidthM physical berth width
         * @param berthHeightM physical berth height
         */
        public ShipyardBerthSample {
            requireText(yardId, "yardId");
            Objects.requireNonNull(authority, "authority");
            requireText(source, "source");
            requirePositive(berthLengthM, "berthLengthM");
            requirePositive(berthWidthM, "berthWidthM");
            requirePositive(berthHeightM, "berthHeightM");
        }
    }

    /**
     * Minimum explicit station geometry input required before Stage-20 placement may derive spacing.
     *
     * @param stationArchetypeId stable station archetype ID
     * @param provenance accepted physical-geometry provenance
     * @param footprintLengthM explicit physical footprint length
     * @param footprintWidthM explicit physical footprint width
     * @param dockingApproachClearanceM explicit docking-approach clearance
     * @param trafficClearanceM explicit traffic clearance
     */
    public record StationPlacementGeometryInput(
            String stationArchetypeId,
            String provenance,
            double footprintLengthM,
            double footprintWidthM,
            double dockingApproachClearanceM,
            double trafficClearanceM) {
        /**
         * Validates explicit physical placement geometry.
         *
         * @param stationArchetypeId stable station archetype ID
         * @param provenance accepted physical-geometry provenance
         * @param footprintLengthM explicit physical footprint length
         * @param footprintWidthM explicit physical footprint width
         * @param dockingApproachClearanceM explicit docking-approach clearance
         * @param trafficClearanceM explicit traffic clearance
         */
        public StationPlacementGeometryInput {
            requireText(stationArchetypeId, "stationArchetypeId");
            requireText(provenance, "provenance");
            requirePositive(footprintLengthM, "footprintLengthM");
            requirePositive(footprintWidthM, "footprintWidthM");
            requireNonNegative(dockingApproachClearanceM, "dockingApproachClearanceM");
            requireNonNegative(trafficClearanceM, "trafficClearanceM");
        }
    }

    /**
     * Conservative top-down placement envelope derived only from explicit physical geometry.
     *
     * @param stationArchetypeId stable station archetype ID
     * @param provenance accepted physical-geometry provenance
     * @param footprintHalfDiagonalM half-diagonal of the explicit footprint
     * @param operationalClearanceM larger of docking and traffic clearance
     * @param operationalRadiusM conservative footprint-plus-clearance radius
     * @param sameClassMinimumCenterSeparationM conservative same-class center separation
     */
    public record StationPlacementEnvelope(
            String stationArchetypeId,
            String provenance,
            double footprintHalfDiagonalM,
            double operationalClearanceM,
            double operationalRadiusM,
            double sameClassMinimumCenterSeparationM) {
        /**
         * Validates one derived placement envelope.
         *
         * @param stationArchetypeId stable station archetype ID
         * @param provenance accepted physical-geometry provenance
         * @param footprintHalfDiagonalM half-diagonal of the explicit footprint
         * @param operationalClearanceM larger of docking and traffic clearance
         * @param operationalRadiusM conservative footprint-plus-clearance radius
         * @param sameClassMinimumCenterSeparationM conservative same-class center separation
         */
        public StationPlacementEnvelope {
            requireText(stationArchetypeId, "stationArchetypeId");
            requireText(provenance, "provenance");
            requirePositive(footprintHalfDiagonalM, "footprintHalfDiagonalM");
            requireNonNegative(operationalClearanceM, "operationalClearanceM");
            requirePositive(operationalRadiusM, "operationalRadiusM");
            requirePositive(sameClassMinimumCenterSeparationM, "sameClassMinimumCenterSeparationM");
        }
    }

    private static boolean placementValuesComplete(
            OptionalDouble footprintLengthM,
            OptionalDouble footprintWidthM,
            OptionalDouble dockingApproachClearanceM,
            OptionalDouble trafficClearanceM) {
        return footprintLengthM.isPresent()
                && footprintWidthM.isPresent()
                && dockingApproachClearanceM.isPresent()
                && trafficClearanceM.isPresent();
    }

    private static OptionalDouble checkedOptional(OptionalDouble value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isPresent()) {
            requireNonNegative(value.getAsDouble(), field);
        }
        return value;
    }

    private static <T> List<T> sortedCopy(List<T> values, Comparator<? super T> comparator, String field) {
        Objects.requireNonNull(values, field);
        ArrayList<T> copy = new ArrayList<>(values);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " must not contain null");
        }
        copy.sort(comparator);
        return List.copyOf(copy);
    }

    private static List<String> sortedStrings(List<String> values, String field) {
        Objects.requireNonNull(values, field);
        ArrayList<String> copy = new ArrayList<>();
        for (String value : values) {
            copy.add(requireText(value, field + " entry"));
        }
        copy.sort(String::compareTo);
        return List.copyOf(copy);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static void requirePositive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }

    private static void requireNonNegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }
}
