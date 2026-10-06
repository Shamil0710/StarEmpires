package com.spacesim.campaign;

import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.economy.ShipyardModuleTransferWorkQueue;
import com.spacesim.persistence.*;
import com.spacesim.player.PlayerJournalState;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedCampaignPersonalModuleTransportTest {
    @Test void actualCommandsLoadCancelResumeAndUnloadExactEquipment() throws Exception {
        var c = fixture(); String station = station(c); var row = c.moduleCustody().modules().get(0);
        var fleet = c.playerState().orElseThrow().activeFleetId();
        long wallet = c.playerState().orElseThrow().walletMilliCredits();
        var stock = c.coordinator().runtime().infrastructure().endpoint(station).storage().snapshot();
        var before = c.captureState(); var preview = c.previewPilotAction("LOAD_MODULE", station, row.custodyId(), 0);
        var handling = c.coordinator().runtime().infrastructure().endpoint(station).handlingCapability();
        assertTrue(preview.allowed(), "Actual endpoint must support cargo section: " + handling);
        assertTrue(before.equals(c.captureState()), "Load preview must be pure");
        c.submitPilotAction(preview); assertEquals(1, c.moduleTransfers().orders().size());
        assertEquals(row, c.moduleCustody().modules().get(0));
        assertFalse(c.previewPilotAction("UNDOCK", "", "", 0).allowed());
        assertFalse(c.previewPilotAction("START_REFIT_USED", station, row.custodyId(), 0).allowed());
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertTrue(c.moduleTransfers().orders().get(0).completedHandlingKg() > 0);
        c = roundTrip(c); assertEquals(row, c.moduleCustody().modules().get(0));
        c.submitPilotAction(c.previewPilotAction("CANCEL_MODULE_TRANSFER", station, c.moduleTransfers().orders().get(0).orderId(), 0));
        assertTrue(c.moduleTransfers().orders().isEmpty()); assertEquals(stock, c.coordinator().runtime().infrastructure().endpoint(station).storage().snapshot());
        c.submitPilotAction(c.previewPilotAction("LOAD_MODULE", station, row.custodyId(), 0));
        c = finishOneTick(c);
        assertTrue(c.moduleTransfers().orders().isEmpty());
        var aboard = c.moduleCustody().modules().get(0);
        assertEquals(Stage20FreightPersistentState.cargoHoldId(fleet), aboard.stationId());
        assertEquals(row.custodyId(), aboard.custodyId()); assertEquals(row.condition(), aboard.condition());
        double mass = Stage22CivilianMiningProductionPath.loadProducts().findProduct(row.condition().assignment().moduleId()).unitMassKg();
        var physical = c.coordinator().runtime().freight().findFreighter(fleet).orElseThrow();
        assertEquals(mass, physical.carriedEquipmentMassKg()); assertEquals(mass, physical.cargoMassKg());
        assertEquals(0, physical.cargoStorage().productCountById().size());
        assertEquals(mass, c.coordinator().runtime().freight().moduleCargoStorage(fleet).usedCapacityKg(
                Stage22CivilianMiningProductionPath.loadProducts().findProduct(row.condition().assignment().moduleId()).storageClassId()));
        var exact = c.captureState(); c = roundTrip(c); assertTrue(exact.equals(c.captureState()), "Aboard equipment must restore exactly");
        var qa = roundTrip(c); qa.coordinator().setPaused(true);
        java.nio.file.Files.write(java.nio.file.Path.of("target/stage23b-module-aboard-fixture.s28c"), Stage228GeneratedCampaignPersistenceCodec.encode(qa.captureState()));
        var travelling = roundTrip(c);
        travelling.submitPilotAction(travelling.previewPilotAction("UNDOCK", "", "", 0));
        var world = travelling.coordinator().runtime().world();
        var origin = world.findFleet(fleet).orElseThrow().systemId();
        var hop = world.getTopology().neighbors(origin).stream().map(destination -> travelling.previewPilotAction("JUMP",
                Long.toString(destination.value()), "", 0)).filter(p -> p.allowed()).findFirst().orElseThrow();
        travelling.submitPilotAction(hop);
        assertTrue(world.findFleetJump(fleet).isPresent());
        assertEquals(aboard, travelling.moduleCustody().modules().get(0));
        assertTrue(travelling.captureState().equals(roundTrip(travelling).captureState()), "Transit must preserve exact equipment and mass");
        var lost = roundTrip(c);
        lost.coordinator().runtime().destroyLocalFreighter(fleet, com.spacesim.world.DestructionPolicy.destroyAll());
        lost.coordinator().setPaused(false); lost.advanceFrame(lost.coordinator().session().fixedStepSeconds());
        assertTrue(lost.moduleCustody().modules().isEmpty());
        assertEquals(0, lost.coordinator().runtime().freight().findFreighter(fleet).orElseThrow().carriedEquipmentMassKg());
        assertTrue(lost.captureState().equals(roundTrip(lost).captureState()), "Destroyed equipment must not reappear after restore");
        assertFalse(c.previewPilotAction("UNLOAD_MODULE", "unknown", row.custodyId(), 0).allowed());
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        c.submitPilotAction(c.previewPilotAction("UNLOAD_MODULE", station, row.custodyId(), 0));
        c = finishOneTick(c); assertEquals(row, c.moduleCustody().modules().get(0));
        assertEquals(0, c.coordinator().runtime().freight().findFreighter(fleet).orElseThrow().cargoMassKg());
        assertEquals(wallet, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(2, c.playerJournal().entries().stream().filter(e -> e.kind() == PlayerJournalState.Kind.MODULE_TRANSFER_COMPLETED).count());
        assertTrue(c.captureState().equals(roundTrip(c).captureState()), "Unload must restore station, hold and journal together");
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds()); c.coordinator().setPaused(true);
        java.nio.file.Files.write(java.nio.file.Path.of("target/stage23b-module-transport-fixture.s28c"), Stage228GeneratedCampaignPersistenceCodec.encode(c.captureState()));
    }

    @Test void missingExactEquipmentAndFutureWorkCannotCompose() {
        var c = fixture(); var row = c.moduleCustody().modules().get(0);
        c.submitPilotAction(c.previewPilotAction("LOAD_MODULE", station(c), row.custodyId(), 0));
        var base = c.captureState();
        assertThrows(IllegalArgumentException.class, () -> compose(base, com.spacesim.economy.ShipyardModuleCustodyState.empty(), base.moduleTransfers()));
        assertThrows(IllegalArgumentException.class, () -> compose(base, base.moduleCustody(),
                new ShipyardModuleTransferWorkQueue.State(base.moduleTransfers().lastProcessedTick() + 1, base.moduleTransfers().orders())));
    }

    @Test void totalHoldMassRejectsLoadingEvenWhenIndividualStorageClassesHaveRoom() {
        var c = fixture(); var station = station(c); var r = c.coordinator().runtime();
        var row = c.moduleCustody().modules().get(0); var fleet = c.playerState().orElseThrow().activeFleetId();
        var store = r.infrastructure().endpoint(station).storage();
        var ontology = com.spacesim.content.Stage18ResourceOntologyLoader.loadDefault();
        String commodity = store.snapshotCommodityMassByIdKg().keySet().stream()
                .filter(id -> ontology.findCommodity(id).storageClassId().equals("storage.dry_bulk")).findFirst().orElseThrow();
        double kg = 7_000_000;
        var source = new com.spacesim.economy.Stage18StationStorage(ontology, Stage22CivilianMiningProductionPath.loadProducts(),
                "fixture.module.cargo", Map.of("storage.dry_bulk", kg), Map.of(commodity, kg), Map.of());
        var handling = new com.spacesim.economy.Stage18LogisticsRuntime.HandlingCapability("fixture.module.mass", Set.of("storage.dry_bulk"), kg, kg);
        assertTrue(new com.spacesim.economy.Stage18LogisticsRuntime(ontology, Stage22CivilianMiningProductionPath.loadProducts())
                .transferCommodity(source, store, commodity, kg, handling, handling.openInterval(1)).transferred());
        assertTrue(r.freight().exchangeManualCommodity(fleet, store, commodity, kg, true, 0, handling, handling.openInterval(1)));
        r.synchronizeFreightEngineeringCargo(fleet);
        var before = c.captureState(); assertFalse(c.previewPilotAction("LOAD_MODULE", station, row.custodyId(), 0).allowed());
        assertTrue(before.equals(c.captureState()), "Total hold rejection must not reserve equipment or change cargo");
    }

    @Test void nativeV9MigrationPreservesStationEquipmentWithoutAddingHandlingWork() throws Exception {
        var c = fixture(); var current = c.captureState();
        // Explicit schema-1 station custody represents v9 data, before actor tags existed.
        var historicalCustody = new com.spacesim.economy.ShipyardModuleCustodyState(current.moduleCustody().modules().stream()
                .map(m -> new com.spacesim.economy.ShipyardModuleCustodyState.StoredModule(
                        m.custodyId(), m.stationId(), m.sourceAssetId(), m.removedAtTick(), m.condition())).toList());
        var base = compose(current, historicalCustody, current.moduleTransfers());
        var buffer = new java.io.ByteArrayOutputStream();
        try (var out = new java.io.DataOutputStream(buffer)) {
            out.writeInt(0x53323843); out.writeInt(9); out.writeInt(9); out.writeUTF("m22.8.generated-campaign.v9");
            for (var payload : List.of(Stage21IGeneratedWorldRuntimePersistenceCodec.encode(base.stage21Runtime()),
                    Stage228SmallCraftPersistenceCodec.encode(base.smallCraft()), Stage228HangarPersistenceCodec.encode(base.hangars()),
                    Stage228FlightDeckPersistenceCodec.encode(base.flightDeck()), Stage228OperationsPersistenceCodec.encode(base.operations()),
                    GeneratedCampaignPlayerStateCodec.encode(base.playerState()), PlayerJournalPersistenceCodec.encode(base.playerJournal()),
                    ShipyardModuleCustodyPersistenceCodec.encode(base.moduleCustody()), ShipyardRepairQueuePersistenceCodec.encode(base.repairQueue()),
                    ShipyardRefitQueuePersistenceCodec.encode(base.refitQueue()))) { out.writeInt(payload.length); out.write(payload); }
        }
        var migrated = Stage228GeneratedCampaignPersistenceCodec.decode(buffer.toByteArray());
        assertTrue(base.equals(migrated), "Historical native v9 must retain station equipment exactly");
        assertEquals(ShipyardModuleTransferWorkQueue.State.empty(), migrated.moduleTransfers());
    }

    private static Stage228CampaignAuthority fixture() {
        var c = GeneratedCampaignPersonalRefitTest.fixture(); var station = station(c);
        c.submitPilotAction(c.previewPilotAction("START_REFIT", station, Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT, 0));
        var base = c.captureState(); var job = base.refitQueue().orders().get(0);
        var yard = c.coordinator().runtime().industry().industrial().station(station).yardCapabilities().stream()
                .filter(y -> y.yardInstanceId().equals(job.yardInstanceId())).findFirst().orElseThrow();
        // Explicit prior-work fixture; only the final physical tick is accelerated here.
        var queue = new com.spacesim.economy.ShipyardRefitQueueState(base.refitQueue().lastProcessedTick(), List.of(job.withWork(
                job.requiredWorkSeconds() - yard.plannerCapability().workRate() * c.coordinator().session().fixedStepSeconds() / 2)));
        c = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistentState.compose(base.stage21Runtime(), base.smallCraft(),
                base.hangars(), base.flightDeck(), base.operations(), base.playerState(), base.playerJournal(), base.moduleCustody(), base.repairQueue(), queue, base.moduleTransfers()));
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertEquals(1, c.moduleCustody().modules().size()); c.coordinator().setPaused(true); return c;
    }

    private static Stage228CampaignAuthority finishOneTick(Stage228CampaignAuthority c) {
        var base = c.captureState(); var job = base.moduleTransfers().orders().get(0);
        double mass = Stage22CivilianMiningProductionPath.loadProducts().findProduct(job.source().condition().assignment().moduleId()).unitMassKg();
        String station = job.source().stationId().startsWith("freight-hold:") ? job.destinationStorageId() : job.source().stationId();
        double lastWork = c.coordinator().runtime().infrastructure().endpoint(station).handlingCapability().massRateKgPerSecond()
                * c.coordinator().session().fixedStepSeconds() / 2;
        var queued = new ShipyardModuleTransferWorkQueue.State(base.moduleTransfers().lastProcessedTick(), List.of(
                new ShipyardModuleTransferWorkQueue.Order(job.orderId(), job.source(), job.destinationStorageId(), job.startedAtTick(), mass - lastWork)));
        c = Stage228CampaignAuthority.restore(compose(base, base.moduleCustody(), queued));
        var paused = c.captureState(); c.coordinator().setPaused(true); c.advanceFrame(0);
        // The checkpoint's pause flag may change; physical equipment remains at the source.
        assertEquals(paused.moduleCustody(), c.moduleCustody());
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds()); return c;
    }

    private static Stage228GeneratedCampaignPersistentState compose(Stage228GeneratedCampaignPersistentState base,
            com.spacesim.economy.ShipyardModuleCustodyState custody, ShipyardModuleTransferWorkQueue.State transfers) {
        return Stage228GeneratedCampaignPersistentState.compose(base.stage21Runtime(), base.smallCraft(), base.hangars(), base.flightDeck(),
                base.operations(), base.playerState(), base.playerJournal(), custody, base.repairQueue(), base.refitQueue(), transfers);
    }
    private static String station(Stage228CampaignAuthority c) { return c.dockedModuleStationId().orElseThrow(); }
    private static Stage228CampaignAuthority roundTrip(Stage228CampaignAuthority c) {
        return Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(c.captureState())));
    }
}
