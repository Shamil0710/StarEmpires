package com.spacesim.economy;

import com.spacesim.content.*;
import com.spacesim.content.ship.*;
import com.spacesim.economy.Stage18ShipyardRuntime.*;
import com.spacesim.persistence.ShipyardSmallCraftBuildQueuePersistenceCodec;
import com.spacesim.ship.ShipyardEngineeringService.ShipyardCapability;
import com.spacesim.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShipyardSmallCraftBuildWorkQueueTest {
    static final String DESIGN = Stage228SmallCraftProductionProjection.EMPIRE_INTERCEPTOR_FIT_ID;
    static YardCapabilitySnapshot yard() {
        var d = Stage23SmallCraftProductionCatalogs.shipyards().findYard("yard.empire_capital_service_v1");
        return new YardCapabilitySnapshot("fixture.build-yard", d.id(), YardStatus.ACTIVE,
                new ShipyardCapability("fixture.build-yard", d.berthDimensionsM(), d.maxServiceMassKg(), d.stage175FabricationCapabilities(),
                        d.stage175HandledRequirementIds(), d.toolingTags(), d.precisionCapability(), d.ratedEngineeringWorkRate(),
                        d.laborCapacity(), d.automationCapacity(), d.ratedIntegrationPowerW()), d.handledStorageClassIds(), d.maxHandledUnitMassKg());
    }
    static Stage18StationStorage store(boolean supply) {
        var ontology = Stage18ResourceOntologyLoader.loadDefault(); var capacities = new TreeMap<String, Double>();
        ontology.getStorageClasses().forEach(s -> capacities.put(s.id(), 1e9));
        var s = new Stage18StationStorage(ontology, Stage23SmallCraftProductionCatalogs.products(), "fixture.build-station", capacities, Map.of(), Map.of());
        if (supply) {
            var fit = Stage23SmallCraftProductionCatalogs.fit(DESIGN);
            Stage23SmallCraftProductionCatalogs.shipyards().findHullProfile(fit.hullId()).buildInputsKg().forEach(i -> s.addCommodity(i.commodityId(), i.massKg()));
            fit.installedModules().forEach(m -> s.addProduct(m.moduleId(), 1));
        }
        return s;
    }
    static ShipyardSmallCraftBuildWorkQueue start(Stage18StationStorage store) {
        var q = new ShipyardSmallCraftBuildWorkQueue(ShipyardSmallCraftBuildQueueState.empty());
        q.start("build.one", "player", "faction.alpha", DESIGN, store, yard(), 10); return q;
    }
    @Test void exactBillReservationCancellationAndOtherCustodyRemainAtomic() {
        var s = store(false); var q = new ShipyardSmallCraftBuildWorkQueue(ShipyardSmallCraftBuildQueueState.empty());
        var before = s.snapshot(); var emptyQueue = q; var emptyStore = s;
        assertThrows(IllegalArgumentException.class, () -> emptyQueue.start("missing", "player", "faction.alpha", DESIGN, emptyStore, yard(), 10));
        assertEquals(before, s.snapshot()); assertTrue(q.capture().orders().isEmpty());
        s = store(true); var stock = s.snapshot(); var store = s;
        var occupied = new TreeMap<String, Double>(); s.snapshotCapacityByStorageClassKg().keySet().forEach(id -> occupied.put(id, store.usedCapacityKg(id)));
        q = start(s); var queue = q;
        occupied.forEach((id, kg) -> assertEquals(kg, store.usedCapacityKg(id), 1e-8));
        assertTrue(s.snapshotProductCountById().isEmpty()); assertTrue(s.snapshotCommodityMassByIdKg().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> queue.start("build.two", "player", "faction.alpha", DESIGN, store, yard(), 10));
        s.replaceContentsWithRepairReservation(Map.of(), Map.of(), Map.of("storage.dry_bulk", 100d));
        q.cancel("build.one", s); assertEquals(stock, s.snapshot());
        assertEquals(100d + occupied.get("storage.dry_bulk"), s.usedCapacityKg("storage.dry_bulk"), 1e-8);
        assertThrows(NullPointerException.class, () -> queue.cancel("build.one", store));
    }
    @Test void sharedBudgetSuspensionRepeatedTickAndPartialReloadCannotCreateWork() {
        var s = store(true); var q = start(s); var yard = yard();
        var shared = yard.openInterval(.1); double remaining = shared.remainingWorkSeconds() / 2;
        shared.allocate(remaining); q.advance(11, o -> yard, o -> shared);
        assertEquals(remaining, q.capture().orders().get(0).completedWorkSeconds(), 1e-9);
        var saved = ShipyardSmallCraftBuildQueuePersistenceCodec.decode(ShipyardSmallCraftBuildQueuePersistenceCodec.encode(q.capture()));
        assertEquals(q.capture(), saved); var restored = new ShipyardSmallCraftBuildWorkQueue(saved);
        var copy = Stage18StationStorage.restore(Stage18ResourceOntologyLoader.loadDefault(), Stage23SmallCraftProductionCatalogs.products(), s.snapshot());
        restored.restoreReservations(id -> copy);
        s.snapshotCapacityByStorageClassKg().keySet().forEach(id -> assertEquals(s.usedCapacityKg(id), copy.usedCapacityKg(id)));
        restored.advance(11, o -> yard, o -> yard.openInterval(1)); assertEquals(saved, restored.capture());
        restored.advance(12, o -> null, o -> { fail("Suspended build must not open a budget"); return null; });
        assertEquals(remaining, restored.capture().orders().get(0).completedWorkSeconds(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> restored.advance(14, o -> yard, o -> yard.openInterval(1)));
        assertThrows(IllegalStateException.class, () -> restored.cancel("build.one", copy));
        assertThrows(IllegalStateException.class, () -> restored.completionPermit("build.one", copy));
    }
    @Test void completedEscrowCreatesExactlyOneEmptyCraftAndRejectsDuplicatePublication() {
        var s = store(true); var q = start(s); var yard = yard(); var plan = ShipyardSmallCraftBuildWorkQueue.plan(DESIGN, yard);
        // Explicit finite work fixture isolates publication. This is not an ordinary campaign timing claim.
        var budget = yard.openInterval(plan.requirements().totalWorkSeconds() / yard.plannerCapability().workRate() + 1);
        q.advance(11, o -> yard, o -> budget); assertTrue(q.capture().orders().get(0).ready());
        var paid = q.capture(); var actual = s.snapshot();
        assertThrows(IllegalArgumentException.class, () -> q.completionPermit("build.one", store(false)));
        assertThrows(IllegalArgumentException.class, () -> q.cancel("build.one", store(false)));
        assertThrows(IllegalArgumentException.class, () -> q.restoreReservations(id -> store(false)));
        assertEquals(paid, q.capture()); assertEquals(actual, s.snapshot());
        var engineering = Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        var registry = SmallCraftRegistry.empty(new SmallCraftFitAuthority(engineering));
        var hangars = SmallCraftHangarRegistry.empty(registry);
        var service = new SmallCraftPhysicalLogisticsService(registry, hangars, engineering, Stage22CorePairProtectionCatalogLoader.project(engineering),
                Stage23SmallCraftProductionCatalogs.planner(DESIGN), new Stage18ShipyardRuntime(Stage23SmallCraftProductionCatalogs.shipyards(),
                        Stage18ResourceOntologyLoader.loadDefault(), Stage23SmallCraftProductionCatalogs.products()),
                (pending, bay, tick) -> { throw new AssertionError("No physical delivery was performed"); });
        var permit = q.completionPermit("build.one", s); var other = q.completionPermit("build.one", s);
        var built = service.completeEscrowedBuild(SmallCraftPhysicalLogisticsService.LogisticsState.empty(), permit);
        assertEquals(1, registry.size()); assertEquals(2, registry.nextIdValue()); assertTrue(q.capture().orders().isEmpty());
        var craft = built.producedCraftOptional().orElseThrow(); assertEquals(0, craft.runtimeState().consumables().reactionMassKg());
        assertTrue(hangars.find(craft.id()).isEmpty()); assertEquals(1, built.logisticsState().pendingDeliveries().size());
        var snapshot = s.snapshot();
        assertThrows(IllegalStateException.class, () -> service.completeEscrowedBuild(built.logisticsState(), other));
        assertEquals(snapshot, s.snapshot()); assertEquals(1, registry.size()); assertEquals(2, registry.nextIdValue());
        s.snapshotCapacityByStorageClassKg().keySet().forEach(id -> assertEquals(0d, s.usedCapacityKg(id)));
    }
    @Test void cancelledZeroBudgetOrderCannotReuseAnotherDesignsCachedPlan() {
        var s = store(true);
        String replacement = Stage228SmallCraftProductionProjection.EMPIRE_DEFENCE_FIT_ID;
        Stage23SmallCraftProductionCatalogs.fit(replacement).installedModules().forEach(m -> s.addProduct(m.moduleId(), 1));
        var q = start(s); var yard = yard();
        var exhausted = yard.openInterval(.1);
        exhausted.allocate(exhausted.remainingWorkSeconds());
        q.advance(11, o -> yard, o -> exhausted);
        assertEquals(0, q.capture().orders().get(0).completedWorkSeconds());
        q.cancel("build.one", s);
        q.start("build.one", "player", "faction.alpha", replacement, s, yard, 11);
        var expected = ShipyardSmallCraftBuildWorkQueue.plan(replacement, yard);
        assertNotEquals(ShipyardSmallCraftBuildWorkQueue.plan(DESIGN, yard).requirements().totalWorkSeconds(),
                expected.requirements().totalWorkSeconds());
        q.advance(12, o -> yard, o -> yard.openInterval(.1));
        var order = q.capture().orders().get(0);
        assertEquals(replacement, order.designId());
        assertEquals(expected.requirements().totalWorkSeconds(), order.requiredWorkSeconds());
        assertTrue(order.completedWorkSeconds() > 0);
    }
    @Test void forgedBillsAndTruncatedOrTrailingCodecPayloadsFailClosed() {
        var q = start(store(true)); var state = q.capture(); var o = state.orders().get(0);
        var forged = new ShipyardSmallCraftBuildQueueState.Order(o.orderId(), o.ownerActorId(), o.builderFactionId(), o.stationId(),
                o.yardInstanceId(), o.yardDefinitionId(), o.designId(), o.startedAtTick(), o.requiredWorkSeconds() + 1, 0,
                o.reservedCommodityMassKg(), o.reservedProductCounts());
        assertThrows(IllegalArgumentException.class, () -> new ShipyardSmallCraftBuildWorkQueue(new ShipyardSmallCraftBuildQueueState(10, List.of(forged))));
        byte[] bytes = ShipyardSmallCraftBuildQueuePersistenceCodec.encode(state);
        assertThrows(IllegalArgumentException.class, () -> ShipyardSmallCraftBuildQueuePersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length - 1)));
        assertThrows(IllegalArgumentException.class, () -> ShipyardSmallCraftBuildQueuePersistenceCodec.decode(Arrays.copyOf(bytes, bytes.length + 1)));
    }
}
