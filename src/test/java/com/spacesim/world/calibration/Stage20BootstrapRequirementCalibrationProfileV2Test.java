package com.spacesim.world.calibration;

import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20BootstrapRequirementCalibrationProfileV2Test {
    private static final double EPSILON = 1e-9d;

    @Test
    void v2PreservesV1DemandButSeparatesBufferCoverageFromSupplierServiceTime() {
        var v1 = Stage20BootstrapRequirementCalibrationProfile.deriveCurrent();
        var v2 = Stage20BootstrapRequirementCalibrationProfileV2.deriveCurrent();
        Map<String, CommodityRequirement> v1ByCommodity = v1.bootstrapRequirements().essentialCommodities().stream()
                .collect(Collectors.toMap(CommodityRequirement::commodityId, Function.identity()));
        Map<String, CommodityRequirement> v2ByCommodity = v2.bootstrapRequirements().essentialCommodities().stream()
                .collect(Collectors.toMap(CommodityRequirement::commodityId, Function.identity()));

        assertEquals(v1ByCommodity.keySet(), v2ByCommodity.keySet());
        for (String commodityId : v1ByCommodity.keySet()) {
            CommodityRequirement oldRequirement = v1ByCommodity.get(commodityId);
            CommodityRequirement correctedRequirement = v2ByCommodity.get(commodityId);
            assertEquals(oldRequirement.minSupplierThroughputKgPerSecond(),
                    correctedRequirement.minSupplierThroughputKgPerSecond(), 0d);
            assertEquals(oldRequirement.maxSupplierRouteTimeS(),
                    v2.referenceBufferCoverageSecondsByCommodity().get(commodityId), EPSILON);
            assertEquals(v2.serviceCadence().maximumSupplierDeliveryTimeSeconds(),
                    correctedRequirement.maxSupplierRouteTimeS(), EPSILON);
            assertNotEquals(correctedRequirement.maxSupplierRouteTimeS(),
                    v2.referenceBufferCoverageSecondsByCommodity().get(commodityId));
        }

        assertEquals(50d,
                v2ByCommodity.get("commodity.feedstock.water_ice").minSupplierThroughputKgPerSecond(), EPSILON);
        assertEquals(25d,
                v2ByCommodity.get("commodity.feedstock.metallic_ore").minSupplierThroughputKgPerSecond(), EPSILON);
    }

    @Test
    void dependencyProjectionUsesCorrectedServiceTimeAndExactPreservedRates() {
        var v2 = Stage20BootstrapRequirementCalibrationProfileV2.deriveCurrent();
        Map<String, CommodityRequirement> economic = v2.bootstrapRequirements().essentialCommodities().stream()
                .collect(Collectors.toMap(CommodityRequirement::commodityId, Function.identity()));

        assertEquals(economic.size(), v2.dependencyRequirements().size());
        v2.dependencyRequirements().forEach(dependency -> {
            CommodityRequirement requirement = economic.get(dependency.commodityId());
            assertEquals(requirement.minSupplierThroughputKgPerSecond(), dependency.requiredKgPerSecond(), 0d);
            assertEquals(requirement.maxSupplierRouteTimeS(), dependency.maxSupplierRouteTimeS(), 0d);
        });
        assertEquals(Stage20BootstrapRequirementCalibrationProfileV2.CURRENT_VERSION, v2.version());
        assertEquals(Stage20BootstrapRequirementCalibrationProfile.CURRENT_VERSION, v2.demandAuthorityVersion());
        assertTrue(v2.resourceOntologyFingerprint().matches("[0-9a-f]{64}"));
        assertTrue(v2.stationInfrastructureFingerprint().matches("[0-9a-f]{64}"));
        assertTrue(v2.stage22ReviewRequired());
    }
}
