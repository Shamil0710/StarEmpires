package com.spacesim.world.calibration;

import com.spacesim.ship.SensorDefinition.Mode;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import com.spacesim.world.calibration.Stage20SensorTargetClassCoverageProfile.TargetClass;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20SensorTargetClassCoverageProfileTest {
    @Test
    void currentCoverageIsDeterministicAndClosesTheRepresentativeTargetBlocker() {
        Stage20SensorTargetClassCoverageProfile first = Stage20SensorTargetClassCoverageProfile.deriveCurrent();
        Stage20SensorTargetClassCoverageProfile second = Stage20SensorTargetClassCoverageProfile.deriveCurrent();

        assertEquals(first, second);
        assertEquals(Stage20SensorTargetClassCoverageProfile.CURRENT_VERSION, first.version());
        assertEquals("ESCORT_DESTROYER", first.observerRepresentativeId());
        assertEquals(CalibrationAuthority.PRODUCTION_ENGINEERING, first.observerAuthority());
        assertEquals(EnumSet.of(Mode.PASSIVE_THERMAL, Mode.ACTIVE_RADAR), first.observerModes());
        assertEquals(EnumSet.allOf(TargetClass.class), first.targets().stream()
                .map(Stage20SensorTargetClassCoverageProfile.TargetSignatureReference::targetClass)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TargetClass.class))));
        assertTrue(first.closesStage20BEntryCoverage());
    }

    @Test
    void benchmarkTargetsRemainExplicitlyProvisionalWhileEscortRemainsProductionDerived() {
        Stage20SensorTargetClassCoverageProfile profile = Stage20SensorTargetClassCoverageProfile.deriveCurrent();

        var production = profile.targets().stream()
                .filter(value -> value.targetClass() == TargetClass.ESCORT_DESTROYER)
                .findFirst().orElseThrow();
        assertEquals(CalibrationAuthority.PRODUCTION_ENGINEERING, production.authority());
        assertFalse(production.stage22ReviewRequired());
        assertTrue(production.provenanceId().contains("fit.escort_destroyer_schema_v1"));

        var provisional = profile.targets().stream()
                .filter(value -> value.targetClass() != TargetClass.ESCORT_DESTROYER)
                .toList();
        assertFalse(provisional.isEmpty());
        assertTrue(provisional.stream().allMatch(value ->
                value.authority() == CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE));
        assertTrue(provisional.stream().allMatch(
                Stage20SensorTargetClassCoverageProfile.TargetSignatureReference::stage22ReviewRequired));
        assertTrue(provisional.stream().allMatch(value -> value.provenanceId().startsWith("docs/benchmarks/")));
    }

    @Test
    void everyRepresentativeTargetHasPhysicalThermalDetectionWithoutInventingPassiveRangeTracks() {
        Stage20SensorTargetClassCoverageProfile profile = Stage20SensorTargetClassCoverageProfile.deriveCurrent();

        for (TargetClass targetClass : TargetClass.values()) {
            var thermal = profile.sample(targetClass, Mode.PASSIVE_THERMAL);
            assertTrue(thermal.thresholds().detectedMaxDistanceM().isPresent(), targetClass.name());
            assertTrue(thermal.thresholds().classifiedMaxDistanceM().isPresent(), targetClass.name());
            assertTrue(thermal.thresholds().trackedMaxDistanceM().isEmpty(),
                    "single passive bearing must not invent ranged track for " + targetClass);
            assertTrue(thermal.thresholds().fireControlMaxDistanceM().isEmpty(),
                    "single passive bearing must not invent fire control for " + targetClass);
        }
    }

    @Test
    void acceptedThermalReferencesProduceOrderedPhysicalDetectionEnvelopes() {
        Stage20SensorTargetClassCoverageProfile profile = Stage20SensorTargetClassCoverageProfile.deriveCurrent();

        double interceptor = detected(profile, TargetClass.CARRIER_INTERCEPTOR, Mode.PASSIVE_THERMAL);
        double corvette = detected(profile, TargetClass.TORPEDO_CORVETTE, Mode.PASSIVE_THERMAL);
        double recon = detected(profile, TargetClass.RECON_EW_FRIGATE, Mode.PASSIVE_THERMAL);
        double cruiser = detected(profile, TargetClass.CRUISER, Mode.PASSIVE_THERMAL);
        double carrier = detected(profile, TargetClass.FLEET_CARRIER, Mode.PASSIVE_THERMAL);
        double battleship = detected(profile, TargetClass.BATTLESHIP, Mode.PASSIVE_THERMAL);

        assertTrue(interceptor < corvette);
        assertTrue(corvette < recon);
        assertTrue(recon < cruiser);
        assertTrue(cruiser < carrier);
        assertTrue(carrier < battleship);
    }

    @Test
    void activeRadarUsesOnlyTargetsWithAuthoredRcsInsteadOfClassFallbacks() {
        Stage20SensorTargetClassCoverageProfile profile = Stage20SensorTargetClassCoverageProfile.deriveCurrent();

        double corvette = detected(profile, TargetClass.TORPEDO_CORVETTE, Mode.ACTIVE_RADAR);
        double battleship = detected(profile, TargetClass.BATTLESHIP, Mode.ACTIVE_RADAR);
        assertTrue(battleship > corvette);

        Set<TargetClass> intentionallyUnsupported = EnumSet.of(
                TargetClass.CARRIER_INTERCEPTOR,
                TargetClass.RECON_EW_FRIGATE,
                TargetClass.CRUISER,
                TargetClass.FLEET_CARRIER);
        for (TargetClass targetClass : intentionallyUnsupported) {
            assertTrue(profile.sample(targetClass, Mode.ACTIVE_RADAR)
                    .thresholds().detectedMaxDistanceM().isEmpty(),
                    "missing benchmark RCS must stay unsupported for " + targetClass);
        }
    }

    private static double detected(
            Stage20SensorTargetClassCoverageProfile profile,
            TargetClass targetClass,
            Mode mode) {
        return profile.sample(targetClass, mode).thresholds().detectedMaxDistanceM().orElseThrow();
    }
}
