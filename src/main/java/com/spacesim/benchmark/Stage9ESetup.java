package com.spacesim.benchmark;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.model.ShipType;
import com.spacesim.persistence.EntityId;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.DestructionPolicy;
import com.spacesim.world.MoneyDestructionFate;
import com.spacesim.world.ResourceDestructionFate;
import com.spacesim.world.WorldSimulation;

final class Stage9ESetup {
    private static final String MINERS = "faction.miners";
    private static final String STEEL = "item.steel";
    private static final String ENERGY = "item.energy";

    private Stage9ESetup() {
        throw new AssertionError("Utility class");
    }

    static EntityId createReserveNetwork(WorldSimulation world, ContentCatalog content, Entity foundry) {
        TransformComponent zone = replacementZone(world);
        Entity salvage = salvageVault(foundry, content);
        Entity steel = reserveCarrier(
                "Corona Steel Reserve",
                zone.position.x - 20f,
                zone.position.y,
                content,
                STEEL,
                ShipType.MATERIAL_CARRIER,
                500);
        Entity energy = reserveCarrier(
                "Corona Energy Reserve",
                zone.position.x + 20f,
                zone.position.y,
                content,
                ENERGY,
                ShipType.GAS_LIQUID_CARRIER,
                300);
        EntityId salvageId = world.createEntity(DemoGalaxyFactory.INNER_SYSTEM_ID, salvage);
        world.createEntity(DemoGalaxyFactory.INNER_SYSTEM_ID, steel);
        world.createEntity(DemoGalaxyFactory.INNER_SYSTEM_ID, energy);
        steel.getComponent(InventoryComponent.class).stock[content.findItem(STEEL).runtimeId()] = 500;
        energy.getComponent(InventoryComponent.class).stock[content.findItem(ENERGY).runtimeId()] = 300;
        return salvageId;
    }

    static void releaseReserves(SimulationSession session) {
        for (Entity entity : session.getEngine().getEntities()) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            TradeAIComponent trade = entity.getComponent(TradeAIComponent.class);
            if (identity != null
                    && trade != null
                    && ("Corona Steel Reserve".equals(identity.name)
                    || "Corona Energy Reserve".equals(identity.name))) {
                trade.routeSearchCooldown = 0f;
            }
        }
    }

    static void applyShock(WorldSimulation world, Entity foundry, EntityId salvageId) {
        EntityIdComponent foundryId = foundry.getComponent(EntityIdComponent.class);
        if (foundryId == null || foundryId.id == null) {
            throw new IllegalStateException("Stage 9E foundry не имеет persistent EntityId");
        }
        world.destroyEntity(
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                foundryId.id,
                new DestructionPolicy(
                        ResourceDestructionFate.TRANSFER_TO_ENTITY,
                        MoneyDestructionFate.SINK,
                        salvageId));
    }

    private static Entity salvageVault(Entity foundry, ContentCatalog content) {
        TransformComponent source = foundry.getComponent(TransformComponent.class);
        TransformComponent transform = new TransformComponent();
        transform.position.set(source.position);
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 5_000;
        return new Entity()
                .add(new IdentityComponent("Corona Salvage Vault", IdentityComponent.Kind.SALVAGE))
                .add(transform)
                .add(inventory)
                .add(new FactionComponent(content.findFaction(MINERS).runtimeId()));
    }

    private static Entity reserveCarrier(
            String name,
            float x,
            float y,
            ContentCatalog content,
            String itemContentId,
            ShipType type,
            int capacity) {
        int itemId = content.findItem(itemContentId).runtimeId();
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = capacity;
        TradeAIComponent trade = new TradeAIComponent();
        trade.specializedItem = itemId;
        trade.cargoSpace = capacity;
        trade.movementSpeed = 220f;
        trade.routeSearchCooldown = Float.MAX_VALUE;
        return new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.FLEET))
                .add(transform)
                .add(inventory)
                .add(new WalletComponent())
                .add(trade)
                .add(new ShipComponent(type))
                .add(new FactionComponent(content.findFaction(MINERS).runtimeId()));
    }

    private static TransformComponent replacementZone(WorldSimulation world) {
        var session = world.findSession(DemoGalaxyFactory.INNER_SYSTEM_ID).orElseThrow();
        TransformComponent result = new TransformComponent();
        long bestId = Long.MAX_VALUE;
        for (Entity entity : session.getEngine().getEntities()) {
            EntityIdComponent id = entity.getComponent(EntityIdComponent.class);
            TransformComponent position = entity.getComponent(TransformComponent.class);
            if (id != null
                    && position != null
                    && entity.getComponent(MarketComponent.class) != null
                    && id.id.value() < bestId) {
                bestId = id.id.value();
                result.position.set(position.position.x + 60f, position.position.y + 60f);
            }
        }
        if (bestId == Long.MAX_VALUE) {
            result.position.set(1000f, 700f);
        }
        return result;
    }
}
