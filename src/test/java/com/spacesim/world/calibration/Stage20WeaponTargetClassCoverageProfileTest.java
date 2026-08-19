package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import com.spacesim.world.calibration.Stage20WeaponTargetClassCoverageProfile.SpatialFamily;
import com.spacesim.world.calibration.Stage20WeaponTargetClassCoverageProfile.TargetClass;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20WeaponTargetClassCoverageProfileTest {
    @Test
    void currentProfileIsDeterministicAndClosesRepresentativeWeaponTargetCoverage() {
        Stage20WeaponTargetClassCoverageProfile first = Stage20WeaponTargetClassCoverageProfile.deriveCurrent();
        Stage20WeaponTargetClassCoverageProfile second = Stage20WeaponTargetClassCoverageProfile.deriveCurrent();

        assertEquals(first, second);
        assertEquals(Stage20WeaponTargetClassCoverageProfile.CURRENT_VERSION, first.version());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, first.authority());
        assertTrue(first.stage22ReviewRequired());
        assertEquals(Stage20WeaponSpatialCalibrationProfile.CURRENT_VERSION, first.productionSpatialProfileVersion());
        assertEquals(EnumSet.allOf(SpatialFamily.class), first.productionSpatialFamilies());
        assertTrue(first.closesStage20BEntryCoverage());
    }

    @Test
    void targetGeometryIsCompleteButUnauthoredDestroyerP50RemainsExplicitlyUnsupported() {
        Stage20WeaponTargetClassCoverageProfile profile = Stage20WeaponTargetClassCoverageProfile.deriveCurrent();

        assertEquals(EnumSet.allOf(TargetClass.class), profile.targets().stream()
                .map(Stage20WeaponTargetClassCoverageProfile.TargetReference::targetClass)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TargetClass.class))));
        assertEquals(List.of(TargetClass.DESTROYER), profile.unsupportedP50Targets());
        assertEquals(5, profile.kineticP50Samples().size());
        assertTrue(profile.targets().stream()
                .filter(value -> value.targetClass() == TargetClass.DESTROYER)
                .noneMatch(Stage20WeaponTargetClassCoverageProfile.TargetReference::p50RowAuthored));
    }

    @Test
    void everyAcceptedP50RowPreservesPhysicalTimeOfFlightAndProjectileEnergy() {
        Stage20WeaponTargetClassCoverageProfile profile = Stage20WeaponTargetClassCoverageProfile.deriveCurrent();

        for (var sample : profile.kineticP50Samples()) {
            assertTrue(sample.isPhysicallyConsistent(), sample.targetClass().name());
            assertEquals(0.5d, sample.singleShotHitProbability(), 1e-12d);
            assertEquals(sample.p50RangeM() / sample.muzzleVelocityMps(), sample.timeOfFlightAtP50S(), 1e-9d);
            assertEquals(
                    0.5d * sample.projectileMassKg() * sample.muzzleVelocityMps() * sample.muzzleVelocityMps(),
                    sample.muzzleEnergyJ(),
                    Math.max(1d, sample.muzzleEnergyJ() * 1e-12d));
        }
    }

    @Test
    void acceptedP50RowsSpanSmallMediumAndCapitalTargetsWithoutClassInterpolation() {
        Stage20WeaponTargetClassCoverageProfile profile = Stage20WeaponTargetClassCoverageProfile.deriveCurrent();
        EnumSet<TargetClass> covered = profile.kineticP50Samples().stream()
                .map(Stage20WeaponTargetClassCoverageProfile.KineticP50Sample::targetClass)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TargetClass.class)));

        assertEquals(EnumSet.of(
                TargetClass.CORVETTE,
                TargetClass.FRIGATE,
                TargetClass.CRUISER,
                TargetClass.BATTLECRUISER,
                TargetClass.BATTLESHIP), covered);
        assertTrue(profile.kineticP50Samples().stream()
                .filter(value -> value.targetClass() == TargetClass.BATTLESHIP)
                .findFirst().orElseThrow().p50RangeM()
                > profile.kineticP50Samples().stream()
                        .filter(value -> value.targetClass() == TargetClass.CORVETTE)
                        .findFirst().orElseThrow().p50RangeM());
    }

    @Test
    void benchmarkAuthorityAndProvenanceRemainExplicitlyProvisional() {
        Stage20WeaponTargetClassCoverageProfile profile = Stage20WeaponTargetClassCoverageProfile.deriveCurrent();

        assertEquals(Stage20WeaponTargetClassCoverageProfile.BENCHMARK_PROVENANCE, profile.benchmarkProvenanceId());
        assertEquals(CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE, profile.authority());
        assertTrue(profile.stage22ReviewRequired());
    }
}
