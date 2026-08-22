package com.spacesim.world;

import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.world.Stage20DiscoveryKnowledgeRuntime.StaticObservation;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryEvidence;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoverySource;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.Freshness;
import com.spacesim.world.calibration.Stage20RepresentativeEnduranceProfile;
import com.spacesim.world.calibration.Stage20RepresentativeEnduranceProfile.EnduranceSample;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import com.spacesim.world.calibration.Stage20RouteCalibrationCalculator;
import com.spacesim.world.calibration.Stage20ScaleCalibrationCalculator;
import com.spacesim.world.calibration.Stage20ScaleCalibrationProfile;
import com.spacesim.world.calibration.Stage20ScaleCalibrationProfile.RepresentativeShipPropulsionEnvelope;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Stage-20I physical transmission and delayed-intelligence delivery boundary.
 *
 * <p>Local electromagnetic transmission uses SI separation divided by the exact vacuum light-speed
 * constant. Inter-system intelligence has no invented FTL radio: it uses an explicit physical
 * courier route over ordinary neighbor edges plus derived hub-to-arrival access. The service keeps
 * observation, send, transmission and receipt times separate so stale intelligence remains stale
 * when finally delivered.</p>
 */
public final class Stage20IntelligenceLatencyService {
    /** Exact vacuum speed of light in meters per second. */
    public static final double VACUUM_LIGHT_SPEED_MPS = 299_792_458d;
    /** Current Stage-20I transmission architecture version. */
    public static final String CURRENT_VERSION = "stage20i.intelligence-latency.v1";
    private static final String COURIER_REPRESENTATIVE_ID = "BULK_FREIGHTER_LOADED";

    private Stage20IntelligenceLatencyService() {
        throw new AssertionError("No instances");
    }

    /** Physical transmission mechanism. */
    public enum TransmissionMode {
        /** Same-system electromagnetic propagation at vacuum light speed. */ LOCAL_ELECTROMAGNETIC,
        /** Inter-system transport by an explicit fitted physical courier. */ PHYSICAL_COURIER
    }

    /** Recipient-facing provenance channel after physical transmission. */
    public enum DeliveryChannel {
        /** Purchased or deliberately shared map/intelligence report. */ SHARED_INTELLIGENCE,
        /** Persistent infrastructure broadcast received after propagation. */ INFRASTRUCTURE_BROADCAST
    }

    /**
     * One physical sender/receiver node.
     *
     * @param nodeId stable communication-node identity
     * @param ownerId stable node owner/intelligence-network identity
     * @param systemId owning star system
     * @param position authoritative local SI position
     */
    public record CommunicationNode(
            String nodeId,
            String ownerId,
            StarSystemId systemId,
            LocalPhysicalPosition position) {
        /**
         * Validates one physical communication node.
         *
         * @param nodeId stable node identity
         * @param ownerId stable owner identity
         * @param systemId owning system
         * @param position local SI position
         */
        public CommunicationNode {
            nodeId = requireText(nodeId, "nodeId");
            ownerId = requireText(ownerId, "ownerId");
            Objects.requireNonNull(systemId, "systemId");
            Objects.requireNonNull(position, "position");
        }
    }

    /**
     * One immutable physical transmission plan.
     *
     * @param version transmission architecture version
     * @param planId stable plan identity
     * @param mode physical mechanism
     * @param source source node
     * @param destination destination node
     * @param localPropagationDistanceM local EM path distance; zero for courier plans
     * @param sourceCourierAccessSeconds source-hub to first jump-arrival access
     * @param destinationCourierAccessSeconds final jump-arrival to destination-hub access
     * @param courierRoute explicit neighbor-edge route; present exactly for courier plans
     * @param transmissionSeconds total physical transmission duration
     * @param authorityProvenanceId exact physical planning authority
     */
    public record TransmissionPlan(
            String version,
            String planId,
            TransmissionMode mode,
            CommunicationNode source,
            CommunicationNode destination,
            double localPropagationDistanceM,
            double sourceCourierAccessSeconds,
            double destinationCourierAccessSeconds,
            Optional<Stage20PhysicalGalacticRoute> courierRoute,
            double transmissionSeconds,
            String authorityProvenanceId) {
        /**
         * Validates an exact local-signal or physical-courier plan.
         *
         * @param version transmission architecture version
         * @param planId stable plan identity
         * @param mode physical mechanism
         * @param source source node
         * @param destination destination node
         * @param localPropagationDistanceM local EM path distance
         * @param sourceCourierAccessSeconds source courier access
         * @param destinationCourierAccessSeconds destination courier access
         * @param courierRoute explicit physical courier route
         * @param transmissionSeconds total physical duration
         * @param authorityProvenanceId exact physical authority
         */
        public TransmissionPlan {
            version = requireText(version, "version");
            planId = requireText(planId, "planId");
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(destination, "destination");
            requireNonNegativeFinite(localPropagationDistanceM, "localPropagationDistanceM");
            requireNonNegativeFinite(sourceCourierAccessSeconds, "sourceCourierAccessSeconds");
            requireNonNegativeFinite(destinationCourierAccessSeconds, "destinationCourierAccessSeconds");
            Objects.requireNonNull(courierRoute, "courierRoute");
            requireNonNegativeFinite(transmissionSeconds, "transmissionSeconds");
            authorityProvenanceId = requireText(authorityProvenanceId, "authorityProvenanceId");

            if (mode == TransmissionMode.LOCAL_ELECTROMAGNETIC) {
                if (!source.systemId().equals(destination.systemId())
                        || courierRoute.isPresent()
                        || sourceCourierAccessSeconds != 0d
                        || destinationCourierAccessSeconds != 0d) {
                    throw new IllegalArgumentException("local EM plan must remain inside one system without courier");
                }
                double physicalDistance = source.position().distanceTo(destination.position());
                requireEquivalent(physicalDistance, localPropagationDistanceM, "local propagation distance");
                requireEquivalent(
                        physicalDistance / VACUUM_LIGHT_SPEED_MPS,
                        transmissionSeconds,
                        "local propagation duration");
            } else {
                Stage20PhysicalGalacticRoute route = courierRoute.orElseThrow(() ->
                        new IllegalArgumentException("inter-system courier plan requires a physical route"));
                if (source.systemId().equals(destination.systemId())
                        || localPropagationDistanceM != 0d
                        || !route.origin().equals(source.systemId())
                        || !route.destination().equals(destination.systemId())
                        || route.jumpCount() <= 0) {
                    throw new IllegalArgumentException("courier route endpoints must match distinct node systems");
                }
                double expected = sourceCourierAccessSeconds
                        + route.estimatedArrivalSeconds()
                        + destinationCourierAccessSeconds;
                requireEquivalent(expected, transmissionSeconds, "courier transmission duration");
            }
        }
    }

    /**
     * Delivered observer-local report with explicit timing and staleness.
     *
     * @param receiptId stable delivery identity
     * @param plan exact physical transmission plan
     * @param observationTimeSeconds original physical observation time
     * @param sentTimeSeconds time transmission began
     * @param receiptTimeSeconds time the recipient received the report
     * @param ageAtReceiptSeconds report age at receipt
     * @param freshnessAtReceipt freshness derived from the original evidence horizon
     * @param recipientObservation Stage-20G observation carrying delayed provenance
     */
    public record TransmissionReceipt(
            String receiptId,
            TransmissionPlan plan,
            double observationTimeSeconds,
            double sentTimeSeconds,
            double receiptTimeSeconds,
            double ageAtReceiptSeconds,
            Freshness freshnessAtReceipt,
            StaticObservation recipientObservation) {
        /**
         * Validates one complete delayed delivery.
         *
         * @param receiptId stable receipt identity
         * @param plan exact transmission plan
         * @param observationTimeSeconds original observation time
         * @param sentTimeSeconds transmission start
         * @param receiptTimeSeconds receipt time
         * @param ageAtReceiptSeconds age at receipt
         * @param freshnessAtReceipt freshness at receipt
         * @param recipientObservation delayed recipient observation
         */
        public TransmissionReceipt {
            receiptId = requireText(receiptId, "receiptId");
            Objects.requireNonNull(plan, "plan");
            requireNonNegativeFinite(observationTimeSeconds, "observationTimeSeconds");
            requireNonNegativeFinite(sentTimeSeconds, "sentTimeSeconds");
            requireNonNegativeFinite(receiptTimeSeconds, "receiptTimeSeconds");
            requireNonNegativeFinite(ageAtReceiptSeconds, "ageAtReceiptSeconds");
            Objects.requireNonNull(freshnessAtReceipt, "freshnessAtReceipt");
            Objects.requireNonNull(recipientObservation, "recipientObservation");
            if (sentTimeSeconds < observationTimeSeconds) {
                throw new IllegalArgumentException("transmission cannot begin before observation");
            }
            requireEquivalent(sentTimeSeconds + plan.transmissionSeconds(), receiptTimeSeconds, "receipt time");
            requireEquivalent(receiptTimeSeconds - observationTimeSeconds, ageAtReceiptSeconds, "receipt age");
            if (recipientObservation.evidence().observedAtSeconds() != observationTimeSeconds) {
                throw new IllegalArgumentException("recipient evidence must preserve original observation time");
            }
        }
    }

    /**
     * Plans local electromagnetic propagation from exact endpoint positions.
     *
     * @param planId stable plan identity
     * @param source source node
     * @param destination destination node in the same system
     * @return physical local transmission plan
     */
    public static TransmissionPlan planLocal(
            String planId,
            CommunicationNode source,
            CommunicationNode destination) {
        CommunicationNode from = Objects.requireNonNull(source, "source");
        CommunicationNode to = Objects.requireNonNull(destination, "destination");
        double distance = from.position().distanceTo(to.position());
        return new TransmissionPlan(
                CURRENT_VERSION,
                planId,
                TransmissionMode.LOCAL_ELECTROMAGNETIC,
                from,
                to,
                distance,
                0d,
                0d,
                Optional.empty(),
                distance / VACUUM_LIGHT_SPEED_MPS,
                "SI-distance/vacuum-light-speed");
    }

    /**
     * Plans an inter-system physical courier between generated major hubs.
     *
     * @param planId stable transmission-plan identity
     * @param accepted exact accepted generated world
     * @param sourceOwnerId sender owner/intelligence network
     * @param sourceSystem source major-hub system
     * @param destinationOwnerId recipient owner/intelligence network
     * @param destinationSystem destination major-hub system
     * @param courierJumpPlan executable fitted one-edge courier jump capability
     * @param authorityProvenanceId exact courier authority provenance
     * @return physical neighbor-edge courier plan
     */
    public static TransmissionPlan planGeneratedHubCourier(
            String planId,
            ResolvedProbeResult accepted,
            String sourceOwnerId,
            StarSystemId sourceSystem,
            String destinationOwnerId,
            StarSystemId destinationSystem,
            JumpPlan courierJumpPlan,
            String authorityProvenanceId) {
        ResolvedProbeResult world = requireAccepted(accepted);
        Stage20LocalInfrastructureLayout sourceLayout = requireLayout(world, sourceSystem);
        Stage20LocalInfrastructureLayout destinationLayout = requireLayout(world, destinationSystem);
        InfrastructureEndpoints endpoints = endpoints(sourceLayout, destinationLayout);
        CommunicationNode source = new CommunicationNode(
                sourceLayout.majorHubId(), sourceOwnerId, sourceSystem, endpoints.sourceHub().position());
        CommunicationNode destination = new CommunicationNode(
                destinationLayout.majorHubId(), destinationOwnerId, destinationSystem,
                endpoints.destinationHub().position());

        Stage20PhysicalGalacticRoute route = new Stage20PhysicalGalacticRoutePlanner(
                world.generation().topology().requireAcceptedTopology(),
                Objects.requireNonNull(courierJumpPlan, "courierJumpPlan"),
                world.generation().jumpEdges().orElseThrow())
                .findPath(sourceSystem, destinationSystem)
                .orElseThrow(() -> new IllegalArgumentException("no physical courier route between generated hubs"));
        if (route.jumpCount() <= 0) {
            throw new IllegalArgumentException("inter-system courier requires distinct systems");
        }

        RepresentativeShipPropulsionEnvelope accessEnvelope = courierAccessEnvelope();
        StarSystemId firstNeighbor = route.systems().get(1);
        StarSystemId finalPrevious = route.systems().get(route.systems().size() - 2);
        LocalPhysicalPosition sourceArrival = sourceLayout.placement(
                jumpAnchorId(sourceSystem, firstNeighbor)).position();
        LocalPhysicalPosition destinationArrival = destinationLayout.placement(
                jumpAnchorId(destinationSystem, finalPrevious)).position();
        double sourceAccess = Stage20RouteCalibrationCalculator.derive(
                COURIER_REPRESENTATIVE_ID,
                accessEnvelope,
                source.position().distanceTo(sourceArrival)).totalTravelTimeS();
        double destinationAccess = Stage20RouteCalibrationCalculator.derive(
                COURIER_REPRESENTATIVE_ID,
                accessEnvelope,
                destinationArrival.distanceTo(destination.position())).totalTravelTimeS();
        double duration = sourceAccess + route.estimatedArrivalSeconds() + destinationAccess;
        return new TransmissionPlan(
                CURRENT_VERSION,
                planId,
                TransmissionMode.PHYSICAL_COURIER,
                source,
                destination,
                0d,
                sourceAccess,
                destinationAccess,
                Optional.of(route),
                duration,
                requireText(authorityProvenanceId, "authorityProvenanceId")
                        + ":" + COURIER_REPRESENTATIVE_ID
                        + ":" + world.generation().jumpEdges().orElseThrow().version());
    }

    /**
     * Delivers one existing observation after the plan's physical duration.
     *
     * @param receiptId stable receipt identity
     * @param plan exact physical transmission plan
     * @param sourceObservation fact actually held by the sender
     * @param sentTimeSeconds authoritative transmission start time
     * @param channel recipient-facing delivery provenance channel
     * @return delayed report with receipt age/freshness and a Stage-20G observation
     */
    public static TransmissionReceipt deliver(
            String receiptId,
            TransmissionPlan plan,
            StaticObservation sourceObservation,
            double sentTimeSeconds,
            DeliveryChannel channel) {
        String receipt = requireText(receiptId, "receiptId");
        TransmissionPlan transmission = Objects.requireNonNull(plan, "plan");
        StaticObservation source = Objects.requireNonNull(sourceObservation, "sourceObservation");
        Objects.requireNonNull(channel, "channel");
        requireNonNegativeFinite(sentTimeSeconds, "sentTimeSeconds");
        double observedAt = source.evidence().observedAtSeconds();
        if (sentTimeSeconds < observedAt) {
            throw new IllegalArgumentException("intelligence cannot be sent before it was observed");
        }
        double receivedAt = sentTimeSeconds + transmission.transmissionSeconds();
        if (!Double.isFinite(receivedAt)) {
            throw new IllegalArgumentException("intelligence receipt time overflow");
        }
        Freshness freshness = freshnessAt(source.evidence(), receivedAt);
        DiscoverySource deliverySource = channel == DeliveryChannel.SHARED_INTELLIGENCE
                ? DiscoverySource.PURCHASED_OR_SHARED_MAP_DATA
                : DiscoverySource.PERSISTENT_INFRASTRUCTURE_BROADCAST;
        DiscoveryEvidence receivedEvidence = new DiscoveryEvidence(
                deliverySource,
                receipt + ":via:" + transmission.planId() + ":source:" + source.evidence().provenanceId(),
                observedAt,
                source.evidence().freshUntilSeconds());
        StaticObservation recipient = new StaticObservation(
                source.object(),
                source.state(),
                source.classificationId(),
                source.knownLocation(),
                source.resourceKnowledge(),
                receivedEvidence);
        return new TransmissionReceipt(
                receipt,
                transmission,
                observedAt,
                sentTimeSeconds,
                receivedAt,
                receivedAt - observedAt,
                freshness,
                recipient);
    }

    private static Freshness freshnessAt(DiscoveryEvidence evidence, double receiptTimeSeconds) {
        OptionalDouble horizon = evidence.freshUntilSeconds();
        if (horizon.isEmpty()) {
            return Freshness.PERMANENT;
        }
        return receiptTimeSeconds <= horizon.getAsDouble() ? Freshness.CURRENT : Freshness.STALE;
    }

    private static ResolvedProbeResult requireAccepted(ResolvedProbeResult value) {
        ResolvedProbeResult accepted = Objects.requireNonNull(value, "accepted");
        if (accepted.seedAcceptance().status() != Stage20GeneratedWorldSeedAcceptance.Status.ACCEPTED
                || accepted.generation().localLayouts().isEmpty()
                || accepted.generation().jumpEdges().isEmpty()) {
            throw new IllegalArgumentException("Stage-20I requires one accepted complete generated world");
        }
        return accepted;
    }

    private static Stage20LocalInfrastructureLayout requireLayout(
            ResolvedProbeResult world,
            StarSystemId systemId) {
        return world.generation().localLayouts().orElseThrow().stream()
                .filter(value -> value.systemId().equals(Objects.requireNonNull(systemId, "systemId")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("generated world has no local layout: " + systemId));
    }

    private static InfrastructureEndpoints endpoints(
            Stage20LocalInfrastructureLayout source,
            Stage20LocalInfrastructureLayout destination) {
        return new InfrastructureEndpoints(
                source.placement(source.majorHubId()),
                destination.placement(destination.majorHubId()));
    }

    private static String jumpAnchorId(StarSystemId system, StarSystemId neighbor) {
        return "jump-arrival." + system.value() + "." + neighbor.value();
    }

    private static RepresentativeShipPropulsionEnvelope courierAccessEnvelope() {
        RepresentativeShipPropulsionEnvelope baseline = Stage20ScaleCalibrationProfile.deriveCurrent()
                .representativeShips().stream()
                .filter(value -> value.representativeId().equals(COURIER_REPRESENTATIVE_ID))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing courier propulsion authority"));
        EnduranceSample endurance = Stage20RepresentativeEnduranceProfile.deriveCurrent().samples().stream()
                .filter(value -> value.representativeId().equals(COURIER_REPRESENTATIVE_ID))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing courier endurance authority"));
        return Stage20ScaleCalibrationCalculator.deriveAtThrust(
                baseline,
                CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE,
                endurance.sustainedThrustSourceEvidenceId(),
                CURRENT_VERSION + ":courier-local-access",
                endurance.sustainedThrustN());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }

    private static void requireEquivalent(double expected, double actual, String field) {
        double tolerance = Math.max(1e-9d, Math.max(Math.abs(expected), Math.abs(actual)) * 1e-12d);
        if (Math.abs(expected - actual) > tolerance) {
            throw new IllegalArgumentException(field + " differs from physical derivation");
        }
    }

    private record InfrastructureEndpoints(
            Stage20LocalInfrastructureLayout.InfrastructurePlacement sourceHub,
            Stage20LocalInfrastructureLayout.InfrastructurePlacement destinationHub) {
    }
}
