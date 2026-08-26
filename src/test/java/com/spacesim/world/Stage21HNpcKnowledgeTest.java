package com.spacesim.world;

import com.spacesim.world.FactionActorObservationSnapshot.ActorObservation;
import com.spacesim.world.FactionActorObservationSnapshot.Domain;
import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryEvidence;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoverySource;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledge;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticKnowledge;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef;
import com.spacesim.world.Stage21HNpcMissionState.NpcAvailability;
import com.spacesim.world.Stage21HNpcMissionState.NpcRole;
import com.spacesim.world.Stage21HNpcMissionState.NpcState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21HNpcKnowledgeTest {

    @Test
    void npcReceivesOnlyCurrentAffiliatedActorObservationAndDialogueDoesNotRequeryWorld() {
        Stage21HNpcMissionService service = service("faction.alpha");
        ActorObservation observation = new ActorObservation(
                Domain.ECONOMIC,
                InterestKind.RESOURCE_DEFICIT,
                "commodity.test",
                7000,
                new ObservationEvidence(
                        ObservationChannel.ECONOMIC_LEDGER,
                        "ledger.shortage.test",
                        5L,
                        8L));
        FactionActorObservationSnapshot snapshot = new FactionActorObservationSnapshot(
                "faction.alpha", 5L, List.of(observation), List.of(), List.of(), List.of());

        service.receiveActorObservation("npc.test", snapshot, observation, "fact.shortage");
        assertEquals(List.of("fact.shortage"),
                service.dialogueFacts("npc.test", 5L).stream().map(value -> value.factId()).toList());
        assertTrue(service.dialogueFacts("npc.test", 9L).isEmpty());

        FactionActorObservationSnapshot foreign = new FactionActorObservationSnapshot(
                "faction.beta", 5L, List.of(observation), List.of(), List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () ->
                service.receiveActorObservation("npc.test", foreign, observation, "fact.foreign"));
    }

    @Test
    void npcCannotReceiveUnknownOrForeignDiscoveryFact() {
        Stage21HNpcMissionService service = service("faction.alpha");
        StaticObjectRef object = new StaticObjectRef(
                new StarSystemId(1L), StaticObjectKind.SPECIAL_LOCATION, "special.test");
        Stage20DiscoveryKnowledgeState unknown = new Stage20DiscoveryKnowledgeState("faction.alpha", List.of());
        assertThrows(IllegalArgumentException.class, () ->
                service.receiveDiscovery("npc.test", unknown, object, 1L, "fact.discovery"));

        DiscoveryEvidence evidence = new DiscoveryEvidence(
                DiscoverySource.PHYSICAL_VISIT_OR_SURVEY,
                "survey.test",
                1d,
                OptionalDouble.empty());
        StaticKnowledge known = new StaticKnowledge(
                object,
                DiscoveryState.KNOWN_STATIC_LOCATION,
                Optional.of("special.derelict"),
                Optional.of(LocalPhysicalPosition.origin()),
                ResourceKnowledge.none(),
                List.of(evidence), 1d, 1d);
        Stage20DiscoveryKnowledgeState foreign = new Stage20DiscoveryKnowledgeState(
                "faction.beta", List.of(known));
        assertThrows(IllegalArgumentException.class, () ->
                service.receiveDiscovery("npc.test", foreign, object, 1L, "fact.discovery"));
    }

    private static Stage21HNpcMissionService service(String faction) {
        NpcState npc = new NpcState(
                "npc.test", "npc.test.name", NpcRole.OFFICIAL,
                faction, new StarSystemId(1L), NpcAvailability.AVAILABLE, List.of());
        return new Stage21HNpcMissionService(new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION,
                0L, 1L, List.of(npc), List.of(), List.of(), List.of()));
    }
}
