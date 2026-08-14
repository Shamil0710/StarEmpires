package com.spacesim.player;

import com.spacesim.world.FleetId;
import com.spacesim.world.FleetJumpState;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Builds the global strategic map strictly from player-known authoritative state.
 *
 * <p>No arbitrary remote NPC entity scan occurs here. The model projects discovered topology,
 * explicit persistent threat intel and player-owned FleetIds only. Stage-16 construction projects
 * and completed stations are projected through {@link PlayerConstructionManagementModel}, keeping
 * all economic/progress calculations in the authoritative read model rather than duplicating them
 * in map/UI code.</p>
 */
public final class GlobalFleetMapModel {
    private GlobalFleetMapModel() {
        throw new AssertionError("GlobalFleetMapModel does not create instances");
    }

    /**
     * Captures the current strategic map.
     *
     * @param runtime playable runtime whose player knowledge defines visibility
     * @return deterministic known-world and owned-asset map snapshot
     */
    public static GlobalFleetMapSnapshot capture(PlayerRuntime runtime) {
        PlayerRuntime checked = Objects.requireNonNull(runtime, "PlayerRuntime not set");
        PlayerState player = checked.player();
        Set<StarSystemId> discovered = new HashSet<>(player.discoveredSystemIds());
        Map<StarSystemId, PlayerThreatIntelState> systemIntel = new HashMap<>();
        List<PlayerThreatIntelState> linkIntel = new ArrayList<>();
        for (PlayerThreatIntelState intel : player.threatIntel()) {
            if (intel.kind() == PlayerThreatIntelKind.SYSTEM) {
                systemIntel.put(intel.systemA(), intel);
            } else {
                linkIntel.add(intel);
            }
        }

        List<GlobalFleetMapSnapshot.SystemMarker> systems = new ArrayList<>();
        for (StarSystemNode node : checked.world().getTopology().systems()) {
            if (!discovered.contains(node.id())) {
                continue;
            }
            PlayerThreatIntelState intel = systemIntel.get(node.id());
            systems.add(new GlobalFleetMapSnapshot.SystemMarker(
                    node.id(),
                    node.name(),
                    node.x(),
                    node.y(),
                    intel == null ? 0d : intel.dangerScore(),
                    intel == null ? 0f : intel.confidence()));
        }

        List<GlobalFleetMapSnapshot.LinkMarker> links = new ArrayList<>();
        List<StarSystemId> orderedSystems = new ArrayList<>(discovered);
        orderedSystems.sort(StarSystemId::compareTo);
        for (StarSystemId first : orderedSystems) {
            List<StarSystemId> neighbors = new ArrayList<>(checked.world().getTopology().neighbors(first));
            neighbors.sort(StarSystemId::compareTo);
            for (StarSystemId second : neighbors) {
                if (!discovered.contains(second) || first.compareTo(second) >= 0) {
                    continue;
                }
                PlayerThreatIntelState intel = null;
                for (PlayerThreatIntelState candidate : linkIntel) {
                    if (candidate.matchesLink(first, second)) {
                        intel = candidate;
                        break;
                    }
                }
                links.add(new GlobalFleetMapSnapshot.LinkMarker(
                        first,
                        second,
                        intel == null ? 0d : intel.dangerScore(),
                        intel == null ? 0f : intel.confidence()));
            }
        }

        Map<FleetId, FleetOrderType> orderByFleet = new HashMap<>();
        for (PlayerFleetOrderState order : player.fleetOrders()) {
            orderByFleet.put(order.fleetId(), order.type());
        }
        List<GlobalFleetMapSnapshot.FleetMarker> fleets = new ArrayList<>();
        for (FleetId fleetId : player.ownedFleetIds()) {
            FleetPlacementState placement = checked.world().findFleet(fleetId).orElse(null);
            if (placement == null) {
                continue;
            }
            StarSystemId systemId = placement.locationKind() == FleetLocationKind.IN_SYSTEM
                    ? placement.systemId() : null;
            FleetJumpState jump = checked.world().findFleetJump(fleetId).orElse(null);
            StarSystemId transitDestination = jump == null ? null : jump.destinationSystemId();
            if (systemId == null && transitDestination == null) {
                continue;
            }
            fleets.add(new GlobalFleetMapSnapshot.FleetMarker(
                    fleetId,
                    systemId,
                    transitDestination,
                    fleetId.equals(player.activeFleetId()),
                    orderByFleet.getOrDefault(fleetId, FleetOrderType.HOLD)));
        }

        PlayerConstructionManagementSnapshot construction =
                new PlayerConstructionManagementModel(checked).capture();
        List<GlobalFleetMapSnapshot.ConstructionProjectMarker> projects = new ArrayList<>();
        for (PlayerConstructionProjectView project : construction.projects()) {
            if (!discovered.contains(project.systemId())) {
                continue;
            }
            projects.add(new GlobalFleetMapSnapshot.ConstructionProjectMarker(
                    project.projectId(),
                    project.systemId(),
                    project.stationArchetypeContentId(),
                    project.stationDisplayName(),
                    project.status(),
                    project.buildProgress(),
                    project.totalMissingUnits(),
                    project.fundingShortfallMilliCredits(),
                    project.territorialAccessCurrentlyAllowed(),
                    project.supplyFleetIds()));
        }
        List<GlobalFleetMapSnapshot.OwnedStationMarker> stations = new ArrayList<>();
        for (PlayerOwnedStationView station : construction.stations()) {
            if (!discovered.contains(station.systemId())) {
                continue;
            }
            stations.add(new GlobalFleetMapSnapshot.OwnedStationMarker(
                    station.reference(),
                    station.stationArchetypeContentId(),
                    station.stationDisplayName(),
                    station.walletMilliCredits(),
                    station.legalFactionContentId()));
        }
        return new GlobalFleetMapSnapshot(systems, links, fleets, projects, stations);
    }
}
