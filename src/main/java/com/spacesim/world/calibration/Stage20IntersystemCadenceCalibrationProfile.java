package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20FtlCalibrationProfile.JumpEdgeCalibrationSample;
import com.spacesim.world.calibration.Stage20FtlCalibrationProfile.ReferenceDriveCompatibility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Machine-readable Stage-20A inter-system cadence bands derived from accepted one-edge FTL physics.
 *
 * <p>The profile does not author a generated edge-transit distribution. It repeatedly applies the
 * current accepted reference spool/transit/cooldown law across explicit hop-count calibration probes.
 * The final destination does not pay cooldown before arrival; ready-again cadence adds the final
 * cooldown separately.</p>
 *
 * @param version stable profile version
 * @param samples compatible representative hop samples
 * @param bands named aggregate cadence bands required by the Stage-20A gate
 * @param excludedRepresentatives representatives outside the current reference-drive mass domain
 */
public record Stage20IntersystemCadenceCalibrationProfile(
        String version,
        List<HopCadenceSample> samples,
        List<CadenceBand> bands,
        List<String> excludedRepresentatives) {
    /** Current Stage-20A inter-system cadence profile version. */
    public static final String CURRENT_VERSION = "stage20a.intersystem-cadence.v1";

    private static final int NEIGHBOR_HOPS = 1;
    private static final int REGIONAL_MIN_HOPS = 3;
    private static final int REGIONAL_MAX_HOPS = 5;
    private static final int REINFORCEMENT_HOPS = 3;
    private static final Set<String> REINFORCEMENT_REPRESENTATIVES = Set.of(
            "TORPEDO_CORVETTE",
            "ESCORT_DESTROYER",
            "CRUISER");

    /** Stable semantic calibration band names. */
    public enum BandId {
        /** One explicit neighboring topology edge. */ NEIGHBOR_EDGE,
        /** Lower regional multi-hop probe. */ REGIONAL_3_HOP,
        /** Upper regional multi-hop probe. */ REGIONAL_5_HOP,
        /** Military reinforcement calibration probe. */ FLEET_REINFORCEMENT_3_HOP
    }

    /**
     * Creates an immutable deterministic cadence profile.
     *
     * @param version stable profile version
     * @param samples compatible representative hop samples
     * @param bands named aggregate cadence bands
     * @param excludedRepresentatives representatives outside the current reference-drive domain
     */
    public Stage20IntersystemCadenceCalibrationProfile {
        requireNonBlank(version, "version");
        samples = sortedCopy(samples,
                Comparator.comparing(HopCadenceSample::representativeId)
                        .thenComparingInt(HopCadenceSample::hopCount),
                "samples");
        bands = sortedCopy(bands, Comparator.comparing(value -> value.id().name()), "bands");
        Objects.requireNonNull(excludedRepresentatives, "excludedRepresentatives");
        ArrayList<String> excluded = new ArrayList<>(excludedRepresentatives);
        if (excluded.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("excludedRepresentatives must contain no blanks");
        }
        excluded.sort(String::compareTo);
        excludedRepresentatives = List.copyOf(excluded);
    }

    /**
     * Derives the current cadence profile from the accepted Stage-20 FTL profile.
     *
     * @return deterministic current profile
     */
    public static Stage20IntersystemCadenceCalibrationProfile deriveCurrent() {
        Stage20FtlCalibrationProfile ftl = Stage20FtlCalibrationProfile.deriveCurrent();
        List<JumpEdgeCalibrationSample> compatible = ftl.samples().stream()
                .filter(value -> value.compatibility() == ReferenceDriveCompatibility.COMPATIBLE)
                .toList();
        List<String> excluded = ftl.samples().stream()
                .filter(value -> value.compatibility() != ReferenceDriveCompatibility.COMPATIBLE)
                .map(JumpEdgeCalibrationSample::representativeId)
                .toList();
        if (compatible.isEmpty()) {
            throw new IllegalStateException("No compatible Stage-20 FTL representatives for cadence calibration");
        }

        List<HopCadenceSample> samples = new ArrayList<>();
        for (JumpEdgeCalibrationSample value : compatible) {
            samples.add(derive(value, NEIGHBOR_HOPS));
            samples.add(derive(value, REGIONAL_MIN_HOPS));
            samples.add(derive(value, REGIONAL_MAX_HOPS));
        }

        List<CadenceBand> bands = List.of(
                band(BandId.NEIGHBOR_EDGE, NEIGHBOR_HOPS, compatible, samples, false),
                band(BandId.REGIONAL_3_HOP, REGIONAL_MIN_HOPS, compatible, samples, false),
                band(BandId.REGIONAL_5_HOP, REGIONAL_MAX_HOPS, compatible, samples, false),
                band(BandId.FLEET_REINFORCEMENT_3_HOP, REINFORCEMENT_HOPS, compatible, samples, true));
        return new Stage20IntersystemCadenceCalibrationProfile(CURRENT_VERSION, samples, bands, excluded);
    }

    private static HopCadenceSample derive(JumpEdgeCalibrationSample edge, int hops) {
        if (hops <= 0) {
            throw new IllegalArgumentException("hops must be positive");
        }
        double spool = edge.spoolTimeS().orElseThrow();
        double transit = edge.referenceEdgeTransitTimeS();
        double cooldown = edge.cooldownS();
        double arrival = hops * (spool + transit) + (hops - 1d) * cooldown;
        double readyAgain = arrival + cooldown;
        return new HopCadenceSample(
                edge.representativeId(),
                hops,
                spool,
                transit,
                cooldown,
                arrival,
                readyAgain,
                edge.shipProvenanceId(),
                edge.ftlProvenanceId());
    }

    private static CadenceBand band(
            BandId id,
            int hops,
            List<JumpEdgeCalibrationSample> compatible,
            List<HopCadenceSample> samples,
            boolean reinforcementOnly) {
        Set<String> includedIds = compatible.stream()
                .map(JumpEdgeCalibrationSample::representativeId)
                .filter(value -> !reinforcementOnly || REINFORCEMENT_REPRESENTATIVES.contains(value))
                .collect(java.util.stream.Collectors.toSet());
        List<HopCadenceSample> selected = samples.stream()
                .filter(value -> value.hopCount() == hops)
                .filter(value -> includedIds.contains(value.representativeId()))
                .toList();
        if (selected.isEmpty()) {
            throw new IllegalStateException("No samples for cadence band " + id);
        }
        return new CadenceBand(
                id,
                hops,
                selected.stream().map(HopCadenceSample::representativeId).sorted().toList(),
                selected.stream().mapToDouble(HopCadenceSample::arrivalTimeS).min().orElseThrow(),
                selected.stream().mapToDouble(HopCadenceSample::arrivalTimeS).max().orElseThrow(),
                selected.stream().mapToDouble(HopCadenceSample::readyAgainTimeS).min().orElseThrow(),
                selected.stream().mapToDouble(HopCadenceSample::readyAgainTimeS).max().orElseThrow());
    }

    /**
     * One representative hop-count cadence consequence.
     *
     * @param representativeId stable representative role ID
     * @param hopCount explicit neighboring-edge count
     * @param spoolPerEdgeS accepted mass-sensitive spool time per edge
     * @param transitPerEdgeS accepted current reference edge-transit probe
     * @param cooldownBetweenEdgesS accepted cooldown before another jump
     * @param arrivalTimeS time to arrive after the final edge, excluding final cooldown
     * @param readyAgainTimeS arrival plus final cooldown
     * @param shipProvenanceId exact translated-mass provenance
     * @param ftlProvenanceId exact FTL-law provenance
     */
    public record HopCadenceSample(
            String representativeId,
            int hopCount,
            double spoolPerEdgeS,
            double transitPerEdgeS,
            double cooldownBetweenEdgesS,
            double arrivalTimeS,
            double readyAgainTimeS,
            String shipProvenanceId,
            String ftlProvenanceId) {
        /**
         * Validates one hop-cadence sample.
         *
         * @param representativeId stable representative role ID
         * @param hopCount explicit neighboring-edge count
         * @param spoolPerEdgeS spool time per edge
         * @param transitPerEdgeS edge transit time
         * @param cooldownBetweenEdgesS cooldown between jumps
         * @param arrivalTimeS final-arrival time
         * @param readyAgainTimeS arrival plus final cooldown
         * @param shipProvenanceId translated-mass provenance
         * @param ftlProvenanceId FTL-law provenance
         */
        public HopCadenceSample {
            requireNonBlank(representativeId, "representativeId");
            if (hopCount <= 0) {
                throw new IllegalArgumentException("hopCount must be positive");
            }
            requirePositiveFinite(spoolPerEdgeS, "spoolPerEdgeS");
            requirePositiveFinite(transitPerEdgeS, "transitPerEdgeS");
            requireNonNegativeFinite(cooldownBetweenEdgesS, "cooldownBetweenEdgesS");
            requirePositiveFinite(arrivalTimeS, "arrivalTimeS");
            requirePositiveFinite(readyAgainTimeS, "readyAgainTimeS");
            requireNonBlank(shipProvenanceId, "shipProvenanceId");
            requireNonBlank(ftlProvenanceId, "ftlProvenanceId");
            if (readyAgainTimeS < arrivalTimeS) {
                throw new IllegalArgumentException("readyAgainTimeS cannot precede arrivalTimeS");
            }
        }
    }

    /**
     * Aggregate accepted cadence band across a named representative subset.
     *
     * @param id semantic calibration band ID
     * @param hopCount explicit hop count
     * @param representativeIds included compatible representative IDs
     * @param minArrivalTimeS fastest arrival in the included set
     * @param maxArrivalTimeS slowest arrival in the included set
     * @param minReadyAgainTimeS fastest ready-again cadence
     * @param maxReadyAgainTimeS slowest ready-again cadence
     */
    public record CadenceBand(
            BandId id,
            int hopCount,
            List<String> representativeIds,
            double minArrivalTimeS,
            double maxArrivalTimeS,
            double minReadyAgainTimeS,
            double maxReadyAgainTimeS) {
        /**
         * Validates one aggregate cadence band.
         *
         * @param id semantic calibration band ID
         * @param hopCount explicit hop count
         * @param representativeIds included representative IDs
         * @param minArrivalTimeS fastest arrival
         * @param maxArrivalTimeS slowest arrival
         * @param minReadyAgainTimeS fastest ready-again cadence
         * @param maxReadyAgainTimeS slowest ready-again cadence
         */
        public CadenceBand {
            Objects.requireNonNull(id, "id");
            if (hopCount <= 0) {
                throw new IllegalArgumentException("hopCount must be positive");
            }
            Objects.requireNonNull(representativeIds, "representativeIds");
            if (representativeIds.isEmpty() || representativeIds.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("representativeIds must be non-empty and contain no blanks");
            }
            representativeIds = List.copyOf(representativeIds);
            requirePositiveFinite(minArrivalTimeS, "minArrivalTimeS");
            requirePositiveFinite(maxArrivalTimeS, "maxArrivalTimeS");
            requirePositiveFinite(minReadyAgainTimeS, "minReadyAgainTimeS");
            requirePositiveFinite(maxReadyAgainTimeS, "maxReadyAgainTimeS");
            if (minArrivalTimeS > maxArrivalTimeS || minReadyAgainTimeS > maxReadyAgainTimeS) {
                throw new IllegalArgumentException("cadence band minima cannot exceed maxima");
            }
        }
    }

    private static <T> List<T> sortedCopy(List<T> values, Comparator<? super T> comparator, String field) {
        Objects.requireNonNull(values, field);
        ArrayList<T> copy = new ArrayList<>(values);
        if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " must be non-empty and contain no null entries");
        }
        copy.sort(comparator);
        return List.copyOf(copy);
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }
}
