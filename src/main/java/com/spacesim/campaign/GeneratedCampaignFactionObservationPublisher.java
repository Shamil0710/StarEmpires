package com.spacesim.campaign;

import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState;
import com.spacesim.world.FactionActorObservationSnapshot;
import com.spacesim.world.FactionActorObservationSnapshot.ActorObservation;
import com.spacesim.world.FactionActorObservationSnapshot.Domain;
import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only campaign adapter from the accepted Stage-20 freight ledger into Stage-21A actor knowledge.
 *
 * <p>The adapter owns no cargo, industry or faction state. Stable Stage-20 transport-order IDs remain
 * the causal identity all the way into observation provenance and interest targets. Delivery and
 * delay/loss facts therefore stay traceable without introducing a second persistence authority.</p>
 */
final class GeneratedCampaignFactionObservationPublisher {
    private GeneratedCampaignFactionObservationPublisher() {
        throw new AssertionError("Utility class");
    }

    /**
     * Publishes one actor-bounded snapshot from the current persisted freight outcome.
     *
     * @param stage20 current authoritative Stage-20 checkpoint
     * @param factionContentId observing faction
     * @param observedAtTick authoritative review tick
     * @return immutable Stage-21A observation snapshot
     */
    static FactionActorObservationSnapshot publish(
            Stage20GeneratedWorldRuntimePersistentState stage20,
            String factionContentId,
            long observedAtTick) {
        Stage20GeneratedWorldRuntimePersistentState state = Objects.requireNonNull(stage20, "stage20");
        String factionId = requireText(factionContentId, "factionContentId");
        if (observedAtTick < 0L) {
            throw new IllegalArgumentException("observedAtTick must be non-negative");
        }

        ArrayList<ActorObservation> economic = new ArrayList<>();
        state.freight().orders().stream()
                .filter(order -> factionId.equals(order.stableFactionId()))
                .sorted(java.util.Comparator.comparing(TransportOrderState::orderId))
                .map(order -> toObservation(state, order, observedAtTick))
                .forEach(economic::add);

        return new FactionActorObservationSnapshot(
                factionId,
                observedAtTick,
                List.copyOf(economic),
                List.of(),
                List.of(),
                List.of());
    }

    private static ActorObservation toObservation(
            Stage20GeneratedWorldRuntimePersistentState state,
            TransportOrderState order,
            long observedAtTick) {
        FreightPhase phase = state.freight().freighters().stream()
                .filter(freighter -> freighter.fleetId().equals(order.fleetId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Freight order references absent physical fleet: " + order.orderId()))
                .phase();

        InterestKind kind;
        int severity;
        if (phase == FreightPhase.DESTROYED) {
            kind = InterestKind.RESOURCE_DEFICIT;
            severity = 10_000;
        } else if (order.delayedDeliveryCount() > 0L) {
            kind = InterestKind.RESOURCE_DEFICIT;
            severity = (int) Math.min(9_500L, 7_500L + order.delayedDeliveryCount() * 250L);
        } else if (order.deliveredMassKg() > 0d) {
            kind = InterestKind.SUPPLY_DEPENDENCY;
            severity = 3_500;
        } else {
            kind = InterestKind.ROUTE_EXPOSURE;
            severity = 5_000;
        }

        return new ActorObservation(
                Domain.ECONOMIC,
                kind,
                order.orderId(),
                severity,
                new ObservationEvidence(
                        ObservationChannel.ECONOMIC_LEDGER,
                        order.orderId(),
                        observedAtTick,
                        -1L));
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
