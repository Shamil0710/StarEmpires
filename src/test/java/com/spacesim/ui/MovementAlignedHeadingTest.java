package com.spacesim.ui;

import com.spacesim.campaign.GeneratedCampaignSession;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.presentation.asset.Stage22RuntimeSpriteEffects;
import com.spacesim.ui.GeneratedWorldUiSnapshot.LocalObjectView;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementAlignedHeadingTest {
    private static final double EPSILON = 1.0e-12d;

    @Test
    void resolvesCardinalVelocityDirectionsAndStableStationaryFallback() {
        assertEquals(0d, MovementAlignedHeading.radians(10d, 0d), EPSILON);
        assertEquals(Math.PI / 2d, MovementAlignedHeading.radians(0d, 10d), EPSILON);
        assertEquals(Math.PI, MovementAlignedHeading.radians(-10d, 0d), EPSILON);
        assertEquals(-Math.PI / 2d, MovementAlignedHeading.radians(0d, -10d), EPSILON);
        assertEquals(0d, MovementAlignedHeading.radians(0d, 0d), EPSILON);
        assertEquals(0d, MovementAlignedHeading.radians(1.0e-9d, -1.0e-9d), EPSILON);
        assertEquals(90f, MovementAlignedHeading.degrees(Math.PI / 2d), 1.0e-6f);
    }

    @Test
    void movingFreighterProjectsHeadingAndAuthoritativePropulsionSeparately() {
        GeneratedCampaignSession campaign = GeneratedCampaignSession.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        var runtime = campaign.runtime();
        FreighterState freight = runtime.freight().capture().freighters().stream()
                .filter(FreighterState::operational)
                .filter(value -> runtime.freight().findOrder(value.activeOrderId()).isPresent())
                .findFirst().orElseThrow();
        List<StarSystemId> route = runtime.freight().findOrder(freight.activeOrderId()).orElseThrow()
                .orderedSystems();
        StarSystemId origin = freight.currentSystemId();
        StarSystemId destination = nextNeighbor(freight, route);
        runtime.world().activateSystem(origin);

        GeneratedWorldUiModel model = new GeneratedWorldUiModel(
                campaign.rootSeed(), runtime, campaign.content());
        LocalObjectView beforeCommand = model.capture().localObjects().stream()
                .filter(value -> value.stableId().equals("fleet:" + freight.fleetId().value()))
                .findFirst().orElseThrow();
        assertEquals(0d, beforeCommand.propulsionFraction(), EPSILON);
        assertEquals(0d, Stage22RuntimeSpriteEffects.propulsionFraction(beforeCommand.sprite()), EPSILON);

        runtime.world().requestFleetJump(freight.fleetId(), destination);
        campaign.advanceFrame(0.1f);

        var placement = runtime.world().findFleet(freight.fleetId()).orElseThrow();
        assertEquals(FleetLocationKind.IN_SYSTEM, placement.locationKind());
        var kinematics = runtime.arrival().materialization(origin)
                .physicalState(placement.localEntityId()).orElseThrow();
        assertTrue(Math.hypot(kinematics.velocityXMps(), kinematics.velocityYMps()) > 1.0e-6d,
                "acceptance freighter must actually be moving during the approach");

        LocalObjectView projected = model.capture().localObjects().stream()
                .filter(value -> value.stableId().equals("fleet:" + freight.fleetId().value()))
                .findFirst().orElseThrow();

        assertEquals(
                MovementAlignedHeading.radians(
                        kinematics.velocityXMps(), kinematics.velocityYMps()),
                projected.headingRad(),
                EPSILON);
        assertEquals(1d, projected.propulsionFraction(), EPSILON,
                "MOVING_TO_JUMP must expose commanded propulsion independently of speed");
        assertEquals(1d, Stage22RuntimeSpriteEffects.propulsionFraction(projected.sprite()), EPSILON,
                "renderer binding must carry the same authoritative propulsion activity");
    }

    private static StarSystemId nextNeighbor(FreighterState freight, List<StarSystemId> route) {
        int nextIndex = freight.routeIndex() + 1;
        if (nextIndex >= route.size()) {
            nextIndex = freight.routeIndex() - 1;
        }
        if (nextIndex < 0 || nextIndex >= route.size()) {
            throw new IllegalStateException("test freight route has no adjacent hop");
        }
        return route.get(nextIndex);
    }
}
