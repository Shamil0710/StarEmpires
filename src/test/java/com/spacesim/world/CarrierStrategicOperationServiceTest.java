package com.spacesim.world;

import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.world.CarrierWingStrategicReadinessService.CarrierWingAssignment;
import com.spacesim.world.FleetCommandState.CommandGroupState;
import com.spacesim.world.FleetCommandState.FleetOrderState;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.FleetCommandState.OrderStatus;
import com.spacesim.world.FleetCommandState.OrderType;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CarrierStrategicOperationServiceTest {
    private static final FleetId CARRIER = new FleetId(100L);
    private static final FleetId ESCORT = new FleetId(101L);
    private static final StarSystemId SYSTEM = new StarSystemId(7L);
    private static final String HOST = "carrier.alpha";

    @Test
    void wingProjectionRemainsDerivedFromIndividualCraftAndPermanentLoss() {
        Fixture fixture = fixture();
        var projection = fixture.readiness.project(
                fixture.forces,
                fixture.registry,
                fixture.hangars,
                SmallCraftMissionState.empty(),
                fixture.identities,
                List.of(fixture.assignment));

        var wing = projection.wing(CARRIER).orElseThrow();
        assertEquals(2, wing.readyOrActiveCraft());
        assertEquals(0, wing.lostCraft());
        assertEquals(10_000, wing.availabilityBps());
        assertEquals(7_900, wing.wingReadiness().structuralBps());
        assertEquals(7_900, wing.projectedCarrierReadiness().overallBps());
        assertEquals(fixture.carrierEntity, projection.forces().find(CARRIER).orElseThrow().entityState());
        assertEquals(List.of(fixture.alpha, fixture.beta), wing.craftIds());

        fixture.registry.removeDestroyedCraft(fixture.beta);
        var afterLoss = fixture.readiness.project(
                fixture.forces,
                fixture.registry,
                fixture.hangars,
                SmallCraftMissionState.empty(),
                fixture.identities,
                List.of(fixture.assignment));

        var degraded = afterLoss.wing(CARRIER).orElseThrow();
        assertEquals(1, degraded.readyOrActiveCraft());
        assertEquals(1, degraded.lostCraft());
        assertEquals(5_000, degraded.availabilityBps());
        assertTrue(degraded.projectedCarrierReadiness().overallBps() < 5_000);
        assertTrue(fixture.registry.find(fixture.beta).isEmpty(),
                "strategic projection must not recreate a physically destroyed craft");
        assertEquals(3L, fixture.registry.nextIdValue(),
                "destroyed identity must not rewind the individual-craft allocator");
    }

    @Test
    void servicingCraftReducesStrategicAvailabilityWithoutChangingPhysicalState() {
        Fixture fixture = fixture();
        fixture.hangars.transition(fixture.beta, OccupancyState.SERVICING);
        var before = fixture.registry.snapshot();

        var projected = fixture.readiness.project(
                fixture.forces,
                fixture.registry,
                fixture.hangars,
                SmallCraftMissionState.empty(),
                fixture.identities,
                List.of(fixture.assignment));

        assertEquals(5_000, projected.wing(CARRIER).orElseThrow().availabilityBps());
        assertEquals(before, fixture.registry.snapshot());
        assertEquals(OccupancyState.SERVICING, fixture.hangars.find(fixture.beta).orElseThrow().state());
    }

    @Test
    void escortInterceptRaidAndDefenceUseOrdinaryStage21OperationAuthority() {
        for (OrderType orderType : List.of(
                OrderType.ESCORT,
                OrderType.INTERCEPT,
                OrderType.RAID,
                OrderType.GUARD)) {
            Fixture fixture = fixture();
            var projected = fixture.readiness.project(
                    fixture.forces,
                    fixture.registry,
                    fixture.hangars,
                    SmallCraftMissionState.empty(),
                    fixture.identities,
                    List.of(fixture.assignment));
            FleetCommandState commands = commands(orderType);
            SupplyPolicy supply = new SupplyPolicy(5_000, 0, 100L);

            StrategicOperationState state = new CarrierStrategicOperationService().beginCarrierOperation(
                    StrategicOperationState.empty(),
                    commands,
                    projected,
                    1L,
                    CARRIER,
                    50L,
                    RulesOfEngagement.IDENTIFIED_HOSTILES,
                    supply,
                    new WithdrawalPolicy(SYSTEM, 4_000, true, true));

            var operation = state.requireOperation(1L);
            assertEquals(expectedOperationType(orderType), operation.type());
            assertEquals(List.of(CARRIER, ESCORT), operation.participantFleetIds());
            assertEquals(1L, operation.commandGroupId());
            assertEquals(1L, operation.sourceOrderId());
        }
    }

    @Test
    void degradedWingFailsCarrierAdmissionAndUnsupportedOrderCannotBypassStage21() {
        Fixture fixture = fixture();
        fixture.registry.removeDestroyedCraft(fixture.beta);
        var projected = fixture.readiness.project(
                fixture.forces,
                fixture.registry,
                fixture.hangars,
                SmallCraftMissionState.empty(),
                fixture.identities,
                List.of(fixture.assignment));
        CarrierStrategicOperationService service = new CarrierStrategicOperationService();

        assertThrows(IllegalStateException.class, () -> service.beginCarrierOperation(
                StrategicOperationState.empty(),
                commands(OrderType.INTERCEPT),
                projected,
                1L,
                CARRIER,
                50L,
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(5_000, 0, 100L),
                new WithdrawalPolicy(SYSTEM, 4_000, true, true)));

        Fixture healthy = fixture();
        var healthyProjection = healthy.readiness.project(
                healthy.forces,
                healthy.registry,
                healthy.hangars,
                SmallCraftMissionState.empty(),
                healthy.identities,
                List.of(healthy.assignment));
        assertThrows(IllegalArgumentException.class, () -> service.beginCarrierOperation(
                StrategicOperationState.empty(),
                commands(OrderType.BLOCKADE),
                healthyProjection,
                1L,
                CARRIER,
                50L,
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(5_000, 0, 100L),
                new WithdrawalPolicy(SYSTEM, 4_000, true, true)));
    }

    private static Fixture fixture() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        ContentCatalog.FactionDefinition faction = content.getFactions().get(0);
        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(content, List.of());

        SmallCraftRegistry registry = SmallCraftRegistry.empty(ProductionSmallCraftFixture.fitAuthority());
        SmallCraftId alpha = registry.reserveIdentityForCompletedProduction();
        SmallCraftId beta = registry.reserveIdentityForCompletedProduction();
        registry.registerProducedCraft(withFaction(ProductionSmallCraftFixture.craft(
                alpha, 150_000L, 150_000d, 2_600_000d, 1d, 0d), faction.id()));
        registry.registerProducedCraft(withFaction(ProductionSmallCraftFixture.craft(
                beta, 150_000L, 150_000d, 2_600_000d, 1d, 0d), faction.id()));

        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(registry);
        BayDefinition bay = new BayDefinition(
                new BayId(HOST, "bay.flight"),
                HostKind.SHIP,
                new Dimensions3d(250d, 250d, 250d),
                100_000_000d,
                100_000_000d,
                1d);
        hangars.assign(alpha, bay, OccupancyState.READY);
        hangars.assign(beta, bay, OccupancyState.READY);

        EntityState carrier = entity(1_100L, faction.runtimeId());
        EntityState escort = entity(1_101L, faction.runtimeId());
        FleetForceRegistry forces = new FleetForceRegistry(List.of(
                entry(CARRIER, faction.runtimeId(), carrier),
                entry(ESCORT, faction.runtimeId(), escort)));

        CarrierWingAssignment assignment =
                new CarrierWingAssignment(CARRIER, HOST, faction.id(), List.of(alpha, beta));
        return new Fixture(
                registry,
                hangars,
                identities,
                forces,
                carrier,
                assignment,
                alpha,
                beta,
                new CarrierWingStrategicReadinessService(
                        Stage22CorePairEngineeringCatalogLoader.loadDefault()));
    }

    private static SmallCraftState withFaction(SmallCraftState source, String factionId) {
        return new SmallCraftState(
                source.id(),
                factionId,
                source.designId(),
                source.fit(),
                source.runtimeState(),
                source.instanceState());
    }

    private static FleetForceRegistry.Entry entry(
            FleetId id,
            int factionId,
            EntityState entity) {
        return new FleetForceRegistry.Entry(
                id,
                factionId,
                FleetLocationKind.IN_SYSTEM,
                SYSTEM,
                null,
                null,
                entity,
                new FleetReadinessState(
                        10_000, 10_000, 10_000, 10_000, 10_000, 10_000, 10_000));
    }

    private static EntityState entity(long id, int factionId) {
        return new EntityState(
                new EntityId(id),
                null, null, null, null, null, null, null,
                new EntityState.FactionState(factionId),
                null, null, null, null, null, null, null, null, null);
    }

    private static FleetCommandState commands(OrderType type) {
        CommandGroupState group = new CommandGroupState(
                1L,
                ContentCatalogLoader.loadDefault().getFactions().get(0).runtimeId(),
                "Carrier Group",
                List.of(CARRIER, ESCORT),
                SYSTEM,
                false,
                false,
                10_000);
        FleetOrderState order = new FleetOrderState(
                1L,
                1L,
                type,
                OrderSource.AI,
                SYSTEM,
                List.of(SYSTEM),
                0,
                40L,
                60L,
                OrderStatus.ACTIVE);
        return FleetCommandState.empty().addGroup(group).addOrder(order);
    }

    private static OperationType expectedOperationType(OrderType type) {
        return switch (type) {
            case ESCORT -> OperationType.ESCORT;
            case INTERCEPT -> OperationType.INTERCEPTION;
            case RAID -> OperationType.RAID;
            case GUARD -> OperationType.DEFENSE;
            default -> throw new IllegalArgumentException("unsupported test order: " + type);
        };
    }

    private record Fixture(
            SmallCraftRegistry registry,
            SmallCraftHangarRegistry hangars,
            FactionIdentityResolver identities,
            FleetForceRegistry forces,
            EntityState carrierEntity,
            CarrierWingAssignment assignment,
            SmallCraftId alpha,
            SmallCraftId beta,
            CarrierWingStrategicReadinessService readiness) { }
}
