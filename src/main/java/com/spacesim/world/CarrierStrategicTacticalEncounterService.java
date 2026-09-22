package com.spacesim.world;

import com.spacesim.world.CarrierWingStrategicReadinessService.CarrierWingAssignment;
import com.spacesim.world.SmallCraftTacticalEncounterService.EncounterResult;
import com.spacesim.world.SmallCraftTacticalEncounterService.ExternalCombatant;
import com.spacesim.world.SmallCraftTacticalEncounterService.SmallCraftParticipant;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * M22.8H strategic admission seam for exact local carrier-wing encounters.
 *
 * <p>The class owns no tactical physics and no strategic operation state. It validates that the
 * declared ordinary carrier participates in the already-admitted Stage-21E operation and that every
 * individual small craft belongs to the current carrier-wing association, then delegates the exact
 * exchange to {@link SmallCraftTacticalEncounterService}. External carrier/escort/target engineering
 * remains detached and is returned to its pre-existing world authority for ordinary commit-back.</p>
 */
public final class CarrierStrategicTacticalEncounterService {
    private final SmallCraftTacticalEncounterService tactical;

    /**
     * Creates the H bridge over the existing M22.8E exact tactical authority.
     *
     * @param tactical accepted individual-craft Stage-19 bridge
     */
    public CarrierStrategicTacticalEncounterService(
            SmallCraftTacticalEncounterService tactical) {
        this.tactical = Objects.requireNonNull(tactical, "tactical");
    }

    /**
     * Resolves one carrier-wing encounter through the accepted M22.8E/Stage-19 path.
     *
     * @param operation current admitted Stage-21E strategic operation
     * @param carrierFleetId ordinary carrier FleetId participating in the operation
     * @param wing current explicit carrier-to-individual-craft association
     * @param missionState current individual mission authority
     * @param smallCraft exact deployed individual participants
     * @param externalCombatants ordinary carrier/escort/target participants owned by existing world authority
     * @param maximumTicks positive exact-local Stage-19 horizon
     * @return M22.8E exact commit result for individual craft plus detached external outcomes
     */
    public EncounterResult resolve(
            OperationState operation,
            FleetId carrierFleetId,
            CarrierWingAssignment wing,
            SmallCraftMissionState missionState,
            Collection<SmallCraftParticipant> smallCraft,
            Collection<ExternalCombatant> externalCombatants,
            long maximumTicks) {
        OperationState strategic = Objects.requireNonNull(operation, "operation");
        FleetId carrier = Objects.requireNonNull(carrierFleetId, "carrierFleetId");
        CarrierWingAssignment assignment = Objects.requireNonNull(wing, "wing");
        Objects.requireNonNull(missionState, "missionState");
        Objects.requireNonNull(smallCraft, "smallCraft");
        Objects.requireNonNull(externalCombatants, "externalCombatants");

        if (!strategic.status().active()
                || strategic.status() == OperationStatus.STAGING
                || strategic.status() == OperationStatus.WITHDRAWING) {
            throw new IllegalStateException(
                    "carrier tactical handoff requires an active in-area Stage-21E operation");
        }
        if (!strategic.participantFleetIds().contains(carrier)) {
            throw new IllegalArgumentException(
                    "declared carrier is not a physical participant of the Stage-21E operation");
        }
        if (!assignment.carrierFleetId().equals(carrier)) {
            throw new IllegalArgumentException(
                    "carrier-wing association belongs to another ordinary FleetId");
        }

        Set<SmallCraftId> admitted = new HashSet<>(assignment.craftIds());
        Set<SmallCraftId> seen = new HashSet<>();
        for (SmallCraftParticipant participant : smallCraft) {
            SmallCraftParticipant checked =
                    Objects.requireNonNull(participant, "small-craft participant");
            if (!admitted.contains(checked.craftId())) {
                throw new IllegalArgumentException(
                        "tactical small craft is not assigned to the strategic carrier wing: "
                                + checked.craftId());
            }
            if (!seen.add(checked.craftId())) {
                throw new IllegalArgumentException(
                        "duplicate strategic carrier-wing tactical participant: "
                                + checked.craftId());
            }
        }
        if (seen.isEmpty()) {
            throw new IllegalArgumentException(
                    "carrier strategic tactical handoff requires at least one individual craft");
        }

        return tactical.resolve(
                missionState,
                smallCraft,
                externalCombatants,
                maximumTicks);
    }
}
