package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.LiveTacticalInitialOrdnanceService.FeedLoad;
import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalBattleDefenseRuntimeTest {
    private static final long ATTACKER_ID = 198_101L;
    private static final long DEFENDER_ID = 198_201L;

    @Test
    void layeredSchedulerConsumesRealInterceptorRoundAndMaterializesPhysicalBody() {
        LiveTacticalBattleRuntimeState battle = interceptorDuelState();
        long initialRounds = guidedRounds(battle.requireCombatant(DEFENDER_ID));
        LiveTacticalBattleDefenseRuntime runtime = defenseRuntime(battle);

        for (int index = 0; index < 800 && runtime.interceptorLaunches(DEFENDER_ID) == 0L; index++) {
            runtime.advanceOneTick();
        }

        assertTrue(runtime.ordnanceRuntime().guidedLaunches(ATTACKER_ID) > 0L,
                "attacker must first materialize a physical STRIKE threat");
        assertTrue(runtime.interceptorLaunches(DEFENDER_ID) > 0L,
                "production LayeredDefenseScheduler must authorize at least one physical interceptor launch");
        assertFalse(runtime.interceptorBodies().isEmpty());
        assertEquals(
                initialRounds - runtime.interceptorLaunches(DEFENDER_ID),
                guidedRounds(battle.requireCombatant(DEFENDER_ID)),
                "every defensive launch must remove exactly one itemized physical interceptor round");
        assertFalse(battle.requireCombatant(DEFENDER_ID).engineering().instanceState
                .weaponMountRuntime().cooldownSecondsByMount().isEmpty(),
                "defensive launch must commit the ordinary persistent launcher cycle");
    }

    @Test
    void firstObservedPositionDoesNotInventVelocityOrImmediatelyAuthorizeDefense() {
        LiveTacticalBattleDefenseRuntime runtime = defenseRuntime(interceptorDuelState());

        while (runtime.tick() < 800L
                && runtime.observationRuntime().tracksForObserver(DEFENDER_ID).isEmpty()) {
            runtime.advanceOneTick();
        }

        var first = runtime.observationRuntime().tracksForObserver(DEFENDER_ID).stream()
                .findFirst()
                .orElseThrow();
        assertTrue(first.track().positionKnown(),
                "production radar must solve a position before defense can reason about geometry");
        assertFalse(first.velocityKnown(),
                "one observed Cartesian position must not manufacture an exact missile velocity");
        assertEquals(0L, runtime.interceptorLaunches(DEFENDER_ID),
                "layered defense must wait for actor-bounded kinematics instead of reading physical body velocity");

        while (runtime.tick() < 800L && runtime.interceptorLaunches(DEFENDER_ID) == 0L) {
            runtime.advanceOneTick();
        }

        var tracked = runtime.observationRuntime().tracksForObserver(DEFENDER_ID).stream()
                .filter(value -> value.velocityKnown())
                .findFirst()
                .orElseThrow();
        assertTrue(tracked.track().informationState() == TrackState.InformationState.TRACKED
                        || tracked.track().informationState() == TrackState.InformationState.FIRE_CONTROL,
                "defense launch must be backed by a tactical-quality observer-local track");
        assertTrue(runtime.interceptorLaunches(DEFENDER_ID) > 0L);
    }

    @Test
    void destroyedRadarCannotLaunchInterceptorsAgainstPhysicallyPresentThreat() {
        LiveTacticalBattleRuntimeState battle = interceptorDuelState();
        destroyRadar(battle.requireCombatant(DEFENDER_ID));
        LiveTacticalBattleDefenseRuntime runtime = defenseRuntime(battle);

        for (int index = 0; index < 500; index++) {
            runtime.advanceOneTick();
        }

        assertTrue(runtime.ordnanceRuntime().guidedLaunches(ATTACKER_ID) > 0L,
                "a real hostile guided threat must exist during this acceptance case");
        assertTrue(runtime.observationRuntime().tracksForObserver(DEFENDER_ID).isEmpty(),
                "destroyed fitted radar must not create observer-local missile tracks");
        assertEquals(0L, runtime.interceptorLaunches(DEFENDER_ID),
                "defense must not fall back to exact physical missile state when sensing is unavailable");
    }

    @Test
    void interceptorLoadedGuidedMountsAreNeverConsumedByOrdinaryShipStrikeRuntime() {
        LiveTacticalBattleRuntimeState battle = interceptorDuelState();
        long initialRounds = guidedRounds(battle.requireCombatant(DEFENDER_ID));
        LiveTacticalBattleDefenseRuntime runtime = defenseRuntime(battle);

        for (int index = 0; index < 200; index++) {
            runtime.advanceOneTick();
        }

        assertEquals(0L, runtime.ordnanceRuntime().guidedLaunches(DEFENDER_ID),
                "INTERCEPTOR ammunition must remain invisible to ordinary ship-target guided fire");
        assertEquals(
                initialRounds - runtime.interceptorLaunches(DEFENDER_ID),
                guidedRounds(battle.requireCombatant(DEFENDER_ID)),
                "only defensive interceptor launches may consume the defender's guided feeds");
    }

    @Test
    void materializedInterceptorUsesFiniteGuidancePropellantAndMoves() {
        LiveTacticalBattleDefenseRuntime runtime = defenseRuntime(interceptorDuelState());
        while (runtime.tick() < 800L && runtime.interceptorBodies().isEmpty()) {
            runtime.advanceOneTick();
        }
        GuidedWeaponBody first = runtime.interceptorBodies().stream().findFirst().orElseThrow();
        long bodyId = first.bodyId();
        double propellantBefore = first.remainingPropellantKg();
        double xBefore = first.xM();
        double yBefore = first.yM();

        runtime.advanceOneTick();

        GuidedWeaponBody after = runtime.interceptorBodies().stream()
                .filter(body -> body.bodyId() == bodyId)
                .findFirst()
                .orElseThrow();
        assertTrue(after.remainingPropellantKg() < propellantBefore,
                "interceptor guidance must burn real onboard propellant from observer-local kinematics");
        assertTrue(Math.hypot(after.xM() - xBefore, after.yM() - yBefore) > 0d,
                "interceptor must propagate as an independent physical guided body");
    }

    @Test
    void identicalLayeredDefenseDuelRunsRemainDeterministic() {
        LiveTacticalBattleDefenseRuntime first = defenseRuntime(interceptorDuelState());
        LiveTacticalBattleDefenseRuntime second = defenseRuntime(interceptorDuelState());

        for (int index = 0; index < 300; index++) {
            first.advanceOneTick();
            second.advanceOneTick();
        }

        assertEquals(first.fingerprint(), second.fingerprint(),
                "same physical threats/stores/ticks must reproduce observed assignments and interceptor bodies");
    }

    private static LiveTacticalBattleRuntimeState interceptorDuelState() {
        LiveTacticalBattleRuntimeState battle = new LiveTacticalBattleRuntimeState(
                new LiveTacticalBattleScenario(List.of(
                        new CombatantSpec(
                                ATTACKER_ID,
                                Side.ALPHA,
                                DoctrineId.B_MISSILE_STRIKE,
                                260d,
                                700d),
                        new CombatantSpec(
                                DEFENDER_ID,
                                Side.BETA,
                                DoctrineId.B_MISSILE_STRIKE,
                                1_690d,
                                700d))));
        new LiveTacticalInitialOrdnanceService().apply(
                battle.requireCombatant(DEFENDER_ID),
                List.of(
                        new FeedLoad("weapon_primary", "ammo.test_interceptor_750kg_v1", 8L),
                        new FeedLoad("weapon_secondary", "ammo.test_interceptor_750kg_v1", 8L)));
        return battle;
    }

    private static LiveTacticalBattleDefenseRuntime defenseRuntime(LiveTacticalBattleRuntimeState battle) {
        return new LiveTacticalBattleDefenseRuntime(
                new LiveTacticalBattleOrdnanceRuntime(
                        new LiveTacticalBattleWeaponRuntime(
                                new LiveTacticalBattleControlRuntime(battle))));
    }

    private static void destroyRadar(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        var engineering = combatant.engineering();
        var instance = engineering.instanceState;
        Snapshot damage = new Snapshot(
                instance.damage().compartmentIntegrityById(),
                new DamageState(Map.of("utility_sensor", 0d)));
        engineering.setInstanceState(new ShipInstanceRuntimeState(
                damage,
                instance.shieldStatesByMount(),
                instance.maintenance(),
                instance.weaponLoadout(),
                instance.weaponMountRuntime()));
    }

    private static long guidedRounds(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        return combatant.engineering().runtimeState.consumables().interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .filter(value -> "guided_feed".equals(value.interfaceId()))
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }
}
