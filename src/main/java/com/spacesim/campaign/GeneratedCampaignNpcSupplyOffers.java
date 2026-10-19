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

/**
 * Bounded dispatcher offers funded by real faction treasury. A delayed or lost reviewed freight
 * delivery is offered first; otherwise any station the operator supplies by freight whose actual
 * stock of that commodity is below one shipload is offered as a paid delivery.
 */
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
        long tick = coordinator.runtime().world().getAuthoritativeWorldTick();
        if (observations.observedAtTick() != tick) throw new IllegalArgumentException("Offers require the exact completed review tick");
        // Emergencies first: a reviewed delay or loss of the operator's own delivery.
        for (var observation : observations.currentObservations()) {
            if (observation.interestKind() == FactionActorObservationSnapshot.InterestKind.RESOURCE_DEFICIT
                    && tryOffer(coordinator, npcId, observations, observation, true)) return;
        }
        // Otherwise an actual stock deficit on an established supply route (ordinary procurement).
        for (var observation : observations.currentObservations()) {
            if (observation.interestKind() == FactionActorObservationSnapshot.InterestKind.SUPPLY_DEPENDENCY
                    && tryOffer(coordinator, npcId, observations, observation, false)) return;
        }
    }

    private static boolean tryOffer(GeneratedCampaignCoordinator coordinator, String npcId,
            FactionActorObservationSnapshot observations, FactionActorObservationSnapshot.ActorObservation observation, boolean emergency) {
        if (observation.domain() != FactionActorObservationSnapshot.Domain.ECONOMIC) return false;
        var service = coordinator.npcMissionService();
        var npc = service.snapshot().npcs().stream().filter(n -> n.npcId().equals(npcId)).findFirst().orElseThrow();
        var runtime = coordinator.runtime(); var world = runtime.world();
        long tick = world.getAuthoritativeWorldTick();
        var archive = runtime.discoveryState().knowledgeFor(npc.factionContentId());
        var order = runtime.freight().findOrder(observation.targetId()).orElse(null);
        if (order == null || !order.stableFactionId().equals(npc.factionContentId())) return false;
        var ship = runtime.freight().findFreighter(order.fleetId()).orElseThrow();
        boolean delayedOrLost = order.delayedDeliveryCount() > 0 || ship.phase() == FreightPhase.DESTROYED;
        if (emergency != delayedOrLost) return false;
        var endpoint = runtime.infrastructure().endpoints().stream()
                .filter(e -> e.stationId().equals(order.destinationEndpointId())).findFirst().orElse(null);
        // A station sold to the pilot is no longer the seller's to supply.
        if (endpoint == null || runtime.personalStations().personallyOwned(endpoint.stationId())) return false;
        var ref = new StaticObjectRef(endpoint.systemId(), StaticObjectKind.INFRASTRUCTURE, endpoint.stationId());
        if (archive.knowledge(ref).filter(k -> k.knownLocation().isPresent()).isEmpty()) return false;
        var definition = Stage18ResourceOntologyLoader.loadDefault().findCommodity(order.commodityId());
        if (definition == null || !endpoint.handlingCapability().supportedStorageClassIds().contains(definition.storageClassId())) return false;
        double missing = ship.cargoCapacityKg() - endpoint.storage().commodityMassKg(order.commodityId());
        double perTick = endpoint.handlingCapability().massRateKgPerSecond() * coordinator.session().fixedStepSeconds();
        long kilograms = (long) Math.floor(Math.min(MAX_DELIVERY_KG, Math.min(missing,
                Math.min(perTick, endpoint.storage().remainingCapacityKg(definition.storageClassId())))));
        if (kilograms < 1) return false;
        String cause = emergency
                ? npcId + ":shortage:" + order.orderId() + ":delays:" + order.delayedDeliveryCount()
                        + ":lost:" + (ship.phase() == FreightPhase.DESTROYED)
                : npcId + ":deficit:" + endpoint.stationId() + ':' + order.commodityId() + ":review:" + tick;
        if (service.snapshot().missions().stream().anyMatch(m -> m.issuerNpcId().equals(npcId)
                && m.sourceKnowledgeFactIds().contains(cause))) return false;
        long premium = PREMIUM_MILLI_CREDITS_PER_KG + Stage228CampaignAuthority.pilotCommodityPrice(order.commodityId(), true) / 5L;
        long reward = Math.multiplyExact(kilograms, premium);
        var economy = world.findFactionEconomicState(npc.factionContentId()).orElseThrow();
        if (economy.treasuryMilliCredits() - economy.treasuryReserveFloorMilliCredits() < reward) return false;
        // Cheap legal-operator check before the full issuer authority, which captures whole-world state.
        if (!operatedBy(world, endpoint, npc.factionContentId())) return false;
        var objective = new MissionObjective(ObjectiveAuthority.FREIGHT, ObjectiveKind.PLAYER_SUPPLY_DELIVERY_KG_AT_LEAST,
                endpoint.stationId(), endpoint.systemId().value(), kilograms, order.commodityId());
        try {
            com.spacesim.world.Stage21HMissionAuthority.requireIssuerAuthority(world, runtime.freight().capture(),
                    runtime.captureCampaignState().industrialState(), archive, coordinator.operations(), npc.factionContentId(), objective);
        } catch (IllegalStateException exception) {
            return false;
        }
        if (npc.knowledge().stream().noneMatch(k -> k.factId().equals(cause)))
            service.receiveActorObservation(npcId, observations, observation, cause);
        long duration = (long) Math.ceil(86400d / coordinator.session().fixedStepSeconds());
        if (tick > Long.MAX_VALUE - duration) return true;
        offerMission(service, world, runtime, archive, coordinator, npcId, emergency, objective, cause, tick + duration, reward);
        return true;
    }

    private static boolean operatedBy(com.spacesim.world.WorldSimulation world,
            com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint endpoint, String faction) {
        var operator = world.findFactionRuntimeId(faction);
        if (operator.isEmpty()) return false;
        String market = Stage228CampaignAuthority.PILOT_MARKET_V2_IDENTITY_PREFIX + endpoint.stationId();
        for (var entity : world.findSession(endpoint.systemId()).orElseThrow().getEngine().getEntities()) {
            var identity = entity.getComponent(com.spacesim.components.IdentityComponent.class);
            if (identity == null || !identity.name.equals(market)) continue;
            var legal = entity.getComponent(com.spacesim.components.FactionComponent.class);
            return legal != null && legal.factionId == operator.orElseThrow();
        }
        return false;
    }

    private static void offerMission(com.spacesim.world.Stage21HNpcMissionService service, com.spacesim.world.WorldSimulation world,
            com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            com.spacesim.world.Stage20DiscoveryKnowledgeState archive, GeneratedCampaignCoordinator coordinator, String npcId,
            boolean emergency, MissionObjective objective, String cause, long expiresTick, long reward) {
        service.offerMission(world, runtime.freight().capture(), runtime.captureCampaignState().industrialState(), archive,
                coordinator.operations(), npcId, emergency ? Stage21HNpcMissionState.MissionTemplate.EMERGENCY_SUPPLY_DELIVERY
                        : Stage21HNpcMissionState.MissionTemplate.ORDINARY_MARKET_PROCUREMENT,
                objective, List.of(cause), expiresTick, reward);
    }
}
