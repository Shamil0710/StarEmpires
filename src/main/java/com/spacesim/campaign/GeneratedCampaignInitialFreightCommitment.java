package com.spacesim.campaign;

import com.spacesim.persistence.Stage20FreightPersistentState;
import com.spacesim.persistence.Stage20FreightPersistentState.AssignmentKind;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;

/**
 * One-time M22.7 new-campaign normalization for the opening generated freight commitment.
 *
 * <p>Stage-20 route calibration intentionally gives the ordinary generated world multi-day physical
 * inter-system travel times. The bootstrap freight rows, however, represent already accepted
 * essential supply commitments rather than offers created at simulation second zero. A fresh
 * integrated campaign therefore retains one deterministic oldest essential commitment as due at the
 * opening checkpoint. This creates real early logistics pressure without changing route distance,
 * propulsion, cargo mass, fleet identity or later recurring cadence.</p>
 *
 * <p>Only a newly-created campaign is normalized. Save-game restore never reapplies this policy and
 * therefore preserves the exact persisted delivery deadline and missed-delivery history.</p>
 */
final class GeneratedCampaignInitialFreightCommitment {
    private GeneratedCampaignInitialFreightCommitment() {
        throw new AssertionError("No instances");
    }

    /**
     * Marks the stable first essential bootstrap commitment as already due at campaign epoch.
     *
     * @param checkpoint exact freshly-created Stage-20.5 runtime checkpoint
     * @return equivalent checkpoint with one deterministic opening essential obligation
     */
    static Stage20GeneratedWorldRuntimePersistentState apply(
            Stage20GeneratedWorldRuntimePersistentState checkpoint) {
        Stage20GeneratedWorldRuntimePersistentState source = Objects.requireNonNull(checkpoint, "checkpoint");
        Stage20FreightPersistentState freight = source.freight();
        String openingOrderId = freight.orders().stream()
                .filter(order -> order.assignmentKind() == AssignmentKind.ESSENTIAL_BOOTSTRAP)
                .min(Comparator.comparing(TransportOrderState::orderId))
                .map(TransportOrderState::orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "generated campaign requires at least one essential freight commitment"));

        ArrayList<TransportOrderState> orders = new ArrayList<>(freight.orders().size());
        for (TransportOrderState order : freight.orders()) {
            if (!order.orderId().equals(openingOrderId)) {
                orders.add(order);
                continue;
            }
            orders.add(new TransportOrderState(
                    order.orderId(),
                    order.fleetId(),
                    order.stableFactionId(),
                    order.assignmentKind(),
                    order.commodityId(),
                    order.sourceEndpointId(),
                    order.destinationEndpointId(),
                    order.sourceProvenanceId(),
                    order.orderedSystems(),
                    order.oneWayDeliverySeconds(),
                    order.roundTripCycleSeconds(),
                    0d,
                    order.deliveredMassKg(),
                    order.delayedDeliveryCount()));
        }

        Stage20FreightPersistentState normalizedFreight = new Stage20FreightPersistentState(
                freight.schemaVersion(),
                freight.rootSeed(),
                freight.generatorVersion(),
                freight.worldFingerprint(),
                freight.materializationVersion(),
                freight.compatibilityAuthorityVersion(),
                freight.nextFleetIdValue(),
                freight.nextCargoLotOrdinal(),
                freight.freighters(),
                freight.cargoLots(),
                orders);

        return new Stage20GeneratedWorldRuntimePersistentState(
                source.schemaVersion(),
                source.bridgeVersion(),
                source.campaign(),
                source.worldState(),
                source.activeSystemId(),
                source.strategicStepTicks(),
                source.remoteUpdateBudgetPerFrame(),
                normalizedFreight,
                source.localFleetPhysicalStates());
    }
}
