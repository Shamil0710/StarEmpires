package com.spacesim.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Persistent Stage-20G knowledge about world-stable static objects for one actor or intelligence node.
 *
 * <p>This state deliberately does not persist mobile-fleet contacts. Mobile targets remain in the
 * system-local Stage-17.5 sensor domain, where {@code SensorMeasurement}, {@code TrackState},
 * covariance and age are authoritative. A static entry may retain a surveyed location across system
 * transitions because its identity is a world-stable generated/content identity rather than a local
 * runtime entity ID.</p>
 *
 * <p>Unknown objects are represented by absence. {@link #discoveryState(StaticObjectRef)} returns
 * {@link DiscoveryState#UNKNOWN} for that absence, so callers never need a fabricated placeholder
 * entry for every object in the generated galaxy.</p>
 *
 * @param ownerId stable player, faction or intelligence-network identity
 * @param entries deterministic static-object knowledge entries
 */
public record Stage20DiscoveryKnowledgeState(
        String ownerId,
        List<StaticKnowledge> entries) {

    /** Persistent discovery-quality vocabulary required by Stage 20G. */
    public enum DiscoveryState {
        /** No evidence has reached this knowledge owner. */ UNKNOWN,
        /** A rough contact exists without an exact identity or static location. */ DETECTED,
        /** Evidence supports a stable class/family identity. */ CLASSIFIED,
        /** Mobile position hypothesis; reserved for Stage-17.5 {@code TrackState}. */ TRACKED,
        /** A world-stable static location is known and may survive ordinary sensor aging. */ KNOWN_STATIC_LOCATION
    }

    /** World-stable static object categories supported by the Stage-20G durable knowledge domain. */
    public enum StaticObjectKind {
        /** Star, planet, moon or another persistent celestial landmark. */ CELESTIAL_BODY,
        /** Station, jump infrastructure or another persistent installed structure. */ INFRASTRUCTURE,
        /** Physical body/field capable of hosting natural resources. */ RESOURCE_HOST,
        /** One generated finite Stage-18 natural occurrence. */ RESOURCE_OCCURRENCE
    }

    /** Physical or institutional provenance for one received discovery fact. */
    public enum DiscoverySource {
        /** Local passive-channel observation. */ PASSIVE_SENSOR,
        /** Local active scan. */ ACTIVE_SCAN,
        /** Probe or reconnaissance-craft observation. */ PROBE_OR_RECON,
        /** Purchased or explicitly shared map/intelligence data. */ PURCHASED_OR_SHARED_MAP_DATA,
        /** Faction intelligence report. */ FACTION_INTELLIGENCE,
        /** Direct physical visit or survey. */ PHYSICAL_VISIT_OR_SURVEY,
        /** Received broadcast from persistent infrastructure. */ PERSISTENT_INFRASTRUCTURE_BROADCAST
    }

    /** Resource-specific information progression; none of these values is the physical reserve itself. */
    public enum ResourceKnowledgeLevel {
        /** Object has no resource-specific evidence. */ NONE,
        /** The physical host/body is known. */ HOST_KNOWN,
        /** Evidence indicates some resource-bearing material. */ RESOURCE_INDICATION,
        /** Evidence supports a resource family classification. */ CLASSIFIED_RESOURCE_FAMILY,
        /** Survey evidence provides bounded grade and recoverable-mass estimates. */ ESTIMATED_GRADE_RESERVE,
        /** A deposit has been physically surveyed, still through bounded estimates rather than truth state. */
        SURVEYED_DEPOSIT
    }

    /** Freshness summary derived from explicit evidence horizons. */
    public enum Freshness {
        /** At least one supporting item is intentionally non-expiring. */ PERMANENT,
        /** No item is permanent, but at least one item is still inside its freshness horizon. */ CURRENT,
        /** Every supporting item with a finite horizon has expired. */ STALE
    }

    /**
     * World-stable reference to one static generated/content object.
     *
     * @param systemId owning star system
     * @param kind static object category
     * @param objectId stable generated/content identity within its category
     */
    public record StaticObjectRef(
            StarSystemId systemId,
            StaticObjectKind kind,
            String objectId) implements Comparable<StaticObjectRef> {
        /**
         * Validates a static world identity.
         *
         * @param systemId owning star system
         * @param kind static object category
         * @param objectId stable generated/content identity
         */
        public StaticObjectRef {
            Objects.requireNonNull(systemId, "systemId");
            Objects.requireNonNull(kind, "kind");
            objectId = requireText(objectId, "objectId");
        }

        @Override
        public int compareTo(StaticObjectRef other) {
            Objects.requireNonNull(other, "other");
            int system = systemId.compareTo(other.systemId);
            if (system != 0) {
                return system;
            }
            int kindOrder = kind.compareTo(other.kind);
            return kindOrder != 0 ? kindOrder : objectId.compareTo(other.objectId);
        }

        /** @return whether this identity is in the resource-knowledge domain */
        public boolean resourceObject() {
            return kind == StaticObjectKind.RESOURCE_HOST
                    || kind == StaticObjectKind.RESOURCE_OCCURRENCE;
        }
    }

    /**
     * One supporting discovery item with explicit provenance and optional freshness horizon.
     *
     * <p>An empty {@code freshUntilSeconds} means the fact is intentionally durable, as for a
     * completed physical survey. A finite horizon does not delete the fact; it makes staleness
     * machine-visible to consumers.</p>
     *
     * @param source physical/institutional evidence source
     * @param provenanceId stable report, scan, survey or broadcaster identity
     * @param observedAtSeconds authoritative observation/receipt time
     * @param freshUntilSeconds inclusive freshness horizon, or empty for non-expiring evidence
     */
    public record DiscoveryEvidence(
            DiscoverySource source,
            String provenanceId,
            double observedAtSeconds,
            OptionalDouble freshUntilSeconds) implements Comparable<DiscoveryEvidence> {
        /**
         * Validates one evidence item without guessing a freshness policy from its source type.
         *
         * @param source evidence source
         * @param provenanceId stable provenance identity
         * @param observedAtSeconds observation/receipt time
         * @param freshUntilSeconds freshness horizon or empty for non-expiring evidence
         */
        public DiscoveryEvidence {
            Objects.requireNonNull(source, "source");
            provenanceId = requireText(provenanceId, "provenanceId");
            requireNonNegativeFinite(observedAtSeconds, "observedAtSeconds");
            Objects.requireNonNull(freshUntilSeconds, "freshUntilSeconds");
            if (freshUntilSeconds.isPresent()
                    && (!Double.isFinite(freshUntilSeconds.getAsDouble())
                    || freshUntilSeconds.getAsDouble() < observedAtSeconds)) {
                throw new IllegalArgumentException(
                        "freshUntilSeconds must be finite and not precede observedAtSeconds");
            }
        }

        /**
         * Checks the explicit evidence horizon.
         *
         * @param nowSeconds authoritative current time
         * @return whether this evidence has a finite freshness horizon that has passed
         */
        public boolean staleAt(double nowSeconds) {
            requireNonNegativeFinite(nowSeconds, "nowSeconds");
            if (nowSeconds < observedAtSeconds) {
                throw new IllegalArgumentException("nowSeconds cannot precede observation time");
            }
            return freshUntilSeconds.isPresent() && nowSeconds > freshUntilSeconds.getAsDouble();
        }

        /** @return whether this evidence intentionally has no expiry */
        public boolean permanent() {
            return freshUntilSeconds.isEmpty();
        }

        @Override
        public int compareTo(DiscoveryEvidence other) {
            Objects.requireNonNull(other, "other");
            int time = Double.compare(observedAtSeconds, other.observedAtSeconds);
            if (time != 0) {
                return time;
            }
            int sourceOrder = source.compareTo(other.source);
            if (sourceOrder != 0) {
                return sourceOrder;
            }
            int provenance = provenanceId.compareTo(other.provenanceId);
            if (provenance != 0) {
                return provenance;
            }
            if (freshUntilSeconds.isEmpty()) {
                return other.freshUntilSeconds.isEmpty() ? 0 : -1;
            }
            return other.freshUntilSeconds.isEmpty()
                    ? 1
                    : Double.compare(freshUntilSeconds.getAsDouble(), other.freshUntilSeconds.getAsDouble());
        }
    }

    /**
     * Non-degenerate resource estimate exposed to an observer rather than the generated physical reserve.
     *
     * @param minimumGradeFraction lower estimated useful-material fraction
     * @param maximumGradeFraction upper estimated useful-material fraction
     * @param minimumRecoverableMassKg lower estimated recoverable mass
     * @param maximumRecoverableMassKg upper estimated recoverable mass
     * @param confidence evidence confidence in {@code (0,1]}
     */
    public record ResourceEstimate(
            double minimumGradeFraction,
            double maximumGradeFraction,
            double minimumRecoverableMassKg,
            double maximumRecoverableMassKg,
            double confidence) {
        /**
         * Enforces bounded estimates and forbids an exact physical-reserve surrogate.
         *
         * @param minimumGradeFraction lower estimated grade
         * @param maximumGradeFraction upper estimated grade
         * @param minimumRecoverableMassKg lower estimated recoverable mass
         * @param maximumRecoverableMassKg upper estimated recoverable mass
         * @param confidence evidence confidence
         */
        public ResourceEstimate {
            requireNonNegativeFinite(minimumGradeFraction, "minimumGradeFraction");
            requirePositiveFinite(maximumGradeFraction, "maximumGradeFraction");
            if (maximumGradeFraction > 1d || minimumGradeFraction >= maximumGradeFraction) {
                throw new IllegalArgumentException("grade estimate must be a non-degenerate interval inside [0,1]");
            }
            requireNonNegativeFinite(minimumRecoverableMassKg, "minimumRecoverableMassKg");
            requirePositiveFinite(maximumRecoverableMassKg, "maximumRecoverableMassKg");
            if (minimumRecoverableMassKg >= maximumRecoverableMassKg) {
                throw new IllegalArgumentException("recoverable-mass estimate must be a non-degenerate interval");
            }
            if (!Double.isFinite(confidence) || confidence <= 0d || confidence > 1d) {
                throw new IllegalArgumentException("confidence must be finite in (0,1]");
            }
        }
    }

    /**
     * Resource-specific observer knowledge, never the authoritative {@code ResourceOccurrence} state.
     *
     * @param level current resource-knowledge progression
     * @param resourceFamilyId classified family when available
     * @param estimate bounded survey estimate when available
     */
    public record ResourceKnowledge(
            ResourceKnowledgeLevel level,
            Optional<String> resourceFamilyId,
            Optional<ResourceEstimate> estimate) {
        /**
         * Validates the resource information progression without filling missing facts from truth.
         *
         * @param level current resource-knowledge progression
         * @param resourceFamilyId classified family when available
         * @param estimate bounded observer estimate when available
         */
        public ResourceKnowledge {
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(resourceFamilyId, "resourceFamilyId");
            Objects.requireNonNull(estimate, "estimate");
            resourceFamilyId = resourceFamilyId.map(value -> requireText(value, "resourceFamilyId"));
            boolean classified = level.ordinal() >= ResourceKnowledgeLevel.CLASSIFIED_RESOURCE_FAMILY.ordinal();
            boolean estimated = level.ordinal() >= ResourceKnowledgeLevel.ESTIMATED_GRADE_RESERVE.ordinal();
            if (resourceFamilyId.isPresent() != classified) {
                throw new IllegalArgumentException(
                        "resource family must exist exactly at classified-or-better knowledge");
            }
            if (estimate.isPresent() != estimated) {
                throw new IllegalArgumentException(
                        "bounded estimate must exist exactly at estimated-or-surveyed knowledge");
            }
        }

        /** @return canonical absence of resource-specific evidence */
        public static ResourceKnowledge none() {
            return new ResourceKnowledge(ResourceKnowledgeLevel.NONE, Optional.empty(), Optional.empty());
        }
    }

    /**
     * One durable static-object knowledge row.
     *
     * @param object world-stable static object identity
     * @param state persistent discovery quality
     * @param classificationId stable object class/family identity when classified
     * @param knownLocation exact static SI location only at {@code KNOWN_STATIC_LOCATION}
     * @param resourceKnowledge resource-specific knowledge projection
     * @param evidence deterministically ordered provenance/freshness history
     * @param firstObservedSeconds time of the first retained evidence item
     * @param lastUpdatedSeconds time of the latest retained evidence item
     */
    public record StaticKnowledge(
            StaticObjectRef object,
            DiscoveryState state,
            Optional<String> classificationId,
            Optional<LocalPhysicalPosition> knownLocation,
            ResourceKnowledge resourceKnowledge,
            List<DiscoveryEvidence> evidence,
            double firstObservedSeconds,
            double lastUpdatedSeconds) implements Comparable<StaticKnowledge> {
        /**
         * Validates one static row and keeps mobile tracking outside this persistence domain.
         *
         * @param object world-stable static identity
         * @param state persistent discovery quality
         * @param classificationId classification when available
         * @param knownLocation exact static location when known
         * @param resourceKnowledge resource-specific observer knowledge
         * @param evidence provenance/freshness history
         * @param firstObservedSeconds first retained evidence time
         * @param lastUpdatedSeconds latest retained evidence time
         */
        public StaticKnowledge {
            Objects.requireNonNull(object, "object");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(classificationId, "classificationId");
            Objects.requireNonNull(knownLocation, "knownLocation");
            Objects.requireNonNull(resourceKnowledge, "resourceKnowledge");
            classificationId = classificationId.map(value -> requireText(value, "classificationId"));
            if (state == DiscoveryState.UNKNOWN) {
                throw new IllegalArgumentException("UNKNOWN is represented by absence, not a persisted row");
            }
            if (state == DiscoveryState.TRACKED) {
                throw new IllegalArgumentException("TRACKED mobile knowledge belongs to Stage-17.5 TrackState");
            }
            boolean classified = state == DiscoveryState.CLASSIFIED
                    || state == DiscoveryState.KNOWN_STATIC_LOCATION;
            if (classificationId.isPresent() != classified) {
                throw new IllegalArgumentException(
                        "classification identity must exist exactly at classified-or-static-location knowledge");
            }
            if (knownLocation.isPresent() != (state == DiscoveryState.KNOWN_STATIC_LOCATION)) {
                throw new IllegalArgumentException(
                        "exact static location must exist exactly at KNOWN_STATIC_LOCATION");
            }
            if (!object.resourceObject() && resourceKnowledge.level() != ResourceKnowledgeLevel.NONE) {
                throw new IllegalArgumentException("non-resource object cannot carry resource knowledge");
            }
            if (object.resourceObject()
                    && resourceKnowledge.level().ordinal() >= ResourceKnowledgeLevel.HOST_KNOWN.ordinal()
                    && state != DiscoveryState.KNOWN_STATIC_LOCATION) {
                throw new IllegalArgumentException("known resource host requires a known static location");
            }
            if (resourceKnowledge.level().ordinal()
                    >= ResourceKnowledgeLevel.CLASSIFIED_RESOURCE_FAMILY.ordinal()
                    && !resourceKnowledge.resourceFamilyId().equals(classificationId)) {
                throw new IllegalArgumentException(
                        "classified resource family must be the static-object classification identity");
            }

            ArrayList<DiscoveryEvidence> evidenceCopy = new ArrayList<>(
                    Objects.requireNonNull(evidence, "evidence"));
            if (evidenceCopy.isEmpty() || evidenceCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("static knowledge requires non-empty evidence");
            }
            evidenceCopy.sort(null);
            Set<DiscoveryEvidence> unique = new HashSet<>();
            for (DiscoveryEvidence item : evidenceCopy) {
                if (!unique.add(item)) {
                    throw new IllegalArgumentException("duplicate discovery evidence: " + item);
                }
            }
            evidence = List.copyOf(evidenceCopy);
            requireNonNegativeFinite(firstObservedSeconds, "firstObservedSeconds");
            requireNonNegativeFinite(lastUpdatedSeconds, "lastUpdatedSeconds");
            if (firstObservedSeconds != evidence.get(0).observedAtSeconds()
                    || lastUpdatedSeconds != evidence.get(evidence.size() - 1).observedAtSeconds()) {
                throw new IllegalArgumentException("first/last observation times must match retained evidence");
            }
        }

        /**
         * Derives a freshness summary without deleting durable knowledge.
         *
         * @param nowSeconds authoritative current time
         * @return freshness summary at that time
         */
        public Freshness freshnessAt(double nowSeconds) {
            requireNonNegativeFinite(nowSeconds, "nowSeconds");
            if (nowSeconds < lastUpdatedSeconds) {
                throw new IllegalArgumentException("nowSeconds cannot precede latest evidence");
            }
            if (evidence.stream().anyMatch(DiscoveryEvidence::permanent)) {
                return Freshness.PERMANENT;
            }
            return evidence.stream().anyMatch(item -> !item.staleAt(nowSeconds))
                    ? Freshness.CURRENT
                    : Freshness.STALE;
        }

        @Override
        public int compareTo(StaticKnowledge other) {
            return object.compareTo(Objects.requireNonNull(other, "other").object);
        }
    }

    /**
     * Validates, sorts and freezes one actor-local knowledge snapshot.
     *
     * @param ownerId stable knowledge-owner identity
     * @param entries durable static-object entries
     */
    public Stage20DiscoveryKnowledgeState {
        ownerId = requireText(ownerId, "ownerId");
        ArrayList<StaticKnowledge> copy = new ArrayList<>(Objects.requireNonNull(entries, "entries"));
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("entries cannot contain null");
        }
        copy.sort(null);
        Set<StaticObjectRef> objects = new HashSet<>();
        for (StaticKnowledge entry : copy) {
            if (!objects.add(entry.object())) {
                throw new IllegalArgumentException("duplicate static knowledge object: " + entry.object());
            }
        }
        entries = List.copyOf(copy);
    }

    /**
     * Returns the explicit discovery quality for an object, including {@code UNKNOWN} for absence.
     *
     * @param object world-stable static object identity
     * @return current persistent discovery state
     */
    public DiscoveryState discoveryState(StaticObjectRef object) {
        return knowledge(object).map(StaticKnowledge::state).orElse(DiscoveryState.UNKNOWN);
    }

    /**
     * Finds the durable row for one static object.
     *
     * @param object world-stable static object identity
     * @return row when any evidence exists
     */
    public Optional<StaticKnowledge> knowledge(StaticObjectRef object) {
        StaticObjectRef checked = Objects.requireNonNull(object, "object");
        return entries.stream().filter(value -> value.object().equals(checked)).findFirst();
    }

    /**
     * Replaces or inserts one row and returns a new canonical snapshot.
     *
     * @param knowledge updated static knowledge
     * @return new owner-local state
     */
    public Stage20DiscoveryKnowledgeState withKnowledge(StaticKnowledge knowledge) {
        StaticKnowledge checked = Objects.requireNonNull(knowledge, "knowledge");
        ArrayList<StaticKnowledge> copy = new ArrayList<>(entries);
        copy.removeIf(value -> value.object().equals(checked.object()));
        copy.add(checked);
        return new Stage20DiscoveryKnowledgeState(ownerId, copy);
    }

    /** Returns the static-state strength used only inside the static persistence domain. */
    static int staticRank(DiscoveryState state) {
        return switch (Objects.requireNonNull(state, "state")) {
            case UNKNOWN -> 0;
            case DETECTED -> 1;
            case CLASSIFIED -> 2;
            case KNOWN_STATIC_LOCATION -> 3;
            case TRACKED -> throw new IllegalArgumentException("TRACKED has no static knowledge rank");
        };
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }
}
