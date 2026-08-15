package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.economy.Money;
import com.spacesim.persistence.EntityId;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DestructionServiceIntegrationTest {
    private static final long ROOT_SEED = 0x9C2026L;
    private static final String MINERS = "faction.miners";

    @Test
    void destroyAllЯвноСписываетMoneyResourcesУдаляетMarketProductionИПубликуетNews() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);
        SimulationSession session = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        EntityId targetId = createEconomicTarget(world, content, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 40, 25_000L);

        DestructionResult result = world.destroyEntity(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID, targetId, DestructionPolicy.destroyAll());

        assertEquals(targetId, result.destroyedEntityId());
        assertEquals(40L, result.destroyedResourceUnits());
        assertEquals(25_000L, result.sunkMoneyMilliCredits());
        assertEquals(0L, result.transferredResourceUnits());
        assertEquals(0L, result.transferredMoneyMilliCredits());
        assertTrue(result.removedMarket());
        assertTrue(result.removedProduction());
        assertFalse(session.getEntityRegistry().contains(targetId));
        assertEquals(40L, ledgerItemAmount(session, EconomicTransaction.Type.RESOURCE_SINK, "entity-destruction"));
        assertEquals(25_000L, ledgerMoney(session, EconomicTransaction.Type.MONEY_SINK, "entity-destruction"));

        List<com.spacesim.events.NewsArticle> news = session.getEventManager().consumePendingNews();
        assertEquals(1, news.size());
        assertTrue(news.get(0).headline.contains("Уничтожен"));
    }

    @Test
    void transferPolicyСохраняетResourcesБезSourceSink() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);
        SimulationSession session = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        EntityId targetId = createInventoryTarget(world, content, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 35);
        EntityId recipientId = createEmptyCargo(world, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 100);

        DestructionResult result = world.destroyEntity(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                targetId,
                new DestructionPolicy(
                        ResourceDestructionFate.TRANSFER_TO_ENTITY,
                        MoneyDestructionFate.SINK,
                        recipientId));

        Entity recipient = session.getEntityRegistry().require(recipientId);
        int steelId = content.findItem("item.steel").runtimeId();
        assertEquals(35, recipient.getComponent(InventoryComponent.class).stock[steelId]);
        assertEquals(35L, result.transferredResourceUnits());
        assertEquals(0L, result.destroyedResourceUnits());
        assertEquals(35L, ledgerItemAmount(
                session, EconomicTransaction.Type.RESOURCE_TRANSFER, "entity-destruction-transfer"));
        assertEquals(0L, ledgerItemAmount(session, EconomicTransaction.Type.RESOURCE_SINK, "entity-destruction"));
    }

    @Test
    void salvagePolicyСоздаётPersistentPhysicalSalvage() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);
        EntityId targetId = createInventoryTarget(world, content, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 22);

        DestructionResult result = world.destroyEntity(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID, targetId, DestructionPolicy.salvageResources());

        assertNotNull(result.salvageEntityId());
        SimulationSession session = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        Entity salvage = session.getEntityRegistry().require(result.salvageEntityId());
        assertEquals(IdentityComponent.Kind.SALVAGE, salvage.getComponent(IdentityComponent.class).kind);
        assertEquals(22, salvage.getComponent(InventoryComponent.class).getTotalStock());

        WorldSimulation restored = WorldSimulation.restore(world.snapshot(), DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        Entity restoredSalvage = restored.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow()
                .getEntityRegistry().require(result.salvageEntityId());
        assertEquals(22, restoredSalvage.getComponent(InventoryComponent.class).getTotalStock());
        assertEquals(IdentityComponent.Kind.SALVAGE, restoredSalvage.getComponent(IdentityComponent.class).kind);
    }

    @Test
    void moneyCanReturnToOwnerTreasuryWithoutCurrencySink() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);
        long treasuryBefore = world.findFactionEconomicState(MINERS).orElseThrow().treasuryMilliCredits();
        EntityId targetId = createEconomicTarget(world, content, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 0, 75_000L);

        DestructionResult result = world.destroyEntity(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                targetId,
                new DestructionPolicy(
                        ResourceDestructionFate.DESTROY,
                        MoneyDestructionFate.TRANSFER_TO_FACTION_TREASURY,
                        null));

        assertEquals(75_000L, result.transferredMoneyMilliCredits());
        assertEquals(0L, result.sunkMoneyMilliCredits());
        assertEquals(treasuryBefore + 75_000L,
                world.findFactionEconomicState(MINERS).orElseThrow().treasuryMilliCredits());
    }

    @Test
    void destroyedConstructionSiteПереходитВFailedСИсториейДоставки() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);
        ConstructionProjectId projectId = ConstructionProjectTestFixtures.createAuthorizedProject(world,
                MINERS, "station.mining_base", DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 400f, 450f);
        world.fundConstructionProject(projectId, Money.fromCredits(25_000d));
        EntityId cargo = ConstructionProjectTestFixtures.createLoadedCargo(
                world, content, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 15, 0);
        world.deliverConstructionMaterial(projectId, cargo, "item.steel", 15);
        ConstructionProjectState before = world.findConstructionProject(projectId).orElseThrow();

        DestructionResult result = world.destroyEntity(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                before.constructionSiteEntityId(),
                DestructionPolicy.destroyAll());

        ConstructionProjectState failed = world.findConstructionProject(projectId).orElseThrow();
        assertEquals(ConstructionProjectStatus.FAILED, failed.status());
        assertNull(failed.constructionSiteEntityId());
        assertEquals(15L, failed.totalDeliveredUnits(), "FAILED history сохраняет delivered amount до destruction");
        assertEquals(projectId, result.failedConstructionProject());
        assertEquals(0L, failed.projectWalletMilliCredits());
        WorldSimulation restored = WorldSimulation.restore(world.snapshot(), DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        assertEquals(ConstructionProjectStatus.FAILED,
                restored.findConstructionProject(projectId).orElseThrow().status());
    }

    @Test
    void completedConstructionHistoryПереживаетПозднееУничтожениеStation() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);
        ConstructionProjectId projectId = ConstructionProjectTestFixtures.createAuthorizedProject(world,
                MINERS, "station.mining_base", DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 470f, 520f);
        world.fundConstructionProject(projectId, Money.fromCredits(25_000d));
        EntityId cargo = ConstructionProjectTestFixtures.createLoadedCargo(
                world, content, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 120, 60);
        world.deliverConstructionMaterial(projectId, cargo, "item.steel", 120);
        world.deliverConstructionMaterial(projectId, cargo, "item.energy", 60);
        for (int frame = 0; frame < 1_000; frame++) {
            world.advanceFrame(0.1f);
            if (world.findConstructionProject(projectId).orElseThrow().status()
                    == ConstructionProjectStatus.COMPLETED) {
                break;
            }
        }
        ConstructionProjectState completed = world.findConstructionProject(projectId).orElseThrow();
        assertEquals(ConstructionProjectStatus.COMPLETED, completed.status());

        world.destroyEntity(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                completed.completedStationEntityId(),
                DestructionPolicy.destroyAll());
        assertFalse(world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow()
                .getEntityRegistry().contains(completed.completedStationEntityId()));

        WorldSimulation restored = WorldSimulation.restore(world.snapshot(), DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        ConstructionProjectState historical = restored.findConstructionProject(projectId).orElseThrow();
        assertEquals(ConstructionProjectStatus.COMPLETED, historical.status());
        assertEquals(completed.completedStationEntityId(), historical.completedStationEntityId());
    }

    @Test
    void remoteDestructionСохраняетсяЧерезWorldSnapshot() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);
        EntityId target = createInventoryTarget(world, content, DemoGalaxyFactory.FRONTIER_SYSTEM_ID, 9);
        DestructionResult result = world.destroyEntity(
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID, target, DestructionPolicy.salvageResources());

        WorldSimulation restored = WorldSimulation.restore(world.snapshot(), DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        SimulationSession remote = restored.findSession(DemoGalaxyFactory.FRONTIER_SYSTEM_ID).orElseThrow();
        assertFalse(remote.getEntityRegistry().contains(target));
        assertNotNull(remote.getEntityRegistry().find(result.salvageEntityId()));
    }

    private static EntityId createEconomicTarget(
            WorldSimulation world,
            ContentCatalog content,
            StarSystemId systemId,
            int steel,
            long money) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 500;
        TransformComponent transform = new TransformComponent();
        transform.position.set(650f, 420f);
        Entity entity = new Entity()
                .add(new IdentityComponent("Destruction target", IdentityComponent.Kind.STATION))
                .add(transform)
                .add(inventory)
                .add(new WalletComponent())
                .add(new MarketComponent())
                .add(new ProductionComponent())
                .add(new FactionComponent(content.findFaction(MINERS).runtimeId()));
        EntityId id = world.createEntity(systemId, entity);
        int steelId = content.findItem("item.steel").runtimeId();
        inventory.stock[steelId] = steel;
        SimulationSession session = world.findSession(systemId).orElseThrow();
        if (steel > 0) {
            session.getLedger().recordResourceSource("test-destruction-target", steelId, steel, "test-setup");
        }
        if (money > 0L) {
            WalletComponent wallet = entity.getComponent(WalletComponent.class);
            assertTrue(wallet.creditFromSource(money));
            session.getLedger().recordMoneySource("test-destruction-target", money, "test-setup");
        }
        return id;
    }

    private static EntityId createInventoryTarget(
            WorldSimulation world, ContentCatalog content, StarSystemId systemId, int steel) {
        return createEconomicTarget(world, content, systemId, steel, 0L);
    }

    private static EntityId createEmptyCargo(WorldSimulation world, StarSystemId systemId, int capacity) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = capacity;
        TransformComponent transform = new TransformComponent();
        transform.position.set(700f, 430f);
        Entity entity = new Entity()
                .add(new IdentityComponent("Destruction recipient", IdentityComponent.Kind.FLEET))
                .add(transform)
                .add(inventory);
        return world.createEntity(systemId, entity);
    }

    private static long ledgerItemAmount(
            SimulationSession session, EconomicTransaction.Type type, String reason) {
        return session.getLedger().getEntries().stream()
                .filter(entry -> entry.type() == type && entry.reason().equals(reason))
                .mapToLong(EconomicTransaction::itemAmount)
                .sum();
    }

    private static long ledgerMoney(
            SimulationSession session, EconomicTransaction.Type type, String reason) {
        return session.getLedger().getEntries().stream()
                .filter(entry -> entry.type() == type && entry.reason().equals(reason))
                .mapToLong(EconomicTransaction::moneyMilliCredits)
                .sum();
    }
}
