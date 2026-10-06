package com.spacesim.campaign;

import com.spacesim.components.WalletComponent;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.Stage21HNpcMissionService;
import com.spacesim.world.Stage21HNpcMissionState;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("slow")
class GeneratedCampaignPersonalSupplyContractTest {
    @Test void exactExternalDeliveryPaysHeldEscrowOnceWhileLocalResaleCannotClaimIt() throws Exception {
        var campaign = Stage228CampaignAuthority.create(1);
        campaign.submitIndependentPilotStart(campaign.previewIndependentPilotStart());
        var runtime = campaign.coordinator().runtime(); var world = runtime.world();
        var playerFleet = campaign.playerState().orElseThrow().activeFleetId();
        String seller = runtime.freight().findFreighter(playerFleet).orElseThrow().stableFactionId();
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var holdClasses = runtime.freight().cargoHoldSnapshot(playerFleet).capacityByStorageClassKg().keySet();
        var station = runtime.industry().industrial().stations().stream().filter(s -> s.stableFactionId().equals(seller)
                && GeneratedCampaignStationSalePolicy.priceMilliCredits(s.stationArchetypeId()) > 0
                && ontology.getCommodities().stream().anyMatch(c -> holdClasses.contains(c.storageClassId())
                        && runtime.infrastructure().endpoint(s.stationId()).storage().snapshot().capacityByStorageClassKg().containsKey(c.storageClassId())
                        && runtime.infrastructure().endpoint(s.stationId()).handlingCapability().supportedStorageClassIds().contains(c.storageClassId())))
                .findFirst().orElseThrow();
        var definition = ontology.getCommodities().stream().filter(c -> holdClasses.contains(c.storageClassId())
                && runtime.infrastructure().endpoint(station.stationId()).storage().snapshot().capacityByStorageClassKg().containsKey(c.storageClassId())
                && runtime.infrastructure().endpoint(station.stationId()).handlingCapability().supportedStorageClassIds().contains(c.storageClassId())).findFirst().orElseThrow();
        String commodity = definition.id(); String storageClass = definition.storageClassId();
        var fleet = world.findFleet(playerFleet).orElseThrow();
        // Explicit geometry and test shortage evidence isolate physical payout, not an ordinary journey or offer generation.
        if (!fleet.systemId().equals(station.systemId())) {
            world.beginFleetTransfer(fleet.id(), station.systemId()); world.completeFleetTransfer(fleet.id(), 0, 0);
            fleet = world.findFleet(fleet.id()).orElseThrow();
        }
        var local = runtime.arrival().materialization(fleet.systemId());
        if (local.physicalState(fleet.localEntityId()).isEmpty()) local.registerPhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(station.position()));
        local.updatePhysicalState(fleet.localEntityId(), LocalPhysicalKinematics.stationary(station.position()));
        campaign.coordinator().setPaused(false); campaign.advanceFrame(campaign.coordinator().session().fixedStepSeconds());
        campaign.coordinator().setPaused(true);
        campaign.submitPilotAction(campaign.previewPilotAction("DOCK", station.stationId(), "", 0));
        long tick = world.getAuthoritativeWorldTick();
        var fact = new Stage21HNpcMissionState.NpcKnowledgeFact("fixture.shortage", station.stationId(),
                Stage21HNpcMissionState.KnowledgeKind.ACTOR_OBSERVATION, "ECONOMIC.RESOURCE_DEFICIT", 8000, "fixture.shortage", tick, -1);
        var npc = new Stage21HNpcMissionState.NpcState("fixture.dispatcher", "Диспетчер", Stage21HNpcMissionState.NpcRole.TRADE_LOGISTICS,
                seller, station.systemId(), Stage21HNpcMissionState.NpcAvailability.AVAILABLE, List.of(fact));
        var service = new Stage21HNpcMissionService(new Stage21HNpcMissionState(1, tick, 1, List.of(npc), List.of(), List.of(), List.of()));
        var objective = new Stage21HNpcMissionState.MissionObjective(Stage21HNpcMissionState.ObjectiveAuthority.FREIGHT,
                Stage21HNpcMissionState.ObjectiveKind.PLAYER_SUPPLY_DELIVERY_KG_AT_LEAST, station.stationId(), station.systemId().value(), 1, commodity);
        var archive = runtime.discoveryState().knowledgeFor(seller);
        long treasury = world.findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits();
        var mission = service.offerMission(world, runtime.freight().capture(), runtime.captureState().campaign().industrialState(), archive,
                com.spacesim.world.StrategicOperationState.empty(), npc.npcId(), Stage21HNpcMissionState.MissionTemplate.EMERGENCY_SUPPLY_DELIVERY,
                objective, List.of(fact.factId()), tick + 100, 1000);
        service.acceptMission(mission.missionId(), tick);
        assertEquals(treasury - 1000, world.findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits());
        var coordinator = campaign.coordinator();
        var stage21 = GeneratedCampaignAuthorityCheckpoint.capture(coordinator.session(), coordinator.actors(), coordinator.strategicIntents(),
                coordinator.diplomacy(), coordinator.warfare(), coordinator.commands(), coordinator.operations(), coordinator.transitions(),
                coordinator.recovery(), service.snapshot());
        var base = campaign.captureState();
        var savedContract = new com.spacesim.persistence.Stage228GeneratedCampaignPersistentState(15, "m22.8.generated-campaign.v15",
                stage21, base.smallCraft(), base.hangars(), base.flightDeck(), base.operations(), base.playerState(), base.playerJournal(),
                base.moduleCustody(), base.repairQueue(), base.refitQueue(), base.moduleTransfers(), base.productTransfers(), base.yardConstruction());
        var encoded = com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.encode(savedContract);
        assertEquals(savedContract, com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.decode(encoded));
        try (var in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(encoded))) {
            var bytes = new java.io.ByteArrayOutputStream();
            try (var out = new java.io.DataOutputStream(bytes)) {
                out.writeInt(in.readInt()); in.readInt(); in.readInt(); in.readUTF();
                out.writeInt(14); out.writeInt(14); out.writeUTF("m22.8.generated-campaign.v14"); out.write(in.readAllBytes());
            }
            assertThrows(IllegalArgumentException.class, () -> com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec.decode(bytes.toByteArray()),
                    "Historical native v14 cannot claim a new physical supply obligation");
        }
        campaign.coordinator().setPaused(false); campaign.advanceFrame(campaign.coordinator().session().fixedStepSeconds());
        campaign.coordinator().setPaused(true);
        double seconds = world.getAuthoritativeWorldTick() * campaign.coordinator().session().fixedStepSeconds();
        var handling = runtime.infrastructure().endpoint(station.stationId()).handlingCapability();
        // Two explicit finite stocks distinguish circular resale from external cargo.
        var circular = new Stage18StationStorage(ontology, Stage18ManufacturingProductRegistry.loadDefault(), station.stationId(),
                Map.of(storageClass, 10d), Map.of(commodity, 1d), Map.of());
        assertTrue(runtime.freight().exchangeManualCommodity(playerFleet, circular, commodity, 1, true, seconds, handling, handling.openInterval(1)));
        var target = runtime.infrastructure().endpoint(station.stationId()).storage();
        var receipt = runtime.freight().deliverPersonalCommodity(playerFleet, target, commodity, 1, seconds, handling, handling.openInterval(1)).orElseThrow();
        var recipient = new WalletComponent(0);
        assertTrue(service.tryCompletePersonalSupplyDelivery(world, runtime.freight(), runtime.captureState().campaign().industrialState(), archive,
                campaign.playerState().orElseThrow(), mission.missionId(), receipt, recipient).isEmpty());
        assertEquals(0, recipient.getBalanceMilliCredits());
        assertTrue(runtime.freight().claimPersonalDelivery(receipt), "A rejected bonus must not consume the ordinary sale receipt");
        var external = new Stage18StationStorage(ontology, Stage18ManufacturingProductRegistry.loadDefault(), "fixture.external-source",
                Map.of(storageClass, 10d), Map.of(commodity, 1d), Map.of());
        assertTrue(runtime.freight().exchangeManualCommodity(playerFleet, external, commodity, 1, true, seconds, handling, handling.openInterval(1)));
        receipt = runtime.freight().deliverPersonalCommodity(playerFleet, target, commodity, 1, seconds, handling, handling.openInterval(1)).orElseThrow();
        service = new Stage21HNpcMissionService(service.snapshot());
        var completed = service.tryCompletePersonalSupplyDelivery(world, runtime.freight(), runtime.captureState().campaign().industrialState(), archive,
                campaign.playerState().orElseThrow(), mission.missionId(), receipt, recipient).orElseThrow();
        assertEquals(Stage21HNpcMissionState.MissionStatus.COMPLETED, completed.status());
        assertEquals(0, completed.escrowMilliCredits()); assertEquals(1000, recipient.getBalanceMilliCredits());
        assertEquals(treasury - 1000, world.findFactionEconomicState(seller).orElseThrow().treasuryMilliCredits());
        assertFalse(runtime.freight().claimPersonalDelivery(receipt));
        assertTrue(service.tryCompletePersonalSupplyDelivery(world, runtime.freight(), runtime.captureState().campaign().industrialState(), archive,
                campaign.playerState().orElseThrow(), mission.missionId(), receipt, recipient).isEmpty());
        assertEquals(1000, recipient.getBalanceMilliCredits());
    }
}
