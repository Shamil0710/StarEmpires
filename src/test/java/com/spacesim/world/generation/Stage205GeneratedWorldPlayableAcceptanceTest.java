package com.spacesim.world.generation;

import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog;
import com.spacesim.simulation.GeneratedWorldFreightAutopilot;
import com.spacesim.ui.GeneratedWorldUiModel;
import com.spacesim.world.DestructionPolicy;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetJumpPhase;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage205GeneratedWorldPlayableAcceptanceTest {
    @Test
    void acceptedWorldRunsConservedFreightThroughOrdinaryJumpAndMidTransitSaveLoad() {
        LiveRuntime bootstrap = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        var ui = new GeneratedWorldUiModel(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED,
                bootstrap,
                bootstrap.world().findSession(bootstrap.world().getActiveSystemId())
                        .orElseThrow().getContentCatalog()).capture();
        assertEquals(bootstrap.world().getTopology().systems().size(), ui.galaxy().systems().size());
        assertFalse(ui.localObjects().isEmpty());
        assertTrue(ui.localObjects().stream().allMatch(value -> !value.sections().isEmpty()));
        assertEquals(bootstrap.freight().capture().freighters().size(), ui.freight().size());
        assertTrue(ui.freight().stream().allMatch(value -> !value.hullId().isBlank()
                && !value.fitId().isBlank() && !value.sections().isEmpty()));

        LiveRuntime automatic = Stage20GeneratedWorldRuntimeBridge.restore(bootstrap.captureState());
        var automaticReport = new GeneratedWorldFreightAutopilot(automatic).advance();
        assertTrue(automaticReport.changedState());
        long canonicalStationCount = bootstrap.captureState().campaign().materializedWorld()
                .worldRows().stream()
                .filter(row -> row.domain().equals("INFRASTRUCTURE_PLACEMENT"))
                .filter(row -> row.values().size() >= 3 && !row.values().get(2).isBlank())
                .count();
        assertEquals(canonicalStationCount, bootstrap.infrastructure().endpoints().size());
        Candidate candidate = candidate(bootstrap);
        FleetId fleetId = candidate.order().fleetId();
        int materializedFleetCount = bootstrap.freight().capture().freighters().size();

        assertEquals(materializedFleetCount, bootstrap.freight().capture().freighters().stream()
                .filter(value -> bootstrap.world().findFleet(value.fleetId()).isPresent())
                .count());
        var sprite = bootstrap.freightSprite(fleetId);
        assertEquals(Stage20MinimumPlayableSpriteCatalog.VisualRole.CARGO_TRANSPORT_SHIP,
                sprite.binding().role());
        assertEquals(Stage20MinimumPlayableSpriteCatalog.ScaleAuthority.EXACT_PHYSICAL_CONTENT,
                sprite.scaleAuthority());

        double reserveBefore = candidate.outpost().source().sourceState().remainingAccessibleMassKg();
        var extraction = bootstrap.extract(candidate.outpost().site().siteId(), 1d, 60d);
        assertTrue(extraction.committed());
        double cargoMass = extraction.outputMassStoredKg();
        assertTrue(cargoMass > 0d);
        assertTrue(bootstrap.transferOutpostToOrderSource(
                fleetId, candidate.outpost().site().siteId(), cargoMass, 60d).transferred());
        assertTrue(bootstrap.loadAtOrderSource(fleetId, cargoMass, 60d, 60d).transferred());
        assertEquals(reserveBefore - extraction.sourceMassRemovedKg(),
                candidate.outpost().source().sourceState().remainingAccessibleMassKg(), 0d);
        assertEquals(cargoMass, bootstrap.freight().findFreighter(fleetId).orElseThrow()
                .cargoMassKg(), 0d);

        Stage20GeneratedWorldRuntimePersistentState loadedAtSource = bootstrap.captureState();
        byte[] sourceBytes = Stage20GeneratedWorldRuntimePersistenceCodec.encode(loadedAtSource);
        assertArrayEquals(sourceBytes,
                Stage20GeneratedWorldRuntimePersistenceCodec.encode(loadedAtSource));
        assertEquals(loadedAtSource,
                Stage20GeneratedWorldRuntimePersistenceCodec.decode(sourceBytes));
        assertThrows(IllegalArgumentException.class, () ->
                Stage20GeneratedWorldRuntimePersistenceCodec.decode(
                        Arrays.copyOf(sourceBytes, sourceBytes.length - 1)));

        LiveRuntime destroyedBranch = Stage20GeneratedWorldRuntimeBridge.restore(
                Stage20GeneratedWorldRuntimePersistenceCodec.decode(sourceBytes));
        var destruction = destroyedBranch.destroyLocalFreighter(fleetId, DestructionPolicy.destroyAll());
        assertTrue(destruction.freightResult().destroyedNow());
        assertEquals(cargoMass, destruction.freightResult().lostCargoMassKg(), 0d);
        assertTrue(destroyedBranch.world().findFleet(fleetId).isEmpty());
        assertEquals(FreightPhase.DESTROYED,
                destroyedBranch.freight().findFreighter(fleetId).orElseThrow().phase());
        assertEquals(materializedFleetCount, destroyedBranch.freight().capture().freighters().size());
        Stage20GeneratedWorldRuntimePersistentState destroyedCheckpoint =
                destroyedBranch.captureState();
        assertTrue(destroyedCheckpoint.worldState().fleets().stream()
                .noneMatch(value -> value.id().equals(fleetId)));

        LiveRuntime delivery = Stage20GeneratedWorldRuntimeBridge.restore(
                Stage20GeneratedWorldRuntimePersistenceCodec.decode(sourceBytes));
        assertEquals(sprite, delivery.freightSprite(fleetId));
        delivery.freight().dispatchOutbound(fleetId, 120d);
        boolean restoredInTransit = false;
        for (int hop = 1; hop < candidate.order().orderedSystems().size(); hop++) {
            StarSystemId origin = candidate.order().orderedSystems().get(hop - 1);
            StarSystemId destination = candidate.order().orderedSystems().get(hop);
            var expectedArrival = delivery.arrival().resolve(origin, destination).physicalState();
            delivery.requestNextRouteHop(fleetId);
            if (!restoredInTransit) {
                advanceUntilPhase(delivery, fleetId, FleetJumpPhase.IN_TRANSIT);
                Stage20GeneratedWorldRuntimePersistentState midTransit = delivery.captureState();
                assertEquals(FleetLocationKind.IN_TRANSIT,
                        midTransit.worldState().fleets().stream()
                                .filter(value -> value.id().equals(fleetId))
                                .findFirst().orElseThrow().locationKind());
                delivery = Stage20GeneratedWorldRuntimeBridge.restore(
                        Stage20GeneratedWorldRuntimePersistenceCodec.decode(
                                Stage20GeneratedWorldRuntimePersistenceCodec.encode(midTransit)));
                restoredInTransit = true;
            }
            advanceUntilJumpComplete(delivery, fleetId);
            var placement = delivery.world().findFleet(fleetId).orElseThrow();
            assertEquals(destination, placement.systemId());
            assertEquals(expectedArrival,
                    delivery.arrival().materialization(destination)
                            .physicalState(placement.localEntityId()).orElseThrow());
            assertEquals(expectedArrival,
                    delivery.freight().findFreighter(fleetId).orElseThrow().physicalState());
        }
        assertTrue(restoredInTransit);
        assertEquals(FreightPhase.AT_DESTINATION,
                delivery.freight().findFreighter(fleetId).orElseThrow().phase());

        double destinationBefore = delivery.infrastructure()
                .endpoint(candidate.order().destinationEndpointId())
                .storage().commodityMassKg(candidate.order().commodityId());
        assertTrue(delivery.unloadAtOrderDestination(fleetId, cargoMass, 60d).transferred());
        assertEquals(destinationBefore + cargoMass,
                delivery.infrastructure().endpoint(candidate.order().destinationEndpointId())
                        .storage().commodityMassKg(candidate.order().commodityId()), 0d);
        assertEquals(0d, delivery.freight().findFreighter(fleetId).orElseThrow().cargoMassKg(), 0d);
        assertTrue(delivery.freight().cargoLots().isEmpty());
        assertEquals(cargoMass,
                delivery.freight().findOrder(candidate.order().orderId()).orElseThrow()
                        .deliveredMassKg(), 0d);

        Stage20GeneratedWorldRuntimePersistentState finalState = delivery.captureState();
        LiveRuntime finalRestore = Stage20GeneratedWorldRuntimeBridge.restore(
                Stage20GeneratedWorldRuntimePersistenceCodec.decode(
                        Stage20GeneratedWorldRuntimePersistenceCodec.encode(finalState)));
        assertEquals(fleetId,
                finalRestore.world().findFleet(fleetId).orElseThrow().id());
        assertEquals(finalState.freight(), finalRestore.freight().capture());
        assertFalse(finalRestore.world().getFleetPlacements().isEmpty());
    }

    private static Candidate candidate(LiveRuntime runtime) {
        for (var order : runtime.freight().capture().orders()) {
            for (var outpost : runtime.industry().sourceOutposts().outposts()) {
                if (outpost.site().systemId().equals(order.orderedSystems().get(0))
                        && outpost.source().sourceState().outputCommodityId()
                        .equals(order.commodityId())) {
                    return new Candidate(order, outpost);
                }
            }
        }
        throw new AssertionError("accepted generated fixture lacks a source-backed freight order");
    }

    private static void advanceUntilPhase(
            LiveRuntime runtime,
            FleetId fleetId,
            FleetJumpPhase phase) {
        for (int attempt = 0; attempt < 2_000; attempt++) {
            if (runtime.world().findFleetJump(fleetId).orElseThrow().phase() == phase) {
                return;
            }
            runtime.advanceFrame(0.25f);
        }
        throw new AssertionError("ordinary freight jump did not reach phase " + phase);
    }

    private static void advanceUntilJumpComplete(LiveRuntime runtime, FleetId fleetId) {
        for (int attempt = 0; attempt < 20_000; attempt++) {
            if (runtime.world().findFleetJump(fleetId).isEmpty()) {
                return;
            }
            runtime.advanceFrame(0.25f);
        }
        throw new AssertionError("ordinary freight jump did not complete");
    }

    private record Candidate(
            com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState order,
            com.spacesim.persistence.Stage20SourceOutpostMaterializer.MaterializedExtractionOutpost
                    outpost) { }
}
