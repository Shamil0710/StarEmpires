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
import com.spacesim.persistence.EntityId;
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
        ContentCatalog.ItemDefinition importItem = content.getItems().get(0);
        ContentCatalog.ItemDefinition exportItem = content.getItems().get(1);
        int importItemId = importItem.runtimeId();
        int exportItemId = exportItem.runtimeId();
        clearExistingEconomicSignal(world);

        int sourceRuntime = world.findFactionRuntimeId(SOURCE).orElseThrow();
        int partnerRuntime = world.findFactionRuntimeId(PARTNER).orElseThrow();
        int alternativeRuntime = world.findFactionRuntimeId(ALTERNATIVE).orElseThrow();

        addMarketStation(
                world, DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                "Source import market", sourceRuntime, importItemId, 20, 100, 50f);
        addMarketStation(
                world, DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                "Partner supplier", partnerRuntime, importItemId, 100, 20, 10f);
        addMarketStation(
                world, DemoGalaxyFactory.INNER_SYSTEM_ID,
                "Alternative supplier", alternativeRuntime, importItemId, 60, 20, 20f);

        addMarketStation(
                world, DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                "Source exporter", sourceRuntime, exportItemId, 50, 0, 30f);
        addMarketStation(
                world, DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                "Partner buyer", partnerRuntime, exportItemId, 0, 30, 60f);
        addMarketStation(
                world, DemoGalaxyFactory.INNER_SYSTEM_ID,
                "Alternative buyer", alternativeRuntime, exportItemId, 0, 10, 55f);

        FactionEconomicDependenceDiagnostics before = world.analyzeEconomicDependence(SOURCE, PARTNER);
        FactionItemDependenceDiagnostic importRow = item(before, importItem.id());
        assertEquals(80L, importRow.currentExternalRequirementUnits());
        assertEquals(80L, importRow.partnerPhysicalSurplusUnits());
        assertEquals(80L, importRow.partnerAccessibleSurplusUnits());
        assertEquals(40L, importRow.alternativeAccessibleSurplusUnits());
        assertEquals(10_000, importRow.partnerCoverageBasisPoints());
        assertEquals(6_666, importRow.partnerSupplyShareBasisPoints());
        assertEquals(10_000L, importRow.partnerBestUnitSellPriceMilliCredits());
        assertEquals(20_000L, importRow.alternativeBestUnitSellPriceMilliCredits());
        assertEquals(400_000L, importRow.estimatedReplacementPremiumMilliCredits());
        assertEquals(40L, importRow.uncoveredUnitsAfterPartnerLoss());
        assertEquals(2, importRow.bestPartnerRouteHops());
        assertEquals(1, importRow.bestAlternativeRouteHops());
        assertTrue(importRow.uniquePartnerShortestRoute());
        assertEquals(1, importRow.uniquePartnerCorridorIntermediateSystems());

        FactionItemDependenceDiagnostic exportRow = item(before, exportItem.id());
        assertEquals(50L, exportRow.sourceExportableSurplusUnits());
        assertEquals(30L, exportRow.partnerAccessibleDemandUnits());
        assertEquals(10L, exportRow.otherAccessibleForeignDemandUnits());
        assertEquals(7_500, exportRow.partnerDemandShareBasisPoints());

        assertEquals(10_000, before.structuralImportDependenceBasisPoints());
        assertEquals(7_500, before.structuralExportMarketDependenceBasisPoints());
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
        FactionItemDependenceDiagnostic blockedImport = item(after, importItem.id());
        assertEquals(80L, blockedImport.partnerPhysicalSurplusUnits());
        assertEquals(0L, blockedImport.partnerAccessibleSurplusUnits());
        assertEquals(40L, blockedImport.alternativeAccessibleSurplusUnits());
        assertEquals(0, blockedImport.partnerCoverageBasisPoints());
        assertEquals(0, blockedImport.partnerSupplyShareBasisPoints());
        assertEquals(40L, blockedImport.uncoveredUnitsAfterPartnerLoss());

        FactionItemDependenceDiagnostic blockedExport = item(after, exportItem.id());
        assertEquals(50L, blockedExport.sourceExportableSurplusUnits());
        assertEquals(0L, blockedExport.partnerAccessibleDemandUnits());
        assertEquals(10L, blockedExport.otherAccessibleForeignDemandUnits());
        assertEquals(0, blockedExport.partnerDemandShareBasisPoints());

        assertEquals(0, after.structuralImportDependenceBasisPoints());
        assertEquals(0, after.structuralExportMarketDependenceBasisPoints());
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

    private static void addMarketStation(
            WorldSimulation world,
            StarSystemId systemId,
            String name,
            int factionRuntimeId,
            int itemId,
            int stock,
            int target,
            float sellPrice) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 1_000;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(itemId, target, 0f);
        market.sellPrices[itemId] = sellPrice;
        market.buyPrices[itemId] = Math.max(1f, sellPrice * 0.9f);
        Entity shell = new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.STATION))
                .add(new FactionComponent(factionRuntimeId))
                .add(inventory)
                .add(market)
                .add(new WalletComponent());
        EntityId id = world.createEntity(systemId, shell);
        Entity live = world.findSession(systemId).orElseThrow().getEntityRegistry().find(id);
        live.getComponent(InventoryComponent.class).stock[itemId] = stock;
        assertTrue(live.getComponent(WalletComponent.class).creditFromSource(1_000_000L));
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
