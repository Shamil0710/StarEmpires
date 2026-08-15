package com.spacesim.world;

import java.util.Objects;

/**
 * Deterministic conversion from persistent institutional doctrine to common decision weights.
 *
 * <p>The conversion only changes priorities used by an evaluator. It is deliberately incapable of
 * changing wallets, inventories, production, combat statistics, territorial control or legal
 * access. Stage 21 may tune these baseline integer mappings without changing the persistence
 * contract.</p>
 */
public final class FactionDoctrineDecisionPolicy {
    private static final long MILLI_CREDITS_PER_ECONOMIC_POINT = 1_000L;
    private static final long INFORMATION_DECAY_TICKS = 3_600L;
    private static final int MINIMUM_DECISION_CONFIDENCE_BASIS_POINTS = 5_000;
    private static final int ACCEPT_UTILITY_THRESHOLD = 20;
    private static final int REJECT_UTILITY_THRESHOLD = -20;

    private FactionDoctrineDecisionPolicy() {
        throw new AssertionError("Utility class");
    }

    /**
     * Builds the Stage-17F baseline diplomatic policy from one persistent doctrine profile.
     *
     * <p>Trade openness raises the value placed on economic benefit and lowers dependency aversion;
     * resilience priority raises dependency aversion; security, sovereignty and legalism weight the
     * matching diagnostic/history components. Expansion preference and interventionism intentionally
     * remain persisted but are not forced into ordinary treaty scoring before their dedicated
     * expansion/security evaluators consume them.</p>
     *
     * @param doctrine persistent faction doctrine
     * @return deterministic common treaty-evaluation policy
     */
    public static DiplomaticDecisionDoctrine diplomatic(FactionDoctrineState doctrine) {
        FactionDoctrineState value = Objects.requireNonNull(doctrine, "Faction doctrine not set");
        int economicWeight = 25 + scale(value.tradeOpenness(), 75);
        int dependencyWeight = Math.min(
                100,
                20
                        + scale(value.economicResiliencePriority(), 55)
                        + scale(100 - value.tradeOpenness(), 25));
        int securityWeight = 25 + scale(value.securityPosture(), 75);
        int sovereigntyWeight = 25 + scale(value.sovereigntySensitivity(), 75);
        int trustWeight = 20 + scale(value.treatyLegalism(), 80);
        int credibilityWeight = 30 + scale(value.treatyLegalism(), 70);
        int fiscalWeight = 60;
        return new DiplomaticDecisionDoctrine(
                economicWeight,
                dependencyWeight,
                securityWeight,
                sovereigntyWeight,
                trustWeight,
                credibilityWeight,
                fiscalWeight,
                MILLI_CREDITS_PER_ECONOMIC_POINT,
                INFORMATION_DECAY_TICKS,
                MINIMUM_DECISION_CONFIDENCE_BASIS_POINTS,
                ACCEPT_UTILITY_THRESHOLD,
                REJECT_UTILITY_THRESHOLD);
    }

    private static int scale(int axis, int range) {
        return (axis * range) / 100;
    }
}
