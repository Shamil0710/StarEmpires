package com.spacesim.campaign;

import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.world.DestructionPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("slow")
class GeneratedCampaignNpcSupplyOffersTest {
    @Test void physicalFreightLossCreatesOneActuallyFundedOfferAndCannotBeReissuedAfterRefundOrRestore() {
        var campaign = Stage228CampaignAuthority.create(1);
        campaign.submitIndependentPilotStart(campaign.previewIndependentPilotStart());
        var coordinator = campaign.coordinator(); var runtime = coordinator.runtime();
        String faction = runtime.freight().findFreighter(campaign.playerState().orElseThrow().activeFleetId()).orElseThrow().stableFactionId();
        var npc = coordinator.npcMissions().npcs().stream().filter(n -> n.factionContentId().equals(faction)).findFirst().orElseThrow();
        var initial = GeneratedCampaignFactionObservationPublisher.publish(runtime.captureState(), faction, 0);
        GeneratedCampaignNpcSupplyOffers.offer(coordinator, npc.npcId(), initial);
        assertTrue(coordinator.npcMissions().missions().isEmpty(), "Normal route exposure is not an invented shortage");
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var order = runtime.freight().capture().orders().stream().filter(o -> o.stableFactionId().equals(faction)
                && runtime.world().findFleet(o.fleetId()).isPresent()
                && runtime.industry().industrial().stations().stream().anyMatch(s -> s.stationId().equals(o.destinationEndpointId())
                        && s.stableFactionId().equals(faction) && GeneratedCampaignStationSalePolicy.priceMilliCredits(s.stationArchetypeId()) > 0)
                && runtime.infrastructure().endpoint(o.destinationEndpointId()).storage().commodityMassKg(o.commodityId())
                        < runtime.freight().findFreighter(o.fleetId()).orElseThrow().cargoCapacityKg()
                && runtime.infrastructure().endpoint(o.destinationEndpointId()).storage().remainingCapacityKg(ontology.findCommodity(o.commodityId()).storageClassId()) >= 1
                && runtime.infrastructure().endpoint(o.destinationEndpointId()).handlingCapability().supportedStorageClassIds().contains(ontology.findCommodity(o.commodityId()).storageClassId()))
                .findFirst().orElseThrow();
        // A real destruction command triggers the loss. No shortage fact, warehouse stock or treasury is injected.
        runtime.destroyLocalFreighter(order.fleetId(), DestructionPolicy.destroyAll());
        long treasury = runtime.world().findFactionEconomicState(faction).orElseThrow().treasuryMilliCredits();
        var freight = runtime.freight().capture();
        var warehouses = runtime.captureState().campaign().industrialState();
        var observations = GeneratedCampaignFactionObservationPublisher.publish(runtime.captureState(), faction, 0);
        GeneratedCampaignNpcSupplyOffers.offer(coordinator, npc.npcId(), observations);
        assertEquals(1, coordinator.npcMissions().missions().size());
        var mission = coordinator.npcMissions().missions().get(0);
        assertEquals(order.destinationEndpointId(), mission.objective().subjectId());
        assertEquals(order.commodityId(), mission.objective().requiredState());
        assertTrue(mission.objective().threshold() > 0 && mission.objective().threshold() <= 1000);
        assertEquals(mission.objective().threshold() * (GeneratedCampaignNpcSupplyOffers.PREMIUM_MILLI_CREDITS_PER_KG
                + Stage228CampaignAuthority.pilotCommodityPrice(order.commodityId(), true) / 5L), mission.escrowMilliCredits());
        assertEquals(treasury - mission.escrowMilliCredits(), runtime.world().findFactionEconomicState(faction).orElseThrow().treasuryMilliCredits());
        assertEquals(freight, runtime.freight().capture()); assertEquals(warehouses, runtime.captureState().campaign().industrialState());
        assertEquals(List.of(npc.npcId() + ":shortage:" + order.orderId() + ":delays:0:lost:true"), mission.sourceKnowledgeFactIds());
        var offered = campaign.captureState();
        assertEquals(offered, Stage228CampaignAuthority.restore(offered).captureState());
        verifySupplyPortionAndCircularTrade(offered, mission, npc.npcId());
        GeneratedCampaignNpcSupplyOffers.offer(coordinator, npc.npcId(), observations);
        assertEquals(offered, campaign.captureState());
        coordinator.npcMissionService().rejectMission(runtime.world(), mission.missionId());
        assertEquals(treasury, runtime.world().findFactionEconomicState(faction).orElseThrow().treasuryMilliCredits());
        var refunded = campaign.captureState();
        GeneratedCampaignNpcSupplyOffers.offer(coordinator, npc.npcId(), observations);
        assertEquals(refunded, campaign.captureState());
        var restored = Stage228CampaignAuthority.restore(refunded);
        GeneratedCampaignNpcSupplyOffers.offer(restored.coordinator(), npc.npcId(),
                GeneratedCampaignFactionObservationPublisher.publish(restored.coordinator().runtime().captureState(), faction, 0));
        assertEquals(refunded, restored.captureState());
    }

    private static void verifySupplyPortionAndCircularTrade(com.spacesim.persistence.Stage228GeneratedCampaignPersistentState offered,
            com.spacesim.world.Stage21HNpcMissionState.MissionContract proposal, String npcId) {
        var campaign = Stage228CampaignAuthority.restore(offered); var runtime = campaign.coordinator().runtime();
        var npc = campaign.coordinator().npcMissions().npcs().stream().filter(n -> n.npcId().equals(npcId)).findFirst().orElseThrow();
        String posting = npc.knowledge().stream().filter(k -> k.factId().equals(npcId + ":posting")).findFirst().orElseThrow().subjectId();
        dockWithExplicitTravelGeometry(campaign, posting);
        assertTrue(campaign.canContactNpc(npcId));
        var baseline = campaign.captureState();
        var accept = campaign.previewMissionCommand(com.spacesim.world.Stage21HNpcMissionService.PlayerCommand.ACCEPT, proposal.missionId(), 1);
        assertTrue(accept.allowed()); assertEquals(baseline, campaign.captureState(), "Preview cannot move escrow");
        var mission = campaign.submitMissionCommand(accept);
        assertEquals(1, mission.objective().threshold());
        assertEquals(accept.proposedRewardMilliCredits(), mission.rewardMilliCredits());
        assertNotEquals(proposal.missionId(), mission.missionId());
        var parent = campaign.coordinator().npcMissions().missions().stream().filter(m -> m.missionId().equals(proposal.missionId())).findFirst().orElseThrow();
        assertEquals(0, parent.escrowMilliCredits());
        assertEquals(com.spacesim.world.Stage21HNpcMissionState.MissionStatus.REJECTED, parent.status());
        assertThrows(IllegalStateException.class, () -> campaign.submitMissionCommand(accept));
        dockWithExplicitTravelGeometry(campaign, mission.objective().subjectId());
        String commodity = mission.objective().requiredState();
        assertFalse(campaign.previewPilotAction("SELL", mission.objective().subjectId(), commodity, 1).allowed(), "Empty cargo cannot fulfill a funded order");
        long wallet = campaign.playerState().orElseThrow().walletMilliCredits();
        campaign.submitPilotAction(campaign.previewPilotAction("BUY", mission.objective().subjectId(), commodity, 1));
        assertTrue(runtime.freight().capture().cargoLots().stream().anyMatch(l -> l.commodityId().equals(commodity)
                && l.sourceEndpointId().equals(mission.objective().subjectId())));
        advanceOneTick(campaign);
        long journal = campaign.playerJournal().nextSequence();
        long beforeSale = campaign.playerState().orElseThrow().walletMilliCredits();
        campaign.submitPilotAction(campaign.previewPilotAction("SELL", mission.objective().subjectId(), commodity, 1));
        var retained = campaign.coordinator().npcMissions().missions().stream().filter(m -> m.missionId().equals(mission.missionId())).findFirst().orElseThrow();
        assertEquals(com.spacesim.world.Stage21HNpcMissionState.MissionStatus.ACCEPTED, retained.status());
        assertEquals(mission.rewardMilliCredits(), retained.escrowMilliCredits());
        assertTrue(campaign.playerState().orElseThrow().walletMilliCredits() < wallet, "Circular purchase/resale incurs the real spread and no bonus");
        assertEquals(campaign.playerState().orElseThrow().walletMilliCredits() - beforeSale,
                campaign.playerJournal().entries().stream().filter(e -> e.sequence() >= journal).mapToLong(e -> e.walletDeltaMilliCredits()).sum());
        var saved = campaign.captureState(); assertEquals(saved, Stage228CampaignAuthority.restore(saved).captureState());
    }
    private static void advanceOneTick(Stage228CampaignAuthority campaign) {
        campaign.coordinator().setPaused(false); campaign.advanceFrame(campaign.coordinator().session().fixedStepSeconds());
        campaign.coordinator().setPaused(true);
    }

    private static void dockWithExplicitTravelGeometry(Stage228CampaignAuthority campaign, String stationId) {
        if (campaign.playerState().orElseThrow().docked()) campaign.submitPilotAction(campaign.previewPilotAction("UNDOCK", "", "", 0));
        var runtime = campaign.coordinator().runtime(); var endpoint = runtime.infrastructure().endpoint(stationId);
        var fleet = runtime.world().findFleet(campaign.playerState().orElseThrow().activeFleetId()).orElseThrow();
        if (!fleet.systemId().equals(endpoint.systemId())) {
            runtime.world().beginFleetTransfer(fleet.id(), endpoint.systemId()); runtime.world().completeFleetTransfer(fleet.id(), 0, 0);
            fleet = runtime.world().findFleet(fleet.id()).orElseThrow();
        }
        var local = runtime.arrival().materialization(fleet.systemId());
        if (local.physicalState(fleet.localEntityId()).isEmpty()) local.registerPhysicalState(fleet.localEntityId(), com.spacesim.world.LocalPhysicalKinematics.stationary(endpoint.position()));
        local.updatePhysicalState(fleet.localEntityId(), com.spacesim.world.LocalPhysicalKinematics.stationary(endpoint.position()));
        advanceOneTick(campaign);
        campaign.submitPilotAction(campaign.previewPilotAction("DOCK", stationId, "", 0));
    }
}
