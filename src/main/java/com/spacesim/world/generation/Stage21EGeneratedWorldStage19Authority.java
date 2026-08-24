package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.ImportedCombatantState;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.CombatantResult;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.Result;
import com.spacesim.world.DestructionPolicy;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.Stage21ETacticalMaterializationService.CombatSide;
import com.spacesim.world.Stage21ETacticalMaterializationService.PhysicalCombatant;
import com.spacesim.world.Stage21ETacticalMaterializationService.TacticalMaterializationAuthority;
import com.spacesim.world.Stage21ETacticalMaterializationService.TacticalMaterializationRequest;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Production Stage-21E adapter that executes exact Stage-19 tactical state against a generated world.
 *
 * <p>The adapter re-validates every handoff row against the current ordinary {@link FleetId}, local
 * entity payload and Stage-20 exact physical sidecar before simulation. Stage 19 then runs only on
 * detached copies. On successful bounded resolution, surviving engineering state and exact local
 * kinematics are written back to the same ordinary entities; catastrophically destroyed fleets are
 * removed through {@link com.spacesim.world.WorldSimulation#destroyEntity} and their Stage-20
 * physical sidecars are released. No replacement FleetId is allocated.</p>
 */
public final class Stage21EGeneratedWorldStage19Authority implements TacticalMaterializationAuthority {
    private final LiveRuntime runtime;
    private final Stage19ExactTacticalEncounterResolver resolver;
    private final long maximumTicks;

    /**
     * Creates the production exact-tactical adapter with the current bounded Stage-19 horizon.
     *
     * @param runtime live generated-world composition owning ordinary fleets and Stage-20 kinematics
     */
    public Stage21EGeneratedWorldStage19Authority(LiveRuntime runtime) {
        this(runtime, new Stage19ExactTacticalEncounterResolver(),
                Stage19ExactTacticalEncounterResolver.DEFAULT_MAXIMUM_TICKS);
    }

    /**
     * Creates an adapter with an explicit deterministic tactical horizon, primarily for acceptance.
     *
     * @param runtime live generated-world composition
     * @param resolver exact Stage-19 physical encounter resolver
     * @param maximumTicks positive Stage-19 fixed-tick encounter horizon
     */
    public Stage21EGeneratedWorldStage19Authority(
            LiveRuntime runtime,
            Stage19ExactTacticalEncounterResolver resolver,
            long maximumTicks) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        if (maximumTicks <= 0L) throw new IllegalArgumentException("maximumTicks must be positive");
        this.maximumTicks = maximumTicks;
    }

    /**
     * Re-validates, resolves and synchronously commits one exact Stage-19 tactical exchange.
     *
     * <p>The returned encounter identity is scoped to the owning operation and is derived from the
     * authoritative materialization tick. The method does not leave hidden in-memory battle state:
     * every tactical effect is committed before it returns, allowing save/load immediately after the
     * call to reconstruct the same strategic/physical state.</p>
     *
     * @param request exact ordinary physical payload handoff validated by Stage 21E
     * @return positive deterministic encounter identity scoped to the operation
     */
    @Override
    public long materializeExact(TacticalMaterializationRequest request) {
        TacticalMaterializationRequest checked = Objects.requireNonNull(request, "request");
        StarSystemId systemId = checked.systemId();
        var world = runtime.world();
        var session = world.findSession(systemId)
                .orElseThrow(() -> new IllegalStateException("tactical system has no ordinary local session"));

        ArrayList<BoundCombatant> bound = new ArrayList<>();
        for (PhysicalCombatant row : checked.combatants()) {
            FleetPlacementState placement = world.findFleet(row.fleetId())
                    .orElseThrow(() -> new IllegalStateException("tactical FleetId disappeared before exact import: " + row.fleetId()));
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !systemId.equals(placement.systemId())
                    || placement.localEntityId() == null) {
                throw new IllegalStateException("tactical FleetId left the exact local system before import: " + row.fleetId());
            }
            Entity entity = session.getEntityRegistry().require(placement.localEntityId());
            EntityState actual = EntityStateMapper.capture(entity);
            if (!actual.equals(row.entityState())) {
                throw new IllegalStateException("tactical handoff payload is stale for ordinary fleet: " + row.fleetId());
            }
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            if (engineering == null) {
                throw new IllegalStateException("tactical ordinary fleet lacks engineering authority: " + row.fleetId());
            }
            LocalPhysicalKinematics physical = runtime.arrival().materialization(systemId)
                    .physicalState(placement.localEntityId())
                    .orElseThrow(() -> new IllegalStateException(
                            "tactical ordinary fleet lacks Stage-20 physical kinematics: " + row.fleetId()));
            bound.add(new BoundCombatant(row, placement, entity, actual, physical));
        }
        bound.sort(Comparator.comparing(value -> value.row().fleetId()));
        LocalPhysicalPosition anchor = bound.get(0).physical().position();

        ArrayList<ImportedCombatantState> imported = new ArrayList<>(bound.size());
        for (BoundCombatant value : bound) {
            LocalPhysicalPosition.Displacement displacement = anchor.displacementTo(value.physical().position());
            EngineeringComponent source = value.entity().getComponent(EngineeringComponent.class);
            EngineeringComponent detached = new EngineeringComponent(
                    source.fit, source.runtimeState, source.instanceState);
            imported.add(new ImportedCombatantState(
                    value.row().fleetId().value(),
                    value.row().side() == CombatSide.OPERATION ? Side.ALPHA : Side.BETA,
                    detached,
                    displacement.deltaXM(),
                    displacement.deltaYM(),
                    value.physical().velocityXMps(),
                    value.physical().velocityYMps()));
        }

        Result result = resolver.resolve(imported, maximumTicks);
        validateCommitBoundary(systemId, bound, result);
        commitSurvivors(systemId, anchor, bound, result);
        commitDestructions(systemId, bound, result);
        return Math.addExact(checked.materializedAtTick(), 1L);
    }

    private void validateCommitBoundary(
            StarSystemId systemId,
            List<BoundCombatant> bound,
            Result result) {
        var world = runtime.world();
        for (BoundCombatant before : bound) {
            FleetPlacementState placement = world.findFleet(before.row().fleetId())
                    .orElseThrow(() -> new IllegalStateException(
                            "ordinary FleetId disappeared while detached tactical resolution was running"));
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !systemId.equals(placement.systemId())
                    || !placement.localEntityId().equals(before.placement().localEntityId())) {
                throw new IllegalStateException("ordinary fleet placement changed during detached tactical resolution");
            }
            Entity current = world.findSession(systemId).orElseThrow()
                    .getEntityRegistry().require(placement.localEntityId());
            if (!EntityStateMapper.capture(current).equals(before.entityState())) {
                throw new IllegalStateException("ordinary physical payload changed during detached tactical resolution");
            }
            CombatantResult after = result.require(before.row().fleetId().value());
            EngineeringComponent currentEngineering = current.getComponent(EngineeringComponent.class);
            if (currentEngineering == null || !currentEngineering.fit.equals(after.fit())) {
                throw new IllegalStateException("tactical result attempts to replace the ordinary installed fit");
            }
        }
    }

    private void commitSurvivors(
            StarSystemId systemId,
            LocalPhysicalPosition anchor,
            List<BoundCombatant> bound,
            Result result) {
        var materialization = runtime.arrival().materialization(systemId);
        for (BoundCombatant before : bound) {
            CombatantResult after = result.require(before.row().fleetId().value());
            if (after.destroyed()) continue;
            Entity entity = before.entity();
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            engineering.setRuntimeState(after.runtimeState());
            engineering.setInstanceState(after.instanceState());
            LocalPhysicalPosition position = anchor.translated(after.xM(), after.yM());
            LocalPhysicalKinematics physical = new LocalPhysicalKinematics(
                    position, after.velocityXMps(), after.velocityYMps());
            materialization.updatePhysicalState(before.placement().localEntityId(), physical);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (transform != null) {
                transform.position.set(
                        exactFloat(position.offsetXM(), "tactical legacy X"),
                        exactFloat(position.offsetYM(), "tactical legacy Y"));
                transform.velocity.set(
                        exactFloat(after.velocityXMps(), "tactical legacy velocity X"),
                        exactFloat(after.velocityYMps(), "tactical legacy velocity Y"));
            }
        }
    }

    private void commitDestructions(
            StarSystemId systemId,
            List<BoundCombatant> bound,
            Result result) {
        TreeMap<FleetId, BoundCombatant> destroyed = new TreeMap<>();
        for (BoundCombatant before : bound) {
            if (result.require(before.row().fleetId().value()).destroyed()) {
                destroyed.put(before.row().fleetId(), before);
            }
        }
        for (BoundCombatant value : destroyed.values()) {
            runtime.world().destroyEntity(
                    systemId, value.placement().localEntityId(), DestructionPolicy.destroyAll());
            runtime.arrival().materialization(systemId)
                    .releasePhysicalStateForWorldTransfer(value.placement().localEntityId());
        }
    }

    private static float exactFloat(double value, String label) {
        float projected = (float) value;
        if (!Float.isFinite(projected)) {
            throw new IllegalStateException(label + " is outside the legacy ECS projection range");
        }
        return projected;
    }

    private record BoundCombatant(
            PhysicalCombatant row,
            FleetPlacementState placement,
            Entity entity,
            EntityState entityState,
            LocalPhysicalKinematics physical) {
        private BoundCombatant {
            Objects.requireNonNull(row, "row");
            Objects.requireNonNull(placement, "placement");
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(entityState, "entityState");
            Objects.requireNonNull(physical, "physical");
        }
    }
}
