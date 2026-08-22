package com.spacesim.world;

import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.ReferenceDefinition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Stage-20D/E adapter from explicit fitted jump routes to sustainable representative freight cycles.
 *
 * <p>The adapter deliberately does not own a second movement model. Inter-system time comes only
 * from the supplied {@link Stage20PhysicalGalacticRoutePlanner} instances. Local endpoint travel and
 * cargo handling are explicit caller inputs because the current Stage-20C/18 data does not provide
 * one universal loading/unloading time for every possible extraction site and station pairing.</p>
 *
 * <p>Sustainable throughput is a repeated-cycle diagnostic, not a reservation. Both outbound and
 * return routes must exist. Final FTL cooldown is included through {@code estimatedReadyAgainSeconds}
 * on each leg so the same representative ship can repeat the route without silently resetting its
 * jump hardware.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20PhysicalFreightRouteEvaluator
        implements Stage20EconomicBootstrapValidator.RouteEvaluator {
    private final Stage20PhysicalGalacticRoutePlanner loadedOutboundPlanner;
    private final Stage20PhysicalGalacticRoutePlanner returnPlanner;
    private final FreightFleetProfile fleetProfile;
    private final EndpointCycleProvider endpointProvider;

    /**
     * Creates a physical freight evaluator.
     *
     * @param loadedOutboundPlanner fitted route planner for the loaded outbound state
     * @param returnPlanner fitted route planner for the return state
     * @param fleetProfile explicit representative freight allocation
     * @param endpointProvider physical local-access and cargo-handling consequences
     */
    public Stage20PhysicalFreightRouteEvaluator(
            Stage20PhysicalGalacticRoutePlanner loadedOutboundPlanner,
            Stage20PhysicalGalacticRoutePlanner returnPlanner,
            FreightFleetProfile fleetProfile,
            EndpointCycleProvider endpointProvider) {
        this.loadedOutboundPlanner = Objects.requireNonNull(loadedOutboundPlanner, "loadedOutboundPlanner");
        this.returnPlanner = Objects.requireNonNull(returnPlanner, "returnPlanner");
        this.fleetProfile = Objects.requireNonNull(fleetProfile, "fleetProfile");
        this.endpointProvider = Objects.requireNonNull(endpointProvider, "endpointProvider");
    }

    /**
     * Evaluates one repeatable supply route using the profile's full route-level freight allocation.
     *
     * @param origin producer system
     * @param destination consumer system
     * @return forward route, delivery time and sustainable fleet throughput, or empty when any
     *         physical leg/endpoint consequence is unavailable
     */
    @Override
    public Optional<Stage20EconomicBootstrapValidator.RouteAssessment> assess(
            StarSystemId origin,
            StarSystemId destination) {
        return assessWithAllocatedFreighters(origin, destination, fleetProfile.activeFreighterCount());
    }

    /**
     * Evaluates the same physical route with an explicit bounded subset of the configured freighters.
     *
     * <p>This is an allocation diagnostic seam, not a fleet-creation API. The requested count must be
     * positive and cannot exceed {@link FreightFleetProfile#activeFreighterCount()}. Route geometry,
     * FTL timing, endpoint access, handling limits and payload remain identical to {@link #assess};
     * only the number of already-authorized identical freighters assigned to this route changes.</p>
     *
     * @param origin producer system
     * @param destination consumer system
     * @param allocatedFreighterCount number of configured freighters assigned to this route
     * @return route assessment under the explicit allocation, or empty when the route is unresolved
     */
    public Optional<Stage20EconomicBootstrapValidator.RouteAssessment> assessWithAllocatedFreighters(
            StarSystemId origin,
            StarSystemId destination,
            int allocatedFreighterCount) {
        return assessCycleWithAllocatedFreighters(origin, destination, allocatedFreighterCount)
                .map(FreightCycleAssessment::routeAssessment);
    }

    /**
     * Evaluates and retains every physical component of one repeatable freight cycle.
     *
     * <p>The historical route assessment intentionally exposes only delivery time and sustainable
     * throughput. Stage 20J additionally needs the load/unload overhead and ready-again round trip,
     * so this projection retains the exact already-computed components without introducing another
     * route or cadence model.</p>
     *
     * @param origin producer system
     * @param destination consumer system
     * @param allocatedFreighterCount number of configured freighters assigned to the route
     * @return detailed physical cycle, or empty when an endpoint or route is unavailable
     */
    public Optional<FreightCycleAssessment> assessCycleWithAllocatedFreighters(
            StarSystemId origin,
            StarSystemId destination,
            int allocatedFreighterCount) {
        if (allocatedFreighterCount <= 0 || allocatedFreighterCount > fleetProfile.activeFreighterCount()) {
            throw new IllegalArgumentException(
                    "allocatedFreighterCount must be in 1.." + fleetProfile.activeFreighterCount());
        }
        StarSystemId checkedOrigin = Objects.requireNonNull(origin, "origin");
        StarSystemId checkedDestination = Objects.requireNonNull(destination, "destination");
        Optional<EndpointCycleProfile> maybeEndpoint = endpointProvider.profile(checkedOrigin, checkedDestination);
        if (maybeEndpoint.isEmpty()) {
            return Optional.empty();
        }
        Optional<Stage20PhysicalGalacticRoute> forward = loadedOutboundPlanner.findPath(
                checkedOrigin, checkedDestination);
        Optional<Stage20PhysicalGalacticRoute> reverse = returnPlanner.findPath(
                checkedDestination, checkedOrigin);
        if (forward.isEmpty() || reverse.isEmpty()) {
            return Optional.empty();
        }

        EndpointCycleProfile endpoint = maybeEndpoint.orElseThrow();
        double payloadKg = fleetProfile.payloadMassKgPerFreighter();
        double loadSeconds = payloadKg / endpoint.sourceLoadingRateKgPerSecond();
        double unloadSeconds = payloadKg / endpoint.destinationUnloadingRateKgPerSecond();
        double deliverySeconds = finiteSum(
                loadSeconds,
                endpoint.outboundLocalAccessSeconds(),
                forward.orElseThrow().estimatedArrivalSeconds(),
                unloadSeconds);
        double oneFreighterCycleSeconds = finiteSum(
                loadSeconds,
                endpoint.outboundLocalAccessSeconds(),
                forward.orElseThrow().estimatedReadyAgainSeconds(),
                unloadSeconds,
                endpoint.returnLocalAccessSeconds(),
                reverse.orElseThrow().estimatedReadyAgainSeconds());
        if (!(oneFreighterCycleSeconds > 0d)) {
            throw new IllegalStateException("physical freight cycle must have positive duration");
        }

        double fleetCycleThroughput = payloadKg * allocatedFreighterCount / oneFreighterCycleSeconds;
        double sustainableThroughput = Math.min(
                fleetCycleThroughput,
                Math.min(endpoint.sourceLoadingRateKgPerSecond(),
                        endpoint.destinationUnloadingRateKgPerSecond()));
        requirePositiveFinite(sustainableThroughput, "sustainableThroughput");

        return Optional.of(new FreightCycleAssessment(
                forward.orElseThrow().systems(),
                allocatedFreighterCount,
                payloadKg,
                loadSeconds,
                unloadSeconds,
                endpoint.outboundLocalAccessSeconds(),
                forward.orElseThrow().estimatedArrivalSeconds(),
                forward.orElseThrow().estimatedReadyAgainSeconds(),
                endpoint.returnLocalAccessSeconds(),
                reverse.orElseThrow().estimatedReadyAgainSeconds(),
                deliverySeconds,
                oneFreighterCycleSeconds,
                fleetCycleThroughput,
                Math.min(endpoint.sourceLoadingRateKgPerSecond(),
                        endpoint.destinationUnloadingRateKgPerSecond()),
                sustainableThroughput));
    }

    /**
     * Explicit representative freight allocation used only for throughput estimation.
     *
     * @param version stable profile version
     * @param payloadMassKgPerFreighter physical delivered payload per loaded trip
     * @param activeFreighterCount number of identical freighters allocated to this route
     * @param sourceEvidenceId provenance of the physical payload/fleet assumption
     * @param stage22ReviewRequired whether provisional calibration input still requires Stage-22 review
     */
    public record FreightFleetProfile(
            String version,
            double payloadMassKgPerFreighter,
            int activeFreighterCount,
            String sourceEvidenceId,
            boolean stage22ReviewRequired) {
        /**
         * Validates one explicit immutable freight allocation.
         *
         * @param version stable profile version
         * @param payloadMassKgPerFreighter physical delivered payload per loaded trip
         * @param activeFreighterCount number of identical allocated freighters
         * @param sourceEvidenceId provenance of the physical payload/fleet assumption
         * @param stage22ReviewRequired whether provisional calibration input still requires Stage-22 review
         */
        public FreightFleetProfile {
            version = requireText(version, "version");
            requirePositiveFinite(payloadMassKgPerFreighter, "payloadMassKgPerFreighter");
            if (activeFreighterCount <= 0) {
                throw new IllegalArgumentException("activeFreighterCount must be positive");
            }
            sourceEvidenceId = requireText(sourceEvidenceId, "sourceEvidenceId");
        }

        /**
         * Creates a provisional Stage-20 calibration profile from one representative propulsion row.
         *
         * <p>{@code missionCargoStoresMassKg} is intentionally treated as a calibration payload
         * proxy, not silently promoted to the final production hull cargo-capacity contract. The
         * source catalog's Stage-22 review flag is preserved.</p>
         *
         * @param catalog accepted Stage-20 representative propulsion catalog
         * @param representativeClass representative row such as {@code BULK_FREIGHTER_LOADED}
         * @param activeFreighterCount explicit number of identical allocated freighters
         * @return freight profile retaining the reference provenance/review boundary
         */
        public static FreightFleetProfile fromMissionCargoStoresReference(
                Stage20RepresentativePropulsionCatalog catalog,
                String representativeClass,
                int activeFreighterCount) {
            Stage20RepresentativePropulsionCatalog checked = Objects.requireNonNull(catalog, "catalog");
            String checkedClass = requireText(representativeClass, "representativeClass");
            ReferenceDefinition reference = checked.findByRepresentativeClass(checkedClass);
            if (reference == null) {
                throw new IllegalArgumentException("unknown representative freight class: " + checkedClass);
            }
            return new FreightFleetProfile(
                    checked.version() + ":" + checkedClass,
                    reference.missionCargoStoresMassKg(),
                    activeFreighterCount,
                    reference.sourceEvidenceId(),
                    checked.stage22ReviewRequired());
        }
    }

    /** Physical endpoint/local-route facts for one ordered supplier-consumer pair. */
    @FunctionalInterface
    public interface EndpointCycleProvider {
        /**
         * Resolves physical local access and cargo handling for the ordered route pair.
         *
         * @param origin supplier system
         * @param destination consumer system
         * @return endpoint cycle facts, or empty when the physical last-mile/handling layer is unresolved
         */
        Optional<EndpointCycleProfile> profile(StarSystemId origin, StarSystemId destination);
    }

    /**
     * Physical non-FTL consequences of one repeated freight route.
     *
     * @param outboundLocalAccessSeconds supplier-to-departure plus arrival-to-consumer physical time
     * @param returnLocalAccessSeconds consumer-to-departure plus arrival-to-supplier physical time
     * @param sourceLoadingRateKgPerSecond total physical source loading/transfer rate
     * @param destinationUnloadingRateKgPerSecond total physical destination unloading/transfer rate
     * @param sourceEvidenceId provenance of the local/handling facts
     */
    public record EndpointCycleProfile(
            double outboundLocalAccessSeconds,
            double returnLocalAccessSeconds,
            double sourceLoadingRateKgPerSecond,
            double destinationUnloadingRateKgPerSecond,
            String sourceEvidenceId) {
        /**
         * Validates one immutable physical endpoint profile.
         *
         * @param outboundLocalAccessSeconds supplier-to-departure plus arrival-to-consumer physical time
         * @param returnLocalAccessSeconds consumer-to-departure plus arrival-to-supplier physical time
         * @param sourceLoadingRateKgPerSecond total physical source loading/transfer rate
         * @param destinationUnloadingRateKgPerSecond total physical destination unloading/transfer rate
         * @param sourceEvidenceId provenance of the local/handling facts
         */
        public EndpointCycleProfile {
            requireNonNegativeFinite(outboundLocalAccessSeconds, "outboundLocalAccessSeconds");
            requireNonNegativeFinite(returnLocalAccessSeconds, "returnLocalAccessSeconds");
            requirePositiveFinite(sourceLoadingRateKgPerSecond, "sourceLoadingRateKgPerSecond");
            requirePositiveFinite(destinationUnloadingRateKgPerSecond, "destinationUnloadingRateKgPerSecond");
            sourceEvidenceId = requireText(sourceEvidenceId, "sourceEvidenceId");
        }
    }

    /**
     * Exact component breakdown of one repeatable physical freight allocation.
     *
     * @param orderedSystems loaded outbound route including both endpoints
     * @param allocatedFreighterCount explicit finite ship count
     * @param payloadMassKgPerFreighter delivered mass per loaded trip
     * @param sourceLoadingSeconds source handling time for one payload
     * @param destinationUnloadingSeconds destination handling time for one payload
     * @param outboundLocalAccessSeconds combined loaded endpoint access
     * @param forwardFtlArrivalSeconds loaded FTL arrival time
     * @param forwardFtlReadyAgainSeconds loaded FTL arrival plus final cooldown
     * @param returnLocalAccessSeconds combined return endpoint access
     * @param returnFtlReadyAgainSeconds return FTL arrival plus final cooldown
     * @param deliverySeconds one-way load/access/arrival/unload delivery time
     * @param roundTripCycleSeconds ready-again repeatable cycle duration
     * @param fleetCycleThroughputKgPerSecond payload/count/cycle throughput before handling ceiling
     * @param endpointHandlingCeilingKgPerSecond shared endpoint handling ceiling
     * @param sustainableCargoThroughputKgPerSecond final physical sustainable throughput
     */
    public record FreightCycleAssessment(
            List<StarSystemId> orderedSystems,
            int allocatedFreighterCount,
            double payloadMassKgPerFreighter,
            double sourceLoadingSeconds,
            double destinationUnloadingSeconds,
            double outboundLocalAccessSeconds,
            double forwardFtlArrivalSeconds,
            double forwardFtlReadyAgainSeconds,
            double returnLocalAccessSeconds,
            double returnFtlReadyAgainSeconds,
            double deliverySeconds,
            double roundTripCycleSeconds,
            double fleetCycleThroughputKgPerSecond,
            double endpointHandlingCeilingKgPerSecond,
            double sustainableCargoThroughputKgPerSecond) {
        /**
         * Validates one exact cycle projection.
         *
         * @param orderedSystems loaded outbound route including both endpoints
         * @param allocatedFreighterCount explicit finite ship count
         * @param payloadMassKgPerFreighter delivered mass per loaded trip
         * @param sourceLoadingSeconds source handling time for one payload
         * @param destinationUnloadingSeconds destination handling time for one payload
         * @param outboundLocalAccessSeconds combined loaded endpoint access
         * @param forwardFtlArrivalSeconds loaded FTL arrival time
         * @param forwardFtlReadyAgainSeconds loaded FTL arrival plus final cooldown
         * @param returnLocalAccessSeconds combined return endpoint access
         * @param returnFtlReadyAgainSeconds return FTL arrival plus final cooldown
         * @param deliverySeconds one-way physical delivery time
         * @param roundTripCycleSeconds ready-again repeatable cycle duration
         * @param fleetCycleThroughputKgPerSecond payload/count/cycle throughput
         * @param endpointHandlingCeilingKgPerSecond endpoint handling ceiling
         * @param sustainableCargoThroughputKgPerSecond final sustainable throughput
         */
        public FreightCycleAssessment {
            orderedSystems = List.copyOf(Objects.requireNonNull(orderedSystems, "orderedSystems"));
            if (orderedSystems.isEmpty() || orderedSystems.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("freight cycle route cannot be empty");
            }
            if (allocatedFreighterCount <= 0) {
                throw new IllegalArgumentException("allocatedFreighterCount must be positive");
            }
            requirePositiveFinite(payloadMassKgPerFreighter, "payloadMassKgPerFreighter");
            requirePositiveFinite(sourceLoadingSeconds, "sourceLoadingSeconds");
            requirePositiveFinite(destinationUnloadingSeconds, "destinationUnloadingSeconds");
            requireNonNegativeFinite(outboundLocalAccessSeconds, "outboundLocalAccessSeconds");
            requireNonNegativeFinite(forwardFtlArrivalSeconds, "forwardFtlArrivalSeconds");
            requireNonNegativeFinite(forwardFtlReadyAgainSeconds, "forwardFtlReadyAgainSeconds");
            requireNonNegativeFinite(returnLocalAccessSeconds, "returnLocalAccessSeconds");
            requireNonNegativeFinite(returnFtlReadyAgainSeconds, "returnFtlReadyAgainSeconds");
            requirePositiveFinite(deliverySeconds, "deliverySeconds");
            requirePositiveFinite(roundTripCycleSeconds, "roundTripCycleSeconds");
            requirePositiveFinite(fleetCycleThroughputKgPerSecond,
                    "fleetCycleThroughputKgPerSecond");
            requirePositiveFinite(endpointHandlingCeilingKgPerSecond,
                    "endpointHandlingCeilingKgPerSecond");
            requirePositiveFinite(sustainableCargoThroughputKgPerSecond,
                    "sustainableCargoThroughputKgPerSecond");
            double expectedDelivery = finiteSum(
                    sourceLoadingSeconds,
                    outboundLocalAccessSeconds,
                    forwardFtlArrivalSeconds,
                    destinationUnloadingSeconds);
            double expectedCycle = finiteSum(
                    sourceLoadingSeconds,
                    outboundLocalAccessSeconds,
                    forwardFtlReadyAgainSeconds,
                    destinationUnloadingSeconds,
                    returnLocalAccessSeconds,
                    returnFtlReadyAgainSeconds);
            requireClose(expectedDelivery, deliverySeconds, "deliverySeconds");
            requireClose(expectedCycle, roundTripCycleSeconds, "roundTripCycleSeconds");
            requireClose(
                    payloadMassKgPerFreighter * allocatedFreighterCount / roundTripCycleSeconds,
                    fleetCycleThroughputKgPerSecond,
                    "fleetCycleThroughputKgPerSecond");
            requireClose(
                    Math.min(fleetCycleThroughputKgPerSecond, endpointHandlingCeilingKgPerSecond),
                    sustainableCargoThroughputKgPerSecond,
                    "sustainableCargoThroughputKgPerSecond");
        }

        /** @return historical compact route projection without losing physical semantics */
        public Stage20EconomicBootstrapValidator.RouteAssessment routeAssessment() {
            return new Stage20EconomicBootstrapValidator.RouteAssessment(
                    orderedSystems, deliverySeconds, sustainableCargoThroughputKgPerSecond);
        }

        /** @return total source plus destination handling time for one payload */
        public double handlingOverheadSeconds() {
            return sourceLoadingSeconds + destinationUnloadingSeconds;
        }
    }

    private static double finiteSum(double... values) {
        double result = 0d;
        for (double value : values) {
            requireNonNegativeFinite(value, "cycle time component");
            result += value;
            if (!Double.isFinite(result)) {
                throw new IllegalArgumentException("physical freight cycle time overflow");
            }
        }
        return result;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }

    private static void requireClose(double expected, double actual, String field) {
        double tolerance = 1.0e-9d * Math.max(1d, Math.max(Math.abs(expected), Math.abs(actual)));
        if (Math.abs(expected - actual) > tolerance) {
            throw new IllegalArgumentException(field + " differs from physical cycle components");
        }
    }
}
