package com.spacesim.warfare;

import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage19ConflictState.ConflictSnapshot;
import com.spacesim.persistence.Stage19ConflictState.ConflictStatus;
import com.spacesim.persistence.Stage19ConflictState.ObjectiveSnapshot;
import com.spacesim.persistence.Stage19ConflictStateCodec;
import com.spacesim.warfare.Stage19ConflictRuntime.CurrentPhysicalReadiness;
import com.spacesim.warfare.Stage19ConflictRuntime.DecisionApplication;
import com.spacesim.warfare.Stage19ConflictRuntime.ObservationDelta;
import com.spacesim.warfare.StrategicWarPolicyService.Decision;
import com.spacesim.warfare.StrategicWarPolicyService.EscalationLevel;
import com.spacesim.warfare.StrategicWarPolicyService.ObjectiveEvidence;
import com.spacesim.warfare.StrategicWarPolicyService.Policy;
import com.spacesim.warfare.StrategicWarPolicyService.SettlementOffer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Stage-19H deterministic aggregate acceptance harness for the strategic warfare causal chain.
 *
 * <p>This harness intentionally does not simulate tactical combat. Stage 19I owns the final live
 * production-combat gate. Here the already-measured physical consequences of Stages 17.5/18/19E-F
 * are supplied explicitly, persisted mid-conflict and then consumed by Stage-19G policy. The harness
 * proves that save/load does not create a second economy, erase actor-bounded history or change the
 * political result.</p>
 */
public final class Stage19AggregateWarfareAcceptanceHarness {
    private static final String CONFLICT_ID = "conflict.acceptance.alpha";
    private static final String ACTOR_ID = "faction.acceptance.actor";
    private static final String OPPONENT_ID = "faction.acceptance.opponent";
    private static final String OBJECTIVE_ID = "objective.acceptance.open_corridor";
    private static final String SUBJECT_ID = "link.acceptance.alpha-beta";

    private static final Policy POLICY = new Policy(
            2,
            20_000d,
            true,
            2_000_000d,
            500_000d);
    private static final CurrentPhysicalReadiness READINESS = new CurrentPhysicalReadiness(
            4,
            50_000d,
            10_000d,
            12_000d);
    private static final MobilizationDemand MOBILIZATION = new MobilizationDemand(
            20L,
            20L,
            20_000d,
            50_000d,
            10_000d,
            12_000d,
            0,
            0);

    /**
     * Runs the canonical warfare chain without a persistence interruption.
     *
     * @return deterministic aggregate acceptance result
     */
    public AcceptanceResult runUninterrupted() {
        return run(false);
    }

    /**
     * Runs the same canonical warfare chain with an encode/decode/restore checkpoint after escalation.
     *
     * @return deterministic aggregate acceptance result after mid-conflict save/load
     */
    public AcceptanceResult runWithMidConflictCheckpoint() {
        return run(true);
    }

    private AcceptanceResult run(boolean checkpoint) {
        ObjectiveSnapshot objective = new ObjectiveSnapshot(
                OBJECTIVE_ID,
                SUBJECT_ID,
                true,
                ObjectiveEvidence.OBSERVED_UNMET);
        ConflictSnapshot initial = ConflictSnapshot.active(
                CONFLICT_ID,
                ACTOR_ID,
                OPPONENT_ID,
                EscalationLevel.CRISIS,
                List.of(objective));
        Stage19ConflictRuntime runtime = new Stage19ConflictRuntime(new Stage19ConflictState(
                Stage19ConflictState.CURRENT_VERSION,
                100L,
                List.of(initial)));
        ArrayList<Decision> decisions = new ArrayList<>();

        if (!MOBILIZATION.fullyBacked()) {
            throw new IllegalStateException("Canonical Stage-19H mobilization fixture must be physically backed");
        }

        DecisionApplication escalation = runtime.decide(
                CONFLICT_ID,
                101L,
                READINESS,
                POLICY,
                SettlementOffer.none());
        decisions.add(escalation.policyResult().decision());

        if (checkpoint) {
            byte[] bytes = Stage19ConflictStateCodec.encode(runtime.snapshot());
            runtime = new Stage19ConflictRuntime(Stage19ConflictStateCodec.decode(bytes));
        }

        runtime.observe(
                CONFLICT_ID,
                102L,
                new ObservationDelta(
                        0d,
                        0d,
                        0d,
                        600_000d,
                        java.util.Map.of()));
        DecisionApplication coerciveOffer = runtime.decide(
                CONFLICT_ID,
                103L,
                READINESS,
                POLICY,
                SettlementOffer.none());
        decisions.add(coerciveOffer.policyResult().decision());

        DecisionApplication accepted = runtime.decide(
                CONFLICT_ID,
                104L,
                READINESS,
                POLICY,
                new SettlementOffer(true, Set.of(OBJECTIVE_ID)));
        decisions.add(accepted.policyResult().decision());

        Stage19ConflictState finalState = runtime.snapshot();
        ConflictSnapshot finalConflict = finalState.conflicts().stream()
                .filter(value -> value.conflictId().equals(CONFLICT_ID))
                .findFirst()
                .orElseThrow();
        return new AcceptanceResult(decisions, finalState, finalConflict, MOBILIZATION);
    }

    /**
     * Inspectable physical backing required by the canonical mobilization fixture.
     *
     * <p>This record is not a readiness currency. Each field retains the native physical/count unit
     * supplied by its owner, and {@link #fullyBacked()} is only an acceptance predicate. It cannot
     * transfer stock, manufacture ammunition or complete replacement construction.</p>
     *
     * @param ammunitionRoundsRequired finite manufactured ammunition rounds required
     * @param ammunitionRoundsAvailable finite manufactured ammunition rounds physically available
     * @param reactionMassKgRequired physical reaction mass required in kilograms
     * @param reactionMassKgAvailable physical reaction mass available in kilograms
     * @param repairMaterialKgRequired physical compatible repair material required in kilograms
     * @param repairMaterialKgAvailable physical compatible repair material available in kilograms
     * @param replacementShipsRequired physical completed replacement ships required
     * @param replacementShipsAvailable physical completed replacement ships available
     */
    public record MobilizationDemand(
            long ammunitionRoundsRequired,
            long ammunitionRoundsAvailable,
            double reactionMassKgRequired,
            double reactionMassKgAvailable,
            double repairMaterialKgRequired,
            double repairMaterialKgAvailable,
            int replacementShipsRequired,
            int replacementShipsAvailable) {
        /**
         * Validates one native-unit mobilization backing diagnostic.
         *
         * @param ammunitionRoundsRequired required finite ammunition rounds
         * @param ammunitionRoundsAvailable available finite ammunition rounds
         * @param reactionMassKgRequired required reaction mass in kilograms
         * @param reactionMassKgAvailable available reaction mass in kilograms
         * @param repairMaterialKgRequired required compatible repair material in kilograms
         * @param repairMaterialKgAvailable available compatible repair material in kilograms
         * @param replacementShipsRequired required completed replacement ships
         * @param replacementShipsAvailable available completed replacement ships
         */
        public MobilizationDemand {
            if (ammunitionRoundsRequired < 0L || ammunitionRoundsAvailable < 0L) {
                throw new IllegalArgumentException("ammunition round counts must be non-negative");
            }
            requireNonNegativeFinite(reactionMassKgRequired, "reactionMassKgRequired");
            requireNonNegativeFinite(reactionMassKgAvailable, "reactionMassKgAvailable");
            requireNonNegativeFinite(repairMaterialKgRequired, "repairMaterialKgRequired");
            requireNonNegativeFinite(repairMaterialKgAvailable, "repairMaterialKgAvailable");
            if (replacementShipsRequired < 0 || replacementShipsAvailable < 0) {
                throw new IllegalArgumentException("replacement ship counts must be non-negative");
            }
        }

        /**
         * Reports whether every represented physical/count demand is already backed by available state.
         *
         * @return true only when ammunition, reaction mass, repair material and completed replacements cover demand
         */
        public boolean fullyBacked() {
            return ammunitionRoundsAvailable >= ammunitionRoundsRequired
                    && reactionMassKgAvailable + 1.0e-9d >= reactionMassKgRequired
                    && repairMaterialKgAvailable + 1.0e-9d >= repairMaterialKgRequired
                    && replacementShipsAvailable >= replacementShipsRequired;
        }
    }

    /**
     * Aggregate Stage-19H acceptance output.
     *
     * @param decisions ordered strategic decisions produced by the canonical chain
     * @param finalState final warfare-owned persistent extension
     * @param finalConflict final canonical conflict snapshot
     * @param mobilizationDemand inspectable native-unit mobilization backing
     */
    public record AcceptanceResult(
            List<Decision> decisions,
            Stage19ConflictState finalState,
            ConflictSnapshot finalConflict,
            MobilizationDemand mobilizationDemand) {
        /**
         * Freezes one aggregate acceptance result.
         *
         * @param decisions ordered strategic decisions
         * @param finalState final warfare persistence extension
         * @param finalConflict final canonical conflict state
         * @param mobilizationDemand native-unit mobilization backing diagnostic
         */
        public AcceptanceResult {
            decisions = List.copyOf(Objects.requireNonNull(decisions, "decisions"));
            Objects.requireNonNull(finalState, "finalState");
            Objects.requireNonNull(finalConflict, "finalConflict");
            Objects.requireNonNull(mobilizationDemand, "mobilizationDemand");
        }

        /**
         * Reports whether the canonical aggregate chain reached the required political result.
         *
         * @return true for backed mobilization, expected causal decisions and resolved final conflict
         */
        public boolean acceptanceSatisfied() {
            return mobilizationDemand.fullyBacked()
                    && decisions.equals(List.of(
                            Decision.ESCALATE,
                            Decision.OFFER_SETTLEMENT,
                            Decision.ACCEPT_SETTLEMENT))
                    && finalConflict.status() == ConflictStatus.RESOLVED;
        }
    }

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}
