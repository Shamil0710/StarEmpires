package com.spacesim.world.generation;

import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.Stage20DirectionalJumpAnchorLayout;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance;
import com.spacesim.world.Stage20JumpEdgeCatalog;
import com.spacesim.world.Stage20JumpEdgeStateMaterializer;
import com.spacesim.world.Stage20LocalInfrastructureLayout;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator;
import com.spacesim.world.Stage20ResolvedFreightAcceptance;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.calibration.Stage20CoordinatedFreightAcceptanceProfile;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.ProbeResult;
import com.spacesim.world.generation.Stage20RepresentativeGeneratedWorldProbeProfileV3.DerivedProfile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Stage-20E production probe whose authoritative whole-seed decision uses coordinated finite freight.
 *
 * <p>The existing {@link Stage20GeneratedWorldProductionProbe} remains the reproducible physical
 * generation/evidence pipeline for v1/v2 profiles. The current resolved wrapper additionally composes
 * the accepted macro topology with Stage-20C local geometry before final freight evaluation: each
 * canonical jump-arrival anchor keeps its generated calibrated hub distance but is rotated onto the
 * side of the system facing the neighbor represented by that ordinary edge. Stage-20D edge metadata
 * is then rematerialized from those exact aligned local layouts.</p>
 *
 * <p>This topology-facing finalization changes only jump-anchor azimuth. Hub-to-anchor radial distance,
 * accepted local-route bands, resources, supply closure, candidate evaluations and placement remain
 * the source probe's deterministic evidence. Coordinated freight is evaluated against the finalized
 * layouts and exact rematerialized jump endpoints, so the playable/persisted resolved world cannot
 * silently fall back to randomly oriented entry lanes.</p>
 *
 * <p>The historical single-supplier result embedded in the source probe remains diagnostic evidence;
 * it is not consulted when deciding the resolved v3 whole-seed status. No failed economic result may
 * request another resource, station, topology edge or root seed through this wrapper.</p>
 */
public final class Stage20ResolvedGeneratedWorldProductionProbe {
    /** Stable resolved production-probe version. */
    public static final String CURRENT_VERSION = "stage20e.resolved-production-seed-probe.v2";

    private Stage20ResolvedGeneratedWorldProductionProbe() {
        throw new AssertionError("No instances");
    }

    /**
     * Complete resolved production evidence for one exact root seed.
     *
     * @param version resolved probe version
     * @param rootSeed exact root seed
     * @param sourceProbeVersion preserved physical generation probe version
     * @param representativeProfileVersion v3 representative profile version
     * @param generation finalized generated-world evidence with topology-facing jump endpoints
     * @param coordinatedFreightAcceptance present exactly for accepted faction-start placement
     * @param seedAcceptance authoritative resolved-freight whole-seed result
     */
    public record ResolvedProbeResult(
            String version,
            long rootSeed,
            String sourceProbeVersion,
            String representativeProfileVersion,
            ProbeResult generation,
            Optional<Stage20ResolvedFreightAcceptance.AcceptanceReport> coordinatedFreightAcceptance,
            Stage20GeneratedWorldSeedAcceptance.SeedResult seedAcceptance) {
        /**
         * Validates one immutable resolved production result.
         *
         * @param version resolved probe version
         * @param rootSeed exact root seed
         * @param sourceProbeVersion preserved physical generation probe version
         * @param representativeProfileVersion v3 representative profile version
         * @param generation finalized generated-world evidence
         * @param coordinatedFreightAcceptance present exactly for accepted faction-start placement
         * @param seedAcceptance authoritative resolved-freight whole-seed result
         */
        public ResolvedProbeResult {
            version = requireText(version, "version");
            sourceProbeVersion = requireText(sourceProbeVersion, "sourceProbeVersion");
            representativeProfileVersion = requireText(
                    representativeProfileVersion, "representativeProfileVersion");
            Objects.requireNonNull(generation, "generation");
            Objects.requireNonNull(coordinatedFreightAcceptance, "coordinatedFreightAcceptance");
            Objects.requireNonNull(seedAcceptance, "seedAcceptance");
            if (generation.rootSeed() != rootSeed || seedAcceptance.rootSeed() != rootSeed) {
                throw new IllegalArgumentException("resolved production evidence root seeds differ");
            }
            if (!sourceProbeVersion.equals(generation.version())) {
                throw new IllegalArgumentException("sourceProbeVersion must equal preserved generation version");
            }
            if (!Stage20GeneratedWorldSeedAcceptance.RESOLVED_FREIGHT_VERSION.equals(seedAcceptance.version())) {
                throw new IllegalArgumentException("resolved production result requires v2 whole-seed composition");
            }
            boolean topologyAccepted = generation.topology().status()
                    == Stage20JumpTopologyGenerationResult.Status.ACCEPTED;
            if (!topologyAccepted) {
                if (generation.placement().isPresent() || coordinatedFreightAcceptance.isPresent()) {
                    throw new IllegalArgumentException(
                            "topology-rejected resolved result cannot carry placement/freight evidence");
                }
            } else {
                PlacementResult placement = generation.placement().orElseThrow(() ->
                        new IllegalArgumentException("accepted topology requires placement evidence"));
                boolean freightRequired = placement.status() == PlacementStatus.ACCEPTED;
                if (freightRequired != coordinatedFreightAcceptance.isPresent()) {
                    throw new IllegalArgumentException(
                            "coordinated freight must exist exactly for accepted placement");
                }
                if (coordinatedFreightAcceptance.isPresent()
                        && coordinatedFreightAcceptance.orElseThrow().rootSeed() != rootSeed) {
                    throw new IllegalArgumentException(
                            "coordinated freight and resolved production root seeds differ");
                }
            }
        }
    }

    /**
     * Runs the resolved production path for one exact seed and explicit v3 profile.
     *
     * @param rootSeed exact root seed
     * @param profile explicit representative v3 profile and coordinated freight policy
     * @return immutable resolved production result
     */
    public static ResolvedProbeResult run(long rootSeed, DerivedProfile profile) {
        DerivedProfile authority = Objects.requireNonNull(profile, "profile");
        ProbeResult sourceGeneration = Stage20GeneratedWorldProductionProbe.run(rootSeed, authority.inputs());
        if (sourceGeneration.topology().status() == Stage20JumpTopologyGenerationResult.Status.REJECTED_SEED) {
            Stage20GeneratedWorldSeedAcceptance.SeedResult seedAcceptance =
                    Stage20GeneratedWorldSeedAcceptance.composeResolvedFreight(
                            sourceGeneration.topology(), Optional.empty(), Optional.empty());
            return new ResolvedProbeResult(
                    CURRENT_VERSION,
                    rootSeed,
                    sourceGeneration.version(),
                    authority.version(),
                    sourceGeneration,
                    Optional.empty(),
                    seedAcceptance);
        }

        ProbeResult generation = withDirectionalJumpAnchors(sourceGeneration);
        PlacementResult placement = generation.placement().orElseThrow();
        Optional<Stage20ResolvedFreightAcceptance.AcceptanceReport> freight = Optional.empty();
        if (placement.status() == PlacementStatus.ACCEPTED) {
            Stage20CoordinatedFreightAcceptanceProfile policy = authority.coordinatedFreightAcceptance();
            int perStartCapacity = policy.requiredFreighterCountPerFactionStart();
            Stage20PhysicalFreightRouteEvaluator allocatedRoutes =
                    Stage20PhysicalFreightRouteEvaluatorFactory.create(
                            generation.topology().requireAcceptedTopology(),
                            generation.jumpEdges().orElseThrow(),
                            generation.localLayouts().orElseThrow(),
                            Stage18StationInfrastructureCatalogLoader.loadDefault(),
                            authority.inputs().transport(),
                            perStartCapacity);
            Map<String, Integer> budgets = freightBudgets(placement, perStartCapacity);
            freight = Optional.of(Stage20ResolvedFreightAcceptance.evaluate(
                    generation.topology().requireAcceptedTopology(),
                    placement,
                    generation.supplyThroughput().orElseThrow(),
                    authority.inputs().acceptance().bootstrapRequirements().essentialCommodities(),
                    budgets,
                    policy.searchNodeBudgetPerCommodity(),
                    allocatedRoutes::assessWithAllocatedFreighters));
        }

        Stage20GeneratedWorldSeedAcceptance.SeedResult seedAcceptance =
                Stage20GeneratedWorldSeedAcceptance.composeResolvedFreight(
                        generation.topology(), freight, Optional.of(placement));
        return new ResolvedProbeResult(
                CURRENT_VERSION,
                rootSeed,
                generation.version(),
                authority.version(),
                generation,
                freight,
                seedAcceptance);
    }

    /**
     * Runs the resolved production path under the current representative v3 profile.
     *
     * @param rootSeed exact root seed
     * @return resolved production result under the current representative v3 profile
     */
    public static ResolvedProbeResult runCurrent(long rootSeed) {
        return run(rootSeed, Stage20RepresentativeGeneratedWorldProbeProfileV3.deriveCurrent());
    }

    private static ProbeResult withDirectionalJumpAnchors(ProbeResult source) {
        GalaxyTopology topology = source.topology().requireAcceptedTopology();
        List<Stage20LocalInfrastructureLayout> layouts = Stage20DirectionalJumpAnchorLayout.alignAll(
                topology, source.localLayouts().orElseThrow());
        TreeMap<StarSystemId, Stage20LocalInfrastructureLayout> bySystem = new TreeMap<>();
        for (Stage20LocalInfrastructureLayout layout : layouts) {
            bySystem.put(layout.systemId(), layout);
        }
        Stage20JumpEdgeCatalog jumpEdges = Stage20JumpEdgeStateMaterializer.materializeCurrent(
                topology, bySystem);
        return new ProbeResult(
                source.version(),
                source.rootSeed(),
                source.macroGeometry(),
                source.topology(),
                Optional.of(jumpEdges),
                Optional.of(layouts),
                source.physicalHosts(),
                source.resourceWorld(),
                source.logisticsReport(),
                source.supplyThroughput(),
                source.candidateEvaluations(),
                source.placement(),
                source.economicAcceptance(),
                source.seedAcceptance());
    }

    private static Map<String, Integer> freightBudgets(PlacementResult placement, int perStartCapacity) {
        TreeMap<String, Integer> result = new TreeMap<>();
        for (Assignment assignment : placement.assignments()) {
            if (result.put(assignment.stableFactionId(), perStartCapacity) != null) {
                throw new IllegalArgumentException("duplicate placed faction in freight budget projection");
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("accepted placement must contain at least one faction start");
        }
        return Map.copyOf(result);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }
}