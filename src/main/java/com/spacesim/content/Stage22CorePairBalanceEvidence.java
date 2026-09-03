package com.spacesim.content;

import com.spacesim.content.Stage22EmpirePackageValidator.FamilyMetrics;
import com.spacesim.content.Stage22IndustrialUnionProductionState.YardSeriesState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only M22.6 pairwise evidence projection for the Empire and Industrial Union.
 *
 * <p>Every numeric value is derived from accepted package, engineering, production or persistence
 * data. Nothing in this class is consumed by gameplay and no value is a faction-wide combat or
 * economy modifier. The projection exists to make equal-burden review and freeze drift observable.</p>
 */
public final class Stage22CorePairBalanceEvidence {
    /** Stable Empire runtime/save identity. */
    public static final String EMPIRE_FACTION_ID = "faction.imperial_directorate";
    /** Stable Industrial Union runtime/save identity. */
    public static final String UNION_FACTION_ID = "faction.industrial_combine";

    private Stage22CorePairBalanceEvidence() {
        throw new AssertionError("utility class");
    }

    /**
     * Derives the current cross-package diagnostic card from accepted authorities.
     *
     * @return immutable pair evidence
     */
    public static PairEvidence deriveCurrent() {
        Stage22EmpirePackageValidator.ValidationReport empireValidation =
                Stage22EmpirePackageValidator.validateDefault();
        Stage22IndustrialUnionPackageValidator.ValidationReport unionValidation =
                Stage22IndustrialUnionPackageValidator.validateDefault();
        Stage22FactionProfileCatalog empireProfiles = Stage22EmpireFactionProfileCatalog.loadDefault();
        Stage22FactionProfileCatalog coreProfiles = Stage22FactionProfileLoader.loadDefault();

        requireProfile(empireProfiles, EMPIRE_FACTION_ID);
        requireProfile(coreProfiles, UNION_FACTION_ID);

        PackageVector empire = empireVector(empireValidation);
        PackageVector union = unionVector(unionValidation);
        UnionDisruptionVector disruption = unionDisruptionVector();

        if (empire.roleFamilyCount() != union.roleFamilyCount()) {
            throw new IllegalStateException("Core pair must expose the same required role-family floor");
        }
        if (empire.roleFamilyCount() != Stage22EmpirePackageCatalog.REQUIRED_SHIP_FAMILIES
                || union.roleFamilyCount() != Stage22IndustrialUnionPackageCatalog.REQUIRED_SHIP_FAMILIES) {
            throw new IllegalStateException("Core pair role-family floor drifted from package contracts");
        }
        if (unionValidation.maximumBuildTimeReduction() <= 0d
                || unionValidation.maximumThroughputImprovement() <= 0d) {
            throw new IllegalStateException("Industrial Union series-production advantage is not measurable");
        }
        if (!disruption.correlatedDisruption()
                || disruption.correlatedThroughputDegradation() < 0.25d
                || disruption.correlatedThroughputDegradation() <= disruption.isolatedThroughputDegradation()) {
            throw new IllegalStateException("Industrial Union commonality downside is not materially observable");
        }
        if (empire.projectionBundleMassKg() <= empire.carrierMassKg()) {
            throw new IllegalStateException("Empire remote projection no longer carries visible support burden");
        }

        PairwiseBalanceCard card = new PairwiseBalanceCard(
                "core.empire_vs_core.industrial_union",
                "finite strategic capability under production, logistics and recovery pressure",
                "Empire: preparation, protected capital infrastructure, reserves and preservation",
                "Empire: capital intensity, concentrated strategic-node dependency and remote support burden",
                "Industrial Union: series throughput, replacement tempo and commonality while flows remain healthy",
                "Industrial Union: material hunger, correlated hub/route/common-assembly disruption and finite retool debt",
                Stage22CorePairBalanceCatalog.scenarios().stream().map(value -> value.id()).toList(),
                "No faction-name damage/armor/income/repair scalar; no free supply/replacement/retool; no slot-based doctrine",
                List.of(
                        "Stage22 package/profile/manufacturing/shipyard fingerprints",
                        "Stage18 production and finite-input authorities",
                        "Stage19/21 combat, operation, territory and recovery authorities",
                        "Stage22 exact-fit visual and Character Master Prompt overlays"),
                List.of(
                        "B18-B20 human blind-test percentages require recorded review evidence in the final M22.6 report",
                        "Materially stochastic B06-B14 runners require exact-RC multi-seed evidence before freeze sign-off"));

        return new PairEvidence(
                Stage22CorePairBalanceCatalog.SUITE_VERSION,
                empireProfiles.fingerprint(),
                coreProfiles.fingerprint(),
                empire,
                union,
                disruption,
                card);
    }

    private static PackageVector empireVector(Stage22EmpirePackageValidator.ValidationReport validation) {
        Map<String, FamilyMetrics> metrics = validation.familyMetrics();
        double total = metrics.values().stream().mapToDouble(FamilyMetrics::fittedDryMassKg).sum();
        double capital = mass(metrics, "role.military.cruiser")
                + mass(metrics, "role.military.battleship")
                + mass(metrics, "role.military.carrier");
        double support = mass(metrics, "role.support.freight")
                + mass(metrics, "role.support.tanker_replenishment")
                + mass(metrics, "role.support.fleet_logistics_repair_salvage");
        double carrier = mass(metrics, "role.military.carrier");
        double projection = carrier
                + mass(metrics, "role.support.tanker_replenishment")
                + mass(metrics, "role.support.fleet_logistics_repair_salvage");
        double averageCrew = metrics.values().stream().mapToInt(FamilyMetrics::staffedCrewBurden)
                .average().orElseThrow();
        return new PackageVector(
                "core.empire",
                EMPIRE_FACTION_ID,
                validation.packageFingerprint(),
                validation.productionFingerprint(),
                validation.engineeringFingerprint(),
                validation.manufacturingFingerprint(),
                validation.shipyardFingerprint(),
                validation.stationFingerprint(),
                metrics.size(),
                total,
                capital / total,
                support / total,
                projection,
                carrier,
                averageCrew,
                Stage22EmpireShipyardCatalogLoader.loadDefault().getYards().size(),
                0d,
                0d);
    }

    private static PackageVector unionVector(Stage22IndustrialUnionPackageValidator.ValidationReport validation) {
        Map<String, Stage22IndustrialUnionPackageValidator.FamilyMetrics> metrics = validation.familyMetrics();
        double total = metrics.values().stream()
                .mapToDouble(Stage22IndustrialUnionPackageValidator.FamilyMetrics::fittedDryMassKg).sum();
        double capital = unionMass(metrics, "role.military.cruiser")
                + unionMass(metrics, "role.military.battleship")
                + unionMass(metrics, "role.military.carrier");
        double support = unionMass(metrics, "role.support.freight")
                + unionMass(metrics, "role.support.tanker_replenishment")
                + unionMass(metrics, "role.support.fleet_logistics_repair_salvage");
        double carrier = unionMass(metrics, "role.military.carrier");
        double projection = carrier
                + unionMass(metrics, "role.support.tanker_replenishment")
                + unionMass(metrics, "role.support.fleet_logistics_repair_salvage");
        double averageCrew = metrics.values().stream()
                .mapToInt(Stage22IndustrialUnionPackageValidator.FamilyMetrics::staffedCrewBurden)
                .average().orElseThrow();
        return new PackageVector(
                "core.industrial_union",
                UNION_FACTION_ID,
                validation.packageFingerprint(),
                validation.productionFingerprint(),
                validation.engineeringFingerprint(),
                validation.manufacturingFingerprint(),
                validation.shipyardFingerprint(),
                validation.stationFingerprint(),
                metrics.size(),
                total,
                capital / total,
                support / total,
                projection,
                carrier,
                averageCrew,
                Stage22IndustrialUnionShipyardCatalogLoader.loadDefault().getYards().size(),
                validation.maximumBuildTimeReduction(),
                validation.maximumThroughputImprovement());
    }

    private static UnionDisruptionVector unionDisruptionVector() {
        YardSeriesState yard = Stage22IndustrialUnionProductionState.unqualifiedYard(
                Stage22IndustrialUnionIndustrialProgram.YARD_ID);
        YardSeriesState pending = Stage22IndustrialUnionIndustrialProgram.beginRetool(
                yard, "ship_family.industrial_union.freight");
        YardSeriesState paid = Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(
                pending, pending.retoolWorkRemainingSeconds(), pending.retoolEnergyRemainingJ());
        yard = Stage22IndustrialUnionIndustrialProgram.completeRetool(paid);
        for (int index = 0; index < 3; index++) {
            yard = Stage22IndustrialUnionIndustrialProgram.recordCompletedUnit(
                    yard, "ship_family.industrial_union.freight");
        }

        Stage22IndustrialUnionCommonalityNetwork.Availability healthy =
                Stage22IndustrialUnionCommonalityNetwork.healthy();
        LinkedHashMap<String, Double> isolatedAssemblies =
                new LinkedHashMap<>(healthy.sharedAssemblyAvailability());
        isolatedAssemblies.put("module.industrial_union_sensor_block_v1", 0.75d);
        Stage22IndustrialUnionCommonalityNetwork.Report isolated =
                Stage22IndustrialUnionCommonalityNetwork.observe(
                        yard,
                        "ship_family.industrial_union.freight",
                        new Stage22IndustrialUnionCommonalityNetwork.Availability(isolatedAssemblies, 1d, 1d));

        LinkedHashMap<String, Double> correlatedAssemblies = new LinkedHashMap<>();
        Stage22IndustrialUnionCommonalityNetwork.SHARED_ASSEMBLY_IDS.stream().sorted()
                .forEach(id -> correlatedAssemblies.put(id, 0.75d));
        Stage22IndustrialUnionCommonalityNetwork.Report correlated =
                Stage22IndustrialUnionCommonalityNetwork.observe(
                        yard,
                        "ship_family.industrial_union.freight",
                        new Stage22IndustrialUnionCommonalityNetwork.Availability(
                                correlatedAssemblies, 0.75d, 0.75d));

        return new UnionDisruptionVector(
                pending.retoolWorkRemainingSeconds(),
                pending.retoolEnergyRemainingJ(),
                isolated.throughputDegradation(),
                correlated.throughputDegradation(),
                correlated.workBurdenMultiplier(),
                correlated.correlatedDisruption());
    }

    private static Stage22FactionProfileCatalog.SystemicProfileDefinition requireProfile(
            Stage22FactionProfileCatalog catalog,
            String stableFactionId) {
        Stage22FactionProfileCatalog.SystemicProfileDefinition value = catalog.findProfileForFaction(stableFactionId);
        if (value == null) {
            throw new IllegalStateException("Missing M22.6 systemic profile for " + stableFactionId);
        }
        return value;
    }

    private static double mass(Map<String, FamilyMetrics> metrics, String roleId) {
        FamilyMetrics value = Objects.requireNonNull(metrics.get(roleId), "Missing Empire role " + roleId);
        return value.fittedDryMassKg();
    }

    private static double unionMass(
            Map<String, Stage22IndustrialUnionPackageValidator.FamilyMetrics> metrics,
            String roleId) {
        Stage22IndustrialUnionPackageValidator.FamilyMetrics value =
                Objects.requireNonNull(metrics.get(roleId), "Missing Industrial Union role " + roleId);
        return value.fittedDryMassKg();
    }

    /** Immutable current core-pair diagnostic bundle. */
    public record PairEvidence(
            String scenarioSuiteVersion,
            String empireProfileFingerprint,
            String coreProfileCatalogFingerprint,
            PackageVector empire,
            PackageVector industrialUnion,
            UnionDisruptionVector unionDisruption,
            PairwiseBalanceCard card) { }

    /** Read-only package burden vector used for equal-burden review. */
    public record PackageVector(
            String packageKey,
            String stableFactionId,
            String packageFingerprint,
            String productionFingerprint,
            String engineeringFingerprint,
            String manufacturingFingerprint,
            String shipyardFingerprint,
            String stationFingerprint,
            int roleFamilyCount,
            double totalPrimaryFittedMassKg,
            double capitalMassShare,
            double supportMassShare,
            double projectionBundleMassKg,
            double carrierMassKg,
            double averageStaffedCrewBurden,
            int productionYardCount,
            double maximumBuildTimeReduction,
            double maximumThroughputImprovement) { }

    /** Industrial Union retool/commonality downside vector from the accepted production authority. */
    public record UnionDisruptionVector(
            long retoolWorkSeconds,
            long retoolEnergyJ,
            double isolatedThroughputDegradation,
            double correlatedThroughputDegradation,
            double correlatedWorkBurdenMultiplier,
            boolean correlatedDisruption) { }

    /**
     * Canonical pairwise balance card required by the validation framework.
     *
     * <p>Text describes hypotheses/counterplay and never acts as gameplay authority.</p>
     */
    public record PairwiseBalanceCard(
            String pair,
            String contestedResourceOrObjective,
            String advantageA,
            String costA,
            String advantageB,
            String costB,
            List<String> requiredScenarios,
            String prohibitedShortcut,
            List<String> evidenceLinks,
            List<String> openRisks) {
        /** Freezes mutable caller lists. */
        public PairwiseBalanceCard {
            requiredScenarios = List.copyOf(requiredScenarios);
            evidenceLinks = List.copyOf(evidenceLinks);
            openRisks = List.copyOf(openRisks);
        }
    }
}
