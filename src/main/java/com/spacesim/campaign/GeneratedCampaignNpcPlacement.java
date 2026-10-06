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
        var snapshot = GeneratedCampaignFactionObservationPublisher.publish(coordinator.runtime().captureState(), faction, tick);
        for (var npc : recipients) {
            for (var observation : snapshot.currentObservations())
                coordinator.npcMissionService().refreshActorObservation(npc.npcId(), snapshot, observation,
                        npc.npcId() + ":freight:" + observation.targetId() + ":review:" + tick);
            GeneratedCampaignNpcSupplyOffers.offer(coordinator, npc.npcId(), snapshot);
        }
    }

    /** Installs one dispatcher per actual faction with an archived civilian station. */
    static void install(GeneratedCampaignCoordinator coordinator) {
        var snapshot = coordinator.runtime().captureState();
        var roster = new ArrayList<NpcState>();
        var factions = new HashSet<String>();
        var postings = new java.util.HashMap<String, StaticObjectRef>();
        var service = coordinator.npcMissionService();
        for (var station : coordinator.runtime().industry().industrial().stations().stream()
                .sorted(java.util.Comparator.comparing(s -> s.stationId())).toList()) {
            String faction = station.stableFactionId();
            if (factions.contains(faction)
                    || GeneratedCampaignStationSalePolicy.priceMilliCredits(station.stationArchetypeId()) == 0
                    || coordinator.runtime().world().findFactionEconomicState(faction).isEmpty()) continue;
            var ref = new StaticObjectRef(station.systemId(), StaticObjectKind.INFRASTRUCTURE, station.stationId());
            var archive = coordinator.runtime().discoveryState().knowledgeFor(faction);
            if (archive.knowledge(ref).filter(k -> k.knownLocation().isPresent()
                    && k.classificationId().equals(java.util.Optional.of(station.stationArchetypeId()))).isEmpty()) continue;
            factions.add(faction);
            postings.put(faction, ref);
            roster.add(new NpcState("npc.stage23b.dispatcher:" + faction, "Диспетчер перевозок",
                    NpcRole.TRADE_LOGISTICS, faction, station.systemId(), NpcAvailability.AVAILABLE, List.of()));
        }
        service.installNpcRoster(roster);
        for (var npc : roster) {
            var archive = coordinator.runtime().discoveryState().knowledgeFor(npc.factionContentId());
            service.receiveDiscovery(npc.npcId(), archive, postings.get(npc.factionContentId()), 0,
                    npc.npcId() + ":posting");
            var observations = GeneratedCampaignFactionObservationPublisher.publish(snapshot, npc.factionContentId(), 0);
            for (var observation : observations.currentObservations())
                service.receiveActorObservation(npc.npcId(), observations, observation,
                        npc.npcId() + ":freight:" + observation.targetId());
        }
    }
}
