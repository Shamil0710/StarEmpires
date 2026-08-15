package com.spacesim.player;

import com.spacesim.economy.EconomicTransaction;
import com.spacesim.economy.Money;
import com.spacesim.persistence.PlayableWorldStateCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17FactionTreasuryCapitalizationAcceptanceTest {
    private static final String PLAYER_FACTION_ID = "faction.stage17c_union";

    @Test
    void explicitCapitalizationMovesExistingMoneyIntoOrdinaryFactionTreasury() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_601L);
        PlayerRuntime runtime = foundedRuntime(scenario);
        PlayerFactionTreasuryService service = new PlayerFactionTreasuryService(runtime);
        PlayerFactionTreasuryView before = service.view().orElseThrow();
        long amount = Money.fromCredits(5_000d);
        long totalBefore = Math.addExact(
                before.personalWalletMilliCredits(), before.factionTreasuryMilliCredits());
        int ledgerSizeBefore = runtime.world().findSession(runtime.world().getActiveSystemId())
                .orElseThrow().getLedger().size();

        assertTrue(service.capitalize(amount));

        PlayerFactionTreasuryView after = service.view().orElseThrow();
        assertEquals(before.personalWalletMilliCredits() - amount, after.personalWalletMilliCredits());
        assertEquals(before.factionTreasuryMilliCredits() + amount, after.factionTreasuryMilliCredits());
        assertEquals(totalBefore, Math.addExact(
                after.personalWalletMilliCredits(), after.factionTreasuryMilliCredits()));

        var ledger = runtime.world().findSession(runtime.world().getActiveSystemId()).orElseThrow().getLedger();
        assertEquals(ledgerSizeBefore + 1, ledger.size());
        EconomicTransaction transfer = ledger.getEntries().get(ledger.size() - 1);
        assertEquals(EconomicTransaction.Type.MONEY_TRANSFER, transfer.type());
        assertEquals("PLAYER", transfer.source());
        assertEquals("faction:" + PLAYER_FACTION_ID + ":treasury", transfer.destination());
        assertEquals(amount, transfer.moneyMilliCredits());
        assertEquals("player-faction-capitalization", transfer.reason());
    }

    @Test
    void capitalizationSurvivesPlayableRoundTripAndCanContinueWithoutMoneyCreation() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_602L);
        PlayerRuntime runtime = foundedRuntime(scenario);
        PlayerFactionTreasuryService service = new PlayerFactionTreasuryService(runtime);
        long firstAmount = Money.fromCredits(4_000d);
        assertTrue(service.capitalize(firstAmount));
        PlayerFactionTreasuryView beforeSave = service.view().orElseThrow();

        PlayableWorldState decoded = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(runtime.snapshot()));
        PlayerRuntime restored = PlayerRuntime.restore(
                decoded,
                scenario.content(),
                runtime.world().getActiveSystemId());
        PlayerFactionTreasuryService restoredService = new PlayerFactionTreasuryService(restored);
        PlayerFactionTreasuryView afterRestore = restoredService.view().orElseThrow();
        assertEquals(beforeSave, afterRestore);

        long secondAmount = Money.fromCredits(1_000d);
        long totalBeforeSecond = Math.addExact(
                afterRestore.personalWalletMilliCredits(), afterRestore.factionTreasuryMilliCredits());
        assertTrue(restoredService.capitalize(secondAmount));
        PlayerFactionTreasuryView afterSecond = restoredService.view().orElseThrow();
        assertEquals(totalBeforeSecond, Math.addExact(
                afterSecond.personalWalletMilliCredits(), afterSecond.factionTreasuryMilliCredits()));
        assertEquals(afterRestore.factionTreasuryMilliCredits() + secondAmount,
                afterSecond.factionTreasuryMilliCredits());
    }

    @Test
    void capitalizationRejectsImplicitGrantsInvalidAmountsAndIndependentPlayer() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_603L);
        PlayableWorldState independent = independentSnapshot(scenario.runtime().snapshot());
        PlayerRuntime independentRuntime = PlayerRuntime.restore(
                independent,
                scenario.content(),
                scenario.runtime().world().getActiveSystemId());
        PlayerFactionTreasuryService independentService = new PlayerFactionTreasuryService(independentRuntime);
        long independentWallet = independentRuntime.player().walletMilliCredits();

        assertTrue(independentService.view().isEmpty());
        assertFalse(independentService.capitalize(Money.fromCredits(1d)));
        assertEquals(independentWallet, independentRuntime.player().walletMilliCredits());
        assertThrows(IllegalArgumentException.class, () -> independentService.capitalize(0L));
        assertThrows(IllegalArgumentException.class, () -> independentService.capitalize(-1L));

        PlayerRuntime founded = foundedRuntime(scenario);
        PlayerFactionTreasuryService foundedService = new PlayerFactionTreasuryService(founded);
        PlayerFactionTreasuryView before = foundedService.view().orElseThrow();
        assertEquals(0L, before.factionTreasuryMilliCredits());
        assertFalse(foundedService.capitalize(before.personalWalletMilliCredits() + 1L));
        assertEquals(before, foundedService.view().orElseThrow());
    }

    private static PlayerRuntime foundedRuntime(PlayableTestWorldFactory.Scenario scenario) {
        PlayableWorldState founded = PlayerFactionFoundationService.foundFaction(
                independentSnapshot(scenario.runtime().snapshot()),
                scenario.content(),
                PLAYER_FACTION_ID,
                "Stage 17C Union");
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
