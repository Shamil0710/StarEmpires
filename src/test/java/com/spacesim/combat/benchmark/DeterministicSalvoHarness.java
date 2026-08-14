package com.spacesim.combat.benchmark;

import java.util.Arrays;

/**
 * Pure headless engineering harness for the Ship Mathematics v0.4 terminal-salvo benchmark.
 *
 * <p>This class deliberately lives in test sources. It does not replace the Stage-13 authoritative
 * combat controller. Its purpose is to turn the v0.3 physical seeds into deterministic acceptance
 * evidence before projectile/guidance logic is promoted into production simulation code.</p>
 */
final class DeterministicSalvoHarness {
    static final int INCOMING_THREATS = 48;
    static final double INITIAL_RANGE_M = 800_000.0;
    static final double INCOMING_SPEED_MPS = 18_000.0;
    static final double IMPACT_TIME_S = INITIAL_RANGE_M / INCOMING_SPEED_MPS;

    static final double AREA_DEFENSE_RANGE_M = 700_000.0;
    static final double FLEET_INTERCEPTOR_RANGE_M = 350_000.0;
    static final double POINT_DEFENSE_RANGE_M = 300_000.0;
    static final double MIN_SAFE_INTERCEPT_RANGE_M = 10_000.0;

    static final double LASER_BEAM_POWER_W = 5_000_000.0;
    static final double LASER_WAVELENGTH_M = 1.064e-6;
    static final double LASER_APERTURE_M = 0.5;
    static final double LASER_POINTING_JITTER_RAD = 5.0e-8;
    static final double MISSILE_SURFACE_ABSORPTIVITY = 0.5;
    static final double LASER_GUIDANCE_KILL_FLUENCE_J_PER_M2 = 8_000_000.0;
    static final double LASER_HARD_KILL_FLUENCE_J_PER_M2 = 80_000_000.0;
    static final double DANGEROUS_BALLISTIC_MISS_RADIUS_M = 60.0;
    static final double LASER_RETARGET_DELAY_S = 0.4;

    /** Close-screen reference spacing. Larger spacing becomes a real inner-defense trade-off. */
    static final double DEFENDER_ESCORT_OFFSET_Y_M = 15_000.0;
    static final double INTEGRATION_STEP_S = 0.02;

    private static final double GOLDEN_ANGLE_RAD = 2.399963229728653;
    private static final double AREA_ENTRY_TIME_S =
            (INITIAL_RANGE_M - AREA_DEFENSE_RANGE_M) / INCOMING_SPEED_MPS;
    private static final double FLEET_ENTRY_TIME_S =
            (INITIAL_RANGE_M - FLEET_INTERCEPTOR_RANGE_M) / INCOMING_SPEED_MPS;

    private static final InterceptorSpec AREA_INTERCEPTOR = new InterceptorSpec(
            4_000.0,
            2_000.0,
            35_000.0,
            400_000.0,
            4.0,
            150.0);
    private static final InterceptorSpec FLEET_INTERCEPTOR = new InterceptorSpec(
            1_200.0,
            700.0,
            30_000.0,
            180_000.0,
            4.0,
            100.0);

    private DeterministicSalvoHarness() {
        throw new AssertionError("DeterministicSalvoHarness does not create instances");
    }

    /**
     * Runs the canonical first-wave saturation scenario.
     *
     * @param includeEscort whether one reference escort destroyer protects the battleship
     * @return immutable deterministic diagnostics
     */
    static SalvoReport runScenario(boolean includeEscort) {
        Threat[] threats = new Threat[INCOMING_THREATS];
        for (int index = 0; index < threats.length; index++) {
            threats[index] = new Threat(index);
        }

        DefenseCounters counters = new DefenseCounters();
        scheduleDefenderInterceptors(threats, 0.0, counters);
        if (includeEscort) {
            scheduleDefenderInterceptors(threats, DEFENDER_ESCORT_OFFSET_Y_M, counters);
        }

        double[] laserEmitterOffsetsYM = pointDefenseEmitterOffsets(includeEscort);
        LaserCounters laserCounters = runPointDefense(threats, laserEmitterOffsetsYM);
        int leakers = 0;
        for (Threat threat : threats) {
            if (threat.mode == ThreatMode.GUIDED
                    || threat.mode == ThreatMode.BALLISTIC
                    || threat.mode == ThreatMode.LEAKER) {
                leakers++;
            }
        }

        return new SalvoReport(
                INCOMING_THREATS,
                counters.areaInterceptorKills,
                counters.fleetInterceptorKills,
                counters.areaInterceptorsExpended,
                counters.fleetInterceptorsExpended,
                laserEmitterOffsetsYM.length,
                laserCounters.missionKills,
                laserCounters.ballisticMissNeutralizations,
                laserCounters.hardKills,
                laserCounters.totalBeamSeconds,
                leakers);
    }

    /**
     * Exercises the same two-dimensional proportional-navigation integration used by the salvo
     * predictor for one explicitly chosen target.
     *
     * @param areaDefense true for the extended area-defense interceptor, false for the S fleet interceptor
     * @param targetId deterministic incoming-threat id
     * @param launchTimeS launch time from scenario start
     * @param launcherOffsetYM launcher y offset in metres
     * @return intercept diagnostics
     */
    static InterceptResult predictReferenceIntercept(
            boolean areaDefense,
            int targetId,
            double launchTimeS,
            double launcherOffsetYM) {
        if (targetId < 0 || targetId >= INCOMING_THREATS) {
            throw new IllegalArgumentException("targetId outside benchmark wave");
        }
        return integrateInterceptor(
                areaDefense ? AREA_INTERCEPTOR : FLEET_INTERCEPTOR,
                targetId,
                launchTimeS,
                launcherOffsetYM);
    }

    private static double[] pointDefenseEmitterOffsets(boolean includeEscort) {
        double[] offsets = new double[includeEscort ? 10 : 6];
        if (includeEscort) {
            Arrays.fill(offsets, 6, offsets.length, DEFENDER_ESCORT_OFFSET_Y_M);
        }
        return offsets;
    }

    private static void scheduleDefenderInterceptors(
            Threat[] threats,
            double launcherOffsetYM,
            DefenseCounters counters) {
        // One L area-defense battery: four launch cells, six terminal-support channels.
        // The synchronized first wave reserves four threats at entry and two after the first
        // 6 s cell recycle. Further simultaneous terminal solutions exceed the six support channels.
        for (int launch = 0; launch < 6; launch++) {
            double launchTime = launch < 4 ? AREA_ENTRY_TIME_S : AREA_ENTRY_TIME_S + 6.0;
            counters.areaInterceptorsExpended++;
            Threat target = nextGuidedThreat(threats);
            if (target == null) {
                break;
            }
            InterceptResult result = integrateInterceptor(
                    AREA_INTERCEPTOR,
                    target.id,
                    launchTime,
                    launcherOffsetYM);
            if (result.success()) {
                target.mode = ThreatMode.INTERCEPTED;
                counters.areaInterceptorKills++;
            }
        }

        // Two M fleet-interceptor batteries: each has two cells and two terminal-support channels.
        for (int launch = 0; launch < 4; launch++) {
            counters.fleetInterceptorsExpended++;
            Threat target = nextGuidedThreat(threats);
            if (target == null) {
                break;
            }
            InterceptResult result = integrateInterceptor(
                    FLEET_INTERCEPTOR,
                    target.id,
                    FLEET_ENTRY_TIME_S,
                    launcherOffsetYM);
            if (result.success()) {
                target.mode = ThreatMode.INTERCEPTED;
                counters.fleetInterceptorKills++;
            }
        }
    }

    private static Threat nextGuidedThreat(Threat[] threats) {
        for (Threat threat : threats) {
            if (threat.mode == ThreatMode.GUIDED) {
                return threat;
            }
        }
        return null;
    }

    private static LaserCounters runPointDefense(Threat[] threats, double[] emitterOffsetsYM) {
        int[] laserTargets = new int[emitterOffsetsYM.length];
        Arrays.fill(laserTargets, -1);
        double[] readyAt = new double[emitterOffsetsYM.length];
        LaserCounters counters = new LaserCounters();

        for (double time = 0.0; time <= IMPACT_TIME_S + 1.0e-9; time += INTEGRATION_STEP_S) {
            updateBallisticOutcomes(threats, time);
            for (int laser = 0; laser < emitterOffsetsYM.length; laser++) {
                int targetId = laserTargets[laser];
                if (targetId >= 0 && !isLaserTargetable(threats[targetId])) {
                    laserTargets[laser] = -1;
                    readyAt[laser] = time + LASER_RETARGET_DELAY_S;
                }
                if (laserTargets[laser] < 0 && time + 1.0e-12 >= readyAt[laser]) {
                    laserTargets[laser] = selectLaserTarget(
                            threats,
                            laserTargets,
                            time,
                            emitterOffsetsYM[laser]);
                }
            }

            for (int laser = 0; laser < emitterOffsetsYM.length; laser++) {
                int targetId = laserTargets[laser];
                if (targetId < 0) {
                    continue;
                }
                Threat target = threats[targetId];
                if (!isLaserTargetable(target)) {
                    continue;
                }
                MotionState state = target.motionState(time);
                double range = Math.hypot(state.xM, state.yM - emitterOffsetsYM[laser]);
                if (range > POINT_DEFENSE_RANGE_M) {
                    continue;
                }
                target.absorbedFluenceJPerM2 += absorbedLaserFlux(range) * INTEGRATION_STEP_S;
                counters.totalBeamSeconds += INTEGRATION_STEP_S;

                if (target.mode == ThreatMode.GUIDED
                        && target.absorbedFluenceJPerM2 >= LASER_GUIDANCE_KILL_FLUENCE_J_PER_M2) {
                    counters.missionKills++;
                    target.enterBallisticState(time, state);
                    ClosestApproach closestApproach = closestBallisticApproach(state);
                    if (closestApproach.distanceM > DANGEROUS_BALLISTIC_MISS_RADIUS_M) {
                        target.mode = ThreatMode.NEUTRALIZED;
                        counters.ballisticMissNeutralizations++;
                    }
                }

                if (target.mode == ThreatMode.BALLISTIC
                        && target.absorbedFluenceJPerM2 >= LASER_HARD_KILL_FLUENCE_J_PER_M2) {
                    target.mode = ThreatMode.NEUTRALIZED;
                    counters.hardKills++;
                }
            }
        }

        for (Threat threat : threats) {
            if (threat.mode == ThreatMode.GUIDED) {
                threat.mode = ThreatMode.LEAKER;
            } else if (threat.mode == ThreatMode.BALLISTIC) {
                ClosestApproach closestApproach = closestBallisticApproach(threat.ballisticInitialState());
                threat.mode = closestApproach.distanceM <= DANGEROUS_BALLISTIC_MISS_RADIUS_M
                        ? ThreatMode.LEAKER
                        : ThreatMode.NEUTRALIZED;
            }
        }
        return counters;
    }

    private static void updateBallisticOutcomes(Threat[] threats, double time) {
        for (Threat threat : threats) {
            if (threat.mode != ThreatMode.BALLISTIC) {
                continue;
            }
            ClosestApproach closestApproach = closestBallisticApproach(threat.ballisticInitialState());
            double elapsed = time - threat.ballisticStartTimeS;
            if (elapsed > closestApproach.timeFromStateS + 0.1) {
                threat.mode = closestApproach.distanceM <= DANGEROUS_BALLISTIC_MISS_RADIUS_M
                        ? ThreatMode.LEAKER
                        : ThreatMode.NEUTRALIZED;
            }
        }
    }

    private static int selectLaserTarget(
            Threat[] threats,
            int[] currentTargets,
            double time,
            double emitterOffsetYM) {
        int bestId = -1;
        double bestRange = Double.POSITIVE_INFINITY;
        for (Threat threat : threats) {
            if (!isLaserTargetable(threat) || contains(currentTargets, threat.id)) {
                continue;
            }
            MotionState state = threat.motionState(time);
            double range = Math.hypot(state.xM, state.yM - emitterOffsetYM);
            if (range > POINT_DEFENSE_RANGE_M) {
                continue;
            }
            if (range < bestRange - 1.0e-9
                    || (Math.abs(range - bestRange) <= 1.0e-9 && threat.id < bestId)) {
                bestRange = range;
                bestId = threat.id;
            }
        }
        return bestId;
    }

    private static boolean contains(int[] values, int candidate) {
        for (int value : values) {
            if (value == candidate) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLaserTargetable(Threat threat) {
        return threat.mode == ThreatMode.GUIDED || threat.mode == ThreatMode.BALLISTIC;
    }

    private static double absorbedLaserFlux(double rangeM) {
        double diffractionRad = 1.22 * LASER_WAVELENGTH_M / LASER_APERTURE_M;
        double effectiveDivergenceRad = Math.hypot(diffractionRad, LASER_POINTING_JITTER_RAD);
        double spotRadiusM = Math.max(1.0e-9, rangeM * effectiveDivergenceRad);
        double incidentWPerM2 = LASER_BEAM_POWER_W / (Math.PI * spotRadiusM * spotRadiusM);
        return incidentWPerM2 * MISSILE_SURFACE_ABSORPTIVITY;
    }

    private static InterceptResult integrateInterceptor(
            InterceptorSpec spec,
            int targetId,
            double launchTimeS,
            double launcherOffsetYM) {
        double xM = 0.0;
        double yM = launcherOffsetYM;
        double vxMps = 0.0;
        double vyMps = 0.0;
        double massKg = spec.wetMassKg;
        double massFlowKgPerS = spec.thrustN / spec.exhaustVelocityMps;
        double time = launchTimeS;
        double closestRangeM = Double.POSITIVE_INFINITY;

        while (time <= IMPACT_TIME_S + INTEGRATION_STEP_S) {
            MotionState target = guidedThreatState(targetId, time);
            double protectedRangeM = Math.hypot(target.xM, target.yM);
            double rx = target.xM - xM;
            double ry = target.yM - yM;
            double rangeM = Math.hypot(rx, ry);
            closestRangeM = Math.min(closestRangeM, rangeM);
            if (rangeM <= spec.proximityKillRadiusM) {
                return new InterceptResult(
                        protectedRangeM >= MIN_SAFE_INTERCEPT_RANGE_M,
                        time,
                        closestRangeM);
            }
            if (target.xM <= 0.0 || protectedRangeM < MIN_SAFE_INTERCEPT_RANGE_M) {
                return new InterceptResult(false, time, closestRangeM);
            }

            double ax = 0.0;
            double ay = 0.0;
            if (massKg > spec.dryMassKg + 1.0e-9) {
                double availableAcceleration = spec.thrustN / massKg;
                double ux = rx / rangeM;
                double uy = ry / rangeM;
                double relativeVx = target.vxMps - vxMps;
                double relativeVy = target.vyMps - vyMps;
                double closingVelocity = -(ux * relativeVx + uy * relativeVy);
                double lineOfSightRate =
                        (rx * relativeVy - ry * relativeVx) / (rangeM * rangeM);
                double lateralCommand =
                        spec.navigationConstant * closingVelocity * lineOfSightRate;
                lateralCommand = clamp(
                        lateralCommand,
                        -availableAcceleration,
                        availableAcceleration);
                double forwardCommand = Math.sqrt(Math.max(
                        0.0,
                        availableAcceleration * availableAcceleration
                                - lateralCommand * lateralCommand));
                double perpendicularX = -uy;
                double perpendicularY = ux;
                ax = ux * forwardCommand + perpendicularX * lateralCommand;
                ay = uy * forwardCommand + perpendicularY * lateralCommand;
                massKg -= Math.min(
                        massKg - spec.dryMassKg,
                        massFlowKgPerS * INTEGRATION_STEP_S);
            }

            double nextVx = vxMps + ax * INTEGRATION_STEP_S;
            double nextVy = vyMps + ay * INTEGRATION_STEP_S;
            double nextX = xM + nextVx * INTEGRATION_STEP_S;
            double nextY = yM + nextVy * INTEGRATION_STEP_S;
            double nextTime = time + INTEGRATION_STEP_S;
            MotionState nextTarget = guidedThreatState(targetId, nextTime);
            double endRx = nextTarget.xM - nextX;
            double endRy = nextTarget.yM - nextY;
            SegmentApproach segmentApproach = segmentClosestApproach(rx, ry, endRx, endRy);
            closestRangeM = Math.min(closestRangeM, segmentApproach.distanceM);
            if (segmentApproach.distanceM <= spec.proximityKillRadiusM) {
                double interceptTime = time + segmentApproach.segmentFraction * INTEGRATION_STEP_S;
                MotionState interceptTarget = guidedThreatState(targetId, interceptTime);
                double interceptProtectedRangeM = Math.hypot(interceptTarget.xM, interceptTarget.yM);
                return new InterceptResult(
                        interceptProtectedRangeM >= MIN_SAFE_INTERCEPT_RANGE_M,
                        interceptTime,
                        closestRangeM);
            }

            xM = nextX;
            yM = nextY;
            vxMps = nextVx;
            vyMps = nextVy;
            time = nextTime;
        }
        return new InterceptResult(false, time, closestRangeM);
    }

    private static SegmentApproach segmentClosestApproach(
            double startX,
            double startY,
            double endX,
            double endY) {
        double dx = endX - startX;
        double dy = endY - startY;
        double denominator = dx * dx + dy * dy;
        double fraction = denominator <= 1.0e-18
                ? 0.0
                : clamp(-(startX * dx + startY * dy) / denominator, 0.0, 1.0);
        double closestX = startX + dx * fraction;
        double closestY = startY + dy * fraction;
        return new SegmentApproach(Math.hypot(closestX, closestY), fraction);
    }

    private static MotionState guidedThreatState(int targetId, double timeS) {
        double xM = Math.max(0.0, INITIAL_RANGE_M - INCOMING_SPEED_MPS * timeS);
        double rangeFraction = xM / INITIAL_RANGE_M;
        double lateralOffsetM = (targetId - (INCOMING_THREATS - 1) / 2.0) * 500.0;
        double jinkAmplitudeM = 40.0 + (targetId % 5) * 5.0;
        double jinkPeriodS = 8.0 + (targetId % 4) * 0.6;
        double omega = 2.0 * Math.PI / jinkPeriodS;
        double phase = (targetId * GOLDEN_ANGLE_RAD) % (2.0 * Math.PI);
        double angle = omega * timeS + phase;
        double sine = Math.sin(angle);
        double cosine = Math.cos(angle);
        double yM = rangeFraction * (lateralOffsetM + jinkAmplitudeM * sine);

        if (xM <= 0.0) {
            return new MotionState(0.0, 0.0, 0.0, 0.0);
        }
        double vxMps = -INCOMING_SPEED_MPS;
        double fractionRatePerS = -INCOMING_SPEED_MPS / INITIAL_RANGE_M;
        double vyMps = fractionRatePerS * (lateralOffsetM + jinkAmplitudeM * sine)
                + rangeFraction * jinkAmplitudeM * omega * cosine;
        return new MotionState(xM, yM, vxMps, vyMps);
    }

    private static ClosestApproach closestBallisticApproach(MotionState state) {
        double velocitySquared = state.vxMps * state.vxMps + state.vyMps * state.vyMps;
        if (velocitySquared <= 1.0e-18) {
            return new ClosestApproach(Math.hypot(state.xM, state.yM), 0.0);
        }
        double timeToClosest = Math.max(
                0.0,
                -(state.xM * state.vxMps + state.yM * state.vyMps) / velocitySquared);
        double closestX = state.xM + state.vxMps * timeToClosest;
        double closestY = state.yM + state.vyMps * timeToClosest;
        return new ClosestApproach(Math.hypot(closestX, closestY), timeToClosest);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Immutable outcome of the terminal-salvo benchmark. */
    record SalvoReport(
            int incomingThreats,
            int areaInterceptorKills,
            int fleetInterceptorKills,
            int areaInterceptorsExpended,
            int fleetInterceptorsExpended,
            int pointDefenseLasers,
            int laserMissionKills,
            int laserBallisticMissNeutralizations,
            int laserHardKills,
            double laserBeamSeconds,
            int leakers) {
        /** @return number of threats safely removed before laser point defense */
        int interceptorKills() {
            return areaInterceptorKills + fleetInterceptorKills;
        }

        /** @return number of physically safe outcomes plus remaining leakers */
        int accountedThreats() {
            return interceptorKills()
                    + laserBallisticMissNeutralizations
                    + laserHardKills
                    + leakers;
        }
    }

    /** Result from one deterministic two-dimensional PN intercept integration. */
    record InterceptResult(boolean success, double interceptTimeS, double closestRangeM) {
    }

    private record InterceptorSpec(
            double wetMassKg,
            double dryMassKg,
            double exhaustVelocityMps,
            double thrustN,
            double navigationConstant,
            double proximityKillRadiusM) {
    }

    private record MotionState(double xM, double yM, double vxMps, double vyMps) {
    }

    private record ClosestApproach(double distanceM, double timeFromStateS) {
    }

    private record SegmentApproach(double distanceM, double segmentFraction) {
    }

    private enum ThreatMode {
        GUIDED,
        BALLISTIC,
        INTERCEPTED,
        NEUTRALIZED,
        LEAKER
    }

    private static final class Threat {
        private final int id;
        private ThreatMode mode = ThreatMode.GUIDED;
        private double absorbedFluenceJPerM2;
        private double ballisticStartTimeS;
        private MotionState ballisticInitialState;

        private Threat(int id) {
            this.id = id;
        }

        private MotionState motionState(double timeS) {
            if (mode == ThreatMode.BALLISTIC && ballisticInitialState != null) {
                double elapsed = Math.max(0.0, timeS - ballisticStartTimeS);
                return new MotionState(
                        ballisticInitialState.xM + ballisticInitialState.vxMps * elapsed,
                        ballisticInitialState.yM + ballisticInitialState.vyMps * elapsed,
                        ballisticInitialState.vxMps,
                        ballisticInitialState.vyMps);
            }
            return guidedThreatState(id, timeS);
        }

        private void enterBallisticState(double timeS, MotionState state) {
            ballisticStartTimeS = timeS;
            ballisticInitialState = state;
            mode = ThreatMode.BALLISTIC;
        }

        private MotionState ballisticInitialState() {
            return ballisticInitialState;
        }
    }

    private static final class DefenseCounters {
        private int areaInterceptorKills;
        private int fleetInterceptorKills;
        private int areaInterceptorsExpended;
        private int fleetInterceptorsExpended;
    }

    private static final class LaserCounters {
        private int missionKills;
        private int ballisticMissNeutralizations;
        private int hardKills;
        private double totalBeamSeconds;
    }
}