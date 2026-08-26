package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent Stage-21G peace, demobilization and replacement metadata.
 *
 * <p>This state is deliberately not an authority for diplomacy, treasury, fleets, ship damage,
 * consumables or shipyard work. It records only which already-accepted peace obligations and
 * ordinary recovery requests have been executed, stalled or completed so save/load cannot duplicate
 * reparations, forget losses or restore destroyed capability for free.</p>
 *
 * @param schemaVersion exact Stage-21G state schema
 * @param simulationTick latest authoritative tick reconciled by Stage 21G
 * @param nextSettlementId next positive settlement identifier
 * @param nextReplacementDemandId next positive replacement-demand identifier
 * @param settlements accepted ceasefire/peace settlements
 * @param payments real-treasury transfer obligations derived from accepted proposals
 * @param demobilizations ordinary Stage-21D return-order obligations
 * @param losses immutable ordinary FleetId loss provenance
 * @param replacementDemands economy/shipyard replacement demand without automatic spawning
 */
public record SettlementRecoveryState(
        int schemaVersion,
        long simulationTick,
        long nextSettlementId,
        long nextReplacementDemandId,
        List<Settlement> settlements,
        List<PaymentObligation> payments,
        List<DemobilizationDirective> demobilizations,
        List<FleetLossRecord> losses,
        List<ReplacementDemand> replacementDemands) {

    /** Current standalone Stage-21G state schema. */
    public static final int CURRENT_VERSION = 1;

    /**
     * Validates referential integrity and canonical deterministic ordering.
     *
     * @param schemaVersion exact supported schema
     * @param simulationTick latest non-negative reconciliation tick
     * @param nextSettlementId next positive settlement identity
     * @param nextReplacementDemandId next positive replacement-demand identity
     * @param settlements settlement rows
     * @param payments payment rows
     * @param demobilizations demobilization rows
     * @param losses loss rows
     * @param replacementDemands replacement rows
     */
    public SettlementRecoveryState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-21G settlement state schema: " + schemaVersion);
        }
        if (simulationTick < 0L || nextSettlementId <= 0L || nextReplacementDemandId <= 0L) {
            throw new IllegalArgumentException("Stage-21G clocks/allocator watermarks are invalid");
        }
        settlements = canonical(settlements, Comparator.comparingLong(Settlement::id));
        payments = canonical(payments, Comparator.comparingLong(PaymentObligation::settlementId)
                .thenComparingInt(PaymentObligation::ordinal));
        demobilizations = canonical(demobilizations, Comparator.comparingLong(DemobilizationDirective::settlementId)
                .thenComparingLong(DemobilizationDirective::commandGroupId));
        losses = canonical(losses, Comparator.comparingLong(FleetLossRecord::settlementId)
                .thenComparing(FleetLossRecord::lostFleetId));
        replacementDemands = canonical(replacementDemands, Comparator.comparingLong(ReplacementDemand::id));

        Set<Long> settlementIds = new HashSet<>();
        Set<String> proposalIds = new HashSet<>();
        long maxSettlement = 0L;
        for (Settlement settlement : settlements) {
            if (!settlementIds.add(settlement.id())) {
                throw new IllegalArgumentException("Duplicate Stage-21G settlement id: " + settlement.id());
            }
            if (!proposalIds.add(settlement.proposalId())) {
                throw new IllegalArgumentException("Accepted proposal already owns a Stage-21G settlement: "
                        + settlement.proposalId());
            }
            if (settlement.updatedTick() > simulationTick) {
                throw new IllegalArgumentException("Settlement is ahead of Stage-21G simulation tick: " + settlement.id());
            }
            maxSettlement = Math.max(maxSettlement, settlement.id());
        }
        if (nextSettlementId <= maxSettlement) {
            throw new IllegalArgumentException("nextSettlementId must be above every persisted settlement id");
        }

        Set<String> paymentKeys = new HashSet<>();
        for (PaymentObligation payment : payments) {
            requireSettlement(settlementIds, payment.settlementId(), "payment");
            String key = payment.settlementId() + ":" + payment.ordinal();
            if (!paymentKeys.add(key)) throw new IllegalArgumentException("Duplicate payment obligation: " + key);
            if (payment.completedTick() > simulationTick) {
                throw new IllegalArgumentException("Payment completion is ahead of Stage-21G simulation tick");
            }
        }

        Set<String> demobilizationKeys = new HashSet<>();
        for (DemobilizationDirective directive : demobilizations) {
            requireSettlement(settlementIds, directive.settlementId(), "demobilization");
            String key = directive.settlementId() + ":" + directive.commandGroupId();
            if (!demobilizationKeys.add(key)) {
                throw new IllegalArgumentException("Duplicate demobilization directive: " + key);
            }
            if (directive.updatedTick() > simulationTick) {
                throw new IllegalArgumentException("Demobilization is ahead of Stage-21G simulation tick");
            }
        }

        Set<FleetId> lostFleetIds = new HashSet<>();
        for (FleetLossRecord loss : losses) {
            requireSettlement(settlementIds, loss.settlementId(), "loss");
            if (!lostFleetIds.add(loss.lostFleetId())) {
                throw new IllegalArgumentException("FleetId loss recorded more than once: " + loss.lostFleetId());
            }
            if (loss.recordedTick() > simulationTick) {
                throw new IllegalArgumentException("Loss record is ahead of Stage-21G simulation tick");
            }
        }

        Set<Long> demandIds = new HashSet<>();
        Set<FleetId> demandedLosses = new HashSet<>();
        long maxDemand = 0L;
        for (ReplacementDemand demand : replacementDemands) {
            requireSettlement(settlementIds, demand.settlementId(), "replacement demand");
            if (!lostFleetIds.contains(demand.lostFleetId())) {
                throw new IllegalArgumentException("Replacement demand has no persisted physical loss: "
                        + demand.lostFleetId());
            }
            if (!demandIds.add(demand.id())) {
                throw new IllegalArgumentException("Duplicate replacement demand id: " + demand.id());
            }
            if (!demandedLosses.add(demand.lostFleetId())) {
                throw new IllegalArgumentException("Physical loss has multiple replacement demands: "
                        + demand.lostFleetId());
            }
            if (demand.updatedTick() > simulationTick) {
                throw new IllegalArgumentException("Replacement demand is ahead of Stage-21G simulation tick");
            }
            if (demand.commissionedFleetId() != null && lostFleetIds.contains(demand.commissionedFleetId())) {
                throw new IllegalArgumentException("Destroyed FleetId cannot be reused as a commissioned replacement");
            }
            maxDemand = Math.max(maxDemand, demand.id());
        }
        if (nextReplacementDemandId <= maxDemand) {
            throw new IllegalArgumentException(
                    "nextReplacementDemandId must be above every persisted replacement-demand id");
        }
    }

    /** @return canonical empty Stage-21G state at tick zero */
    public static SettlementRecoveryState empty() {
        return empty(0L);
    }

    /**
     * Creates an empty Stage-21G state at the supplied authoritative tick.
     *
     * @param simulationTick non-negative authoritative tick
     * @return canonical empty state
     */
    public static SettlementRecoveryState empty(long simulationTick) {
        return new SettlementRecoveryState(
                CURRENT_VERSION, simulationTick, 1L, 1L,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Finds a settlement by accepted Stage-21C proposal identity.
     *
     * @param proposalId proposal identity
     * @return matching settlement or null
     */
    public Settlement findSettlementByProposal(String proposalId) {
        if (proposalId == null) return null;
        for (Settlement settlement : settlements) {
            if (settlement.proposalId().equals(proposalId)) return settlement;
        }
        return null;
    }

    /**
     * Resolves one settlement or fails closed.
     *
     * @param settlementId positive settlement identifier
     * @return matching settlement
     */
    public Settlement requireSettlement(long settlementId) {
        for (Settlement settlement : settlements) {
            if (settlement.id() == settlementId) return settlement;
        }
        throw new IllegalArgumentException("Unknown Stage-21G settlement: " + settlementId);
    }

    /**
     * Resolves one replacement demand or fails closed.
     *
     * @param demandId positive replacement-demand identifier
     * @return matching replacement demand
     */
    public ReplacementDemand requireReplacementDemand(long demandId) {
        for (ReplacementDemand demand : replacementDemands) {
            if (demand.id() == demandId) return demand;
        }
        throw new IllegalArgumentException("Unknown Stage-21G replacement demand: " + demandId);
    }

    /** Settlement lifecycle status. */
    public enum SettlementStatus {
        /** Accepted legal settlement is still collecting its finite recovery plan. */ PENDING,
        /** Recovery plan is finalized and at least one obligation remains or has executed. */ EXECUTING,
        /** Every registered Stage-21G obligation has completed after explicit plan finalization. */ COMPLETE,
        /** Current physical/economic authority cannot satisfy one or more obligations. */ STALLED
    }

    /** Individual obligation lifecycle status. */
    public enum ObligationStatus {
        /** Obligation has not yet executed. */ PENDING,
        /** Current authority cannot execute the obligation yet. */ STALLED,
        /** Obligation completed exactly once. */ COMPLETE
    }

    /** Replacement-demand lifecycle status. */
    public enum ReplacementStatus {
        /** Demand exists but no physical shipyard settlement has completed. */ DEMANDED,
        /** Existing Stage-18 shipyard work settled and its ordinary built entity is persisted. */ YARD_SETTLED,
        /** The exact built ordinary entity has received a distinct replacement FleetId. */ COMMISSIONED,
        /** Demand was explicitly abandoned without restoring capability. */ CANCELLED
    }

    /**
     * Accepted ceasefire/peace settlement identity.
     *
     * @param id positive Stage-21G settlement identity
     * @param proposalId accepted Stage-21C proposal identity
     * @param warId legal Stage-21C war identity
     * @param factionA first legal participant
     * @param factionB second legal participant
     * @param openedTick tick when Stage 21G first consumed the accepted proposal
     * @param updatedTick latest reconciliation tick
     * @param status current recovery lifecycle
     * @param memoryRecorded whether post-war diplomatic performance memory was emitted
     */
    public record Settlement(
            long id,
            String proposalId,
            String warId,
            String factionA,
            String factionB,
            long openedTick,
            long updatedTick,
            SettlementStatus status,
            boolean memoryRecorded) {
        /**
         * Validates one settlement row.
         *
         * @param id settlement identity
         * @param proposalId proposal identity
         * @param warId war identity
         * @param factionA first faction
         * @param factionB second faction
         * @param openedTick opening tick
         * @param updatedTick update tick
         * @param status lifecycle status
         * @param memoryRecorded memory emission flag
         */
        public Settlement {
            if (id <= 0L) throw new IllegalArgumentException("settlement id must be positive");
            proposalId = requireText(proposalId, "proposalId");
            warId = requireText(warId, "warId");
            factionA = requireText(factionA, "factionA");
            factionB = requireText(factionB, "factionB");
            if (factionA.equals(factionB)) throw new IllegalArgumentException("settlement factions must differ");
            if (openedTick < 0L || updatedTick < openedTick) {
                throw new IllegalArgumentException("settlement ticks are invalid");
            }
            Objects.requireNonNull(status, "status");
            if (status != SettlementStatus.COMPLETE && memoryRecorded) {
                throw new IllegalArgumentException("post-war memory cannot be recorded before settlement completion");
            }
        }
    }

    /**
     * One real treasury-transfer obligation.
     *
     * @param settlementId owning settlement
     * @param ordinal deterministic zero-based proposal-term ordinal
     * @param payerFactionId faction whose ordinary treasury pays
     * @param recipientFactionId faction whose ordinary treasury receives
     * @param amountMilliCredits exact positive amount
     * @param status current execution status
     * @param completedTick completion tick; zero is also valid when completion occurs at simulation tick zero
     */
    public record PaymentObligation(
            long settlementId,
            int ordinal,
            String payerFactionId,
            String recipientFactionId,
            long amountMilliCredits,
            ObligationStatus status,
            long completedTick) {
        /**
         * Validates one payment obligation.
         *
         * @param settlementId owning settlement
         * @param ordinal term ordinal
         * @param payerFactionId payer faction
         * @param recipientFactionId recipient faction
         * @param amountMilliCredits exact amount
         * @param status lifecycle status
         * @param completedTick completion tick; pending/stalled obligations use zero
         */
        public PaymentObligation {
            if (settlementId <= 0L || ordinal < 0 || amountMilliCredits <= 0L) {
                throw new IllegalArgumentException("payment identity/ordinal/amount is invalid");
            }
            payerFactionId = requireText(payerFactionId, "payerFactionId");
            recipientFactionId = requireText(recipientFactionId, "recipientFactionId");
            if (payerFactionId.equals(recipientFactionId)) {
                throw new IllegalArgumentException("payment payer and recipient must differ");
            }
            Objects.requireNonNull(status, "status");
            if (completedTick < 0L) {
                throw new IllegalArgumentException("payment completion tick must be non-negative");
            }
            if (status != ObligationStatus.COMPLETE && completedTick != 0L) {
                throw new IllegalArgumentException("pending/stalled payment cannot carry a completion tick");
            }
        }
    }

    /**
     * One ordinary Stage-21D demobilization obligation.
     *
     * @param settlementId owning settlement
     * @param commandGroupId existing Stage-21D command-group identity
     * @param factionContentId stable owning faction identity
     * @param returnOrderId accepted Stage-21D RETURN order identity, or zero before submission
     * @param status current lifecycle status
     * @param updatedTick latest reconciliation tick
     */
    public record DemobilizationDirective(
            long settlementId,
            long commandGroupId,
            String factionContentId,
            long returnOrderId,
            ObligationStatus status,
            long updatedTick) {
        /**
         * Validates one demobilization directive.
         *
         * @param settlementId owning settlement
         * @param commandGroupId command-group identity
         * @param factionContentId stable faction identity
         * @param returnOrderId accepted return-order identity or zero
         * @param status lifecycle status
         * @param updatedTick latest update tick
         */
        public DemobilizationDirective {
            if (settlementId <= 0L || commandGroupId <= 0L || returnOrderId < 0L || updatedTick < 0L) {
                throw new IllegalArgumentException("demobilization identity/timing is invalid");
            }
            factionContentId = requireText(factionContentId, "factionContentId");
            Objects.requireNonNull(status, "status");
            if (status == ObligationStatus.COMPLETE && returnOrderId <= 0L) {
                throw new IllegalArgumentException("completed demobilization requires an accepted RETURN order");
            }
        }
    }

    /**
     * Immutable provenance for one physically destroyed ordinary fleet.
     *
     * @param settlementId settlement whose recovery accounts for the loss
     * @param operationId Stage-21E operation that produced the physical consequence report
     * @param lostFleetId exact ordinary FleetId that disappeared
     * @param factionContentId stable owner at loss time
     * @param recordedTick authoritative loss-recording tick
     */
    public record FleetLossRecord(
            long settlementId,
            long operationId,
            FleetId lostFleetId,
            String factionContentId,
            long recordedTick) {
        /**
         * Validates one physical loss record.
         *
         * @param settlementId owning settlement
         * @param operationId source operation identity
         * @param lostFleetId destroyed FleetId
         * @param factionContentId stable owner
         * @param recordedTick loss-recording tick
         */
        public FleetLossRecord {
            if (settlementId <= 0L || operationId <= 0L || recordedTick < 0L) {
                throw new IllegalArgumentException("loss identity/tick is invalid");
            }
            Objects.requireNonNull(lostFleetId, "lostFleetId");
            factionContentId = requireText(factionContentId, "factionContentId");
        }
    }

    /**
     * Demand to replace one persisted physical loss through ordinary Stage-18 shipyard work.
     *
     * @param id positive demand identity
     * @param settlementId owning peace/recovery settlement
     * @param lostFleetId exact destroyed FleetId; never reused for the replacement
     * @param factionContentId stable faction requesting replacement
     * @param targetFitFingerprint deterministic requested-fit fingerprint
     * @param createdTick demand creation tick
     * @param updatedTick latest lifecycle tick
     * @param status replacement lifecycle
     * @param completedAssetSystemId exact system containing the ordinary built entity, or null before settlement
     * @param completedAssetIdValue system-local built EntityId value, or zero before settlement
     * @param commissionedFleetId distinct ordinary commissioned FleetId, or null before commissioning
     */
    public record ReplacementDemand(
            long id,
            long settlementId,
            FleetId lostFleetId,
            String factionContentId,
            String targetFitFingerprint,
            long createdTick,
            long updatedTick,
            ReplacementStatus status,
            StarSystemId completedAssetSystemId,
            long completedAssetIdValue,
            FleetId commissionedFleetId) {
        /**
         * Validates one replacement demand.
         *
         * @param id demand identity
         * @param settlementId owning settlement
         * @param lostFleetId destroyed FleetId
         * @param factionContentId stable faction
         * @param targetFitFingerprint requested-fit fingerprint
         * @param createdTick creation tick
         * @param updatedTick latest update tick
         * @param status lifecycle status
         * @param completedAssetSystemId system containing the built entity or null
         * @param completedAssetIdValue completed EntityId value or zero
         * @param commissionedFleetId replacement FleetId or null
         */
        public ReplacementDemand {
            if (id <= 0L || settlementId <= 0L || createdTick < 0L || updatedTick < createdTick
                    || completedAssetIdValue < 0L) {
                throw new IllegalArgumentException("replacement demand identity/timing is invalid");
            }
            Objects.requireNonNull(lostFleetId, "lostFleetId");
            factionContentId = requireText(factionContentId, "factionContentId");
            targetFitFingerprint = requireText(targetFitFingerprint, "targetFitFingerprint");
            Objects.requireNonNull(status, "status");
            boolean assetRequired = status == ReplacementStatus.YARD_SETTLED
                    || status == ReplacementStatus.COMMISSIONED;
            boolean assetPresent = completedAssetSystemId != null && completedAssetIdValue > 0L;
            if (assetRequired != assetPresent) {
                throw new IllegalArgumentException(
                        "yard-settled/commissioned demand requires exact built system/entity identity");
            }
            if (!assetRequired && (completedAssetSystemId != null || completedAssetIdValue != 0L)) {
                throw new IllegalArgumentException("unsettled/cancelled demand cannot carry built asset identity");
            }
            if ((status == ReplacementStatus.COMMISSIONED) != (commissionedFleetId != null)) {
                throw new IllegalArgumentException("commissioned status and FleetId must appear together");
            }
        }
    }

    private static <T> List<T> canonical(List<T> values, Comparator<T> comparator) {
        Objects.requireNonNull(values, "values");
        ArrayList<T> copy = new ArrayList<>(values);
        if (copy.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Stage-21G lists cannot contain null");
        copy.sort(comparator);
        return List.copyOf(copy);
    }

    private static void requireSettlement(Set<Long> settlementIds, long settlementId, String label) {
        if (!settlementIds.contains(settlementId)) {
            throw new IllegalArgumentException("Stage-21G " + label + " references unknown settlement: " + settlementId);
        }
    }

    private static String requireText(String value, String label) {
        String result = Objects.requireNonNull(value, label).strip();
        if (result.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return result;
    }
}
