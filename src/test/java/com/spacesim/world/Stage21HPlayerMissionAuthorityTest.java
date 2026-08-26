package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.Stage20FreightPersistentState;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.player.PlayerState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryEvidence;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoverySource;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledge;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticKnowledge;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef;
import com.spacesim.world.Stage21HNpcMissionState.MissionObjective;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Stage21HPlayerMissionAuthorityTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";

    @Test
    void freightCompletionRequiresPlayerOwnershipOfAssignedPhysicalFleet() {
        var runtime = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        var saved = runtime.captureState();
        Stage20FreightPersistentState freight = saved.freight();
        TransportOrderState order = freight.orders().get(0);
        MissionObjective objective = new MissionObjective(
                ObjectiveAuthority.FREIGHT,
                ObjectiveKind.FREIGHT_ORDER_DELIVERED_KG_AT_LEAST,
                order.orderId(), 0L, 1L, "");

        assertEquals(Stage21HPlayerMissionAuthority.Result.PARTICIPATED,
                Stage21HPlayerMissionAuthority.evaluate(
                        runtime.world(), freight, null, StrategicOperationState.empty(),
                        player(List.of(order.fleetId()), List.of(), null), objective).result());
        assertEquals(Stage21HPlayerMissionAuthority.Result.NOT_PROVEN,
                Stage21HPlayerMissionAuthority.evaluate(
                        runtime.world(), freight, null, StrategicOperationState.empty(),
                        player(List.of(), List.of(), null), objective).result());
    }

    @Test
    void escortCompletionRequiresContractedEscortFleetToBePlayerOwned() {
        WorldSimulation world = DemoGalaxyFactory.create(21_851L);
        StarSystemId system = DemoGalaxyFactory.ACTIVE_SYSTEM_ID;
        int faction = world.findFactionRuntimeId(TRADE_LEAGUE).orElseThrow();
        FleetId convoy = createFleet(world, system, faction, "Convoy");
        FleetId escort = createFleet(world, system, faction, "Escort");
        MissionObjective objective = new MissionObjective(
                ObjectiveAuthority.FLEET,
                ObjectiveKind.ESCORT_FLEETS_PRESENT_IN_SYSTEM,
                Long.toString(convoy.value()), system.value(), 0L, Long.toString(escort.value()));

        assertEquals(Stage21HPlayerMissionAuthority.Result.PARTICIPATED,
                Stage21HPlayerMissionAuthority.evaluate(
                        world, null, null, StrategicOperationState.empty(),
                        player(List.of(escort), List.of(), null), objective).result());
        assertEquals(Stage21HPlayerMissionAuthority.Result.NOT_PROVEN,
                Stage21HPlayerMissionAuthority.evaluate(
                        world, null, null, StrategicOperationState.empty(),
                        player(List.of(convoy), List.of(), null), objective).result());
    }

    @Test
    void discoveryCompletionRequiresPlayerOwnedStage20Knowledge() {
        WorldSimulation world = DemoGalaxyFactory.create(21_852L);
        StarSystemId system = DemoGalaxyFactory.ACTIVE_SYSTEM_ID;
        String objectId = "special.player.recon";
        MissionObjective objective = new MissionObjective(
                ObjectiveAuthority.DISCOVERY,
                ObjectiveKind.DISCOVERY_AT_LEAST,
                objectId,
                system.value(),
                0L,
                "SPECIAL_LOCATION:KNOWN_STATIC_LOCATION");

        Stage20DiscoveryKnowledgeState playerKnowledge = discovery(
                Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID, system, objectId);
        Stage20DiscoveryKnowledgeState factionKnowledge = discovery(TRADE_LEAGUE, system, objectId);

        assertEquals(Stage21HPlayerMissionAuthority.Result.PARTICIPATED,
                Stage21HPlayerMissionAuthority.evaluate(
                        world, null, playerKnowledge, StrategicOperationState.empty(),
                        player(List.of(), List.of(), null), objective).result());
        assertEquals(Stage21HPlayerMissionAuthority.Result.NOT_PROVEN,
                Stage21HPlayerMissionAuthority.evaluate(
                        world, null, factionKnowledge, StrategicOperationState.empty(),
                        player(List.of(), List.of(), null), objective).result());
    }

    @Test
    void constructionCompletionAcceptsOnlyPlayerOwnedProjectOrPhysicalPresence() {
        WorldSimulation world = DemoGalaxyFactory.create(21_853L);
        ConstructionProjectId projectId = ConstructionProjectTestFixtures.createAuthorizedProject(
                world, MINERS, "station.mining_base", DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 320f, 180f);
        MissionObjective objective = new MissionObjective(
                ObjectiveAuthority.CONSTRUCTION,
                ObjectiveKind.CONSTRUCTION_DELIVERED_UNITS_AT_LEAST,
                Long.toString(projectId.value()), 0L, 1L, "");

        assertEquals(Stage21HPlayerMissionAuthority.Result.PARTICIPATED,
                Stage21HPlayerMissionAuthority.evaluate(
                        world, null, null, StrategicOperationState.empty(),
                        player(List.of(), List.of(projectId), null), objective).result());
        assertEquals(Stage21HPlayerMissionAuthority.Result.NOT_PROVEN,
                Stage21HPlayerMissionAuthority.evaluate(
                        world, null, null, StrategicOperationState.empty(),
                        player(List.of(), List.of(), null), objective).result());
    }

    @Test
    void operationCompletionRequiresPlayerOwnedParticipantAndAbsenceCausationFailsClosed() {
        WorldSimulation world = DemoGalaxyFactory.create(21_854L);
        StarSystemId system = DemoGalaxyFactory.ACTIVE_SYSTEM_ID;
        int faction = world.findFactionRuntimeId(TRADE_LEAGUE).orElseThrow();
        FleetId participant = createFleet(world, system, faction, "Operation participant");
        long tick = world.getAuthoritativeWorldTick();
        OperationState completed = new OperationState(
                1L,
                OperationType.DEFENSE,
                1L,
                1L,
                faction,
                List.of(participant),
                system,
                system,
                "system:" + system.value(),
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(0, 0, 0L),
                new WithdrawalPolicy(system, 0, true, true),
                OperationStatus.COMPLETED,
                tick,
                tick,
                -1L,
                null,
                null);
        StrategicOperationState operations = new StrategicOperationState(2L, List.of(completed));
        MissionObjective operationObjective = new MissionObjective(
                ObjectiveAuthority.OPERATION,
                ObjectiveKind.OPERATION_STATUS,
                "1", 0L, 0L, OperationStatus.COMPLETED.name());

        assertEquals(Stage21HPlayerMissionAuthority.Result.PARTICIPATED,
                Stage21HPlayerMissionAuthority.evaluate(
                        world, null, null, operations,
                        player(List.of(participant), List.of(), null), operationObjective).result());
        assertEquals(Stage21HPlayerMissionAuthority.Result.NOT_PROVEN,
                Stage21HPlayerMissionAuthority.evaluate(
                        world, null, null, operations,
                        player(List.of(), List.of(), null), operationObjective).result());

        MissionObjective absence = new MissionObjective(
                ObjectiveAuthority.FLEET,
                ObjectiveKind.FLEET_ABSENT,
                Long.toString(participant.value()), 0L, 0L, "");
        assertEquals(Stage21HPlayerMissionAuthority.Result.NOT_PROVEN,
                Stage21HPlayerMissionAuthority.evaluate(
                        world, null, null, operations,
                        player(List.of(participant), List.of(), null), absence).result());
    }

    private static FleetId createFleet(
            WorldSimulation world,
            StarSystemId system,
            int faction,
            String name) {
        Entity entity = new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.FLEET))
                .add(new FactionComponent(faction));
        EntityId localId = world.createEntity(system, entity);
        return world.findFleetByLocal(system, localId).orElseThrow();
    }

    private static Stage20DiscoveryKnowledgeState discovery(
            String owner,
            StarSystemId system,
            String objectId) {
        StaticObjectRef ref = new StaticObjectRef(system, StaticObjectKind.SPECIAL_LOCATION, objectId);
        DiscoveryEvidence evidence = new DiscoveryEvidence(
                DiscoverySource.PHYSICAL_VISIT_OR_SURVEY,
                "survey.player." + objectId,
                1d,
                OptionalDouble.empty());
        StaticKnowledge knowledge = new StaticKnowledge(
                ref,
                DiscoveryState.KNOWN_STATIC_LOCATION,
                Optional.of("special.derelict"),
                Optional.of(LocalPhysicalPosition.origin()),
                ResourceKnowledge.none(),
                List.of(evidence),
                1d,
                1d);
        return new Stage20DiscoveryKnowledgeState(owner, List.of(knowledge));
    }

    private static PlayerState player(
            List<FleetId> fleets,
            List<ConstructionProjectId> projects,
            String faction) {
        return new PlayerState(
                0L,
                faction,
                List.of(),
                fleets,
                fleets.isEmpty() ? null : fleets.get(0),
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                projects,
                List.of());
    }
}
