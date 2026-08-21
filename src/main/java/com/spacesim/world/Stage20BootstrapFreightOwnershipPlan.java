package com.spacesim.world;

import com.spacesim.world.Stage20BootstrapFreightPhysicalPlan.PlanReport;
import com.spacesim.world.Stage20BootstrapFreightPhysicalPlan.SelectedCommodityPlan;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.StartPlan;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.SupplierCommitment;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Deterministic Stage-20E ownership authority for bootstrap freight capacity at accepted faction starts.
 *
 * <p>The derived freight-capacity requirement is a finite service pool per ordinary faction start,
 * not a hidden global fleet. This planner binds that pool to the placed faction/start and separates
 * the concrete freighters consumed by the selected physical supplier commitments from uncommitted
 * reserve capacity. It does not allocate {@link FleetId}s or choose a physical spawn position.</p>
 *
 * <p>Every remote allocation is copied from the already reconstructed physical plan and therefore
 * retains producer, consumer, explicit neighbor route, delivered throughput and integer ship count.
 * Local service consumes no inter-system freight ownership slot.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20BootstrapFreightOwnershipPlan {
    /** Stable bootstrap freight ownership-plan version. */
    public static final String CURRENT_VERSION = "stage20e.bootstrap-freight-ownership-plan.v1";

    private Stage20BootstrapFreightOwnershipPlan() {
        throw new AssertionError("No instances");
    }

    /**
     * Deterministic source identity for one selected remote supplier commitment.
     *
     * <p>This is planning provenance, not a runtime fleet identifier. Runtime identity remains owned
     * exclusively by {@link WorldSimulation} and {@link FleetWorldService}.</p>
     *
     * @param frontierVersion exact source physical-frontier version
     * @param optionId exact combiner-selected physical option
     * @param stableFactionId owning faction/start identity
     * @param commodityId authoritative Stage-18 commodity identifier
     * @param producerSystemId physical producer system
     * @param consumerStartSystemId consuming faction-start system
     * @param sourceCommitmentOrdinal canonical commitment position inside the selected demand
     */
    public record CommitmentKey(
            String frontierVersion,
            String optionId,
            String stableFactionId,
            String commodityId,
            StarSystemId producerSystemId,
            StarSystemId consumerStartSystemId,
            int sourceCommitmentOrdinal) {
        /**
         * Validates one immutable commitment-provenance key.
         *
         * @param frontierVersion exact source physical-frontier version
         * @param optionId exact combiner-selected physical option
         * @param stableFactionId owning faction/start identity
         * @param commodityId authoritative Stage-18 commodity identifier
         * @param producerSystemId physical producer system
         * @param consumerStartSystemId consuming faction-start system
         * @param sourceCommitmentOrdinal canonical commitment position inside the selected demand
         */
        public CommitmentKey {
            frontierVersion = requireText(frontierVersion, "frontierVersion");
            optionId = requireText(optionId, "optionId");
            stableFactionId = WorldFactionIdentityState.normalizeStableId(stableFactionId);
            commodityId = requireText(commodityId, "commodityId");
            Objects.requireNonNull(producerSystemId, "producerSystemId");
            Objects.requireNonNull(consumerStartSystemId, "consumerStartSystemId");
            if (producerSystemId.equals(consumerStartSystemId) || sourceCommitmentOrdinal < 0) {
                throw new IllegalArgumentException("remote commitment key must identify a remote canonical source");
            }
        }
    }

    /**
     * One deterministic logical freighter inside a committed aggregate allocation.
     *
     * @param commitmentKey exact selected physical commitment
     * @param freighterOrdinal zero-based logical freighter position inside that commitment
     */
    public record CommitmentSlot(CommitmentKey commitmentKey, int freighterOrdinal) {
        /**
         * Validates one non-runtime commitment slot.
         *
         * @param commitmentKey exact selected physical commitment
         * @param freighterOrdinal zero-based logical freighter position inside that commitment
         */
        public CommitmentSlot {
            Objects.requireNonNull(commitmentKey, "commitmentKey");
            if (freighterOrdinal < 0) {
                throw new IllegalArgumentException("freighterOrdinal must be non-negative");
            }
        }
    }

    /**
     * Deterministic materialization order for one owned freighter, before any FleetId exists.
     *
     * @param stableFactionId owning faction
     * @param ownershipOrdinal zero-based position inside the complete owned faction pool
     * @param commitment committed source slot, or empty for an uncommitted reserve freighter
     */
    public record OwnershipSlot(
            String stableFactionId,
            int ownershipOrdinal,
            Optional<CommitmentSlot> commitment) {
        /**
         * Validates one deterministic pre-materialization ownership slot.
         *
         * @param stableFactionId owning faction
         * @param ownershipOrdinal zero-based position inside the complete owned faction pool
         * @param commitment committed source slot, or empty for an uncommitted reserve freighter
         */
        public OwnershipSlot {
            stableFactionId = WorldFactionIdentityState.normalizeStableId(stableFactionId);
            if (ownershipOrdinal < 0) {
                throw new IllegalArgumentException("ownershipOrdinal must be non-negative");
            }
            Objects.requireNonNull(commitment, "commitment");
            if (commitment.isPresent()
                    && !stableFactionId.equals(commitment.orElseThrow().commitmentKey().stableFactionId())) {
                throw new IllegalArgumentException("ownership slot faction must match its commitment source");
            }
        }
    }

    /**
     * One remote physical supplier commitment consuming part of a faction-start freight pool.
     *
     * @param commitmentKey deterministic selected-option/source provenance
     * @param allocatedFreighters exact integer freighters committed to this remote service
     * @param deliveredKgPerSecond physical throughput committed by the selected frontier option
     * @param route authoritative explicit-neighbor physical route
     */
    public record RemoteCommitmentAllocation(
            CommitmentKey commitmentKey,
            int allocatedFreighters,
            double deliveredKgPerSecond,
            RouteAssessment route) {
        /**
         * Validates one immutable remote commitment allocation.
         *
         * @param commitmentKey deterministic selected-option/source provenance
         * @param allocatedFreighters exact positive integer freight allocation
         * @param deliveredKgPerSecond positive committed physical throughput
         * @param route authoritative explicit-neighbor route
         */
        public RemoteCommitmentAllocation {
            Objects.requireNonNull(commitmentKey, "commitmentKey");
            if (allocatedFreighters <= 0) {
                throw new IllegalArgumentException("remote ownership allocation must consume positive freighters");
            }
            requirePositiveFinite(deliveredKgPerSecond, "deliveredKgPerSecond");
            Objects.requireNonNull(route, "route");
            if (!route.orderedSystems().get(0).equals(commitmentKey.producerSystemId())
                    || !route.orderedSystems().get(route.orderedSystems().size() - 1)
                    .equals(commitmentKey.consumerStartSystemId())) {
                throw new IllegalArgumentException("remote ownership route endpoints must match producer/start");
            }
        }
    }

    /**
     * Finite bootstrap freight capacity owned by one placed faction start.
     *
     * <p>{@code homeStartSystemId} is an administrative ownership/home association only. It is not a
     * spawn coordinate and does not claim that all ships are already materialized in that system.</p>
     *
     * @param stableFactionId stable owning faction identity
     * @param homeStartSystemId accepted faction-start system
     * @param ownedFreighterCount full finite freight-capacity pool owned by this start
     * @param committedFreighterCount freighters currently committed by the selected physical plan
     * @param reserveFreighterCount owned but not currently committed freighters
     * @param remoteCommitments selected physical remote commitments consuming the committed pool
     */
    public record FactionFleetOwnership(
            String stableFactionId,
            StarSystemId homeStartSystemId,
            int ownedFreighterCount,
            int committedFreighterCount,
            int reserveFreighterCount,
            List<RemoteCommitmentAllocation> remoteCommitments) {
        /**
         * Validates one faction-start finite freight ownership pool.
         *
         * @param stableFactionId stable owning faction identity
         * @param homeStartSystemId accepted faction-start system
         * @param ownedFreighterCount full finite owned pool
         * @param committedFreighterCount physically committed subset
         * @param reserveFreighterCount uncommitted owned subset
         * @param remoteCommitments selected physical commitments
         */
        public FactionFleetOwnership {
            stableFactionId = WorldFactionIdentityState.normalizeStableId(stableFactionId);
            Objects.requireNonNull(homeStartSystemId, "homeStartSystemId");
            if (ownedFreighterCount <= 0 || committedFreighterCount < 0 || reserveFreighterCount < 0
                    || committedFreighterCount > ownedFreighterCount
                    || Math.addExact(committedFreighterCount, reserveFreighterCount) != ownedFreighterCount) {
                throw new IllegalArgumentException("owned/committed/reserve freight counts are inconsistent");
            }
            ArrayList<RemoteCommitmentAllocation> copy = new ArrayList<>(
                    Objects.requireNonNull(remoteCommitments, "remoteCommitments"));
            if (copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("remoteCommitments cannot contain nulls");
            }
            copy.sort(COMMITMENT_ORDER);
            int allocated = 0;
            Set<CommitmentKey> commitmentKeys = new HashSet<>();
            for (RemoteCommitmentAllocation commitment : copy) {
                if (!commitment.commitmentKey().stableFactionId().equals(stableFactionId)
                        || !commitment.commitmentKey().consumerStartSystemId().equals(homeStartSystemId)) {
                    throw new IllegalArgumentException("remote commitment consumer must equal owning faction start");
                }
                if (!commitmentKeys.add(commitment.commitmentKey())) {
                    throw new IllegalArgumentException("remote commitment keys must be unique inside an owned pool");
                }
                allocated = Math.addExact(allocated, commitment.allocatedFreighters());
            }
            if (allocated != committedFreighterCount) {
                throw new IllegalArgumentException("remote commitment allocations must equal committed freight count");
            }
            remoteCommitments = List.copyOf(copy);
        }

        /**
         * Expands aggregate commitments and reserve into an exact deterministic pre-FleetId order.
         *
         * @return exactly {@link #ownedFreighterCount()} immutable ownership slots
         */
        public List<OwnershipSlot> materializationSlots() {
            ArrayList<OwnershipSlot> result = new ArrayList<>(ownedFreighterCount);
            int ownershipOrdinal = 0;
            for (RemoteCommitmentAllocation allocation : remoteCommitments) {
                for (int freighterOrdinal = 0;
                        freighterOrdinal < allocation.allocatedFreighters();
                        freighterOrdinal++) {
                    result.add(new OwnershipSlot(
                            stableFactionId,
                            ownershipOrdinal++,
                            Optional.of(new CommitmentSlot(
                                    allocation.commitmentKey(),
                                    freighterOrdinal))));
                }
            }
            for (int reserveOrdinal = 0; reserveOrdinal < reserveFreighterCount; reserveOrdinal++) {
                result.add(new OwnershipSlot(stableFactionId, ownershipOrdinal++, Optional.empty()));
            }
            return List.copyOf(result);
        }
    }

    /**
     * Complete deterministic freight ownership authority for one accepted generated placement.
     *
     * @param version ownership-plan contract version
     * @param rootSeed exact generated-world seed whose placement owns the fleet
     * @param placementProfileVersion exact faction-start placement profile version
     * @param physicalPlan exact selected rich physical freight authority
     * @param factions one ownership pool for every placed faction start
     */
    public record OwnershipReport(
            String version,
            long rootSeed,
            String placementProfileVersion,
            PlanReport physicalPlan,
            List<FactionFleetOwnership> factions) {
        /**
         * Validates complete unique faction ownership coverage.
         *
         * @param version ownership-plan contract version
         * @param rootSeed exact generated-world seed whose placement owns the fleet
         * @param placementProfileVersion exact faction-start placement profile version
         * @param physicalPlan exact selected rich physical freight authority
         * @param factions one ownership pool for every placed faction start
         */
        public OwnershipReport {
            version = requireText(version, "version");
            placementProfileVersion = requireText(placementProfileVersion, "placementProfileVersion");
            Objects.requireNonNull(physicalPlan, "physicalPlan");
            ArrayList<FactionFleetOwnership> copy = new ArrayList<>(
                    Objects.requireNonNull(factions, "factions"));
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("ownership report must contain faction pools");
            }
            copy.sort(Comparator.comparing(FactionFleetOwnership::stableFactionId));
            Set<String> ids = new HashSet<>();
            Set<StarSystemId> starts = new HashSet<>();
            for (FactionFleetOwnership ownership : copy) {
                if (!ids.add(ownership.stableFactionId()) || !starts.add(ownership.homeStartSystemId())) {
                    throw new IllegalArgumentException("ownership report requires unique factions and start systems");
                }
                Integer budget = physicalPlan.remoteFreighterBudgetByFaction()
                        .get(ownership.stableFactionId());
                Integer committed = physicalPlan.remoteFreightersByFaction()
                        .get(ownership.stableFactionId());
                if (budget == null || committed == null
                        || ownership.ownedFreighterCount() != budget
                        || ownership.committedFreighterCount() != committed) {
                    throw new IllegalArgumentException(
                            "ownership pools must equal the retained physical plan budget and usage");
                }
            }
            if (!ids.equals(physicalPlan.remoteFreighterBudgetByFaction().keySet())) {
                throw new IllegalArgumentException("ownership report must cover every physical-plan faction");
            }
            factions = List.copyOf(copy);
        }

        /** @return total finite bootstrap freight assets authorized across all starts */
        public int totalOwnedFreighters() {
            int total = 0;
            for (FactionFleetOwnership faction : factions) {
                total = Math.addExact(total, faction.ownedFreighterCount());
            }
            return total;
        }

        /** @return total selected physical remote freight commitments across all starts */
        public int totalCommittedFreighters() {
            int total = 0;
            for (FactionFleetOwnership faction : factions) {
                total = Math.addExact(total, faction.committedFreighterCount());
            }
            return total;
        }
    }

    /**
     * Binds one accepted resolved generated world to exact finite freight ownership.
     *
     * <p>The resolved production result is the single public provenance authority: placement and
     * freight acceptance are taken from the same root-seed evidence object, and the selected rich
     * physical plan is reconstructed internally. Callers therefore cannot substitute a placement
     * from another generated seed even when its faction/start mapping happens to compare equal.</p>
     *
     * @param resolved accepted resolved generated-world production evidence
     * @return deterministic ownership plan retaining the exact reconstructed physical plan
     */
    public static OwnershipReport plan(ResolvedProbeResult resolved) {
        ResolvedProbeResult accepted = Objects.requireNonNull(resolved, "resolved");
        if (accepted.seedAcceptance().status() != Stage20GeneratedWorldSeedAcceptance.Status.ACCEPTED) {
            throw new IllegalArgumentException("bootstrap freight ownership requires an accepted resolved seed");
        }
        PlacementResult placement = accepted.generation().placement().orElseThrow(
                () -> new IllegalArgumentException("accepted resolved seed lost faction-start placement"));
        if (placement.rootSeed() != accepted.rootSeed()) {
            throw new IllegalArgumentException("resolved seed and faction-start placement provenance differ");
        }
        PlanReport physicalPlan = Stage20BootstrapFreightPhysicalPlan.reconstruct(
                accepted.coordinatedFreightAcceptance().orElseThrow(
                        () -> new IllegalArgumentException("accepted resolved seed lost freight acceptance")));
        return planAccepted(placement, physicalPlan);
    }

    /**
     * Package-private regression seam for fail-closed placement/physical-plan mismatch tests.
     *
     * @param placement accepted deterministic faction-start placement
     * @param physicalPlan accepted/reconstructed physical freight selection
     * @return deterministic ownership plan; no runtime asset is created
     */
    static OwnershipReport planAccepted(
            PlacementResult placement,
            PlanReport physicalPlan) {
        PlacementResult checkedPlacement = Objects.requireNonNull(placement, "placement");
        if (checkedPlacement.status() != PlacementStatus.ACCEPTED || checkedPlacement.assignments().isEmpty()) {
            throw new IllegalArgumentException("bootstrap freight ownership requires an accepted non-empty placement");
        }
        PlanReport selected = Objects.requireNonNull(physicalPlan, "physicalPlan");
        if (!checkedPlacement.version().equals(selected.placementVersion())) {
            throw new IllegalArgumentException("placement version differs from selected physical authority");
        }

        TreeMap<String, Assignment> assignments = new TreeMap<>();
        for (Assignment assignment : checkedPlacement.assignments()) {
            assignments.put(assignment.stableFactionId(), assignment);
        }
        Map<String, Integer> capacities = selected.remoteFreighterBudgetByFaction();
        if (!capacities.keySet().equals(assignments.keySet())
                || !selected.remoteFreightersByFaction().keySet().equals(assignments.keySet())) {
            throw new IllegalArgumentException("placement and physical plan must cover the same factions");
        }

        TreeMap<String, ArrayList<RemoteCommitmentAllocation>> allocations = new TreeMap<>();
        for (String factionId : assignments.keySet()) {
            allocations.put(factionId, new ArrayList<>());
        }

        for (SelectedCommodityPlan commodity : selected.commodities()) {
            for (StartPlan start : commodity.starts()) {
                Assignment assignment = assignments.get(start.stableFactionId());
                if (assignment == null || !assignment.systemId().equals(start.startSystemId())) {
                    throw new IllegalArgumentException("selected physical plan start differs from accepted placement");
                }
                int capacity = capacities.get(start.stableFactionId());
                if (start.remoteFreighterBudget() != capacity) {
                    throw new IllegalArgumentException("selected physical plan budget differs from ownership authority");
                }
                for (var demand : start.demands()) {
                    int sourceCommitmentOrdinal = 0;
                    for (SupplierCommitment commitment : demand.commitments()) {
                        int currentOrdinal = sourceCommitmentOrdinal++;
                        if (commitment.local()) {
                            if (commitment.allocatedFreighters() != 0
                                    || !commitment.producerSystemId().equals(start.startSystemId())) {
                                throw new IllegalArgumentException("local commitment cannot consume remote freight ownership");
                            }
                            continue;
                        }
                        RouteAssessment route = commitment.route().orElseThrow(
                                () -> new IllegalArgumentException("remote commitment lost physical route"));
                        allocations.get(start.stableFactionId()).add(new RemoteCommitmentAllocation(
                                new CommitmentKey(
                                        commodity.frontierVersion(),
                                        commodity.optionId(),
                                        start.stableFactionId(),
                                        demand.commodityId(),
                                        commitment.producerSystemId(),
                                        start.startSystemId(),
                                        currentOrdinal),
                                commitment.allocatedFreighters(),
                                commitment.deliveredKgPerSecond(),
                                route));
                    }
                }
            }
        }

        ArrayList<FactionFleetOwnership> result = new ArrayList<>();
        for (Map.Entry<String, Assignment> entry : assignments.entrySet()) {
            String factionId = entry.getKey();
            int capacity = capacities.get(factionId);
            int expectedCommitted = selected.remoteFreightersByFaction().get(factionId);
            if (expectedCommitted > capacity) {
                throw new IllegalArgumentException("selected physical freight usage exceeds owned start capacity");
            }
            int actualCommitted = allocations.get(factionId).stream()
                    .mapToInt(RemoteCommitmentAllocation::allocatedFreighters)
                    .sum();
            if (actualCommitted != expectedCommitted) {
                throw new IllegalArgumentException("selected physical commitments do not reconstruct aggregate ship usage");
            }
            result.add(new FactionFleetOwnership(
                    factionId,
                    entry.getValue().systemId(),
                    capacity,
                    actualCommitted,
                    capacity - actualCommitted,
                    allocations.get(factionId)));
        }

        return new OwnershipReport(
                CURRENT_VERSION,
                checkedPlacement.rootSeed(),
                checkedPlacement.profileVersion(),
                selected,
                result);
    }

    private static final Comparator<RemoteCommitmentAllocation> COMMITMENT_ORDER =
            Comparator.comparing((RemoteCommitmentAllocation value) ->
                            value.commitmentKey().frontierVersion())
                    .thenComparing(value -> value.commitmentKey().optionId())
                    .thenComparing(value -> value.commitmentKey().stableFactionId())
                    .thenComparing(value -> value.commitmentKey().commodityId())
                    .thenComparing(value -> value.commitmentKey().producerSystemId())
                    .thenComparingInt(value -> value.commitmentKey().sourceCommitmentOrdinal());

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }
}
