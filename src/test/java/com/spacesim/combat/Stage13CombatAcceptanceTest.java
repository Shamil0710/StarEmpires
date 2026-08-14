package com.spacesim.combat;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.CombatRuntimeComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.player.PlayerReputationState;
import com.spacesim.player.PlayerRuntime;
import com.spacesim.player.PlayerState;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.CombatDestructionResolver;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.WorldSimulation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage13CombatAcceptanceTest {
    @Test
    void playerAndAiShareFirePipelineAndLethalShotUsesWorldDestruction() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        ContentCatalog.WeaponDefinition weapon = content.findWeapon("weapon.pulse_laser_mk1");
        assertNotNull(weapon);
        assertEquals(21f, weapon.damagePerShot());
        assertEquals(0.5f, weapon.cooldownSeconds());
        assertEquals(150f, weapon.range());

        WorldSimulation world = DemoGalaxyFactory.create(13_013L);
        SimulationSession session = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        FleetPlacementState enemyFleet = findCombatFleet(world, session);
        Entity enemy = session.getEntityRegistry().find(enemyFleet.localEntityId());
        assertNotNull(enemy);
        FactionComponent enemyFaction = enemy.getComponent(FactionComponent.class);
        assertNotNull(enemyFaction);

        FleetPlacementState playerFleet = findDifferentFactionNonCombatFleet(world, session, enemyFaction.factionId);
        Entity playerShip = session.getEntityRegistry().find(playerFleet.localEntityId());
        assertNotNull(playerShip);
        FactionComponent playerFaction = playerShip.getComponent(FactionComponent.class);
        assertNotNull(playerFaction);

        playerShip.add(new CombatComponent(320f, 320f, 180f, 180f, 42f, 150f));
        playerShip.add(new CombatRuntimeComponent("weapon.pulse_laser_mk1"));
        CombatComponent enemyCombat = enemy.getComponent(CombatComponent.class);
        enemyCombat.hull = 42f;
        enemyCombat.maxHull = 42f;
        enemyCombat.shields = 0f;
        enemyCombat.maxShields = 0f;

        InventoryComponent enemyInventory = enemy.getComponent(InventoryComponent.class);
        enemyInventory.capacity = Math.max(enemyInventory.capacity, 10);
        enemyInventory.stock[content.findItem("item.weapons").runtimeId()] = 3;

        TransformComponent playerTransform = playerShip.getComponent(TransformComponent.class);
        TransformComponent enemyTransform = enemy.getComponent(TransformComponent.class);
        playerTransform.position.set(0f, 0f);
        playerTransform.velocity.setZero();
        enemyTransform.position.set(50f, 0f);
        enemyTransform.velocity.setZero();

        String playerFactionId = content.findFaction(playerFaction.factionId).id();
        PlayerState player = new PlayerState(
                20_000_000L,
                playerFactionId,
                List.of(new PlayerReputationState(playerFactionId, 0f)),
                List.of(playerFleet.id()),
                playerFleet.id(),
                List.of(DemoGalaxyFactory.ACTIVE_SYSTEM_ID),
                List.of(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        PlayerRuntime runtime = PlayerRuntime.create(world, content, player);
        assertTrue(runtime.selectCombatTarget(enemyFleet.localEntityId()));
        assertTrue(runtime.setFireIntent(true));

        float playerShieldsBefore = playerShip.getComponent(CombatComponent.class).shields;
        CombatDestructionResolver.ResolvedCombatDestruction resolved = null;
        for (int step = 0; step < 20 && resolved == null; step++) {
            world.advanceFrame(0.1f);
            List<CombatDestructionResolver.ResolvedCombatDestruction> deaths =
                    CombatDestructionResolver.resolve(world);
            if (!deaths.isEmpty()) {
                resolved = deaths.get(0);
            }
        }

        assertNotNull(resolved, "Combat should produce a lethal world destruction event");
        assertEquals(enemyFleet.localEntityId(), resolved.request().victimId());
        assertEquals(3L, resolved.destructionResult().transferredResourceUnits());
        assertNotNull(resolved.destructionResult().salvageEntityId());
        assertFalse(world.findFleet(enemyFleet.id()).isPresent(), "Destroyed physical fleet must leave world registry");
        assertTrue(world.findFleet(playerFleet.id()).isPresent(), "Player physical fleet must survive the exchange");
        assertTrue(playerShip.getComponent(CombatComponent.class).shields < playerShieldsBefore,
                "AI must fire back through the same combat system");
    }

    @Test
    void sharedControllerRejectsRangeAndCooldownAndAppliesShieldsBeforeHull() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        SimulationSession session = SimulationSession.createDemo(13_014L, content);
        Entity attacker = null;
        for (Entity entity : session.getEngine().getEntities()) {
            if (entity.getComponent(CombatComponent.class) != null) {
                attacker = entity;
                break;
            }
        }
        assertNotNull(attacker);
        Entity target = new Entity()
                .add(new com.spacesim.components.ArchetypeComponent("ship.guard_frigate"))
                .add(new CombatComponent(100f, 100f, 10f, 10f, 42f, 150f))
                .add(new CombatRuntimeComponent("weapon.pulse_laser_mk1"));
        TransformComponent targetTransform = new TransformComponent();
        target.add(targetTransform);
        TransformComponent attackerTransform = attacker.getComponent(TransformComponent.class);

        targetTransform.position.set(attackerTransform.position.x + 151f, attackerTransform.position.y);
        assertEquals(CombatController.FireStatus.OUT_OF_RANGE,
                CombatController.tryFire(attacker, target, content).status());

        targetTransform.position.set(attackerTransform.position.x + 50f, attackerTransform.position.y);
        CombatController.FireResult first = CombatController.tryFire(attacker, target, content);
        assertTrue(first.fired());
        assertEquals(10f, first.shieldDamage());
        assertEquals(11f, first.hullDamage());
        assertEquals(0f, target.getComponent(CombatComponent.class).shields);
        assertEquals(89f, target.getComponent(CombatComponent.class).hull);
        assertEquals(CombatController.FireStatus.COOLDOWN,
                CombatController.tryFire(attacker, target, content).status());
    }

    private static FleetPlacementState findCombatFleet(WorldSimulation world, SimulationSession session) {
        for (FleetPlacementState fleet : world.getFleetPlacements()) {
            if (fleet.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !DemoGalaxyFactory.ACTIVE_SYSTEM_ID.equals(fleet.systemId())) {
                continue;
            }
            Entity entity = session.getEntityRegistry().find(fleet.localEntityId());
            if (entity != null && entity.getComponent(CombatComponent.class) != null) {
                return fleet;
            }
        }
        throw new AssertionError("Demo galaxy has no combat FleetId in active system");
    }

    private static FleetPlacementState findDifferentFactionNonCombatFleet(
            WorldSimulation world,
            SimulationSession session,
            int enemyFactionId) {
        for (FleetPlacementState fleet : world.getFleetPlacements()) {
            if (fleet.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !DemoGalaxyFactory.ACTIVE_SYSTEM_ID.equals(fleet.systemId())) {
                continue;
            }
            Entity entity = session.getEntityRegistry().find(fleet.localEntityId());
            FactionComponent faction = entity == null ? null : entity.getComponent(FactionComponent.class);
            if (entity != null
                    && entity.getComponent(CombatComponent.class) == null
                    && faction != null
                    && faction.factionId != enemyFactionId
                    && entity.getComponent(TransformComponent.class) != null) {
                return fleet;
            }
        }
        throw new AssertionError("Demo galaxy has no different-faction player fleet for combat setup");
    }
}
