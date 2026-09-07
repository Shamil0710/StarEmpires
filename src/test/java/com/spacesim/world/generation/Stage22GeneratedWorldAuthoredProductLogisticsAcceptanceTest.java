package com.spacesim.world.generation;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.economy.Stage18LogisticsRuntime.Status;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 L0/L4 regression for later-authored manufactured products in the complete playable
 * generated-world runtime.
 *
 * <p>The fixture starts through the production Stage-20.5 factory and captures an ordinary atomic
 * checkpoint. Test setup then places one exact Stage-22 authored module into a persisted canonical
 * station-storage snapshot. Legacy restore must fail closed because the baseline Stage-18 product
 * vocabulary does not know that ID. Explicit ordinary Stage-18 content composition must restore it,
 * move it only through {@code Stage18LogisticsRuntime.transferProduct}, and preserve the resulting
 * physical inventory over another full generated-world save/load.</p>
 */
class Stage22GeneratedWorldAuthoredProductLogisticsAcceptanceTest {
    private static final String AUTHORED_PRODUCT = "module.empire_cargo_secure_v1";

    @Test
    void composedRegistrySurvivesFullRuntimeRestoreProductTransferAndSecondRestore() {
        Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault()
                .withEngineeringCatalog(
                        Stage22CorePairEngineeringCatalogLoader.loadDefault(),
                        Provenance.STAGE22_AUTHORED);
        var product = products.findProduct(AUTHORED_PRODUCT);
        if (product == null) {
            throw new AssertionError("core-pair authored product is absent from composed Stage-18 registry");
        }

        var generated = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        var baselineRuntime = generated.runtime();
        Stage20GeneratedWorldRuntimePersistentState baseline = baselineRuntime.captureState();

        List<RuntimeEndpoint> compatible = baselineRuntime.infrastructure().endpoints().stream()
                .filter(endpoint -> endpoint.handlingCapability().supportedStorageClassIds()
                        .contains(product.storageClassId()))
                .filter(endpoint -> endpoint.handlingCapability().maxUnitMassKg() >= product.unitMassKg())
                .filter(endpoint -> endpoint.storage().remainingCapacityKg(product.storageClassId())
                        >= product.unitMassKg())
                .toList();
        RuntimeEndpoint source = compatible.stream()
                .filter(endpoint -> !endpoint.generatedIndustrial())
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "playable generated world lacks a compatible ordinary infrastructure endpoint"));
        RuntimeEndpoint destination = compatible.stream()
                .filter(endpoint -> !endpoint.stationId().equals(source.stationId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "playable generated world lacks a second compatible infrastructure endpoint"));
        assertFalse(source.generatedIndustrial(),
                "source must exercise the top-level infrastructure registry rather than only Stage-20.5C");

        Stage20GeneratedWorldRuntimePersistentState seeded = replaceCampaign(
                baseline,
                replaceIndustry(
                        baseline.campaign(),
                        addProduct(
                                baseline.campaign().industrialState(),
                                source.stationId(),
                                AUTHORED_PRODUCT)));

        assertThrows(IllegalArgumentException.class,
                () -> Stage20GeneratedWorldRuntimeBridge.restore(seeded),
                "legacy baseline restore must fail closed on a later-authored product ID");

        var restored = Stage20GeneratedWorldRuntimeBridge.restore(seeded, products);
        assertEquals(1,
                restored.infrastructure().endpoint(source.stationId()).storage().productCount(AUTHORED_PRODUCT));
        assertEquals(0,
                restored.infrastructure().endpoint(destination.stationId()).storage().productCount(AUTHORED_PRODUCT));

        var restoredSource = restored.infrastructure().endpoint(source.stationId());
        var restoredDestination = restored.infrastructure().endpoint(destination.stationId());
        double commonRate = Math.min(
                restoredSource.handlingCapability().massRateKgPerSecond(),
                restoredDestination.handlingCapability().massRateKgPerSecond());
        double durationSeconds = product.unitMassKg() / commonRate + 1d;
        var transfer = restored.transferProductBetweenEndpoints(
                source.stationId(),
                destination.stationId(),
                AUTHORED_PRODUCT,
                1,
                durationSeconds);

        assertEquals(Status.TRANSFERRED, transfer.status());
        assertEquals(AUTHORED_PRODUCT, transfer.cargoId());
        assertEquals(1, transfer.transferredUnitCount());
        assertEquals(0, restoredSource.storage().productCount(AUTHORED_PRODUCT));
        assertEquals(1, restoredDestination.storage().productCount(AUTHORED_PRODUCT));

        Stage20GeneratedWorldRuntimePersistentState afterTransfer = restored.captureState();
        var continued = Stage20GeneratedWorldRuntimeBridge.restore(afterTransfer, products);
        assertEquals(0,
                continued.infrastructure().endpoint(source.stationId()).storage().productCount(AUTHORED_PRODUCT));
        assertEquals(1,
                continued.infrastructure().endpoint(destination.stationId()).storage().productCount(AUTHORED_PRODUCT));
        assertEquals(afterTransfer.campaign().industrialState(),
                continued.captureState().campaign().industrialState(),
                "full generated-world continuation must preserve exact product inventory after transfer");
        assertTrue(continued.infrastructure().endpoints().stream()
                .anyMatch(endpoint -> endpoint.stationId().equals(destination.stationId())));
    }

    private static Stage18IndustrialState addProduct(
            Stage18IndustrialState base,
            String stationId,
            String productId) {
        ArrayList<StationStorageSnapshot> storage = new ArrayList<>();
        boolean found = false;
        for (StationStorageSnapshot snapshot : base.stationStorages()) {
            if (!snapshot.stationId().equals(stationId)) {
                storage.add(snapshot);
                continue;
            }
            TreeMap<String, Integer> products = new TreeMap<>(snapshot.productCountById());
            products.merge(productId, 1, Math::addExact);
            storage.add(new StationStorageSnapshot(
                    snapshot.stationId(),
                    snapshot.capacityByStorageClassKg(),
                    snapshot.commodityMassByIdKg(),
                    products));
            found = true;
        }
        if (!found) {
            throw new AssertionError("captured playable checkpoint omitted canonical endpoint storage: " + stationId);
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

    private static Stage20GeneratedWorldRuntimePersistentState replaceCampaign(
            Stage20GeneratedWorldRuntimePersistentState base,
            Stage20GeneratedCampaignPersistentState campaign) {
        return new Stage20GeneratedWorldRuntimePersistentState(
                base.schemaVersion(),
                base.bridgeVersion(),
                campaign,
                base.worldState(),
                base.activeSystemId(),
                base.freight(),
                base.localFleetPhysicalStates());
    }
}
