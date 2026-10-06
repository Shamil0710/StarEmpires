package com.spacesim.economy;

import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.economy.FinishedProductTransferWorkQueue.*;
import com.spacesim.world.FleetId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FinishedProductTransferWorkQueueTest {
    private static final String PRODUCT = com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID;
    private final double mass = Stage22CivilianMiningProductionPath.loadProducts().findProduct(PRODUCT).unitMassKg();
    private final String storageClass = Stage22CivilianMiningProductionPath.loadProducts().findProduct(PRODUCT).storageClassId();
    private final Stage18LogisticsRuntime.HandlingCapability handling =
            new Stage18LogisticsRuntime.HandlingCapability("actual-handling", Set.of(storageClass), mass / 2, mass);

    @Test void twoShipsShareOneEndpointBudgetAndProductsStayReservedUntilActualCompletion() {
        var source = storage("station", 2);
        var first = storage("freight-hold:1", 0); var second = storage("freight-hold:2", 0);
        var stores = Map.of(source.stationId(), source, first.stationId(), first, second.stationId(), second);
        var queue = queue(State.empty());
        queue.start(order("first", 1, 0), context(source, first), stores::get, 0);
        queue.start(order("second", 2, 0), context(source, second), stores::get, 0);
        assertEquals(0, source.productCount(PRODUCT)); assertEquals(2, source.snapshotProductCountById().get(PRODUCT));
        var budget = handling.openInterval(1);
        assertTrue(queue.advance(1, stores::get, o -> context(source, o.fleetId().value() == 1 ? first : second), o -> budget,
                CompletionPermit::publish).isEmpty());
        assertEquals(mass / 2, queue.capture().orders().get(0).completedHandlingKg());
        assertEquals(0d, queue.capture().orders().get(1).completedHandlingKg());
        assertEquals(0d, budget.remainingMassKg());
        assertEquals(0, first.productCount(PRODUCT));
        var duplicate = handling.openInterval(1);
        assertTrue(queue.advance(1, stores::get, o -> context(source, first), o -> duplicate, CompletionPermit::publish).isEmpty());
        assertEquals(mass / 2, duplicate.remainingMassKg());
        assertThrows(IllegalArgumentException.class, () -> queue.advance(3, stores::get, o -> context(source, first),
                o -> duplicate, CompletionPermit::publish));
        var next = handling.openInterval(1);
        assertEquals(1, queue.advance(2, stores::get, o -> context(source, o.fleetId().value() == 1 ? first : second), o -> next,
                CompletionPermit::publish).size());
        assertEquals(1, first.productCount(PRODUCT)); assertEquals(1, source.snapshotProductCountById().get(PRODUCT));
        assertEquals(0, source.productCount(PRODUCT));
        queue.cancel("second", stores::get);
        assertEquals(1, source.productCount(PRODUCT)); assertTrue(queue.capture().orders().isEmpty());
    }

    @Test void pausedUnavailableAndDeclinedPublicationCannotSpendWorkOrLeakACompletionPermit() {
        var source = storage("station", 1); var hold = storage("freight-hold:1", 0);
        var stores = Map.of(source.stationId(), source, hold.stationId(), hold);
        var queue = queue(State.empty()); queue.start(order("first", 1, 0), context(source, hold), stores::get, 0);
        var unavailable = handling.openInterval(1);
        queue.advance(1, stores::get, o -> null, o -> unavailable, CompletionPermit::publish);
        assertEquals(mass / 2, unavailable.remainingMassKg());
        var partial = handling.openInterval(1); queue.advance(2, stores::get, o -> context(source, hold), o -> partial, CompletionPermit::publish);
        var leaked = new AtomicReference<CompletionPermit>(); var finish = handling.openInterval(1);
        assertTrue(queue.advance(3, stores::get, o -> context(source, hold), o -> finish,
                permit -> { leaked.set(permit); return false; }).isEmpty());
        assertFalse(leaked.get().publish(), "A declined callback cannot retain authority for unpaid future work");
        assertEquals(mass / 2, finish.remainingMassKg());
        assertEquals(0, source.productCount(PRODUCT)); assertEquals(1, source.snapshotProductCountById().get(PRODUCT));
        assertEquals(0, hold.productCount(PRODUCT));
        var finalBudget = handling.openInterval(1);
        assertEquals(1, queue.advance(4, stores::get, o -> context(source, hold), o -> finalBudget, permit -> {
            assertTrue(permit.publish()); assertFalse(permit.publish()); return true;
        }).size());
        assertEquals(1, hold.productCount(PRODUCT)); assertTrue(source.snapshotProductCountById().isEmpty());
    }

    @Test void restoredPartialWorkRebindsOriginalSourceAndCompletesWithoutInventoryGrants() {
        var source = storage("station", 1); var hold = storage("freight-hold:1", 0);
        var stores = Map.of(source.stationId(), source, hold.stationId(), hold);
        var queue = queue(State.empty()); queue.start(order("first", 1, 0), context(source, hold), stores::get, 0);
        queue.advance(1, stores::get, o -> context(source, hold), o -> handling.openInterval(1), CompletionPermit::publish);
        var saved = queue.capture();
        var copiedSource = Stage18StationStorage.restore(Stage18ResourceOntologyLoader.loadDefault(), Stage22CivilianMiningProductionPath.loadProducts(), source.snapshot());
        var copiedHold = Stage18StationStorage.restore(Stage18ResourceOntologyLoader.loadDefault(), Stage22CivilianMiningProductionPath.loadProducts(), hold.snapshot());
        var copiedStores = Map.of(copiedSource.stationId(), copiedSource, copiedHold.stationId(), copiedHold);
        var loaded = queue(saved); loaded.bindReservations(copiedStores::get);
        assertEquals(saved, loaded.capture()); assertEquals(0, copiedSource.productCount(PRODUCT));
        assertEquals(1, loaded.advance(2, copiedStores::get, o -> context(copiedSource, copiedHold),
                o -> handling.openInterval(1), CompletionPermit::publish).size());
        assertEquals(1, copiedHold.productCount(PRODUCT)); assertTrue(copiedSource.snapshotProductCountById().isEmpty());
        assertEquals(saved, queue.capture()); assertEquals(1, source.snapshotProductCountById().get(PRODUCT));
        assertThrows(IllegalArgumentException.class, () -> new State(1, List.of(order("first", 1, 2))));
        assertThrows(IllegalArgumentException.class, () -> new State(0, List.of(order("a", 1, 0), order("b", 1, 0))));
        assertThrows(IllegalArgumentException.class, () -> queue(new State(0, List.of(new Order("complete", new FleetId(1),
                source.stationId(), hold.stationId(), PRODUCT, 1, 0, mass)))));
    }

    private FinishedProductTransferWorkQueue queue(State state) { return new FinishedProductTransferWorkQueue(Stage22CivilianMiningProductionPath.loadProducts(), state); }
    private Order order(String id, long fleet, long tick) { return new Order(id, new FleetId(fleet), "station", "freight-hold:" + fleet, PRODUCT, 1, tick, 0); }
    private Context context(Stage18StationStorage source, Stage18StationStorage destination) { return new Context(source, destination, handling, mass * 3); }
    private Stage18StationStorage storage(String id, int count) {
        return new Stage18StationStorage(Stage18ResourceOntologyLoader.loadDefault(), Stage22CivilianMiningProductionPath.loadProducts(),
                id, Map.of(storageClass, mass * 3), Map.of(), count == 0 ? Map.of() : Map.of(PRODUCT, count));
    }
}
