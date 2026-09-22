package com.spacesim.world;

import com.spacesim.world.CarrierWingStrategicReadinessService.ProjectionResult;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;

import java.util.EnumSet;
import java.util.Objects;

/**
 * M22.8H carrier admission facade over the accepted Stage-21D/E strategic operation authority.
 *
 * <p>This service does not own orders, movement, contacts, combat or operation persistence. It only
 * proves that an ordinary Stage-21 command group contains the declared carrier and that the current
 * read-only wing projection is physically mission-capable, then delegates admission/review to the
 * existing {@link StrategicOperationService} using the projected ordinary {@link FleetForceRegistry}.</p>
 */
public final class CarrierStrategicOperationService {
    private static final EnumSet<OrderType> CARRIER_OPERATION_TYPES = EnumSet.of(
            OrderType.ESCORT,
            OrderType.INTERCEPT,
            OrderType.RAID,
            OrderType.GUARD);

    private final StrategicOperationService delegate;

    /** Creates the carrier integration facade over the accepted Stage-21E operation service. */
    public CarrierStrategicOperationService() {
        this(new StrategicOperationService());
    }

    CarrierStrategicOperationService(StrategicOperationService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /**
     * Admits an escort/interception/raid/defence carrier group through ordinary Stage-21E authority.
     *
     * @param state current strategic operation registry
     * @param commandState accepted Stage-21D command state
     * @param projected current M22.8H force projection
     * @param commandGroupId command group receiving strategic operation admission
     * @param carrierFleetId ordinary carrier FleetId within that group
     * @param currentTick authoritative world tick
     * @param rulesOfEngagement existing Stage-21E ROE
     * @param supplyPolicy existing Stage-21E physical readiness/supply policy
     * @param withdrawalPolicy existing Stage-21E withdrawal policy
     * @return ordinary Stage-21E operation state
     */
    public StrategicOperationState beginCarrierOperation(
            StrategicOperationState state,
            FleetCommandState commandState,
            ProjectionResult projected,
            long commandGroupId,
            FleetId carrierFleetId,
            long currentTick,
            RulesOfEngagement rulesOfEngagement,
            SupplyPolicy supplyPolicy,
            WithdrawalPolicy withdrawalPolicy) {
        Objects.requireNonNull(state, "state");
        FleetCommandState commands = Objects.requireNonNull(commandState, "commandState");
        ProjectionResult projection = Objects.requireNonNull(projected, "projected");
        FleetId carrier = Objects.requireNonNull(carrierFleetId, "carrierFleetId");
        SupplyPolicy policy = Objects.requireNonNull(supplyPolicy, "supplyPolicy");

        CommandGroupState group = commands.requireGroup(commandGroupId);
        if (!group.memberFleetIds().contains(carrier)) {
            throw new IllegalArgumentException(
                    "declared carrier is not a member of the Stage-21 command group: " + carrier);
        }
        FleetOrderState order = commands.activeOrderFor(commandGroupId)
                .orElseThrow(() -> new IllegalStateException(
                        "carrier strategic operation requires an accepted active Stage-21D order"));
        if (!CARRIER_OPERATION_TYPES.contains(order.type())) {
            throw new IllegalArgumentException(
                    "M22.8H carrier operation does not cover Stage-21D order type: " + order.type());
        }

        var wing = projection.wing(carrier)
                .orElseThrow(() -> new IllegalStateException(
                        "declared carrier has no current individual-wing strategic projection"));
        if (!wing.projectedCarrierReadiness().missionCapable(policy.minimumMissionReadinessBps())) {
            throw new IllegalStateException(
                    "carrier group is below current physical mission-readiness threshold");
        }

        return delegate.beginFromActiveOrder(
                state,
                commands,
                projection.forces(),
                commandGroupId,
                currentTick,
                Objects.requireNonNull(rulesOfEngagement, "rulesOfEngagement"),
                policy,
                Objects.requireNonNull(withdrawalPolicy, "withdrawalPolicy"));
    }

    /**
     * Reuses the ordinary Stage-21E supply/readiness review after wing loss, depletion or servicing.
     *
     * @param state current operation state
     * @param operationId admitted operation identity
     * @param projected fresh M22.8H force projection from current individual state
     * @param currentTick authoritative world tick
     * @return ordinary Stage-21E continuation/withdrawal decision
     */
    public StrategicOperationService.SupplyReview reviewSupplyAndReadiness(
            StrategicOperationState state,
            long operationId,
            ProjectionResult projected,
            long currentTick) {
        return delegate.reviewSupplyAndReadiness(
                Objects.requireNonNull(state, "state"),
                operationId,
                Objects.requireNonNull(projected, "projected").forces(),
                currentTick);
    }

    /**
     * Reports whether an existing Stage-21D order is in the explicitly accepted M22.8H carrier set.
     *
     * @param type Stage-21D order family
     * @return true for escort, intercept, raid and guard/defence
     */
    public static boolean carrierOperationType(OrderType type) {
        return CARRIER_OPERATION_TYPES.contains(Objects.requireNonNull(type, "type"));
    }
}
