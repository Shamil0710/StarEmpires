package com.spacesim.persistence;

import com.spacesim.world.ProductionSmallCraftFixture;
import com.spacesim.world.SmallCraftFitAuthority;
import com.spacesim.world.SmallCraftFlightDeckOperations;
import com.spacesim.world.SmallCraftFlightDeckOperations.OperationKind;
import com.spacesim.world.SmallCraftFlightDeckOperations.OperationPhase;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftHangarRegistry;
import com.spacesim.world.SmallCraftId;
import com.spacesim.world.SmallCraftRegistry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage228FlightDeckPersistenceTest {
    @Test
    void codecAndMapperRoundTripAwaitingHandoffWithoutRemovingCraft() {
        Fixture fixture = fixture();
        SmallCraftId launchCraft = fixture.ids().get(0);
        SmallCraftId returningCraft = fixture.ids().get(1);
        Stage228FlightDeckPersistentState state = new Stage228FlightDeckPersistentState(
                Stage228FlightDeckPersistentState.CURRENT_VERSION,
                Stage228FlightDeckPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228FlightDeckPersistentState.CURRENT_SEMANTIC_CONTRACT,
                43L,
                List.of(new Stage228FlightDeckPersistentState.DeckProfileState(
                        "carrier:persist", "mission_primary", 3d, 4d)),
                List.of(new Stage228FlightDeckPersistentState.RequestState(
                        returningCraft,
                        "carrier:persist",
                        "mission_primary",
                        OperationKind.RECOVERY,
                        44L)),
                List.of(new Stage228FlightDeckPersistentState.ActiveState(
                        new Stage228FlightDeckPersistentState.RequestState(
                                launchCraft,
                                "carrier:persist",
                                "mission_primary",
                                OperationKind.LAUNCH,
                                40L),
                        OperationPhase.AWAITING_HANDOFF,
                        0d,
                        null)));

        byte[] first = Stage228FlightDeckPersistenceCodec.encode(state);
        Stage228FlightDeckPersistentState decoded =
                Stage228FlightDeckPersistenceCodec.decode(first);
        byte[] second = Stage228FlightDeckPersistenceCodec.encode(decoded);

        assertEquals(state, decoded);
        assertTrue(Arrays.equals(first, second));
        SmallCraftFlightDeckOperations restored =
                Stage228FlightDeckPersistenceMapper.restore(decoded, fixture.hangars());
        assertEquals(decoded, Stage228FlightDeckPersistenceMapper.capture(restored));
        assertEquals(OperationPhase.AWAITING_HANDOFF,
                restored.activeFor(launchCraft).orElseThrow().phase());
        assertTrue(fixture.hangars().find(launchCraft).isPresent(),
                "save/load must not complete physical launch handoff");
        assertTrue(fixture.hangars().find(returningCraft).isEmpty(),
                "queued recovery must remain outside bay occupancy");
    }

    @Test
    void persistedQueueKeepsRecoveryPriorityAtEqualTickAndBay() {
        Fixture fixture = fixtureWithLaunchState(OccupancyState.READY);
        SmallCraftId launch = fixture.ids().get(0);
        SmallCraftId recovery = fixture.ids().get(1);
        Stage228FlightDeckPersistentState state = new Stage228FlightDeckPersistentState(
                Stage228FlightDeckPersistentState.CURRENT_VERSION,
                Stage228FlightDeckPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228FlightDeckPersistentState.CURRENT_SEMANTIC_CONTRACT,
                List.of(new Stage228FlightDeckPersistentState.DeckProfileState(
                        "carrier:persist", "mission_primary", 3d, 4d)),
                List.of(
                        new Stage228FlightDeckPersistentState.RequestState(
                                launch,
                                "carrier:persist",
                                "mission_primary",
                                OperationKind.LAUNCH,
                                50L),
                        new Stage228FlightDeckPersistentState.RequestState(
                                recovery,
                                "carrier:persist",
                                "mission_primary",
                                OperationKind.RECOVERY,
                                50L)),
                List.of());

        assertEquals(OperationKind.RECOVERY, state.queued().get(0).kind());
        SmallCraftFlightDeckOperations restored =
                Stage228FlightDeckPersistenceMapper.restore(state, fixture.hangars());
        assertEquals(OperationKind.RECOVERY, restored.queued().get(0).kind());
        assertEquals(OperationKind.LAUNCH, restored.queued().get(1).kind());
    }

    @Test
    void persistentStateRejectsQueuedPhaseInsideActiveOperation() {
        SmallCraftId craft = new SmallCraftId(1L);
        var request = new Stage228FlightDeckPersistentState.RequestState(
                craft,
                "carrier:persist",
                "mission_primary",
                OperationKind.LAUNCH,
                1L);

        assertThrows(IllegalArgumentException.class,
                () -> new Stage228FlightDeckPersistentState.ActiveState(
                        request,
                        OperationPhase.QUEUED,
                        1d,
                        null));
    }

    @Test
    void mapperAndCodecPreserveAuthoritativeTickWatermark() {
        Fixture fixture = fixtureWithLaunchState(OccupancyState.READY);
        SmallCraftId launch = fixture.ids().get(0);
        Stage228FlightDeckPersistentState state = new Stage228FlightDeckPersistentState(
                Stage228FlightDeckPersistentState.CURRENT_VERSION,
                Stage228FlightDeckPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228FlightDeckPersistentState.CURRENT_SEMANTIC_CONTRACT,
                77L,
                List.of(new Stage228FlightDeckPersistentState.DeckProfileState(
                        "carrier:persist", "mission_primary", 3d, 4d)),
                List.of(new Stage228FlightDeckPersistentState.RequestState(
                        launch,
                        "carrier:persist",
                        "mission_primary",
                        OperationKind.LAUNCH,
                        78L)),
                List.of());

        byte[] encoded = Stage228FlightDeckPersistenceCodec.encode(state);
        Stage228FlightDeckPersistentState decoded =
                Stage228FlightDeckPersistenceCodec.decode(encoded);
        SmallCraftFlightDeckOperations restored =
                Stage228FlightDeckPersistenceMapper.restore(decoded, fixture.hangars());

        assertEquals(77L, decoded.lastProcessedTick());
        assertEquals(77L, restored.lastProcessedTick());
        assertThrows(IllegalArgumentException.class,
                () -> restored.advanceFixedTick(77L, 1d, java.util.Map.of()));
    }

    @Test
    void persistentStateRejectsActiveOperationFromFutureOfWatermark() {
        SmallCraftId craft = new SmallCraftId(1L);
        var request = new Stage228FlightDeckPersistentState.RequestState(
                craft,
                "carrier:persist",
                "mission_primary",
                OperationKind.LAUNCH,
                11L);
        var active = new Stage228FlightDeckPersistentState.ActiveState(
                request,
                OperationPhase.CYCLING,
                2d,
                null);

        assertThrows(IllegalArgumentException.class, () ->
                new Stage228FlightDeckPersistentState(
                        Stage228FlightDeckPersistentState.CURRENT_VERSION,
                        Stage228FlightDeckPersistentState.CURRENT_RUNTIME_VERSION,
                        Stage228FlightDeckPersistentState.CURRENT_SEMANTIC_CONTRACT,
                        10L,
                        List.of(new Stage228FlightDeckPersistentState.DeckProfileState(
                                "carrier:persist", "mission_primary", 3d, 4d)),
                        List.of(),
                        List.of(active)));
    }

    @Test
    void mapperRejectsActiveLaunchWhenPersistedOccupancyIsNotLaunching() {
        Fixture fixture = fixtureWithLaunchState(OccupancyState.READY);
        SmallCraftId craft = fixture.ids().get(0);
        Stage228FlightDeckPersistentState malformed = new Stage228FlightDeckPersistentState(
                Stage228FlightDeckPersistentState.CURRENT_VERSION,
                Stage228FlightDeckPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228FlightDeckPersistentState.CURRENT_SEMANTIC_CONTRACT,
                10L,
                List.of(new Stage228FlightDeckPersistentState.DeckProfileState(
                        "carrier:persist", "mission_primary", 3d, 4d)),
                List.of(),
                List.of(new Stage228FlightDeckPersistentState.ActiveState(
                        new Stage228FlightDeckPersistentState.RequestState(
                                craft,
                                "carrier:persist",
                                "mission_primary",
                                OperationKind.LAUNCH,
                                10L),
                        OperationPhase.CYCLING,
                        2d,
                        null)));

        assertThrows(IllegalArgumentException.class,
                () -> Stage228FlightDeckPersistenceMapper.restore(
                        malformed, fixture.hangars()));
    }

    @Test
    void codecRejectsTrailingAndTruncatedPayloads() {
        Stage228FlightDeckPersistentState empty = Stage228FlightDeckPersistentState.empty();
        byte[] valid = Stage228FlightDeckPersistenceCodec.encode(empty);

        assertThrows(IllegalArgumentException.class,
                () -> Stage228FlightDeckPersistenceCodec.decode(
                        Arrays.copyOf(valid, valid.length + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> Stage228FlightDeckPersistenceCodec.decode(
                        Arrays.copyOf(valid, valid.length - 1)));
    }

    private static Fixture fixture() {
        return fixtureWithLaunchState(OccupancyState.LAUNCHING);
    }

    private static Fixture fixtureWithLaunchState(OccupancyState launchState) {
        SmallCraftFitAuthority authority = ProductionSmallCraftFixture.fitAuthority();
        SmallCraftRegistry craft = SmallCraftRegistry.empty(authority);
        SmallCraftId first = craft.reserveIdentityForCompletedProduction();
        craft.registerProducedCraft(ProductionSmallCraftFixture.craft(
                first, 8L, 80d, 4_000d, 1d, 100d));
        SmallCraftId second = craft.reserveIdentityForCompletedProduction();
        craft.registerProducedCraft(ProductionSmallCraftFixture.craft(
                second, 8L, 80d, 4_000d, 1d, 100d));

        Stage228HangarPersistentState hangarState = new Stage228HangarPersistentState(
                Stage228HangarPersistentState.CURRENT_VERSION,
                Stage228HangarPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228HangarPersistentState.CURRENT_SEMANTIC_CONTRACT,
                List.of(new Stage228HangarPersistentState.AssignmentState(
                        first,
                        "carrier:persist",
                        "mission_primary",
                        HostKind.SHIP,
                        launchState)));
        SmallCraftHangarRegistry hangars =
                Stage228HangarPersistenceMapper.restore(hangarState, craft);
        return new Fixture(craft, hangars, List.of(first, second));
    }

    private record Fixture(
            SmallCraftRegistry craft,
            SmallCraftHangarRegistry hangars,
            List<SmallCraftId> ids) { }
}
