package com.spacesim.ship;

import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.LiveTacticalInitialOrdnanceService.FeedLoad;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalFormationOrdnanceObservationTest {
    private static final long DECOY_SOURCE_ID = 199_951L;
    private static final long JAMMER_ID = 199_952L;
    private static final long OBSERVER_ONE_ID = 199_953L;
    private static final long OBSERVER_TWO_ID = 199_954L;
    private static final String DECOY_ID = "ammo.test_radar_repeater_decoy_300kg_v1";

    @Test
    void jammedActiveRadarCanRecoverCartesianOrdnanceTrackFromSharedPassiveBearings() {
        Fixture fixture = fixture(false);
        long bodyId = fixture.decoys().decoyBodies().get(0).bodyId();

        advanceObservedToTick(fixture, 2L);

        assertTrue(fixture.observation().lastScanDiagnostics(OBSERVER_ONE_ID).measurementsProduced() == 0,
                "300 MW hostile noise jammer must still suppress the local active-radar ordnance return");
        assertTrue(fixture.observation().lastScanDiagnostics(OBSERVER_TWO_ID).measurementsProduced() == 0,
                "formation recovery must not secretly come from a second active-radar success");

        var first = fixture.observation().track(OBSERVER_ONE_ID, bodyId);
        var second = fixture.observation().track(OBSERVER_TWO_ID, bodyId);
        assertNotNull(first);
        assertNotNull(second);
        assertTrue(first.track().positionKnown());
        assertTrue(second.track().positionKnown());
        assertTrue(first.track().contributingObservers() >= 2);
        assertTrue(second.track().contributingObservers() >= 2);
        assertFalse(first.velocityKnown(),
                "one formation scan may triangulate position but cannot manufacture velocity");
    }

    @Test
    void destroyedDatalinkLeavesEachPassiveObserverBearingOnlyUnderSameJamming() {
        Fixture fixture = fixture(true);
        long bodyId = fixture.decoys().decoyBodies().get(0).bodyId();

        advanceObservedToTick(fixture, 2L);

        var first = fixture.observation().track(OBSERVER_ONE_ID, bodyId);
        var second = fixture.observation().track(OBSERVER_TWO_ID, bodyId);
        assertNotNull(first);
        assertNotNull(second);
        assertFalse(first.track().positionKnown(),
                "destroyed recipient/source datalinks must not grant range by faction membership");
        assertFalse(second.track().positionKnown(),
                "the other observer must also remain bearing-only when its only peer cannot transmit");
        assertTrue(first.track().contributingObservers() == 1);
        assertTrue(second.track().contributingObservers() == 1);
    }

    private static Fixture fixture(boolean destroyFirstObserverDatalink) {
        LiveTacticalBattleRuntimeState battle = new LiveTacticalBattleRuntimeState(
                new LiveTacticalBattleScenario(List.of(
                        new CombatantSpec(
                                DECOY_SOURCE_ID,
                                Side.ALPHA,
                                DoctrineId.B_MISSILE_STRIKE,
                                300d,
                                700d),
                        new CombatantSpec(
                                JAMMER_ID,
                                Side.ALPHA,
                                DoctrineId.D_DEFENSIVE_EW,
                                300d,
                                820d),
                        new CombatantSpec(
                                OBSERVER_ONE_ID,
                                Side.BETA,
                                DoctrineId.A_KINETIC_LINE,
                                1_690d,
                                600d),
                        new CombatantSpec(
                                OBSERVER_TWO_ID,
                                Side.BETA,
                                DoctrineId.A_KINETIC_LINE,
                                1_690d,
                                800d))));
        new LiveTacticalInitialOrdnanceService().apply(
                battle.requireCombatant(DECOY_SOURCE_ID),
                List.of(
                        new FeedLoad("weapon_primary", DECOY_ID, 2L),
                        new FeedLoad("weapon_secondary", DECOY_ID, 2L)));
        if (destroyFirstObserverDatalink) {
            destroyDatalink(battle.requireCombatant(OBSERVER_ONE_ID));
        }

        LiveTacticalBattleOrdnanceRuntime ordnance = new LiveTacticalBattleOrdnanceRuntime(
                new LiveTacticalBattleWeaponRuntime(
                        new LiveTacticalBattleControlRuntime(battle)));
        LiveTacticalBattleDecoyRuntime decoys = new LiveTacticalBattleDecoyRuntime(ordnance);
        assertTrue(decoys.deployOne(DECOY_SOURCE_ID, "weapon_primary", 1d, 0d));
        LiveTacticalOrdnanceObservationRuntime observation =
                new LiveTacticalOrdnanceObservationRuntime(ordnance, decoys);
        return new Fixture(ordnance, decoys, observation);
    }

    private static void destroyDatalink(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        var engineering = combatant.engineering();
        ShipInstanceRuntimeState instance = engineering.instanceState;
        ShipDamageRuntime.Snapshot damage = new ShipDamageRuntime.Snapshot(
                instance.damage().compartmentIntegrityById(),
                new DamageState(Map.of("utility_datalink", 0d)));
        engineering.setInstanceState(new ShipInstanceRuntimeState(
                damage,
                instance.shieldStatesByMount(),
                instance.maintenance(),
                instance.weaponLoadout(),
                instance.weaponMountRuntime()));
    }

    private static void advanceObservedToTick(Fixture fixture, long targetTick) {
        while (fixture.ordnance().tick() < targetTick) {
            fixture.ordnance().advanceOneTick();
            fixture.decoys().advanceToCurrentTick();
            fixture.observation().observeCurrentTick();
        }
    }

    private record Fixture(
            LiveTacticalBattleOrdnanceRuntime ordnance,
            LiveTacticalBattleDecoyRuntime decoys,
            LiveTacticalOrdnanceObservationRuntime observation) {
    }
}
