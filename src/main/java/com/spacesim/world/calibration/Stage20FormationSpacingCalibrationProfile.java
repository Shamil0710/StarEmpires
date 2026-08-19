package com.spacesim.world.calibration;

import com.spacesim.ship.TacticalFormationPlanner.FormationMode;
import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.FormationProbeSample;
import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.SpatialAuthority;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stage-20A versioned acceptance bands for tactical formation spacing.
 *
 * <p>The profile promotes no Stage-19 distance to final production doctrine. Instead it records the
 * already acceptance-tested Stage-19 compact/dispersed geometries as a provisional Stage-20 world
 * calibration reference, together with the physically derived escort recovery evidence already
 * produced by {@link Stage20FormationStationSpatialCalibrationCalculator}. Missing formation modes
 * or new fleet sizes must be authored explicitly rather than inferred from class or screen scale.</p>
 *
 * @param version stable Stage-20A formation-band version
 * @param authority authority of the accepted calibration reference
 * @param sourceProfileVersion exact Stage-20A.6 source profile version
 * @param sourceDocument exact accepted Stage-19 formation document
 * @param stage22ReviewRequired whether provisional balance values require Stage-22 review
 * @param sourceSamples exact source samples retained for provenance
 * @param bands derived mode-specific acceptance bands
 */
public record Stage20FormationSpacingCalibrationProfile(
        String version,
        CalibrationAuthority authority,
        String sourceProfileVersion,
        String sourceDocument,
        boolean stage22ReviewRequired,
        List<FormationProbeSample> sourceSamples,
        List<FormationSpacingBand> bands) {
    /** Current formation-spacing closure profile version. */
    public static final String CURRENT_VERSION = "stage20a.formation-spacing-bands.v1";
    /** Accepted Stage-19 source document. */
    public static final String SOURCE_DOCUMENT = "docs/stage19i_l_tactical_formation.md";

    /** Stable Stage-20A band identities. */
    public enum BandId {
        /** Compact 4-ship and scaled 16-ship acceptance geometries. */ COMPACT_ACCEPTANCE,
        /** Explicit dispersed 4-ship acceptance geometry. */ DISPERSED_ACCEPTANCE
    }

    /**
     * Validates and deterministically freezes the profile.
     *
     * @param version stable Stage-20A formation-band version
     * @param authority authority of the accepted calibration reference
     * @param sourceProfileVersion exact Stage-20A.6 source profile version
     * @param sourceDocument exact accepted Stage-19 formation document
     * @param stage22ReviewRequired whether provisional balance values require Stage-22 review
     * @param sourceSamples exact source samples retained for provenance
     * @param bands derived mode-specific acceptance bands
     */
    public Stage20FormationSpacingCalibrationProfile {
        requireText(version, "version");
        Objects.requireNonNull(authority, "authority");
        requireText(sourceProfileVersion, "sourceProfileVersion");
        requireText(sourceDocument, "sourceDocument");
        sourceSamples = sortedSamples(sourceSamples);
        bands = sortedBands(bands);
        if (authority == CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE && !stage22ReviewRequired) {
            throw new IllegalArgumentException("provisional formation bands require Stage-22 review");
        }
    }

    /**
     * Derives the current formation bands only from the existing Stage-20A.6/Stage-19 evidence.
     *
     * @return deterministic provisional formation-spacing acceptance profile
     */
    public static Stage20FormationSpacingCalibrationProfile deriveCurrent() {
        Stage20FormationStationSpatialCalibrationProfile source =
                Stage20FormationStationSpatialCalibrationCalculator.calibrate();
        List<FormationProbeSample> samples = source.formationSamples();
        return new Stage20FormationSpacingCalibrationProfile(
                CURRENT_VERSION,
                CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE,
                source.version(),
                SOURCE_DOCUMENT,
                true,
                samples,
                List.of(
                        band(BandId.COMPACT_ACCEPTANCE, FormationMode.COMPACT, samples),
                        band(BandId.DISPERSED_ACCEPTANCE, FormationMode.DISPERSED, samples)));
    }

    /**
     * Returns whether existing acceptance-tested formation geometries are sufficient to calibrate
     * Stage-20B placement without inventing a new spacing constant.
     *
     * <p>Closure requires both authored modes, compact evidence at both 4-ship and 16-ship scales,
     * physical recovery evidence for every source sample, explicit Stage-19 provisional provenance,
     * and Stage-22 review because these are not final combat-balance values.</p>
     *
     * @return true when the Stage-20A formation-spacing readiness requirement is closed
     */
    public boolean closesStage20BEntryCoverage() {
        if (authority != CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE
                || !stage22ReviewRequired
                || !sourceProfileVersion.equals(Stage20FormationStationSpatialCalibrationProfile.CURRENT_VERSION)
                || !sourceDocument.equals(SOURCE_DOCUMENT)) {
            return false;
        }
        Set<BandId> ids = bands.stream()
                .map(FormationSpacingBand::id)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(BandId.class)));
        if (!ids.equals(EnumSet.allOf(BandId.class))) {
            return false;
        }
        boolean sourceAuthorityValid = sourceSamples.stream().allMatch(value ->
                value.authority() == SpatialAuthority.PROVISIONAL_STAGE19_TACTICAL_PROBE
                        && value.source().contains(SOURCE_DOCUMENT)
                        && value.accelerationMps2() > 0d
                        && value.idealRestToToleranceRecoveryTimeS() > 0d);
        Set<Integer> compactShipCounts = sourceSamples.stream()
                .filter(value -> value.mode() == FormationMode.COMPACT)
                .map(FormationProbeSample::shipCount)
                .collect(Collectors.toSet());
        boolean dispersedPresent = sourceSamples.stream()
                .anyMatch(value -> value.mode() == FormationMode.DISPERSED && value.shipCount() == 4);
        return sourceAuthorityValid
                && compactShipCounts.containsAll(Set.of(4, 16))
                && dispersedPresent;
    }

    /**
     * Returns the unique band with the requested stable identity.
     *
     * @param id stable band identity
     * @return matching formation spacing band
     */
    public FormationSpacingBand band(BandId id) {
        Objects.requireNonNull(id, "id");
        return bands.stream()
                .filter(value -> value.id() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing formation band: " + id));
    }

    private static FormationSpacingBand band(
            BandId id,
            FormationMode mode,
            List<FormationProbeSample> samples) {
        List<FormationProbeSample> selected = samples.stream()
                .filter(value -> value.mode() == mode)
                .toList();
        if (selected.isEmpty()) {
            throw new IllegalStateException("No Stage-19 formation samples for mode " + mode);
        }
        double minSpacing = selected.stream().mapToDouble(FormationProbeSample::spacingM).min().orElseThrow();
        double maxSpacing = selected.stream().mapToDouble(FormationProbeSample::spacingM).max().orElseThrow();
        double minRecovery = selected.stream()
                .mapToDouble(FormationProbeSample::idealRestToToleranceRecoveryTimeS).min().orElseThrow();
        double maxRecovery = selected.stream()
                .mapToDouble(FormationProbeSample::idealRestToToleranceRecoveryTimeS).max().orElseThrow();
        int minShips = selected.stream().mapToInt(FormationProbeSample::shipCount).min().orElseThrow();
        int maxShips = selected.stream().mapToInt(FormationProbeSample::shipCount).max().orElseThrow();
        return new FormationSpacingBand(
                id,
                mode,
                minShips,
                maxShips,
                minSpacing,
                maxSpacing,
                minRecovery,
                maxRecovery,
                selected.stream().map(FormationProbeSample::probeId).sorted().toList());
    }

    /**
     * One mode-specific band derived from exact accepted Stage-19 sample values.
     *
     * @param id stable band identity
     * @param mode formation mode represented by the band
     * @param minimumShipCount smallest accepted sample size
     * @param maximumShipCount largest accepted sample size
     * @param minimumSpacingM minimum accepted center-to-center spacing in meters
     * @param maximumSpacingM maximum accepted center-to-center spacing in meters
     * @param minimumIdealRecoveryTimeS minimum physical lower-bound recovery time in seconds
     * @param maximumIdealRecoveryTimeS maximum physical lower-bound recovery time in seconds
     * @param sourceProbeIds exact source probe identities
     */
    public record FormationSpacingBand(
            BandId id,
            FormationMode mode,
            int minimumShipCount,
            int maximumShipCount,
            double minimumSpacingM,
            double maximumSpacingM,
            double minimumIdealRecoveryTimeS,
            double maximumIdealRecoveryTimeS,
            List<String> sourceProbeIds) {
        /**
         * Validates one derived formation-spacing band.
         *
         * @param id stable band identity
         * @param mode formation mode represented by the band
         * @param minimumShipCount smallest accepted sample size
         * @param maximumShipCount largest accepted sample size
         * @param minimumSpacingM minimum accepted center-to-center spacing in meters
         * @param maximumSpacingM maximum accepted center-to-center spacing in meters
         * @param minimumIdealRecoveryTimeS minimum physical lower-bound recovery time in seconds
         * @param maximumIdealRecoveryTimeS maximum physical lower-bound recovery time in seconds
         * @param sourceProbeIds exact source probe identities
         */
        public FormationSpacingBand {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(mode, "mode");
            if (minimumShipCount <= 0 || maximumShipCount < minimumShipCount) {
                throw new IllegalArgumentException("invalid formation ship-count band");
            }
            requirePositive(minimumSpacingM, "minimumSpacingM");
            requirePositive(maximumSpacingM, "maximumSpacingM");
            if (maximumSpacingM < minimumSpacingM) {
                throw new IllegalArgumentException("maximumSpacingM must be >= minimumSpacingM");
            }
            requirePositive(minimumIdealRecoveryTimeS, "minimumIdealRecoveryTimeS");
            requirePositive(maximumIdealRecoveryTimeS, "maximumIdealRecoveryTimeS");
            if (maximumIdealRecoveryTimeS < minimumIdealRecoveryTimeS) {
                throw new IllegalArgumentException("maximumIdealRecoveryTimeS must be >= minimumIdealRecoveryTimeS");
            }
            Objects.requireNonNull(sourceProbeIds, "sourceProbeIds");
            ArrayList<String> ids = new ArrayList<>();
            for (String sourceProbeId : sourceProbeIds) {
                ids.add(requireText(sourceProbeId, "sourceProbeId"));
            }
            ids.sort(String::compareTo);
            if (ids.isEmpty()) {
                throw new IllegalArgumentException("sourceProbeIds must not be empty");
            }
            sourceProbeIds = List.copyOf(ids);
        }
    }

    private static List<FormationProbeSample> sortedSamples(List<FormationProbeSample> values) {
        Objects.requireNonNull(values, "sourceSamples");
        ArrayList<FormationProbeSample> copy = new ArrayList<>(values);
        if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("sourceSamples must be non-empty and contain no nulls");
        }
        copy.sort(Comparator.comparing(FormationProbeSample::probeId));
        return List.copyOf(copy);
    }

    private static List<FormationSpacingBand> sortedBands(List<FormationSpacingBand> values) {
        Objects.requireNonNull(values, "bands");
        ArrayList<FormationSpacingBand> copy = new ArrayList<>(values);
        if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("bands must be non-empty and contain no nulls");
        }
        copy.sort(Comparator.comparing(FormationSpacingBand::id));
        Map<BandId, Long> counts = copy.stream()
                .collect(Collectors.groupingBy(FormationSpacingBand::id, () -> new EnumMap<>(BandId.class), Collectors.counting()));
        if (counts.values().stream().anyMatch(value -> value != 1L)) {
            throw new IllegalArgumentException("formation band IDs must be unique");
        }
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
}
