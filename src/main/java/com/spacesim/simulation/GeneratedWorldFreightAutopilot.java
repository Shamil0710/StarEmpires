package com.spacesim.simulation;

import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.world.FleetLocationKind;

import java.util.Objects;

/**
 * Minimal ordinary-runtime freight circulation used by the generated-world playable viewer.
 *
 * <p>The service does not invent cargo, routes, owners, fleets or arrival coordinates. It consumes
 * accepted orders, existing station inventory and finite generated extraction sources through the
 * same Stage-18/20.5 APIs covered by final acceptance. One call performs at most one lifecycle
 * operation per freighter, keeping rendering cadence separate from simulation authority.</p>
 */
public final class GeneratedWorldFreightAutopilot {
    private static final double HANDLING_INTERVAL_SECONDS = 60d;
    private static final double MAX_CYCLE_LOAD_KG = 1d;

    private final LiveRuntime runtime;

    /**
     * Binds the circulation policy to one live generated-world runtime.
     *
     * @param runtime authoritative generated-world runtime
     */
    public GeneratedWorldFreightAutopilot(LiveRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    /**
     * Advances eligible accepted transport orders by one ordinary lifecycle action.
     *
     * @return deterministic operation counters for UI diagnostics
     */
    public ActionReport advance() {
        int loaded = 0;
        int unloaded = 0;
        int dispatched = 0;
        int jumpsRequested = 0;
        int extracted = 0;
        for (FreighterState fleet : runtime.freight().capture().freighters()) {
            if (fleet.phase() == FreightPhase.IDLE || fleet.phase() == FreightPhase.DESTROYED) {
                continue;
            }
            switch (fleet.phase()) {
                case AT_SOURCE -> {
                    if (fleet.cargoMassKg() <= 0d) {
                        var order = runtime.freight().findOrder(fleet.activeOrderId()).orElseThrow();
                        var endpoint = runtime.infrastructure().endpoint(order.sourceEndpointId());
                        if (endpoint.storage().commodityMassKg(order.commodityId()) <= 0d) {
                            var outpost = runtime.industry().sourceOutposts().outposts().stream()
                                    .filter(value -> value.site().systemId().equals(fleet.currentSystemId()))
                                    .filter(value -> value.source().sourceState().outputCommodityId()
                                            .equals(order.commodityId()))
                                    .findFirst().orElse(null);
                            if (outpost != null) {
                                var extraction = runtime.extract(
                                        outpost.site().siteId(), MAX_CYCLE_LOAD_KG, HANDLING_INTERVAL_SECONDS);
                                if (extraction.committed() && extraction.outputMassStoredKg() > 0d) {
                                    var staged = runtime.transferOutpostToOrderSource(
                                            fleet.fleetId(),
                                            outpost.site().siteId(),
                                            extraction.outputMassStoredKg(),
                                            HANDLING_INTERVAL_SECONDS);
                                    if (staged.transferred()) {
                                        extracted++;
                                    }
                                }
                            }
                        }
                        double available = endpoint.storage().commodityMassKg(order.commodityId());
                        double mass = Math.min(MAX_CYCLE_LOAD_KG,
                                Math.min(available, fleet.cargoCapacityKg()));
                        if (mass > 0d && runtime.loadAtOrderSource(
                                fleet.fleetId(), mass, simulationSeconds(),
                                HANDLING_INTERVAL_SECONDS).transferred()) {
                            loaded++;
                        }
                    }
                    FreighterState current = runtime.freight().findFreighter(fleet.fleetId()).orElseThrow();
                    if (current.cargoMassKg() > 0d) {
                        runtime.freight().dispatchOutbound(fleet.fleetId(), simulationSeconds());
                        dispatched++;
                    }
                }
                case OUTBOUND, RETURNING -> {
                    var placement = runtime.world().findFleet(fleet.fleetId()).orElse(null);
                    if (placement != null
                            && placement.locationKind() == FleetLocationKind.IN_SYSTEM
                            && runtime.world().findFleetJump(fleet.fleetId()).isEmpty()) {
                        runtime.requestNextRouteHop(fleet.fleetId());
                        jumpsRequested++;
                    }
                }
                case AT_DESTINATION -> {
                    if (fleet.cargoMassKg() > 0d && runtime.unloadAtOrderDestination(
                            fleet.fleetId(), fleet.cargoMassKg(),
                            HANDLING_INTERVAL_SECONDS).transferred()) {
                        unloaded++;
                    }
                    FreighterState current = runtime.freight().findFreighter(fleet.fleetId()).orElseThrow();
                    if (current.cargoMassKg() <= 0d) {
                        runtime.freight().dispatchReturn(fleet.fleetId());
                        dispatched++;
                    }
                }
                default -> {
                    // IDLE and DESTROYED are filtered above; enum exhaustiveness is retained here.
                }
            }
        }
        return new ActionReport(loaded, unloaded, dispatched, jumpsRequested, extracted);
    }

    private double simulationSeconds() {
        var world = runtime.world();
        var clock = world.findSession(world.getActiveSystemId()).orElseThrow().getClock();
        return world.getAuthoritativeWorldTick() * (double) clock.getFixedStepSeconds();
    }

    /**
     * Per-call circulation diagnostics.
     *
     * @param loaded cargo-load operations committed
     * @param unloaded cargo-unload operations committed
     * @param dispatched outbound/return phases started
     * @param jumpsRequested ordinary neighbor jumps requested
     * @param extracted finite extraction/staging operations committed
     */
    public record ActionReport(
            int loaded,
            int unloaded,
            int dispatched,
            int jumpsRequested,
            int extracted) {
        /** Validates non-negative operation counts. */
        public ActionReport {
            if (loaded < 0 || unloaded < 0 || dispatched < 0
                    || jumpsRequested < 0 || extracted < 0) {
                throw new IllegalArgumentException("Autopilot operation counts cannot be negative");
            }
        }

        /** @return whether this call changed any freight/industrial state */
        public boolean changedState() {
            return loaded + unloaded + dispatched + jumpsRequested + extracted > 0;
        }
    }
}
