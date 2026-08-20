package com.spacesim.world;

import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable Stage-20D physical metadata for one existing ordinary jump connection.
 *
 * <p>This record augments, but never replaces, {@link JumpConnection}. The canonical connection is
 * still the topology identity; metadata supplies stable persistence identity, world-global physical
 * availability, fitted transit parameters, destination-local arrival geometry and explicitly
 * observed hazard/security state.</p>
 *
 * @param version stable metadata schema/version
 * @param edgeId stable ordinary edge identity
 * @param connection canonical existing topology edge
 * @param operationalAccessState world-global physical availability, not faction law
 * @param discoveryPolicy physical discoverability class, not observer knowledge
 * @param transitParameters fitted transit parameters
 * @param firstEndpoint arrival geometry when the first canonical endpoint is destination
 * @param secondEndpoint arrival geometry when the second canonical endpoint is destination
 * @param hazardSecurityMetadata observed/unassessed hazard and security metadata
 * @param topologyQualityProfileVersion source Stage-20 topology-quality calibration version
 * @param intersystemCadenceProfileVersion source Stage-20 intersystem-cadence calibration version
 */
public record Stage20JumpEdgeState(
        String version,
        String edgeId,
        JumpConnection connection,
        OperationalAccessState operationalAccessState,
        DiscoveryPolicy discoveryPolicy,
        TransitParameters transitParameters,
        ArrivalEndpoint firstEndpoint,
        ArrivalEndpoint secondEndpoint,
        HazardSecurityMetadata hazardSecurityMetadata,
        String topologyQualityProfileVersion,
        String intersystemCadenceProfileVersion) {
    /** Current Stage-20D physical-edge metadata version. */
    public static final String CURRENT_VERSION = "stage20d.jump-edge-metadata.v1";

    /** World-global physical edge availability only; faction access belongs to Stage 17. */
    public enum OperationalAccessState {
        /** Edge physically exists and can be considered by routing/execution. */ OPEN,
        /** Edge physically exists but is currently globally unavailable. */ PHYSICALLY_CLOSED
    }

    /** Physical discovery class; observer-relative knowledge remains Stage 20G authority. */
    public enum DiscoveryPolicy {
        /** Ordinary neighbor edge participates in normal discovery. */ ORDINARY_DISCOVERABLE,
        /** Reserved for later special hidden topology; not emitted by ordinary v1 materialization. */ SPECIAL_HIDDEN
    }

    /** Observation state for hazard/security metadata. */
    public enum ObservationState {
        /** No authoritative physical observation is currently attached. */ UNASSESSED,
        /** Metadata is based on an explicit observed/provenanced source. */ OBSERVED
    }

    /**
     * Fitted transit parameters that apply to one edge without duplicating FTL capability physics.
     *
     * @param fittedTransitMultiplier multiplier applied to current fitted {@link JumpPlan#edgeTransitSeconds()}
     * @param ftlProfileVersion source FTL edge-cadence profile version
     * @param semantics stable description of the physical transit law
     */
    public record TransitParameters(
            double fittedTransitMultiplier,
            String ftlProfileVersion,
            String semantics) {
        /** Validates one immutable transit-parameter row. */
        public TransitParameters {
            if (!Double.isFinite(fittedTransitMultiplier) || fittedTransitMultiplier <= 0d) {
                throw new IllegalArgumentException("fittedTransitMultiplier must be positive and finite");
            }
            requireText(ftlProfileVersion, "ftlProfileVersion");
            requireText(semantics, "semantics");
        }
    }

    /**
     * Destination-local physical arrival endpoint.
     *
     * @param systemId destination system
     * @param anchorId stable Stage-20C jump-arrival anchor identity
     * @param position authoritative hierarchical local position
     * @param arrivalVelocityMps calibrated arrival speed magnitude
     * @param localInfrastructureVersion source Stage-20C layout version
     * @param jumpArrivalCalibrationVersion source Stage-20 arrival calibration version
     */
    public record ArrivalEndpoint(
            StarSystemId systemId,
            String anchorId,
            LocalPhysicalPosition position,
            double arrivalVelocityMps,
            String localInfrastructureVersion,
            String jumpArrivalCalibrationVersion) {
        /** Validates one destination-local endpoint without reducing coordinates to legacy floats. */
        public ArrivalEndpoint {
            Objects.requireNonNull(systemId, "systemId");
            requireText(anchorId, "anchorId");
            Objects.requireNonNull(position, "position");
            if (!Double.isFinite(arrivalVelocityMps) || arrivalVelocityMps < 0d) {
                throw new IllegalArgumentException("arrivalVelocityMps must be non-negative and finite");
            }
            requireText(localInfrastructureVersion, "localInfrastructureVersion");
            requireText(jumpArrivalCalibrationVersion, "jumpArrivalCalibrationVersion");
        }
    }

    /**
     * Explicit hazard/security observation payload.
     *
     * @param observationState whether values are authoritative observations
     * @param hazardTags deterministic sorted stable hazard tags
     * @param securityTags deterministic sorted stable security tags
     * @param provenance evidence/provenance identifier for observed metadata
     */
    public record HazardSecurityMetadata(
            ObservationState observationState,
            List<String> hazardTags,
            List<String> securityTags,
            Optional<String> provenance) {
        /** Canonicalizes observation metadata and prevents invented values on unassessed edges. */
        public HazardSecurityMetadata {
            Objects.requireNonNull(observationState, "observationState");
            hazardTags = sortedTags(hazardTags, "hazardTags");
            securityTags = sortedTags(securityTags, "securityTags");
            Objects.requireNonNull(provenance, "provenance");
            if (provenance.isPresent() && provenance.orElseThrow().isBlank()) {
                throw new IllegalArgumentException("provenance must not be blank");
            }
            if (observationState == ObservationState.UNASSESSED
                    && (!hazardTags.isEmpty() || !securityTags.isEmpty() || provenance.isPresent())) {
                throw new IllegalArgumentException("unassessed hazard/security metadata cannot invent observations");
            }
            if (observationState == ObservationState.OBSERVED && provenance.isEmpty()) {
                throw new IllegalArgumentException("observed hazard/security metadata requires provenance");
            }
        }

        /** @return explicit empty unassessed metadata */
        public static HazardSecurityMetadata unassessed() {
            return new HazardSecurityMetadata(ObservationState.UNASSESSED, List.of(), List.of(), Optional.empty());
        }
    }

    /** Validates endpoint/canonical identity coupling and provenance. */
    public Stage20JumpEdgeState {
        requireText(version, "version");
        requireText(edgeId, "edgeId");
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(operationalAccessState, "operationalAccessState");
        Objects.requireNonNull(discoveryPolicy, "discoveryPolicy");
        Objects.requireNonNull(transitParameters, "transitParameters");
        Objects.requireNonNull(firstEndpoint, "firstEndpoint");
        Objects.requireNonNull(secondEndpoint, "secondEndpoint");
        Objects.requireNonNull(hazardSecurityMetadata, "hazardSecurityMetadata");
        requireText(topologyQualityProfileVersion, "topologyQualityProfileVersion");
        requireText(intersystemCadenceProfileVersion, "intersystemCadenceProfileVersion");
        if (!firstEndpoint.systemId().equals(connection.first())
                || !secondEndpoint.systemId().equals(connection.second())) {
            throw new IllegalArgumentException("arrival endpoints must match canonical connection endpoints");
        }
        if (!edgeId.equals(stableOrdinaryEdgeId(connection))) {
            throw new IllegalArgumentException("edgeId must match canonical ordinary edge identity");
        }
    }

    /**
     * Derives a stable ordinary edge ID from canonical endpoint IDs only.
     *
     * @param connection canonical ordinary edge
     * @return stable generation/persistence identity
     */
    public static String stableOrdinaryEdgeId(JumpConnection connection) {
        JumpConnection checked = Objects.requireNonNull(connection, "connection");
        return "ordinary:" + checked.first().value() + ":" + checked.second().value();
    }

    /**
     * Returns destination-local arrival geometry for one endpoint.
     *
     * @param destination destination system on this edge
     * @return authoritative local physical endpoint
     */
    public ArrivalEndpoint arrivalIn(StarSystemId destination) {
        StarSystemId checked = Objects.requireNonNull(destination, "destination");
        if (checked.equals(connection.first())) {
            return firstEndpoint;
        }
        if (checked.equals(connection.second())) {
            return secondEndpoint;
        }
        throw new IllegalArgumentException("destination is not an endpoint of " + connection + ": " + destination);
    }

    /**
     * Returns an immutable copy with updated world-global physical access.
     *
     * @param state new physical access state
     * @return updated metadata row
     */
    public Stage20JumpEdgeState withOperationalAccess(OperationalAccessState state) {
        return new Stage20JumpEdgeState(
                version,
                edgeId,
                connection,
                Objects.requireNonNull(state, "state"),
                discoveryPolicy,
                transitParameters,
                firstEndpoint,
                secondEndpoint,
                hazardSecurityMetadata,
                topologyQualityProfileVersion,
                intersystemCadenceProfileVersion);
    }

    private static List<String> sortedTags(List<String> source, String field) {
        Objects.requireNonNull(source, field);
        ArrayList<String> copy = new ArrayList<>(source);
        if (copy.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(field + " cannot contain blank values");
        }
        copy.sort(Comparator.naturalOrder());
        for (int index = 1; index < copy.size(); index++) {
            if (copy.get(index - 1).equals(copy.get(index))) {
                throw new IllegalArgumentException(field + " cannot contain duplicates: " + copy.get(index));
            }
        }
        return List.copyOf(copy);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
