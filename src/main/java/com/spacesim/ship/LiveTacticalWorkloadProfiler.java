package com.spacesim.ship;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Stage-19I diagnostic profiler for the authoritative live tactical runtime.
 *
 * <p>The profiler advances the supplied production runtime by an explicit number of fixed ticks and
 * records two deliberately separate classes of evidence:</p>
 *
 * <ul>
 *   <li>deterministic workload counts/peaks derived only from authoritative simulation state;</li>
 *   <li>environment-dependent wall-clock and JVM heap observations used for scalability evidence.</li>
 * </ul>
 *
 * <p>Wall time and memory are never written into simulation state or authoritative fingerprints.
 * They are diagnostics only, so profiling cannot make combat outcomes depend on runner speed, GC or
 * rendering. No performance threshold is invented here; representative hardware calibration remains
 * a separate Stage-19 acceptance decision.</p>
 */
public final class LiveTacticalWorkloadProfiler {
    /**
     * Profiles an explicit fixed-tick interval of one authoritative battle.
     *
     * @param runtime shared production deception/defense/ordnance runtime to advance
     * @param ticks positive number of authoritative fixed ticks to execute
     * @return immutable performance/workload report
     */
    public ProfileReport profile(LiveTacticalBattleDeceptionRuntime runtime, int ticks) {
        LiveTacticalBattleDeceptionRuntime checked = Objects.requireNonNull(runtime, "runtime");
        if (ticks <= 0) {
            throw new IllegalArgumentException("ticks must be positive");
        }

        Runtime jvm = Runtime.getRuntime();
        long initialHeapBytes = usedHeapBytes(jvm);
        long peakHeapBytes = initialHeapBytes;
        long wallStartNanos = System.nanoTime();
        List<Long> tickDurationsNanos = new ArrayList<>(ticks);

        Totals start = totals(checked);
        long cumulativeShipTrackHypotheses = 0L;
        long cumulativeOrdnanceTrackHypotheses = 0L;
        int peakShipTrackHypotheses = 0;
        int peakOrdnanceTrackHypotheses = 0;
        int peakKineticBodies = 0;
        int peakGuidedBodies = 0;
        int peakInterceptorBodies = 0;
        int peakDecoyBodies = 0;
        int peakTotalOrdnanceBodies = 0;
        boolean allBodyKindsConcurrent = false;

        for (int index = 0; index < ticks; index++) {
            long tickStart = System.nanoTime();
            checked.advanceOneTick();
            long tickDuration = Math.max(0L, System.nanoTime() - tickStart);
            tickDurationsNanos.add(tickDuration);

            int kineticBodies = checked.ordnanceRuntime().weaponRuntime().projectiles().size();
            int guidedBodies = checked.ordnanceRuntime().guidedBodies().size();
            int interceptorBodies = checked.defenseRuntime().interceptorBodies().size();
            int decoyBodies = checked.decoyRuntime().decoyBodies().size();
            int totalBodies = Math.addExact(
                    Math.addExact(kineticBodies, guidedBodies),
                    Math.addExact(interceptorBodies, decoyBodies));

            int shipTrackHypotheses = checked.battleState().combatants().stream()
                    .mapToInt(value -> checked.battleState().visibleContacts(value.spec().entityId()).size())
                    .sum();
            int ordnanceTrackHypotheses = checked.battleState().combatants().stream()
                    .mapToInt(value -> checked.defenseRuntime().observationRuntime()
                            .tracksForObserver(value.spec().entityId()).size())
                    .sum();

            cumulativeShipTrackHypotheses = Math.addExact(
                    cumulativeShipTrackHypotheses,
                    shipTrackHypotheses);
            cumulativeOrdnanceTrackHypotheses = Math.addExact(
                    cumulativeOrdnanceTrackHypotheses,
                    ordnanceTrackHypotheses);
            peakShipTrackHypotheses = Math.max(peakShipTrackHypotheses, shipTrackHypotheses);
            peakOrdnanceTrackHypotheses = Math.max(peakOrdnanceTrackHypotheses, ordnanceTrackHypotheses);
            peakKineticBodies = Math.max(peakKineticBodies, kineticBodies);
            peakGuidedBodies = Math.max(peakGuidedBodies, guidedBodies);
            peakInterceptorBodies = Math.max(peakInterceptorBodies, interceptorBodies);
            peakDecoyBodies = Math.max(peakDecoyBodies, decoyBodies);
            peakTotalOrdnanceBodies = Math.max(peakTotalOrdnanceBodies, totalBodies);
            allBodyKindsConcurrent |= kineticBodies > 0
                    && guidedBodies > 0
                    && interceptorBodies > 0
                    && decoyBodies > 0;
            peakHeapBytes = Math.max(peakHeapBytes, usedHeapBytes(jvm));
        }

        long wallElapsedNanos = Math.max(1L, System.nanoTime() - wallStartNanos);
        long finalHeapBytes = usedHeapBytes(jvm);
        peakHeapBytes = Math.max(peakHeapBytes, finalHeapBytes);
        Totals end = totals(checked);
        int activeShips = checked.battleState().combatants().size();
        DeterministicWorkload workload = new DeterministicWorkload(
                checked.tick(),
                activeShips,
                ticks,
                Math.multiplyExact((long) activeShips, ticks),
                cumulativeShipTrackHypotheses,
                cumulativeOrdnanceTrackHypotheses,
                peakShipTrackHypotheses,
                peakOrdnanceTrackHypotheses,
                peakKineticBodies,
                peakGuidedBodies,
                peakInterceptorBodies,
                peakDecoyBodies,
                peakTotalOrdnanceBodies,
                allBodyKindsConcurrent,
                Math.subtractExact(end.kineticShots(), start.kineticShots()),
                Math.subtractExact(end.guidedLaunches(), start.guidedLaunches()),
                Math.subtractExact(end.decoyDeployments(), start.decoyDeployments()),
                Math.subtractExact(end.interceptorLaunches(), start.interceptorLaunches()),
                Math.subtractExact(end.impacts(), start.impacts()),
                Math.subtractExact(end.interceptions(), start.interceptions()));

        List<Long> sortedDurations = new ArrayList<>(tickDurationsNanos);
        Collections.sort(sortedDurations);
        double meanTickMillis = tickDurationsNanos.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0d) / 1_000_000d;
        double p95TickMillis = percentileNanos(sortedDurations, 0.95d) / 1_000_000d;
        double maxTickMillis = sortedDurations.get(sortedDurations.size() - 1) / 1_000_000d;
        double ticksPerRealSecond = ticks * 1_000_000_000d / wallElapsedNanos;

        return new ProfileReport(
                workload,
                wallElapsedNanos,
                ticksPerRealSecond,
                meanTickMillis,
                p95TickMillis,
                maxTickMillis,
                initialHeapBytes,
                finalHeapBytes,
                peakHeapBytes,
                finalHeapBytes - initialHeapBytes);
    }

    private static Totals totals(LiveTacticalBattleDeceptionRuntime runtime) {
        long kineticShots = 0L;
        long guidedLaunches = 0L;
        long decoyDeployments = 0L;
        long interceptorLaunches = 0L;
        for (LiveTacticalBattleRuntimeState.CombatantRuntime combatant : runtime.battleState().combatants()) {
            long entityId = combatant.spec().entityId();
            kineticShots = Math.addExact(
                    kineticShots,
                    runtime.ordnanceRuntime().weaponRuntime().shotsFired(entityId));
            guidedLaunches = Math.addExact(
                    guidedLaunches,
                    runtime.ordnanceRuntime().guidedLaunches(entityId));
            decoyDeployments = Math.addExact(
                    decoyDeployments,
                    runtime.automaticDeployments(entityId));
            interceptorLaunches = Math.addExact(
                    interceptorLaunches,
                    runtime.defenseRuntime().interceptorLaunches(entityId));
        }
        return new Totals(
                kineticShots,
                guidedLaunches,
                decoyDeployments,
                interceptorLaunches,
                runtime.ordnanceRuntime().weaponRuntime().totalImpacts(),
                runtime.defenseRuntime().totalSuccessfulInterceptions());
    }

    private static long percentileNanos(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
        return sorted.get(Math.min(index, sorted.size() - 1));
    }

    private static long usedHeapBytes(Runtime runtime) {
        return Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
    }

    private record Totals(
            long kineticShots,
            long guidedLaunches,
            long decoyDeployments,
            long interceptorLaunches,
            long impacts,
            long interceptions) {
    }

    /**
     * Deterministic simulation-work evidence collected during the profiled interval.
     *
     * @param finalTick authoritative tick after the interval
     * @param activeShips materialized physical ships
     * @param profiledTicks fixed ticks executed by the profiler
     * @param tacticalAiDecisions expected production actor decisions (ships × ticks)
     * @param cumulativeShipTrackHypotheses sum of actor-local ship-track entries observed after each tick
     * @param cumulativeOrdnanceTrackHypotheses sum of actor-local ordnance-track entries after each tick
     * @param peakShipTrackHypotheses peak simultaneous ship-track entries across all actors
     * @param peakOrdnanceTrackHypotheses peak simultaneous ordnance-track entries across all actors
     * @param peakKineticBodies peak ordinary projectile/residual bodies
     * @param peakGuidedBodies peak STRIKE guided bodies
     * @param peakInterceptorBodies peak guided interceptor bodies
     * @param peakDecoyBodies peak physical decoy bodies
     * @param peakTotalOrdnanceBodies peak simultaneous non-ship bodies of all four classes
     * @param allBodyKindsConcurrent whether all four body classes were positive on the same tick
     * @param kineticShots physical kinetic shots materialized during the interval
     * @param guidedLaunches physical STRIKE launches during the interval
     * @param decoyDeployments physical DECOY deployments during the interval
     * @param interceptorLaunches physical INTERCEPTOR launches during the interval
     * @param protectionImpacts physical ship-protection interactions during the interval
     * @param physicalInterceptions swept interceptor/threat contacts during the interval
     */
    public record DeterministicWorkload(
            long finalTick,
            int activeShips,
            int profiledTicks,
            long tacticalAiDecisions,
            long cumulativeShipTrackHypotheses,
            long cumulativeOrdnanceTrackHypotheses,
            int peakShipTrackHypotheses,
            int peakOrdnanceTrackHypotheses,
            int peakKineticBodies,
            int peakGuidedBodies,
            int peakInterceptorBodies,
            int peakDecoyBodies,
            int peakTotalOrdnanceBodies,
            boolean allBodyKindsConcurrent,
            long kineticShots,
            long guidedLaunches,
            long decoyDeployments,
            long interceptorLaunches,
            long protectionImpacts,
            long physicalInterceptions) {
        /** Validates non-negative deterministic workload evidence. */
        public DeterministicWorkload {
            if (finalTick < 0L || activeShips <= 0 || profiledTicks <= 0 || tacticalAiDecisions < 0L
                    || cumulativeShipTrackHypotheses < 0L || cumulativeOrdnanceTrackHypotheses < 0L
                    || peakShipTrackHypotheses < 0 || peakOrdnanceTrackHypotheses < 0
                    || peakKineticBodies < 0 || peakGuidedBodies < 0 || peakInterceptorBodies < 0
                    || peakDecoyBodies < 0 || peakTotalOrdnanceBodies < 0
                    || kineticShots < 0L || guidedLaunches < 0L || decoyDeployments < 0L
                    || interceptorLaunches < 0L || protectionImpacts < 0L || physicalInterceptions < 0L) {
                throw new IllegalArgumentException("deterministic workload values must be non-negative");
            }
        }
    }

    /**
     * Environment-dependent performance report plus deterministic workload evidence.
     *
     * @param workload deterministic authoritative workload projection
     * @param wallElapsedNanos wall-clock duration of the profiled interval
     * @param ticksPerRealSecond authoritative fixed ticks processed per wall-clock second
     * @param meanTickMillis arithmetic mean tick duration in milliseconds
     * @param p95TickMillis 95th-percentile tick duration in milliseconds
     * @param maxTickMillis maximum observed tick duration in milliseconds
     * @param initialHeapBytes approximate JVM used heap before profiling
     * @param finalHeapBytes approximate JVM used heap after profiling
     * @param peakHeapBytes peak sampled JVM used heap during profiling
     * @param heapGrowthBytes signed final-minus-initial used-heap delta
     */
    public record ProfileReport(
            DeterministicWorkload workload,
            long wallElapsedNanos,
            double ticksPerRealSecond,
            double meanTickMillis,
            double p95TickMillis,
            double maxTickMillis,
            long initialHeapBytes,
            long finalHeapBytes,
            long peakHeapBytes,
            long heapGrowthBytes) {
        /** Validates one diagnostic report without imposing a performance threshold. */
        public ProfileReport {
            Objects.requireNonNull(workload, "workload");
            if (wallElapsedNanos <= 0L
                    || !Double.isFinite(ticksPerRealSecond) || ticksPerRealSecond <= 0d
                    || !Double.isFinite(meanTickMillis) || meanTickMillis < 0d
                    || !Double.isFinite(p95TickMillis) || p95TickMillis < 0d
                    || !Double.isFinite(maxTickMillis) || maxTickMillis < 0d
                    || initialHeapBytes < 0L || finalHeapBytes < 0L || peakHeapBytes < 0L) {
                throw new IllegalArgumentException("invalid performance report");
            }
        }
    }
}
