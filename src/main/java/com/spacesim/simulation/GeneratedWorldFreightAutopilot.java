package com.spacesim.simulation;

import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.StarSystemId;

import java.util.Objects;

/**
 * Ordinary-runtime freight circulation used by the generated-world campaign.
 *
 * <p>The service does not invent cargo, routes, owners, fleets or arrival coordinates. It consumes
 * accepted orders, existing station inventory, the ordinary diplomatic market-access resolver and
 * finite generated extraction sources through the same Stage-18/20.5 APIs covered by final
 * acceptance. Canonical infrastructure in unclaimed space remains neutral rather than receiving an
 * invented faction owner; controlled and generated-industrial endpoints use the existing diplomatic
 * authority. One call performs at most one lifecycle operation per freighter. Every extraction and
 * cargo-transfer budget is derived from the explicit simulation-time interval supplied by the
 * campaign orchestrator, never from wall-clock time or render cadence.</p>
 */
public final class GeneratedWorldFreightAutopilot {
    private static final double MASS_EPSILON_KG = 1.0e-9d;

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
     * <p>The caller owns cadence. The supplied interval is also the only handling/extraction time
     * budget granted to this decision, so repeated decisions cannot smuggle extra physical time into
     * the logistics authority.</p>
     *
     * @param elapsedSimulationSeconds positive simulation-time interval represented by this decision
     * @return deterministic operation counters for diagnostics
     */
    public ActionReport advance(double elapsedSimulationSeconds) {
        if (!Double.isFinite(elapsedSimulationSeconds) || elapsedSimulationSeconds <= 0d) {
            throw new IllegalArgumentException(
                    "Freight autonomy interval must be positive finite simulation time");
        }
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
                    TransportOrderState order = runtime.freight()
                            .findOrder(fleet.activeOrderId()).orElseThrow();
                    if (!hasLegalOrderAccess(fleet, order)) {
                        continue;
                    }
                    var endpoint = runtime.infrastructure().endpoint(order.sourceEndpointId());
                    FreighterState current = runtime.freight().findFreighter(fleet.fleetId()).orElseThrow();
                    double remainingCapacityKg = Math.max(
                            0d, current.cargoCapacityKg() - current.cargoMassKg());
                    if (remainingCapacityKg > MASS_EPSILON_KG) {
                        if (endpoint.storage().commodityMassKg(order.commodityId()) <= MASS_EPSILON_KG) {
                            StarSystemId sourceSystemId = current.currentSystemId();
                            var outpost = runtime.industry().sourceOutposts().outposts().stream()
                                    .filter(value -> value.site().systemId().equals(sourceSystemId))
                                    .filter(value -> value.source().sourceState().outputCommodityId()
                                            .equals(order.commodityId()))
                                    .findFirst().orElse(null);
                            if (outpost != null) {
                                double stagingRateKgPerSecond = Math.min(
                                        outpost.stationNode().handlingCapability().massRateKgPerSecond(),
                                        endpoint.handlingCapability().massRateKgPerSecond());
                                double extractionRequestKg = Math.min(
                                        remainingCapacityKg,
                                        stagingRateKgPerSecond * elapsedSimulationSeconds);
                                if (extractionRequestKg > MASS_EPSILON_KG) {
                                    var extraction = runtime.extract(
                                            outpost.site().siteId(), extractionRequestKg,
                                            elapsedSimulationSeconds);
                                    if (extraction.committed() && extraction.outputMassStoredKg() > 0d) {
                                        var staged = runtime.transferOutpostToOrderSource(
                                                fleet.fleetId(),
                                                outpost.site().siteId(),
                                                extraction.outputMassStoredKg(),
                                                elapsedSimulationSeconds);
                                        if (staged.transferred()) {
                                            extracted++;
                                        }
                                    }
                                }
                            }
                        }
                        current = runtime.freight().findFreighter(fleet.fleetId()).orElseThrow();
                        remainingCapacityKg = Math.max(
                                0d, current.cargoCapacityKg() - current.cargoMassKg());
                        double availableKg = endpoint.storage().commodityMassKg(order.commodityId());
                        double handlingBudgetKg = endpoint.handlingCapability().massRateKgPerSecond()
                                * elapsedSimulationSeconds;
                        double loadMassKg = Math.min(
                                remainingCapacityKg, Math.min(availableKg, handlingBudgetKg));
                        if (loadMassKg > MASS_EPSILON_KG && runtime.loadAtOrderSource(
                                fleet.fleetId(), loadMassKg, simulationSeconds(),
                                elapsedSimulationSeconds).transferred()) {
                            loaded++;
                        }
                    }
                    current = runtime.freight().findFreighter(fleet.fleetId()).orElseThrow();
                    if (current.cargoMassKg() + MASS_EPSILON_KG >= current.cargoCapacityKg()) {
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
                    if (fleet.cargoMassKg() > MASS_EPSILON_KG) {
                        var endpoint = runtime.infrastructure().endpoint(
                                runtime.freight().findOrder(fleet.activeOrderId()).orElseThrow()
                                        .destinationEndpointId());
                        double handlingBudgetKg = endpoint.handlingCapability().massRateKgPerSecond()
                                * elapsedSimulationSeconds;
                        double unloadMassKg = Math.min(fleet.cargoMassKg(), handlingBudgetKg);
                        if (unloadMassKg > MASS_EPSILON_KG && runtime.unloadAtOrderDestination(
                                fleet.fleetId(), unloadMassKg,
                                elapsedSimulationSeconds).transferred()) {
                            unloaded++;
                        }
                    }
                    FreighterState current = runtime.freight().findFreighter(fleet.fleetId()).orElseThrow();
                    if (current.cargoMassKg() <= MASS_EPSILON_KG) {
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

    private boolean hasLegalOrderAccess(FreighterState fleet, TransportOrderState order) {
        return hasLegalEndpointAccess(order.sourceEndpointId(), fleet.stableFactionId())
                && hasLegalEndpointAccess(order.destinationEndpointId(), fleet.stableFactionId());
    }

    private boolean hasLegalEndpointAccess(String endpointId, String participantFactionId) {
        var endpoint = runtime.infrastructure().endpoint(endpointId);
        String owner = endpoint.generatedIndustrial()
                ? industrialStationOwner(endpointId)
                : runtime.world().controllingFaction(endpoint.systemId()).orElse(null);
        if (owner == null) {
            // Canonical infrastructure in unclaimed space is neutral. There is no faction market
            // owner whose persistent diplomacy could legally grant or deny access.
            return true;
        }
        return runtime.world().evaluateFactionMarketAccess(owner, participantFactionId).allowed();
    }

    private String industrialStationOwner(String stationId) {
        return runtime.industry().industrial().stations().stream()
                .filter(value -> value.stationId().equals(stationId))
                .map(value -> value.stableFactionId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "generated industrial endpoint lacks its accepted owner: " + stationId));
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
        /**
         * Validates non-negative operation counts.
         *
         * @param loaded cargo-load operations committed
         * @param unloaded cargo-unload operations committed
         * @param dispatched outbound/return phases started
         * @param jumpsRequested ordinary neighbor jumps requested
         * @param extracted finite extraction/staging operations committed
         */
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
