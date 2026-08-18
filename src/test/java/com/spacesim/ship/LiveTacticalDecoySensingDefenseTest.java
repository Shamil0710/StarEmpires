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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalDecoySensingDefenseTest {
    private static final long DECOY_SOURCE_ID = 199_701L;
    private static final long DEFENDER_ID = 199_702L;
    private static final String DECOY_ID = "ammo.test_radar_repeater_decoy_300kg_v1";
    private static final String INTERCEPTOR_ID = "ammo.test_interceptor_750kg_v1";

    @Test
    void physicalDecoyAloneCanCreateTrackConsumeInterceptorAndBePhysicallyIntercepted() {
        Fixture fixture = fixture();
        long decoyBodyId = fixture.decoys().decoyBodies().get(0).bodyId();
        long initialInterceptorRounds = guidedRounds(fixture.battle().requireCombatant(DEFENDER_ID));

        while (fixture.defense().tick() < 1_200L
                && fixture.defense().interceptorLaunches(DEFENDER_ID) == 0L) {
            fixture.defense().advanceOneTick();
        }

        assertEquals(0L, fixture.ordnance().guidedLaunches(DECOY_SOURCE_ID),
                "DECOY ammunition must not become a hidden STRIKE missile");
        assertEquals(0L, fixture.ordnance().guidedLaunches(DEFENDER_ID),
                "INTERCEPTOR ammunition must not become ordinary offensive guided fire");
        var observed = fixture.defense().observationRuntime().track(DEFENDER_ID, decoyBodyId);
        assertNotNull(observed, "physical decoy must enter the ordinary observer-local radar track domain");
        assertTrue(observed.velocityKnown(),
                "layered defense must wait for velocity inferred from multiple observed positions");
        assertTrue(observed.track().informationState() == TrackState.InformationState.TRACKED
                        || observed.track().informationState() == TrackState.InformationState.FIRE_CONTROL);
        assertTrue(fixture.defense().interceptorLaunches(DEFENDER_ID) > 0L,
                "a convincing inbound decoy must consume a real defensive assignment without a hidden type label");
        assertTrue(fixture.defense().interceptorBodies().stream()
                        .anyMatch(body -> body.targetId() == decoyBodyId),
                "materialized interceptor target identity must be the observed decoy body hypothesis");
        assertEquals(
                initialInterceptorRounds - fixture.defense().interceptorLaunches(DEFENDER_ID),
                guidedRounds(fixture.battle().requireCombatant(DEFENDER_ID)),
                "decoy diversion must consume finite itemized interceptor stores");

        int projectileCountBefore = fixture.ordnance().weaponRuntime().projectiles().size();
        while (fixture.defense().tick() < 2_400L
                && fixture.defense().successfulInterceptions(DEFENDER_ID) == 0L) {
            fixture.defense().advanceOneTick();
        }

        assertTrue(fixture.defense().successfulInterceptions(DEFENDER_ID) > 0L,
                "diversion is not complete until interceptor and decoy physically make swept contact");
        assertFalse(fixture.decoys().decoyBodies().stream().anyMatch(body -> body.bodyId() == decoyBodyId),
                "physically intercepted decoy must be removed through its authoritative body owner");
        assertTrue(fixture.ordnance().weaponRuntime().projectiles().size() >= projectileCountBefore + 2,
                "interceptor/decoy mass must survive as ordinary residual projectile bodies");
    }

    @Test
    void samePhysicalDecoyDiversionReplaysDeterministically() {
        Fixture first = fixture();
        Fixture second = fixture();

        for (int index = 0; index < 700; index++) {
            first.defense().advanceOneTick();
            second.defense().advanceOneTick();
        }

        assertEquals(first.defense().fingerprint(), second.defense().fingerprint());
        assertEquals(first.decoys().fingerprint(), second.decoys().fingerprint());
        assertEquals(
                first.defense().observationRuntime().tracksForObserver(DEFENDER_ID),
                second.defense().observationRuntime().tracksForObserver(DEFENDER_ID));
    }

    private static Fixture fixture() {
        LiveTacticalBattleRuntimeState battle = new LiveTacticalBattleRuntimeState(
                new LiveTacticalBattleScenario(List.of(
                        new CombatantSpec(
                                DECOY_SOURCE_ID,
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
        LiveTacticalInitialOrdnanceService initial = new LiveTacticalInitialOrdnanceService();
        initial.apply(
                battle.requireCombatant(DECOY_SOURCE_ID),
                List.of(
                        new FeedLoad("weapon_primary", DECOY_ID, 8L),
                        new FeedLoad("weapon_secondary", DECOY_ID, 8L)));
        initial.apply(
                battle.requireCombatant(DEFENDER_ID),
                List.of(
                        new FeedLoad("weapon_primary", INTERCEPTOR_ID, 8L),
                        new FeedLoad("weapon_secondary", INTERCEPTOR_ID, 8L)));

        LiveTacticalBattleOrdnanceRuntime ordnance = new LiveTacticalBattleOrdnanceRuntime(
                new LiveTacticalBattleWeaponRuntime(
                        new LiveTacticalBattleControlRuntime(battle)));
        LiveTacticalBattleDecoyRuntime decoys = new LiveTacticalBattleDecoyRuntime(ordnance);
        assertTrue(decoys.deployOne(DECOY_SOURCE_ID, "weapon_primary", 1d, 0d));
        LiveTacticalBattleDefenseRuntime defense = new LiveTacticalBattleDefenseRuntime(ordnance, decoys);
        return new Fixture(battle, ordnance, decoys, defense);
    }

    private static long guidedRounds(LiveTacticalBattleRuntimeState.CombatantRuntime combatant) {
        return combatant.engineering().runtimeState.consumables().interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .filter(value -> "guided_feed".equals(value.interfaceId()))
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }

    private record Fixture(
            LiveTacticalBattleRuntimeState battle,
            LiveTacticalBattleOrdnanceRuntime ordnance,
            LiveTacticalBattleDecoyRuntime decoys,
            LiveTacticalBattleDefenseRuntime defense) {
    }
}
