package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.simulation.SimulationSession;

import java.util.Objects;

/**
 * Shared read boundary for Stage-17D territorial state.
 *
 * <p>Physical presence is derived from real local ECS entities. Sovereignty is read from the
 * existing persistent strategic control map. This intentionally prevents an owned station or a
 * passing fleet from silently becoming territorial control.</p>
 */
public final class FactionTerritoryService {
    private FactionTerritoryService() {
        throw new AssertionError("FactionTerritoryService не создаёт экземпляров");
    }

    /**
     * Assesses one faction's territorial relationship to a star system without mutating the world.
     *
     * @param world authoritative runtime world
     * @param systemId system to inspect
     * @param factionContentId stable authored or world-defined faction ID
     * @return immutable derived territory view
     * @throws NullPointerException when a required argument is null
     * @throws IllegalArgumentException when the system or faction is unknown
     */
    public static FactionTerritoryView assess(
            WorldSimulation world,
            StarSystemId systemId,
            String factionContentId) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation не задан");
        StarSystemId checkedSystem = Objects.requireNonNull(systemId, "StarSystemId не задан");
        if (checkedWorld.getTopology().findSystem(checkedSystem).isEmpty()) {
            throw new IllegalArgumentException("Неизвестная StarSystem: " + checkedSystem);
        }

        String factionId = Objects.requireNonNull(factionContentId, "Faction content ID не задан").strip();
        if (factionId.isEmpty()) {
            throw new IllegalArgumentException("Faction content ID не может быть пустым");
        }
        int factionRuntimeId = checkedWorld.findFactionRuntimeId(factionId)
                .orElseThrow(() -> new IllegalArgumentException("Неизвестная faction: " + factionId));

        SimulationSession session = checkedWorld.findSession(checkedSystem)
                .orElseThrow(() -> new IllegalStateException(
                        "StarSystem не имеет runtime SimulationSession: " + checkedSystem));
        boolean physicalPresence = hasPhysicalPresence(session, factionRuntimeId);
        String controller = checkedWorld.controllingFaction(checkedSystem).orElse(null);

        FactionTerritoryView.Jurisdiction jurisdiction;
        if (factionId.equals(controller)) {
            jurisdiction = FactionTerritoryView.Jurisdiction.SELF_CONTROLLED;
        } else if (controller != null) {
            jurisdiction = FactionTerritoryView.Jurisdiction.FOREIGN_CONTROLLED;
        } else if (physicalPresence) {
            jurisdiction = FactionTerritoryView.Jurisdiction.PRESENT;
        } else {
            jurisdiction = FactionTerritoryView.Jurisdiction.UNCLAIMED;
        }
        return new FactionTerritoryView(
                checkedSystem,
                factionId,
                jurisdiction,
                physicalPresence,
                controller);
    }

    private static boolean hasPhysicalPresence(SimulationSession session, int factionRuntimeId) {
        for (Entity entity : session.getEngine().getEntities()) {
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (faction != null && faction.factionId == factionRuntimeId) {
                return true;
            }
        }
        return false;
    }
}
