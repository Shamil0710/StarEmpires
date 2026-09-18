package com.spacesim.persistence;

import com.spacesim.world.SmallCraftFlightDeckOperations;
import com.spacesim.world.SmallCraftFlightDeckOperations.ActiveOperation;
import com.spacesim.world.SmallCraftFlightDeckOperations.DeckProfile;
import com.spacesim.world.SmallCraftFlightDeckOperations.Request;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarRegistry;

import java.util.List;
import java.util.Objects;

/** Maps M22.8C deterministic flight-deck handling state to/from persistence. */
public final class Stage228FlightDeckPersistenceMapper {
    private Stage228FlightDeckPersistenceMapper() {
        throw new AssertionError("utility class");
    }

    /** Captures profiles, queue and active operations exactly.
     * @param operations runtime flight-deck sequencer
     * @return deterministic persistent sidecar
     */
    public static Stage228FlightDeckPersistentState capture(
            SmallCraftFlightDeckOperations operations) {
        SmallCraftFlightDeckOperations checked =
                Objects.requireNonNull(operations, "operations");
        List<Stage228FlightDeckPersistentState.DeckProfileState> profiles =
                checked.profiles().stream().map(value ->
                        new Stage228FlightDeckPersistentState.DeckProfileState(
                                value.bayId().hostStableId(),
                                value.bayId().bayStableId(),
                                value.launchWorkSeconds(),
                                value.recoveryWorkSeconds()))
                        .toList();
        List<Stage228FlightDeckPersistentState.RequestState> queued =
                checked.queued().stream()
                        .map(Stage228FlightDeckPersistenceMapper::captureRequest)
                        .toList();
        List<Stage228FlightDeckPersistentState.ActiveState> active =
                checked.active().stream()
                        .map(value -> new Stage228FlightDeckPersistentState.ActiveState(
                                captureRequest(value.request()),
                                value.phase(),
                                value.remainingWorkSeconds(),
                                value.failureKind()))
                        .toList();
        return new Stage228FlightDeckPersistentState(
                Stage228FlightDeckPersistentState.CURRENT_VERSION,
                Stage228FlightDeckPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228FlightDeckPersistentState.CURRENT_SEMANTIC_CONTRACT,
                profiles,
                queued,
                active);
    }

    /** Restores exact flight-deck state against already restored physical hangar occupancy.
     * @param state validated C sidecar
     * @param hangars restored physical hangar authority
     * @return independent restored sequencer
     */
    public static SmallCraftFlightDeckOperations restore(
            Stage228FlightDeckPersistentState state,
            SmallCraftHangarRegistry hangars) {
        Stage228FlightDeckPersistentState checked =
                Objects.requireNonNull(state, "state");
        SmallCraftHangarRegistry checkedHangars =
                Objects.requireNonNull(hangars, "hangars");
        List<DeckProfile> profiles = checked.profiles().stream()
                .map(value -> new DeckProfile(
                        new BayId(value.hostStableId(), value.bayStableId()),
                        value.launchWorkSeconds(),
                        value.recoveryWorkSeconds()))
                .toList();
        List<Request> queued = checked.queued().stream()
                .map(Stage228FlightDeckPersistenceMapper::restoreRequest)
                .toList();
        List<ActiveOperation> active = checked.active().stream()
                .map(value -> new ActiveOperation(
                        restoreRequest(value.request()),
                        value.phase(),
                        value.remainingWorkSeconds(),
                        value.failureKind()))
                .toList();
        return SmallCraftFlightDeckOperations.restore(
                checkedHangars, profiles, queued, active);
    }

    private static Stage228FlightDeckPersistentState.RequestState captureRequest(
            Request request) {
        Request checked = Objects.requireNonNull(request, "request");
        return new Stage228FlightDeckPersistentState.RequestState(
                checked.craftId(),
                checked.bayId().hostStableId(),
                checked.bayId().bayStableId(),
                checked.kind(),
                checked.requestedTick());
    }

    private static Request restoreRequest(
            Stage228FlightDeckPersistentState.RequestState request) {
        Stage228FlightDeckPersistentState.RequestState checked =
                Objects.requireNonNull(request, "request");
        return new Request(
                checked.craftId(),
                new BayId(checked.hostStableId(), checked.bayStableId()),
                checked.kind(),
                checked.requestedTick());
    }
}
