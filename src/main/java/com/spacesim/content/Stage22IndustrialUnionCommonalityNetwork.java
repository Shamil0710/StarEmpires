package com.spacesim.content;

import com.spacesim.content.Stage18ManufacturingCatalog.ProductProfileDefinition;
import com.spacesim.content.Stage22IndustrialUnionIndustrialProgram.ProductionModifier;
import com.spacesim.content.Stage22IndustrialUnionProductionState.YardSeriesState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * M22.4 commonality-network downside and observability projection.
 *
 * <p>The Industrial Union gains serial efficiency because the same reactor, drive, sensor and
 * radiator assemblies recur across all three assembly series. The same topology creates a systemic
 * weakness: correlated loss of common assemblies, bulk logistics and yard facilities increases the
 * ordinary Stage-18 work/energy required to produce the same finite output. This class owns no
 * inventory, freight, facility health, construction progress, treasury value or faction-wide outcome.
 * Callers provide observed availability from those ordinary authorities; the result is only a
 * deterministic profile projection and a read-only diagnostic report.</p>
 *
 * <p>No calculation consumes a faction ID. The downside follows from shared dependency availability,
 * so identical inputs always produce identical burden regardless of actor identity.</p>
 */
public final class Stage22IndustrialUnionCommonalityNetwork {
    /** Common assemblies deliberately repeated across every M22.4 series. */
    public static final Set<String> SHARED_ASSEMBLY_IDS = Set.of(
            "module.industrial_union_reactor_bank_v1",
            "module.industrial_union_drive_bank_v1",
            "module.industrial_union_sensor_block_v1",
            "module.industrial_union_radiator_panel_v1");
    /** Observability identity for the common bulk-logistics dependency domain. */
    public static final String BULK_LOGISTICS_DEPENDENCY_ID = "dependency.industrial_union.bulk_logistics";
    /** Observability identity for the common yard-facility dependency domain. */
    public static final String YARD_FACILITY_DEPENDENCY_ID = "dependency.industrial_union.series_yard_facilities";

    private static final double ASSEMBLY_WEIGHT = 0.50d;
    private static final double BULK_LOGISTICS_WEIGHT = 0.30d;
    private static final double YARD_FACILITY_WEIGHT = 0.20d;
    private static final double WORK_BURDEN_PER_MISSING_FRACTION = 1.60d;
    private static final double ENERGY_BURDEN_PER_MISSING_FRACTION = 0.40d;
    private static final double HEALTHY_EPSILON = 1e-12d;

    private Stage22IndustrialUnionCommonalityNetwork() {
        throw new AssertionError("utility class");
    }

    /** @return a fully available commonality network */
    public static Availability healthy() {
        Map<String, Double> assemblies = new LinkedHashMap<>();
        SHARED_ASSEMBLY_IDS.stream().sorted().forEach(id -> assemblies.put(id, 1d));
        return new Availability(assemblies, 1d, 1d);
    }

    /**
     * Derives the production burden and read-only observability vector for one legal series build.
     *
     * @param yard existing per-yard series qualification state
     * @param targetShipFamilyId legal M22.4 family requested from the qualified series
     * @param availability observed common-assembly/logistics/facility availability in [0,1]
     * @return deterministic network diagnostic and effective process multipliers
     */
    public static Report observe(
            YardSeriesState yard,
            String targetShipFamilyId,
            Availability availability) {
        YardSeriesState checkedYard = Objects.requireNonNull(yard, "yard");
        Availability checkedAvailability = Objects.requireNonNull(availability, "availability");
        ProductionModifier series = Stage22IndustrialUnionIndustrialProgram.modifierFor(
                checkedYard, targetShipFamilyId);

        double assemblyAvailability = checkedAvailability.sharedAssemblyAvailability().values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow();
        double networkAvailability = ASSEMBLY_WEIGHT * assemblyAvailability
                + BULK_LOGISTICS_WEIGHT * checkedAvailability.bulkLogisticsAvailability()
                + YARD_FACILITY_WEIGHT * checkedAvailability.yardFacilityAvailability();
        double missingFraction = 1d - networkAvailability;
        double workBurden = 1d + WORK_BURDEN_PER_MISSING_FRACTION * missingFraction;
        double energyBurden = 1d + ENERGY_BURDEN_PER_MISSING_FRACTION * missingFraction;
        double throughputRelativeToHealthySameSeries = 1d / workBurden;
        double throughputDegradation = 1d - throughputRelativeToHealthySameSeries;

        int degradedDomains = 0;
        if (assemblyAvailability < 1d - HEALTHY_EPSILON) {
            degradedDomains++;
        }
        if (checkedAvailability.bulkLogisticsAvailability() < 1d - HEALTHY_EPSILON) {
            degradedDomains++;
        }
        if (checkedAvailability.yardFacilityAvailability() < 1d - HEALTHY_EPSILON) {
            degradedDomains++;
        }

        return new Report(
                checkedYard.yardId(),
                checkedYard.activeSeriesId(),
                checkedYard.commonalityStreak(),
                checkedAvailability.sharedAssemblyAvailability(),
                assemblyAvailability,
                checkedAvailability.bulkLogisticsAvailability(),
                checkedAvailability.yardFacilityAvailability(),
                bottleneckId(checkedAvailability),
                networkAvailability,
                workBurden,
                energyBurden,
                series.workMultiplier() * workBurden,
                series.energyMultiplier() * energyBurden,
                throughputRelativeToHealthySameSeries,
                throughputDegradation,
                degradedDomains >= 2);
    }

    /**
     * Projects one observed network state into the ordinary Stage-18 manufacturing grammar.
     * Finite material inputs, capability requirements and maintenance work are unchanged; only the
     * explicit process work/energy burden changes.
     *
     * @param base ordinary Stage-18 product profile
     * @param derivedProfileId stable ID for the projected profile
     * @param yard existing per-yard series qualification state
     * @param targetShipFamilyId legal family requested from the qualified series
     * @param availability observed dependency availability
     * @return ordinary Stage-18 profile carrying the causal network burden
     */
    public static ProductProfileDefinition deriveProfile(
            ProductProfileDefinition base,
            String derivedProfileId,
            YardSeriesState yard,
            String targetShipFamilyId,
            Availability availability) {
        ProductProfileDefinition checked = Objects.requireNonNull(base, "base");
        Report report = observe(yard, targetShipFamilyId, availability);
        return new ProductProfileDefinition(
                Objects.requireNonNull(derivedProfileId, "derivedProfileId"),
                checked.displayName() + " / Industrial Union observed commonality network",
                checked.inputs(),
                checked.requiredCapabilityTags(),
                checked.energyJPerOutputKg() * report.effectiveEnergyMultiplier(),
                checked.workSecondsPerOutputKg() * report.effectiveWorkMultiplier(),
                checked.maintenanceWorkSecondsPerOutputKg());
    }

    private static String bottleneckId(Availability availability) {
        List<DependencyValue> dependencies = new ArrayList<>();
        availability.sharedAssemblyAvailability().forEach((id, value) ->
                dependencies.add(new DependencyValue(id, value)));
        dependencies.add(new DependencyValue(BULK_LOGISTICS_DEPENDENCY_ID, availability.bulkLogisticsAvailability()));
        dependencies.add(new DependencyValue(YARD_FACILITY_DEPENDENCY_ID, availability.yardFacilityAvailability()));
        return dependencies.stream()
                .min(Comparator.comparingDouble(DependencyValue::availability)
                        .thenComparing(DependencyValue::id))
                .orElseThrow()
                .id();
    }

    /**
     * Observed availability of ordinary physical dependency domains.
     *
     * @param sharedAssemblyAvailability exact four common assembly availability fractions
     * @param bulkLogisticsAvailability aggregate availability supplied by ordinary freight/logistics authority
     * @param yardFacilityAvailability aggregate availability supplied by ordinary Stage-18 facility authority
     */
    public record Availability(
            Map<String, Double> sharedAssemblyAvailability,
            double bulkLogisticsAvailability,
            double yardFacilityAvailability) {
        /**
         * Validates exact dependency coverage and canonicalizes the shared-assembly map.
         *
         * @param sharedAssemblyAvailability exact four common assembly availability fractions
         * @param bulkLogisticsAvailability aggregate availability from ordinary freight/logistics authority
         * @param yardFacilityAvailability aggregate availability from ordinary Stage-18 facility authority
         */
        public Availability {
            Map<String, Double> checked = new TreeMap<>(Objects.requireNonNull(
                    sharedAssemblyAvailability, "sharedAssemblyAvailability"));
            if (!checked.keySet().equals(SHARED_ASSEMBLY_IDS)) {
                throw new IllegalArgumentException(
                        "Industrial Union commonality observation must cover exactly the four shared assemblies");
            }
            checked.replaceAll((id, value) -> availability(value, id));
            sharedAssemblyAvailability = Map.copyOf(checked);
            bulkLogisticsAvailability = availability(bulkLogisticsAvailability, BULK_LOGISTICS_DEPENDENCY_ID);
            yardFacilityAvailability = availability(yardFacilityAvailability, YARD_FACILITY_DEPENDENCY_ID);
        }
    }

    /**
     * Read-only M22.4 production-network diagnostics.
     *
     * @param yardId ordinary yard identity
     * @param activeSeriesId qualified assembly series
     * @param commonalityStreak same-series completion streak
     * @param sharedAssemblyAvailability exact observed assembly availability
     * @param averageAssemblyAvailability mean availability of repeated core assemblies
     * @param bulkLogisticsAvailability observed bulk logistics availability
     * @param yardFacilityAvailability observed yard facility availability
     * @param bottleneckDependencyId lowest-availability dependency with deterministic tie break
     * @param networkAvailability weighted availability before burden projection
     * @param workBurdenMultiplier extra work caused by dependency loss, 1 when healthy
     * @param energyBurdenMultiplier extra energy caused by dependency loss, 1 when healthy
     * @param effectiveWorkMultiplier serial work multiplier composed with network burden
     * @param effectiveEnergyMultiplier serial energy multiplier composed with network burden
     * @param throughputRelativeToHealthySameSeries relative throughput at equal work capacity
     * @param throughputDegradation loss relative to the same serial state with a healthy network
     * @param correlatedDisruption whether at least two independent dependency domains are degraded
     */
    public record Report(
            String yardId,
            String activeSeriesId,
            int commonalityStreak,
            Map<String, Double> sharedAssemblyAvailability,
            double averageAssemblyAvailability,
            double bulkLogisticsAvailability,
            double yardFacilityAvailability,
            String bottleneckDependencyId,
            double networkAvailability,
            double workBurdenMultiplier,
            double energyBurdenMultiplier,
            double effectiveWorkMultiplier,
            double effectiveEnergyMultiplier,
            double throughputRelativeToHealthySameSeries,
            double throughputDegradation,
            boolean correlatedDisruption) { }

    private record DependencyValue(String id, double availability) { }

    private static double availability(Double value, String label) {
        return availability(Objects.requireNonNull(value, label).doubleValue(), label);
    }

    private static double availability(double value, String label) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(label + " availability must be finite in [0,1]");
        }
        return value;
    }
}
