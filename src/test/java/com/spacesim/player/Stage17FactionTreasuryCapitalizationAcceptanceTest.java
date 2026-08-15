package com.spacesim.player;

import com.spacesim.economy.Money;
import com.spacesim.persistence.PlayableWorldStateCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17FactionTreasuryCapitalizationAcceptanceTest {
    private static final String PLAYER_FACTION_ID = "faction.stage17c_union";

    @Test
    void explicitCapitalizationMovesExistingMoneyWithoutChangingPhysicalWorld() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_601L);
        PlayableWorldState founded = foundedSnapshot(scenario);
        PlayerFactionTreasuryView before = PlayerFactionTreasuryService.view(founded).orElseThrow();
        long amount = Money.fromCredits(5_000d);
        long totalBefore = Math.addExact(
                before.personalWalletMilliCredits(), before.factionTreasuryMilliCredits());

        PlayableWorldState capitalized = PlayerFactionTreasuryService.capitalize(founded, amount);
        PlayerFactionTreasuryView after = PlayerFactionTreasuryService.view(capitalized).orElseThrow();

        assertEquals(before.personalWalletMilliCredits() - amount, after.personalWalletMilliCredits());
        assertEquals(before.factionTreasuryMilliCredits() + amount, after.factionTreasuryMilliCredits());
        assertEquals(totalBefore, Math.addExact(
                after.personalWalletMilliCredits(), after.factionTreasuryMilliCredits()));
        assertEquals(founded.worldState().systems(), capitalized.worldState().systems());
        assertEquals(founded.worldState().fleets(), capitalized.worldState().fleets());
        assertEquals(founded.worldState().fleetJumps(), capitalized.worldState().fleetJumps());
        assertEquals(founded.worldState().constructionProjects(), capitalized.worldState().constructionProjects());
        assertEquals(founded.worldState().factionStrategies(), capitalized.worldState().factionStrategies());
        assertEquals(founded.worldState().factionIdentities(), capitalized.worldState().factionIdentities());
        assertEquals(founded.playerState().ownedFleetIds(), capitalized.playerState().ownedFleetIds());
        assertEquals(founded.playerState().ownedStations(), capitalized.playerState().ownedStations());
    }

    @Test
    void capitalizationSurvivesPlayableRoundTripAndCanContinueWithoutMoneyCreation() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_602L);
        PlayableWorldState founded = foundedSnapshot(scenario);
        PlayableWorldState first = PlayerFactionTreasuryService.capitalize(
                founded,
                Money.fromCredits(4_000d));
        PlayerFactionTreasuryView beforeSave = PlayerFactionTreasuryService.view(first).orElseThrow();

        PlayableWorldState restored = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(first));
        assertEquals(first, restored);
        assertEquals(beforeSave, PlayerFactionTreasuryService.view(restored).orElseThrow());

        long secondAmount = Money.fromCredits(1_000d);
        long totalBeforeSecond = Math.addExact(
                beforeSave.personalWalletMilliCredits(), beforeSave.factionTreasuryMilliCredits());
        PlayableWorldState second = PlayerFactionTreasuryService.capitalize(restored, secondAmount);
        PlayerFactionTreasuryView afterSecond = PlayerFactionTreasuryService.view(second).orElseThrow();
        assertEquals(totalBeforeSecond, Math.addExact(
                afterSecond.personalWalletMilliCredits(), afterSecond.factionTreasuryMilliCredits()));
        assertEquals(beforeSave.factionTreasuryMilliCredits() + secondAmount,
                afterSecond.factionTreasuryMilliCredits());
    }

    @Test
    void capitalizationRejectsImplicitGrantsInvalidAmountsAndIndependentPlayer() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_603L);
        PlayableWorldState independent = independentSnapshot(scenario.runtime().snapshot());
        long independentWallet = independent.playerState().walletMilliCredits();

        assertTrue(PlayerFactionTreasuryService.view(independent).isEmpty());
        assertThrows(IllegalArgumentException.class, () ->
                PlayerFactionTreasuryService.capitalize(independent, Money.fromCredits(1d)));
        assertEquals(independentWallet, independent.playerState().walletMilliCredits());
        assertThrows(IllegalArgumentException.class, () ->
                PlayerFactionTreasuryService.capitalize(independent, 0L));
        assertThrows(IllegalArgumentException.class, () ->
                PlayerFactionTreasuryService.capitalize(independent, -1L));

        PlayableWorldState founded = foundedSnapshot(scenario);
        PlayerFactionTreasuryView before = PlayerFactionTreasuryService.view(founded).orElseThrow();
        assertEquals(0L, before.factionTreasuryMilliCredits());
        assertThrows(IllegalArgumentException.class, () -> PlayerFactionTreasuryService.capitalize(
                founded,
                before.personalWalletMilliCredits() + 1L));
        assertEquals(before, PlayerFactionTreasuryService.view(founded).orElseThrow());
    }

    private static PlayableWorldState foundedSnapshot(PlayableTestWorldFactory.Scenario scenario) {
        return PlayerFactionFoundationService.foundFaction(
                independentSnapshot(scenario.runtime().snapshot()),
                scenario.content(),
                PLAYER_FACTION_ID,
                "Stage 17C Union");
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
