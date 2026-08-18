package com.spacesim.ship;

import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.LiveTacticalInitialOrdnanceService.FeedLoad;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalBattlePhysicalInterceptionAcceptanceTest {
    private static final long ATTACKER_ID = 199_101L;
    private static final long DEFENDER_ID = 199_201L;

    @Test
    void schedulerLaunchBecomesSweptPhysicalInterceptionAndOrdinaryResidualBodies() {
        LiveTacticalBattleDefenseRuntime runtime = runtime();

        for (int index = 0; index < 1_600 && runtime.successfulInterceptions(DEFENDER_ID) == 0L; index++) {
            runtime.advanceOneTick();
        }

        assertTrue(runtime.ordnanceRuntime().guidedLaunches(ATTACKER_ID) > 0L,
                "attacker must create a physical STRIKE guided threat");
        assertTrue(runtime.interceptorLaunches(DEFENDER_ID) > 0L,
                "defender must physically launch an interceptor after scheduler assignment");
        assertTrue(runtime.successfulInterceptions(DEFENDER_ID) > 0L,
                "assignment alone is insufficient; a swept physical body-body contact must occur");

        List<ProjectileBody> residuals = runtime.ordnanceRuntime().weaponRuntime().projectiles();
        assertTrue(residuals.stream().anyMatch(body -> body.projectileId() >= 1_300_000_000L
                        && body.projectileId() < 1_400_000_000L),
                "intercepted strike mass must survive as an ordinary threat residual body");
        assertTrue(residuals.stream().anyMatch(body -> body.projectileId() >= 1_400_000_000L),
                "interceptor mass must survive as an ordinary interceptor residual body");
    }

    @Test
    void identicalPhysicalInterceptionRunsRemainDeterministic() {
        LiveTacticalBattleDefenseRuntime first = runtime();
        LiveTacticalBattleDefenseRuntime second = runtime();

        for (int index = 0; index < 500; index++) {
            first.advanceOneTick();
            second.advanceOneTick();
        }

        assertEquals(first.fingerprint(), second.fingerprint(),
                "same exact-local physical bodies and tick schedule must reproduce interception state");
    }

    private static LiveTacticalBattleDefenseRuntime runtime() {
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
                                2_400d,
                                700d))));
        new LiveTacticalInitialOrdnanceService().apply(
                battle.requireCombatant(DEFENDER_ID),
                List.of(
                        new FeedLoad("weapon_primary", "ammo.test_interceptor_750kg_v1", 16L),
                        new FeedLoad("weapon_secondary", "ammo.test_interceptor_750kg_v1", 16L)));
        return new LiveTacticalBattleDefenseRuntime(
                new LiveTacticalBattleOrdnanceRuntime(
                        new LiveTacticalBattleWeaponRuntime(
                                new LiveTacticalBattleControlRuntime(battle))));
    }
}
