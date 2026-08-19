package com.spacesim.world.calibration;

import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.DerivedShipCalculator;
import com.spacesim.ship.ElectronicWarfareState;
import com.spacesim.ship.SensorDefinition.Mode;
import com.spacesim.ship.SensorRuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipSensorEngineeringAdapter;
import com.spacesim.ship.SignatureState;
import com.spacesim.world.calibration.Stage20SensorCalibrationProfile.CalibrationCondition;
import com.spacesim.world.calibration.Stage20SensorCalibrationProfile.InterferenceAuthority;
import com.spacesim.world.calibration.Stage20SensorCalibrationProfile.SensorEnvelopeSample;
import com.spacesim.world.calibration.Stage20SensorCalibrationProfile.TrackPolicyAuthority;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20SensorCalibrationProfileTest {
    @Test
    void currentProfileMeasuresProductionEscortInformationStateEnvelopes() {
        Stage20SensorCalibrationProfile profile = Stage20SensorCalibrationProfile.deriveCurrent();

        assertEquals(Stage20SensorCalibrationProfile.CURRENT_VERSION, profile.version());
        assertEquals("ESCORT_DESTROYER", profile.observerRepresentativeId());
        assertEquals("ESCORT_DESTROYER", profile.targetRepresentativeId());
        assertEquals(6, profile.samples().size());
        assertEquals(EnumSet.of(Mode.PASSIVE_THERMAL, Mode.ACTIVE_RADAR),
                profile.samples().stream().map(SensorEnvelopeSample::mode)
                        .collect(() -> EnumSet.noneOf(Mode.class), EnumSet::add, EnumSet::addAll));

        SensorEnvelopeSample passive = sample(profile, Mode.PASSIVE_THERMAL, CalibrationCondition.PRISTINE);
        assertTrue(passive.thresholds().detectedMaxDistanceM().isPresent());
        assertTrue(passive.thresholds().classifiedMaxDistanceM().isPresent());
        assertTrue(passive.thresholds().detectedMaxDistanceM().getAsDouble()
                >= passive.thresholds().classifiedMaxDistanceM().getAsDouble());
        assertTrue(passive.thresholds().trackedMaxDistanceM().isEmpty(),
                "one passive bearing must not invent a ranged tactical track");
        assertTrue(passive.thresholds().fireControlMaxDistanceM().isEmpty());

        SensorEnvelopeSample radar = sample(profile, Mode.ACTIVE_RADAR, CalibrationCondition.PRISTINE);
        assertTrue(radar.thresholds().detectedMaxDistanceM().isPresent());
        assertTrue(radar.thresholds().classifiedMaxDistanceM().isPresent());
        assertTrue(radar.thresholds().trackedMaxDistanceM().isPresent());
        assertTrue(radar.thresholds().fireControlMaxDistanceM().isPresent());
        assertTrue(radar.thresholds().detectedMaxDistanceM().getAsDouble()
                >= radar.thresholds().classifiedMaxDistanceM().getAsDouble());
        assertTrue(radar.thresholds().classifiedMaxDistanceM().getAsDouble()
                >= radar.thresholds().trackedMaxDistanceM().getAsDouble());
        assertTrue(radar.thresholds().trackedMaxDistanceM().getAsDouble()
                >= radar.thresholds().fireControlMaxDistanceM().getAsDouble());
    }

    @Test
    void physicalSensorDamageCannotImproveSpatialEvidenceEnvelope() {
        Stage20SensorCalibrationProfile profile = Stage20SensorCalibrationProfile.deriveCurrent();

        for (Mode mode : EnumSet.of(Mode.PASSIVE_THERMAL, Mode.ACTIVE_RADAR)) {
            SensorEnvelopeSample pristine = sample(profile, mode, CalibrationCondition.PRISTINE);
            SensorEnvelopeSample damaged = sample(profile, mode, CalibrationCondition.SENSOR_MOUNT_50_PERCENT);
            assertTrue(damaged.apertureAreaM2() < pristine.apertureAreaM2());
            assertTrue(damaged.receiverNoisePowerW() > pristine.receiverNoisePowerW());
            assertTrue(damaged.thresholds().detectedMaxDistanceM().getAsDouble()
                    < pristine.thresholds().detectedMaxDistanceM().getAsDouble());
            assertTrue(damaged.thresholds().classifiedMaxDistanceM().getAsDouble()
                    < pristine.thresholds().classifiedMaxDistanceM().getAsDouble());
            if (pristine.thresholds().trackedMaxDistanceM().isPresent()) {
                assertTrue(damaged.thresholds().trackedMaxDistanceM().getAsDouble()
                        < pristine.thresholds().trackedMaxDistanceM().getAsDouble());
                assertTrue(damaged.thresholds().fireControlMaxDistanceM().getAsDouble()
                        < pristine.thresholds().fireControlMaxDistanceM().getAsDouble());
            }
        }
    }

    @Test
    void authoredNoiseJammerShrinksRadarEnvelopeAndEccmRecoversCapabilityAtPhysicalCost() {
        Stage20SensorCalibrationProfile profile = Stage20SensorCalibrationProfile.deriveCurrent();
        SensorEnvelopeSample pristine = sample(profile, Mode.ACTIVE_RADAR, CalibrationCondition.PRISTINE);
        SensorEnvelopeSample jammed = sample(profile, Mode.ACTIVE_RADAR, CalibrationCondition.JAMMED_NO_ECCM);
        SensorEnvelopeSample eccm = sample(profile, Mode.ACTIVE_RADAR, CalibrationCondition.JAMMED_WITH_ECCM);

        assertEquals(InterferenceAuthority.PROVISIONAL_ACCEPTED_COMBAT_TEST, jammed.interferenceAuthority());
        assertTrue(jammed.jammerObserverDistanceM() > 0d);
        assertTrue(jammed.jammerRadiatedPowerW() > 0d);
        assertTrue(jammed.thresholds().detectedMaxDistanceM().getAsDouble()
                < pristine.thresholds().detectedMaxDistanceM().getAsDouble());
        assertTrue(eccm.thresholds().detectedMaxDistanceM().getAsDouble()
                > jammed.thresholds().detectedMaxDistanceM().getAsDouble());
        assertTrue(eccm.eccmPowerDemandW() > 0d);
        assertTrue(eccm.eccmWasteHeatW() > 0d);
    }

    @Test
    void strongerPhysicalRadarSignatureCannotReduceDetectionBoundary() {
        var derived = new DerivedShipCalculator(ShipEngineeringCatalogLoader.loadDefault()).deriveDemonstrator(
                "fit.escort_destroyer_schema_v1", ConsumableState.empty(), DamageState.pristine());
        var suite = new ShipSensorEngineeringAdapter().derive(derived);
        var radar = suite.sensors().stream()
                .filter(value -> value.definition().mode() == Mode.ACTIVE_RADAR)
                .findFirst().orElseThrow().definition();
        SignatureState baseline = suite.staticSignature();
        SignatureState stronger = new SignatureState(
                baseline.thermalRadiantPowerW(),
                baseline.enginePlumeRadiantPowerW(),
                baseline.radarCrossSectionM2() * 2d,
                baseline.reflectedOpticalPowerW(),
                baseline.activeRadioEmissionPowerW(),
                baseline.jammerEmissionPowerW());

        double baselineDistance = Stage20SensorCalibrationCalculator.deriveThresholdDistances(
                        radar, SensorRuntimeState.nominal(), baseline, ElectronicWarfareState.empty())
                .detectedMaxDistanceM().orElseThrow();
        double strongerDistance = Stage20SensorCalibrationCalculator.deriveThresholdDistances(
                        radar, SensorRuntimeState.nominal(), stronger, ElectronicWarfareState.empty())
                .detectedMaxDistanceM().orElseThrow();
        assertTrue(strongerDistance > baselineDistance);
    }

    @Test
    void currentProfileIsDeterministicAndKeepsTrackPolicyProvisional() {
        Stage20SensorCalibrationProfile first = Stage20SensorCalibrationProfile.deriveCurrent();
        Stage20SensorCalibrationProfile second = Stage20SensorCalibrationProfile.deriveCurrent();

        assertEquals(first, second);
        assertEquals(TrackPolicyAuthority.PROVISIONAL_PRE_STAGE20_DEFAULT, first.trackPolicy().authority());
        assertTrue(first.unresolvedGaps().contains("final_fused_track_quality_policy_pending_weapon_geometry"));
        assertTrue(first.unresolvedGaps().contains("distributed_passive_triangulation_geometry_not_yet_profiled"));
        assertFalse(first.unresolvedGaps().isEmpty());
    }

    private static SensorEnvelopeSample sample(
            Stage20SensorCalibrationProfile profile,
            Mode mode,
            CalibrationCondition condition) {
        return profile.samples().stream()
                .filter(value -> value.mode() == mode && value.condition() == condition)
                .findFirst()
                .orElseThrow();
    }
}
