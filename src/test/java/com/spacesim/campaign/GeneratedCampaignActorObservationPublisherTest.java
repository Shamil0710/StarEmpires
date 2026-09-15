package com.spacesim.campaign;

import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.FactionActorObservationSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCampaignActorObservationPublisherTest {
    @Test
    void publishesOnlyActorOwnedSemanticallyExplicitRelationMemory() {
        var memory = new DiplomaticLifecycleState.RelationMemory(
                "faction.alpha",
                "faction.beta",
                List.of(
                        event("trade", DiplomaticLifecycleState.RelationFactor.TRADE_DEPENDENCE, 45, 10L),
                        event("territory", DiplomaticLifecycleState.RelationFactor.TERRITORIAL_CONFLICT, -30, 11L),
                        event("threat", DiplomaticLifecycleState.RelationFactor.THREAT, -70, 12L),
                        event("commitment", DiplomaticLifecycleState.RelationFactor.DIPLOMATIC_COMMITMENT, 50, 13L),
                        event("generic", DiplomaticLifecycleState.RelationFactor.REMEMBERED_ACTION, -100, 14L),
                        event("performance", DiplomaticLifecycleState.RelationFactor.TREATY_PERFORMANCE, -80, 15L)));
        var state = new DiplomaticLifecycleState(
                DiplomaticLifecycleState.CURRENT_VERSION,
                20L,
                1L,
                1L,
                1L,
                List.of(memory),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        FactionActorObservationSnapshot snapshot = GeneratedCampaignActorObservationPublisher.publish(
                "faction.alpha", 20L, state);

        assertEquals(1, snapshot.economic().size());
        assertEquals(FactionActorObservationSnapshot.InterestKind.SUPPLY_DEPENDENCY,
                snapshot.economic().get(0).interestKind());
        assertEquals(4_500, snapshot.economic().get(0).severityBasisPoints());
        assertEquals(1, snapshot.territorial().size());
        assertEquals(1, snapshot.security().size());
        assertEquals(1, snapshot.diplomatic().size());
        assertEquals(4, snapshot.currentObservations().size(),
                "generic remembered action and past treaty performance must not be reinterpreted");
        snapshot.currentObservations().forEach(observation -> {
            assertEquals("faction.beta", observation.targetId());
            assertEquals(FactionActorObservationSnapshot.ObservationChannel.DIPLOMATIC_REGISTRY,
                    observation.evidence().channel());
            assertTrue(observation.evidence().provenanceId().startsWith("stage21c:relation:"));
        });
    }

    @Test
    void doesNotLeakOtherFactionMemoryOrFutureReports() {
        var alphaMemory = new DiplomaticLifecycleState.RelationMemory(
                "faction.alpha",
                "faction.beta",
                List.of(event("future-threat", DiplomaticLifecycleState.RelationFactor.THREAT, -90, 30L)));
        var betaMemory = new DiplomaticLifecycleState.RelationMemory(
                "faction.beta",
                "faction.alpha",
                List.of(event("beta-threat", DiplomaticLifecycleState.RelationFactor.THREAT, -90, 10L)));
        var state = new DiplomaticLifecycleState(
                DiplomaticLifecycleState.CURRENT_VERSION,
                30L,
                1L,
                1L,
                1L,
                List.of(alphaMemory, betaMemory),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        FactionActorObservationSnapshot alphaAt20 = GeneratedCampaignActorObservationPublisher.publish(
                "faction.alpha", 20L, state);

        assertTrue(alphaAt20.currentObservations().isEmpty(),
                "publisher must not expose another faction's memory or reports not yet observed at review time");
    }

    private static DiplomaticLifecycleState.RelationEvent event(
            String id,
            DiplomaticLifecycleState.RelationFactor factor,
            int impact,
            long tick) {
        return new DiplomaticLifecycleState.RelationEvent(id, factor, impact, tick, "subject:" + id);
    }
}
