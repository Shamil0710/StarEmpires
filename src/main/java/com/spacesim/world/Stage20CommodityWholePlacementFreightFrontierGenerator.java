package com.spacesim.world;

import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.CommodityFrontier;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.CommodityOption;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.FrontierStatus;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.DemandPlan;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.ProducerUsage;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.StartPlan;
import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Deterministic Stage-20E sweep that constructs a nondominated per-commodity whole-placement
 * freight frontier from an exact physical planner capable of enforcing a different finite freight
 * upper bound at every placed faction start.
 *
 * <p>The generator enumerates every positive budget vector up to the caller-authorized maximum.
 * One deterministic accepted physical plan per budget vector is sufficient to recover every
 * nondominated ship-usage vector: if a nondominated feasible vector {@code v} is evaluated under
 * upper bounds {@code v}, any accepted result {@code u} must satisfy {@code u <= v}. If
 * {@code u < v}, then {@code v} was dominated and therefore could not have been nondominated.</p>
 *
 * <p>The generator never turns an unresolved physical evaluation into infeasibility. Any unresolved
 * budget vector keeps the emitted combinable frontier in {@link FrontierStatus#UNRESOLVED_SEARCH_BUDGET},
 * while already discovered concrete physical options remain available to the exact cross-commodity
 * combiner.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20CommodityWholePlacementFreightFrontierGenerator {
    /** Stable frontier-sweep version. */
    public static final String CURRENT_VERSION = "stage20e.commodity-whole-placement-freight-frontier-generator.v1";

    private Stage20CommodityWholePlacementFreightFrontierGenerator() {
        throw new AssertionError("No instances");
    }

    /** Result of one exact physical solve under one per-start budget vector. */
    public enum EvaluationStatus {
        /** A concrete physically valid whole-placement plan was found within the supplied vector. */ ACCEPTED,
        /** The supplied vector was proved physically infeasible. */ INFEASIBLE,
        /** The physical planner exhausted its bounded search before proof. */ UNRESOLVED_SEARCH_BUDGET
    }

    /**
     * Adapter to the authoritative physical coordinated planner with exact per-start upper bounds.
     */
    @FunctionalInterface
    public interface BudgetVectorPlanner {
        /**
         * Evaluates one exact per-start freight upper-bound vector for one commodity.
         *
         * @param requirement single commodity bootstrap requirement
         * @param remoteFreighterBudgetByFaction canonical positive budget at every placed start
         * @param searchNodeBudget caller-authorized physical search-node budget for this vector
         * @return bounded physical feasibility evidence
         */
        PhysicalEvaluation evaluate(
                CommodityRequirement requirement,
                Map<String, Integer> remoteFreighterBudgetByFaction,
                int searchNodeBudget);
    }

    /** Physical evidence returned by the budget-vector planner adapter. */
    public record PhysicalEvaluation(
            EvaluationStatus status,
            int searchNodesVisited,
            List<StartPlan> starts,
            List<ProducerUsage> producerUsage) {
        /**
         * Validates generic bounded physical evidence.
         *
         * @param status physical bounded-solve status
         * @param searchNodesVisited search states inspected by the physical solver
         * @param starts accepted physical start plans; empty unless accepted
         * @param producerUsage accepted shared producer-capacity use; empty unless accepted
         */
        public PhysicalEvaluation {
            Objects.requireNonNull(status, "status");
            if (searchNodesVisited < 0) {
                throw new IllegalArgumentException("searchNodesVisited must be non-negative");
            }
            ArrayList<StartPlan> startCopy = new ArrayList<>(Objects.requireNonNull(starts, "starts"));
            ArrayList<ProducerUsage> producerCopy = new ArrayList<>(Objects.requireNonNull(producerUsage, "producerUsage"));
            if (startCopy.stream().anyMatch(Objects::isNull) || producerCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("physical evaluation evidence cannot contain nulls");
            }
            startCopy.sort(Comparator.comparing(StartPlan::stableFactionId));
            producerCopy.sort(Comparator.comparing(ProducerUsage::supplyKey));
            starts = List.copyOf(startCopy);
            producerUsage = List.copyOf(producerCopy);
            if (status == EvaluationStatus.ACCEPTED) {
                if (starts.isEmpty() || producerUsage.isEmpty()) {
                    throw new IllegalArgumentException("accepted physical evaluation must expose starts and producer usage");
                }
            } else if (!starts.isEmpty() || !producerUsage.isEmpty()) {
                throw new IllegalArgumentException("non-accepted physical evaluation cannot expose accepted plans");
            }
        }
    }

    /** One retained nondominated physical frontier plan. */
    public record FrontierPlan(
            CommodityOption option,
            Map<String, Integer> evaluatedBudgetByFaction,
            int searchNodesVisited,
            List<StartPlan> starts,
            List<ProducerUsage> producerUsage) {
        /**
         * Validates retained physical frontier evidence.
         *
         * @param option combinable ship-usage projection
         * @param evaluatedBudgetByFaction exact upper-bound vector under which this plan was found
         * @param searchNodesVisited physical search states used by that solve
         * @param starts accepted detailed physical start plans
         * @param producerUsage accepted detailed shared producer usage
         */
        public FrontierPlan {
            Objects.requireNonNull(option, "option");
            evaluatedBudgetByFaction = canonicalPositiveMap(evaluatedBudgetByFaction, "evaluatedBudgetByFaction");
            if (searchNodesVisited < 0) {
                throw new IllegalArgumentException("searchNodesVisited must be non-negative");
            }
            ArrayList<StartPlan> startCopy = new ArrayList<>(Objects.requireNonNull(starts, "starts"));
            ArrayList<ProducerUsage> producerCopy = new ArrayList<>(Objects.requireNonNull(producerUsage, "producerUsage"));
            if (startCopy.isEmpty() || producerCopy.isEmpty()
                    || startCopy.stream().anyMatch(Objects::isNull)
                    || producerCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("frontier plan must expose non-null physical evidence");
            }
            startCopy.sort(Comparator.comparing(StartPlan::stableFactionId));
            producerCopy.sort(Comparator.comparing(ProducerUsage::supplyKey));
            starts = List.copyOf(startCopy);
            producerUsage = List.copyOf(producerCopy);
            Map<String, Integer> actualUsage = usageByFaction(starts);
            if (!actualUsage.equals(option.remoteFreightersByFaction())) {
                throw new IllegalArgumentException("frontier option must equal actual physical start ship usage");
            }
            if (!actualUsage.keySet().equals(evaluatedBudgetByFaction.keySet())) {
                throw new IllegalArgumentException("frontier plan faction set must match evaluated budget vector");
            }
            for (String faction : actualUsage.keySet()) {
                if (actualUsage.get(faction) > evaluatedBudgetByFaction.get(faction)) {
                    throw new IllegalArgumentException("frontier physical usage cannot exceed evaluated budget vector");
                }
            }
        }
    }

    /** Complete deterministic sweep evidence for one commodity. */
    public record FrontierReport(
            String version,
            String placementVersion,
            String commodityId,
            int maximumRemoteFreightersPerStart,
            int searchNodeBudgetPerVector,
            int budgetVectorsEvaluated,
            int acceptedVectorCount,
            int infeasibleVectorCount,
            int unresolvedVectorCount,
            long totalSearchNodesVisited,
            CommodityFrontier combinableFrontier,
            List<FrontierPlan> plans) {
        /**
         * Validates one complete frontier sweep report.
         *
         * @param version generator version
         * @param placementVersion accepted placement authority version
         * @param commodityId stable commodity identifier
         * @param maximumRemoteFreightersPerStart inclusive positive vector bound
         * @param searchNodeBudgetPerVector bounded physical search allowance for each vector
         * @param budgetVectorsEvaluated number of exact budget vectors inspected
         * @param acceptedVectorCount vectors with concrete physical plans
         * @param infeasibleVectorCount vectors proved physically infeasible
         * @param unresolvedVectorCount vectors whose physical search exhausted its budget
         * @param totalSearchNodesVisited aggregate physical search states over the sweep
         * @param combinableFrontier nondominated ship-vector projection for exact cross-commodity combination
         * @param plans retained detailed physical plan per nondominated ship vector
         */
        public FrontierReport {
            version = requireText(version, "version");
            placementVersion = requireText(placementVersion, "placementVersion");
            commodityId = requireText(commodityId, "commodityId");
            if (maximumRemoteFreightersPerStart <= 0 || searchNodeBudgetPerVector <= 0
                    || budgetVectorsEvaluated <= 0 || acceptedVectorCount < 0
                    || infeasibleVectorCount < 0 || unresolvedVectorCount < 0
                    || totalSearchNodesVisited < 0L
                    || acceptedVectorCount + infeasibleVectorCount + unresolvedVectorCount != budgetVectorsEvaluated) {
                throw new IllegalArgumentException("frontier sweep counts/budgets must be valid and exhaustive");
            }
            Objects.requireNonNull(combinableFrontier, "combinableFrontier");
            if (!commodityId.equals(combinableFrontier.commodityId())) {
                throw new IllegalArgumentException("combinable frontier commodity must match report");
            }
            FrontierStatus expectedStatus = unresolvedVectorCount == 0
                    ? FrontierStatus.COMPLETE
                    : FrontierStatus.UNRESOLVED_SEARCH_BUDGET;
            if (combinableFrontier.status() != expectedStatus) {
                throw new IllegalArgumentException("frontier completeness must match unresolved-vector evidence");
            }
            ArrayList<FrontierPlan> planCopy = new ArrayList<>(Objects.requireNonNull(plans, "plans"));
            if (planCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("frontier plans cannot contain nulls");
            }
            planCopy.sort(Comparator.comparing(value -> value.option().optionId()));
            plans = List.copyOf(planCopy);
            List<CommodityOption> projected = plans.stream().map(FrontierPlan::option).toList();
            if (!projected.equals(combinableFrontier.options())) {
                throw new IllegalArgumentException("detailed plans must exactly match combinable frontier options");
            }
        }
    }

    /**
     * Sweeps every positive per-start upper-bound vector and emits the nondominated physical frontier.
     *
     * @param placement accepted faction-start placement
     * @param requirement single commodity bootstrap requirement
     * @param maximumRemoteFreightersPerStart inclusive maximum remote-freighter budget at each start
     * @param searchNodeBudgetPerVector physical search-node budget applied independently to each vector
     * @param planner authoritative exact per-start-budget physical planner adapter
     * @return deterministic physical frontier sweep evidence
     */
    public static FrontierReport generate(
            PlacementResult placement,
            CommodityRequirement requirement,
            int maximumRemoteFreightersPerStart,
            int searchNodeBudgetPerVector,
            BudgetVectorPlanner planner) {
        PlacementResult checkedPlacement = requireAcceptedPlacement(placement);
        CommodityRequirement checkedRequirement = Objects.requireNonNull(requirement, "requirement");
        if (maximumRemoteFreightersPerStart <= 0 || searchNodeBudgetPerVector <= 0) {
            throw new IllegalArgumentException("frontier maximum/search budgets must be positive");
        }
        BudgetVectorPlanner checkedPlanner = Objects.requireNonNull(planner, "planner");

        ArrayList<Assignment> assignments = new ArrayList<>(checkedPlacement.assignments());
        assignments.sort(Comparator.comparing(Assignment::stableFactionId));
        ArrayList<String> factions = new ArrayList<>();
        for (Assignment assignment : assignments) {
            factions.add(WorldFactionIdentityState.normalizeStableId(assignment.stableFactionId()));
        }

        SweepAccumulator accumulator = new SweepAccumulator(
                checkedPlacement,
                checkedRequirement,
                maximumRemoteFreightersPerStart,
                searchNodeBudgetPerVector,
                checkedPlanner,
                List.copyOf(factions));
        enumerateVectors(accumulator, new int[factions.size()], 0);

        ArrayList<FrontierPlan> nondominated = nondominatedPlans(
                new ArrayList<>(accumulator.firstPlanByUsageVector.values()),
                factions);
        nondominated.sort(Comparator.comparing(value -> value.option().optionId()));
        FrontierStatus frontierStatus = accumulator.unresolvedVectorCount == 0
                ? FrontierStatus.COMPLETE
                : FrontierStatus.UNRESOLVED_SEARCH_BUDGET;
        CommodityFrontier combinable = new CommodityFrontier(
                checkedRequirement.commodityId(),
                CURRENT_VERSION,
                frontierStatus,
                nondominated.stream().map(FrontierPlan::option).toList());

        return new FrontierReport(
                CURRENT_VERSION,
                checkedPlacement.version(),
                checkedRequirement.commodityId(),
                maximumRemoteFreightersPerStart,
                searchNodeBudgetPerVector,
                accumulator.budgetVectorsEvaluated,
                accumulator.acceptedVectorCount,
                accumulator.infeasibleVectorCount,
                accumulator.unresolvedVectorCount,
                accumulator.totalSearchNodesVisited,
                combinable,
                nondominated);
    }

    private static void enumerateVectors(
            SweepAccumulator accumulator,
            int[] vector,
            int index) {
        if (index == vector.length) {
            evaluateVector(accumulator, vector);
            return;
        }
        for (int budget = 1; budget <= accumulator.maximumRemoteFreightersPerStart; budget++) {
            vector[index] = budget;
            enumerateVectors(accumulator, vector, index + 1);
        }
    }

    private static void evaluateVector(SweepAccumulator accumulator, int[] vector) {
        LinkedHashMap<String, Integer> budget = new LinkedHashMap<>();
        for (int index = 0; index < accumulator.factions.size(); index++) {
            budget.put(accumulator.factions.get(index), vector[index]);
        }
        Map<String, Integer> canonicalBudget = Collections.unmodifiableMap(new TreeMap<>(budget));
        PhysicalEvaluation evaluation = Objects.requireNonNull(
                accumulator.planner.evaluate(
                        accumulator.requirement,
                        canonicalBudget,
                        accumulator.searchNodeBudgetPerVector),
                "budget vector planner result");
        accumulator.budgetVectorsEvaluated++;
        accumulator.totalSearchNodesVisited = Math.addExact(
                accumulator.totalSearchNodesVisited,
                evaluation.searchNodesVisited());

        switch (evaluation.status()) {
            case INFEASIBLE -> accumulator.infeasibleVectorCount++;
            case UNRESOLVED_SEARCH_BUDGET -> accumulator.unresolvedVectorCount++;
            case ACCEPTED -> {
                accumulator.acceptedVectorCount++;
                validateAcceptedEvaluation(accumulator, canonicalBudget, evaluation);
                Map<String, Integer> usage = usageByFaction(evaluation.starts());
                String optionId = optionId(accumulator.requirement.commodityId(), usage);
                CommodityOption option = new CommodityOption(
                        optionId,
                        accumulator.requirement.commodityId(),
                        usage);
                FrontierPlan plan = new FrontierPlan(
                        option,
                        canonicalBudget,
                        evaluation.searchNodesVisited(),
                        evaluation.starts(),
                        evaluation.producerUsage());
                UsageVector key = new UsageVector(accumulator.factions.stream().map(usage::get).toList());
                accumulator.firstPlanByUsageVector.putIfAbsent(key, plan);
            }
        }
    }

    private static void validateAcceptedEvaluation(
            SweepAccumulator accumulator,
            Map<String, Integer> budget,
            PhysicalEvaluation evaluation) {
        if (evaluation.starts().size() != accumulator.factions.size()) {
            throw new IllegalArgumentException("accepted physical evaluation must cover every placed faction start exactly once");
        }
        TreeMap<String, StartPlan> startsByFaction = new TreeMap<>();
        for (StartPlan start : evaluation.starts()) {
            String faction = WorldFactionIdentityState.normalizeStableId(start.stableFactionId());
            if (startsByFaction.putIfAbsent(faction, start) != null) {
                throw new IllegalArgumentException("accepted physical evaluation contains duplicate faction starts");
            }
            Integer expectedBudget = budget.get(faction);
            if (expectedBudget == null || start.remoteFreighterBudget() != expectedBudget) {
                throw new IllegalArgumentException("physical start plan must expose the exact evaluated per-start budget");
            }
            if (start.demands().size() != 1) {
                throw new IllegalArgumentException("single-commodity frontier evaluation must contain exactly one demand per start");
            }
            DemandPlan demand = start.demands().get(0);
            if (!accumulator.requirement.commodityId().equals(demand.commodityId())) {
                throw new IllegalArgumentException("physical frontier demand commodity must match requested commodity");
            }
        }
        if (!startsByFaction.keySet().equals(budget.keySet())) {
            throw new IllegalArgumentException("accepted physical evaluation faction set must match the evaluated vector");
        }
        for (ProducerUsage usage : evaluation.producerUsage()) {
            if (!accumulator.requirement.commodityId().equals(usage.supplyKey().commodityId())) {
                throw new IllegalArgumentException("single-commodity frontier producer usage cannot contain another commodity");
            }
        }
    }

    private static ArrayList<FrontierPlan> nondominatedPlans(
            ArrayList<FrontierPlan> source,
            List<String> factions) {
        source.sort(Comparator.comparing(value -> value.option().optionId()));
        ArrayList<FrontierPlan> kept = new ArrayList<>();
        for (FrontierPlan candidate : source) {
            boolean dominated = false;
            for (FrontierPlan other : source) {
                if (candidate == other) {
                    continue;
                }
                if (strictlyDominates(
                        other.option().remoteFreightersByFaction(),
                        candidate.option().remoteFreightersByFaction(),
                        factions)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    private static boolean strictlyDominates(
            Map<String, Integer> left,
            Map<String, Integer> right,
            List<String> factions) {
        boolean strict = false;
        for (String faction : factions) {
            int leftCount = left.get(faction);
            int rightCount = right.get(faction);
            if (leftCount > rightCount) {
                return false;
            }
            strict |= leftCount < rightCount;
        }
        return strict;
    }

    private static Map<String, Integer> usageByFaction(List<StartPlan> starts) {
        TreeMap<String, Integer> result = new TreeMap<>();
        for (StartPlan start : starts) {
            String faction = WorldFactionIdentityState.normalizeStableId(start.stableFactionId());
            if (result.putIfAbsent(faction, start.remoteFreightersUsed()) != null) {
                throw new IllegalArgumentException("start plans contain duplicate canonical faction IDs");
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("start plans must be non-empty");
        }
        return Collections.unmodifiableMap(result);
    }

    private static String optionId(String commodityId, Map<String, Integer> usage) {
        StringBuilder value = new StringBuilder(requireText(commodityId, "commodityId")).append("|ships");
        new TreeMap<>(usage).forEach((faction, count) -> value.append('|').append(faction).append('=').append(count));
        return value.toString();
    }

    private static Map<String, Integer> canonicalPositiveMap(
            Map<String, Integer> source,
            String fieldName) {
        TreeMap<String, Integer> result = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : Objects.requireNonNull(source, fieldName).entrySet()) {
            String faction = WorldFactionIdentityState.normalizeStableId(entry.getKey());
            Integer count = Objects.requireNonNull(entry.getValue(), fieldName + " count");
            if (count <= 0) {
                throw new IllegalArgumentException(fieldName + " counts must be positive");
            }
            if (result.putIfAbsent(faction, count) != null) {
                throw new IllegalArgumentException(fieldName + " contains duplicate canonical faction IDs");
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must be non-empty");
        }
        return Collections.unmodifiableMap(result);
    }

    private static PlacementResult requireAcceptedPlacement(PlacementResult placement) {
        PlacementResult checked = Objects.requireNonNull(placement, "placement");
        if (checked.status() != PlacementStatus.ACCEPTED || checked.assignments().isEmpty()) {
            throw new IllegalArgumentException("frontier generation requires an accepted non-empty placement");
        }
        return checked;
    }

    private static String requireText(String value, String fieldName) {
        String checked = Objects.requireNonNull(value, fieldName).trim();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return checked;
    }

    private record UsageVector(List<Integer> counts) {
        private UsageVector {
            counts = List.copyOf(counts);
        }
    }

    private static final class SweepAccumulator {
        private final PlacementResult placement;
        private final CommodityRequirement requirement;
        private final int maximumRemoteFreightersPerStart;
        private final int searchNodeBudgetPerVector;
        private final BudgetVectorPlanner planner;
        private final List<String> factions;
        private final Map<UsageVector, FrontierPlan> firstPlanByUsageVector = new LinkedHashMap<>();
        private int budgetVectorsEvaluated;
        private int acceptedVectorCount;
        private int infeasibleVectorCount;
        private int unresolvedVectorCount;
        private long totalSearchNodesVisited;

        private SweepAccumulator(
                PlacementResult placement,
                CommodityRequirement requirement,
                int maximumRemoteFreightersPerStart,
                int searchNodeBudgetPerVector,
                BudgetVectorPlanner planner,
                List<String> factions) {
            this.placement = placement;
            this.requirement = requirement;
            this.maximumRemoteFreightersPerStart = maximumRemoteFreightersPerStart;
            this.searchNodeBudgetPerVector = searchNodeBudgetPerVector;
            this.planner = planner;
            this.factions = factions;
        }
    }
}
