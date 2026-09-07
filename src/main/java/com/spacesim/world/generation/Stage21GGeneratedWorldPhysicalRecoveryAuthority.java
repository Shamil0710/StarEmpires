package com.spacesim.world.generation;

import com.spacesim.economy.Stage18ShipyardRuntime;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.SettlementRecoveryService;
import com.spacesim.world.Stage21GPhysicalRecoveryService;
import com.spacesim.world.StarSystemId;

import java.util.Objects;

/**
 * Generated-world composition boundary for Stage-21G replacement commissioning.
 *
 * <p>{@link Stage21GPhysicalRecoveryService} remains the only owner of shipyard settlement,
 * ordinary entity creation, FleetId allocation and recovery provenance. This adapter adds only the
 * exact Stage-20 local physical sidecar required by the already-composed generated-world runtime.
 * It therefore closes the same representation boundary that Stage-21E tactical commitment closes
 * for surviving fleets: no replacement position is inferred later from the legacy float transform.
 *
 * <p>The Stage-20 materialization service is resolved before finite shipyard settlement. A successful
 * Stage-21G build always creates a fresh live local entity, so registering that fresh EntityId is a
 * deterministic one-shot operation. Any duplicate/missing-local invariant fails closed immediately;
 * the adapter never invents or repairs kinematics after the fact.</p>
 */
public final class Stage21GGeneratedWorldPhysicalRecoveryAuthority {
    private final LiveRuntime runtime;
    private final Stage21GPhysicalRecoveryService recovery;

    /**
     * Binds ordinary Stage-21G physical recovery to one already composed generated-world runtime.
     *
     * @param runtime live Stage-20.5 generated-world composition
     * @param recovery existing Stage-21G physical recovery authority
     */
    public Stage21GGeneratedWorldPhysicalRecoveryAuthority(
            LiveRuntime runtime,
            Stage21GPhysicalRecoveryService recovery) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.recovery = Objects.requireNonNull(recovery, "recovery");
    }

    /**
     * Builds one ordinary replacement and immediately registers exact Stage-20 berth kinematics.
     *
     * <p>The supplied berth must be stationary: a just-completed shipyard asset cannot acquire free
     * velocity from this composition layer. The legacy float transform supplied to Stage-21G is only
     * an exactly representable projection of the authoritative hierarchical/double local offset.
     * Failed Stage-18 settlement produces no entity and therefore no Stage-20 physical registration.</p>
     *
     * @param recoveryState existing Stage-21G settlement/replacement coordinator
     * @param demandId persisted replacement demand
     * @param identities stable/runtime faction identity authority
     * @param buildSystemId exact generated system containing the yard/berth
     * @param displayName ordinary fleet display name
     * @param berthPhysical exact stationary Stage-20 local berth
     * @param targetFit exact persisted replacement fit
     * @param station finite Stage-18 station stock
     * @param yard active Stage-18 yard capability
     * @param budget finite Stage-18 yard work budget
     * @param currentTick authoritative world/recovery tick
     * @return ordinary Stage-21G build result after exact physical registration on success
     */
    public Stage21GPhysicalRecoveryService.BuildResult buildReplacement(
            SettlementRecoveryService recoveryState,
            long demandId,
            FactionIdentityResolver identities,
            StarSystemId buildSystemId,
            String displayName,
            LocalPhysicalKinematics berthPhysical,
            InstalledFit targetFit,
            Stage18StationStorage station,
            Stage18ShipyardRuntime.YardCapabilitySnapshot yard,
            Stage18ShipyardRuntime.YardWorkBudget budget,
            long currentTick) {
        SettlementRecoveryService checkedRecovery = Objects.requireNonNull(recoveryState, "recoveryState");
        StarSystemId checkedSystem = Objects.requireNonNull(buildSystemId, "buildSystemId");
        LocalPhysicalKinematics physical = Objects.requireNonNull(berthPhysical, "berthPhysical");
        if (Double.compare(physical.velocityXMps(), 0d) != 0
                || Double.compare(physical.velocityYMps(), 0d) != 0) {
            throw new IllegalArgumentException("generated-world replacement berth must be stationary");
        }
        if (runtime.world().findSession(checkedSystem).isEmpty()) {
            throw new IllegalArgumentException("generated-world replacement system is absent: " + checkedSystem);
        }
        var materialization = runtime.arrival().materialization(checkedSystem);
        float projectedX = exactFloat(physical.position().offsetXM(), "replacement berth X");
        float projectedY = exactFloat(physical.position().offsetYM(), "replacement berth Y");

        Stage21GPhysicalRecoveryService.BuildResult built = recovery.buildReplacement(
                checkedRecovery,
                demandId,
                runtime.world(),
                Objects.requireNonNull(identities, "identities"),
                checkedSystem,
                displayName,
                projectedX,
                projectedY,
                Objects.requireNonNull(targetFit, "targetFit"),
                Objects.requireNonNull(station, "station"),
                Objects.requireNonNull(yard, "yard"),
                Objects.requireNonNull(budget, "budget"),
                currentTick);
        if (!built.settlement().settled()) {
            if (built.commissionedFleetId() != null || built.builtSystemId() != null || built.completion() != null) {
                throw new IllegalStateException("unsettled replacement unexpectedly materialized an ordinary asset");
            }
            return built;
        }

        if (built.commissionedFleetId() == null || built.builtSystemId() == null || built.completion() == null) {
            throw new IllegalStateException("settled replacement lacks ordinary commissioning identity");
        }
        if (!checkedSystem.equals(built.builtSystemId())) {
            throw new IllegalStateException("replacement commissioned in a different generated system");
        }
        FleetPlacementState placement = runtime.world().findFleet(built.commissionedFleetId())
                .orElseThrow(() -> new IllegalStateException(
                        "commissioned replacement FleetId is absent from ordinary world"));
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                || !checkedSystem.equals(placement.systemId())
                || placement.localEntityId() == null) {
            throw new IllegalStateException("commissioned replacement is not a live local generated-world fleet");
        }
        EntityId localId = placement.localEntityId();
        if (materialization.physicalState(localId).isPresent()) {
            throw new IllegalStateException("fresh replacement already owns Stage-20 physical authority");
        }
        materialization.registerPhysicalState(localId, physical);
        LocalPhysicalKinematics registered = materialization.physicalState(localId).orElseThrow();
        if (!registered.equals(physical)) {
            throw new IllegalStateException("registered replacement physical state differs from exact berth");
        }
        return built;
    }

    private static float exactFloat(double value, String label) {
        float projected = (float) value;
        if (!Float.isFinite(projected) || Double.compare((double) projected, value) != 0) {
            throw new IllegalArgumentException(label + " is not exactly representable by the legacy float projection");
        }
        return projected;
    }
}
