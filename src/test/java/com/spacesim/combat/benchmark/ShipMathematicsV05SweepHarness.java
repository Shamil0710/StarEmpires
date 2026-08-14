package com.spacesim.combat.benchmark;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * Deterministic Ship Mathematics v0.5 parameter-sweep harness.
 *
 * <p>v0.4 is intentionally kept frozen as the single canonical terminal-salvo baseline. This
 * harness carries the same physical seeds into variable attacking-wave sizes, multiple escort
 * positions and persistent interceptor magazines so saturation and endurance curves can be tested
 * without changing production combat code.</p>
 */
final class ShipMathematicsV05SweepHarness {
    static final int[] CORVETTE_COUNTS = {8, 16, 24, 32, 48};
    static final int[] ESCORT_COUNTS = {0, 1, 2, 3};
    static final double[] ESCORT_SPACINGS_M = {5_000.0, 10_000.0, 15_000.0, 20_000.0, 25_000.0, 40_000.0};
    static final int MISSILES_PER_CORVETTE = 2;
    static final int ENDURANCE_CORVETTES = 24;
    static final int ENDURANCE_WAVES = 13;

    static final int AREA_MAGAZINE_PER_DEFENDER = 48;
    static final int FLEET_MAGAZINE_PER_DEFENDER = 48; // two M batteries, 24 rounds each
    static final int AREA_FIRST_WAVE_SUPPORT = 6;
    static final int FLEET_FIRST_WAVE_SUPPORT = 4;

    private static final double INITIAL_RANGE_M = 800_000.0;
    private static final double INCOMING_SPEED_MPS = 18_000.0;
    private static final double IMPACT_TIME_S = INITIAL_RANGE_M / INCOMING_SPEED_MPS;
    private static final double AREA_DEFENSE_RANGE_M = 700_000.0;
    private static final double FLEET_INTERCEPTOR_RANGE_M = 350_000.0;
    private static final double POINT_DEFENSE_RANGE_M = 300_000.0;
    private static final double MIN_SAFE_INTERCEPT_RANGE_M = 10_000.0;
    private static final double INTEGRATION_STEP_S = 0.02;

    private static final double LASER_BEAM_POWER_W = 5_000_000.0;
    private static final double LASER_WAVELENGTH_M = 1.064e-6;
    private static final double LASER_APERTURE_M = 0.5;
    private static final double LASER_POINTING_JITTER_RAD = 5.0e-8;
    private static final double MISSILE_SURFACE_ABSORPTIVITY = 0.5;
    private static final double LASER_GUIDANCE_KILL_FLUENCE_J_PER_M2 = 8_000_000.0;
    private static final double LASER_HARD_KILL_FLUENCE_J_PER_M2 = 80_000_000.0;
    private static final double DANGEROUS_BALLISTIC_MISS_RADIUS_M = 60.0;
    private static final double LASER_RETARGET_DELAY_S = 0.4;

    private static final double GOLDEN_ANGLE_RAD = 2.399963229728653;
    private static final double AREA_ENTRY_TIME_S =
            (INITIAL_RANGE_M - AREA_DEFENSE_RANGE_M) / INCOMING_SPEED_MPS;
    private static final double FLEET_ENTRY_TIME_S =
            (INITIAL_RANGE_M - FLEET_INTERCEPTOR_RANGE_M) / INCOMING_SPEED_MPS;

    private static final InterceptorSpec AREA_INTERCEPTOR = new InterceptorSpec(
            4_000.0, 2_000.0, 35_000.0, 400_000.0, 4.0, 150.0);
    private static final InterceptorSpec FLEET_INTERCEPTOR = new InterceptorSpec(
            1_200.0, 700.0, 30_000.0, 180_000.0, 4.0, 100.0);

    private ShipMathematicsV05SweepHarness() {
        throw new AssertionError("ShipMathematicsV05SweepHarness does not create instances");
    }

    /** Runs the complete v0.5 first-wave surface. */
    static List<SurfacePoint> runSingleWaveSurface() {
        List<SurfacePoint> points = new ArrayList<>();
        for (int corvettes : CORVETTE_COUNTS) {
            for (int escorts : ESCORT_COUNTS) {
                if (escorts == 0) {
                    points.add(runSurfacePoint(corvettes, 0, 0.0));
                    continue;
                }
                for (double spacingM : ESCORT_SPACINGS_M) {
                    points.add(runSurfacePoint(corvettes, escorts, spacingM));
                }
            }
        }
        return List.copyOf(points);
    }

    /**
     * Runs the canonical 24-corvette attack repeatedly until both interceptor magazines have
     * crossed their exhaustion boundaries. Cell recycle and laser readiness are reset between
     * waves; ammunition is not replenished.
     */
    static List<EndurancePoint> runCanonicalEnduranceSurface() {
        List<EndurancePoint> points = new ArrayList<>();
        for (int escorts : ESCORT_COUNTS) {
            if (escorts == 0) {
                appendEndurance(points, 0, 0.0);
                continue;
            }
            for (double spacingM : ESCORT_SPACINGS_M) {
                appendEndurance(points, escorts, spacingM);
            }
        }
        return List.copyOf(points);
    }

    static SurfacePoint findSurfacePoint(
            List<SurfacePoint> points,
            int corvettes,
            int escorts,
            double spacingM) {
        for (SurfacePoint point : points) {
            if (point.corvettes == corvettes
                    && point.escorts == escorts
                    && Double.compare(point.spacingM, spacingM) == 0) {
                return point;
            }
        }
        throw new IllegalArgumentException("Surface point not found");
    }

    static EndurancePoint findEndurancePoint(
            List<EndurancePoint> points,
            int escorts,
            double spacingM,
            int wave) {
        for (EndurancePoint point : points) {
            if (point.escorts == escorts
                    && Double.compare(point.spacingM, spacingM) == 0
                    && point.wave == wave) {
                return point;
            }
        }
        throw new IllegalArgumentException("Endurance point not found");
    }

    /** Stable SHA-256 over every v0.5 sweep result. */
    static String fingerprint(
            List<SurfacePoint> surface,
            List<EndurancePoint> endurance) {
        StringBuilder canonical = new StringBuilder(64_000);
        for (SurfacePoint point : surface) {
            canonical.append("S|")
                    .append(point.corvettes).append('|')
                    .append(point.escorts).append('|')
                    .append(Double.toHexString(point.spacingM)).append('|');
            appendReport(canonical, point.report);
            canonical.append('\n');
        }
        for (EndurancePoint point : endurance) {
            canonical.append("E|")
                    .append(point.escorts).append('|')
                    .append(Double.toHexString(point.spacingM)).append('|')
                    .append(point.wave).append('|');
            appendReport(canonical, point.report);
            canonical.append('|')
                    .append(point.areaRoundsRemaining).append('|')
                    .append(point.fleetRoundsRemaining).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    static double[] defenderOffsets(int escorts, double spacingM) {
        if (escorts < 0 || escorts > 3) {
            throw new IllegalArgumentException("escorts outside v0.5 sweep");
        }
        if (escorts > 0 && !(spacingM > 0.0)) {
            throw new IllegalArgumentException("escort spacing must be positive");
        }
        double[] offsets = new double[escorts + 1];
        for (int escort = 0; escort < escorts; escort++) {
            int shell = escort / 2 + 1;
            double sign = escort % 2 == 0 ? 1.0 : -1.0;
            offsets[escort + 1] = sign * shell * spacingM;
        }
        return offsets;
    }

    private static SurfacePoint runSurfacePoint(int corvettes, int escorts, double spacingM) {
        double[] offsets = defenderOffsets(escorts, spacingM);
        MagazineState magazines = MagazineState.full(offsets.length);
        SalvoReport report = runWave(corvettes * MISSILES_PER_CORVETTE, offsets, magazines);
        return new SurfacePoint(corvettes, escorts, spacingM, report);
    }

    private static void appendEndurance(List<EndurancePoint> points, int escorts, double spacingM) {
        double[] offsets = defenderOffsets(escorts, spacingM);
        MagazineState magazines = MagazineState.full(offsets.length);
        for (int wave = 1; wave <= ENDURANCE_WAVES; wave++) {
            SalvoReport report = runWave(
                    ENDURANCE_CORVETTES * MISSILES_PER_CORVETTE,
                    offsets,
                    magazines);
            points.add(new EndurancePoint(
                    escorts,
                    spacingM,
                    wave,
                    report,
                    magazines.totalAreaRemaining(),
                    magazines.totalFleetRemaining()));
        }
    }

    static SalvoReport runWave(
            int incomingThreatCount,
            double[] defenderOffsetsYM,
            MagazineState magazines) {
        if (incomingThreatCount <= 0) {
            throw new IllegalArgumentException("incomingThreatCount must be positive");
        }
        if (defenderOffsetsYM == null || defenderOffsetsYM.length == 0 || defenderOffsetsYM[0] != 0.0) {
            throw new IllegalArgumentException("defender offsets must start with the battleship at zero");
        }
        if (magazines.defenderCount() != defenderOffsetsYM.length) {
            throw new IllegalArgumentException("magazine state does not match defender count");
        }

        Threat[] threats = new Threat[incomingThreatCount];
        for (int index = 0; index < threats.length; index++) {
            threats[index] = new Threat(index, incomingThreatCount);
        }

        DefenseCounters defense = new DefenseCounters();
        for (int defender = 0; defender < defenderOffsetsYM.length; defender++) {
            scheduleDefenderInterceptors(
                    threats,
                    defenderOffsetsYM[defender],
                    defense,
                    magazines,
                    defender);
        }

        double[] laserOffsets = pointDefenseEmitterOffsets(defenderOffsetsYM);
        LaserCounters laser = runPointDefense(threats, laserOffsets);
        int leakers = 0;
        for (Threat threat : threats) {
            if (threat.mode == ThreatMode.GUIDED
                    || threat.mode == ThreatMode.BALLISTIC
                    || threat.mode == ThreatMode.LEAKER) {
                leakers++;
            }
        }

        return new SalvoReport(
                incomingThreatCount,
                defense.areaInterceptorKills,
                defense.fleetInterceptorKills,
                defense.areaInterceptorsExpended,
                defense.fleetInterceptorsExpended,
                laserOffsets.length,
                laser.missionKills,
                laser.ballisticMissNeutralizations,
                laser.hardKills,
                laser.totalBeamSeconds,
                leakers);
    }

    private static void scheduleDefenderInterceptors(
            Threat[] threats,
            double launcherOffsetYM,
            DefenseCounters counters,
            MagazineState magazines,
            int defenderIndex) {
        int areaLaunches = Math.min(AREA_FIRST_WAVE_SUPPORT, magazines.areaRemaining(defenderIndex));
        for (int launch = 0; launch < areaLaunches; launch++) {
            Threat target = nextGuidedThreat(threats);
            if (target == null) {
                break;
            }
            double launchTime = launch < 4 ? AREA_ENTRY_TIME_S : AREA_ENTRY_TIME_S + 6.0;
            magazines.consumeArea(defenderIndex);
            counters.areaInterceptorsExpended++;
            InterceptResult result = integrateInterceptor(
                    AREA_INTERCEPTOR,
                    target,
                    launchTime,
                    launcherOffsetYM);
            if (result.success) {
                target.mode = ThreatMode.INTERCEPTED;
                counters.areaInterceptorKills++;
            }
        }

        int fleetLaunches = Math.min(FLEET_FIRST_WAVE_SUPPORT, magazines.fleetRemaining(defenderIndex));
        for (int launch = 0; launch < fleetLaunches; launch++) {
            Threat target = nextGuidedThreat(threats);
            if (target == null) {
                break;
            }
            magazines.consumeFleet(defenderIndex);
            counters.fleetInterceptorsExpended++;
            InterceptResult result = integrateInterceptor(
                    FLEET_INTERCEPTOR,
                    target,
                    FLEET_ENTRY_TIME_S,
                    launcherOffsetYM);
            if (result.success) {
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

    private static double[] pointDefenseEmitterOffsets(double[] defenderOffsetsYM) {
        int laserCount = 6 + Math.max(0, defenderOffsetsYM.length - 1) * 4;
        double[] offsets = new double[laserCount];
        int cursor = 6;
        for (int defender = 1; defender < defenderOffsetsYM.length; defender++) {
            Arrays.fill(offsets, cursor, cursor + 4, defenderOffsetsYM[defender]);
            cursor += 4;
        }
        return offsets;
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
                ClosestApproach closestApproach = closestBallisticApproach(threat.ballisticInitialState);
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
            ClosestApproach closestApproach = closestBallisticApproach(threat.ballisticInitialState);
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
            Threat targetThreat,
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
            MotionState target = guidedThreatState(targetThreat.id, targetThreat.cohortSize, time);
            double protectedRangeM = Math.hypot(target.xM, target.yM);
            double rx = target.xM - xM;
            double ry = target.yM - yM;
            double rangeM = Math.hypot(rx, ry);
            closestRangeM = Math.min(closestRangeM, rangeM);
            if (rangeM <= spec.proximityKillRadiusM) {
                return new InterceptResult(protectedRangeM >= MIN_SAFE_INTERCEPT_RANGE_M, time, closestRangeM);
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
                double lineOfSightRate = (rx * relativeVy - ry * relativeVx) / (rangeM * rangeM);
                double lateralCommand = spec.navigationConstant * closingVelocity * lineOfSightRate;
                lateralCommand = clamp(lateralCommand, -availableAcceleration, availableAcceleration);
                double forwardCommand = Math.sqrt(Math.max(
                        0.0,
                        availableAcceleration * availableAcceleration - lateralCommand * lateralCommand));
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
            MotionState nextTarget = guidedThreatState(targetThreat.id, targetThreat.cohortSize, nextTime);
            double endRx = nextTarget.xM - nextX;
            double endRy = nextTarget.yM - nextY;
            SegmentApproach segmentApproach = segmentClosestApproach(rx, ry, endRx, endRy);
            closestRangeM = Math.min(closestRangeM, segmentApproach.distanceM);
            if (segmentApproach.distanceM <= spec.proximityKillRadiusM) {
                double interceptTime = time + segmentApproach.segmentFraction * INTEGRATION_STEP_S;
                MotionState interceptTarget = guidedThreatState(
                        targetThreat.id,
                        targetThreat.cohortSize,
                        interceptTime);
                double protectedRange = Math.hypot(interceptTarget.xM, interceptTarget.yM);
                return new InterceptResult(
                        protectedRange >= MIN_SAFE_INTERCEPT_RANGE_M,
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

    private static MotionState guidedThreatState(int targetId, int cohortSize, double timeS) {
        double xM = Math.max(0.0, INITIAL_RANGE_M - INCOMING_SPEED_MPS * timeS);
        double rangeFraction = xM / INITIAL_RANGE_M;
        double lateralOffsetM = (targetId - (cohortSize - 1) / 2.0) * 500.0;
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

    private static void appendReport(StringBuilder out, SalvoReport report) {
        out.append(report.incomingThreats).append('|')
                .append(report.areaInterceptorKills).append('|')
                .append(report.fleetInterceptorKills).append('|')
                .append(report.areaInterceptorsExpended).append('|')
                .append(report.fleetInterceptorsExpended).append('|')
                .append(report.pointDefenseLasers).append('|')
                .append(report.laserMissionKills).append('|')
                .append(report.laserBallisticMissNeutralizations).append('|')
                .append(report.laserHardKills).append('|')
                .append(Double.toHexString(report.laserBeamSeconds)).append('|')
                .append(report.leakers);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    record SurfacePoint(int corvettes, int escorts, double spacingM, SalvoReport report) {
    }

    record EndurancePoint(
            int escorts,
            double spacingM,
            int wave,
            SalvoReport report,
            int areaRoundsRemaining,
            int fleetRoundsRemaining) {
    }

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
        int interceptorKills() {
            return areaInterceptorKills + fleetInterceptorKills;
        }

        int accountedThreats() {
            return interceptorKills() + laserBallisticMissNeutralizations + laserHardKills + leakers;
        }
    }

    static final class MagazineState {
        private final int[] areaRemaining;
        private final int[] fleetRemaining;

        private MagazineState(int defenderCount) {
            areaRemaining = new int[defenderCount];
            fleetRemaining = new int[defenderCount];
            Arrays.fill(areaRemaining, AREA_MAGAZINE_PER_DEFENDER);
            Arrays.fill(fleetRemaining, FLEET_MAGAZINE_PER_DEFENDER);
        }

        static MagazineState full(int defenderCount) {
            if (defenderCount <= 0) {
                throw new IllegalArgumentException("defenderCount must be positive");
            }
            return new MagazineState(defenderCount);
        }

        int defenderCount() {
            return areaRemaining.length;
        }

        int areaRemaining(int defender) {
            return areaRemaining[defender];
        }

        int fleetRemaining(int defender) {
            return fleetRemaining[defender];
        }

        void consumeArea(int defender) {
            if (areaRemaining[defender] <= 0) {
                throw new IllegalStateException("area magazine exhausted");
            }
            areaRemaining[defender]--;
        }

        void consumeFleet(int defender) {
            if (fleetRemaining[defender] <= 0) {
                throw new IllegalStateException("fleet magazine exhausted");
            }
            fleetRemaining[defender]--;
        }

        int totalAreaRemaining() {
            return Arrays.stream(areaRemaining).sum();
        }

        int totalFleetRemaining() {
            return Arrays.stream(fleetRemaining).sum();
        }
    }

    private record InterceptorSpec(
            double wetMassKg,
            double dryMassKg,
            double exhaustVelocityMps,
            double thrustN,
            double navigationConstant,
            double proximityKillRadiusM) {
    }

    private record InterceptResult(boolean success, double interceptTimeS, double closestRangeM) {
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
        private final int cohortSize;
        private ThreatMode mode = ThreatMode.GUIDED;
        private double absorbedFluenceJPerM2;
        private double ballisticStartTimeS;
        private MotionState ballisticInitialState;

        private Threat(int id, int cohortSize) {
            this.id = id;
            this.cohortSize = cohortSize;
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
            return guidedThreatState(id, cohortSize, timeS);
        }

        private void enterBallisticState(double timeS, MotionState state) {
            ballisticStartTimeS = timeS;
            ballisticInitialState = state;
            mode = ThreatMode.BALLISTIC;
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
