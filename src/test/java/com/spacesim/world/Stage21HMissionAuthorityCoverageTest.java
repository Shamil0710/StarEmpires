package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.world.Stage21HMissionAuthority.Result;
import com.spacesim.world.Stage21HNpcMissionState.MissionObjective;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21HMissionAuthorityCoverageTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";

    @Test
    void escortRequiresDistinctPhysicalFleetsAndFailsWhenEscortDisappears() {
        WorldSimulation world = DemoGalaxyFactory.create(21_801L);
        StarSystemId system = DemoGalaxyFactory.ACTIVE_SYSTEM_ID;
        int runtimeFaction = world.findFactionRuntimeId(TRADE_LEAGUE).orElseThrow();
        EntityId convoyLocal = world.createEntity(system, fleetEntity("Convoy", runtimeFaction));
        EntityId escortLocal = world.createEntity(system, fleetEntity("Escort", runtimeFaction));
        FleetId convoy = world.findFleetByLocal(system, convoyLocal).orElseThrow();
        FleetId escort = world.findFleetByLocal(system, escortLocal).orElseThrow();
        MissionObjective objective = new MissionObjective(
                ObjectiveAuthority.FLEET,
                ObjectiveKind.ESCORT_FLEETS_PRESENT_IN_SYSTEM,
                Long.toString(convoy.value()),
                system.value(),
                0L,
                Long.toString(escort.value()));

        Stage21HMissionAuthority.requireIssuerAuthority(
                world, null, null, null, StrategicOperationState.empty(), TRADE_LEAGUE, objective);
        assertEquals(Result.SATISFIED, Stage21HMissionAuthority.evaluate(
                world, null, null, null, StrategicOperationState.empty(), objective).result());

        assertTrue(world.removeEntity(system, escortLocal));
        assertEquals(Result.FAILED, Stage21HMissionAuthority.evaluate(
                world, null, null, null, StrategicOperationState.empty(), objective).result());

        assertThrows(IllegalArgumentException.class, () -> new MissionObjective(
                ObjectiveAuthority.FLEET,
                ObjectiveKind.ESCORT_FLEETS_PRESENT_IN_SYSTEM,
                Long.toString(convoy.value()),
                system.value(),
                0L,
                Long.toString(convoy.value())));
    }

    @Test
    void rescueRefuelReadsReactionMassFromOrdinaryEngineeringState() {
        WorldSimulation world = DemoGalaxyFactory.create(21_802L);
        StarSystemId system = DemoGalaxyFactory.ACTIVE_SYSTEM_ID;
        int runtimeFaction = world.findFactionRuntimeId(TRADE_LEAGUE).orElseThrow();
        FleetId dry = engineeringFleet(world, system, runtimeFaction, 0d);
        FleetId fueled = engineeringFleet(world, system, runtimeFaction, 80_000d);
        MissionObjective dryObjective = new MissionObjective(
                ObjectiveAuthority.FLEET,
                ObjectiveKind.FLEET_REACTION_MASS_KG_AT_LEAST,
                Long.toString(dry.value()), 0L, 50_000L, "");
        MissionObjective fueledObjective = new MissionObjective(
                ObjectiveAuthority.FLEET,
                ObjectiveKind.FLEET_REACTION_MASS_KG_AT_LEAST,
                Long.toString(fueled.value()), 0L, 50_000L, "");

        Stage21HMissionAuthority.requireIssuerAuthority(
                world, null, null, null, StrategicOperationState.empty(), TRADE_LEAGUE, dryObjective);
        assertEquals(Result.PENDING, Stage21HMissionAuthority.evaluate(
                world, null, null, null, StrategicOperationState.empty(), dryObjective).result());
        assertEquals(Result.SATISFIED, Stage21HMissionAuthority.evaluate(
                world, null, null, null, StrategicOperationState.empty(), fueledObjective).result());
        assertThrows(IllegalStateException.class, () -> Stage21HMissionAuthority.requireIssuerAuthority(
                world, null, null, null, StrategicOperationState.empty(), MINERS, fueledObjective));
    }

    @Test
    void defenseMissionTracksOwnedStage21EOperationToTerminalCompletion() {
        WorldSimulation world = DemoGalaxyFactory.create(21_803L);
        StarSystemId system = DemoGalaxyFactory.ACTIVE_SYSTEM_ID;
        int runtimeFaction = world.findFactionRuntimeId(TRADE_LEAGUE).orElseThrow();
        EntityId participantLocal = world.createEntity(system, fleetEntity("Defense participant", runtimeFaction));
        FleetId participant = world.findFleetByLocal(system, participantLocal).orElseThrow();
        long tick = world.getAuthoritativeWorldTick();
        OperationState active = new OperationState(
                1L,
                OperationType.DEFENSE,
                1L,
                1L,
                runtimeFaction,
                List.of(participant),
                system,
                system,
                "system:" + system.value(),
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(0, 0, 0L),
                new WithdrawalPolicy(system, 0, true, true),
                OperationStatus.ACTIVE,
                tick,
                tick,
                -1L,
                null,
                null);
        StrategicOperationState operations = new StrategicOperationState(2L, List.of(active));
        MissionObjective objective = new MissionObjective(
                ObjectiveAuthority.OPERATION,
                ObjectiveKind.OPERATION_STATUS,
                "1", 0L, 0L, OperationStatus.COMPLETED.name());

        Stage21HMissionAuthority.requireIssuerAuthority(
                world, null, null, null, operations, TRADE_LEAGUE, objective);
        assertEquals(Result.PENDING, Stage21HMissionAuthority.evaluate(
                world, null, null, null, operations, objective).result());

        StrategicOperationState completed = operations.replace(
                active.withLifecycle(OperationStatus.COMPLETED, tick, -1L, null, null));
        assertEquals(Result.SATISFIED, Stage21HMissionAuthority.evaluate(
                world, null, null, null, completed, objective).result());
        assertThrows(IllegalStateException.class, () -> Stage21HMissionAuthority.requireIssuerAuthority(
                world, null, null, null, operations, MINERS, objective));
    }

    @Test
    void constructionMissionUsesOrdinaryProjectOwnershipAndTerminalFailure() {
        WorldSimulation world = DemoGalaxyFactory.create(21_804L);
        ConstructionProjectId projectId = ConstructionProjectTestFixtures.createAuthorizedProject(
                world,
                MINERS,
                "station.mining_base",
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                320f,
                180f);
        MissionObjective objective = new MissionObjective(
                ObjectiveAuthority.CONSTRUCTION,
                ObjectiveKind.CONSTRUCTION_DELIVERED_UNITS_AT_LEAST,
                Long.toString(projectId.value()), 0L, 1L, "");

        Stage21HMissionAuthority.requireIssuerAuthority(
                world, null, null, null, StrategicOperationState.empty(), MINERS, objective);
        assertEquals(Result.PENDING, Stage21HMissionAuthority.evaluate(
                world, null, null, null, StrategicOperationState.empty(), objective).result());
        assertThrows(IllegalStateException.class, () -> Stage21HMissionAuthority.requireIssuerAuthority(
                world, null, null, null, StrategicOperationState.empty(), TRADE_LEAGUE, objective));

        assertTrue(world.cancelConstructionProject(projectId));
        assertEquals(Result.FAILED, Stage21HMissionAuthority.evaluate(
                world, null, null, null, StrategicOperationState.empty(), objective).result());
    }

    private static Entity fleetEntity(String name, int runtimeFaction) {
        return new Entity()
                .add(new IdentityComponent(name, IdentityComponent.Kind.FLEET))
                .add(new FactionComponent(runtimeFaction));
    }

    private static FleetId engineeringFleet(
            WorldSimulation world,
            StarSystemId system,
            int runtimeFaction,
            double reactionMassKg) {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        InstalledFit fit = InstalledFit.fromDemonstrator(
                catalog.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
        List<ConsumableLoad> loads = reactionMassKg <= 0d
                ? List.of()
                : List.of(new ConsumableLoad(
                        "core_drive",
                        "propellant_feed",
                        InterfaceKind.REACTION_MASS,
                        reactionMassKg,
                        reactionMassKg,
                        0L));
        ConsumableState consumables = new ConsumableState(0d, 0d, 0d, 0d, loads);
        ShipEngineeringRuntime.RuntimeState runtime = new ShipEngineeringRuntime(catalog)
                .initialize(fit, consumables, DamageState.pristine());
        Entity entity = fleetEntity("Engineering fleet", runtimeFaction)
                .add(new EngineeringComponent(fit, runtime));
        EntityId localId = world.createEntity(system, entity);
        return world.findFleetByLocal(system, localId).orElseThrow();
    }
}
