package com.spacesim.campaign;

import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

class GeneratedCampaignFirstHourTimingDiagnosticsTest {
    @Test
    void reportDefaultGeneratedFreightTravelTimes() {
        GeneratedCampaignCoordinator campaign = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        var freight = campaign.session().captureState().freight();
        System.out.println("M22.7_FIRST_HOUR_FREIGHT_TIMING_BEGIN");
        freight.orders().stream()
                .sorted(Comparator.comparingDouble(order -> order.oneWayDeliverySeconds()))
                .forEach(order -> System.out.printf(
                        java.util.Locale.ROOT,
                        "order=%s faction=%s kind=%s oneWayS=%.6f roundTripS=%.6f route=%s%n",
                        order.orderId(),
                        order.stableFactionId(),
                        order.assignmentKind(),
                        order.oneWayDeliverySeconds(),
                        order.roundTripCadenceSeconds(),
                        order.orderedSystems()));
        System.out.println("M22.7_FIRST_HOUR_FREIGHT_TIMING_END");
    }
}
