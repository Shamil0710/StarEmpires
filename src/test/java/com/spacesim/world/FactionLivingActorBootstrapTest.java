package com.spacesim.world;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionLivingActorBootstrapTest {

    @Test
    void bootstrapCreatesOneStableStaggeredActorStatePerAuthorizedFaction() {
        List<FactionLivingActorState> first = FactionLivingActorBootstrap.bootstrap(
                List.of("faction.charlie", "faction.alpha", "faction.bravo", "faction.alpha"),
                100L,
                20L);
        List<FactionLivingActorState> second = FactionLivingActorBootstrap.bootstrap(
                List.of("faction.bravo", "faction.charlie", "faction.alpha"),
                100L,
                20L);

        assertEquals(first, second);
        assertEquals(
                List.of("faction.alpha", "faction.bravo", "faction.charlie"),
                first.stream().map(FactionLivingActorState::factionContentId).toList());
        assertTrue(first.stream().allMatch(state -> state.nextReviewTick() >= 100L && state.nextReviewTick() < 120L));
    }
}
