package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.LiveTacticalInitialOrdnanceService.FeedLoad;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.List;

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
                "interceptor guidance must burn real onboard propellant");
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
                "same physical threats/stores/ticks must reproduce layered assignments and interceptor bodies");
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

    private static long guidedRounds(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        return combatant.engineering().runtimeState.consumables().interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .filter(value -> "guided_feed".equals(value.interfaceId()))
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }
}
