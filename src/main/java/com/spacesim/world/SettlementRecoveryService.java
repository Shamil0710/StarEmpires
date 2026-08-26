package com.spacesim.world;

import com.spacesim.components.WalletComponent;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.world.DiplomaticLifecycleState.Proposal;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.ProposalStatus;
import com.spacesim.world.DiplomaticLifecycleState.RelationEvent;
import com.spacesim.world.DiplomaticLifecycleState.RelationFactor;
import com.spacesim.world.DiplomaticLifecycleState.Term;
import com.spacesim.world.DiplomaticLifecycleState.TermKind;
import com.spacesim.world.DiplomaticLifecycleState.War;
import com.spacesim.world.DiplomaticLifecycleState.WarStatus;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderStatus;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.SettlementRecoveryState.DemobilizationDirective;
import com.spacesim.world.SettlementRecoveryState.FleetLossRecord;
import com.spacesim.world.SettlementRecoveryState.ObligationStatus;
import com.spacesim.world.SettlementRecoveryState.PaymentObligation;
import com.spacesim.world.SettlementRecoveryState.ReplacementDemand;
import com.spacesim.world.SettlementRecoveryState.ReplacementStatus;
import com.spacesim.world.SettlementRecoveryState.Settlement;
import com.spacesim.world.SettlementRecoveryState.SettlementStatus;
import com.spacesim.world.Stage21EPhysicalConsequenceService.ConsequenceReport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Stage-21G deterministic orchestration over accepted diplomacy, real treasury, ordinary fleets and losses.
 *
 * <p>The service never creates peace, money, FleetIds, repaired engineering state or shipyard output.
 * Peace must already be accepted by Stage 21C. Reparations move through {@link WorldSimulation}'s
 * treasury transfer boundaries. Demobilization submits ordinary Stage-21D {@code RETURN} orders.
 * Losses must come from a Stage-21E physical consequence report, and replacement rows are only demand
 * metadata until the existing Stage-18/17.5 shipyard boundary proves physical build completion.</p>
 */
public final class SettlementRecoveryService {
    private SettlementRecoveryState state;

    public SettlementRecoveryService(SettlementRecoveryState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public SettlementRecoveryState snapshot() {
        return state;
    }

    /** Opens recovery for one already-accepted Stage-21C ceasefire or peace proposal. */
    public Settlement openAcceptedSettlement(
            DiplomaticLifecycleService diplomacy,
            String proposalId,
            long currentTick) {
        Objects.requireNonNull(diplomacy, "diplomacy");
        requireTick(currentTick);
        Settlement existing = state.findSettlementByProposal(proposalId);
        if (existing != null) return existing;

        DiplomaticLifecycleState diplomatic = diplomacy.snapshot();
        Proposal proposal = diplomatic.proposals().stream()
                .filter(value -> value.proposalId().equals(proposalId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Stage-21C proposal: " + proposalId));
        if (proposal.status() != ProposalStatus.ACCEPTED
                || proposal.kind() != ProposalKind.CEASEFIRE && proposal.kind() != ProposalKind.PEACE) {
            throw new IllegalStateException("Stage-21G requires an accepted ceasefire/peace proposal");
        }
        War war = diplomatic.wars().stream()
                .filter(value -> value.warId().equals(proposal.issueId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Accepted peace proposal does not reference an existing legal war: " + proposal.issueId()));
        WarStatus requiredStatus = proposal.kind() == ProposalKind.PEACE ? WarStatus.PEACE : WarStatus.CEASEFIRE;
        if (war.status() != requiredStatus) {
            throw new IllegalStateException("Legal war status does not match accepted settlement proposal");
        }
        if (currentTick < proposal.updatedTick() || currentTick < war.statusChangedTick()) {
            throw new IllegalArgumentException("Stage-21G settlement cannot predate accepted diplomatic state");
        }

        long settlementId = state.nextSettlementId();
        Settlement settlement = new Settlement(
                settlementId,
                proposal.proposalId(),
                war.warId(),
                war.factionA(),
                war.factionB(),
                currentTick,
                currentTick,
                SettlementStatus.PENDING,
                false);
        ArrayList<Settlement> settlements = new ArrayList<>(state.settlements());
        settlements.add(settlement);
        ArrayList<PaymentObligation> payments = new ArrayList<>(state.payments());
        int ordinal = addPayments(payments, settlementId, proposal.proposerFactionId(),
                proposal.recipientFactionId(), proposal.concessions(), 0);
        addPayments(payments, settlementId, proposal.recipientFactionId(),
                proposal.proposerFactionId(), proposal.demands(), ordinal);
        state = rebuild(currentTick, Math.addExact(settlementId, 1L), state.nextReplacementDemandId(),
                settlements, payments, state.demobilizations(), state.losses(), state.replacementDemands());
        return state.requireSettlement(settlementId);
    }

    /**
     * Closes the finite recovery-plan authoring window.
     *
     * <p>Before this transition payment terms may be accompanied by demobilization directives,
     * physical-loss records and replacement demands. Once finalized, no new recovery obligations may
     * be appended; execution can therefore use ordinary all-complete checks without the empty-stream
     * completion bug. An intentionally empty plan completes only through this explicit transition.</p>
     */
    public Settlement finalizeRecoveryPlan(long settlementId, long currentTick) {
        requireTick(currentTick);
        Settlement current = state.requireSettlement(settlementId);
        if (current.status() != SettlementStatus.PENDING) {
            return current;
        }
        Settlement finalized = new Settlement(
                current.id(), current.proposalId(), current.warId(), current.factionA(), current.factionB(),
                current.openedTick(), currentTick, SettlementStatus.EXECUTING, current.memoryRecorded());
        replaceSettlement(finalized, currentTick);
        refreshSettlementStatus(settlementId, currentTick);
        return state.requireSettlement(settlementId);
    }

    /** Executes every still-due treasury payment exactly once through ordinary faction treasuries. */
    public SettlementRecoveryState executePayments(
            WorldSimulation world,
            long settlementId,
            long currentTick) {
        Objects.requireNonNull(world, "world");
        requireTick(currentTick);
        requireFinalizedSettlement(settlementId);
        ArrayList<PaymentObligation> next = new ArrayList<>(state.payments().size());
        for (PaymentObligation payment : state.payments()) {
            if (payment.settlementId() != settlementId || payment.status() == ObligationStatus.COMPLETE) {
                next.add(payment);
                continue;
            }
            FactionEconomicState payer = world.findFactionEconomicState(payment.payerFactionId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Payment payer lacks treasury authority: " + payment.payerFactionId()));
            world.findFactionEconomicState(payment.recipientFactionId()).orElseThrow(
                    () -> new IllegalArgumentException(
                            "Payment recipient lacks treasury authority: " + payment.recipientFactionId()));
            long spendable = Math.max(0L,
                    payer.treasuryMilliCredits() - payer.treasuryReserveFloorMilliCredits());
            if (spendable < payment.amountMilliCredits()) {
                next.add(new PaymentObligation(
                        payment.settlementId(), payment.ordinal(), payment.payerFactionId(),
                        payment.recipientFactionId(), payment.amountMilliCredits(),
                        ObligationStatus.STALLED, 0L));
                continue;
            }
            WalletComponent clearing = new WalletComponent();
            String transferId = "stage21g.settlement." + settlementId + ".payment." + payment.ordinal();
            boolean debited = world.transferFromFactionTreasury(
                    payment.payerFactionId(), clearing, transferId + ".clearing",
                    payment.amountMilliCredits(), transferId + ".debit");
            if (!debited) {
                next.add(new PaymentObligation(
                        payment.settlementId(), payment.ordinal(), payment.payerFactionId(),
                        payment.recipientFactionId(), payment.amountMilliCredits(),
                        ObligationStatus.STALLED, 0L));
                continue;
            }
            boolean credited = world.transferToFactionTreasury(
                    payment.recipientFactionId(), clearing, transferId + ".clearing",
                    payment.amountMilliCredits(), transferId + ".credit");
            if (!credited) {
                boolean rolledBack = world.transferToFactionTreasury(
                        payment.payerFactionId(), clearing, transferId + ".rollback",
                        payment.amountMilliCredits(), transferId + ".rollback");
                if (!rolledBack || clearing.getBalanceMilliCredits() != 0L) {
                    throw new IllegalStateException("Stage-21G treasury rollback failed; refusing divergent state");
                }
                next.add(new PaymentObligation(
                        payment.settlementId(), payment.ordinal(), payment.payerFactionId(),
                        payment.recipientFactionId(), payment.amountMilliCredits(),
                        ObligationStatus.STALLED, 0L));
                continue;
            }
            if (clearing.getBalanceMilliCredits() != 0L) {
                throw new IllegalStateException("Stage-21G clearing wallet must be empty after conserved transfer");
            }
            next.add(new PaymentObligation(
                    payment.settlementId(), payment.ordinal(), payment.payerFactionId(),
                    payment.recipientFactionId(), payment.amountMilliCredits(),
                    ObligationStatus.COMPLETE, currentTick));
        }
        state = rebuild(currentTick, state.nextSettlementId(), state.nextReplacementDemandId(),
                state.settlements(), next, state.demobilizations(), state.losses(), state.replacementDemands());
        return refreshSettlementStatus(settlementId, currentTick);
    }

    /** Registers one surviving command group while the finite recovery plan is still open. */
    public DemobilizationDirective registerDemobilization(
            long settlementId,
            long commandGroupId,
            String factionContentId,
            long currentTick) {
        Settlement settlement = requirePlanningSettlement(settlementId);
        String faction = requireText(factionContentId, "factionContentId");
        if (!faction.equals(settlement.factionA()) && !faction.equals(settlement.factionB())) {
            throw new IllegalArgumentException("Demobilized group faction is not a settlement participant");
        }
        requireTick(currentTick);
        for (DemobilizationDirective existing : state.demobilizations()) {
            if (existing.settlementId() == settlementId && existing.commandGroupId() == commandGroupId) {
                if (!existing.factionContentId().equals(faction)) {
                    throw new IllegalStateException("Command group demobilization owner changed");
                }
                return existing;
            }
        }
        DemobilizationDirective created = new DemobilizationDirective(
                settlementId, commandGroupId, faction, 0L, ObligationStatus.PENDING, currentTick);
        ArrayList<DemobilizationDirective> directives = new ArrayList<>(state.demobilizations());
        directives.add(created);
        state = rebuild(currentTick, state.nextSettlementId(), state.nextReplacementDemandId(),
                state.settlements(), state.payments(), directives, state.losses(), state.replacementDemands());
        return created;
    }

    /** Cancels a remaining active war commitment and submits an ordinary Stage-21D RETURN order. */
    public DemobilizationResult submitReturnOrder(
            FleetCommandState commandState,
            FleetForceRegistry forces,
            FactionIdentityResolver identities,
            FleetOrderSubmissionService submission,
            long settlementId,
            long commandGroupId,
            OrderSource source,
            long currentTick,
            FleetStrategicRoutePlanner.TransitAccessPolicy accessPolicy,
            FleetOrderSubmissionService.ServiceCapabilityPolicy servicePolicy,
            FleetOrderSubmissionService.StrategicRiskPolicy riskPolicy) {
        Objects.requireNonNull(commandState, "commandState");
        Objects.requireNonNull(forces, "forces");
        Objects.requireNonNull(identities, "identities");
        Objects.requireNonNull(submission, "submission");
        Objects.requireNonNull(source, "source");
        requireFinalizedSettlement(settlementId);
        DemobilizationDirective directive = findDirective(settlementId, commandGroupId);
        String actualOwner = stableFaction(commandState.requireGroup(commandGroupId), identities);
        if (!directive.factionContentId().equals(actualOwner)) {
            throw new IllegalStateException("Demobilization directive no longer matches command-group owner");
        }
        if (directive.status() == ObligationStatus.COMPLETE) {
            return new DemobilizationResult(commandState, state,
                    commandState.requireOrder(directive.returnOrderId()));
        }

        FleetCommandState nextCommand = commandState;
        var active = nextCommand.activeOrderFor(commandGroupId);
        if (active.isPresent()) {
            FleetOrderState current = active.orElseThrow();
            if (current.type() == OrderType.RETURN) {
                DemobilizationDirective completed = new DemobilizationDirective(
                        settlementId, commandGroupId, directive.factionContentId(), current.id(),
                        ObligationStatus.COMPLETE, currentTick);
                replaceDirective(completed, currentTick);
                refreshSettlementStatus(settlementId, currentTick);
                return new DemobilizationResult(nextCommand, state, current);
            }
            nextCommand = nextCommand.replaceOrder(current.withStatus(OrderStatus.CANCELLED));
        }
        CommandGroupState group = nextCommand.requireGroup(commandGroupId);
        FleetOrderSubmissionService.SubmissionResult accepted = submission.submit(
                nextCommand, forces, commandGroupId, OrderType.RETURN, source, group.homeSystemId(),
                currentTick, accessPolicy, servicePolicy, riskPolicy);
        DemobilizationDirective completed = new DemobilizationDirective(
                settlementId, commandGroupId, directive.factionContentId(), accepted.order().id(),
                ObligationStatus.COMPLETE, currentTick);
        replaceDirective(completed, currentTick);
        refreshSettlementStatus(settlementId, currentTick);
        return new DemobilizationResult(accepted.state(), state, accepted.order());
    }

    /** Records only real FleetId losses reported by Stage-21E while the recovery plan is open. */
    public SettlementRecoveryState recordPhysicalLosses(
            long settlementId,
            long operationId,
            ConsequenceReport report,
            FleetForceRegistry before,
            FactionIdentityResolver identities,
            long currentTick) {
        Settlement settlement = requirePlanningSettlement(settlementId);
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(identities, "identities");
        if (report.operationId() != operationId) {
            throw new IllegalArgumentException("Consequence report operation identity mismatch");
        }
        requireTick(currentTick);
        ArrayList<FleetLossRecord> losses = new ArrayList<>(state.losses());
        for (FleetId fleetId : report.losses()) {
            FleetForceRegistry.Entry previous = before.find(fleetId)
                    .orElseThrow(() -> new IllegalStateException("Lost FleetId missing from before registry: " + fleetId));
            String owner = identities.stableId(previous.factionId())
                    .orElseThrow(() -> new IllegalStateException("Lost fleet has unknown stable faction: " + fleetId));
            if (!owner.equals(settlement.factionA()) && !owner.equals(settlement.factionB())) {
                throw new IllegalStateException("Physical loss owner is not a settlement participant: " + owner);
            }
            FleetLossRecord existing = losses.stream()
                    .filter(row -> row.lostFleetId().equals(fleetId))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                if (existing.settlementId() != settlementId
                        || existing.operationId() != operationId
                        || !existing.factionContentId().equals(owner)) {
                    throw new IllegalStateException(
                            "Physical loss replay does not match persisted provenance: " + fleetId);
                }
                continue;
            }
            losses.add(new FleetLossRecord(settlementId, operationId, fleetId, owner, currentTick));
        }
        state = rebuild(currentTick, state.nextSettlementId(), state.nextReplacementDemandId(),
                state.settlements(), state.payments(), state.demobilizations(), losses, state.replacementDemands());
        return state;
    }

    /** Creates one replacement demand for a persisted physical loss while planning is open. */
    public ReplacementDemand requestReplacement(
            long settlementId,
            FleetId lostFleetId,
            InstalledFit targetFit,
            long currentTick) {
        requirePlanningSettlement(settlementId);
        Objects.requireNonNull(lostFleetId, "lostFleetId");
        Objects.requireNonNull(targetFit, "targetFit");
        requireTick(currentTick);
        FleetLossRecord loss = state.losses().stream()
                .filter(row -> row.settlementId() == settlementId && row.lostFleetId().equals(lostFleetId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Replacement demand requires a persisted physical FleetId loss"));
        String targetFitFingerprint = fitFingerprint(targetFit);
        for (ReplacementDemand existing : state.replacementDemands()) {
            if (!existing.lostFleetId().equals(lostFleetId)) continue;
            if (existing.settlementId() != settlementId
                    || !existing.factionContentId().equals(loss.factionContentId())
                    || !existing.targetFitFingerprint().equals(targetFitFingerprint)) {
                throw new IllegalStateException(
                        "Replacement demand replay does not match persisted loss/fit provenance: " + lostFleetId);
            }
            return existing;
        }
        long id = state.nextReplacementDemandId();
        ReplacementDemand demand = new ReplacementDemand(
                id, settlementId, lostFleetId, loss.factionContentId(), targetFitFingerprint,
                currentTick, currentTick, ReplacementStatus.DEMANDED, null, 0L, null);
        ArrayList<ReplacementDemand> demands = new ArrayList<>(state.replacementDemands());
        demands.add(demand);
        state = rebuild(currentTick, state.nextSettlementId(), Math.addExact(id, 1L),
                state.settlements(), state.payments(), state.demobilizations(), state.losses(), demands);
        return state.requireReplacementDemand(id);
    }

    /** Internal proof transition used only after Stage-18 settlement and ordinary Entity materialization. */
    ReplacementDemand markYardSettled(
            long demandId,
            StarSystemId completedAssetSystemId,
            long completedAssetIdValue,
            long currentTick) {
        ReplacementDemand current = state.requireReplacementDemand(demandId);
        Objects.requireNonNull(completedAssetSystemId, "completedAssetSystemId");
        if (completedAssetIdValue <= 0L) {
            throw new IllegalArgumentException("completedAssetIdValue must be positive");
        }
        requireTick(currentTick);
        requireFinalizedSettlement(current.settlementId());
        if (current.status() == ReplacementStatus.COMMISSIONED) return current;
        if (current.status() == ReplacementStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled replacement demand cannot settle");
        }
        if (current.status() == ReplacementStatus.YARD_SETTLED) {
            if (!current.completedAssetSystemId().equals(completedAssetSystemId)
                    || current.completedAssetIdValue() != completedAssetIdValue) {
                throw new IllegalStateException("Replacement demand already settled to another physical asset");
            }
            return current;
        }
        ReplacementDemand updated = new ReplacementDemand(
                current.id(), current.settlementId(), current.lostFleetId(), current.factionContentId(),
                current.targetFitFingerprint(), current.createdTick(), currentTick,
                ReplacementStatus.YARD_SETTLED, completedAssetSystemId, completedAssetIdValue, null);
        replaceDemand(updated, currentTick);
        return updated;
    }

    /** Internal proof transition used only after exact ordinary entity-to-FleetId registration. */
    ReplacementDemand markCommissioned(
            long demandId,
            FleetId commissionedFleetId,
            long currentTick) {
        ReplacementDemand current = state.requireReplacementDemand(demandId);
        Objects.requireNonNull(commissionedFleetId, "commissionedFleetId");
        requireTick(currentTick);
        requireFinalizedSettlement(current.settlementId());
        if (current.status() == ReplacementStatus.COMMISSIONED) {
            if (!current.commissionedFleetId().equals(commissionedFleetId)) {
                throw new IllegalStateException("Replacement demand already commissioned as another FleetId");
            }
            return current;
        }
        if (current.status() != ReplacementStatus.YARD_SETTLED) {
            throw new IllegalStateException("Replacement cannot commission before physical shipyard settlement");
        }
        if (current.lostFleetId().equals(commissionedFleetId)) {
            throw new IllegalStateException("Replacement cannot reuse the destroyed FleetId");
        }
        ReplacementDemand updated = new ReplacementDemand(
                current.id(), current.settlementId(), current.lostFleetId(), current.factionContentId(),
                current.targetFitFingerprint(), current.createdTick(), currentTick,
                ReplacementStatus.COMMISSIONED, current.completedAssetSystemId(),
                current.completedAssetIdValue(), commissionedFleetId);
        replaceDemand(updated, currentTick);
        refreshSettlementStatus(current.settlementId(), currentTick);
        return updated;
    }

    /** Emits deterministic bilateral treaty-performance memory once settlement obligations are complete. */
    public Settlement recordCompletionMemory(
            DiplomaticLifecycleService diplomacy,
            long settlementId,
            long currentTick) {
        Objects.requireNonNull(diplomacy, "diplomacy");
        requireTick(currentTick);
        refreshSettlementStatus(settlementId, currentTick);
        Settlement current = state.requireSettlement(settlementId);
        if (current.status() != SettlementStatus.COMPLETE) {
            throw new IllegalStateException("Settlement memory requires completed recovery obligations");
        }
        if (current.memoryRecorded()) return current;
        String eventId = "stage21g.settlement." + settlementId + ".honored";
        diplomacy.remember(current.factionA(), current.factionB(), new RelationEvent(
                eventId + ".a", RelationFactor.TREATY_PERFORMANCE, 12, currentTick, current.warId()));
        diplomacy.remember(current.factionB(), current.factionA(), new RelationEvent(
                eventId + ".b", RelationFactor.TREATY_PERFORMANCE, 12, currentTick, current.warId()));
        Settlement updated = new Settlement(
                current.id(), current.proposalId(), current.warId(), current.factionA(), current.factionB(),
                current.openedTick(), currentTick, current.status(), true);
        replaceSettlement(updated, currentTick);
        return updated;
    }

    /** Deterministically fingerprints hull + sorted mount/module assignments. */
    public static String fitFingerprint(InstalledFit fit) {
        InstalledFit checked = Objects.requireNonNull(fit, "fit");
        StringBuilder canonical = new StringBuilder(checked.hullId()).append('\n');
        checked.installedModules().stream()
                .sorted(java.util.Comparator.comparing(
                        com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition::mountId)
                        .thenComparing(
                                com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition::moduleId))
                .forEach(module -> canonical.append(module.mountId()).append('=')
                        .append(module.moduleId()).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private SettlementRecoveryState refreshSettlementStatus(long settlementId, long currentTick) {
        Settlement current = state.requireSettlement(settlementId);
        if (current.status() == SettlementStatus.PENDING) {
            return state;
        }
        boolean stalled = state.payments().stream().anyMatch(row -> row.settlementId() == settlementId
                && row.status() == ObligationStatus.STALLED);
        boolean paymentsComplete = state.payments().stream().filter(row -> row.settlementId() == settlementId)
                .allMatch(row -> row.status() == ObligationStatus.COMPLETE);
        boolean demobilizationComplete = state.demobilizations().stream()
                .filter(row -> row.settlementId() == settlementId)
                .allMatch(row -> row.status() == ObligationStatus.COMPLETE);
        boolean replacementComplete = state.replacementDemands().stream()
                .filter(row -> row.settlementId() == settlementId)
                .allMatch(row -> row.status() == ReplacementStatus.COMMISSIONED
                        || row.status() == ReplacementStatus.CANCELLED);
        SettlementStatus status;
        if (stalled) status = SettlementStatus.STALLED;
        else if (paymentsComplete && demobilizationComplete && replacementComplete) status = SettlementStatus.COMPLETE;
        else status = SettlementStatus.EXECUTING;
        Settlement updated = new Settlement(
                current.id(), current.proposalId(), current.warId(), current.factionA(), current.factionB(),
                current.openedTick(), currentTick, status, current.memoryRecorded());
        replaceSettlement(updated, currentTick);
        return state;
    }

    private Settlement requirePlanningSettlement(long settlementId) {
        Settlement settlement = state.requireSettlement(settlementId);
        if (settlement.status() != SettlementStatus.PENDING) {
            throw new IllegalStateException("Stage-21G recovery plan is already finalized");
        }
        return settlement;
    }

    private Settlement requireFinalizedSettlement(long settlementId) {
        Settlement settlement = state.requireSettlement(settlementId);
        if (settlement.status() == SettlementStatus.PENDING) {
            throw new IllegalStateException("Stage-21G recovery plan must be finalized before execution");
        }
        return settlement;
    }

    private static int addPayments(
            List<PaymentObligation> output,
            long settlementId,
            String payer,
            String recipient,
            List<Term> terms,
            int startingOrdinal) {
        int ordinal = startingOrdinal;
        for (Term term : terms) {
            if (term.kind() == TermKind.TREASURY_PAYMENT) {
                output.add(new PaymentObligation(
                        settlementId, ordinal, payer, recipient, term.amountMilliCredits(),
                        ObligationStatus.PENDING, 0L));
            }
            ordinal++;
        }
        return ordinal;
    }

    private static String stableFaction(CommandGroupState group, FactionIdentityResolver identities) {
        return identities.stableId(group.factionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Command group references unknown runtime faction: " + group.factionId()));
    }

    private DemobilizationDirective findDirective(long settlementId, long commandGroupId) {
        return state.demobilizations().stream()
                .filter(row -> row.settlementId() == settlementId && row.commandGroupId() == commandGroupId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Demobilization must be registered before recovery-plan finalization"));
    }

    private void replaceDirective(DemobilizationDirective replacement, long tick) {
        ArrayList<DemobilizationDirective> rows = new ArrayList<>(state.demobilizations().size());
        boolean found = false;
        for (DemobilizationDirective row : state.demobilizations()) {
            if (row.settlementId() == replacement.settlementId()
                    && row.commandGroupId() == replacement.commandGroupId()) {
                rows.add(replacement);
                found = true;
            } else rows.add(row);
        }
        if (!found) throw new IllegalStateException("Unknown demobilization directive");
        state = rebuild(tick, state.nextSettlementId(), state.nextReplacementDemandId(),
                state.settlements(), state.payments(), rows, state.losses(), state.replacementDemands());
    }

    private void replaceDemand(ReplacementDemand replacement, long tick) {
        ArrayList<ReplacementDemand> rows = new ArrayList<>(state.replacementDemands().size());
        boolean found = false;
        for (ReplacementDemand row : state.replacementDemands()) {
            if (row.id() == replacement.id()) {
                rows.add(replacement);
                found = true;
            } else rows.add(row);
        }
        if (!found) throw new IllegalStateException("Unknown replacement demand");
        state = rebuild(tick, state.nextSettlementId(), state.nextReplacementDemandId(),
                state.settlements(), state.payments(), state.demobilizations(), state.losses(), rows);
    }

    private void replaceSettlement(Settlement replacement, long tick) {
        ArrayList<Settlement> rows = new ArrayList<>(state.settlements().size());
        boolean found = false;
        for (Settlement row : state.settlements()) {
            if (row.id() == replacement.id()) {
                rows.add(replacement);
                found = true;
            } else rows.add(row);
        }
        if (!found) throw new IllegalStateException("Unknown settlement");
        state = rebuild(tick, state.nextSettlementId(), state.nextReplacementDemandId(),
                rows, state.payments(), state.demobilizations(), state.losses(), state.replacementDemands());
    }

    private SettlementRecoveryState rebuild(
            long tick,
            long nextSettlementId,
            long nextReplacementDemandId,
            List<Settlement> settlements,
            List<PaymentObligation> payments,
            List<DemobilizationDirective> demobilizations,
            List<FleetLossRecord> losses,
            List<ReplacementDemand> replacementDemands) {
        state = new SettlementRecoveryState(
                SettlementRecoveryState.CURRENT_VERSION,
                Math.max(state.simulationTick(), tick),
                nextSettlementId,
                nextReplacementDemandId,
                settlements,
                payments,
                demobilizations,
                losses,
                replacementDemands);
        return state;
    }

    private static String requireText(String value, String label) {
        String result = Objects.requireNonNull(value, label).strip();
        if (result.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return result;
    }

    private static void requireTick(long tick) {
        if (tick < 0L) throw new IllegalArgumentException("Stage-21G tick must be non-negative");
    }

    public record DemobilizationResult(
            FleetCommandState commandState,
            SettlementRecoveryState recoveryState,
            FleetOrderState returnOrder) {
        public DemobilizationResult {
            Objects.requireNonNull(commandState, "commandState");
            Objects.requireNonNull(recoveryState, "recoveryState");
            Objects.requireNonNull(returnOrder, "returnOrder");
            if (returnOrder.type() != OrderType.RETURN) {
                throw new IllegalArgumentException("Demobilization result requires RETURN order");
            }
        }
    }
}
