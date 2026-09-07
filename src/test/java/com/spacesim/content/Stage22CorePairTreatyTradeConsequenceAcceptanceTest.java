package com.spacesim.content;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.controllers.TradeController;
import com.spacesim.economy.Money;
import com.spacesim.persistence.EntityId;
import com.spacesim.world.DiplomaticTreatyClauseState;
import com.spacesim.world.DiplomaticTreatyCommand;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.WorldSimulation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B16 physical trade-volume consequence for the actual core faction identities.
 *
 * <p>The command/persistence probe freezes treaty lifecycle and access/tariff policy itself. This
 * acceptance closes the next causal seam: a pending agreement must not move physical goods or money,
 * an active mutual market-access/customs agreement must admit the ordinary {@link TradeController},
 * breach must stop the same transaction without mutation, and a later accepted agreement must
 * restore real trade. Station and visitor are persistent entities created through the ordinary world
 * lifecycle; no abstract trade score or synthetic volume counter participates.</p>
 */
class Stage22CorePairTreatyTradeConsequenceAcceptanceTest {
    private static final int INITIAL_STATION_STOCK = 20;
    private static final int FIRST_TRADE_AMOUNT = 3;
    private static final int RECOVERY_TRADE_AMOUNT = 2;

    @Test
    void b16CoreTreatyShockRemovesAndRestoresPhysicalTradeVolumeAcrossSaveLoad() {
        List<LaneResult> rows = new ArrayList<>();
        for (var permutation : Stage22CorePairExperimentProtocol.Permutation.values()) {
            LaneResult result = runLane(permutation);
            assertEquals(FIRST_TRADE_AMOUNT + RECOVERY_TRADE_AMOUNT, result.totalDeliveredUnits());
            assertTrue(result.pendingDeniedWithoutMutation());
            assertTrue(result.breachDeniedWithoutMutation());
            assertTrue(result.activeTradePersisted());
            assertTrue(result.recoveredTradePersisted());
            rows.add(result);
        }

        Stage22CorePairEvidenceArchive.write(
                "B16-core-treaty-physical-trade-consequence",
                rows,
                "Both mirrored Empire/Industrial-Union ownership lanes use persistent ordinary market/visitor entities and WorldSimulation.createTradeController. A pending treaty refresh leaves the same purchase at zero physical volume with byte-stable money/inventory, acceptance moves real item units and wallet value with an ordinary ledger trade, breach removes access and produces zero mutation, and a newly accepted agreement restores physical volume. Active and breached/recovered consequences survive ordinary WorldState binary round trips; no composite economic score or synthetic trade-volume counter is used.");
    }

    private static LaneResult runLane(Stage22CorePairExperimentProtocol.Permutation permutation) {
        String owner = permutation == Stage22CorePairExperimentProtocol.Permutation.DEFAULT
                ? Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID
                : Stage22CorePairBalanceEvidence.UNION_FACTION_ID;
        String visitor = permutation == Stage22CorePairExperimentProtocol.Permutation.DEFAULT
                ? Stage22CorePairBalanceEvidence.UNION_FACTION_ID
                : Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID;

        WorldSimulation world = Stage22CorePairWorldFixture.create(Stage22CorePairExperimentProtocol.FIRST_SEED);
        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(
                ContentCatalogLoader.loadDefault(), world.snapshot().factionIdentities());
        int ownerRuntimeId = identities.runtimeId(owner).orElseThrow();
        int visitorRuntimeId = identities.runtimeId(visitor).orElseThrow();

        EntityId stationId = world.createEntity(world.getActiveSystemId(), station(ownerRuntimeId));
        EntityId visitorId = world.createEntity(world.getActiveSystemId(), visitor(visitorRuntimeId));

        var offered = world.applyDiplomaticTreatyCommand(new DiplomaticTreatyCommand.Offer(
                owner,
                visitor,
                clauses(),
                -1L)).treaty();
        Entity station = entity(world, stationId);
        Entity buyer = entity(world, visitorId);
        TradeController pendingController = world.createTradeController(
                world.findSession(world.getActiveSystemId()).orElseThrow());
        PhysicalState pendingBefore = physical(station, buyer);
        assertFalse(pendingController.buyFromStation(
                station, buyer, Constants.ITEM_FOOD, FIRST_TRADE_AMOUNT));
        PhysicalState pendingAfter = physical(station, buyer);
        assertEquals(pendingBefore, pendingAfter,
                "pending B16 agreement must not mutate physical trade state");
        assertEquals(0, pendingController.getLedger().getEntries().size());

        world.applyDiplomaticTreatyCommand(new DiplomaticTreatyCommand.Accept(visitor, offered.treatyId()));
        station = entity(world, stationId);
        buyer = entity(world, visitorId);
        TradeController activeController = world.createTradeController(
                world.findSession(world.getActiveSystemId()).orElseThrow());
        PhysicalState activeBefore = physical(station, buyer);
        assertTrue(activeController.buyFromStation(
                station, buyer, Constants.ITEM_FOOD, FIRST_TRADE_AMOUNT));
        PhysicalState activeAfter = physical(station, buyer);
        assertTradeDelta(activeBefore, activeAfter, FIRST_TRADE_AMOUNT);
        assertEquals(1, activeController.getLedger().getEntries().size(),
                "tariff-exempt B16 trade should produce exactly one ordinary trade ledger row");

        world = Stage22CorePairWorldFixture.roundTrip(world);
        PhysicalState activeRestored = physical(entity(world, stationId), entity(world, visitorId));
        assertEquals(activeAfter, activeRestored,
                "accepted B16 physical trade consequence must survive WorldState persistence");

        world.applyDiplomaticTreatyCommand(new DiplomaticTreatyCommand.Breach(
                owner, offered.treatyId(), "core-pair-B16-physical-access-shock"));
        station = entity(world, stationId);
        buyer = entity(world, visitorId);
        TradeController breachedController = world.createTradeController(
                world.findSession(world.getActiveSystemId()).orElseThrow());
        PhysicalState breachedBefore = physical(station, buyer);
        assertFalse(breachedController.buyFromStation(
                station, buyer, Constants.ITEM_FOOD, RECOVERY_TRADE_AMOUNT));
        PhysicalState breachedAfter = physical(station, buyer);
        assertEquals(breachedBefore, breachedAfter,
                "breached B16 access shock must fail closed without money or inventory mutation");
        assertEquals(0, breachedController.getLedger().getEntries().size());

        world = Stage22CorePairWorldFixture.roundTrip(world);
        PhysicalState breachedRestored = physical(entity(world, stationId), entity(world, visitorId));
        assertEquals(breachedAfter, breachedRestored,
                "breached zero-volume physical state must survive WorldState persistence");

        var recovery = world.applyDiplomaticTreatyCommand(new DiplomaticTreatyCommand.Offer(
                owner,
                visitor,
                clauses(),
                -1L)).treaty();
        world.applyDiplomaticTreatyCommand(new DiplomaticTreatyCommand.Accept(visitor, recovery.treatyId()));
        station = entity(world, stationId);
        buyer = entity(world, visitorId);
        TradeController recoveryController = world.createTradeController(
                world.findSession(world.getActiveSystemId()).orElseThrow());
        PhysicalState recoveryBefore = physical(station, buyer);
        assertTrue(recoveryController.buyFromStation(
                station, buyer, Constants.ITEM_FOOD, RECOVERY_TRADE_AMOUNT));
        PhysicalState recoveryAfter = physical(station, buyer);
        assertTradeDelta(recoveryBefore, recoveryAfter, RECOVERY_TRADE_AMOUNT);
        assertEquals(1, recoveryController.getLedger().getEntries().size());

        world = Stage22CorePairWorldFixture.roundTrip(world);
        PhysicalState recoveredRestored = physical(entity(world, stationId), entity(world, visitorId));
        assertEquals(recoveryAfter, recoveredRestored,
                "recovered B16 trade volume must survive WorldState persistence");

        return new LaneResult(
                permutation.name(),
                owner,
                visitor,
                stationId.value(),
                visitorId.value(),
                FIRST_TRADE_AMOUNT + RECOVERY_TRADE_AMOUNT,
                pendingBefore.equals(pendingAfter),
                breachedBefore.equals(breachedAfter),
                activeAfter.equals(activeRestored),
                recoveryAfter.equals(recoveredRestored),
                recoveredRestored.stationStock(),
                recoveredRestored.visitorStock(),
                recoveredRestored.stationWalletMilliCredits(),
                recoveredRestored.visitorWalletMilliCredits());
    }

    private static List<DiplomaticTreatyClauseState> clauses() {
        return List.of(
                new DiplomaticTreatyClauseState(
                        DiplomaticTreatyClauseState.Kind.MARKET_ACCESS,
                        DiplomaticTreatyClauseState.Direction.MUTUAL,
                        null),
                new DiplomaticTreatyClauseState(
                        DiplomaticTreatyClauseState.Kind.CUSTOMS_TARIFF_EXEMPTION,
                        DiplomaticTreatyClauseState.Direction.MUTUAL,
                        null));
    }

    private static Entity station(int factionId) {
        InventoryComponent inventory = new InventoryComponent();
        inventory.stock[Constants.ITEM_FOOD] = INITIAL_STATION_STOCK;
        MarketComponent market = new MarketComponent();
        market.configureTradableItem(Constants.ITEM_FOOD, 100, 0f);
        market.sellPrices[Constants.ITEM_FOOD] = 10f;
        market.buyPrices[Constants.ITEM_FOOD] = 9f;
        return new Entity()
                .add(new IdentityComponent("M22.6 treaty market", IdentityComponent.Kind.STATION))
                .add(inventory)
                .add(market)
                .add(new WalletComponent(Money.fromCredits(1_000d)))
                .add(new FactionComponent(factionId));
    }

    private static Entity visitor(int factionId) {
        return new Entity()
                .add(new IdentityComponent("M22.6 treaty visitor", IdentityComponent.Kind.FLEET))
                .add(new InventoryComponent())
                .add(new WalletComponent(Money.fromCredits(100d)))
                .add(new FactionComponent(factionId));
    }

    private static Entity entity(WorldSimulation world, EntityId id) {
        return world.findSession(world.getActiveSystemId()).orElseThrow()
                .getEntityRegistry().require(id);
    }

    private static PhysicalState physical(Entity station, Entity visitor) {
        InventoryComponent stationInventory = station.getComponent(InventoryComponent.class);
        InventoryComponent visitorInventory = visitor.getComponent(InventoryComponent.class);
        WalletComponent stationWallet = station.getComponent(WalletComponent.class);
        WalletComponent visitorWallet = visitor.getComponent(WalletComponent.class);
        return new PhysicalState(
                stationInventory.stock[Constants.ITEM_FOOD],
                visitorInventory.stock[Constants.ITEM_FOOD],
                stationWallet.getBalanceMilliCredits(),
                visitorWallet.getBalanceMilliCredits());
    }

    private static void assertTradeDelta(PhysicalState before, PhysicalState after, int amount) {
        assertEquals(before.stationStock() - amount, after.stationStock());
        assertEquals(before.visitorStock() + amount, after.visitorStock());
        long stationGain = after.stationWalletMilliCredits() - before.stationWalletMilliCredits();
        long visitorLoss = before.visitorWalletMilliCredits() - after.visitorWalletMilliCredits();
        assertTrue(stationGain > 0L, "physical B16 trade must move positive wallet value");
        assertEquals(stationGain, visitorLoss,
                "customs-exempt B16 transaction must conserve buyer/station wallet value");
    }

    private record PhysicalState(
            int stationStock,
            int visitorStock,
            long stationWalletMilliCredits,
            long visitorWalletMilliCredits) { }

    private record LaneResult(
            String permutation,
            String marketOwnerFactionId,
            String visitingFactionId,
            long stationEntityId,
            long visitorEntityId,
            int totalDeliveredUnits,
            boolean pendingDeniedWithoutMutation,
            boolean breachDeniedWithoutMutation,
            boolean activeTradePersisted,
            boolean recoveredTradePersisted,
            int finalStationStock,
            int finalVisitorStock,
            long finalStationWalletMilliCredits,
            long finalVisitorWalletMilliCredits) { }
}
