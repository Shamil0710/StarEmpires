package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.CombatCommandComponent;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Converts actually observed attacks against player-owned physical fleets into persistent intel.
 *
 * <p>Only systems containing an owned materialized FleetId are inspected. An attacker contributes
 * danger only when its live {@link CombatCommandComponent} currently targets an owned local Entity.
 * Remote systems without an owned observer are never scanned, preventing omniscient danger updates.</p>
 */
final class PlayerThreatObserver {
    private final PlayerRuntime runtime;
    private final PlayerThreatIntelService intelService;

    PlayerThreatObserver(PlayerRuntime runtime) {
        this.runtime = runtime;
        this.intelService = new PlayerThreatIntelService(runtime);
    }

    /** Records fresh system danger observations produced by the just-completed simulation update. */
    void observe() {
        Map<StarSystemId, Set<com.spacesim.persistence.EntityId>> ownedBySystem = new HashMap<>();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() == FleetLocationKind.IN_SYSTEM
                    && runtime.player().ownedFleetIds().contains(placement.id())) {
                ownedBySystem.computeIfAbsent(placement.systemId(), ignored -> new HashSet<>())
                        .add(placement.localEntityId());
            }
        }
        for (Map.Entry<StarSystemId, Set<com.spacesim.persistence.EntityId>> entry : ownedBySystem.entrySet()) {
            SimulationSession session = runtime.world().findSession(entry.getKey()).orElse(null);
            if (session == null) {
                continue;
            }
            float danger = 0f;
            boolean observedAttack = false;
            for (Entity entity : session.getEngine().getEntities()) {
                EntityIdComponent id = entity.getComponent(EntityIdComponent.class);
                CombatCommandComponent command = entity.getComponent(CombatCommandComponent.class);
                CombatComponent combat = entity.getComponent(CombatComponent.class);
                if (id == null || command == null || command.targetId == null || combat == null
                        || entry.getValue().contains(id.id)
                        || !entry.getValue().contains(command.targetId)
                        || !combat.isOperational()) {
                    continue;
                }
                observedAttack = true;
                danger += Math.max(1f, combat.damagePerSecond);
            }
            if (observedAttack) {
                intelService.observeSystem(
                        entry.getKey(),
                        danger,
                        1f,
                        session.getClock().getTick());
            }
        }
    }
}
