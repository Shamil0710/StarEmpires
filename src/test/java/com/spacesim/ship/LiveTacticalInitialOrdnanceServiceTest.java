package com.spacesim.ship;

import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.LiveTacticalInitialOrdnanceService.FeedLoad;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalInitialOrdnanceServiceTest {
    @Test
    void missileFitCanStartWithAuthoredInterceptorRoundsWithoutChangingFitOrModuleIdentity() {
        long entityId = 196_101L;
        LiveTacticalBattleRuntimeState battle = battle(entityId, DoctrineId.B_MISSILE_STRIKE);
        var combatant = battle.requireCombatant(entityId);
        var service = new LiveTacticalInitialOrdnanceService();
        String hullIdBefore = combatant.engineering().fit.hullId();
        List<String> modulesBefore = combatant.engineering().fit.installedModules().stream()
                .map(value -> value.mountId() + "=" + value.moduleId())
                .toList();

        service.apply(combatant, List.of(
                new FeedLoad("weapon_primary", "ammo.test_interceptor_750kg_v1", 8L),
                new FeedLoad("weapon_secondary", "ammo.test_interceptor_750kg_v1", 6L)));

        assertEquals(hullIdBefore, combatant.engineering().fit.hullId());
        assertEquals(modulesBefore, combatant.engineering().fit.installedModules().stream()
                .map(value -> value.mountId() + "=" + value.moduleId())
                .toList());
        assertEquals(8L, rounds(combatant, "weapon_primary", "guided_feed"));
        assertEquals(6L, rounds(combatant, "weapon_secondary", "guided_feed"));
        assertEquals(8d * 750d, mass(combatant, "weapon_primary", "guided_feed"), 1e-9d);
        assertEquals(6d * 750d, mass(combatant, "weapon_secondary", "guided_feed"), 1e-9d);
        assertEquals(
                "ammo.test_interceptor_750kg_v1",
                combatant.engineering().instanceState.weaponLoadout()
                        .ammunitionContentId("weapon_primary", "guided_feed")
                        .orElseThrow());
    }

    @Test
    void zeroCountIsAValidExplicitDepletedInitialCondition() {
        long entityId = 196_201L;
        LiveTacticalBattleRuntimeState battle = battle(entityId, DoctrineId.E_BALANCED_CONTROL);
        var combatant = battle.requireCombatant(entityId);

        new LiveTacticalInitialOrdnanceService().apply(
                combatant,
                List.of(new FeedLoad("weapon_secondary", "ammo.test_anti_ship_missile_2t_v1", 0L)));

        assertEquals(0L, rounds(combatant, "weapon_secondary", "guided_feed"));
        assertEquals(0d, mass(combatant, "weapon_secondary", "guided_feed"), 1e-12d);
        assertEquals(
                "ammo.test_anti_ship_missile_2t_v1",
                combatant.engineering().instanceState.weaponLoadout()
                        .ammunitionContentId("weapon_secondary", "guided_feed")
                        .orElseThrow());
    }

    @Test
    void incompatibleFamilyAndInterfaceCapacityAreRejectedBeforeMutation() {
        long entityId = 196_301L;
        LiveTacticalBattleRuntimeState battle = battle(entityId, DoctrineId.B_MISSILE_STRIKE);
        var combatant = battle.requireCombatant(entityId);
        long roundsBefore = rounds(combatant, "weapon_primary", "guided_feed");
        String identityBefore = combatant.engineering().instanceState.weaponLoadout()
                .ammunitionContentId("weapon_primary", "guided_feed")
                .orElseThrow();
        var service = new LiveTacticalInitialOrdnanceService();

        assertThrows(IllegalArgumentException.class, () -> service.apply(combatant, List.of(
                new FeedLoad("weapon_primary", "ammo.test_kinetic_dart_150kg_v1", 1L))));
        assertThrows(IllegalArgumentException.class, () -> service.apply(combatant, List.of(
                new FeedLoad("weapon_primary", "ammo.test_interceptor_750kg_v1", 65L))));

        assertEquals(roundsBefore, rounds(combatant, "weapon_primary", "guided_feed"));
        assertEquals(identityBefore, combatant.engineering().instanceState.weaponLoadout()
                .ammunitionContentId("weapon_primary", "guided_feed")
                .orElseThrow());
    }

    @Test
    void duplicateMountRequestsAreRejectedAtomically() {
        long entityId = 196_401L;
        LiveTacticalBattleRuntimeState battle = battle(entityId, DoctrineId.B_MISSILE_STRIKE);
        var combatant = battle.requireCombatant(entityId);
        long roundsBefore = rounds(combatant, "weapon_primary", "guided_feed");
        var service = new LiveTacticalInitialOrdnanceService();

        assertThrows(IllegalArgumentException.class, () -> service.apply(combatant, List.of(
                new FeedLoad("weapon_primary", "ammo.test_interceptor_750kg_v1", 4L),
                new FeedLoad("weapon_primary", "ammo.test_interceptor_750kg_v1", 5L))));

        assertEquals(roundsBefore, rounds(combatant, "weapon_primary", "guided_feed"));
        assertTrue(combatant.engineering().instanceState.weaponMountRuntime()
                .cooldownSecondsByMount().isEmpty());
    }

    private static LiveTacticalBattleRuntimeState battle(long entityId, DoctrineId doctrineId) {
        return new LiveTacticalBattleRuntimeState(new LiveTacticalBattleScenario(List.of(
                new CombatantSpec(entityId, Side.ALPHA, doctrineId, 0d, 0d),
                new CombatantSpec(entityId + 10_000L, Side.BETA, DoctrineId.A_KINETIC_LINE, 10_000d, 0d))));
    }

    private static long rounds(
            LiveTacticalBattleRuntimeState.CombatantRuntime combatant,
            String mountId,
            String interfaceId) {
        return combatant.engineering().runtimeState.consumables().interfaceLoads().stream()
                .filter(value -> value.kind() == com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind.AMMUNITION)
                .filter(value -> value.mountId().equals(mountId) && value.interfaceId().equals(interfaceId))
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }

    private static double mass(
            LiveTacticalBattleRuntimeState.CombatantRuntime combatant,
            String mountId,
            String interfaceId) {
        return combatant.engineering().runtimeState.consumables().interfaceLoads().stream()
                .filter(value -> value.kind() == com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind.AMMUNITION)
                .filter(value -> value.mountId().equals(mountId) && value.interfaceId().equals(interfaceId))
                .mapToDouble(ShipEngineeringState.ConsumableLoad::massKg)
                .sum();
    }
}
