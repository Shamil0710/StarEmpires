package com.spacesim.world;

import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stage-21E cross-layer cleanup after physical fleet destruction.
 *
 * <p>The service never decides that a fleet was lost. It receives only an existence predicate over
 * ordinary world {@link FleetId} authority. Missing identities are removed from current Stage-21D
 * command membership. If the operation-owning command group has no surviving ordinary member, that
 * group and its orders are removed and the operation becomes terminal FAILED while retaining its
 * historical participant identities for consequence/audit evidence.</p>
 */
public final class Stage21ECommandLossReconciliationService {
    /**
     * Reconciles one operation-owning command group against current ordinary fleet existence.
     *
     * @param commandState current Stage-21D command metadata
     * @param operationState current Stage-21E operation metadata after physical consequence commit
     * @param operationId operation whose owning command group is being reconciled
     * @param fleetExists read-only ordinary-world FleetId existence authority
     * @param currentTick authoritative non-negative reconciliation tick
     * @return updated command and operation metadata with no active reference to destroyed fleets
     */
    public ReconciliationResult reconcile(
            FleetCommandState commandState,
            StrategicOperationState operationState,
            long operationId,
            FleetExistencePolicy fleetExists,
            long currentTick) {
        Objects.requireNonNull(commandState, "commandState");
        Objects.requireNonNull(operationState, "operationState");
        Objects.requireNonNull(fleetExists, "fleetExists");
        if (currentTick < 0L) throw new IllegalArgumentException("currentTick must be non-negative");
        OperationState operation = operationState.requireOperation(operationId);
        CommandGroupState group = commandState.requireGroup(operation.commandGroupId());

        ArrayList<FleetId> survivingGroupMembers = new ArrayList<>();
        for (FleetId fleetId : group.memberFleetIds()) {
            if (fleetExists.exists(fleetId)) survivingGroupMembers.add(fleetId);
        }
        ArrayList<FleetId> survivingParticipants = new ArrayList<>();
        for (FleetId fleetId : operation.participantFleetIds()) {
            if (fleetExists.exists(fleetId)) survivingParticipants.add(fleetId);
        }

        if (survivingGroupMembers.isEmpty()) {
            List<CommandGroupState> groups = commandState.groups().stream()
                    .filter(value -> value.id() != group.id())
                    .toList();
            List<FleetOrderState> orders = commandState.orders().stream()
                    .filter(value -> value.commandGroupId() != group.id())
                    .toList();
            FleetCommandState command = new FleetCommandState(
                    commandState.nextCommandGroupId(), commandState.nextOrderId(), groups, orders);
            OperationState failed = operation.withLifecycle(
                    OperationStatus.FAILED,
                    currentTick,
                    operation.unsupportedSinceTick(),
                    operation.contact(),
                    operation.encounter());
            return new ReconciliationResult(command, operationState.replace(failed), true);
        }

        CommandGroupState replacementGroup = new CommandGroupState(
                group.id(), group.factionId(), group.name(), survivingGroupMembers,
                group.homeSystemId(), group.reserve(), group.homeDefense(), group.maxStrategicRiskBps());
        ArrayList<CommandGroupState> groups = new ArrayList<>(commandState.groups().size());
        for (CommandGroupState current : commandState.groups()) {
            groups.add(current.id() == group.id() ? replacementGroup : current);
        }
        FleetCommandState command = new FleetCommandState(
                commandState.nextCommandGroupId(), commandState.nextOrderId(), groups, commandState.orders());

        StrategicOperationState operations = operationState;
        if (!survivingParticipants.equals(operation.participantFleetIds())) {
            if (survivingParticipants.isEmpty()) {
                OperationState failed = operation.withLifecycle(
                        OperationStatus.FAILED,
                        currentTick,
                        operation.unsupportedSinceTick(),
                        operation.contact(),
                        operation.encounter());
                operations = operationState.replace(failed);
            } else {
                OperationState trimmed = new OperationState(
                        operation.id(), operation.type(), operation.commandGroupId(), operation.sourceOrderId(),
                        operation.factionId(), survivingParticipants, operation.stagingSystemId(),
                        operation.objectiveSystemId(), operation.objectiveId(), operation.rulesOfEngagement(),
                        operation.supplyPolicy(), operation.withdrawalPolicy(), operation.status(),
                        operation.createdAtTick(), currentTick, operation.unsupportedSinceTick(),
                        operation.contact(), operation.encounter());
                operations = operationState.replace(trimmed);
            }
        }
        return new ReconciliationResult(command, operations, false);
    }

    /** Read-only ordinary-world FleetId existence authority. */
    @FunctionalInterface
    public interface FleetExistencePolicy {
        /**
         * Tests whether the exact ordinary fleet identity still exists.
         *
         * @param fleetId stable ordinary FleetId
         * @return true only while the ordinary world still owns that fleet
         */
        boolean exists(FleetId fleetId);
    }

    /**
     * Result of one command-loss cleanup.
     *
     * @param commandState updated Stage-21D command metadata
     * @param operationState updated Stage-21E operation metadata
     * @param owningGroupDestroyed whether the operation-owning command group lost every member
     */
    public record ReconciliationResult(
            FleetCommandState commandState,
            StrategicOperationState operationState,
            boolean owningGroupDestroyed) {
        /**
         * Validates one cleanup result.
         *
         * @param commandState updated Stage-21D command metadata
         * @param operationState updated Stage-21E operation metadata
         * @param owningGroupDestroyed whether the operation-owning group was removed
         */
        public ReconciliationResult {
            Objects.requireNonNull(commandState, "commandState");
            Objects.requireNonNull(operationState, "operationState");
        }
    }
}
