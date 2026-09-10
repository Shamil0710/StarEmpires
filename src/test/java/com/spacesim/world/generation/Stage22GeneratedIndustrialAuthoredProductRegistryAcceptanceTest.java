package com.spacesim.world.generation;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistence;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage20GeneratedIndustrialRuntimeBridge;
import com.spacesim.persistence.Stage20IndustrialEntityMaterializer;
import com.spacesim.persistence.Stage20MaterializationPersistence;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.simulation.Stage20MaterializationService;
import com.spacesim.world.Stage20DiscoveryKnowledgeState;
import com.spacesim.world.Stage20SpecialLocationGenerator;
import com.spacesim.world.generation.Stage20OperationalIndustrialSpecializationProductionIntegrationTest.CadenceFixture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 generated-industry composition regression for later authored manufactured products.
 *
 * <p>Stage 20 remains unaware of Stage-22 package classes. The accepted generated industrial
 * materializer instead receives the ordinary Stage-18 manufactured-product registry explicitly.
 * The tests persist one exact Stage-22 authored module inside a real generated station snapshot,
 * prove the baseline vocabulary fails closed, and prove the composed vocabulary restores and
 * re-captures the same canonical physical inventory without creating a second storage authority.</p>
 */
class Stage22GeneratedIndustrialAuthoredProductRegistryAcceptanceTest {
    private static final String AUTHORED_PRODUCT = "module.empire_cargo_secure_v1";
    private static volatile CadenceFixture sharedFixture;

    @Test
    void explicitStage18ProductCompositionPreservesAuthoredProductAcrossGeneratedRestore() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState initial = savedState(
                fixture, Stage18IndustrialState.empty(0L));
        Stage18ManufacturingProductRegistry products = composedProducts();
        var product = requireAuthoredProduct(products);

        var bootstrap = Stage20IndustrialEntityMaterializer.materializeBootstrap(
                initial, fixture.specialization(), products);
        Stage18IndustrialState captured = bootstrap.captureIndustrialState(initial.industrialState());
        var target = bootstrap.stations().stream()
                .filter(station -> station.storage().remainingCapacityKg(product.storageClassId())
                        >= product.unitMassKg())
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "accepted generated industrial fixture has no physical capacity for authored product"));
        Stage18IndustrialState extended = addAuthoredProduct(captured, target.stationId());
        Stage20GeneratedCampaignPersistentState persisted = replaceIndustry(initial, extended);

        assertThrows(IllegalArgumentException.class,
                () -> Stage20IndustrialEntityMaterializer.restore(persisted),
                "baseline Stage-18 product vocabulary must fail closed on later authored IDs");

        var restored = Stage20IndustrialEntityMaterializer.restore(persisted, products);
        assertEquals(1, restored.station(target.stationId()).storage().productCount(AUTHORED_PRODUCT));
        assertEquals(extended, restored.captureIndustrialState(extended),
                "generated save/load must retain the exact authored product in canonical Stage-18 storage");
        assertTrue(restored.station(target.stationId()).stationNode().stationId().equals(target.stationId()));
    }

    @Test
    void composedGeneratedIndustrialBridgePreservesAuthoredProductAndSourceOutpostStateTogether() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState initial = savedState(
                fixture, Stage18IndustrialState.empty(0L));
        Stage18ManufacturingProductRegistry products = composedProducts();
        var product = requireAuthoredProduct(products);

        var runtime = Stage20GeneratedIndustrialRuntimeBridge.materializeBootstrap(
                initial, fixture.specialization(), products);
        var target = runtime.industrial().stations().stream()
                .filter(station -> station.storage().remainingCapacityKg(product.storageClassId())
                        >= product.unitMassKg())
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "accepted generated industrial fixture has no physical capacity for authored product"));
        Stage20GeneratedCampaignPersistentState captured = runtime.captureCampaignState(initial);
        Stage18IndustrialState extendedIndustry = addAuthoredProduct(
                captured.industrialState(), target.stationId());
        Stage20GeneratedCampaignPersistentState persisted = replaceIndustry(captured, extendedIndustry);

        assertThrows(IllegalArgumentException.class,
                () -> Stage20GeneratedIndustrialRuntimeBridge.restore(persisted),
                "baseline generated-industry bridge must fail closed on later authored IDs");

        var restored = Stage20GeneratedIndustrialRuntimeBridge.restore(persisted, products);
        assertEquals(1,
                restored.industrial().station(target.stationId()).storage().productCount(AUTHORED_PRODUCT));
        Stage20GeneratedCampaignPersistentState recaptured = restored.captureCampaignState(persisted);
        assertEquals(persisted.industrialState(), recaptured.industrialState(),
                "composed bridge must preserve authored product plus source-outpost industrial state exactly");
        assertEquals(
                runtime.sourceOutposts().outposts().stream().map(value -> value.stationId()).toList(),
                restored.sourceOutposts().outposts().stream().map(value -> value.stationId()).toList());
    }

    private static Stage18ManufacturingProductRegistry composedProducts() {
        return Stage18ManufacturingProductRegistry.loadDefault()
                .withEngineeringCatalog(
                        Stage22CorePairEngineeringCatalogLoader.loadDefault(),
                        Provenance.STAGE22_AUTHORED);
    }

    private static Stage18ManufacturingProductRegistry.ProductDefinition requireAuthoredProduct(
            Stage18ManufacturingProductRegistry products) {
        var product = products.findProduct(AUTHORED_PRODUCT);
        if (product == null) {
            throw new AssertionError("core-pair authored product is absent from composed Stage-18 registry");
        }
        return product;
    }

    private static Stage18IndustrialState addAuthoredProduct(
            Stage18IndustrialState base,
            String stationId) {
        ArrayList<StationStorageSnapshot> storage = new ArrayList<>();
        boolean found = false;
        for (StationStorageSnapshot snapshot : base.stationStorages()) {
            if (!snapshot.stationId().equals(stationId)) {
                storage.add(snapshot);
                continue;
            }
            found = true;
            TreeMap<String, Integer> counts = new TreeMap<>(snapshot.productCountById());
            counts.merge(AUTHORED_PRODUCT, 1, Math::addExact);
            storage.add(new StationStorageSnapshot(
                    snapshot.stationId(),
                    snapshot.capacityByStorageClassKg(),
                    snapshot.commodityMassByIdKg(),
                    counts));
        }
        if (!found) {
            throw new AssertionError("target generated station missing from persisted industrial state");
        }
        return new Stage18IndustrialState(
                base.schemaVersion(),
                base.contentFingerprint(),
                base.simulationTick(),
                base.sources(),
                storage,
                base.facilities(),
                base.yards(),
                base.constructionOrders(),
                base.processOrders());
    }

    private static Stage20GeneratedCampaignPersistentState savedState(
            CadenceFixture fixture,
            Stage18IndustrialState industry) {
        SimulationSession session = SimulationSession.createDemo(fixture.resolved().rootSeed());
        var physical = Stage20MaterializationPersistence.capture(
                session,
                Stage20MaterializationService.forSession(session));
        return Stage20GeneratedCampaignPersistence.capture(
                fixture.resolved(),
                Stage20SpecialLocationGenerator.generateCurrent(fixture.resolved()),
                fixture.specialization(),
                physical,
                industry,
                List.of(new Stage20DiscoveryKnowledgeState(
                        "faction.m22_6.authored-product-registry",
                        List.of())));
    }

    private static Stage20GeneratedCampaignPersistentState replaceIndustry(
            Stage20GeneratedCampaignPersistentState base,
            Stage18IndustrialState industry) {
        return new Stage20GeneratedCampaignPersistentState(
                base.schemaVersion(),
                base.generationIdentity(),
                base.materializedWorld(),
                base.materializationState(),
                industry,
                base.discoveryState(),
                base.openRuntimeBoundaries());
    }

    private static synchronized CadenceFixture fixture() {
        if (sharedFixture == null) {
            sharedFixture = Stage20OperationalIndustrialSpecializationProductionIntegrationTest.cadenceFixture();
        }
        return sharedFixture;
    }
}
