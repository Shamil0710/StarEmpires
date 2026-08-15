package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.controllers.TradeController;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.WorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17EEmbargoAcceptanceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";

    @Test
    void embargoChangesOnlyLegalAccessAndOrdinaryTradeStopsUntilRevokedAcrossSaveLoad() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(17_540L);
        int itemId = content.getItems().get(0).runtimeId();
        int tradeLeagueRuntimeId = world.findFactionRuntimeId(TRADE_LEAGUE).orElseThrow();
        int minersRuntimeId = world.findFactionRuntimeId(MINERS).orElseThrow();
        EntityId stationId = createSeller(world, tradeLeagueRuntimeId, itemId);
        EntityId buyerId = createBuyer(world, minersRuntimeId);
        FactionPolicyRefreshService.refresh(world, content);
        assertTrue(world.evaluateFactionMarketAccess(TRADE_LEAGUE, MINERS).allowed());

        SimulationSession session = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        Entity station = session.getEntityRegistry().find(stationId);
        Entity buyer = session.getEntityRegistry().find(buyerId);
        TradeController trade = new TradeController(session.getLedger());
        assertTrue(trade.buyFromStation(station, buyer, itemId, 2, null));

        InventoryComponent stationInventory = station.getComponent(InventoryComponent.class);
        InventoryComponent buyerInventory = buyer.getComponent(InventoryComponent.class);
        WalletComponent stationWallet = station.getComponent(WalletComponent.class);
        WalletComponent buyerWallet = buyer.getComponent(WalletComponent.class);
        int stationStockBeforeEmbargo = stationInventory.stock[itemId];
        int buyerStockBeforeEmbargo = buyerInventory.stock[itemId];
        long stationMoneyBeforeEmbargo = stationWallet.getBalanceMilliCredits();
        long buyerMoneyBeforeEmbargo = buyerWallet.getBalanceMilliCredits();
        int ledgerSizeBeforeEmbargo = session.getLedger().size();

        DiplomaticEmbargoCommandResult imposed = world.applyDiplomaticEmbargoCommand(
                new DiplomaticEmbargoCommand.Impose(
                        TRADE_LEAGUE,
                        MINERS,
                        -1L,
                        "critical-supply-dispute"));
        assertEquals(DiplomaticEmbargoCommandResult.Operation.IMPOSED, imposed.operation());
        assertEquals(MINERS, imposed.embargo().targetFactionContentId());
        assertFalse(imposed.grievanceId().isEmpty());
        assertEquals(stationStockBeforeEmbargo, stationInventory.stock[itemId]);
        assertEquals(buyerStockBeforeEmbargo, buyerInventory.stock[itemId]);
        assertEquals(stationMoneyBeforeEmbargo, stationWallet.getBalanceMilliCredits());
        assertEquals(buyerMoneyBeforeEmbargo, buyerWallet.getBalanceMilliCredits());
        assertEquals(ledgerSizeBeforeEmbargo, session.getLedger().size(),
                "Embargo command itself must not invent an economic transaction");
        assertEquals(DiplomaticMarketAccessResolver.Reason.EMBARGO,
                world.evaluateFactionMarketAccess(TRADE_LEAGUE, MINERS).reason());

        assertFalse(trade.buyFromStation(station, buyer, itemId, 1, null));
        assertEquals(stationStockBeforeEmbargo, stationInventory.stock[itemId]);
        assertEquals(buyerStockBeforeEmbargo, buyerInventory.stock[itemId]);
        assertEquals(stationMoneyBeforeEmbargo, stationWallet.getBalanceMilliCredits());
        assertEquals(buyerMoneyBeforeEmbargo, buyerWallet.getBalanceMilliCredits());
        assertEquals(ledgerSizeBeforeEmbargo, session.getLedger().size());

        FactionDiplomacyState minersDiplomacy = world.findFactionDiplomacyState(MINERS).orElseThrow();
        DiplomaticGrievanceState grievance = minersDiplomacy.grievances().stream()
                .filter(value -> value.grievanceId().equals(imposed.grievanceId()))
                .findFirst()
                .orElseThrow();
        assertEquals(DiplomaticGrievanceState.Kind.EMBARGO, grievance.kind());
        assertEquals(TRADE_LEAGUE, grievance.targetFactionContentId());
        assertEquals(40, grievance.severity());

        WorldSimulation restored = restore(
                WorldStateCodec.decode(WorldStateCodec.encode(world.snapshot())), content);
        assertEquals(DiplomaticMarketAccessResolver.Reason.EMBARGO,
                restored.evaluateFactionMarketAccess(TRADE_LEAGUE, MINERS).reason());
        SimulationSession restoredSession = restored.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        Entity restoredStation = restoredSession.getEntityRegistry().find(stationId);
        Entity restoredBuyer = restoredSession.getEntityRegistry().find(buyerId);
        assertFalse(new TradeController(restoredSession.getLedger())
                .buyFromStation(restoredStation, restoredBuyer, itemId, 1, null));

        DiplomaticEmbargoCommandResult revoked = restored.applyDiplomaticEmbargoCommand(
                new DiplomaticEmbargoCommand.Revoke(TRADE_LEAGUE, MINERS));
        assertEquals(DiplomaticEmbargoCommandResult.Operation.REVOKED, revoked.operation());
        assertTrue(restored.evaluateFactionMarketAccess(TRADE_LEAGUE, MINERS).allowed());
        assertTrue(new TradeController(restoredSession.getLedger())
                .buyFromStation(restoredStation, restoredBuyer, itemId, 1, null));
        assertTrue(restored.findFactionDiplomacyState(MINERS).orElseThrow().grievances().stream()
                .anyMatch(value -> value.grievanceId().equals(imposed.grievanceId())),
                "Lifting an embargo must not erase the political history it created");
    }

    @Test
    void duplicateActiveEmbargoIsRejectedAndFiniteEmbargoExpiresThroughOrdinaryWorldAdvance() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(17_541L);
        long start = world.getAuthoritativeWorldTick();
        DiplomaticEmbargoCommand.Impose command = new DiplomaticEmbargoCommand.Impose(
                TRADE_LEAGUE,
                MINERS,
                start + 30L,
                "temporary-sanction");
        world.applyDiplomaticEmbargoCommand(command);
        WorldState beforeDuplicate = world.snapshot();
        assertThrows(IllegalStateException.class, () -> world.applyDiplomaticEmbargoCommand(command));
        assertEquals(beforeDuplicate, world.snapshot());
        assertEquals(DiplomaticMarketAccessResolver.Reason.EMBARGO,
                world.evaluateFactionMarketAccess(TRADE_LEAGUE, MINERS).reason());

        advanceToAtLeast(world, start + 31L);
        assertTrue(world.evaluateFactionMarketAccess(TRADE_LEAGUE, MINERS).allowed(),
                "Expired embargo must stop affecting ordinary access without manual refresh");

        DiplomaticEmbargoCommandResult reimposed = world.applyDiplomaticEmbargoCommand(
                new DiplomaticEmbargoCommand.Impose(
                        TRADE_LEAGUE,
                        MINERS,
                        -1L,
                        "renewed-sanction"));
        assertTrue(reimposed.embargo().imposedTick() >= start + 31L);
        assertEquals(DiplomaticMarketAccessResolver.Reason.EMBARGO,
                world.evaluateFactionMarketAccess(TRADE_LEAGUE, MINERS).reason());
    }

    private static EntityId createSeller(WorldSimulation world, int factionRuntimeId, int itemId) {
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

    private static EntityId createBuyer(WorldSimulation world, int factionRuntimeId) {
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

    private static void advanceToAtLeast(WorldSimulation world, long targetTick) {
        int guard = 0;
        while (world.getAuthoritativeWorldTick() < targetTick) {
            world.advanceFrame(1f);
            if (++guard > 10_000) {
                throw new AssertionError("World did not reach embargo target tick");
            }
        }
    }

    private static WorldSimulation restore(WorldState state, ContentCatalog content) {
        return WorldSimulation.restore(
                state,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }
}
