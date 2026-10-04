package com.spacesim.persistence;

import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.player.PlayerState;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.StarSystemId;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Pure cross-reference validation of the existing player contract against one generated checkpoint. */
final class GeneratedCampaignPlayerCheckpointValidator {
    private GeneratedCampaignPlayerCheckpointValidator() {
        throw new AssertionError("No instances");
    }

    static void validate(Stage21IGeneratedWorldRuntimePersistentState stage21, PlayerState player) {
        var stage20 = Objects.requireNonNull(stage21, "stage21").stage21HRuntime()
                .stage21GRuntime().stage21FRuntime().stage21ERuntime().stage21DRuntime()
                .stage21CRuntime().stage21BRuntime().stage21ARuntime().stage20Runtime();
        for (var lot : stage20.freight().cargoLots()) {
            if (lot.orderId().equals(Stage20FreightPersistentState.manualCargoOrderId(lot.fleetId()))
                    && (player == null || !player.ownedFleetIds().contains(lot.fleetId())))
                throw new IllegalArgumentException("Manual cargo must belong to a persisted personal fleet");
        }
        for (var fleet : stage20.freight().freighters()) {
            // Destruction removes live personal ownership; the registered wreck row is historical.
            // The world/freight bridge independently requires every DESTROYED fleet to be absent.
            if (!fleet.legalFactionId().equals(fleet.stableFactionId())
                    && (player == null || (fleet.phase() != Stage20FreightPersistentState.FreightPhase.DESTROYED
                    && !player.ownedFleetIds().contains(fleet.fleetId()))
                    || !fleet.legalFactionId().equals(player.factionContentId())))
                throw new IllegalArgumentException("Explicit freight affiliation requires its persisted personal owner and faction");
        }
        if (player == null) return;
        var world = stage20.worldState();
        var content = ContentCatalogLoader.loadDefault();
        var identities = FactionIdentityResolver.createDefault(content, world.factionIdentities());
        if (player.factionContentId() != null && !identities.containsStableId(player.factionContentId())) {
            throw new IllegalArgumentException("Player affiliation references unknown faction");
        }
        for (var reputation : player.reputations()) {
            if (!identities.containsStableId(reputation.factionContentId())) {
                throw new IllegalArgumentException("Player reputation references unknown faction");
            }
        }
        Set<StarSystemId> systemIds = new HashSet<>();
        world.topology().systems().forEach(system -> systemIds.add(system.id()));
        if (!systemIds.containsAll(player.discoveredSystemIds())) {
            throw new IllegalArgumentException("Player discovery references unknown system");
        }
        var fleets = new HashMap<com.spacesim.world.FleetId, com.spacesim.world.FleetPlacementState>();
        world.fleets().forEach(fleet -> fleets.put(fleet.id(), fleet));
        if (!fleets.keySet().containsAll(player.ownedFleetIds())) {
            throw new IllegalArgumentException("Player ownership references absent fleet");
        }
        var projects = new HashSet<com.spacesim.world.ConstructionProjectId>();
        world.constructionProjects().forEach(project -> projects.add(project.id()));
        if (!projects.containsAll(player.ownedConstructionProjectIds())) {
            throw new IllegalArgumentException("Player ownership references absent construction project");
        }
        long tick = world.systems().stream()
                .filter(system -> system.systemId().equals(stage20.activeSystemId()))
                .findFirst().orElseThrow().simulationState().clock().tick();
        for (var intel : player.threatIntel()) {
            if (intel.observedTick() > tick) {
                throw new IllegalArgumentException("Player observation is ahead of campaign time");
            }
        }
        for (var order : player.fleetOrders()) {
            if (order.targetSystemId() != null && !systemIds.contains(order.targetSystemId())
                    || order.secondarySystemId() != null && !systemIds.contains(order.secondarySystemId())
                    || !systemIds.containsAll(order.patrolSystemIds())) {
                throw new IllegalArgumentException("Player order references unknown system");
            }
            if (order.itemContentId() != null && content.findItem(order.itemContentId()) == null) {
                throw new IllegalArgumentException("Player order references unknown item");
            }
            if (order.targetFleetId() != null && !fleets.containsKey(order.targetFleetId())) {
                throw new IllegalArgumentException("Player order references absent target fleet");
            }
        }
        // Discovery is historical knowledge: a destroyed/moved local object need not remain live.
        // Docking is current physical state and therefore cannot use that historical exception.
        if (player.dockedAt() != null) {
            var dock = player.dockedAt();
            var active = fleets.get(player.activeFleetId());
            if (active == null || active.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !active.systemId().equals(dock.systemId())) {
                throw new IllegalArgumentException("Player docking does not match local active fleet");
            }
            var station = world.systems().stream()
                    .filter(system -> system.systemId().equals(dock.systemId()))
                    .flatMap(system -> system.simulationState().entities().stream())
                    .filter(entity -> entity.id().equals(dock.entityId())).findFirst().orElse(null);
            if (station == null || station.market() == null || station.transform() == null) {
                throw new IllegalArgumentException("Player docking references absent market");
            }
        }
    }
}
