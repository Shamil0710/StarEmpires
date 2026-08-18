package com.spacesim.ship;

import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.LiveTacticalInitialOrdnanceService.FeedLoad;
import com.spacesim.ship.ShipSensorRuntime.TrackQualityPolicy;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalOrdnanceTrackContinuityTest {
    private static final long DECOY_SOURCE_ID = 199_901L;
    private static final long OBSERVER_ID = 199_902L;
    private static final String DECOY_ID = "ammo.test_radar_repeater_decoy_300kg_v1";

    @Test
    void degradedTrackDropsVelocityAndReacquisitionNeedsTwoFreshCartesianSolutions() {
        Fixture fixture = fixture();
        long bodyId = fixture.decoys().decoyBodies().get(0).bodyId();

        advanceObservedToTick(fixture, 6L);
        var established = fixture.observation().track(OBSERVER_ID, bodyId);
        assertNotNull(established);
        assertTrue(established.velocityKnown(),
                "two ordinary radar solutions must establish actor-local velocity before the outage");
        assertTrue(actionable(established.track()));

        while (fixture.ordnance().tick() < 13L) {
            fixture.ordnance().advanceOneTick();
            fixture.decoys().advanceToCurrentTick();
        }
        fixture.observation().observeCurrentTick();

        var degraded = fixture.observation().track(OBSERVER_ID, bodyId);
        assertNotNull(degraded);
        assertFalse(actionable(degraded.track()),
                "the shortened acceptance policy must age the unrefreshed hypothesis below TRACKED");
        assertFalse(degraded.velocityKnown(),
                "a track-quality break must discard stale velocity continuity");
        assertTrue(degraded.estimatedVelocityXMps() == 0d
                        && degraded.estimatedVelocityYMps() == 0d
                        && degraded.oneSigmaVelocityMps() == 0d,
                "unknown velocity must return to canonical zero state after track loss");

        advanceObservedToTick(fixture, 14L);
        var firstReacquisition = fixture.observation().track(OBSERVER_ID, bodyId);
        assertNotNull(firstReacquisition);
        assertTrue(actionable(firstReacquisition.track()),
                "one fresh radar scan may reacquire an actionable Cartesian position");
        assertFalse(firstReacquisition.velocityKnown(),
                "one fresh Cartesian solution must not resurrect stale pre-loss velocity");

        advanceObservedToTick(fixture, 18L);
        var secondReacquisition = fixture.observation().track(OBSERVER_ID, bodyId);
        assertNotNull(secondReacquisition);
        assertTrue(actionable(secondReacquisition.track()));
        assertTrue(secondReacquisition.velocityKnown(),
                "a second temporally distinct post-loss solution may establish velocity again");
    }

    @Test
    void sameLossAndReacquisitionSequenceReplaysDeterministically() {
        Fixture first = fixture();
        Fixture second = fixture();

        advanceObservedToTick(first, 6L);
        advanceObservedToTick(second, 6L);
        while (first.ordnance().tick() < 13L) {
            first.ordnance().advanceOneTick();
            first.decoys().advanceToCurrentTick();
            second.ordnance().advanceOneTick();
            second.decoys().advanceToCurrentTick();
        }
        first.observation().observeCurrentTick();
        second.observation().observeCurrentTick();
        advanceObservedToTick(first, 18L);
        advanceObservedToTick(second, 18L);

        assertTrue(first.observation().tracksForObserver(OBSERVER_ID)
                .equals(second.observation().tracksForObserver(OBSERVER_ID)));
        assertTrue(first.decoys().fingerprint().equals(second.decoys().fingerprint()));
        assertTrue(first.ordnance().fingerprint().equals(second.ordnance().fingerprint()));
    }

    private static Fixture fixture() {
        LiveTacticalBattleRuntimeState battle = new LiveTacticalBattleRuntimeState(
                new LiveTacticalBattleScenario(List.of(
                        new CombatantSpec(
                                DECOY_SOURCE_ID,
                                Side.ALPHA,
                                DoctrineId.B_MISSILE_STRIKE,
                                300d,
                                700d),
                        new CombatantSpec(
                                OBSERVER_ID,
                                Side.BETA,
                                DoctrineId.A_KINETIC_LINE,
                                1_690d,
                                700d))));
        new LiveTacticalInitialOrdnanceService().apply(
                battle.requireCombatant(DECOY_SOURCE_ID),
                List.of(
                        new FeedLoad("weapon_primary", DECOY_ID, 2L),
                        new FeedLoad("weapon_secondary", DECOY_ID, 2L)));
        LiveTacticalBattleOrdnanceRuntime ordnance = new LiveTacticalBattleOrdnanceRuntime(
                new LiveTacticalBattleWeaponRuntime(
                        new LiveTacticalBattleControlRuntime(battle)));
        LiveTacticalBattleDecoyRuntime decoys = new LiveTacticalBattleDecoyRuntime(ordnance);
        assertTrue(decoys.deployOne(DECOY_SOURCE_ID, "weapon_primary", -1d, 0d));
        TrackQualityPolicy shortAcceptancePolicy = new TrackQualityPolicy(
                10_000d,
                1_000d,
                0.25d,
                0.10d,
                400d,
                1e-8d,
                0.5d);
        LiveTacticalOrdnanceObservationRuntime observation =
                new LiveTacticalOrdnanceObservationRuntime(ordnance, decoys, shortAcceptancePolicy);
        return new Fixture(ordnance, decoys, observation);
    }

    private static void advanceObservedToTick(Fixture fixture, long targetTick) {
        while (fixture.ordnance().tick() < targetTick) {
            fixture.ordnance().advanceOneTick();
            fixture.decoys().advanceToCurrentTick();
            fixture.observation().observeCurrentTick();
        }
    }

    private static boolean actionable(TrackState track) {
        return track.positionKnown()
                && (track.informationState() == TrackState.InformationState.TRACKED
                || track.informationState() == TrackState.InformationState.FIRE_CONTROL);
    }

    private record Fixture(
            LiveTacticalBattleOrdnanceRuntime ordnance,
            LiveTacticalBattleDecoyRuntime decoys,
            LiveTacticalOrdnanceObservationRuntime observation) {
    }
}
