package com.spacesim.player;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionFiscalPolicyState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17F2PlayerFactionFiscalAcceptanceTest {
    private static final String PLAYER_FACTION = "faction.player_fiscal";

    @Test
    void foundedPlayerFactionStartsWithZeroAuthorizationsAndUsesCommonFiscalBoundary() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_705L);
        PlayableWorldState independent = independentSnapshot(scenario.runtime().snapshot());
        PlayableWorldState founded = PlayerFactionFoundationService.foundFaction(
                independent,
                scenario.content(),
                PLAYER_FACTION,
                "Player Fiscal");

        FactionEconomicState initialEconomy = founded.worldState().factions().stream()
                .filter(state -> state.factionContentId().equals(PLAYER_FACTION))
                .findFirst()
                .orElseThrow();
        assertEquals(0L, initialEconomy.treasuryMilliCredits());
        assertEquals(0L, initialEconomy.stationLiquidityReserveMilliCredits());
        assertEquals(0L, initialEconomy.maxLiquiditySupportPerDecisionMilliCredits());
        assertEquals(0L, initialEconomy.treasuryReserveFloorMilliCredits());
        assertEquals(0L, initialEconomy.maxConstructionInvestmentPerDecisionMilliCredits());

        PlayerRuntime restored = PlayerRuntime.restore(
                founded,
                scenario.content(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        assertEquals(
                new FactionFiscalPolicyState(PLAYER_FACTION, 0, 0, 0, 0L, 0L, 0L, 0L),
                restored.world().findFactionFiscalPolicy(PLAYER_FACTION).orElseThrow());

        FactionFiscalPolicyState edited = new FactionFiscalPolicyState(
                PLAYER_FACTION,
                125,
                250,
                375,
                1_000L,
                2_000L,
                3_000L,
                4_000L);
        assertEquals(edited, restored.world().updateFactionFiscalPolicy(edited));
        assertEquals(edited, restored.world().findFactionFiscalPolicy(PLAYER_FACTION).orElseThrow());

        PlayableWorldState saved = restored.snapshot();
        assertFiscalPolicyStored(saved, edited);

        assertTrue(saved.playerState().walletMilliCredits() > 0L);
        PlayableWorldState capitalized = PlayerFactionTreasuryService.capitalize(saved, 1L);
        assertEquals(1L, factionEconomy(capitalized).treasuryMilliCredits());
        assertFiscalPolicyStored(capitalized, edited);

        PlayableWorldState returned = PlayerFactionTreasuryService.transferToPersonal(capitalized, 1L);
        assertEquals(0L, factionEconomy(returned).treasuryMilliCredits());
        assertFiscalPolicyStored(returned, edited);
    }

    private static void assertFiscalPolicyStored(
            PlayableWorldState state,
            FactionFiscalPolicyState expected) {
        FactionEconomicState economy = factionEconomy(state);
        assertEquals(expected.treasuryReserveFloorMilliCredits(), economy.treasuryReserveFloorMilliCredits());
        assertEquals(expected.stationLiquidityReserveMilliCredits(), economy.stationLiquidityReserveMilliCredits());
        assertEquals(
                expected.maxLiquiditySupportPerDecisionMilliCredits(),
                economy.maxLiquiditySupportPerDecisionMilliCredits());
        assertEquals(
                expected.maxConstructionInvestmentPerDecisionMilliCredits(),
                economy.maxConstructionInvestmentPerDecisionMilliCredits());
        assertEquals(expected.ownStationTaxBasisPoints(), state.worldState().factionStrategies().stream()
                .filter(strategy -> strategy.factionContentId().equals(PLAYER_FACTION))
                .findFirst()
                .orElseThrow()
                .stationTaxBasisPoints());
        assertEquals(expected.territorialForeignStationLevyBasisPoints(), state.worldState().factionStrategies().stream()
                .filter(strategy -> strategy.factionContentId().equals(PLAYER_FACTION))
                .findFirst()
                .orElseThrow()
                .foreignTerritoryTariffBasisPoints());
        assertEquals(expected.customsTariffBasisPoints(), state.worldState().factionDiplomacyStates().stream()
                .filter(diplomacy -> diplomacy.factionContentId().equals(PLAYER_FACTION))
                .findFirst()
                .orElseThrow()
                .customsTariffBasisPoints());
    }

    private static FactionEconomicState factionEconomy(PlayableWorldState state) {
        return state.worldState().factions().stream()
                .filter(economy -> economy.factionContentId().equals(PLAYER_FACTION))
                .findFirst()
                .orElseThrow();
    }

    private static PlayableWorldState independentSnapshot(PlayableWorldState source) {
        PlayerState player = source.playerState();
        PlayerState independentPlayer = new PlayerState(
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
                independentPlayer);
    }
}
