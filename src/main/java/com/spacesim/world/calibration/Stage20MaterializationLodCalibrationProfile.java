package com.spacesim.world.calibration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Versioned Stage-20A.9 materialization/LOD calibration evidence.
 *
 * <p>The profile describes computational representations of one authoritative world. It does not
 * create a second off-screen economy or make any render/materialization distance a physical world
 * boundary. Numeric activation distance is present only when backed by explicit physical interaction
 * and wake-up inputs.</p>
 *
 * @param version stable calibration-profile version
 * @param representationPolicies canonical representation-level contracts
 * @param runtimeCadenceEvidence existing production cadence evidence
 * @param currentDistanceBandClosures current machine-visible numeric distance-band closure status
 * @param unresolvedConstraints missing production/runtime closure that Stage 20 must not replace with fixed map radii
 */
public record Stage20MaterializationLodCalibrationProfile(
        String version,
        List<RepresentationPolicy> representationPolicies,
        RuntimeCadenceEvidence runtimeCadenceEvidence,
        List<DistanceBandClosure> currentDistanceBandClosures,
        List<String> unresolvedConstraints) {
    /** Current Stage-20A.9 materialization/LOD calibration profile version. */
    public static final String CURRENT_VERSION = "stage20a.materialization-lod.v1";

    /**
     * Creates one deterministic immutable materialization/LOD profile.
     *
     * @param version stable calibration-profile version
     * @param representationPolicies canonical representation-level contracts
     * @param runtimeCadenceEvidence production cadence evidence
     * @param currentDistanceBandClosures current numeric band closure state
     * @param unresolvedConstraints remaining materialization/LOD gaps
     */
    public Stage20MaterializationLodCalibrationProfile {
        requireText(version, "version");
        representationPolicies = sortedCopy(
                representationPolicies,
                Comparator.comparing(RepresentationPolicy::level),
                "representationPolicies");
        Objects.requireNonNull(runtimeCadenceEvidence, "runtimeCadenceEvidence");
        currentDistanceBandClosures = sortedCopy(
                currentDistanceBandClosures,
                Comparator.comparing(DistanceBandClosure::level),
                "currentDistanceBandClosures");
        unresolvedConstraints = sortedStrings(unresolvedConstraints, "unresolvedConstraints");
    }

    /** Canonical computational relevance levels from the accepted scalability architecture. */
    public enum RepresentationLevel {
        /** Persistent authoritative state with event/on-demand work only. */
        DORMANT,
        /** Persistent strategic/event-driven representation without full local runtime. */
        STRATEGIC,
        /** Reduced local/system representation for nearby operational relevance. */
        ACTIVE_LOCAL,
        /** Full detailed physical/tactical representation for direct interaction. */
        TACTICAL
    }

    /** Authority of a numeric materialization-distance band. */
    public enum DistanceBandAuthority {
        /** Required physical/wake-up inputs do not yet exist as accepted production authority. */
        UNRESOLVED,
        /** Band was derived exclusively from explicit physical interaction and wake-up inputs. */
        EXPLICIT_PHYSICAL_INPUT
    }

    /**
     * Computational contract of one canonical representation level.
     *
     * @param level canonical representation level
     * @param authoritativeStateRetained whether persistent authoritative state must remain retained
     * @param fullTacticalRuntimeRequired whether full detailed tactical runtime is required
     * @param localRuntimePermitted whether a local runtime representation may exist
     * @param renderingRequired whether this level intrinsically requires rendering
     * @param triggerSummary stable machine-readable trigger-policy description
     */
    public record RepresentationPolicy(
            RepresentationLevel level,
            boolean authoritativeStateRetained,
            boolean fullTacticalRuntimeRequired,
            boolean localRuntimePermitted,
            boolean renderingRequired,
            String triggerSummary) {
        /**
         * Validates one representation-level contract.
         *
         * @param level canonical representation level
         * @param authoritativeStateRetained whether persistent authoritative state remains retained
         * @param fullTacticalRuntimeRequired whether full tactical runtime is required
         * @param localRuntimePermitted whether local runtime representation is permitted
         * @param renderingRequired whether rendering is intrinsic to the level
         * @param triggerSummary stable trigger-policy description
         */
        public RepresentationPolicy {
            Objects.requireNonNull(level, "level");
            requireText(triggerSummary, "triggerSummary");
            if (!authoritativeStateRetained) {
                throw new IllegalArgumentException("All Stage-20 LOD levels must retain authoritative state");
            }
            if (fullTacticalRuntimeRequired && level != RepresentationLevel.TACTICAL) {
                throw new IllegalArgumentException("Only TACTICAL may require full tactical runtime");
            }
        }
    }

    /**
     * Existing production cadence evidence consumed by later wake-up calibration.
     *
     * @param tacticalTickSeconds fixed interval of the accepted live tactical runtime
     * @param activeLocalFixedStepSeconds fixed interval of the current headless local simulation session
     * @param strategicReducedSteppingAvailable whether the current clock exposes reduced strategic stepping
     * @param tacticalSource exact production provenance
     * @param activeLocalSource exact production provenance
     * @param strategicSource exact production provenance
     */
    public record RuntimeCadenceEvidence(
            double tacticalTickSeconds,
            double activeLocalFixedStepSeconds,
            boolean strategicReducedSteppingAvailable,
            String tacticalSource,
            String activeLocalSource,
            String strategicSource) {
        /**
         * Validates production cadence evidence.
         *
         * @param tacticalTickSeconds fixed tactical interval in seconds
         * @param activeLocalFixedStepSeconds current local fixed interval in seconds
         * @param strategicReducedSteppingAvailable whether reduced strategic stepping exists
         * @param tacticalSource tactical cadence provenance
         * @param activeLocalSource local cadence provenance
         * @param strategicSource strategic stepping provenance
         */
        public RuntimeCadenceEvidence {
            requirePositiveFinite(tacticalTickSeconds, "tacticalTickSeconds");
            requirePositiveFinite(activeLocalFixedStepSeconds, "activeLocalFixedStepSeconds");
            requireText(tacticalSource, "tacticalSource");
            requireText(activeLocalSource, "activeLocalSource");
            requireText(strategicSource, "strategicSource");
        }
    }

    /**
     * Current closure state of a numeric distance band for one representation level.
     *
     * @param level representation level whose promotion band is being described
     * @param authority numeric-band authority
     * @param activationDistanceM physical center/separation threshold when physically closed; absent when unresolved
     * @param provenance exact closure or unresolved provenance
     */
    public record DistanceBandClosure(
            RepresentationLevel level,
            DistanceBandAuthority authority,
            OptionalDouble activationDistanceM,
            String provenance) {
        /**
         * Validates one current distance-band closure state.
         *
         * @param level representation level
         * @param authority numeric-band authority
         * @param activationDistanceM derived activation threshold or absent gap
         * @param provenance closure/gap provenance
         */
        public DistanceBandClosure {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(authority, "authority");
            Objects.requireNonNull(activationDistanceM, "activationDistanceM");
            requireText(provenance, "provenance");
            activationDistanceM.ifPresent(value -> requireNonNegativeFinite(value, "activationDistanceM"));
            if (authority == DistanceBandAuthority.UNRESOLVED && activationDistanceM.isPresent()) {
                throw new IllegalArgumentException("Unresolved distance band cannot contain a fallback radius");
            }
            if (authority == DistanceBandAuthority.EXPLICIT_PHYSICAL_INPUT && activationDistanceM.isEmpty()) {
                throw new IllegalArgumentException("Physically closed distance band requires a numeric threshold");
            }
        }
    }

    /**
     * Relevance facts used to choose the minimum required computational representation.
     *
     * @param directTacticalInteraction real combat/sensor/weapon/docking interaction requires detailed physical execution
     * @param localOperationalRelevance nearby traffic/local-operation relevance requires reduced local execution
     * @param strategicRelevance strategic order/transit/economic relevance requires strategic execution
     * @param dueAuthoritativeEvent an authoritative scheduled event is due and must not be skipped by LOD
     */
    public record RelevanceInput(
            boolean directTacticalInteraction,
            boolean localOperationalRelevance,
            boolean strategicRelevance,
            boolean dueAuthoritativeEvent) {
    }

    /**
     * Explicit physical inputs for an interaction-driven promotion distance.
     *
     * @param interactionEnvelopeRadiusM accepted center/separation radius at which the detailed interaction domain begins
     * @param maximumClosingSpeedMps accepted maximum relevant closing speed for the promoted actor pair/group
     * @param maximumWakeLatencyS accepted maximum scheduler/materialization wake latency
     * @param provenance exact authority for all three inputs
     */
    public record InteractionActivationInput(
            double interactionEnvelopeRadiusM,
            double maximumClosingSpeedMps,
            double maximumWakeLatencyS,
            String provenance) {
        /**
         * Validates explicit interaction activation inputs.
         *
         * @param interactionEnvelopeRadiusM accepted physical interaction envelope radius
         * @param maximumClosingSpeedMps maximum relevant closing speed
         * @param maximumWakeLatencyS maximum accepted wake latency
         * @param provenance exact input provenance
         */
        public InteractionActivationInput {
            requireNonNegativeFinite(interactionEnvelopeRadiusM, "interactionEnvelopeRadiusM");
            requireNonNegativeFinite(maximumClosingSpeedMps, "maximumClosingSpeedMps");
            requireNonNegativeFinite(maximumWakeLatencyS, "maximumWakeLatencyS");
            requireText(provenance, "provenance");
        }
    }

    /**
     * Derived promotion-distance evidence from explicit physical inputs.
     *
     * @param interactionEnvelopeRadiusM accepted physical interaction envelope
     * @param closingDuringWakeM distance that can close during maximum wake latency
     * @param activationDistanceM minimum promotion distance preserving the entire wake margin
     * @param authority distance-band authority
     * @param provenance exact source provenance
     */
    public record InteractionActivationBand(
            double interactionEnvelopeRadiusM,
            double closingDuringWakeM,
            double activationDistanceM,
            DistanceBandAuthority authority,
            String provenance) {
        /**
         * Validates one derived interaction activation band.
         *
         * @param interactionEnvelopeRadiusM accepted interaction envelope
         * @param closingDuringWakeM physical closing distance during wake latency
         * @param activationDistanceM resulting promotion threshold
         * @param authority distance-band authority
         * @param provenance exact source provenance
         */
        public InteractionActivationBand {
            requireNonNegativeFinite(interactionEnvelopeRadiusM, "interactionEnvelopeRadiusM");
            requireNonNegativeFinite(closingDuringWakeM, "closingDuringWakeM");
            requireNonNegativeFinite(activationDistanceM, "activationDistanceM");
            Objects.requireNonNull(authority, "authority");
            requireText(provenance, "provenance");
            if (authority != DistanceBandAuthority.EXPLICIT_PHYSICAL_INPUT) {
                throw new IllegalArgumentException("Derived activation band requires explicit physical input authority");
            }
        }
    }

    /**
     * Render/culling result deliberately separated from physical and simulation authority.
     *
     * @param representationLevel required simulation representation
     * @param rendered whether current presentation policy renders the object
     * @param authoritativeStateRetained must remain true independent of rendering
     */
    public record RenderCullingDecision(
            RepresentationLevel representationLevel,
            boolean rendered,
            boolean authoritativeStateRetained) {
        /**
         * Validates one render/culling decision.
         *
         * @param representationLevel required simulation representation
         * @param rendered current presentation visibility
         * @param authoritativeStateRetained whether physical/persistent authority remains retained
         */
        public RenderCullingDecision {
            Objects.requireNonNull(representationLevel, "representationLevel");
            if (!authoritativeStateRetained) {
                throw new IllegalArgumentException("Render culling cannot delete authoritative state");
            }
        }
    }

    private static <T> List<T> sortedCopy(List<T> values, Comparator<? super T> comparator, String field) {
        Objects.requireNonNull(values, field);
        ArrayList<T> copy = new ArrayList<>(values);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " must not contain null");
        }
        copy.sort(comparator);
        return List.copyOf(copy);
    }

    private static List<String> sortedStrings(List<String> values, String field) {
        Objects.requireNonNull(values, field);
        ArrayList<String> copy = new ArrayList<>();
        for (String value : values) {
            copy.add(requireText(value, field + " entry"));
        }
        copy.sort(String::compareTo);
        return List.copyOf(copy);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
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
