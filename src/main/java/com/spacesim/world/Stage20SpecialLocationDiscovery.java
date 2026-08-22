package com.spacesim.world;

import com.spacesim.world.Stage20DiscoveryKnowledgeRuntime.StaticObservation;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryEvidence;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoverySource;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledge;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef;
import com.spacesim.world.Stage20SpecialLocationWorld.ScanRequirement;
import com.spacesim.world.Stage20SpecialLocationWorld.SpecialLocation;

import java.util.Objects;
import java.util.Optional;

/**
 * Observer-evidence adapter from Stage-20H special locations into Stage-20G static knowledge.
 *
 * <p>The adapter never enumerates the special-location world for an actor. It is called only after
 * an actual sensor, recon or survey action has produced evidence. Weak methods may create a contact
 * without revealing truth classification, while exact static location is granted only by a physical
 * visit/survey.</p>
 */
public final class Stage20SpecialLocationDiscovery {
    private Stage20SpecialLocationDiscovery() {
        throw new AssertionError("No instances");
    }

    /** Physical method that produced one special-location observation. */
    public enum ObservationMethod {
        /** Passive local sensor observation. */ PASSIVE_SENSOR,
        /** Active local scan. */ ACTIVE_SCAN,
        /** Probe or reconnaissance-craft observation. */ PROBE_OR_RECON,
        /** Direct physical visit or survey. */ PHYSICAL_SURVEY
    }

    /**
     * Builds one observer-local fact without copying finite salvage/resource truth.
     *
     * @param location actually observed physical special location
     * @param method physical observation method
     * @param evidence matching provenance/freshness evidence
     * @return Stage-20G observation suitable for the ordinary merge runtime
     */
    public static StaticObservation observe(
            SpecialLocation location,
            ObservationMethod method,
            DiscoveryEvidence evidence) {
        SpecialLocation target = Objects.requireNonNull(location, "location");
        ObservationMethod observationMethod = Objects.requireNonNull(method, "method");
        DiscoveryEvidence proof = Objects.requireNonNull(evidence, "evidence");
        requireMatchingSource(observationMethod, proof.source());

        DiscoveryState state;
        if (observationMethod == ObservationMethod.PHYSICAL_SURVEY) {
            state = DiscoveryState.KNOWN_STATIC_LOCATION;
        } else if (methodRank(observationMethod) >= requirementRank(target.scanRequirement())) {
            state = DiscoveryState.CLASSIFIED;
        } else {
            state = DiscoveryState.DETECTED;
        }
        Optional<String> classification = state == DiscoveryState.DETECTED
                ? Optional.empty()
                : Optional.of(target.archetypeId());
        Optional<LocalPhysicalPosition> locationEvidence = state == DiscoveryState.KNOWN_STATIC_LOCATION
                ? Optional.of(target.position())
                : Optional.empty();
        return new StaticObservation(
                new StaticObjectRef(target.systemId(), StaticObjectKind.SPECIAL_LOCATION, target.locationId()),
                state,
                classification,
                locationEvidence,
                ResourceKnowledge.none(),
                proof);
    }

    private static int methodRank(ObservationMethod method) {
        return switch (method) {
            case PASSIVE_SENSOR -> 0;
            case ACTIVE_SCAN -> 1;
            case PROBE_OR_RECON -> 2;
            case PHYSICAL_SURVEY -> 3;
        };
    }

    private static int requirementRank(ScanRequirement requirement) {
        return switch (Objects.requireNonNull(requirement, "requirement")) {
            case PASSIVE_CLASSIFICATION -> 0;
            case ACTIVE_CLASSIFICATION -> 1;
            case PHYSICAL_SURVEY -> 3;
        };
    }

    private static void requireMatchingSource(ObservationMethod method, DiscoverySource source) {
        DiscoverySource expected = switch (method) {
            case PASSIVE_SENSOR -> DiscoverySource.PASSIVE_SENSOR;
            case ACTIVE_SCAN -> DiscoverySource.ACTIVE_SCAN;
            case PROBE_OR_RECON -> DiscoverySource.PROBE_OR_RECON;
            case PHYSICAL_SURVEY -> DiscoverySource.PHYSICAL_VISIT_OR_SURVEY;
        };
        if (source != expected) {
            throw new IllegalArgumentException("special-location observation method and evidence source differ");
        }
    }
}
