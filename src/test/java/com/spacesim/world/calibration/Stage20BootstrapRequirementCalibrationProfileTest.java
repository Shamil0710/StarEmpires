package com.spacesim.world.calibration;

import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20BootstrapRequirementCalibrationProfileTest {
    private static final double EPSILON = 1e-9d;

    @Test
    void currentProfileDerivesDemandFromReferenceFacilityBottlenecks() {
        var profile = Stage20BootstrapRequirementCalibrationProfile.deriveCurrent();
        Map<String, Stage20BootstrapRequirementCalibrationProfile.ProcessEvidence> evidence =
                profile.processEvidence().stream().collect(Collectors.toMap(
                        Stage20BootstrapRequirementCalibrationProfile.ProcessEvidence::recipeId,
                        value -> value));

        var water = evidence.get("refining.water_purification");
        assertEquals("facility.processing.volatiles", water.facilityDefinitionId());
        assertEquals(60d, water.powerLimitedInputKgPerSecond(), EPSILON);
        assertEquals(10d / 0.15d, water.engineeringLimitedInputKgPerSecond(), EPSILON);
        assertEquals(50d, water.maintenanceLimitedInputKgPerSecond(), EPSILON);
        assertEquals(50d, water.grossInputKgPerSecond(), EPSILON);
        assertEquals(47d, water.usefulOutputKgPerSecond(), EPSILON);
        assertEquals(Stage20BootstrapRequirementCalibrationProfile.ProcessLimiter.MAINTENANCE_WORK,
                water.limitingAuthority());

        var structural = evidence.get("refining.structural_alloy");
        assertEquals("facility.processing.bulk_refinery", structural.facilityDefinitionId());
        assertEquals(25d, structural.powerLimitedInputKgPerSecond(), EPSILON);
        assertEquals(50d, structural.engineeringLimitedInputKgPerSecond(), EPSILON);
        assertEquals(5d / 0.12d, structural.maintenanceLimitedInputKgPerSecond(), EPSILON);
        assertEquals(25d, structural.grossInputKgPerSecond(), EPSILON);
        assertEquals(17d, structural.usefulOutputKgPerSecond(), EPSILON);
        assertEquals(Stage20BootstrapRequirementCalibrationProfile.ProcessLimiter.PROCESS_POWER,
                structural.limitingAuthority());
    }

    @Test
    void sharedDryBulkStorageProducesOneConservativeSupplierRouteHorizon() {
        var profile = Stage20BootstrapRequirementCalibrationProfile.deriveCurrent();
        Map<String, CommodityRequirement> requirements = profile.bootstrapRequirements().essentialCommodities().stream()
                .collect(Collectors.toMap(CommodityRequirement::commodityId, value -> value));
        double expectedSharedDryBulkHorizonSeconds = 40_000_000d / (50d + 25d);

        assertEquals(2, requirements.size());
        assertEquals(50d,
                requirements.get("commodity.feedstock.water_ice").minSupplierThroughputKgPerSecond(), EPSILON);
        assertEquals(25d,
                requirements.get("commodity.feedstock.metallic_ore").minSupplierThroughputKgPerSecond(), EPSILON);
        assertEquals(expectedSharedDryBulkHorizonSeconds,
                requirements.get("commodity.feedstock.water_ice").maxSupplierRouteTimeS(), EPSILON);
        assertEquals(expectedSharedDryBulkHorizonSeconds,
                requirements.get("commodity.feedstock.metallic_ore").maxSupplierRouteTimeS(), EPSILON);
        assertEquals(expectedSharedDryBulkHorizonSeconds,
                profile.bootstrapRequirements().maxIntermediateInputRouteTimeS(), EPSILON);
        assertEquals(25d, profile.bootstrapRequirements().minIntermediateInputThroughputKgPerSecond(), EPSILON);
    }

    @Test
    void dependencyProjectionPreservesExactlyTheDerivedEconomicRateAndTime() {
        var profile = Stage20BootstrapRequirementCalibrationProfile.deriveCurrent();
        Map<String, CommodityRequirement> economic = profile.bootstrapRequirements().essentialCommodities().stream()
                .collect(Collectors.toMap(CommodityRequirement::commodityId, value -> value));

        assertEquals(economic.size(), profile.dependencyRequirements().size());
        profile.dependencyRequirements().forEach(dependency -> {
            CommodityRequirement requirement = economic.get(dependency.commodityId());
            assertEquals(requirement.minSupplierThroughputKgPerSecond(),
                    dependency.requiredKgPerSecond(), 0d);
            assertEquals(requirement.maxSupplierRouteTimeS(), dependency.maxSupplierRouteTimeS(), 0d);
        });
    }

    @Test
    void currentPolicyAndCatalogProvenanceRemainExplicit() {
        var profile = Stage20BootstrapRequirementCalibrationProfile.deriveCurrent();

        assertEquals(Stage20BootstrapRequirementCalibrationProfile.CURRENT_VERSION, profile.version());
        assertEquals(Stage20BootstrapRequirementCalibrationProfile.CURRENT_REFERENCE_STATION_ID,
                profile.referenceStationArchetypeId());
        assertEquals(
                java.util.List.of("refining.structural_alloy", "refining.water_purification"),
                profile.processPolicy().stream()
                        .map(Stage20BootstrapRequirementCalibrationProfile.EssentialProcessPolicy::recipeId)
                        .toList());
        assertTrue(profile.resourceOntologyFingerprint().matches("[0-9a-f]{64}"));
        assertTrue(profile.refiningFingerprint().matches("[0-9a-f]{64}"));
        assertTrue(profile.facilityFingerprint().matches("[0-9a-f]{64}"));
        assertTrue(profile.stationInfrastructureFingerprint().matches("[0-9a-f]{64}"));
        assertTrue(profile.stage22ReviewRequired());
    }
}
