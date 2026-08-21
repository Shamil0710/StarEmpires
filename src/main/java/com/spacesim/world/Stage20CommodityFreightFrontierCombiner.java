package com.spacesim.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Exact Stage-20E combiner for independently generated per-commodity freight frontiers.
 *
 * <p>Producer capacities are keyed by commodity, so independently valid commodity plans do not share
 * producer throughput. Their remaining cross-commodity coupling is the finite inter-system freight
 * fleet available at each placed faction start. This combiner therefore treats each commodity option
 * as an already physically valid whole-placement plan and performs only the exact shared-fleet join.</p>
 *
 * <p>The join is a deterministic dynamic program over per-start ship-count vectors. State space is
 * bounded by the product of {@code (budget + 1)} for the placed starts rather than by the Cartesian
 * product of every supplier-route prefix. Dominated options are removed only when another option uses
 * no more ships at every start, which cannot remove a fleet-feasible combination.</p>
 *
 * <p>An incomplete upstream frontier can still produce an accepted result when its already discovered
 * options contain a feasible combination. When no known combination exists, any incomplete frontier
 * keeps the result unresolved rather than turning search incompleteness into physical infeasibility.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20CommodityFreightFrontierCombiner {
    /** Stable combiner version. */
    public static final String CURRENT_VERSION = "stage20e.commodity-freight-frontier-combiner.v1";

    private Stage20CommodityFreightFrontierCombiner() {
        throw new AssertionError("No instances");
    }

    /** Completeness state of one upstream per-commodity frontier. */
    public enum FrontierStatus {
        /** The upstream generator proved that the supplied option set is complete. */ COMPLETE,
        /** The upstream search budget ended before the complete option frontier was proved. */ UNRESOLVED_SEARCH_BUDGET
    }

    /** Final exact shared-fleet combination status. */
    public enum Status {
        /** A known set of physical commodity plans fits every start's finite fleet. */ ACCEPTED,
        /** Complete frontier evidence proves that no fitting shared-fleet combination exists. */ INFEASIBLE,
        /** No known combination fits, but at least one upstream frontier is incomplete. */ UNRESOLVED_FRONTIER
    }

    /** Explicit causal failure or unresolved reason. */
    public enum FailureReason {
        /** A complete commodity frontier contains no physically feasible option. */ COMMODITY_INFEASIBLE,
        /** Complete commodity frontiers exist but none fit the shared per-start freight budgets. */ SHARED_FLEET_COMBINATION_INFEASIBLE,
        /** At least one incomplete commodity frontier could still contain an enabling option. */ FRONTIER_INCOMPLETE
    }

    /** One already physically valid whole-placement option for a single commodity. */
    public record CommodityOption(
            String optionId,
            String commodityId,
            Map<String, Integer> remoteFreightersByFaction) {
        /**
         * Validates and canonicalizes one commodity option.
         *
         * @param optionId stable deterministic option identifier within the commodity frontier
         * @param commodityId stable commodity identifier
         * @param remoteFreightersByFaction remote freighters used at each placed faction start
         */
        public CommodityOption {
            optionId = requireText(optionId, "optionId");
            commodityId = requireText(commodityId, "commodityId");
            remoteFreightersByFaction = canonicalFreighterMap(
                    remoteFreightersByFaction,
                    false,
                    "remoteFreightersByFaction");
        }

        /**
         * Returns the total remote freighters used by this option across all starts.
         *
         * @return exact summed remote-freighter count
         */
        public int totalRemoteFreighters() {
            int total = 0;
            for (int count : remoteFreightersByFaction.values()) {
                total = Math.addExact(total, count);
            }
            return total;
        }
    }

    /** One upstream per-commodity option frontier. */
    public record CommodityFrontier(
            String commodityId,
            String frontierVersion,
            FrontierStatus status,
            List<CommodityOption> options) {
        /**
         * Validates one upstream commodity frontier.
         *
         * @param commodityId stable commodity identifier
         * @param frontierVersion upstream frontier-generator/evidence version
         * @param status whether the supplied option set is complete
         * @param options already discovered physically valid whole-placement options
         */
        public CommodityFrontier {
            commodityId = requireText(commodityId, "commodityId");
            frontierVersion = requireText(frontierVersion, "frontierVersion");
            Objects.requireNonNull(status, "status");
            ArrayList<CommodityOption> copy = new ArrayList<>(Objects.requireNonNull(options, "options"));
            if (copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("commodity frontier options cannot contain nulls");
            }
            TreeSet<String> optionIds = new TreeSet<>();
            for (CommodityOption option : copy) {
                if (!commodityId.equals(option.commodityId())) {
                    throw new IllegalArgumentException("frontier option commodity must match frontier commodity");
                }
                if (!optionIds.add(option.optionId())) {
                    throw new IllegalArgumentException("commodity frontier option IDs must be unique");
                }
            }
            copy.sort(Comparator.comparing(CommodityOption::optionId));
            options = List.copyOf(copy);
        }
    }

    /** One selected commodity option in an accepted exact combination. */
    public record SelectedOption(
            String commodityId,
            String frontierVersion,
            String optionId,
            Map<String, Integer> remoteFreightersByFaction) {
        /**
         * Validates one selected option projection.
         *
         * @param commodityId stable commodity identifier
         * @param frontierVersion source frontier version
         * @param optionId selected stable option identifier
         * @param remoteFreightersByFaction exact per-start freight usage of the selected option
         */
        public SelectedOption {
            commodityId = requireText(commodityId, "commodityId");
            frontierVersion = requireText(frontierVersion, "frontierVersion");
            optionId = requireText(optionId, "optionId");
            remoteFreightersByFaction = canonicalFreighterMap(
                    remoteFreightersByFaction,
                    false,
                    "remoteFreightersByFaction");
        }
    }

    /** Complete deterministic shared-fleet combination evidence. */
    public record CombinationReport(
            String version,
            Map<String, Integer> remoteFreighterBudgetByFaction,
            Status status,
            Optional<FailureReason> failureReason,
            Map<String, Integer> remoteFreightersUsedByFaction,
            List<SelectedOption> selectedOptions) {
        /**
         * Validates one final combination report.
         *
         * @param version combiner version
         * @param remoteFreighterBudgetByFaction finite freight budget at each placed faction start
         * @param status final exact combination status
         * @param failureReason explicit failure/unresolved reason when not accepted
         * @param remoteFreightersUsedByFaction exact selected usage when accepted, empty otherwise
         * @param selectedOptions selected per-commodity options when accepted, empty otherwise
         */
        public CombinationReport {
            version = requireText(version, "version");
            remoteFreighterBudgetByFaction = canonicalFreighterMap(
                    remoteFreighterBudgetByFaction,
                    true,
                    "remoteFreighterBudgetByFaction");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(failureReason, "failureReason");
            Map<String, Integer> suppliedUsage = Objects.requireNonNull(
                    remoteFreightersUsedByFaction,
                    "remoteFreightersUsedByFaction");
            Map<String, Integer> usage = suppliedUsage.isEmpty()
                    ? Map.of()
                    : canonicalFreighterMap(suppliedUsage, false, "remoteFreightersUsedByFaction");
            ArrayList<SelectedOption> selectedCopy = new ArrayList<>(
                    Objects.requireNonNull(selectedOptions, "selectedOptions"));
            if (selectedCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("selected options cannot contain nulls");
            }
            selectedCopy.sort(Comparator.comparing(SelectedOption::commodityId));
            remoteFreightersUsedByFaction = usage;
            selectedOptions = List.copyOf(selectedCopy);

            if (status == Status.ACCEPTED) {
                if (failureReason.isPresent() || selectedOptions.isEmpty()
                        || !remoteFreightersUsedByFaction.keySet().equals(remoteFreighterBudgetByFaction.keySet())) {
                    throw new IllegalArgumentException("accepted combination must expose complete usage/options and no failure");
                }
                for (Map.Entry<String, Integer> entry : remoteFreightersUsedByFaction.entrySet()) {
                    if (entry.getValue() > remoteFreighterBudgetByFaction.get(entry.getKey())) {
                        throw new IllegalArgumentException("accepted combination cannot exceed a start freight budget");
                    }
                }
            } else if (failureReason.isEmpty()
                    || !remoteFreightersUsedByFaction.isEmpty()
                    || !selectedOptions.isEmpty()) {
                throw new IllegalArgumentException("non-accepted combinations expose only explicit failure evidence");
            }
            if ((status == Status.UNRESOLVED_FRONTIER)
                    != failureReason.equals(Optional.of(FailureReason.FRONTIER_INCOMPLETE))) {
                throw new IllegalArgumentException("unresolved status and frontier-incomplete reason must agree");
            }
        }
    }

    /**
     * Exactly combines known per-commodity physical frontiers under shared per-start finite fleets.
     *
     * @param frontiers one physically valid option frontier per required commodity
     * @param remoteFreighterBudgetByFaction finite remote-freighter budget at every placed start
     * @return deterministic accepted, infeasible or unresolved shared-fleet evidence
     */
    public static CombinationReport combine(
            List<CommodityFrontier> frontiers,
            Map<String, Integer> remoteFreighterBudgetByFaction) {
        Map<String, Integer> budgets = canonicalFreighterMap(
                remoteFreighterBudgetByFaction,
                true,
                "remoteFreighterBudgetByFaction");
        ArrayList<String> factions = new ArrayList<>(budgets.keySet());
        factions.sort(String::compareTo);

        TreeMap<String, CommodityFrontier> byCommodity = new TreeMap<>();
        for (CommodityFrontier frontier : Objects.requireNonNull(frontiers, "frontiers")) {
            Objects.requireNonNull(frontier, "frontier");
            if (byCommodity.putIfAbsent(frontier.commodityId(), frontier) != null) {
                throw new IllegalArgumentException("commodity frontiers must be unique by commodityId");
            }
            validateOptionFactionSets(frontier, budgets.keySet());
        }
        if (byCommodity.isEmpty()) {
            throw new IllegalArgumentException("at least one commodity frontier is required");
        }

        for (CommodityFrontier frontier : byCommodity.values()) {
            if (frontier.status() == FrontierStatus.COMPLETE && frontier.options().isEmpty()) {
                return failedReport(budgets, Status.INFEASIBLE, FailureReason.COMMODITY_INFEASIBLE);
            }
            if (frontier.status() == FrontierStatus.COMPLETE
                    && frontier.options().stream().noneMatch(option -> fitsBudget(option, budgets))) {
                return failedReport(
                        budgets,
                        Status.INFEASIBLE,
                        FailureReason.SHARED_FLEET_COMBINATION_INFEASIBLE);
            }
        }

        boolean hasIncompleteFrontier = byCommodity.values().stream()
                .anyMatch(value -> value.status() == FrontierStatus.UNRESOLVED_SEARCH_BUDGET);

        Map<ShipVector, Selection> states = new HashMap<>();
        states.put(zeroVector(factions.size()), new Selection(List.of()));
        for (CommodityFrontier frontier : byCommodity.values()) {
            List<CommodityOption> options = nondominated(frontier.options(), factions);
            Map<ShipVector, Selection> next = new HashMap<>();
            for (Map.Entry<ShipVector, Selection> stateEntry : states.entrySet()) {
                for (CommodityOption option : options) {
                    ShipVector combined = addWithinBudget(
                            stateEntry.getKey(),
                            option,
                            factions,
                            budgets);
                    if (combined == null) {
                        continue;
                    }
                    ArrayList<SelectedOption> selected = new ArrayList<>(stateEntry.getValue().selectedOptions());
                    selected.add(new SelectedOption(
                            frontier.commodityId(),
                            frontier.frontierVersion(),
                            option.optionId(),
                            option.remoteFreightersByFaction()));
                    Selection candidate = new Selection(List.copyOf(selected));
                    Selection existing = next.get(combined);
                    if (existing == null || compareSelection(candidate, existing) < 0) {
                        next.put(combined, candidate);
                    }
                }
            }
            states = next;
        }

        if (states.isEmpty()) {
            return hasIncompleteFrontier
                    ? failedReport(budgets, Status.UNRESOLVED_FRONTIER, FailureReason.FRONTIER_INCOMPLETE)
                    : failedReport(budgets, Status.INFEASIBLE, FailureReason.SHARED_FLEET_COMBINATION_INFEASIBLE);
        }

        Map.Entry<ShipVector, Selection> best = states.entrySet().stream()
                .min(Stage20CommodityFreightFrontierCombiner::compareAcceptedState)
                .orElseThrow();
        TreeMap<String, Integer> used = new TreeMap<>();
        for (int index = 0; index < factions.size(); index++) {
            used.put(factions.get(index), best.getKey().counts().get(index));
        }
        return new CombinationReport(
                CURRENT_VERSION,
                budgets,
                Status.ACCEPTED,
                Optional.empty(),
                used,
                best.getValue().selectedOptions());
    }

    private static CombinationReport failedReport(
            Map<String, Integer> budgets,
            Status status,
            FailureReason reason) {
        return new CombinationReport(
                CURRENT_VERSION,
                budgets,
                status,
                Optional.of(reason),
                Map.of(),
                List.of());
    }

    private static void validateOptionFactionSets(
            CommodityFrontier frontier,
            java.util.Set<String> expectedFactions) {
        for (CommodityOption option : frontier.options()) {
            if (!option.remoteFreightersByFaction().keySet().equals(expectedFactions)) {
                throw new IllegalArgumentException("every commodity option must cover exactly the placed faction starts");
            }
        }
    }

    private static boolean fitsBudget(
            CommodityOption option,
            Map<String, Integer> budgets) {
        for (Map.Entry<String, Integer> entry : option.remoteFreightersByFaction().entrySet()) {
            if (entry.getValue() > budgets.get(entry.getKey())) {
                return false;
            }
        }
        return true;
    }

    private static List<CommodityOption> nondominated(
            List<CommodityOption> source,
            List<String> factions) {
        ArrayList<CommodityOption> ordered = new ArrayList<>(source);
        ordered.sort(Comparator.comparing(CommodityOption::optionId));
        ArrayList<CommodityOption> kept = new ArrayList<>();
        for (CommodityOption candidate : ordered) {
            boolean dominated = false;
            for (CommodityOption other : ordered) {
                if (candidate == other) {
                    continue;
                }
                int relation = dominanceRelation(other, candidate, factions);
                if (relation < 0 || (relation == 0 && other.optionId().compareTo(candidate.optionId()) < 0)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) {
                kept.add(candidate);
            }
        }
        return List.copyOf(kept);
    }

    private static int dominanceRelation(
            CommodityOption left,
            CommodityOption right,
            List<String> factions) {
        boolean strictlyLess = false;
        for (String faction : factions) {
            int leftCount = left.remoteFreightersByFaction().get(faction);
            int rightCount = right.remoteFreightersByFaction().get(faction);
            if (leftCount > rightCount) {
                return 1;
            }
            strictlyLess |= leftCount < rightCount;
        }
        return strictlyLess ? -1 : 0;
    }

    private static ShipVector addWithinBudget(
            ShipVector base,
            CommodityOption option,
            List<String> factions,
            Map<String, Integer> budgets) {
        ArrayList<Integer> counts = new ArrayList<>(factions.size());
        for (int index = 0; index < factions.size(); index++) {
            String faction = factions.get(index);
            int combined = Math.addExact(
                    base.counts().get(index),
                    option.remoteFreightersByFaction().get(faction));
            if (combined > budgets.get(faction)) {
                return null;
            }
            counts.add(combined);
        }
        return new ShipVector(List.copyOf(counts));
    }

    private static int compareAcceptedState(
            Map.Entry<ShipVector, Selection> left,
            Map.Entry<ShipVector, Selection> right) {
        int totalCompare = Integer.compare(left.getKey().total(), right.getKey().total());
        if (totalCompare != 0) {
            return totalCompare;
        }
        int vectorCompare = compareIntLists(left.getKey().counts(), right.getKey().counts());
        if (vectorCompare != 0) {
            return vectorCompare;
        }
        return compareSelection(left.getValue(), right.getValue());
    }

    private static int compareSelection(Selection left, Selection right) {
        int size = Math.min(left.selectedOptions().size(), right.selectedOptions().size());
        for (int index = 0; index < size; index++) {
            SelectedOption leftOption = left.selectedOptions().get(index);
            SelectedOption rightOption = right.selectedOptions().get(index);
            int commodityCompare = leftOption.commodityId().compareTo(rightOption.commodityId());
            if (commodityCompare != 0) {
                return commodityCompare;
            }
            int optionCompare = leftOption.optionId().compareTo(rightOption.optionId());
            if (optionCompare != 0) {
                return optionCompare;
            }
        }
        return Integer.compare(left.selectedOptions().size(), right.selectedOptions().size());
    }

    private static int compareIntLists(List<Integer> left, List<Integer> right) {
        for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
            int compare = Integer.compare(left.get(index), right.get(index));
            if (compare != 0) {
                return compare;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private static ShipVector zeroVector(int size) {
        ArrayList<Integer> counts = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            counts.add(0);
        }
        return new ShipVector(List.copyOf(counts));
    }

    private static Map<String, Integer> canonicalFreighterMap(
            Map<String, Integer> source,
            boolean requirePositive,
            String fieldName) {
        TreeMap<String, Integer> canonical = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : Objects.requireNonNull(source, fieldName).entrySet()) {
            String stableFactionId = WorldFactionIdentityState.normalizeStableId(entry.getKey());
            Integer count = Objects.requireNonNull(entry.getValue(), fieldName + " count");
            if (count < 0 || (requirePositive && count == 0)) {
                throw new IllegalArgumentException(fieldName + " counts must be "
                        + (requirePositive ? "positive" : "non-negative"));
            }
            if (canonical.putIfAbsent(stableFactionId, count) != null) {
                throw new IllegalArgumentException(fieldName + " contains duplicate canonical faction IDs");
            }
        }
        if (canonical.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must be non-empty");
        }
        return Collections.unmodifiableMap(canonical);
    }

    private static String requireText(String value, String fieldName) {
        String checked = Objects.requireNonNull(value, fieldName).trim();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return checked;
    }

    private record ShipVector(List<Integer> counts) {
        private ShipVector {
            counts = List.copyOf(counts);
        }

        private int total() {
            int total = 0;
            for (int count : counts) {
                total = Math.addExact(total, count);
            }
            return total;
        }
    }

    private record Selection(List<SelectedOption> selectedOptions) {
        private Selection {
            selectedOptions = List.copyOf(selectedOptions);
        }
    }
}
