package com.spacesim.world;

import com.spacesim.world.Stage20FactionStartCandidateEvaluator.Evaluation;
import com.spacesim.world.Stage20FactionStartCandidateEvaluator.Status;
import com.spacesim.world.calibration.Stage20FactionStartAcceptanceProfile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Bounded deterministic Stage-20E placement of ordinary faction starts over accepted candidates.
 *
 * <p>The generator consumes candidate evaluations only after topology/resources/facilities and
 * dependency diagnostics already exist. It never changes candidate diagnostics, creates resources,
 * grants stock, adds topology edges or relaxes physical acceptance. Failure returns an explicit seed
 * rejection or unresolved-authority result.</p>
 *
 * <p>Stable faction IDs use the existing {@link WorldFactionIdentityState} {@code faction.*}
 * identity contract. This result is generation state only; materializing runtime faction identities,
 * territory and markets remains a separate world-bootstrap integration step.</p>
 */
public final class Stage20FactionStartPlacementGenerator {
    /** Current immutable placement-result version. */
    public static final String CURRENT_VERSION = "stage20e.faction-start-placement.v1";

    private Stage20FactionStartPlacementGenerator() {
        throw new AssertionError("No instances");
    }

    /** Final bounded placement status. */
    public enum PlacementStatus {
        /** Every requested faction received one accepted sufficiently separated system. */
        ACCEPTED,
        /** Generated physical/economic state cannot satisfy the bounded ordinary-start policy. */
        REJECTED_SEED,
        /** Too few candidates are decidable because required acceptance authority is unresolved. */
        UNRESOLVED_AUTHORITY
    }

    /** Explicit non-success reasons. */
    public enum FailureReason {
        /** Fewer accepted candidates exist than requested faction starts. */
        INSUFFICIENT_ACCEPTED_CANDIDATES,
        /** Required candidate decisions are blocked by unresolved upstream acceptance authority. */
        REQUIRED_CANDIDATES_UNRESOLVED,
        /** Accepted candidates cannot satisfy the configured ordinary-hop separation. */
        NO_SEPARATED_ASSIGNMENT,
        /** Deterministic bounded search exhausted its explicit node budget. */
        SEARCH_BUDGET_EXHAUSTED
    }

    /**
     * One selected faction start.
     *
     * @param stableFactionId existing canonical {@code faction.*} identity
     * @param systemId selected accepted start system
     * @param candidateSelectionPenalty evaluator ordering penalty retained as evidence
     */
    public record Assignment(
            String stableFactionId,
            StarSystemId systemId,
            double candidateSelectionPenalty) {
        /**
         * Validates one immutable assignment.
         *
         * @param stableFactionId canonical faction identity
         * @param systemId selected system
         * @param candidateSelectionPenalty non-negative diagnostic ordering penalty
         */
        public Assignment {
            stableFactionId = WorldFactionIdentityState.normalizeStableId(stableFactionId);
            Objects.requireNonNull(systemId, "systemId");
            if (!Double.isFinite(candidateSelectionPenalty) || candidateSelectionPenalty < 0d) {
                throw new IllegalArgumentException("candidateSelectionPenalty must be non-negative and finite");
            }
        }
    }

    /**
     * Immutable bounded placement result.
     *
     * @param version stable result version
     * @param rootSeed deterministic world-generation seed used for tie-breaking
     * @param profileVersion exact acceptance/placement profile consumed
     * @param status final placement status
     * @param assignments deterministic faction-ID ordered assignments; complete only when accepted
     * @param searchNodes number of bounded candidate assignment attempts consumed
     * @param failureReason explicit reason absent only for accepted results
     */
    public record PlacementResult(
            String version,
            long rootSeed,
            String profileVersion,
            PlacementStatus status,
            List<Assignment> assignments,
            int searchNodes,
            Optional<FailureReason> failureReason) {
        /**
         * Validates and freezes one placement result.
         *
         * @param version stable result version
         * @param rootSeed deterministic root seed
         * @param profileVersion exact profile version consumed
         * @param status final placement status
         * @param assignments selected assignments
         * @param searchNodes bounded search attempts consumed
         * @param failureReason non-success reason
         */
        public PlacementResult {
            version = requireText(version, "version");
            profileVersion = requireText(profileVersion, "profileVersion");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(assignments, "assignments");
            Objects.requireNonNull(failureReason, "failureReason");
            if (searchNodes < 0) {
                throw new IllegalArgumentException("searchNodes must be non-negative");
            }
            ArrayList<Assignment> copy = new ArrayList<>(assignments);
            if (copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("assignments cannot contain null");
            }
            copy.sort(Comparator.comparing(Assignment::stableFactionId));
            Set<String> factions = new HashSet<>();
            Set<StarSystemId> systems = new HashSet<>();
            for (Assignment assignment : copy) {
                if (!factions.add(assignment.stableFactionId())) {
                    throw new IllegalArgumentException("duplicate faction assignment: " + assignment.stableFactionId());
                }
                if (!systems.add(assignment.systemId())) {
                    throw new IllegalArgumentException("duplicate start system assignment: " + assignment.systemId());
                }
            }
            assignments = List.copyOf(copy);
            if (status == PlacementStatus.ACCEPTED) {
                if (failureReason.isPresent()) {
                    throw new IllegalArgumentException("accepted placement cannot carry a failure reason");
                }
            } else if (failureReason.isEmpty()) {
                throw new IllegalArgumentException("non-accepted placement requires a failure reason");
            }
        }
    }

    /**
     * Assigns accepted candidate systems to stable factions with bounded deterministic backtracking.
     *
     * @param rootSeed authoritative generation seed used only for deterministic equal-penalty tie-breaking
     * @param topology authoritative accepted ordinary jump topology
     * @param stableFactionIds requested existing/canonical faction identities
     * @param evaluations one evaluation per candidate system
     * @param profile versioned acceptance and placement profile
     * @return accepted assignment or explicit unresolved/rejected result
     */
    public static PlacementResult place(
            long rootSeed,
            GalaxyTopology topology,
            List<String> stableFactionIds,
            List<Evaluation> evaluations,
            Stage20FactionStartAcceptanceProfile profile) {
        GalaxyTopology checkedTopology = Objects.requireNonNull(topology, "topology");
        Stage20FactionStartAcceptanceProfile policy = Objects.requireNonNull(profile, "profile");
        List<String> factions = canonicalFactions(stableFactionIds);
        List<Evaluation> candidates = canonicalEvaluations(checkedTopology, evaluations, policy);

        List<Evaluation> accepted = candidates.stream()
                .filter(value -> value.status() == Status.ACCEPTED)
                .toList();
        long unresolvedCount = candidates.stream()
                .filter(value -> value.status() == Status.UNRESOLVED_AUTHORITY)
                .count();
        if (accepted.size() < factions.size()) {
            PlacementStatus status = unresolvedCount > 0
                    ? PlacementStatus.UNRESOLVED_AUTHORITY
                    : PlacementStatus.REJECTED_SEED;
            FailureReason reason = unresolvedCount > 0
                    ? FailureReason.REQUIRED_CANDIDATES_UNRESOLVED
                    : FailureReason.INSUFFICIENT_ACCEPTED_CANDIDATES;
            return failed(rootSeed, policy, status, 0, reason);
        }

        SearchState state = new SearchState(policy.maximumSearchNodes());
        TreeMap<String, Assignment> selected = new TreeMap<>();
        HashMap<SystemPair, Integer> distanceCache = new HashMap<>();
        boolean resolved = assign(
                0,
                factions,
                accepted,
                selected,
                state,
                checkedTopology,
                policy,
                rootSeed,
                distanceCache);
        if (resolved) {
            return new PlacementResult(
                    CURRENT_VERSION,
                    rootSeed,
                    policy.version(),
                    PlacementStatus.ACCEPTED,
                    List.copyOf(selected.values()),
                    state.nodes,
                    Optional.empty());
        }
        FailureReason reason = state.exhausted
                ? FailureReason.SEARCH_BUDGET_EXHAUSTED
                : FailureReason.NO_SEPARATED_ASSIGNMENT;
        return failed(rootSeed, policy, PlacementStatus.REJECTED_SEED, state.nodes, reason);
    }

    private static boolean assign(
            int factionIndex,
            List<String> factions,
            List<Evaluation> accepted,
            TreeMap<String, Assignment> selected,
            SearchState state,
            GalaxyTopology topology,
            Stage20FactionStartAcceptanceProfile profile,
            long rootSeed,
            Map<SystemPair, Integer> distanceCache) {
        if (factionIndex >= factions.size()) {
            return true;
        }
        if (state.nodes >= state.maximumNodes) {
            state.exhausted = true;
            return false;
        }

        String faction = factions.get(factionIndex);
        List<Evaluation> ordered = orderedForFaction(accepted, rootSeed, faction);
        for (Evaluation candidate : ordered) {
            if (state.nodes >= state.maximumNodes) {
                state.exhausted = true;
                return false;
            }
            state.nodes++;
            if (!separated(
                    candidate.candidateSystemId(),
                    selected.values(),
                    topology,
                    profile.minimumFactionStartHopSeparation(),
                    distanceCache)) {
                continue;
            }
            selected.put(faction, new Assignment(
                    faction,
                    candidate.candidateSystemId(),
                    candidate.selectionPenalty()));
            if (assign(
                    factionIndex + 1,
                    factions,
                    accepted,
                    selected,
                    state,
                    topology,
                    profile,
                    rootSeed,
                    distanceCache)) {
                return true;
            }
            selected.remove(faction);
        }
        return false;
    }

    private static List<Evaluation> orderedForFaction(
            List<Evaluation> accepted,
            long rootSeed,
            String faction) {
        ArrayList<Evaluation> ordered = new ArrayList<>(accepted);
        ordered.sort(Comparator.comparingDouble(Evaluation::selectionPenalty)
                .thenComparingLong(value -> deterministicTie(rootSeed, faction, value.candidateSystemId()))
                .thenComparing(Evaluation::candidateSystemId));
        return List.copyOf(ordered);
    }

    private static boolean separated(
            StarSystemId candidate,
            Iterable<Assignment> assignments,
            GalaxyTopology topology,
            int minimumHopSeparation,
            Map<SystemPair, Integer> distanceCache) {
        for (Assignment assignment : assignments) {
            StarSystemId occupied = assignment.systemId();
            SystemPair pair = new SystemPair(candidate, occupied);
            int distance = distanceCache.computeIfAbsent(pair, ignored -> hopDistance(topology, candidate, occupied));
            if (distance < minimumHopSeparation) {
                return false;
            }
        }
        return true;
    }

    private static int hopDistance(GalaxyTopology topology, StarSystemId origin, StarSystemId destination) {
        if (origin.equals(destination)) {
            return 0;
        }
        ArrayDeque<StarSystemId> queue = new ArrayDeque<>();
        HashMap<StarSystemId, Integer> distance = new HashMap<>();
        queue.add(origin);
        distance.put(origin, 0);
        while (!queue.isEmpty()) {
            StarSystemId current = queue.removeFirst();
            int nextDistance = distance.get(current) + 1;
            for (StarSystemId neighbor : topology.neighbors(current)) {
                if (distance.putIfAbsent(neighbor, nextDistance) != null) {
                    continue;
                }
                if (neighbor.equals(destination)) {
                    return nextDistance;
                }
                queue.addLast(neighbor);
            }
        }
        throw new IllegalArgumentException("faction-start candidates must share connected accepted topology");
    }

    private static List<String> canonicalFactions(List<String> source) {
        Objects.requireNonNull(source, "stableFactionIds");
        if (source.isEmpty()) {
            throw new IllegalArgumentException("at least one faction start is required");
        }
        ArrayList<String> result = new ArrayList<>();
        HashSet<String> unique = new HashSet<>();
        for (String value : source) {
            String normalized = WorldFactionIdentityState.normalizeStableId(value);
            if (!unique.add(normalized)) {
                throw new IllegalArgumentException("duplicate faction start identity: " + normalized);
            }
            result.add(normalized);
        }
        result.sort(String::compareTo);
        return List.copyOf(result);
    }

    private static List<Evaluation> canonicalEvaluations(
            GalaxyTopology topology,
            List<Evaluation> source,
            Stage20FactionStartAcceptanceProfile profile) {
        Objects.requireNonNull(source, "evaluations");
        if (source.isEmpty()) {
            throw new IllegalArgumentException("candidate evaluations must not be empty");
        }
        ArrayList<Evaluation> result = new ArrayList<>();
        HashSet<StarSystemId> systems = new HashSet<>();
        for (Evaluation evaluation : source) {
            Evaluation checked = Objects.requireNonNull(evaluation, "evaluation");
            if (!Stage20FactionStartCandidateEvaluator.CURRENT_VERSION.equals(checked.version())) {
                throw new IllegalArgumentException("candidate evaluation uses incompatible version");
            }
            if (!profile.version().equals(checked.profileVersion())) {
                throw new IllegalArgumentException("candidate evaluation uses a different acceptance profile");
            }
            if (topology.findSystem(checked.candidateSystemId()).isEmpty()) {
                throw new IllegalArgumentException("candidate system is outside authoritative topology");
            }
            if (!systems.add(checked.candidateSystemId())) {
                throw new IllegalArgumentException("duplicate evaluation for system " + checked.candidateSystemId());
            }
            result.add(checked);
        }
        result.sort(Comparator.comparing(Evaluation::candidateSystemId));
        return List.copyOf(result);
    }

    private static PlacementResult failed(
            long rootSeed,
            Stage20FactionStartAcceptanceProfile profile,
            PlacementStatus status,
            int searchNodes,
            FailureReason reason) {
        return new PlacementResult(
                CURRENT_VERSION,
                rootSeed,
                profile.version(),
                status,
                List.of(),
                searchNodes,
                Optional.of(reason));
    }

    private static long deterministicTie(long seed, String faction, StarSystemId systemId) {
        long value = seed ^ ((long) faction.hashCode() << 32) ^ systemId.value();
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53l;
        value ^= value >>> 33;
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private record SystemPair(StarSystemId first, StarSystemId second) {
        private SystemPair {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
            if (first.compareTo(second) > 0) {
                StarSystemId swap = first;
                first = second;
                second = swap;
            }
        }
    }

    private static final class SearchState {
        private final int maximumNodes;
        private int nodes;
        private boolean exhausted;

        private SearchState(int maximumNodes) {
            this.maximumNodes = maximumNodes;
        }
    }
}
