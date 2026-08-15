package com.spacesim.player;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.world.FactionDiplomacyState;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionFiscalPolicyState;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17F2FiscalContinuityAcceptanceTest {
    private static final String PLAYER_FACTION = "faction.player_fiscal_continuity";
    private static final String TRADE_LEAGUE = "faction.trade_league";

    @Test
    void foundingAndPureTreasuryTransitionsPreserveLaterFiscalAndDiplomacyLayers() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_712L);
        PlayableWorldState source = withNonNeutralDiplomacy(scenario.runtime().snapshot());
        PlayableWorldState independent = independentSnapshot(source);
        FactionDiplomacyState tradeLeagueBefore = independent.worldState().factionDiplomacyStates().stream()
                .filter(state -> state.factionContentId().equals(TRADE_LEAGUE))
                .findFirst()
                .orElseThrow();
        int diplomacyCountBefore = independent.worldState().factionDiplomacyStates().size();

        PlayableWorldState founded = PlayerFactionFoundationService.foundFaction(
                independent,
                scenario.content(),
                PLAYER_FACTION,
                "Fiscal Continuity");

        assertEquals(diplomacyCountBefore + 1, founded.worldState().factionDiplomacyStates().size());
        assertEquals(tradeLeagueBefore, founded.worldState().factionDiplomacyStates().stream()
                .filter(state -> state.factionContentId().equals(TRADE_LEAGUE))
                .findFirst()
                .orElseThrow());
        assertEquals(FactionDiplomacyState.neutral(PLAYER_FACTION), founded.worldState().factionDiplomacyStates().stream()
                .filter(state -> state.factionContentId().equals(PLAYER_FACTION))
                .findFirst()
                .orElseThrow());

        FactionEconomicState initialEconomy = factionEconomy(founded);
        assertEquals(0L, initialEconomy.treasuryMilliCredits());
        assertEquals(0L, initialEconomy.stationLiquidityReserveMilliCredits());
        assertEquals(0L, initialEconomy.maxLiquiditySupportPerDecisionMilliCredits());
        assertEquals(0L, initialEconomy.treasuryReserveFloorMilliCredits());
        assertEquals(0L, initialEconomy.maxConstructionInvestmentPerDecisionMilliCredits());

        PlayerRuntime runtime = PlayerRuntime.restore(
                founded,
                scenario.content(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        FactionFiscalPolicyState policy = new FactionFiscalPolicyState(
                125,
                250,
                1_000L,
                2_000L,
                3_000L,
                4_000L);
        assertEquals(policy, runtime.world().updateFactionFiscalPolicy(PLAYER_FACTION, policy));
        PlayableWorldState configured = runtime.snapshot();
        assertFiscalPolicy(configured, policy);
        List<FactionDiplomacyState> diplomacyBeforeTreasuryTransfer = configured.worldState().factionDiplomacyStates();

        assertTrue(configured.playerState().walletMilliCredits() > 0L);
        PlayableWorldState capitalized = PlayerFactionTreasuryService.capitalize(configured, 1L);
        assertEquals(1L, factionEconomy(capitalized).treasuryMilliCredits());
        assertFiscalPolicy(capitalized, policy);
        assertEquals(diplomacyBeforeTreasuryTransfer, capitalized.worldState().factionDiplomacyStates());

        PlayableWorldState returned = PlayerFactionTreasuryService.transferToPersonal(capitalized, 1L);
        assertEquals(0L, factionEconomy(returned).treasuryMilliCredits());
        assertFiscalPolicy(returned, policy);
        assertEquals(diplomacyBeforeTreasuryTransfer, returned.worldState().factionDiplomacyStates());
    }

    private static PlayableWorldState withNonNeutralDiplomacy(PlayableWorldState source) {
        WorldState world = source.worldState();
        List<FactionDiplomacyState> diplomacy = new ArrayList<>(world.factionDiplomacyStates().size());
        for (FactionDiplomacyState state : world.factionDiplomacyStates()) {
            if (state.factionContentId().equals(TRADE_LEAGUE)) {
                diplomacy.add(new FactionDiplomacyState(
                        state.factionContentId(),
                        state.standings(),
                        state.grievances(),
                        state.treaties(),
                        state.embargoes(),
                        1_234));
            } else {
                diplomacy.add(state);
            }
        }
        WorldState modified = new WorldState(
                world.schemaVersion(),
                world.topology(),
                world.systems(),
                world.factions(),
                world.factionStrategies(),
                world.nextConstructionProjectIdValue(),
                world.constructionProjects(),
                world.factionEconomicPressures(),
                world.nextFleetIdValue(),
                world.fleets(),
                world.fleetJumps(),
                world.factionIdentities(),
                diplomacy);
        return new PlayableWorldState(source.schemaVersion(), modified, source.playerState());
    }

    private static void assertFiscalPolicy(
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
        assertEquals(expected.stationTaxBasisPoints(), state.worldState().factionStrategies().stream()
                .filter(strategy -> strategy.factionContentId().equals(PLAYER_FACTION))
                .findFirst()
                .orElseThrow()
                .stationTaxBasisPoints());
        assertEquals(expected.foreignTerritoryLevyBasisPoints(), state.worldState().factionStrategies().stream()
                .filter(strategy -> strategy.factionContentId().equals(PLAYER_FACTION))
                .findFirst()
                .orElseThrow()
                .foreignTerritoryTariffBasisPoints());
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
