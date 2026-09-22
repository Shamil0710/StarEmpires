package com.spacesim.ship;

import com.spacesim.ship.ElectronicWarfareState.NoiseJammer;
import com.spacesim.ship.LayeredDefenseScheduler.DefendedZone;
import com.spacesim.ship.LayeredDefenseScheduler.DefenseStation;
import com.spacesim.ship.LayeredDefenseScheduler.Threat;
import com.spacesim.ship.ShipSensorRuntime.ObservationResult;
import com.spacesim.ship.ShipSensorRuntime.Position2d;
import com.spacesim.ship.WeaponDefinition.GuidedWeapon;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Stage228CarrierCounterplayPhysicalSystemsTest {

    @Test
    void pointDefenseScreenIsEffectiveButFiniteInChannelsAmmoAndThermalDuty() {
        LayeredDefenseScheduler scheduler = new LayeredDefenseScheduler();
        DefendedZone zone = new DefendedZone(0d, 0d, 1_000d);
        List<Threat> wave = List.of(
                new Threat(101L, 100_000d, 0d, -1_000d, 0d, 1_000d, true),
                new Threat(102L, 102_000d, 0d, -1_000d, 0d, 1_000d, true),
                new Threat(103L, 104_000d, 0d, -1_000d, 0d, 1_000d, true));

        assertEquals(
                1,
                scheduler.schedule(
                        zone,
                        wave,
                        List.of(station(1, 1L, true))).size(),
                "one channel/round must not become an infinite PD aura");
        assertEquals(
                3,
                scheduler.schedule(
                        zone,
                        wave,
                        List.of(station(3, 3L, true))).size(),
                "sufficient physical channels/ammunition may cover the full wave");
        assertEquals(
                0,
                scheduler.schedule(
                        zone,
                        wave,
                        List.of(station(3, 3L, false))).size(),
                "thermal denial must disable otherwise stocked PD");
    }

    @Test
    void electronicWarfareCanSuppressTrackingAndEccmRecoversAtExplicitPowerCost() {
        ShipSensorRuntime runtime = new ShipSensorRuntime();
        SensorDefinition radar = ShipSensorGeometryTest.activeRadar();
        ElectronicWarfareState ew = new ElectronicWarfareState(
                List.of(new NoiseJammer(99L, 0d, 1_000_000d, 130d, 1d, 1d)),
                List.of());
        Position2d observer = new Position2d(0d, 0d);
        Position2d target = new Position2d(1_000_000d, 0d);

        ObservationResult suppressed = runtime.observe(
                1L,
                2L,
                radar,
                new SensorRuntimeState(true, false, 1d, 1d),
                observer,
                target,
                ShipSensorGeometryTest.radarTarget(),
                ew,
                50d);
        ObservationResult eccm = runtime.observe(
                1L,
                2L,
                radar,
                new SensorRuntimeState(true, true, 1d, 1d),
                observer,
                target,
                ShipSensorGeometryTest.radarTarget(),
                ew,
                50d);

        assertTrue(suppressed.measurement().isEmpty(),
                "carrier/small-craft sensing must not bypass common jammer physics");
        assertTrue(eccm.measurement().isPresent());
        assertEquals(radar.eccmPowerDemandW(), eccm.additionalPowerDemandW(), 0d);
        assertTrue(eccm.measurement().orElseThrow().effectiveInterferencePowerW() > 0d);
    }

    private static DefenseStation station(
            int channels,
            long rounds,
            boolean thermalAvailable) {
        return new DefenseStation(
                77L,
                0d,
                0d,
                0d,
                interceptor(),
                true,
                channels,
                rounds,
                thermalAvailable,
                5_000d);
    }

    private static GuidedWeapon interceptor() {
        return new GuidedWeapon(
                "ammo.m22_8k_pd_interceptor",
                "seeker.m22_8k_pd",
                800d,
                200d,
                20_000d,
                5_000d,
                40d,
                0.0005d,
                200d);
    }
}
