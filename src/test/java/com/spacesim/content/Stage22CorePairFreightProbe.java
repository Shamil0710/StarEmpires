package com.spacesim.content;

import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.content.Stage22CorePairExperimentProtocol.RunCoordinate;
import com.spacesim.economy.Stage18LogisticsRuntime.HandlingCapability;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.persistence.Stage20FreightPersistenceCodec;
import com.spacesim.persistence.Stage20FreightPersistentState;
import com.spacesim.persistence.Stage20FreightPersistentState.AssignmentKind;
import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.persistence.Stage20FreightRuntime;
import com.spacesim.world.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Controlled B05 authority probe; the two finite routes and all starting stock are explicit inputs. */
final class Stage22CorePairFreightProbe {
    private static final String WATER = "commodity.material.purified_water";
    private static final String STORAGE = "storage.liquid_tank";
    private static final StarSystemId A = new StarSystemId(1);
    private static final StarSystemId HUB = new StarSystemId(2);
    private static final StarSystemId ALTERNATE = new StarSystemId(3);
    private static final StarSystemId B = new StarSystemId(4);
    private static final double CAPACITY_KG = 100_000d;

    private Stage22CorePairFreightProbe() { }

    static Stage22CorePairMachineEvidenceBatch.ObservationPayload observe(RunCoordinate coordinate) {
        Map<String, Double> metrics = new TreeMap<>();
        List<String> breaches = new ArrayList<>();
        boolean mirrored = coordinate.permutation() == Permutation.MIRRORED;
        // The shock always strikes the same physical slot. Mirroring swaps owners and route ends.
        String affectedOwner = mirrored ? Stage22CorePairBalanceEvidence.UNION_FACTION_ID
                : Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID;
        StarSystemId sourceSystem = mirrored ? B : A;
        StarSystemId destinationSystem = mirrored ? A : B;
        List<StarSystemId> primary = List.of(sourceSystem, HUB, destinationSystem);
        List<StarSystemId> alternate = List.of(sourceSystem, ALTERNATE, destinationSystem);
        // This is a declared load sensitivity sweep, not gameplay randomness or a faction modifier.
        double loadKg = 10_000d + Math.floorMod(coordinate.seed(), 11L) * 1_000d;
        Stage20FreightRuntime freight = fresh(coordinate.seed(), affectedOwner, primary, alternate);
        Stage18StationStorage source = storage("b05.source", 2d * loadKg);
        Stage18StationStorage destination = storage("b05.destination", 0d);
        HandlingCapability handling = new HandlingCapability("b05.handling", Set.of(STORAGE), CAPACITY_KG, CAPACITY_KG);
        for (long id : new long[] { 1L, 2L }) {
            var order = freight.findOrder("b05.order." + id).orElseThrow();
            require(freight.loadCommodity(new FleetId(id), source, loadKg, order.sourceProvenanceId(),
                    0d, handling, handling.openInterval(1d)).transferred(), "finite_load", breaches);
            freight.dispatchOutbound(new FleetId(id), 0d);
        }
        byte[] checkpoint = Stage20FreightPersistenceCodec.encode(freight.capture());
        Stage20FreightRuntime restored = Stage20FreightRuntime.restore(Stage20FreightPersistenceCodec.decode(checkpoint));
        require(Arrays.equals(checkpoint, Stage20FreightPersistenceCodec.encode(restored.capture())),
                "freight_byte_round_trip", breaches);

        var loss = freight.destroy(new FleetId(1L));
        var replayLoss = restored.destroy(new FleetId(1L));
        require(loss.equals(replayLoss), "loss_replay", breaches);
        require(loss.destroyedNow() && loss.lostCargoMassKg() == loadKg && loss.lostLots().size() == 1,
                "physical_loss_and_provenance", breaches);
        require(!freight.destroy(new FleetId(1L)).destroyedNow(), "loss_idempotent", breaches);
        require(freight.capture().nextFleetIdValue() == 3L && freight.capture().freighters().size() == 2,
                "no_replacement", breaches);
        require(freight.findOrder("b05.order.1").orElseThrow().deliveredMassKg() == 0d,
                "lost_cargo_not_delivered", breaches);
        byte[] destroyedCheckpoint = Stage20FreightPersistenceCodec.encode(freight.capture());
        require(Stage20FreightRuntime.restore(Stage20FreightPersistenceCodec.decode(destroyedCheckpoint))
                .findFreighter(new FleetId(1L)).orElseThrow().phase() == FreightPhase.DESTROYED,
                "destroyed_state_survives_load", breaches);
        try {
            freight.dispatchOutbound(new FleetId(1L), 1d);
            breaches.add("destroyed_fleet_dispatched");
        } catch (IllegalStateException expected) {
            // A destroyed physical asset cannot resume its old route.
        }

        // Stage-21D observes the explicit hub outage. Planning does not rewrite the freight sidecar.
        var planner = new FleetStrategicRoutePlanner(topology());
        var route = planner.plan(7, sourceSystem, destinationSystem, 1L,
                (owner, from, to, tick, finalHop) -> !to.equals(HUB)).orElseThrow();
        require(route.systems().equals(alternate), "neighbor_only_alternate_route", breaches);
        for (int index = 1; index < alternate.size(); index++) {
            var position = new LocalPhysicalKinematics(
                    new LocalPhysicalPosition(0, 0, index * 100d, 0d), 0d, 0d);
            freight.completeNextOutboundHop(new FleetId(2L), alternate.get(index), position);
            restored.completeNextOutboundHop(new FleetId(2L), alternate.get(index), position);
        }
        var unloaded = freight.unloadCommodity(new FleetId(2L), destination, loadKg,
                handling, handling.openInterval(1d));
        Stage18StationStorage replayDestination = storage("b05.destination", 0d);
        restored.unloadCommodity(new FleetId(2L), replayDestination, loadKg, handling, handling.openInterval(1d));
        require(unloaded.transferred(), "surviving_route_delivers", breaches);
        require(Arrays.equals(Stage20FreightPersistenceCodec.encode(freight.capture()),
                Stage20FreightPersistenceCodec.encode(restored.capture())), "continuation_exact", breaches);
        require(source.commodityMassKg(WATER) + destination.commodityMassKg(WATER)
                + loss.lostCargoMassKg() == 2d * loadKg, "mass_conserved_with_explicit_loss", breaches);
        require(planner.plan(7, sourceSystem, destinationSystem, 1L,
                (owner, from, to, tick, finalHop) -> !to.equals(HUB) && !to.equals(ALTERNATE)).isEmpty(),
                "all_routes_denied", breaches);

        metrics.put("starting_stock_kg", 2d * loadKg);
        metrics.put("lost_cargo_kg", loss.lostCargoMassKg());
        metrics.put("delivered_cargo_kg", destination.commodityMassKg(WATER));
        metrics.put("remaining_owned_freighters", (double) freight.capture().freighters().stream()
                .filter(FreighterState::operational).count());
        metrics.put("alternate_neighbor_hops", (double) route.hopCount());
        metrics.put("lost_lot_provenance_rows", (double) loss.lostLots().size());
        return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(metrics,
                Map.of("freight_invariants", breaches.isEmpty() ? 1d : 0d), breaches);
    }

    private static Stage20FreightRuntime fresh(long seed, String owner,
            List<StarSystemId> primary, List<StarSystemId> alternate) {
        List<FreighterState> fleets = new ArrayList<>();
        List<TransportOrderState> orders = new ArrayList<>();
        String faction = owner.equals(Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID) ? "empire" : "industrial_union";
        for (int index = 0; index < 2; index++) {
            FleetId fleetId = new FleetId(index + 1L);
            String orderId = "b05.order." + fleetId.value();
            List<StarSystemId> route = index == 0 ? primary : alternate;
            fleets.add(new FreighterState(fleetId, owner, index, "hull." + faction + "_freight_v1",
                    "fit." + faction + ".freight.bulk_v1", CAPACITY_KG, route.get(0),
                    new LocalPhysicalKinematics(new LocalPhysicalPosition(0, 0, 0d, 0d), 0d, 0d),
                    FreightPhase.AT_SOURCE, orderId, 0,
                    storage(Stage20FreightPersistentState.cargoHoldId(fleetId), 0d).snapshot()));
            orders.add(new TransportOrderState(orderId, fleetId, owner, AssignmentKind.INDUSTRIAL_INPUT,
                    WATER, "b05.source", "b05.destination", "b05.paid_starting_inventory", route,
                    100d, 250d, 100d, 0d, 0L));
        }
        return Stage20FreightRuntime.restore(new Stage20FreightPersistentState(
                Stage20FreightPersistentState.CURRENT_VERSION, seed, "b05.controlled_routes.v1",
                "b05.two_routes.explicit_stock.v1", "b05.fixture.v1", "stage22.authored_freight.v1",
                3L, 1L, fleets, List.of(), orders));
    }

    private static Stage18StationStorage storage(String id, double waterKg) {
        return new Stage18StationStorage(Stage18ResourceOntologyLoader.loadDefault(),
                Stage18ManufacturingProductRegistry.loadDefault(), id, Map.of(STORAGE, CAPACITY_KG),
                waterKg == 0d ? Map.of() : Map.of(WATER, waterKg), Map.of());
    }

    private static GalaxyTopology topology() {
        return new GalaxyTopology(new GalaxyId(22605), "B05 explicit two-route fixture",
                List.of(new SectorNode(new SectorId(1), "Core", List.of(
                        new StarSystemNode(A, "Source", 0d, 0d), new StarSystemNode(HUB, "Hub", 100d, -50d),
                        new StarSystemNode(ALTERNATE, "Alternate", 100d, 50d), new StarSystemNode(B, "Sink", 200d, 0d)))),
                List.of(new JumpConnection(A, HUB), new JumpConnection(HUB, B),
                        new JumpConnection(A, ALTERNATE), new JumpConnection(ALTERNATE, B)));
    }

    private static void require(boolean condition, String invariant, List<String> breaches) {
        if (!condition) breaches.add(invariant);
    }

    public static void main(String[] args) {
        var result = Stage22CorePairMachineEvidenceBatch.runScenario("B05", "finite_freight_loss",
                "two_routes.v1", Stage22CorePairExperimentProtocol.releaseCandidateSchedule(),
                (scenario, variant, profile, coordinate) -> observe(coordinate));
        if (result.hardRuleBreachCount() != 0) throw new AssertionError(result);
        System.out.println("B05|" + result.evidenceFingerprint() + "|" + new TreeMap<>(result.metricMeans()));
    }
}
