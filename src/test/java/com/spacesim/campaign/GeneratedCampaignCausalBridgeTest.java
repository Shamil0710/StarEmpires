package com.spacesim.campaign;

import com.spacesim.world.FactionInterestResolver;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCampaignCausalBridgeTest {
    @Test
    void stage20FreightOrderIdentityReachesActorObservationAndDecisionTrace() {
        GeneratedCampaignCoordinator campaign = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        campaign.advanceFrame(0.4f);
        var stage20 = campaign.session().captureState();

        assertFalse(stage20.freight().orders().isEmpty(),
                "ordinary generated campaign must expose its persisted freight orders");
        var order = stage20.freight().orders().get(0);
        long nowTick = campaign.runtime().world().getAuthoritativeWorldTick();

        var snapshot = GeneratedCampaignFactionObservationPublisher.publish(
                stage20,
                order.stableFactionId(),
                nowTick);
        var observation = snapshot.economic().stream()
                .filter(row -> row.targetId().equals(order.orderId()))
                .findFirst()
                .orElseThrow();
        var trace = FactionInterestResolver.resolve(snapshot);

        assertEquals(order.orderId(), observation.evidence().provenanceId());
        assertTrue(trace.orderedEvidence().stream().anyMatch(evidence ->
                        evidence.targetId().equals(order.orderId())
                                && evidence.supportingObservations().stream().anyMatch(row ->
                                row.evidence().provenanceId().equals(order.orderId()))),
                "the decision trace must retain the exact physical transport-order identity");
    }

    @Test
    void actorReviewBridgeIsInvariantToEightTimesPresentationFramePartitioning() {
        GeneratedCampaignCoordinator bootstrap = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        var checkpoint = bootstrap.captureState();
        GeneratedCampaignCoordinator coarse = GeneratedCampaignCoordinator.restore(checkpoint);
        GeneratedCampaignCoordinator fine = GeneratedCampaignCoordinator.restore(checkpoint);
        coarse.setTimeScale(8d);
        fine.setTimeScale(8d);

        long startTick = coarse.runtime().world().getAuthoritativeWorldTick();
        long firstReviewTick = coarse.actors().capture().stream()
                .mapToLong(state -> state.nextReviewTick())
                .min()
                .orElseThrow();
        long ticksToAdvance = Math.max(1L, firstReviewTick - startTick);

        long wholeEightTickFrames = ticksToAdvance / 8L;
        for (long frame = 0L; frame < wholeEightTickFrames; frame++) {
            coarse.advanceFrame(0.1f);
        }
        for (long tick = wholeEightTickFrames * 8L; tick < ticksToAdvance; tick++) {
            coarse.advanceFrame(0.0125f);
        }
        for (long tick = 0L; tick < ticksToAdvance; tick++) {
            fine.advanceFrame(0.0125f);
        }

        assertEquals(coarse.captureState(), fine.captureState(),
                "Stage-21 actor scheduling must remain tied to authoritative ticks, not render frames");
        assertTrue(coarse.actors().capture().stream()
                        .mapToLong(state -> state.completedReviewCount())
                        .sum() > 0L,
                "ordinary campaign progression must execute the first due faction review");
        assertEquals(
                coarse.actors().capture().stream().map(state -> state.factionContentId()).toList(),
                fine.actors().capture().stream().map(state -> state.factionContentId()).toList());
    }
}
