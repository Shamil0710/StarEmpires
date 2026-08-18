package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.LiveTacticalInitialOrdnanceService.FeedLoad;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTacticalSaturationProfilingAcceptanceTest {
    private static final String STRIKE_ID = "ammo.test_anti_ship_missile_2t_v1";
    private static final String INTERCEPTOR_ID = "ammo.test_interceptor_750kg_v1";
    private static final String DECOY_ID = "ammo.test_radar_repeater_decoy_300kg_v1";

    private static final List<Long> STRIKE_DECOY_SPECIALISTS = List.of(
            191_501L,
            191_509L,
            191_601L,
            191_610L);
    private static final List<Long> INTERCEPTOR_SPECIALISTS = List.of(
            191_506L,
            191_514L,
            191_605L,
            191_613L);

    @Test
    void denseThirtyTwoShipOrdnanceUsesAllPhysicalBodyClassesAndFiniteStores() {
        Fixture fixture = fixture();
        long initialGuidedRounds = totalGuidedRounds(fixture.runtime());

        LiveTacticalWorkloadProfiler.ProfileReport report =
                new LiveTacticalWorkloadProfiler().profile(fixture.runtime(), 240);
        LiveTacticalWorkloadProfiler.DeterministicWorkload workload = report.workload();

        assertEquals(32, workload.activeShips());
        assertEquals(240, workload.profiledTicks());
        assertEquals(32L * 240L, workload.tacticalAiDecisions());
        assertTrue(workload.cumulativeShipTrackHypotheses() > 0L);
        assertTrue(workload.cumulativeOrdnanceTrackHypotheses() > 0L);
        assertTrue(workload.peakShipTrackHypotheses() > 0);
        assertTrue(workload.peakOrdnanceTrackHypotheses() > 0);

        assertTrue(workload.peakKineticBodies() > 0);
        assertTrue(workload.peakGuidedBodies() > 0);
        assertTrue(workload.peakInterceptorBodies() > 0);
        assertTrue(workload.peakDecoyBodies() > 0);
        assertTrue(workload.allBodyKindsConcurrent(),
                "saturation is not accepted unless kinetic/STRIKE/interceptor/decoy bodies coexist on one tick");
        assertTrue(workload.peakTotalOrdnanceBodies() > workload.activeShips(),
                "dense acceptance requires more simultaneous physical non-ship bodies than combatant ships");

        assertTrue(workload.kineticShots() > 0L);
        assertTrue(workload.guidedLaunches() > 0L);
        assertTrue(workload.decoyDeployments() > 0L);
        assertTrue(workload.interceptorLaunches() > 0L);
        assertTrue(workload.protectionImpacts() > 0L);
        assertTrue(workload.physicalInterceptions() > 0L,
                "finite layered defense must physically contact threats during the dense body interval");

        long consumedGuidedRounds = initialGuidedRounds - totalGuidedRounds(fixture.runtime());
        assertEquals(
                workload.guidedLaunches() + workload.decoyDeployments() + workload.interceptorLaunches(),
                consumedGuidedRounds,
                "every STRIKE/DECOY/INTERCEPTOR materialization must consume one real guided-feed item");

        assertTrue(report.wallElapsedNanos() > 0L);
        assertTrue(report.ticksPerRealSecond() > 0d);
        assertTrue(report.meanTickMillis() >= 0d);
        assertTrue(report.p95TickMillis() >= report.meanTickMillis() * 0.25d,
                "percentile diagnostic should remain numerically meaningful without imposing a hardware threshold");
        assertTrue(report.maxTickMillis() >= report.p95TickMillis());
        assertTrue(report.peakHeapBytes() >= report.initialHeapBytes());
        assertTrue(report.peakHeapBytes() >= report.finalHeapBytes());

        System.out.println("STAGE19_SATURATION_PROFILE=" + report);
    }

    @Test
    void saturationWorkloadAndAuthoritativeOutcomeReplayDeterministically() {
        Fixture first = fixture();
        Fixture second = fixture();
        LiveTacticalWorkloadProfiler profiler = new LiveTacticalWorkloadProfiler();

        var firstReport = profiler.profile(first.runtime(), 60);
        var secondReport = profiler.profile(second.runtime(), 60);

        assertEquals(firstReport.workload(), secondReport.workload(),
                "wall-clock/heap diagnostics may differ, but deterministic workload must replay exactly");
        assertEquals(first.runtime().fingerprint(), second.runtime().fingerprint(),
                "profiling must not alter the authoritative saturation outcome");
    }

    private static Fixture fixture() {
        LiveTacticalBattleRuntimeState battle =
                new LiveTacticalBattleRuntimeState(LiveTacticalBattleScenario.mixed16v16());
        LiveTacticalInitialOrdnanceService initial = new LiveTacticalInitialOrdnanceService();
        for (long entityId : STRIKE_DECOY_SPECIALISTS) {
            initial.apply(
                    battle.requireCombatant(entityId),
                    List.of(
                            new FeedLoad("weapon_primary", STRIKE_ID, 8L),
                            new FeedLoad("weapon_secondary", DECOY_ID, 8L)));
        }
        for (long entityId : INTERCEPTOR_SPECIALISTS) {
            initial.apply(
                    battle.requireCombatant(entityId),
                    List.of(
                            new FeedLoad("weapon_primary", INTERCEPTOR_ID, 8L),
                            new FeedLoad("weapon_secondary", INTERCEPTOR_ID, 8L)));
        }

        LiveTacticalBattleOrdnanceRuntime ordnance = new LiveTacticalBattleOrdnanceRuntime(
                new LiveTacticalBattleWeaponRuntime(
                        new LiveTacticalBattleControlRuntime(battle)));
        return new Fixture(new LiveTacticalBattleDeceptionRuntime(ordnance));
    }

    private static long totalGuidedRounds(LiveTacticalBattleDeceptionRuntime runtime) {
        return runtime.battleState().combatants().stream()
                .flatMap(value -> value.engineering().runtimeState.consumables().interfaceLoads().stream())
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .filter(value -> "guided_feed".equals(value.interfaceId()))
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }

    private record Fixture(LiveTacticalBattleDeceptionRuntime runtime) {
    }
}
