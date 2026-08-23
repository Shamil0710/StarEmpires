package com.spacesim.world;

import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.FleetOrderSubmissionService.ServiceCapability;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetOrderSubmissionServiceTest {
    private static final StarSystemId ALPHA = new StarSystemId(1L);
    private static final StarSystemId BETA = new StarSystemId(2L);
    private static final StarSystemId GAMMA = new StarSystemId(3L);
    private static final FleetId FLEET = new FleetId(101L);
    private static final int FACTION = 7;

    private static final FleetStrategicRoutePlanner.TransitAccessPolicy ACCESS =
            (factionId, from, to, tick, destination) -> true;
    private static final FleetOrderSubmissionService.ServiceCapabilityPolicy SERVICES =
            (factionId, systemId, tick) -> new ServiceCapability(true, true, true, 3L, 7L);
    private static final FleetOrderSubmissionService.StrategicRiskPolicy LOW_RISK =
            (factionId, type, route, tick) -> 1_000;

    @Test
    void everyOrderFamilyUsesTheSameValidatedSubmissionBoundary() {
        FleetOrderSubmissionService service = service();
        for (OrderType type : OrderType.values()) {
            FleetCommandState state = state(group(false, false, FleetReadinessState.FULL));
            FleetOrderSubmissionService.SubmissionResult result = service.submit(
                    state,
                    forces(fullReadiness(), ALPHA),
                    1L,
                    type,
                    type.ordinal() % 2 == 0 ? OrderSource.PLAYER : OrderSource.AI,
                    BETA,
                    100L,
                    ACCESS,
                    SERVICES,
                    LOW_RISK);

            assertEquals(type, result.order().type());
            assertEquals(List.of(ALPHA, BETA), result.order().route());
            assertEquals(110L, result.order().stagingDeadlineTick(),
                    "one physical hop plus handling time must define the staging deadline");
            assertEquals(result.order(), result.state().requireOrder(result.order().id()));
        }
    }

    @Test
    void playerAndAiReceiveEquivalentLawfulRoutingAndFeasibilityChecks() {
        FleetOrderSubmissionService service = service();
        FleetCommandState state = state(group(false, false, FleetReadinessState.FULL));

        var player = service.submit(state, forces(fullReadiness(), ALPHA), 1L, OrderType.REINFORCE,
                OrderSource.PLAYER, GAMMA, 100L, ACCESS, SERVICES, LOW_RISK).order();
        var ai = service.submit(state, forces(fullReadiness(), ALPHA), 1L, OrderType.REINFORCE,
                OrderSource.AI, GAMMA, 100L, ACCESS, SERVICES, LOW_RISK).order();

        assertEquals(player.type(), ai.type());
        assertEquals(player.targetSystemId(), ai.targetSystemId());
        assertEquals(player.route(), ai.route());
        assertEquals(List.of(ALPHA, BETA, GAMMA), player.route());
        assertEquals(117L, player.stagingDeadlineTick());
        assertEquals(player.stagingDeadlineTick(), ai.stagingDeadlineTick());
    }

    @Test
    void infeasibleFuelAmmunitionAccessAndServiceRequestsFailClosed() {
        FleetOrderSubmissionService service = service();
        FleetCommandState state = state(group(false, false, FleetReadinessState.FULL));

        FleetReadinessState noPropellant = readiness(10_000, 10_000, 0, 10_000, 10_000, 10_000, 10_000);
        assertThrows(IllegalStateException.class,
                () -> service.submit(state, forces(noPropellant, ALPHA), 1L, OrderType.STAGE,
                        OrderSource.AI, BETA, 10L, ACCESS, SERVICES, LOW_RISK));

        FleetReadinessState noAmmunition = readiness(10_000, 0, 10_000, 10_000, 10_000, 10_000, 10_000);
        assertThrows(IllegalStateException.class,
                () -> service.submit(state, forces(noAmmunition, ALPHA), 1L, OrderType.INTERCEPT,
                        OrderSource.AI, BETA, 10L, ACCESS, SERVICES, LOW_RISK));

        FleetStrategicRoutePlanner.TransitAccessPolicy denied =
                (factionId, from, to, tick, destination) -> false;
        assertThrows(IllegalStateException.class,
                () -> service.submit(state, forces(fullReadiness(), ALPHA), 1L, OrderType.PATROL,
                        OrderSource.PLAYER, BETA, 10L, denied, SERVICES, LOW_RISK));

        FleetReadinessState noSupplyAccess = readiness(10_000, 10_000, 10_000, 10_000, 10_000, 10_000, 0);
        assertThrows(IllegalStateException.class,
                () -> service.submit(state, forces(noSupplyAccess, ALPHA), 1L, OrderType.REPAIR,
                        OrderSource.PLAYER, ALPHA, 10L, ACCESS, SERVICES, LOW_RISK));

        var noRepair = (FleetOrderSubmissionService.ServiceCapabilityPolicy)
                (factionId, systemId, tick) -> new ServiceCapability(true, true, false, 0L, 1L);
        assertThrows(IllegalStateException.class,
                () -> service.submit(state, forces(fullReadiness(), ALPHA), 1L, OrderType.REPAIR,
                        OrderSource.PLAYER, ALPHA, 10L, ACCESS, noRepair, LOW_RISK));
    }

    @Test
    void reserveHomeDefenseAndRiskConstraintsRejectUnlawfulCommitments() {
        FleetOrderSubmissionService service = service();
        FleetCommandState reserveState = state(group(true, false, FleetReadinessState.FULL));
        assertThrows(IllegalStateException.class,
                () -> service.submit(reserveState, forces(fullReadiness(), ALPHA), 1L, OrderType.RAID,
                        OrderSource.AI, BETA, 10L, ACCESS, SERVICES, LOW_RISK));

        FleetCommandState homeDefenseState = state(group(false, true, FleetReadinessState.FULL));
        assertThrows(IllegalStateException.class,
                () -> service.submit(homeDefenseState, forces(fullReadiness(), ALPHA), 1L, OrderType.INVADE,
                        OrderSource.PLAYER, BETA, 10L, ACCESS, SERVICES, LOW_RISK));

        FleetCommandState cautious = state(group(false, false, 2_000));
        FleetOrderSubmissionService.StrategicRiskPolicy tooRisky =
                (factionId, type, route, tick) -> 2_001;
        assertThrows(IllegalStateException.class,
                () -> service.submit(cautious, forces(fullReadiness(), ALPHA), 1L, OrderType.PATROL,
                        OrderSource.AI, BETA, 10L, ACCESS, SERVICES, tooRisky));

        FleetOrderSubmissionService.StrategicRiskPolicy corruptRisk =
                (factionId, type, route, tick) -> 10_001;
        assertThrows(IllegalStateException.class,
                () -> service.submit(cautious, forces(fullReadiness(), ALPHA), 1L, OrderType.PATROL,
                        OrderSource.AI, BETA, 10L, ACCESS, SERVICES, corruptRisk));
    }

    @Test
    void secondActiveOrderForTheSameCommandGroupIsRejected() {
        FleetOrderSubmissionService service = service();
        FleetCommandState initial = state(group(false, false, FleetReadinessState.FULL));
        FleetCommandState withActive = service.submit(initial, forces(fullReadiness(), ALPHA), 1L,
                OrderType.GUARD, OrderSource.PLAYER, BETA, 10L, ACCESS, SERVICES, LOW_RISK).state();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> service.submit(withActive, forces(fullReadiness(), ALPHA), 1L,
                        OrderType.PATROL, OrderSource.AI, BETA, 11L, ACCESS, SERVICES, LOW_RISK));
        assertTrue(failure.getMessage().contains("active order"));
    }

    private static FleetOrderSubmissionService service() {
        return new FleetOrderSubmissionService(new FleetStrategicRoutePlanner(topology()));
    }

    private static FleetCommandState state(CommandGroupState group) {
        return new FleetCommandState(2L, 1L, List.of(group), List.of());
    }

    private static CommandGroupState group(boolean reserve, boolean homeDefense, int maxRiskBps) {
        return new CommandGroupState(1L, FACTION, "Test Group", List.of(FLEET), ALPHA,
                reserve, homeDefense, maxRiskBps);
    }

    private static FleetForceRegistry forces(FleetReadinessState readiness, StarSystemId systemId) {
        return new FleetForceRegistry(List.of(new FleetForceRegistry.Entry(
                FLEET,
                FACTION,
                FleetLocationKind.IN_SYSTEM,
                systemId,
                null,
                null,
                dummyEntity(FLEET.value()),
                readiness)));
    }

    private static EntityState dummyEntity(long id) {
        return new EntityState(new EntityId(id), null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    private static FleetReadinessState fullReadiness() {
        return readiness(10_000, 10_000, 10_000, 10_000, 10_000, 10_000, 10_000);
    }

    private static FleetReadinessState readiness(
            int structural,
            int ammunition,
            int propellant,
            int crew,
            int sensors,
            int maintenance,
            int supply) {
        return new FleetReadinessState(structural, ammunition, propellant, crew, sensors, maintenance, supply);
    }

    private static GalaxyTopology topology() {
        StarSystemNode alpha = new StarSystemNode(ALPHA, "Alpha", 0d, 0d);
        StarSystemNode beta = new StarSystemNode(BETA, "Beta", 100d, 0d);
        StarSystemNode gamma = new StarSystemNode(GAMMA, "Gamma", 200d, 0d);
        return new GalaxyTopology(
                new GalaxyId(21L),
                "Stage 21D Test Galaxy",
                List.of(new SectorNode(new SectorId(1L), "Core", List.of(alpha, beta, gamma))),
                List.of(new JumpConnection(ALPHA, BETA), new JumpConnection(BETA, GAMMA)));
    }
}
