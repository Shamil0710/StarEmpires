package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.world.FactionActorObservationSnapshot.ActorObservation;
import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Stage21FTerritorialInterestAcceptanceTest {
    @Test
    void establishedControlFeedsFutureActorInterestThroughTerritoryLedgerOnly() {
        WorldSimulation world = DemoGalaxyFactory.create(21_610L);
        FactionStrategicState controller = world.snapshot().factionStrategies().stream()
                .filter(strategy -> !strategy.controlledSystems().isEmpty())
                .findFirst()
                .orElseThrow();
        StarSystemId systemId = controller.controlledSystems().get(0);
        long tick = world.getAuthoritativeWorldTick();

        List<ActorObservation> territorial = TerritorialInterestObservationAdapter.observe(
                world, controller.factionContentId(), systemId, tick);

        assertEquals(1, territorial.size());
        ActorObservation observation = territorial.get(0);
        assertEquals(InterestKind.BORDER_SECURITY, observation.interestKind());
        assertEquals(ObservationChannel.TERRITORY_LEDGER, observation.evidence().channel());
        assertEquals("system:" + systemId.value(), observation.targetId());

        FactionActorObservationSnapshot snapshot = new FactionActorObservationSnapshot(
                controller.factionContentId(), tick, List.of(), territorial, List.of(), List.of());
        FactionInterestResolver.DecisionTrace trace = FactionInterestResolver.resolve(snapshot);
        assertEquals(InterestKind.BORDER_SECURITY, trace.primaryInterest().orElseThrow().kind());
        assertEquals(observation.targetId(), trace.primaryInterest().orElseThrow().targetId());
    }

    @Test
    void ledgerAdapterDoesNotInventInterestWithoutOwnClaimOrControl() {
        WorldSimulation world = DemoGalaxyFactory.create(21_611L);
        FactionStrategicState actor = world.snapshot().factionStrategies().stream().findFirst().orElseThrow();
        StarSystemId unrelated = world.getTopology().systems().stream()
                .map(StarSystemNode::id)
                .filter(systemId -> !actor.controls(systemId) && actor.claimFor(systemId) == null)
                .findFirst()
                .orElseThrow();

        assertTrue(TerritorialInterestObservationAdapter.observe(
                world, actor.factionContentId(), unrelated, world.getAuthoritativeWorldTick()).isEmpty());
    }

    @Test
    void ledgerAdapterRejectsNonAuthoritativeObservationTick() {
        WorldSimulation world = DemoGalaxyFactory.create(21_612L);
        FactionStrategicState actor = world.snapshot().factionStrategies().stream().findFirst().orElseThrow();
        StarSystemId systemId = world.getTopology().systems().get(0).id();
        long authoritativeTick = world.getAuthoritativeWorldTick();

        assertThrows(IllegalArgumentException.class, () -> TerritorialInterestObservationAdapter.observe(
                world, actor.factionContentId(), systemId, authoritativeTick + 1L));
    }
}
