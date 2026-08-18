package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.CombatComponent;
import com.spacesim.persistence.EntityId;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicalWarfareOperationServiceTest {
    @Test
    void blockadeRequiresMaterializedOperationalCombatFleetAndReadOnlyValidation() {
        WorldSimulation world = DemoGalaxyFactory.create(19_501L);
        FleetPlacementState aggressor = operationalCombatFleet(world, DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        PhysicalWarfareOperation operation = PhysicalWarfareOperation.blockade(
                aggressor.id(), DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        PhysicalWarfareOperationService service = new PhysicalWarfareOperationService(world);
        WorldState before = world.snapshot();

        assertTrue(service.isPhysicallyActive(operation));
        assertEquals(before, world.snapshot(), "operation validation must not mutate physical/economic state");

        StarSystemId neighbor = world.getTopology().neighbors(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).get(0);
        world.beginFleetTransfer(aggressor.id(), neighbor);
        assertFalse(service.isPhysicallyActive(operation),
                "detached transit fleet can no longer maintain local blockade presence");
    }

    @Test
    void interdictionRequiresRealTopologyEdgeAndRaidRequiresRealLocalTarget() {
        WorldSimulation world = DemoGalaxyFactory.create(19_502L);
        FleetPlacementState aggressor = operationalCombatFleet(world, DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        PhysicalWarfareOperationService service = new PhysicalWarfareOperationService(world);
        StarSystemId neighbor = world.getTopology().neighbors(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).get(0);

        assertTrue(service.isPhysicallyActive(PhysicalWarfareOperation.interdict(
                aggressor.id(), DemoGalaxyFactory.ACTIVE_SYSTEM_ID, neighbor)));
        assertFalse(service.isPhysicallyActive(PhysicalWarfareOperation.interdict(
                aggressor.id(), DemoGalaxyFactory.ACTIVE_SYSTEM_ID, DemoGalaxyFactory.FRONTIER_SYSTEM_ID)),
                "operation cannot invent a non-existent direct topology corridor");
        assertFalse(service.isPhysicallyActive(PhysicalWarfareOperation.raid(
                aggressor.id(), DemoGalaxyFactory.ACTIVE_SYSTEM_ID, new EntityId(9_999_999L))),
                "raid cannot target an entity absent from the physical local session");
    }

    private static FleetPlacementState operationalCombatFleet(WorldSimulation world, StarSystemId systemId) {
        SimulationSession session = world.findSession(systemId).orElseThrow();
        for (FleetPlacementState placement : world.getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !systemId.equals(placement.systemId())) {
                continue;
            }
            Entity entity = session.getEntityRegistry().find(placement.localEntityId());
            CombatComponent combat = entity == null ? null : entity.getComponent(CombatComponent.class);
            if (combat != null && combat.isOperational()) {
                return placement;
            }
        }
        throw new AssertionError("No operational combat fleet in system " + systemId);
    }
}
