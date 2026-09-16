package com.spacesim.campaign;

import com.spacesim.persistence.Stage20FreightPersistentState.CargoLotState;
import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.world.FactionActorObservationSnapshot.InterestKind;
import com.spacesim.world.FactionInterestResolver.DecisionTrace;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCampaignFirstHourJourneyTest {
    private static final float EIGHT_TIMES_PRESENTATION_FRAME_SECONDS = 0.05f;
    private static final double SIMULATION_SECONDS_PER_FRAME = 0.4d;
    private static final double FIRST_HOUR_SIMULATION_SECONDS = 3_600d;
    private static final int FIRST_HOUR_FRAME_BUDGET =
            (int) Math.ceil(FIRST_HOUR_SIMULATION_SECONDS / SIMULATION_SECONDS_PER_FRAME);

    @Test
    void physicalCargoIdentitySurvivesMidJourneyReloadAndReachesFactionDecisionWithinFirstHour() {
        GeneratedCampaignCoordinator campaign = GeneratedCampaignCoordinator.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        campaign.setTimeScale(8d);
        var autonomousFactionIds = campaign.actors().capture().stream()
                .map(state -> state.factionContentId())
                .toList();

        CargoLotState inFlightLot = null;
        int framesUsed = 0;
        while (framesUsed < FIRST_HOUR_FRAME_BUDGET && inFlightLot == null) {
            campaign.advanceFrame(EIGHT_TIMES_PRESENTATION_FRAME_SECONDS);
            framesUsed++;
            var freight = campaign.session().captureState().freight();
            var ordersById = freight.orders().stream().collect(Collectors.toMap(
                    TransportOrderState::orderId,
                    Function.identity()));
            inFlightLot = freight.cargoLots().stream()
                    .filter(lot -> {
                        TransportOrderState order = ordersById.get(lot.orderId());
                        return order != null
                                && autonomousFactionIds.contains(order.stableFactionId())
                                && order.deliveryDeadlineSeconds() < FIRST_HOUR_SIMULATION_SECONDS;
                    })
                    .min(Comparator
                            .comparingDouble((CargoLotState lot) ->
                                    ordersById.get(lot.orderId()).deliveryDeadlineSeconds())
                            .thenComparing(CargoLotState::lotId))
                    .orElse(null);
        }
        assertNotNull(inFlightLot,
                "ordinary generated campaign must materialize autonomous-faction physical cargo with an authoritative first-hour deadline");

        var midStage20 = campaign.session().captureState();
        byte[] midJourneyBytes = Stage21IGeneratedWorldRuntimePersistenceCodec.encode(campaign.captureState());
        CargoLotState savedLot = inFlightLot;
        TransportOrderState savedOrder = midStage20.freight().orders().stream()
                .filter(order -> order.orderId().equals(savedLot.orderId()))
                .findFirst()
                .orElseThrow();
        assertTrue(savedOrder.deliveryDeadlineSeconds() < FIRST_HOUR_SIMULATION_SECONDS,
                "tracked cargo must be committed to a physical first-hour delivery deadline before reload");

        campaign = GeneratedCampaignCoordinator.restore(
                Stage21IGeneratedWorldRuntimePersistenceCodec.decode(midJourneyBytes));
        campaign.setTimeScale(8d);
        assertTrue(campaign.session().captureState().freight().cargoLots().stream()
                        .anyMatch(savedLot::equals),
                "save/codec/reload must preserve the exact physical cargo-lot identity and provenance");

        TransportOrderState outcome = null;
        FreightPhase outcomePhase = null;
        long outcomeTick = -1L;
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
                outcomeTick = campaign.runtime().world().getAuthoritativeWorldTick();
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

        TransportOrderState finalOutcome = outcome;
        InterestKind expectedInterest = outcomePhase == FreightPhase.DESTROYED
                || finalOutcome.delayedDeliveryCount() > 0L
                ? InterestKind.RESOURCE_DEFICIT
                : InterestKind.SUPPLY_DEPENDENCY;
        DecisionTrace productionDecision = null;
        while (framesUsed < FIRST_HOUR_FRAME_BUDGET && productionDecision == null) {
            var candidate = campaign.latestDecisionTrace(finalOutcome.stableFactionId()).orElse(null);
            if (candidate != null
                    && candidate.observationTick() >= outcomeTick
                    && candidate.orderedEvidence().stream().anyMatch(evidence ->
                    evidence.kind() == expectedInterest
                            && evidence.targetId().equals(finalOutcome.orderId())
                            && evidence.supportingObservations().stream().anyMatch(row ->
                            row.evidence().provenanceId().equals(finalOutcome.orderId())))) {
                productionDecision = candidate;
                break;
            }
            campaign.advanceFrame(EIGHT_TIMES_PRESENTATION_FRAME_SECONDS);
            framesUsed++;
        }

        assertNotNull(productionDecision,
                "the ordinary Stage-21A actor runtime must review the same freight outcome within the first hour");
        assertEquals(finalOutcome.stableFactionId(), productionDecision.factionContentId());
        assertTrue(productionDecision.observationTick() >= outcomeTick,
                "faction decision must be based on an observation at or after the physical freight outcome");
        assertEquals(productionDecision.observationTick(), campaign.actors()
                        .findState(finalOutcome.stableFactionId()).orElseThrow().lastReviewTick(),
                "decision trace must come from the actor runtime's completed scheduled review");
        assertTrue(productionDecision.orderedEvidence().stream().anyMatch(evidence ->
                        evidence.kind() == expectedInterest
                                && evidence.targetId().equals(finalOutcome.orderId())
                                && evidence.supportingObservations().stream().anyMatch(row ->
                                row.evidence().provenanceId().equals(finalOutcome.orderId()))),
                "the same transport identity must remain visible in the production faction decision");
    }
}
