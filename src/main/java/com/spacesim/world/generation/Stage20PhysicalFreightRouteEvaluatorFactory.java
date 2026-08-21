package com.spacesim.world.generation;

import com.spacesim.content.Stage18StationInfrastructureCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.Stage20JumpEdgeCatalog;
import com.spacesim.world.Stage20LocalInfrastructureLayout;
import com.spacesim.world.Stage20LocalInfrastructureLayout.CalibratedConnection;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator.EndpointCycleProfile;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator.FreightFleetProfile;
import com.spacesim.world.Stage20PhysicalGalacticRoutePlanner;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.PhysicalTransportAuthority;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Production-safe Stage-20E factory for one physical freight evaluator at an explicit fleet allocation.
 *
 * <p>The generated-world production probe already owns fitted loaded/return jump plans, representative
 * payload and Stage-20C endpoint geometry/handling inputs. Earlier freight-capacity diagnostics had to
 * reconstruct the same evaluator with a different active-freighter count. This factory centralizes that
 * projection so future production acceptance can consume the derived finite freight capacity without
 * depending on a diagnostics class or inventing a second route model.</p>
 *
 * <p>Only the number of already-authorized identical freighters changes. Payload, FTL plans, topology,
 * local access and transfer-rate authorities are copied unchanged from the supplied generation state.</p>
 */
public final class Stage20PhysicalFreightRouteEvaluatorFactory {
    /** Stable explicit-allocation factory version. */
    public static final String CURRENT_VERSION = "stage20e.physical-freight-route-evaluator-factory.v1";

    private Stage20PhysicalFreightRouteEvaluatorFactory() {
        throw new AssertionError("No instances");
    }

    /**
     * Creates the physical freight evaluator for an explicit finite allocation count.
     *
     * @param topology authoritative accepted explicit-neighbor topology
     * @param jumpEdges exact physical jump-edge state covering the topology
     * @param layouts generated Stage-20C local infrastructure layouts
     * @param stations authoritative Stage-18 station infrastructure catalog
     * @param transport fitted loaded/return transport authority and representative payload
     * @param activeFreighterCount positive number of identical authorized freighters available to allocation
     * @return physical route evaluator using the exact supplied physics with the requested fleet count
     */
    public static Stage20PhysicalFreightRouteEvaluator create(
            GalaxyTopology topology,
            Stage20JumpEdgeCatalog jumpEdges,
            List<Stage20LocalInfrastructureLayout> layouts,
            Stage18StationInfrastructureCatalog stations,
            PhysicalTransportAuthority transport,
            int activeFreighterCount) {
        GalaxyTopology checkedTopology = Objects.requireNonNull(topology, "topology");
        Stage20JumpEdgeCatalog checkedEdges = Objects.requireNonNull(jumpEdges, "jumpEdges");
        List<Stage20LocalInfrastructureLayout> checkedLayouts = List.copyOf(
                Objects.requireNonNull(layouts, "layouts"));
        Stage18StationInfrastructureCatalog checkedStations = Objects.requireNonNull(stations, "stations");
        PhysicalTransportAuthority checkedTransport = Objects.requireNonNull(transport, "transport");
        if (activeFreighterCount <= 0) {
            throw new IllegalArgumentException("activeFreighterCount must be positive");
        }
        if (!checkedEdges.topology().equals(checkedTopology)) {
            throw new IllegalArgumentException("jump-edge catalog must cover the supplied topology");
        }

        FreightFleetProfile baseFleet = checkedTransport.fleetProfile();
        FreightFleetProfile allocatedFleet = new FreightFleetProfile(
                baseFleet.version() + ":allocation-count-" + activeFreighterCount,
                baseFleet.payloadMassKgPerFreighter(),
                activeFreighterCount,
                baseFleet.sourceEvidenceId(),
                baseFleet.stage22ReviewRequired());

        TreeMap<StarSystemId, EndpointAuthority> endpointBySystem = new TreeMap<>();
        for (Stage20LocalInfrastructureLayout layout : checkedLayouts) {
            Objects.requireNonNull(layout, "layout");
            if (checkedTopology.findSystem(layout.systemId()).isEmpty()) {
                throw new IllegalArgumentException("local layout system is outside supplied topology");
            }
            if (endpointBySystem.containsKey(layout.systemId())) {
                throw new IllegalArgumentException("duplicate local layout system: " + layout.systemId());
            }
            StationArchetypeDefinition hub = checkedStations.findArchetype(
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
                            "generated layout lacks jump-arrival physical access: " + layout.systemId()));
            endpointBySystem.put(
                    layout.systemId(),
                    new EndpointAuthority(
                            maximumLocalTravelSeconds,
                            jumpAccessSeconds,
                            hub.transferMassRateKgPerSecond(),
                            "stage20c-layout:" + layout.routeCalibrationVersion()));
        }
        if (endpointBySystem.size() != checkedTopology.systems().size()) {
            throw new IllegalArgumentException("one local layout is required for every topology system");
        }

        Stage20PhysicalGalacticRoutePlanner loaded = new Stage20PhysicalGalacticRoutePlanner(
                checkedTopology, checkedTransport.loadedOutboundPlan(), checkedEdges);
        Stage20PhysicalGalacticRoutePlanner returned = new Stage20PhysicalGalacticRoutePlanner(
                checkedTopology, checkedTransport.returnPlan(), checkedEdges);
        return new Stage20PhysicalFreightRouteEvaluator(
                loaded,
                returned,
                allocatedFleet,
                (origin, destination) -> endpointProfile(endpointBySystem, origin, destination));
    }

    private static Optional<EndpointCycleProfile> endpointProfile(
            TreeMap<StarSystemId, EndpointAuthority> endpointBySystem,
            StarSystemId origin,
            StarSystemId destination) {
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
