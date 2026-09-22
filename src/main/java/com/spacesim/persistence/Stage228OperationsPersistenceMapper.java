package com.spacesim.persistence;

import com.spacesim.persistence.Stage228OperationsPersistentState.CarrierWingState;
import com.spacesim.persistence.Stage228OperationsPersistentState.MissionState;
import com.spacesim.persistence.Stage228OperationsPersistentState.PendingDeliveryState;
import com.spacesim.world.CarrierWingStrategicReadinessService.CarrierWingAssignment;
import com.spacesim.world.SmallCraftMissionState;
import com.spacesim.world.SmallCraftMissionState.MissionOrder;
import com.spacesim.world.SmallCraftMissionState.MissionTarget;
import com.spacesim.world.SmallCraftPhysicalLogisticsService.LogisticsState;
import com.spacesim.world.SmallCraftPhysicalLogisticsService.PendingDelivery;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Maps M22.8 D/G/H runtime values to the single M22.8M operations persistence sidecar. */
public final class Stage228OperationsPersistenceMapper {
    private Stage228OperationsPersistenceMapper() {
        throw new AssertionError("utility class");
    }

    /**
     * Captures exact durable mission, pending-delivery and carrier-wing state.
     *
     * @param missions current immutable D mission authority
     * @param logistics current immutable G pending-delivery state
     * @param carrierWings current H strategic carrier associations
     * @return deterministic operations sidecar
     */
    public static Stage228OperationsPersistentState capture(
            SmallCraftMissionState missions,
            LogisticsState logistics,
            Collection<CarrierWingAssignment> carrierWings) {
        SmallCraftMissionState checkedMissions =
                Objects.requireNonNull(missions, "missions");
        LogisticsState checkedLogistics = Objects.requireNonNull(logistics, "logistics");
        Objects.requireNonNull(carrierWings, "carrierWings");

        List<MissionState> missionRows = checkedMissions.missions().stream()
                .map(Stage228OperationsPersistenceMapper::captureMission)
                .toList();
        List<PendingDeliveryState> deliveryRows =
                checkedLogistics.pendingDeliveries().stream()
                        .map(value -> new PendingDeliveryState(
                                value.craftId(),
                                value.sourceStationId(),
                                value.designId()))
                        .toList();
        List<CarrierWingState> wingRows = carrierWings.stream()
                .map(value -> {
                    CarrierWingAssignment checked =
                            Objects.requireNonNull(value, "carrierWing");
                    return new CarrierWingState(
                            checked.carrierFleetId(),
                            checked.hostStableId(),
                            checked.stableFactionId(),
                            checked.craftIds());
                })
                .toList();
        return new Stage228OperationsPersistentState(
                Stage228OperationsPersistentState.CURRENT_VERSION,
                Stage228OperationsPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228OperationsPersistentState.CURRENT_SEMANTIC_CONTRACT,
                checkedMissions.nextMissionId(),
                missionRows,
                deliveryRows,
                wingRows);
    }

    /**
     * Restores immutable D/G/H runtime values without creating physical assets.
     *
     * @param state validated operations sidecar
     * @return exact runtime value bundle
     */
    public static RuntimeState restore(Stage228OperationsPersistentState state) {
        Stage228OperationsPersistentState checked = Objects.requireNonNull(state, "state");
        List<MissionOrder> missions = checked.missions().stream()
                .map(Stage228OperationsPersistenceMapper::restoreMission)
                .toList();
        SmallCraftMissionState missionState =
                new SmallCraftMissionState(checked.nextMissionId(), missions);
        LogisticsState logistics = new LogisticsState(
                checked.pendingDeliveries().stream()
                        .map(value -> new PendingDelivery(
                                value.craftId(),
                                value.sourceStationId(),
                                value.designId()))
                        .toList());
        List<CarrierWingAssignment> wings = checked.carrierWings().stream()
                .map(CarrierWingState::toRuntime)
                .toList();
        return new RuntimeState(missionState, logistics, wings);
    }

    private static MissionState captureMission(MissionOrder mission) {
        MissionOrder checked = Objects.requireNonNull(mission, "mission");
        return new MissionState(
                checked.id(),
                checked.craftId(),
                checked.source(),
                checked.type(),
                checked.target().kind(),
                checked.target().referenceId(),
                checked.submittedTick(),
                checked.status());
    }

    private static MissionOrder restoreMission(MissionState state) {
        MissionState checked = Objects.requireNonNull(state, "state");
        return new MissionOrder(
                checked.missionId(),
                checked.craftId(),
                checked.source(),
                checked.type(),
                new MissionTarget(
                        checked.targetKind(),
                        checked.targetReferenceId()),
                checked.submittedTick(),
                checked.status());
    }

    /**
     * Immutable restored D/G/H runtime values.
     *
     * @param missions restored mission state
     * @param logistics restored pending-delivery state
     * @param carrierWings restored carrier-wing associations
     */
    public record RuntimeState(
            SmallCraftMissionState missions,
            LogisticsState logistics,
            List<CarrierWingAssignment> carrierWings) {
        /**
         * Validates one restored value bundle.
         *
         * @param missions restored mission state
         * @param logistics restored logistics state
         * @param carrierWings restored strategic associations
         */
        public RuntimeState {
            Objects.requireNonNull(missions, "missions");
            Objects.requireNonNull(logistics, "logistics");
            carrierWings = List.copyOf(Objects.requireNonNull(carrierWings, "carrierWings"));
        }
    }
}
