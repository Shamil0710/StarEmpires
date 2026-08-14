package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.CombatCommandComponent;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.FlightCommandComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.WorldSimulation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage15CivilianSurvivalAcceptanceTest {
    @Test
    void observedAttackInterruptsCivilianOrderFleesPersistsIntelThenResumesAfterHysteresis() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(15_401L);
        SimulationSession session = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        FleetPlacementState civilianFleet = civilianFleet(world, session);
        FleetPlacementState activeFleet = otherFleet(world, civilianFleet.id());
        Entity civilian = session.getEntityRegistry().find(civilianFleet.localEntityId());
        Entity attacker = hostileCombatant(session, civilian);
        TransformComponent civilianTransform = civilian.getComponent(TransformComponent.class);
        TransformComponent attackerTransform = attacker.getComponent(TransformComponent.class);

        civilianTransform.position.set(600f, 600f);
        civilianTransform.velocity.setZero();
        attackerTransform.position.set(605f, 600f);
        attackerTransform.velocity.setZero();
        // Keep the intended MOVE direction opposite to the initial flee direction.
        float orderTargetX = 800f;

        PlayerState player = new PlayerState(
                10_000_000L,
                null,
                List.of(),
                List.of(activeFleet.id(), civilianFleet.id()),
                activeFleet.id(),
                List.of(
                        DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                        DemoGalaxyFactory.INNER_SYSTEM_ID,
                        DemoGalaxyFactory.FRONTIER_SYSTEM_ID),
                List.of(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        PlayerRuntime runtime = PlayerRuntime.create(world, content, player);
        PlayerFleetOrderService orders = new PlayerFleetOrderService(runtime);
        assertTrue(orders.issue(PlayerFleetOrderState.move(
                civilianFleet.id(), DemoGalaxyFactory.ACTIVE_SYSTEM_ID, orderTargetX, 600f)));

        // Force an actual observed attack command. The combat system may also refresh it through
        // CombatAI because attacker and civilian are now physically inside weapon range.
        EntityIdComponent civilianId = civilian.getComponent(EntityIdComponent.class);
        CombatCommandComponent attack = attacker.getComponent(CombatCommandComponent.class);
        if (attack == null) {
            attack = new CombatCommandComponent();
            attacker.add(attack);
        }
        attack.targetId = civilianId.id;
        attack.fireRequested = true;

        runtime.advanceFrame(0.1f);
        FlightCommandComponent flee = civilian.getComponent(FlightCommandComponent.class);
        assertNotNull(flee);
        assertTrue(flee.axisX < 0f,
                "attacker is to the right, so civilian must physically accelerate left away from it");
        assertEquals(FleetOrderType.MOVE, orders.order(civilianFleet.id()).orElseThrow().type(),
                "survival must interrupt rather than overwrite the persistent job");
        assertTrue(runtime.player().threatIntel().stream().anyMatch(intel ->
                intel.kind() == PlayerThreatIntelKind.SYSTEM
                        && DemoGalaxyFactory.ACTIVE_SYSTEM_ID.equals(intel.systemA())
                        && intel.dangerScore() > 0f));
        assertFalse(runtime.player().threatIntel().stream().anyMatch(intel ->
                DemoGalaxyFactory.INNER_SYSTEM_ID.equals(intel.systemA())),
                "remote systems without an owned observer must not gain omniscient intel");

        PlayableWorldState decoded = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(runtime.snapshot()));
        assertEquals(PlayableWorldState.CURRENT_VERSION, decoded.schemaVersion());
        assertEquals(runtime.player().threatIntel(), decoded.playerState().threatIntel());

        // Remove the real local threat and allow the bounded 30-tick clear hysteresis to expire.
        attackerTransform.position.set(950f, 950f);
        attack.clear();
        for (int step = 0; step < 45; step++) {
            runtime.advanceFrame(0.1f);
        }
        FlightCommandComponent resumed = civilian.getComponent(FlightCommandComponent.class);
        assertNotNull(resumed);
        assertTrue(resumed.axisX > 0f,
                "after hysteresis the original MOVE order must resume toward its persistent target");
        assertEquals(FleetOrderType.MOVE, orders.order(civilianFleet.id()).orElseThrow().type());
    }

    private static FleetPlacementState civilianFleet(WorldSimulation world, SimulationSession session) {
        for (FleetPlacementState placement : world.getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !DemoGalaxyFactory.ACTIVE_SYSTEM_ID.equals(placement.systemId())) {
                continue;
            }
            Entity entity = session.getEntityRegistry().find(placement.localEntityId());
            if (entity != null
                    && (entity.getComponent(TradeAIComponent.class) != null
                    || entity.getComponent(MiningComponent.class) != null)
                    && entity.getComponent(TransformComponent.class) != null
                    && entity.getComponent(FactionComponent.class) != null) {
                return placement;
            }
        }
        throw new AssertionError("No civilian fleet in active system");
    }

    private static FleetPlacementState otherFleet(WorldSimulation world, FleetId excluded) {
        for (FleetPlacementState placement : world.getFleetPlacements()) {
            if (placement.locationKind() == FleetLocationKind.IN_SYSTEM
                    && DemoGalaxyFactory.ACTIVE_SYSTEM_ID.equals(placement.systemId())
                    && !placement.id().equals(excluded)) {
                return placement;
            }
        }
        throw new AssertionError("No second active-system fleet");
    }

    private static Entity hostileCombatant(SimulationSession session, Entity civilian) {
        FactionComponent civilianFaction = civilian.getComponent(FactionComponent.class);
        List<Entity> candidates = new ArrayList<>();
        for (Entity entity : session.getEngine().getEntities()) {
            CombatComponent combat = entity.getComponent(CombatComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (entity != civilian
                    && combat != null
                    && combat.isOperational()
                    && faction != null
                    && faction.factionId != civilianFaction.factionId
                    && entity.getComponent(TransformComponent.class) != null
                    && entity.getComponent(EntityIdComponent.class) != null) {
                candidates.add(entity);
            }
        }
        if (candidates.isEmpty()) {
            throw new AssertionError("No hostile combatant available for survival acceptance");
        }
        candidates.sort((left, right) -> left.getComponent(EntityIdComponent.class).id.compareTo(
                right.getComponent(EntityIdComponent.class).id));
        return candidates.get(0);
    }
}
