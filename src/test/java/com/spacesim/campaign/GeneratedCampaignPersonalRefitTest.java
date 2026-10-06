package com.spacesim.campaign;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.*;
import com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.economy.*;
import com.spacesim.persistence.*;
import com.spacesim.player.*;
import com.spacesim.ship.*;
import com.spacesim.world.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignPersonalRefitTest {
    private static final String MINING = Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT;
    private static final String CARGO = Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT;

    @Test void foreignRefitReservesRefundsAndSettlesActualMoneyWhileRemovedEquipmentRemainsPersonal() throws Exception {
        // Explicit engineering, berth, equipment, funding and prior-work fixtures; not ordinary acquisition proof.
        var own = fixture(true); String id = station(own); var base = own.captureState(); var p = base.playerState();
        var foreign = new PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(), p.ownedFleetIds(), p.activeFleetId(),
                p.discoveredSystemIds(), p.discoveredObjects(), p.homeSystemId(), p.dockedAt(), p.fleetOrders(), p.threatIntel(),
                p.ownedConstructionProjectIds(), List.of());
        var c = Stage228CampaignAuthority.restore(GeneratedCampaignPlayerWorldTransition.composeRuntime(base, own.coordinator().runtime().captureState(), foreign));
        long fee = c.personalRefitPriceMilliCredits(id, MINING); assertTrue(fee > 0);
        long balance = Math.max(foreign.walletMilliCredits(), Math.multiplyExact(fee, 3));
        long source = balance - foreign.walletMilliCredits();
        if (source > 0) c.coordinator().runtime().world().findSession(c.playerState().orElseThrow().dockedAt().systemId()).orElseThrow()
                .getLedger().recordMoneySource("PLAYER", source, "fixture.foreign-refit-funding");
        var funded = new PlayerState(balance, foreign.factionContentId(), foreign.reputations(), foreign.ownedFleetIds(), foreign.activeFleetId(),
                foreign.discoveredSystemIds(), foreign.discoveredObjects(), foreign.homeSystemId(), foreign.dockedAt(), foreign.fleetOrders(),
                foreign.threatIntel(), foreign.ownedConstructionProjectIds(), foreign.ownedStations());
        c = Stage228CampaignAuthority.restore(GeneratedCampaignPlayerWorldTransition.composeRuntime(c.captureState(), c.coordinator().runtime().captureState(), funded));
        String seller = c.coordinator().runtime().industry().industrial().station(id).stableFactionId();
        long treasury = c.coordinator().runtime().world().findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits();
        var before = c.captureState(); var preview = c.previewPilotAction("START_REFIT", id, MINING, 0);
        assertEquals(before, Stage228GeneratedCampaignPersistenceCodec.decode(nativeV13(before)), "Historical v13 must not create new rights or obligations");
        assertTrue(preview.allowed()); assertEquals(-fee, preview.walletChangeMilliCredits()); assertEquals(before, c.captureState());
        c.submitPilotAction(preview);
        assertEquals(balance - fee, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(treasury, c.coordinator().runtime().world().findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits());
        assertEquals(fee, c.refitQueue().orders().get(0).servicePayment().reservedMilliCredits());
        var held = c.captureState(); c = roundTrip(c); assertEquals(held, c.captureState());
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistenceCodec.decode(nativeV13(held)));
        c.submitPilotAction(c.previewPilotAction("CANCEL_REFIT", id, c.refitQueue().orders().get(0).orderId(), 0));
        assertEquals(balance, c.playerState().orElseThrow().walletMilliCredits());
        c.submitPilotAction(c.previewPilotAction("START_REFIT", id, MINING, 0));
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertFalse(c.previewPilotAction("CANCEL_REFIT", id, c.refitQueue().orders().get(0).orderId(), 0).allowed());
        c = roundTrip(c);
        var pending = c.captureState(); var order = pending.refitQueue().orders().get(0);
        var yard = c.coordinator().runtime().industry().industrial().station(id).yardCapabilities().stream()
                .filter(y -> y.yardInstanceId().equals(order.yardInstanceId())).findFirst().orElseThrow();
        double finalWork = yard.plannerCapability().workRate() * c.coordinator().session().fixedStepSeconds() / 2;
        // Full finite execution is covered by the domain test; isolate the campaign settlement boundary.
        c = Stage228CampaignAuthority.restore(withRefits(pending, new ShipyardRefitQueueState(pending.refitQueue().lastProcessedTick(),
                List.of(order.withWork(order.requiredWorkSeconds() - finalWork))), pending.playerState()));
        c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertTrue(c.refitQueue().orders().isEmpty());
        assertEquals(balance - fee, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(treasury + fee, c.coordinator().runtime().world().findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits());
        var removed = c.moduleCustody().modules().get(0);
        assertEquals(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID, removed.ownerActorId());
        assertEquals(.35, removed.condition().integrity()); assertEquals(920, removed.condition().secondsSinceService());
        assertTrue(c.ownsStoredModule(removed.custodyId())); assertFalse(c.ownsProductionStation(id));
        assertTrue(com.spacesim.ui.GeneratedCampaignModuleCustodyUi.rows(c).stream().anyMatch(row -> row.selection().stableId().contains(removed.custodyId())));
        assertEquals(1, c.playerJournal().entries().stream().filter(e -> e.action().equals("REFIT_SERVICE_SETTLED")).count());
        var complete = c.captureState(); c = roundTrip(c); assertEquals(complete, c.captureState());
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistenceCodec.decode(nativeV13(complete)));
        c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertEquals(1, c.playerJournal().entries().stream().filter(e -> e.action().equals("REFIT_SERVICE_SETTLED")).count());
        var used = c.previewPilotAction("START_REFIT_USED", id, removed.custodyId(), 0);
        assertTrue(used.allowed()); assertTrue(used.walletChangeMilliCredits() < 0 && -used.walletChangeMilliCredits() < fee);
        c.submitPilotAction(used); assertTrue(c.isStoredModuleReserved(removed.custodyId()));
        var using = c.captureState(); c = roundTrip(c); assertEquals(using, c.captureState());
        c.submitPilotAction(c.previewPilotAction("CANCEL_REFIT", id, c.refitQueue().orders().get(0).orderId(), 0));
        assertEquals(removed, c.moduleCustody().modules().get(0));
        var load = c.previewPilotAction("LOAD_MODULE", id, removed.custodyId(), 0); assertTrue(load.allowed()); c.submitPilotAction(load);
        var handling = c.captureState(); c = roundTrip(c); assertEquals(handling, c.captureState());
        int ticks = 0;
        while (!c.moduleTransfers().orders().isEmpty() && ticks++ < 500) c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertTrue(c.moduleTransfers().orders().isEmpty());
        var carried = c.moduleCustody().modules().get(0);
        assertEquals(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID, carried.ownerActorId());
        assertTrue(c.moduleCarrier(carried.stationId()).isPresent()); assertEquals(removed.condition(), carried.condition());
        assertEquals(c.captureState(), roundTrip(c).captureState());
    }

    @Test void actualCommandsReserveEquipmentContinueOnRealTicksAndCancelExactly() throws Exception {
        var c = fixture(); String id = station(c); var ship = ship(c); var sourceFit = ship.fit; var instance = ship.instanceState;
        var store = c.coordinator().runtime().infrastructure().endpoint(id).storage(); var stock = store.snapshot();
        long wallet = c.playerState().orElseThrow().walletMilliCredits(); var before = c.captureState();
        assertTrue(c.hasPersonalRefitAccess(id, MINING));
        var preview = c.previewPilotAction("START_REFIT", id, MINING, 0);
        assertTrue(preview.allowed()); assertTrue(before.equals(c.captureState()), "Refit preview must be pure");
        c.submitPilotAction(preview); assertSame(instance, ship.instanceState); assertEquals(sourceFit, ship.fit);
        assertEquals(0, store.productCount(Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID));
        assertEquals(1, c.refitQueue().orders().size()); assertEquals(wallet, c.playerState().orElseThrow().walletMilliCredits());
        assertFalse(c.previewPilotAction("START_REPAIR", id, "", 0).allowed());
        assertFalse(c.previewPilotAction("UNDOCK", "", "", 0).allowed());
        assertFalse(c.previewPilotAction("START_REFIT", id, MINING, 0).allowed());
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(preview));
        var queued = c.captureState(); c.advanceFrame(0);
        assertTrue(queued.equals(c.captureState()), "Paused work must preserve ship and escrow");
        var loaded = roundTrip(c); assertTrue(queued.equals(loaded.captureState()), "Native refit restore must retain all owners exactly");
        assertEquals(1, com.spacesim.ui.GeneratedCampaignRefitUi.rows(loaded).stream()
                .filter(row -> row.selection().stableId().startsWith("pilot-refit-cancel|")).count());
        var p = queued.playerState();
        var composed = GeneratedCampaignPlayerWorldTransition.compose(queued,
                new PlayableWorldState(PlayableWorldState.CURRENT_VERSION, c.coordinator().runtime().captureState().worldState(), p));
        assertEquals(queued.refitQueue(), composed.refitQueue());
        c.coordinator().setPaused(false); loaded.coordinator().setPaused(false); float step = c.coordinator().session().fixedStepSeconds();
        c.advanceFrame(step); loaded.advanceFrame(step);
        assertTrue(c.captureState().equals(loaded.captureState()), "Saved refit must continue identically on actual completed time");
        var job = loaded.refitQueue().orders().get(0);
        assertTrue(job.completedWorkSeconds() > 0 && job.completedWorkSeconds() < job.requiredWorkSeconds());
        assertEquals(sourceFit, ship(loaded).fit); assertEquals(instance.damage(), ship(loaded).instanceState.damage());
        loaded.submitPilotAction(loaded.previewPilotAction("CANCEL_REFIT", id, job.orderId(), 0));
        assertTrue(loaded.refitQueue().orders().isEmpty());
        assertEquals(stock, loaded.coordinator().runtime().infrastructure().endpoint(id).storage().snapshot());
        assertEquals(wallet, loaded.playerState().orElseThrow().walletMilliCredits());
        assertFalse(loaded.playerJournal().entries().stream().anyMatch(e -> e.kind() == PlayerJournalState.Kind.REFIT_COMPLETED));
        assertEquals("CANCEL_REFIT", loaded.playerJournal().entries().get(loaded.playerJournal().entries().size() - 1).action());
        var migrated = Stage228GeneratedCampaignPersistenceCodec.decode(nativeV8(before));
        assertTrue(before.equals(migrated), "Native v8 must migrate without granting equipment or work");
        assertEquals(ShipyardRefitQueueState.empty(), migrated.refitQueue());
        FilesForProbe.write(before);
    }

    @Test void missingEquipmentAndOverfullTargetHoldRejectBeforeAnyEscrowOrFittingChange() {
        var c = fixture(); String id = station(c); var r = c.coordinator().runtime(); var store = r.infrastructure().endpoint(id).storage();
        var products = Stage22CivilianMiningProductionPath.loadProducts(); var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var product = products.findProduct(Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID);
        // Finite logistics removes the fixture's supplied module; the player cannot reserve absent equipment.
        var sink = new Stage18StationStorage(ontology, products, "fixture.refit.sink",
                Map.of(product.storageClassId(), product.unitMassKg()), Map.of(), Map.of());
        var handling = new Stage18LogisticsRuntime.HandlingCapability("fixture.refit.transfer", Set.of(product.storageClassId()),
                product.unitMassKg(), product.unitMassKg());
        assertTrue(new Stage18LogisticsRuntime(ontology, products).transferProduct(store, sink, product.contentId(), 1,
                handling, handling.openInterval(1)).transferred());
        var baseline = c.captureState(); assertFalse(c.previewPilotAction("START_REFIT", id, MINING, 0).allowed());
        assertTrue(baseline.equals(c.captureState()), "Missing module must reject without escrow or work");
        // Actual purchased cargo and provenance exceeding the smaller eight-million-kilogram target.
        var fleet = c.playerState().orElseThrow().activeFleetId();
        var commodity = ontology.getCommodities().stream().filter(item -> r.freight().cargoHoldSnapshot(fleet)
                .capacityByStorageClassKg().containsKey(item.storageClassId())).findFirst().orElseThrow();
        double kg = Stage22CivilianMiningEngineeringCatalogLoader.ORE_CAPACITY_KG + 1;
        var supply = new Stage18StationStorage(ontology, products, "fixture.refit.cargo",
                Map.of(commodity.storageClassId(), kg), Map.of(commodity.id(), kg), Map.of());
        var cargoHandling = new Stage18LogisticsRuntime.HandlingCapability("fixture.cargo.transfer", Set.of(commodity.storageClassId()), kg, kg);
        assertTrue(new Stage18LogisticsRuntime(ontology, products).transferCommodity(supply, store, commodity.id(), kg,
                cargoHandling, cargoHandling.openInterval(1)).transferred());
        assertTrue(r.freight().exchangeManualCommodity(fleet, store, commodity.id(), kg, true, 0, cargoHandling, cargoHandling.openInterval(1)));
        r.synchronizeFreightEngineeringCargo(fleet);
        var before = r.freight().capture(); var fitting = ship(c).fit;
        assertThrows(IllegalArgumentException.class, () -> r.freight().previewPersonalRefit(fleet, MINING));
        assertFalse(c.previewPilotAction("START_REFIT", id, MINING, 0).allowed());
        assertEquals(before, r.freight().capture()); assertSame(fitting, ship(c).fit); assertTrue(c.refitQueue().orders().isEmpty());
    }

    @Test void finalActualTickPublishesFittingSmallerHoldUsedEquipmentAndJournalTogether() throws Exception {
        var c = fixture(); String id = station(c); var r = c.coordinator().runtime(); var fleetId = c.playerState().orElseThrow().activeFleetId();
        var store = r.infrastructure().endpoint(id).storage();
        String commodity = store.snapshotCommodityMassByIdKg().entrySet().stream().filter(e -> e.getValue() >= 100)
                .findFirst().orElseThrow().getKey();
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var handling = new Stage18LogisticsRuntime.HandlingCapability("fixture.refit.cargo", Set.of(ontology.findCommodity(commodity).storageClassId()), 100, 100);
        assertTrue(r.freight().exchangeManualCommodity(fleetId, store, commodity, 100, true, 0, handling, handling.openInterval(1)));
        r.synchronizeFreightEngineeringCargo(fleetId); var lots = r.freight().cargoLots();
        var original = r.freight().findFreighter(fleetId).orElseThrow(); var fitting = ship(c).fit;
        long wallet = c.playerState().orElseThrow().walletMilliCredits();
        c.submitPilotAction(c.previewPilotAction("START_REFIT", id, MINING, 0));
        var queued = c.captureState(); var order = queued.refitQueue().orders().get(0);
        var yard = r.industry().industrial().station(id).yardCapabilities().stream()
                .filter(y -> y.yardInstanceId().equals(order.yardInstanceId())).findFirst().orElseThrow();
        double finalWork = yard.plannerCapability().workRate() * c.coordinator().session().fixedStepSeconds() / 2;
        // Explicit prior-work fixture isolates final-tick composition. Full finite-work accounting is tested
        // by ShipyardRefitWorkQueueTest; this is not an ordinary full-duration player acquisition proof.
        var nearComplete = new ShipyardRefitQueueState(queued.refitQueue().lastProcessedTick(), List.of(order.withWork(order.requiredWorkSeconds() - finalWork)));
        c = Stage228CampaignAuthority.restore(withRefits(queued, nearComplete, queued.playerState()));
        var before = c.captureState(); c.advanceFrame(0); assertTrue(before.equals(c.captureState()), "Prior work cannot finish on pause");
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertTrue(c.refitQueue().orders().isEmpty());
        var actual = c.coordinator().runtime().freight().findFreighter(fleetId).orElseThrow();
        assertEquals(original.hullId(), actual.hullId()); assertEquals(original.stableFactionId(), actual.stableFactionId());
        assertEquals(original.ownershipOrdinal(), actual.ownershipOrdinal()); assertEquals(MINING, actual.fitId());
        assertEquals(Stage22CivilianMiningEngineeringCatalogLoader.ORE_CAPACITY_KG, actual.cargoCapacityKg());
        assertEquals(original.cargoStorage().commodityMassByIdKg(), actual.cargoStorage().commodityMassByIdKg());
        assertEquals(100, actual.cargoMassKg()); assertEquals(lots, c.coordinator().runtime().freight().cargoLots());
        assertEquals(original.cargoStorage().stationId(), actual.cargoStorage().stationId());
        assertTrue(actual.cargoStorage().capacityByStorageClassKg().values().stream().allMatch(kg -> kg == actual.cargoCapacityKg()));
        var changed = ship(c); assertEquals(fitting.hullId(), changed.fit.hullId());
        assertTrue(new ShipMiningEngineeringAdapter().derive(new ShipEngineeringRuntime(Stage22FreightStrategicEngineeringCatalogLoader.loadDefault())
                .derive(changed.fit, changed.runtimeState, changed.instanceState.damage().moduleDamage())).isPresent());
        var removed = c.moduleCustody().modules().get(0);
        assertEquals(id, removed.stationId()); assertEquals(order.assetId(), removed.sourceAssetId());
        assertEquals(.35, removed.condition().integrity()); assertTrue(removed.condition().secondsSinceService() >= 920);
        assertEquals(c.coordinator().runtime().world().getAuthoritativeWorldTick(), removed.removedAtTick());
        assertEquals(0, c.coordinator().runtime().infrastructure().endpoint(id).storage().productCount("module.industrial_union_cargo_section_v1"));
        assertEquals(wallet, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(1, c.playerJournal().entries().stream().filter(e -> e.kind() == PlayerJournalState.Kind.REFIT_COMPLETED).count());
        var loaded = roundTrip(c); assertTrue(c.captureState().equals(loaded.captureState()), "Completed fitting, hold, custody and journal must restore together");
        loaded.coordinator().setPaused(false); loaded.advanceFrame(loaded.coordinator().session().fixedStepSeconds());
        assertEquals(1, loaded.playerJournal().entries().stream().filter(e -> e.kind() == PlayerJournalState.Kind.REFIT_COMPLETED).count());
        var proposedReturn = loaded.coordinator().runtime().freight().previewPersonalRefit(fleetId, CARGO);
        assertEquals(Stage20FreightRuntimeMaterializer.CURRENT_PAYLOAD_KG, proposedReturn.cargoCapacityKg());
        assertEquals("fit.test_bulk_freighter_baseline_v1", proposedReturn.fitId());
        String custodyId = removed.custodyId();
        assertTrue(loaded.hasPersonalStoredModuleRefitAccess(custodyId));
        var stockBefore = loaded.coordinator().runtime().infrastructure().endpoint(id).storage().snapshot();
        var previewBefore = loaded.captureState();
        java.nio.file.Files.write(java.nio.file.Path.of("target/stage23b-used-refit-fixture.s28c"),
                Stage228GeneratedCampaignPersistenceCodec.encode(previewBefore));
        var usedPreview = loaded.previewPilotAction("START_REFIT_USED", id, custodyId, 0);
        assertTrue(usedPreview.allowed()); assertTrue(previewBefore.equals(loaded.captureState()), "Used preview must be pure");
        loaded.submitPilotAction(usedPreview);
        assertTrue(loaded.isStoredModuleReserved(custodyId));
        assertFalse(loaded.hasPersonalStoredModuleRefitAccess(custodyId));
        assertFalse(loaded.previewPilotAction("START_REFIT_USED", id, custodyId, 0).allowed());
        assertTrue(loaded.refitQueue().orders().get(0).reservedProductCounts().isEmpty());
        loaded = roundTrip(loaded);
        assertEquals(removed, loaded.moduleCustody().modules().get(0));
        var usedCheckpoint = loaded.captureState();
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistentState.compose(
                usedCheckpoint.stage21Runtime(), usedCheckpoint.smallCraft(), usedCheckpoint.hangars(), usedCheckpoint.flightDeck(),
                usedCheckpoint.operations(), usedCheckpoint.playerState(), usedCheckpoint.playerJournal(),
                com.spacesim.economy.ShipyardModuleCustodyState.empty(), usedCheckpoint.repairQueue(), usedCheckpoint.refitQueue()));
        loaded.submitPilotAction(loaded.previewPilotAction("CANCEL_REFIT", id, loaded.refitQueue().orders().get(0).orderId(), 0));
        assertEquals(stockBefore, loaded.coordinator().runtime().infrastructure().endpoint(id).storage().snapshot());
        assertFalse(loaded.isStoredModuleReserved(custodyId)); assertEquals(removed, loaded.moduleCustody().modules().get(0));
        loaded.submitPilotAction(loaded.previewPilotAction("START_REFIT_USED", id, custodyId, 0));
        var usedJob = loaded.refitQueue().orders().get(0); var usedQueued = loaded.captureState();
        loaded = Stage228CampaignAuthority.restore(withRefits(usedQueued,
                new ShipyardRefitQueueState(usedQueued.refitQueue().lastProcessedTick(),
                        List.of(usedJob.withWork(usedJob.requiredWorkSeconds() - finalWork))), usedQueued.playerState()));
        loaded.coordinator().setPaused(false); loaded.advanceFrame(loaded.coordinator().session().fixedStepSeconds());
        assertTrue(loaded.refitQueue().orders().isEmpty());
        assertEquals(fitting, ship(loaded).fit);
        assertEquals(.35, ship(loaded).instanceState.damage().moduleDamage().moduleIntegrityByMount().get("mission_primary"));
        assertTrue(ship(loaded).instanceState.maintenance().secondsSinceServiceByMount().get("mission_primary")
                >= removed.condition().secondsSinceService());
        var returned = loaded.coordinator().runtime().freight().findFreighter(fleetId).orElseThrow();
        assertEquals(proposedReturn.fitId(), returned.fitId()); assertEquals(proposedReturn.cargoCapacityKg(), returned.cargoCapacityKg());
        assertEquals(100, returned.cargoMassKg()); assertEquals(lots, loaded.coordinator().runtime().freight().cargoLots());
        assertEquals(1, loaded.moduleCustody().modules().size());
        assertEquals(Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID,
                loaded.moduleCustody().modules().get(0).condition().assignment().moduleId());
        assertEquals(0, loaded.coordinator().runtime().infrastructure().endpoint(id).storage().productCount("module.industrial_union_cargo_section_v1"));
        assertEquals(wallet, loaded.playerState().orElseThrow().walletMilliCredits());
        assertTrue(loaded.captureState().equals(roundTrip(loaded).captureState()), "Used completion must restore fit, condition, cargo and custody together");
    }

    @Test void nativeV8PreservesRepairAndRefitCompositionRejectsMissingOwnerOrInventedYard() throws Exception {
        var c = fixture(); String id = station(c); c.submitPilotAction(c.previewPilotAction("START_REPAIR", id, "", 0));
        var repaired = c.captureState(); var migrated = Stage228GeneratedCampaignPersistenceCodec.decode(nativeV8(repaired));
        assertEquals(repaired.repairQueue(), migrated.repairQueue()); assertEquals(ShipyardRefitQueueState.empty(), migrated.refitQueue());
        assertTrue(repaired.equals(migrated), "Native v8 must retain existing repair escrow exactly");
        c.submitPilotAction(c.previewPilotAction("CANCEL_REPAIR", id, c.repairQueue().orders().get(0).orderId(), 0));
        c.submitPilotAction(c.previewPilotAction("START_REFIT", id, MINING, 0)); var queued = c.captureState(); var job = queued.refitQueue().orders().get(0);
        var p = queued.playerState(); var missingOwner = new PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(),
                p.ownedFleetIds(), p.activeFleetId(), p.discoveredSystemIds(), p.discoveredObjects(), p.homeSystemId());
        assertThrows(IllegalArgumentException.class, () -> withRefits(queued, queued.refitQueue(), missingOwner));
        var forged = new ShipyardRefitQueueState.Order(job.orderId(), job.fleetId(), job.assetId(), job.stationId(), "uninstalled.yard",
                job.yardDefinitionId(), job.startedAtTick(), job.sourceFit(), job.targetFit(), job.sourceDamage(), job.requiredWorkSeconds(),
                job.completedWorkSeconds(), job.reservedProductCounts());
        assertThrows(IllegalArgumentException.class, () -> withRefits(queued,
                new ShipyardRefitQueueState(queued.refitQueue().lastProcessedTick(), List.of(forged)), p));
        assertThrows(IllegalArgumentException.class, () -> withRefits(queued,
                new ShipyardRefitQueueState(queued.refitQueue().lastProcessedTick() + 1, queued.refitQueue().orders()), p));
    }

    private static Stage228GeneratedCampaignPersistentState withRefits(Stage228GeneratedCampaignPersistentState state,
            ShipyardRefitQueueState queue, PlayerState player) {
        return Stage228GeneratedCampaignPersistentState.compose(state.stage21Runtime(), state.smallCraft(), state.hangars(), state.flightDeck(),
                state.operations(), player, state.playerJournal(), state.moduleCustody(), state.repairQueue(), queue);
    }

    static Stage228CampaignAuthority fixture() { return fixture(false); }
    private static Stage228CampaignAuthority fixture(boolean civilian) {
        var c = FoundedCampaignFixture.restore(); var r = c.coordinator().runtime(); var p = c.playerState().orElseThrow();
        var union = r.freight().capture().freighters().stream().filter(f -> f.stableFactionId().equals("faction.beta")
                && f.phase() == Stage20FreightPersistentState.FreightPhase.IDLE).findFirst().orElseThrow();
        // Explicit selection/docking/yard fixtures use the real existing reserve and a conserved paid purchase.
        var adapter = PlayerRuntime.attachToCampaign(r.world(), c.coordinator().content(), p);
        assertTrue(new PlayerOwnershipService(adapter).purchaseFactionFleet(union.fleetId(), "faction.beta", Stage228CampaignAuthority.PILOT_SHIP_PRICE_MILLI_CREDITS));
        p = adapter.player(); var known = new ArrayList<>(p.discoveredSystemIds()); if (!known.contains(union.currentSystemId())) known.add(union.currentSystemId());
        var selected = new PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(), p.ownedFleetIds(), union.fleetId(),
                known, p.discoveredObjects(), p.homeSystemId(), null, p.fleetOrders(), p.threatIntel(), p.ownedConstructionProjectIds(), p.ownedStations());
        c = Stage228CampaignAuthority.restore(GeneratedCampaignPlayerWorldTransition.composeRuntime(c.captureState(), r.captureState(), selected));
        c = GeneratedCampaignPersonalRepairTest.fixture("mission_primary", .35, c, civilian);
        var products = Stage22CivilianMiningProductionPath.loadProducts(); var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var module = products.findProduct(Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID);
        var source = new Stage18StationStorage(ontology, products, "fixture.refit.equipment",
                Map.of(module.storageClassId(), module.unitMassKg()), Map.of(), Map.of(module.contentId(), 1));
        var handling = new Stage18LogisticsRuntime.HandlingCapability("fixture.refit.transfer", Set.of(module.storageClassId()), module.unitMassKg(), module.unitMassKg());
        var store = c.coordinator().runtime().infrastructure().endpoint(station(c)).storage();
        assertTrue(new Stage18LogisticsRuntime(ontology, products).transferProduct(source, store, module.contentId(), 1,
                handling, handling.openInterval(1)).transferred());
        return c;
    }
    private static String station(Stage228CampaignAuthority c) {
        return c.coordinator().runtime().industry().industrial().stations().stream().filter(s -> c.ownsProductionStation(s.stationId())).findFirst().orElseThrow().stationId();
    }
    private static EngineeringComponent ship(Stage228CampaignAuthority c) {
        var world = c.coordinator().runtime().world(); var fleet = world.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        return world.findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId()).getComponent(EngineeringComponent.class);
    }
    private static Stage228CampaignAuthority roundTrip(Stage228CampaignAuthority c) {
        return Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(c.captureState())));
    }
    private static byte[] nativeV8(Stage228GeneratedCampaignPersistentState base) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(0x53323843); out.writeInt(8); out.writeInt(8); out.writeUTF("m22.8.generated-campaign.v8");
            for (var payload : List.of(Stage21IGeneratedWorldRuntimePersistenceCodec.encode(base.stage21Runtime()),
                    Stage228SmallCraftPersistenceCodec.encode(base.smallCraft()), Stage228HangarPersistenceCodec.encode(base.hangars()),
                    Stage228FlightDeckPersistenceCodec.encode(base.flightDeck()), Stage228OperationsPersistenceCodec.encode(base.operations()),
                    GeneratedCampaignPlayerStateCodec.encode(base.playerState()), PlayerJournalPersistenceCodec.encode(base.playerJournal()),
                    ShipyardModuleCustodyPersistenceCodec.encode(base.moduleCustody()), ShipyardRepairQueuePersistenceCodec.encode(base.repairQueue()))) {
                out.writeInt(payload.length); out.write(payload);
            }
        }
        return bytes.toByteArray();
    }
    private static byte[] nativeV13(Stage228GeneratedCampaignPersistentState state) throws IOException {
        try (var in = new DataInputStream(new ByteArrayInputStream(Stage228GeneratedCampaignPersistenceCodec.encode(state)))) {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeInt(in.readInt()); in.readInt(); in.readInt(); in.readUTF();
                out.writeInt(13); out.writeInt(13); out.writeUTF("m22.8.generated-campaign.v13"); out.write(in.readAllBytes());
            }
            return bytes.toByteArray();
        }
    }
    private static final class FilesForProbe {
        private static void write(Stage228GeneratedCampaignPersistentState state) throws IOException {
            java.nio.file.Files.write(java.nio.file.Path.of("target/stage23b-refit-fixture.s28c"), Stage228GeneratedCampaignPersistenceCodec.encode(state));
        }
    }
}

