package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionInterestResolver.DecisionTrace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Conservative Stage-21A-to-21B candidate bridge.
 *
 * <p>The resolver uses only the already-ranked actor-bounded evidence in a decision trace. It does
 * not query world truth, doctrine, fleets, treasury or diplomatic authority. Budget units are
 * abstract planning-envelope weights proportional to evidence severity.</p>
 */
public final class FactionStrategicGoalCandidateResolver {
    private FactionStrategicGoalCandidateResolver() {
        throw new AssertionError("Utility class");
    }

    /**
     * Projects current actor interests into the initial peaceful Stage-21B goal vocabulary.
     *
     * <p>Treaty obligations are intentionally not converted automatically: an obligation can imply
     * several responses and needs a later policy/read-model adapter rather than a hidden default.
     * Supply dependency conservatively becomes stockpiling; route-specific evidence becomes route
     * security.</p>
     *
     * @param trace Stage-21A actor-bounded decision trace
     * @return immutable canonical candidate list
     */
    public static List<StrategicGoalCandidate> resolve(DecisionTrace trace) {
        DecisionTrace checked = Objects.requireNonNull(trace, "Faction decision trace not set");
        ArrayList<StrategicGoalCandidate> candidates = new ArrayList<>();
        for (FactionInterestEvidence evidence : checked.orderedEvidence()) {
            StrategicGoalType type = defaultType(evidence.kind());
            if (type == null) {
                continue;
            }
            int urgency = evidence.priorityBasisPoints();
            long budgetUnits = urgency == 0 ? 0L : Math.max(1L, (urgency + 99L) / 100L);
            candidates.add(new StrategicGoalCandidate(
                    type,
                    evidence.targetId(),
                    StrategicGoalEvidence.from(evidence),
                    urgency,
                    10_000,
                    budgetUnits));
        }
        return candidates.stream().sorted().toList();
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
