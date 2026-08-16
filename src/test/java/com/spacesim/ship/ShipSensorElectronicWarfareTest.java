package com.spacesim.ship;

import com.spacesim.ship.ElectronicWarfareState.DeceptionSource;
import com.spacesim.ship.ElectronicWarfareState.NoiseJammer;
import com.spacesim.ship.ShipSensorRuntime.ObservationResult;
import com.spacesim.ship.ShipSensorRuntime.Position2d;
import com.spacesim.ship.ShipSensorRuntime.TrackQualityPolicy;
import com.spacesim.ship.TrackState.InformationState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipSensorElectronicWarfareTest {
    private static final ShipSensorRuntime RUNTIME = new ShipSensorRuntime();

    @Test
    void activeRadarProducesRangeAndObservableRadioEmission() {
        SensorDefinition radar = ShipSensorGeometryTest.activeRadar();
        ObservationResult result = RUNTIME.observe(
                11L, 12L, radar, SensorRuntimeState.nominal(),
                new Position2d(0d, 0d), new Position2d(1_000_000d, 0d),
                ShipSensorGeometryTest.radarTarget(), ElectronicWarfareState.empty(), 40d);

        SensorMeasurement measurement = result.measurement().orElseThrow();
        assertTrue(measurement.hasRange());
        assertEquals(1_000_000d, measurement.rangeM(), 0d);
        assertEquals(radar.activeTransmitPowerW(),
                result.observerEmission().activeRadioEmissionPowerW(), 0d);
        assertEquals(0d, result.additionalPowerDemandW(), 0d);
    }

    @Test
    void noiseInterferenceAndEccmHaveExplicitSignalAndPowerTradeoff() {
        SensorDefinition radar = ShipSensorGeometryTest.activeRadar();
        ElectronicWarfareState ew = new ElectronicWarfareState(
                List.of(new NoiseJammer(99L, 0d, 1_000_000d, 130d, 1d, 1d)),
                List.of());
        Position2d observer = new Position2d(0d, 0d);
        Position2d target = new Position2d(1_000_000d, 0d);

        ObservationResult suppressed = RUNTIME.observe(
                1L, 2L, radar, new SensorRuntimeState(true, false, 1d, 1d),
                observer, target, ShipSensorGeometryTest.radarTarget(), ew, 50d);
        ObservationResult processed = RUNTIME.observe(
                1L, 2L, radar, new SensorRuntimeState(true, true, 1d, 1d),
                observer, target, ShipSensorGeometryTest.radarTarget(), ew, 50d);

        assertTrue(suppressed.measurement().isEmpty());
        assertTrue(processed.measurement().isPresent());
        assertEquals(radar.eccmPowerDemandW(), processed.additionalPowerDemandW(), 0d);
        assertTrue(processed.measurement().orElseThrow().effectiveInterferencePowerW() > 0d);
    }

    @Test
    void deceptionIsReturnedAsExplicitAlternativeHypothesis() {
        SensorDefinition radar = ShipSensorGeometryTest.activeRadar();
        ElectronicWarfareState ew = new ElectronicWarfareState(
                List.of(),
                List.of(new DeceptionSource(88L, "false_echo_alpha", 0.05d, 25_000d, 1e-8d)));

        ObservationResult result = RUNTIME.observe(
                1L, 2L, radar, SensorRuntimeState.nominal(),
                new Position2d(0d, 0d), new Position2d(1_000_000d, 0d),
                ShipSensorGeometryTest.radarTarget(), ew, 60d);

        assertEquals(1, result.deceptionHypotheses().size());
        assertEquals("false_echo_alpha", result.deceptionHypotheses().get(0).hypothesisId());
        SensorMeasurement apparent = result.deceptionHypotheses().get(0).apparentMeasurement();
        assertEquals(1_025_000d, apparent.rangeM(), 1e-6);
        assertEquals(0.05d, apparent.bearingRad(), 1e-12);
    }

    @Test
    void staleTrackCovarianceGrowsAndInformationStateDegrades() {
        SensorMeasurement measurement = RUNTIME.observe(
                1L, 2L, ShipSensorGeometryTest.activeRadar(), SensorRuntimeState.nominal(),
                new Position2d(0d, 0d), new Position2d(1_000_000d, 0d),
                ShipSensorGeometryTest.radarTarget(), ElectronicWarfareState.empty(), 100d)
                .measurement().orElseThrow();
        TrackQualityPolicy policy = TrackQualityPolicy.defaultPolicy();
        TrackState fresh = RUNTIME.fuse(
                2L, List.of(measurement), DatalinkState.local(), policy, 100d);
        TrackState twentySecondsOld = RUNTIME.ageTrack(fresh, 120d, policy);
        TrackState veryStale = RUNTIME.ageTrack(fresh, 220d, policy);

        assertEquals(InformationState.FIRE_CONTROL, fresh.informationState());
        assertEquals(InformationState.TRACKED, twentySecondsOld.informationState());
        assertEquals(InformationState.CLASSIFIED, veryStale.informationState());
        assertTrue(twentySecondsOld.covariance().positionVarianceM2()
                > fresh.covariance().positionVarianceM2());
    }
}
