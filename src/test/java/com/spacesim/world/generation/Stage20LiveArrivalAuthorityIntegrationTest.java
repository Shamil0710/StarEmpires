package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.Stage22CorePairStrategicMobilityProjection;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistence;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage20LiveArrivalAuthorityIntegration;
import com.spacesim.persistence.Stage20MaterializationPersistence;
import com.spacesim.persistence.Stage20MaterializationPersistentState;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.simulation.Stage20MaterializationService;
import com.spacesim.ship.ProductionEngineeringRuntimeResolver;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetJumpPhase;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.LocalPhysicalKinematics;
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
        var outgoing = integration.resolve(destinationSystem, originSystem);
        EntityId formerLocalId = source.localEntityId();
        integration.materialization(originSystem).registerPhysicalState(
                formerLocalId,
                LocalPhysicalKinematics.stationary(outgoing.physicalState().position()));
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
    void routeThroughIntermediateSystemArrivesFromOriginSideAndMustFlyToNextFtlPoint() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState campaign = savedState(fixture);
        WorldSimulation world = world(fixture);
        Stage20LiveArrivalAuthorityIntegration integration =
                Stage20LiveArrivalAuthorityIntegration.restoreAndBind(campaign, world);
        RouteFixture route = routeFixture(world);
        FleetPlacementState source = route.sourceFleet();

        var aOutgoingToB = integration.resolve(route.middle(), route.origin());
        integration.materialization(route.origin()).registerPhysicalState(
                source.localEntityId(),
                LocalPhysicalKinematics.stationary(aOutgoingToB.physicalState().position()));
        world.requestFleetJump(source.id(), route.middle());
        advanceUntilArrived(world, source.id());

        FleetPlacementState inMiddle = world.findFleet(source.id()).orElseThrow();
        var bIncomingFromA = integration.resolve(route.origin(), route.middle());
        var bOutgoingToC = integration.resolve(route.destination(), route.middle());
        assertEquals(route.middle(), inMiddle.systemId());
        assertEquals(
                bIncomingFromA.physicalState().position(),
                integration.materialization(route.middle())
                        .physicalState(inMiddle.localEntityId()).orElseThrow().position());
        assertNotEquals(
                bIncomingFromA.physicalState().position(),
                bOutgoingToC.physicalState().position(),
                "different neighboring systems must use different local FTL endpoints");

        var secondJump = world.requestFleetJump(source.id(), route.destination());
        assertEquals(FleetJumpPhase.MOVING_TO_JUMP, secondJump.phase());
        assertTrue(secondJump.phaseEndsTick() - secondJump.phaseStartedTick() > 1L,
                "cross-system local traversal must not collapse to the legacy one-tick approach");
        assertEquals(FleetLocationKind.IN_SYSTEM, world.findFleet(source.id()).orElseThrow().locationKind());
        assertEquals(route.middle(), world.findFleet(source.id()).orElseThrow().systemId());

        var before = integration.materialization(route.middle())
                .physicalState(inMiddle.localEntityId()).orElseThrow();
        world.advanceFrame(SimulationSession.DEFAULT_FIXED_STEP_SECONDS);
        var after = integration.materialization(route.middle())
                .physicalState(inMiddle.localEntityId()).orElseThrow();
        assertNotEquals(before.position(), after.position(),
                "MOVING_TO_JUMP must physically advance toward the next outgoing endpoint");
        assertTrue(after.position().distanceTo(bOutgoingToC.physicalState().position())
                < before.position().distanceTo(bOutgoingToC.physicalState().position()));
        assertEquals(FleetLocationKind.IN_SYSTEM, world.findFleet(source.id()).orElseThrow().locationKind());
        assertEquals(route.middle(), world.findFleet(source.id()).orElseThrow().systemId());

        Entity live = world.findSession(route.middle()).orElseThrow()
                .getEntityRegistry().find(inMiddle.localEntityId());
        TransformComponent transform = live.getComponent(TransformComponent.class);
        assertEquals((float) after.position().offsetXM(), transform.position.x, 0f);
        assertEquals((float) after.position().offsetYM(), transform.position.y, 0f);
        assertEquals((float) after.velocityXMps(), transform.velocity.x, 0f);
        assertEquals((float) after.velocityYMps(), transform.velocity.y, 0f);
    }

    @Test
    void directJumpRejectsBeforeSpendingWhenFuelCanAccelerateButCannotBrake() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState campaign = savedState(fixture);
        WorldSimulation world = world(fixture);
        Stage20LiveArrivalAuthorityIntegration integration =
                Stage20LiveArrivalAuthorityIntegration.restoreAndBind(campaign, world);
        FleetPlacementState source = sourceFleetWithNeighbor(world);
        StarSystemId origin = source.systemId();
        StarSystemId destination = world.getTopology().neighbors(origin).get(0);
        var outgoing = integration.resolve(destination, origin);
        integration.materialization(origin).registerPhysicalState(
                source.localEntityId(),
                LocalPhysicalKinematics.stationary(
                        outgoing.physicalState().position().translated(5_000_000d, 0d)));

        Entity entity = world.findSession(origin).orElseThrow()
                .getEntityRegistry().require(source.localEntityId());
        EngineeringComponent full = strategicEngineering(Double.MAX_VALUE);
        entity.add(full);

        var fullLeg = world.planFleetRouteFuel(source.id(), List.of(origin, destination));
        assertTrue(fullLeg.supported());
        assertTrue(fullLeg.feasible());
        assertTrue(fullLeg.requiredDeltaVMps() > 0d);

        double maximumReactionMass = full.runtimeState.consumables().reactionMassKg();
        double accelerationOnlyBudget = fullLeg.requiredDeltaVMps() * 0.75d;
        ProductionEngineeringRuntimeResolver resolver = new ProductionEngineeringRuntimeResolver();
        double low = 0d;
        double high = maximumReactionMass;
        for (int attempt = 0; attempt < 60; attempt++) {
            double candidate = (low + high) * 0.5d;
            if (resolver.planDeltaV(
                    strategicEngineering(candidate), accelerationOnlyBudget).feasible()) {
                high = candidate;
            } else {
                low = candidate;
            }
        }
        double limitedMass = Math.min(maximumReactionMass, high * 1.001d + 1.0e-6d);
        EngineeringComponent limited = strategicEngineering(limitedMass);
        assertTrue(resolver.planDeltaV(limited, accelerationOnlyBudget).feasible(),
                "fixture must retain enough propellant for the initial acceleration");
        assertFalse(resolver.planDeltaV(limited, fullLeg.requiredDeltaVMps()).feasible(),
                "fixture must be unable to complete acceleration plus braking");
        entity.add(limited);
        double before = limited.runtimeState.consumables().reactionMassKg();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> world.requestFleetJump(source.id(), destination));

        assertTrue(failure.getMessage().contains("complete FTL departure approach"));
        assertEquals(before, limited.runtimeState.consumables().reactionMassKg(), 1.0e-9d,
                "failed complete-leg preflight must spend no reaction mass");
        assertTrue(world.findFleetJump(source.id()).isEmpty(),
                "infeasible direct hop must not create an active jump");
        assertEquals(origin, world.findFleet(source.id()).orElseThrow().systemId());
    }

    @Test
    void departureReadinessRejectsFleetAwayFromOutgoingFtlPoint() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState campaign = savedState(fixture);
        WorldSimulation world = world(fixture);
        Stage20LiveArrivalAuthorityIntegration integration =
                Stage20LiveArrivalAuthorityIntegration.restoreAndBind(campaign, world);
        FleetPlacementState source = sourceFleetWithNeighbor(world);
        StarSystemId origin = source.systemId();
        StarSystemId destination = world.getTopology().neighbors(origin).get(0);
        var outgoing = integration.resolve(destination, origin);
        var displaced = outgoing.physicalState().position().translated(123d, -45d);
        integration.materialization(origin).registerPhysicalState(
                source.localEntityId(),
                LocalPhysicalKinematics.stationary(displaced));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> integration.validateDepartureReady(
                        source.id(), origin, destination, source.localEntityId()));
        assertTrue(failure.getMessage().contains("has not reached outgoing FTL endpoint"));

        integration.materialization(origin).updatePhysicalState(
                source.localEntityId(),
                LocalPhysicalKinematics.stationary(outgoing.physicalState().position()));
        integration.validateDepartureReady(
                source.id(), origin, destination, source.localEntityId());
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
        var outgoing = originalIntegration.resolve(destination, source.systemId());
        originalIntegration.materialization(source.systemId()).registerPhysicalState(
                source.localEntityId(),
                LocalPhysicalKinematics.stationary(outgoing.physicalState().position()));

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

    private static EngineeringComponent strategicEngineering(double requestedReactionMassKg) {
        var catalog = Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
        var definition = catalog.findDemonstratorFit(
                Stage22CorePairStrategicMobilityProjection.EMPIRE_DESTROYER_STRATEGIC_FIT);
        InstalledFit fit = InstalledFit.fromDemonstrator(definition);
        double remaining = requestedReactionMassKg;
        ArrayList<ConsumableLoad> loads = new ArrayList<>();
        for (var installed : fit.installedModules()) {
            var module = catalog.findModule(installed.moduleId());
            for (var iface : module.interfaces()) {
                if (iface.kind() != InterfaceKind.REACTION_MASS) {
                    continue;
                }
                double amount = Math.min(iface.capacity(), Math.max(0d, remaining));
                loads.add(new ConsumableLoad(
                        installed.mountId(), iface.id(), iface.kind(), amount, amount, 0L));
                remaining = Math.max(0d, remaining - amount);
            }
        }
        ConsumableState consumables = new ConsumableState(0d, 0d, 0d, 0d, loads);
        ShipEngineeringRuntime runtime = new ShipEngineeringRuntime(catalog);
        var state = runtime.initialize(fit, consumables, DamageState.pristine());
        return new EngineeringComponent(fit, state);
    }

    private static RouteFixture routeFixture(WorldSimulation world) {
        for (FleetPlacementState fleet : world.getFleetPlacements()) {
            if (fleet.locationKind() != FleetLocationKind.IN_SYSTEM) {
                continue;
            }
            Entity entity = world.findSession(fleet.systemId()).orElseThrow()
                    .getEntityRegistry().find(fleet.localEntityId());
            if (entity == null) {
                continue;
            }
            for (StarSystemId middle : world.getTopology().neighbors(fleet.systemId())) {
                StarSystemId destination = world.getTopology().neighbors(middle).stream()
                        .filter(value -> !value.equals(fleet.systemId()))
                        .findFirst().orElse(null);
                if (destination != null) {
                    return new RouteFixture(fleet, fleet.systemId(), middle, destination);
                }
            }
        }
        throw new AssertionError("generated acceptance world lacks fleet route A->B->C");
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

    private record RouteFixture(
            FleetPlacementState sourceFleet,
            StarSystemId origin,
            StarSystemId middle,
            StarSystemId destination) { }
}
