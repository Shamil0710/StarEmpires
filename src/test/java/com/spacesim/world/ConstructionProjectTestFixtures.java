package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.persistence.EntityId;

/** Shared test-only helpers for physical construction deliveries. */
final class ConstructionProjectTestFixtures {
    private ConstructionProjectTestFixtures() {
        throw new AssertionError("ConstructionProjectTestFixtures не создаёт экземпляров");
    }

    static EntityId createLoadedCargo(
            WorldSimulation world,
            ContentCatalog content,
            StarSystemId systemId,
            int steel,
            int energy) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 1_000;
        TransformComponent transform = new TransformComponent();
        transform.position.set(200f, 200f);
        Entity cargo = new Entity()
                .add(new IdentityComponent("Construction test cargo", IdentityComponent.Kind.FLEET))
                .add(transform)
                .add(inventory);
        EntityId id = world.createEntity(systemId, cargo);
        inventory.stock[content.findItem("item.steel").runtimeId()] = steel;
        inventory.stock[content.findItem("item.energy").runtimeId()] = energy;
        return id;
    }
}
