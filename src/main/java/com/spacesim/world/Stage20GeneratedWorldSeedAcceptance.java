package com.spacesim.world;

import com.spacesim.world.Stage20EconomicThroughputAcceptance.AcceptanceReport;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.generation.Stage20JumpTopologyGenerationResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Stage-20E whole-seed acceptance composition over already authoritative generation gates.
 *
 * <p>This class does not generate, repair or rebalance a world. It combines the existing Stage-20D
 * topology result, Stage-20E physical economic-throughput acceptance and Stage-20E bounded faction
 * start placement into one machine-readable seed outcome. A topology-rejected seed stops before
 * downstream materialization. A topology-accepted seed must supply both downstream reports; missing
 * reports are an integration error and are never counted as an ordinary rejected seed.</p>
 */
public final class Stage20GeneratedWorldSeedAcceptance {
    /** Current immutable whole-seed acceptance version. */
    public static final String CURRENT_VERSION = "stage20e.generated-world-seed-acceptance.v1";

    private Stage20GeneratedWorldSeedAcceptance() {
        throw new AssertionError("No instances");
    }

    /** Whole-seed final status. */
    public enum Status {
        /** All currently authoritative ordinary-generation gates accepted the seed. */
        ACCEPTED,
        /** At least one authoritative physical/economic/start gate rejected the seed. */
        REJECTED_SEED,
        /** No authoritative rejection exists, but required start acceptance authority is unresolved. */
        UNRESOLVED_AUTHORITY
    }

    /** Stable whole-seed rejection/blocker classes. */
    public enum FailureReason {
        /** Stage-20D ordinary topology quality could not be repaired inside its bounded policy. */
        TOPOLOGY_QUALITY_REJECTED,
        /** Selected/ordinary starts fail physical essential delivered-throughput acceptance. */
        ECONOMIC_THROUGHPUT_REJECTED,
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
            return reason == FailureReason.FACTION_START_AUTHORITY_UNRESOLVED;
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
     * @param economicAcceptancePresent whether an economic-throughput report was applicable/present
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
         * Validates and freezes one composed seed result.
         *
         * @param version stable result version
         * @param rootSeed authoritative generation seed
         * @param status final whole-seed status
         * @param topologyStatus exact topology status
         * @param topologyRepairPasses topology repair additions
         * @param economicAcceptancePresent whether economic acceptance was applicable/present
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
     * Composes one whole-seed decision from existing authoritative generation outputs.
     *
     * <p>When topology is rejected, both downstream reports must be absent because the rejected
     * candidate must not be materialized as an ordinary production world. When topology is accepted,
     * both reports are mandatory. This distinction prevents a broken batch harness from lowering the
     * measured seed acceptance rate.</p>
     *
     * @param topology Stage-20D topology generation result
     * @param economicThroughput physical Stage-20E throughput acceptance when topology passed
     * @param placement bounded Stage-20E faction-start placement when topology passed
     * @return deterministic whole-seed result
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
            return new SeedResult(
                    CURRENT_VERSION,
                    seed,
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

        AcceptanceReport economics = economicThroughput.orElseThrow(() -> new IllegalArgumentException(
                "topology-accepted seed requires physical economic-throughput acceptance"));
        PlacementResult starts = placement.orElseThrow(() -> new IllegalArgumentException(
                "topology-accepted seed requires bounded faction-start placement"));
        if (starts.rootSeed() != seed) {
            throw new IllegalArgumentException("placement root seed differs from topology root seed");
        }

        ArrayList<Failure> failures = new ArrayList<>();
        if (!economics.accepted()) {
            for (Stage20EconomicThroughputAcceptance.RequirementFailure failure : economics.failures()) {
                failures.add(new Failure(
                        FailureReason.ECONOMIC_THROUGHPUT_REJECTED,
                        failure.startSystemId() + ":" + failure.commodityId(),
                        failure.reason().name() + ": " + failure.detail()));
            }
        }
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

        boolean authoritativeRejection = failures.stream().anyMatch(value -> !value.unresolvedAuthority());
        boolean authorityBlocker = failures.stream().anyMatch(Failure::unresolvedAuthority);
        Status status = authoritativeRejection
                ? Status.REJECTED_SEED
                : authorityBlocker ? Status.UNRESOLVED_AUTHORITY : Status.ACCEPTED;
        return new SeedResult(
                CURRENT_VERSION,
                seed,
                status,
                topologyResult.status(),
                topologyResult.repairPasses(),
                true,
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
