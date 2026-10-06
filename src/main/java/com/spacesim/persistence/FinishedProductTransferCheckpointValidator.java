package com.spacesim.persistence;

import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.economy.*;
import com.spacesim.player.PlayerState;
import java.util.HashMap;

/** Joint admission of pending fresh-product handling against its real personal and stock owners. */
final class FinishedProductTransferCheckpointValidator {
    private FinishedProductTransferCheckpointValidator() { }

    static void validate(FinishedProductTransferWorkQueue.State saved, Stage20GeneratedWorldRuntimePersistentState stage20,
            PlayerState player, long tick, ShipyardRepairQueueState repairs, ShipyardRefitQueueState refits,
            ShipyardModuleTransferWorkQueue.State moduleTransfers) {
        if (saved == null || saved.lastProcessedTick() > tick || !saved.orders().isEmpty() && saved.lastProcessedTick() != tick)
            throw new IllegalArgumentException("Product handling watermark must match actual completed time");
        if (saved.orders().isEmpty()) return;
        var products = Stage22CivilianMiningProductionPath.loadProducts();
        var stores = new HashMap<String, Stage18StationStorage>();
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        for (var order : saved.orders()) {
            var fleet = stage20.freight().freighters().stream().filter(f -> f.fleetId().equals(order.fleetId())).findFirst().orElseThrow(
                    () -> new IllegalArgumentException("Product handling fleet is absent"));
            if (player == null || !player.ownedFleetIds().contains(order.fleetId()) || !order.fleetId().equals(player.activeFleetId())
                    || fleet.phase() != Stage20FreightPersistentState.FreightPhase.IDLE
                    || repairs.orders().stream().anyMatch(o -> o.fleetId() == order.fleetId().value())
                    || refits.orders().stream().anyMatch(o -> o.fleetId() == order.fleetId().value())
                    || moduleTransfers.orders().stream().anyMatch(o -> o.source().stationId().equals(fleet.cargoStorage().stationId())
                            || o.destinationStorageId().equals(fleet.cargoStorage().stationId()))
                    || stage20.freight().personalMiningOrders().stream().anyMatch(o -> o.fleetId().equals(order.fleetId())))
                throw new IllegalArgumentException("Product handling requires an idle unreserved actual personal owner");
            boolean owner = player.ownedStations().stream().anyMatch(ref -> stage20.worldState().systems().stream()
                    .filter(s -> s.systemId().equals(ref.systemId())).flatMap(s -> s.simulationState().entities().stream())
                    .anyMatch(e -> e.id().equals(ref.stationEntityId()) && e.market() != null && e.identity() != null
                            && (e.identity().name().equals("Generated market " + order.stationId())
                            || e.identity().name().equals("Generated market v2 " + order.stationId()))));
            if (!owner) throw new IllegalArgumentException("Product handling requires its actual personal station owner");
            var station = stage20.campaign().industrialState().stationStorages().stream()
                    .filter(s -> s.stationId().equals(order.stationId())).findFirst().orElseThrow(
                            () -> new IllegalArgumentException("Product handling station storage is absent"));
            stores.computeIfAbsent(station.stationId(), ignored -> Stage18StationStorage.restore(ontology, products, station));
            stores.computeIfAbsent(fleet.cargoStorage().stationId(), ignored -> Stage18StationStorage.restore(ontology, products, fleet.cargoStorage()));
            var product = products.findProduct(order.productId());
            if (product == null || !station.capacityByStorageClassKg().containsKey(product.storageClassId())
                    || !fleet.cargoStorage().capacityByStorageClassKg().containsKey(product.storageClassId())
                    || order.completedHandlingKg() > 0d && order.startedAtTick() == tick)
                throw new IllegalArgumentException("Product handling lacks physical storage or elapsed work");
        }
        var queue = new FinishedProductTransferWorkQueue(products, saved);
        queue.bindReservations(stores::get);
    }
}
