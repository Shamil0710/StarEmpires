package com.spacesim.world;

import com.spacesim.world.Stage20EconomicThroughputAcceptance.AcceptanceReport;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.generation.Stage20JumpTopologyGenerationResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Stage-20E whole-seed acceptance composition over already authoritative generation gates.
 *
 * <p>This class does not generate, repair or rebalance a world. The original v1 composition remains
 * available for the historical single-supplier economic-throughput baseline. The resolved-freight v2
 * path composes Stage-20D topology, bounded faction-start placement and the coordinated finite-fleet
 * Stage-20E acceptance without converting bounded-search uncertainty into a rejected world.</p>
 */
public final class Stage20GeneratedWorldSeedAcceptance {
    /** Historical single-supplier whole-seed acceptance version retained for baseline reproducibility. */
    public static final String CURRENT_VERSION = "stage20e.generated-world-seed-acceptance.v1";
    /** Production candidate using coordinated finite-fleet Stage-20E acceptance. */
    public static final String RESOLVED_FREIGHT_VERSION = "stage20e.generated-world-seed-acceptance.v2";

    private Stage20GeneratedWorldSeedAcceptance() {
        throw new AssertionError("No instances");
    }

    /** Whole-seed final status. */
    public enum Status {
        /** All currently authoritative ordinary-generation gates accepted the seed. */
        ACCEPTED,
        /** At least one authoritative physical/economic/start gate rejected the seed. */
        REJECTED_SEED,
        /** No authoritative rejection exists, but required acceptance authority is unresolved. */
        UNRESOLVED_AUTHORITY
    }

    /** Stable whole-seed rejection/blocker classes. */
    public enum FailureReason {
        /** Stage-20D ordinary topology quality could not be repaired inside its bounded policy. */
        TOPOLOGY_QUALITY_REJECTED,
        /** Historical selected/ordinary starts fail single-supplier delivered-throughput acceptance. */
        ECONOMIC_THROUGHPUT_REJECTED,
        /** Complete coordinated finite-fleet evidence proves the placed starts physically infeasible. */
        COORDINATED_FREIGHT_INFEASIBLE,
        /** Coordinated freight search remains incomplete and therefore cannot authorize acceptance. */
        COORDINATED_FREIGHT_AUTHORITY_UNRESOLVED,
        /** Bounded faction-start assignment rejected the generated seed. */
        FACTION_START_PLACEMENT_REJECTED,
        /** Faction-start acceptance is blocked by explicitly missing upstream authority. */
        FACTION_START_AUTHORITY_UNRESOLVED
    }

    /**
     * One normalized whole-seed failure/blocker row.
     *
     * @param reason stable failure class
     * @param subject deterministic source subject
     * @param detail deterministic human-readable diagnostic detail
     */
    public record Failure(FailureReason reason, String subject, String detail) {
        /**
         * Validates one immutable failure row.
         *
         * @param reason stable failure class
         * @param subject deterministic source subject
         * @param detail deterministic diagnostic detail
         */
        public Failure {
            Objects.requireNonNull(reason, "reason");
            subject = requireText(subject, "subject");
            detail = requireText(detail, "detail");
        }

        /**
         * Returns whether this row represents unavailable authority rather than a rejected world.
         *
         * @return true only for unresolved-authority blockers
         */
        public boolean unresolvedAuthority() {
            return reason == FailureReason.FACTION_START_AUTHORITY_UNRESOLVED
                    || reason == FailureReason.COORDINATED_FREIGHT_AUTHORITY_UNRESOLVED;
        }
    }

    /**
     * Immutable composed acceptance result for one root seed.
     *
     * @param version stable result version
     * @param rootSeed authoritative generation seed
     * @param status final whole-seed status
     * @param topologyStatus exact Stage-20D topology status
     * @param topologyRepairPasses deterministic topology repair additions committed before decision
     * @param economicAcceptancePresent whether the applicable economic/freight report was present
     * @param placementStatus bounded start-placement status when topology passed
     * @param failures deterministic normalized rejection/blocker rows
     */
    public record SeedResult(
            String version,
            long rootSeed,
            Status status,
            Stage20JumpTopologyGenerationResult.Status topologyStatus,
            int topologyRepairPasses,
            boolean economicAcceptancePresent,
            Optional<PlacementStatus> placementStatus,
            List<Failure> failures) {
        /**
         * Validates and freezes one composed acceptance result.
         *
         * @param version stable result version
         * @param rootSeed authoritative generation seed
         * @param status final whole-seed status
         * @param topologyStatus exact Stage-20D topology status
         * @param topologyRepairPasses topology repair additions
         * @param economicAcceptancePresent whether the applicable economic/freight report was present
         * @param placementStatus start-placement status when applicable
         * @param failures normalized rejection/blocker rows
         */
        public SeedResult {
            version = requireText(version, "version");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(topologyStatus, "topologyStatus");
            Objects.requireNonNull(placementStatus, "placementStatus");
            Objects.requireNonNull(failures, "failures");
            if (topologyRepairPasses < 0) {
                throw new IllegalArgumentException("topologyRepairPasses must be non-negative");
            }
            ArrayList<Failure> copy = new ArrayList<>(failures);
            if (copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("failures cannot contain null");
            }
            copy.sort(Comparator.comparing((Failure value) -> value.reason().name())
                    .thenComparing(Failure::subject)
                    .thenComparing(Failure::detail));
            failures = List.copyOf(copy);
            if (status == Status.ACCEPTED && !failures.isEmpty()) {
                throw new IllegalArgumentException("accepted seed cannot contain failures");
            }
            if (status == Status.UNRESOLVED_AUTHORITY
                    && failures.stream().noneMatch(Failure::unresolvedAuthority)) {
                throw new IllegalArgumentException("unresolved seed requires an authority blocker");
            }
            if (status == Status.REJECTED_SEED
                    && failures.stream().noneMatch(value -> !value.unresolvedAuthority())) {
                throw new IllegalArgumentException("rejected seed requires an authoritative rejection");
            }
        }
    }

    /**
     * Composes the historical v1 single-supplier whole-seed decision.
     *
     * <p>This path remains unchanged for baseline reproducibility. New Stage-20E production integration
     * must use {@link #composeResolvedFreight(Stage20JumpTopologyGenerationResult, Optional, Optional)}.</p>
     *
     * @param topology Stage-20D topology generation result
     * @param economicThroughput historical physical throughput acceptance when topology passed
     * @param placement bounded Stage-20E faction-start placement when topology passed
     * @return deterministic historical whole-seed result
     */
    public static SeedResult compose(
            Stage20JumpTopologyGenerationResult topology,
            Optional<AcceptanceReport> economicThroughput,
            Optional<PlacementResult> placement) {
        Stage20JumpTopologyGenerationResult topologyResult = Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(economicThroughput, "economicThroughput");
        Objects.requireNonNull(placement, "placement");
        long seed = topologyResult.seed();

        if (topologyResult.status() == Stage20JumpTopologyGenerationResult.Status.REJECTED_SEED) {
            if (economicThroughput.isPresent() || placement.isPresent()) {
                throw new IllegalArgumentException(
                        "topology-rejected seed cannot carry materialized downstream acceptance reports");
            }
            return topologyRejected(CURRENT_VERSION, topologyResult);
        }

        AcceptanceReport economics = economicThroughput.orElseThrow(() -> new IllegalArgumentException(
                "topology-accepted seed requires physical economic-throughput acceptance"));
        PlacementResult starts = placement.orElseThrow(() -> new IllegalArgumentException(
                "topology-accepted seed requires bounded faction-start placement"));
        requirePlacementSeed(starts, seed);

        ArrayList<Failure> failures = new ArrayList<>();
        if (!economics.accepted()) {
            for (Stage20EconomicThroughputAcceptance.RequirementFailure failure : economics.failures()) {
                failures.add(new Failure(
                        FailureReason.ECONOMIC_THROUGHPUT_REJECTED,
                        failure.startSystemId() + ":" + failure.commodityId(),
                        failure.reason().name() + ": " + failure.detail()));
            }
        }
        addPlacementFailure(starts, failures);

        return completed(
                CURRENT_VERSION,
                topologyResult,
                starts,
                true,
                failures);
    }

    /**
     * Composes the Stage-20E v2 whole-seed decision from coordinated finite-fleet freight evidence.
     *
     * <p>Topology rejection stops before any downstream result. For an accepted topology, placement
     * is decided first. A rejected or unresolved placement must not carry a synthetic freight report.
     * Only an accepted non-empty placement may enter coordinated freight acceptance. Complete freight
     * infeasibility is an authoritative seed rejection; unresolved frontier search is an authority
     * blocker and can never be converted into physical infeasibility.</p>
     *
     * @param topology Stage-20D topology generation result
     * @param resolvedFreight coordinated Stage-20E freight evidence only for accepted placement
     * @param placement bounded Stage-20E faction-start placement when topology passed
     * @return deterministic v2 whole-seed result
     */
    public static SeedResult composeResolvedFreight(
            Stage20JumpTopologyGenerationResult topology,
            Optional<Stage20ResolvedFreightAcceptance.AcceptanceReport> resolvedFreight,
            Optional<PlacementResult> placement) {
        Stage20JumpTopologyGenerationResult topologyResult = Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(resolvedFreight, "resolvedFreight");
        Objects.requireNonNull(placement, "placement");
        long seed = topologyResult.seed();

        if (topologyResult.status() == Stage20JumpTopologyGenerationResult.Status.REJECTED_SEED) {
            if (resolvedFreight.isPresent() || placement.isPresent()) {
                throw new IllegalArgumentException(
                        "topology-rejected seed cannot carry placement/freight acceptance reports");
            }
            return topologyRejected(RESOLVED_FREIGHT_VERSION, topologyResult);
        }

        PlacementResult starts = placement.orElseThrow(() -> new IllegalArgumentException(
                "topology-accepted seed requires bounded faction-start placement"));
        requirePlacementSeed(starts, seed);

        ArrayList<Failure> failures = new ArrayList<>();
        if (starts.status() != PlacementStatus.ACCEPTED) {
            if (resolvedFreight.isPresent()) {
                throw new IllegalArgumentException(
                        "non-accepted placement cannot carry coordinated freight acceptance");
            }
            addPlacementFailure(starts, failures);
            return completed(
                    RESOLVED_FREIGHT_VERSION,
                    topologyResult,
                    starts,
                    false,
                    failures);
        }
        if (starts.assignments().isEmpty()) {
            throw new IllegalArgumentException("accepted placement must contain at least one faction start");
        }

        Stage20ResolvedFreightAcceptance.AcceptanceReport freight = resolvedFreight.orElseThrow(
                () -> new IllegalArgumentException(
                        "accepted placement requires coordinated finite-fleet acceptance"));
        if (freight.rootSeed() != seed) {
            throw new IllegalArgumentException("coordinated freight root seed differs from generated topology");
        }
        if (!freight.placementVersion().equals(starts.version())) {
            throw new IllegalArgumentException("coordinated freight placement version differs from selected placement");
        }
        Set<String> placedFactions = new HashSet<>();
        starts.assignments().forEach(value -> placedFactions.add(value.stableFactionId()));
        if (!freight.remoteFreighterBudgetByFaction().keySet().equals(placedFactions)) {
            throw new IllegalArgumentException("coordinated freight faction set differs from selected placement");
        }

        if (freight.infeasible()) {
            failures.add(new Failure(
                    FailureReason.COORDINATED_FREIGHT_INFEASIBLE,
                    "coordinated-freight",
                    freight.combination().failureReason().map(Enum::name)
                            .orElse("complete coordinated freight infeasibility")));
        } else if (freight.unresolved()) {
            failures.add(new Failure(
                    FailureReason.COORDINATED_FREIGHT_AUTHORITY_UNRESOLVED,
                    "coordinated-freight",
                    freight.combination().failureReason().map(Enum::name)
                            .orElse("coordinated freight frontier remains unresolved")));
        } else if (!freight.accepted()) {
            throw new IllegalStateException("unknown coordinated freight acceptance status");
        }

        return completed(
                RESOLVED_FREIGHT_VERSION,
                topologyResult,
                starts,
                true,
                failures);
    }

    private static SeedResult topologyRejected(
            String version,
            Stage20JumpTopologyGenerationResult topologyResult) {
        return new SeedResult(
                version,
                topologyResult.seed(),
                Status.REJECTED_SEED,
                topologyResult.status(),
                topologyResult.repairPasses(),
                false,
                Optional.empty(),
                List.of(new Failure(
                        FailureReason.TOPOLOGY_QUALITY_REJECTED,
                        "topology",
                        "Stage-20D topology quality report retains "
                                + topologyResult.qualityReport().violations().size() + " violation(s)")));
    }

    private static void requirePlacementSeed(PlacementResult starts, long seed) {
        if (starts.rootSeed() != seed) {
            throw new IllegalArgumentException("placement root seed differs from topology root seed");
        }
    }

    private static void addPlacementFailure(PlacementResult starts, List<Failure> failures) {
        if (starts.status() == PlacementStatus.REJECTED_SEED) {
            failures.add(new Failure(
                    FailureReason.FACTION_START_PLACEMENT_REJECTED,
                    "faction-start-placement",
                    starts.failureReason().map(Enum::name).orElse("unspecified placement rejection")));
        } else if (starts.status() == PlacementStatus.UNRESOLVED_AUTHORITY) {
            failures.add(new Failure(
                    FailureReason.FACTION_START_AUTHORITY_UNRESOLVED,
                    "faction-start-placement",
                    starts.failureReason().map(Enum::name).orElse("unresolved placement authority")));
        }
    }

    private static SeedResult completed(
            String version,
            Stage20JumpTopologyGenerationResult topologyResult,
            PlacementResult starts,
            boolean economicAcceptancePresent,
            List<Failure> failures) {
        boolean authoritativeRejection = failures.stream().anyMatch(value -> !value.unresolvedAuthority());
        boolean authorityBlocker = failures.stream().anyMatch(Failure::unresolvedAuthority);
        Status status = authoritativeRejection
                ? Status.REJECTED_SEED
                : authorityBlocker ? Status.UNRESOLVED_AUTHORITY : Status.ACCEPTED;
        return new SeedResult(
                version,
                topologyResult.seed(),
                status,
                topologyResult.status(),
                topologyResult.repairPasses(),
                economicAcceptancePresent,
                Optional.of(starts.status()),
                failures);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
