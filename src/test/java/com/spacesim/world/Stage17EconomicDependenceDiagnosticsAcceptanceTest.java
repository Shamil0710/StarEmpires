package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.WorldStateCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17EconomicDependenceDiagnosticsAcceptanceTest {
    private static final String SOURCE = "faction.neutral";
    private static final String PARTNER = "faction.trade_league";
    private static final String ALTERNATIVE = "faction.miners";

    @Test
    void diagnosticsMeasureCurrentStructuralSupplyAndReactToRealEmbargoAccess() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(17_701L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        ContentCatalog.ItemDefinition item = content.getItems().get(0);
        int itemId = item.runtimeId();
        clearExistingEconomicSignal(world);

        int sourceRuntime = world.findFactionRuntimeId(SOURCE).orElseThrow();
        int partnerRuntime = world.findFactionRuntimeId(PARTNER).orElseThrow();
        int alternativeRuntime = world.findFactionRuntimeId(ALTERNATIVE).orElseThrow();
        world.createEntity(
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                marketStation("Source market", sourceRuntime, itemId, 20, 100, 50f));
        world.createEntity(
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                marketStation("Partner supplier", partnerRuntime, itemId, 100, 20, 10f));
        world.createEntity(
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                marketStation("Alternative supplier", alternativeRuntime, itemId, 60, 20, 20f));

        FactionEconomicDependenceDiagnostics before = world.analyzeEconomicDependence(SOURCE, PARTNER);
        FactionItemDependenceDiagnostic row = item(before, item.id());
        assertEquals(80L, row.currentExternalRequirementUnits());
        assertEquals(80L, row.partnerPhysicalSurplusUnits());
        assertEquals(80L, row.partnerAccessibleSurplusUnits());
        assertEquals(40L, row.alternativeAccessibleSurplusUnits());
        assertEquals(10_000, row.partnerCoverageBasisPoints());
        assertEquals(6_666, row.partnerSupplyShareBasisPoints());
        assertEquals(10_000L, row.partnerBestUnitSellPriceMilliCredits());
        assertEquals(20_000L, row.alternativeBestUnitSellPriceMilliCredits());
        assertEquals(400_000L, row.estimatedReplacementPremiumMilliCredits());
        assertEquals(40L, row.uncoveredUnitsAfterPartnerLoss());
        assertEquals(2, row.bestPartnerRouteHops());
        assertEquals(1, row.bestAlternativeRouteHops());
        assertTrue(row.uniquePartnerShortestRoute());
        assertEquals(1, row.uniquePartnerCorridorIntermediateSystems());
        assertEquals(10_000, before.structuralImportDependenceBasisPoints());
        assertEquals(400_000L, before.estimatedCurrentAccessLossPremiumMilliCredits());
        assertEquals(40L, before.currentUncoveredUnitsAfterAccessLoss());
        assertEquals(10_000, before.confidenceBasisPoints());

        WorldState snapshot = world.snapshot();
        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(snapshot));
        WorldSimulation restored = WorldSimulation.restore(
                decoded,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        assertEquals(before, restored.analyzeEconomicDependence(SOURCE, PARTNER));

        restored.applyDiplomaticEmbargoCommand(new DiplomaticEmbargoCommand.Impose(
                PARTNER,
                SOURCE,
                -1L,
                "stage17e7-test"));
        FactionEconomicDependenceDiagnostics after = restored.analyzeEconomicDependence(SOURCE, PARTNER);
        FactionItemDependenceDiagnostic blocked = item(after, item.id());
        assertEquals(80L, blocked.partnerPhysicalSurplusUnits());
        assertEquals(0L, blocked.partnerAccessibleSurplusUnits());
        assertEquals(40L, blocked.alternativeAccessibleSurplusUnits());
        assertEquals(0, blocked.partnerCoverageBasisPoints());
        assertEquals(0, blocked.partnerSupplyShareBasisPoints());
        assertEquals(40L, blocked.uncoveredUnitsAfterPartnerLoss());
        assertEquals(0, after.structuralImportDependenceBasisPoints());
        assertEquals(0L, after.estimatedCurrentAccessLossPremiumMilliCredits());
    }

    private static void clearExistingEconomicSignal(WorldSimulation world) {
        for (StarSystemNode system : world.getTopology().systems()) {
            for (Entity entity : world.findSession(system.id()).orElseThrow().getEngine().getEntities()) {
                MarketComponent market = entity.getComponent(MarketComponent.class);
                InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
                if (market != null && inventory != null) {
                    for (int itemId = 0; itemId < inventory.stock.length; itemId++) {
                        inventory.stock[itemId] = 0;
                        market.targetStock[itemId] = 0;
                    }
                }
                ProductionComponent production = entity.getComponent(ProductionComponent.class);
                if (production != null) {
                    production.activeRecipeIndex = -1;
                }
            }
        }
    }

    private static Entity marketStation(
            String name,
            int factionRuntimeId,
            int itemId,
            int stock,
            int target,
            float sellPrice) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 1_000;
        inventory.stock[itemId] = stock;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(itemId, target, 0f);
        market.sellPrices[itemId] = sellPrice;
        market.buyPrices[itemId] = Math.max(1f, sellPrice * 0.9f);
        return new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.STATION))
                .add(new FactionComponent(factionRuntimeId))
                .add(inventory)
                .add(market)
                .add(new WalletComponent(1_000_000L));
    }

    private static FactionItemDependenceDiagnostic item(
            FactionEconomicDependenceDiagnostics diagnostics,
            String itemContentId) {
        return diagnostics.items().stream()
                .filter(row -> row.itemContentId().equals(itemContentId))
                .findFirst()
                .orElseThrow();
    }
}
