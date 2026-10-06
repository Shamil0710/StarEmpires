package com.spacesim.persistence;

import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.economy.ShipyardModuleCustodyState;
import com.spacesim.economy.ShipyardModuleCustodyStorage;
import com.spacesim.economy.Stage18ManufacturingWorkQueue;
import com.spacesim.economy.Stage18StationStorage;
import java.util.TreeMap;

/** Composed validation of physical custody and manufacturing against the same finite storage. */
final class ShipyardModuleCustodyCheckpointValidator {
    private ShipyardModuleCustodyCheckpointValidator() { }

    static void validate(ShipyardModuleCustodyState custody, Stage20GeneratedWorldRuntimePersistentState stage20,
            com.spacesim.player.PlayerState player, long tick,
            com.spacesim.economy.ShipyardRepairQueueState repairs, com.spacesim.economy.ShipyardRefitQueueState refits,
            com.spacesim.economy.ShipyardModuleTransferWorkQueue.State transfers) {
        var industry = stage20.campaign().industrialState();
        for (var module : custody.modules()) if (module.ownerActorId() != null) {
            boolean personal = module.ownerActorId().equals(com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
            if (personal ? player == null : stage20.worldState().factions().stream().noneMatch(f -> f.factionContentId().equals(module.ownerActorId())))
                throw new IllegalArgumentException("Equipment owner must be an actual persisted actor");
        }
        if (transfers.lastProcessedTick() > tick || !transfers.orders().isEmpty() && transfers.lastProcessedTick() != tick)
            throw new IllegalArgumentException("Module handling watermark differs from completed time");
        if (custody.modules().isEmpty() && repairs.orders().isEmpty() && refits.orders().isEmpty() && transfers.orders().isEmpty()
                && stage20.freight().freighters().stream().allMatch(f -> f.carriedEquipmentMassKg() == 0)) return;
        if (custody.modules().stream().anyMatch(module -> module.removedAtTick() > tick))
            throw new IllegalArgumentException("Module removal cannot occur in the future");
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var products = Stage22CivilianMiningProductionPath.loadProducts();
        var stations = new TreeMap<String, Stage18StationStorage>();
        var affected = custody.modules().stream().map(ShipyardModuleCustodyState.StoredModule::stationId)
                .collect(java.util.stream.Collectors.toSet());
        repairs.orders().forEach(order -> affected.add(order.stationId()));
        refits.orders().forEach(order -> affected.add(order.stationId()));
        transfers.orders().forEach(order -> { affected.add(order.source().stationId()); affected.add(order.destinationStorageId()); });
        industry.stationStorages().stream().filter(s -> affected.contains(s.stationId())).forEach(s ->
                stations.put(s.stationId(), Stage18StationStorage.restore(ontology, products, s)));
        var mass = ShipyardModuleCustodyStorage.massByStation(custody, products);
        var ships = new TreeMap<String, Stage20FreightPersistentState.FreighterState>();
        for (var ship : stage20.freight().freighters()) {
            String id = ship.cargoStorage().stationId(); ships.put(id, ship);
            double equipmentMass = mass.getOrDefault(id, java.util.Map.of()).values().stream().mapToDouble(Double::doubleValue).sum();
            if (Double.compare(equipmentMass, ship.carriedEquipmentMassKg()) != 0)
                throw new IllegalArgumentException("Aboard equipment differs from physical freight mass");
            if (equipmentMass > 0 && (player == null || !player.ownedFleetIds().contains(ship.fleetId()) || !ship.operational()))
                throw new IllegalArgumentException("Aboard equipment requires its actual personal ship owner");
            if (equipmentMass > 0) {
                var placement = stage20.worldState().fleets().stream().filter(f -> f.id().equals(ship.fleetId())).findFirst().orElseThrow();
                var entity = placement.locationKind() == com.spacesim.world.FleetLocationKind.IN_TRANSIT ? placement.transitState().entityState()
                        : stage20.worldState().systems().stream().filter(s -> s.systemId().equals(placement.systemId()))
                        .flatMap(s -> s.simulationState().entities().stream()).filter(e -> e.id().equals(placement.localEntityId())).findFirst().orElseThrow();
                if (entity.engineering() == null || Double.compare(EngineeringStatePersistenceMapper.restore(entity.engineering())
                        .runtimeState.consumables().cargoMassKg(), ship.cargoMassKg()) != 0)
                    throw new IllegalArgumentException("Aboard equipment mass differs from actual ship engineering cargo");
            }
            if (affected.contains(id)) stations.put(id, Stage18StationStorage.restore(ontology, products, ship.cargoStorage()));
        }
        var handling = new com.spacesim.economy.ShipyardModuleTransferWorkQueue(products, transfers);
        handling.validateCustody(custody);
        var usedIds = new java.util.HashSet<String>();
        refits.orders().forEach(order -> order.reservedUsedModulesByTargetMount().values().forEach(row -> usedIds.add(row.custodyId())));
        var handlingShips = new java.util.HashSet<com.spacesim.world.FleetId>();
        for (var order : transfers.orders()) {
            var sourceShip = ships.get(order.source().stationId()); var targetShip = ships.get(order.destinationStorageId());
            if ((sourceShip == null) == (targetShip == null) || usedIds.contains(order.source().custodyId()))
                throw new IllegalArgumentException("Handling requires one ship and one unreserved station module");
            var ship = sourceShip == null ? targetShip : sourceShip;
            String stationId = sourceShip == null ? order.source().stationId() : order.destinationStorageId();
            if (player == null || !player.ownedFleetIds().contains(ship.fleetId()) || !ship.fleetId().equals(player.activeFleetId())
                    || !handlingShips.add(ship.fleetId()) || ship.phase() != Stage20FreightPersistentState.FreightPhase.IDLE
                    || repairs.orders().stream().anyMatch(o -> o.fleetId() == ship.fleetId().value())
                    || refits.orders().stream().anyMatch(o -> o.fleetId() == ship.fleetId().value()))
                throw new IllegalArgumentException("Handling requires an available active personal ship");
            boolean owner = player.ownedStations().stream().anyMatch(ref -> stage20.worldState().systems().stream()
                    .filter(s -> s.systemId().equals(ref.systemId())).flatMap(s -> s.simulationState().entities().stream())
                    .anyMatch(e -> e.id().equals(ref.stationEntityId()) && e.market() != null && e.identity() != null
                            && (e.identity().name().equals("Generated market " + stationId)
                            || e.identity().name().equals("Generated market v2 " + stationId))));
            boolean privateEquipment = com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID.equals(order.source().ownerActorId());
            if (order.source().ownerActorId() != null && !privateEquipment)
                throw new IllegalArgumentException("Handling cannot transfer another actor's equipment");
            if ((!owner && !privateEquipment) || !stations.containsKey(stationId))
                throw new IllegalArgumentException("Handling requires station ownership or actual private equipment ownership");
            if (!owner) {
                var placement = stage20.worldState().fleets().stream().filter(f -> f.id().equals(ship.fleetId())).findFirst().orElseThrow();
                String operator = stage20.campaign().materializedWorld().worldRows().stream()
                        .filter(row -> row.domain().equals("INDUSTRIAL_SPECIALIZATION") && row.values().size() >= 4
                                && row.values().get(1).equals(stationId)
                                && row.values().get(0).equals(Long.toString(placement.systemId().value())))
                        .findFirst().orElseThrow().values().get(2);
                ShipyardRepairCheckpointValidator.validateCivilianBerth(stationId, operator, stage20, player, placement);
            }
        }
        var orders = industry.processOrders().stream().filter(Stage18ManufacturingWorkQueue::managed)
                .filter(order -> affected.contains(order.stationId())).toList();
        var queue = new Stage18ManufacturingWorkQueue(ontology, Stage22CivilianMiningProductionPath.loadManufacturing(),
                products, orders, industry.simulationTick());
        queue.restoreReservations(stations::get);
        ShipyardModuleCustodyStorage.bind(custody, products, stations::get);
        new com.spacesim.economy.ShipyardRepairWorkQueue(repairs).restoreReservations(stations::get);
        var refitQueue = new com.spacesim.economy.ShipyardRefitWorkQueue(refits);
        refitQueue.validateUsedReservations(custody);
        refitQueue.restoreReservations(stations::get);
    }
}
