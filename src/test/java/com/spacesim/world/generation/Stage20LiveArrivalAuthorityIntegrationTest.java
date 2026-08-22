package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistence;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage20LiveArrivalAuthorityIntegration;
import com.spacesim.persistence.Stage20MaterializationPersistence;
import com.spacesim.persistence.Stage20MaterializationPersistentState;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.simulation.Stage20MaterializationService;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetJumpPhase;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.Stage20DiscoveryKnowledgeState;
import com.spacesim.world.Stage20SpecialLocationGenerator;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemSimulationState;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;
import com.spacesim.world.generation.Stage20OperationalIndustrialSpecializationProductionIntegrationTest
        .CadenceFixture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20LiveArrivalAuthorityIntegrationTest {
    private static final ContentCatalog CONTENT = ContentCatalogLoader.loadDefault();
    private static volatile CadenceFixture sharedFixture;

    @Test
    void ordinaryLiveJumpAppliesExactSavedEndpointWithoutFloatClampOrVelocityReset() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState campaign = savedState(fixture);
        WorldSimulation world = world(fixture);
        Stage20LiveArrivalAuthorityIntegration integration =
                Stage20LiveArrivalAuthorityIntegration.restoreAndBind(campaign, world);
        FleetPlacementState source = sourceFleetWithNeighbor(world);
        StarSystemId originSystem = source.systemId();
        StarSystemId destinationSystem = world.getTopology().neighbors(originSystem).get(0);
        var expected = integration.resolve(originSystem, destinationSystem);
        EntityId formerLocalId = source.localEntityId();
        LocalPhysicalKinematics originPhysical = new LocalPhysicalKinematics(
                new LocalPhysicalPosition(9_000_000_000L, -8_000_000_000L, 42.5d, -73.25d),
                810d,
                -215d);
        integration.materialization(originSystem).registerPhysicalState(
                formerLocalId, originPhysical);
        var discoveryBefore = campaign.discoveryState();

        var requested = world.requestFleetJump(source.id(), destinationSystem, 999f, -777f);

        assertEquals(expected.legacyProjectionX(), requested.arrivalX(), 0f);
        assertEquals(expected.legacyProjectionY(), requested.arrivalY(), 0f);
        assertNotEquals(999f, requested.arrivalX());
        assertNotEquals(-777f, requested.arrivalY());
        advanceUntilArrived(world, source.id());

        FleetPlacementState arrived = world.findFleet(source.id()).orElseThrow();
        assertEquals(source.id(), arrived.id());
        assertEquals(FleetLocationKind.IN_SYSTEM, arrived.locationKind());
        assertEquals(destinationSystem, arrived.systemId());
        assertFalse(integration.materialization(originSystem)
                .physicalState(formerLocalId).isPresent());
        assertEquals(
                expected.physicalState(),
                integration.materialization(destinationSystem)
                        .physicalState(arrived.localEntityId()).orElseThrow());
        assertEquals(expected.physicalState().position().cellX(),
                integration.materialization(destinationSystem)
                        .physicalState(arrived.localEntityId()).orElseThrow().position().cellX());
        assertEquals(expected.physicalState().velocityXMps(),
                integration.materialization(destinationSystem)
                        .physicalState(arrived.localEntityId()).orElseThrow().velocityXMps(), 0d);
        assertEquals(discoveryBefore, campaign.discoveryState());

        Stage20MaterializationPersistentState persisted = Stage20MaterializationPersistence.capture(
                world.findSession(destinationSystem).orElseThrow(),
                integration.materialization(destinationSystem));
        assertEquals(expected.physicalState(), persisted.physicalEntities().stream()
                .filter(value -> value.id().equals(arrived.localEntityId()))
                .findFirst().orElseThrow().physicalState());
    }

    @Test
    void liveAuthorityRejectsNonNeighbor() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState campaign = savedState(fixture);
        WorldSimulation world = world(fixture);
        Stage20LiveArrivalAuthorityIntegration integration =
                Stage20LiveArrivalAuthorityIntegration.restoreAndBind(campaign, world);
        FleetPlacementState source = sourceFleetWithNeighbor(world);
        StarSystemId nonNeighbor = world.getTopology().systems().stream()
                .map(value -> value.id())
                .filter(value -> !value.equals(source.systemId()))
                .filter(value -> !world.getTopology().neighbors(source.systemId()).contains(value))
                .findFirst().orElseThrow();

        assertThrows(IllegalArgumentException.class,
                () -> integration.resolve(source.systemId(), nonNeighbor));
        assertThrows(IllegalArgumentException.class,
                () -> world.requestFleetJump(source.id(), nonNeighbor));
        assertEquals(FleetLocationKind.IN_SYSTEM,
                world.findFleet(source.id()).orElseThrow().locationKind());
        assertTrue(world.findFleetJump(source.id()).isEmpty());
    }

    @Test
    void restoredMidTransitFleetNeedsNoProcessLocalDepartureMarker() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState campaign = savedState(fixture);
        WorldSimulation original = world(fixture);
        Stage20LiveArrivalAuthorityIntegration originalIntegration =
                Stage20LiveArrivalAuthorityIntegration.restoreAndBind(campaign, original);
        FleetPlacementState source = sourceFleetWithNeighbor(original);
        StarSystemId destination = original.getTopology().neighbors(source.systemId()).get(0);
        var expected = originalIntegration.resolve(source.systemId(), destination);
        originalIntegration.materialization(source.systemId()).registerPhysicalState(
                source.localEntityId(),
                new LocalPhysicalKinematics(
                        new LocalPhysicalPosition(3L, -4L, 5d, -6d),
                        7d,
                        -8d));

        original.requestFleetJump(source.id(), destination);
        advanceUntilPhase(original, source.id(), FleetJumpPhase.IN_TRANSIT);
        WorldState inTransit = original.snapshot();

        WorldSimulation restored = WorldSimulation.restore(
                inTransit,
                CONTENT,
                original.getActiveSystemId(),
                10,
                Math.max(1, inTransit.systems().size()));
        Stage20LiveArrivalAuthorityIntegration restoredIntegration =
                Stage20LiveArrivalAuthorityIntegration.restoreAndBind(campaign, restored);
        advanceUntilArrived(restored, source.id());

        FleetPlacementState arrived = restored.findFleet(source.id()).orElseThrow();
        assertEquals(source.id(), arrived.id());
        assertEquals(destination, arrived.systemId());
        assertEquals(
                expected.physicalState(),
                restoredIntegration.materialization(destination)
                        .physicalState(arrived.localEntityId()).orElseThrow());
    }

    private static void advanceUntilArrived(
            WorldSimulation world,
            com.spacesim.world.FleetId fleetId) {
        for (int attempt = 0; attempt < 200 && world.findFleetJump(fleetId).isPresent(); attempt++) {
            world.advanceFrame(0.25f);
        }
        assertTrue(world.findFleetJump(fleetId).isEmpty(), "ordinary jump did not complete");
    }

    private static void advanceUntilPhase(
            WorldSimulation world,
            com.spacesim.world.FleetId fleetId,
            FleetJumpPhase phase) {
        for (int attempt = 0; attempt < 200; attempt++) {
            if (world.findFleetJump(fleetId).orElseThrow().phase() == phase) {
                return;
            }
            world.advanceFrame(0.25f);
        }
        throw new AssertionError("ordinary jump did not reach phase " + phase);
    }

    private static FleetPlacementState sourceFleetWithNeighbor(WorldSimulation world) {
        return world.getFleetPlacements().stream()
                .filter(value -> value.locationKind() == FleetLocationKind.IN_SYSTEM)
                .filter(value -> !world.getTopology().neighbors(value.systemId()).isEmpty())
                .filter(value -> {
                    Entity entity = world.findSession(value.systemId()).orElseThrow()
                            .getEntityRegistry().find(value.localEntityId());
                    return entity != null;
                })
                .findFirst().orElseThrow();
    }

    private static WorldSimulation world(CadenceFixture fixture) {
        var topology = fixture.resolved().generation().topology().requireAcceptedTopology();
        ArrayList<StarSystemSimulationState> systems = new ArrayList<>();
        for (var system : topology.systems()) {
            systems.add(new StarSystemSimulationState(
                    system.id(),
                    SimulationSession.createDemo(
                            fixture.resolved().rootSeed() ^ system.id().value(), CONTENT).snapshot()));
        }
        StarSystemId active = topology.systems().get(0).id();
        return WorldSimulation.restore(
                new WorldState(WorldState.CURRENT_VERSION, topology, systems),
                CONTENT,
                active,
                10,
                Math.max(1, systems.size()));
    }

    private static Stage20GeneratedCampaignPersistentState savedState(CadenceFixture fixture) {
        SimulationSession session = SimulationSession.createDemo(fixture.resolved().rootSeed());
        Stage20MaterializationService materialization = Stage20MaterializationService.forSession(session);
        return Stage20GeneratedCampaignPersistence.capture(
                fixture.resolved(),
                Stage20SpecialLocationGenerator.generateCurrent(fixture.resolved()),
                fixture.specialization(),
                Stage20MaterializationPersistence.capture(session, materialization),
                Stage18IndustrialState.empty(0L),
                List.of(new Stage20DiscoveryKnowledgeState(
                        "faction.stage20_5.live-arrival",
                        List.of())));
    }

    private static synchronized CadenceFixture fixture() {
        if (sharedFixture == null) {
            sharedFixture = Stage20OperationalIndustrialSpecializationProductionIntegrationTest.cadenceFixture();
        }
        return sharedFixture;
    }
}
