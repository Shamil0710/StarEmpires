package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.WorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17FStockProductionPolicyAcceptanceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String DYNAMIC_ID = "faction.player.stock_policy";
    private static final int DYNAMIC_RUNTIME_ID = Constants.LEGACY_FACTION_COUNT;

    @Test
    void authoringIsPurePersistentPolicyAndExplicitApplyUsesOrdinaryPhysicalConfiguration() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F40001L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        Entity arsenal = ownedArchetype(world, TRADE_LEAGUE, "station.arsenal");
        int energyId = content.findItem("item.energy").runtimeId();
        MarketComponent market = arsenal.getComponent(MarketComponent.class);
        InventoryComponent inventory = arsenal.getComponent(InventoryComponent.class);
        WalletComponent wallet = arsenal.getComponent(WalletComponent.class);
        ProductionComponent production = arsenal.getComponent(ProductionComponent.class);
        assertNotNull(market);
        assertNotNull(inventory);
        assertNotNull(wallet);
        assertNotNull(production);

        FactionStrategicState beforeStrategy = world.findFactionStrategicState(TRADE_LEAGUE).orElseThrow();
        long treasuryBefore = world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits();
        int targetBefore = market.targetStock[energyId];
        int stockBefore = inventory.stock[energyId];
        long walletBefore = wallet.getBalanceMilliCredits();
        String recipeBefore = production.getActiveRecipe().name;
        float progressBefore = production.progressSeconds;

        FactionStockProductionPolicyState requested = new FactionStockProductionPolicyState(
                List.of(new FactionStockPolicyState("item.energy", 913)),
                List.of(new FactionProductionPolicyState("station.arsenal", "recipe.food_growing")));
        assertEquals(requested, world.updateFactionStockProductionPolicy(TRADE_LEAGUE, requested));

        FactionStrategicState authored = world.findFactionStrategicState(TRADE_LEAGUE).orElseThrow();
        assertEquals(requested, world.findFactionStockProductionPolicy(TRADE_LEAGUE).orElseThrow());
        assertEquals(beforeStrategy.minimumMarketAccessRelation(), authored.minimumMarketAccessRelation());
        assertEquals(beforeStrategy.relations(), authored.relations());
        assertEquals(beforeStrategy.controlledSystems(), authored.controlledSystems());
        assertEquals(beforeStrategy.stationTaxBasisPoints(), authored.stationTaxBasisPoints());
        assertEquals(beforeStrategy.foreignTerritoryTariffBasisPoints(), authored.foreignTerritoryTariffBasisPoints());
        assertEquals(beforeStrategy.strategicGoals(), authored.strategicGoals());
        assertEquals(beforeStrategy.territorialClaims(), authored.territorialClaims());
        assertEquals(beforeStrategy.territorialControlStates(), authored.territorialControlStates());
        assertEquals(beforeStrategy.territorialRecognitions(), authored.territorialRecognitions());
        assertEquals(beforeStrategy.constructionRightsGranted(), authored.constructionRightsGranted());
        assertEquals(beforeStrategy.doctrine(), authored.doctrine());
        assertEquals(treasuryBefore, world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits());
        assertEquals(targetBefore, market.targetStock[energyId]);
        assertEquals(stockBefore, inventory.stock[energyId]);
        assertEquals(walletBefore, wallet.getBalanceMilliCredits());
        assertEquals(recipeBefore, production.getActiveRecipe().name);
        assertEquals(progressBefore, production.progressSeconds);

        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(world.snapshot()));
        WorldSimulation restored = WorldSimulation.restore(
                decoded,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        assertEquals(requested, restored.findFactionStockProductionPolicy(TRADE_LEAGUE).orElseThrow());
        Entity restoredArsenal = ownedArchetype(restored, TRADE_LEAGUE, "station.arsenal");
        InventoryComponent restoredInventory = restoredArsenal.getComponent(InventoryComponent.class);
        WalletComponent restoredWallet = restoredArsenal.getComponent(WalletComponent.class);
        int totalStockBeforeApply = restoredInventory.getTotalStock();
        long restoredWalletBefore = restoredWallet.getBalanceMilliCredits();
        long restoredTreasuryBefore = restored.findFactionEconomicState(TRADE_LEAGUE)
                .orElseThrow().treasuryMilliCredits();

        FactionStrategicPolicyEngine.ApplicationReport report =
                restored.applyFactionStrategicPolicy(TRADE_LEAGUE);

        assertTrue(report.marketsAdjusted() > 0);
        assertTrue(report.productionStationsRetooled() > 0,
                "Every matching owned arsenal may be retooled across the multi-system world");
        MarketComponent appliedMarket = restoredArsenal.getComponent(MarketComponent.class);
        ProductionComponent appliedProduction = restoredArsenal.getComponent(ProductionComponent.class);
        assertEquals(913, appliedMarket.targetStock[energyId]);
        assertEquals("Выращивание продовольствия", appliedProduction.getActiveRecipe().name);
        assertEquals(0f, appliedProduction.progressSeconds);
        assertEquals(totalStockBeforeApply, restoredInventory.getTotalStock(),
                "Policy apply must not create cargo");
        assertEquals(restoredWalletBefore, restoredWallet.getBalanceMilliCredits(),
                "Policy apply must not create or spend station money");
        assertEquals(restoredTreasuryBefore,
                restored.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits(),
                "Policy apply must not create or spend treasury money");
    }

    @Test
    void invalidSemanticReferencesAreRejectedBeforePersistentMutation() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F40002L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        FactionStockProductionPolicyState before =
                world.findFactionStockProductionPolicy(TRADE_LEAGUE).orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> world.updateFactionStockProductionPolicy(
                TRADE_LEAGUE,
                new FactionStockProductionPolicyState(
                        List.of(new FactionStockPolicyState("item.not_real", 500)),
                        List.of())));
        assertEquals(before, world.findFactionStockProductionPolicy(TRADE_LEAGUE).orElseThrow());

        assertThrows(IllegalArgumentException.class, () -> world.updateFactionStockProductionPolicy(
                TRADE_LEAGUE,
                new FactionStockProductionPolicyState(
                        List.of(),
                        List.of(new FactionProductionPolicyState(
                                "station.arsenal", "recipe.not_real")))));
        assertEquals(before, world.findFactionStockProductionPolicy(TRADE_LEAGUE).orElseThrow());
    }

    @Test
    void worldDefinedPlayerFactionUsesTheSameAuthoringAndExecutorBoundary() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation base = DemoGalaxyFactory.create(0x17F40003L);
        int energyId = content.findItem("item.energy").runtimeId();
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(energyId, 100, 0f);
        Entity station = new Entity()
                .add(new IdentityComponent("Player Policy Market", IdentityComponent.Kind.STATION))
                .add(new InventoryComponent())
                .add(market)
                .add(new FactionComponent(DYNAMIC_RUNTIME_ID));
        EntityId stationId = base.createEntity(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, station);
        WorldState state = withDynamicFaction(
                base.snapshot(),
                new FactionStrategicState(DYNAMIC_ID, 0, List.of(), List.of()));
        WorldSimulation world = WorldSimulation.restore(
                state,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);

        FactionStockProductionPolicyState policy = new FactionStockProductionPolicyState(
                List.of(new FactionStockPolicyState("item.energy", 850)),
                List.of());
        assertEquals(policy, world.updateFactionStockProductionPolicy(DYNAMIC_ID, policy));
        Entity dynamicMarket = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .orElseThrow().getEntityRegistry().find(stationId);
        assertNotNull(dynamicMarket);
        assertEquals(100, dynamicMarket.getComponent(MarketComponent.class).targetStock[energyId],
                "Authoring must not mutate the market immediately");

        FactionStrategicPolicyEngine.ApplicationReport report = world.applyFactionStrategicPolicy(DYNAMIC_ID);

        assertEquals(1, report.marketsAdjusted());
        assertEquals(0, report.productionStationsRetooled());
        assertEquals(850, dynamicMarket.getComponent(MarketComponent.class).targetStock[energyId]);
        assertEquals(0, dynamicMarket.getComponent(InventoryComponent.class).getTotalStock());
    }

    private static WorldState withDynamicFaction(
            WorldState source,
            FactionStrategicState dynamicStrategy) {
        List<FactionEconomicState> factions = new ArrayList<>(source.factions());
        factions.add(new FactionEconomicState(DYNAMIC_ID, 0L, 0L, 0L));
        List<FactionStrategicState> strategies = new ArrayList<>(source.factionStrategies());
        strategies.add(dynamicStrategy);
        List<WorldFactionIdentityState> identities = new ArrayList<>(source.factionIdentities());
        identities.add(new WorldFactionIdentityState(
                DYNAMIC_ID,
                DYNAMIC_RUNTIME_ID,
                "Player Stock Policy Faction",
                WorldFactionIdentityState.Origin.PLAYER_CREATED));
        List<FactionDiplomacyState> diplomacy = new ArrayList<>(source.factionDiplomacyStates());
        diplomacy.add(FactionDiplomacyState.neutral(DYNAMIC_ID));
        return new WorldState(
                WorldState.CURRENT_VERSION,
                source.topology(),
                source.systems(),
                factions,
                strategies,
                source.nextConstructionProjectIdValue(),
                source.constructionProjects(),
                source.factionEconomicPressures(),
                source.nextFleetIdValue(),
                source.fleets(),
                source.fleetJumps(),
                identities,
                diplomacy);
    }

    private static Entity ownedArchetype(
            WorldSimulation world,
            String factionContentId,
            String archetypeId) {
        int factionId = world.findFactionRuntimeId(factionContentId).orElseThrow();
        for (Entity entity : world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow()
                .getEngine().getEntities()) {
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
            if (faction != null
                    && faction.factionId == factionId
                    && archetype != null
                    && archetype.contentId.equals(archetypeId)) {
                return entity;
            }
        }
        throw new AssertionError("Archetype not found: " + archetypeId);
    }
}
