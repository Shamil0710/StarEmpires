package com.spacesim.world;

import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan.FactionFleetOwnership;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan.OwnershipReport;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan.OwnershipSlot;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.InputReservation;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessSelectionKey;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ReservationReport;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.MissingAuthority;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import com.spacesim.world.generation.Stage20PhysicalFreightRouteEvaluatorFactory;
import com.spacesim.world.generation.Stage20RepresentativeGeneratedWorldProbeProfileV3;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;

/**
 * Binds accepted Stage-20F remote input reservations to finite Stage-20E owned freight slots.
 *
 * <p>The caller explicitly assigns every selected physical process to a stable faction. This plan
 * never infers ownership from a system, station name or proximity to a faction start. Only reserve
 * slots in the accepted {@link Stage20BootstrapFreightOwnershipPlan} may be assigned; freighters
 * already committed to Stage-20E essential bootstrap service cannot be counted again.</p>
 *
 * <p>Every remote reservation is re-evaluated with one through the complete owned-pool ship count.
 * The first count whose unchanged physical route sustains the reserved kg/s is the exact integer
 * demand. All demands sharing an owner then consume one common reserve pool. Rejection commits no
 * partial industrial freight allocation and leaves the ownership authority unresolved.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20IndustrialInputFreightOwnershipPlan {
    /** Stable Stage-20F industrial-input freight ownership version. */
    public static final String CURRENT_VERSION =
            "stage20f.industrial-input-freight-ownership-plan.v1";
    private static final double EPSILON = 1.0e-9d;

    private Stage20IndustrialInputFreightOwnershipPlan() {
        throw new AssertionError("No instances");
    }

    /** Final state of the shared finite owned-freight allocation. */
    public enum Status {
        /** Every remote reservation owns enough distinct previously uncommitted freight slots. */
        ACCEPTED,
        /** At least one owner lacks enough uncommitted physical freight capacity. */
        INSUFFICIENT_OWNED_FREIGHT
    }

    /** Machine-readable reason for a non-committing freight result. */
    public enum FailureReason {
        /** Route demand exceeds a pool or summed demands exceed its uncommitted reserve slots. */
        INSUFFICIENT_UNCOMMITTED_OWNED_FREIGHT
    }

    /** Whether one exact route demand fits inside its owner's complete finite pool. */
    public enum DemandStatus {
        /** A finite minimum ship count was found inside the complete owned pool. */
        CAPACITY_BOUND,
        /** Even assigning the owner's complete pool cannot sustain the reserved rate. */
        EXCEEDS_OWNED_POOL
    }

    /**
     * One explicit selected-process owner.
     *
     * @param process complete generated physical process identity
     * @param stableFactionId stable faction that owns the process's industrial freight
     */
    public record ProcessOwnerAssignment(
            ProcessSelectionKey process,
            String stableFactionId) {
        /**
         * Validates one explicit process owner.
         *
         * @param process complete generated physical process identity
         * @param stableFactionId stable faction that owns the process's industrial freight
         */
        public ProcessOwnerAssignment {
            Objects.requireNonNull(process, "process");
            stableFactionId = WorldFactionIdentityState.normalizeStableId(stableFactionId);
        }
    }

    /**
     * Versioned caller authority assigning every selected process to one explicit faction.
     *
     * @param version caller-defined ownership-policy/result version
     * @param rootSeed exact accepted generated root seed
     * @param assignments exact unique ownership coverage of the process selection
     */
    public record ProcessOwnershipAuthority(
            String version,
            long rootSeed,
            List<ProcessOwnerAssignment> assignments) {
        /**
         * Canonicalizes one explicit process-ownership authority.
         *
         * @param version caller-defined ownership-policy/result version
         * @param rootSeed exact accepted generated root seed
         * @param assignments exact unique ownership coverage of the process selection
         */
        public ProcessOwnershipAuthority {
            version = requireText(version, "version");
            ArrayList<ProcessOwnerAssignment> copy = new ArrayList<>(Objects.requireNonNull(
                    assignments, "assignments"));
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException(
                        "process ownership assignments must be non-empty and contain no nulls");
            }
            copy.sort(Comparator.comparing(ProcessOwnerAssignment::process));
            HashSet<ProcessSelectionKey> processes = new HashSet<>();
            for (ProcessOwnerAssignment assignment : copy) {
                if (!processes.add(assignment.process())) {
                    throw new IllegalArgumentException("every selected process must have exactly one owner");
                }
            }
            assignments = List.copyOf(copy);
        }
    }

    /**
     * Exact physical evaluator provenance used to derive integer ship counts.
     *
     * @param version versioned evaluator/factory authority
     * @param representativeProfileVersion exact generated-world profile version
     * @param payloadMassKgPerFreighter physical delivered payload per loaded trip
     * @param sourceEvidenceId freight payload/physics provenance
     * @param maximumValidatedFreighters largest count accepted by the supplied evaluator
     */
    public record FreightCapacityProfile(
            String version,
            String representativeProfileVersion,
            double payloadMassKgPerFreighter,
            String sourceEvidenceId,
            int maximumValidatedFreighters) {
        /**
         * Validates one exact finite route-capacity authority.
         *
         * @param version versioned evaluator/factory authority
         * @param representativeProfileVersion exact generated-world profile version
         * @param payloadMassKgPerFreighter physical delivered payload per loaded trip
         * @param sourceEvidenceId freight payload/physics provenance
         * @param maximumValidatedFreighters largest count accepted by the supplied evaluator
         */
        public FreightCapacityProfile {
            version = requireText(version, "version");
            representativeProfileVersion = requireText(
                    representativeProfileVersion, "representativeProfileVersion");
            requirePositiveFinite(payloadMassKgPerFreighter, "payloadMassKgPerFreighter");
            sourceEvidenceId = requireText(sourceEvidenceId, "sourceEvidenceId");
            if (maximumValidatedFreighters <= 0) {
                throw new IllegalArgumentException("maximumValidatedFreighters must be positive");
            }
        }
    }

    /** Physical route evaluator at one explicit already-owned freighter count. */
    @FunctionalInterface
    public interface FreightCapacityEvaluator {
        /**
         * Evaluates a repeated physical service route at an explicit finite ship count.
         *
         * @param origin physical supplier system
         * @param destination physical consuming process system
         * @param allocatedFreighters positive allocated freighter count
         * @return physical route/capacity result, or empty when the route cannot be executed
         */
        Optional<RouteAssessment> assess(
                StarSystemId origin,
                StarSystemId destination,
                int allocatedFreighters);
    }

    /**
     * Stable identity of one remote industrial input reservation.
     *
     * @param process exact selected process
     * @param inputCommodityId exact required input commodity
     * @param supplyKey exact finite source capacity identity
     */
    public record InputFreightKey(
            ProcessSelectionKey process,
            String inputCommodityId,
            SupplyKey supplyKey) implements Comparable<InputFreightKey> {
        /**
         * Validates one exact remote input identity.
         *
         * @param process exact selected process
         * @param inputCommodityId exact required input commodity
         * @param supplyKey exact finite source capacity identity
         */
        public InputFreightKey {
            Objects.requireNonNull(process, "process");
            inputCommodityId = requireText(inputCommodityId, "inputCommodityId");
            Objects.requireNonNull(supplyKey, "supplyKey");
            if (!inputCommodityId.equals(supplyKey.commodityId())) {
                throw new IllegalArgumentException("input freight key commodity identities differ");
            }
        }

        /**
         * Creates a key from one accepted reservation.
         *
         * @param reservation accepted remote input reservation
         * @return exact stable freight identity
         */
        public static InputFreightKey from(InputReservation reservation) {
            InputReservation value = Objects.requireNonNull(reservation, "reservation");
            if (value.local()) {
                throw new IllegalArgumentException("local input reservation requires no freight key");
            }
            return new InputFreightKey(
                    value.process(), value.inputCommodityId(), value.supplyKey());
        }

        /** Orders complete reservation identities deterministically. */
        @Override
        public int compareTo(InputFreightKey other) {
            int comparison = process.compareTo(other.process);
            if (comparison != 0) return comparison;
            comparison = inputCommodityId.compareTo(other.inputCommodityId);
            return comparison != 0 ? comparison : supplyKey.compareTo(other.supplyKey);
        }
    }

    /**
     * Exact integer ship demand found for one remote reservation.
     *
     * @param input exact remote reservation identity
     * @param stableFactionId explicit owner
     * @param reservedInputKgPerSecond exact reserved material rate
     * @param retainedRoute original reservation route authority
     * @param ownerOwnedFreighterCount complete finite owner pool
     * @param minimumRequiredFreighters present when the rate fits inside the complete pool
     * @param minimumCapacityRoute route evaluation at the minimum count, present with the count
     * @param maximumOwnedPoolRoute route evaluation using the complete owned pool
     * @param status whether the route demand is bounded by that pool
     */
    public record FreightDemandEvidence(
            InputFreightKey input,
            String stableFactionId,
            double reservedInputKgPerSecond,
            RouteAssessment retainedRoute,
            int ownerOwnedFreighterCount,
            OptionalInt minimumRequiredFreighters,
            Optional<RouteAssessment> minimumCapacityRoute,
            RouteAssessment maximumOwnedPoolRoute,
            DemandStatus status) {
        /**
         * Validates one immutable route-to-integer-freighter result.
         *
         * @param input exact remote reservation identity
         * @param stableFactionId explicit owner
         * @param reservedInputKgPerSecond exact reserved material rate
         * @param retainedRoute original reservation route authority
         * @param ownerOwnedFreighterCount complete finite owner pool
         * @param minimumRequiredFreighters present when the rate fits inside the complete pool
         * @param minimumCapacityRoute route evaluation at the minimum count, present with the count
         * @param maximumOwnedPoolRoute route evaluation using the complete owned pool
         * @param status whether the route demand is bounded by that pool
         */
        public FreightDemandEvidence {
            Objects.requireNonNull(input, "input");
            stableFactionId = WorldFactionIdentityState.normalizeStableId(stableFactionId);
            requirePositiveFinite(reservedInputKgPerSecond, "reservedInputKgPerSecond");
            Objects.requireNonNull(retainedRoute, "retainedRoute");
            if (ownerOwnedFreighterCount <= 0) {
                throw new IllegalArgumentException("ownerOwnedFreighterCount must be positive");
            }
            Objects.requireNonNull(minimumRequiredFreighters, "minimumRequiredFreighters");
            Objects.requireNonNull(minimumCapacityRoute, "minimumCapacityRoute");
            Objects.requireNonNull(maximumOwnedPoolRoute, "maximumOwnedPoolRoute");
            Objects.requireNonNull(status, "status");
            validateSameRoute(retainedRoute, maximumOwnedPoolRoute);
            boolean bounded = minimumRequiredFreighters.isPresent();
            if (bounded != minimumCapacityRoute.isPresent()
                    || (status == DemandStatus.CAPACITY_BOUND) != bounded) {
                throw new IllegalArgumentException(
                        "freight demand status/count/route presence must agree");
            }
            if (bounded) {
                int count = minimumRequiredFreighters.orElseThrow();
                if (count <= 0 || count > ownerOwnedFreighterCount) {
                    throw new IllegalArgumentException("minimum freighter count is outside the owned pool");
                }
                RouteAssessment route = minimumCapacityRoute.orElseThrow();
                validateSameRoute(retainedRoute, route);
                if (route.sustainableCargoThroughputKgPerSecond()
                        + EPSILON < reservedInputKgPerSecond) {
                    throw new IllegalArgumentException(
                            "minimum-capacity route cannot sustain its reserved input rate");
                }
            } else if (maximumOwnedPoolRoute.sustainableCargoThroughputKgPerSecond()
                    + EPSILON >= reservedInputKgPerSecond) {
                throw new IllegalArgumentException(
                        "unbounded demand cannot fit inside the reported maximum owned-pool route");
            }
        }
    }

    /**
     * One previously uncommitted Stage-20E ownership slot assigned to industrial freight.
     *
     * @param stableFactionId owning faction
     * @param ownershipOrdinal exact ordinal inside the complete bootstrap ownership pool
     * @param routeFreighterOrdinal zero-based position inside this industrial route allocation
     */
    public record AssignedFreighterSlot(
            String stableFactionId,
            int ownershipOrdinal,
            int routeFreighterOrdinal) {
        /**
         * Validates one exact assigned reserve slot.
         *
         * @param stableFactionId owning faction
         * @param ownershipOrdinal exact ordinal inside the complete bootstrap ownership pool
         * @param routeFreighterOrdinal zero-based position inside this industrial route allocation
         */
        public AssignedFreighterSlot {
            stableFactionId = WorldFactionIdentityState.normalizeStableId(stableFactionId);
            if (ownershipOrdinal < 0 || routeFreighterOrdinal < 0) {
                throw new IllegalArgumentException("freighter slot ordinals must be non-negative");
            }
        }
    }

    /**
     * Accepted owned-freight allocation for one remote input reservation.
     *
     * @param demand exact route/count evidence
     * @param assignedSlots distinct previously uncommitted bootstrap ownership slots
     */
    public record OwnedInputFreightAllocation(
            FreightDemandEvidence demand,
            List<AssignedFreighterSlot> assignedSlots) {
        /**
         * Validates one exact accepted route allocation.
         *
         * @param demand exact route/count evidence
         * @param assignedSlots distinct previously uncommitted bootstrap ownership slots
         */
        public OwnedInputFreightAllocation {
            Objects.requireNonNull(demand, "demand");
            if (demand.status() != DemandStatus.CAPACITY_BOUND) {
                throw new IllegalArgumentException("owned allocation requires bounded route demand");
            }
            ArrayList<AssignedFreighterSlot> copy = new ArrayList<>(Objects.requireNonNull(
                    assignedSlots, "assignedSlots"));
            copy.sort(Comparator.comparingInt(AssignedFreighterSlot::routeFreighterOrdinal));
            int required = demand.minimumRequiredFreighters().orElseThrow();
            if (copy.size() != required) {
                throw new IllegalArgumentException("assigned slots must equal minimum required freighters");
            }
            HashSet<Integer> ownershipOrdinals = new HashSet<>();
            for (int ordinal = 0; ordinal < copy.size(); ordinal++) {
                AssignedFreighterSlot slot = copy.get(ordinal);
                if (!slot.stableFactionId().equals(demand.stableFactionId())
                        || slot.routeFreighterOrdinal() != ordinal
                        || !ownershipOrdinals.add(slot.ownershipOrdinal())) {
                    throw new IllegalArgumentException(
                            "assigned route slots must be canonical, unique and owner-matched");
                }
            }
            assignedSlots = List.copyOf(copy);
        }
    }

    /**
     * Shared reserve-pool accounting for one Stage-20E freight owner.
     *
     * @param stableFactionId owning faction
     * @param ownedFreighterCount complete finite pool
     * @param alreadyCommittedFreighterCount Stage-20E essential-service commitments
     * @param availableReserveFreighterCount previously uncommitted slots
     * @param requiredIndustrialFreighterCount summed known industrial route demand
     * @param allRouteDemandsBounded whether every owned route fits inside the complete pool
     * @param status whether the shared reserve can satisfy all owned industrial routes
     */
    public record FactionFreightEvidence(
            String stableFactionId,
            int ownedFreighterCount,
            int alreadyCommittedFreighterCount,
            int availableReserveFreighterCount,
            int requiredIndustrialFreighterCount,
            boolean allRouteDemandsBounded,
            Status status) {
        /**
         * Validates one complete finite faction-pool accounting row.
         *
         * @param stableFactionId owning faction
         * @param ownedFreighterCount complete finite pool
         * @param alreadyCommittedFreighterCount Stage-20E essential-service commitments
         * @param availableReserveFreighterCount previously uncommitted slots
         * @param requiredIndustrialFreighterCount summed known industrial route demand
         * @param allRouteDemandsBounded whether every owned route fits inside the complete pool
         * @param status whether the shared reserve can satisfy all owned industrial routes
         */
        public FactionFreightEvidence {
            stableFactionId = WorldFactionIdentityState.normalizeStableId(stableFactionId);
            if (ownedFreighterCount <= 0
                    || alreadyCommittedFreighterCount < 0
                    || availableReserveFreighterCount < 0
                    || requiredIndustrialFreighterCount < 0
                    || Math.addExact(alreadyCommittedFreighterCount, availableReserveFreighterCount)
                    != ownedFreighterCount) {
                throw new IllegalArgumentException("faction freight counts are inconsistent");
            }
            Objects.requireNonNull(status, "status");
            boolean accepted = allRouteDemandsBounded
                    && requiredIndustrialFreighterCount <= availableReserveFreighterCount;
            if ((status == Status.ACCEPTED) != accepted) {
                throw new IllegalArgumentException("faction freight status differs from finite-pool evidence");
            }
        }
    }

    /**
     * Complete all-or-nothing Stage-20F industrial input freight ownership result.
     *
     * @param version plan contract version
     * @param rootSeed exact accepted generated root seed
     * @param reservation exact accepted shared-input reservation authority
     * @param bootstrapOwnership exact Stage-20E finite ownership authority
     * @param processOwnership exact caller-authored process owners
     * @param capacityProfile exact physical route evaluator provenance
     * @param status final shared owned-freight status
     * @param failureReason absent only when accepted
     * @param demands exact per-remote-reservation ship-count evidence
     * @param factions shared pool accounting for every Stage-20E owner
     * @param allocations exact committed reserve slots; empty on rejection
     * @param missingAuthorities authorities still blocking operational specialization
     */
    public record IndustrialFreightReport(
            String version,
            long rootSeed,
            ReservationReport reservation,
            OwnershipReport bootstrapOwnership,
            ProcessOwnershipAuthority processOwnership,
            FreightCapacityProfile capacityProfile,
            Status status,
            Optional<FailureReason> failureReason,
            List<FreightDemandEvidence> demands,
            List<FactionFreightEvidence> factions,
            List<OwnedInputFreightAllocation> allocations,
            Set<MissingAuthority> missingAuthorities) {
        /**
         * Canonicalizes and validates one immutable industrial freight result.
         *
         * @param version plan contract version
         * @param rootSeed exact accepted generated root seed
         * @param reservation exact accepted shared-input reservation authority
         * @param bootstrapOwnership exact Stage-20E finite ownership authority
         * @param processOwnership exact caller-authored process owners
         * @param capacityProfile exact physical route evaluator provenance
         * @param status final shared owned-freight status
         * @param failureReason absent only when accepted
         * @param demands exact per-remote-reservation ship-count evidence
         * @param factions shared pool accounting for every Stage-20E owner
         * @param allocations exact committed reserve slots; empty on rejection
         * @param missingAuthorities authorities still blocking operational specialization
         */
        public IndustrialFreightReport {
            version = requireText(version, "version");
            Objects.requireNonNull(reservation, "reservation");
            Objects.requireNonNull(bootstrapOwnership, "bootstrapOwnership");
            Objects.requireNonNull(processOwnership, "processOwnership");
            Objects.requireNonNull(capacityProfile, "capacityProfile");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(failureReason, "failureReason");
            if (reservation.rootSeed() != rootSeed
                    || bootstrapOwnership.rootSeed() != rootSeed
                    || processOwnership.rootSeed() != rootSeed) {
                throw new IllegalArgumentException("industrial freight authorities target different root seeds");
            }
            if (reservation.status() != Stage20IndustrialInputReservationPlan.Status.ACCEPTED
                    || !reservation.industrialInputReservationAuthoritative()) {
                throw new IllegalArgumentException("industrial freight requires accepted input reservations");
            }
            if ((status == Status.ACCEPTED) != failureReason.isEmpty()) {
                throw new IllegalArgumentException("failure reason must be absent exactly when accepted");
            }

            ArrayList<FreightDemandEvidence> demandCopy = new ArrayList<>(Objects.requireNonNull(
                    demands, "demands"));
            demandCopy.sort(Comparator.comparing(FreightDemandEvidence::input));
            if (demandCopy.stream().anyMatch(Objects::isNull)
                    || demandCopy.stream().map(FreightDemandEvidence::input).distinct().count()
                    != demandCopy.size()) {
                throw new IllegalArgumentException("freight demands must be unique and contain no nulls");
            }
            demands = List.copyOf(demandCopy);

            ArrayList<FactionFreightEvidence> factionCopy = new ArrayList<>(Objects.requireNonNull(
                    factions, "factions"));
            factionCopy.sort(Comparator.comparing(FactionFreightEvidence::stableFactionId));
            if (factionCopy.isEmpty()
                    || factionCopy.stream().anyMatch(Objects::isNull)
                    || factionCopy.stream().map(FactionFreightEvidence::stableFactionId).distinct().count()
                    != factionCopy.size()) {
                throw new IllegalArgumentException("faction freight evidence must be complete and unique");
            }
            factions = List.copyOf(factionCopy);

            ArrayList<OwnedInputFreightAllocation> allocationCopy = new ArrayList<>(Objects.requireNonNull(
                    allocations, "allocations"));
            allocationCopy.sort(Comparator.comparing(value -> value.demand().input()));
            if (allocationCopy.stream().anyMatch(Objects::isNull)
                    || allocationCopy.stream().map(value -> value.demand().input()).distinct().count()
                    != allocationCopy.size()) {
                throw new IllegalArgumentException("freight allocations must be unique and contain no nulls");
            }
            allocations = List.copyOf(allocationCopy);

            validateCoverage(reservation, bootstrapOwnership, processOwnership, demands, factions);
            validateAssignedSlots(bootstrapOwnership, allocations);
            boolean everyFactionAccepted = factions.stream()
                    .allMatch(value -> value.status() == Status.ACCEPTED);
            if (status == Status.ACCEPTED) {
                if (!everyFactionAccepted || allocations.size() != demands.size()) {
                    throw new IllegalArgumentException(
                            "accepted freight report requires every demand and faction allocation");
                }
                for (int index = 0; index < demands.size(); index++) {
                    if (!allocations.get(index).demand().equals(demands.get(index))) {
                        throw new IllegalArgumentException(
                                "accepted freight allocations must exactly cover demand evidence");
                    }
                }
            } else if (everyFactionAccepted || !allocations.isEmpty()) {
                throw new IllegalArgumentException(
                        "rejected freight report must remain non-committing and retain a failed faction");
            }

            Objects.requireNonNull(missingAuthorities, "missingAuthorities");
            EnumSet<MissingAuthority> expected = reservation.missingAuthorities().isEmpty()
                    ? EnumSet.noneOf(MissingAuthority.class)
                    : EnumSet.copyOf(reservation.missingAuthorities());
            if (status == Status.ACCEPTED) {
                expected.remove(MissingAuthority.OWNED_INDUSTRIAL_INPUT_FREIGHT);
            }
            EnumSet<MissingAuthority> actual = missingAuthorities.isEmpty()
                    ? EnumSet.noneOf(MissingAuthority.class)
                    : EnumSet.copyOf(missingAuthorities);
            if (!actual.equals(expected)) {
                throw new IllegalArgumentException(
                        "industrial freight report cannot silently change another authority");
            }
            missingAuthorities = immutableAuthorities(actual);
        }

        /** @return whether every remote input has distinct finite owned freight */
        public boolean freightOwnershipAuthoritative() {
            return status == Status.ACCEPTED
                    && !missingAuthorities.contains(MissingAuthority.OWNED_INDUSTRIAL_INPUT_FREIGHT);
        }

        /** @return total previously uncommitted freighters assigned to industrial routes */
        public int totalAssignedIndustrialFreighters() {
            return allocations.stream().mapToInt(value -> value.assignedSlots().size()).sum();
        }

        /** @return whether every operational specialization authority is present */
        public boolean operationallyAuthoritative() {
            return missingAuthorities.isEmpty();
        }
    }

    /**
     * Plans current representative industrial freight against exact Stage-20E ownership.
     *
     * @param resolved accepted current generated-world authority
     * @param reservation accepted input reservations for that result
     * @param processOwnership explicit owners for every selected process
     * @return deterministic all-or-nothing industrial freight ownership result
     */
    public static IndustrialFreightReport planCurrent(
            ResolvedProbeResult resolved,
            ReservationReport reservation,
            ProcessOwnershipAuthority processOwnership) {
        ResolvedProbeResult accepted = Objects.requireNonNull(resolved, "resolved");
        var current = Stage20RepresentativeGeneratedWorldProbeProfileV3.deriveCurrent();
        if (!accepted.representativeProfileVersion().equals(current.version())) {
            throw new IllegalArgumentException(
                    "current industrial freight planner requires the current representative profile");
        }
        OwnershipReport ownership = Stage20BootstrapFreightOwnershipPlan.plan(accepted);
        int maximumOwned = ownership.factions().stream()
                .mapToInt(FactionFleetOwnership::ownedFreighterCount)
                .max()
                .orElseThrow();
        var evaluator = Stage20PhysicalFreightRouteEvaluatorFactory.create(
                accepted.generation().topology().requireAcceptedTopology(),
                accepted.generation().jumpEdges().orElseThrow(),
                accepted.generation().localLayouts().orElseThrow(),
                Stage18StationInfrastructureCatalogLoader.loadDefault(),
                current.inputs().transport(),
                maximumOwned);
        var baseFleet = current.inputs().transport().fleetProfile();
        FreightCapacityProfile capacity = new FreightCapacityProfile(
                Stage20PhysicalFreightRouteEvaluatorFactory.CURRENT_VERSION
                        + ":" + baseFleet.version(),
                current.version(),
                baseFleet.payloadMassKgPerFreighter(),
                baseFleet.sourceEvidenceId(),
                maximumOwned);
        return planEvidence(
                reservation,
                ownership,
                processOwnership,
                capacity,
                evaluator::assessWithAllocatedFreighters);
    }

    static IndustrialFreightReport planEvidence(
            ReservationReport reservation,
            OwnershipReport bootstrapOwnership,
            ProcessOwnershipAuthority processOwnership,
            FreightCapacityProfile capacityProfile,
            FreightCapacityEvaluator evaluator) {
        ReservationReport reserved = Objects.requireNonNull(reservation, "reservation");
        OwnershipReport ownership = Objects.requireNonNull(bootstrapOwnership, "bootstrapOwnership");
        ProcessOwnershipAuthority owners = Objects.requireNonNull(processOwnership, "processOwnership");
        FreightCapacityProfile capacity = Objects.requireNonNull(capacityProfile, "capacityProfile");
        FreightCapacityEvaluator routes = Objects.requireNonNull(evaluator, "evaluator");
        if (reserved.status() != Stage20IndustrialInputReservationPlan.Status.ACCEPTED
                || !reserved.industrialInputReservationAuthoritative()) {
            throw new IllegalArgumentException("industrial freight requires accepted input reservations");
        }
        if (reserved.rootSeed() != ownership.rootSeed() || owners.rootSeed() != reserved.rootSeed()) {
            throw new IllegalArgumentException("industrial freight authorities target different root seeds");
        }

        TreeMap<ProcessSelectionKey, String> ownerByProcess = validateOwnerCoverage(
                reserved, ownership, owners);
        TreeMap<String, FactionFleetOwnership> poolByFaction = new TreeMap<>();
        for (FactionFleetOwnership pool : ownership.factions()) {
            poolByFaction.put(pool.stableFactionId(), pool);
            if (pool.ownedFreighterCount() > capacity.maximumValidatedFreighters()) {
                throw new IllegalArgumentException(
                        "freight evaluator cannot cover a complete bootstrap ownership pool");
            }
        }

        ArrayList<FreightDemandEvidence> demands = new ArrayList<>();
        for (InputReservation input : reserved.reservations()) {
            if (input.local()) {
                continue;
            }
            String owner = ownerByProcess.get(input.process());
            FactionFleetOwnership pool = poolByFaction.get(owner);
            demands.add(evaluateDemand(input, owner, pool.ownedFreighterCount(), routes));
        }
        demands.sort(Comparator.comparing(FreightDemandEvidence::input));

        TreeMap<String, Integer> requiredByFaction = new TreeMap<>();
        TreeMap<String, Boolean> boundedByFaction = new TreeMap<>();
        for (String faction : poolByFaction.keySet()) {
            requiredByFaction.put(faction, 0);
            boundedByFaction.put(faction, true);
        }
        for (FreightDemandEvidence demand : demands) {
            if (demand.minimumRequiredFreighters().isPresent()) {
                requiredByFaction.merge(
                        demand.stableFactionId(),
                        demand.minimumRequiredFreighters().orElseThrow(),
                        Math::addExact);
            } else {
                boundedByFaction.put(demand.stableFactionId(), false);
            }
        }

        ArrayList<FactionFreightEvidence> factionEvidence = new ArrayList<>();
        boolean accepted = true;
        for (FactionFleetOwnership pool : poolByFaction.values()) {
            int required = requiredByFaction.get(pool.stableFactionId());
            boolean bounded = boundedByFaction.get(pool.stableFactionId());
            Status factionStatus = bounded && required <= pool.reserveFreighterCount()
                    ? Status.ACCEPTED
                    : Status.INSUFFICIENT_OWNED_FREIGHT;
            if (factionStatus != Status.ACCEPTED) {
                accepted = false;
            }
            factionEvidence.add(new FactionFreightEvidence(
                    pool.stableFactionId(),
                    pool.ownedFreighterCount(),
                    pool.committedFreighterCount(),
                    pool.reserveFreighterCount(),
                    required,
                    bounded,
                    factionStatus));
        }

        ArrayList<OwnedInputFreightAllocation> allocations = accepted
                ? assignReserveSlots(demands, poolByFaction)
                : new ArrayList<>();
        EnumSet<MissingAuthority> missing = EnumSet.copyOf(reserved.missingAuthorities());
        if (accepted) {
            missing.remove(MissingAuthority.OWNED_INDUSTRIAL_INPUT_FREIGHT);
        }
        return new IndustrialFreightReport(
                CURRENT_VERSION,
                reserved.rootSeed(),
                reserved,
                ownership,
                owners,
                capacity,
                accepted ? Status.ACCEPTED : Status.INSUFFICIENT_OWNED_FREIGHT,
                accepted
                        ? Optional.empty()
                        : Optional.of(FailureReason.INSUFFICIENT_UNCOMMITTED_OWNED_FREIGHT),
                demands,
                factionEvidence,
                allocations,
                missing);
    }

    private static TreeMap<ProcessSelectionKey, String> validateOwnerCoverage(
            ReservationReport reservation,
            OwnershipReport ownership,
            ProcessOwnershipAuthority owners) {
        HashSet<ProcessSelectionKey> selected = new HashSet<>();
        reservation.selection().requests().forEach(value -> selected.add(value.process()));
        TreeMap<ProcessSelectionKey, String> result = new TreeMap<>();
        Set<String> knownFactions = ownership.factions().stream()
                .map(FactionFleetOwnership::stableFactionId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (ProcessOwnerAssignment assignment : owners.assignments()) {
            if (!knownFactions.contains(assignment.stableFactionId())) {
                throw new IllegalArgumentException("process owner has no bootstrap freight pool");
            }
            result.put(assignment.process(), assignment.stableFactionId());
        }
        if (!result.keySet().equals(selected)) {
            throw new IllegalArgumentException(
                    "process ownership must exactly cover the explicit process selection");
        }
        return result;
    }

    private static FreightDemandEvidence evaluateDemand(
            InputReservation input,
            String owner,
            int ownedFreighters,
            FreightCapacityEvaluator evaluator) {
        RouteAssessment maximum = null;
        OptionalInt minimumCount = OptionalInt.empty();
        Optional<RouteAssessment> minimumRoute = Optional.empty();
        for (int count = 1; count <= ownedFreighters; count++) {
            RouteAssessment assessed = evaluator.assess(
                    input.supplyKey().systemId(), input.process().systemId(), count).orElseThrow(
                            () -> new IllegalArgumentException(
                                    "freight evaluator lost a retained industrial input route"));
            validateSameRoute(input.route(), assessed);
            maximum = assessed;
            if (minimumCount.isEmpty()
                    && assessed.sustainableCargoThroughputKgPerSecond()
                    + EPSILON >= input.reservedInputKgPerSecond()) {
                minimumCount = OptionalInt.of(count);
                minimumRoute = Optional.of(assessed);
            }
        }
        return new FreightDemandEvidence(
                InputFreightKey.from(input),
                owner,
                input.reservedInputKgPerSecond(),
                input.route(),
                ownedFreighters,
                minimumCount,
                minimumRoute,
                Objects.requireNonNull(maximum, "maximum route"),
                minimumCount.isPresent()
                        ? DemandStatus.CAPACITY_BOUND
                        : DemandStatus.EXCEEDS_OWNED_POOL);
    }

    private static ArrayList<OwnedInputFreightAllocation> assignReserveSlots(
            List<FreightDemandEvidence> demands,
            Map<String, FactionFleetOwnership> pools) {
        TreeMap<String, ArrayList<OwnershipSlot>> reserves = new TreeMap<>();
        TreeMap<String, Integer> cursor = new TreeMap<>();
        for (FactionFleetOwnership pool : pools.values()) {
            ArrayList<OwnershipSlot> slots = new ArrayList<>(pool.materializationSlots().stream()
                    .filter(value -> value.commitment().isEmpty())
                    .toList());
            slots.sort(Comparator.comparingInt(OwnershipSlot::ownershipOrdinal));
            if (slots.size() != pool.reserveFreighterCount()) {
                throw new IllegalArgumentException(
                        "bootstrap ownership reserve slots differ from aggregate reserve count");
            }
            reserves.put(pool.stableFactionId(), slots);
            cursor.put(pool.stableFactionId(), 0);
        }

        ArrayList<OwnedInputFreightAllocation> result = new ArrayList<>();
        for (FreightDemandEvidence demand : demands) {
            int required = demand.minimumRequiredFreighters().orElseThrow();
            int start = cursor.get(demand.stableFactionId());
            ArrayList<AssignedFreighterSlot> assigned = new ArrayList<>();
            for (int routeOrdinal = 0; routeOrdinal < required; routeOrdinal++) {
                OwnershipSlot slot = reserves.get(demand.stableFactionId()).get(start + routeOrdinal);
                assigned.add(new AssignedFreighterSlot(
                        slot.stableFactionId(), slot.ownershipOrdinal(), routeOrdinal));
            }
            cursor.put(demand.stableFactionId(), Math.addExact(start, required));
            result.add(new OwnedInputFreightAllocation(demand, assigned));
        }
        return result;
    }

    private static void validateCoverage(
            ReservationReport reservation,
            OwnershipReport ownership,
            ProcessOwnershipAuthority owners,
            List<FreightDemandEvidence> demands,
            List<FactionFreightEvidence> factions) {
        Set<ProcessSelectionKey> selected = reservation.selection().requests().stream()
                .map(Stage20IndustrialInputReservationPlan.ProcessOutputRequest::process)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<ProcessSelectionKey> assigned = owners.assignments().stream()
                .map(ProcessOwnerAssignment::process)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!selected.equals(assigned)) {
            throw new IllegalArgumentException("process ownership must exactly cover selected processes");
        }
        TreeMap<ProcessSelectionKey, String> ownerByProcess = new TreeMap<>();
        owners.assignments().forEach(value -> ownerByProcess.put(
                value.process(), value.stableFactionId()));
        TreeMap<InputFreightKey, InputReservation> reservationByKey = new TreeMap<>();
        reservation.reservations().stream()
                .filter(value -> !value.local())
                .forEach(value -> reservationByKey.put(InputFreightKey.from(value), value));
        Set<InputFreightKey> expectedDemands = reservationByKey.keySet();
        Set<InputFreightKey> actualDemands = demands.stream()
                .map(FreightDemandEvidence::input)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!expectedDemands.equals(actualDemands)) {
            throw new IllegalArgumentException("freight demand evidence must cover every remote reservation");
        }
        TreeMap<String, FactionFleetOwnership> ownershipByFaction = new TreeMap<>();
        ownership.factions().forEach(value -> ownershipByFaction.put(value.stableFactionId(), value));
        Set<String> expectedFactions = ownershipByFaction.keySet();
        Set<String> actualFactions = factions.stream()
                .map(FactionFreightEvidence::stableFactionId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!expectedFactions.equals(actualFactions)) {
            throw new IllegalArgumentException("freight evidence must cover every bootstrap owner pool");
        }

        TreeMap<String, Integer> requiredByFaction = new TreeMap<>();
        TreeMap<String, Boolean> boundedByFaction = new TreeMap<>();
        for (String faction : expectedFactions) {
            requiredByFaction.put(faction, 0);
            boundedByFaction.put(faction, true);
        }
        for (FreightDemandEvidence demand : demands) {
            InputReservation retained = reservationByKey.get(demand.input());
            String expectedOwner = ownerByProcess.get(demand.input().process());
            FactionFleetOwnership pool = ownershipByFaction.get(demand.stableFactionId());
            if (retained == null
                    || expectedOwner == null
                    || !expectedOwner.equals(demand.stableFactionId())
                    || pool == null
                    || demand.ownerOwnedFreighterCount() != pool.ownedFreighterCount()
                    || Double.compare(
                    demand.reservedInputKgPerSecond(), retained.reservedInputKgPerSecond()) != 0
                    || !demand.retainedRoute().equals(retained.route())) {
                throw new IllegalArgumentException(
                        "freight demand must retain its reservation, explicit owner and complete pool");
            }
            if (demand.minimumRequiredFreighters().isPresent()) {
                requiredByFaction.merge(
                        demand.stableFactionId(),
                        demand.minimumRequiredFreighters().orElseThrow(),
                        Math::addExact);
            } else {
                boundedByFaction.put(demand.stableFactionId(), false);
            }
        }
        for (FactionFreightEvidence evidence : factions) {
            FactionFleetOwnership pool = ownershipByFaction.get(evidence.stableFactionId());
            if (evidence.ownedFreighterCount() != pool.ownedFreighterCount()
                    || evidence.alreadyCommittedFreighterCount() != pool.committedFreighterCount()
                    || evidence.availableReserveFreighterCount() != pool.reserveFreighterCount()
                    || evidence.requiredIndustrialFreighterCount()
                    != requiredByFaction.get(evidence.stableFactionId())
                    || evidence.allRouteDemandsBounded()
                    != boundedByFaction.get(evidence.stableFactionId())) {
                throw new IllegalArgumentException(
                        "faction freight evidence must equal ownership and summed route demand");
            }
        }
    }

    private static void validateAssignedSlots(
            OwnershipReport ownership,
            List<OwnedInputFreightAllocation> allocations) {
        HashMap<SlotKey, Boolean> reserveSlots = new HashMap<>();
        for (FactionFleetOwnership faction : ownership.factions()) {
            for (OwnershipSlot slot : faction.materializationSlots()) {
                reserveSlots.put(
                        new SlotKey(slot.stableFactionId(), slot.ownershipOrdinal()),
                        slot.commitment().isEmpty());
            }
        }
        HashSet<SlotKey> assigned = new HashSet<>();
        for (OwnedInputFreightAllocation allocation : allocations) {
            for (AssignedFreighterSlot slot : allocation.assignedSlots()) {
                SlotKey key = new SlotKey(slot.stableFactionId(), slot.ownershipOrdinal());
                if (!Boolean.TRUE.equals(reserveSlots.get(key)) || !assigned.add(key)) {
                    throw new IllegalArgumentException(
                            "industrial allocation must use each bootstrap reserve slot at most once");
                }
            }
        }
    }

    private static void validateSameRoute(RouteAssessment retained, RouteAssessment assessed) {
        Objects.requireNonNull(retained, "retained");
        Objects.requireNonNull(assessed, "assessed");
        if (!retained.orderedSystems().equals(assessed.orderedSystems())
                || Math.abs(retained.travelTimeS() - assessed.travelTimeS()) > EPSILON) {
            throw new IllegalArgumentException(
                    "freight capacity evaluator changed retained route geometry or delivery time");
        }
    }

    private static Set<MissingAuthority> immutableAuthorities(EnumSet<MissingAuthority> authorities) {
        return Collections.unmodifiableSet(authorities.isEmpty()
                ? EnumSet.noneOf(MissingAuthority.class)
                : EnumSet.copyOf(authorities));
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

    private record SlotKey(String stableFactionId, int ownershipOrdinal) {}
}
