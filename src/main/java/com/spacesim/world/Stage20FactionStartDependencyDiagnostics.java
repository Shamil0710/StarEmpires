package com.spacesim.world;

import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteEvaluator;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.InitialExtractionSite;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.ResourceOccurrence;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Read-only Stage-20E dependency diagnostics for one procedural faction-start candidate.
 *
 * <p>The analyzer consumes already generated topology, finite supply capacity and physically
 * assessed routes. It never places a faction, creates a deposit, changes a route or grants missing
 * stock. Optional authorities such as delivered monetary cost, current buffer stock and source
 * ownership remain explicitly unresolved when the caller cannot provide them.</p>
 *
 * <p>The report is designed to feed the later bounded faction-start candidate evaluator required by
 * the Stage-20 roadmap. It deliberately preserves per-commodity evidence instead of collapsing the
 * world into one opaque score.</p>
 */
public final class Stage20FactionStartDependencyDiagnostics {
    /** Current Stage-20E faction-start dependency diagnostics version. */
    public static final String CURRENT_VERSION = "stage20e.faction-start-dependency-diagnostics.v1";

    private Stage20FactionStartDependencyDiagnostics() {
        throw new AssertionError("No instances");
    }

    /**
     * Supplies whole-route delivered monetary cost when an accepted authority exists.
     *
     * <p>Empty means unresolved. Implementations must not replace physical route costs with direct
     * Euclidean distance or a hidden per-hop bonus.</p>
     */
    @FunctionalInterface
    public interface DeliveredCostEvaluator {
        /**
         * Returns delivered transport/economic cost for one physically assessed supplier route.
         *
         * @param supplierSystem supplier/producer system
         * @param candidateSystem faction-start candidate system
         * @param commodityId authoritative Stage-18 commodity ID
         * @param route exact physical route assessment used by the dependency analyzer
         * @return milli-credits per delivered kilogram, or empty when cost authority is unresolved
         */
        OptionalDouble deliveredCostMilliCreditsPerKg(
                StarSystemId supplierSystem,
                StarSystemId candidateSystem,
                String commodityId,
                RouteAssessment route);
    }

    /** Supplies current/generated inventory-buffer evidence without inventing stock. */
    @FunctionalInterface
    public interface BufferStateProvider {
        /**
         * Resolves one commodity buffer at the candidate.
         *
         * @param candidateSystem faction-start candidate system
         * @param commodityId authoritative Stage-18 commodity ID
         * @return physical buffer state, or empty when bootstrap stock/consumption authority is unresolved
         */
        Optional<BufferState> buffer(StarSystemId candidateSystem, String commodityId);
    }

    /**
     * Essential commodity requirement used only for dependency/start viability diagnostics.
     *
     * @param commodityId authoritative Stage-18 commodity ID
     * @param familyId stable dependency-report family label
     * @param requiredKgPerSecond required sustained delivered rate
     * @param maxSupplierRouteTimeS maximum accepted physical supplier route time
     */
    public record Requirement(
            String commodityId,
            String familyId,
            double requiredKgPerSecond,
            double maxSupplierRouteTimeS) {
        /**
         * Validates one immutable requirement.
         *
         * @param commodityId authoritative Stage-18 commodity ID
         * @param familyId stable dependency-report family label
         * @param requiredKgPerSecond required sustained delivered rate
         * @param maxSupplierRouteTimeS maximum accepted physical supplier route time
         */
        public Requirement {
            commodityId = requireText(commodityId, "commodityId");
            familyId = requireText(familyId, "familyId");
            requirePositiveFinite(requiredKgPerSecond, "requiredKgPerSecond");
            requirePositiveFinite(maxSupplierRouteTimeS, "maxSupplierRouteTimeS");
        }
    }

    /**
     * Physical inventory buffer evidence.
     *
     * @param availableMassKg currently available physical buffer mass
     * @param sustainedConsumptionKgPerSecond sustained consumption rate used for depletion exposure
     * @param sourceEvidenceId authoritative evidence/provenance ID
     */
    public record BufferState(
            double availableMassKg,
            double sustainedConsumptionKgPerSecond,
            String sourceEvidenceId) {
        /**
         * Validates one immutable buffer row.
         *
         * @param availableMassKg currently available physical buffer mass
         * @param sustainedConsumptionKgPerSecond sustained consumption rate used for depletion exposure
         * @param sourceEvidenceId authoritative evidence/provenance ID
         */
        public BufferState {
            requireNonNegativeFinite(availableMassKg, "availableMassKg");
            requirePositiveFinite(sustainedConsumptionKgPerSecond, "sustainedConsumptionKgPerSecond");
            sourceEvidenceId = requireText(sourceEvidenceId, "sourceEvidenceId");
        }

        /**
         * Returns physical time until this buffer is depleted at the supplied sustained consumption.
         *
         * @return finite buffer coverage in seconds
         */
        public double coverageSeconds() {
            return availableMassKg / sustainedConsumptionKgPerSecond;
        }
    }

    /**
     * One finite accessible reserve row used for concentration diagnostics.
     *
     * @param sourceId stable physical source identity
     * @param commodityId extracted Stage-18 commodity ID
     * @param systemId source system
     * @param recoverableMassKg finite useful recoverable output mass
     * @param ownerId current source owner, absent before ownership authority exists
     */
    public record ReserveSource(
            String sourceId,
            String commodityId,
            StarSystemId systemId,
            double recoverableMassKg,
            Optional<String> ownerId) {
        /**
         * Validates one finite accessible reserve row.
         *
         * @param sourceId stable physical source identity
         * @param commodityId extracted Stage-18 commodity ID
         * @param systemId source system
         * @param recoverableMassKg finite useful recoverable output mass
         * @param ownerId current source owner, absent before ownership authority exists
         */
        public ReserveSource {
            sourceId = requireText(sourceId, "sourceId");
            commodityId = requireText(commodityId, "commodityId");
            Objects.requireNonNull(systemId, "systemId");
            requirePositiveFinite(recoverableMassKg, "recoverableMassKg");
            Objects.requireNonNull(ownerId, "ownerId");
            ownerId = ownerId.map(value -> requireText(value, "ownerId"));
        }
    }

    /**
     * Delivered-cost samples for viable suppliers.
     *
     * @param minMilliCreditsPerKg minimum resolved delivered cost
     * @param medianMilliCreditsPerKg deterministic median resolved delivered cost
     * @param maxMilliCreditsPerKg maximum resolved delivered cost
     * @param resolvedSupplierCount number of viable suppliers with cost authority
     * @param viableSupplierCount total viable supplier count
     */
    public record DeliveredCostBand(
            double minMilliCreditsPerKg,
            double medianMilliCreditsPerKg,
            double maxMilliCreditsPerKg,
            int resolvedSupplierCount,
            int viableSupplierCount) {
        /**
         * Validates one delivered-cost band.
         *
         * @param minMilliCreditsPerKg minimum resolved delivered cost
         * @param medianMilliCreditsPerKg deterministic median resolved delivered cost
         * @param maxMilliCreditsPerKg maximum resolved delivered cost
         * @param resolvedSupplierCount number of viable suppliers with cost authority
         * @param viableSupplierCount total viable supplier count
         */
        public DeliveredCostBand {
            requireNonNegativeFinite(minMilliCreditsPerKg, "minMilliCreditsPerKg");
            requireNonNegativeFinite(medianMilliCreditsPerKg, "medianMilliCreditsPerKg");
            requireNonNegativeFinite(maxMilliCreditsPerKg, "maxMilliCreditsPerKg");
            if (minMilliCreditsPerKg > medianMilliCreditsPerKg
                    || medianMilliCreditsPerKg > maxMilliCreditsPerKg) {
                throw new IllegalArgumentException("delivered-cost band must be monotonic");
            }
            if (resolvedSupplierCount <= 0 || viableSupplierCount <= 0
                    || resolvedSupplierCount > viableSupplierCount) {
                throw new IllegalArgumentException("delivered-cost sample counts are inconsistent");
            }
        }

        /**
         * Returns fraction of viable suppliers covered by monetary-cost authority.
         *
         * @return fraction in {@code (0,1]}
         */
        public double authorityCoverageFraction() {
            return (double) resolvedSupplierCount / viableSupplierCount;
        }
    }

    /**
     * Per-commodity authoritative-derived dependency diagnostics.
     *
     * @param commodityId authoritative Stage-18 commodity ID
     * @param familyId dependency-report family label
     * @param requiredKgPerSecond sustained requirement
     * @param localSupplyKgPerSecond supply physically produced in the candidate system
     * @param totalReachableSupplyKgPerSecond physical delivered-capacity upper bound from viable suppliers
     * @param localSupplyCoverageFraction local fraction of the requirement
     * @param importDependencyFraction fraction of requirement not coverable locally
     * @param localExportPotentialKgPerSecond local production above the requirement
     * @param throughputHeadroomKgPerSecond reachable delivered capacity minus requirement
     * @param viableSupplierCount number of suppliers satisfying route-time and throughput constraints
     * @param externalSupplierCount viable suppliers outside the candidate system
     * @param supplierConcentrationHhi HHI over viable delivered-capacity shares
     * @param routeConcentrationHhi HHI over external delivered capacity grouped by final gateway edge
     * @param criticalGatewayDependencyFraction largest external gateway share
     * @param alternativePathCountFloor best proven edge-disjoint path floor among external suppliers, 0..2
     * @param deliveredCostBand resolved delivered-cost band, absent when no supplier cost authority exists
     * @param bufferCoverageSeconds physical depletion exposure, absent when buffer authority is unresolved
     * @param accessibleReserveConcentrationHhi HHI over reachable finite recoverable reserve sources
     * @param ownershipConcentrationHhi HHI over reachable reserve ownership, absent unless every relevant source has owner authority
     */
    public record CommodityDiagnostic(
            String commodityId,
            String familyId,
            double requiredKgPerSecond,
            double localSupplyKgPerSecond,
            double totalReachableSupplyKgPerSecond,
            double localSupplyCoverageFraction,
            double importDependencyFraction,
            double localExportPotentialKgPerSecond,
            double throughputHeadroomKgPerSecond,
            int viableSupplierCount,
            int externalSupplierCount,
            double supplierConcentrationHhi,
            double routeConcentrationHhi,
            double criticalGatewayDependencyFraction,
            int alternativePathCountFloor,
            Optional<DeliveredCostBand> deliveredCostBand,
            OptionalDouble bufferCoverageSeconds,
            double accessibleReserveConcentrationHhi,
            OptionalDouble ownershipConcentrationHhi) {
        /**
         * Validates one immutable commodity diagnostics row.
         *
         * @param commodityId authoritative Stage-18 commodity ID
         * @param familyId dependency-report family label
         * @param requiredKgPerSecond sustained requirement
         * @param localSupplyKgPerSecond supply physically produced in the candidate system
         * @param totalReachableSupplyKgPerSecond physical delivered-capacity upper bound from viable suppliers
         * @param localSupplyCoverageFraction local fraction of the requirement
         * @param importDependencyFraction fraction of requirement not coverable locally
         * @param localExportPotentialKgPerSecond local production above the requirement
         * @param throughputHeadroomKgPerSecond reachable delivered capacity minus requirement
         * @param viableSupplierCount number of suppliers satisfying route-time and throughput constraints
         * @param externalSupplierCount viable suppliers outside the candidate system
         * @param supplierConcentrationHhi HHI over viable delivered-capacity shares
         * @param routeConcentrationHhi HHI over external delivered capacity grouped by final gateway edge
         * @param criticalGatewayDependencyFraction largest external gateway share
         * @param alternativePathCountFloor best proven edge-disjoint path floor among external suppliers, 0..2
         * @param deliveredCostBand resolved delivered-cost band, absent when no supplier cost authority exists
         * @param bufferCoverageSeconds physical depletion exposure, absent when buffer authority is unresolved
         * @param accessibleReserveConcentrationHhi HHI over reachable finite recoverable reserve sources
         * @param ownershipConcentrationHhi HHI over reachable reserve ownership, absent unless every relevant source has owner authority
         */
        public CommodityDiagnostic {
            commodityId = requireText(commodityId, "commodityId");
            familyId = requireText(familyId, "familyId");
            requirePositiveFinite(requiredKgPerSecond, "requiredKgPerSecond");
            requireNonNegativeFinite(localSupplyKgPerSecond, "localSupplyKgPerSecond");
            requireNonNegativeFinite(totalReachableSupplyKgPerSecond, "totalReachableSupplyKgPerSecond");
            requireUnitFraction(localSupplyCoverageFraction, "localSupplyCoverageFraction");
            requireUnitFraction(importDependencyFraction, "importDependencyFraction");
            requireNonNegativeFinite(localExportPotentialKgPerSecond, "localExportPotentialKgPerSecond");
            if (!Double.isFinite(throughputHeadroomKgPerSecond)) {
                throw new IllegalArgumentException("throughputHeadroomKgPerSecond must be finite");
            }
            if (viableSupplierCount < 0 || externalSupplierCount < 0 || externalSupplierCount > viableSupplierCount) {
                throw new IllegalArgumentException("supplier counts are inconsistent");
            }
            requireUnitFraction(supplierConcentrationHhi, "supplierConcentrationHhi");
            requireUnitFraction(routeConcentrationHhi, "routeConcentrationHhi");
            requireUnitFraction(criticalGatewayDependencyFraction, "criticalGatewayDependencyFraction");
            if (alternativePathCountFloor < 0 || alternativePathCountFloor > 2) {
                throw new IllegalArgumentException("alternativePathCountFloor must be in 0..2");
            }
            Objects.requireNonNull(deliveredCostBand, "deliveredCostBand");
            Objects.requireNonNull(bufferCoverageSeconds, "bufferCoverageSeconds");
            if (bufferCoverageSeconds.isPresent()) {
                requireNonNegativeFinite(bufferCoverageSeconds.getAsDouble(), "bufferCoverageSeconds");
            }
            requireUnitFraction(accessibleReserveConcentrationHhi, "accessibleReserveConcentrationHhi");
            Objects.requireNonNull(ownershipConcentrationHhi, "ownershipConcentrationHhi");
            if (ownershipConcentrationHhi.isPresent()) {
                requireUnitFraction(ownershipConcentrationHhi.getAsDouble(), "ownershipConcentrationHhi");
            }
        }
    }

    /**
     * Aggregate deterministic diagnostics for one faction-start candidate.
     *
     * @param version stable diagnostics version
     * @param candidateSystemId evaluated start system
     * @param commodities deterministic per-commodity evidence
     * @param essentialLocalSupplyCoverageFraction requirement-weighted local essential coverage
     * @param importDependencyByFamily requirement-weighted import dependency by family
     * @param localExportPotentialKgPerSecondByFamily local exportable production by family
     * @param minimumThroughputHeadroomKgPerSecond worst commodity throughput headroom
     * @param maximumSupplierConcentrationHhi worst commodity supplier concentration
     * @param maximumRouteConcentrationHhi worst commodity route concentration
     * @param maximumCriticalGatewayDependencyFraction worst commodity gateway dependency
     * @param minimumExternalSupplierCount minimum external supplier count across import-dependent commodities
     * @param minimumAlternativePathCountFloor minimum proven alternative-path floor across import-dependent commodities
     * @param maximumAccessibleReserveConcentrationHhi worst finite reserve-source concentration
     * @param maximumOwnershipConcentrationHhi worst ownership concentration, absent until all relevant ownership is authoritative
     * @param unresolvedDeliveredCostCommodityCount commodities without any resolved delivered-cost authority
     * @param unresolvedBufferCommodityCount commodities without physical buffer authority
     * @param unresolvedOwnershipCommodityCount commodities without complete reserve ownership authority
     */
    public record Report(
            String version,
            StarSystemId candidateSystemId,
            List<CommodityDiagnostic> commodities,
            double essentialLocalSupplyCoverageFraction,
            Map<String, Double> importDependencyByFamily,
            Map<String, Double> localExportPotentialKgPerSecondByFamily,
            double minimumThroughputHeadroomKgPerSecond,
            double maximumSupplierConcentrationHhi,
            double maximumRouteConcentrationHhi,
            double maximumCriticalGatewayDependencyFraction,
            int minimumExternalSupplierCount,
            int minimumAlternativePathCountFloor,
            double maximumAccessibleReserveConcentrationHhi,
            OptionalDouble maximumOwnershipConcentrationHhi,
            int unresolvedDeliveredCostCommodityCount,
            int unresolvedBufferCommodityCount,
            int unresolvedOwnershipCommodityCount) {
        /**
         * Validates and freezes one aggregate diagnostics report.
         *
         * @param version stable diagnostics version
         * @param candidateSystemId evaluated start system
         * @param commodities deterministic per-commodity evidence
         * @param essentialLocalSupplyCoverageFraction requirement-weighted local essential coverage
         * @param importDependencyByFamily requirement-weighted import dependency by family
         * @param localExportPotentialKgPerSecondByFamily local exportable production by family
         * @param minimumThroughputHeadroomKgPerSecond worst commodity throughput headroom
         * @param maximumSupplierConcentrationHhi worst commodity supplier concentration
         * @param maximumRouteConcentrationHhi worst commodity route concentration
         * @param maximumCriticalGatewayDependencyFraction worst commodity gateway dependency
         * @param minimumExternalSupplierCount minimum external supplier count across import-dependent commodities
         * @param minimumAlternativePathCountFloor minimum proven alternative-path floor across import-dependent commodities
         * @param maximumAccessibleReserveConcentrationHhi worst finite reserve-source concentration
         * @param maximumOwnershipConcentrationHhi worst ownership concentration, absent until all relevant ownership is authoritative
         * @param unresolvedDeliveredCostCommodityCount commodities without any resolved delivered-cost authority
         * @param unresolvedBufferCommodityCount commodities without physical buffer authority
         * @param unresolvedOwnershipCommodityCount commodities without complete reserve ownership authority
         */
        public Report {
            version = requireText(version, "version");
            Objects.requireNonNull(candidateSystemId, "candidateSystemId");
            Objects.requireNonNull(commodities, "commodities");
            ArrayList<CommodityDiagnostic> sorted = new ArrayList<>(commodities);
            if (sorted.isEmpty() || sorted.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("commodities must be non-empty and contain no nulls");
            }
            sorted.sort(Comparator.comparing(CommodityDiagnostic::commodityId));
            commodities = List.copyOf(sorted);
            requireUnitFraction(essentialLocalSupplyCoverageFraction, "essentialLocalSupplyCoverageFraction");
            importDependencyByFamily = immutableFractionMap(importDependencyByFamily, "importDependencyByFamily");
            localExportPotentialKgPerSecondByFamily = immutableNonNegativeMap(
                    localExportPotentialKgPerSecondByFamily, "localExportPotentialKgPerSecondByFamily");
            if (!Double.isFinite(minimumThroughputHeadroomKgPerSecond)) {
                throw new IllegalArgumentException("minimumThroughputHeadroomKgPerSecond must be finite");
            }
            requireUnitFraction(maximumSupplierConcentrationHhi, "maximumSupplierConcentrationHhi");
            requireUnitFraction(maximumRouteConcentrationHhi, "maximumRouteConcentrationHhi");
            requireUnitFraction(maximumCriticalGatewayDependencyFraction, "maximumCriticalGatewayDependencyFraction");
            if (minimumExternalSupplierCount < 0 || minimumAlternativePathCountFloor < 0
                    || minimumAlternativePathCountFloor > 2) {
                throw new IllegalArgumentException("aggregate supplier/path minima are invalid");
            }
            requireUnitFraction(maximumAccessibleReserveConcentrationHhi,
                    "maximumAccessibleReserveConcentrationHhi");
            Objects.requireNonNull(maximumOwnershipConcentrationHhi, "maximumOwnershipConcentrationHhi");
            if (maximumOwnershipConcentrationHhi.isPresent()) {
                requireUnitFraction(maximumOwnershipConcentrationHhi.getAsDouble(),
                        "maximumOwnershipConcentrationHhi");
            }
            if (unresolvedDeliveredCostCommodityCount < 0 || unresolvedBufferCommodityCount < 0
                    || unresolvedOwnershipCommodityCount < 0) {
                throw new IllegalArgumentException("unresolved-authority counts must be non-negative");
            }
        }
    }

    /**
     * Builds finite useful reserve rows for generated sources that already have an explicit initial
     * extraction installation. Ownership remains unresolved and may be attached by a later owner-aware
     * projection.
     *
     * @param world generated Stage-20E resource world
     * @return deterministic finite reserve rows
     */
    public static List<ReserveSource> initialOperationalReserves(Stage20ResourceOccurrenceWorld world) {
        Stage20ResourceOccurrenceWorld checked = Objects.requireNonNull(world, "world");
        HashSet<String> operationalSources = new HashSet<>();
        for (InitialExtractionSite site : checked.initialExtractionSites()) {
            operationalSources.add(site.sourceId());
        }
        ArrayList<ReserveSource> result = new ArrayList<>();
        for (ResourceOccurrence occurrence : checked.occurrences()) {
            if (!operationalSources.contains(occurrence.sourceId())) {
                continue;
            }
            double recoverable = occurrence.initialAccessibleMassKg()
                    * occurrence.gradeFraction()
                    * occurrence.sourceRecoveryFraction();
            requirePositiveFinite(recoverable, "recoverable reserve mass");
            result.add(new ReserveSource(
                    occurrence.sourceId(),
                    occurrence.outputCommodityId(),
                    occurrence.systemId(),
                    recoverable,
                    Optional.empty()));
        }
        result.sort(Comparator.comparing(ReserveSource::commodityId)
                .thenComparing(ReserveSource::systemId)
                .thenComparing(ReserveSource::sourceId));
        return List.copyOf(result);
    }

    /**
     * Analyzes one procedural faction-start candidate without mutating generated world state.
     *
     * @param topology authoritative ordinary neighbor topology
     * @param candidateSystemId candidate faction-start system
     * @param requirements essential sustained dependency requirements
     * @param supply non-reserved physical supply-throughput closure
     * @param routes physical route evaluator
     * @param reserves finite accessible reserve rows
     * @param costEvaluator whole-route delivered-cost authority; may return empty per supplier
     * @param bufferProvider physical buffer authority; may return empty per commodity
     * @return immutable machine-readable dependency diagnostics
     */
    public static Report analyze(
            GalaxyTopology topology,
            StarSystemId candidateSystemId,
            List<Requirement> requirements,
            SupplyThroughputReport supply,
            RouteEvaluator routes,
            List<ReserveSource> reserves,
            DeliveredCostEvaluator costEvaluator,
            BufferStateProvider bufferProvider) {
        GalaxyTopology checkedTopology = Objects.requireNonNull(topology, "topology");
        StarSystemId candidate = Objects.requireNonNull(candidateSystemId, "candidateSystemId");
        if (checkedTopology.findSystem(candidate).isEmpty()) {
            throw new IllegalArgumentException("candidate system is outside topology: " + candidate);
        }
        Objects.requireNonNull(requirements, "requirements");
        ArrayList<Requirement> orderedRequirements = new ArrayList<>(requirements);
        if (orderedRequirements.isEmpty() || orderedRequirements.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("requirements must be non-empty and contain no nulls");
        }
        orderedRequirements.sort(Comparator.comparing(Requirement::commodityId));
        HashSet<String> commodityIds = new HashSet<>();
        for (Requirement requirement : orderedRequirements) {
            if (!commodityIds.add(requirement.commodityId())) {
                throw new IllegalArgumentException("duplicate requirement commodity: " + requirement.commodityId());
            }
        }
        SupplyThroughputReport checkedSupply = Objects.requireNonNull(supply, "supply");
        RouteEvaluator checkedRoutes = Objects.requireNonNull(routes, "routes");
        Objects.requireNonNull(reserves, "reserves");
        ArrayList<ReserveSource> orderedReserves = new ArrayList<>(reserves);
        if (orderedReserves.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("reserves cannot contain nulls");
        }
        orderedReserves.sort(Comparator.comparing(ReserveSource::commodityId)
                .thenComparing(ReserveSource::systemId)
                .thenComparing(ReserveSource::sourceId));
        DeliveredCostEvaluator checkedCosts = Objects.requireNonNull(costEvaluator, "costEvaluator");
        BufferStateProvider checkedBuffers = Objects.requireNonNull(bufferProvider, "bufferProvider");

        ArrayList<CommodityDiagnostic> diagnostics = new ArrayList<>();
        double totalRequirement = 0d;
        double totalLocalCovered = 0d;
        TreeMap<String, Double> familyRequirement = new TreeMap<>();
        TreeMap<String, Double> familyImportNeed = new TreeMap<>();
        TreeMap<String, Double> familyExportPotential = new TreeMap<>();
        int unresolvedCosts = 0;
        int unresolvedBuffers = 0;
        int unresolvedOwnership = 0;

        for (Requirement requirement : orderedRequirements) {
            CommodityDiagnostic diagnostic = analyzeCommodity(
                    checkedTopology,
                    candidate,
                    requirement,
                    checkedSupply,
                    checkedRoutes,
                    orderedReserves,
                    checkedCosts,
                    checkedBuffers);
            diagnostics.add(diagnostic);
            totalRequirement += requirement.requiredKgPerSecond();
            totalLocalCovered += Math.min(
                    diagnostic.localSupplyKgPerSecond(), requirement.requiredKgPerSecond());
            familyRequirement.merge(requirement.familyId(), requirement.requiredKgPerSecond(), Double::sum);
            familyImportNeed.merge(
                    requirement.familyId(),
                    requirement.requiredKgPerSecond() * diagnostic.importDependencyFraction(),
                    Double::sum);
            familyExportPotential.merge(
                    requirement.familyId(), diagnostic.localExportPotentialKgPerSecond(), Double::sum);
            if (diagnostic.deliveredCostBand().isEmpty()) {
                unresolvedCosts++;
            }
            if (diagnostic.bufferCoverageSeconds().isEmpty()) {
                unresolvedBuffers++;
            }
            if (diagnostic.ownershipConcentrationHhi().isEmpty()) {
                unresolvedOwnership++;
            }
        }

        TreeMap<String, Double> importDependencyByFamily = new TreeMap<>();
        for (Map.Entry<String, Double> entry : familyRequirement.entrySet()) {
            importDependencyByFamily.put(
                    entry.getKey(), familyImportNeed.getOrDefault(entry.getKey(), 0d) / entry.getValue());
        }

        List<CommodityDiagnostic> importDependent = diagnostics.stream()
                .filter(value -> value.importDependencyFraction() > 0d)
                .toList();
        int minimumExternalSuppliers = importDependent.isEmpty()
                ? 0
                : importDependent.stream().mapToInt(CommodityDiagnostic::externalSupplierCount).min().orElse(0);
        int minimumPathFloor = importDependent.isEmpty()
                ? 0
                : importDependent.stream().mapToInt(CommodityDiagnostic::alternativePathCountFloor).min().orElse(0);

        OptionalDouble maximumOwnership = diagnostics.stream().allMatch(value -> value.ownershipConcentrationHhi().isPresent())
                ? OptionalDouble.of(diagnostics.stream()
                        .mapToDouble(value -> value.ownershipConcentrationHhi().orElseThrow())
                        .max().orElse(0d))
                : OptionalDouble.empty();

        return new Report(
                CURRENT_VERSION,
                candidate,
                diagnostics,
                totalLocalCovered / totalRequirement,
                importDependencyByFamily,
                familyExportPotential,
                diagnostics.stream().mapToDouble(CommodityDiagnostic::throughputHeadroomKgPerSecond).min().orElse(0d),
                diagnostics.stream().mapToDouble(CommodityDiagnostic::supplierConcentrationHhi).max().orElse(0d),
                diagnostics.stream().mapToDouble(CommodityDiagnostic::routeConcentrationHhi).max().orElse(0d),
                diagnostics.stream().mapToDouble(CommodityDiagnostic::criticalGatewayDependencyFraction).max().orElse(0d),
                minimumExternalSuppliers,
                minimumPathFloor,
                diagnostics.stream().mapToDouble(CommodityDiagnostic::accessibleReserveConcentrationHhi).max().orElse(0d),
                maximumOwnership,
                unresolvedCosts,
                unresolvedBuffers,
                unresolvedOwnership);
    }

    private static CommodityDiagnostic analyzeCommodity(
            GalaxyTopology topology,
            StarSystemId candidate,
            Requirement requirement,
            SupplyThroughputReport supply,
            RouteEvaluator routes,
            List<ReserveSource> reserves,
            DeliveredCostEvaluator costs,
            BufferStateProvider buffers) {
        double localSupply = supply.capacityKgPerSecond(requirement.commodityId(), candidate);
        double localCoverage = Math.min(1d, localSupply / requirement.requiredKgPerSecond());
        double importDependency = 1d - localCoverage;
        double localExportPotential = Math.max(0d, localSupply - requirement.requiredKgPerSecond());

        ArrayList<SupplierEvidence> suppliers = new ArrayList<>();
        for (Map.Entry<SupplyKey, Double> entry : supply.capacityKgPerSecondBySupply().entrySet()) {
            SupplyKey key = entry.getKey();
            if (!key.commodityId().equals(requirement.commodityId())) {
                continue;
            }
            double producerCapacity = entry.getValue();
            RouteAssessment route;
            if (key.systemId().equals(candidate)) {
                route = new RouteAssessment(List.of(candidate), 0d, producerCapacity);
            } else {
                Optional<RouteAssessment> maybeRoute = routes.assess(key.systemId(), candidate);
                if (maybeRoute.isEmpty()) {
                    continue;
                }
                route = validateRoute(topology, key.systemId(), candidate, maybeRoute.orElseThrow());
                if (route.travelTimeS() > requirement.maxSupplierRouteTimeS()) {
                    continue;
                }
            }
            double deliveredCapacity = Math.min(producerCapacity, route.sustainableCargoThroughputKgPerSecond());
            if (!(deliveredCapacity > 0d)) {
                continue;
            }
            OptionalDouble deliveredCost = Objects.requireNonNull(
                    costs.deliveredCostMilliCreditsPerKg(
                            key.systemId(), candidate, requirement.commodityId(), route),
                    "delivered cost result");
            if (deliveredCost.isPresent()) {
                requireNonNegativeFinite(deliveredCost.getAsDouble(), "delivered cost");
            }
            JumpConnection gateway = key.systemId().equals(candidate)
                    ? null
                    : finalGateway(route.orderedSystems());
            int pathFloor = key.systemId().equals(candidate)
                    ? 0
                    : edgeDisjointPathFloor(topology, key.systemId(), candidate);
            suppliers.add(new SupplierEvidence(
                    key.systemId(), deliveredCapacity, route, deliveredCost, gateway, pathFloor));
        }
        suppliers.sort(Comparator.comparing(SupplierEvidence::systemId));
        double totalReachable = suppliers.stream().mapToDouble(SupplierEvidence::deliveredCapacityKgPerSecond).sum();
        double supplierHhi = hhiByKey(suppliers, value -> value.systemId().toString());
        List<SupplierEvidence> external = suppliers.stream()
                .filter(value -> !value.systemId().equals(candidate))
                .toList();
        double routeHhi = hhiByKey(external, value -> value.gateway().toString());
        double gatewayDependency = maximumShareByKey(external, value -> value.gateway().toString());
        int pathFloor = external.stream().mapToInt(SupplierEvidence::edgeDisjointPathFloor).max().orElse(0);

        ArrayList<Double> costSamples = new ArrayList<>();
        for (SupplierEvidence supplier : suppliers) {
            if (supplier.deliveredCostMilliCreditsPerKg().isPresent()) {
                costSamples.add(supplier.deliveredCostMilliCreditsPerKg().getAsDouble());
            }
        }
        Optional<DeliveredCostBand> costBand = costSamples.isEmpty()
                ? Optional.empty()
                : Optional.of(deliveredCostBand(costSamples, suppliers.size()));

        Optional<BufferState> buffer = Objects.requireNonNull(
                buffers.buffer(candidate, requirement.commodityId()), "buffer result");
        OptionalDouble bufferCoverage = buffer.isPresent()
                ? OptionalDouble.of(buffer.orElseThrow().coverageSeconds())
                : OptionalDouble.empty();

        List<ReserveSource> reachableReserves = reachableReserves(
                topology, candidate, requirement, routes, reserves);
        double reserveHhi = hhiReserves(reachableReserves);
        OptionalDouble ownershipHhi = ownershipHhi(reachableReserves);

        return new CommodityDiagnostic(
                requirement.commodityId(),
                requirement.familyId(),
                requirement.requiredKgPerSecond(),
                localSupply,
                totalReachable,
                localCoverage,
                importDependency,
                localExportPotential,
                totalReachable - requirement.requiredKgPerSecond(),
                suppliers.size(),
                external.size(),
                supplierHhi,
                routeHhi,
                gatewayDependency,
                pathFloor,
                costBand,
                bufferCoverage,
                reserveHhi,
                ownershipHhi);
    }

    private static List<ReserveSource> reachableReserves(
            GalaxyTopology topology,
            StarSystemId candidate,
            Requirement requirement,
            RouteEvaluator routes,
            List<ReserveSource> reserves) {
        ArrayList<ReserveSource> result = new ArrayList<>();
        for (ReserveSource reserve : reserves) {
            if (!reserve.commodityId().equals(requirement.commodityId())) {
                continue;
            }
            if (topology.findSystem(reserve.systemId()).isEmpty()) {
                throw new IllegalArgumentException("reserve source is outside topology: " + reserve.sourceId());
            }
            if (reserve.systemId().equals(candidate)) {
                result.add(reserve);
                continue;
            }
            Optional<RouteAssessment> maybeRoute = routes.assess(reserve.systemId(), candidate);
            if (maybeRoute.isEmpty()) {
                continue;
            }
            RouteAssessment route = validateRoute(topology, reserve.systemId(), candidate, maybeRoute.orElseThrow());
            if (route.travelTimeS() <= requirement.maxSupplierRouteTimeS()) {
                result.add(reserve);
            }
        }
        return List.copyOf(result);
    }

    private static DeliveredCostBand deliveredCostBand(List<Double> samples, int viableSupplierCount) {
        ArrayList<Double> sorted = new ArrayList<>(samples);
        sorted.sort(Double::compareTo);
        int size = sorted.size();
        double median = size % 2 == 1
                ? sorted.get(size / 2)
                : (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2d;
        return new DeliveredCostBand(
                sorted.get(0), median, sorted.get(size - 1), size, viableSupplierCount);
    }

    private static double hhiReserves(List<ReserveSource> reserves) {
        double total = reserves.stream().mapToDouble(ReserveSource::recoverableMassKg).sum();
        if (!(total > 0d)) {
            return 0d;
        }
        double result = 0d;
        for (ReserveSource reserve : reserves) {
            double share = reserve.recoverableMassKg() / total;
            result += share * share;
        }
        return clampUnit(result);
    }

    private static OptionalDouble ownershipHhi(List<ReserveSource> reserves) {
        if (reserves.isEmpty() || reserves.stream().anyMatch(value -> value.ownerId().isEmpty())) {
            return OptionalDouble.empty();
        }
        TreeMap<String, Double> massByOwner = new TreeMap<>();
        double total = 0d;
        for (ReserveSource reserve : reserves) {
            massByOwner.merge(reserve.ownerId().orElseThrow(), reserve.recoverableMassKg(), Double::sum);
            total += reserve.recoverableMassKg();
        }
        double result = 0d;
        for (double mass : massByOwner.values()) {
            double share = mass / total;
            result += share * share;
        }
        return OptionalDouble.of(clampUnit(result));
    }

    private interface EvidenceKey<T> {
        String key(T value);
    }

    private static <T extends WeightedEvidence> double hhiByKey(List<T> evidence, EvidenceKey<T> key) {
        double total = evidence.stream().mapToDouble(WeightedEvidence::weight).sum();
        if (!(total > 0d)) {
            return 0d;
        }
        TreeMap<String, Double> byKey = new TreeMap<>();
        for (T value : evidence) {
            byKey.merge(key.key(value), value.weight(), Double::sum);
        }
        double result = 0d;
        for (double weight : byKey.values()) {
            double share = weight / total;
            result += share * share;
        }
        return clampUnit(result);
    }

    private static <T extends WeightedEvidence> double maximumShareByKey(List<T> evidence, EvidenceKey<T> key) {
        double total = evidence.stream().mapToDouble(WeightedEvidence::weight).sum();
        if (!(total > 0d)) {
            return 0d;
        }
        TreeMap<String, Double> byKey = new TreeMap<>();
        for (T value : evidence) {
            byKey.merge(key.key(value), value.weight(), Double::sum);
        }
        return clampUnit(byKey.values().stream().mapToDouble(Double::doubleValue).max().orElse(0d) / total);
    }

    private static int edgeDisjointPathFloor(
            GalaxyTopology topology,
            StarSystemId origin,
            StarSystemId destination) {
        List<StarSystemId> path = shortestPath(topology, origin, destination, null);
        if (path.isEmpty()) {
            return 0;
        }
        if (path.size() == 1) {
            return 0;
        }
        for (int index = 0; index < path.size() - 1; index++) {
            JumpConnection removed = new JumpConnection(path.get(index), path.get(index + 1));
            if (shortestPath(topology, origin, destination, removed).isEmpty()) {
                return 1;
            }
        }
        return 2;
    }

    private static List<StarSystemId> shortestPath(
            GalaxyTopology topology,
            StarSystemId origin,
            StarSystemId destination,
            JumpConnection excluded) {
        if (origin.equals(destination)) {
            return List.of(origin);
        }
        ArrayDeque<StarSystemId> queue = new ArrayDeque<>();
        HashMap<StarSystemId, StarSystemId> previous = new HashMap<>();
        HashSet<StarSystemId> visited = new HashSet<>();
        queue.add(origin);
        visited.add(origin);
        while (!queue.isEmpty()) {
            StarSystemId current = queue.removeFirst();
            for (StarSystemId neighbor : topology.neighbors(current)) {
                JumpConnection edge = new JumpConnection(current, neighbor);
                if (excluded != null && excluded.equals(edge)) {
                    continue;
                }
                if (!visited.add(neighbor)) {
                    continue;
                }
                previous.put(neighbor, current);
                if (neighbor.equals(destination)) {
                    ArrayList<StarSystemId> path = new ArrayList<>();
                    StarSystemId cursor = destination;
                    path.add(cursor);
                    while (!cursor.equals(origin)) {
                        cursor = previous.get(cursor);
                        path.add(cursor);
                    }
                    Collections.reverse(path);
                    return List.copyOf(path);
                }
                queue.addLast(neighbor);
            }
        }
        return List.of();
    }

    private static RouteAssessment validateRoute(
            GalaxyTopology topology,
            StarSystemId origin,
            StarSystemId destination,
            RouteAssessment route) {
        List<StarSystemId> path = route.orderedSystems();
        if (!path.get(0).equals(origin) || !path.get(path.size() - 1).equals(destination)) {
            throw new IllegalArgumentException("dependency route endpoints do not match request");
        }
        if (origin.equals(destination)) {
            if (path.size() != 1) {
                throw new IllegalArgumentException("same-system dependency route must contain one system");
            }
            return route;
        }
        for (int index = 0; index < path.size() - 1; index++) {
            if (!topology.neighbors(path.get(index)).contains(path.get(index + 1))) {
                throw new IllegalArgumentException("dependency route contains a non-neighbor shortcut");
            }
        }
        return route;
    }

    private static JumpConnection finalGateway(List<StarSystemId> path) {
        if (path.size() < 2) {
            throw new IllegalArgumentException("external route must contain at least two systems");
        }
        return new JumpConnection(path.get(path.size() - 2), path.get(path.size() - 1));
    }

    private static Map<String, Double> immutableFractionMap(Map<String, Double> source, String field) {
        Objects.requireNonNull(source, field);
        TreeMap<String, Double> result = new TreeMap<>();
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            String key = requireText(entry.getKey(), field + " key");
            double value = Objects.requireNonNull(entry.getValue(), field + " value");
            requireUnitFraction(value, field + " value");
            result.put(key, value);
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Double> immutableNonNegativeMap(Map<String, Double> source, String field) {
        Objects.requireNonNull(source, field);
        TreeMap<String, Double> result = new TreeMap<>();
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            String key = requireText(entry.getKey(), field + " key");
            double value = Objects.requireNonNull(entry.getValue(), field + " value");
            requireNonNegativeFinite(value, field + " value");
            result.put(key, value);
        }
        return Collections.unmodifiableMap(result);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
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

    private static void requireUnitFraction(double value, String field) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(field + " must be in [0,1]");
        }
    }

    private static double clampUnit(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("concentration overflow");
        }
        return Math.max(0d, Math.min(1d, value));
    }

    private interface WeightedEvidence {
        double weight();
    }

    private record SupplierEvidence(
            StarSystemId systemId,
            double deliveredCapacityKgPerSecond,
            RouteAssessment route,
            OptionalDouble deliveredCostMilliCreditsPerKg,
            JumpConnection gateway,
            int edgeDisjointPathFloor) implements WeightedEvidence {
        @Override
        public double weight() {
            return deliveredCapacityKgPerSecond;
        }
    }
}
