package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.content.Stage22CorePairEvidenceArchive;
import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21BGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21CGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21DGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21EGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21EGeneratedWorldRuntimePersistentState;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.Stage22CorePairTacticalFactory;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderStatus;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.StrategicOperationService.SupplyDecision;
import com.spacesim.world.StrategicOperationService.SupplyReview;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B01/B09 generated-world save-continuation for prepared defense and physical reinforcement.
 *
 * <p>Each lane uses two already-commissioned ordinary military FleetIds from one generated faction.
 * The defender first travels one real topology edge through the ordinary fitted FTL FSM while the
 * reserve remains at home. Only after arrival is the defender converted to its exact Stage-22 core
 * package and admitted to a Stage-21D GUARD / Stage-21E DEFENSE operation. Reinforcement therefore
 * fails while the reserve is still physically absent. The reserve then travels the same real edge on
 * its original generated strategic fit, receives the same exact prepared core package after arrival,
 * and joins through {@link Stage21EReinforcementService}.</p>
 *
 * <p>The complete Stage-20.5 world plus Stage-21A-E sidecars is encoded by the production Stage-21E
 * codec and restored. Supported continuation and subsequent supply-loss withdrawal must be identical
 * before and after save/load for both exact core packages. No reserve FleetId, movement, ammunition,
 * readiness or supply fact is synthesized by operation metadata.</p>
 */
class Stage22CorePairPreparedDefenseGeneratedWorldPersistenceAcceptanceTest {
    private static final int CREW_AVAILABLE = 100_000;
    private static final int MINIMUM_MISSION_READINESS_BPS = 1_000;
    private static final int MINIMUM_SUPPLY_ACCESS_BPS = 5_000;

    @Test
    void b09PreparedDefenseReinforcementAndSupplyReviewSurviveAtomicGeneratedWorldSave() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        List<DefenseSeed> seeds = defenseSeeds(runtime);
        DefenseSeed empireSeed = seeds.get(0);
        DefenseSeed unionSeed = seeds.get(1);

        PreparedLane empire = moveDefenderOneOrdinaryHop(runtime, empireSeed);
        PreparedLane union = moveDefenderOneOrdinaryHop(runtime, unionSeed);

        var duel = Stage22CorePairTacticalFactory.createDestroyerDuel(Permutation.DEFAULT);
        EngineeringComponent empireCore = engineering(
                duel, Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID);
        EngineeringComponent unionCore = engineering(
                duel, Stage22CorePairTacticalFactory.UNION_ENTITY_ID);
        ShipEngineeringCatalog engineering = duel.content().engineering();
        entity(runtime, empire.defenderFleetId()).add(preparedDefenseLoadout(empireCore, engineering));
        entity(runtime, union.defenderFleetId()).add(preparedDefenseLoadout(unionCore, engineering));

        FleetReadinessEvaluator evaluator = new FleetReadinessEvaluator(engineering);
        Map<FleetId, FleetOperationalAvailability> supportedAvailability = availability(
                empire, union, FleetReadinessState.FULL);
        FleetForceRegistry beforeReserveArrival = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator, supportedAvailability);

        long admissionTick = runtime.world().getAuthoritativeWorldTick();
        FleetCommandState commands = commands(empire, union, admissionTick);
        StrategicOperationService service = new StrategicOperationService();
        StrategicOperationState operations = StrategicOperationState.empty();
        SupplyPolicy supply = new SupplyPolicy(
                MINIMUM_MISSION_READINESS_BPS,
                MINIMUM_SUPPLY_ACCESS_BPS,
                0L);
        operations = service.beginFromActiveOrder(
                operations,
                commands,
                beforeReserveArrival,
                1L,
                admissionTick,
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                supply,
                new WithdrawalPolicy(empire.homeSystemId(), 0, true, true));
        operations = service.beginFromActiveOrder(
                operations,
                commands,
                beforeReserveArrival,
                2L,
                admissionTick,
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                supply,
                new WithdrawalPolicy(union.homeSystemId(), 0, true, true));
        final StrategicOperationState admittedOperations = operations;

        Stage21EReinforcementService reinforcements = new Stage21EReinforcementService();
        IllegalStateException empireAbsent = assertThrows(
                IllegalStateException.class,
                () -> reinforcements.attachArrived(
                        admittedOperations,
                        1L,
                        empire.reserveFleetId(),
                        beforeReserveArrival,
                        admissionTick));
        IllegalStateException unionAbsent = assertThrows(
                IllegalStateException.class,
                () -> reinforcements.attachArrived(
                        admittedOperations,
                        2L,
                        union.reserveFleetId(),
                        beforeReserveArrival,
                        admissionTick));
        assertTrue(empireAbsent.getMessage().contains("physically arrived"));
        assertTrue(unionAbsent.getMessage().contains("physically arrived"));

        moveOrdinaryHop(runtime, empire.reserveFleetId(), empire.objectiveSystemId());
        moveOrdinaryHop(runtime, union.reserveFleetId(), union.objectiveSystemId());
        entity(runtime, empire.reserveFleetId()).add(preparedDefenseLoadout(empireCore, engineering));
        entity(runtime, union.reserveFleetId()).add(preparedDefenseLoadout(unionCore, engineering));

        FleetForceRegistry arrived = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator, supportedAvailability);
        long reinforcementTick = runtime.world().getAuthoritativeWorldTick();
        operations = reinforcements.attachArrived(
                admittedOperations, 1L, empire.reserveFleetId(), arrived, reinforcementTick);
        operations = reinforcements.attachArrived(
                operations, 2L, union.reserveFleetId(), arrived, reinforcementTick);
        assertEquals(2, operations.requireOperation(1L).participantFleetIds().size());
        assertEquals(2, operations.requireOperation(2L).participantFleetIds().size());
        assertTrue(arrived.find(empire.defenderFleetId()).orElseThrow().readiness()
                .missionCapable(MINIMUM_MISSION_READINESS_BPS));
        assertTrue(arrived.find(empire.reserveFleetId()).orElseThrow().readiness()
                .missionCapable(MINIMUM_MISSION_READINESS_BPS));
        assertTrue(arrived.find(union.defenderFleetId()).orElseThrow().readiness()
                .missionCapable(MINIMUM_MISSION_READINESS_BPS));
        assertTrue(arrived.find(union.reserveFleetId()).orElseThrow().readiness()
                .missionCapable(MINIMUM_MISSION_READINESS_BPS));

        long checkpointTick = runtime.world().getAuthoritativeWorldTick();
        Stage21EGeneratedWorldRuntimePersistentState checkpoint = checkpoint(
                runtime.captureState(), commands, operations, checkpointTick);
        byte[] encoded = Stage21EGeneratedWorldRuntimePersistenceCodec.encode(checkpoint);
        Stage21EGeneratedWorldRuntimePersistentState decoded =
                Stage21EGeneratedWorldRuntimePersistenceCodec.decode(encoded);
        assertArrayEquals(encoded, Stage21EGeneratedWorldRuntimePersistenceCodec.encode(decoded),
                "prepared-defense Stage-21E checkpoint must be byte-stable");

        Stage20GeneratedWorldRuntimeBridge.LiveRuntime restoredRuntime = Stage20GeneratedWorldRuntimeBridge.restore(
                decoded.stage21DRuntime().stage21CRuntime().stage21BRuntime().stage21ARuntime().stage20Runtime());
        FleetForceRegistry restoredSupported = FleetForceRegistry.reconstruct(
                restoredRuntime.world().snapshot(), evaluator, supportedAvailability);
        assertEquals(
                arrived.find(empire.defenderFleetId()).orElseThrow().entityState().engineering(),
                restoredSupported.find(empire.defenderFleetId()).orElseThrow().entityState().engineering());
        assertEquals(
                arrived.find(empire.reserveFleetId()).orElseThrow().entityState().engineering(),
                restoredSupported.find(empire.reserveFleetId()).orElseThrow().entityState().engineering());
        assertEquals(
                arrived.find(union.defenderFleetId()).orElseThrow().entityState().engineering(),
                restoredSupported.find(union.defenderFleetId()).orElseThrow().entityState().engineering());
        assertEquals(
                arrived.find(union.reserveFleetId()).orElseThrow().entityState().engineering(),
                restoredSupported.find(union.reserveFleetId()).orElseThrow().entityState().engineering());

        long supportedReviewTick = checkpointTick + 1L;
        ReviewPair directSupported = reviewBoth(service, operations, arrived, supportedReviewTick);
        ReviewPair restoredSupportedReview = reviewBoth(
                service, decoded.operationState(), restoredSupported, supportedReviewTick);
        assertEquals(SupplyDecision.CONTINUE, directSupported.empireDecision());
        assertEquals(SupplyDecision.CONTINUE, directSupported.unionDecision());
        assertEquals(directSupported, restoredSupportedReview,
                "supported prepared-defense continuation must be save/load invariant");

        Map<FleetId, FleetOperationalAvailability> cutOffAvailability = availability(empire, union, 0);
        FleetForceRegistry directCutOffForces = FleetForceRegistry.reconstruct(
                runtime.world().snapshot(), evaluator, cutOffAvailability);
        FleetForceRegistry restoredCutOffForces = FleetForceRegistry.reconstruct(
                restoredRuntime.world().snapshot(), evaluator, cutOffAvailability);
        long cutOffReviewTick = supportedReviewTick + 1L;
        ReviewPair directCutOff = reviewBoth(
                service, directSupported.state(), directCutOffForces, cutOffReviewTick);
        ReviewPair restoredCutOff = reviewBoth(
                service, restoredSupportedReview.state(), restoredCutOffForces, cutOffReviewTick);
        assertEquals(SupplyDecision.SUBMIT_ORDINARY_WITHDRAW_ORDER, directCutOff.empireDecision());
        assertEquals(SupplyDecision.SUBMIT_ORDINARY_WITHDRAW_ORDER, directCutOff.unionDecision());
        assertEquals(directCutOff, restoredCutOff,
                "supply-loss withdrawal pressure must be identical after generated-world restore");
        assertEquals(StrategicOperationState.OperationStatus.WITHDRAWING,
                directCutOff.state().requireOperation(1L).status());
        assertEquals(StrategicOperationState.OperationStatus.WITHDRAWING,
                directCutOff.state().requireOperation(2L).status());

        LinkedHashMap<String, Object> archive = new LinkedHashMap<>();
        archive.put("checkpointBytes", encoded.length);
        archive.put("checkpointTick", checkpointTick);
        archive.put("empire", laneEvidence(empire, directSupported.empireDecision(), directCutOff.empireDecision()));
        archive.put("union", laneEvidence(union, directSupported.unionDecision(), directCutOff.unionDecision()));
        archive.put("reinforcementParticipants", 2);
        archive.put("byteStable", true);
        archive.put("restoredContinuationEqual", directSupported.equals(restoredSupportedReview));
        archive.put("restoredCutOffEqual", directCutOff.equals(restoredCutOff));
        Stage22CorePairEvidenceArchive.write(
                "B09-generated-world-prepared-defense-save-continuation",
                archive,
                "Generated-world defenders and reserves use ordinary persistent FleetIds and the ordinary fitted FTL FSM. Reinforcement is physically rejected before arrival, then admitted only after the reserve reaches the objective and receives the exact prepared core package. Full Stage-21E save/load preserves both supported continuation and supply-loss withdrawal decisions. This closes the B09 save-continuation seam only; multi-wave campaign endurance and wider recovery coupling remain separate requirements.");
    }

    private static ReviewPair reviewBoth(
            StrategicOperationService service,
            StrategicOperationState initial,
            FleetForceRegistry forces,
            long tick) {
        SupplyReview empire = service.reviewSupplyAndReadiness(initial, 1L, forces, tick);
        SupplyReview union = service.reviewSupplyAndReadiness(empire.state(), 2L, forces, tick);
        return new ReviewPair(empire.decision(), union.decision(), union.state());
    }

    private static Stage21EGeneratedWorldRuntimePersistentState checkpoint(
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
                        Stage21AGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                        stage20,
                        actors);
        List<FactionStrategicIntentState> intents = actors.stream()
                .map(actor -> FactionStrategicIntentState.initial(actor.factionContentId()))
                .toList();
        Stage21BGeneratedWorldRuntimePersistentState stage21B =
                new Stage21BGeneratedWorldRuntimePersistentState(
                        Stage21BGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                        Stage21BGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                        stage21A,
                        intents);
        Stage21CGeneratedWorldRuntimePersistentState stage21C =
                new Stage21CGeneratedWorldRuntimePersistentState(
                        Stage21CGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                        Stage21CGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                        stage21B,
                        DiplomaticLifecycleState.empty(tick),
                        Stage19ConflictState.empty(tick));
        Stage21DGeneratedWorldRuntimePersistentState stage21D =
                Stage21DGeneratedWorldRuntimePersistentState.compose(stage21C, commands);
        return Stage21EGeneratedWorldRuntimePersistentState.compose(stage21D, operations);
    }

    private static FleetCommandState commands(PreparedLane empire, PreparedLane union, long tick) {
        CommandGroupState empireGroup = group(1L, empire);
        CommandGroupState unionGroup = group(2L, union);
        FleetOrderState empireOrder = order(1L, empireGroup.id(), empire.objectiveSystemId(), tick);
        FleetOrderState unionOrder = order(2L, unionGroup.id(), union.objectiveSystemId(), tick);
        return new FleetCommandState(
                3L,
                3L,
                List.of(empireGroup, unionGroup),
                List.of(empireOrder, unionOrder));
    }

    private static CommandGroupState group(long id, PreparedLane lane) {
        return new CommandGroupState(
                id,
                lane.factionId(),
                "M22.6 prepared defense group " + id,
                List.of(lane.defenderFleetId()),
                lane.homeSystemId(),
                false,
                true,
                FleetReadinessState.FULL);
    }

    private static FleetOrderState order(long id, long groupId, StarSystemId objective, long tick) {
        return new FleetOrderState(
                id,
                groupId,
                OrderType.GUARD,
                OrderSource.AI,
                objective,
                List.of(objective),
                0,
                tick,
                tick + 10L,
                OrderStatus.ACTIVE);
    }

    private static Map<FleetId, FleetOperationalAvailability> availability(
            PreparedLane empire,
            PreparedLane union,
            int supplyAccessBps) {
        FleetOperationalAvailability observed = new FleetOperationalAvailability(
                CREW_AVAILABLE, supplyAccessBps);
        return Map.ofEntries(
                Map.entry(empire.defenderFleetId(), observed),
                Map.entry(empire.reserveFleetId(), observed),
                Map.entry(union.defenderFleetId(), observed),
                Map.entry(union.reserveFleetId(), observed));
    }

    private static List<DefenseSeed> defenseSeeds(Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        TreeMap<Integer, ArrayList<MilitaryFleet>> byFaction = new TreeMap<>();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) continue;
            Entity entity = entity(runtime, placement.id());
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (engineering == null || faction == null) continue;
            byFaction.computeIfAbsent(faction.factionId, ignored -> new ArrayList<>())
                    .add(new MilitaryFleet(placement.id(), placement.systemId()));
        }

        ArrayList<DefenseSeed> result = new ArrayList<>();
        for (var entry : byFaction.entrySet()) {
            List<MilitaryFleet> fleets = entry.getValue().stream()
                    .sorted(java.util.Comparator.comparing(MilitaryFleet::fleetId))
                    .toList();
            if (fleets.size() < 2) continue;
            MilitaryFleet defender = fleets.get(0);
            MilitaryFleet reserve = fleets.stream()
                    .filter(value -> value.systemId().equals(defender.systemId()))
                    .skip(1)
                    .findFirst()
                    .orElse(null);
            if (reserve == null || runtime.world().getTopology().neighbors(defender.systemId()).isEmpty()) continue;
            result.add(new DefenseSeed(
                    entry.getKey(), defender.fleetId(), reserve.fleetId(), defender.systemId()));
            if (result.size() == 2) break;
        }
        if (result.size() < 2) {
            throw new AssertionError("generated world lacks two factions with defender/reserve military pairs");
        }
        return List.copyOf(result);
    }

    private static PreparedLane moveDefenderOneOrdinaryHop(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            DefenseSeed seed) {
        StarSystemId objective = runtime.world().getTopology().neighbors(seed.homeSystemId()).stream()
                .sorted()
                .findFirst()
                .orElseThrow();
        moveOrdinaryHop(runtime, seed.defenderFleetId(), objective);
        FleetPlacementState placement = runtime.world().findFleet(seed.defenderFleetId()).orElseThrow();
        assertEquals(FleetLocationKind.IN_SYSTEM, placement.locationKind());
        assertEquals(objective, placement.systemId());
        FleetPlacementState reserve = runtime.world().findFleet(seed.reserveFleetId()).orElseThrow();
        assertEquals(seed.homeSystemId(), reserve.systemId(),
                "reserve must remain physically absent while defender establishes prepared position");
        return new PreparedLane(
                seed.factionId(),
                seed.defenderFleetId(),
                seed.reserveFleetId(),
                seed.homeSystemId(),
                objective);
    }

    private static void moveOrdinaryHop(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId,
            StarSystemId destination) {
        GeneratedWorldFtlTestSupport.placeAtOutgoingEndpoint(runtime, fleetId, destination);
        runtime.world().requestFleetJump(fleetId, destination);
        GeneratedWorldFtlTestSupport.advanceOrdinaryJumpToCompletion(runtime, fleetId);
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM || !destination.equals(placement.systemId())) {
            throw new AssertionError("ordinary generated-world hop did not reach its objective: " + fleetId);
        }
    }

    private static Entity entity(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetId fleetId) {
        FleetPlacementState placement = runtime.world().findFleet(fleetId).orElseThrow();
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            throw new IllegalStateException("prepared-defense entity lookup requires local FleetId");
        }
        return runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
    }

    private static EngineeringComponent engineering(
            Stage22CorePairTacticalFactory.Duel duel,
            long entityId) {
        return duel.weapons().battleState().combatants().stream()
                .filter(value -> value.spec().entityId() == entityId)
                .findFirst()
                .orElseThrow()
                .engineering();
    }

    private static EngineeringComponent preparedDefenseLoadout(
            EngineeringComponent source,
            ShipEngineeringCatalog catalog) {
        ConsumableState current = source.runtimeState.consumables();
        ArrayList<ConsumableLoad> loads = new ArrayList<>(current.interfaceLoads().size());
        for (ConsumableLoad load : current.interfaceLoads()) {
            if (load.kind() != InterfaceKind.AMMUNITION) {
                loads.add(load);
                continue;
            }
            if (load.itemCount() <= 0L || !(load.amount() > 0d) || !(load.massKg() > 0d)) {
                throw new AssertionError("prepared core ammunition seed must be itemized and physical: " + load);
            }
            var installed = source.fit.installedModules().stream()
                    .filter(value -> value.mountId().equals(load.mountId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "prepared ammunition mount is not installed: " + load.mountId()));
            var module = catalog.findModule(installed.moduleId());
            if (module == null) {
                throw new AssertionError("prepared ammunition module is absent from exact core catalog: "
                        + installed.moduleId());
            }
            var physicalInterface = module.interfaces().stream()
                    .filter(value -> value.kind() == InterfaceKind.AMMUNITION
                            && value.id().equals(load.interfaceId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("prepared ammunition interface is absent: " + load));

            double amountPerRound = load.amount() / load.itemCount();
            double massPerRoundKg = load.massKg() / load.itemCount();
            double minimumAmount = physicalInterface.capacity()
                    * MINIMUM_MISSION_READINESS_BPS / FleetReadinessState.FULL;
            long preparedRounds = Math.max(
                    load.itemCount(),
                    (long) Math.ceil(minimumAmount / amountPerRound));
            double preparedAmount = preparedRounds * amountPerRound;
            if (preparedAmount > physicalInterface.capacity() + 1e-9d) {
                throw new AssertionError("prepared ammunition exceeds authored interface capacity: " + load.mountId());
            }
            loads.add(new ConsumableLoad(
                    load.mountId(),
                    load.interfaceId(),
                    load.kind(),
                    preparedAmount,
                    preparedRounds * massPerRoundKg,
                    preparedRounds));
        }

        ConsumableState prepared = new ConsumableState(
                current.cargoMassKg(),
                current.storesMassKg(),
                current.missionPayloadMassKg(),
                current.missionIntegrationVolumeM3(),
                loads);
        RuntimeState runtime = source.runtimeState;
        RuntimeState preparedRuntime = new RuntimeState(
                prepared,
                runtime.sharedBusEnergyJ(),
                runtime.shipHeatStoredJ(),
                runtime.localHeatJByMount(),
                runtime.thrustLimitNByMount(),
                runtime.coolantBusCapacityW(),
                runtime.ftlCooldownSecondsByMount());
        return new EngineeringComponent(source.fit, preparedRuntime, source.instanceState);
    }

    private static LaneEvidence laneEvidence(
            PreparedLane lane,
            SupplyDecision supported,
            SupplyDecision cutOff) {
        return new LaneEvidence(
                lane.factionId(),
                lane.defenderFleetId().value(),
                lane.reserveFleetId().value(),
                lane.homeSystemId().value(),
                lane.objectiveSystemId().value(),
                supported,
                cutOff);
    }

    private record MilitaryFleet(FleetId fleetId, StarSystemId systemId) { }

    private record DefenseSeed(
            int factionId,
            FleetId defenderFleetId,
            FleetId reserveFleetId,
            StarSystemId homeSystemId) { }

    private record PreparedLane(
            int factionId,
            FleetId defenderFleetId,
            FleetId reserveFleetId,
            StarSystemId homeSystemId,
            StarSystemId objectiveSystemId) { }

    private record ReviewPair(
            SupplyDecision empireDecision,
            SupplyDecision unionDecision,
            StrategicOperationState state) { }

    private record LaneEvidence(
            int factionId,
            long defenderFleetId,
            long reserveFleetId,
            long homeSystemId,
            long objectiveSystemId,
            SupplyDecision supportedDecision,
            SupplyDecision cutOffDecision) { }
}
