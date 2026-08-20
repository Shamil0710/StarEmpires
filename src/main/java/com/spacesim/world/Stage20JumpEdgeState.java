package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable Stage-20D physical metadata for one explicit ordinary {@link JumpConnection}.
 *
 * <p>The existing {@link JumpConnection} remains the canonical graph edge used by Stage-10 and
 * later runtime code. This record supplies the Stage-20D authority that the bare pair intentionally
 * does not contain: stable edge identity, physical open/closed state, discovery policy, transit
 * parameters, directional arrival geometry and observed hazard/security metadata.</p>
 *
 * <p>Faction-specific legal access and observer-specific knowledge do not live here. They are
 * separate political/information projections over the same physical edge. Consequently an edge may
 * be physically {@link OperationalAccessState#OPEN} while a particular faction is legally denied or
 * has not yet discovered it.</p>
 *
 * @param version stable metadata schema version
 * @param edgeId stable ordinary-edge identity derived from canonical endpoint IDs
 * @param connection existing canonical topology connection
 * @param operationalAccessState world-global physical edge state
 * @param discoveryPolicy physical discoverability class, not observer knowledge
 * @param transitParameters physical transit-law parameters applied to a fitted jump plan
 * @param firstEndpoint directional arrival geometry when entering {@code connection.first()}
 * @param secondEndpoint directional arrival geometry when entering {@code connection.second()}
 * @param hazardSecurityMetadata physically observed hazard/security evidence, or explicitly unassessed
 * @param topologyQualityProfileVersion consumed topology-quality calibration version
 * @param intersystemCadenceProfileVersion consumed cadence calibration version
 */
@SuppressWarnings("doclint:missing")
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
    /** Current Stage-20D ordinary jump-edge metadata version. */
    public static final String CURRENT_VERSION = "stage20d.jump-edge-metadata.v1";

    /** Physical availability of an otherwise existing ordinary topology edge. */
    public enum OperationalAccessState {
        /** The physical edge exists and may be considered by ordinary route planning. */ OPEN,
        /** The edge still exists in topology but is physically unavailable for ordinary transit. */ PHYSICALLY_CLOSED
    }

    /**
     * Physical discoverability class. This never means that a particular observer currently knows
     * the edge; observer-relative discovery belongs to the Stage-20G information layer.
     */
    public enum DiscoveryPolicy {
        /** Ordinary generated edge may be discovered through the normal information model. */ ORDINARY_DISCOVERABLE,
        /** Reserved explicit special-edge class; never emitted by the ordinary v1 materializer. */ SPECIAL_HIDDEN
    }

    /** Observation state for hazard/security metadata. */
    public enum ObservationState {
        /** No physical assessment has been authored/observed; no values may be invented. */ UNASSESSED,
        /** Metadata is backed by explicit physical observation/provenance. */ OBSERVED
    }

    /**
     * Edge-specific physical transit parameters layered over the live fitted jump capability.
     *
     * <p>Stage-20A currently supplies a reference one-edge transit law but deliberately does not
     * author a generated per-edge duration distribution. Therefore ordinary Stage-20D v1 edges use
     * multiplier {@code 1.0}; this field is an explicit parameter seam, not a hidden random speed
     * modifier.</p>
     *
     * @param fittedTransitMultiplier multiplier applied to live {@code JumpPlan.edgeTransitSeconds}
     * @param ftlProfileVersion source FTL calibration profile
     * @param semantics stable textual law identifier
     */
    public record TransitParameters(
            double fittedTransitMultiplier,
            String ftlProfileVersion,
            String semantics) {
        /**
         * Validates explicit transit parameters.
         *
         * @param fittedTransitMultiplier multiplier applied to live fitted edge-transit time
         * @param ftlProfileVersion source FTL calibration profile
         * @param semantics stable textual law identifier
         */
        public TransitParameters {
            if (!Double.isFinite(fittedTransitMultiplier) || fittedTransitMultiplier <= 0d) {
                throw new IllegalArgumentException("fittedTransitMultiplier must be positive and finite");
            }
            requireText(ftlProfileVersion, "ftlProfileVersion");
            requireText(semantics, "semantics");
        }
    }

    /**
     * Destination-local physical arrival geometry for one end of an undirected edge.
     *
     * @param systemId destination system represented by this endpoint
     * @param anchorId stable Stage-20C jump-arrival anchor ID
     * @param position authoritative hierarchical SI position; never reduced to float here
     * @param arrivalVelocityMps current calibrated post-materialization speed
     * @param localInfrastructureVersion source Stage-20C layout version
     * @param jumpArrivalCalibrationVersion source Stage-20A arrival-calibration version
     */
    public record ArrivalEndpoint(
            StarSystemId systemId,
            String anchorId,
            LocalPhysicalPosition position,
            double arrivalVelocityMps,
            String localInfrastructureVersion,
            String jumpArrivalCalibrationVersion) {
        /**
         * Validates one directional physical endpoint.
         *
         * @param systemId destination system represented by this endpoint
         * @param anchorId stable Stage-20C jump-arrival anchor ID
         * @param position authoritative hierarchical SI position
         * @param arrivalVelocityMps calibrated post-materialization speed
         * @param localInfrastructureVersion source Stage-20C layout version
         * @param jumpArrivalCalibrationVersion source Stage-20A arrival calibration version
         */
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
     * Hazard/security evidence attached only when physically observed.
     *
     * @param observationState whether evidence exists
     * @param hazardTags deterministic physical hazard tags
     * @param securityTags deterministic physically observed security tags
     * @param provenance observation provenance; absent while unassessed
     */
    public record HazardSecurityMetadata(
            ObservationState observationState,
            List<String> hazardTags,
            List<String> securityTags,
            Optional<String> provenance) {
        /**
         * Validates one evidence bundle without inventing values for unassessed edges.
         *
         * @param observationState whether evidence exists
         * @param hazardTags deterministic physical hazard tags
         * @param securityTags deterministic physically observed security tags
         * @param provenance observation provenance; absent while unassessed
         */
        public HazardSecurityMetadata {
            Objects.requireNonNull(observationState, "observationState");
            hazardTags = canonicalTags(hazardTags, "hazardTags");
            securityTags = canonicalTags(securityTags, "securityTags");
            Objects.requireNonNull(provenance, "provenance");
            if (observationState == ObservationState.UNASSESSED) {
                if (!hazardTags.isEmpty() || !securityTags.isEmpty() || provenance.isPresent()) {
                    throw new IllegalArgumentException("UNASSESSED metadata cannot contain invented observations");
                }
            } else if (provenance.isEmpty() || provenance.orElseThrow().isBlank()) {
                throw new IllegalArgumentException("OBSERVED metadata requires provenance");
            }
        }

        /**
         * Creates explicit empty unassessed metadata.
         *
         * @return explicit empty unassessed metadata
         */
        public static HazardSecurityMetadata unassessed() {
            return new HazardSecurityMetadata(ObservationState.UNASSESSED, List.of(), List.of(), Optional.empty());
        }
    }

    /**
     * Validates one complete edge-metadata state.
     *
     * @param version stable metadata schema version
     * @param edgeId stable ordinary-edge identity derived from canonical endpoint IDs
     * @param connection existing canonical topology connection
     * @param operationalAccessState world-global physical edge state
     * @param discoveryPolicy physical discoverability class, not observer knowledge
     * @param transitParameters physical transit-law parameters applied to a fitted jump plan
     * @param firstEndpoint directional arrival geometry for the first endpoint
     * @param secondEndpoint directional arrival geometry for the second endpoint
     * @param hazardSecurityMetadata physically observed hazard/security evidence or unassessed state
     * @param topologyQualityProfileVersion consumed topology-quality calibration version
     * @param intersystemCadenceProfileVersion consumed cadence calibration version
     */
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
        String expectedEdgeId = ordinaryEdgeId(connection);
        if (!expectedEdgeId.equals(edgeId)) {
            throw new IllegalArgumentException("edgeId must be derived from canonical topology endpoints");
        }
        if (!connection.first().equals(firstEndpoint.systemId())
                || !connection.second().equals(secondEndpoint.systemId())) {
            throw new IllegalArgumentException("arrival endpoint systems must match canonical connection endpoints");
        }
        if (firstEndpoint.anchorId().equals(secondEndpoint.anchorId())
                && firstEndpoint.systemId().equals(secondEndpoint.systemId())) {
            throw new IllegalArgumentException("one system cannot reuse the same arrival endpoint for both sides");
        }
    }

    /**
     * Stable ordinary-edge identity independent of list ordering or generation pass count.
     *
     * @param connection canonical connection
     * @return stable textual edge identity
     */
    public static String ordinaryEdgeId(JumpConnection connection) {
        JumpConnection checked = Objects.requireNonNull(connection, "connection");
        return "ordinary:" + checked.first().value() + ':' + checked.second().value();
    }

    /**
     * Returns destination-local arrival geometry for either connection endpoint.
     *
     * @param destination one endpoint of this edge
     * @return matching physical arrival endpoint
     */
    public ArrivalEndpoint arrivalIn(StarSystemId destination) {
        StarSystemId checked = Objects.requireNonNull(destination, "destination");
        if (connection.first().equals(checked)) {
            return firstEndpoint;
        }
        if (connection.second().equals(checked)) {
            return secondEndpoint;
        }
        throw new IllegalArgumentException("destination is not an endpoint of edge " + edgeId);
    }

    /**
     * Returns a copy with a new world-global physical availability state.
     *
     * @param newState new physical access state
     * @return immutable updated metadata
     */
    public Stage20JumpEdgeState withOperationalAccess(OperationalAccessState newState) {
        return new Stage20JumpEdgeState(
                version,
                edgeId,
                connection,
                Objects.requireNonNull(newState, "newState"),
                discoveryPolicy,
                transitParameters,
                firstEndpoint,
                secondEndpoint,
                hazardSecurityMetadata,
                topologyQualityProfileVersion,
                intersystemCadenceProfileVersion);
    }

    private static List<String> canonicalTags(List<String> values, String field) {
        Objects.requireNonNull(values, field);
        ArrayList<String> copy = new ArrayList<>();
        for (String value : values) {
            copy.add(requireText(value, field + " entry"));
        }
        copy.sort(Comparator.naturalOrder());
        for (int index = 1; index < copy.size(); index++) {
            if (copy.get(index - 1).equals(copy.get(index))) {
                throw new IllegalArgumentException(field + " cannot contain duplicates");
            }
        }
        return List.copyOf(copy);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
