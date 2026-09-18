package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.world.SmallCraftFlightDeckOperations.DeckProfile;
import com.spacesim.world.SmallCraftFlightDeckOperations.OperationPhase;
import com.spacesim.world.SmallCraftFlightDeckOperations.RecoveryFailureKind;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallCraftFlightDeckOperationsTest {
    @Test
    void launchCycleNeverRemovesCraftBeforePhysicalHandoff() {
        Fixture fixture = fixture(2);
        SmallCraftId craft = fixture.ids().get(0);
        fixture.hangars().assign(craft, fixture.bay(), OccupancyState.READY);
        SmallCraftFlightDeckOperations operations = operations(fixture, 2d, 3d);

        operations.requestLaunch(craft, fixture.bay().id(), 10L);
        operations.advanceFixedTick(9L, 1d, Map.of(fixture.bay().id(), fixture.bay()));
        assertTrue(operations.active().isEmpty());
        assertEquals(OccupancyState.READY,
                fixture.hangars().find(craft).orElseThrow().state());

        operations.advanceFixedTick(10L, 1d, Map.of(fixture.bay().id(), fixture.bay()));
        assertEquals(OccupancyState.LAUNCHING,
                fixture.hangars().find(craft).orElseThrow().state());
        assertEquals(OperationPhase.CYCLING,
                operations.activeFor(craft).orElseThrow().phase());

        operations.advanceFixedTick(11L, 1d, Map.of(fixture.bay().id(), fixture.bay()));
        assertEquals(OperationPhase.AWAITING_HANDOFF,
                operations.activeFor(craft).orElseThrow().phase());
        assertTrue(fixture.hangars().find(craft).isPresent(),
                "cycle completion must not teleport craft out of the bay");

        assertFalse(operations.cancelLaunch(craft),
                "handoff-ready launch crossed the command-abort boundary");
        operations.confirmLaunchHandoff(craft);
        assertTrue(fixture.hangars().find(craft).isEmpty());
        assertTrue(operations.activeFor(craft).isEmpty());
    }

    @Test
    void duplicateAuthoritativeTickCannotAdvanceDeckWorkTwice() {
        Fixture fixture = fixture(1);
        SmallCraftId craft = fixture.ids().get(0);
        fixture.hangars().assign(craft, fixture.bay(), OccupancyState.READY);
        SmallCraftFlightDeckOperations operations = operations(fixture, 4d, 3d);
        operations.requestLaunch(craft, fixture.bay().id(), 5L);

        operations.advanceFixedTick(5L, 1d, Map.of(fixture.bay().id(), fixture.bay()));
        assertEquals(3d,
                operations.activeFor(craft).orElseThrow().remainingWorkSeconds(), 1e-9);
        assertEquals(5L, operations.lastProcessedTick());

        assertThrows(IllegalArgumentException.class,
                () -> operations.advanceFixedTick(
                        5L,
                        1d,
                        Map.of(fixture.bay().id(), fixture.bay())));
        assertEquals(3d,
                operations.activeFor(craft).orElseThrow().remainingWorkSeconds(), 1e-9);
    }

    @Test
    void damagedDeckProgressUsesExactFixedTicksAndStopsAtZeroIntegrity() {
        Fixture fixture = fixture(1);
        SmallCraftId craft = fixture.ids().get(0);
        fixture.hangars().assign(craft, fixture.bay(), OccupancyState.READY);
        SmallCraftFlightDeckOperations operations = operations(fixture, 2d, 3d);
        BayDefinition halfCondition = withCondition(fixture.bay(), 0.5d);

        operations.requestLaunch(craft, fixture.bay().id(), 1L);
        operations.advanceFixedTick(1L, 1d, Map.of(fixture.bay().id(), halfCondition));
        assertEquals(1.5d,
                operations.activeFor(craft).orElseThrow().remainingWorkSeconds(), 1e-9);
        operations.advanceFixedTick(2L, 1d, Map.of(
                fixture.bay().id(), withCondition(fixture.bay(), 0d)));
        assertEquals(1.5d,
                operations.activeFor(craft).orElseThrow().remainingWorkSeconds(), 1e-9);
        operations.advanceFixedTick(3L, 1d, Map.of(fixture.bay().id(), halfCondition));
        operations.advanceFixedTick(4L, 1d, Map.of(fixture.bay().id(), halfCondition));
        operations.advanceFixedTick(5L, 1d, Map.of(fixture.bay().id(), halfCondition));
        assertEquals(OperationPhase.AWAITING_HANDOFF,
                operations.activeFor(craft).orElseThrow().phase());
    }

    @Test
    void returningCraftCanQueueOutsideFullBayUntilLaunchPhysicallyFreesCapacity() {
        Fixture fixture = fixture(2);
        SmallCraftId embarked = fixture.ids().get(0);
        SmallCraftId returning = fixture.ids().get(1);
        fixture.hangars().assign(embarked, fixture.bay(), OccupancyState.READY);
        SmallCraftFlightDeckOperations operations = operations(fixture, 1d, 1d);

        operations.offerPhysicalRecovery(returning, fixture.bay(), 1L);
        operations.requestLaunch(embarked, fixture.bay().id(), 1L);
        assertTrue(fixture.hangars().find(returning).isEmpty(),
                "queued recovery must remain outside physical bay occupancy");

        operations.advanceFixedTick(1L, 1d, Map.of(fixture.bay().id(), fixture.bay()));
        assertEquals(OperationPhase.AWAITING_HANDOFF,
                operations.activeFor(embarked).orElseThrow().phase());
        assertTrue(operations.activeFor(returning).isEmpty());
        assertTrue(fixture.hangars().find(returning).isEmpty());

        operations.confirmLaunchHandoff(embarked);
        operations.advanceFixedTick(2L, 1d, Map.of(fixture.bay().id(), fixture.bay()));

        assertTrue(operations.activeFor(returning).isEmpty());
        assertEquals(OccupancyState.SERVICING,
                fixture.hangars().find(returning).orElseThrow().state());
    }

    @Test
    void recoveryFailureBlocksBayAndNeverSilentlyParksCraft() {
        Fixture fixture = fixture(2);
        SmallCraftId returning = fixture.ids().get(0);
        SmallCraftId second = fixture.ids().get(1);
        SmallCraftFlightDeckOperations operations = operations(fixture, 1d, 4d);

        operations.offerPhysicalRecovery(returning, fixture.bay(), 1L);
        operations.offerPhysicalRecovery(second, fixture.bay(), 2L);
        operations.advanceFixedTick(1L, 1d, Map.of(fixture.bay().id(), fixture.bay()));
        assertEquals(OccupancyState.RECOVERING,
                fixture.hangars().find(returning).orElseThrow().state());

        operations.reportRecoveryFailure(returning, RecoveryFailureKind.DECK_CONTACT);
        assertEquals(OperationPhase.FAILED_BLOCKED,
                operations.activeFor(returning).orElseThrow().phase());
        operations.advanceFixedTick(20L, 10d, Map.of(fixture.bay().id(), fixture.bay()));
        assertEquals(OccupancyState.RECOVERING,
                fixture.hangars().find(returning).orElseThrow().state());
        assertTrue(fixture.hangars().find(second).isEmpty(),
                "blocked failed recovery must not let later queue entries teleport through");

        operations.confirmFailedRecoveryDeparture(returning);
        assertTrue(fixture.hangars().find(returning).isEmpty());
        operations.advanceFixedTick(21L, 4d, Map.of(fixture.bay().id(), fixture.bay()));
        assertEquals(OccupancyState.SERVICING,
                fixture.hangars().find(second).orElseThrow().state());
    }

    @Test
    void restoreRejectsQueuedRecoveryForUnknownCraftImmediately() {
        Fixture fixture = fixture(1);
        BayId bayId = fixture.bay().id();
        var request = new SmallCraftFlightDeckOperations.Request(
                new SmallCraftId(999L),
                bayId,
                SmallCraftFlightDeckOperations.OperationKind.RECOVERY,
                1L);

        assertThrows(IllegalArgumentException.class, () ->
                SmallCraftFlightDeckOperations.restore(
                        fixture.hangars(),
                        List.of(new DeckProfile(bayId, 1d, 1d)),
                        List.of(request),
                        List.of(),
                        -1L));
    }

    @Test
    void commandPathRejectsDuplicateOrWrongStateLaunches() {
        Fixture fixture = fixture(1);
        SmallCraftId craft = fixture.ids().get(0);
        SmallCraftFlightDeckOperations operations = operations(fixture, 2d, 3d);

        assertThrows(IllegalArgumentException.class,
                () -> operations.requestLaunch(craft, fixture.bay().id(), 1L));
        fixture.hangars().assign(craft, fixture.bay(), OccupancyState.READY);
        operations.requestLaunch(craft, fixture.bay().id(), 1L);
        assertThrows(IllegalArgumentException.class,
                () -> operations.requestLaunch(craft, fixture.bay().id(), 2L));
        assertTrue(operations.cancelLaunch(craft));
        assertEquals(OccupancyState.READY,
                fixture.hangars().find(craft).orElseThrow().state());
    }

    private static SmallCraftFlightDeckOperations operations(
            Fixture fixture,
            double launchWorkSeconds,
            double recoveryWorkSeconds) {
        return new SmallCraftFlightDeckOperations(
                fixture.hangars(),
                List.of(new DeckProfile(
                        fixture.bay().id(), launchWorkSeconds, recoveryWorkSeconds)));
    }

    private static Fixture fixture(int craftCount) {
        SmallCraftRegistry registry = SmallCraftRegistry.empty(
                ProductionSmallCraftFixture.fitAuthority());
        java.util.ArrayList<SmallCraftId> ids = new java.util.ArrayList<>();
        for (int index = 0; index < craftCount; index++) {
            SmallCraftId id = registry.reserveIdentityForCompletedProduction();
            registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                    id, 8L, 80d, 4_000d, 1d, 100d));
            ids.add(id);
        }
        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(registry);
        var footprint = registry.physicalFootprint(ids.get(0));
        BayDefinition bay = new BayDefinition(
                new BayId("carrier:test", "mission_primary"),
                HostKind.SHIP,
                new Dimensions3d(1_000d, 1_000d, 1_000d),
                footprint.envelopeVolumeM3() * 1.5d,
                footprint.currentMassKg() * 1.5d,
                1d);
        return new Fixture(registry, hangars, List.copyOf(ids), bay);
    }

    private static BayDefinition withCondition(BayDefinition bay, double condition) {
        return new BayDefinition(
                bay.id(),
                bay.hostKind(),
                bay.singleCraftEnvelopeM(),
                bay.pristineUsableVolumeM3(),
                bay.pristineSupportedMassKg(),
                condition);
    }

    private record Fixture(
            SmallCraftRegistry registry,
            SmallCraftHangarRegistry hangars,
            List<SmallCraftId> ids,
            BayDefinition bay) { }
}
