package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.world.LocalSystemCoordinates;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage16ConstructionPlacementPolicyAcceptanceTest {
    @Test
    void previewAndCreateShareTheSameAuthoritativePlacementDecision() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_701L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        PlayerConstructionPlacementView valid = findValidPlacement(construction);

        assertTrue(valid.allowed());
        assertEquals(ConstructionPlacementRejection.NONE, valid.rejection());
        assertTrue(runtime.player().ownedConstructionProjectIds().isEmpty());

        var projectId = construction.createProject("station.mining_base", valid.x(), valid.y());

        assertTrue(runtime.player().ownedConstructionProjectIds().contains(projectId));
        assertEquals(valid.systemId(), runtime.world().findConstructionProject(projectId).orElseThrow().systemId());
    }

    @Test
    void rejectsBoundsAndCanonicalJumpArrivalBeforeWorldMutation() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_702L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        int projectsBefore = runtime.world().getConstructionProjects().size();

        assertEquals(
                ConstructionPlacementRejection.OUTSIDE_LOCAL_BOUNDS,
                construction.previewPlacement(0f, Constants.WORLD_HEIGHT / 2f).rejection());
        assertEquals(
                ConstructionPlacementRejection.JUMP_ARRIVAL_EXCLUSION,
                construction.previewPlacement(
                        LocalSystemCoordinates.ARRIVAL_X,
                        LocalSystemCoordinates.ARRIVAL_Y).rejection());

        assertThrows(IllegalArgumentException.class,
                () -> construction.createProject("station.mining_base", 0f, 100f));
        assertThrows(IllegalArgumentException.class,
                () -> construction.createProject(
                        "station.mining_base",
                        LocalSystemCoordinates.ARRIVAL_X,
                        LocalSystemCoordinates.ARRIVAL_Y));
        assertEquals(projectsBefore, runtime.world().getConstructionProjects().size());
        assertTrue(runtime.player().ownedConstructionProjectIds().isEmpty());
    }

    @Test
    void rejectsPhysicalStationAndResourceOverlap() {
        PlayableTestWorldFactory.Scenario scenario = PlayableTestWorldFactory.create(16_703L);
        PlayerRuntime runtime = scenario.runtime();
        PlayerConstructionService construction = new PlayerConstructionService(runtime);
        Entity station = findEntityAwayFromArrival(runtime, IdentityComponent.Kind.STATION);
        Entity asteroid = findEntityAwayFromArrival(runtime, IdentityComponent.Kind.ASTEROID);
        TransformComponent stationTransform = station.getComponent(TransformComponent.class);
        TransformComponent asteroidTransform = asteroid.getComponent(TransformComponent.class);

        assertEquals(
                ConstructionPlacementRejection.STATION_CLEARANCE,
                construction.previewPlacement(
                        stationTransform.position.x,
                        stationTransform.position.y).rejection());
        assertEquals(
                ConstructionPlacementRejection.RESOURCE_CLEARANCE,
                construction.previewPlacement(
                        asteroidTransform.position.x,
                        asteroidTransform.position.y).rejection());
    }

    private static PlayerConstructionPlacementView findValidPlacement(PlayerConstructionService construction) {
        for (float y = 100f; y <= Constants.WORLD_HEIGHT - 100f; y += 100f) {
            for (float x = 100f; x <= Constants.WORLD_WIDTH - 100f; x += 100f) {
                PlayerConstructionPlacementView view = construction.previewPlacement(x, y);
                if (view.allowed()) {
                    return view;
                }
            }
        }
        throw new AssertionError("Playable test world has no valid construction placement");
    }

    private static Entity findEntityAwayFromArrival(PlayerRuntime runtime, IdentityComponent.Kind kind) {
        for (Entity entity : runtime.world().findSession(runtime.world().getActiveSystemId()).orElseThrow()
                .getEngine().getEntities()) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (identity == null || identity.kind != kind || transform == null) {
                continue;
            }
            float dx = transform.position.x - LocalSystemCoordinates.ARRIVAL_X;
            float dy = transform.position.y - LocalSystemCoordinates.ARRIVAL_Y;
            if (dx * dx + dy * dy
                    > ConstructionPlacementPolicy.JUMP_ARRIVAL_CLEARANCE
                    * ConstructionPlacementPolicy.JUMP_ARRIVAL_CLEARANCE) {
                return entity;
            }
        }
        throw new AssertionError("Playable test world has no suitable entity kind " + kind);
    }
}
