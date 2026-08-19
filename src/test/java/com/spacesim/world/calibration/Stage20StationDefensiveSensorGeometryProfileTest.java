package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import com.spacesim.world.calibration.Stage20StationDefensiveSensorGeometryProfile.SecurityTier;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20StationDefensiveSensorGeometryProfileTest {
    @Test
    void currentProfileIsDeterministicAndClosesAllEightStationRows() {
        Stage20StationDefensiveSensorGeometryProfile first =
                Stage20StationDefensiveSensorGeometryProfile.deriveCurrent();
        Stage20StationDefensiveSensorGeometryProfile second =
                Stage20StationDefensiveSensorGeometryProfile.deriveCurrent();

        assertEquals(first, second);
        assertEquals(Stage20StationDefensiveSensorGeometryProfile.CURRENT_VERSION, first.version());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, first.authority());
        assertTrue(first.stage22ReviewRequired());
        assertEquals(8, first.stations().size());
        assertTrue(first.closesStage20BEntryCoverage());
        assertTrue(first.stations().stream()
                .allMatch(Stage20StationDefensiveSensorGeometryProfile.StationDefensiveSensorGeometry::sensorGeometryNested));
        assertTrue(first.stations().stream().allMatch(value -> value.defensiveExclusionReferenceM() > 0d));
    }

    @Test
    void tiersSelectOnlyAlreadyAcceptedSensorAndWeaponReferences() {
        Stage20StationDefensiveSensorGeometryProfile profile =
                Stage20StationDefensiveSensorGeometryProfile.deriveCurrent();

        Set<SecurityTier> tiers = profile.stations().stream()
                .map(Stage20StationDefensiveSensorGeometryProfile.StationDefensiveSensorGeometry::securityTier)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                SecurityTier.BASIC_SECURITY,
                SecurityTier.HARDENED_SECURITY,
                SecurityTier.NAVAL_FORTIFIED), tiers);
        assertTrue(profile.stations().stream()
                .allMatch(value -> value.sensorProvenance().contains(Stage20SensorTargetClassCoverageProfile.CURRENT_VERSION)));
        assertTrue(profile.stations().stream()
                .allMatch(value -> value.defenseProvenance().contains(Stage20WeaponTargetClassCoverageProfile.CURRENT_VERSION)));
        assertTrue(profile.stations().stream()
                .allMatch(value -> value.defenseProvenance().contains(Stage20WeaponTargetClassCoverageProfile.BENCHMARK_PROVENANCE)));
    }

    @Test
    void navalOrdnanceDepotUsesCapitalExclusionReferenceWithoutChangingProductionWeaponRules() {
        Stage20StationDefensiveSensorGeometryProfile profile =
                Stage20StationDefensiveSensorGeometryProfile.deriveCurrent();
        var naval = profile.station("station.infrastructure.naval_ordnance_depot");

        assertEquals(SecurityTier.NAVAL_FORTIFIED, naval.securityTier());
        assertEquals(Stage20SensorTargetClassCoverageProfile.TargetClass.BATTLESHIP, naval.sensorReferenceTarget());
        assertEquals(Stage20WeaponTargetClassCoverageProfile.TargetClass.BATTLESHIP, naval.defenseReferenceTarget());
        assertEquals("weapon.kinetic.xl_capital_v0_3", naval.defenseReferenceWeaponId());
        assertEquals(2_819_430.91298755d, naval.defensiveExclusionReferenceM(), 1e-9d);
    }

    @Test
    void defensiveExclusionScaleIsIndependentFromEscortDerivedSensorFloor() {
        Stage20StationDefensiveSensorGeometryProfile profile =
                Stage20StationDefensiveSensorGeometryProfile.deriveCurrent();
        var mining = profile.station("station.infrastructure.mining_outpost");
        var refinery = profile.station("station.infrastructure.refinery_complex");
        var naval = profile.station("station.infrastructure.naval_ordnance_depot");

        assertTrue(mining.defensiveExclusionReferenceM() < refinery.defensiveExclusionReferenceM());
        assertTrue(refinery.defensiveExclusionReferenceM() < naval.defensiveExclusionReferenceM());
        assertTrue(mining.sensorGeometryNested());
        assertTrue(refinery.sensorGeometryNested());
        assertTrue(naval.sensorGeometryNested());
        assertTrue(profile.stations().stream()
                .anyMatch(value -> value.defensiveExclusionReferenceM() > value.activeFireControlM()));
    }
}
