package com.spacesim.player;

import com.spacesim.constants.Constants;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.world.FactionEconomicState;
import com.spacesim.world.FactionStrategicState;
import com.spacesim.world.WorldFactionIdentityState;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17FactionFoundationAcceptanceTest {
    @Test
    void foundingFactionCreatesZeroGrantWorldActorAndPreservesPhysicalOwnership() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_001L);
        PlayableWorldState source = independentSnapshot(scenario.runtime().snapshot());
        PlayerState before = source.playerState();

        PlayableWorldState founded = PlayerFactionFoundationService.foundFaction(
                source,
                scenario.content(),
                "faction.star_empire",
                "Star Empire");
        PlayerState after = founded.playerState();

        assertEquals("faction.star_empire", after.factionContentId());
        assertEquals(before.walletMilliCredits(), after.walletMilliCredits());
        assertEquals(before.ownedFleetIds(), after.ownedFleetIds());
        assertEquals(before.activeFleetId(), after.activeFleetId());
        assertEquals(before.ownedConstructionProjectIds(), after.ownedConstructionProjectIds());
        assertEquals(before.ownedStations(), after.ownedStations());
        assertEquals(source.worldState().systems(), founded.worldState().systems());
        assertEquals(source.worldState().fleets(), founded.worldState().fleets());
        assertEquals(source.worldState().constructionProjects(), founded.worldState().constructionProjects());

        FactionEconomicState economy = founded.worldState().factions().stream()
                .filter(state -> state.factionContentId().equals("faction.star_empire"))
                .findFirst()
                .orElseThrow();
        assertEquals(0L, economy.treasuryMilliCredits());
        assertEquals(0L, economy.stationLiquidityReserveMilliCredits());
        assertEquals(0L, economy.maxLiquiditySupportPerDecisionMilliCredits());

        FactionStrategicState strategy = founded.worldState().factionStrategies().stream()
                .filter(state -> state.factionContentId().equals("faction.star_empire"))
                .findFirst()
                .orElseThrow();
        assertTrue(strategy.controlledSystems().isEmpty());
        assertTrue(strategy.relations().isEmpty());
        assertEquals(0, strategy.stationTaxBasisPoints());
        assertEquals(0, strategy.foreignTerritoryTariffBasisPoints());

        WorldFactionIdentityState identity = founded.worldState().factionIdentities().stream()
                .filter(state -> state.stableFactionId().equals("faction.star_empire"))
                .findFirst()
                .orElseThrow();
        assertEquals(Constants.LEGACY_FACTION_COUNT, identity.runtimeFactionId());
        assertEquals("Star Empire", identity.displayName());
        assertEquals(WorldFactionIdentityState.Origin.PLAYER_CREATED, identity.origin());
    }

    @Test
    void foundedFactionSurvivesPlayableSaveRoundTripWithoutChangingPhysicalIds() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_002L);
        PlayableWorldState source = independentSnapshot(scenario.runtime().snapshot());
        PlayableWorldState founded = PlayerFactionFoundationService.foundFaction(
                source,
                scenario.content(),
                "faction.long_watch");

        PlayableWorldState restored = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(founded));

        assertEquals(founded, restored);
        assertEquals(source.playerState().ownedFleetIds(), restored.playerState().ownedFleetIds());
        assertEquals(source.worldState().fleets(), restored.worldState().fleets());
        assertEquals(source.worldState().systems(), restored.worldState().systems());
        assertEquals(1, restored.worldState().factionIdentities().size());
        assertEquals("faction.long_watch", restored.worldState().factionIdentities().get(0).stableFactionId());
    }

    @Test
    void foundingRejectsImplicitSecondFactionAndStableIdCollisions() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_003L);
        PlayableWorldState independent = independentSnapshot(scenario.runtime().snapshot());
        PlayableWorldState founded = PlayerFactionFoundationService.foundFaction(
                independent,
                scenario.content(),
                "faction.frontier_union");

        assertThrows(IllegalArgumentException.class, () -> PlayerFactionFoundationService.foundFaction(
                founded,
                scenario.content(),
                "faction.second_union"));

        PlayableWorldState independentAgain = new PlayableWorldState(
                PlayableWorldState.CURRENT_VERSION,
                founded.worldState(),
                copyWithFaction(founded.playerState(), null));
        assertThrows(IllegalArgumentException.class, () -> PlayerFactionFoundationService.foundFaction(
                independentAgain,
                scenario.content(),
                "faction.frontier_union"));

        String contentFactionId = scenario.content().getFactions().get(0).id();
        assertThrows(IllegalArgumentException.class, () -> PlayerFactionFoundationService.foundFaction(
                independent,
                scenario.content(),
                contentFactionId));
        assertThrows(IllegalArgumentException.class, () -> PlayerFactionFoundationService.foundFaction(
                independent,
                scenario.content(),
                "Player Faction"));
    }

    @Test
    void foundingPreservesExistingDynamicIdentityAndTakesLowestFreeRuntimeSlot() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_004L);
        PlayableWorldState independent = independentSnapshot(scenario.runtime().snapshot());
        WorldFactionIdentityState existing = new WorldFactionIdentityState(
                "faction.existing_dynamic",
                Constants.LEGACY_FACTION_COUNT,
                "Existing Dynamic",
                WorldFactionIdentityState.Origin.PLAYER_CREATED);
        List<WorldFactionIdentityState> identities = new ArrayList<>(independent.worldState().factionIdentities());
        identities.add(existing);
        WorldState worldWithIdentity = copyWithIdentities(independent.worldState(), identities);
        PlayableWorldState source = new PlayableWorldState(
                PlayableWorldState.CURRENT_VERSION,
                worldWithIdentity,
                independent.playerState());

        PlayableWorldState founded = PlayerFactionFoundationService.foundFaction(
                source,
                scenario.content(),
                "faction.new_union",
                "New Union");

        assertTrue(founded.worldState().factionIdentities().contains(existing));
        WorldFactionIdentityState created = founded.worldState().factionIdentities().stream()
                .filter(identity -> identity.stableFactionId().equals("faction.new_union"))
                .findFirst()
                .orElseThrow();
        assertEquals(Constants.LEGACY_FACTION_COUNT + 1, created.runtimeFactionId());
    }

    private static PlayableWorldState independentSnapshot(PlayableWorldState source) {
        return new PlayableWorldState(
                PlayableWorldState.CURRENT_VERSION,
                source.worldState(),
                copyWithFaction(source.playerState(), null));
    }

    private static PlayerState copyWithFaction(PlayerState source, String factionId) {
        return new PlayerState(
                source.walletMilliCredits(),
                factionId,
                source.reputations(),
                source.ownedFleetIds(),
                source.activeFleetId(),
                source.discoveredSystemIds(),
                source.discoveredObjects(),
                source.homeSystemId(),
                source.dockedAt(),
                source.fleetOrders(),
                source.threatIntel(),
                source.ownedConstructionProjectIds(),
                source.ownedStations());
    }

    private static WorldState copyWithIdentities(
            WorldState source,
            List<WorldFactionIdentityState> identities) {
        return new WorldState(
                WorldState.CURRENT_VERSION,
                source.topology(),
                source.systems(),
                source.factions(),
                source.factionStrategies(),
                source.nextConstructionProjectIdValue(),
                source.constructionProjects(),
                source.factionEconomicPressures(),
                source.nextFleetIdValue(),
                source.fleets(),
                source.fleetJumps(),
                identities);
    }
}
