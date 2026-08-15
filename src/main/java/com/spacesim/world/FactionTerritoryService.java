package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.simulation.SimulationSession;

import java.util.Objects;

/**
 * Shared read boundary for Stage-17D territorial state.
 *
 * <p>Physical presence is derived from real local ECS entities. Claims, recognition and control are
 * read from persistent strategic state. This prevents stations, fleets or diplomatic acknowledgement
 * from silently becoming sovereignty.</p>
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
        FactionStrategicState strategy = checkedWorld.findFactionStrategicState(factionId)
                .orElseThrow(() -> new IllegalArgumentException("Faction has no strategic state: " + factionId));

        SimulationSession session = checkedWorld.findSession(checkedSystem)
                .orElseThrow(() -> new IllegalStateException(
                        "StarSystem не имеет runtime SimulationSession: " + checkedSystem));
        boolean physicalPresence = hasPhysicalPresence(session, factionRuntimeId);
        String controller = checkedWorld.controllingFaction(checkedSystem).orElse(null);
        TerritorialClaimState claim = strategy.claimFor(checkedSystem);
        int recognitionCount = recognitionCount(checkedWorld, factionId, checkedSystem, controller, claim);

        FactionTerritoryView.Jurisdiction jurisdiction;
        if (factionId.equals(controller)) {
            jurisdiction = FactionTerritoryView.Jurisdiction.SELF_CONTROLLED;
        } else if (claim != null && claim.status() == TerritorialClaimState.Status.CONTESTED) {
            jurisdiction = FactionTerritoryView.Jurisdiction.CONTESTED;
        } else if (controller != null) {
            jurisdiction = FactionTerritoryView.Jurisdiction.FOREIGN_CONTROLLED;
        } else if (claim != null && claim.status() == TerritorialClaimState.Status.STABILIZING) {
            jurisdiction = FactionTerritoryView.Jurisdiction.STABILIZING;
        } else if (claim != null) {
            jurisdiction = FactionTerritoryView.Jurisdiction.CLAIMED;
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
                controller,
                claim != null,
                claim == null ? null : claim.status(),
                claim == null ? 0L : claim.stabilizationTicks(),
                claim != null && claim.status() == TerritorialClaimState.Status.CONTESTED,
                recognitionCount);
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

    private static int recognitionCount(
            WorldSimulation world,
            String factionId,
            StarSystemId systemId,
            String controller,
            TerritorialClaimState claim) {
        TerritorialRecognitionState.Kind relevantKind = factionId.equals(controller)
                ? TerritorialRecognitionState.Kind.CONTROL
                : claim == null ? null : TerritorialRecognitionState.Kind.CLAIM;
        if (relevantKind == null) {
            return 0;
        }
        int count = 0;
        for (FactionStrategicState strategy : world.snapshot().factionStrategies()) {
            for (TerritorialRecognitionState recognition : strategy.territorialRecognitions()) {
                if (recognition.targetFactionContentId().equals(factionId)
                        && recognition.systemId().equals(systemId)
                        && recognition.kind() == relevantKind) {
                    count++;
                }
            }
        }
        return count;
    }
}
