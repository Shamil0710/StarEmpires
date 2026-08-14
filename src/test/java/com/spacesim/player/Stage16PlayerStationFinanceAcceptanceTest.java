package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetPlacementState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage16PlayerStationFinanceAcceptanceTest {
    @Test
    void dockedOwnedStationMovesRealMoneyBothDirectionsAndPreservesOwnership() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_801L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerState current = runtime.player();
        runtime.replacePlayerState(PlayerRuntime.copyWithOwnershipAndWallet(
                current,
                100_000_000L,
                current.ownedFleetIds(),
                current.activeFleetId()));

        StationFixture station = findStation(runtime);
        OwnedStationRef stationRef = new OwnedStationRef(
                runtime.world().getActiveSystemId(), station.entityId());
        runtime.replacePlayerState(PlayerRuntime.copyWithConstructionOwnership(
                runtime.player(),
                runtime.player().ownedConstructionProjectIds(),
                List.of(stationRef)));
        PlayerStationFinanceService finance = new PlayerStationFinanceService(runtime);

        assertTrue(finance.view().isEmpty());
        assertFalse(finance.deposit(1_000L));

        dock(runtime, station);
        PlayerStationFinanceView before = finance.view().orElseThrow();
        long totalBefore = Math.addExact(
                before.playerWalletMilliCredits(), before.stationWalletMilliCredits());
        int ledgerBefore = station.session().getLedger().size();
        long deposit = 2_000_000L;

        assertTrue(finance.deposit(deposit));
        PlayerStationFinanceView afterDeposit = finance.view().orElseThrow();
        assertEquals(before.playerWalletMilliCredits() - deposit, afterDeposit.playerWalletMilliCredits());
        assertEquals(before.stationWalletMilliCredits() + deposit, afterDeposit.stationWalletMilliCredits());
        assertEquals(totalBefore,
                afterDeposit.playerWalletMilliCredits() + afterDeposit.stationWalletMilliCredits());
        assertEquals(ledgerBefore + 1, station.session().getLedger().size());
        EconomicTransaction depositEntry = station.session().getLedger().getEntries()
                .get(station.session().getLedger().size() - 1);
        assertEquals(EconomicTransaction.Type.MONEY_TRANSFER, depositEntry.type());
        assertEquals("player-station-deposit", depositEntry.reason());

        long withdraw = 750_000L;
        assertTrue(finance.withdraw(withdraw));
        PlayerStationFinanceView afterWithdraw = finance.view().orElseThrow();
        assertEquals(afterDeposit.playerWalletMilliCredits() + withdraw, afterWithdraw.playerWalletMilliCredits());
        assertEquals(afterDeposit.stationWalletMilliCredits() - withdraw, afterWithdraw.stationWalletMilliCredits());
        assertEquals(totalBefore,
                afterWithdraw.playerWalletMilliCredits() + afterWithdraw.stationWalletMilliCredits());
        EconomicTransaction withdrawEntry = station.session().getLedger().getEntries()
                .get(station.session().getLedger().size() - 1);
        assertEquals(EconomicTransaction.Type.MONEY_TRANSFER, withdrawEntry.type());
        assertEquals("player-station-withdraw", withdrawEntry.reason());
        assertEquals(List.of(stationRef), runtime.player().ownedStations());
    }

    @Test
    void nonOwnedOrOverdrawnStationCannotPayPlayer() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_802L);
        PlayerRuntime runtime = scenario.runtime();
        StationFixture station = findStation(runtime);
        PlayerStationFinanceService finance = new PlayerStationFinanceService(runtime);
        dock(runtime, station);

        assertTrue(finance.view().isEmpty());
        assertFalse(finance.withdraw(1L));

        OwnedStationRef stationRef = new OwnedStationRef(
                runtime.world().getActiveSystemId(), station.entityId());
        runtime.replacePlayerState(PlayerRuntime.copyWithConstructionOwnership(
                runtime.player(),
                runtime.player().ownedConstructionProjectIds(),
                List.of(stationRef)));
        long playerBefore = runtime.player().walletMilliCredits();
        long stationBefore = station.wallet().getBalanceMilliCredits();
        long impossible = stationBefore == Long.MAX_VALUE ? Long.MAX_VALUE : stationBefore + 1L;

        assertFalse(finance.withdraw(impossible));
        assertEquals(playerBefore, runtime.player().walletMilliCredits());
        assertEquals(stationBefore, station.wallet().getBalanceMilliCredits());
    }

    private static StationFixture findStation(PlayerRuntime runtime) {
        SimulationSession session = runtime.world().findSession(runtime.world().getActiveSystemId()).orElseThrow();
        for (Entity entity : session.getEngine().getEntities()) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            EntityIdComponent id = entity.getComponent(EntityIdComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            WalletComponent wallet = entity.getComponent(WalletComponent.class);
            if (identity != null
                    && identity.kind == IdentityComponent.Kind.STATION
                    && id != null
                    && transform != null
                    && wallet != null
                    && entity.getComponent(MarketComponent.class) != null) {
                return new StationFixture(session, entity, id.id, transform, wallet);
            }
        }
        throw new AssertionError("Playable test world has no local market station");
    }

    private static void dock(PlayerRuntime runtime, StationFixture station) {
        FleetPlacementState placement = runtime.world().findFleet(runtime.player().activeFleetId()).orElseThrow();
        Entity ship = station.session().getEntityRegistry().find(placement.localEntityId());
        TransformComponent shipTransform = ship.getComponent(TransformComponent.class);
        shipTransform.position.set(station.transform().position);
        shipTransform.velocity.setZero();
        assertTrue(runtime.dockAt(station.entityId()));
    }

    private record StationFixture(
            SimulationSession session,
            Entity entity,
            com.spacesim.persistence.EntityId entityId,
            TransformComponent transform,
            WalletComponent wallet) {
    }
}
