package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.ActorObservation;
import com.spacesim.world.FactionActorObservationSnapshot.Domain;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.StrategicOperationState.ContactState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.TacticalEncounterState;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;

import java.util.Objects;

/**
 * Stage-21E lifecycle coordinator for physical strategic operations.
 *
 * <p>This service owns only operation metadata. It never searches hidden world truth, never applies
 * combat/economic percentages, never moves a fleet and never destroys an entity. Admission is tied
 * to an already accepted Stage-21D order and exact ordinary {@link FleetForceRegistry} entries.</p>
 */
public final class StrategicOperationService {
    /** Starts a Stage-21E operation from an already accepted active Stage-21D order. */
    public StrategicOperationState beginFromActiveOrder(
            StrategicOperationState state,
            FleetCommandState commandState,
            FleetForceRegistry forces,
            long commandGroupId,
            long currentTick,
            RulesOfEngagement rulesOfEngagement,
            SupplyPolicy supplyPolicy,
            WithdrawalPolicy withdrawalPolicy) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(commandState, "commandState");
        Objects.requireNonNull(forces, "forces");
        Objects.requireNonNull(rulesOfEngagement, "rulesOfEngagement");
        Objects.requireNonNull(supplyPolicy, "supplyPolicy");
        Objects.requireNonNull(withdrawalPolicy, "withdrawalPolicy");
        requireTick(currentTick);
        if (state.activeForCommandGroup(commandGroupId).isPresent()) {
            throw new IllegalStateException("command group already owns an active operation");
        }
        CommandGroupState group = commandState.requireGroup(commandGroupId);
        FleetOrderState order = commandState.activeOrderFor(commandGroupId)
                .orElseThrow(() -> new IllegalStateException("operation requires an accepted active Stage-21D order"));
        OperationType operationType = operationType(order.type());
        StarSystemId stagingSystem = null;
        for (FleetId fleetId : group.memberFleetIds()) {
            FleetForceRegistry.Entry force = forces.find(fleetId)
                    .orElseThrow(() -> new IllegalStateException("operation participant FleetId is absent: " + fleetId));
            if (force.factionId() != group.factionId()) {
                throw new IllegalStateException("operation participant owner differs from command group: " + fleetId);
            }
            if (force.locationKind() != FleetLocationKind.IN_SYSTEM || force.systemId() == null) {
                throw new IllegalStateException("operation participants must begin from a physical staging system");
            }
            if (stagingSystem == null) stagingSystem = force.systemId();
            else if (!stagingSystem.equals(force.systemId())) {
                throw new IllegalStateException("operation participants are not physically co-located for staging");
            }
            if (!force.readiness().missionCapable(supplyPolicy.minimumMissionReadinessBps())) {
                throw new IllegalStateException("operation participant is below mission readiness: " + fleetId);
            }
        }
        if (stagingSystem == null) throw new IllegalStateException("operation command group contains no physical fleets");
        OperationStatus initialStatus = stagingSystem.equals(order.targetSystemId())
                ? OperationStatus.ACTIVE : OperationStatus.STAGING;
        return state.add(new OperationState(
                state.nextOperationId(), operationType, group.id(), order.id(), group.factionId(),
                group.memberFleetIds(), stagingSystem, order.targetSystemId(),
                "system:" + order.targetSystemId().value(), rulesOfEngagement, supplyPolicy,
                withdrawalPolicy, initialStatus, currentTick, currentTick, -1L, null, null));
    }

    /** Advances staging only after all participants physically reach the objective system. */
    public StrategicOperationState activateWhenPhysicallyArrived(
            StrategicOperationState state, long operationId, FleetForceRegistry forces, long currentTick) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(forces, "forces");
        requireTick(currentTick);
        OperationState operation = state.requireOperation(operationId);
        if (operation.status() != OperationStatus.STAGING) return state;
        for (FleetId fleetId : operation.participantFleetIds()) {
            FleetForceRegistry.Entry force = forces.find(fleetId).orElse(null);
            if (force == null || force.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !operation.objectiveSystemId().equals(force.systemId())) return state;
        }
        return state.replace(operation.withLifecycle(
                OperationStatus.ACTIVE, currentTick, operation.unsupportedSinceTick(), null, null));
    }

    /** Acquires a target exclusively from an actor-bounded current security observation. */
    public StrategicOperationState acquireContact(
            StrategicOperationState state,
            long operationId,
            FactionActorObservationSnapshot observations,
            FleetId targetFleetId,
            StarSystemId observedSystemId,
            long currentTick) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(observations, "observations");
        Objects.requireNonNull(targetFleetId, "targetFleetId");
        Objects.requireNonNull(observedSystemId, "observedSystemId");
        requireTick(currentTick);
        OperationState operation = state.requireOperation(operationId);
        if (operation.status() != OperationStatus.ACTIVE && operation.status() != OperationStatus.CONTACT_CONFIRMED) {
            throw new IllegalStateException("operation is not available for contact acquisition");
        }
        ActorObservation matched = observations.security().stream()
                .filter(observation -> observation.domain() == Domain.SECURITY)
                .filter(observation -> observation.targetId().equals(targetFleetId.toString()))
                .filter(observation -> observation.evidence().currentAt(currentTick))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("target lacks current actor-bounded security evidence"));
        ObservationEvidence evidence = matched.evidence();
        ContactState contact = new ContactState(
                targetFleetId, observedSystemId, evidence.channel(), evidence.provenanceId(),
                evidence.observedAtTick(), evidence.freshUntilTick());
        return state.replace(operation.withLifecycle(
                OperationStatus.CONTACT_CONFIRMED, currentTick, operation.unsupportedSinceTick(),
                contact, operation.encounter()));
    }

    /** Records that the separate Stage-19 authority accepted exact-local tactical materialization. */
    public StrategicOperationState markEngaged(
            StrategicOperationState state, long operationId, TacticalEncounterState encounter, long currentTick) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(encounter, "encounter");
        requireTick(currentTick);
        OperationState operation = state.requireOperation(operationId);
        if (operation.status() != OperationStatus.CONTACT_CONFIRMED || operation.contact() == null) {
            throw new IllegalStateException("tactical materialization requires confirmed actor-bounded contact");
        }
        if (!encounter.targetFleetId().equals(operation.contact().targetFleetId())) {
            throw new IllegalArgumentException("encounter target differs from confirmed contact");
        }
        return state.replace(operation.withLifecycle(
                OperationStatus.ENGAGED, currentTick, operation.unsupportedSinceTick(),
                operation.contact(), encounter));
    }

    /** Re-evaluates physical readiness/supply without modifying those physical facts. */
    public SupplyReview reviewSupplyAndReadiness(
            StrategicOperationState state, long operationId, FleetForceRegistry forces, long currentTick) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(forces, "forces");
        requireTick(currentTick);
        OperationState operation = state.requireOperation(operationId);
        if (!operation.status().active()) return new SupplyReview(state, SupplyDecision.CONTINUE);
        boolean missingParticipant = false;
        boolean missionFailure = false;
        boolean withdrawalThresholdFailure = false;
        boolean ammunitionFailure = false;
        boolean propellantFailure = false;
        boolean supplyFailure = false;
        for (FleetId fleetId : operation.participantFleetIds()) {
            FleetForceRegistry.Entry force = forces.find(fleetId).orElse(null);
            if (force == null) { missingParticipant = true; continue; }
            FleetReadinessState readiness = force.readiness();
            missionFailure |= !readiness.missionCapable(operation.supplyPolicy().minimumMissionReadinessBps());
            withdrawalThresholdFailure |= readiness.overallBps()
                    < operation.withdrawalPolicy().withdrawBelowReadinessBps();
            ammunitionFailure |= readiness.ammunitionBps() == 0;
            propellantFailure |= readiness.propellantBps() == 0;
            supplyFailure |= readiness.supplyAccessBps() < operation.supplyPolicy().minimumSupplyAccessBps();
        }
        if (missingParticipant && forces.entries().stream()
                .noneMatch(entry -> operation.participantFleetIds().contains(entry.fleetId()))) {
            OperationState failed = operation.withLifecycle(
                    OperationStatus.FAILED, currentTick, operation.unsupportedSinceTick(),
                    operation.contact(), resolvedEncounter(operation.encounter(), currentTick));
            return new SupplyReview(state.replace(failed), SupplyDecision.FAIL_NO_SURVIVORS);
        }
        boolean unsupported = missionFailure || supplyFailure;
        long unsupportedSince = unsupported
                ? (operation.unsupportedSinceTick() >= 0L ? operation.unsupportedSinceTick() : currentTick) : -1L;
        boolean unsupportedExpired = unsupported
                && currentTick - unsupportedSince >= operation.supplyPolicy().maximumUnsupportedTicks();
        boolean withdraw = unsupportedExpired || withdrawalThresholdFailure
                || (ammunitionFailure && operation.withdrawalPolicy().withdrawWhenOutOfAmmunition());
        if (propellantFailure && operation.withdrawalPolicy().withdrawWhenOutOfPropellant()) {
            OperationState failed = operation.withLifecycle(
                    OperationStatus.FAILED, currentTick, unsupportedSince,
                    operation.contact(), resolvedEncounter(operation.encounter(), currentTick));
            return new SupplyReview(state.replace(failed), SupplyDecision.FAIL_CANNOT_WITHDRAW);
        }
        if (withdraw) {
            OperationState withdrawing = operation.withLifecycle(
                    OperationStatus.WITHDRAWING, currentTick, unsupportedSince,
                    operation.contact(), resolvedEncounter(operation.encounter(), currentTick));
            return new SupplyReview(state.replace(withdrawing), SupplyDecision.SUBMIT_ORDINARY_WITHDRAW_ORDER);
        }
        OperationState refreshed = operation.withLifecycle(
                operation.status(), currentTick, unsupportedSince, operation.contact(), operation.encounter());
        return new SupplyReview(state.replace(refreshed), SupplyDecision.CONTINUE);
    }

    /** Maps only the six Stage-21E operation-bearing Stage-21D order families. */
    public static OperationType operationType(OrderType type) {
        return switch (Objects.requireNonNull(type, "type")) {
            case ESCORT -> OperationType.ESCORT;
            case INTERCEPT -> OperationType.INTERCEPTION;
            case RAID -> OperationType.RAID;
            case BLOCKADE -> OperationType.BLOCKADE;
            case GUARD -> OperationType.DEFENSE;
            case INVADE -> OperationType.INVASION;
            default -> throw new IllegalArgumentException(
                    "Stage-21D order does not materialize a Stage-21E operation: " + type);
        };
    }

    /** Physical continuation decision; withdrawal must use ordinary Stage-21D order authority. */
    public enum SupplyDecision {
        /** Current physical facts permit continuation. */ CONTINUE,
        /** Submit ordinary WITHDRAW through FleetOrderSubmissionService after closing the source order. */ SUBMIT_ORDINARY_WITHDRAW_ORDER,
        /** No physical participant remains. */ FAIL_NO_SURVIVORS,
        /** Withdrawal policy requires propellant but none remains. */ FAIL_CANNOT_WITHDRAW
    }

    /** @param state updated operation registry @param decision required continuation action */
    public record SupplyReview(StrategicOperationState state, SupplyDecision decision) {
        /** Validates a review result. */
        public SupplyReview {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(decision, "decision");
        }
    }

    private static TacticalEncounterState resolvedEncounter(TacticalEncounterState encounter, long tick) {
        if (encounter == null || !encounter.active()) return encounter;
        return new TacticalEncounterState(encounter.encounterId(), encounter.targetFleetId(), encounter.systemId(),
                encounter.materializedAtTick(), tick);
    }

    private static void requireTick(long tick) {
        if (tick < 0L) throw new IllegalArgumentException("tick must be non-negative");
    }
}
