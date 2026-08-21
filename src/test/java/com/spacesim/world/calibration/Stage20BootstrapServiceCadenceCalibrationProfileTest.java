package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20IntersystemCadenceCalibrationProfile.HopCadenceSample;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile.RepresentativeGroup;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile.SemanticRouteSample;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile.ThrustPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20BootstrapServiceCadenceCalibrationProfileTest {
    private static final double EPSILON = 1e-9d;

    @Test
    void currentServiceBudgetIsComposedFromAcceptedPhysicalCadence() {
        var service = Stage20BootstrapServiceCadenceCalibrationProfile.deriveCurrent();
        var local = Stage20LocalRouteSemanticCalibrationProfile.deriveCurrent();
        var intersystem = Stage20IntersystemCadenceCalibrationProfile.deriveCurrent();
        var propulsion = Stage20RepresentativePropulsionCatalogLoader.loadDefault();
        var stations = com.spacesim.content.Stage18StationInfrastructureCatalogLoader.loadDefault();

        double expectedSourceLocal = maximumCivilianRoutineSeconds(
                local, List.of(BandId.STATION_TO_STATION, BandId.STATION_TO_RESOURCE_FIELD));
        double expectedJumpAccess = maximumCivilianRoutineSeconds(
                local, List.of(BandId.JUMP_ARRIVAL_TO_MAJOR_HUB));
        HopCadenceSample expectedRegionalFtl = intersystem.samples().stream()
                .filter(value -> value.representativeId().equals(
                        Stage20BootstrapServiceCadenceCalibrationProfile.FREIGHT_REFERENCE_CLASS))
                .filter(value -> value.hopCount()
                        == Stage20BootstrapServiceCadenceCalibrationProfile.REGIONAL_HOP_COUNT)
                .findFirst()
                .orElseThrow();
        var freight = propulsion.findByRepresentativeClass(
                Stage20BootstrapServiceCadenceCalibrationProfile.FREIGHT_REFERENCE_CLASS);
        var hub = stations.findArchetype(
                Stage20BootstrapServiceCadenceCalibrationProfile.HUB_STATION_ARCHETYPE_ID);
        double expectedHandling = freight.missionCargoStoresMassKg() / hub.transferMassRateKgPerSecond();
        double expectedTotal = 2d * expectedHandling
                + expectedSourceLocal
                + 2d * expectedJumpAccess
                + expectedRegionalFtl.arrivalTimeS();

        assertEquals(expectedSourceLocal, service.maximumSourceLocalAccessSeconds(), EPSILON);
        assertEquals(expectedJumpAccess, service.maximumJumpAccessSeconds(), EPSILON);
        assertEquals(expectedRegionalFtl.arrivalTimeS(), service.regionalFtlArrivalSeconds(), EPSILON);
        assertEquals(freight.missionCargoStoresMassKg(), service.payloadMassKg(), EPSILON);
        assertEquals(hub.transferMassRateKgPerSecond(), service.hubTransferMassRateKgPerSecond(), EPSILON);
        assertEquals(expectedHandling, service.oneEndpointHandlingSeconds(), EPSILON);
        assertEquals(expectedTotal, service.maximumSupplierDeliveryTimeSeconds(), EPSILON);
    }

    @Test
    void serviceAuthorityKeepsAcceptedUpstreamProvenanceAndStage22Boundary() {
        var service = Stage20BootstrapServiceCadenceCalibrationProfile.deriveCurrent();

        assertEquals(Stage20BootstrapServiceCadenceCalibrationProfile.CURRENT_VERSION, service.version());
        assertEquals(Stage20BootstrapServiceCadenceCalibrationProfile.FREIGHT_REFERENCE_CLASS,
                service.freightReferenceClass());
        assertEquals(Stage20BootstrapServiceCadenceCalibrationProfile.HUB_STATION_ARCHETYPE_ID,
                service.hubStationArchetypeId());
        assertEquals(Stage20BootstrapServiceCadenceCalibrationProfile.REGIONAL_HOP_COUNT,
                service.regionalHopCount());
        assertEquals(Stage20LocalRouteSemanticCalibrationProfile.CURRENT_VERSION,
                service.localRouteCalibrationVersion());
        assertEquals(Stage20IntersystemCadenceCalibrationProfile.CURRENT_VERSION,
                service.intersystemCadenceVersion());
        assertTrue(service.stationInfrastructureFingerprint().matches("[0-9a-f]{64}"));
        assertTrue(service.stage22ReviewRequired());
    }

    private static double maximumCivilianRoutineSeconds(
            Stage20LocalRouteSemanticCalibrationProfile local,
            List<BandId> allowedBands) {
        return local.samples().stream()
                .filter(value -> allowedBands.contains(value.bandId()))
                .filter(value -> value.representativeGroup() == RepresentativeGroup.CIVILIAN_LOGISTICS)
                .filter(value -> value.thrustPolicy() == ThrustPolicy.ROUTINE_SUSTAINED)
                .mapToDouble(SemanticRouteSample::totalTravelTimeS)
                .max()
                .orElseThrow();
    }
}
