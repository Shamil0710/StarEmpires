package com.spacesim.persistence;

import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.content.Stage23YardConstructionCatalog;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.economy.Stage23YardConstructionWorkQueue;
import com.spacesim.player.PlayerState;
import java.util.HashMap;

/** Joint admission of physical yard construction, real station ownership and material capacity. */
final class YardConstructionCheckpointValidator {
    private YardConstructionCheckpointValidator() { }

    static void validate(Stage23YardConstructionWorkQueue.State saved,
            Stage20GeneratedWorldRuntimePersistentState stage20, PlayerState player, long tick) {
        if (saved == null || saved.lastProcessedTick() > tick)
            throw new IllegalArgumentException("Yard construction cannot contain future work");
        var catalog = Stage23YardConstructionCatalog.loadDefault();
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var queue = new Stage23YardConstructionWorkQueue(catalog, ontology, saved);
        if (saved.orders().isEmpty()) return;
        var industry = stage20.campaign().industrialState();
        var stores = new HashMap<String, Stage18StationStorage>();
        for (var order : saved.orders()) {
            boolean owner = player != null && player.ownedStations().stream().anyMatch(ref ->
                    stage20.worldState().systems().stream().filter(s -> s.systemId().equals(ref.systemId()))
                    .flatMap(s -> s.simulationState().entities().stream())
                    .anyMatch(e -> e.id().equals(ref.stationEntityId()) && e.market() != null && e.identity() != null
                            && (e.identity().name().equals("Generated market " + order.stationId())
                            || e.identity().name().equals("Generated market v2 " + order.stationId()))));
            if (!owner) throw new IllegalArgumentException("Yard construction requires its actual personal station owner");
            var storage = industry.stationStorages().stream().filter(s -> s.stationId().equals(order.stationId()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Yard construction source station is absent"));
            if (industry.facilities().stream().filter(f -> f.stationId().equals(order.stationId()))
                    .noneMatch(f -> f.state().locationTag().equals(order.locationTag())))
                throw new IllegalArgumentException("Yard construction requires the actual station installation location");
            stores.computeIfAbsent(order.stationId(), ignored -> Stage18StationStorage.restore(ontology,
                    Stage22CivilianMiningProductionPath.loadProducts(), storage));
            boolean complete = order.completedWorkSeconds() == catalog.find(order.yardDefinitionId()).requiredWorkSeconds();
            var installed = industry.yards().stream().filter(y -> y.state().yardInstanceId().equals(order.yardInstanceId())).toList();
            if (complete) {
                if (installed.size() != 1 || !installed.get(0).stationId().equals(order.stationId())
                        || !installed.get(0).state().yardDefinitionId().equals(order.yardDefinitionId()))
                    throw new IllegalArgumentException("Completed construction must retain its exact installed yard");
            } else if (!installed.isEmpty() || saved.lastProcessedTick() != tick)
                throw new IllegalArgumentException("Pending yard construction must match world time and cannot be installed");
        }
        // Restore the facility/material reserve first: both physical queues share this storage capacity.
        var facilityWork = new com.spacesim.economy.Stage18FacilityConstructionWorkQueue(
                new com.spacesim.economy.Stage18FacilityConstructionRuntime(
                        com.spacesim.content.Stage18FacilityConstructionCatalogLoader.loadDefault(),
                        com.spacesim.content.Stage18FacilityCatalogLoader.loadDefault(), ontology),
                ontology, industry.constructionOrders(), industry.simulationTick());
        java.util.function.Function<String, Stage18StationStorage> storeResolver = id -> stores.computeIfAbsent(id,
                ignored -> Stage18StationStorage.restore(ontology, Stage22CivilianMiningProductionPath.loadProducts(),
                        industry.stationStorages().stream().filter(s -> s.stationId().equals(id)).findFirst().orElseThrow()));
        facilityWork.bindReservations(storeResolver);
        var manufacturing = new com.spacesim.economy.Stage18ManufacturingWorkQueue(ontology,
                Stage22CivilianMiningProductionPath.loadManufacturing(), Stage22CivilianMiningProductionPath.loadProducts(),
                industry.processOrders(), industry.simulationTick());
        manufacturing.restoreReservations(storeResolver);
        queue.bindReservations(stores::get);
    }
}
