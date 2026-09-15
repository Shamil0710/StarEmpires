package com.spacesim.campaign;

import com.spacesim.persistence.Stage20FreightPersistentState.CargoLotState;
import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.world.FactionInterestResolver;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCampaignFirstHourJourneyTest {
    private static final float EIGHT_TIMES_PRESENTATION_FRAME_SECONDS = 0.1f;
    private static final double SIMULATION_SECONDS_PER_FRAME = 0.8d;
    private static final int FIRST_HOUR_FRAME_BUDGET =
            (int) Math.ceil(3_600d / SIMULATION_SECONDS_PER_FRAME);

    @Test
    void physicalCargoIdentitySurvivesMidJourneyReloadAndReachesFactionDecisionWithinFirstHour() {
        GeneratedCampaignCoordinator campaign = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        campaign.setTimeScale(8d);

        CargoLotState inFlightLot = null;
        int framesUsed = 0;
        while (framesUsed < FIRST_HOUR_FRAME_BUDGET && inFlightLot == null) {
            campaign.advanceFrame(EIGHT_TIMES_PRESENTATION_FRAME_SECONDS);
            framesUsed++;
            var freight = campaign.session().captureState().freight();
            inFlightLot = freight.cargoLots().stream().findFirst().orElse(null);
        }
        assertNotNull(inFlightLot,
                "ordinary generated campaign must materialize physical cargo during the first hour");

        var midJourney = campaign.captureState();
        var midStage20 = midJourney.stage21HRuntime().stage21GRuntime().stage21FRuntime()
                .stage21ERuntime().stage21DRuntime().stage21CRuntime().stage21BRuntime()
                .stage21ARuntime().stage20Runtime();
        CargoLotState savedLot = inFlightLot;
        TransportOrderState savedOrder = midStage20.freight().orders().stream()
                .filter(order -> order.orderId().equals(savedLot.orderId()))
                .findFirst()
                .orElseThrow();

        campaign = GeneratedCampaignCoordinator.restore(midJourney);
        campaign.setTimeScale(8d);
        assertTrue(campaign.session().captureState().freight().cargoLots().stream()
                        .anyMatch(savedLot::equals),
                "save/reload must preserve the exact physical cargo-lot identity and provenance");

        TransportOrderState outcome = null;
        FreightPhase outcomePhase = null;
        while (framesUsed < FIRST_HOUR_FRAME_BUDGET && outcome == null) {
            campaign.advanceFrame(EIGHT_TIMES_PRESENTATION_FRAME_SECONDS);
            framesUsed++;
            if (framesUsed % 8 != 0) {
                continue;
            }
            var freight = campaign.session().captureState().freight();
            var currentOrder = freight.orders().stream()
                    .filter(order -> order.orderId().equals(savedOrder.orderId()))
                    .findFirst()
                    .orElseThrow();
            var currentFleet = freight.freighters().stream()
                    .filter(fleet -> fleet.fleetId().equals(savedOrder.fleetId()))
                    .findFirst()
                    .orElseThrow();
            if (currentOrder.deliveredMassKg() > savedOrder.deliveredMassKg()
                    || currentOrder.delayedDeliveryCount() > savedOrder.delayedDeliveryCount()
                    || currentFleet.phase() == FreightPhase.DESTROYED) {
                outcome = currentOrder;
                outcomePhase = currentFleet.phase();
            }
        }

        assertNotNull(outcome,
                "the tracked first-hour freight identity must reach delivery, delay or physical loss");
        assertEquals(savedLot.orderId(), outcome.orderId());
        assertEquals(savedLot.fleetId(), outcome.fleetId());
        assertEquals(savedLot.commodityId(), outcome.commodityId());
        assertTrue(outcome.deliveredMassKg() > savedOrder.deliveredMassKg()
                        || outcome.delayedDeliveryCount() > savedOrder.delayedDeliveryCount()
                        || outcomePhase == FreightPhase.DESTROYED,
                "terminal causal outcome must be a physical delivery, shortage signal or loss");

        long nowTick = campaign.runtime().world().getAuthoritativeWorldTick();
        var snapshot = GeneratedCampaignFactionObservationPublisher.publish(
                campaign.session().captureState(), outcome.stableFactionId(), nowTick);
        var observation = snapshot.economic().stream()
                .filter(row -> row.targetId().equals(outcome.orderId()))
                .findFirst()
                .orElseThrow();
        var trace = FactionInterestResolver.resolve(snapshot);

        assertEquals(outcome.orderId(), observation.evidence().provenanceId());
        assertTrue(trace.orderedEvidence().stream().anyMatch(evidence ->
                        evidence.targetId().equals(outcome.orderId())
                                && evidence.supportingObservations().stream().anyMatch(row ->
                                row.evidence().provenanceId().equals(outcome.orderId()))),
                "the same transport identity must remain visible in downstream faction reasoning");
    }
}
