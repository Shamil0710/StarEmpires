package com.spacesim.campaign;

import com.spacesim.persistence.Stage20FreightPersistentState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState;
import com.spacesim.world.FleetId;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCampaignFreightDeliveryAcceptanceTest {
    private static final double MASS_EPSILON_KG = 1.0e-9d;
    private static final int FIRST_HOUR_AT_8X_REAL_SECONDS = 450;

    @Test
    void midTransitCheckpointContinuesSameCargoIntoPersistedDestinationIndustrialStorageWithinFirstHour() {
        GeneratedCampaignSession bootstrap = GeneratedCampaignSession.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        Stage20FreightPersistentState initialFreight = bootstrap.captureState().freight();
        Stage20FreightPersistentState.FreighterState initialFreighter = initialFreight.freighters().stream()
                .filter(freighter -> freighter.phase() == Stage20FreightPersistentState.FreightPhase.AT_SOURCE)
                .filter(freighter -> !freighter.activeOrderId().isBlank())
                .findFirst()
                .orElseThrow();
        FleetId fleetId = initialFreighter.fleetId();
        String orderId = initialFreighter.activeOrderId();
        Stage20FreightPersistentState.TransportOrderState initialOrder = order(initialFreight, orderId);

        Stage20GeneratedWorldRuntimePersistentState inTransitCheckpoint = null;
        Stage20FreightPersistentState.CargoLotState inTransitLot = null;
        int bootstrapGuard = 0;
        while (bootstrapGuard++ < 40) {
            bootstrap.advanceFrame(0.1f);
            Stage20GeneratedWorldRuntimePersistentState candidate = bootstrap.captureState();
            inTransitLot = candidate.freight().cargoLots().stream()
                    .filter(lot -> lot.fleetId().equals(fleetId))
                    .filter(lot -> lot.orderId().equals(orderId))
                    .findFirst()
                    .orElse(null);
            boolean hasAuthoritativeJump = candidate.worldState().fleetJumps().stream()
                    .anyMatch(jump -> jump.fleetId().equals(fleetId));
            if (inTransitLot != null && hasAuthoritativeJump) {
                inTransitCheckpoint = candidate;
                break;
            }
        }

        assertNotNull(inTransitCheckpoint,
                "generated freight must expose a physical loaded FleetJumpState before delivery acceptance");
        assertNotNull(inTransitLot);
        assertEquals(initialOrder.sourceProvenanceId(), inTransitLot.sourceProvenanceId());

        var departure = inTransitCheckpoint.worldState().fleetJumps().stream()
                .filter(jump -> jump.fleetId().equals(fleetId))
                .findFirst().orElseThrow();
        double fixedStepSeconds = bootstrap.runtime().world()
                .findSession(bootstrap.runtime().world().getActiveSystemId()).orElseThrow()
                .getClock().getFixedStepSeconds();
        double remainingDepartureSeconds = (departure.phaseEndsTick()
                - bootstrap.runtime().world().getAuthoritativeWorldTick()) * fixedStepSeconds;
        assertTrue(remainingDepartureSeconds <= FIRST_HOUR_AT_8X_REAL_SECONDS * 8d,
                () -> "First-hour scenario cannot finish even its current departure phase: order="
                        + orderId + ", fleet=" + fleetId + ", remainingDepartureSeconds="
                        + remainingDepartureSeconds + ", plannedOneWaySeconds="
                        + initialOrder.oneWayDeliverySeconds()
                        + ". Preserve physical timing; provide a feasible first-hour scenario.");

        Stage20FreightPersistentState.TransportOrderState checkpointOrder =
                order(inTransitCheckpoint.freight(), orderId);
        Stage20FreightPersistentState.FreighterState checkpointFreighter =
                freighter(inTransitCheckpoint.freight(), fleetId);
        assertEquals(Stage20FreightPersistentState.FreightPhase.OUTBOUND, checkpointFreighter.phase());
        assertTrue(checkpointFreighter.cargoMassKg() > MASS_EPSILON_KG);
        double cargoMassBeforeKg = checkpointFreighter.cargoMassKg();
        double deliveredMassBeforeKg = checkpointOrder.deliveredMassKg();

        GeneratedCampaignSession continuous = GeneratedCampaignSession.restore(inTransitCheckpoint);
        GeneratedCampaignSession resumed = GeneratedCampaignSession.restore(inTransitCheckpoint);
        assertEquals(inTransitCheckpoint, continuous.captureState());
        assertEquals(inTransitCheckpoint, resumed.captureState());

        double destinationMassBeforeKg = destinationCommodityMassKg(continuous, checkpointOrder);
        assertEquals(
                destinationMassBeforeKg,
                destinationCommodityMassKg(resumed, checkpointOrder),
                MASS_EPSILON_KG,
                "restore must preserve exact destination industrial inventory before delivery");

        continuous.setTimeScale(8d);
        resumed.setTimeScale(8d);
        Stage20GeneratedWorldRuntimePersistentState deliveredCheckpoint = null;
        int realSeconds = 0;
        while (realSeconds++ < FIRST_HOUR_AT_8X_REAL_SECONDS) {
            continuous.advanceFrame(1f);
            resumed.advanceFrame(1f);
            Stage20GeneratedWorldRuntimePersistentState candidate = resumed.captureState();
            if (order(candidate.freight(), orderId).deliveredMassKg() > deliveredMassBeforeKg) {
                deliveredCheckpoint = candidate;
                break;
            }
        }

        assertNotNull(deliveredCheckpoint,
                "the accepted first-hour generated campaign must physically deliver the in-flight cargo");
        assertTrue(realSeconds <= FIRST_HOUR_AT_8X_REAL_SECONDS);
        assertEquals(
                continuous.captureState(),
                deliveredCheckpoint,
                "save/restore continuation must produce the same authoritative delivery state");

        Stage20FreightPersistentState.TransportOrderState deliveredOrder =
                order(deliveredCheckpoint.freight(), orderId);
        Stage20FreightPersistentState.FreighterState deliveredFreighter =
                freighter(deliveredCheckpoint.freight(), fleetId);
        double deliveredDeltaKg = deliveredOrder.deliveredMassKg() - deliveredMassBeforeKg;
        double cargoDeltaKg = cargoMassBeforeKg - deliveredFreighter.cargoMassKg();
        double destinationMassAfterKg = destinationCommodityMassKg(resumed, deliveredOrder);

        assertTrue(deliveredDeltaKg > MASS_EPSILON_KG);
        assertEquals(fleetId, deliveredOrder.fleetId());
        assertEquals(initialOrder.sourceProvenanceId(), deliveredOrder.sourceProvenanceId());
        assertEquals(deliveredDeltaKg, cargoDeltaKg, MASS_EPSILON_KG,
                "delivered order mass must come from the same physical freight hold");
        assertTrue(destinationMassAfterKg > destinationMassBeforeKg,
                "delivery must increase the real destination Stage-18 storage");
        assertTrue(destinationMassAfterKg - destinationMassBeforeKg + MASS_EPSILON_KG >= deliveredDeltaKg,
                "destination inventory increase must account for the delivered physical mass");

        GeneratedCampaignSession deliveredRestore = GeneratedCampaignSession.restore(deliveredCheckpoint);
        assertEquals(deliveredCheckpoint, deliveredRestore.captureState(),
                "delivered order and destination inventory must survive another atomic checkpoint restore");
        assertEquals(
                destinationMassAfterKg,
                destinationCommodityMassKg(deliveredRestore, deliveredOrder),
                MASS_EPSILON_KG,
                "restored industrial storage must retain the delivered commodity mass");
    }

    private static Stage20FreightPersistentState.TransportOrderState order(
            Stage20FreightPersistentState freight,
            String orderId) {
        return freight.orders().stream()
                .filter(order -> order.orderId().equals(orderId))
                .findFirst()
                .orElseThrow();
    }

    private static Stage20FreightPersistentState.FreighterState freighter(
            Stage20FreightPersistentState freight,
            FleetId fleetId) {
        return freight.freighters().stream()
                .filter(freighter -> freighter.fleetId().equals(fleetId))
                .findFirst()
                .orElseThrow();
    }

    private static double destinationCommodityMassKg(
            GeneratedCampaignSession session,
            Stage20FreightPersistentState.TransportOrderState order) {
        return session.runtime().infrastructure()
                .endpoint(order.destinationEndpointId())
                .storage()
                .commodityMassKg(order.commodityId());
    }
}
