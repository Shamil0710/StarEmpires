package com.spacesim.persistence;

import com.spacesim.content.*;
import com.spacesim.economy.*;
import com.spacesim.player.PlayerState;
import java.util.*;

/** Joint retained build custody, real ownership, installed yard and total physical capacity. */
final class SmallCraftBuildCheckpointValidator {
    private SmallCraftBuildCheckpointValidator() { }
    static void validate(ShipyardSmallCraftBuildQueueState saved, Stage20GeneratedWorldRuntimePersistentState world,
            PlayerState player, long tick, ShipyardModuleCustodyState custody, ShipyardRepairQueueState repairs,
            ShipyardRefitQueueState refits, Stage23YardConstructionWorkQueue.State yards,
            FinishedProductTransferWorkQueue.State products) {
        Objects.requireNonNull(saved);
        if (saved.lastProcessedTick() > tick || !saved.orders().isEmpty() && saved.lastProcessedTick() != tick)
            throw new IllegalArgumentException("Build work must match actual world interval");
        var queue = new ShipyardSmallCraftBuildWorkQueue(saved);
        if (saved.orders().isEmpty()) return;
        var industrial = world.campaign().industrialState();
        for (var o : saved.orders()) {
            var owned = player == null ? java.util.Optional.<EntityState>empty() : player.ownedStations().stream().flatMap(ref -> world.worldState().systems().stream()
                    .filter(s -> s.systemId().equals(ref.systemId())).flatMap(s -> s.simulationState().entities().stream())
                    .filter(e -> e.id().equals(ref.stationEntityId()) && e.market() != null && e.identity() != null
                            && (e.identity().name().equals("Generated market " + o.stationId())
                            || e.identity().name().equals("Generated market v2 " + o.stationId())))).findFirst();
            if (owned.isEmpty() || !o.ownerActorId().equals(com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID)
                    || o.startedAtTick() == tick && o.completedWorkSeconds() > 0)
                throw new IllegalArgumentException("Build requires its actual owner and elapsed work");
            var entity = owned.orElseThrow();
            var identity = world.worldState().factionIdentities().stream().filter(f -> f.stableFactionId().equals(o.builderFactionId())).findFirst();
            var authored = identity.isPresent() ? null : Authored.CONTENT.findFaction(o.builderFactionId());
            Integer runtimeId = identity.map(com.spacesim.world.WorldFactionIdentityState::runtimeFactionId)
                    .orElse(authored == null ? null : authored.runtimeId());
            if (entity.faction() == null || runtimeId == null || entity.faction().factionId() != runtimeId
                    || world.worldState().factions().stream().noneMatch(f -> f.factionContentId().equals(o.builderFactionId())))
                throw new IllegalArgumentException("Build operator differs from actual station legal owner/economy");
            if (industrial.yards().stream().noneMatch(y -> y.stationId().equals(o.stationId())
                    && y.state().yardInstanceId().equals(o.yardInstanceId()) && y.state().yardDefinitionId().equals(o.yardDefinitionId())))
                throw new IllegalArgumentException("Build lacks its exact installed yard");
            double step = world.worldState().systems().stream().filter(s -> s.systemId().equals(world.activeSystemId()))
                    .findFirst().orElseThrow().simulationState().clock().fixedStepSeconds();
            double possible = (tick - o.startedAtTick()) * step
                    * Stage23SmallCraftProductionCatalogs.shipyards().findYard(o.yardDefinitionId()).ratedEngineeringWorkRate();
            if (o.completedWorkSeconds() > possible + Math.max(1e-8, Math.ulp(possible) * Math.max(16d, 2d * (tick - o.startedAtTick() + 1d))))
                throw new IllegalArgumentException("Build work exceeds its actual elapsed rated limit");
        }
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var registry = Stage23SmallCraftProductionCatalogs.products(); var stores = new HashMap<String, Stage18StationStorage>();
        java.util.function.Function<String, Stage18StationStorage> resolver = id -> stores.computeIfAbsent(id, ignored -> {
            var s = industrial.stationStorages().stream().filter(row -> row.stationId().equals(id)).findFirst()
                    .orElseGet(() -> world.freight().freighters().stream().map(row -> row.cargoStorage())
                            .filter(row -> row.stationId().equals(id)).findFirst().orElseThrow());
            return Stage18StationStorage.restore(ontology, registry, s);
        });
        new Stage18FacilityConstructionWorkQueue(new Stage18FacilityConstructionRuntime(
                Stage23StationStorageCatalog.construction(), Stage23StationStorageCatalog.facilities(), ontology),
                ontology, industrial.constructionOrders(), industrial.simulationTick()).bindReservations(resolver);
        new Stage18ManufacturingWorkQueue(ontology, Stage23SmallCraftProductionCatalogs.manufacturing(), registry,
                industrial.processOrders(), industrial.simulationTick()).restoreReservations(resolver);
        ShipyardModuleCustodyStorage.bind(custody, registry, resolver);
        new ShipyardRepairWorkQueue(repairs).restoreReservations(resolver);
        new ShipyardRefitWorkQueue(refits).restoreReservations(resolver);
        new Stage23YardConstructionWorkQueue(Stage23YardConstructionCatalog.loadDefault(), ontology, yards).bindReservations(resolver);
        new FinishedProductTransferWorkQueue(registry, products).bindReservations(resolver);
        queue.restoreReservations(resolver);
    }
    private static final class Authored { private static final ContentCatalog CONTENT = ContentCatalogLoader.loadDefault(); }
}
