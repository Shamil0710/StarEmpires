package com.spacesim.world;

import com.spacesim.world.StrategicOperationState.OperationState;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Stage-21E admission seam for reinforcements that have already travelled through ordinary fleet movement.
 *
 * <p>The service cannot move or spawn a fleet. A reinforcement may join only after the ordinary
 * {@link FleetId} is physically present in the operation objective system, has the same owner and
 * satisfies the operation's physical mission-readiness threshold. Therefore route time remains owned
 * by Stage-21D movement rather than being bypassed by operation metadata.</p>
 */
public final class Stage21EReinforcementService {
    /**
     * Attaches one already-arrived ordinary fleet to an active operation.
     *
     * @param state current operation state
     * @param operationId active operation receiving reinforcement
     * @param reinforcementFleetId ordinary fleet that has physically arrived
     * @param forces current physical force reconstruction
     * @param currentTick authoritative tick
     * @return immutable operation state containing the arrived reinforcement
     */
    public StrategicOperationState attachArrived(
            StrategicOperationState state,
            long operationId,
            FleetId reinforcementFleetId,
            FleetForceRegistry forces,
            long currentTick) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(reinforcementFleetId, "reinforcementFleetId");
        Objects.requireNonNull(forces, "forces");
        if (currentTick < 0L) throw new IllegalArgumentException("currentTick must be non-negative");
        OperationState operation = state.requireOperation(operationId);
        if (!operation.status().active()) throw new IllegalStateException("cannot reinforce a terminal operation");
        if (operation.participantFleetIds().contains(reinforcementFleetId)) return state;

        FleetForceRegistry.Entry reinforcement = forces.find(reinforcementFleetId)
                .orElseThrow(() -> new IllegalStateException("reinforcement FleetId is absent from physical world"));
        if (reinforcement.factionId() != operation.factionId()) {
            throw new IllegalStateException("reinforcement owner differs from operation owner");
        }
        if (reinforcement.locationKind() != FleetLocationKind.IN_SYSTEM
                || !operation.objectiveSystemId().equals(reinforcement.systemId())) {
            throw new IllegalStateException("reinforcement has not physically arrived at operation objective");
        }
        if (!reinforcement.readiness().missionCapable(operation.supplyPolicy().minimumMissionReadinessBps())) {
            throw new IllegalStateException("reinforcement is below operation mission-readiness threshold");
        }

        ArrayList<FleetId> participants = new ArrayList<>(operation.participantFleetIds());
        participants.add(reinforcementFleetId);
        OperationState reinforced = new OperationState(
                operation.id(), operation.type(), operation.commandGroupId(), operation.sourceOrderId(),
                operation.factionId(), participants, operation.stagingSystemId(), operation.objectiveSystemId(),
                operation.objectiveId(), operation.rulesOfEngagement(), operation.supplyPolicy(),
                operation.withdrawalPolicy(), operation.status(), operation.createdAtTick(), currentTick,
                operation.unsupportedSinceTick(), operation.contact(), operation.encounter());
        return state.replace(reinforced);
    }
}
