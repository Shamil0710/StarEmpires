package com.spacesim.campaign;

import com.spacesim.content.*;
import com.spacesim.economy.*;
import com.spacesim.persistence.*;
import com.spacesim.ui.GeneratedCampaignAmmunitionManufacturingUi;
import com.spacesim.ship.ShipAmmunitionSupplyService;
import com.spacesim.components.EngineeringComponent;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Finite material and construction-work fixtures; does not claim ordinary acquisition. */
@Tag("slow")
class GeneratedCampaignAmmunitionManufacturingTest {
    @ParameterizedTest
    @ValueSource(strings = {Stage23AmmunitionProduction.EMPIRE, Stage23AmmunitionProduction.UNION})
    void actualBatchReservesWholeBillAndContinuesExactlyAfterPartialNativeReload(String product) {
        var c = fixture(true); String station = station(c); supply(c, product, 3);
        var storage = c.coordinator().runtime().infrastructure().endpoint(station).storage();
        var available = storage.snapshotCommodityMassByIdKg();
        var before = c.captureState();
        var preview = c.previewPilotAction("START_MANUFACTURING", station, product, 3);
        assertTrue(preview.allowed()); assertEquals(before, c.captureState());
        var rows = GeneratedCampaignAmmunitionManufacturingUi.rows(c, 3);
        assertEquals(2, rows.size());
        var row = rows.stream().filter(r -> r.selection().stableId().endsWith('|' + product)).findFirst().orElseThrow();
        assertEquals("3 шт.", value(row.sections().get(0), "Партия"));
        double mass = Stage22CivilianMiningProductionPath.loadProducts().findProduct(product).unitMassKg() * 3;
        assertEquals(String.format(Locale.ROOT, "%.3f кг", mass), value(row.sections().get(0), "Общая масса"));
        assertEquals(before, c.captureState());
        c.submitPilotAction(preview);
        assertThrows(IllegalStateException.class, () -> c.submitPilotAction(preview));
        var order = orders(c).get(0); assertEquals(3, order.requestedUnits());
        order.reservedCommodityMassByIdKg().forEach((commodity, kg) -> assertEquals(
                available.getOrDefault(commodity, 0d) - kg, storage.commodityMassKg(commodity), 1e-7));
        assertEquals(0, storage.productCount(product));
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds());
        assertTrue(orders(c).get(0).completedFraction() > 0 && orders(c).get(0).completedFraction() < 1);
        var pending = c.captureState(); var restored = roundTrip(c);
        assertEquals(pending, restored.captureState());
        var pendingRows = GeneratedCampaignAmmunitionManufacturingUi.rows(restored, 99);
        var pendingRow = pendingRows.stream().filter(r -> r.name().equals(Stage23AmmunitionProduction.displayName(product))).findFirst().orElseThrow();
        assertEquals("3 шт.", value(pendingRow.sections().get(0), "Партия"), "Existing order retains its original count");
        int ticks = 0;
        while (!orders(c).isEmpty() && ticks++ < 10000) {
            c.advanceFrame(c.coordinator().session().fixedStepSeconds());
            restored.advanceFrame(restored.coordinator().session().fixedStepSeconds());
        }
        assertTrue(orders(c).isEmpty(), "Finite authored work must finish the three-round batch");
        assertEquals(c.captureState(), restored.captureState());
        assertEquals(3, storage.productCount(product));
        assertEquals(1, c.playerJournal().entries().stream().filter(e -> e.action().equals("MANUFACTURING_COMPLETED") && e.subject().equals(product)).count());
        if (product.equals(Stage23AmmunitionProduction.EMPIRE)) {
            var world = c.coordinator().runtime().world();
            var fleet = world.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
            var ship = world.findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId()).getComponent(EngineeringComponent.class);
            var feed = new ShipAmmunitionSupplyService().feeds(ship).get(0);
            c.submitPilotAction(c.previewPilotAction("LOAD_AMMUNITION", station, feed.mountId(), 1));
            assertEquals(2, storage.productCount(product)); assertEquals(1, ship.runtimeState.consumables().ammunitionCount());
            assertEquals(c.captureState(), roundTrip(c).captureState());
        }
    }

    @Test void simultaneousAmmoOrdersShareActualEnergyAndCancellationRefundsReservedMaterialsOnce() {
        var c = fixture(true); var runtime = c.coordinator().runtime(); String station = station(c);
        for (String product : Stage23AmmunitionProduction.products()) supply(c, product, 2);
        var storage = runtime.infrastructure().endpoint(station).storage(); var before = storage.snapshotCommodityMassByIdKg();
        for (String product : Stage23AmmunitionProduction.products())
            c.submitPilotAction(c.previewPilotAction("START_MANUFACTURING", station, product, 2));
        var profile = profile(Stage23AmmunitionProduction.EMPIRE);
        var line = runtime.industry().industrial().station(station).facilityCapabilities().stream()
                .filter(f -> f.capabilityTags().containsAll(profile.requiredCapabilityTags()) && f.effectiveProcessPowerW() > 0).findFirst().orElseThrow();
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds()); c.coordinator().setPaused(true);
        double energy = orders(c).stream().mapToDouble(o -> o.completedFraction() * o.requestedUnits()
                * Stage22CivilianMiningProductionPath.loadProducts().findProduct(o.operationId()).unitMassKg() * profile(o.operationId()).energyJPerOutputKg()).sum();
        assertTrue(energy > 0 && energy <= line.effectiveProcessPowerW() * c.coordinator().session().fixedStepSeconds() + 1e-5);
        c = roundTrip(c); runtime = c.coordinator().runtime(); storage = runtime.infrastructure().endpoint(station).storage();
        for (var order : List.copyOf(orders(c))) c.submitPilotAction(c.previewPilotAction("CANCEL_MANUFACTURING", station, order.orderId(), 0));
        assertTrue(orders(c).isEmpty());
        for (var entry : before.entrySet()) assertEquals(entry.getValue(), storage.commodityMassKg(entry.getKey()), 1e-7);
        assertEquals(0, storage.productCount(Stage23AmmunitionProduction.EMPIRE));
        var saved = c.captureState();
        assertFalse(c.previewPilotAction("CANCEL_MANUFACTURING", station, Stage18ManufacturingWorkQueue.PREFIX + station + ':' + Stage23AmmunitionProduction.EMPIRE, 0).allowed());
        assertEquals(saved, c.captureState());
    }

    @Test void missingLineMissingInputsAndWrongStationCannotStartOrGrantProduction() {
        var c = fixture(false); String station = station(c); supply(c, Stage23AmmunitionProduction.EMPIRE, 3);
        var before = c.captureState();
        assertFalse(c.previewPilotAction("START_MANUFACTURING", station, Stage23AmmunitionProduction.EMPIRE, 3).allowed());
        assertTrue(GeneratedCampaignAmmunitionManufacturingUi.rows(c, 3).stream().flatMap(r -> r.sections().stream())
                .flatMap(s -> s.lines().stream()).anyMatch(l -> l.value().contains("Нет совместимой действующей линии")));
        assertEquals(before, c.captureState());
        c = fixture(true); before = c.captureState(); station = station(c);
        assertFalse(c.previewPilotAction("START_MANUFACTURING", station, Stage23AmmunitionProduction.EMPIRE, Integer.MAX_VALUE).allowed());
        assertFalse(c.previewPilotAction("START_MANUFACTURING", "missing", Stage23AmmunitionProduction.EMPIRE, 2).allowed());
        assertFalse(c.previewPilotAction("START_MANUFACTURING", station, Stage23AmmunitionProduction.EMPIRE, 0).allowed());
        assertEquals(before, c.captureState());
    }

    static Stage228CampaignAuthority fixture(boolean line) {
        var c = GeneratedCampaignPersonalAmmunitionTest.fixture(0);
        if (!line) return c;
        var runtime = c.coordinator().runtime(); String station = station(c);
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var construction = new Stage18FacilityConstructionRuntime(Stage18FacilityConstructionCatalogLoader.loadDefault(), Stage18FacilityCatalogLoader.loadDefault(), ontology);
        String id = Stage18FacilityConstructionWorkQueue.PREFIX + "ammunition-fixture";
        var template = construction.createOrder(id, "fixture.ordnance", "facility.fabrication.ordnance", station, "location.orbital_station");
        supplyMaterials(c, template.requiredMassByCommodityKg());
        runtime.constructionQueue().start(id, "fixture.ordnance", "facility.fabrication.ordnance", "location.orbital_station",
                runtime.infrastructure().endpoint(station).storage(), runtime.world().getAuthoritativeWorldTick());
        float step = c.coordinator().session().fixedStepSeconds();
        // Explicit finite construction-work fixture. The new installation still arrives disabled.
        var capability = new Stage18FacilityConstructionRuntime.ConstructionCapability("fixture.construction",
                Set.of("capability.fabrication.heavy", "capability.fabrication.electrical", "capability.fabrication.assembly"), template.requiredWorkSeconds() / step);
        var completed = runtime.constructionQueue().advance(runtime.world().getAuthoritativeWorldTick() + 1,
                s -> runtime.infrastructure().endpoint(s).storage(), o -> capability.openInterval(step));
        assertEquals(1, completed.size());
        c.coordinator().setPaused(false); c.advanceFrame(step); c.coordinator().setPaused(true);
        runtime.adoptCompletedFacilityConstruction(completed);
        runtime.allocateFacilityResources(station, "fixture.ordnance");
        assertTrue(c.hasManufacturingLine(station, Stage23AmmunitionProduction.EMPIRE));
        assertTrue(c.hasManufacturingLine(station, Stage23AmmunitionProduction.UNION));
        return c;
    }

    static void supply(Stage228CampaignAuthority c, String product, int units) {
        double mass = Stage22CivilianMiningProductionPath.loadProducts().findProduct(product).unitMassKg() * units;
        var inputs = new TreeMap<String, Double>(); profile(product).inputs().forEach(i -> inputs.put(i.commodityId(), mass * i.fractionOfOutputMass()));
        supplyMaterials(c, inputs);
    }
    private static void supplyMaterials(Stage228CampaignAuthority c, Map<String, Double> inputs) {
        var ontology = Stage18ResourceOntologyLoader.loadDefault(); var products = Stage22CivilianMiningProductionPath.loadProducts();
        var capacities = new TreeMap<String, Double>(); inputs.forEach((commodity, mass) -> capacities.merge(ontology.findCommodity(commodity).storageClassId(), mass, Double::sum));
        double total = inputs.values().stream().mapToDouble(Double::doubleValue).sum();
        var donor = new Stage18StationStorage(ontology, products, "fixture.finite-materials", capacities, inputs, Map.of());
        var handling = new Stage18LogisticsRuntime.HandlingCapability("fixture.materials", capacities.keySet(), total, total);
        var storage = c.coordinator().runtime().infrastructure().endpoint(station(c)).storage();
        for (var input : inputs.entrySet()) assertTrue(new Stage18LogisticsRuntime(ontology, products).transferCommodity(
                donor, storage, input.getKey(), input.getValue(), handling, handling.openInterval(1)).transferred());
        assertTrue(donor.snapshotCommodityMassByIdKg().isEmpty());
    }
    private static Stage18ManufacturingCatalog.ProductProfileDefinition profile(String product) {
        var catalog = Stage22CivilianMiningProductionPath.loadManufacturing(); return catalog.findProductProfile(catalog.findProductBinding(product).profileId());
    }
    static String station(Stage228CampaignAuthority c) { return c.dockedAmmunitionStationId().orElseThrow(); }
    private static List<Stage18IndustrialState.ProcessOrderSnapshot> orders(Stage228CampaignAuthority c) { return c.coordinator().runtime().manufacturingQueue().capture(); }
    private static Stage228CampaignAuthority roundTrip(Stage228CampaignAuthority c) {
        return Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(c.captureState())));
    }
    private static String value(com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection section, String label) {
        return section.lines().stream().filter(l -> l.label().equals(label)).findFirst().orElseThrow().value();
    }
}
