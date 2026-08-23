package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionInterestResolver.DecisionTrace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Conservative Stage-21A-to-21B candidate bridge.
 *
 * <p>The resolver uses only already-ranked actor-bounded evidence in a decision trace. It does not
 * query world truth, fleets, treasury or diplomatic authority. Doctrine is caller-owned preference
 * input only. Cost profiles remain normalized planning weights.</p>
 */
public final class FactionStrategicGoalCandidateResolver {
    /** Default cadence for Stage-21B strategic-goal review. */
    public static final long DEFAULT_REVIEW_CADENCE_TICKS = 24L;

    private FactionStrategicGoalCandidateResolver() {
        throw new AssertionError("Utility class");
    }

    /**
     * Projects current actor interests into the conservative baseline goal vocabulary.
     *
     * <p>This compatibility entry point intentionally retains the pre-doctrine peaceful mapping.
     * Treaty obligations remain unconverted because their required response is policy-dependent.</p>
     *
     * @param trace Stage-21A actor-bounded decision trace
     * @return immutable canonical candidate list
     */
    public static List<StrategicGoalCandidate> resolve(DecisionTrace trace) {
        DecisionTrace checked = Objects.requireNonNull(trace, "Faction decision trace not set");
        ArrayList<StrategicGoalCandidate> candidates = new ArrayList<>();
        for (FactionInterestEvidence evidence : checked.orderedEvidence()) {
            StrategicGoalType type = defaultType(evidence.kind());
            if (type != null) {
                candidates.add(candidate(type, evidence, 10_000));
            }
        }
        return candidates.stream().sorted().toList();
    }

    /**
     * Projects actor-bounded interests into all doctrine-enabled compatible roadmap goal families.
     *
     * <p>A zero doctrine preference suppresses a family. This is especially important for coercion,
     * raids, blockades and invasions: the neutral doctrine profile disables them, so ambiguous
     * evidence can never silently manufacture hostile intent.</p>
     *
     * @param trace Stage-21A actor-bounded decision trace
     * @param doctrine immutable caller-owned strategic preference profile
     * @return immutable canonical candidate list
     */
    public static List<StrategicGoalCandidate> resolve(
            DecisionTrace trace,
            FactionStrategicDoctrineProfile doctrine) {
        DecisionTrace checked = Objects.requireNonNull(trace, "Faction decision trace not set");
        FactionStrategicDoctrineProfile policy = Objects.requireNonNull(doctrine, "Strategic doctrine not set");
        ArrayList<StrategicGoalCandidate> candidates = new ArrayList<>();
        for (FactionInterestEvidence evidence : checked.orderedEvidence()) {
            for (StrategicGoalType type : StrategicGoalType.values()) {
                int preference = policy.preferenceBasisPoints(type);
                if (preference > 0 && type.supports(evidence.kind())) {
                    candidates.add(candidate(type, evidence, preference));
                }
            }
        }
        return candidates.stream().sorted().distinct().toList();
    }

    private static StrategicGoalCandidate candidate(
            StrategicGoalType type,
            FactionInterestEvidence evidence,
            int doctrinePreferenceBasisPoints) {
        int urgency = evidence.priorityBasisPoints();
        return new StrategicGoalCandidate(
                type,
                evidence.targetId(),
                StrategicGoalEvidence.from(evidence),
                urgency,
                defaultStrategicValue(type),
                10_000,
                doctrinePreferenceBasisPoints,
                defaultBudget(type, urgency),
                List.of(),
                -1L,
                DEFAULT_REVIEW_CADENCE_TICKS,
                StrategicGoalOutcomeSignal.NONE);
    }

    private static int defaultStrategicValue(StrategicGoalType type) {
        return switch (type) {
            case DEFEND, SECURE_ROUTE, ESCORT -> 10_000;
            case STOCKPILE, OBTAIN_ACCESS, DETER, RECOVER -> 9_500;
            case EXPLORE, CLAIM -> 9_000;
            case COERCE, RAID, BLOCKADE, INVADE -> 8_500;
        };
    }

    private static StrategicPlanningEnvelope defaultBudget(StrategicGoalType type, int urgencyBasisPoints) {
        long scale = urgencyBasisPoints == 0 ? 0L : Math.max(1L, (urgencyBasisPoints + 999L) / 1_000L);
        return switch (type) {
            case SECURE_ROUTE -> new StrategicPlanningEnvelope(scale, 3L * scale, 0L, 3L * scale);
            case ESCORT -> new StrategicPlanningEnvelope(scale, 2L * scale, 0L, 4L * scale);
            case CLAIM -> new StrategicPlanningEnvelope(2L * scale, scale, scale, scale);
            case DETER -> new StrategicPlanningEnvelope(2L * scale, 2L * scale, 0L, 5L * scale);
            case COERCE -> new StrategicPlanningEnvelope(3L * scale, 2L * scale, 0L, 4L * scale);
            case RAID -> new StrategicPlanningEnvelope(2L * scale, 3L * scale, 0L, 6L * scale);
            case BLOCKADE -> new StrategicPlanningEnvelope(4L * scale, 5L * scale, 0L, 7L * scale);
            case INVADE -> new StrategicPlanningEnvelope(6L * scale, 7L * scale, 3L * scale, 9L * scale);
            case STOCKPILE -> new StrategicPlanningEnvelope(2L * scale, 3L * scale, scale, 0L);
            case EXPLORE -> new StrategicPlanningEnvelope(scale, scale, 0L, 2L * scale);
            case RECOVER -> new StrategicPlanningEnvelope(3L * scale, 3L * scale, scale, 6L * scale);
            case DEFEND -> new StrategicPlanningEnvelope(2L * scale, 2L * scale, scale, 4L * scale);
            case OBTAIN_ACCESS -> new StrategicPlanningEnvelope(2L * scale, scale, 0L, 0L);
        };
    }

    private static StrategicGoalType defaultType(InterestKind kind) {
        return switch (kind) {
            case SUPPLY_DEPENDENCY, RESOURCE_DEFICIT -> StrategicGoalType.STOCKPILE;
            case MARKET_ACCESS -> StrategicGoalType.OBTAIN_ACCESS;
            case ROUTE_EXPOSURE -> StrategicGoalType.SECURE_ROUTE;
            case BORDER_SECURITY -> StrategicGoalType.DEFEND;
            case TERRITORIAL_OPPORTUNITY -> StrategicGoalType.EXPLORE;
            case TREATY_OBLIGATION -> null;
        };
    }
}
