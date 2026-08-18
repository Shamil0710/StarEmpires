package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.LiveTacticalInitialOrdnanceService.FeedLoad;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalBattleDeceptionRuntimeTest {
    private static final long SOURCE_ID = 199_801L;
    private static final long TARGET_ID = 199_802L;
    private static final String STRIKE_ID = "ammo.test_anti_ship_missile_2t_v1";
    private static final String DECOY_ID = "ammo.test_radar_repeater_decoy_300kg_v1";

    @Test
    void aiAccompaniesOwnActiveStrikeWithOneFiniteActorBoundedDecoy() {
        LiveTacticalBattleRuntimeState battle = mixedStrikeDecoyBattle();
        LiveTacticalBattleDeceptionRuntime runtime = runtime(battle);
        long decoyRoundsBefore = roundsOnMount(battle.requireCombatant(SOURCE_ID), "weapon_secondary");

        while (runtime.tick() < 800L && runtime.automaticDeployments(SOURCE_ID) == 0L) {
            runtime.advanceOneTick();
        }

        assertTrue(runtime.ordnanceRuntime().guidedLaunches(SOURCE_ID) > 0L,
                "automatic deception requires a real active STRIKE launch first");
        assertEquals(1L, runtime.automaticDeployments(SOURCE_ID));
        assertEquals(1L, runtime.decoyRuntime().deployments(SOURCE_ID));
        assertEquals(decoyRoundsBefore - 1L, roundsOnMount(battle.requireCombatant(SOURCE_ID), "weapon_secondary"),
                "automatic policy must consume exactly one real decoy round through the physical owner");
        assertEquals(1L, runtime.decoyRuntime().decoyBodies().stream()
                .filter(body -> body.sourceEntityId() == SOURCE_ID)
                .count(),
                "provisional anti-spam policy allows only one active decoy per source");

        var control = runtime.ordnanceRuntime().weaponRuntime().controlRuntime().controlState(SOURCE_ID);
        assertTrue(control.intent().targetSelected());
        assertEquals(TARGET_ID, control.intent().targetId());
        assertTrue(battle.visibleContacts(SOURCE_ID).stream()
                        .anyMatch(contact -> contact.track().targetId() == TARGET_ID
                                && contact.track().positionKnown()),
                "deployment direction must be supported by the source actor's own visible target domain");
    }

    @Test
    void decoyOnlyLoadNeverSelfTriggersWithoutAnOwnStrikeBody() {
        LiveTacticalBattleRuntimeState battle = decoyOnlyBattle();
        LiveTacticalBattleDeceptionRuntime runtime = runtime(battle);
        long before = roundsOnMount(battle.requireCombatant(SOURCE_ID), "weapon_primary")
                + roundsOnMount(battle.requireCombatant(SOURCE_ID), "weapon_secondary");

        for (int index = 0; index < 320; index++) {
            runtime.advanceOneTick();
        }

        assertEquals(0L, runtime.ordnanceRuntime().guidedLaunches(SOURCE_ID));
        assertEquals(0L, runtime.automaticDeployments(SOURCE_ID),
                "actor-local target knowledge alone must not manufacture a deception attack without own STRIKE ordnance");
        assertEquals(before,
                roundsOnMount(battle.requireCombatant(SOURCE_ID), "weapon_primary")
                        + roundsOnMount(battle.requireCombatant(SOURCE_ID), "weapon_secondary"));
    }

    @Test
    void automaticDeceptionSequenceReplaysDeterministically() {
        LiveTacticalBattleDeceptionRuntime first = runtime(mixedStrikeDecoyBattle());
        LiveTacticalBattleDeceptionRuntime second = runtime(mixedStrikeDecoyBattle());

        for (int index = 0; index < 500; index++) {
            first.advanceOneTick();
            second.advanceOneTick();
        }

        assertEquals(first.fingerprint(), second.fingerprint());
    }

    private static LiveTacticalBattleRuntimeState mixedStrikeDecoyBattle() {
        LiveTacticalBattleRuntimeState battle = baseBattle();
        new LiveTacticalInitialOrdnanceService().apply(
                battle.requireCombatant(SOURCE_ID),
                List.of(
                        new FeedLoad("weapon_primary", STRIKE_ID, 4L),
                        new FeedLoad("weapon_secondary", DECOY_ID, 4L)));
        return battle;
    }

    private static LiveTacticalBattleRuntimeState decoyOnlyBattle() {
        LiveTacticalBattleRuntimeState battle = baseBattle();
        new LiveTacticalInitialOrdnanceService().apply(
                battle.requireCombatant(SOURCE_ID),
                List.of(
                        new FeedLoad("weapon_primary", DECOY_ID, 4L),
                        new FeedLoad("weapon_secondary", DECOY_ID, 4L)));
        return battle;
    }

    private static LiveTacticalBattleRuntimeState baseBattle() {
        return new LiveTacticalBattleRuntimeState(new LiveTacticalBattleScenario(List.of(
                new CombatantSpec(SOURCE_ID, Side.ALPHA, DoctrineId.B_MISSILE_STRIKE, 300d, 700d),
                new CombatantSpec(TARGET_ID, Side.BETA, DoctrineId.A_KINETIC_LINE, 2_200d, 700d))));
    }

    private static LiveTacticalBattleDeceptionRuntime runtime(LiveTacticalBattleRuntimeState battle) {
        LiveTacticalBattleOrdnanceRuntime ordnance = new LiveTacticalBattleOrdnanceRuntime(
                new LiveTacticalBattleWeaponRuntime(
                        new LiveTacticalBattleControlRuntime(battle)));
        return new LiveTacticalBattleDeceptionRuntime(ordnance);
    }

    private static long roundsOnMount(
            LiveTacticalBattleRuntimeState.CombatantRuntime combatant,
            String mountId) {
        return combatant.engineering().runtimeState.consumables().interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .filter(value -> value.mountId().equals(mountId))
                .filter(value -> "guided_feed".equals(value.interfaceId()))
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }
}
