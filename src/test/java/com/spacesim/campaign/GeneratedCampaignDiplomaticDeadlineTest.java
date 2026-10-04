package com.spacesim.campaign;

import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.world.DiplomaticLifecycleService;
import com.spacesim.world.DiplomaticLifecycleService.ProposalRequest;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.ProposalStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCampaignDiplomaticDeadlineTest {
    @Test
    void ordinaryCampaignExpiresRestoredProposalAtItsExactDeadline() {
        var campaign = withProposal(3L);
        assertEquals(ProposalStatus.OPEN, campaign.diplomacy().proposals().get(0).status());
        advanceTo(campaign, 2L);
        assertEquals(ProposalStatus.OPEN, campaign.diplomacy().proposals().get(0).status());
        advanceTo(campaign, 3L);
        var expired = campaign.diplomacy().proposals().get(0);
        assertEquals(ProposalStatus.EXPIRED, expired.status());
        assertEquals(3L, expired.updatedTick());
        assertEquals(3L, campaign.diplomacy().simulationTick());
    }

    @Test
    void deadlineLifecycleDoesNotRunFromPausedZeroOrFractionalFramesOrCapture() {
        var campaign = withProposal(3L);
        var before = campaign.captureState();
        assertEquals(before, GeneratedCampaignCoordinator.restore(before).captureState());
        campaign.advanceFrame(0f);
        assertEquals(before, campaign.captureState());
        campaign.advanceFrame(0.001f);
        assertEquals(ProposalStatus.OPEN, campaign.diplomacy().proposals().get(0).status());
        assertEquals(0L, campaign.diplomacy().simulationTick());
        campaign.setPaused(true);
        var paused = campaign.captureState();
        campaign.advanceFrame(100f);
        assertEquals(paused, campaign.captureState());
        campaign.setPaused(false);
        advanceTo(campaign, 3L);
        assertEquals(ProposalStatus.EXPIRED, campaign.diplomacy().proposals().get(0).status());
    }

    @Test
    void saveLoadBeforeDeadlineAndEightTimesSpeedContinueIdentically() {
        var campaign = withProposal(12L);
        advanceTo(campaign, 4L);
        var saved = campaign.captureState();
        var restored = GeneratedCampaignCoordinator.restore(
                Stage21IGeneratedWorldRuntimePersistenceCodec.decode(
                        Stage21IGeneratedWorldRuntimePersistenceCodec.encode(saved)));
        assertEquals(saved, restored.captureState());
        campaign.setTimeScale(8d);
        restored.setTimeScale(8d);
        campaign.advanceFrame(0.2f);
        restored.advanceFrame(0.2f);
        assertEquals(campaign.captureState(), restored.captureState());
        assertEquals(ProposalStatus.EXPIRED, restored.diplomacy().proposals().get(0).status());
        assertEquals(12L, restored.diplomacy().proposals().get(0).updatedTick());
        var expired = restored.captureState();
        assertEquals(expired, GeneratedCampaignCoordinator.restore(expired).captureState());
    }

    @Test
    void campaignWithoutOffersRetainsExactHistoricalDiplomacyCheckpoint() {
        var campaign = GeneratedCampaignCoordinator.create(1L);
        var original = campaign.diplomacy();
        advanceTo(campaign, 5L);
        assertEquals(original, campaign.diplomacy());
        var saved = campaign.captureState();
        assertEquals(saved, GeneratedCampaignCoordinator.restore(saved).captureState());
    }

    @Test
    void linkedOrdinaryTreatyExpiresWithProposalAndCannotRemainIndependentlyOpen() {
        var campaign = withProposal(3L, true, false);
        String treaty = campaign.diplomacy().proposals().get(0).linkedTreatyId();
        assertTrue(!treaty.isEmpty());
        assertEquals(com.spacesim.world.DiplomaticTreatyState.Status.PROPOSED,
                campaign.runtime().world().findDiplomaticTreaty(treaty).orElseThrow().status());
        advanceTo(campaign, 2L);
        var restored = GeneratedCampaignCoordinator.restore(
                Stage21IGeneratedWorldRuntimePersistenceCodec.decode(
                        Stage21IGeneratedWorldRuntimePersistenceCodec.encode(campaign.captureState())));
        advanceTo(campaign, 3L);
        advanceTo(restored, 3L);
        assertEquals(campaign.captureState(), restored.captureState());
        assertEquals(com.spacesim.world.DiplomaticTreatyState.Status.REJECTED,
                restored.runtime().world().findDiplomaticTreaty(treaty).orElseThrow().status());
        var expired = restored.diplomacy().proposals().get(0);
        advanceTo(restored, 5L);
        assertEquals(expired, restored.diplomacy().proposals().get(0));
    }

    @Test
    void alreadyOverdueLegacyOfferRemainsExactOnRestoreAndExpiresAtFirstResumedTick() {
        var source = GeneratedCampaignCoordinator.create(1L);
        var service = new DiplomaticLifecycleService(source.runtime().world(),
                new Stage19ConflictRuntime(source.warfare()), source.diplomacy());
        service.propose(new ProposalRequest("command.overdue-test", "faction.alpha", "faction.beta",
                ProposalKind.RECOGNITION, "test.overdue", List.of(), List.of(), 3L));
        advanceTo(source, 5L);
        var checkpoint = GeneratedCampaignAuthorityCheckpoint.capture(source.session(), source.actors(),
                source.strategicIntents(), service.snapshot(), source.warfare(), source.commands(),
                source.operations(), source.transitions(), source.recovery(), source.npcMissions());
        var restored = GeneratedCampaignCoordinator.restore(checkpoint);
        assertEquals(checkpoint, restored.captureState());
        assertEquals(ProposalStatus.OPEN, restored.diplomacy().proposals().get(0).status());
        advanceTo(restored, 6L);
        assertEquals(ProposalStatus.EXPIRED, restored.diplomacy().proposals().get(0).status());
        assertEquals(6L, restored.diplomacy().proposals().get(0).updatedTick());
    }

    @Test
    void acceptedProposalAndTreatyAreNotExpiredByResponseDeadline() {
        var campaign = withProposal(3L, true, true);
        var proposal = campaign.diplomacy().proposals().get(0);
        advanceTo(campaign, 5L);
        assertEquals(proposal, campaign.diplomacy().proposals().get(0));
        assertEquals(com.spacesim.world.DiplomaticTreatyState.Status.ACTIVE,
                campaign.runtime().world().findDiplomaticTreaty(
                        proposal.linkedTreatyId()).orElseThrow().status());
    }

    private static GeneratedCampaignCoordinator withProposal(long deadlineTick) {
        return withProposal(deadlineTick, false, false);
    }

    private static GeneratedCampaignCoordinator withProposal(
            long deadlineTick, boolean linkedTreaty, boolean accepted) {
        var source = GeneratedCampaignCoordinator.create(1L);
        var service = new DiplomaticLifecycleService(source.runtime().world(),
                new Stage19ConflictRuntime(source.warfare()), source.diplomacy());
        var proposal = service.propose(new ProposalRequest("command.deadline-test", "faction.alpha", "faction.beta",
                linkedTreaty ? ProposalKind.TRADE : ProposalKind.RECOGNITION,
                "test.deadline", List.of(), List.of(), deadlineTick));
        if (linkedTreaty) {
            service.materializeTreatyOffer(proposal.proposalId());
        }
        if (accepted) {
            service.accept(proposal.proposalId());
        }
        var checkpoint = GeneratedCampaignAuthorityCheckpoint.capture(source.session(), source.actors(),
                source.strategicIntents(), service.snapshot(), source.warfare(), source.commands(),
                source.operations(), source.transitions(), source.recovery(), source.npcMissions());
        return GeneratedCampaignCoordinator.restore(checkpoint);
    }

    private static void advanceTo(GeneratedCampaignCoordinator campaign, long tick) {
        int frames = 0;
        float step = campaign.session().fixedStepSeconds();
        while (campaign.runtime().world().getAuthoritativeWorldTick() < tick && frames++ < 200) {
            campaign.advanceFrame(step);
        }
        assertEquals(tick, campaign.runtime().world().getAuthoritativeWorldTick());
        assertTrue(frames < 200);
    }
}
