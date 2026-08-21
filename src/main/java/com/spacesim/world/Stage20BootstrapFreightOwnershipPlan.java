package com.spacesim.world;

import com.spacesim.world.Stage20BootstrapFreightPhysicalPlan.PlanReport;
import com.spacesim.world.Stage20BootstrapFreightPhysicalPlan.SelectedCommodityPlan;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.StartPlan;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.SupplierCommitment;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;

import java.util.ArrayList;
import java.util.Collections;
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
     * One remote physical supplier commitment consuming part of a faction-start freight pool.
     *
     * @param commodityId authoritative Stage-18 commodity identifier
     * @param producerSystemId physical producer system
     * @param consumerStartSystemId faction start consuming the delivered service
     * @param allocatedFreighters exact integer freighters committed to this remote service
     * @param deliveredKgPerSecond physical throughput committed by the selected frontier option
     * @param route authoritative explicit-neighbor physical route
     */
    public record RemoteCommitmentAllocation(
            String commodityId,
            StarSystemId producerSystemId,
            StarSystemId consumerStartSystemId,
            int allocatedFreighters,
            double deliveredKgPerSecond,
            RouteAssessment route) {
        /**
         * Validates one immutable remote commitment allocation.
         *
         * @param commodityId authoritative Stage-18 commodity identifier
         * @param producerSystemId physical producer system
         * @param consumerStartSystemId faction start consuming the delivered service
         * @param allocatedFreighters exact positive integer freight allocation
         * @param deliveredKgPerSecond positive committed physical throughput
         * @param route authoritative explicit-neighbor route
         */
        public RemoteCommitmentAllocation {
            commodityId = requireText(commodityId, "commodityId");
            Objects.requireNonNull(producerSystemId, "producerSystemId");
            Objects.requireNonNull(consumerStartSystemId, "consumerStartSystemId");
            if (producerSystemId.equals(consumerStartSystemId)) {
                throw new IllegalArgumentException("remote ownership allocation cannot use a local producer");
            }
            if (allocatedFreighters <= 0) {
                throw new IllegalArgumentException("remote ownership allocation must consume positive freighters");
            }
            requirePositiveFinite(deliveredKgPerSecond, "deliveredKgPerSecond");
            Objects.requireNonNull(route, "route");
            if (!route.orderedSystems().get(0).equals(producerSystemId)
                    || !route.orderedSystems().get(route.orderedSystems().size() - 1)
                    .equals(consumerStartSystemId)) {
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
            for (RemoteCommitmentAllocation commitment : copy) {
                if (!commitment.consumerStartSystemId().equals(homeStartSystemId)) {
                    throw new IllegalArgumentException("remote commitment consumer must equal owning faction start");
                }
                allocated = Math.addExact(allocated, commitment.allocatedFreighters());
            }
            if (allocated != committedFreighterCount) {
                throw new IllegalArgumentException("remote commitment allocations must equal committed freight count");
            }
            remoteCommitments = List.copyOf(copy);
        }
    }

    /**
     * Complete deterministic freight ownership authority for one accepted generated placement.
     *
     * @param version ownership-plan contract version
     * @param placementVersion accepted faction-start placement version
     * @param physicalPlanVersion selected physical freight-plan version
     * @param factions one ownership pool for every placed faction start
     */
    public record OwnershipReport(
            String version,
            String placementVersion,
            String physicalPlanVersion,
            List<FactionFleetOwnership> factions) {
        /**
         * Validates complete unique faction ownership coverage.
         *
         * @param version ownership-plan contract version
         * @param placementVersion accepted faction-start placement version
         * @param physicalPlanVersion selected physical freight-plan version
         * @param factions one ownership pool for every placed faction start
         */
        public OwnershipReport {
            version = requireText(version, "version");
            placementVersion = requireText(placementVersion, "placementVersion");
            physicalPlanVersion = requireText(physicalPlanVersion, "physicalPlanVersion");
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
     * Binds finite per-start freight capacity to accepted factions and selected physical commitments.
     *
     * @param placement accepted deterministic faction-start placement
     * @param ownedFreighterCapacityByFaction explicit finite ownership capacity per placed start
     * @param physicalPlan accepted/reconstructed physical freight selection
     * @return deterministic ownership plan; no runtime asset is created
     */
    public static OwnershipReport plan(
            PlacementResult placement,
            Map<String, Integer> ownedFreighterCapacityByFaction,
            PlanReport physicalPlan) {
        PlacementResult checkedPlacement = Objects.requireNonNull(placement, "placement");
        if (checkedPlacement.status() != PlacementStatus.ACCEPTED || checkedPlacement.assignments().isEmpty()) {
            throw new IllegalArgumentException("bootstrap freight ownership requires an accepted non-empty placement");
        }
        PlanReport selected = Objects.requireNonNull(physicalPlan, "physicalPlan");

        TreeMap<String, Assignment> assignments = new TreeMap<>();
        for (Assignment assignment : checkedPlacement.assignments()) {
            assignments.put(assignment.stableFactionId(), assignment);
        }
        TreeMap<String, Integer> capacities = canonicalPositiveCapacityMap(ownedFreighterCapacityByFaction);
        if (!capacities.keySet().equals(assignments.keySet())
                || !selected.remoteFreightersByFaction().keySet().equals(assignments.keySet())) {
            throw new IllegalArgumentException("placement, ownership capacity and physical plan must cover the same factions");
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
                    for (SupplierCommitment commitment : demand.commitments()) {
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
                                demand.commodityId(),
                                commitment.producerSystemId(),
                                start.startSystemId(),
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
                checkedPlacement.version(),
                selected.version(),
                result);
    }

    private static final Comparator<RemoteCommitmentAllocation> COMMITMENT_ORDER =
            Comparator.comparing(RemoteCommitmentAllocation::commodityId)
                    .thenComparing(RemoteCommitmentAllocation::producerSystemId)
                    .thenComparing(RemoteCommitmentAllocation::consumerStartSystemId)
                    .thenComparing(value -> value.route().orderedSystems().toString());

    private static TreeMap<String, Integer> canonicalPositiveCapacityMap(Map<String, Integer> input) {
        TreeMap<String, Integer> result = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : Objects.requireNonNull(input, "ownedFreighterCapacityByFaction").entrySet()) {
            String faction = WorldFactionIdentityState.normalizeStableId(entry.getKey());
            Integer count = Objects.requireNonNull(entry.getValue(), "owned freight capacity");
            if (count <= 0 || result.putIfAbsent(faction, count) != null) {
                throw new IllegalArgumentException("owned freight capacity must contain unique factions and positive counts");
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("owned freight capacity cannot be empty");
        }
        return result;
    }

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
