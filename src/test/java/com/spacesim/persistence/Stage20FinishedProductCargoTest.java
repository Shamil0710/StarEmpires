package com.spacesim.persistence;

import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.economy.Stage18LogisticsRuntime.HandlingCapability;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.persistence.Stage20FreightPersistentState.*;
import com.spacesim.world.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage20FinishedProductCargoTest {
    private static final FleetId FLEET = new FleetId(1);
    private static final String PRODUCT = com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID;
    private final double mass = Stage22CivilianMiningProductionPath.loadProducts().findProduct(PRODUCT).unitMassKg();
    private final String storageClass = Stage22CivilianMiningProductionPath.loadProducts().findProduct(PRODUCT).storageClassId();

    @Test void historicalSchemaSixAdoptsItsOriginalLayoutWithoutAnyFinishedGoods() {
        var before = runtime(mass).capture();
        var current = Stage20FreightPersistenceCodec.encode(before);
        assertEquals(0, java.nio.ByteBuffer.wrap(current).getInt(current.length - 4));
        var legacy = java.util.Arrays.copyOf(current, current.length - 4);
        java.nio.ByteBuffer.wrap(legacy).putInt(8, 6);
        assertEquals(before, Stage20FreightPersistenceCodec.decode(legacy));
        assertTrue(Stage20FreightPersistenceCodec.decode(legacy).productLots().isEmpty());
    }

    @Test void actualProductsKeepTheirSourceThroughPartialUnloadingNativeRestoreAndDestruction() {
        var runtime = runtime(mass * 3);
        var source = storage("station.source", 3);
        var destination = storage("station.destination", 0);
        var handling = new HandlingCapability("physical.handling", Set.of(storageClass), mass * 2, mass);
        var shared = handling.openInterval(1);
        assertTrue(runtime.exchangePersonalProduct(FLEET, source, PRODUCT, 2, true, 1, handling, shared));
        assertEquals(1, source.productCount(PRODUCT));
        assertEquals(2 * mass, runtime.findFreighter(FLEET).orElseThrow().cargoMassKg());
        assertEquals(1, runtime.capture().productLots().size());
        assertFalse(runtime.exchangePersonalProduct(FLEET, source, PRODUCT, 1, true, 1, handling, shared));
        var saved = runtime.capture();
        var restored = Stage20FreightRuntime.restore(Stage20FreightPersistenceCodec.decode(Stage20FreightPersistenceCodec.encode(saved)));
        assertEquals(saved, restored.capture());
        assertTrue(restored.exchangePersonalProduct(FLEET, destination, PRODUCT, 1, false, 2, handling, handling.openInterval(1)));
        var retained = restored.capture().productLots().get(0);
        assertEquals(1, retained.count());
        assertEquals(source.stationId(), retained.sourceEndpointId());
        assertEquals(1d, retained.loadedAtSimulationSeconds());
        assertEquals(1, destination.productCount(PRODUCT));
        assertEquals(mass, restored.findFreighter(FLEET).orElseThrow().cargoMassKg());
        assertEquals(mass, restored.destroy(FLEET).lostCargoMassKg());
        assertTrue(restored.capture().productLots().isEmpty());
        assertTrue(restored.cargoHoldSnapshot(FLEET).productCountById().isEmpty());
        assertEquals(1, destination.productCount(PRODUCT), "Destruction cannot deliver remaining goods");
        assertEquals(saved, runtime.capture(), "Restored owner cannot mutate the original cargo");
    }

    @Test void missingProvenanceUnknownProductCapacityAndHistoricalSchemaRejectWithoutMutation() {
        var runtime = runtime(mass);
        var source = storage("station.source", 2);
        var handling = new HandlingCapability("physical.handling", Set.of(storageClass), mass * 3, mass);
        var before = runtime.capture();
        var budget = handling.openInterval(1);
        assertFalse(runtime.exchangePersonalProduct(FLEET, source, PRODUCT, 2, true, 1, handling, budget));
        assertFalse(runtime.exchangePersonalProduct(FLEET, source, "unknown", 1, true, 1, handling, budget));
        assertFalse(runtime.exchangePersonalProduct(FLEET, source, PRODUCT, 1, false, 1, handling, budget));
        assertEquals(before, runtime.capture());
        assertEquals(mass * 3, budget.remainingMassKg());
        assertEquals(2, source.productCount(PRODUCT));
        assertTrue(runtime.exchangePersonalProduct(FLEET, source, PRODUCT, 1, true, 1, handling, budget));
        var loaded = runtime.capture();
        assertThrows(IllegalArgumentException.class, () -> copy(loaded, 6, loaded.productLots()));
        assertThrows(IllegalArgumentException.class, () -> copy(loaded, 7, List.of()));
        var lot = loaded.productLots().get(0);
        assertThrows(IllegalArgumentException.class, () -> copy(loaded, 7, List.of(lot, lot)));
        assertThrows(IllegalArgumentException.class, () -> new ProductCargoLotState("freight-lot:3", FLEET,
                "unknown", 1, source.stationId(), 1));
        assertEquals(loaded, runtime.capture());
    }

    private Stage20FreightRuntime runtime(double capacity) {
        var hold = new StationStorageSnapshot(Stage20FreightPersistentState.cargoHoldId(FLEET), Map.of(storageClass, capacity), Map.of(), Map.of());
        var fleet = new FreighterState(FLEET, "faction.test", 0, "hull.test", "fit.test", capacity,
                new StarSystemId(1), LocalPhysicalKinematics.stationary(LocalPhysicalPosition.origin()), FreightPhase.IDLE, "", 0, hold);
        return Stage20FreightRuntime.restore(new Stage20FreightPersistentState(7, 1, "generator", "fingerprint", "materializer", "compatibility",
                2, 1, List.of(fleet), List.of(), List.of()));
    }

    private Stage18StationStorage storage(String id, int count) {
        return new Stage18StationStorage(Stage18ResourceOntologyLoader.loadDefault(), Stage22CivilianMiningProductionPath.loadProducts(),
                id, Map.of(storageClass, mass * 3), Map.of(), count == 0 ? Map.of() : Map.of(PRODUCT, count));
    }

    private static Stage20FreightPersistentState copy(Stage20FreightPersistentState state, int version, List<ProductCargoLotState> lots) {
        return new Stage20FreightPersistentState(version, state.rootSeed(), state.generatorVersion(), state.worldFingerprint(), state.materializationVersion(),
                state.compatibilityAuthorityVersion(), state.nextFleetIdValue(), state.nextCargoLotOrdinal(), state.freighters(), state.cargoLots(),
                state.orders(), state.personalMiningOrders(), lots);
    }
}
