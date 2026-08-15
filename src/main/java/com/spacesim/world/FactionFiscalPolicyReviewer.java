package com.spacesim.world;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Stage-17F.6 fiscal controller over the existing common fiscal-policy boundary.
 *
 * <p>The reviewer observes only real treasury/station/project wallet diagnostics. It never executes
 * taxes, subsidies or construction funding. Planning is read-only and separated from the common
 * review-window claim so a higher-level coordinator can claim one window and eventually apply several
 * policy families inside it.</p>
 */
public final class FactionFiscalPolicyReviewer {
    private static final BigInteger BASIS_POINTS = BigInteger.valueOf(10_000L);

    private FactionFiscalPolicyReviewer() {
        throw new AssertionError("Utility class");
    }

    /** Fiscal signal zone selected before a review claim. */
    public enum Zone {
        /** Liquidity shortfall is at/below the exit threshold. */
        NORMAL,
        /** Liquidity shortfall is at/above the enter threshold. */
        STRESS,
        /** Signal is inside the deadband and policy must remain unchanged. */
        DEADBAND
    }

    /**
     * Source-compatible one-shot review through the shared Stage-17F.6 lifecycle.
     *
     * <p>The method plans first, then claims the common review window, then applies the planned fiscal
     * step. New multi-policy coordinators should use {@link #plan(WorldSimulation, String,
     * FactionFiscalReviewProfile)} and {@link #applyClaimed(WorldSimulation, String, Plan)} directly.</p>
     *
     * @param world authoritative world runtime
     * @param factionContentId stable faction ID
     * @param cadence common policy-review cadence
     * @param profile explicit bounded fiscal targets and thresholds
     * @return immutable review result
     */
    public static Result review(
            WorldSimulation world,
            String factionContentId,
            FactionPolicyReviewCadence cadence,
            FactionFiscalReviewProfile profile) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        String factionId = normalizedFactionId(factionContentId);
        FactionPolicyReviewCadence checkedCadence = Objects.requireNonNull(
                cadence, "Faction policy review cadence not set");
        Plan plan = plan(checkedWorld, factionId, profile);
        if (!checkedWorld.tryBeginFactionPolicyReview(factionId, checkedCadence)) {
            return plan.result(false, false, plan.previousPolicy());
        }
        return applyClaimed(checkedWorld, factionId, plan);
    }

    /**
     * Builds one read-only fiscal policy plan from live diagnostics and an explicit response profile.
     *
     * @param world authoritative world runtime
     * @param factionContentId stable faction ID
     * @param profile explicit bounded fiscal targets and thresholds
     * @return immutable read-only plan
     */
    public static Plan plan(
            WorldSimulation world,
            String factionContentId,
            FactionFiscalReviewProfile profile) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        String factionId = normalizedFactionId(factionContentId);
        FactionFiscalReviewProfile checkedProfile = Objects.requireNonNull(
                profile, "Faction fiscal review profile not set");
        FactionFiscalPositionDiagnostics diagnostics = FactionFiscalPositionAnalyzer.analyze(
                checkedWorld, factionId);
        FactionFiscalPolicyState previous = diagnostics.policy();
        int shortfallBasisPoints = liquidityShortfallBasisPoints(diagnostics);
        Zone zone = zone(shortfallBasisPoints, checkedProfile);
        FactionFiscalPolicyState candidate = candidate(previous, zone, checkedProfile);
        return new Plan(zone, shortfallBasisPoints, previous, candidate);
    }

    /**
     * Applies a previously built fiscal plan inside an already claimed common policy-review window.
     *
     * <p>The method refuses stale plans if another operation changed fiscal policy after planning. It
     * does not inspect or modify the review watermark; ownership of that claim belongs to the caller.</p>
     *
     * @param world authoritative world runtime
     * @param factionContentId stable faction ID
     * @param plan previously built read-only plan
     * @return immutable result marked as a claimed review
     */
    public static Result applyClaimed(
            WorldSimulation world,
            String factionContentId,
            Plan plan) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        String factionId = normalizedFactionId(factionContentId);
        Plan checkedPlan = Objects.requireNonNull(plan, "Faction fiscal review plan not set");
        FactionFiscalPolicyState livePolicy = checkedWorld.findFactionFiscalPolicy(factionId)
                .orElseThrow(() -> new IllegalArgumentException("Faction has no fiscal policy: " + factionId));
        if (!livePolicy.equals(checkedPlan.previousPolicy())) {
            throw new IllegalStateException("Fiscal policy changed after review planning for faction: " + factionId);
        }
        if (!checkedPlan.changesPolicy()) {
            return checkedPlan.result(true, false, livePolicy);
        }
        FactionFiscalPolicyState installed = checkedWorld.updateFactionFiscalPolicy(
                factionId, checkedPlan.candidatePolicy());
        return checkedPlan.result(true, true, installed);
    }

    private static Zone zone(int shortfallBasisPoints, FactionFiscalReviewProfile profile) {
        if (shortfallBasisPoints >= profile.liquidityStressEnterBasisPoints()) {
            return Zone.STRESS;
        }
        if (shortfallBasisPoints <= profile.liquidityStressExitBasisPoints()) {
            return Zone.NORMAL;
        }
        return Zone.DEADBAND;
    }

    private static FactionFiscalPolicyState candidate(
            FactionFiscalPolicyState current,
            Zone zone,
            FactionFiscalReviewProfile profile) {
        if (zone == Zone.DEADBAND) {
            return current;
        }
        int targetTax = zone == Zone.STRESS
                ? profile.stressStationTaxTargetBasisPoints()
                : profile.normalStationTaxTargetBasisPoints();
        long targetSupport = zone == Zone.STRESS
                ? profile.stressLiquiditySupportCapMilliCredits()
                : profile.normalLiquiditySupportCapMilliCredits();
        int tax = FactionPolicyHysteresis.boundedBasisPointStep(
                current.stationTaxBasisPoints(),
                targetTax,
                profile.maxStationTaxStepBasisPoints());
        long support = FactionPolicyHysteresis.boundedMoneyStep(
                current.maxLiquiditySupportPerDecisionMilliCredits(),
                targetSupport,
                profile.maxLiquiditySupportCapStepMilliCredits());
        return new FactionFiscalPolicyState(
                tax,
                current.foreignTerritoryLevyBasisPoints(),
                current.treasuryReserveFloorMilliCredits(),
                current.stationLiquidityReserveMilliCredits(),
                support,
                current.maxConstructionInvestmentPerDecisionMilliCredits());
    }

    private static int liquidityShortfallBasisPoints(FactionFiscalPositionDiagnostics diagnostics) {
        long target = diagnostics.liquidityReserveTargetMilliCredits();
        long shortfall = diagnostics.liquidityShortfallMilliCredits();
        if (target <= 0L || shortfall <= 0L) {
            return 0;
        }
        if (shortfall >= target) {
            return 10_000;
        }
        BigInteger scaled = BigInteger.valueOf(shortfall).multiply(BASIS_POINTS);
        BigInteger roundedUp = scaled.add(BigInteger.valueOf(target - 1L))
                .divide(BigInteger.valueOf(target));
        return Math.min(10_000, roundedUp.intValueExact());
    }

    private static String normalizedFactionId(String factionContentId) {
        String factionId = Objects.requireNonNull(factionContentId, "Faction content ID not set").strip();
        if (factionId.isEmpty()) {
            throw new IllegalArgumentException("Faction content ID cannot be blank");
        }
        return factionId;
    }

    /**
     * Immutable read-only fiscal plan produced before the common review window is claimed.
     *
     * @param zone measured signal zone
     * @param liquidityShortfallBasisPoints real shortfall / configured reserve target ratio
     * @param previousPolicy policy observed while planning
     * @param candidatePolicy bounded candidate policy
     */
    public record Plan(
            Zone zone,
            int liquidityShortfallBasisPoints,
            FactionFiscalPolicyState previousPolicy,
            FactionFiscalPolicyState candidatePolicy) {

        /**
         * Validates one immutable fiscal review plan.
         *
         * @param zone measured fiscal signal zone
         * @param liquidityShortfallBasisPoints measured shortfall ratio
         * @param previousPolicy policy observed while planning
         * @param candidatePolicy bounded candidate policy
         */
        public Plan {
            Objects.requireNonNull(zone, "Fiscal review zone not set");
            Objects.requireNonNull(previousPolicy, "Previous fiscal policy not set");
            Objects.requireNonNull(candidatePolicy, "Candidate fiscal policy not set");
            if (liquidityShortfallBasisPoints < 0 || liquidityShortfallBasisPoints > 10_000) {
                throw new IllegalArgumentException("Liquidity shortfall ratio must be in 0..10000 bps");
            }
        }

        /** @return whether applying this plan changes fiscal policy values */
        public boolean changesPolicy() {
            return !previousPolicy.equals(candidatePolicy);
        }

        private Result result(
                boolean reviewClaimed,
                boolean policyChanged,
                FactionFiscalPolicyState resultingPolicy) {
            return new Result(
                    reviewClaimed,
                    policyChanged,
                    zone,
                    liquidityShortfallBasisPoints,
                    previousPolicy,
                    resultingPolicy);
        }
    }

    /**
     * Explainable result of one attempted fiscal review.
     *
     * @param reviewClaimed whether the common cadence window was successfully claimed
     * @param policyChanged whether the fiscal policy was actually updated
     * @param zone measured signal zone
     * @param liquidityShortfallBasisPoints real shortfall / configured reserve target ratio
     * @param previousPolicy policy before this call
     * @param resultingPolicy policy after this call
     */
    public record Result(
            boolean reviewClaimed,
            boolean policyChanged,
            Zone zone,
            int liquidityShortfallBasisPoints,
            FactionFiscalPolicyState previousPolicy,
            FactionFiscalPolicyState resultingPolicy) {

        /**
         * Validates one immutable review result.
         *
         * @param reviewClaimed whether the review window was claimed
         * @param policyChanged whether policy changed
         * @param zone measured fiscal signal zone
         * @param liquidityShortfallBasisPoints measured shortfall ratio
         * @param previousPolicy policy before review
         * @param resultingPolicy policy after review
         */
        public Result {
            Objects.requireNonNull(zone, "Fiscal review zone not set");
            Objects.requireNonNull(previousPolicy, "Previous fiscal policy not set");
            Objects.requireNonNull(resultingPolicy, "Resulting fiscal policy not set");
            if (liquidityShortfallBasisPoints < 0 || liquidityShortfallBasisPoints > 10_000) {
                throw new IllegalArgumentException("Liquidity shortfall ratio must be in 0..10000 bps");
            }
            if (policyChanged && !reviewClaimed) {
                throw new IllegalArgumentException("Fiscal policy cannot change without a claimed review");
            }
            if (policyChanged == previousPolicy.equals(resultingPolicy)) {
                throw new IllegalArgumentException("Fiscal review change flag is inconsistent with policy values");
            }
        }
    }
}
