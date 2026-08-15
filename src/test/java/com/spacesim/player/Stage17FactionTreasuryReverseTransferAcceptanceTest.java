package com.spacesim.player;

import com.spacesim.economy.Money;
import com.spacesim.persistence.PlayableWorldStateCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage17FactionTreasuryReverseTransferAcceptanceTest {
    private static final String PLAYER_FACTION_ID = "faction.stage17c_reverse_union";

    @Test
    void explicitTreasuryToPersonalTransferConservesMoneyAndPhysicalState() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_621L);
        PlayableWorldState founded = foundedSnapshot(scenario);
        PlayableWorldState capitalized = PlayerFactionTreasuryService.capitalize(
                founded,
                Money.fromCredits(5_000d));
        PlayerFactionTreasuryView before = PlayerFactionTreasuryService.view(capitalized).orElseThrow();
        long amount = Money.fromCredits(2_000d);
        long totalBefore = Math.addExact(
                before.personalWalletMilliCredits(), before.factionTreasuryMilliCredits());

        PlayableWorldState transferred = PlayerFactionTreasuryService.transferToPersonal(capitalized, amount);
        PlayerFactionTreasuryView after = PlayerFactionTreasuryService.view(transferred).orElseThrow();

        assertEquals(before.personalWalletMilliCredits() + amount, after.personalWalletMilliCredits());
        assertEquals(before.factionTreasuryMilliCredits() - amount, after.factionTreasuryMilliCredits());
        assertEquals(totalBefore, Math.addExact(
                after.personalWalletMilliCredits(), after.factionTreasuryMilliCredits()));
        assertEquals(capitalized.worldState().systems(), transferred.worldState().systems());
        assertEquals(capitalized.worldState().fleets(), transferred.worldState().fleets());
        assertEquals(capitalized.worldState().fleetJumps(), transferred.worldState().fleetJumps());
        assertEquals(capitalized.worldState().constructionProjects(), transferred.worldState().constructionProjects());
        assertEquals(capitalized.worldState().factionStrategies(), transferred.worldState().factionStrategies());
        assertEquals(capitalized.playerState().ownedFleetIds(), transferred.playerState().ownedFleetIds());
        assertEquals(capitalized.playerState().ownedStations(), transferred.playerState().ownedStations());
    }

    @Test
    void reverseTransferSurvivesRoundTripAndCanDrainOnlyExistingTreasury() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_622L);
        PlayableWorldState capitalized = PlayerFactionTreasuryService.capitalize(
                foundedSnapshot(scenario),
                Money.fromCredits(4_000d));
        PlayableWorldState first = PlayerFactionTreasuryService.transferToPersonal(
                capitalized,
                Money.fromCredits(1_500d));
        PlayerFactionTreasuryView beforeSave = PlayerFactionTreasuryService.view(first).orElseThrow();

        PlayableWorldState restored = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(first));
        assertEquals(first, restored);
        assertEquals(beforeSave, PlayerFactionTreasuryService.view(restored).orElseThrow());

        PlayableWorldState drained = PlayerFactionTreasuryService.transferToPersonal(
                restored,
                beforeSave.factionTreasuryMilliCredits());
        PlayerFactionTreasuryView afterDrain = PlayerFactionTreasuryService.view(drained).orElseThrow();
        assertEquals(0L, afterDrain.factionTreasuryMilliCredits());
        assertEquals(
                Math.addExact(beforeSave.personalWalletMilliCredits(), beforeSave.factionTreasuryMilliCredits()),
                afterDrain.personalWalletMilliCredits());
    }

    @Test
    void reverseTransferRejectsMissingFundsInvalidAmountAndIndependentPlayer() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_623L);
        PlayableWorldState founded = foundedSnapshot(scenario);
        PlayerFactionTreasuryView zeroTreasury = PlayerFactionTreasuryService.view(founded).orElseThrow();
        assertEquals(0L, zeroTreasury.factionTreasuryMilliCredits());
        assertThrows(IllegalArgumentException.class, () ->
                PlayerFactionTreasuryService.transferToPersonal(founded, Money.fromCredits(1d)));
        assertThrows(IllegalArgumentException.class, () ->
                PlayerFactionTreasuryService.transferToPersonal(founded, 0L));
        assertThrows(IllegalArgumentException.class, () ->
                PlayerFactionTreasuryService.transferToPersonal(founded, -1L));
        assertEquals(zeroTreasury, PlayerFactionTreasuryService.view(founded).orElseThrow());

        PlayableWorldState independent = independentSnapshot(scenario.runtime().snapshot());
        assertThrows(IllegalArgumentException.class, () ->
                PlayerFactionTreasuryService.transferToPersonal(independent, Money.fromCredits(1d)));
    }

    private static PlayableWorldState foundedSnapshot(PlayableTestWorldFactory.Scenario scenario) {
        return PlayerFactionFoundationService.foundFaction(
                independentSnapshot(scenario.runtime().snapshot()),
                scenario.content(),
                PLAYER_FACTION_ID,
                "Stage 17C Reverse Union");
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
