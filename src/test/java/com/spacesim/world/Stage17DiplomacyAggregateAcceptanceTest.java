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
import com.spacesim.controllers.TradeController;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.WorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17DiplomacyAggregateAcceptanceTest {
    private static final String SOURCE = "faction.neutral";
    private static final String PARTNER = "faction.trade_league";
    private static final String ALTERNATIVE = "faction.miners";
    private static final int CUSTOMS_BPS = 1_000;

    @Test
    void dependenceTreatyCustomsBreachEmbargoAndPersistenceFormOnePhysicalLoop() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                withCustomsRate(DemoGalaxyFactory.createState(17_800L, content), PARTNER, CUSTOMS_BPS),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        clearExistingEconomicSignal(world);

        ContentCatalog.ItemDefinition item = content.getItems().get(0);
        int itemId = item.runtimeId();
        int sourceRuntime = world.findFactionRuntimeId(SOURCE).orElseThrow();
        int partnerRuntime = world.findFactionRuntimeId(PARTNER).orElseThrow();
        int alternativeRuntime = world.findFactionRuntimeId(ALTERNATIVE).orElseThrow();

        addMarketStation(
                world,
                DemoGalaxyFactory.FRONTIER_SYSTEM_ID,
                "Source demand market",
                sourceRuntime,
                itemId,
                20,
                100,
                50f,
                1_000_000L);
        EntityId partnerStationId = addMarketStation(
                world,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                "Partner supplier",
                partnerRuntime,
                itemId,
                100,
                20,
                10f,
                1_000_000L);
        addMarketStation(
                world,
                DemoGalaxyFactory.INNER_SYSTEM_ID,
                "Alternative supplier",
                alternativeRuntime,
                itemId,
                60,
                20,
                20f,
                1_000_000L);
        EntityId buyerId = addTradeActor(
                world,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                "Neutral commercial buyer",
                sourceRuntime,
                1_000_000L);
        FactionPolicyRefreshService.refresh(world, content);

        Entity partnerStation = entity(world, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, partnerStationId);
        Entity buyer = entity(world, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, buyerId);
        TradeController trade = world.createTradeController(
                world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow());
        EconomicLedger ledger = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow().getLedger();

        FactionEconomicDependenceDiagnostics dependence = world.analyzeEconomicDependence(SOURCE, PARTNER);
        FactionItemDependenceDiagnostic itemDependence = item(dependence, item.id());
        assertEquals(80L, itemDependence.currentExternalRequirementUnits());
        assertEquals(80L, itemDependence.partnerAccessibleSurplusUnits());
        assertEquals(40L, itemDependence.alternativeAccessibleSurplusUnits());
        assertEquals(10_000, dependence.structuralImportDependenceBasisPoints());
        assertEquals(400_000L, dependence.estimatedCurrentAccessLossPremiumMilliCredits());

        DiplomaticMarketAccessResolver.Decision ordinaryAccess =
                world.evaluateFactionMarketAccess(PARTNER, SOURCE);
        assertTrue(ordinaryAccess.allowed());
        assertEquals(DiplomaticMarketAccessResolver.Reason.RELATION_THRESHOLD_ALLOW, ordinaryAccess.reason());
        assertEquals(
                CUSTOMS_BPS,
                CustomsTariffResolver.evaluate(
                        world.getFactionDiplomacyStates(),
                        PARTNER,
                        SOURCE,
                        world.getAuthoritativeWorldTick()).basisPoints());

        WalletComponent partnerWallet = partnerStation.getComponent(WalletComponent.class);
        WalletComponent buyerWallet = buyer.getComponent(WalletComponent.class);
        long buyerBeforeTaxedTrade = buyerWallet.getBalanceMilliCredits();
        long stationBeforeTaxedTrade = partnerWallet.getBalanceMilliCredits();
        long treasuryBeforeTaxedTrade = treasury(world, PARTNER);
        int ledgerBeforeTaxedTrade = ledger.size();
        assertTrue(trade.buyFromStation(partnerStation, buyer, itemId, 1));
        assertEquals(buyerBeforeTaxedTrade - 11_000L, buyerWallet.getBalanceMilliCredits());
        assertEquals(stationBeforeTaxedTrade + 10_000L, partnerWallet.getBalanceMilliCredits());
        assertEquals(treasuryBeforeTaxedTrade + 1_000L, treasury(world, PARTNER));
        assertEquals(ledgerBeforeTaxedTrade + 2, ledger.size());
        EconomicTransaction dutyEntry = ledger.getEntries().get(ledger.size() - 1);
        assertEquals(EconomicTransaction.Type.MONEY_TRANSFER, dutyEntry.type());
        assertEquals("customs-tariff", dutyEntry.reason());
        assertEquals(1_000L, dutyEntry.moneyMilliCredits());

        DiplomaticTreatyCommandResult offer = world.applyDiplomaticTreatyCommand(
                new DiplomaticTreatyCommand.Offer(
                        SOURCE,
                        PARTNER,
                        List.of(
                                new DiplomaticTreatyClauseState(
                                        DiplomaticTreatyClauseState.Kind.MARKET_ACCESS,
                                        DiplomaticTreatyClauseState.Direction.MUTUAL,
                                        null),
                                new DiplomaticTreatyClauseState(
                                        DiplomaticTreatyClauseState.Kind.CUSTOMS_TARIFF_EXEMPTION,
                                        DiplomaticTreatyClauseState.Direction.COUNTERPARTY_TO_OWNER,
                                        null)),
                        -1L));
        String treatyId = offer.treaty().treatyId();
        assertEquals(DiplomaticTreatyCommandResult.Operation.OFFERED, offer.operation());

        DiplomaticDecisionDoctrine doctrine = new DiplomaticDecisionDoctrine(
                100,
                0,
                0,
                0,
                0,
                0,
                0,
                10_000L,
                1_000L,
                5_000,
                10,
                -10);
        DiplomaticTreatyEvaluationInputs evaluationInputs = new DiplomaticTreatyEvaluationInputs(
                Math.max(100_000L, dependence.estimatedCurrentAccessLossPremiumMilliCredits()),
                dependence.structuralImportDependenceBasisPoints() / 100,
                0,
                0,
                0L,
                dependence.observationTick(),
                dependence.confidenceBasisPoints());
        DiplomaticTreatyEvaluation evaluation = DiplomaticTreatyEvaluator.evaluate(
                world,
                treatyId,
                PARTNER,
                doctrine,
                evaluationInputs);
        assertEquals(DiplomaticTreatyEvaluation.Recommendation.ACCEPT, evaluation.recommendation());
        assertTrue(evaluation.economicBenefitUtility() >= doctrine.acceptUtilityThreshold());

        DiplomaticTreatyCommandResult accepted = world.applyDiplomaticTreatyCommand(
                new DiplomaticTreatyCommand.Accept(PARTNER, treatyId));
        assertEquals(DiplomaticTreatyCommandResult.Operation.ACCEPTED, accepted.operation());
        DiplomaticMarketAccessResolver.Decision treatyAccess = world.evaluateFactionMarketAccess(PARTNER, SOURCE);
        assertTrue(treatyAccess.allowed());
        assertEquals(DiplomaticMarketAccessResolver.Reason.EXPLICIT_TREATY_RIGHT, treatyAccess.reason());
        assertEquals(treatyId, treatyAccess.instrumentId());
        assertEquals(
                0,
                CustomsTariffResolver.evaluate(
                        world.getFactionDiplomacyStates(),
                        PARTNER,
                        SOURCE,
                        world.getAuthoritativeWorldTick()).basisPoints());

        long buyerBeforeExemptTrade = buyerWallet.getBalanceMilliCredits();
        long stationBeforeExemptTrade = partnerWallet.getBalanceMilliCredits();
        long treasuryBeforeExemptTrade = treasury(world, PARTNER);
        int ledgerBeforeExemptTrade = ledger.size();
        assertTrue(trade.buyFromStation(partnerStation, buyer, itemId, 1));
        assertEquals(buyerBeforeExemptTrade - 10_000L, buyerWallet.getBalanceMilliCredits());
        assertEquals(stationBeforeExemptTrade + 10_000L, partnerWallet.getBalanceMilliCredits());
        assertEquals(treasuryBeforeExemptTrade, treasury(world, PARTNER));
        assertEquals(ledgerBeforeExemptTrade + 1, ledger.size());
        assertEquals(EconomicTransaction.Type.TRADE, ledger.getEntries().get(ledger.size() - 1).type());

        DiplomaticTreatyCommandResult breached = world.applyDiplomaticTreatyCommand(
                new DiplomaticTreatyCommand.Breach(SOURCE, treatyId, "aggregate-contract-breach"));
        assertEquals(DiplomaticTreatyCommandResult.Operation.BREACHED, breached.operation());
        assertEquals(DiplomaticTreatyState.Status.BREACHED, world.findDiplomaticTreaty(treatyId).orElseThrow().status());
        assertEquals(
                CUSTOMS_BPS,
                CustomsTariffResolver.evaluate(
                        world.getFactionDiplomacyStates(),
                        PARTNER,
                        SOURCE,
                        world.getAuthoritativeWorldTick()).basisPoints());
        assertTrue(world.findFactionDiplomacyState(PARTNER).orElseThrow().grievances().stream()
                .anyMatch(grievance -> grievance.kind() == DiplomaticGrievanceState.Kind.TREATY_BREACH
                        && grievance.targetFactionContentId().equals(SOURCE)));

        world.applyDiplomaticEmbargoCommand(new DiplomaticEmbargoCommand.Impose(
                PARTNER,
                SOURCE,
                -1L,
                "aggregate-market-embargo"));
        DiplomaticMarketAccessResolver.Decision embargoedAccess = world.evaluateFactionMarketAccess(PARTNER, SOURCE);
        assertFalse(embargoedAccess.allowed());
        assertEquals(DiplomaticMarketAccessResolver.Reason.EMBARGO, embargoedAccess.reason());
        assertFalse(trade.canTradeWithStation(buyer, partnerStation));

        long buyerBeforeBlockedTrade = buyerWallet.getBalanceMilliCredits();
        long stationBeforeBlockedTrade = partnerWallet.getBalanceMilliCredits();
        long treasuryBeforeBlockedTrade = treasury(world, PARTNER);
        int stockBeforeBlockedTrade = partnerStation.getComponent(InventoryComponent.class).stock[itemId];
        int ledgerBeforeBlockedTrade = ledger.size();
        assertFalse(trade.buyFromStation(partnerStation, buyer, itemId, 1));
        assertEquals(buyerBeforeBlockedTrade, buyerWallet.getBalanceMilliCredits());
        assertEquals(stationBeforeBlockedTrade, partnerWallet.getBalanceMilliCredits());
        assertEquals(treasuryBeforeBlockedTrade, treasury(world, PARTNER));
        assertEquals(stockBeforeBlockedTrade, partnerStation.getComponent(InventoryComponent.class).stock[itemId]);
        assertEquals(ledgerBeforeBlockedTrade, ledger.size());

        FactionEconomicDependenceDiagnostics embargoedDependence = world.analyzeEconomicDependence(SOURCE, PARTNER);
        FactionItemDependenceDiagnostic embargoedItem = item(embargoedDependence, item.id());
        assertEquals(78L, embargoedItem.partnerPhysicalSurplusUnits());
        assertEquals(0L, embargoedItem.partnerAccessibleSurplusUnits());
        assertEquals(40L, embargoedItem.alternativeAccessibleSurplusUnits());
        assertEquals(0, embargoedDependence.structuralImportDependenceBasisPoints());

        WorldState snapshot = world.snapshot();
        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(snapshot));
        assertEquals(snapshot, decoded);
        WorldSimulation restored = WorldSimulation.restore(
                decoded,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        assertEquals(DiplomaticTreatyState.Status.BREACHED,
                restored.findDiplomaticTreaty(treatyId).orElseThrow().status());
        assertEquals(DiplomaticMarketAccessResolver.Reason.EMBARGO,
                restored.evaluateFactionMarketAccess(PARTNER, SOURCE).reason());
        assertEquals(CUSTOMS_BPS,
                restored.findFactionDiplomacyState(PARTNER).orElseThrow().customsTariffBasisPoints());
        assertEquals(embargoedDependence, restored.analyzeEconomicDependence(SOURCE, PARTNER));
        assertEquals(
                buyerBeforeBlockedTrade,
                entity(restored, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, buyerId)
                        .getComponent(WalletComponent.class).getBalanceMilliCredits());
        assertEquals(
                stationBeforeBlockedTrade,
                entity(restored, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, partnerStationId)
                        .getComponent(WalletComponent.class).getBalanceMilliCredits());
        assertEquals(
                ledger.getEntries(),
                restored.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow().getLedger().getEntries());
    }

    private static WorldState withCustomsRate(WorldState base, String factionId, int basisPoints) {
        List<FactionDiplomacyState> diplomacy = new ArrayList<>(base.factionDiplomacyStates().size());
        for (FactionDiplomacyState state : base.factionDiplomacyStates()) {
            diplomacy.add(state.factionContentId().equals(factionId)
                    ? new FactionDiplomacyState(
                            state.factionContentId(),
                            state.standings(),
                            state.grievances(),
                            state.treaties(),
                            state.embargoes(),
                            basisPoints)
                    : state);
        }
        return new WorldState(
                WorldState.CURRENT_VERSION,
                base.topology(),
                base.systems(),
                base.factions(),
                base.factionStrategies(),
                base.nextConstructionProjectIdValue(),
                base.constructionProjects(),
                base.factionEconomicPressures(),
                base.nextFleetIdValue(),
                base.fleets(),
                base.fleetJumps(),
                base.factionIdentities(),
                diplomacy);
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

    private static EntityId addMarketStation(
            WorldSimulation world,
            StarSystemId systemId,
            String name,
            int factionRuntimeId,
            int itemId,
            int stock,
            int target,
            float sellPrice,
            long walletMilliCredits) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 1_000;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(itemId, Math.max(1, target), 0f);
        market.targetStock[itemId] = target;
        market.sellPrices[itemId] = sellPrice;
        market.buyPrices[itemId] = Math.max(1f, sellPrice * 0.9f);
        Entity shell = new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.STATION))
                .add(new FactionComponent(factionRuntimeId))
                .add(inventory)
                .add(market)
                .add(new WalletComponent());
        EntityId id = world.createEntity(systemId, shell);
        Entity live = entity(world, systemId, id);
        live.getComponent(InventoryComponent.class).stock[itemId] = stock;
        assertTrue(live.getComponent(WalletComponent.class).creditFromSource(walletMilliCredits));
        return id;
    }

    private static EntityId addTradeActor(
            WorldSimulation world,
            StarSystemId systemId,
            String name,
            int factionRuntimeId,
            long walletMilliCredits) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = 100;
        EntityId id = world.createEntity(
                systemId,
                new Entity()
                        .add(new IdentityComponent(name, IdentityComponent.Kind.STATION))
                        .add(new FactionComponent(factionRuntimeId))
                        .add(inventory)
                        .add(new WalletComponent()));
        assertTrue(entity(world, systemId, id)
                .getComponent(WalletComponent.class)
                .creditFromSource(walletMilliCredits));
        return id;
    }

    private static Entity entity(WorldSimulation world, StarSystemId systemId, EntityId entityId) {
        return world.findSession(systemId).orElseThrow().getEntityRegistry().find(entityId);
    }

    private static long treasury(WorldSimulation world, String factionContentId) {
        return world.snapshot().factions().stream()
                .filter(state -> state.factionContentId().equals(factionContentId))
                .findFirst()
                .orElseThrow()
                .treasuryMilliCredits();
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
