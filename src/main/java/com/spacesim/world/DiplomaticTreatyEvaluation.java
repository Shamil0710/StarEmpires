package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Explainable deterministic result of evaluating one incoming treaty proposal.
 *
 * @param treatyId evaluated proposal ID
 * @param proposerFactionContentId proposing faction
 * @param evaluatingFactionContentId faction deciding how to respond
 * @param recommendation common lifecycle recommendation
 * @param totalUtilityPoints summed utility after doctrine weights/confidence
 * @param economicBenefitUtility economic component contribution
 * @param dependencyUtility dependency-risk contribution
 * @param securityUtility security contribution
 * @param sovereigntyUtility sovereignty contribution
 * @param trustUtility directed-trust contribution
 * @param credibilityUtility directed-credibility contribution
 * @param fiscalCostUtility fiscal-cost contribution
 * @param effectiveConfidenceBasisPoints confidence after freshness decay
 * @param observationAgeTicks age of the diagnostic input at evaluation time
 */
public record DiplomaticTreatyEvaluation(
        String treatyId,
        String proposerFactionContentId,
        String evaluatingFactionContentId,
        Recommendation recommendation,
        int totalUtilityPoints,
        int economicBenefitUtility,
        int dependencyUtility,
        int securityUtility,
        int sovereigntyUtility,
        int trustUtility,
        int credibilityUtility,
        int fiscalCostUtility,
        int effectiveConfidenceBasisPoints,
        long observationAgeTicks) {

    /** Response recommended by the common utility model. */
    public enum Recommendation {
        /** Accept the current proposal. */
        ACCEPT,
        /** Do not accept current terms, but negotiation remains viable. */
        COUNTEROFFER,
        /** Reject current terms. */
        REJECT
    }

    /** Utility component categories exposed to UI/AI diagnostics. */
    public enum Reason {
        /** Expected net economic benefit. */
        ECONOMIC_BENEFIT,
        /** Critical import/export dependency risk. */
        DEPENDENCY_RISK,
        /** Security value or exposure. */
        SECURITY,
        /** Sovereignty/jurisdiction cost. */
        SOVEREIGNTY,
        /** Directed trust. */
        TRUST,
        /** Directed credibility assessment. */
        CREDIBILITY,
        /** Expected fiscal/treasury cost. */
        FISCAL_COST
    }

    /**
     * One explainable scored utility component.
     *
     * @param reason component category
     * @param utilityPoints signed contribution to total utility
     */
    public record ReasonContribution(Reason reason, int utilityPoints) {
        /**
         * Validates one reason contribution.
         *
         * @param reason component category
         * @param utilityPoints signed contribution to total utility
         */
        public ReasonContribution {
            reason = Objects.requireNonNull(reason, "Diplomatic evaluation reason not set");
        }
    }

    /**
     * Validates identity/confidence metadata and utility conservation.
     *
     * @param treatyId evaluated proposal ID
     * @param proposerFactionContentId proposing faction
     * @param evaluatingFactionContentId deciding faction
     * @param recommendation common lifecycle recommendation
     * @param totalUtilityPoints summed utility
     * @param economicBenefitUtility economic contribution
     * @param dependencyUtility dependency contribution
     * @param securityUtility security contribution
     * @param sovereigntyUtility sovereignty contribution
     * @param trustUtility trust contribution
     * @param credibilityUtility credibility contribution
     * @param fiscalCostUtility fiscal contribution
     * @param effectiveConfidenceBasisPoints confidence after freshness decay
     * @param observationAgeTicks age of diagnostic input
     */
    public DiplomaticTreatyEvaluation {
        treatyId = requireId(treatyId, "Evaluated treaty ID");
        proposerFactionContentId = requireId(proposerFactionContentId, "Treaty proposer faction ID");
        evaluatingFactionContentId = requireId(evaluatingFactionContentId, "Evaluating faction ID");
        recommendation = Objects.requireNonNull(recommendation, "Diplomatic recommendation not set");
        if (proposerFactionContentId.equals(evaluatingFactionContentId)) {
            throw new IllegalArgumentException("Treaty proposal cannot be evaluated as self-proposal");
        }
        if (effectiveConfidenceBasisPoints < 0 || effectiveConfidenceBasisPoints > 10_000) {
            throw new IllegalArgumentException("Effective confidence must be in [0,10000]");
        }
        if (observationAgeTicks < 0L) {
            throw new IllegalArgumentException("Observation age cannot be negative");
        }
        int expectedTotal = economicBenefitUtility
                + dependencyUtility
                + securityUtility
                + sovereigntyUtility
                + trustUtility
                + credibilityUtility
                + fiscalCostUtility;
        if (expectedTotal != totalUtilityPoints) {
            throw new IllegalArgumentException("Diplomatic utility total does not equal component sum");
        }
    }

    /**
     * Returns up to three strongest non-zero reasons ordered by absolute impact then enum order.
     *
     * @return immutable primary reason list suitable for UI explanation
     */
    public List<ReasonContribution> primaryReasons() {
        List<ReasonContribution> reasons = new ArrayList<>();
        add(reasons, Reason.ECONOMIC_BENEFIT, economicBenefitUtility);
        add(reasons, Reason.DEPENDENCY_RISK, dependencyUtility);
        add(reasons, Reason.SECURITY, securityUtility);
        add(reasons, Reason.SOVEREIGNTY, sovereigntyUtility);
        add(reasons, Reason.TRUST, trustUtility);
        add(reasons, Reason.CREDIBILITY, credibilityUtility);
        add(reasons, Reason.FISCAL_COST, fiscalCostUtility);
        reasons.sort(Comparator
                .comparingInt((ReasonContribution value) -> Math.abs(value.utilityPoints())).reversed()
                .thenComparing(ReasonContribution::reason));
        return List.copyOf(reasons.subList(0, Math.min(3, reasons.size())));
    }

    private static void add(List<ReasonContribution> target, Reason reason, int utility) {
        if (utility != 0) {
            target.add(new ReasonContribution(reason, utility));
        }
    }

    private static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label + " not set").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return normalized;
    }
}
