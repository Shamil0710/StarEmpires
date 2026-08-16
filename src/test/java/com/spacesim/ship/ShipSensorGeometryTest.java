package com.spacesim.ship;

import com.spacesim.ship.SensorDefinition.Mode;
import com.spacesim.ship.ShipSensorRuntime.Position2d;
import com.spacesim.ship.ShipSensorRuntime.TrackQualityPolicy;
import com.spacesim.ship.SignatureState.Channel;
import com.spacesim.ship.TrackState.InformationState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipSensorGeometryTest {
    private static final ShipSensorRuntime RUNTIME = new ShipSensorRuntime();

    @Test
    void passiveBearingOnlyContactNeverInventsExactRange() {
        SensorMeasurement measurement = RUNTIME.observe(
                1L, 2L, passiveThermal(), SensorRuntimeState.nominal(),
                new Position2d(0d, 0d), new Position2d(1_000_000d, 500_000d),
                brightTarget(), ElectronicWarfareState.empty(), 10d)
                .measurement().orElseThrow();

        assertFalse(measurement.hasRange());
        assertEquals(null, measurement.rangeM());
        assertEquals(null, measurement.rangeVarianceM2());

        TrackState track = RUNTIME.fuse(
                2L, List.of(measurement), DatalinkState.local(),
                TrackQualityPolicy.defaultPolicy(), 10d);
        assertFalse(track.positionKnown());
        assertFalse(track.covariance().hasPositionCovariance());
        assertFalse(track.covariance().hasRangeCovariance());
        assertEquals(InformationState.CLASSIFIED, track.informationState());
    }

    @Test
    void twoPassiveBearingsTriangulateThroughObserverGeometry() {
        Position2d target = new Position2d(1_000_000d, 1_000_000d);
        SensorMeasurement first = RUNTIME.observe(
                1L, 7L, passiveThermal(), SensorRuntimeState.nominal(),
                new Position2d(0d, 0d), target, brightTarget(),
                ElectronicWarfareState.empty(), 30d).measurement().orElseThrow();
        SensorMeasurement second = RUNTIME.observe(
                2L, 7L, passiveThermal(), SensorRuntimeState.nominal(),
                new Position2d(0d, 2_000_000d), target, brightTarget(),
                ElectronicWarfareState.empty(), 30d).measurement().orElseThrow();

        assertFalse(first.hasRange());
        assertFalse(second.hasRange());
        TrackState fused = RUNTIME.fuse(
                7L, List.of(first, second), DatalinkState.local(),
                TrackQualityPolicy.defaultPolicy(), 30d);

        assertTrue(fused.positionKnown());
        assertEquals(2, fused.contributingObservers());
        assertEquals(1_000_000d, fused.estimatedXM(), 1e-6);
        assertEquals(1_000_000d, fused.estimatedYM(), 1e-6);
    }

    @Test
    void distributedRangingObserversReducePositionCovariance() {
        Position2d target = new Position2d(1_000_000d, 1_000_000d);
        SensorMeasurement first = RUNTIME.observe(
                1L, 9L, activeRadar(), SensorRuntimeState.nominal(),
                new Position2d(0d, 0d), target, radarTarget(),
                ElectronicWarfareState.empty(), 20d).measurement().orElseThrow();
        SensorMeasurement second = RUNTIME.observe(
                2L, 9L, activeRadar(), SensorRuntimeState.nominal(),
                new Position2d(0d, 2_000_000d), target, radarTarget(),
                ElectronicWarfareState.empty(), 20d).measurement().orElseThrow();

        TrackState one = RUNTIME.fuse(
                9L, List.of(first), DatalinkState.local(),
                TrackQualityPolicy.defaultPolicy(), 20d);
        TrackState two = RUNTIME.fuse(
                9L, List.of(first, second), DatalinkState.local(),
                TrackQualityPolicy.defaultPolicy(), 20d);

        assertTrue(two.covariance().positionVarianceM2() < one.covariance().positionVarianceM2());
    }

    static SensorDefinition passiveThermal() {
        return new SensorDefinition(
                "sensor.passive_thermal_test", Mode.PASSIVE_THERMAL, Channel.THERMAL,
                10d, 1e-12d, 5d, 20d, 100d, 500d,
                1e-3d, 1e-3d,
                0d, 1d, 0d, 0d,
                20d, 2_000d, 1_000d);
    }

    static SensorDefinition activeRadar() {
        return new SensorDefinition(
                "sensor.active_radar_test", Mode.ACTIVE_RADAR, Channel.RADAR,
                100d, 1e-12d, 10d, 20d, 50d, 100d,
                1e-4d, 1e-3d,
                1e12d, 1d, 1.2e12d, 1e11d,
                100d, 5_000d, 2_500d);
    }

    static SignatureState brightTarget() {
        return new SignatureState(1e9d, 5e9d, 500d, 1e8d, 0d, 0d);
    }

    static SignatureState radarTarget() {
        return new SignatureState(1e8d, 1e9d, 1_000d, 1e7d, 0d, 0d);
    }
}
