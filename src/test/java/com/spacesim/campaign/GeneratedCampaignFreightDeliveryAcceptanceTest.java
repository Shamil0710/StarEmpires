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
    private static final double FIRST_HOUR_SIMULATION_SECONDS = 3_600d;

    @Test
    void midTransitCheckpointPreservesSameCargoAndPhysicalProgressAcrossFirstHour() {
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
            inTransitLot = cargoLot(candidate.freight(), fleetId, orderId);
            boolean hasAuthoritativeJump = candidate.worldState().fleetJumps().stream()
                    .anyMatch(jump -> jump.fleetId().equals(fleetId));
            if (inTransitLot != null && hasAuthoritativeJump) {
                inTransitCheckpoint = candidate;
                break;
            }
        }

        assertNotNull(inTransitCheckpoint,
                "generated freight must expose a physical loaded FleetJumpState before continuation acceptance");
        assertNotNull(inTransitLot);
        assertEquals(initialOrder.sourceProvenanceId(), inTransitLot.sourceProvenanceId());

        var departureBefore = inTransitCheckpoint.worldState().fleetJumps().stream()
                .filter(jump -> jump.fleetId().equals(fleetId))
                .findFirst().orElseThrow();
        double fixedStepSeconds = bootstrap.runtime().world()
                .findSession(bootstrap.runtime().world().getActiveSystemId()).orElseThrow()
                .getClock().getFixedStepSeconds();
        long checkpointWorldTick = bootstrap.runtime().world().getAuthoritativeWorldTick();
        double remainingDepartureSecondsBefore =
                (departureBefore.phaseEndsTick() - checkpointWorldTick) * fixedStepSeconds;
        assertTrue(remainingDepartureSecondsBefore > FIRST_HOUR_SIMULATION_SECONDS,
                () -> "default generated convoy is expected to remain physically in departure after one hour: order="
                        + orderId + ", fleet=" + fleetId + ", remainingDepartureSeconds="
                        + remainingDepartureSecondsBefore + ", plannedOneWaySeconds="
                        + initialOrder.oneWayDeliverySeconds());

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
                "restore must preserve exact destination industrial inventory before continuation");

        continuous.setTimeScale(8d);
        resumed.setTimeScale(8d);
        for (int realSeconds = 0; realSeconds < FIRST_HOUR_AT_8X_REAL_SECONDS; realSeconds++) {
            continuous.advanceFrame(1f);
            resumed.advanceFrame(1f);
        }

        Stage20GeneratedWorldRuntimePersistentState continuousAfterHour = continuous.captureState();
        Stage20GeneratedWorldRuntimePersistentState resumedAfterHour = resumed.captureState();
        assertEquals(
                continuousAfterHour,
                resumedAfterHour,
                "save/restore continuation must produce the same authoritative state after one simulation hour");

        Stage20FreightPersistentState.TransportOrderState orderAfterHour =
                order(resumedAfterHour.freight(), orderId);
        Stage20FreightPersistentState.FreighterState freighterAfterHour =
                freighter(resumedAfterHour.freight(), fleetId);
        Stage20FreightPersistentState.CargoLotState lotAfterHour =
                cargoLot(resumedAfterHour.freight(), fleetId, orderId);
        assertNotNull(lotAfterHour, "same physical cargo lot must survive the first-hour checkpoint continuation");
        assertEquals(initialOrder.sourceProvenanceId(), lotAfterHour.sourceProvenanceId());
        assertEquals(Stage20FreightPersistentState.FreightPhase.OUTBOUND, freighterAfterHour.phase());
        assertEquals(cargoMassBeforeKg, freighterAfterHour.cargoMassKg(), MASS_EPSILON_KG,
                "cargo cannot disappear before the physical route reaches its destination");
        assertEquals(deliveredMassBeforeKg, orderAfterHour.deliveredMassKg(), MASS_EPSILON_KG,
                "a remote convoy still in departure must not report a virtual delivery");
        assertEquals(
                destinationMassBeforeKg,
                destinationCommodityMassKg(resumed, orderAfterHour),
                MASS_EPSILON_KG,
                "destination industrial storage must not gain cargo before physical delivery");

        var departureAfter = resumedAfterHour.worldState().fleetJumps().stream()
                .filter(jump -> jump.fleetId().equals(fleetId))
                .findFirst().orElseThrow();
        long worldTickAfter = resumed.runtime().world().getAuthoritativeWorldTick();
        double remainingDepartureSecondsAfter =
                (departureAfter.phaseEndsTick() - worldTickAfter) * fixedStepSeconds;
        assertTrue(remainingDepartureSecondsAfter < remainingDepartureSecondsBefore,
                "first-hour continuation must advance the same physical departure toward completion");
        assertEquals(
                FIRST_HOUR_SIMULATION_SECONDS,
                remainingDepartureSecondsBefore - remainingDepartureSecondsAfter,
                fixedStepSeconds * 2d,
                "one hour at 8x must advance exactly one simulation hour without changing route timing");
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

    private static Stage20FreightPersistentState.CargoLotState cargoLot(
            Stage20FreightPersistentState freight,
            FleetId fleetId,
            String orderId) {
        return freight.cargoLots().stream()
                .filter(lot -> lot.fleetId().equals(fleetId))
                .filter(lot -> lot.orderId().equals(orderId))
                .findFirst()
                .orElse(null);
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
