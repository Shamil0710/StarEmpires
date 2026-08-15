package com.spacesim.player;

import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17PlayerRuntimeAffiliationAcceptanceTest {
    private static final String PLAYER_FACTION_ID = "faction.player.runtime_bridge";

    @Test
    void foundedFactionAndReputationSurvivePlayableRestore() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_351L);
        StarSystemId activeSystem = scenario.runtime().world().getActiveSystemId();
        PlayableWorldState independent = independentSnapshot(scenario.runtime().snapshot());
        PlayableWorldState founded = PlayerFactionFoundationService.foundFaction(
                independent,
                scenario.content(),
                PLAYER_FACTION_ID,
                "Runtime Bridge Union");
        PlayableWorldState withDynamicReputation = new PlayableWorldState(
                PlayableWorldState.CURRENT_VERSION,
                founded.worldState(),
                copyWithReputations(
                        founded.playerState(),
                        appendReputation(
                                founded.playerState().reputations(),
                                new PlayerReputationState(PLAYER_FACTION_ID, 7.5f))));

        PlayableWorldState decoded = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(withDynamicReputation));
        PlayerRuntime restored = PlayerRuntime.restore(decoded, scenario.content(), activeSystem);

        assertEquals(PLAYER_FACTION_ID, restored.player().factionContentId());
        assertEquals(3, restored.world().findFactionRuntimeId(PLAYER_FACTION_ID).orElseThrow());
        assertTrue(restored.player().reputations().stream()
                .anyMatch(reputation -> reputation.factionContentId().equals(PLAYER_FACTION_ID)
                        && Float.compare(reputation.value(), 7.5f) == 0));

        PlayableWorldState snapshot = restored.snapshot();
        assertEquals(PLAYER_FACTION_ID, snapshot.playerState().factionContentId());
        assertEquals(decoded.worldState().factionIdentities(), snapshot.worldState().factionIdentities());
        assertEquals(decoded.playerState().ownedFleetIds(), snapshot.playerState().ownedFleetIds());
    }

    @Test
    void unknownPlayerFactionStillFailsPlayableRestore() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(17_352L);
        StarSystemId activeSystem = scenario.runtime().world().getActiveSystemId();
        PlayableWorldState source = scenario.runtime().snapshot();
        PlayerState invalid = copyWithFaction(source.playerState(), "faction.missing_runtime_identity");
        PlayableWorldState invalidState = new PlayableWorldState(
                PlayableWorldState.CURRENT_VERSION,
                source.worldState(),
                invalid);

        assertThrows(IllegalArgumentException.class,
                () -> PlayerRuntime.restore(invalidState, scenario.content(), activeSystem));
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

    private static PlayerState copyWithReputations(
            PlayerState source,
            List<PlayerReputationState> reputations) {
        return new PlayerState(
                source.walletMilliCredits(),
                source.factionContentId(),
                reputations,
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

    private static List<PlayerReputationState> appendReputation(
            List<PlayerReputationState> current,
            PlayerReputationState added) {
        List<PlayerReputationState> result = new ArrayList<>(current);
        result.add(added);
        return List.copyOf(result);
    }
}
