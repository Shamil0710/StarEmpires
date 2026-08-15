package com.spacesim.player;

import com.spacesim.economy.EconomicLedger;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.economy.Money;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17FactionTreasuryRuntimeAcceptanceTest {
    private static final String PLAYER_FACTION_ID = "faction.stage17c_live_union";

    @Test
    void liveCapitalizationRecordsOneMoneyTransferAndPreservesTotalMoney() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_611L);
        PlayerRuntime runtime = foundedRuntime(scenario);
        PlayerFactionTreasuryRuntimeService service = new PlayerFactionTreasuryRuntimeService(runtime);
        PlayerFactionTreasuryView before = service.view().orElseThrow();
        long amount = Money.fromCredits(5_000d);
        long totalBefore = Math.addExact(
                before.personalWalletMilliCredits(), before.factionTreasuryMilliCredits());
        SimulationSession activeSession = runtime.world().findSession(runtime.world().getActiveSystemId()).orElseThrow();
        EconomicLedger ledger = activeSession.getLedger();
        int ledgerSizeBefore = ledger.size();
        var systemsBefore = runtime.world().snapshot().systems();
        var fleetsBefore = runtime.world().snapshot().fleets();

        assertTrue(service.capitalize(amount));

        PlayerFactionTreasuryView after = service.view().orElseThrow();
        assertEquals(before.personalWalletMilliCredits() - amount, after.personalWalletMilliCredits());
        assertEquals(before.factionTreasuryMilliCredits() + amount, after.factionTreasuryMilliCredits());
        assertEquals(totalBefore, Math.addExact(
                after.personalWalletMilliCredits(), after.factionTreasuryMilliCredits()));
        assertEquals(systemsBefore, runtime.world().snapshot().systems());
        assertEquals(fleetsBefore, runtime.world().snapshot().fleets());

        assertEquals(ledgerSizeBefore + 1, ledger.size());
        EconomicTransaction transfer = ledger.getEntries().get(ledger.size() - 1);
        assertEquals(EconomicTransaction.Type.MONEY_TRANSFER, transfer.type());
        assertEquals("PLAYER", transfer.source());
        assertEquals("faction:" + PLAYER_FACTION_ID + ":treasury", transfer.destination());
        assertEquals(amount, transfer.moneyMilliCredits());
        assertEquals("player-faction-capitalization", transfer.reason());
    }

    @Test
    void liveCapitalizationSurvivesSaveLoadAndCanContinueConservatively() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_612L);
        PlayerRuntime runtime = foundedRuntime(scenario);
        PlayerFactionTreasuryRuntimeService service = new PlayerFactionTreasuryRuntimeService(runtime);
        assertTrue(service.capitalize(Money.fromCredits(4_000d)));
        PlayerFactionTreasuryView beforeSave = service.view().orElseThrow();

        PlayableWorldState decoded = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(runtime.snapshot()));
        PlayerRuntime restored = PlayerRuntime.restore(
                decoded,
                scenario.content(),
                runtime.world().getActiveSystemId());
        PlayerFactionTreasuryRuntimeService restoredService = new PlayerFactionTreasuryRuntimeService(restored);
        assertEquals(beforeSave, restoredService.view().orElseThrow());

        long secondAmount = Money.fromCredits(1_000d);
        long totalBeforeSecond = Math.addExact(
                beforeSave.personalWalletMilliCredits(), beforeSave.factionTreasuryMilliCredits());
        assertTrue(restoredService.capitalize(secondAmount));
        PlayerFactionTreasuryView afterSecond = restoredService.view().orElseThrow();
        assertEquals(totalBeforeSecond, Math.addExact(
                afterSecond.personalWalletMilliCredits(), afterSecond.factionTreasuryMilliCredits()));
        assertEquals(beforeSave.factionTreasuryMilliCredits() + secondAmount,
                afterSecond.factionTreasuryMilliCredits());
    }

    @Test
    void rejectedLiveCapitalizationLeavesPlayerTreasuryAndLedgerUnchanged() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_613L);
        PlayerRuntime runtime = foundedRuntime(scenario);
        PlayerFactionTreasuryRuntimeService service = new PlayerFactionTreasuryRuntimeService(runtime);
        PlayerFactionTreasuryView before = service.view().orElseThrow();
        EconomicLedger ledger = runtime.world().findSession(runtime.world().getActiveSystemId())
                .orElseThrow().getLedger();
        int ledgerSizeBefore = ledger.size();

        assertFalse(service.capitalize(before.personalWalletMilliCredits() + 1L));
        assertEquals(before, service.view().orElseThrow());
        assertEquals(ledgerSizeBefore, ledger.size());
        assertThrows(IllegalArgumentException.class, () -> service.capitalize(0L));
        assertThrows(IllegalArgumentException.class, () -> service.capitalize(-1L));
        assertEquals(before, service.view().orElseThrow());
        assertEquals(ledgerSizeBefore, ledger.size());
    }

    @Test
    void independentPlayerCannotReachAnyFactionTreasury() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_614L);
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
        assertFalse(service.capitalize(Money.fromCredits(1d)));
        assertEquals(walletBefore, runtime.player().walletMilliCredits());
        assertEquals(ledgerSizeBefore, ledger.size());
    }

    private static PlayerRuntime foundedRuntime(PlayableTestWorldFactory.Scenario scenario) {
        PlayableWorldState founded = PlayerFactionFoundationService.foundFaction(
                independentSnapshot(scenario.runtime().snapshot()),
                scenario.content(),
                PLAYER_FACTION_ID,
                "Stage 17C Live Union");
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
