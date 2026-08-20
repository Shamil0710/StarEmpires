package com.spacesim.world;

import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.ReferenceDefinition;

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
     * Evaluates one repeatable supply route without mutating ship, market or world state.
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

        double fleetCycleThroughput = payloadKg * fleetProfile.activeFreighterCount()
                / oneFreighterCycleSeconds;
        double sustainableThroughput = Math.min(
                fleetCycleThroughput,
                Math.min(endpoint.sourceLoadingRateKgPerSecond(),
                        endpoint.destinationUnloadingRateKgPerSecond()));
        requirePositiveFinite(sustainableThroughput, "sustainableThroughput");

        return Optional.of(new Stage20EconomicBootstrapValidator.RouteAssessment(
                forward.orElseThrow().systems(),
                deliverySeconds,
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
        /** Validates one explicit immutable freight allocation. */
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
        /** Validates one immutable physical endpoint profile. */
        public EndpointCycleProfile {
            requireNonNegativeFinite(outboundLocalAccessSeconds, "outboundLocalAccessSeconds");
            requireNonNegativeFinite(returnLocalAccessSeconds, "returnLocalAccessSeconds");
            requirePositiveFinite(sourceLoadingRateKgPerSecond, "sourceLoadingRateKgPerSecond");
            requirePositiveFinite(destinationUnloadingRateKgPerSecond, "destinationUnloadingRateKgPerSecond");
            sourceEvidenceId = requireText(sourceEvidenceId, "sourceEvidenceId");
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
}
