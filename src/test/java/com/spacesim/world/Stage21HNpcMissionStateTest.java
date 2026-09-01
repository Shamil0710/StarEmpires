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

class Stage21HNpcMissionStateTest {

    @Test
    void objectiveKindsExposeTheirExistingOrdinaryAuthorityWithoutASecondMatrix() {
        assertEquals(ObjectiveAuthority.FREIGHT,
                Stage21HNpcMissionState.expectedAuthority(ObjectiveKind.FREIGHT_ORDER_DELIVERED_KG_AT_LEAST));
        assertEquals(ObjectiveAuthority.FLEET,
                Stage21HNpcMissionState.expectedAuthority(ObjectiveKind.FLEET_PRESENT_IN_SYSTEM));
        assertEquals(ObjectiveAuthority.FLEET,
                Stage21HNpcMissionState.expectedAuthority(ObjectiveKind.FLEET_ABSENT));
        assertEquals(ObjectiveAuthority.FLEET,
                Stage21HNpcMissionState.expectedAuthority(ObjectiveKind.ESCORT_FLEETS_PRESENT_IN_SYSTEM));
        assertEquals(ObjectiveAuthority.FLEET,
                Stage21HNpcMissionState.expectedAuthority(ObjectiveKind.FLEET_REACTION_MASS_KG_AT_LEAST));
        assertEquals(ObjectiveAuthority.DISCOVERY,
                Stage21HNpcMissionState.expectedAuthority(ObjectiveKind.DISCOVERY_AT_LEAST));
        assertEquals(ObjectiveAuthority.INDUSTRY,
                Stage21HNpcMissionState.expectedAuthority(
                        ObjectiveKind.DERELICT_DISCOVERED_AND_SALVAGED_KG_AT_LEAST));
        assertEquals(ObjectiveAuthority.CONSTRUCTION,
                Stage21HNpcMissionState.expectedAuthority(ObjectiveKind.CONSTRUCTION_DELIVERED_UNITS_AT_LEAST));
        assertEquals(ObjectiveAuthority.CONSTRUCTION,
                Stage21HNpcMissionState.expectedAuthority(ObjectiveKind.CONSTRUCTION_COMPLETED));
        assertEquals(ObjectiveAuthority.DIPLOMACY,
                Stage21HNpcMissionState.expectedAuthority(ObjectiveKind.MARKET_ACCESS_ALLOWED));
        assertEquals(ObjectiveAuthority.OPERATION,
                Stage21HNpcMissionState.expectedAuthority(ObjectiveKind.OPERATION_STATUS));
        assertEquals(ObjectiveAuthority.ECONOMY,
                Stage21HNpcMissionState.expectedAuthority(ObjectiveKind.FACTION_TREASURY_AT_LEAST));
    }

    @Test
    void canonicalStateRetainsBoundedNpcMissionAndAuthoredChainIdentity() {
        NpcState npc = npc();
        MissionContract first = mission("mission.stage21h.1", MissionStatus.COMPLETED, 0L, "freight.delivery-satisfied");
        MissionContract second = mission("mission.stage21h.2", MissionStatus.OFFERED, 100L, "");
        StoryChainState chain = new StoryChainState(
                "story.test", 2, 3, StoryChainStatus.ACTIVE,
                List.of(first.missionId(), second.missionId()));

        Stage21HNpcMissionState state = new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION,
                10L,
                3L,
                List.of(npc),
                List.of(second, first),
                List.of(),
                List.of(chain));

        assertEquals(List.of(first, second), state.missions());
        assertEquals(List.of(first.missionId(), second.missionId()), state.storyChains().get(0).missionIds());
        assertEquals(List.of(npc.knowledge().get(0)), npc.currentKnowledge(10L));
    }

    @Test
    void templateCannotMasqueradeAsUnrelatedAuthorityPredicate() {
        MissionObjective fleetArrival = new MissionObjective(
                ObjectiveAuthority.FLEET,
                ObjectiveKind.FLEET_PRESENT_IN_SYSTEM,
                "1",
                2L,
                0L,
                "");

        assertThrows(IllegalArgumentException.class, () -> new MissionContract(
                "mission.stage21h.1",
                MissionTemplate.EMERGENCY_SUPPLY_DELIVERY,
                1,
                npc().npcId(),
                npc().factionContentId(),
                List.of("fact.test"),
                fleetArrival,
                5L,
                20L,
                MissionStatus.OFFERED,
                5L,
                100L,
                100L,
                "",
                List.of()));
    }

    @Test
    void stateRejectsFutureKnowledgeStaleCreationFactsAndAllocatorReuse() {
        NpcKnowledgeFact future = new NpcKnowledgeFact(
                "fact.future", "order.test", KnowledgeKind.ACTOR_OBSERVATION,
                "ECONOMIC.RESOURCE_DEFICIT", 5000, "report.future", 11L, -1L);
        NpcState futureNpc = new NpcState(
                "npc.test", "npc.test.name", NpcRole.TRADE_LOGISTICS,
                "faction.test", new StarSystemId(1L), NpcAvailability.AVAILABLE, List.of(future));
        assertThrows(IllegalArgumentException.class, () -> new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION, 10L, 1L,
                List.of(futureNpc), List.of(), List.of(), List.of()));

        NpcKnowledgeFact stale = new NpcKnowledgeFact(
                "fact.test", "order.test", KnowledgeKind.ACTOR_OBSERVATION,
                "ECONOMIC.RESOURCE_DEFICIT", 5000, "report.test", 2L, 4L);
        NpcState staleNpc = new NpcState(
                "npc.test", "npc.test.name", NpcRole.TRADE_LOGISTICS,
                "faction.test", new StarSystemId(1L), NpcAvailability.AVAILABLE, List.of(stale));
        MissionContract mission = mission("mission.stage21h.1", MissionStatus.OFFERED, 100L, "");
        assertThrows(IllegalArgumentException.class, () -> new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION, 10L, 2L,
                List.of(staleNpc), List.of(mission), List.of(), List.of()));

        assertThrows(IllegalArgumentException.class, () -> new Stage21HNpcMissionState(
                Stage21HNpcMissionState.CURRENT_VERSION, 10L, 1L,
                List.of(npc()), List.of(mission), List.of(), List.of()));
    }

    @Test
    void terminalContractRequiresZeroEscrowOutcomeAndNoWakeups() {
        assertThrows(IllegalArgumentException.class, () -> new MissionContract(
                "mission.stage21h.1",
                MissionTemplate.EMERGENCY_SUPPLY_DELIVERY,
                1,
                npc().npcId(),
                npc().factionContentId(),
                List.of("fact.test"),
                freightObjective(),
                5L,
                20L,
                MissionStatus.REJECTED,
                6L,
                100L,
                100L,
                "rejected",
                List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MissionContract(
                "mission.stage21h.1",
                MissionTemplate.EMERGENCY_SUPPLY_DELIVERY,
                1,
                npc().npcId(),
                npc().factionContentId(),
                List.of("fact.test"),
                freightObjective(),
                5L,
                20L,
                MissionStatus.REJECTED,
                6L,
                100L,
                0L,
                "",
                List.of()));
    }

    private static NpcState npc() {
        NpcKnowledgeFact fact = new NpcKnowledgeFact(
                "fact.test", "order.test", KnowledgeKind.ACTOR_OBSERVATION,
                "ECONOMIC.RESOURCE_DEFICIT", 5000, "report.test", 2L, -1L);
        return new NpcState(
                "npc.test", "npc.test.name", NpcRole.TRADE_LOGISTICS,
                "faction.test", new StarSystemId(1L), NpcAvailability.AVAILABLE, List.of(fact));
    }

    private static MissionObjective freightObjective() {
        return new MissionObjective(
                ObjectiveAuthority.FREIGHT,
                ObjectiveKind.FREIGHT_ORDER_DELIVERED_KG_AT_LEAST,
                "order.test",
                0L,
                10L,
                "");
    }

    private static MissionContract mission(
            String id,
            MissionStatus status,
            long escrow,
            String outcome) {
        return new MissionContract(
                id,
                MissionTemplate.EMERGENCY_SUPPLY_DELIVERY,
                1,
                npc().npcId(),
                npc().factionContentId(),
                List.of("fact.test"),
                freightObjective(),
                5L,
                20L,
                status,
                status == MissionStatus.OFFERED ? 5L : 6L,
                100L,
                escrow,
                outcome,
                List.of());
    }
}
