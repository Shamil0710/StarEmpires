package com.spacesim.persistence;

import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.world.DiplomaticLifecycleState.Proposal;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.ProposalStatus;
import com.spacesim.world.DiplomaticLifecycleState.Term;
import com.spacesim.world.DiplomaticLifecycleState.TermKind;
import com.spacesim.world.DiplomaticLifecycleState.War;
import com.spacesim.world.DiplomaticLifecycleState.WarStatus;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.SettlementRecoveryState;
import com.spacesim.world.SettlementRecoveryState.DemobilizationDirective;
import com.spacesim.world.SettlementRecoveryState.FleetLossRecord;
import com.spacesim.world.SettlementRecoveryState.PaymentObligation;
import com.spacesim.world.SettlementRecoveryState.ReplacementDemand;
import com.spacesim.world.SettlementRecoveryState.ReplacementStatus;
import com.spacesim.world.SettlementRecoveryState.Settlement;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.WorldState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Atomic Stage-21G generated-world checkpoint composition.
 *
 * <p>The complete accepted Stage-21F runtime is embedded unchanged. Stage 21G adds only peace and
 * recovery obligation/provenance metadata. Legal war state, treasury, fleets, damage, consumables,
 * shipyard inventory/work and territory remain persisted by their existing authorities.</p>
 *
 * @param schemaVersion exact Stage-21G checkpoint schema version
 * @param runtimeVersion exact Stage-21G runtime contract identifier
 * @param stage21FRuntime complete embedded Stage-21F checkpoint
 * @param settlementRecovery Stage-21G settlement/recovery metadata
 */
public record Stage21GGeneratedWorldRuntimePersistentState(
        int schemaVersion,
        String runtimeVersion,
        Stage21FGeneratedWorldRuntimePersistentState stage21FRuntime,
        SettlementRecoveryState settlementRecovery) {

    /** Current Stage-21G checkpoint schema. */
    public static final int CURRENT_VERSION = 10;
    /** Current Stage-21G runtime contract identifier. */
    public static final String CURRENT_RUNTIME_VERSION = "stage21g.generated-world-peace-recovery.v10";

    /**
     * Cross-validates every recovery reference against embedded diplomatic, fleet, operation and world authority.
     *
     * @param schemaVersion exact supported schema
     * @param runtimeVersion exact supported runtime contract
     * @param stage21FRuntime complete accepted Stage-21F checkpoint
     * @param settlementRecovery Stage-21G recovery metadata
     */
    public Stage21GGeneratedWorldRuntimePersistentState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-21G checkpoint schema: " + schemaVersion);
        }
        runtimeVersion = Objects.requireNonNull(runtimeVersion, "runtimeVersion").strip();
        if (!CURRENT_RUNTIME_VERSION.equals(runtimeVersion)) {
            throw new IllegalArgumentException("Unsupported Stage-21G runtime version: " + runtimeVersion);
        }
        Objects.requireNonNull(stage21FRuntime, "stage21FRuntime");
        Objects.requireNonNull(settlementRecovery, "settlementRecovery");

        Stage21EGeneratedWorldRuntimePersistentState stage21E = stage21FRuntime.stage21ERuntime();
        Stage21DGeneratedWorldRuntimePersistentState stage21D = stage21E.stage21DRuntime();
        Stage21CGeneratedWorldRuntimePersistentState stage21C = stage21D.stage21CRuntime();
        Stage20GeneratedWorldRuntimePersistentState stage20 = stage21C.stage21BRuntime()
                .stage21ARuntime().stage20Runtime();
        WorldState world = stage20.worldState();
        long authoritativeWorldTick = world.systems().stream()
                .filter(system -> system.systemId().equals(stage20.activeSystemId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Stage-21G checkpoint active system is absent from saved world state"))
                .simulationState().clock().tick();
        if (settlementRecovery.simulationTick() > authoritativeWorldTick) {
            throw new IllegalArgumentException("Stage-21G recovery state is ahead of authoritative world time");
        }

        Map<String, Proposal> proposals = new HashMap<>();
        stage21C.diplomacyLifecycle().proposals().forEach(value -> proposals.put(value.proposalId(), value));
        Map<String, War> wars = new HashMap<>();
        stage21C.diplomacyLifecycle().wars().forEach(value -> wars.put(value.warId(), value));
        Map<Long, CommandGroupState> groups = new HashMap<>();
        stage21D.fleetCommandState().groups().forEach(value -> groups.put(value.id(), value));
        Map<Long, FleetOrderState> orders = new HashMap<>();
        stage21D.fleetCommandState().orders().forEach(value -> orders.put(value.id(), value));
        Map<Long, OperationState> operations = new HashMap<>();
        stage21E.operationState().operations().forEach(value -> operations.put(value.id(), value));
        Set<FleetId> ordinaryFleets = new HashSet<>();
        for (FleetPlacementState placement : world.fleets()) ordinaryFleets.add(placement.id());
        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(
                ContentCatalogLoader.loadDefault(), world.factionIdentities());

        Map<Long, Settlement> settlements = new HashMap<>();
        for (Settlement settlement : settlementRecovery.settlements()) {
            settlements.put(settlement.id(), settlement);
            Proposal proposal = proposals.get(settlement.proposalId());
            if (proposal == null || proposal.status() != ProposalStatus.ACCEPTED
                    || proposal.kind() != ProposalKind.CEASEFIRE && proposal.kind() != ProposalKind.PEACE) {
                throw new IllegalArgumentException(
                        "Stage-21G settlement lacks accepted Stage-21C ceasefire/peace proposal: " + settlement.id());
            }
            War war = wars.get(settlement.warId());
            if (war == null || !proposal.issueId().equals(war.warId())) {
                throw new IllegalArgumentException("Stage-21G settlement references incompatible legal war: " + settlement.id());
            }
            if (!samePair(settlement.factionA(), settlement.factionB(), war.factionA(), war.factionB())) {
                throw new IllegalArgumentException("Stage-21G settlement participant pair differs from legal war");
            }
            WarStatus expected = proposal.kind() == ProposalKind.PEACE ? WarStatus.PEACE : WarStatus.CEASEFIRE;
            if (war.status() != expected) {
                throw new IllegalArgumentException("Stage-21G settlement legal war status differs from accepted proposal");
            }
        }

        for (PaymentObligation payment : settlementRecovery.payments()) {
            Settlement settlement = settlements.get(payment.settlementId());
            Proposal proposal = proposals.get(settlement.proposalId());
            List<ExpectedPayment> expected = expectedPayments(proposal);
            boolean matched = expected.stream().anyMatch(value -> value.ordinal == payment.ordinal()
                    && value.payer.equals(payment.payerFactionId())
                    && value.recipient.equals(payment.recipientFactionId())
                    && value.amount == payment.amountMilliCredits());
            if (!matched) {
                throw new IllegalArgumentException("Stage-21G payment differs from accepted proposal terms");
            }
        }

        for (DemobilizationDirective directive : settlementRecovery.demobilizations()) {
            Settlement settlement = settlements.get(directive.settlementId());
            if (!directive.factionContentId().equals(settlement.factionA())
                    && !directive.factionContentId().equals(settlement.factionB())) {
                throw new IllegalArgumentException("Stage-21G demobilization faction is not a settlement participant");
            }
            CommandGroupState group = groups.get(directive.commandGroupId());
            if (group == null) {
                throw new IllegalArgumentException("Stage-21G demobilization references missing surviving command group");
            }
            String owner = identities.stableId(group.factionId()).orElseThrow(
                    () -> new IllegalArgumentException("Stage-21G command group has unknown stable faction"));
            if (!owner.equals(directive.factionContentId())) {
                throw new IllegalArgumentException("Stage-21G demobilization owner differs from command group");
            }
            if (directive.returnOrderId() > 0L) {
                FleetOrderState order = orders.get(directive.returnOrderId());
                if (order == null || order.commandGroupId() != directive.commandGroupId()
                        || order.type() != OrderType.RETURN) {
                    throw new IllegalArgumentException("Stage-21G demobilization references incompatible RETURN order");
                }
            }
        }

        for (FleetLossRecord loss : settlementRecovery.losses()) {
            Settlement settlement = settlements.get(loss.settlementId());
            if (!loss.factionContentId().equals(settlement.factionA())
                    && !loss.factionContentId().equals(settlement.factionB())) {
                throw new IllegalArgumentException("Stage-21G loss owner is not a settlement participant");
            }
            OperationState operation = operations.get(loss.operationId());
            if (operation == null) {
                throw new IllegalArgumentException("Stage-21G loss references missing Stage-21E operation");
            }
            boolean referenced = operation.participantFleetIds().contains(loss.lostFleetId())
                    || operation.contact() != null && operation.contact().targetFleetId().equals(loss.lostFleetId());
            if (!referenced) {
                throw new IllegalArgumentException("Stage-21G loss FleetId was not bounded by its Stage-21E operation");
            }
            if (ordinaryFleets.contains(loss.lostFleetId())) {
                throw new IllegalArgumentException("Stage-21G persisted loss still exists in ordinary world: "
                        + loss.lostFleetId());
            }
        }

        for (ReplacementDemand demand : settlementRecovery.replacementDemands()) {
            if (!identities.containsStableId(demand.factionContentId())) {
                throw new IllegalArgumentException("Stage-21G replacement references unknown faction");
            }
            if (demand.status() == ReplacementStatus.COMMISSIONED
                    && !ordinaryFleets.contains(demand.commissionedFleetId())) {
                throw new IllegalArgumentException("Stage-21G commissioned replacement FleetId is absent from ordinary world");
            }
        }
    }

    /**
     * Composes a current Stage-21G checkpoint.
     *
     * @param stage21F complete accepted Stage-21F checkpoint
     * @param recovery Stage-21G recovery metadata
     * @return validated current-version wrapper
     */
    public static Stage21GGeneratedWorldRuntimePersistentState compose(
            Stage21FGeneratedWorldRuntimePersistentState stage21F,
            SettlementRecoveryState recovery) {
        return new Stage21GGeneratedWorldRuntimePersistentState(
                CURRENT_VERSION, CURRENT_RUNTIME_VERSION, stage21F, recovery);
    }

    private static boolean samePair(String leftA, String leftB, String rightA, String rightB) {
        return leftA.equals(rightA) && leftB.equals(rightB) || leftA.equals(rightB) && leftB.equals(rightA);
    }

    private static List<ExpectedPayment> expectedPayments(Proposal proposal) {
        ArrayList<ExpectedPayment> result = new ArrayList<>();
        int ordinal = 0;
        for (Term term : proposal.concessions()) {
            if (term.kind() == TermKind.TREASURY_PAYMENT) {
                result.add(new ExpectedPayment(ordinal, proposal.proposerFactionId(),
                        proposal.recipientFactionId(), term.amountMilliCredits()));
            }
            ordinal++;
        }
        for (Term term : proposal.demands()) {
            if (term.kind() == TermKind.TREASURY_PAYMENT) {
                result.add(new ExpectedPayment(ordinal, proposal.recipientFactionId(),
                        proposal.proposerFactionId(), term.amountMilliCredits()));
            }
            ordinal++;
        }
        return List.copyOf(result);
    }

    private record ExpectedPayment(int ordinal, String payer, String recipient, long amount) { }
}
