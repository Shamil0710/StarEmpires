package com.spacesim.economy;

import com.spacesim.content.*;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.economy.ShipyardModuleCustodyState.StoredModule;
import com.spacesim.economy.ShipyardModuleTransferWorkQueue.*;
import com.spacesim.persistence.ShipyardModuleTransferQueuePersistenceCodec;
import com.spacesim.ship.ShipyardRefitContinuity.RemovedModuleState;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipyardModuleTransferWorkQueueTest {
    private final Stage18ManufacturingProductRegistry products = Stage22CivilianMiningProductionPath.loadProducts();
    private final Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
    private final String moduleId = "module.industrial_union_cargo_section_v1";
    private final String storageClass = products.findProduct(moduleId).storageClassId();
    private final double mass = products.findProduct(moduleId).unitMassKg();
    private final Stage18LogisticsRuntime.HandlingCapability handling =
            new Stage18LogisticsRuntime.HandlingCapability("fixture.actual.handling", Set.of(storageClass), mass / 4, mass);

    private StoredModule row(String id) {
        return new StoredModule(id, "source", 31, 0, new RemovedModuleState(new InstalledModuleDefinition("mission_primary", moduleId), .35, 920));
    }
    private Stage18StationStorage store(String id, double capacity) {
        return new Stage18StationStorage(ontology, products, id, Map.of(storageClass, capacity), Map.of(), Map.of());
    }
    private ShipyardModuleTransferWorkQueue queue() { return new ShipyardModuleTransferWorkQueue(products, State.empty()); }
    private void bind(ShipyardModuleCustodyState custody, Stage18StationStorage source, Stage18StationStorage destination) {
        ShipyardModuleCustodyStorage.bind(custody, products,
                id -> id.equals(source.stationId()) ? source : id.equals(destination.stationId()) ? destination : null);
    }

    @Test void fullFiniteHandlingResumesExactlyAndMovesOneIdentityWithoutPristineProducts() {
        var custody = new ShipyardModuleCustodyState(List.of(row("exact/one")));
        var source = store("source", mass); var destination = store("fixture.hold", mass); bind(custody, source, destination);
        var queue = queue(); var context = new Context(source, destination, handling);
        queue.start("load", custody, "exact/one", context, Set.of(), 0);
        assertEquals(mass, source.usedCapacityKg(storageClass)); assertEquals(0, destination.usedCapacityKg(storageClass));
        var partial = queue.advance(1, custody, ignored -> context, ignored -> handling.openInterval(1), Set.of());
        assertEquals(custody, partial.custody()); assertTrue(partial.completed().isEmpty());
        assertEquals(mass / 4, queue.capture().orders().get(0).completedHandlingKg());
        var checkpoint = queue.capture();
        var restored = new ShipyardModuleTransferWorkQueue(products,
                ShipyardModuleTransferQueuePersistenceCodec.decode(ShipyardModuleTransferQueuePersistenceCodec.encode(checkpoint)));
        var restoredSource = Stage18StationStorage.restore(ontology, products, source.snapshot());
        var restoredDestination = Stage18StationStorage.restore(ontology, products, destination.snapshot());
        bind(custody, restoredSource, restoredDestination); restored.validateCustody(custody);
        var restoredContext = new Context(restoredSource, restoredDestination, handling);
        var replay = handling.openInterval(1);
        queue.advance(1, custody, ignored -> context, ignored -> replay, Set.of());
        assertEquals(mass / 4, replay.remainingMassKg()); assertEquals(checkpoint, queue.capture());
        assertThrows(IllegalArgumentException.class, () -> queue.advance(3, partial.custody(), ignored -> context, ignored -> replay, Set.of()));
        var restoredCustody = custody;
        for (int tick = 2; tick <= 4; tick++) {
            var actual = queue.advance(tick, custody, ignored -> context, ignored -> handling.openInterval(1), Set.of());
            var loaded = restored.advance(tick, restoredCustody, ignored -> restoredContext, ignored -> handling.openInterval(1), Set.of());
            custody = actual.custody(); restoredCustody = loaded.custody();
            assertEquals(custody, restoredCustody); assertEquals(queue.capture(), restored.capture());
            assertEquals(source.usedCapacityKg(storageClass), restoredSource.usedCapacityKg(storageClass));
            assertEquals(destination.usedCapacityKg(storageClass), restoredDestination.usedCapacityKg(storageClass));
            assertEquals(tick == 4 ? 1 : 0, actual.completed().size());
        }
        assertTrue(queue.capture().orders().isEmpty()); var moved = custody.modules().get(0);
        assertEquals("fixture.hold", moved.stationId()); assertEquals(row("exact/one").condition(), moved.condition());
        assertEquals("exact/one", moved.custodyId()); assertEquals(31, moved.sourceAssetId()); assertEquals(0, moved.removedAtTick());
        assertEquals(0, source.usedCapacityKg(storageClass)); assertEquals(mass, destination.usedCapacityKg(storageClass));
        assertEquals(0, source.productCount(moduleId)); assertEquals(0, destination.productCount(moduleId));
        var unload = queue(); var receivingStation = store("other-station", mass);
        unload.start("unload", custody, moved.custodyId(), new Context(destination, receivingStation, handling), Set.of(), 4);
        var delivered = unload.advance(5, custody, ignored -> new Context(destination, receivingStation, handling),
                ignored -> handling.openInterval(4), Set.of());
        assertEquals("other-station", delivered.custody().modules().get(0).stationId());
        assertEquals(moved.condition(), delivered.custody().modules().get(0).condition());
        assertEquals(0, destination.usedCapacityKg(storageClass)); assertEquals(mass, receivingStation.usedCapacityKg(storageClass));
    }

    @Test void missingContactChangedCapacityAndExternalReservationPauseThenCancelWithoutInventoryChanges() {
        var custody = new ShipyardModuleCustodyState(List.of(row("exact")));
        var source = store("source", mass); var destination = store("receiver", mass); bind(custody, source, destination);
        var context = new Context(source, destination, handling); var queue = queue();
        queue.start("load", custody, "exact", context, Set.of(), 0);
        queue.advance(1, custody, ignored -> context, ignored -> handling.openInterval(1), Set.of());
        double paid = queue.capture().orders().get(0).completedHandlingKg();
        var blocked = handling.openInterval(1);
        queue.advance(2, custody, ignored -> null, ignored -> blocked, Set.of());
        queue.advance(3, custody, ignored -> context, ignored -> blocked, Set.of("exact"));
        destination.addProduct(moduleId, 1);
        queue.advance(4, custody, ignored -> context, ignored -> blocked, Set.of());
        assertEquals(paid, queue.capture().orders().get(0).completedHandlingKg()); assertEquals(mass / 4, blocked.remainingMassKg());
        assertEquals(mass, source.usedCapacityKg(storageClass)); assertEquals(row("exact"), custody.modules().get(0));
        queue.cancel("load"); assertTrue(queue.capture().orders().isEmpty());
        assertEquals(mass, source.usedCapacityKg(storageClass)); assertEquals(1, destination.productCount(moduleId));
        assertThrows(IllegalArgumentException.class, () -> queue.cancel("load"));
    }

    @Test void sourceDamageOrIdentityCannotBeSubstitutedAndDuplicateReservationsCannotStart() {
        var custody = new ShipyardModuleCustodyState(List.of(row("exact")));
        var source = store("source", mass); var destination = store("receiver", mass); bind(custody, source, destination);
        var queue = queue(); var context = new Context(source, destination, handling);
        assertThrows(IllegalArgumentException.class, () -> queue.start("reserved", custody, "exact", context, Set.of("exact"), 0));
        queue.start("load", custody, "exact", context, Set.of(), 0); var before = queue.capture();
        assertThrows(IllegalArgumentException.class, () -> queue.start("again", custody, "exact", context, Set.of(), 0));
        var original = row("exact");
        var changed = new ShipyardModuleCustodyState(List.of(new StoredModule(original.custodyId(), original.stationId(),
                original.sourceAssetId(), original.removedAtTick(), new RemovedModuleState(original.condition().assignment(), .9, 0))));
        var budget = handling.openInterval(1);
        assertThrows(IllegalArgumentException.class, () -> queue.advance(1, changed, ignored -> context, ignored -> budget, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> queue.validateCustody(ShipyardModuleCustodyState.empty()));
        assertEquals(before, queue.capture()); assertEquals(mass / 4, budget.remainingMassKg());
        assertThrows(IllegalArgumentException.class, () -> new State(0, List.of(before.orders().get(0),
                new Order("other", original, "receiver", 0, 0))));
    }

    @Test void sharedFiniteBudgetCannotBeCreditedTwiceAndHandlingUnitLimitPauses() {
        var custody = new ShipyardModuleCustodyState(List.of(row("a"), row("b")));
        var source = store("source", mass * 2); var destination = store("receiver", mass * 2); bind(custody, source, destination);
        var queue = queue(); var context = new Context(source, destination, handling);
        queue.start("a", custody, "a", context, Set.of(), 0); queue.start("b", custody, "b", context, Set.of(), 0);
        var shared = handling.openInterval(1);
        queue.advance(1, custody, ignored -> context, ignored -> shared, Set.of());
        assertEquals(mass / 4, queue.capture().orders().stream().mapToDouble(Order::completedHandlingKg).sum());
        assertEquals(0, shared.remainingMassKg());
        var reduced = new Stage18LogisticsRuntime.HandlingCapability("reduced", Set.of(storageClass), mass / 4, mass - 1);
        var next = reduced.openInterval(1);
        queue.advance(2, custody, ignored -> new Context(source, destination, reduced), ignored -> next, Set.of());
        assertEquals(mass / 4, queue.capture().orders().stream().mapToDouble(Order::completedHandlingKg).sum());
        assertEquals(mass / 4, next.remainingMassKg());
        var excessive = new Stage18LogisticsRuntime.HandlingCapability("invented", Set.of(storageClass), mass, mass).openInterval(1);
        assertThrows(IllegalArgumentException.class, () -> queue.advance(3, custody, ignored -> context, ignored -> excessive, Set.of()));
        assertEquals(mass, excessive.remainingMassKg());
    }

    @Test void nativeBoundsAndForgedCompletedWorkCannotRestore() {
        var source = row("exact"); var state = new State(0, List.of(new Order("load", source, "destination", 0, mass / 4)));
        byte[] bytes = ShipyardModuleTransferQueuePersistenceCodec.encode(state);
        assertEquals(state, ShipyardModuleTransferQueuePersistenceCodec.decode(bytes));
        assertThrows(IllegalArgumentException.class, () -> ShipyardModuleTransferQueuePersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> ShipyardModuleTransferQueuePersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length + 1)));
        var count = bytes.clone(); java.nio.ByteBuffer.wrap(count).putInt(12, Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> ShipyardModuleTransferQueuePersistenceCodec.decode(count));
        var schema = bytes.clone(); java.nio.ByteBuffer.wrap(schema).putInt(0, 2);
        assertThrows(IllegalArgumentException.class, () -> ShipyardModuleTransferQueuePersistenceCodec.decode(schema));
        assertThrows(IllegalArgumentException.class, () -> new ShipyardModuleTransferWorkQueue(products,
                new State(0, List.of(new Order("load", source, "destination", 0, mass)))));
        assertThrows(IllegalArgumentException.class, () -> new Order("load", source, "source", 0, 0));
    }

    @Test void directPhysicalTransferRejectsBeforeMutationWithoutEnoughHandlingOrDestinationSpace() {
        var custody = new ShipyardModuleCustodyState(List.of(row("exact")));
        var source = store("source", mass); var receiver = store("receiver", mass); bind(custody, source, receiver);
        var tooShort = handling.openInterval(1);
        assertThrows(IllegalArgumentException.class, () -> ShipyardModuleCustodyStorage.transfer(custody, "exact", products,
                source, receiver, handling, tooShort, Set.of()));
        assertEquals(mass / 4, tooShort.remainingMassKg()); assertEquals(mass, source.usedCapacityKg(storageClass));
        receiver.addProduct(moduleId, 1); var sufficient = handling.openInterval(4);
        assertThrows(IllegalArgumentException.class, () -> ShipyardModuleCustodyStorage.transfer(custody, "exact", products,
                source, receiver, handling, sufficient, Set.of()));
        assertEquals(mass, sufficient.remainingMassKg()); receiver.removeProduct(moduleId, 1);
        var next = ShipyardModuleCustodyStorage.transfer(custody, "exact", products, source, receiver, handling, sufficient, Set.of());
        assertEquals(0, sufficient.remainingMassKg()); assertEquals("receiver", next.modules().get(0).stationId());
        assertEquals(row("exact").condition(), next.modules().get(0).condition()); assertEquals(0, receiver.productCount(moduleId));
    }
}
