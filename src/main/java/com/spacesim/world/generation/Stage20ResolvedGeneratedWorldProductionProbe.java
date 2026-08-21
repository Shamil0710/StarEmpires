package com.spacesim.world.generation;

import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator;
import com.spacesim.world.Stage20ResolvedFreightAcceptance;
import com.spacesim.world.calibration.Stage20CoordinatedFreightAcceptanceProfile;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.ProbeResult;
import com.spacesim.world.generation.Stage20RepresentativeGeneratedWorldProbeProfileV3.DerivedProfile;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Stage-20E production probe whose authoritative whole-seed decision uses coordinated finite freight.
 *
 * <p>The existing {@link Stage20GeneratedWorldProductionProbe} remains the reproducible physical
 * generation/evidence pipeline for v1/v2 profiles. This wrapper consumes its exact generated topology,
 * local layouts, resources, supply closure, candidate evaluations and placement once, then replaces
 * only the final economic acceptance authority for v3: accepted placement is evaluated by
 * {@link Stage20ResolvedFreightAcceptance}, and whole-seed status is composed through resolved-freight
 * v2 semantics.</p>
 *
 * <p>The historical single-supplier result embedded in the source probe remains diagnostic evidence;
 * it is not consulted when deciding the resolved v3 whole-seed status. No generated layer is rerun,
 * repaired or mutated after observing coordinated freight.</p>
 */
public final class Stage20ResolvedGeneratedWorldProductionProbe {
    /** Stable resolved production-probe version. */
    public static final String CURRENT_VERSION = "stage20e.resolved-production-seed-probe.v1";

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
     * @param generation unchanged underlying generated-world evidence
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
        /** Validates one immutable resolved production result. */
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
        ProbeResult generation = Stage20GeneratedWorldProductionProbe.run(rootSeed, authority.inputs());
        if (generation.topology().status() == Stage20JumpTopologyGenerationResult.Status.REJECTED_SEED) {
            Stage20GeneratedWorldSeedAcceptance.SeedResult seedAcceptance =
                    Stage20GeneratedWorldSeedAcceptance.composeResolvedFreight(
                            generation.topology(), Optional.empty(), Optional.empty());
            return new ResolvedProbeResult(
                    CURRENT_VERSION,
                    rootSeed,
                    generation.version(),
                    authority.version(),
                    generation,
                    Optional.empty(),
                    seedAcceptance);
        }

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

    /** @return resolved production result under the current representative v3 profile */
    public static ResolvedProbeResult runCurrent(long rootSeed) {
        return run(rootSeed, Stage20RepresentativeGeneratedWorldProbeProfileV3.deriveCurrent());
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
