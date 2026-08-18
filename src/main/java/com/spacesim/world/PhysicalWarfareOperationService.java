package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.CombatComponent;
import com.spacesim.simulation.SimulationSession;

import java.util.Objects;

/**
 * Read-only Stage-19E resolver for whether a warfare operation has a physical actor and anchor.
 *
 * <p>An operation is active only while its aggressor {@link FleetId} is materialized in a system,
 * references a real topology/system/entity target and has an operational combat capability. The
 * service does not move fleets, discover contacts, change route risk, apply damage or mutate the
 * economy.</p>
 */
public final class PhysicalWarfareOperationService {
    private final WorldSimulation world;

    /**
     * Creates an operation resolver over one authoritative world.
     *
     * @param world authoritative world simulation
     */
    public PhysicalWarfareOperationService(WorldSimulation world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    /**
     * Tests whether the supplied operation is physically active now.
     *
     * <p>Interdiction is anchored to one real topology edge and is active only while the aggressor
     * is physically materialized at either endpoint. The current pre-Stage-20 world has no authored
     * jump-gate local geometry, so this endpoint presence is the narrowest honest physical anchor;
     * the operation cannot project danger onto unrelated systems or links.</p>
     *
     * @param operation immutable warfare operation intent
     * @return {@code true} only when actor, combat capability and target anchor all currently exist
     */
    public boolean isPhysicallyActive(PhysicalWarfareOperation operation) {
        PhysicalWarfareOperation checked = Objects.requireNonNull(operation, "operation");
        FleetPlacementState placement = world.findFleet(checked.aggressorFleetId()).orElse(null);
        if (placement == null || placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            return false;
        }
        SimulationSession aggressorSession = world.findSession(placement.systemId()).orElse(null);
        Entity aggressor = aggressorSession == null
                ? null
                : aggressorSession.getEntityRegistry().find(placement.localEntityId());
        CombatComponent combat = aggressor == null ? null : aggressor.getComponent(CombatComponent.class);
        if (combat == null || !combat.isOperational()) {
            return false;
        }

        return switch (checked.type()) {
            case BLOCKADE -> realSystem(checked.systemA())
                    && checked.systemA().equals(placement.systemId());
            case INTERDICTION -> realEdge(checked.systemA(), checked.systemB())
                    && (checked.systemA().equals(placement.systemId())
                    || checked.systemB().equals(placement.systemId()));
            case RAID -> checked.systemA().equals(placement.systemId())
                    && realRaidTarget(checked, placement);
        };
    }

    private boolean realSystem(StarSystemId systemId) {
        return world.getTopology().findSystem(systemId).isPresent();
    }

    private boolean realEdge(StarSystemId first, StarSystemId second) {
        return realSystem(first)
                && realSystem(second)
                && world.getTopology().neighbors(first).contains(second);
    }

    private boolean realRaidTarget(
            PhysicalWarfareOperation operation,
            FleetPlacementState aggressorPlacement) {
        if (!realSystem(operation.systemA())
                || operation.targetEntityId().equals(aggressorPlacement.localEntityId())) {
            return false;
        }
        SimulationSession session = world.findSession(operation.systemA()).orElse(null);
        return session != null && session.getEntityRegistry().find(operation.targetEntityId()) != null;
    }
}
