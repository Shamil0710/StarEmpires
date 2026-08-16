package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.GuidedAmmunitionDefinition;
import com.spacesim.content.weapon.WeaponAmmunitionCatalogLoader;
import com.spacesim.content.weapon.WeaponLauncherCatalog;
import com.spacesim.content.weapon.WeaponLauncherCatalogLoader;
import com.spacesim.ship.LayeredDefenseScheduler.DefendedZone;
import com.spacesim.ship.LayeredDefenseScheduler.DefenseStation;
import com.spacesim.ship.LayeredDefenseScheduler.Threat;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.TrackState.InformationState;
import com.spacesim.ship.WeaponDefinition.BeamWeapon;
import com.spacesim.ship.WeaponDefinition.GuidedWeapon;
import com.spacesim.ship.WeaponFireControl.KinematicState;
import com.spacesim.ship.WeaponFireControl.TargetMotionEstimate;
import com.spacesim.ship.WeaponLoadoutState.FeedBinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175EWeaponAcceptanceTest {
    @Test
    void productionKineticPathConsumesCentralAmmoAndCreatesPersistentIndividualBody() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        WeaponAmmunitionCatalog ammunition = WeaponAmmunitionCatalogLoader.loadDefault();
        WeaponLauncherCatalog launchers = WeaponLauncherCatalogLoader.loadDefault();
        DerivedShipCalculator calculator = new DerivedShipCalculator(engineering);
        ConsumableState beforeLoads = railAmmunitionState(8L);
        DerivedShipState before = calculator.deriveDemonstrator(
                "fit.escort_destroyer_schema_v1", beforeLoads, DamageState.pristine());
        WeaponLoadoutState loadout = new WeaponLoadoutState(List.of(new FeedBinding(
                "weapon_spinal", "kinetic_magazine_feed", "ammo.rail_dart_150kg_v1")));
        ShipWeaponEngineeringAdapter.FittedKineticMount mount = new ShipWeaponEngineeringAdapter()
                .deriveKineticMounts(before, ammunition, launchers, loadout)
                .get(0);

        TrackState target = fireControlTrack(3_000_000d, 100_000d, 100d);
        WeaponFireControl fireControl = new WeaponFireControl();
        WeaponFireControl.KineticFireSolution solution = fireControl.planKinetic(
                mount.round(),
                target,
                new KinematicState(0d, 0d, 0d, 0d),
                new TargetMotionEstimate(0d, 250d, 4d, 0.3d),
                mount.pointingJitterRad(),
                100d);
        assertTrue(solution.allowed());
        assertTrue(solution.timeOfFlightSeconds() > 0d);

        ProjectileBody projectile = fireControl.materializeKineticProjectile(
                10_001L,
                501L,
                42_000L,
                mount.round(),
                new KinematicState(0d, 0d, 0d, 0d),
                solution);
        KineticProjectilePool pool = new KineticProjectilePool(2);
        pool.add(projectile);
        pool.advanceAll(10d);
        ProjectileBody advanced = pool.bodyAt(0);
        assertEquals(10_001L, advanced.projectileId());
        assertEquals(42_000L, advanced.spawnTick());
        assertTrue(Math.hypot(advanced.xM(), advanced.yM()) > 80_000d);

        ConsumableState afterLoads = new AmmunitionRuntime().consumeOne(
                beforeLoads,
                mount.mountId(),
                mount.launcher(),
                mount.round().massKg()).consumables();
        DerivedShipState after = calculator.deriveDemonstrator(
                "fit.escort_destroyer_schema_v1", afterLoads, DamageState.pristine());
        assertEquals(before.totalMassKg() - 150d, after.totalMassKg(), 1e-6d);
        assertEquals(before.ammunitionMassKg() - 150d, after.ammunitionMassKg(), 1e-6d);
        assertEquals(before.ammunitionCount() - 1L, after.ammunitionCount());
    }

    @Test
    void beamEnvelopeDegradesContinuouslyWithRangeInsteadOfUsingAWeaponRangeWall() {
        BeamWeapon weapon = new BeamWeapon(
                "weapon.acceptance_laser_v1",
                1.06e-6d,
                4d,
                2e-6d,
                500_000_000d,
                700_000_000d,
                220_000_000d,
                3d);
        BeamWeaponRuntime runtime = new BeamWeaponRuntime();

        BeamWeaponRuntime.BeamSolution near = runtime.plan(
                weapon, fireControlTrack(2_000_000d, 0d, 0d), 0d, 0d, 1d);
        BeamWeaponRuntime.BeamSolution far = runtime.plan(
                weapon, fireControlTrack(20_000_000d, 0d, 0d), 0d, 0d, 1d);

        assertTrue(near.allowed());
        assertTrue(far.allowed());
        assertTrue(far.effectiveSpotRadiusM() > near.effectiveSpotRadiusM());
        assertTrue(far.meanIrradianceWPerM2() < near.meanIrradianceWPerM2());
        assertEquals(weapon.wasteHeatW(), far.wasteHeatJ(), 1e-6d);
    }

    @Test
    void guidanceKillPreservesAPhysicalThreatAndDatalinkDoesNotRestoreDestroyedGuidance() {
        GuidedAmmunitionDefinition content = WeaponAmmunitionCatalogLoader.loadDefault()
                .findGuided("ammo.interceptor_1t_v1");
        GuidedWeapon definition = content.toRuntimeWeapon();
        GuidedWeaponBody launched = GuidedWeaponBody.launch(
                20_001L,
                601L,
                777L,
                definition,
                content.materialId(),
                content.shape(),
                content.lengthM(),
                content.diameterM(),
                content.impactPayloadId(),
                70_000d,
                0d,
                -900d,
                0d);
        GuidedWeaponBody residual = launched.disableGuidance();

        assertFalse(residual.guidanceAvailable());
        assertEquals(launched.currentMassKg(), residual.currentMassKg(), 1e-9d);
        assertEquals(launched.materialId(), residual.materialId());
        assertEquals(launched.kineticEnergyJ(), residual.kineticEnergyJ(), 1e-6d);

        GuidanceRuntime.GuidanceCommand command = new GuidanceRuntime().planLeadPursuit(
                residual,
                trackedTarget(50_000d, 0d),
                new TargetMotionEstimate(0d, 0d, 0d, 0d),
                GuidanceRuntime.TrackSource.DATALINK,
                1d);
        assertEquals(GuidanceRuntime.Failure.GUIDANCE_DISABLED, command.failure());

        LayeredDefenseScheduler scheduler = new LayeredDefenseScheduler();
        List<LayeredDefenseScheduler.Assignment> assignments = scheduler.schedule(
                new DefendedZone(0d, 0d, 1_000d),
                List.of(new Threat(
                        residual.bodyId(),
                        residual.xM(),
                        residual.yM(),
                        residual.velocityXMps(),
                        residual.velocityYMps(),
                        residual.currentMassKg(),
                        residual.guidanceAvailable())),
                List.of(defenseStation(1L, 0d, 0d, 1, 1L, true)));
        assertEquals(1, assignments.size());
    }

    @Test
    void formationAndFiniteResourcesChangeLayeredDefenseWithoutPdProbability() {
        LayeredDefenseScheduler scheduler = new LayeredDefenseScheduler();
        DefendedZone zone = new DefendedZone(0d, 0d, 1_000d);
        Threat fastThreat = new Threat(30_001L, 30_000d, 0d, -2_000d, 0d, 1_000d, true);

        assertEquals(0, scheduler.schedule(
                zone,
                List.of(fastThreat),
                List.of(defenseStation(2L, 0d, 0d, 1, 1L, true))).size());
        assertEquals(1, scheduler.schedule(
                zone,
                List.of(fastThreat),
                List.of(defenseStation(2L, 22_000d, 0d, 1, 1L, true))).size());

        List<Threat> wave = List.of(
                new Threat(31_001L, 100_000d, 0d, -1_000d, 0d, 1_000d, true),
                new Threat(31_002L, 102_000d, 0d, -1_000d, 0d, 1_000d, true),
                new Threat(31_003L, 104_000d, 0d, -1_000d, 0d, 1_000d, true));
        assertEquals(1, scheduler.schedule(
                zone,
                wave,
                List.of(defenseStation(3L, 0d, 0d, 1, 1L, true))).size());
        assertEquals(0, scheduler.schedule(
                zone,
                wave,
                List.of(defenseStation(3L, 0d, 0d, 3, 3L, false))).size());
    }

    @Test
    void largeSimpleWaveUsesDenseDeterministicPoolWithoutCameraOrOwnershipInputs() {
        KineticProjectilePool first = new KineticProjectilePool(8);
        KineticProjectilePool second = new KineticProjectilePool(8);
        for (int index = 0; index < 5_000; index++) {
            ProjectileBody body = new ProjectileBody(
                    index + 1L,
                    700L,
                    9_000L + index,
                    "material.high_strength_steel_v1",
                    WeaponDefinition.ProjectileShape.DART,
                    1.8d,
                    0.075d,
                    150d,
                    index * 10d,
                    0d,
                    9_000d,
                    index * 0.01d);
            first.add(body);
            second.add(body);
        }
        first.advanceAll(3d);
        second.advanceAll(3d);

        assertEquals(5_000, first.size());
        assertEquals(5_000, second.size());
        for (int index : new int[]{0, 1, 999, 2_499, 4_999}) {
            ProjectileBody left = first.bodyAt(index);
            ProjectileBody right = second.bodyAt(index);
            assertEquals(left.projectileId(), right.projectileId());
            assertEquals(Double.doubleToLongBits(left.xM()), Double.doubleToLongBits(right.xM()));
            assertEquals(Double.doubleToLongBits(left.yM()), Double.doubleToLongBits(right.yM()));
        }
    }

    private static ConsumableState railAmmunitionState(long rounds) {
        return new ConsumableState(
                0d,
                0d,
                0d,
                0d,
                List.of(new ConsumableLoad(
                        "weapon_spinal",
                        "kinetic_magazine_feed",
                        InterfaceKind.AMMUNITION,
                        rounds,
                        rounds * 150d,
                        rounds)));
    }

    private static TrackState fireControlTrack(double xM, double yM, double timestamp) {
        return new TrackState(
                777L,
                InformationState.FIRE_CONTROL,
                true,
                xM,
                yM,
                new TrackCovariance(400d, 1e-10d, 400d),
                0.98d,
                timestamp,
                2,
                4);
    }

    private static TrackState trackedTarget(double xM, double yM) {
        return new TrackState(
                777L,
                InformationState.TRACKED,
                true,
                xM,
                yM,
                new TrackCovariance(400d, 1e-10d, 400d),
                0.9d,
                0d,
                2,
                3);
    }

    private static DefenseStation defenseStation(
            long stationId,
            double xM,
            double yM,
            int channels,
            long rounds,
            boolean thermalAvailable) {
        GuidedWeapon interceptor = WeaponAmmunitionCatalogLoader.loadDefault()
                .findGuided("ammo.interceptor_1t_v1")
                .toRuntimeWeapon();
        return new DefenseStation(
                stationId,
                xM,
                yM,
                0d,
                interceptor,
                true,
                channels,
                rounds,
                thermalAvailable,
                5_000d);
    }
}
