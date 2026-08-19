package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;
import com.spacesim.world.calibration.Stage20MajorInfrastructureExtentCalibrationProfile.ExtentBand;
import com.spacesim.world.calibration.Stage20MajorInfrastructureExtentCalibrationProfile.ExtentBandId;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20MajorInfrastructureExtentCalibrationProfileTest {
    @Test
    void currentProfileDerivesThreeDescriptiveExtentsFromAcceptedRouteBands() {
        Stage20MajorInfrastructureExtentCalibrationProfile first =
                Stage20MajorInfrastructureExtentCalibrationProfile.deriveCurrent();
        Stage20MajorInfrastructureExtentCalibrationProfile second =
                Stage20MajorInfrastructureExtentCalibrationProfile.deriveCurrent();

        assertEquals(first, second);
        assertEquals(Stage20MajorInfrastructureExtentCalibrationProfile.CURRENT_VERSION, first.version());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, first.authority());
        assertTrue(first.stage22ReviewRequired());
        assertEquals(Stage20LocalRouteSemanticCalibrationProfile.CURRENT_VERSION, first.localRouteProfileVersion());
        assertEquals(Stage20JumpArrivalSpatialCalibrationProfile.CURRENT_VERSION, first.jumpArrivalProfileVersion());
        assertEquals(3, first.bands().size());
        assertTrue(first.closesStage20BEntryCoverage());

        Map<ExtentBandId, ExtentBand> byId = first.bands().stream()
                .collect(Collectors.toMap(ExtentBand::id, Function.identity()));
        assertEquals(BandId.STATION_TO_STATION,
                byId.get(ExtentBandId.CORE_STATION_CLUSTER).sourceRouteBand());
        assertEquals(BandId.STATION_TO_RESOURCE_FIELD,
                byId.get(ExtentBandId.INDUSTRIAL_RESOURCE_NETWORK).sourceRouteBand());
        assertEquals(BandId.JUMP_ARRIVAL_TO_MAJOR_HUB,
                byId.get(ExtentBandId.MAJOR_HUB_REACH).sourceRouteBand());
        assertEquals(1_000_000_000d, first.maximumMajorInfrastructureExtentM(), 0d);
    }

    @Test
    void extentsRemainOutsideStationStandOffAndInsideInnerOuterScale() {
        Stage20MajorInfrastructureExtentCalibrationProfile profile =
                Stage20MajorInfrastructureExtentCalibrationProfile.deriveCurrent();
        Map<ExtentBandId, ExtentBand> byId = profile.bands().stream()
                .collect(Collectors.toMap(ExtentBand::id, Function.identity()));

        ExtentBand core = byId.get(ExtentBandId.CORE_STATION_CLUSTER);
        ExtentBand hub = byId.get(ExtentBandId.MAJOR_HUB_REACH);
        assertTrue(core.minExtentM() > profile.maxClosedStationStandOffM());
        assertTrue(hub.maxExtentM() <= profile.innerToOuterSystemMinDistanceM());
        assertTrue(profile.bands().stream().allMatch(value -> !value.hardBoundary() && !value.clampAllowed()));
    }

    @Test
    void extentContractRejectsHardBoundaryOrClampSemantics() {
        assertThrows(IllegalArgumentException.class, () -> new ExtentBand(
                ExtentBandId.CORE_STATION_CLUSTER,
                BandId.STATION_TO_STATION,
                1d,
                2d,
                "test",
                true,
                false));
        assertThrows(IllegalArgumentException.class, () -> new ExtentBand(
                ExtentBandId.CORE_STATION_CLUSTER,
                BandId.STATION_TO_STATION,
                1d,
                2d,
                "test",
                false,
                true));

        Stage20MajorInfrastructureExtentCalibrationProfile profile =
                Stage20MajorInfrastructureExtentCalibrationProfile.deriveCurrent();
        assertFalse(profile.bands().stream().anyMatch(ExtentBand::hardBoundary));
        assertFalse(profile.bands().stream().anyMatch(ExtentBand::clampAllowed));
    }
}
