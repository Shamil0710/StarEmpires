package com.spacesim.world;

import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryEvidence;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledge;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticKnowledge;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic merge runtime for persistent Stage-20G static discovery.
 *
 * <p>The runtime accepts observer-local facts, never generated truth. Weaker later reports add
 * provenance/freshness without erasing stronger survey knowledge. Conflicting stable identity or
 * location claims fail closed instead of silently rewriting a world-stable object. Resource
 * estimates may be refreshed only by an equally strong or stronger resource observation.</p>
 */
public final class Stage20DiscoveryKnowledgeRuntime {

    /**
     * One received static-object observation.
     *
     * @param object world-stable static identity
     * @param state evidence quality; {@code UNKNOWN} and {@code TRACKED} are invalid here
     * @param classificationId stable class/family identity when classified
     * @param knownLocation exact static location only for {@code KNOWN_STATIC_LOCATION}
     * @param resourceKnowledge resource-specific observer knowledge
     * @param evidence physical/institutional provenance and freshness
     */
    public record StaticObservation(
            StaticObjectRef object,
            DiscoveryState state,
            Optional<String> classificationId,
            Optional<LocalPhysicalPosition> knownLocation,
            ResourceKnowledge resourceKnowledge,
            DiscoveryEvidence evidence) {
        /**
         * Validates an observation through the same invariants used by persistent rows.
         *
         * @param object world-stable static identity
         * @param state received discovery quality
         * @param classificationId classification when available
         * @param knownLocation exact static location when available
         * @param resourceKnowledge resource-specific observer knowledge
         * @param evidence provenance and freshness
         */
        public StaticObservation {
            Objects.requireNonNull(object, "object");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(classificationId, "classificationId");
            Objects.requireNonNull(knownLocation, "knownLocation");
            Objects.requireNonNull(resourceKnowledge, "resourceKnowledge");
            Objects.requireNonNull(evidence, "evidence");
            toKnowledge(object, state, classificationId, knownLocation, resourceKnowledge, List.of(evidence));
        }
    }

    /**
     * Applies one received observation to one actor-local snapshot.
     *
     * @param state existing actor-local static knowledge
     * @param observation newly received observer-local fact
     * @return new deterministic snapshot
     */
    public Stage20DiscoveryKnowledgeState observe(
            Stage20DiscoveryKnowledgeState state,
            StaticObservation observation) {
        Stage20DiscoveryKnowledgeState current = Objects.requireNonNull(state, "state");
        StaticObservation received = Objects.requireNonNull(observation, "observation");
        Optional<StaticKnowledge> prior = current.knowledge(received.object());
        if (prior.isEmpty()) {
            return current.withKnowledge(toKnowledge(
                    received.object(),
                    received.state(),
                    received.classificationId(),
                    received.knownLocation(),
                    received.resourceKnowledge(),
                    List.of(received.evidence())));
        }
        return current.withKnowledge(merge(prior.orElseThrow(), received));
    }

    private static StaticKnowledge merge(StaticKnowledge prior, StaticObservation received) {
        Optional<String> classification = mergeStableOptional(
                prior.classificationId(), received.classificationId(), "classification identity");
        Optional<LocalPhysicalPosition> location = mergeStableOptional(
                prior.knownLocation(), received.knownLocation(), "static location");

        DiscoveryState state = Stage20DiscoveryKnowledgeState.staticRank(received.state())
                > Stage20DiscoveryKnowledgeState.staticRank(prior.state())
                ? received.state()
                : prior.state();
        ResourceKnowledge resource = mergeResource(prior, received);

        ArrayList<DiscoveryEvidence> evidence = new ArrayList<>(prior.evidence());
        if (!evidence.contains(received.evidence())) {
            evidence.add(received.evidence());
        }
        return toKnowledge(prior.object(), state, classification, location, resource, evidence);
    }

    private static ResourceKnowledge mergeResource(StaticKnowledge prior, StaticObservation received) {
        ResourceKnowledge current = prior.resourceKnowledge();
        ResourceKnowledge next = received.resourceKnowledge();
        int comparison = Integer.compare(next.level().ordinal(), current.level().ordinal());
        if (comparison > 0) {
            verifyResourceFamily(current, next);
            return next;
        }
        if (comparison < 0) {
            verifyResourceFamily(next, current);
            return current;
        }
        verifyResourceFamily(current, next);
        return received.evidence().observedAtSeconds() >= prior.lastUpdatedSeconds() ? next : current;
    }

    private static void verifyResourceFamily(ResourceKnowledge left, ResourceKnowledge right) {
        if (left.resourceFamilyId().isPresent()
                && right.resourceFamilyId().isPresent()
                && !left.resourceFamilyId().equals(right.resourceFamilyId())) {
            throw new IllegalArgumentException("conflicting resource-family identity");
        }
    }

    private static <T> Optional<T> mergeStableOptional(Optional<T> prior, Optional<T> received, String field) {
        if (prior.isPresent() && received.isPresent() && !prior.equals(received)) {
            throw new IllegalArgumentException("conflicting " + field);
        }
        return received.isPresent() ? received : prior;
    }

    private static StaticKnowledge toKnowledge(
            StaticObjectRef object,
            DiscoveryState state,
            Optional<String> classification,
            Optional<LocalPhysicalPosition> location,
            ResourceKnowledge resource,
            List<DiscoveryEvidence> evidence) {
        List<DiscoveryEvidence> sorted = evidence.stream().sorted().toList();
        return new StaticKnowledge(
                object,
                state,
                classification,
                location,
                resource,
                sorted,
                sorted.get(0).observedAtSeconds(),
                sorted.get(sorted.size() - 1).observedAtSeconds());
    }
}
