package com.spacesim.ship;

import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalBattleGuidedImpactAcceptanceTest {
    @Test
    void guidedBodyPhysicallyIntersectsMovingShipAndUsesSharedProtectionState() {
        long missileShipId = 194_101L;
        long defenderId = 194_201L;
        LiveTacticalBattleRuntimeState battle = new LiveTacticalBattleRuntimeState(
                new LiveTacticalBattleScenario(List.of(
                        new CombatantSpec(
                                missileShipId,
                                Side.ALPHA,
                                DoctrineId.B_MISSILE_STRIKE,
                                260d,
                                700d),
                        new CombatantSpec(
                                defenderId,
                                Side.BETA,
                                DoctrineId.D_DEFENSIVE_EW,
                                1_690d,
                                700d))));
        LiveTacticalBattleWeaponRuntime weaponRuntime = new LiveTacticalBattleWeaponRuntime(
                new LiveTacticalBattleControlRuntime(battle));
        LiveTacticalBattleOrdnanceRuntime runtime = new LiveTacticalBattleOrdnanceRuntime(weaponRuntime);
        double initialShieldReserve = shieldReserve(battle.requireCombatant(defenderId));

        for (int index = 0; index < 1_200 && runtime.guidedImpactsOn(defenderId) == 0L; index++) {
            runtime.advanceOneTick();
        }

        assertTrue(runtime.guidedLaunches(missileShipId) > 0L,
                "missile doctrine must materialize a physical guided body first");
        assertTrue(runtime.guidedImpactsOn(defenderId) > 0L,
                "guided body must intersect the moving production hull rather than resolve a hit chance");
        assertTrue(weaponRuntime.impactsOn(defenderId) >= runtime.guidedImpactsOn(defenderId),
                "guided impact must be counted by the same shared physical protection owner");
        assertTrue(shieldReserve(battle.requireCombatant(defenderId)) < initialShieldReserve,
                "the guided collision must physically consume persistent fitted shield reserve");
    }

    @Test
    void externalPhysicalImpactSeamUsesProductionProtectionAndResidualPool() {
        LiveTacticalBattleRuntimeState battle = new LiveTacticalBattleRuntimeState(
                LiveTacticalBattleScenario.legacyDuel());
        LiveTacticalBattleWeaponRuntime runtime = new LiveTacticalBattleWeaponRuntime(
                new LiveTacticalBattleControlRuntime(battle));
        var target = battle.requireCombatant(LiveTacticalSimulationSession.TARGET_ENTITY_ID);
        double shieldBefore = shieldReserve(target);
        ProjectileBody body = new ProjectileBody(
                1_200_000_001L,
                LiveTacticalSimulationSession.ATTACKER_ENTITY_ID,
                0L,
                "material.stage17_5i_doctrine_alloy_v1",
                WeaponDefinition.ProjectileShape.DART,
                1.5d,
                0.08d,
                150d,
                target.transform().position.x,
                target.transform().position.y,
                1_500d,
                0d);

        KineticProtectionRuntime.Result result = runtime.resolveExternalPhysicalImpact(
                target.spec().entityId(),
                body,
                target.transform().position.x,
                target.transform().position.y);

        assertEquals(1L, runtime.impactsOn(target.spec().entityId()));
        assertTrue(result.shieldInteraction() != null || result.armorReached(),
                "external body must enter the ordinary shield/material protection chain");
        assertTrue(shieldReserve(target) < shieldBefore || result.armorReached());
        if (result.postProtectionProjectile() != null) {
            runtime.acceptExternalProjectile(result.postProtectionProjectile());
            assertTrue(runtime.projectiles().stream()
                    .anyMatch(projectile -> projectile.projectileId() == body.projectileId()),
                    "surviving external residual must enter the one production projectile pool");
        }
    }

    @Test
    void identicalGuidedImpactDuelsRemainDeterministic() {
        LiveTacticalBattleOrdnanceRuntime first = duelRuntime();
        LiveTacticalBattleOrdnanceRuntime second = duelRuntime();

        for (int index = 0; index < 500; index++) {
            first.advanceOneTick();
            second.advanceOneTick();
        }

        assertEquals(first.fingerprint(), second.fingerprint(),
                "guided launch, finite burn, swept collision and shared protection must remain deterministic");
    }

    private static LiveTacticalBattleOrdnanceRuntime duelRuntime() {
        LiveTacticalBattleScenario scenario = new LiveTacticalBattleScenario(List.of(
                new CombatantSpec(194_301L, Side.ALPHA, DoctrineId.B_MISSILE_STRIKE, 260d, 700d),
                new CombatantSpec(194_401L, Side.BETA, DoctrineId.D_DEFENSIVE_EW, 1_690d, 700d)));
        LiveTacticalBattleRuntimeState battle = new LiveTacticalBattleRuntimeState(scenario);
        return new LiveTacticalBattleOrdnanceRuntime(
                new LiveTacticalBattleWeaponRuntime(
                        new LiveTacticalBattleControlRuntime(battle)));
    }

    private static double shieldReserve(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        return combatant.engineering().instanceState.shieldStatesByMount().values().stream()
                .mapToDouble(ShieldFieldRuntime.State::reserveJ)
                .sum();
    }
}
