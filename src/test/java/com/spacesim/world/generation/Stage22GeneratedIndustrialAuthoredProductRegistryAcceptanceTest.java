package com.spacesim.world.generation;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistence;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState;
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
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 generated-industry composition regression for later authored manufactured products.
 *
 * <p>Stage 20 remains unaware of Stage-22 package classes. The accepted generated industrial
 * materializer instead receives the ordinary Stage-18 manufactured-product registry explicitly.
 * The test persists one exact Stage-22 authored module inside a real generated station snapshot,
 * proves the baseline vocabulary fails closed, and proves the composed vocabulary restores and
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
        Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault()
                .withEngineeringCatalog(
                        Stage22CorePairEngineeringCatalogLoader.loadDefault(),
                        Provenance.STAGE22_AUTHORED);

        var product = products.findProduct(AUTHORED_PRODUCT);
        if (product == null) {
            throw new AssertionError("core-pair authored product is absent from composed Stage-18 registry");
        }
        var bootstrap = Stage20IndustrialEntityMaterializer.materializeBootstrap(
                initial, fixture.specialization(), products);
        Stage18IndustrialState captured = bootstrap.captureIndustrialState(initial.industrialState());
        var target = bootstrap.stations().stream()
                .filter(station -> station.storage().remainingCapacityKg(product.storageClassId())
                        >= product.unitMassKg())
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "accepted generated industrial fixture has no physical capacity for authored product"));

        ArrayList<StationStorageSnapshot> storage = new ArrayList<>();
        for (StationStorageSnapshot snapshot : captured.stationStorages()) {
            if (!snapshot.stationId().equals(target.stationId())) {
                storage.add(snapshot);
                continue;
            }
            TreeMap<String, Integer> counts = new TreeMap<>(snapshot.productCountById());
            counts.merge(AUTHORED_PRODUCT, 1, Math::addExact);
            storage.add(new StationStorageSnapshot(
                    snapshot.stationId(),
                    snapshot.capacityByStorageClassKg(),
                    snapshot.commodityMassByIdKg(),
                    counts));
        }
        Stage18IndustrialState extended = new Stage18IndustrialState(
                captured.schemaVersion(),
                captured.contentFingerprint(),
                captured.simulationTick(),
                captured.sources(),
                storage,
                captured.facilities(),
                captured.yards(),
                captured.constructionOrders(),
                captured.processOrders());
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
