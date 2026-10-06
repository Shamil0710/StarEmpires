package com.spacesim.campaign;

import com.spacesim.content.*;
import com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader;
import com.spacesim.economy.*;
import com.spacesim.persistence.*;
import com.spacesim.player.*;
import com.spacesim.ui.GeneratedCampaignManufacturingUi;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignManufacturingQueuePersistenceTest {
    @Test void realCampaignRetainsReservedCustodyAndRejectsMissingPersonalStationOwner() {
        var campaign = FoundedCampaignFixture.restore();
        var products = Stage22CivilianMiningProductionPath.loadProducts();
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var catalog = Stage22CivilianMiningProductionPath.loadManufacturing();
        String module = Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID;
        double mass = products.findProduct(module).unitMassKg();
        var profile = catalog.findProductProfile(catalog.findProductBinding(module).profileId());
        var initialCampaign = campaign;
        var station = campaign.coordinator().runtime().industry().industrial().stations().stream()
                .filter(s -> initialCampaign.hasManufacturingLine(s.stationId(), module))
                .filter(s -> s.storage().remainingCapacityKg(products.findProduct(module).storageClassId()) >= mass)
                .filter(s -> profile.inputs().stream().allMatch(i -> s.storage().remainingCapacityKg(
                        ontology.findCommodity(i.commodityId()).storageClassId()) >= mass))
                .findFirst().orElseThrow();
        assertTrue(GeneratedCampaignManufacturingUi.rows(campaign).isEmpty());
        var before = campaign.captureState();
        assertFalse(campaign.previewPilotAction("START_MANUFACTURING", station.stationId(), module, 1).allowed());
        assertEquals(before, campaign.captureState());
        var ref = campaign.pilotMarketReference(station.stationId()).orElseThrow();
        var p = before.playerState();
        // Explicit existing-station ownership fixture; no ownership is granted by production/UI.
        var knownSystems = new ArrayList<>(p.discoveredSystemIds());
        if (!knownSystems.contains(ref.systemId())) knownSystems.add(ref.systemId());
        var owner = new PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(), p.ownedFleetIds(),
                p.activeFleetId(), knownSystems, p.discoveredObjects(), p.homeSystemId(), p.dockedAt(),
                p.fleetOrders(), p.threatIntel(), p.ownedConstructionProjectIds(),
                List.of(new OwnedStationRef(ref.systemId(), ref.entityId())));
        campaign = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistentState.compose(before.stage21Runtime(),
                before.smallCraft(), before.hangars(), before.flightDeck(), before.operations(), owner));
        var runtime = campaign.coordinator().runtime();
        var storage = runtime.industry().industrial().station(station.stationId()).storage();
        var materials = new TreeMap<String, Double>();
        var capacities = new TreeMap<String, Double>();
        profile.inputs().forEach(i -> {
            materials.put(i.commodityId(), mass * i.fractionOfOutputMass());
            capacities.put(ontology.findCommodity(i.commodityId()).storageClassId(), mass * 2);
        });
        // Finite physical material fixture; the production line is actually installed in this world.
        var supplied = new Stage18StationStorage(ontology, products, "fixture.inputs", capacities, materials, Map.of());
        var handling = new Stage18LogisticsRuntime.HandlingCapability("fixture.handling", capacities.keySet(), mass, mass);
        var logistics = new Stage18LogisticsRuntime(ontology, products);
        for (var input : materials.entrySet()) assertTrue(logistics.transferCommodity(supplied, storage,
                input.getKey(), input.getValue(), handling, handling.openInterval(1)).transferred());
        assertTrue(supplied.snapshotCommodityMassByIdKg().isEmpty());
        var installed = runtime.industry().industrial().station(station.stationId()).facilityCapabilities().stream()
                .filter(c -> c.capabilityTags().containsAll(profile.requiredCapabilityTags()) && c.effectiveProcessPowerW() > 0
                        && c.effectiveEngineeringWorkRate() > 0 && c.effectiveMaintenanceWorkRate() > 0
                        && c.maxHandledUnitMassKg() >= mass).findFirst().orElseThrow();
        var actualLine = new Stage18ManufacturingRuntime.ManufacturingCapability(installed.facilityInstanceId(),
                installed.capabilityTags(), installed.effectiveProcessPowerW(), installed.effectiveEngineeringWorkRate(),
                installed.effectiveMaintenanceWorkRate());
        runtime.manufacturingQueue().start(Stage18ManufacturingWorkQueue.PREFIX + station.stationId(), module, 1,
                storage, actualLine, runtime.world().getAuthoritativeWorldTick());
        assertEquals(0, storage.productCount(module));
        var saved = campaign.captureState();
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistentState.compose(saved.stage21Runtime(),
                saved.smallCraft(), saved.hangars(), saved.flightDeck(), saved.operations(), p));
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistentState.compose(saved.stage21Runtime(),
                saved.smallCraft(), saved.hangars(), saved.flightDeck(), saved.operations(), null));
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertTrue(saved.equals(restored.captureState()), "Native campaign must preserve reserved manufacturing exactly");
        var restoredStorage = restored.coordinator().runtime().industry().industrial().station(station.stationId()).storage();
        for (var storageClass : storage.snapshotCapacityByStorageClassKg().keySet())
            assertEquals(storage.usedCapacityKg(storageClass), restoredStorage.usedCapacityKg(storageClass));
        assertEquals(2, GeneratedCampaignManufacturingUi.rows(restored).size());
        restored.advanceFrame(0);
        assertTrue(saved.equals(restored.captureState()), "Zero frame cannot perform work");
        campaign.coordinator().setPaused(false); restored.coordinator().setPaused(false);
        float step = campaign.coordinator().session().fixedStepSeconds();
        campaign.advanceFrame(step); restored.advanceFrame(step);
        assertTrue(campaign.captureState().equals(restored.captureState()), "Restored queue must continue on the same actual tick");
        double fraction = restored.coordinator().runtime().manufacturingQueue().capture().get(0).completedFraction();
        assertTrue(fraction > 0 && fraction < 1, "An actual installed line must perform bounded partial work");
        var partial = restored.captureState();
        var resumed = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(partial)));
        assertTrue(partial.equals(resumed.captureState()), "Actual partial work must survive a composed save");
        restored.advanceFrame(step); resumed.advanceFrame(step);
        assertTrue(restored.captureState().equals(resumed.captureState()), "Actual partial work must continue identically");
        var working = resumed.captureState();
        long removalTick = resumed.coordinator().runtime().world().getAuthoritativeWorldTick();
        // Individual removed-module fixture shares the real station with actual in-progress production.
        var custody = new ShipyardModuleCustodyState(List.of(new ShipyardModuleCustodyState.StoredModule(
                "fixture.removed/mining", station.stationId(), 31, removalTick,
                new com.spacesim.ship.ShipyardRefitContinuity.RemovedModuleState(
                        new com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition("mission_primary", module), .4, 100))));
        var combined = Stage228GeneratedCampaignPersistentState.compose(working.stage21Runtime(), working.smallCraft(),
                working.hangars(), working.flightDeck(), working.operations(), working.playerState(), working.playerJournal(), custody);
        var combinedLoaded = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(combined)));
        assertTrue(combined.equals(combinedLoaded.captureState()), "Native save must preserve both physical custody owners");
        String storageClass = products.findProduct(module).storageClassId();
        var sourceStore = resumed.coordinator().runtime().industry().industrial().station(station.stationId()).storage();
        var combinedStore = combinedLoaded.coordinator().runtime().industry().industrial().station(station.stationId()).storage();
        assertEquals(sourceStore.usedCapacityKg(storageClass) + mass, combinedStore.usedCapacityKg(storageClass), 1e-8);
        assertEquals(0, combinedStore.productCount(module), "Stored used module is not a free finished manufacturing output");
        var combinedAgain = Stage228CampaignAuthority.restore(combinedLoaded.captureState());
        combinedLoaded.coordinator().setPaused(false); combinedAgain.coordinator().setPaused(false);
        combinedLoaded.advanceFrame(step); combinedAgain.advanceFrame(step);
        assertTrue(combinedLoaded.captureState().equals(combinedAgain.captureState()), "Combined custody must continue on actual ticks");
        assertEquals(custody, combinedAgain.moduleCustody());
    }
}
