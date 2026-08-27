package com.spacesim.world.generation;

import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.world.FleetId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Final Stage-21 generated-seed boundedness evidence.
 *
 * <p>The test deliberately measures cardinalities and identity stability rather than wall-clock
 * duration. That keeps the acceptance deterministic while proving that repeated final-checkpoint
 * projection cannot append fleets, physical sidecars, generated infrastructure or living actors.</p>
 */
final class Stage21IGeneratedSeedBoundednessAcceptanceTest {
    private static final List<Long> CORPUS = List.of(
            Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED,
            Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED + 21L,
            Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED + 23L,
            Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED + 41L,
            Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED + 43L);

    @Test
    void representativeGeneratedSeedsKeepStableIdsAndBoundedFinalCheckpointCardinalities() {
        for (long seed : CORPUS) {
            var generated = Stage20PlayableGeneratedWorldFactory.create(seed);
            var stage20 = generated.runtime().captureState();

            int systemCount = stage20.worldState().topology().systems().size();
            int factionCount = stage20.worldState().factions().size();
            int fleetCount = stage20.worldState().fleets().size();
            int physicalCount = stage20.localFleetPhysicalStates().size();
            int endpointCount = generated.runtime().infrastructure().endpoints().size();

            assertTrue(systemCount > 0, "generated corpus member must contain systems");
            assertTrue(factionCount > 1, "generated corpus member must contain interacting factions");
            assertTrue(fleetCount >= factionCount * GeneratedFactionMilitaryBootstrap.SHIPS_PER_FACTION,
                    "each generated faction must retain its finite physical military bootstrap");
            assertEquals(fleetCount, physicalCount,
                    "new generated-world bootstrap has no transit fleets, so every FleetId must have one physical state");

            Set<FleetId> fleetIds = new HashSet<>();
            stage20.worldState().fleets().forEach(fleet -> assertTrue(fleetIds.add(fleet.id()),
                    "ordinary FleetId must be unique"));
            Set<FleetId> physicalIds = new HashSet<>();
            stage20.localFleetPhysicalStates().forEach(state -> assertTrue(physicalIds.add(state.fleetId()),
                    "physical sidecar must not duplicate a FleetId"));
            assertEquals(fleetIds, physicalIds,
                    "physical sidecar identity coverage must equal ordinary in-system fleet authority");

            Set<String> stationIds = new HashSet<>();
            generated.runtime().infrastructure().endpoints().forEach(endpoint -> assertTrue(
                    stationIds.add(endpoint.stationId()),
                    "generated infrastructure must not duplicate stable station identity"));
            assertEquals(endpointCount, stationIds.size());

            byte[] stage20Bytes = Stage20GeneratedWorldRuntimePersistenceCodec.encode(stage20);
            var finalState = Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(stage20Bytes);
            byte[] canonical = Stage21IGeneratedWorldRuntimePersistenceCodec.encode(finalState);

            int actorCount = finalState.stage21HRuntime().stage21GRuntime().stage21FRuntime()
                    .stage21ERuntime().stage21DRuntime().stage21CRuntime().stage21BRuntime()
                    .stage21ARuntime().livingActors().size();
            assertEquals(factionCount, actorCount,
                    "Stage-21 actor bootstrap remains one bounded persistent actor per generated faction");

            for (int repeat = 0; repeat < 4; repeat++) {
                var restored = Stage21IGeneratedWorldRuntimePersistenceCodec.decode(canonical);
                byte[] reencoded = Stage21IGeneratedWorldRuntimePersistenceCodec.encode(restored);
                assertArrayEquals(canonical, reencoded,
                        "repeated final checkpoint round-trip must be byte stable");

                var restoredStage20 = restored.stage21HRuntime().stage21GRuntime().stage21FRuntime()
                        .stage21ERuntime().stage21DRuntime().stage21CRuntime().stage21BRuntime()
                        .stage21ARuntime().stage20Runtime();
                assertEquals(systemCount, restoredStage20.worldState().topology().systems().size());
                assertEquals(factionCount, restoredStage20.worldState().factions().size());
                assertEquals(fleetCount, restoredStage20.worldState().fleets().size());
                assertEquals(physicalCount, restoredStage20.localFleetPhysicalStates().size());
                assertEquals(actorCount, restored.stage21HRuntime().stage21GRuntime().stage21FRuntime()
                        .stage21ERuntime().stage21DRuntime().stage21CRuntime().stage21BRuntime()
                        .stage21ARuntime().livingActors().size());
                canonical = reencoded;
            }
        }
    }
}
