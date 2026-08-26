package com.spacesim.world;

import com.spacesim.world.DiplomaticLifecycleState.RelationEvent;
import com.spacesim.world.DiplomaticLifecycleState.RelationFactor;
import com.spacesim.world.DiplomaticLifecycleState.RelationMemory;
import com.spacesim.world.DiplomaticLifecycleState.War;
import com.spacesim.world.DiplomaticLifecycleState.WarStatus;
import com.spacesim.world.SettlementRecoveryState.FleetLossRecord;
import com.spacesim.world.SettlementRecoveryState.Settlement;
import com.spacesim.world.SettlementRecoveryState.SettlementStatus;

import java.util.Objects;

/**
 * Stage-21G bridge from completed physical settlement history into existing Stage-21C diplomatic memory.
 *
 * <p>The service does not own relations, war status or cooldowns. Stage 21C remains the authority for
 * those facts. This bridge first lets {@link SettlementRecoveryService} record its deterministic
 * treaty-performance memory, validates that the accepted ceasefire/peace still carries Stage-21C
 * hysteresis, and then records bounded grievances derived only from persisted Stage-21E FleetId losses.
 * Reconciliation is deterministic and idempotent: an already-present identical grievance is accepted,
 * while an event-ID collision with different semantics fails closed.</p>
 */
public final class Stage21GPostWarMemoryService {
    static final int GRIEVANCE_PER_LOST_FLEET = 8;
    static final int MAX_GRIEVANCE = 40;

    /**
     * Records all Stage-21G post-war memory for one completed settlement.
     *
     * @param recovery existing Stage-21G recovery coordinator
     * @param diplomacy existing Stage-21C diplomatic lifecycle authority
     * @param settlementId completed settlement identity
     * @param currentTick authoritative current tick
     * @return completed settlement after memory reconciliation
     */
    public Settlement recordPostWarMemory(
            SettlementRecoveryService recovery,
            DiplomaticLifecycleService diplomacy,
            long settlementId,
            long currentTick) {
        Objects.requireNonNull(recovery, "SettlementRecoveryService not set");
        Objects.requireNonNull(diplomacy, "DiplomaticLifecycleService not set");
        if (currentTick < 0L) {
            throw new IllegalArgumentException("Stage-21G post-war memory tick must be non-negative");
        }

        Settlement settlement = recovery.recordCompletionMemory(diplomacy, settlementId, currentTick);
        if (settlement.status() != SettlementStatus.COMPLETE) {
            throw new IllegalStateException("Post-war memory requires completed recovery obligations");
        }
        validatePeaceHysteresis(diplomacy, settlement);

        var recoveryState = recovery.snapshot();
        recordLossGrievance(
                diplomacy,
                settlement,
                settlement.factionA(),
                settlement.factionB(),
                recoveryState.losses().stream()
                        .filter(loss -> loss.settlementId() == settlementId
                                && loss.factionContentId().equals(settlement.factionA()))
                        .count(),
                currentTick);
        recordLossGrievance(
                diplomacy,
                settlement,
                settlement.factionB(),
                settlement.factionA(),
                recoveryState.losses().stream()
                        .filter(loss -> loss.settlementId() == settlementId
                                && loss.factionContentId().equals(settlement.factionB()))
                        .count(),
                currentTick);
        return recovery.snapshot().requireSettlement(settlementId);
    }

    private static void validatePeaceHysteresis(
            DiplomaticLifecycleService diplomacy,
            Settlement settlement) {
        War war = diplomacy.snapshot().wars().stream()
                .filter(candidate -> candidate.warId().equals(settlement.warId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Post-war settlement no longer references a Stage-21C war"));
        if (war.status() == WarStatus.ACTIVE) {
            throw new IllegalStateException("Post-war memory cannot reconcile while war remains active");
        }
        if (war.reEscalationCooldownUntilTick() <= war.statusChangedTick()) {
            throw new IllegalStateException("Stage-21C peace hysteresis is missing from completed settlement");
        }
    }

    private static void recordLossGrievance(
            DiplomaticLifecycleService diplomacy,
            Settlement settlement,
            String owner,
            String target,
            long lostFleetCount,
            long currentTick) {
        if (lostFleetCount <= 0L) return;
        long rawImpact = Math.multiplyExact(lostFleetCount, (long) GRIEVANCE_PER_LOST_FLEET);
        int impact = -(int) Math.min(MAX_GRIEVANCE, rawImpact);
        RelationEvent expected = new RelationEvent(
                "stage21g.settlement." + settlement.id() + ".loss-grievance." + owner,
                RelationFactor.REMEMBERED_ACTION,
                impact,
                currentTick,
                settlement.warId());

        RelationEvent existing = diplomacy.snapshot().relationMemories().stream()
                .filter(memory -> memory.ownerFactionId().equals(owner)
                        && memory.targetFactionId().equals(target))
                .flatMap(memory -> memory.events().stream())
                .filter(event -> event.eventId().equals(expected.eventId()))
                .findFirst()
                .orElse(null);
        if (existing == null) {
            diplomacy.remember(owner, target, expected);
            return;
        }
        if (existing.factor() != expected.factor()
                || existing.impact() != expected.impact()
                || !existing.subjectId().equals(expected.subjectId())) {
            throw new IllegalStateException("Conflicting Stage-21G grievance memory: " + expected.eventId());
        }
    }
}
