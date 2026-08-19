package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20IntersystemCadenceCalibrationProfile.BandId;
import com.spacesim.world.calibration.Stage20IntersystemCadenceCalibrationProfile.CadenceBand;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;

import java.util.List;
import java.util.Objects;

/**
 * Versioned Stage-20A generation-quality budgets for ordinary inter-system topology.
 *
 * <p>The accepted cross-stage topology contract defines the metrics but intentionally leaves their
 * numeric acceptance bands to Stage-20 calibration. This profile authors those provisional quality
 * budgets after representative FTL cadence established one-hop, regional 3-5-hop and 3-hop fleet
 * reinforcement consequences. The numbers are generator acceptance policy, not physical ship laws;
 * Stage 20D must measure generated graphs against them and repair/reject seeds deterministically.</p>
 *
 * @param version stable topology-quality profile version
 * @param authority explicit provisional generation-policy authority
 * @param cadenceProfileVersion accepted inter-system cadence profile used by this policy
 * @param stage22ReviewRequired whether playable/economic review may revise the budgets
 * @param structuralBudget graph-structure quality limits
 * @param sectorExitBand accepted ordinary exit-count band per developed sector
 * @param hubDegreeBand accepted local hub-degree band
 * @param regionalHopDistanceBand accepted ordinary regional route-hop band
 * @param provenance exact design/calibration provenance
 */
public record Stage20TopologyQualityCalibrationProfile(
        String version,
        CalibrationAuthority authority,
        String cadenceProfileVersion,
        boolean stage22ReviewRequired,
        StructuralBudget structuralBudget,
        IntBand sectorExitBand,
        IntBand hubDegreeBand,
        IntBand regionalHopDistanceBand,
        List<String> provenance) {
    /** Current Stage-20A topology-quality profile. */
    public static final String CURRENT_VERSION = "stage20a.topology-quality-bands.v1";

    private static final int AUTHORED_MAX_LINEAR_CORRIDOR_EDGES = 3;
    private static final double AUTHORED_MAX_DEGREE_ONE_FRACTION = 0.20d;
    private static final double AUTHORED_MIN_REGIONAL_CYCLE_COVERAGE = 0.50d;
    private static final int AUTHORED_MIN_CORE_EDGE_DISJOINT_ROUTES = 2;
    private static final double AUTHORED_MAX_SINGLE_GATEWAY_DEPENDENCY = 0.45d;
    private static final int AUTHORED_MIN_SECTOR_EXITS = 2;
    private static final int AUTHORED_MAX_SECTOR_EXITS = 4;
    private static final int AUTHORED_MIN_HUB_DEGREE = 3;
    private static final int AUTHORED_MAX_HUB_DEGREE = 6;

    /**
     * Validates one immutable topology-quality policy.
     *
     * @param version stable topology-quality profile version
     * @param authority explicit provisional generation-policy authority
     * @param cadenceProfileVersion accepted cadence profile version
     * @param stage22ReviewRequired whether later playable/economic review is required
     * @param structuralBudget graph-structure quality limits
     * @param sectorExitBand developed-sector ordinary exit-count band
     * @param hubDegreeBand local hub-degree band
     * @param regionalHopDistanceBand ordinary regional route-hop band
     * @param provenance exact source provenance
     */
    public Stage20TopologyQualityCalibrationProfile {
        requireText(version, "version");
        Objects.requireNonNull(authority, "authority");
        requireText(cadenceProfileVersion, "cadenceProfileVersion");
        Objects.requireNonNull(structuralBudget, "structuralBudget");
        Objects.requireNonNull(sectorExitBand, "sectorExitBand");
        Objects.requireNonNull(hubDegreeBand, "hubDegreeBand");
        Objects.requireNonNull(regionalHopDistanceBand, "regionalHopDistanceBand");
        Objects.requireNonNull(provenance, "provenance");
        if (provenance.isEmpty() || provenance.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("provenance must be non-empty and contain no blanks");
        }
        provenance = provenance.stream().sorted().toList();
        if (authority == CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE && !stage22ReviewRequired) {
            throw new IllegalArgumentException("provisional topology policy requires Stage-22 review");
        }
    }

    /**
     * Derives the current quality budgets while inheriting the accepted regional 3-5 hop cadence.
     *
     * @return deterministic current topology-quality calibration profile
     */
    public static Stage20TopologyQualityCalibrationProfile deriveCurrent() {
        Stage20IntersystemCadenceCalibrationProfile cadence =
                Stage20IntersystemCadenceCalibrationProfile.deriveCurrent();
        int regionalMinHops = cadenceBand(cadence, BandId.REGIONAL_3_HOP).hopCount();
        int regionalMaxHops = cadenceBand(cadence, BandId.REGIONAL_5_HOP).hopCount();
        int reinforcementHops = cadenceBand(cadence, BandId.FLEET_REINFORCEMENT_3_HOP).hopCount();
        if (regionalMinHops != reinforcementHops) {
            throw new IllegalStateException("Regional lower-hop and reinforcement calibration anchors diverged");
        }

        return new Stage20TopologyQualityCalibrationProfile(
                CURRENT_VERSION,
                CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE,
                cadence.version(),
                true,
                new StructuralBudget(
                        AUTHORED_MAX_LINEAR_CORRIDOR_EDGES,
                        AUTHORED_MAX_DEGREE_ONE_FRACTION,
                        AUTHORED_MIN_REGIONAL_CYCLE_COVERAGE,
                        AUTHORED_MIN_CORE_EDGE_DISJOINT_ROUTES,
                        AUTHORED_MAX_SINGLE_GATEWAY_DEPENDENCY),
                new IntBand(AUTHORED_MIN_SECTOR_EXITS, AUTHORED_MAX_SECTOR_EXITS),
                new IntBand(AUTHORED_MIN_HUB_DEGREE, AUTHORED_MAX_HUB_DEGREE),
                new IntBand(regionalMinHops, regionalMaxHops),
                List.of(
                        "docs/galaxy_topology_resource_geography_generation_contract.md:sections_5-9",
                        "docs/stage20_physical_world_generation_plan.md:Stage20A+Stage20D",
                        "Stage20IntersystemCadenceCalibrationProfile:" + cadence.version()));
    }

    /**
     * Returns whether every topology-quality acceptance band required by Stage 20A is explicit.
     *
     * @return true when Stage 20B/20D may consume this versioned quality policy
     */
    public boolean closesStage20BEntryCoverage() {
        return CURRENT_VERSION.equals(version)
                && authority == CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE
                && Stage20IntersystemCadenceCalibrationProfile.CURRENT_VERSION.equals(cadenceProfileVersion)
                && stage22ReviewRequired
                && structuralBudget.maxLinearCorridorLengthEdges() > 0
                && structuralBudget.maxDegreeOneFraction() > 0d
                && structuralBudget.maxDegreeOneFraction() < 1d
                && structuralBudget.minRegionalCycleCoverage() > 0d
                && structuralBudget.minRegionalCycleCoverage() <= 1d
                && structuralBudget.minCoreEdgeDisjointRoutes() >= 2
                && structuralBudget.maxSingleGatewayDependency() > 0d
                && structuralBudget.maxSingleGatewayDependency() < 0.5d
                && sectorExitBand.minInclusive() >= 2
                && hubDegreeBand.minInclusive() >= 3
                && regionalHopDistanceBand.minInclusive() == 3
                && regionalHopDistanceBand.maxInclusive() == 5;
    }

    private static CadenceBand cadenceBand(Stage20IntersystemCadenceCalibrationProfile cadence, BandId id) {
        return cadence.bands().stream()
                .filter(value -> value.id() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing Stage-20 cadence band " + id));
    }

    /**
     * Fractional/integer graph-quality constraints applied by later generated-topology diagnostics.
     *
     * @param maxLinearCorridorLengthEdges maximum accidental choice-free corridor length in edges
     * @param maxDegreeOneFraction maximum ordinary-system fraction with degree one
     * @param minRegionalCycleCoverage minimum regional-system fraction participating in alternate/cyclic routing
     * @param minCoreEdgeDisjointRoutes minimum edge-disjoint route count required between major core nodes
     * @param maxSingleGatewayDependency maximum regional dependency fraction allowed on one gateway
     */
    public record StructuralBudget(
            int maxLinearCorridorLengthEdges,
            double maxDegreeOneFraction,
            double minRegionalCycleCoverage,
            int minCoreEdgeDisjointRoutes,
            double maxSingleGatewayDependency) {
        /**
         * Validates structural quality constraints.
         *
         * @param maxLinearCorridorLengthEdges maximum accidental corridor length
         * @param maxDegreeOneFraction maximum degree-one fraction
         * @param minRegionalCycleCoverage minimum cycle/alternate-route coverage
         * @param minCoreEdgeDisjointRoutes minimum core route redundancy
         * @param maxSingleGatewayDependency maximum dependency on one gateway
         */
        public StructuralBudget {
            if (maxLinearCorridorLengthEdges <= 0) {
                throw new IllegalArgumentException("maxLinearCorridorLengthEdges must be positive");
            }
            requireUnitFractionExclusiveUpper(maxDegreeOneFraction, "maxDegreeOneFraction");
            requireUnitFractionInclusive(minRegionalCycleCoverage, "minRegionalCycleCoverage");
            if (minCoreEdgeDisjointRoutes < 2) {
                throw new IllegalArgumentException("minCoreEdgeDisjointRoutes must preserve an alternate route");
            }
            requireUnitFractionExclusiveUpper(maxSingleGatewayDependency, "maxSingleGatewayDependency");
        }
    }

    /**
     * Inclusive integer acceptance band.
     *
     * @param minInclusive lower accepted value
     * @param maxInclusive upper accepted value
     */
    public record IntBand(int minInclusive, int maxInclusive) {
        /**
         * Validates one positive ordered integer band.
         *
         * @param minInclusive lower accepted value
         * @param maxInclusive upper accepted value
         */
        public IntBand {
            if (minInclusive <= 0 || maxInclusive < minInclusive) {
                throw new IllegalArgumentException("integer band must be positive and ordered");
            }
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static void requireUnitFractionExclusiveUpper(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d || value >= 1d) {
            throw new IllegalArgumentException(field + " must be finite and in (0,1)");
        }
    }

    private static void requireUnitFractionInclusive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d || value > 1d) {
            throw new IllegalArgumentException(field + " must be finite and in (0,1]");
        }
    }
}
