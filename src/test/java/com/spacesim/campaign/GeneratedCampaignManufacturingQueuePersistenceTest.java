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
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void bulkCustodyRequiresPersonalOwnerAndItsNativeVersion(boolean components) {
        var initial = FoundedCampaignFixture.restore();
        String recipe = components ? "manufacturing.component.heavy" : "refining.conductor_metal";
        var station = initial.coordinator().runtime().industry().industrial().stations().stream()
                .filter(s -> components ? initial.hasComponentLine(s.stationId(), recipe) : initial.hasRefiningLine(s.stationId(), recipe)).findFirst().orElseThrow();
        var before = initial.captureState();
        assertFalse(initial.previewPilotAction(components ? "START_COMPONENT_MANUFACTURING" : "START_REFINING", station.stationId(), recipe, 100).allowed());
        var ref = initial.pilotMarketReference(station.stationId()).orElseThrow();
        var p = before.playerState();
        var systems = new ArrayList<>(p.discoveredSystemIds());
        if (!systems.contains(ref.systemId())) systems.add(ref.systemId());
        // Explicit ownership/input fixtures isolate native custody rules; the ordinary journey is separate.
        var owner = new PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(), p.ownedFleetIds(),
                p.activeFleetId(), systems, p.discoveredObjects(), p.homeSystemId(), p.dockedAt(),
                p.fleetOrders(), p.threatIntel(), p.ownedConstructionProjectIds(), List.of(new OwnedStationRef(ref.systemId(), ref.entityId())));
        var campaign = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistentState.compose(before.stage21Runtime(),
                before.smallCraft(), before.hangars(), before.flightDeck(), before.operations(), owner));
        var runtime = campaign.coordinator().runtime();
        var storage = runtime.industry().industrial().station(station.stationId()).storage();
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var materials = new TreeMap<String, Double>();
        var tags = components ? Stage22CivilianMiningProductionPath.loadManufacturing().findComponentRecipe(recipe).requiredCapabilityTags()
                : Stage18RefiningCatalogLoader.loadDefault().findRecipe(recipe).requiredCapabilityTags();
        if (components) Stage22CivilianMiningProductionPath.loadManufacturing().findComponentRecipe(recipe).inputs()
                .forEach(i -> materials.put(i.commodityId(), 100 * i.fractionOfOutputMass()));
        else materials.put("commodity.feedstock.conductor_ore", 100d);
        var capacities = new TreeMap<String, Double>();
        materials.forEach((commodity, mass) -> capacities.merge(ontology.findCommodity(commodity).storageClassId(), mass, Double::sum));
        var source = new Stage18StationStorage(ontology, Stage22CivilianMiningProductionPath.loadProducts(),
                "fixture.materials", capacities, materials, Map.of());
        var handling = new Stage18LogisticsRuntime.HandlingCapability("fixture.handling", capacities.keySet(), 100, 100);
        var logistics = new Stage18LogisticsRuntime(ontology, Stage22CivilianMiningProductionPath.loadProducts());
        for (var input : materials.entrySet()) assertTrue(logistics.transferCommodity(source, storage, input.getKey(),
                input.getValue(), handling, handling.openInterval(1)).transferred());
        var installed = runtime.industry().industrial().station(station.stationId()).facilityCapabilities().stream()
                .filter(c -> c.capabilityTags().containsAll(tags) && c.effectiveProcessPowerW() > 0).findFirst().orElseThrow();
        var line = new Stage18ManufacturingRuntime.ManufacturingCapability(installed.facilityInstanceId(), installed.capabilityTags(),
                installed.effectiveProcessPowerW(), installed.effectiveEngineeringWorkRate(), installed.effectiveMaintenanceWorkRate());
        if (components) runtime.manufacturingQueue().startComponents(Stage18ManufacturingWorkQueue.COMPONENT_PREFIX + station.stationId(),
                recipe, 100, storage, line, runtime.world().getAuthoritativeWorldTick());
        else runtime.manufacturingQueue().startRefining(Stage18ManufacturingWorkQueue.REFINING_PREFIX + station.stationId(),
                recipe, 100, storage, line, runtime.world().getAuthoritativeWorldTick());
        var pending = campaign.captureState();
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistentState.compose(pending.stage21Runtime(),
                pending.smallCraft(), pending.hangars(), pending.flightDeck(), pending.operations(), before.playerState()));
        byte[] bytes = Stage228GeneratedCampaignPersistenceCodec.encode(pending);
        assertEquals(pending, Stage228GeneratedCampaignPersistenceCodec.decode(bytes));
        int historicalVersion = components ? 19 : 18;
        java.nio.ByteBuffer.wrap(bytes).putInt(4, historicalVersion).putInt(8, historicalVersion);
        int length = ((bytes[12] & 0xff) << 8) | (bytes[13] & 0xff);
        bytes[14 + length - 2] = '1';
        bytes[14 + length - 1] = (byte) (components ? '9' : '8');
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistenceCodec.decode(bytes))
                .getMessage().contains(components ? "native campaign v20" : "native campaign v19"));
    }

    static java.util.stream.Stream<String> supportedSections() {
        return java.util.stream.Stream.of(Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID,
                Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID,
                "module.industrial_union_cargo_section_v1");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("supportedSections")
    void realCampaignRetainsReservedCustodyAndRejectsMissingPersonalStationOwner(String module) {
        var campaign = FoundedCampaignFixture.restore();
        var products = Stage22CivilianMiningProductionPath.loadProducts();
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var catalog = Stage22CivilianMiningProductionPath.loadManufacturing();
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
        var presentationBefore = restored.captureState();
        var rows = GeneratedCampaignManufacturingUi.rows(restored);
        assertEquals(10, rows.size());
        var smallCraftEngineering = com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        for (var product : Stage23SmallCraftProductionCatalogs.moduleIds()) {
            var row = rows.stream().filter(r -> r.selection().stableId().equals(
                    "pilot-manufacturing|" + station.stationId() + '|' + product)).findFirst().orElseThrow();
            assertEquals(smallCraftEngineering.findModule(product).displayName(), row.name());
        }
        String orderId = restored.coordinator().runtime().manufacturingQueue().capture().stream()
                .filter(order -> order.stationId().equals(station.stationId()) && order.operationId().equals(module))
                .findFirst().orElseThrow().orderId();
        assertTrue(rows.stream().anyMatch(row -> row.selection().stableId().equals(
                "pilot-manufacturing-cancel|" + station.stationId() + '|' + orderId)));
        assertTrue(rows.stream().anyMatch(row -> row.name().equals("Грузовая секция для грузового корабля")));
        assertEquals(presentationBefore, restored.captureState(), "Manufacturing presentation cannot grant equipment or materials");
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
