package com.spacesim.world;

import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.world.Stage21HNpcMissionState.NpcAvailability;
import com.spacesim.world.Stage21HNpcMissionState.NpcRole;
import com.spacesim.world.Stage21HNpcMissionState.NpcState;
import com.spacesim.world.generation.Stage20MacroGalaxyGeometryGenerator;
import com.spacesim.world.generation.Stage20MacroGalaxyGeometryGenerator.GenerationRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Deterministic Stage-21I workload envelopes across every required population axis. */
final class Stage21IWorkloadEnvelopeAcceptanceTest {
    private static final List<Integer> INCREASING_COUNTS = List.of(8, 32, 128);
    private static final int ACTOR_REVIEW_BUDGET = 7;

    @Test
    void increasingFactionSystemFleetAndNpcCountsStayIdentityBoundedAndDeterministic() {
        int previousCount = 0;
        for (int count : INCREASING_COUNTS) {
            var actorBatch = FactionLivingActorScheduler.selectDue(actors(count), 10L, ACTOR_REVIEW_BUDGET);
            assertEquals(count, actorBatch.eligibleCount());
            assertEquals(ACTOR_REVIEW_BUDGET, actorBatch.selected().size(),
                    "expensive faction reviews must remain capped as population grows");
            assertEquals(count - ACTOR_REVIEW_BUDGET, actorBatch.deferredCount());
            assertEquals(expectedFirstActorIds(), actorBatch.selected().stream()
                    .map(FactionLivingActorScheduler.ScheduledReview::factionContentId).toList());

            var geometry = Stage20MacroGalaxyGeometryGenerator.generate(
                    0x21_1_5EEDL + count,
                    new GenerationRequest(4, count / 4, count / 4));
            assertEquals(count, geometry.systemEvidence().size(),
                    "system projection must equal the requested finite generated population");
            assertEquals(count, new HashSet<>(geometry.systemEvidence().stream()
                    .map(value -> value.systemId()).toList()).size(),
                    "increasing system populations must not duplicate stable identity");

            FleetForceRegistry forces = new FleetForceRegistry(fleets(count));
            assertEquals(count, forces.entries().size(),
                    "the read model must contain exactly one row per authoritative FleetId");
            assertEquals(count, new HashSet<>(forces.entries().stream()
                    .map(FleetForceRegistry.Entry::fleetId).toList()).size());
            assertEquals(count, java.util.stream.IntStream.range(0, 4)
                    .map(factionId -> forces.ownedBy(factionId).size()).sum());

            Stage21HNpcMissionState npcState = new Stage21HNpcMissionState(
                    Stage21HNpcMissionState.CURRENT_VERSION,
                    10L,
                    1L,
                    npcs(count),
                    List.of(),
                    List.of(),
                    List.of());
            byte[] canonical = Stage21HNpcMissionStateCodec.encode(npcState);
            Stage21HNpcMissionState restored = Stage21HNpcMissionStateCodec.decode(canonical);
            assertEquals(count, restored.npcs().size(),
                    "NPC persistence must retain exactly the supplied bounded population");
            assertEquals(count, new HashSet<>(restored.npcs().stream().map(NpcState::npcId).toList()).size());
            assertArrayEquals(canonical, Stage21HNpcMissionStateCodec.encode(restored));

            org.junit.jupiter.api.Assertions.assertTrue(count > previousCount);
            previousCount = count;
        }
    }

    private static List<FactionLivingActorState> actors(int count) {
        ArrayList<FactionLivingActorState> result = new ArrayList<>();
        for (int index = count - 1; index >= 0; index--) {
            result.add(FactionLivingActorState.initial("faction.workload.%05d".formatted(index), 10L));
        }
        return List.copyOf(result);
    }

    private static List<String> expectedFirstActorIds() {
        return java.util.stream.IntStream.range(0, ACTOR_REVIEW_BUDGET)
                .mapToObj(index -> "faction.workload.%05d".formatted(index))
                .toList();
    }

    private static List<FleetForceRegistry.Entry> fleets(int count) {
        ArrayList<FleetForceRegistry.Entry> result = new ArrayList<>();
        for (int index = count - 1; index >= 0; index--) {
            long id = index + 1L;
            EntityState entity = new EntityState(
                    new EntityId(id),
                    new EntityState.IdentityState("Workload Fleet " + id, "FLEET"),
                    null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
            result.add(new FleetForceRegistry.Entry(
                    new FleetId(id),
                    index % 4,
                    FleetLocationKind.IN_SYSTEM,
                    new StarSystemId(index % 8 + 1L),
                    null,
                    null,
                    entity,
                    FleetReadinessState.unavailable()));
        }
        return List.copyOf(result);
    }

    private static List<NpcState> npcs(int count) {
        ArrayList<NpcState> result = new ArrayList<>();
        for (int index = count - 1; index >= 0; index--) {
            result.add(new NpcState(
                    "npc.workload.%05d".formatted(index),
                    "npc.workload.name.%05d".formatted(index),
                    NpcRole.INDEPENDENT_FRONTIER,
                    "faction.workload",
                    new StarSystemId(index % 8 + 1L),
                    NpcAvailability.AVAILABLE,
                    List.of()));
        }
        return List.copyOf(result);
    }
}
