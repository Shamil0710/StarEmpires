package com.spacesim.player;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.world.FactionDoctrineState;
import com.spacesim.world.WorldSimulation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Stage17FPlayerFactionDoctrineAcceptanceTest {
    @Test
    void foundedPlayerFactionStartsNeutralAndUsesTheSameEditableWorldBoundary() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_604L);
        PlayableWorldState independent = independentSnapshot(scenario.runtime().snapshot());
        PlayableWorldState founded = PlayerFactionFoundationService.foundFaction(
                independent,
                scenario.content(),
                "faction.player_doctrine",
                "Player Doctrine");

        assertEquals(FactionDoctrineState.neutral(), founded.worldState().factionStrategies().stream()
                .filter(strategy -> strategy.factionContentId().equals("faction.player_doctrine"))
                .findFirst()
                .orElseThrow()
                .doctrine());

        PlayerRuntime restored = PlayerRuntime.restore(
                founded,
                scenario.content(),
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID);
        FactionDoctrineState edited = new FactionDoctrineState(78, 62, 71, 44, 83, 39, 69);
        restored.world().updateFactionDoctrine("faction.player_doctrine", edited);

        assertEquals(edited, restored.world().findFactionStrategicState("faction.player_doctrine")
                .orElseThrow()
                .doctrine());
        assertEquals(edited, restored.snapshot().worldState().factionStrategies().stream()
                .filter(strategy -> strategy.factionContentId().equals("faction.player_doctrine"))
                .findFirst()
                .orElseThrow()
                .doctrine());
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
