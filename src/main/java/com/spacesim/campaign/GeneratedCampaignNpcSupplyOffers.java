package com.spacesim.campaign;

import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.world.FactionActorObservationSnapshot;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef;
import com.spacesim.world.Stage21HNpcMissionState;
import com.spacesim.world.Stage21HNpcMissionState.MissionObjective;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind;
import java.util.List;

/** Bounded dispatcher offers funded by real faction treasury for a real reviewed freight shortage. */
final class GeneratedCampaignNpcSupplyOffers {
    private GeneratedCampaignNpcSupplyOffers() { }

    /** Authored maximum size of a single personal emergency delivery, in physical kilograms. */
    static final long MAX_DELIVERY_KG = 1000;
    /** Fixed delivery fee, plus 20% of the opening commodity quote; ordinary sale is separate. */
    static final long PREMIUM_MILLI_CREDITS_PER_KG = 1000;

    /** Attempts one offer from this exact reviewed snapshot; no stock, equipment or money is issued. */
    static void offer(GeneratedCampaignCoordinator coordinator, String npcId, FactionActorObservationSnapshot observations) {
        var service = coordinator.npcMissionService();
        var npc = service.snapshot().npcs().stream().filter(n -> n.npcId().equals(npcId)).findFirst().orElseThrow();
        if (!npc.factionContentId().equals(observations.factionContentId())
                || service.snapshot().missions().stream().anyMatch(m -> m.issuerNpcId().equals(npcId) && m.active())) return;
        var runtime = coordinator.runtime(); var world = runtime.world();
        long tick = world.getAuthoritativeWorldTick();
        if (observations.observedAtTick() != tick) throw new IllegalArgumentException("Offers require the exact completed review tick");
        var archive = runtime.discoveryState().knowledgeFor(npc.factionContentId());
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        for (var observation : observations.currentObservations()) {
            if (observation.domain() != FactionActorObservationSnapshot.Domain.ECONOMIC
                    || observation.interestKind() != FactionActorObservationSnapshot.InterestKind.RESOURCE_DEFICIT) continue;
            var order = runtime.freight().findOrder(observation.targetId()).orElse(null);
            if (order == null || !order.stableFactionId().equals(npc.factionContentId())) continue;
            var ship = runtime.freight().findFreighter(order.fleetId()).orElseThrow();
            if (order.delayedDeliveryCount() == 0 && ship.phase() != FreightPhase.DESTROYED) continue;
            var station = runtime.industry().industrial().stations().stream().filter(s -> s.stationId().equals(order.destinationEndpointId())
                    && s.stableFactionId().equals(npc.factionContentId())
                    && GeneratedCampaignStationSalePolicy.priceMilliCredits(s.stationArchetypeId()) > 0).findFirst().orElse(null);
            if (station == null) continue;
            var ref = new StaticObjectRef(station.systemId(), StaticObjectKind.INFRASTRUCTURE, station.stationId());
            if (archive.knowledge(ref).filter(k -> k.knownLocation().isPresent()).isEmpty()) continue;
            var endpoint = runtime.infrastructure().endpoint(station.stationId());
            var definition = ontology.findCommodity(order.commodityId());
            if (definition == null || !endpoint.handlingCapability().supportedStorageClassIds().contains(definition.storageClassId())) continue;
            double missing = ship.cargoCapacityKg() - endpoint.storage().commodityMassKg(order.commodityId());
            double perTick = endpoint.handlingCapability().massRateKgPerSecond() * coordinator.session().fixedStepSeconds();
            long kilograms = (long) Math.floor(Math.min(MAX_DELIVERY_KG, Math.min(missing,
                    Math.min(perTick, endpoint.storage().remainingCapacityKg(definition.storageClassId())))));
            if (kilograms < 1) continue;
            String cause = npcId + ":shortage:" + order.orderId() + ":delays:" + order.delayedDeliveryCount()
                    + ":lost:" + (ship.phase() == FreightPhase.DESTROYED);
            if (service.snapshot().missions().stream().anyMatch(m -> m.issuerNpcId().equals(npcId)
                    && m.sourceKnowledgeFactIds().contains(cause))) continue;
            long premium = PREMIUM_MILLI_CREDITS_PER_KG + Stage228CampaignAuthority.pilotCommodityPrice(order.commodityId(), true) / 5L;
            long reward = Math.multiplyExact(kilograms, premium);
            var economy = world.findFactionEconomicState(npc.factionContentId()).orElseThrow();
            if (economy.treasuryMilliCredits() - economy.treasuryReserveFloorMilliCredits() < reward) continue;
            var objective = new MissionObjective(ObjectiveAuthority.FREIGHT, ObjectiveKind.PLAYER_SUPPLY_DELIVERY_KG_AT_LEAST,
                    station.stationId(), station.systemId().value(), kilograms, order.commodityId());
            try {
                com.spacesim.world.Stage21HMissionAuthority.requireIssuerAuthority(world, runtime.freight().capture(),
                        runtime.captureState().campaign().industrialState(), archive, coordinator.operations(), npc.factionContentId(), objective);
            } catch (IllegalStateException exception) {
                continue;
            }
            if (service.snapshot().npcs().stream().filter(n -> n.npcId().equals(npcId)).findFirst().orElseThrow().knowledge()
                    .stream().noneMatch(k -> k.factId().equals(cause)))
                service.receiveActorObservation(npcId, observations, observation, cause);
            long duration = (long) Math.ceil(86400d / coordinator.session().fixedStepSeconds());
            if (tick > Long.MAX_VALUE - duration) return;
            service.offerMission(world, runtime.freight().capture(), runtime.captureState().campaign().industrialState(), archive,
                    coordinator.operations(), npcId, Stage21HNpcMissionState.MissionTemplate.EMERGENCY_SUPPLY_DELIVERY,
                    objective, List.of(cause), tick + duration, reward);
            return;
        }
    }
}
