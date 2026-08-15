package com.spacesim.player;

import com.spacesim.economy.EconomicLedger;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.economy.Money;
import com.spacesim.persistence.PlayableWorldStateCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17FactionTreasuryLiveReverseAcceptanceTest {
    private static final String PLAYER_FACTION_ID = "faction.stage17c_live_reverse_union";

    @Test
    void liveTreasuryWithdrawalRecordsOneMoneyTransferAndPreservesTotalMoney() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_631L);
        PlayerRuntime runtime = foundedRuntime(scenario);
        PlayerFactionTreasuryRuntimeService service = new PlayerFactionTreasuryRuntimeService(runtime);
        assertTrue(service.capitalize(Money.fromCredits(5_000d)));

        PlayerFactionTreasuryView before = service.view().orElseThrow();
        long amount = Money.fromCredits(2_000d);
        long totalBefore = Math.addExact(
                before.personalWalletMilliCredits(), before.factionTreasuryMilliCredits());
        EconomicLedger ledger = runtime.world().findSession(runtime.world().getActiveSystemId())
                .orElseThrow().getLedger();
        int ledgerSizeBefore = ledger.size();
        var systemsBefore = runtime.world().snapshot().systems();
        var fleetsBefore = runtime.world().snapshot().fleets();

        assertTrue(service.transferToPersonal(amount));

        PlayerFactionTreasuryView after = service.view().orElseThrow();
        assertEquals(before.personalWalletMilliCredits() + amount, after.personalWalletMilliCredits());
        assertEquals(before.factionTreasuryMilliCredits() - amount, after.factionTreasuryMilliCredits());
        assertEquals(totalBefore, Math.addExact(
                after.personalWalletMilliCredits(), after.factionTreasuryMilliCredits()));

        var systemsAfter = runtime.world().snapshot().systems();
        assertEquals(systemsBefore.size(), systemsAfter.size());
        for (int i = 0; i < systemsBefore.size(); i++) {
            assertEquals(systemsBefore.get(i).systemId(), systemsAfter.get(i).systemId());
            assertEquals(
                    systemsBefore.get(i).simulationState().entities(),
                    systemsAfter.get(i).simulationState().entities());
            assertEquals(
                    systemsBefore.get(i).simulationState().clock(),
                    systemsAfter.get(i).simulationState().clock());
        }
        assertEquals(fleetsBefore, runtime.world().snapshot().fleets());

        assertEquals(ledgerSizeBefore + 1, ledger.size());
        EconomicTransaction transfer = ledger.getEntries().get(ledger.size() - 1);
        assertEquals(EconomicTransaction.Type.MONEY_TRANSFER, transfer.type());
        assertEquals("faction:" + PLAYER_FACTION_ID + ":treasury", transfer.source());
        assertEquals("PLAYER", transfer.destination());
        assertEquals(amount, transfer.moneyMilliCredits());
        assertEquals("player-faction-treasury-to-personal", transfer.reason());
    }

    @Test
    void liveTreasuryWithdrawalSurvivesSaveLoadAndCanDrainExistingTreasury() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_632L);
        PlayerRuntime runtime = foundedRuntime(scenario);
        PlayerFactionTreasuryRuntimeService service = new PlayerFactionTreasuryRuntimeService(runtime);
        assertTrue(service.capitalize(Money.fromCredits(4_000d)));
        assertTrue(service.transferToPersonal(Money.fromCredits(1_500d)));
        PlayerFactionTreasuryView beforeSave = service.view().orElseThrow();

        PlayableWorldState decoded = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(runtime.snapshot()));
        PlayerRuntime restored = PlayerRuntime.restore(
                decoded,
                scenario.content(),
                runtime.world().getActiveSystemId());
        PlayerFactionTreasuryRuntimeService restoredService = new PlayerFactionTreasuryRuntimeService(restored);
        assertEquals(beforeSave, restoredService.view().orElseThrow());

        long totalBeforeDrain = Math.addExact(
                beforeSave.personalWalletMilliCredits(), beforeSave.factionTreasuryMilliCredits());
        assertTrue(restoredService.transferToPersonal(beforeSave.factionTreasuryMilliCredits()));
        PlayerFactionTreasuryView drained = restoredService.view().orElseThrow();
        assertEquals(0L, drained.factionTreasuryMilliCredits());
        assertEquals(totalBeforeDrain, Math.addExact(
                drained.personalWalletMilliCredits(), drained.factionTreasuryMilliCredits()));
    }

    @Test
    void rejectedLiveTreasuryWithdrawalLeavesBalancesAndLedgerUnchanged() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_633L);
        PlayerRuntime runtime = foundedRuntime(scenario);
        PlayerFactionTreasuryRuntimeService service = new PlayerFactionTreasuryRuntimeService(runtime);
        PlayerFactionTreasuryView before = service.view().orElseThrow();
        EconomicLedger ledger = runtime.world().findSession(runtime.world().getActiveSystemId())
                .orElseThrow().getLedger();
        int ledgerSizeBefore = ledger.size();

        assertEquals(0L, before.factionTreasuryMilliCredits());
        assertFalse(service.transferToPersonal(Money.fromCredits(1d)));
        assertEquals(before, service.view().orElseThrow());
        assertEquals(ledgerSizeBefore, ledger.size());
        assertThrows(IllegalArgumentException.class, () -> service.transferToPersonal(0L));
        assertThrows(IllegalArgumentException.class, () -> service.transferToPersonal(-1L));
        assertEquals(before, service.view().orElseThrow());
        assertEquals(ledgerSizeBefore, ledger.size());
    }

    @Test
    void independentPlayerCannotWithdrawFromAnyFactionTreasury() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_634L);
        PlayableWorldState independent = independentSnapshot(scenario.runtime().snapshot());
        PlayerRuntime runtime = PlayerRuntime.restore(
                independent,
                scenario.content(),
                scenario.runtime().world().getActiveSystemId());
        PlayerFactionTreasuryRuntimeService service = new PlayerFactionTreasuryRuntimeService(runtime);
        EconomicLedger ledger = runtime.world().findSession(runtime.world().getActiveSystemId())
                .orElseThrow().getLedger();
        int ledgerSizeBefore = ledger.size();
        long walletBefore = runtime.player().walletMilliCredits();

        assertTrue(service.view().isEmpty());
        assertFalse(service.transferToPersonal(Money.fromCredits(1d)));
        assertEquals(walletBefore, runtime.player().walletMilliCredits());
        assertEquals(ledgerSizeBefore, ledger.size());
    }

    private static PlayerRuntime foundedRuntime(PlayableTestWorldFactory.Scenario scenario) {
        PlayableWorldState founded = PlayerFactionFoundationService.foundFaction(
                independentSnapshot(scenario.runtime().snapshot()),
                scenario.content(),
                PLAYER_FACTION_ID,
                "Stage 17C Live Reverse Union");
        return PlayerRuntime.restore(
                founded,
                scenario.content(),
                scenario.runtime().world().getActiveSystemId());
    }

    private static PlayableWorldState independentSnapshot(PlayableWorldState source) {
        PlayerState player = source.playerState();
        PlayerState independent = new PlayerState(
                player.walletMilliCredits(),
                null,
                player.reputations(),
                player.ownedFleetIds(),
                player.activeFleetId(),
                player.discoveredSystemIds(),
                player.discoveredObjects(),
                player.homeSystemId(),
                player.dockedAt(),
                player.fleetOrders(),
                player.threatIntel(),
                player.ownedConstructionProjectIds(),
                player.ownedStations());
        return new PlayableWorldState(
                PlayableWorldState.CURRENT_VERSION,
                source.worldState(),
                independent);
    }
}
