package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.CombatComponent;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.systems.CombatSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bridges lethal local combat events to the existing authoritative Stage-9 destruction pipeline.
 *
 * <p>CombatSystem never removes Ashley entities during engine iteration. After a world update this
 * resolver drains lethal-shot requests, revalidates that the physical victim still exists with zero
 * hull, and delegates to {@link WorldSimulation#destroyEntity(StarSystemId,
 * com.spacesim.persistence.EntityId, DestructionPolicy)} using physical salvage semantics.</p>
 */
public final class CombatDestructionResolver {
    private CombatDestructionResolver() {
        throw new AssertionError("CombatDestructionResolver does not create instances");
    }

    /**
     * Resolves all currently queued combat deaths in topology order.
     *
     * @param world authoritative world whose local combat systems produced lethal events
     * @return immutable accounting results for physical removals performed by this call
     */
    public static List<ResolvedCombatDestruction> resolve(WorldSimulation world) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        List<ResolvedCombatDestruction> resolved = new ArrayList<>();
        for (StarSystemNode node : checkedWorld.getTopology().systems()) {
            SimulationSession session = checkedWorld.findSession(node.id()).orElseThrow();
            CombatSystem combatSystem = session.getEngine().getSystem(CombatSystem.class);
            if (combatSystem == null) {
                continue;
            }
            for (CombatSystem.DestructionRequest request : combatSystem.drainDestructionRequests()) {
                Entity victim = session.getEntityRegistry().find(request.victimId());
                CombatComponent combat = victim == null ? null : victim.getComponent(CombatComponent.class);
                if (victim == null || combat == null || combat.hull > 0f) {
                    continue;
                }
                DestructionResult result = checkedWorld.destroyEntity(
                        node.id(), request.victimId(), DestructionPolicy.salvageResources());
                resolved.add(new ResolvedCombatDestruction(node.id(), request, result));
            }
        }
        return List.copyOf(resolved);
    }

    /**
     * Diagnostics connecting one lethal shot to the normal destruction result.
     *
     * @param systemId system where the victim physically existed
     * @param request lethal combat request
     * @param destructionResult ordinary Stage-9 accounting/lifecycle result
     */
    public record ResolvedCombatDestruction(
            StarSystemId systemId,
            CombatSystem.DestructionRequest request,
            DestructionResult destructionResult) {
        /**
         * Validates immutable diagnostic members.
         *
         * @param systemId system where the victim physically existed
         * @param request lethal combat request
         * @param destructionResult ordinary Stage-9 accounting/lifecycle result
         */
        public ResolvedCombatDestruction {
            Objects.requireNonNull(systemId, "Combat destruction systemId not set");
            Objects.requireNonNull(request, "Combat destruction request not set");
            Objects.requireNonNull(destructionResult, "Combat destruction result not set");
        }
    }
}
