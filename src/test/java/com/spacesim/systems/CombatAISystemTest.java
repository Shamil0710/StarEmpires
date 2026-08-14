package com.spacesim.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.CombatCommandComponent;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.CombatRuntimeComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CombatAISystemTest {
    @Test
    void equalDistanceTargetsUseStableLowestEntityIdTieBreak() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        Engine engine = new Engine();
        engine.addSystem(new CombatAISystem(content));

        Entity attacker = combatant(100L, 0, 0f, 0f);
        Entity higherIdTarget = combatant(300L, 1, 50f, 0f);
        Entity lowerIdTarget = combatant(200L, 1, -50f, 0f);
        engine.addEntity(attacker);
        engine.addEntity(higherIdTarget);
        engine.addEntity(lowerIdTarget);

        engine.update(0.1f);

        CombatCommandComponent command = attacker.getComponent(CombatCommandComponent.class);
        assertNotNull(command);
        assertEquals(new EntityId(200L), command.targetId);
        assertEquals(true, command.fireRequested);
    }

    private static Entity combatant(long id, int factionId, float x, float y) {
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        return new Entity()
                .add(new EntityIdComponent(new EntityId(id)))
                .add(new FactionComponent(factionId))
                .add(transform)
                .add(new CombatComponent(100f, 100f, 0f, 0f, 42f, 150f))
                .add(new CombatRuntimeComponent("weapon.pulse_laser_mk1"));
    }
}
