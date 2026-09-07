package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.content.Stage22CorePairEvidenceArchive;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21BGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21CGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21DGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21EGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21FGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21FGeneratedWorldRuntimePersistentState;
import com.spacesim.ship.Stage22CorePairTacticalFactory;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderStatus;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import com.spacesim.world.TerritorialTransitionState.OccupationStatus;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B15 physical-core-package and Stage-21F save-continuation evidence.
 *
 * <p>The older B15 probe proves the stable core identities are accepted by Stage-21F territorial
 * law, but its package-local force fixture is intentionally synthetic. This complementary acceptance
 * starts from a production generated world and an already materialized ordinary military FleetId.
 * The fleet moves through the ordinary fitted FTL FSM to a non-owned neighboring system, then
 * receives one exact Stage-22 core destroyer engineering package. Any bootstrap resident fleets in
 * the destination are removed through the ordinary world lifecycle before departure so this focused
 * occupation lane does not depend on an accidental generated-world emptiness invariant. Stage-21D
 * INVADE and Stage-21E operation admission consume a {@link FleetForceRegistry} reconstructed from
 * that exact persistent entity; no hand-authored force entry or readiness vector participates in
 * occupation authority.</p>
 *
 * <p>The occupation is checkpointed halfway through its sustained-security clock via the production
 * Stage-21F codec. Direct and restored worlds must then reach the same Stage-17 claim, completed
 * operation and byte-identical final Stage-21F checkpoint for both exact core packages. The test does
 * not claim that the generated world owner itself is one of the two core faction identities; stable
 * core-identity mirroring remains covered by {@code Stage22CorePairTerritoryMachineEvidenceAcceptanceTest}.</p>
 */
class Stage22CorePairTerritoryGeneratedWorldPersistenceAcceptanceTest {
    private static final int CREW_AVAILABLE = 100_000;
    private static final int SUPPLY_ACCESS_BPS = FleetReadinessState.FULL;
    private static final int MINIMUM_MISSION_READINESS_BPS = 1_000;
    private static final int MINIMUM_SUPPLY_ACCESS_BPS = 5_000;

    @Test
    void b15ExactCorePhysicalFleetOccupationSurvivesMidTransitionStage21FCheckpoint() {
        LaneResult empire = runLane(true);
        LaneResult union = runLane(false);

        assertTrue(empire.finalCheckpointEqual());
        assertTrue(union.finalCheckpointEqual());
        assertNotNull(empire.exactFitId());
        assertNotNull(union.exactFitId());
        assertFalse(empire.exactFitId().equals(union.exactFitId()),
                "B15 physical supplement must exercise both exact core packages");

        LinkedHashMap<String, Object> archive = new LinkedHashMap<>();
        archive.put("empirePackageLane", empire);
        archive.put("industrialUnionPackageLane", union);
        Stage22CorePairEvidenceArchive.write(
                "B15-generated-world-core-package-occupation-save-continuation",
                archive,
                "Two fresh production generated worlds supply ordinary persistent military FleetIds and ordinary fitted FTL movement. Each lane deterministically selects a non-owned neighboring system and removes any bootstrap resident fleets through the ordinary world lifecycle solely to isolate occupation authority from generated-world population accidents. After physical arrival each lane receives one exact Stage-22 core destroyer package, readiness is reconstructed from the persisted entity, and Stage-21D/21E/21F authorities own invasion/occupation/claim progression. A halfway Stage-21F checkpoint resumes to the same Stage-17 claim and byte-identical final checkpoint. Generated world political owners remain world-bootstrap identities; the separate B15 mirrored core-identity probe remains the authority evidence for stable Empire/Industrial Union territorial identity binding.");
    }

    private static LaneResult runLane(boolean empirePackage) {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(
                ContentCatalogLoader.loadDefault(), runtime.world().snapshot().factionIdentities());
        Candidate candidate = candidate(runtime, identities);
        clearDestinationFleets(runtime, candidate.targetSystemId());

        GeneratedWorldFtlTestSupport.placeAtOutgoingEndpoint(runtime, candidate.fleetId(), candidate.targetSystemId());
        runtime.world().requestFleetJump(candidate.fleetId(), candidate.targetSystemId());
        GeneratedWorldFtlTestSupport.advanceOrdinaryJumpToCompletion(runtime, candidate.fleetId());
        assertEquals(candidate.targetSystemId(), runtime.world().findFleet(candidate.fleetId()).orElseThrow().systemId());

        var duel = Stage22CorePairTacticalFactory.createDestroyerDuel(
                com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation.DEFAULT);
        long sourceEntityId = empirePackage
                ? Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID
                : Stage22CorePairTacticalFactory.UNION_ENTITY_ID;
        String exactFitId = empirePackage
                ? Stage22CorePairTacticalFactory.EMPIRE_DESTROYER_FIT
                : Stage22CorePairTacticalFactory.UNION_DESTROYER_FIT;
        EngineeringComponent exact = duel.weapons().battleState().combatants().stream()
                .filter(value -> value.spec().entityId() == sourceEntityId)
                .findFirst()
                .orElseThrow()
                .engineering();
        Entity physical = entity(runtime, candidate.fleetId());
        physical.add(new EngineeringComponent(exact.fit, exact.runtimeState, exact.instanceState));
        assertEquals(candidate.runtimeFactionId(), physical.getComponent(FactionComponent.class).factionId);
        assertEquals(duel.content().engineering().findDemonstratorFit(exactFitId).hullId(), exact.fit.hullId());

        Map<FleetId, FleetOperationalAvailability> availability = Map.of(
                candidate.fleetId(), new FleetOperationalAvailability(CREW_AVAILABLE, SUPPLY_ACCESS_BPS));
        FleetReadinessEvaluator evaluator = new FleetReadinessEvaluator(duel.content().engineering());
        FleetForceRegistry forces = FleetForceRegistry.reconstruct(runtime.world().snapshot(), evaluator, availability);
        FleetForceRegistry.Entry force = forces.find(candidate.fleetId()).orElseThrow();
        assertEquals(candidate.runtimeFactionId(), force.factionId());
        assertEquals(candidate.targetSystemId(), force.systemId());
        assertEquals(exact.fit.hullId(), force.entityState().engineering().hullId());
        assertTrue(force.readiness().missionCapable(MINIMUM_MISSION_READINESS_BPS));
        assertTrue(force.readiness().supplyAccessBps() >= MINIMUM_SUPPLY_ACCESS_BPS);

        long startTick = runtime.world().getAuthoritativeWorldTick();
        FleetCommandState commands = commands(candidate, startTick);
        StrategicOperationState operations = new StrategicOperationService().beginFromActiveOrder(
                StrategicOperationState.empty(), commands, forces, 1L, startTick,
                RulesOfEngagement.DECLARED_HOSTILES,
                new SupplyPolicy(MINIMUM_MISSION_READINESS_BPS, MINIMUM_SUPPLY_ACCESS_BPS,
                        TerritorialTransitionService.OCCUPATION_COLLAPSE_GRACE_TICKS),
                new WithdrawalPolicy(candidate.originSystemId(), 0, true, true));
        assertEquals(StrategicOperationState.OperationType.INVASION, operations.requireOperation(1L).type());
        assertTrue(operations.requireOperation(1L).status().active());

        TerritorialTransitionService territory = new TerritorialTransitionService();
        var initial = territory.advance(TerritorialTransitionState.empty(), runtime.world(), operations, forces,
                identities, 1L, startTick);
        assertEquals(OccupationStatus.OCCUPYING, initial.occupation().status());
        assertEquals(0L, initial.occupation().securedTicks());
        assertFalse(initial.claimCreated());
        assertTrue(initial.securityReady());
        assertTrue(initial.supplyReady());
        assertFalse(initial.rivalFleetPresent());

        long midpointTick = startTick + TerritorialTransitionService.REQUIRED_OCCUPATION_TICKS / 2L;
        advanceToTick(runtime.world(), midpointTick);
        FleetForceRegistry midpointForces = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator, availability);
        var midpoint = territory.advance(initial.transitions(), runtime.world(), initial.operations(), midpointForces,
                identities, 1L, midpointTick);
        assertEquals(OccupationStatus.OCCUPYING, midpoint.occupation().status());
        assertEquals(TerritorialTransitionService.REQUIRED_OCCUPATION_TICKS / 2L,
                midpoint.occupation().securedTicks());
        assertFalse(midpoint.claimCreated());
        assertTrue(runtime.world().findFactionStrategicState(candidate.stableFactionId()).orElseThrow()
                .claimFor(candidate.targetSystemId()) == null);

        Stage21FGeneratedWorldRuntimePersistentState checkpoint = Stage21FGeneratedWorldRuntimePersistentState.compose(
                stage21E(runtime.captureState(), commands, midpoint.operations(), midpointTick), midpoint.transitions());
        byte[] encoded = Stage21FGeneratedWorldRuntimePersistenceCodec.encode(checkpoint);
        Stage21FGeneratedWorldRuntimePersistentState decoded =
                Stage21FGeneratedWorldRuntimePersistenceCodec.decode(encoded);
        assertArrayEquals(encoded, Stage21FGeneratedWorldRuntimePersistenceCodec.encode(decoded));

        Stage20GeneratedWorldRuntimeBridge.LiveRuntime restored = Stage20GeneratedWorldRuntimeBridge.restore(
                decoded.stage21ERuntime().stage21DRuntime().stage21CRuntime().stage21BRuntime()
                        .stage21ARuntime().stage20Runtime());
        FactionIdentityResolver restoredIdentities = FactionIdentityResolver.createDefault(
                ContentCatalogLoader.loadDefault(), restored.world().snapshot().factionIdentities());
        FleetForceRegistry restoredMidpointForces = FleetForceRegistry.reconstruct(
                restored.world().snapshot(), evaluator, availability);
        assertEquals(midpointForces.find(candidate.fleetId()).orElseThrow().entityState().engineering(),
                restoredMidpointForces.find(candidate.fleetId()).orElseThrow().entityState().engineering(),
                "exact B15 core engineering must survive the Stage-21F checkpoint");
        assertEquals(midpoint.transitions(), decoded.territorialTransitions());

        long finalTick = startTick + TerritorialTransitionService.REQUIRED_OCCUPATION_TICKS;
        advanceToTick(runtime.world(), finalTick);
        advanceToTick(restored.world(), finalTick);
        FleetForceRegistry directFinalForces = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator, availability);
        FleetForceRegistry restoredFinalForces = FleetForceRegistry.reconstruct(
                restored.world().snapshot(), evaluator, availability);
        var directFinal = territory.advance(midpoint.transitions(), runtime.world(), midpoint.operations(),
                directFinalForces, identities, 1L, finalTick);
        var restoredFinal = territory.advance(decoded.territorialTransitions(), restored.world(),
                decoded.stage21ERuntime().operationState(), restoredFinalForces, restoredIdentities, 1L, finalTick);

        assertEquals(OccupationStatus.SECURED, directFinal.occupation().status());
        assertEquals(TerritorialTransitionService.REQUIRED_OCCUPATION_TICKS, directFinal.occupation().securedTicks());
        assertTrue(directFinal.claimCreated());
        assertTrue(restoredFinal.claimCreated());
        assertEquals(directFinal.transitions(), restoredFinal.transitions());
        assertEquals(directFinal.operations(), restoredFinal.operations());
        assertNotNull(runtime.world().findFactionStrategicState(candidate.stableFactionId()).orElseThrow()
                .claimFor(candidate.targetSystemId()));
        assertNotNull(restored.world().findFactionStrategicState(candidate.stableFactionId()).orElseThrow()
                .claimFor(candidate.targetSystemId()));
        assertTrue(runtime.world().controllingFaction(candidate.targetSystemId()).isEmpty(),
                "occupation claim must not bypass Stage-17 stabilization into instant sovereignty");
        assertTrue(restored.world().controllingFaction(candidate.targetSystemId()).isEmpty());

        byte[] directFinalCheckpoint = Stage21FGeneratedWorldRuntimePersistenceCodec.encode(
                Stage21FGeneratedWorldRuntimePersistentState.compose(
                        stage21E(runtime.captureState(), commands, directFinal.operations(), finalTick),
                        directFinal.transitions()));
        byte[] restoredFinalCheckpoint = Stage21FGeneratedWorldRuntimePersistenceCodec.encode(
                Stage21FGeneratedWorldRuntimePersistentState.compose(
                        stage21E(restored.captureState(), commands, restoredFinal.operations(), finalTick),
                        restoredFinal.transitions()));
        assertArrayEquals(directFinalCheckpoint, restoredFinalCheckpoint,
                "direct/restored physical occupation must converge to one authoritative Stage-21F checkpoint");

        return new LaneResult(empirePackage ? "empire" : "industrial_union", exactFitId,
                candidate.stableFactionId(), candidate.fleetId().value(), candidate.originSystemId().value(),
                candidate.targetSystemId().value(), startTick, midpointTick, finalTick, encoded.length,
                directFinalCheckpoint.length, directFinal.occupation().securedTicks(), true);
    }

    private static Candidate candidate(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FactionIdentityResolver identities) {
        List<FleetPlacementState> placements = runtime.world().getFleetPlacements();
        for (FleetPlacementState placement : placements) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) continue;
            Entity entity = entity(runtime, placement.id());
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (engineering == null || faction == null) continue;
            String stableFactionId = identities.stableId(faction.factionId).orElse(null);
            if (stableFactionId == null) continue;
            for (StarSystemId target : runtime.world().getTopology().neighbors(placement.systemId()).stream().sorted().toList()) {
                if (runtime.world().controllingFaction(target).filter(stableFactionId::equals).isPresent()) continue;
                return new Candidate(placement.id(), faction.factionId, stableFactionId, placement.systemId(), target);
            }
        }
        throw new AssertionError("generated world lacks an ordinary military FleetId adjacent to a non-owned system");
    }

    private static void clearDestinationFleets(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            StarSystemId targetSystemId) {
        List<FleetPlacementState> residents = runtime.world().getFleetPlacements().stream()
                .filter(placement -> placement.locationKind() == FleetLocationKind.IN_SYSTEM)
                .filter(placement -> targetSystemId.equals(placement.systemId()))
                .toList();
        for (FleetPlacementState resident : residents) {
            assertTrue(runtime.world().removeEntity(targetSystemId, resident.localEntityId()),
                    "B15 lane setup must remove the selected destination resident through world lifecycle");
        }
        assertFalse(runtime.world().getFleetPlacements().stream()
                .anyMatch(placement -> placement.locationKind() == FleetLocationKind.IN_SYSTEM
                        && targetSystemId.equals(placement.systemId())),
                "B15 isolated occupation target must contain no bootstrap resident fleet before departure");
    }

    private static Entity entity(Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime, FleetId fleetId) {
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            throw new IllegalStateException("B15 physical fleet must be local");
        }
        return runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
    }

    private static FleetCommandState commands(Candidate candidate, long tick) {
        CommandGroupState group = new CommandGroupState(
                1L, candidate.runtimeFactionId(), "M22.6 B15 physical occupation group",
                List.of(candidate.fleetId()), candidate.originSystemId(), false, true, FleetReadinessState.FULL);
        FleetOrderState order = new FleetOrderState(
                1L, group.id(), OrderType.INVADE, OrderSource.AI, candidate.targetSystemId(),
                List.of(candidate.targetSystemId()), 0, tick,
                tick + TerritorialTransitionService.REQUIRED_OCCUPATION_TICKS + 10L, OrderStatus.ACTIVE);
        return new FleetCommandState(2L, 2L, List.of(group), List.of(order));
    }

    private static Stage21EGeneratedWorldRuntimePersistentState stage21E(
            Stage20GeneratedWorldRuntimePersistentState stage20,
            FleetCommandState commands,
            StrategicOperationState operations,
            long tick) {
        List<FactionLivingActorState> actors = stage20.worldState().factions().stream()
                .map(faction -> FactionLivingActorState.initial(faction.factionContentId(), tick + 1L))
                .toList();
        Stage21AGeneratedWorldRuntimePersistentState stage21A =
                new Stage21AGeneratedWorldRuntimePersistentState(
                        Stage21AGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                        Stage21AGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION, stage20, actors);
        List<FactionStrategicIntentState> intents = actors.stream()
                .map(actor -> FactionStrategicIntentState.initial(actor.factionContentId()))
                .toList();
        Stage21BGeneratedWorldRuntimePersistentState stage21B =
                new Stage21BGeneratedWorldRuntimePersistentState(
                        Stage21BGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                        Stage21BGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION, stage21A, intents);
        Stage21CGeneratedWorldRuntimePersistentState stage21C =
                new Stage21CGeneratedWorldRuntimePersistentState(
                        Stage21CGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                        Stage21CGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                        stage21B, DiplomaticLifecycleState.empty(tick), Stage19ConflictState.empty(tick));
        Stage21DGeneratedWorldRuntimePersistentState stage21D =
                Stage21DGeneratedWorldRuntimePersistentState.compose(stage21C, commands);
        return Stage21EGeneratedWorldRuntimePersistentState.compose(stage21D, operations);
    }

    private static void advanceToTick(WorldSimulation world, long targetTick) {
        float fixedStepSeconds = world.findSession(world.getActiveSystemId()).orElseThrow()
                .getClock().getFixedStepSeconds();
        int guard = 0;
        while (world.getAuthoritativeWorldTick() < targetTick) {
            world.advanceFrame(fixedStepSeconds);
            if (++guard > 20_000) throw new AssertionError("B15 generated world did not reach target tick");
        }
        assertEquals(targetTick, world.getAuthoritativeWorldTick(),
                "B15 advance helper must stop on the exact authoritative tick");
    }

    private record Candidate(
            FleetId fleetId,
            int runtimeFactionId,
            String stableFactionId,
            StarSystemId originSystemId,
            StarSystemId targetSystemId) { }

    private record LaneResult(
            String corePackage,
            String exactFitId,
            String generatedOwnerStableFactionId,
            long fleetId,
            long originSystemId,
            long targetSystemId,
            long startTick,
            long midpointTick,
            long finalTick,
            int midpointCheckpointBytes,
            int finalCheckpointBytes,
            long securedTicks,
            boolean finalCheckpointEqual) { }
}
