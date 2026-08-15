from pathlib import Path

path = Path('src/test/java/com/spacesim/world/Stage17EEmbargoAcceptanceTest.java')
text = path.read_text()
old_seller = '''    private static EntityId createSeller(WorldSimulation world, int factionRuntimeId, int itemId) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 100;
        inventory.stock[itemId] = 20;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(itemId, 10, 0f);
        market.sellPrices[itemId] = 10f;
        market.buyPrices[itemId] = 8f;
        market.isDirty = false;
        return world.createEntity(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                new Entity()
                        .add(new IdentityComponent("Embargo Seller", IdentityComponent.Kind.STATION))
                        .add(new FactionComponent(factionRuntimeId))
                        .add(inventory)
                        .add(market)
                        .add(new WalletComponent(100_000L)));
    }
'''
new_seller = '''    private static EntityId createSeller(WorldSimulation world, int factionRuntimeId, int itemId) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 100;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(itemId, 10, 0f);
        market.sellPrices[itemId] = 10f;
        market.buyPrices[itemId] = 8f;
        market.isDirty = false;
        EntityId id = world.createEntity(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                new Entity()
                        .add(new IdentityComponent("Embargo Seller", IdentityComponent.Kind.STATION))
                        .add(new FactionComponent(factionRuntimeId))
                        .add(inventory)
                        .add(market)
                        .add(new WalletComponent()));
        Entity registered = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow()
                .getEntityRegistry().find(id);
        registered.getComponent(InventoryComponent.class).stock[itemId] = 20;
        assertTrue(registered.getComponent(WalletComponent.class).creditFromSource(100_000L));
        return id;
    }
'''
old_buyer = '''    private static EntityId createBuyer(WorldSimulation world, int factionRuntimeId) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 100;
        return world.createEntity(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                new Entity()
                        .add(new IdentityComponent("Embargo Buyer", IdentityComponent.Kind.FLEET))
                        .add(new FactionComponent(factionRuntimeId))
                        .add(inventory)
                        .add(new WalletComponent(1_000_000L)));
    }
'''
new_buyer = '''    private static EntityId createBuyer(WorldSimulation world, int factionRuntimeId) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 100;
        EntityId id = world.createEntity(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                new Entity()
                        .add(new IdentityComponent("Embargo Buyer", IdentityComponent.Kind.FLEET))
                        .add(new FactionComponent(factionRuntimeId))
                        .add(inventory)
                        .add(new WalletComponent()));
        Entity registered = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow()
                .getEntityRegistry().find(id);
        assertTrue(registered.getComponent(WalletComponent.class).creditFromSource(1_000_000L));
        return id;
    }
'''
for label, old, new in [('seller fixture', old_seller, new_seller), ('buyer fixture', old_buyer, new_buyer)]:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected once, found {count}')
    text = text.replace(old, new, 1)
path.write_text(text)
