package com.spacesim.world;

import java.util.Objects;

/**
 * Common deterministic utility evaluator for incoming treaty proposals.
 *
 * <p>The evaluator consumes explicitly observed/estimated economic and security diagnostics rather
 * than querying omniscient economic truth. Persistent directed trust/credibility come from the
 * evaluating faction's own diplomatic state. External diagnostic contributions decay with
 * freshness/confidence; insufficient information may prevent automatic acceptance but never hides a
 * strongly negative known utility.</p>
 */
public final class DiplomaticTreatyEvaluator {
    private static final int BASIS_POINTS_DENOMINATOR = 10_000;
    private static final int SCORE_LIMIT = 100;

    private DiplomaticTreatyEvaluator() {
        throw new AssertionError("Utility class");
    }

    /**
     * Evaluates one incoming persistent treaty proposal for its receiving counterparty.
     *
     * @param world authoritative world containing the proposal and directed diplomacy
     * @param treatyId globally unique proposed treaty ID
     * @param evaluatingFactionContentId receiving faction making the decision
     * @param doctrine deterministic decision weights and thresholds
     * @param inputs observed/estimated economic-security diagnostics
     * @return explainable deterministic recommendation and component utilities
     * @throws NullPointerException when a required argument is null
     * @throws IllegalArgumentException when the treaty/faction/observation is invalid
     * @throws IllegalStateException when the treaty is not an open proposal for this counterparty
     */
    public static DiplomaticTreatyEvaluation evaluate(
            WorldSimulation world,
            String treatyId,
            String evaluatingFactionContentId,
            DiplomaticDecisionDoctrine doctrine,
            DiplomaticTreatyEvaluationInputs inputs) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        String id = requireId(treatyId, "Treaty ID");
        String evaluatorId = requireId(evaluatingFactionContentId, "Evaluating faction ID");
        DiplomaticDecisionDoctrine policy = Objects.requireNonNull(doctrine, "Diplomatic decision doctrine not set");
        DiplomaticTreatyEvaluationInputs observed = Objects.requireNonNull(
                inputs, "Diplomatic treaty evaluation inputs not set");

        TreatyLocation location = locateTreaty(checkedWorld, id);
        DiplomaticTreatyState treaty = location.treaty();
        if (treaty.status() != DiplomaticTreatyState.Status.PROPOSED) {
            throw new IllegalStateException("Only an open treaty proposal can be evaluated: " + id);
        }
        if (!treaty.counterpartyFactionContentId().equals(evaluatorId)) {
            throw new IllegalStateException("Only the receiving treaty counterparty can evaluate the proposal");
        }
        long worldTick = checkedWorld.getAuthoritativeWorldTick();
        if (treaty.expiresTick() >= 0L && treaty.expiresTick() <= worldTick) {
            throw new IllegalStateException("Treaty proposal has passed its final expiry tick: " + id);
        }
        if (observed.observationTick() > worldTick) {
            throw new IllegalArgumentException("Diplomatic evaluation observation cannot come from the future");
        }

        checkedWorld.findFactionStrategicState(evaluatorId).orElseThrow(
                () -> new IllegalArgumentException("Evaluating faction has no strategic state: " + evaluatorId));
        FactionDiplomacyState evaluatorDiplomacy = checkedWorld.findFactionDiplomacyState(evaluatorId).orElseThrow(
                () -> new IllegalArgumentException("Evaluating faction has no diplomacy state: " + evaluatorId));
        String proposerId = location.ownerFactionContentId();

        long observationAge = worldTick - observed.observationTick();
        int freshnessBasisPoints = freshnessBasisPoints(observationAge, policy.informationDecayTicks());
        int effectiveConfidence = scaleBasisPoints(
                observed.confidenceBasisPoints(), freshnessBasisPoints);

        int economicRaw = normalizeSignedMoney(
                observed.expectedNetEconomicBenefitMilliCredits(),
                policy.milliCreditsPerEconomicPoint());
        int fiscalRaw = normalizeUnsignedMoney(
                observed.expectedFiscalCostMilliCredits(),
                policy.milliCreditsPerEconomicPoint());
        int credibilityRaw = clamp(
                (evaluatorDiplomacy.credibilityOf(proposerId) - DiplomaticStandingState.NEUTRAL_CREDIBILITY) * 2,
                -SCORE_LIMIT,
                SCORE_LIMIT);

        int economicUtility = observedContribution(
                economicRaw, policy.economicBenefitWeight(), effectiveConfidence);
        int dependencyUtility = observedContribution(
                -observed.criticalDependencyRiskScore(),
                policy.dependencyAversionWeight(),
                effectiveConfidence);
        int securityUtility = observedContribution(
                observed.securityValueScore(), policy.securityWeight(), effectiveConfidence);
        int sovereigntyUtility = observedContribution(
                -observed.sovereigntyCostScore(),
                policy.sovereigntyAversionWeight(),
                effectiveConfidence);
        int fiscalUtility = observedContribution(
                -fiscalRaw, policy.fiscalCostWeight(), effectiveConfidence);
        int trustUtility = weighted(
                evaluatorDiplomacy.trustTo(proposerId), policy.trustWeight());
        int credibilityUtility = weighted(credibilityRaw, policy.credibilityWeight());

        int total = economicUtility
                + dependencyUtility
                + securityUtility
                + sovereigntyUtility
                + trustUtility
                + credibilityUtility
                + fiscalUtility;

        DiplomaticTreatyEvaluation.Recommendation recommendation;
        if (total <= policy.rejectUtilityThreshold()) {
            recommendation = DiplomaticTreatyEvaluation.Recommendation.REJECT;
        } else if (effectiveConfidence < policy.minimumDecisionConfidenceBasisPoints()) {
            recommendation = DiplomaticTreatyEvaluation.Recommendation.COUNTEROFFER;
        } else if (total >= policy.acceptUtilityThreshold()) {
            recommendation = DiplomaticTreatyEvaluation.Recommendation.ACCEPT;
        } else {
            recommendation = DiplomaticTreatyEvaluation.Recommendation.COUNTEROFFER;
        }

        return new DiplomaticTreatyEvaluation(
                treaty.treatyId(),
                proposerId,
                evaluatorId,
                recommendation,
                total,
                economicUtility,
                dependencyUtility,
                securityUtility,
                sovereigntyUtility,
                trustUtility,
                credibilityUtility,
                fiscalUtility,
                effectiveConfidence,
                observationAge);
    }

    private static TreatyLocation locateTreaty(WorldSimulation world, String treatyId) {
        DiplomaticTreatyState found = world.findDiplomaticTreaty(treatyId).orElseThrow(
                () -> new IllegalArgumentException("Unknown treaty: " + treatyId));
        for (FactionDiplomacyState state : world.getFactionDiplomacyStates()) {
            for (DiplomaticTreatyState treaty : state.treaties()) {
                if (treaty.treatyId().equals(treatyId)) {
                    if (!treaty.equals(found)) {
                        throw new IllegalStateException("Treaty directory disagrees with authoritative lookup: " + treatyId);
                    }
                    return new TreatyLocation(state.factionContentId(), treaty);
                }
            }
        }
        throw new IllegalStateException("Treaty lookup lost its owning diplomacy directory: " + treatyId);
    }

    private static int freshnessBasisPoints(long observationAge, long decayTicks) {
        if (observationAge >= decayTicks) {
            return 0;
        }
        long remaining = decayTicks - observationAge;
        return (int) ((remaining * BASIS_POINTS_DENOMINATOR) / decayTicks);
    }

    private static int normalizeSignedMoney(long value, long scale) {
        long points = value / scale;
        if (points < -SCORE_LIMIT) {
            return -SCORE_LIMIT;
        }
        if (points > SCORE_LIMIT) {
            return SCORE_LIMIT;
        }
        return (int) points;
    }

    private static int normalizeUnsignedMoney(long value, long scale) {
        long points = value / scale;
        return points >= SCORE_LIMIT ? SCORE_LIMIT : (int) points;
    }

    private static int observedContribution(int rawScore, int weight, int confidenceBasisPoints) {
        return scaleBasisPoints(weighted(rawScore, weight), confidenceBasisPoints);
    }

    private static int weighted(int rawScore, int weight) {
        return (rawScore * weight) / 100;
    }

    private static int scaleBasisPoints(int value, int basisPoints) {
        return (int) (((long) value * basisPoints) / BASIS_POINTS_DENOMINATOR);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }

    private record TreatyLocation(String ownerFactionContentId, DiplomaticTreatyState treaty) {
    }
}
