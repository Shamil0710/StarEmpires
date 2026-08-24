package com.spacesim.world.generation;

import com.spacesim.economy.Stage18LogisticsRuntime.TransferResult;
import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.persistence.Stage20FreightRuntime;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetJumpState;
import com.spacesim.world.PhysicalWarfareOperationService;
import com.spacesim.world.Stage21EOperationTrafficPolicy;
import com.spacesim.world.Stage21EOperationTrafficPolicy.Availability;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StrategicOperationState;

import java.util.Objects;

/**
 * Stage-21E admission boundary for ordinary generated-world freight movement and handling.
 *
 * <p>This class owns no route, cargo, station-storage or movement state. It derives only a binary
 * admission decision from current persistent Stage-21E operations plus the existing read-only
 * {@link PhysicalWarfareOperationService}. When admitted, execution is delegated unchanged to the
 * existing Stage-20/18 runtime. When denied, the ordinary mutation is not invoked, so a physically
 * anchored blockade or interdiction cannot be bypassed by the Stage-21E production entry point.</p>
 *
 * <p>The operation state is supplied on every call rather than copied into this adapter. Save/load
 * therefore remains owned by the Stage-21E checkpoint and there is no second blockade/interdiction
 * lifecycle. Friendly traffic and traffic unrelated to a physical hostile anchor remain available.</p>
 */
public final class Stage21EGeneratedWorldTrafficRuntime {
    private final LiveRuntime runtime;
    private final Stage21EOperationTrafficPolicy policy;

    /**
     * Creates the production admission adapter over one generated-world runtime.
     *
     * @param runtime existing Stage-20.5 generated-world runtime owning freight and movement
     */
    public Stage21EGeneratedWorldTrafficRuntime(LiveRuntime runtime) {
        this(runtime, productionPolicy(runtime));
    }

    /**
     * Creates an admission adapter with an explicit read-only policy.
     *
     * @param runtime existing Stage-20.5 generated-world runtime
     * @param policy Stage-21E physical blockade/interdiction availability policy
     */
    public Stage21EGeneratedWorldTrafficRuntime(
            LiveRuntime runtime,
            Stage21EOperationTrafficPolicy policy) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * Requests the next ordinary freight route hop only when no physical hostile interdiction blocks
     * the exact topology edge.
     *
     * <p>The preflight derives the same next route edge from immutable freight/order state, but does
     * not advance route state itself. The existing Stage-20 method remains the final movement
     * authority and repeats all phase, placement and jump validation before mutation.</p>
     *
     * @param operations current persistent Stage-21E operation state
     * @param fleetId exact freight fleet requesting its ordinary next route hop
     * @return ordinary persistent jump state created by Stage 20
     * @throws IllegalStateException when a physically active hostile blockade/interdiction denies the edge
     */
    public FleetJumpState requestNextRouteHop(
            StrategicOperationState operations,
            FleetId fleetId) {
        StrategicOperationState current = Objects.requireNonNull(operations, "operations");
        FreighterState fleet = requireFreighter(fleetId);
        TransportOrderState order = requireOrder(fleet);
        int nextIndex = switch (fleet.phase()) {
            case OUTBOUND -> fleet.routeIndex() + 1;
            case RETURNING -> fleet.routeIndex() - 1;
            default -> throw new IllegalStateException(
                    "next route hop requires OUTBOUND or RETURNING freight phase");
        };
        if (nextIndex < 0 || nextIndex >= order.orderedSystems().size()) {
            throw new IllegalStateException("freight route has no next hop");
        }
        StarSystemId from = fleet.currentSystemId();
        StarSystemId to = order.orderedSystems().get(nextIndex);
        Availability availability = policy.edgeAvailability(
                current, from, to, trafficFactionId(fleet));
        requireAvailable(availability, "freight route edge " + from + " -> " + to);
        return runtime.requestNextRouteHop(fleet.fleetId());
    }

    /**
     * Transfers already extracted source cargo to the order source hub only while physical endpoint
     * handling is available.
     *
     * @param operations current persistent Stage-21E operation state
     * @param fleetId order-owning freight fleet
     * @param siteId generated source-outpost identity
     * @param massKg requested physical commodity mass
     * @param durationSeconds finite ordinary handling interval
     * @return ordinary Stage-18 logistics transfer result
     * @throws IllegalStateException when a physically active hostile blockade denies source handling
     */
    public TransferResult transferOutpostToOrderSource(
            StrategicOperationState operations,
            FleetId fleetId,
            String siteId,
            double massKg,
            double durationSeconds) {
        StrategicOperationState current = Objects.requireNonNull(operations, "operations");
        FreighterState fleet = requireFreighter(fleetId);
        TransportOrderState order = requireOrder(fleet);
        StarSystemId source = order.orderedSystems().get(0);
        requireAvailable(
                policy.handlingAvailability(current, source, trafficFactionId(fleet)),
                "freight source handling in " + source);
        return runtime.transferOutpostToOrderSource(
                fleet.fleetId(), siteId, massKg, durationSeconds);
    }

    /**
     * Loads real source-hub inventory into the existing physical freight hold only while hostile
     * physical blockade policy permits handling in that source system.
     *
     * @param operations current persistent Stage-21E operation state
     * @param fleetId exact assigned freight fleet
     * @param massKg physical mass requested for loading
     * @param simulationSeconds authoritative loading timestamp
     * @param durationSeconds finite ordinary handling interval
     * @return ordinary Stage-20 freight cargo operation result
     * @throws IllegalStateException when a physically active hostile blockade denies source handling
     */
    public Stage20FreightRuntime.CargoOperationResult loadAtOrderSource(
            StrategicOperationState operations,
            FleetId fleetId,
            double massKg,
            double simulationSeconds,
            double durationSeconds) {
        StrategicOperationState current = Objects.requireNonNull(operations, "operations");
        FreighterState fleet = requireFreighter(fleetId);
        TransportOrderState order = requireOrder(fleet);
        StarSystemId source = order.orderedSystems().get(0);
        requireAvailable(
                policy.handlingAvailability(current, source, trafficFactionId(fleet)),
                "freight source handling in " + source);
        return runtime.loadAtOrderSource(
                fleet.fleetId(), massKg, simulationSeconds, durationSeconds);
    }

    /**
     * Unloads the existing physical freight hold into the ordinary destination station only while
     * hostile physical blockade policy permits handling in that destination system.
     *
     * @param operations current persistent Stage-21E operation state
     * @param fleetId arrived assigned freight fleet
     * @param massKg physical mass requested for unloading
     * @param durationSeconds finite ordinary handling interval
     * @return ordinary Stage-20 freight cargo operation result
     * @throws IllegalStateException when a physically active hostile blockade denies destination handling
     */
    public Stage20FreightRuntime.CargoOperationResult unloadAtOrderDestination(
            StrategicOperationState operations,
            FleetId fleetId,
            double massKg,
            double durationSeconds) {
        StrategicOperationState current = Objects.requireNonNull(operations, "operations");
        FreighterState fleet = requireFreighter(fleetId);
        TransportOrderState order = requireOrder(fleet);
        StarSystemId destination = order.orderedSystems().get(order.orderedSystems().size() - 1);
        requireAvailable(
                policy.handlingAvailability(current, destination, trafficFactionId(fleet)),
                "freight destination handling in " + destination);
        return runtime.unloadAtOrderDestination(
                fleet.fleetId(), massKg, durationSeconds);
    }

    private FreighterState requireFreighter(FleetId fleetId) {
        FleetId id = Objects.requireNonNull(fleetId, "fleetId");
        return runtime.freight().findFreighter(id).orElseThrow(
                () -> new IllegalArgumentException("unknown Stage-20 freight FleetId: " + id));
    }

    private TransportOrderState requireOrder(FreighterState fleet) {
        if (fleet.phase() == FreightPhase.IDLE || fleet.phase() == FreightPhase.DESTROYED
                || fleet.activeOrderId().isBlank()) {
            throw new IllegalStateException("freight fleet has no active physical transport order");
        }
        return runtime.freight().findOrder(fleet.activeOrderId()).orElseThrow(
                () -> new IllegalStateException(
                        "freight active order disappeared: " + fleet.activeOrderId()));
    }

    private int trafficFactionId(FreighterState fleet) {
        return runtime.world().findFactionRuntimeId(fleet.stableFactionId()).orElseThrow(
                () -> new IllegalStateException(
                        "freight owner is absent from ordinary world faction directory: "
                                + fleet.stableFactionId()));
    }

    private static Stage21EOperationTrafficPolicy productionPolicy(LiveRuntime runtime) {
        LiveRuntime live = Objects.requireNonNull(runtime, "runtime");
        return new Stage21EOperationTrafficPolicy(
                new PhysicalWarfareOperationService(live.world()));
    }

    private static void requireAvailable(Availability availability, String action) {
        Availability checked = Objects.requireNonNull(availability, "availability");
        if (checked != Availability.AVAILABLE) {
            throw new IllegalStateException(action + " denied by physical Stage-21E warfare: " + checked);
        }
    }
}
