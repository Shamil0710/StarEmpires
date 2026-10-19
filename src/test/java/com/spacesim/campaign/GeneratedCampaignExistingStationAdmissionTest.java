package com.spacesim.campaign;

import com.spacesim.persistence.*;
import com.spacesim.player.PlayerState;
import com.spacesim.ui.GeneratedCampaignStationAcquisitionUi;
import com.spacesim.world.*;
import java.io.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignExistingStationAdmissionTest {
    @Test void purchaseRegistersExistingRefineryWithoutStockOrOperatingGrantsAndRestoresExactly() throws Exception {
        var f = fixture(); var c = f.campaign; var r = c.coordinator().runtime();
        var before = c.captureState(); var stock = f.endpoint.storage().snapshot();
        var industry = r.captureState().campaign().industrialState();
        int count = r.industry().industrial().stations().size();
        assertFalse(f.endpoint.generatedIndustrial());
        assertEquals(180_000_000, c.stationSalePriceMilliCredits(f.endpoint.stationId()));
        assertTrue(GeneratedCampaignStationAcquisitionUi.rows(c).stream()
                .anyMatch(row -> row.selection().stableId().equals("pilot-station-purchase|" + f.endpoint.stationId())));
        var preview = c.previewPilotAction("PURCHASE_STATION", f.endpoint.stationId(), "", 0);
        assertTrue(preview.allowed()); assertEquals(before, c.captureState());
        long treasury = r.world().findFactionEconomicState(f.owner).orElseThrow().treasuryMilliCredits();
        c.submitPilotAction(preview);
        assertEquals(before.playerState().walletMilliCredits() - 180_000_000, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(treasury + 180_000_000, r.world().findFactionEconomicState(f.owner).orElseThrow().treasuryMilliCredits());
        assertTrue(c.ownsProductionStation(f.endpoint.stationId()));
        assertEquals(Map.of(f.endpoint.stationId(), f.owner), c.captureState().industrialAdmissions());
        assertEquals(count + 1, r.industry().industrial().stations().size());
        var station = r.industry().industrial().station(f.endpoint.stationId());
        assertSame(f.endpoint.storage(), station.storage());
        assertSame(station.storage(), r.infrastructure().endpoint(station.stationId()).storage());
        assertTrue(r.infrastructure().endpoint(station.stationId()).generatedIndustrial());
        assertEquals(f.endpoint.handlingCapability(), r.infrastructure().endpoint(station.stationId()).handlingCapability());
        assertEquals(stock, station.storage().snapshot());
        assertEquals(industry.stationStorages(), r.captureState().campaign().industrialState().stationStorages());
        assertEquals(f.endpoint.position(), station.position());
        assertEquals(10_000_000d, station.storage().snapshotCapacityByStorageClassKg().get("storage.liquid_tank"));
        assertFalse(station.facilities().isEmpty());
        for (var facility : station.facilities()) {
            assertFalse(facility.enabled()); assertEquals(1d, facility.conditionFraction());
            assertEquals(0d, facility.allocatedProcessPowerW()); assertEquals(0d, facility.availableHeatRejectionW());
            assertEquals(0d, facility.availableLaborUnits()); assertEquals(0d, facility.availableMaintenanceWorkRate());
        }
        assertTrue(station.yards().isEmpty());
        var complete = c.captureState();
        assertThrows(IllegalStateException.class, () -> r.allocateFacilityResources(station.stationId(), station.facilities().get(0).facilityInstanceId()));
        assertThrows(IllegalArgumentException.class, () -> r.admitExistingIndustrialStation(station.stationId(), f.owner));
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(preview));
        assertEquals(complete, c.captureState());
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(complete)));
        assertEquals(complete, restored.captureState());
        assertSame(restored.coordinator().runtime().industry().industrial().station(station.stationId()).storage(),
                restored.coordinator().runtime().infrastructure().endpoint(station.stationId()).storage());
        var runtime = complete.stage21Runtime().stage21HRuntime().stage21GRuntime().stage21FRuntime().stage21ERuntime()
                .stage21DRuntime().stage21CRuntime().stage21BRuntime().stage21ARuntime().stage20Runtime();
        assertEquals(complete, GeneratedCampaignPlayerWorldTransition.composeRuntime(complete, runtime, complete.playerState()));
    }

    @Test void genuineV26DoesNotInferAdmissionAndDroppingAdmissionCannotDiscardNewIndustrialState() throws Exception {
        var f = fixture(); var c = f.campaign; var baseline = c.captureState();
        var migrated = Stage228GeneratedCampaignPersistenceCodec.decode(asV26(baseline));
        assertTrue(migrated.industrialAdmissions().isEmpty());
        assertEquals(HistoricalCampaignContentFixture.withoutObservations(baseline), Stage228CampaignAuthority.restore(migrated).captureState(),
                "v26 holds everything but the observed quotes it cannot contain");
        c.submitPilotAction(c.previewPilotAction("PURCHASE_STATION", f.endpoint.stationId(), "", 0));
        var purchased = c.captureState();
        var forged = Stage228GeneratedCampaignPersistenceCodec.decode(asV26(purchased));
        assertThrows(IllegalArgumentException.class, () -> Stage228CampaignAuthority.restore(forged));
        assertEquals(purchased, c.captureState());
    }

    @Test void originalUnregisteredOperatorCannotSellOrGrantIndustrialAdmission() throws Exception {
        var f = fixture(false); var c = f.campaign; var before = c.captureState();
        assertEquals(0, c.stationSalePriceMilliCredits(f.endpoint.stationId()));
        assertFalse(c.previewPilotAction("PURCHASE_STATION", f.endpoint.stationId(), "", 0).allowed());
        assertEquals(before, c.captureState());
        assertTrue(c.captureState().industrialAdmissions().isEmpty());
        assertFalse(c.coordinator().runtime().infrastructure().endpoint(f.endpoint.stationId()).generatedIndustrial());
    }

    @Test void rejectsUnknownStationAndOperatorConflictingWithExistingLegalState() throws Exception {
        var f = fixture(); var c = f.campaign;
        c.submitPilotAction(c.previewPilotAction("PURCHASE_STATION", f.endpoint.stationId(), "", 0));
        var saved = c.captureState();
        assertThrows(IllegalArgumentException.class, () -> Stage228CampaignAuthority.restore(
                withAdmissions(saved, Map.of("station.does-not-exist", f.owner))));
        assertThrows(IllegalArgumentException.class, () -> Stage228CampaignAuthority.restore(
                withAdmissions(saved, Map.of(f.endpoint.stationId(), "faction.does-not-exist"))));
        String other = c.coordinator().runtime().world().getFactionDiplomacyStates().stream()
                .map(state -> state.factionContentId()).filter(id -> !id.equals(f.owner))
                .filter(id -> c.coordinator().runtime().world().findFactionEconomicState(id).isPresent())
                .findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> Stage228CampaignAuthority.restore(
                withAdmissions(saved, Map.of(f.endpoint.stationId(), other))));
        assertEquals(saved, c.captureState());
    }

    private static Stage228GeneratedCampaignPersistentState withAdmissions(
            Stage228GeneratedCampaignPersistentState s, Map<String, String> admissions) {
        return new Stage228GeneratedCampaignPersistentState(s.schemaVersion(), s.runtimeVersion(), s.stage21Runtime(),
                s.smallCraft(), s.hangars(), s.flightDeck(), s.operations(), s.playerState(), s.playerJournal(),
                s.moduleCustody(), s.repairQueue(), s.refitQueue(), s.moduleTransfers(), s.productTransfers(),
                s.yardConstruction(), s.timeSkip(), s.crewManifest(), s.localFlights(), s.personalCraftOwnership(), admissions);
    }

    @Test void pendingIndividualHandlingSurvivesAdmissionReloadCompletionAndCancellation() throws Exception {
        var f = fixture(true, "station.infrastructure.frontier_multipurpose"); var c = f.campaign; var initial = c.captureState();
        long purchasePrice = c.stationSalePriceMilliCredits(f.endpoint.stationId());
        assertEquals(200_000_000, purchasePrice);
        var r = c.coordinator().runtime(); var fleet = c.playerState().orElseThrow().activeFleetId();
        String stationId = f.endpoint.stationId(); long tick = r.world().getAuthoritativeWorldTick();
        var products = com.spacesim.content.Stage22CivilianMiningProductionPath.loadProducts();
        var hold = r.freight().moduleCargoStorage(fleet);
        double fixedStep = c.coordinator().session().fixedStepSeconds();
        var product = products.getProducts().stream()
                .filter(p -> p.kind() == com.spacesim.content.Stage18ManufacturingProductRegistry.ProductKind.MODULE)
                .filter(p -> f.endpoint.handlingCapability().supportedStorageClassIds().contains(p.storageClassId())
                        && f.endpoint.storage().snapshotCapacityByStorageClassKg().containsKey(p.storageClassId())
                        && hold.snapshotCapacityByStorageClassKg().containsKey(p.storageClassId()))
                .filter(p -> p.unitMassKg() <= f.endpoint.handlingCapability().maxUnitMassKg()
                        && p.unitMassKg() <= f.endpoint.storage().remainingCapacityKg(p.storageClassId())
                        && p.unitMassKg() <= hold.remainingCapacityKg(p.storageClassId())
                        && p.unitMassKg() <= r.freight().findFreighter(fleet).orElseThrow().cargoCapacityKg())
                .findFirst().orElseThrow(() -> new AssertionError("No actual module fits both existing stores and finite handling"));
        double stationUsedKg = f.endpoint.storage().usedCapacityKg(product.storageClassId());
        var row = new com.spacesim.economy.ShipyardModuleCustodyState.StoredModule("fixture.admission.pending-module", stationId,
                r.world().findFleet(fleet).orElseThrow().localEntityId().value(), 0,
                new com.spacesim.ship.ShipyardRefitContinuity.RemovedModuleState(
                    new com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition("fixture.cargo", product.contentId()), .63, 420),
                Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
        var custody = new com.spacesim.economy.ShipyardModuleCustodyState(List.of(row));
        // Explicit historical physical custody/work fixture. Ordinary LOAD_MODULE at a pre-admission
        // endpoint is not claimed; PURCHASE_STATION and subsequent handling use production commands.
        com.spacesim.economy.ShipyardModuleCustodyStorage.bind(custody, products, id ->
                id.equals(stationId) ? f.endpoint.storage() : id.equals(hold.stationId()) ? hold : null);
        var q = new com.spacesim.economy.ShipyardModuleTransferWorkQueue(products,
                com.spacesim.economy.ShipyardModuleTransferWorkQueue.State.empty());
        var context = new com.spacesim.economy.ShipyardModuleTransferWorkQueue.Context(f.endpoint.storage(), hold, f.endpoint.handlingCapability());
        double partialInterval = Math.min(fixedStep, product.unitMassKg() / (2 * f.endpoint.handlingCapability().massRateKgPerSecond()));
        q.start("fixture.pending-handling", custody, row.custodyId(), context, java.util.Set.of(), tick - 1);
        var partial = q.advance(tick, custody, order -> context,
                order -> f.endpoint.handlingCapability().openInterval(partialInterval), java.util.Set.of());
        double paid = q.capture().orders().get(0).completedHandlingKg();
        assertTrue(paid > 0 && paid < product.unitMassKg()); assertEquals(custody, partial.custody());
        var seeded = withHandling(initial, custody, q.capture());
        c = roundTrip(seeded); assertEquals(seeded, c.captureState());
        var pending = c; var pendingPlayer = pending.playerState().orElseThrow();
        var pendingRuntime = pending.coordinator().runtime();
        var berthEntity = pendingRuntime.world().findSession(pendingPlayer.dockedAt().systemId()).orElseThrow()
                .getEntityRegistry().require(pendingPlayer.dockedAt().entityId());
        var legal = berthEntity.remove(com.spacesim.components.FactionComponent.class);
        assertThrows(IllegalArgumentException.class, pending::captureState, "Unregistered station cannot authorize pending handling");
        berthEntity.add(legal);
        var playerField = Stage228CampaignAuthority.class.getDeclaredField("playerState"); playerField.setAccessible(true);
        playerField.set(pending, new PlayerState(pendingPlayer.walletMilliCredits(), pendingPlayer.factionContentId(), pendingPlayer.reputations(),
                pendingPlayer.ownedFleetIds(), pendingPlayer.activeFleetId(), pendingPlayer.discoveredSystemIds(), pendingPlayer.discoveredObjects(),
                pendingPlayer.homeSystemId(), null, pendingPlayer.fleetOrders(), pendingPlayer.threatIntel(),
                pendingPlayer.ownedConstructionProjectIds(), pendingPlayer.ownedStations()));
        assertThrows(IllegalArgumentException.class, pending::captureState, "Pending handling requires actual docking");
        playerField.set(pending, pendingPlayer);
        var placement = pendingRuntime.world().findFleet(fleet).orElseThrow();
        var local = pendingRuntime.arrival().materialization(placement.systemId());
        var pose = local.physicalState(placement.localEntityId()).orElseThrow();
        local.updatePhysicalState(placement.localEntityId(), LocalPhysicalKinematics.stationary(pose.position().translated(5000, 0)));
        assertThrows(IllegalArgumentException.class, pending::captureState, "Docking metadata cannot authorize remote handling");
        local.updatePhysicalState(placement.localEntityId(), pose);
        assertEquals(seeded, pending.captureState());
        var source = c.coordinator().runtime().infrastructure().endpoint(stationId).storage();
        assertEquals(stationUsedKg + product.unitMassKg(), source.usedCapacityKg(product.storageClassId()));
        var preview = c.previewPilotAction("PURCHASE_STATION", stationId, "", 0);
        assertTrue(preview.allowed()); assertEquals(seeded, c.captureState());
        c.submitPilotAction(preview);
        assertSame(source, c.coordinator().runtime().industry().industrial().station(stationId).storage());
        assertEquals(custody, c.moduleCustody()); assertEquals(q.capture(), c.moduleTransfers());
        assertEquals(stationUsedKg + product.unitMassKg(), source.usedCapacityKg(product.storageClassId()));
        var purchased = c.captureState();
        assertEquals(initial.playerState().walletMilliCredits() - purchasePrice, purchased.playerState().walletMilliCredits());
        c = roundTrip(purchased); assertEquals(purchased, c.captureState());
        assertSame(c.coordinator().runtime().infrastructure().endpoint(stationId).storage(),
                c.coordinator().runtime().industry().industrial().station(stationId).storage());

        var cancelled = roundTrip(purchased);
        cancelled.submitPilotAction(cancelled.previewPilotAction("CANCEL_MODULE_TRANSFER", stationId, "fixture.pending-handling", 0));
        assertTrue(cancelled.moduleTransfers().orders().isEmpty()); assertEquals(custody, cancelled.moduleCustody());
        assertEquals(stationUsedKg + product.unitMassKg(), cancelled.coordinator().runtime().infrastructure().endpoint(stationId).storage().usedCapacityKg(product.storageClassId()));
        assertEquals(0, cancelled.coordinator().runtime().freight().findFreighter(fleet).orElseThrow().carriedEquipmentMassKg());
        assertEquals(purchased.playerState(), cancelled.playerState().orElseThrow());
        var cancelledState = cancelled.captureState(); assertEquals(cancelledState, roundTrip(cancelledState).captureState());

        int intervals = 0;
        while (!c.moduleTransfers().orders().isEmpty() && intervals++ < 10000) {
            c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds()); c.coordinator().setPaused(true);
        }
        assertTrue(c.moduleTransfers().orders().isEmpty(), "Existing finite handling must complete the remaining work");
        var aboard = c.moduleCustody().modules().get(0);
        assertEquals(row.custodyId(), aboard.custodyId()); assertEquals(row.condition(), aboard.condition());
        assertEquals(row.ownerActorId(), aboard.ownerActorId());
        assertEquals(Stage20FreightPersistentState.cargoHoldId(fleet), aboard.stationId());
        assertEquals(stationUsedKg, c.coordinator().runtime().infrastructure().endpoint(stationId).storage().usedCapacityKg(product.storageClassId()));
        assertEquals(product.unitMassKg(), c.coordinator().runtime().freight().findFreighter(fleet).orElseThrow().carriedEquipmentMassKg());
        assertEquals(purchased.playerState().walletMilliCredits(), c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(1, c.playerJournal().entries().stream().filter(e -> e.action().equals("MODULE_TRANSFER_COMPLETED")).count());
        var complete = c.captureState(); assertEquals(complete, roundTrip(complete).captureState());
    }

    private static Stage228CampaignAuthority roundTrip(Stage228GeneratedCampaignPersistentState state) {
        return Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(state)));
    }

    private static Stage228GeneratedCampaignPersistentState withHandling(Stage228GeneratedCampaignPersistentState s,
            com.spacesim.economy.ShipyardModuleCustodyState custody, com.spacesim.economy.ShipyardModuleTransferWorkQueue.State handling) {
        return new Stage228GeneratedCampaignPersistentState(s.schemaVersion(), s.runtimeVersion(), s.stage21Runtime(),
                s.smallCraft(), s.hangars(), s.flightDeck(), s.operations(), s.playerState(), s.playerJournal(),
                custody, s.repairQueue(), s.refitQueue(), handling, s.productTransfers(),
                s.yardConstruction(), s.timeSkip(), s.crewManifest(), s.localFlights(), s.personalCraftOwnership(), s.industrialAdmissions());
    }

    private static byte[] asV26(Stage228GeneratedCampaignPersistentState state) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var in = new DataInputStream(new ByteArrayInputStream(Stage228GeneratedCampaignPersistenceCodec.encode(
                HistoricalCampaignContentFixture.withoutObservations(state))));
                var out = new DataOutputStream(bytes)) {
            out.writeInt(in.readInt()); in.readInt(); in.readInt(); in.readUTF();
            out.writeInt(26); out.writeInt(26); out.writeUTF("m22.8.generated-campaign.v26");
            HistoricalCampaignContentFixture.copyPayloads(in, out, 18);
            int length = in.readInt(); assertEquals(length, in.readNBytes(length).length); HistoricalCampaignContentFixture.skipEmptyBuild(in); HistoricalCampaignContentFixture.skipEmptyObservations(in); assertEquals(-1, in.read());
        }
        return bytes.toByteArray();
    }

    static Fixture fixture() throws Exception {
        return fixture(true);
    }

    private static Fixture fixture(boolean explicitLegalRegistration) throws Exception {
        return fixture(explicitLegalRegistration, "station.infrastructure.refinery_complex");
    }

    private static Fixture fixture(boolean explicitLegalRegistration, String archetypeId) throws Exception {
        var c = FoundedCampaignFixture.restore(); var r = c.coordinator().runtime(); var w = r.world();
        var endpoint = r.infrastructure().endpoints().stream().filter(e -> !e.generatedIndustrial()
                && e.stationArchetypeId().equals(archetypeId))
                .filter(e -> c.pilotMarketReference(e.stationId()).map(ref -> w.findSession(ref.systemId()).orElseThrow()
                        .getEntityRegistry().require(ref.entityId()).getComponent(com.spacesim.components.FactionComponent.class) == null).orElse(false))
                .findFirst().orElseThrow();
        assertEquals(0, c.stationSalePriceMilliCredits(endpoint.stationId()), "Unreceived location cannot disclose an offer");
        // Explicit finite funding and berth fixtures isolate integration; ordinary acquisition is not claimed.
        var p = c.playerState().orElseThrow(); long wallet = 500_000_000;
        w.findSession(w.getActiveSystemId()).orElseThrow().getLedger().recordMoneySource("PLAYER", wallet - p.walletMilliCredits(), "explicit-refinery-acquisition-test");
        var field = Stage228CampaignAuthority.class.getDeclaredField("playerState"); field.setAccessible(true);
        field.set(c, new PlayerState(wallet, p.factionContentId(), p.reputations(), p.ownedFleetIds(), p.activeFleetId(),
                p.discoveredSystemIds(), p.discoveredObjects(), p.homeSystemId(), p.dockedAt(), p.fleetOrders(), p.threatIntel(),
                p.ownedConstructionProjectIds(), p.ownedStations()));
        var fleet = w.findFleet(p.activeFleetId()).orElseThrow();
        if (!fleet.systemId().equals(endpoint.systemId())) {
            w.beginFleetTransfer(fleet.id(), endpoint.systemId()); w.completeFleetTransfer(fleet.id(), 0, 0);
            fleet = w.findFleet(fleet.id()).orElseThrow();
        }
        var local = r.arrival().materialization(fleet.systemId());
        if (local.physicalState(fleet.localEntityId()).isEmpty())
            local.registerPhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position()));
        local.updatePhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position()));
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds()); c.coordinator().setPaused(true);
        c.submitPilotAction(c.previewPilotAction("DOCK", endpoint.stationId(), "", 0));
        var ref = c.pilotMarketReference(endpoint.stationId()).orElseThrow();
        if (explicitLegalRegistration) {
            // Deliberate legal-state fixture, not an ordinary authoring fix: original seed1 refineries
            // have no registered seller. Reuse an existing economic actor and its finite treasury.
            String operator = r.industry().industrial().stations().get(0).stableFactionId();
            w.findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId())
                    .add(new com.spacesim.components.FactionComponent(w.findFactionRuntimeId(operator).orElseThrow()));
        }
        var legal = w.findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId())
                .getComponent(com.spacesim.components.FactionComponent.class);
        return new Fixture(c, endpoint, legal == null ? null : w.findFactionStableId(legal.factionId).orElseThrow());
    }

    record Fixture(Stage228CampaignAuthority campaign, Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint endpoint, String owner) { }
}
