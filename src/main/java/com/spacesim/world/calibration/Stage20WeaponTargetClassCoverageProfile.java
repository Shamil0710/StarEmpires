package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stage-20A closure profile for weapon time-of-flight/effectiveness against representative targets.
 *
 * <p>The class-level effectiveness evidence is the accepted v0.3 analytical P50 direct-fire
 * benchmark. It is explicitly provisional authoring evidence, not production lethality content and
 * not a hard range wall. Production Stage-20A.5 remains the authority that kinetic, beam, guided and
 * layered-defense families have executable physical spatial runtimes. Missing benchmark rows are
 * preserved as unsupported instead of being interpolated from hull class.</p>
 *
 * @param version stable closure-profile version
 * @param authority authority of the class-level P50 benchmark
 * @param benchmarkProvenanceId exact accepted benchmark source
 * @param stage22ReviewRequired whether provisional benchmark content must be reviewed in Stage 22
 * @param productionSpatialProfileVersion production-backed Stage-20A.5 spatial profile consumed
 * @param productionSpatialFamilies weapon families with executable Stage-20A.5 spatial evidence
 * @param targets accepted representative physical target references
 * @param kineticP50Samples authored P50 direct-fire target-class rows
 */
public record Stage20WeaponTargetClassCoverageProfile(
        String version,
        CalibrationAuthority authority,
        String benchmarkProvenanceId,
        boolean stage22ReviewRequired,
        String productionSpatialProfileVersion,
        Set<SpatialFamily> productionSpatialFamilies,
        List<TargetReference> targets,
        List<KineticP50Sample> kineticP50Samples) {
    /** Current representative weapon/target-class closure profile version. */
    public static final String CURRENT_VERSION = "stage20a.weapon-target-coverage.v1";
    /** Accepted authoring benchmark consumed by this closure. */
    public static final String BENCHMARK_PROVENANCE =
            "docs/benchmarks/weapon_interaction_reference_v0_3.json";

    /** Production weapon/defense runtime families whose geometry must remain executable. */
    public enum SpatialFamily {
        /** Unguided kinetic direct fire. */ KINETIC_DIRECT_FIRE,
        /** Beam direct fire. */ BEAM_DIRECT_FIRE,
        /** Guided anti-ship strike. */ GUIDED_STRIKE,
        /** Layered missile/interceptor defense. */ LAYERED_DEFENSE
    }

    /** Target classes explicitly present in the accepted v0.3 target benchmark table. */
    public enum TargetClass {
        /** Small combatant benchmark. */ CORVETTE,
        /** Frigate benchmark. */ FRIGATE,
        /** Destroyer benchmark; v0.3 authors geometry but no P50 row. */ DESTROYER,
        /** Cruiser benchmark. */ CRUISER,
        /** Battlecruiser benchmark. */ BATTLECRUISER,
        /** Capital battleship benchmark. */ BATTLESHIP
    }

    /**
     * Validates and deterministically freezes the weapon/target closure profile.
     *
     * @param version stable closure-profile version
     * @param authority authority of the class-level P50 benchmark
     * @param benchmarkProvenanceId exact accepted benchmark source
     * @param stage22ReviewRequired whether provisional benchmark content must be reviewed in Stage 22
     * @param productionSpatialProfileVersion production-backed Stage-20A.5 spatial profile consumed
     * @param productionSpatialFamilies weapon families with executable Stage-20A.5 spatial evidence
     * @param targets accepted representative physical target references
     * @param kineticP50Samples authored P50 direct-fire target-class rows
     */
    public Stage20WeaponTargetClassCoverageProfile {
        requireText(version, "version");
        Objects.requireNonNull(authority, "authority");
        requireText(benchmarkProvenanceId, "benchmarkProvenanceId");
        requireText(productionSpatialProfileVersion, "productionSpatialProfileVersion");
        Objects.requireNonNull(productionSpatialFamilies, "productionSpatialFamilies");
        if (productionSpatialFamilies.isEmpty() || productionSpatialFamilies.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("productionSpatialFamilies must be non-empty and contain no nulls");
        }
        productionSpatialFamilies = Set.copyOf(productionSpatialFamilies);
        targets = sortedUniqueTargets(targets);
        kineticP50Samples = sortedUniqueSamples(kineticP50Samples);
        if (authority == CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE && !stage22ReviewRequired) {
            throw new IllegalArgumentException("provisional weapon benchmark requires Stage-22 review");
        }
    }

    /**
     * Derives the current closure from accepted v0.3 target rows plus production Stage-20A.5 runtime evidence.
     *
     * @return deterministic weapon/target-class coverage profile
     */
    public static Stage20WeaponTargetClassCoverageProfile deriveCurrent() {
        Stage20WeaponSpatialCalibrationProfile spatial = Stage20WeaponSpatialCalibrationCalculator.calibrate();
        EnumSet<SpatialFamily> families = EnumSet.noneOf(SpatialFamily.class);
        if (!spatial.kineticSamples().isEmpty()) {
            families.add(SpatialFamily.KINETIC_DIRECT_FIRE);
        }
        if (!spatial.beamSamples().isEmpty()) {
            families.add(SpatialFamily.BEAM_DIRECT_FIRE);
        }
        if (!spatial.guidedSamples().isEmpty()) {
            families.add(SpatialFamily.GUIDED_STRIKE);
        }
        if (!spatial.defenseSamples().isEmpty()) {
            families.add(SpatialFamily.LAYERED_DEFENSE);
        }

        return new Stage20WeaponTargetClassCoverageProfile(
                CURRENT_VERSION,
                CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE,
                BENCHMARK_PROVENANCE,
                true,
                Stage20WeaponSpatialCalibrationProfile.CURRENT_VERSION,
                families,
                targetReferences(),
                p50Samples());
    }

    /**
     * Returns target classes whose v0.3 geometry is authored but whose P50 direct-fire row is absent.
     *
     * @return immutable target-class list in enum order
     */
    public List<TargetClass> unsupportedP50Targets() {
        Set<TargetClass> covered = kineticP50Samples.stream()
                .map(KineticP50Sample::targetClass)
                .collect(Collectors.toSet());
        return targets.stream()
                .map(TargetReference::targetClass)
                .filter(value -> !covered.contains(value))
                .sorted()
                .toList();
    }

    /**
     * Returns whether representative class-level weapon effectiveness/time-of-flight is sufficient
     * for Stage-20B entry without inventing unauthored benchmark rows.
     *
     * <p>Closure is representative rather than exhaustive: it requires every P50 row actually
     * authored by v0.3 (small through capital targets), keeps the destroyer gap explicit, and also
     * requires executable production spatial evidence for all four Stage-20A.5 weapon/defense
     * families.</p>
     *
     * @return true when the current representative weapon/target evidence closes the readiness item
     */
    public boolean closesStage20BEntryCoverage() {
        Set<TargetClass> expectedTargets = EnumSet.allOf(TargetClass.class);
        Set<TargetClass> actualTargets = targets.stream()
                .map(TargetReference::targetClass)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TargetClass.class)));
        Set<TargetClass> expectedP50 = EnumSet.of(
                TargetClass.CORVETTE,
                TargetClass.FRIGATE,
                TargetClass.CRUISER,
                TargetClass.BATTLECRUISER,
                TargetClass.BATTLESHIP);
        Set<TargetClass> actualP50 = kineticP50Samples.stream()
                .map(KineticP50Sample::targetClass)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TargetClass.class)));
        boolean physicalRows = kineticP50Samples.stream().allMatch(KineticP50Sample::isPhysicallyConsistent);
        return authority == CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE
                && stage22ReviewRequired
                && benchmarkProvenanceId.equals(BENCHMARK_PROVENANCE)
                && productionSpatialFamilies.equals(EnumSet.allOf(SpatialFamily.class))
                && actualTargets.equals(expectedTargets)
                && actualP50.equals(expectedP50)
                && unsupportedP50Targets().equals(List.of(TargetClass.DESTROYER))
                && physicalRows;
    }

    private static List<TargetReference> targetReferences() {
        return List.of(
                new TargetReference(TargetClass.CORVETTE, 135.2d, 6.560144557252417d, 0.35d, true),
                new TargetReference(TargetClass.FRIGATE, 312.0d, 9.965574970333758d, 0.25d, true),
                new TargetReference(TargetClass.DESTROYER, 618.8d, 14.034605714822547d, 0.20d, false),
                new TargetReference(TargetClass.CRUISER, 1608.75d, 22.62920744078708d, 0.12d, true),
                new TargetReference(TargetClass.BATTLECRUISER, 2925.0d, 30.513217088461644d, 0.10d, true),
                new TargetReference(TargetClass.BATTLESHIP, 6077.5d, 43.98327333523493d, 0.06d, true));
    }

    private static List<KineticP50Sample> p50Samples() {
        return List.of(
                new KineticP50Sample(
                        "weapon.kinetic.m_coilgun_v0_3", TargetClass.CORVETTE,
                        25d, 15_000d, 2_812_500_000d,
                        263_037.55010316265d, 17.535836673544175d, 5.571673785515271d, 0.5d),
                new KineticP50Sample(
                        "weapon.kinetic.m_coilgun_v0_3", TargetClass.FRIGATE,
                        25d, 15_000d, 2_812_500_000d,
                        363_099.2477782086d, 24.20661651854724d, 8.463980074709088d, 0.5d),
                new KineticP50Sample(
                        "weapon.kinetic.l_coilgun_v0_3", TargetClass.CRUISER,
                        150d, 20_000d, 30_000_000_000d,
                        982_858.7432747683d, 49.14293716373842d, 19.21947920270032d, 0.5d),
                new KineticP50Sample(
                        "weapon.kinetic.l_coilgun_v0_3", TargetClass.BATTLECRUISER,
                        150d, 20_000d, 30_000_000_000d,
                        1_234_142.9580448447d, 61.707147902242234d, 25.915540470151385d, 0.5d),
                new KineticP50Sample(
                        "weapon.kinetic.xl_capital_v0_3", TargetClass.BATTLESHIP,
                        1_000d, 30_000d, 450_000_000_000d,
                        2_819_430.91298755d, 93.98103043291833d, 37.355952891641756d, 0.5d));
    }

    private static List<TargetReference> sortedUniqueTargets(List<TargetReference> values) {
        Objects.requireNonNull(values, "targets");
        ArrayList<TargetReference> copy = new ArrayList<>(values);
        if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("targets must be non-empty and contain no nulls");
        }
        copy.sort(Comparator.comparing(TargetReference::targetClass));
        if (copy.stream().map(TargetReference::targetClass).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("target classes must be unique");
        }
        return List.copyOf(copy);
    }

    private static List<KineticP50Sample> sortedUniqueSamples(List<KineticP50Sample> values) {
        Objects.requireNonNull(values, "kineticP50Samples");
        ArrayList<KineticP50Sample> copy = new ArrayList<>(values);
        if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("kineticP50Samples must be non-empty and contain no nulls");
        }
        copy.sort(Comparator.comparing(KineticP50Sample::targetClass));
        if (copy.stream().map(KineticP50Sample::targetClass).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("P50 target classes must be unique");
        }
        return List.copyOf(copy);
    }

    /**
     * One accepted physical target geometry/maneuver benchmark.
     *
     * @param targetClass benchmark hull class
     * @param projectedAreaM2 benchmark projected area in square meters
     * @param equivalentRadiusM circularized projected-area radius in meters
     * @param benchmarkLateralAccelerationMps2 benchmark lateral acceleration in m/s^2
     * @param p50RowAuthored whether v0.3 contains a P50 direct-fire row for this class
     */
    public record TargetReference(
            TargetClass targetClass,
            double projectedAreaM2,
            double equivalentRadiusM,
            double benchmarkLateralAccelerationMps2,
            boolean p50RowAuthored) {
        /**
         * Validates one target reference without deriving any missing weapon row.
         *
         * @param targetClass benchmark hull class
         * @param projectedAreaM2 benchmark projected area in square meters
         * @param equivalentRadiusM circularized projected-area radius in meters
         * @param benchmarkLateralAccelerationMps2 benchmark lateral acceleration in m/s^2
         * @param p50RowAuthored whether v0.3 contains a P50 direct-fire row for this class
         */
        public TargetReference {
            Objects.requireNonNull(targetClass, "targetClass");
            requirePositiveFinite(projectedAreaM2, "projectedAreaM2");
            requirePositiveFinite(equivalentRadiusM, "equivalentRadiusM");
            requireNonNegativeFinite(benchmarkLateralAccelerationMps2, "benchmarkLateralAccelerationMps2");
            double areaFromRadius = Math.PI * equivalentRadiusM * equivalentRadiusM;
            if (Math.abs(areaFromRadius - projectedAreaM2) > projectedAreaM2 * 1e-12d) {
                throw new IllegalArgumentException("equivalentRadiusM does not match projectedAreaM2");
            }
        }
    }

    /**
     * One authored v0.3 P50 direct-fire effectiveness/time-of-flight row.
     *
     * @param weaponId benchmark weapon identity
     * @param targetClass representative target class
     * @param projectileMassKg projectile mass in kilograms
     * @param muzzleVelocityMps muzzle velocity in meters per second
     * @param muzzleEnergyJ projectile muzzle kinetic energy in joules
     * @param p50RangeM benchmark range where single-shot hit probability is approximately 50 percent
     * @param timeOfFlightAtP50S projectile time of flight at the P50 range
     * @param oneSigmaAimPlaneAtP50M benchmark one-sigma aim-plane uncertainty at the P50 range
     * @param singleShotHitProbability benchmark single-shot hit probability
     */
    public record KineticP50Sample(
            String weaponId,
            TargetClass targetClass,
            double projectileMassKg,
            double muzzleVelocityMps,
            double muzzleEnergyJ,
            double p50RangeM,
            double timeOfFlightAtP50S,
            double oneSigmaAimPlaneAtP50M,
            double singleShotHitProbability) {
        /**
         * Validates one accepted P50 row.
         *
         * @param weaponId benchmark weapon identity
         * @param targetClass representative target class
         * @param projectileMassKg projectile mass in kilograms
         * @param muzzleVelocityMps muzzle velocity in meters per second
         * @param muzzleEnergyJ projectile muzzle kinetic energy in joules
         * @param p50RangeM benchmark P50 range in meters
         * @param timeOfFlightAtP50S projectile time of flight at P50
         * @param oneSigmaAimPlaneAtP50M one-sigma aim-plane uncertainty at P50
         * @param singleShotHitProbability benchmark single-shot hit probability
         */
        public KineticP50Sample {
            requireText(weaponId, "weaponId");
            Objects.requireNonNull(targetClass, "targetClass");
            requirePositiveFinite(projectileMassKg, "projectileMassKg");
            requirePositiveFinite(muzzleVelocityMps, "muzzleVelocityMps");
            requirePositiveFinite(muzzleEnergyJ, "muzzleEnergyJ");
            requirePositiveFinite(p50RangeM, "p50RangeM");
            requirePositiveFinite(timeOfFlightAtP50S, "timeOfFlightAtP50S");
            requirePositiveFinite(oneSigmaAimPlaneAtP50M, "oneSigmaAimPlaneAtP50M");
            if (!Double.isFinite(singleShotHitProbability)
                    || singleShotHitProbability <= 0d || singleShotHitProbability >= 1d) {
                throw new IllegalArgumentException("singleShotHitProbability must be finite in (0,1)");
            }
        }

        /**
         * Checks the physical bookkeeping encoded by the accepted benchmark row.
         *
         * @return true when time-of-flight and kinetic-energy values agree with mass/velocity inputs
         */
        public boolean isPhysicallyConsistent() {
            double expectedTof = p50RangeM / muzzleVelocityMps;
            double expectedEnergy = 0.5d * projectileMassKg * muzzleVelocityMps * muzzleVelocityMps;
            return relativeClose(timeOfFlightAtP50S, expectedTof, 1e-12d)
                    && relativeClose(muzzleEnergyJ, expectedEnergy, 1e-12d)
                    && Math.abs(singleShotHitProbability - 0.5d) <= 1e-12d;
        }
    }

    private static boolean relativeClose(double left, double right, double relativeTolerance) {
        double scale = Math.max(1d, Math.max(Math.abs(left), Math.abs(right)));
        return Math.abs(left - right) <= scale * relativeTolerance;
    }

    private static void requireText(String value, String field) {
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
