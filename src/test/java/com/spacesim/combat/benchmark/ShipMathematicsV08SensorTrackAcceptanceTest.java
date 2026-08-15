package com.spacesim.combat.benchmark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipMathematicsV08SensorTrackAcceptanceTest {
    @Test
    void passiveInfraredDetectionRangeEmergesFromRadiatedPowerAndSensorNoise() {
        double corvetteRange = ShipMathematicsV08SensorTrackHarness.maximumPassiveDetectionRangeM(
                ShipMathematicsV08SensorTrackHarness.CORVETTE_WASTE_HEAT_W);
        double reconRange = ShipMathematicsV08SensorTrackHarness.maximumPassiveDetectionRangeM(
                ShipMathematicsV08SensorTrackHarness.RECON_FRIGATE_WASTE_HEAT_W);
        double destroyerRange = ShipMathematicsV08SensorTrackHarness.maximumPassiveDetectionRangeM(
                ShipMathematicsV08SensorTrackHarness.ESCORT_DESTROYER_WASTE_HEAT_W);
        double battleshipRange = ShipMathematicsV08SensorTrackHarness.maximumPassiveDetectionRangeM(
                ShipMathematicsV08SensorTrackHarness.BATTLESHIP_WASTE_HEAT_W);

        assertEquals(23_052_791_028.245937, corvetteRange, 2.0);
        assertEquals(60_793_603_749.74133, reconRange, 2.0);
        assertEquals(54_197_463_368.07501, destroyerRange, 2.0);
        assertEquals(258_888_264_412.64874, battleshipRange, 4.0);
        assertTrue(battleshipRange > 10.0 * corvetteRange);

        ShipMathematicsV08SensorTrackHarness.PassiveObservation threshold =
                ShipMathematicsV08SensorTrackHarness.passiveIrObservation(
                        ShipMathematicsV08SensorTrackHarness.CORVETTE_WASTE_HEAT_W,
                        corvetteRange);
        assertEquals(5.0, threshold.snr(), 1.0e-12);
        assertEquals(6.506666666666667e-7, threshold.angularSigmaRad(), 1.0e-18);
    }

    @Test
    void strongPassiveContactCanReachTheV03AngularSeedButStillNeedsRangeGeometry() {
        ShipMathematicsV08SensorTrackHarness.PassiveObservation battleship =
                ShipMathematicsV08SensorTrackHarness.passiveIrObservation(
                        ShipMathematicsV08SensorTrackHarness.BATTLESHIP_WASTE_HEAT_W,
                        10_000_000_000.0);
        assertTrue(battleship.detected());
        assertEquals(1612.0875559189344, battleship.snr(), 1.0e-9);
        assertEquals(5.0e-8, battleship.angularSigmaRad(), 0.0);

        ShipMathematicsV08SensorTrackHarness.TriangulationResult baseline100k =
                ShipMathematicsV08SensorTrackHarness.twoObserverBearingTriangulation(
                        10_000_000_000.0,
                        100_000_000.0,
                        battleship.angularSigmaRad());
        ShipMathematicsV08SensorTrackHarness.TriangulationResult baseline1m =
                ShipMathematicsV08SensorTrackHarness.twoObserverBearingTriangulation(
                        10_000_000_000.0,
                        1_000_000_000.0,
                        battleship.angularSigmaRad());

        assertEquals(70_710.67811865476, baseline100k.rangeSigmaM(), 1.0e-9);
        assertEquals(7_071.067811865476, baseline1m.rangeSigmaM(), 1.0e-9);
        assertEquals(353.5533905932737, baseline100k.crossTrackSigmaM(), 1.0e-12);
        assertEquals(baseline100k.rangeSigmaM() / 10.0, baseline1m.rangeSigmaM(), 1.0e-9);
    }

    @Test
    void activeRadarProvidesRangeButItsEchoEnvelopeDependsStronglyOnRcs() {
        double corvetteRange = ShipMathematicsV08SensorTrackHarness.maximumActiveRadarDetectionRangeM(
                ShipMathematicsV08SensorTrackHarness.CORVETTE_RCS_M2_SEED);
        double battleshipRange = ShipMathematicsV08SensorTrackHarness.maximumActiveRadarDetectionRangeM(
                ShipMathematicsV08SensorTrackHarness.BATTLESHIP_RCS_M2_SEED);

        assertEquals(326_594_711.94887054, corvetteRange, 0.1);
        assertEquals(1_032_783_161.5250403, battleshipRange, 0.2);
        assertTrue(battleshipRange > 3.0 * corvetteRange);

        ShipMathematicsV08SensorTrackHarness.RadarObservation corvette300k =
                ShipMathematicsV08SensorTrackHarness.activeRadarObservation(
                        300_000_000.0,
                        ShipMathematicsV08SensorTrackHarness.CORVETTE_RCS_M2_SEED);
        assertEquals(7.022982396098298, corvette300k.snr(), 1.0e-12);
        assertEquals(7.49481145, corvette300k.rangeResolutionM(), 1.0e-9);
        assertEquals(1.9997924474576743, corvette300k.rangeSigmaM(), 1.0e-12);
        assertTrue(corvette300k.detected());
    }

    @Test
    void activeSearchIsFarEasierToInterceptAlongItsMainBeamThanItsEchoIsToReceive() {
        ShipMathematicsV08SensorTrackHarness.EmissionIntercept intercept =
                ShipMathematicsV08SensorTrackHarness.activeRadarMainBeamIntercept(
                        1_000_000_000.0,
                        1.0);
        ShipMathematicsV08SensorTrackHarness.RadarObservation echo =
                ShipMathematicsV08SensorTrackHarness.activeRadarObservation(
                        1_000_000_000.0,
                        ShipMathematicsV08SensorTrackHarness.BATTLESHIP_RCS_M2_SEED);

        assertEquals(71_485_253_682_068.06, intercept.snr(), 1.0);
        assertEquals(5.688615740839622, echo.snr(), 1.0e-12);
        assertTrue(intercept.snr() / echo.snr() > 1.0e12);
        assertEquals(0.00366, intercept.mainBeamScaleRad(), 1.0e-15);
    }

    @Test
    void ecmAddsRealInterferenceAndEccmCanRecoverByReducingOverlapAndPayingDwellTime() {
        ShipMathematicsV08SensorTrackHarness.JammingResult fullOverlap =
                ShipMathematicsV08SensorTrackHarness.jammedCorvetteRadarAt300000Km(1.0, 1.0);
        ShipMathematicsV08SensorTrackHarness.JammingResult eccmOneSecond =
                ShipMathematicsV08SensorTrackHarness.jammedCorvetteRadarAt300000Km(
                        ShipMathematicsV08SensorTrackHarness.ECCM_EFFECTIVE_OVERLAP_FRACTION_SEED,
                        1.0);
        ShipMathematicsV08SensorTrackHarness.JammingResult eccmTwoSeconds =
                ShipMathematicsV08SensorTrackHarness.jammedCorvetteRadarAt300000Km(
                        ShipMathematicsV08SensorTrackHarness.ECCM_EFFECTIVE_OVERLAP_FRACTION_SEED,
                        2.0);

        assertEquals(0.01161628273565762, fullOverlap.radarSnr(), 1.0e-15);
        assertFalse(fullOverlap.detected());
        assertEquals(4.379562328120378, eccmOneSecond.radarSnr(), 1.0e-12);
        assertFalse(eccmOneSecond.detected());
        assertEquals(8.759124656240756, eccmTwoSeconds.radarSnr(), 1.0e-12);
        assertTrue(eccmTwoSeconds.detected());
    }

    @Test
    void decoyAmbiguityUsesMeasurementConsistencyAndMultibandSignatureInsteadOfHitChance() {
        double hotColor = ShipMathematicsV08SensorTrackHarness.spectralColorRatio8To12Over3To5At1100K();
        double warmColor = ShipMathematicsV08SensorTrackHarness.spectralColorRatio8To12Over3To5At600K();
        assertEquals(0.20715916162650155, hotColor, 1.0e-15);
        assertEquals(0.9048087374004852, warmColor, 1.0e-15);
        assertTrue(warmColor > 4.0 * hotColor);

        ShipMathematicsV08SensorTrackHarness.InnovationResult closeHypothesis =
                ShipMathematicsV08SensorTrackHarness.normalizedInnovation(5.0e-8, 5.0e-8, 5.0e-8);
        ShipMathematicsV08SensorTrackHarness.InnovationResult edgeHypothesis =
                ShipMathematicsV08SensorTrackHarness.normalizedInnovation(2.0e-7, 5.0e-8, 5.0e-8);
        ShipMathematicsV08SensorTrackHarness.InnovationResult falseContact =
                ShipMathematicsV08SensorTrackHarness.normalizedInnovation(5.0e-7, 5.0e-8, 5.0e-8);

        assertEquals(0.5, closeHypothesis.normalizedInnovationSquared(), 1.0e-15);
        assertEquals(8.0, edgeHypothesis.normalizedInnovationSquared(), 1.0e-15);
        assertEquals(50.0, falseContact.normalizedInnovationSquared(), 1.0e-12);
        assertTrue(closeHypothesis.passesGate());
        assertTrue(edgeHypothesis.passesGate());
        assertFalse(falseContact.passesGate());
    }

    @Test
    void staleFireControlTrackDegradesFromVelocityAndUnmodeledManeuver() {
        ShipMathematicsV08SensorTrackHarness.TrackAgingResult fresh =
                ShipMathematicsV08SensorTrackHarness.ageCrossTrackEstimate(15.0, 0.03, 0.05, 0.0);
        ShipMathematicsV08SensorTrackHarness.TrackAgingResult sixty =
                ShipMathematicsV08SensorTrackHarness.ageCrossTrackEstimate(15.0, 0.03, 0.05, 60.0);
        ShipMathematicsV08SensorTrackHarness.TrackAgingResult oneTwenty =
                ShipMathematicsV08SensorTrackHarness.ageCrossTrackEstimate(15.0, 0.03, 0.05, 120.0);

        assertEquals(15.0, fresh.agedPositionSigmaM(), 0.0);
        assertEquals(91.25919131791602, sixty.agedPositionSigmaM(), 1.0e-12);
        assertEquals(360.3303484304368, oneTwenty.agedPositionSigmaM(), 1.0e-12);
        assertTrue(oneTwenty.agedPositionSigmaM() > 3.0 * sixty.agedPositionSigmaM());
    }
}
