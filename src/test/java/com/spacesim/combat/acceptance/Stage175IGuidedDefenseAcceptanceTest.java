package com.spacesim.combat.acceptance;

import com.spacesim.ship.AmmunitionRuntime;
import com.spacesim.ship.GuidanceRuntime;
import com.spacesim.ship.GuidedWeaponBody;
import com.spacesim.ship.LayeredDefenseScheduler;
import com.spacesim.ship.LayeredDefenseScheduler.DefendedZone;
import com.spacesim.ship.LayeredDefenseScheduler.DefenseStation;
import com.spacesim.ship.LayeredDefenseScheduler.Threat;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipGuidedWeaponEngineeringAdapter;
import com.spacesim.ship.TrackCovariance;
import com.spacesim.ship.TrackState;
import com.spacesim.ship.TrackState.InformationState;
import com.spacesim.ship.WeaponFireControl.TargetMotionEstimate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175IGuidedDefenseAcceptanceTest {
    @Test
    void fittedMissileConsumesPhysicalRoundGuidesAndIsAssignedToRealInterceptorResources() {
        Stage175ICombatTestContentPack pack = Stage175ICombatTestContentPack.loadDefault();
        Stage175IShipMaterializer materializer = new Stage175IShipMaterializer(pack);
        var variation = pack.manifest().findVariation("variation.ct_baseline_v1");
        var attacker = materializer.materialize("fit.ct_destroyer_missile_v1", variation);
        var defender = materializer.materialize("fit.ct_destroyer_defense_v1", variation);
        ShipGuidedWeaponEngineeringAdapter guidedAdapter = new ShipGuidedWeaponEngineeringAdapter();

        var missileMount = guidedAdapter.deriveGuidedMounts(
                attacker.derived(),
                pack.ammunition(),
                pack.launchers(),
                attacker.engineering().instanceState.weaponLoadout()).stream()
                .filter(value -> value.launcher().ammunitionInterfaceId().equals("guided_feed"))
                .findFirst().orElseThrow();
        assertEquals("ammo.ct_antiship_missile_v1", missileMount.ammunition().id());
        long attackerAmmoBefore = attacker.engineering().runtimeState.consumables().ammunitionCount();
        var consumedMissile = new AmmunitionRuntime().consumeOne(
                attacker.engineering().runtimeState.consumables(),
                missileMount.mountId(),
                missileMount.launcher(),
                missileMount.ammunition().wetMassKg());
        RuntimeState attackerState = attacker.engineering().runtimeState;
        attacker.engineering().setRuntimeState(new RuntimeState(
                consumedMissile.consumables(),
                attackerState.sharedBusEnergyJ(),
                attackerState.shipHeatStoredJ(),
                attackerState.localHeatJByMount(),
                attackerState.thrustLimitNByMount(),
                attackerState.coolantBusCapacityW(),
                attackerState.ftlCooldownSecondsByMount()));
        assertEquals(attackerAmmoBefore - 1L, attacker.engineering().runtimeState.consumables().ammunitionCount());

        var missileAmmo = missileMount.ammunition();
        GuidedWeaponBody missile = GuidedWeaponBody.launch(
                20_001L,
                1L,
                2L,
                missileAmmo.toRuntimeWeapon(),
                missileAmmo.materialId(),
                missileAmmo.shape(),
                missileAmmo.lengthM(),
                missileAmmo.diameterM(),
                missileAmmo.impactPayloadId(),
                -80_000d,
                0d,
                1_500d,
                0d);
        TrackState targetTrack = new TrackState(
                2L,
                InformationState.TRACKED,
                true,
                0d,
                0d,
                new TrackCovariance(400d, 1e-8d, 400d),
                0.95d,
                0d,
                2,
                4);
        GuidanceRuntime guidance = new GuidanceRuntime();
        var command = guidance.planLeadPursuit(
                missile,
                targetTrack,
                new TargetMotionEstimate(0d, 0d, 0d, 0d),
                GuidanceRuntime.TrackSource.DATALINK,
                2.0d);
        assertTrue(command.allowed());
        GuidedWeaponBody guidedMissile = guidance.execute(missile, command);
        assertTrue(guidedMissile.remainingPropellantKg() < missile.remainingPropellantKg());
        assertTrue(guidedMissile.velocityXMps() > missile.velocityXMps());
        assertEquals(missile.bodyId(), guidedMissile.bodyId());

        var interceptorMount = guidedAdapter.deriveGuidedMounts(
                defender.derived(),
                pack.ammunition(),
                pack.launchers(),
                defender.engineering().instanceState.weaponLoadout()).stream()
                .filter(value -> value.launcher().ammunitionInterfaceId().equals("interceptor_feed"))
                .findFirst().orElseThrow();
        assertEquals("ammo.ct_interceptor_v1", interceptorMount.ammunition().id());
        long roundsOnMount = defender.engineering().runtimeState.consumables().interfaceLoads().stream()
                .filter(value -> value.mountId().equals(interceptorMount.mountId()))
                .filter(value -> value.interfaceId().equals(interceptorMount.launcher().ammunitionInterfaceId()))
                .mapToLong(value -> value.itemCount())
                .sum();
        assertTrue(roundsOnMount > 0L);

        LayeredDefenseScheduler scheduler = new LayeredDefenseScheduler();
        Threat threat = new Threat(
                guidedMissile.bodyId(),
                guidedMissile.xM(),
                guidedMissile.yM(),
                guidedMissile.velocityXMps(),
                guidedMissile.velocityYMps(),
                guidedMissile.currentMassKg(),
                guidedMissile.guidanceAvailable());
        DefenseStation station = new DefenseStation(
                30_001L,
                0d,
                0d,
                0d,
                interceptorMount.ammunition().toRuntimeWeapon(),
                true,
                interceptorMount.launcher().supportChannelCount(),
                roundsOnMount,
                true,
                5_000d);
        var assignments = scheduler.schedule(
                new DefendedZone(0d, 0d, 2_000d),
                List.of(threat),
                List.of(station));
        assertEquals(1, assignments.size());
        assertEquals(threat.threatId(), assignments.get(0).threatId());
        assertEquals(station.stationId(), assignments.get(0).stationId());
        assertTrue(assignments.get(0).plannedInterceptSeconds() <= assignments.get(0).predictedImpactSeconds());

        DefenseStation noAmmo = new DefenseStation(
                30_002L,
                0d,
                0d,
                0d,
                interceptorMount.ammunition().toRuntimeWeapon(),
                true,
                interceptorMount.launcher().supportChannelCount(),
                0L,
                true,
                5_000d);
        assertTrue(scheduler.schedule(
                new DefendedZone(0d, 0d, 2_000d),
                List.of(threat),
                List.of(noAmmo)).isEmpty());
    }

    @Test
    void supportChannelsBoundConcurrentThreatAssignmentsWithoutPdChance() {
        Stage175ICombatTestContentPack pack = Stage175ICombatTestContentPack.loadDefault();
        Stage175IShipMaterializer materializer = new Stage175IShipMaterializer(pack);
        var defender = materializer.materialize(
                "fit.ct_destroyer_defense_v1",
                pack.manifest().findVariation("variation.ct_baseline_v1"));
        var mount = new ShipGuidedWeaponEngineeringAdapter().deriveGuidedMounts(
                defender.derived(),
                pack.ammunition(),
                pack.launchers(),
                defender.engineering().instanceState.weaponLoadout()).get(0);

        DefenseStation oneChannel = new DefenseStation(
                40_001L,
                0d,
                0d,
                0d,
                mount.ammunition().toRuntimeWeapon(),
                true,
                1,
                8L,
                true,
                4_000d);
        List<Threat> threats = List.of(
                new Threat(41_001L, -60_000d, -500d, 2_500d, 0d, 3_000d, true),
                new Threat(41_002L, -62_000d, 500d, 2_500d, 0d, 3_000d, true));
        var assignments = new LayeredDefenseScheduler().schedule(
                new DefendedZone(0d, 0d, 2_000d),
                threats,
                List.of(oneChannel));
        assertEquals(1, assignments.size());
    }
}
