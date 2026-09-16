package com.spacesim.campaign;

import com.spacesim.persistence.Stage20FreightPersistentState.AssignmentKind;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCampaignInitialFreightCommitmentTest {
    private static final float ONE_AUTONOMOUS_PERIOD_AT_EIGHT_TIMES = 0.05f;

    @Test
    void newCampaignRetainsOneDueEssentialCommitmentWithoutChangingPhysicalRouteTiming() {
        var raw = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime().captureState();
        var rawOpening = raw.freight().orders().stream()
                .filter(order -> order.assignmentKind() == AssignmentKind.ESSENTIAL_BOOTSTRAP)
                .min(Comparator.comparing(TransportOrderState::orderId))
                .orElseThrow();

        GeneratedCampaignSession session = GeneratedCampaignSession.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        var created = session.captureState().freight();
        var opening = created.orders().stream()
                .filter(order -> order.orderId().equals(rawOpening.orderId()))
                .findFirst()
                .orElseThrow();

        assertEquals(0d, opening.deliveryDeadlineSeconds(), 0d);
        assertEquals(rawOpening.oneWayDeliverySeconds(), opening.oneWayDeliverySeconds(), 0d);
        assertEquals(rawOpening.roundTripCycleSeconds(), opening.roundTripCycleSeconds(), 0d);
        assertEquals(1L, created.orders().stream()
                .filter(order -> order.assignmentKind() == AssignmentKind.ESSENTIAL_BOOTSTRAP)
                .filter(order -> order.deliveryDeadlineSeconds() == 0d)
                .count());

        session.setTimeScale(8d);
        session.advanceFrame(ONE_AUTONOMOUS_PERIOD_AT_EIGHT_TIMES);
        var dispatched = session.captureState().freight();
        var dispatchedOrder = dispatched.orders().stream()
                .filter(order -> order.orderId().equals(opening.orderId()))
                .findFirst()
                .orElseThrow();
        assertEquals(0d, dispatchedOrder.deliveryDeadlineSeconds(), 0d,
                "dispatch must not slide an already-authoritative service deadline");
        assertTrue(dispatched.cargoLots().stream()
                .anyMatch(lot -> lot.orderId().equals(opening.orderId())),
                "ordinary freight autonomy must create physical cargo before the due commitment is observed");

        session.advanceFrame(ONE_AUTONOMOUS_PERIOD_AT_EIGHT_TIMES);
        var delayed = session.captureState().freight().orders().stream()
                .filter(order -> order.orderId().equals(opening.orderId()))
                .findFirst()
                .orElseThrow();
        assertEquals(1L, delayed.delayedDeliveryCount());
        assertEquals(opening.roundTripCycleSeconds(), delayed.deliveryDeadlineSeconds(), 0d);

        GeneratedCampaignSession restored = GeneratedCampaignSession.restore(session.captureState());
        var restoredOrder = restored.captureState().freight().orders().stream()
                .filter(order -> order.orderId().equals(opening.orderId()))
                .findFirst()
                .orElseThrow();
        assertEquals(delayed, restoredOrder,
                "restore must preserve the opening commitment outcome instead of reapplying bootstrap normalization");
    }
}
