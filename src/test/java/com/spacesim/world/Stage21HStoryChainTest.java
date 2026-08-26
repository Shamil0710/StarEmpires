package com.spacesim.world;

import com.spacesim.world.Stage21HNpcMissionState.KnowledgeKind;
import com.spacesim.world.Stage21HNpcMissionState.MissionContract;
import com.spacesim.world.Stage21HNpcMissionState.MissionObjective;
import com.spacesim.world.Stage21HNpcMissionState.MissionStatus;
import com.spacesim.world.Stage21HNpcMissionState.MissionTemplate;
import com.spacesim.world.Stage21HNpcMissionState.NpcAvailability;
import com.spacesim.world.Stage21HNpcMissionState.NpcKnowledgeFact;
import com.spacesim.world.Stage21HNpcMissionState.NpcRole;
import com.spacesim.world.Stage21HNpcMissionState.NpcState;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind;
import com.spacesim.world.Stage21HNpcMissionState.StoryChainState;
import com.spacesim.world.Stage21HNpcMissionState.StoryChainStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage21HStoryChainTest {

    @Test
    void successfulStepsRemainBoundedAndFinalSuccessCompletesChain() {
        Stage21HNpcMissionService service = service(
                List.of(completed(1), completed(2), completed(3)));
        service.installStoryChain(new StoryChainState(
                "story.test", 0, 3, StoryChainStatus.AVAILABLE, List.of()));

        service.linkMissionToStoryChain("story.test", "mission.stage21h.1");
        assertEquals(StoryChainStatus.ACTIVE, service.reconcileStoryChain("story.test").status());
        service.linkMissionToStoryChain("story.test", "mission.stage21h.2");
        assertEquals(StoryChainStatus.ACTIVE, service.reconcileStoryChain("story.test").status());
        service.linkMissionToStoryChain("story.test", "mission.stage21h.3");

        StoryChainState completed = service.reconcileStoryChain("story.test");
        assertEquals(StoryChainStatus.COMPLETED, completed.status());
        assertEquals(List.of(
                "mission.stage21h.1", "mission.stage21h.2", "mission.stage21h.3"),
                completed.missionIds());
        assertThrows(IllegalStateException.class, () ->
                service.linkMissionToStoryChain("story.test", "mission.stage21h.1"));
    }

    @Test
    void rejectedWorldStepClosesChainAndCannotIssueLaterAuthoredStep() {
        Stage21HNpcMissionService service = service(List.of(completed(1), rejected(2), completed(3)));
        service.installStoryChain(new StoryChainState(
                "story.test", 0, 3, StoryChainStatus.AVAILABLE, List.of()));

        service.linkMissionToStoryChain("story.test", "mission.stage21h.1");
        service.reconcileStoryChain("story.test");
        service.linkMissionToStoryChain("story.test", "mission.stage21h.2");
        StoryChainState closed = service.reconcileStoryChain("story.test");

        assertEquals(StoryChainStatus.CLOSED_BY_WORLD, closed.status());
        assertThrows(IllegalStateException.class, () ->
                service.linkMissionToStoryChain("story.test", "mission.stage21h.3"));
    }

    private static Stage21HNpcMissionService service(List<MissionContract> missions) {
        NpcState npc = npc();
        return new Stage21HNpcMissionService(new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION,
                10L,
                4L,
                List.of(npc),
                missions,
                List.of(),
                List.of()));
    }

    private static NpcState npc() {
        return new NpcState(
                "npc.test", "npc.test.name", NpcRole.TRADE_LOGISTICS,
                "faction.test", new StarSystemId(1L), NpcAvailability.AVAILABLE,
                List.of(new NpcKnowledgeFact(
                        "fact.test", "order.test", KnowledgeKind.ACTOR_OBSERVATION,
                        "ECONOMIC.RESOURCE_DEFICIT", 5000, "report.test", 1L, -1L)));
    }

    private static MissionContract completed(int sequence) {
        return terminal(sequence, MissionStatus.COMPLETED, "freight.delivery-satisfied");
    }

    private static MissionContract rejected(int sequence) {
        return terminal(sequence, MissionStatus.REJECTED, "rejected");
    }

    private static MissionContract terminal(int sequence, MissionStatus status, String outcome) {
        return new MissionContract(
                "mission.stage21h." + sequence,
                MissionTemplate.EMERGENCY_SUPPLY_DELIVERY,
                1,
                npc().npcId(),
                npc().factionContentId(),
                List.of("fact.test"),
                new MissionObjective(
                        ObjectiveAuthority.FREIGHT,
                        ObjectiveKind.FREIGHT_ORDER_DELIVERED_KG_AT_LEAST,
                        "order.test", 0L, 10L, ""),
                2L,
                20L,
                status,
                5L + sequence,
                100L,
                0L,
                outcome,
                List.of());
    }
}
