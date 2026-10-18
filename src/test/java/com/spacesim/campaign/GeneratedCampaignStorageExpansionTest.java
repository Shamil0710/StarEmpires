package com.spacesim.campaign;

import com.spacesim.content.*;
import com.spacesim.economy.*;
import com.spacesim.persistence.*;
import com.spacesim.player.*;
import com.spacesim.world.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignStorageExpansionTest {
    @Test void structuralProofRejectsWrongHostLocationDuplicatesAndAtomicMixedBatch() {
        var c = fixture(); var registry = c.coordinator().runtime().industry().industrial();
        var station = registry.station("industry.2");
        var construction = new Stage18FacilityConstructionRuntime(Stage23StationStorageCatalog.construction(),
                Stage23StationStorageCatalog.facilities(), Stage18ResourceOntologyLoader.loadDefault());
        var first = completed(construction, "first", station.stationId());
        var snapshot = station.storage().snapshot(); var references = station.stationNode().installedFacilities();
        var unfinished = construction.createOrder("unfinished", "unfinished.installed", Stage23StationStorageCatalog.FACILITY_ID,
                station.stationId(), "location.orbital_station");
        assertThrows(IllegalArgumentException.class, () -> registry.adoptCompletedConstruction(List.of(first, unfinished), construction));
        assertEquals(snapshot, station.storage().snapshot()); assertEquals(references, station.stationNode().installedFacilities());
        assertThrows(IllegalArgumentException.class, () -> registry.adoptCompletedConstruction(List.of(first, completed(construction, "second", station.stationId())), construction));
        assertEquals(snapshot, station.storage().snapshot());
        var host = Stage18StationIndustrialNode.instantiate("wrong.host", "location.orbital_station",
                Stage18StationInfrastructureCatalogLoader.loadDefault().findArchetype("station.infrastructure.high_tech_hub"),
                Stage18ResourceOntologyLoader.loadDefault(), Stage22CivilianMiningProductionPath.loadProducts());
        assertThrows(IllegalArgumentException.class, () -> host.withCompletedConstruction(completed(construction, "wrong", host.stationId()), construction));
        var wrongLocation = new Stage18FacilityConstructionRuntime.ConstructionOrderSnapshot(first.orderId(), first.facilityInstanceId(),
                first.facilityDefinitionId(), first.stationId(), "location.surface_station", first.requiredMassByCommodityKg(),
                first.deliveredMassByCommodityKg(), first.requiredWorkSeconds(), first.completedWorkSeconds(), first.status());
        assertThrows(IllegalArgumentException.class, () -> registry.adoptCompletedConstruction(List.of(wrongLocation), construction));
        assertEquals(snapshot, station.storage().snapshot());
    }

    private static Stage18FacilityConstructionRuntime.ConstructionOrderSnapshot completed(Stage18FacilityConstructionRuntime runtime, String id, String station) {
        var p = runtime.createOrder(id, id + ".installed", Stage23StationStorageCatalog.FACILITY_ID, station, "location.orbital_station");
        return new Stage18FacilityConstructionRuntime.ConstructionOrderSnapshot(p.orderId(), p.facilityInstanceId(), p.facilityDefinitionId(),
                p.stationId(), p.locationTag(), p.requiredMassByCommodityKg(), p.requiredMassByCommodityKg(), p.requiredWorkSeconds(),
                p.requiredWorkSeconds(), Stage18FacilityConstructionRuntime.OrderStatus.COMPLETE);
    }

    @Test void paidTankKeepsOneStoreAndFiniteHandlingAcrossPartialNativeRestore() throws Exception {
        var c = fixture(); var r = c.coordinator().runtime(); String id = "industry.2";
        String design = Stage23StationStorageCatalog.FACILITY_ID;
        var store = r.infrastructure().endpoint(id).storage(); var originalHandling = r.infrastructure().endpoint(id).handlingCapability();
        assertSame(store, r.industry().industrial().station(id).stationNode().storage());
        assertFalse(store.snapshotCapacityByStorageClassKg().containsKey("storage.liquid_tank"));
        var before = c.captureState(); assertFalse(c.previewPilotAction("START_FACILITY_CONSTRUCTION", id, design, 1).allowed());
        assertEquals(before, c.captureState());
        supply(store, Stage23StationStorageCatalog.construction().requiredMassByCommodityKg(design));
        var stocked = c.captureState(); var start = c.previewPilotAction("START_FACILITY_CONSTRUCTION", id, design, 1);
        assertTrue(start.allowed()); assertEquals(stocked, c.captureState()); c.submitPilotAction(start);
        var pending = c.captureState();
        assertFalse(c.previewPilotAction("START_FACILITY_CONSTRUCTION", id, design, 1).allowed());
        assertEquals(pending, c.captureState());
        var cancel = roundTrip(c); var order = r.constructionQueue().capture().get(0);
        cancel.submitPilotAction(cancel.previewPilotAction("CANCEL_FACILITY_CONSTRUCTION", id, order.orderId(), 1));
        assertEquals(store.snapshotCapacityByStorageClassKg(), cancel.coordinator().runtime().infrastructure().endpoint(id).storage().snapshotCapacityByStorageClassKg());
        for (var input : Stage23StationStorageCatalog.construction().requiredMassByCommodityKg(design).entrySet())
            assertTrue(cancel.coordinator().runtime().infrastructure().endpoint(id).storage().commodityMassKg(input.getKey()) >= input.getValue());
        step(c); var partial = c.captureState();
        assertTrue(r.constructionQueue().capture().get(0).completedWorkSeconds() > 0);
        assertEquals(store.snapshotCapacityByStorageClassKg(), before.stage21Runtime().stage21HRuntime().stage21GRuntime()
                .stage21FRuntime().stage21ERuntime().stage21DRuntime().stage21CRuntime().stage21BRuntime().stage21ARuntime()
                .stage20Runtime().campaign().industrialState().stationStorages().stream().filter(s -> s.stationId().equals(id)).findFirst().orElseThrow().capacityByStorageClassKg());
        c = roundTrip(c); r = c.coordinator().runtime(); store = r.infrastructure().endpoint(id).storage();
        assertSame(store, r.industry().industrial().station(id).stationNode().storage());
        assertEquals(partial, c.captureState());
        // Explicit historical-work fixture isolates completion admission; no ordinary full-bill journey is claimed.
        var currentOrder = r.constructionQueue().capture().get(0);
        var budget = new Stage18FacilityConstructionRuntime.ConstructionCapability("fixture.completed-work",
                Set.of("capability.fabrication.heavy", "capability.fabrication.assembly"), currentOrder.remainingWorkSeconds()).openInterval(1);
        var runtime = r;
        var completed = r.constructionQueue().advance(r.world().getAuthoritativeWorldTick() + 1,
                station -> runtime.infrastructure().endpoint(station).storage(), ignored -> budget);
        assertThrows(IllegalStateException.class, () -> runtime.adoptCompletedFacilityConstruction(completed));
        step(c); r.adoptCompletedFacilityConstruction(completed);
        var station = r.industry().industrial().station(id); var endpoint = r.infrastructure().endpoint(id);
        assertSame(store, station.storage()); assertSame(store, station.stationNode().storage()); assertSame(store, endpoint.storage());
        assertEquals(Stage23StationStorageCatalog.CAPACITY_KG, store.snapshotCapacityByStorageClassKg().get("storage.liquid_tank"));
        assertEquals(originalHandling.massRateKgPerSecond(), endpoint.handlingCapability().massRateKgPerSecond());
        assertEquals(originalHandling.maxUnitMassKg(), endpoint.handlingCapability().maxUnitMassKg());
        assertTrue(endpoint.handlingCapability().supportedStorageClassIds().contains("storage.liquid_tank"));
        var built = c.captureState(); r.adoptCompletedFacilityConstruction(completed); assertEquals(built, c.captureState());
        assertFalse(c.previewPilotAction("ALLOCATE_FACILITY_RESOURCES", id, currentOrder.facilityInstanceId(), 1).allowed());
        assertFalse(c.previewPilotAction("START_FACILITY_CONSTRUCTION", id, design, 1).allowed());
        assertExact(c);
        // Existing stock and the ordinary physical handler admit real liquid mass only after completion.
        String liquid = Stage18ResourceOntologyLoader.loadDefault().getCommodities().stream()
                .filter(v -> v.storageClassId().equals("storage.liquid_tank")).findFirst().orElseThrow().id();
        var source = new Stage18StationStorage(Stage18ResourceOntologyLoader.loadDefault(), Stage22CivilianMiningProductionPath.loadProducts(),
                "fixture.liquid", Map.of("storage.liquid_tank", 100d), Map.of(liquid, 100d), Map.of());
        var logistics = new Stage18LogisticsRuntime(Stage18ResourceOntologyLoader.loadDefault(), Stage22CivilianMiningProductionPath.loadProducts());
        var interval = endpoint.handlingCapability().openInterval(50d / endpoint.handlingCapability().massRateKgPerSecond());
        assertTrue(logistics.transferCommodity(source, store, liquid, 50d, endpoint.handlingCapability(), interval).transferred());
        assertFalse(logistics.transferCommodity(source, store, liquid, 1d, endpoint.handlingCapability(), interval).transferred());
        assertEquals(50d, store.commodityMassKg(liquid)); assertEquals(50d, source.commodityMassKg(liquid)); assertExact(c);
        var encoded = Stage228GeneratedCampaignPersistenceCodec.encode(c.captureState());
        java.nio.ByteBuffer.wrap(encoded).putInt(4, 27).putInt(8, 27); encoded[14 + Short.toUnsignedInt(java.nio.ByteBuffer.wrap(encoded).getShort(12)) - 1] = '7';
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistenceCodec.decode(encoded));
        var campaign = r.captureCampaignState(); var industrial = campaign.industrialState();
        assertThrows(IllegalArgumentException.class, () -> restoreIndustry(campaign, industrial.facilities().stream()
                .filter(f -> !f.state().definitionId().equals(design)).toList(), industrial.constructionOrders(), industrial.stationStorages()));
        assertThrows(IllegalArgumentException.class, () -> restoreIndustry(campaign, industrial.facilities(), List.of(), industrial.stationStorages()));
    }

    @Test void genuineV27RetainsBaseCapacityAndCannotReceiveAnExpansionOnLoad() throws Exception {
        var c = fixture(); var baseline = c.captureState(); var bytes = new ByteArrayOutputStream();
        try (var in = new DataInputStream(new ByteArrayInputStream(Stage228GeneratedCampaignPersistenceCodec.encode(baseline)));
                var out = new DataOutputStream(bytes)) {
            out.writeInt(in.readInt()); in.readInt(); in.readInt(); in.readUTF();
            out.writeInt(27); out.writeInt(27); out.writeUTF("m22.8.generated-campaign.v27");
            HistoricalCampaignContentFixture.copyPayloads(in, out, 19); HistoricalCampaignContentFixture.skipEmptyBuild(in); HistoricalCampaignContentFixture.skipEmptyObservations(in); assertEquals(-1, in.read());
        }
        var saved = Stage228GeneratedCampaignPersistenceCodec.decode(bytes.toByteArray()); assertEquals(baseline, saved);
        assertEquals(saved, Stage228CampaignAuthority.restore(saved).captureState());
        var store = c.coordinator().runtime().infrastructure().endpoint("industry.2").storage();
        var expanded = new TreeMap<>(store.snapshotCapacityByStorageClassKg()); expanded.put("storage.liquid_tank", 1d);
        store.expandProvedCapacity(expanded);
        assertThrows(IllegalArgumentException.class, () -> roundTrip(c));
    }

    @Test void chemicalBatchUsesPaidLiquidInterfaceAndConservedExistingOperatingAllocations() {
        var c = fixture(); String stationId = "industry.2";
        finishWithHistoricalWorkFixture(c, Stage23StationStorageCatalog.FACILITY_ID);
        String plant = finishWithHistoricalWorkFixture(c, Stage23BootstrapMaterialsCatalog.FACILITY_ID);
        var r = c.coordinator().runtime();
        double[] resources = totals(r.industry().industrial().station(stationId));
        r.allocateFacilityResources(stationId, plant);
        assertArrayEquals(resources, totals(r.industry().industrial().station(stationId)), 1e-8);
        assertTrue(c.hasRefiningLine(stationId, "refining.industrial_chemicals"));
        var store = r.infrastructure().endpoint(stationId).storage();
        // Explicit finite feedstock fixture; the whole ordinary mining/supply journey remains separate.
        supply(store, Map.of("commodity.feedstock.carbonaceous_feedstock", 55d, "commodity.feedstock.volatile_feedstock", 45d));
        double initial = store.commodityMassKg("commodity.material.industrial_chemicals");
        var before = c.captureState(); var preview = c.previewPilotAction("START_REFINING", stationId, "refining.industrial_chemicals", 100);
        assertTrue(preview.allowed()); assertEquals(before, c.captureState()); c.submitPilotAction(preview); step(c);
        assertTrue(r.manufacturingQueue().capture().stream().anyMatch(o -> o.operationId().equals("refining.industrial_chemicals")));
        c = roundTrip(c); r = c.coordinator().runtime();
        assertSame(r.infrastructure().endpoint(stationId).storage(), r.industry().industrial().station(stationId).stationNode().storage());
        int ticks = 0;
        while (r.manufacturingQueue().capture().stream().anyMatch(o -> o.operationId().equals("refining.industrial_chemicals")) && ticks++ < 10000) step(c);
        assertTrue(ticks < 10000, "Finite chemical work must complete using common world intervals");
        assertEquals(initial + 66d, r.infrastructure().endpoint(stationId).storage().commodityMassKg("commodity.material.industrial_chemicals"), 1e-8);
        assertArrayEquals(resources, totals(r.industry().industrial().station(stationId)), 1e-8); assertExact(c);
    }

    private static String finishWithHistoricalWorkFixture(Stage228CampaignAuthority c, String definition) {
        var r = c.coordinator().runtime(); var store = r.infrastructure().endpoint("industry.2").storage();
        supply(store, Stage23StationStorageCatalog.construction().requiredMassByCommodityKg(definition));
        var start = c.previewPilotAction("START_FACILITY_CONSTRUCTION", "industry.2", definition, 1);
        assertTrue(start.allowed()); c.submitPilotAction(start); step(c);
        var order = r.constructionQueue().capture().stream().filter(o -> o.facilityDefinitionId().equals(definition)).findFirst().orElseThrow();
        var budget = new Stage18FacilityConstructionRuntime.ConstructionCapability("fixture.historical-work",
                Set.of("capability.fabrication.heavy", "capability.fabrication.assembly"), order.remainingWorkSeconds()).openInterval(1);
        var completed = r.constructionQueue().advance(r.world().getAuthoritativeWorldTick() + 1,
                id -> r.infrastructure().endpoint(id).storage(), ignored -> budget);
        step(c); r.adoptCompletedFacilityConstruction(completed); return order.facilityInstanceId();
    }

    private static double[] totals(Stage20IndustrialEntityMaterializer.MaterializedIndustrialStation station) {
        return new double[]{station.facilities().stream().mapToDouble(f -> f.allocatedProcessPowerW()).sum(),
                station.facilities().stream().mapToDouble(f -> f.availableHeatRejectionW()).sum(),
                station.facilities().stream().mapToDouble(f -> f.availableLaborUnits()).sum(),
                station.facilities().stream().mapToDouble(f -> f.availableMaintenanceWorkRate()).sum()};
    }

    private static void restoreIndustry(Stage20GeneratedCampaignPersistentState campaign,
            List<Stage18IndustrialState.FacilityInstallationSnapshot> facilities,
            List<Stage18FacilityConstructionRuntime.ConstructionOrderSnapshot> orders,
            List<Stage18StationStorage.StationStorageSnapshot> stores) {
        var i = campaign.industrialState();
        var changed = new Stage18IndustrialState(i.schemaVersion(), i.contentFingerprint(), i.simulationTick(), i.sources(), stores,
                facilities, i.yards(), orders, i.processOrders());
        Stage20IndustrialEntityMaterializer.restore(new Stage20GeneratedCampaignPersistentState(campaign.schemaVersion(), campaign.generationIdentity(),
                campaign.materializedWorld(), campaign.materializationState(), changed, campaign.discoveryState(), campaign.openRuntimeBoundaries()),
                Stage22CivilianMiningProductionPath.loadProducts());
    }
    private static Stage228CampaignAuthority fixture() {
        var c = FoundedCampaignFixture.restore(); var s = c.captureState(); var p = s.playerState();
        var ref = c.pilotMarketReference("industry.2").orElseThrow(); var systems = new ArrayList<>(p.discoveredSystemIds());
        if (!systems.contains(ref.systemId())) systems.add(ref.systemId());
        var owner = new PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(), p.ownedFleetIds(), p.activeFleetId(), systems,
                p.discoveredObjects(), p.homeSystemId(), p.dockedAt(), p.fleetOrders(), p.threatIntel(), p.ownedConstructionProjectIds(),
                List.of(new OwnedStationRef(ref.systemId(), ref.entityId())));
        c = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistentState.compose(s.stage21Runtime(), s.smallCraft(), s.hangars(), s.flightDeck(), s.operations(), owner));
        var r = c.coordinator().runtime(); var f = r.world().findFleet(p.activeFleetId()).orElseThrow(); var endpoint = r.infrastructure().endpoint("industry.2");
        if (!f.systemId().equals(endpoint.systemId())) { r.world().beginFleetTransfer(f.id(), endpoint.systemId()); r.world().completeFleetTransfer(f.id(), 0, 0); f = r.world().findFleet(f.id()).orElseThrow(); }
        var local = r.arrival().materialization(f.systemId());
        if (local.physicalState(f.localEntityId()).isEmpty()) local.registerPhysicalState(f.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position()));
        local.updatePhysicalState(f.localEntityId(), LocalPhysicalKinematics.stationary(endpoint.position())); step(c);
        c.submitPilotAction(c.previewPilotAction("DOCK", "industry.2", "", 0)); return c;
    }
    private static void supply(Stage18StationStorage store, Map<String, Double> bill) {
        var ontology = Stage18ResourceOntologyLoader.loadDefault(); var capacities = new TreeMap<String, Double>();
        bill.forEach((id, mass) -> capacities.merge(ontology.findCommodity(id).storageClassId(), mass, Double::sum));
        var source = new Stage18StationStorage(ontology, Stage22CivilianMiningProductionPath.loadProducts(), "fixture.bill", capacities, bill, Map.of());
        var handler = new Stage18LogisticsRuntime.HandlingCapability("fixture.bill", capacities.keySet(), 1e9, 1e9);
        var logistics = new Stage18LogisticsRuntime(ontology, Stage22CivilianMiningProductionPath.loadProducts());
        bill.forEach((id, mass) -> assertTrue(logistics.transferCommodity(source, store, id, mass, handler, handler.openInterval(1)).transferred()));
    }
    private static void step(Stage228CampaignAuthority c) { c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds()); c.coordinator().setPaused(true); }
    private static Stage228CampaignAuthority roundTrip(Stage228CampaignAuthority c) { return Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(c.captureState()))); }
    private static void assertExact(Stage228CampaignAuthority c) { assertEquals(c.captureState(), roundTrip(c).captureState()); }
}
