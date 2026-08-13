package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.economy.Money;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.WorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionProjectIntegrationTest {
    private static final long ROOT_SEED = 0x9B2026L;
    private static final String OWNER = "faction.miners";
    private static final String TARGET = "station.foundry";

    @Test
    void projectFundingDeliveryBuildИCompletionСохраняютMoneyResourceSemantics() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);
        SimulationSession session = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        long treasuryBefore = world.findFactionEconomicState(OWNER).orElseThrow().treasuryMilliCredits();

        ConstructionProjectId projectId = world.createConstructionProject(
                OWNER, TARGET, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 910f, 640f);
        ConstructionProjectState planned = world.findConstructionProject(projectId).orElseThrow();
        assertEquals(ConstructionProjectStatus.PLANNED, planned.status());
        Entity site = session.getEntityRegistry().require(planned.constructionSiteEntityId());
        InventoryComponent siteInventory = site.getComponent(InventoryComponent.class);
        WalletComponent siteWallet = site.getComponent(WalletComponent.class);
        MarketComponent siteMarket = site.getComponent(MarketComponent.class);
        assertNotNull(siteInventory);
        assertNotNull(siteWallet);
        assertNotNull(siteMarket);
        assertEquals(0, siteInventory.getTotalStock());
        assertEquals(0L, siteWallet.getBalanceMilliCredits());

        long funding = Money.fromCredits(40_000d);
        assertEquals(funding, world.fundConstructionProject(projectId, funding));
        assertEquals(ConstructionProjectStatus.FUNDED,
                world.findConstructionProject(projectId).orElseThrow().status());
        assertEquals(treasuryBefore - funding,
                world.findFactionEconomicState(OWNER).orElseThrow().treasuryMilliCredits());
        advance(world, 1);
        assertEquals(ConstructionProjectStatus.AWAITING_MATERIALS,
                world.findConstructionProject(projectId).orElseThrow().status());

        EntityId cargoId = createLoadedCargo(world, content, 180, 120);
        assertEquals(180, world.deliverConstructionMaterial(projectId, cargoId, "item.steel", 180));
        assertEquals(120, world.deliverConstructionMaterial(projectId, cargoId, "item.energy", 120));
        ConstructionProjectState delivered = world.findConstructionProject(projectId).orElseThrow();
        assertTrue(delivered.materialsFulfilled());
        assertEquals(300L, delivered.totalDeliveredUnits());

        advance(world, 1);
        ConstructionProjectState building = world.findConstructionProject(projectId).orElseThrow();
        assertEquals(ConstructionProjectStatus.BUILDING, building.status());
        long buildDurationTicks = building.buildDurationTicks();
        assertTrue(buildDurationTicks > 0L);

        advance(world, (int) buildDurationTicks + 2);
        ConstructionProjectState completed = world.findConstructionProject(projectId).orElseThrow();
        assertEquals(ConstructionProjectStatus.COMPLETED, completed.status());
        assertNull(completed.constructionSiteEntityId());
        assertNotNull(completed.completedStationEntityId());
        assertFalse(session.getEntityRegistry().contains(planned.constructionSiteEntityId()));

        Entity station = session.getEntityRegistry().require(completed.completedStationEntityId());
        assertEquals(TARGET, station.getComponent(ArchetypeComponent.class).archetypeId);
        assertEquals(0, station.getComponent(InventoryComponent.class).getTotalStock());
        assertEquals(0L, station.getComponent(WalletComponent.class).getBalanceMilliCredits());
        assertEquals(treasuryBefore,
                world.findFactionEconomicState(OWNER).orElseThrow().treasuryMilliCredits(),
                "Manual delivery does not spend project money; full unused wallet must return to treasury");

        long constructionSinks = session.getLedger().getEntries().stream()
                .filter(entry -> entry.type() == EconomicTransaction.Type.RESOURCE_SINK)
                .filter(entry -> entry.reason().startsWith("station-construction:"))
                .mapToLong(EconomicTransaction::itemAmount)
                .sum();
        assertEquals(300L, constructionSinks);
    }

    @Test
    void partialProjectRoundTripsThroughWorldCodecAndRestore() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);
        ConstructionProjectId projectId = world.createConstructionProject(
                OWNER, TARGET, DemoGalaxyFactory.FRONTIER_SYSTEM_ID, 300f, 420f);
        assertEquals(Money.fromCredits(20_000d),
                world.fundConstructionProject(projectId, Money.fromCredits(20_000d)));

        EntityId cargoId = createLoadedCargo(
                world, content, DemoGalaxyFactory.FRONTIER_SYSTEM_ID, 60, 20);
        assertEquals(60, world.deliverConstructionMaterial(projectId, cargoId, "item.steel", 60));
        assertEquals(20, world.deliverConstructionMaterial(projectId, cargoId, "item.energy", 20));

        WorldState snapshot = world.snapshot();
        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(snapshot));
        assertEquals(snapshot, decoded);
        WorldSimulation restored = WorldSimulation.restore(decoded, DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        ConstructionProjectState restoredProject = restored.findConstructionProject(projectId).orElseThrow();
        assertEquals(80L, restoredProject.totalDeliveredUnits());
        assertEquals(Money.fromCredits(20_000d), restoredProject.projectWalletMilliCredits());
        assertNotNull(restored.findSession(DemoGalaxyFactory.FRONTIER_SYSTEM_ID).orElseThrow()
                .getEntityRegistry().find(restoredProject.constructionSiteEntityId()));
    }

    @Test
    void cancelBeforeDeliveryRefundsWalletAndRemovesSite() {
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);
        long treasuryBefore = world.findFactionEconomicState(OWNER).orElseThrow().treasuryMilliCredits();
        ConstructionProjectId projectId = world.createConstructionProject(
                OWNER, "station.mining_base", DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 120f, 140f);
        ConstructionProjectState planned = world.findConstructionProject(projectId).orElseThrow();
        long funding = Money.fromCredits(25_000d);
        world.fundConstructionProject(projectId, funding);

        assertTrue(world.cancelConstructionProject(projectId));
        ConstructionProjectState cancelled = world.findConstructionProject(projectId).orElseThrow();
        assertEquals(ConstructionProjectStatus.CANCELLED, cancelled.status());
        assertNull(cancelled.constructionSiteEntityId());
        assertFalse(world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow()
                .getEntityRegistry().contains(planned.constructionSiteEntityId()));
        assertEquals(treasuryBefore,
                world.findFactionEconomicState(OWNER).orElseThrow().treasuryMilliCredits());
    }

    private static EntityId createLoadedCargo(
            WorldSimulation world, ContentCatalog content, int steel, int energy) {
        return createLoadedCargo(world, content, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, steel, energy);
    }

    private static EntityId createLoadedCargo(
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

    private static void advance(WorldSimulation world, int ticks) {
        for (int index = 0; index < ticks; index++) {
            world.advanceFrame(0.1f);
        }
    }
}
