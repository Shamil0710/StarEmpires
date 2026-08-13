package com.spacesim.benchmark;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.model.ShipType;
import com.spacesim.persistence.EntityId;
import com.spacesim.world.DestructionPolicy;
import com.spacesim.world.MoneyDestructionFate;
import com.spacesim.world.ResourceDestructionFate;
import com.spacesim.world.WorldSimulation;

final class Stage9ESetup {
    private static final String MINERS = "faction.miners";
    private static final String STEEL = "item.steel";

    private Stage9ESetup() {
        throw new AssertionError("Utility class");
    }

    static EntityId createReserve(WorldSimulation world, ContentCatalog content, Entity foundry) {
        TransformComponent source = foundry.getComponent(TransformComponent.class);
        TransformComponent transform = new TransformComponent();
        transform.position.set(source.position.x, source.position.y);
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 5_000;
        TradeAIComponent trade = new TradeAIComponent();
        trade.specializedItem = content.findItem(STEEL).runtimeId();
        trade.cargoSpace = 5_000;
        trade.movementSpeed = 180f;
        trade.routeSearchCooldown = 35f;
        Entity reserve = new Entity()
                .add(new IdentityComponent("Corona Strategic Reserve", IdentityComponent.Kind.FLEET))
                .add(transform)
                .add(inventory)
                .add(new WalletComponent())
                .add(trade)
                .add(new ShipComponent(ShipType.MATERIAL_CARRIER))
                .add(new FactionComponent(content.findFaction(MINERS).runtimeId()));
        return world.createEntity(DemoGalaxyFactory.INNER_SYSTEM_ID, reserve);
    }

    static void applyShock(WorldSimulation world, Entity foundry, EntityId reserveId) {
        var inner = world.findSession(DemoGalaxyFactory.INNER_SYSTEM_ID).orElseThrow();
        world.destroyEntity(
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                inner.getEntityRegistry().idOf(foundry),
                new DestructionPolicy(
                        ResourceDestructionFate.TRANSFER_TO_ENTITY,
                        MoneyDestructionFate.SINK,
                        reserveId));
    }
}
