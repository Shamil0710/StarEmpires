package com.spacesim.campaign;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.*;
import com.spacesim.economy.*;
import com.spacesim.persistence.*;
import com.spacesim.player.*;
import com.spacesim.ship.*;
import com.spacesim.ship.ShipEngineeringState.*;
import com.spacesim.world.*;
import com.spacesim.ui.GeneratedCampaignRepairUi;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignPersonalRepairTest {
    @Test void realCommandsReserveRepairContinueExactlyAndCancelWithoutInstantHealing() throws Exception {
        var c = fixture();
        var station = c.playerState().orElseThrow().ownedStations().get(0);
        String id = c.coordinator().runtime().industry().industrial().stations().stream()
                .filter(s -> c.pilotMarketReference(s.stationId()).orElseThrow().entityId().equals(station.stationEntityId()))
                .findFirst().orElseThrow().stationId();
        java.nio.file.Files.write(java.nio.file.Path.of("target/stage23b-repair-fixture.s28c"),
                Stage228GeneratedCampaignPersistenceCodec.encode(c.captureState()));
        var before = c.captureState(); var engineering = ship(c); var instance = engineering.instanceState;
        var raw = c.coordinator().runtime().infrastructure().endpoint(id).storage().snapshotCommodityMassByIdKg();
        long wallet = c.playerState().orElseThrow().walletMilliCredits();
        var preview = c.previewPilotAction("START_REPAIR", id, "", 0);
        assertTrue(preview.allowed()); assertTrue(before.equals(c.captureState()), "Repair preview must be pure");
        c.submitPilotAction(preview); assertSame(instance, engineering.instanceState);
        assertEquals(wallet, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(1, c.repairQueue().orders().size());
        assertFalse(c.previewPilotAction("UNDOCK", "", "", 0).allowed());
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(preview));
        var queued = c.captureState(); c.advanceFrame(0);
        assertTrue(queued.equals(c.captureState()), "Pause/zero frame must retain damage and work exactly");
        var loaded = roundTrip(c); assertTrue(queued.equals(loaded.captureState()), "Native queue restore must be exact");
        var droppedOwner = new PlayerState(before.playerState().walletMilliCredits(), before.playerState().factionContentId(),
                before.playerState().reputations(), before.playerState().ownedFleetIds(), before.playerState().activeFleetId(),
                before.playerState().discoveredSystemIds(), before.playerState().discoveredObjects(), before.playerState().homeSystemId());
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistentState.compose(queued.stage21Runtime(),
                queued.smallCraft(), queued.hangars(), queued.flightDeck(), queued.operations(), droppedOwner, queued.playerJournal(),
                queued.moduleCustody(), queued.repairQueue()));
        float step = c.coordinator().session().fixedStepSeconds(); c.coordinator().setPaused(false); loaded.coordinator().setPaused(false);
        c.advanceFrame(step); loaded.advanceFrame(step);
        assertTrue(c.captureState().equals(loaded.captureState()), "Saved repair must continue on the same real tick");
        assertEquals(instance.damage(), ship(loaded).instanceState.damage());
        var job = loaded.repairQueue().orders().get(0);
        assertTrue(job.completedWorkSeconds() > 0 && job.completedWorkSeconds() < job.requiredWorkSeconds());
        assertTrue(GeneratedCampaignRepairUi.rows(loaded).stream().anyMatch(r -> r.selection().stableId().startsWith("pilot-repair-cancel|")));
        var cancel = loaded.previewPilotAction("CANCEL_REPAIR", id, job.orderId(), 0); assertTrue(cancel.allowed());
        loaded.submitPilotAction(cancel); assertTrue(loaded.repairQueue().orders().isEmpty());
        assertEquals(raw, loaded.coordinator().runtime().infrastructure().endpoint(id).storage().snapshotCommodityMassByIdKg());
        assertEquals(instance.damage(), ship(loaded).instanceState.damage());
        assertEquals(wallet, loaded.playerState().orElseThrow().walletMilliCredits());
        assertEquals("CANCEL_REPAIR", loaded.playerJournal().entries().get(loaded.playerJournal().entries().size() - 1).action());
        assertFalse(loaded.playerJournal().entries().stream().anyMatch(e -> e.kind() == PlayerJournalState.Kind.REPAIR_COMPLETED));
    }

    @Test void unavailableYardOrMissingPhysicalMaterialsCannotCreateWorkOrHealing() {
        var plain = FoundedCampaignFixture.restore(); var before = plain.captureState();
        var station = plain.coordinator().runtime().industry().industrial().stations().get(0);
        assertFalse(plain.previewPilotAction("START_REPAIR", station.stationId(), "", 0).allowed());
        assertTrue(before.equals(plain.captureState()), "Foreign/undocked station cannot grant repairs");
        var c = fixture();
        String id = c.coordinator().runtime().industry().industrial().stations().stream().filter(s -> c.ownsProductionStation(s.stationId())).findFirst().orElseThrow().stationId();
        var bad = c.previewPilotAction("START_REPAIR", "missing.station", "", 0); assertFalse(bad.allowed());
        var checkpoint = c.captureState();
        var s20 = c.coordinator().runtime().captureState(); var old = s20.campaign().industrialState();
        var emptyStock = old.stationStorages().stream().map(s -> s.stationId().equals(id)
                ? new Stage18StationStorage.StationStorageSnapshot(id, s.capacityByStorageClassKg(), Map.of(), s.productCountById()) : s).toList();
        var industry = new Stage18IndustrialState(old.schemaVersion(), old.contentFingerprint(), old.simulationTick(), old.sources(),
                emptyStock, old.facilities(), old.yards(), old.constructionOrders(), old.processOrders());
        var empty = Stage228CampaignAuthority.restore(withIndustry(checkpoint, s20, industry, checkpoint.playerState()));
        var baseline = empty.captureState(); assertFalse(empty.previewPilotAction("START_REPAIR", id, "", 0).allowed());
        assertTrue(baseline.equals(empty.captureState()), "Missing actual material must reject without repair or work");
    }

    @ParameterizedTest @ValueSource(strings = {"utility_sensor", "core_drive"})
    void actualCompletedTickHealsTinyDamageAndRecordsCompletionOnlyOnce(String mount) {
        var c = fixture(mount, .999999);
        String id = c.coordinator().runtime().industry().industrial().stations().stream()
                .filter(s -> c.ownsProductionStation(s.stationId())).findFirst().orElseThrow().stationId();
        var damage = ship(c).instanceState.damage(); var maintenance = ship(c).instanceState.maintenance();
        long wallet = c.playerState().orElseThrow().walletMilliCredits();
        c.submitPilotAction(c.previewPilotAction("START_REPAIR", id, "", 0));
        assertEquals(damage, ship(c).instanceState.damage());
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertTrue(c.repairQueue().orders().isEmpty());
        assertTrue(ship(c).instanceState.damage().moduleDamage().moduleIntegrityByMount().isEmpty());
        assertEquals(maintenance, ship(c).instanceState.maintenance());
        assertEquals(wallet, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(1, c.playerJournal().entries().stream().filter(e -> e.kind() == PlayerJournalState.Kind.REPAIR_COMPLETED).count());
        var loaded = roundTrip(c); loaded.coordinator().setPaused(false);
        loaded.advanceFrame(loaded.coordinator().session().fixedStepSeconds());
        assertEquals(1, loaded.playerJournal().entries().stream().filter(e -> e.kind() == PlayerJournalState.Kind.REPAIR_COMPLETED).count());
    }

    @Test void foreignRepairHoldsRealMoneyRefundsBeforeWorkAndPaysActualOperatorOnlyOnceAfterCompletion() {
        // Explicit damage, berth, installed-yard and input fixtures isolate the paid-service transaction.
        // This is not proof of ordinary travel or obtaining the working yard.
        var owner = fixture("utility_sensor", .999999, FoundedCampaignFixture.restore(), true);
        var p = owner.playerState().orElseThrow();
        var foreignPlayer = new PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(),
                p.ownedFleetIds(), p.activeFleetId(), p.discoveredSystemIds(), p.discoveredObjects(), p.homeSystemId(),
                p.dockedAt(), p.fleetOrders(), p.threatIntel(), p.ownedConstructionProjectIds(), List.of());
        var c = Stage228CampaignAuthority.restore(withIndustry(owner.captureState(), owner.coordinator().runtime().captureState(),
                owner.coordinator().runtime().captureState().campaign().industrialState(), foreignPlayer));
        String id = c.coordinator().runtime().industry().industrial().stations().stream()
                .filter(s -> c.pilotMarketReference(s.stationId()).orElseThrow().equals(p.dockedAt())).findFirst().orElseThrow().stationId();
        var runtime = c.coordinator().runtime();
        String seller = runtime.industry().industrial().station(id).stableFactionId();
        assertFalse(c.ownsProductionStation(id));
        long wallet = c.playerState().orElseThrow().walletMilliCredits();
        long treasury = runtime.world().findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits();
        long fee = c.personalRepairPriceMilliCredits(id);
        assertTrue(fee > 0 && fee <= wallet);
        assertTrue(GeneratedCampaignRepairUi.rows(c).stream().anyMatch(row -> row.selection().stableId().equals("pilot-repair|" + id + "|")));
        var raw = runtime.infrastructure().endpoint(id).storage().snapshotCommodityMassByIdKg();
        var damage = ship(c).instanceState.damage();
        var before = c.captureState();
        var preview = c.previewPilotAction("START_REPAIR", id, "", 0);
        assertTrue(preview.allowed()); assertEquals(-fee, preview.walletChangeMilliCredits()); assertEquals(before, c.captureState());
        c.submitPilotAction(preview);
        assertEquals(wallet - fee, c.playerState().orElseThrow().walletMilliCredits());
        assertEquals(treasury, runtime.world().findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits());
        assertEquals(fee, c.repairQueue().orders().get(0).servicePayment().reservedMilliCredits());
        assertEquals(damage, ship(c).instanceState.damage());
        var held = c.captureState(); var loaded = roundTrip(c);
        assertEquals(held, loaded.captureState());
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistenceCodec.decode(nativeV12(held)),
                "Historical native v12 cannot claim a held paid-service balance");
        assertThrows(IllegalStateException.class, () -> loaded.submitPilotAction(preview));
        loaded.submitPilotAction(loaded.previewPilotAction("CANCEL_REPAIR", id, loaded.repairQueue().orders().get(0).orderId(), 0));
        assertEquals(wallet, loaded.playerState().orElseThrow().walletMilliCredits());
        assertEquals(raw, loaded.coordinator().runtime().infrastructure().endpoint(id).storage().snapshotCommodityMassByIdKg());
        assertEquals(damage, ship(loaded).instanceState.damage());
        assertTrue(loaded.repairQueue().orders().isEmpty());
        var retry = loaded.previewPilotAction("START_REPAIR", id, "", 0); assertTrue(retry.allowed()); loaded.submitPilotAction(retry);
        loaded.coordinator().setPaused(false); loaded.advanceFrame(loaded.coordinator().session().fixedStepSeconds());
        assertTrue(loaded.repairQueue().orders().isEmpty());
        assertEquals(wallet - fee, loaded.playerState().orElseThrow().walletMilliCredits());
        assertEquals(treasury + fee, loaded.coordinator().runtime().world().findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits());
        assertTrue(ship(loaded).instanceState.damage().moduleDamage().moduleIntegrityByMount().isEmpty());
        assertEquals(1, loaded.playerJournal().entries().stream().filter(e -> e.action().equals("REPAIR_SERVICE_SETTLED")).count());
        var restored = roundTrip(loaded); var paid = restored.coordinator().runtime().world().findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits();
        restored.advanceFrame(restored.coordinator().session().fixedStepSeconds());
        assertEquals(paid, restored.coordinator().runtime().world().findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits());
        assertEquals(1, restored.playerJournal().entries().stream().filter(e -> e.action().equals("REPAIR_SERVICE_SETTLED")).count());
    }

    @Test void nativeV12OwnerWorkMigratesWithoutAddingAServicePaymentOrChangingItsPhysicalCheckpoint() {
        var c = fixture("utility_sensor", .999999);
        String id = c.coordinator().runtime().industry().industrial().stations().stream()
                .filter(s -> c.ownsProductionStation(s.stationId())).findFirst().orElseThrow().stationId();
        c.submitPilotAction(c.previewPilotAction("START_REPAIR", id, "", 0));
        var saved = c.captureState();
        var migrated = Stage228GeneratedCampaignPersistenceCodec.decode(nativeV12(saved));
        assertEquals(saved, migrated);
        assertNull(migrated.repairQueue().orders().get(0).servicePayment());
        assertEquals(saved, Stage228CampaignAuthority.restore(migrated).captureState());
    }

    private static byte[] nativeV12(Stage228GeneratedCampaignPersistentState state) {
        try (var in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(Stage228GeneratedCampaignPersistenceCodec.encode(state)))) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var out = new java.io.DataOutputStream(buffer)) {
                out.writeInt(in.readInt()); in.readInt(); in.readInt(); in.readUTF();
                out.writeInt(12); out.writeInt(12); out.writeUTF("m22.8.generated-campaign.v12");
                out.write(in.readAllBytes());
            }
            return buffer.toByteArray();
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
    }

    private static Stage228CampaignAuthority fixture() { return fixture("utility_sensor", .5); }
    private static Stage228CampaignAuthority fixture(String mount, double integrity) {
        return fixture(mount, integrity, FoundedCampaignFixture.restore());
    }
    static Stage228CampaignAuthority fixture(String mount, double integrity, Stage228CampaignAuthority c) {
        return fixture(mount, integrity, c, false);
    }
    static Stage228CampaignAuthority fixture(String mount, double integrity, Stage228CampaignAuthority c, boolean civilian) {
        var r = c.coordinator().runtime(); var world = r.world();
        c.coordinator().setPaused(true);
        var initialFleet = world.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var station = r.industry().industrial().stations().stream()
                .filter(s -> c.pilotMarketReference(s.stationId()).isPresent()
                        && (!civilian || GeneratedCampaignStationSalePolicy.priceMilliCredits(s.stationArchetypeId()) > 0)).findFirst().orElseThrow();
        // Explicit docking-location fixture preserves the same physical fleet; not a paid player travel scenario.
        if (!station.systemId().equals(initialFleet.systemId())) {
            world.beginFleetTransfer(initialFleet.id(), station.systemId());
            world.completeFleetTransfer(initialFleet.id(), 0, 0);
        }
        var fleet = world.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var ship = ship(c); var state = ship.instanceState;
        ship.setInstanceState(new ShipInstanceRuntimeState(new ShipDamageRuntime.Snapshot(Map.of(), new DamageState(Map.of(mount, integrity))),
                state.shieldStatesByMount(), new ShipyardEngineeringService.MaintenanceState(Map.of(mount, 920d)), state.weaponLoadout(), state.weaponMountRuntime()));
        var catalog = Stage22CivilianMiningProductionPath.loadRuntimeShipyards();
        var design = catalog.getYards().stream().filter(d -> ShipyardRepairWorkQueue.plan(fleet.localEntityId(), ship, design(d)).feasibility().feasible()).findFirst().orElseThrow();
        var ref = c.pilotMarketReference(station.stationId()).orElseThrow();
        var materialization = r.arrival().materialization(fleet.systemId());
        if (!station.systemId().equals(initialFleet.systemId()))
            materialization.registerPhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(station.position()));
        materialization.updatePhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(station.position()));
        var base = c.captureState(); var s20 = r.captureState(); var old = s20.campaign().industrialState();
        var facilities = new ArrayList<>(old.facilities().stream().map(f -> {
            if (!f.stationId().equals(station.stationId()) || !design.requiredSupportFacilityDefinitionIds().contains(f.state().definitionId())) return f;
            var d = Stage18FacilityCatalogLoader.loadDefault().findFacility(f.state().definitionId());
            return new Stage18IndustrialState.FacilityInstallationSnapshot(f.stationId(), new Stage18FacilityRuntime.InstalledFacilityState(
                    f.state().facilityInstanceId(), d.id(), 1, d.ratedProcessPowerW(), d.ratedProcessPowerW() * d.heatRejectionWPerProcessW(),
                    d.requiredLaborUnitsAtFullRate(), d.maintenanceWorkRate(), f.state().locationTag(), true));
        }).toList());
        var constructionOrders = new ArrayList<>(old.constructionOrders());
        var construction = new Stage18FacilityConstructionRuntime(Stage18FacilityConstructionCatalogLoader.loadDefault(),
                Stage18FacilityCatalogLoader.loadDefault(), Stage18ResourceOntologyLoader.loadDefault());
        String location = station.facilities().get(0).locationTag();
        for (String support : design.requiredSupportFacilityDefinitionIds()) {
            if (facilities.stream().anyMatch(f -> f.stationId().equals(station.stationId()) && f.state().definitionId().equals(support))) continue;
            String instance = "fixture.repair.support." + support;
            var order = construction.createOrder(instance, instance, support, station.stationId(), location);
            // Explicit authored completed-construction fixture; not ordinary player construction evidence.
            constructionOrders.add(new Stage18FacilityConstructionRuntime.ConstructionOrderSnapshot(order.orderId(), instance,
                    support, station.stationId(), location, order.requiredMassByCommodityKg(), order.requiredMassByCommodityKg(),
                    order.requiredWorkSeconds(), order.requiredWorkSeconds(), Stage18FacilityConstructionRuntime.OrderStatus.COMPLETE));
            var d = Stage18FacilityCatalogLoader.loadDefault().findFacility(support);
            facilities.add(new Stage18IndustrialState.FacilityInstallationSnapshot(station.stationId(), new Stage18FacilityRuntime.InstalledFacilityState(
                    instance, support, 1, d.ratedProcessPowerW(), d.ratedProcessPowerW() * d.heatRejectionWPerProcessW(),
                    d.requiredLaborUnitsAtFullRate(), d.maintenanceWorkRate(), location, true)));
        }
        var yards = new ArrayList<>(old.yards());
        // Explicit finite installation/power, station ownership and supplied-material fixtures, not ordinary acquisition proof.
        yards.add(new Stage18IndustrialState.YardInstallationSnapshot(station.stationId(), new Stage18ShipyardRuntime.InstalledYardState(
                "fixture.personal.repair.yard", design.id(), 1, design.ratedIntegrationPowerW(), design.ratedEngineeringWorkRate(),
                design.laborCapacity(), design.automationCapacity(), true)));
        var industry = new Stage18IndustrialState(old.schemaVersion(), old.contentFingerprint(), old.simulationTick(), old.sources(),
                old.stationStorages(), facilities, yards, constructionOrders, old.processOrders());
        var p = base.playerState();
        var known = new ArrayList<>(p.discoveredSystemIds()); if (!known.contains(ref.systemId())) known.add(ref.systemId());
        var knownObjects = new ArrayList<>(p.discoveredObjects()); if (!knownObjects.contains(ref)) knownObjects.add(ref);
        var owner = new PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(), p.ownedFleetIds(), p.activeFleetId(),
                known, knownObjects, p.homeSystemId(), ref, p.fleetOrders(), p.threatIntel(),
                p.ownedConstructionProjectIds(), List.of(new OwnedStationRef(ref.systemId(), ref.entityId())));
        var loaded = Stage228CampaignAuthority.restore(withIndustry(base, s20, industry, owner));
        var liveYard = loaded.coordinator().runtime().industry().industrial().station(station.stationId()).yardCapabilities().stream()
                .filter(y -> y.yardInstanceId().equals("fixture.personal.repair.yard")).findFirst().orElseThrow();
        assertTrue(liveYard.active()); assertTrue(loaded.hasPersonalRepairAccess(station.stationId()));
        var ontology = Stage18ResourceOntologyLoader.loadDefault(); var products = Stage22CivilianMiningProductionPath.loadProducts();
        var inputs = new Stage18ShipyardRuntime(catalog, ontology, products).repairMaterialRequirements(
                ShipyardRepairWorkQueue.plan(fleet.localEntityId(), ship(loaded), liveYard), ship(loaded).instanceState.damage());
        var capacities = new TreeMap<String, Double>(); inputs.forEach((id, mass) -> capacities.merge(ontology.findCommodity(id).storageClassId(), mass * 2, Double::sum));
        var supply = new Stage18StationStorage(ontology, products, "fixture.repair.supply", capacities, inputs, Map.of());
        var handling = new Stage18LogisticsRuntime.HandlingCapability("fixture.handling", capacities.keySet(), inputs.values().stream().mapToDouble(Double::doubleValue).sum(),
                inputs.values().stream().mapToDouble(Double::doubleValue).max().orElseThrow());
        var budget = handling.openInterval(1); var target = loaded.coordinator().runtime().infrastructure().endpoint(station.stationId()).storage();
        for (var material : inputs.entrySet()) assertTrue(new Stage18LogisticsRuntime(ontology, products).transferCommodity(supply, target,
                material.getKey(), material.getValue(), handling, budget).transferred());
        assertTrue(supply.snapshotCommodityMassByIdKg().isEmpty());
        return loaded;
    }
    private static Stage18ShipyardRuntime.YardCapabilitySnapshot design(Stage18ShipyardCatalog.YardDefinition d) {
        var c = new ShipyardEngineeringService.ShipyardCapability("design.only", d.berthDimensionsM(), d.maxServiceMassKg(), d.stage175FabricationCapabilities(),
                d.stage175HandledRequirementIds(), d.toolingTags(), d.precisionCapability(), d.ratedEngineeringWorkRate(), d.laborCapacity(), d.automationCapacity(), d.ratedIntegrationPowerW());
        return new Stage18ShipyardRuntime.YardCapabilitySnapshot("design.only", d.id(), Stage18ShipyardRuntime.YardStatus.ACTIVE, c, d.handledStorageClassIds(), d.maxHandledUnitMassKg());
    }
    private static EngineeringComponent ship(Stage228CampaignAuthority c) {
        var r = c.coordinator().runtime(); var f = r.world().findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        return r.world().findSession(f.systemId()).orElseThrow().getEntityRegistry().require(f.localEntityId()).getComponent(EngineeringComponent.class);
    }
    private static Stage228CampaignAuthority roundTrip(Stage228CampaignAuthority c) {
        return Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(c.captureState())));
    }
    private static Stage228GeneratedCampaignPersistentState withIndustry(Stage228GeneratedCampaignPersistentState base,
            Stage20GeneratedWorldRuntimePersistentState s, Stage18IndustrialState industry, PlayerState player) {
        var old = s.campaign();
        var campaign = new Stage20GeneratedCampaignPersistentState(old.schemaVersion(), old.generationIdentity(), old.materializedWorld(), old.materializationState(),
                industry, old.discoveryState(), old.openRuntimeBoundaries());
        var next = new Stage20GeneratedWorldRuntimePersistentState(s.schemaVersion(), s.bridgeVersion(), campaign, s.worldState(), s.activeSystemId(),
                s.strategicStepTicks(), s.remoteUpdateBudgetPerFrame(), s.freight(), s.localFleetPhysicalStates());
        return GeneratedCampaignPlayerWorldTransition.composeRuntime(base, next, player);
    }
}
