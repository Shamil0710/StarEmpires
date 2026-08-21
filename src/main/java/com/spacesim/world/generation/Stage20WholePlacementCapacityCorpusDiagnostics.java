package com.spacesim.world.generation;

import com.spacesim.content.Stage18StationInfrastructureCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20FreightPortfolioAllocator;
import com.spacesim.world.Stage20FreightPortfolioAllocator.AllocationReport;
import com.spacesim.world.Stage20JumpEdgeCatalog;
import com.spacesim.world.Stage20LocalInfrastructureLayout;
import com.spacesim.world.Stage20LocalInfrastructureLayout.CalibratedConnection;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator.EndpointCycleProfile;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator.FreightFleetProfile;
import com.spacesim.world.Stage20PhysicalGalacticRoutePlanner;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;
import com.spacesim.world.Stage20WholePlacementProducerCapacityReservation;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.calibration.Stage20BootstrapFreightCapacityRequirementProfile;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.PhysicalTransportAuthority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Read-only Stage-20E fixed-corpus diagnostics that compose the corrected v2-candidate placement,
 * the derived finite per-start freight-capacity requirement, the production freight portfolio
 * allocator and whole-placement finite producer reservation.
 *
 * <p>The production probe is intentionally executed with the unchanged v2-candidate inputs. The
 * derived 13-freighter authority is injected only into this diagnostic route evaluator after
 * placement, so this class does not improve candidate acceptance, mutate a generated world or grant
 * ships to factions. Payload, fitted FTL plans, local-layout travel times, station transfer rates and
 * source provenance are preserved from the representative production profile.</p>
 *
 * <p>Each accepted placed start receives an independent <em>service-capacity budget</em> equal to
 * {@link Stage20BootstrapFreightCapacityRequirementProfile#requiredFreighterCountPerFactionStart()}.
 * That profile is a generation-quality requirement rather than an ownership/materialization claim.
 * The whole-placement reservation then asks whether the individually feasible portfolios can coexist
 * without double-counting any physical producer capacity. A reservation conflict remains diagnostic:
 * it does not prove that no alternative globally coordinated supplier mix exists.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20WholePlacementCapacityCorpusDiagnostics {
    /** Stable diagnostic version. */
    public static final String CURRENT_VERSION = "stage20e.whole-placement-capacity-corpus-diagnostics.v1";

    private Stage20WholePlacementCapacityCorpusDiagnostics() {
        throw new AssertionError("No instances");
    }

    /** Causal outcome for one fixed seed under the composed diagnostic. */
    public enum SeedStatus {
        /** Existing v2-candidate faction-start placement was not accepted. */ PLACEMENT_REJECTED,
        /** Placement was accepted, but at least one start failed the finite 13-ship portfolio allocator. */ START_ALLOCATION_REJECTED,
        /** Every start had a finite portfolio, but the selected portfolios conflict on producer capacity. */ PRODUCER_RESERVATION_CONFLICT,
        /** Placement, every finite start portfolio and shared producer reservation all succeeded. */ ACCEPTED
    }

    /**
     * Evidence for one fixed seed.
     *
     * @param rootSeed exact fixed-corpus root seed
     * @param placementStatus unchanged v2-candidate placement status
     * @param status composed diagnostic outcome
     * @param assignedStartCount number of starts exposed by an accepted placement
     * @param acceptedStartAllocationCount starts accepted by the finite portfolio allocator
     * @param totalMinimumRemoteFreighters sum of accepted start-level minimum remote freighter counts
     * @param reservationStatus present only when every selected start allocation was accepted
     * @param allocationFailureCounts rejected start-allocation reasons for this seed
     */
    public record SeedEvidence(
            long rootSeed,
            PlacementStatus placementStatus,
            SeedStatus status,
            int assignedStartCount,
            int acceptedStartAllocationCount,
            int totalMinimumRemoteFreighters,
            Optional<Stage20WholePlacementProducerCapacityReservation.Status> reservationStatus,
            Map<String, Integer> allocationFailureCounts) {
    }

    /**
     * Aggregate fixed-corpus evidence.
     *
     * @param version diagnostic version
     * @param candidateProfileVersion unchanged v2-candidate production profile version
     * @param bootstrapRequirementVersion corrected bootstrap requirement version
     * @param freightCapacityRequirementVersion derived per-start freight-capacity authority version
     * @param reservationVersion whole-placement producer reservation version
     * @param perStartFreighterBudget derived finite service-capacity budget
     * @param fixedSeedCount number of evaluated fixed seeds
     * @param acceptedPlacementSeedCount seeds with accepted v2-candidate placement
     * @param allStartAllocationsAcceptedSeedCount accepted placements where every start has a finite portfolio
     * @param producerReservationAcceptedSeedCount seeds whose selected portfolios coexist under producer ceilings
     * @param producerReservationConflictSeedCount seeds whose selected portfolios conflict on producer ceilings
     * @param allocationFailureCounts aggregate start-allocation failure reasons
     * @param seeds deterministic per-seed evidence
     */
    public record Report(
            String version,
            String candidateProfileVersion,
            String bootstrapRequirementVersion,
            String freightCapacityRequirementVersion,
            String reservationVersion,
            int perStartFreighterBudget,
            int fixedSeedCount,
            int acceptedPlacementSeedCount,
            int allStartAllocationsAcceptedSeedCount,
            int producerReservationAcceptedSeedCount,
            int producerReservationConflictSeedCount,
            Map<String, Integer> allocationFailureCounts,
            List<SeedEvidence> seeds) {
    }

    /**
     * Replays the fixed 1..16 corpus without changing production acceptance.
     *
     * @return deterministic composed capacity/reservation evidence
     */
    public static Report evaluateCurrent() {
        var profile = Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent();
        Stage20BootstrapFreightCapacityRequirementProfile capacity =
                Stage20BootstrapFreightCapacityRequirementProfile.deriveCurrent();
        if (!capacity.bootstrapRequirementVersion().equals(profile.bootstrapRequirementVersion())) {
            throw new IllegalStateException("freight-capacity authority and v2 candidate use different bootstrap requirements");
        }
        PhysicalTransportAuthority transport = profile.inputs().transport();
        if (Math.abs(transport.fleetProfile().payloadMassKgPerFreighter() - capacity.payloadMassKg()) > 1.0e-9d) {
            throw new IllegalStateException("freight-capacity authority and production profile use different payloads");
        }

        int budget = capacity.requiredFreighterCountPerFactionStart();
        Stage18StationInfrastructureCatalog stations = Stage18StationInfrastructureCatalogLoader.loadDefault();
        ArrayList<SeedEvidence> seeds = new ArrayList<>();
        TreeMap<String, Integer> allocationFailures = new TreeMap<>();
        int acceptedPlacements = 0;
        int allAllocationsAccepted = 0;
        int reservationsAccepted = 0;
        int reservationConflicts = 0;

        for (long rootSeed : Stage20RepresentativeSeedCorpus.seeds()) {
            var probe = Stage20GeneratedWorldProductionProbe.run(rootSeed, profile.inputs());
            PlacementResult placement = probe.placement().orElseThrow();
            if (placement.status() != PlacementStatus.ACCEPTED) {
                seeds.add(new SeedEvidence(
                        rootSeed,
                        placement.status(),
                        SeedStatus.PLACEMENT_REJECTED,
                        0,
                        0,
                        0,
                        Optional.empty(),
                        Map.of()));
                continue;
            }
            acceptedPlacements++;

            GalaxyTopology topology = probe.topology().requireAcceptedTopology();
            SupplyThroughputReport supply = probe.supplyThroughput().orElseThrow();
            Stage20PhysicalFreightRouteEvaluator routes = physicalRoutes(
                    topology,
                    probe.jumpEdges().orElseThrow(),
                    probe.localLayouts().orElseThrow(),
                    stations,
                    transport,
                    budget);
            List<CommodityRequirement> requirements =
                    profile.inputs().acceptance().bootstrapRequirements().essentialCommodities();

            TreeMap<String, AllocationReport> allocations = new TreeMap<>();
            TreeMap<String, Integer> seedFailures = new TreeMap<>();
            int acceptedStarts = 0;
            int totalMinimumFreighters = 0;
            for (var assignment : placement.assignments()) {
                AllocationReport allocation = Stage20FreightPortfolioAllocator.allocate(
                        topology,
                        supply,
                        assignment.systemId(),
                        requirements,
                        budget,
                        routes::assessWithAllocatedFreighters);
                if (allocation.accepted()) {
                    acceptedStarts++;
                    totalMinimumFreighters = Math.addExact(
                            totalMinimumFreighters,
                            allocation.minimumRemoteFreightersRequired());
                    allocations.put(assignment.stableFactionId(), allocation);
                } else {
                    String reason = allocation.failureReason().orElseThrow().name();
                    seedFailures.merge(reason, 1, Math::addExact);
                    allocationFailures.merge(reason, 1, Math::addExact);
                }
            }

            if (acceptedStarts != placement.assignments().size()) {
                seeds.add(new SeedEvidence(
                        rootSeed,
                        placement.status(),
                        SeedStatus.START_ALLOCATION_REJECTED,
                        placement.assignments().size(),
                        acceptedStarts,
                        totalMinimumFreighters,
                        Optional.empty(),
                        Map.copyOf(seedFailures)));
                continue;
            }

            allAllocationsAccepted++;
            var reservation = Stage20WholePlacementProducerCapacityReservation.reserve(
                    topology,
                    placement,
                    supply,
                    requirements,
                    allocations);
            SeedStatus seedStatus;
            if (reservation.status() == Stage20WholePlacementProducerCapacityReservation.Status.ACCEPTED) {
                reservationsAccepted++;
                seedStatus = SeedStatus.ACCEPTED;
            } else {
                reservationConflicts++;
                seedStatus = SeedStatus.PRODUCER_RESERVATION_CONFLICT;
            }
            seeds.add(new SeedEvidence(
                    rootSeed,
                    placement.status(),
                    seedStatus,
                    placement.assignments().size(),
                    acceptedStarts,
                    totalMinimumFreighters,
                    Optional.of(reservation.status()),
                    Map.of()));
        }

        return new Report(
                CURRENT_VERSION,
                profile.version(),
                profile.bootstrapRequirementVersion(),
                capacity.version(),
                Stage20WholePlacementProducerCapacityReservation.CURRENT_VERSION,
                budget,
                seeds.size(),
                acceptedPlacements,
                allAllocationsAccepted,
                reservationsAccepted,
                reservationConflicts,
                Map.copyOf(allocationFailures),
                List.copyOf(seeds));
    }

    /**
     * Serializes compact deterministic CI evidence.
     *
     * @param report measured report
     * @return stable text ending with a newline
     */
    public static String toText(Report report) {
        Report value = Objects.requireNonNull(report, "report");
        StringBuilder text = new StringBuilder(4_096);
        text.append("version=").append(value.version()).append('\n');
        text.append("candidateProfileVersion=").append(value.candidateProfileVersion()).append('\n');
        text.append("bootstrapRequirementVersion=").append(value.bootstrapRequirementVersion()).append('\n');
        text.append("freightCapacityRequirementVersion=").append(value.freightCapacityRequirementVersion()).append('\n');
        text.append("reservationVersion=").append(value.reservationVersion()).append('\n');
        text.append("perStartFreighterBudget=").append(value.perStartFreighterBudget()).append('\n');
        text.append("fixedSeedCount=").append(value.fixedSeedCount()).append('\n');
        text.append("acceptedPlacementSeedCount=").append(value.acceptedPlacementSeedCount()).append('\n');
        text.append("allStartAllocationsAcceptedSeedCount=")
                .append(value.allStartAllocationsAcceptedSeedCount()).append('\n');
        text.append("producerReservationAcceptedSeedCount=")
                .append(value.producerReservationAcceptedSeedCount()).append('\n');
        text.append("producerReservationConflictSeedCount=")
                .append(value.producerReservationConflictSeedCount()).append('\n');
        text.append("allocationFailureCounts=").append(new TreeMap<>(value.allocationFailureCounts())).append('\n');
        for (SeedEvidence seed : value.seeds()) {
            text.append("seed=").append(seed.rootSeed())
                    .append(" placement=").append(seed.placementStatus())
                    .append(" status=").append(seed.status())
                    .append(" starts=").append(seed.assignedStartCount())
                    .append(" acceptedStartAllocations=").append(seed.acceptedStartAllocationCount())
                    .append(" totalMinimumRemoteFreighters=").append(seed.totalMinimumRemoteFreighters())
                    .append(" reservation=").append(seed.reservationStatus().map(Enum::name).orElse("NONE"))
                    .append(" allocationFailures=").append(new TreeMap<>(seed.allocationFailureCounts()))
                    .append('\n');
        }
        return text.toString();
    }

    private static Stage20PhysicalFreightRouteEvaluator physicalRoutes(
            GalaxyTopology topology,
            Stage20JumpEdgeCatalog jumpEdges,
            List<Stage20LocalInfrastructureLayout> layouts,
            Stage18StationInfrastructureCatalog stations,
            PhysicalTransportAuthority transport,
            int activeFreighterCount) {
        FreightFleetProfile baseFleet = transport.fleetProfile();
        FreightFleetProfile diagnosticFleet = new FreightFleetProfile(
                baseFleet.version() + ":diagnostic-count-" + activeFreighterCount,
                baseFleet.payloadMassKgPerFreighter(),
                activeFreighterCount,
                baseFleet.sourceEvidenceId(),
                baseFleet.stage22ReviewRequired());

        TreeMap<StarSystemId, EndpointAuthority> endpointBySystem = new TreeMap<>();
        for (Stage20LocalInfrastructureLayout layout : layouts) {
            StationArchetypeDefinition hub = stations.findArchetype(
                    layout.placement(layout.majorHubId()).stationArchetypeId().orElseThrow());
            if (hub == null) {
                throw new IllegalArgumentException("generated hub references unknown station archetype");
            }
            double maximumLocalTravelSeconds = layout.connections().stream()
                    .filter(value -> !touchesJumpAnchor(layout, value))
                    .map(CalibratedConnection::logisticsConsequences)
                    .mapToDouble(value -> value.civilianRoutineTravelTimeMaxS())
                    .max()
                    .orElse(0d);
            double jumpAccessSeconds = layout.connections().stream()
                    .filter(value -> touchesJumpAnchor(layout, value))
                    .map(CalibratedConnection::logisticsConsequences)
                    .mapToDouble(value -> value.civilianRoutineTravelTimeMaxS())
                    .max()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "diagnostic layout lacks jump-arrival physical access: " + layout.systemId()));
            endpointBySystem.put(
                    layout.systemId(),
                    new EndpointAuthority(
                            maximumLocalTravelSeconds,
                            jumpAccessSeconds,
                            hub.transferMassRateKgPerSecond(),
                            "stage20c-layout:" + layout.routeCalibrationVersion()));
        }

        Stage20PhysicalGalacticRoutePlanner loaded = new Stage20PhysicalGalacticRoutePlanner(
                topology, transport.loadedOutboundPlan(), jumpEdges);
        Stage20PhysicalGalacticRoutePlanner returned = new Stage20PhysicalGalacticRoutePlanner(
                topology, transport.returnPlan(), jumpEdges);
        return new Stage20PhysicalFreightRouteEvaluator(
                loaded,
                returned,
                diagnosticFleet,
                (origin, destination) -> {
                    EndpointAuthority from = endpointBySystem.get(origin);
                    EndpointAuthority to = endpointBySystem.get(destination);
                    if (from == null || to == null) {
                        return Optional.empty();
                    }
                    double outboundLocal;
                    double returnLocal;
                    if (origin.equals(destination)) {
                        outboundLocal = from.maximumLocalTravelSeconds();
                        returnLocal = from.maximumLocalTravelSeconds();
                    } else {
                        outboundLocal = from.maximumLocalTravelSeconds()
                                + from.jumpAccessSeconds()
                                + to.jumpAccessSeconds();
                        returnLocal = to.maximumLocalTravelSeconds()
                                + to.jumpAccessSeconds()
                                + from.jumpAccessSeconds();
                    }
                    return Optional.of(new EndpointCycleProfile(
                            outboundLocal,
                            returnLocal,
                            from.transferRateKgPerSecond(),
                            to.transferRateKgPerSecond(),
                            from.sourceEvidenceId() + "+" + to.sourceEvidenceId()));
                });
    }

    private static boolean touchesJumpAnchor(
            Stage20LocalInfrastructureLayout layout,
            CalibratedConnection connection) {
        return layout.placement(connection.fromId()).kind() == PlacementKind.JUMP_ARRIVAL_ANCHOR
                || layout.placement(connection.toId()).kind() == PlacementKind.JUMP_ARRIVAL_ANCHOR;
    }

    private record EndpointAuthority(
            double maximumLocalTravelSeconds,
            double jumpAccessSeconds,
            double transferRateKgPerSecond,
            String sourceEvidenceId) {
    }
}