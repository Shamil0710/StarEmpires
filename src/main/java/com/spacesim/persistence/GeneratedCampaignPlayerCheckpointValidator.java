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

    private static boolean initialNpcMiningReserve(Stage20GeneratedWorldRuntimePersistentState stage20,
            Stage20FreightPersistentState.FreighterState fleet) {
        if (!stage20.freight().materializationVersion().equals(Stage20FreightRuntimeMaterializer.MINING_RESERVE_VERSION)
                || !fleet.stableFactionId().equals("faction.beta") || !fleet.legalFactionId().equals(fleet.stableFactionId())
                || fleet.phase() != Stage20FreightPersistentState.FreightPhase.IDLE || !fleet.activeOrderId().isEmpty()) return false;
        var slots = stage20.campaign().materializedWorld().worldRows().stream().filter(r -> r.domain().equals("FREIGHT_OWNERSHIP_SLOT")
                && r.values().size() >= 3 && r.values().get(0).equals(fleet.stableFactionId()) && r.values().get(2).equals("RESERVE")).toList();
        if (slots.size() < 2 || slots.stream().mapToInt(r -> Integer.parseInt(r.values().get(1))).max().orElse(-1) != fleet.ownershipOrdinal()) return false;
        var owner = stage20.worldState().factionIdentities().stream().filter(f -> f.stableFactionId().equals(fleet.stableFactionId())).findFirst();
        var placement = stage20.worldState().fleets().stream().filter(f -> f.id().equals(fleet.fleetId())).findFirst();
        if (owner.isEmpty() || placement.isEmpty() || placement.orElseThrow().locationKind() != FleetLocationKind.IN_SYSTEM) return false;
        var entity = stage20.worldState().systems().stream().filter(s -> s.systemId().equals(placement.orElseThrow().systemId()))
                .flatMap(s -> s.simulationState().entities().stream()).filter(e -> e.id().equals(placement.orElseThrow().localEntityId())).findFirst();
        return entity.isPresent() && entity.orElseThrow().faction() != null
                && entity.orElseThrow().faction().factionId() == owner.orElseThrow().runtimeFactionId();
    }

    static void validate(Stage21IGeneratedWorldRuntimePersistentState stage21, PlayerState player) {
        var stage20 = Objects.requireNonNull(stage21, "stage21").stage21HRuntime()
                .stage21GRuntime().stage21FRuntime().stage21ERuntime().stage21DRuntime()
                .stage21CRuntime().stage21BRuntime().stage21ARuntime().stage20Runtime();
        for (var order : stage20.campaign().industrialState().processOrders()) {
            if (!com.spacesim.economy.Stage18ManufacturingWorkQueue.managed(order)) continue;
            boolean owned = player != null && player.ownedStations().stream().anyMatch(ref ->
                    stage20.worldState().systems().stream().filter(system -> system.systemId().equals(ref.systemId()))
                            .flatMap(system -> system.simulationState().entities().stream())
                            .anyMatch(entity -> entity.id().equals(ref.stationEntityId()) && entity.market() != null
                                    && entity.identity() != null && (entity.identity().name().equals("Generated market " + order.stationId())
                                    || entity.identity().name().equals("Generated market v2 " + order.stationId()))));
            if (!owned) throw new IllegalArgumentException("Manufacturing requires the persisted personal station owner");
        }
        for (var order : stage20.campaign().industrialState().constructionOrders()) {
            if (!com.spacesim.economy.Stage18FacilityConstructionWorkQueue.managed(order)) continue;
            if (order.status() == com.spacesim.economy.Stage18FacilityConstructionRuntime.OrderStatus.COMPLETE) {
                boolean installed = stage20.campaign().industrialState().facilities().stream().anyMatch(f ->
                        f.stationId().equals(order.stationId()) && f.state().facilityInstanceId().equals(order.facilityInstanceId())
                                && f.state().definitionId().equals(order.facilityDefinitionId())
                                && f.state().locationTag().equals(order.locationTag()));
                if (!installed) throw new IllegalArgumentException("Completed construction must retain its actual installation");
                continue;
            }
            if (order.status() == com.spacesim.economy.Stage18FacilityConstructionRuntime.OrderStatus.CANCELLED) continue;
            boolean owned = player != null && player.ownedStations().stream().anyMatch(ref ->
                    stage20.worldState().systems().stream().filter(system -> system.systemId().equals(ref.systemId()))
                            .flatMap(system -> system.simulationState().entities().stream())
                            .anyMatch(entity -> entity.id().equals(ref.stationEntityId()) && entity.market() != null
                                    && entity.identity() != null && (entity.identity().name().equals("Generated market " + order.stationId())
                                    || entity.identity().name().equals("Generated market v2 " + order.stationId()))));
            if (!owned) throw new IllegalArgumentException("Construction requires the persisted personal station owner");
        }
        for (var order : stage20.freight().personalMiningOrders()) {
            if (player == null || !player.ownedFleetIds().contains(order.fleetId()))
                throw new IllegalArgumentException("Mining requires a persisted personal owner");
        }
        for (var lot : stage20.freight().cargoLots()) {
            if ((lot.orderId().equals(Stage20FreightPersistentState.manualCargoOrderId(lot.fleetId()))
                    || lot.orderId().equals(Stage20FreightPersistentState.personalExtractionOrderId(lot.fleetId())))
                    && (player == null || !player.ownedFleetIds().contains(lot.fleetId())))
                throw new IllegalArgumentException("Manual cargo must belong to a persisted personal fleet");
        }
        for (var fleet : stage20.freight().freighters()) {
            if (stage20.freight().productLots().stream().anyMatch(l -> l.fleetId().equals(fleet.fleetId()))
                    && (player == null || !player.ownedFleetIds().contains(fleet.fleetId())))
                throw new IllegalArgumentException("Finished-product cargo requires its persisted personal owner");
            if (fleet.fitId().equals(com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT)) {
                if (fleet.phase() != Stage20FreightPersistentState.FreightPhase.DESTROYED
                        && (player == null || !player.ownedFleetIds().contains(fleet.fleetId()))
                        && !initialNpcMiningReserve(stage20, fleet))
                    throw new IllegalArgumentException("Mining freight fitting requires its persisted personal owner");
                if (fleet.phase() != Stage20FreightPersistentState.FreightPhase.DESTROYED) {
                    var placement = stage20.worldState().fleets().stream().filter(f -> f.id().equals(fleet.fleetId())).findFirst().orElseThrow();
                    var entity = placement.locationKind() == FleetLocationKind.IN_TRANSIT ? placement.transitState().entityState()
                            : stage20.worldState().systems().stream().filter(s -> s.systemId().equals(placement.systemId()))
                            .flatMap(s -> s.simulationState().entities().stream()).filter(e -> e.id().equals(placement.localEntityId())).findFirst().orElseThrow();
                    if (entity.engineering() == null) throw new IllegalArgumentException("Mining freight requires installed engineering");
                    var ship = EngineeringStatePersistenceMapper.restore(entity.engineering());
                    var catalog = com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
                    var expected = com.spacesim.ship.ShipEngineeringState.InstalledFit.fromDemonstrator(catalog.findDemonstratorFit(fleet.fitId()));
                    if (!expected.equals(ship.fit)
                            || !new com.spacesim.ship.ShipEngineeringRuntime(catalog).derive(ship.fit, ship.runtimeState,
                            ship.instanceState.damage().moduleDamage()).validation().isValid())
                        throw new IllegalArgumentException("Mining freight metadata differs from its actual physical fitting");
                }
            }
            // Destruction removes live personal ownership; the registered wreck row is historical.
            // The world/freight bridge independently requires every DESTROYED fleet to be absent.
            if (!fleet.legalFactionId().equals(fleet.stableFactionId())
                    && (player == null || (fleet.phase() != Stage20FreightPersistentState.FreightPhase.DESTROYED
                    && !player.ownedFleetIds().contains(fleet.fleetId()))
                    || !fleet.legalFactionId().equals(player.factionContentId())))
                throw new IllegalArgumentException("Explicit freight affiliation requires its persisted personal owner and faction");
        }
        var clock = stage20.worldState().systems().stream()
                .filter(system -> system.systemId().equals(stage20.activeSystemId()))
                .findFirst().orElseThrow().simulationState().clock();
        long tick = clock.tick();
        double nowSeconds = tick * (double) clock.fixedStepSeconds();
        for (var lot : stage20.freight().productLots())
            if (lot.loadedAtSimulationSeconds() > nowSeconds + Math.max(1e-9, nowSeconds * 1e-12))
                throw new IllegalArgumentException("Finished-product loading is ahead of campaign time");
        if ((stage20.campaign().industrialState().processOrders().stream()
                .anyMatch(com.spacesim.economy.Stage18ManufacturingWorkQueue::managed)
                || stage20.campaign().industrialState().constructionOrders().stream()
                .anyMatch(com.spacesim.economy.Stage18FacilityConstructionWorkQueue::managed))
                && stage20.campaign().industrialState().simulationTick() > tick)
            throw new IllegalArgumentException("Industrial work is ahead of campaign time");
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
        for (var order : stage20.freight().personalMiningOrders()) {
            if (order.lastProcessedTick() > tick)
                throw new IllegalArgumentException("Mining interval is ahead of campaign time");
        }
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
