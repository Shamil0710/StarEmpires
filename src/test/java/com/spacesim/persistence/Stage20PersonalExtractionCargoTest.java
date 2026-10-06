package com.spacesim.persistence;

import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.economy.Stage18ExtractionRuntime.*;
import com.spacesim.economy.Stage18LogisticsRuntime.HandlingCapability;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.persistence.Stage20FreightPersistentState.*;
import com.spacesim.world.FleetId;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class Stage20PersonalExtractionCargoTest {
    private static final FleetId FLEET = new FleetId(1);
    private static final String ORE = "commodity.feedstock.metallic_ore";
    private static final String METHOD = "extraction.asteroid_excavation";

    @Test void deliveryEvidencePreservesExactConsumedOriginsAndPartialFifoLots() {
        var r = runtime(100); var source = source(100);
        var capability = new ExtractionCapability("fixture.installed-equipment", Set.of("capability.extraction.asteroid_excavation"),
                4_000_000, 2.5, .125);
        double mined = r.extractPersonalCommodity(FLEET, source, METHOD, 10, capability, capability.openInterval(1), 1).outputMassStoredKg();
        var ontology = Stage18ResourceOntologyLoader.loadDefault(); var products = Stage18ManufacturingProductRegistry.loadDefault();
        var destination = new Stage18StationStorage(ontology, products, "station.destination", Map.of("storage.dry_bulk", 100d), Map.of(ORE, 10d), Map.of());
        var remote = new Stage18StationStorage(ontology, products, "station.remote", Map.of("storage.dry_bulk", 100d), Map.of(ORE, 10d), Map.of());
        var handling = new HandlingCapability("fixture.handling", Set.of("storage.dry_bulk"), 100, 100);
        assertTrue(r.exchangeManualCommodity(FLEET, destination, ORE, 2, true, 2, handling, handling.openInterval(1)));
        assertTrue(r.exchangeManualCommodity(FLEET, remote, ORE, 3, true, 3, handling, handling.openInterval(1)));
        var original = r.capture().cargoLots();
        var receipt = r.deliverPersonalCommodity(FLEET, destination, ORE, mined + 3, 4, handling, handling.openInterval(1)).orElseThrow();
        var portions = receipt.deliveredLots();
        assertEquals(3, portions.size());
        assertEquals("player-extraction:" + source.sourceId(), portions.get(0).sourceProvenanceId());
        assertEquals(mined, portions.get(0).massKg(), 1e-9);
        assertEquals("station.destination", portions.get(1).sourceEndpointId()); assertEquals(2, portions.get(1).massKg());
        assertEquals("station.remote", portions.get(2).sourceEndpointId()); assertEquals(1, portions.get(2).massKg(), 1e-9);
        assertEquals(receipt.massKg(), portions.stream().mapToDouble(CargoLotState::massKg).sum(), 1e-9);
        for (var portion : portions) {
            var prior = original.stream().filter(l -> l.lotId().equals(portion.lotId())).findFirst().orElseThrow();
            assertEquals(prior.sourceProvenanceId(), portion.sourceProvenanceId());
            assertEquals(prior.loadedAtSimulationSeconds(), portion.loadedAtSimulationSeconds());
        }
        assertThrows(UnsupportedOperationException.class, () -> portions.clear());
        var retained = r.capture().cargoLots().get(0);
        assertEquals(portions.get(2).lotId(), retained.lotId()); assertEquals(2, retained.massKg(), 1e-9);
        assertEquals("station.remote", retained.sourceEndpointId());
        assertEquals(r.capture(), Stage20FreightRuntime.restore(Stage20FreightPersistenceCodec.decode(Stage20FreightPersistenceCodec.encode(r.capture()))).capture());
    }

    @Test void deliveryReceiptRequiresActualCargoAndHandlingAndCanBeClaimedOnlyOnceByItsRuntime() {
        var r = runtime(100);
        var station = new Stage18StationStorage(Stage18ResourceOntologyLoader.loadDefault(), Stage18ManufacturingProductRegistry.loadDefault(),
                "station.test", Map.of("storage.dry_bulk", 100d), Map.of(ORE, 10d), Map.of());
        var handling = new HandlingCapability("fixture.handling", Set.of("storage.dry_bulk"), 100, 100);
        assertTrue(r.deliverPersonalCommodity(FLEET, station, ORE, 1, 1, handling, handling.openInterval(1)).isEmpty());
        assertTrue(r.exchangeManualCommodity(FLEET, station, ORE, 5, true, 1, handling, handling.openInterval(1)));
        var before = r.capture();
        var budget = handling.openInterval(.01);
        assertTrue(r.deliverPersonalCommodity(FLEET, station, ORE, 5, 2, handling, budget).isEmpty());
        assertEquals(before, r.capture()); assertEquals(5, station.commodityMassKg(ORE));
        var receipt = r.deliverPersonalCommodity(FLEET, station, ORE, 5, 2, handling, handling.openInterval(1)).orElseThrow();
        assertEquals(FLEET, receipt.fleetId()); assertEquals("station.test", receipt.stationId());
        assertEquals(ORE, receipt.commodityId()); assertEquals(5, receipt.massKg()); assertEquals(2, receipt.simulationSeconds());
        assertEquals(10, station.commodityMassKg(ORE)); assertTrue(r.capture().cargoLots().isEmpty());
        assertFalse(Stage20FreightRuntime.restore(r.capture()).claimPersonalDelivery(receipt));
        assertTrue(r.claimPersonalDelivery(receipt)); assertFalse(r.claimPersonalDelivery(receipt));
        assertTrue(r.deliverPersonalCommodity(FLEET, station, ORE, 1, 3, handling, handling.openInterval(1)).isEmpty());
    }

    @Test void finiteSourceMassAndSharedWorkBecomeActualHoldAndDistinctPersistentLots() {
        var r = runtime(100); var s = source(25);
        var capability = new ExtractionCapability("fixture.installed-equipment", Set.of("capability.extraction.asteroid_excavation"),
                4_000_000, 2.5, .125);
        var budget = capability.openInterval(1);
        var result = r.extractPersonalCommodity(FLEET, s, METHOD, 100, capability, budget, 1);
        assertEquals(Status.EXTRACTED_DEPLETED, result.status());
        assertEquals(25, result.sourceMassRemovedKg()); assertEquals(9.2, result.outputMassStoredKg(), 1e-9);
        assertEquals(0, s.remainingAccessibleMassKg());
        assertEquals(25, result.outputMassStoredKg() + result.discardedMassKg(), 1e-9);
        assertEquals(9.2, r.cargoHoldSnapshot(FLEET).commodityMassByIdKg().get(ORE), 1e-9);
        var saved = r.capture(); var lot = saved.cargoLots().get(0);
        assertEquals(Stage20FreightPersistentState.personalExtractionOrderId(FLEET), lot.orderId());
        assertEquals("player-extraction:" + s.sourceId(), lot.sourceProvenanceId());
        assertEquals(saved, Stage20FreightRuntime.restore(Stage20FreightPersistenceCodec.decode(Stage20FreightPersistenceCodec.encode(saved))).capture());
        double energy = budget.remainingEnergyJ();
        assertFalse(r.extractPersonalCommodity(FLEET, s, METHOD, 1, capability, budget, 1).committed());
        assertEquals(saved, r.capture()); assertEquals(energy, budget.remainingEnergyJ());
    }

    @Test void repeatedBudgetInsufficientCapabilityAndFullHoldRejectWithoutSourceOrCargoMutation() {
        var r = runtime(100); var s = source(100);
        var capability = new ExtractionCapability("fixture.installed-equipment", Set.of("capability.extraction.asteroid_excavation"),
                1_200_000, .8, .04);
        var budget = capability.openInterval(1);
        assertTrue(r.extractPersonalCommodity(FLEET, s, METHOD, 10, capability, budget, 1).committed());
        var before = r.capture(); double remaining = s.remainingAccessibleMassKg();
        assertEquals(Status.INSUFFICIENT_POWER, r.extractPersonalCommodity(FLEET, s, METHOD, 10, capability, budget, 1).status());
        assertEquals(before, r.capture()); assertEquals(remaining, s.remainingAccessibleMassKg());
        var none = new ExtractionCapability("fixture.freighter-without-miner", Set.of(), 4_000_000, 2.5, .125);
        assertEquals(Status.MISSING_CAPABILITY, r.extractPersonalCommodity(FLEET, s, METHOD, 10, none, none.openInterval(1), 1).status());
        assertEquals(before, r.capture()); assertEquals(remaining, s.remainingAccessibleMassKg());
        var full = runtime(1); var emptyBudget = capability.openInterval(1); before = full.capture();
        assertEquals(Status.STORAGE_FULL, full.extractPersonalCommodity(FLEET, s, METHOD, 10, capability, emptyBudget, 1).status());
        assertEquals(before, full.capture()); assertEquals(remaining, s.remainingAccessibleMassKg());
        assertEquals(1_200_000, emptyBudget.remainingEnergyJ());
    }

    @Test void saleConsumesPurchasedAndExtractedLotsWithoutChangingTheirOrigin() {
        var r = runtime(100); var s = source(100);
        var capability = new ExtractionCapability("fixture.installed-equipment", Set.of("capability.extraction.asteroid_excavation"),
                4_000_000, 2.5, .125);
        assertTrue(r.extractPersonalCommodity(FLEET, s, METHOD, 10, capability, capability.openInterval(1), 1).committed());
        var station = new Stage18StationStorage(Stage18ResourceOntologyLoader.loadDefault(), Stage18ManufacturingProductRegistry.loadDefault(),
                "station.test", Map.of("storage.dry_bulk", 100d), Map.of(ORE, 1d), Map.of());
        var handling = new HandlingCapability("fixture.handling", Set.of("storage.dry_bulk"), 100, 100);
        assertTrue(r.exchangeManualCommodity(FLEET, station, ORE, 1, true, 2, handling, handling.openInterval(1)));
        assertEquals(2, r.capture().cargoLots().size());
        assertTrue(r.exchangeManualCommodity(FLEET, station, ORE, 4.68, false, 3, handling, handling.openInterval(1)));
        assertTrue(r.capture().cargoLots().isEmpty());
        assertEquals(0d, r.cargoHoldSnapshot(FLEET).commodityMassByIdKg().getOrDefault(ORE, 0d), 1e-9);
        assertEquals(4.68, station.commodityMassKg(ORE), 1e-9);
        assertEquals(90, s.remainingAccessibleMassKg());
    }

    @Test void multipleSourcesCannotSpendTheSameMethodsThroughputTwiceInOneInterval() {
        var r = runtime(100); var first = source(100); var second = source("source.other", 100);
        var capability = new ExtractionCapability("fixture.high-power-miner", Set.of("capability.extraction.asteroid_excavation"),
                100_000_000, 100, 100);
        var budget = capability.openInterval(1);
        assertTrue(r.extractPersonalCommodity(FLEET, first, METHOD, 15, capability, budget, 1).committed());
        var before = r.capture(); double energy = budget.remainingEnergyJ();
        assertEquals(Status.THROUGHPUT_LIMIT, r.extractPersonalCommodity(FLEET, second, METHOD, 15, capability, budget, 1).status());
        assertEquals(100, second.remainingAccessibleMassKg()); assertEquals(before, r.capture());
        assertEquals(energy, budget.remainingEnergyJ());
        assertTrue(r.extractPersonalCommodity(FLEET, second, METHOD, 10, capability, budget, 1).committed());
        assertEquals(90, second.remainingAccessibleMassKg());
    }

    @Test void installedThroughputBoundsTheSharedIntervalBelowTheMethodsMaximum() {
        var r = runtime(100); var first = source(100); var second = source("source.other", 100);
        var capability = new ExtractionCapability("fixture.damaged-miner", Set.of("capability.extraction.asteroid_excavation"),
                100_000_000, 100, 100);
        var budget = capability.openInterval(1, 12.5);
        assertTrue(r.extractPersonalCommodity(FLEET, first, METHOD, 10, capability, budget, 1).committed());
        var before = r.capture(); double energy = budget.remainingEnergyJ();
        assertEquals(Status.THROUGHPUT_LIMIT, r.extractPersonalCommodity(FLEET, second, METHOD, 3, capability, budget, 1).status());
        assertEquals(before, r.capture()); assertEquals(100, second.remainingAccessibleMassKg());
        assertEquals(energy, budget.remainingEnergyJ());
        assertTrue(r.extractPersonalCommodity(FLEET, second, METHOD, 2.5, capability, budget, 1).committed());
        assertEquals(Status.THROUGHPUT_LIMIT, r.extractPersonalCommodity(FLEET, second, METHOD, .1, capability, budget, 1).status());
        assertThrows(IllegalArgumentException.class, () -> capability.openInterval(1, Double.NaN));
    }

    @Test void oldSchemaAdoptsWithoutCargoButCannotContainNewExtractionProvenance() {
        var before = runtime(100).capture(); var current = Stage20FreightPersistenceCodec.encode(before);
        // Genuine v3/v4 layouts omit v6 equipment mass and the later empty appended lists.
        var bytes = historicalCommodityPayload(current, 3);
        assertEquals(before, Stage20FreightPersistenceCodec.decode(bytes));
        var r = runtime(100); var capability = new ExtractionCapability("fixture.installed-equipment", Set.of("capability.extraction.asteroid_excavation"),
                4_000_000, 2.5, .125);
        r.extractPersonalCommodity(FLEET, source(100), METHOD, 10, capability, capability.openInterval(1), 1);
        var extracted = r.capture(); var encoded = Stage20FreightPersistenceCodec.encode(extracted);
        var v4 = historicalCommodityPayload(encoded, 4);
        assertEquals(extracted, Stage20FreightPersistenceCodec.decode(v4));
        ByteBuffer.wrap(encoded).putInt(8, 3);
        assertThrows(IllegalArgumentException.class, () -> Stage20FreightPersistenceCodec.decode(encoded));
    }

    @Test void persistentMiningTickCannotBeSpentAgainAndSurvivesCargoIndependentSave() {
        var r = runtime(100);
        var order = new PersonalMiningOrder(FLEET, "source.test", METHOD, 1, 5);
        r.startPersonalMining(order);
        assertFalse(r.claimPersonalMiningTick(FLEET, 5));
        assertTrue(r.claimPersonalMiningTick(FLEET, 6));
        var saved = r.capture(); var restored = Stage20FreightRuntime.restore(Stage20FreightPersistenceCodec.decode(
                Stage20FreightPersistenceCodec.encode(saved)));
        assertEquals(saved, restored.capture());
        assertFalse(restored.claimPersonalMiningTick(FLEET, 6));
        assertThrows(IllegalStateException.class, () -> restored.startPersonalMining(order));
        assertThrows(IllegalArgumentException.class, () -> new Stage20FreightPersistentState(4, saved.rootSeed(),
                saved.generatorVersion(), saved.worldFingerprint(), saved.materializationVersion(), saved.compatibilityAuthorityVersion(),
                saved.nextFleetIdValue(), saved.nextCargoLotOrdinal(), saved.freighters(), saved.cargoLots(), saved.orders(), saved.personalMiningOrders()));
        restored.stopPersonalMining(FLEET); assertTrue(restored.personalMiningOrders().isEmpty());
        restored.startPersonalMining(new PersonalMiningOrder(FLEET, "source.test", METHOD, 1, 6));
        assertFalse(restored.claimPersonalMiningTick(FLEET, 6));
        assertTrue(restored.claimPersonalMiningTick(FLEET, 7));
        restored.destroy(FLEET); assertTrue(restored.personalMiningOrders().isEmpty());
    }

    private static Stage20FreightRuntime runtime(double capacity) {
        var hold = new StationStorageSnapshot(Stage20FreightPersistentState.cargoHoldId(FLEET), Map.of("storage.dry_bulk", capacity), Map.of(), Map.of());
        var fleet = new FreighterState(FLEET, "faction.test", 0, "hull.test", "fit.test", capacity,
                new StarSystemId(1), LocalPhysicalKinematics.stationary(LocalPhysicalPosition.origin()), FreightPhase.IDLE, "", 0, hold);
        return Stage20FreightRuntime.restore(new Stage20FreightPersistentState(4, 1, "test.generator", "test.fingerprint", "test.materializer", "test.compatibility",
                2, 1, List.of(fleet), List.of(), List.of()));
    }

    private static byte[] historicalCommodityPayload(byte[] current, int version) {
        try (var input = new java.io.DataInputStream(new java.io.ByteArrayInputStream(current))) {
            input.skipNBytes(20); // magic, file version, schema, seed
            for (int i = 0; i < 4; i++) skipText(input);
            input.skipNBytes(16);
            assertEquals(1, input.readInt(), "This historical fixture has one freighter");
            input.skipNBytes(8); skipText(input); input.skipNBytes(4);
            skipText(input); skipText(input); input.skipNBytes(64); // capacity, system, kinematics
            skipText(input); skipText(input); input.skipNBytes(4); skipText(input);
            for (int map = 0; map < 3; map++) {
                int rows = input.readInt();
                for (int row = 0; row < rows; row++) { skipText(input); input.skipNBytes(map == 2 ? 4 : 8); }
            }
            skipText(input); // legal affiliation (since schema 3)
            int equipmentOffset = current.length - input.available();
            assertEquals(0d, input.readDouble());
            assertEquals(0, ByteBuffer.wrap(current).getInt(current.length - 4), "No finished-product lots in this fixture");
            assertEquals(0, ByteBuffer.wrap(current).getInt(current.length - 8), "No mining intents in this fixture");
            byte[] legacy = new byte[current.length - 16];
            System.arraycopy(current, 0, legacy, 0, equipmentOffset);
            System.arraycopy(current, equipmentOffset + 8, legacy, equipmentOffset, current.length - equipmentOffset - 16);
            ByteBuffer.wrap(legacy).putInt(8, version);
            return legacy;
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }

    private static void skipText(java.io.DataInputStream input) throws java.io.IOException {
        input.skipNBytes(input.readInt());
    }

    private static PhysicalSourceState source(double mass) {
        return source("source.test", mass);
    }

    private static PhysicalSourceState source(String id, double mass) {
        return new PhysicalSourceState(id, SourceKind.NATURAL_OCCURRENCE, "occurrence.metallic", ExtractionEnvironment.FREE_BODY,
                ORE, mass, mass, .5, .8, Set.of());
    }
}
