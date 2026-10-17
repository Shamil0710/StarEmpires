package com.spacesim.campaign;

import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef;
import com.spacesim.world.Stage21HNpcMissionState.NpcAvailability;
import com.spacesim.world.Stage21HNpcMissionState.NpcRole;
import com.spacesim.world.Stage21HNpcMissionState.NpcState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** New-campaign civilian postings; never called by checkpoint restoration. */
final class GeneratedCampaignNpcPlacement {
    private GeneratedCampaignNpcPlacement() { }

    /** Refreshes existing dispatchers and considers funded offers from an actual faction review. */
    static void review(GeneratedCampaignCoordinator coordinator, String faction, long tick) {
        var recipients = coordinator.npcMissions().npcs().stream().filter(n ->
                n.npcId().startsWith("npc.stage23b.dispatcher:") && n.factionContentId().equals(faction)
                        && n.availability() == NpcAvailability.AVAILABLE).toList();
        if (recipients.isEmpty()) return;
        var snapshot = GeneratedCampaignFactionObservationPublisher.publish(coordinator.runtime().captureFreightState(), faction, tick);
        for (var npc : recipients) {
            for (var observation : snapshot.currentObservations())
                coordinator.npcMissionService().refreshActorObservation(npc.npcId(), snapshot, observation,
                        npc.npcId() + ":freight:" + observation.targetId() + ":review:" + tick);
            GeneratedCampaignNpcSupplyOffers.offer(coordinator, npc.npcId(), snapshot);
        }
    }

    /** Named new-game policy: dispatchers' operators archive the free-body resource sites of their posting systems. */
    static final String LOCAL_RESOURCE_SURVEY_VERSION = "new-game-local-resource-survey.v1";

    /** Installs a local dispatcher at each existing archived civilian station on new-game authoring only. */
    static void install(GeneratedCampaignCoordinator coordinator) {
        install(coordinator, GeneratedCampaignInitialStationLegalCapital.Plan.baseline());
    }

    static void install(GeneratedCampaignCoordinator coordinator, GeneratedCampaignInitialStationLegalCapital.Plan capital) {
        var snapshot = coordinator.runtime().captureState();
        var roster = new ArrayList<NpcState>();
        var factions = new HashSet<String>();
        var postings = new java.util.HashMap<String, StaticObjectRef>();
        var service = coordinator.npcMissionService();
        var runtime = coordinator.runtime();
        for (var station : runtime.infrastructure().endpoints().stream()
                .sorted(java.util.Comparator.comparing((com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint e) ->
                        capital.assignments().containsKey(e.stationId()))
                        .thenComparing(java.util.Comparator.comparing(com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint::generatedIndustrial).reversed())
                        .thenComparing(com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint::stationId)).toList()) {
            var owner = GeneratedCampaignInitialStationLegalCapital.owner(runtime, station, capital);
            if (owner.isEmpty()) continue;
            String faction = owner.orElseThrow();
            if (GeneratedCampaignStationSalePolicy.priceMilliCredits(station.stationArchetypeId()) == 0
                    || coordinator.runtime().world().findFactionEconomicState(faction).isEmpty()) continue;
            var ref = new StaticObjectRef(station.systemId(), StaticObjectKind.INFRASTRUCTURE, station.stationId());
            var archive = coordinator.runtime().discoveryState().knowledgeFor(faction);
            if (archive.knowledge(ref).filter(k -> k.knownLocation().isPresent()
                    && k.classificationId().equals(java.util.Optional.of(station.stationArchetypeId()))).isEmpty()) continue;
            // Preserve the primary posting's established identity; additional local desks have exact station IDs.
            String npcId = "npc.stage23b.dispatcher:" + faction + (factions.add(faction) ? "" : ':' + station.stationId());
            postings.put(npcId, ref);
            roster.add(new NpcState(npcId, "Диспетчер перевозок",
                    NpcRole.TRADE_LOGISTICS, faction, station.systemId(), NpcAvailability.AVAILABLE, List.of()));
        }
        service.installNpcRoster(roster);
        var postingSystems = new java.util.TreeMap<String, java.util.Set<com.spacesim.world.StarSystemId>>();
        for (var npc : roster)
            postingSystems.computeIfAbsent(npc.factionContentId(), f -> new java.util.HashSet<>()).add(npc.locationSystemId());
        runtime.authorInitialLocalResourceSurveys(postingSystems, LOCAL_RESOURCE_SURVEY_VERSION);
        for (var npc : roster) {
            var archive = coordinator.runtime().discoveryState().knowledgeFor(npc.factionContentId());
            service.receiveDiscovery(npc.npcId(), archive, postings.get(npc.npcId()), 0,
                    npc.npcId() + ":posting");
            var observations = GeneratedCampaignFactionObservationPublisher.publish(snapshot, npc.factionContentId(), 0);
            for (var observation : observations.currentObservations())
                service.receiveActorObservation(npc.npcId(), observations, observation,
                        npc.npcId() + ":freight:" + observation.targetId());
        }
    }
}
