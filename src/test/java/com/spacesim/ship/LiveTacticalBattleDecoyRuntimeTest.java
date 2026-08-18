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

class LiveTacticalBattleDecoyRuntimeTest {
    private static final long SOURCE_ID = 199_601L;
    private static final long OPPONENT_ID = 199_602L;
    private static final String DECOY_ID = "ammo.test_radar_repeater_decoy_300kg_v1";

    @Test
    void decoyLoadedGuidedFeedIsInvisibleToOrdinaryStrikeFire() {
        LiveTacticalBattleRuntimeState battle = decoyBattle();
        LiveTacticalBattleOrdnanceRuntime ordnance = ordnanceRuntime(battle);

        for (int index = 0; index < 200; index++) {
            ordnance.advanceOneTick();
        }

        assertEquals(0L, ordnance.guidedLaunches(SOURCE_ID),
                "DECOY role must never be consumed by ordinary STRIKE target fire");
        assertEquals(8L, guidedRounds(battle.requireCombatant(SOURCE_ID)),
                "ordinary strike runtime must leave decoy stores physically untouched");
    }

    @Test
    void deploymentConsumesOneRoundCommitsCooldownAndMaterializesPhysicalBody() {
        LiveTacticalBattleRuntimeState battle = decoyBattle();
        LiveTacticalBattleOrdnanceRuntime ordnance = ordnanceRuntime(battle);
        LiveTacticalBattleDecoyRuntime decoys = new LiveTacticalBattleDecoyRuntime(ordnance);
        long before = guidedRounds(battle.requireCombatant(SOURCE_ID));

        assertTrue(decoys.deployOne(SOURCE_ID, "weapon_primary", 1d, 0d));

        assertEquals(1L, decoys.deployments(SOURCE_ID));
        assertEquals(before - 1L, guidedRounds(battle.requireCombatant(SOURCE_ID)),
                "one physical deployment must consume exactly one itemized decoy round");
        assertEquals(1, decoys.decoyBodies().size());
        GuidedWeaponBody body = decoys.decoyBodies().get(0);
        assertEquals(DECOY_ID, body.definition().id());
        assertEquals(300d, body.currentMassKg(), 1e-9d);
        assertFalse(battle.requireCombatant(SOURCE_ID).engineering().instanceState
                .weaponMountRuntime().cooldownSecondsByMount().isEmpty(),
                "deployment must commit ordinary fitted launcher cooldown state");
        assertFalse(decoys.deployOne(SOURCE_ID, "weapon_primary", 1d, 0d),
                "same mount cannot bypass its physical cycle for a second immediate decoy");
    }

    @Test
    void physicalDecoyBurnsFinitePropellantAndMovesOnAuthoritativeTicks() {
        LiveTacticalBattleRuntimeState battle = decoyBattle();
        LiveTacticalBattleOrdnanceRuntime ordnance = ordnanceRuntime(battle);
        LiveTacticalBattleDecoyRuntime decoys = new LiveTacticalBattleDecoyRuntime(ordnance);
        assertTrue(decoys.deployOne(SOURCE_ID, "weapon_primary", 0d, 1d));
        GuidedWeaponBody before = decoys.decoyBodies().get(0);

        ordnance.advanceOneTick();
        decoys.advanceToCurrentTick();

        GuidedWeaponBody after = decoys.decoyBodies().get(0);
        assertTrue(after.remainingPropellantKg() < before.remainingPropellantKg(),
                "decoy separation/autopilot burn must consume real onboard propellant");
        assertTrue(after.remainingPoweredBurnSeconds() < before.remainingPoweredBurnSeconds());
        assertTrue(Math.hypot(after.xM() - before.xM(), after.yM() - before.yM()) > 0d,
                "decoy must exist as an independently moving physical body");
        assertTrue(after.velocityYMps() > before.velocityYMps(),
                "caller-selected separation direction must be executed through physical rocket burn");
    }

    @Test
    void identicalDecoyDeploymentSequenceReplaysDeterministically() {
        LiveTacticalBattleDecoyRuntime first = decoyRuntime(decoyBattle());
        LiveTacticalBattleDecoyRuntime second = decoyRuntime(decoyBattle());
        assertTrue(first.deployOne(SOURCE_ID, "weapon_primary", 1d, 0.25d));
        assertTrue(second.deployOne(SOURCE_ID, "weapon_primary", 1d, 0.25d));

        for (int index = 0; index < 120; index++) {
            first.ordnanceRuntime().advanceOneTick();
            second.ordnanceRuntime().advanceOneTick();
            first.advanceToCurrentTick();
            second.advanceToCurrentTick();
        }

        assertEquals(first.fingerprint(), second.fingerprint());
    }

    private static LiveTacticalBattleRuntimeState decoyBattle() {
        LiveTacticalBattleRuntimeState battle = new LiveTacticalBattleRuntimeState(
                new LiveTacticalBattleScenario(List.of(
                        new CombatantSpec(SOURCE_ID, Side.ALPHA, DoctrineId.B_MISSILE_STRIKE, 300d, 700d),
                        new CombatantSpec(OPPONENT_ID, Side.BETA, DoctrineId.B_MISSILE_STRIKE, 2_200d, 700d))));
        new LiveTacticalInitialOrdnanceService().apply(
                battle.requireCombatant(SOURCE_ID),
                List.of(
                        new FeedLoad("weapon_primary", DECOY_ID, 4L),
                        new FeedLoad("weapon_secondary", DECOY_ID, 4L)));
        return battle;
    }

    private static LiveTacticalBattleOrdnanceRuntime ordnanceRuntime(LiveTacticalBattleRuntimeState battle) {
        return new LiveTacticalBattleOrdnanceRuntime(
                new LiveTacticalBattleWeaponRuntime(
                        new LiveTacticalBattleControlRuntime(battle)));
    }

    private static LiveTacticalBattleDecoyRuntime decoyRuntime(LiveTacticalBattleRuntimeState battle) {
        return new LiveTacticalBattleDecoyRuntime(ordnanceRuntime(battle));
    }

    private static long guidedRounds(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        return combatant.engineering().runtimeState.consumables().interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .filter(value -> "guided_feed".equals(value.interfaceId()))
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }
}
