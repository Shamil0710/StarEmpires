package com.spacesim.world;

import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.economy.Stage18ExtractionRuntime.PhysicalSourceState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Immutable Stage-20E resource-geography result.
 *
 * <p>Natural occurrences are finite generated world state that can be projected directly into the
 * Stage-18 extraction runtime. Latent system conditions are generation provenance only; they are not
 * runtime production multipliers. Initial extraction sites reference real Stage-18 facility and
 * extraction-method identities instead of granting hidden mining ability to a station role.</p>
 *
 * @param version stable Stage-20E world result version
 * @param rootSeed deterministic root generation seed
 * @param systemConditions correlated latent physical conditions by system
 * @param occurrences concrete finite Stage-18 natural source streams
 * @param initialExtractionSites explicit bootstrap extraction installations
 * @param ontologyFingerprint exact Stage-18 ontology fingerprint consumed
 * @param extractionFingerprint exact Stage-18 extraction-catalog fingerprint consumed
 * @param facilityFingerprint exact Stage-18 facility-catalog fingerprint consumed
 * @param generationProfileVersion exact Stage-20E calibration profile version consumed
 */
public record Stage20ResourceOccurrenceWorld(
        String version,
        long rootSeed,
        List<SystemResourceConditions> systemConditions,
        List<ResourceOccurrence> occurrences,
        List<InitialExtractionSite> initialExtractionSites,
        String ontologyFingerprint,
        String extractionFingerprint,
        String facilityFingerprint,
        String generationProfileVersion) {
    /** Current immutable Stage-20E occurrence-world version. */
    public static final String CURRENT_VERSION = "stage20e.resource-occurrences.v1";

    /**
     * Correlated latent physical conditions for one star system.
     *
     * @param systemId owning system
     * @param occurrencePotentialByTypeId normalized potential in {@code [0,1]} by Stage-18 occurrence ID
     */
    public record SystemResourceConditions(
            StarSystemId systemId,
            Map<String, Double> occurrencePotentialByTypeId) {
        /**
         * Validates and deterministically freezes one condition row.
         *
         * @param systemId owning system
         * @param occurrencePotentialByTypeId normalized potential in {@code [0,1]} by Stage-18 occurrence ID
         */
        public SystemResourceConditions {
            Objects.requireNonNull(systemId, "systemId");
            Objects.requireNonNull(occurrencePotentialByTypeId, "occurrencePotentialByTypeId");
            TreeMap<String, Double> copy = new TreeMap<>();
            for (Map.Entry<String, Double> entry : occurrencePotentialByTypeId.entrySet()) {
                String typeId = requireText(entry.getKey(), "occurrence type ID");
                double value = Objects.requireNonNull(entry.getValue(), "occurrence potential");
                requireFractionInclusive(value, "occurrence potential");
                copy.put(typeId, value);
            }
            if (copy.isEmpty()) {
                throw new IllegalArgumentException("system conditions must contain occurrence potentials");
            }
            occurrencePotentialByTypeId = Collections.unmodifiableMap(copy);
        }

        /**
         * Returns one normalized latent potential.
         *
         * @param occurrenceTypeId Stage-18 occurrence type ID
         * @return potential in {@code [0,1]}
         */
        public double potential(String occurrenceTypeId) {
            Double value = occurrencePotentialByTypeId.get(requireText(occurrenceTypeId, "occurrenceTypeId"));
            if (value == null) {
                throw new IllegalArgumentException("Unknown occurrence potential: " + occurrenceTypeId);
            }
            return value;
        }
    }

    /**
     * One concrete finite natural source stream generated on a Stage-20C resource-field anchor.
     *
     * @param sourceId stable generated source identity
     * @param systemId owning star system
     * @param hostAnchorId owning Stage-20C resource-field anchor
     * @param hostClassId generation-only physical host classifier/provenance
     * @param position authoritative SI anchor position
     * @param occurrenceTypeId authoritative Stage-18 occurrence type ID
     * @param environment Stage-18 extraction environment
     * @param outputCommodityId Stage-18 extracted feedstock produced by this source stream
     * @param generationScore final correlated host-presence score in {@code [0,1]}
     * @param initialAccessibleMassKg finite gross accessible source mass
     * @param gradeFraction useful target fraction in gross removed source mass
     * @param sourceRecoveryFraction source-side recoverability fraction
     * @param requiredCapabilityTags source-specific capabilities beyond method baseline
     */
    public record ResourceOccurrence(
            String sourceId,
            StarSystemId systemId,
            String hostAnchorId,
            String hostClassId,
            LocalPhysicalPosition position,
            String occurrenceTypeId,
            ExtractionEnvironment environment,
            String outputCommodityId,
            double generationScore,
            double initialAccessibleMassKg,
            double gradeFraction,
            double sourceRecoveryFraction,
            Set<String> requiredCapabilityTags) {
        /**
         * Validates one immutable finite natural occurrence stream.
         *
         * @param sourceId stable generated source identity
         * @param systemId owning star system
         * @param hostAnchorId owning Stage-20C resource-field anchor
         * @param hostClassId generation-only physical host classifier/provenance
         * @param position authoritative SI anchor position
         * @param occurrenceTypeId authoritative Stage-18 occurrence type ID
         * @param environment Stage-18 extraction environment
         * @param outputCommodityId Stage-18 extracted feedstock produced by this source stream
         * @param generationScore final correlated host-presence score in {@code [0,1]}
         * @param initialAccessibleMassKg finite gross accessible source mass
         * @param gradeFraction useful target fraction in gross removed source mass
         * @param sourceRecoveryFraction source-side recoverability fraction
         * @param requiredCapabilityTags source-specific capabilities beyond method baseline
         */
        public ResourceOccurrence {
            sourceId = requireText(sourceId, "sourceId");
            Objects.requireNonNull(systemId, "systemId");
            hostAnchorId = requireText(hostAnchorId, "hostAnchorId");
            hostClassId = requireText(hostClassId, "hostClassId");
            Objects.requireNonNull(position, "position");
            occurrenceTypeId = requireText(occurrenceTypeId, "occurrenceTypeId");
            Objects.requireNonNull(environment, "environment");
            outputCommodityId = requireText(outputCommodityId, "outputCommodityId");
            requireFractionInclusive(generationScore, "generationScore");
            requirePositiveFinite(initialAccessibleMassKg, "initialAccessibleMassKg");
            requirePositiveFraction(gradeFraction, "gradeFraction");
            requirePositiveFraction(sourceRecoveryFraction, "sourceRecoveryFraction");
            requiredCapabilityTags = immutableTags(requiredCapabilityTags);
        }

        /**
         * Creates a fresh Stage-18 physical source state at full generated reserve.
         *
         * @return mutable Stage-18 extraction source state
         */
        public PhysicalSourceState toPhysicalSourceState() {
            return new PhysicalSourceState(
                    sourceId,
                    SourceKind.NATURAL_OCCURRENCE,
                    occurrenceTypeId,
                    environment,
                    outputCommodityId,
                    initialAccessibleMassKg,
                    initialAccessibleMassKg,
                    gradeFraction,
                    sourceRecoveryFraction,
                    requiredCapabilityTags);
        }
    }

    /**
     * Explicit initial extraction installation attached to one generated natural source.
     *
     * @param siteId stable generated site identity
     * @param sourceId generated occurrence source served by the installation
     * @param systemId owning system
     * @param hostAnchorId owning physical resource-field anchor
     * @param locationTag Stage-18 facility physical-location tag
     * @param facilityDefinitionId installed Stage-18 facility definition
     * @param extractionMethodId Stage-18 extraction method used by the installation
     */
    public record InitialExtractionSite(
            String siteId,
            String sourceId,
            StarSystemId systemId,
            String hostAnchorId,
            String locationTag,
            String facilityDefinitionId,
            String extractionMethodId) {
        /**
         * Validates one immutable explicit extraction installation.
         *
         * @param siteId stable generated site identity
         * @param sourceId generated occurrence source served by the installation
         * @param systemId owning system
         * @param hostAnchorId owning physical resource-field anchor
         * @param locationTag Stage-18 facility physical-location tag
         * @param facilityDefinitionId installed Stage-18 facility definition
         * @param extractionMethodId Stage-18 extraction method used by the installation
         */
        public InitialExtractionSite {
            siteId = requireText(siteId, "siteId");
            sourceId = requireText(sourceId, "sourceId");
            Objects.requireNonNull(systemId, "systemId");
            hostAnchorId = requireText(hostAnchorId, "hostAnchorId");
            locationTag = requireText(locationTag, "locationTag");
            facilityDefinitionId = requireText(facilityDefinitionId, "facilityDefinitionId");
            extractionMethodId = requireText(extractionMethodId, "extractionMethodId");
        }
    }

    /**
     * Validates, sorts and freezes the complete generated resource world.
     *
     * @param version stable Stage-20E world result version
     * @param rootSeed deterministic root generation seed
     * @param systemConditions correlated latent physical conditions by system
     * @param occurrences concrete finite Stage-18 natural source streams
     * @param initialExtractionSites explicit bootstrap extraction installations
     * @param ontologyFingerprint exact Stage-18 ontology fingerprint consumed
     * @param extractionFingerprint exact Stage-18 extraction-catalog fingerprint consumed
     * @param facilityFingerprint exact Stage-18 facility-catalog fingerprint consumed
     * @param generationProfileVersion exact Stage-20E calibration profile version consumed
     */
    public Stage20ResourceOccurrenceWorld {
        version = requireText(version, "version");
        ontologyFingerprint = requireText(ontologyFingerprint, "ontologyFingerprint");
        extractionFingerprint = requireText(extractionFingerprint, "extractionFingerprint");
        facilityFingerprint = requireText(facilityFingerprint, "facilityFingerprint");
        generationProfileVersion = requireText(generationProfileVersion, "generationProfileVersion");
        Objects.requireNonNull(systemConditions, "systemConditions");
        Objects.requireNonNull(occurrences, "occurrences");
        Objects.requireNonNull(initialExtractionSites, "initialExtractionSites");

        ArrayList<SystemResourceConditions> conditionsCopy = new ArrayList<>(systemConditions);
        ArrayList<ResourceOccurrence> occurrenceCopy = new ArrayList<>(occurrences);
        ArrayList<InitialExtractionSite> siteCopy = new ArrayList<>(initialExtractionSites);
        if (conditionsCopy.isEmpty() || conditionsCopy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("systemConditions must be non-empty and contain no nulls");
        }
        if (occurrenceCopy.stream().anyMatch(Objects::isNull) || siteCopy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("occurrences/sites cannot contain nulls");
        }
        conditionsCopy.sort(Comparator.comparing(SystemResourceConditions::systemId));
        occurrenceCopy.sort(Comparator.comparing(ResourceOccurrence::sourceId));
        siteCopy.sort(Comparator.comparing(InitialExtractionSite::siteId));
        systemConditions = List.copyOf(conditionsCopy);
        occurrences = List.copyOf(occurrenceCopy);
        initialExtractionSites = List.copyOf(siteCopy);

        HashSet<StarSystemId> conditionSystems = new HashSet<>();
        for (SystemResourceConditions conditions : systemConditions) {
            if (!conditionSystems.add(conditions.systemId())) {
                throw new IllegalArgumentException("duplicate system conditions: " + conditions.systemId());
            }
        }
        HashSet<String> sourceIds = new HashSet<>();
        Map<String, ResourceOccurrence> sourcesById = new HashMap<>();
        for (ResourceOccurrence occurrence : occurrences) {
            if (!sourceIds.add(occurrence.sourceId())) {
                throw new IllegalArgumentException("duplicate occurrence sourceId: " + occurrence.sourceId());
            }
            sourcesById.put(occurrence.sourceId(), occurrence);
            if (!conditionSystems.contains(occurrence.systemId())) {
                throw new IllegalArgumentException("occurrence has no owning system conditions: " + occurrence.sourceId());
            }
        }
        HashSet<String> siteIds = new HashSet<>();
        HashSet<String> siteSources = new HashSet<>();
        for (InitialExtractionSite site : initialExtractionSites) {
            if (!siteIds.add(site.siteId())) {
                throw new IllegalArgumentException("duplicate extraction siteId: " + site.siteId());
            }
            if (!sourceIds.contains(site.sourceId())) {
                throw new IllegalArgumentException("extraction site references unknown source: " + site.sourceId());
            }
            if (!siteSources.add(site.sourceId())) {
                throw new IllegalArgumentException("source has more than one initial extraction site: " + site.sourceId());
            }
            ResourceOccurrence source = sourcesById.get(site.sourceId());
            if (!source.systemId().equals(site.systemId()) || !source.hostAnchorId().equals(site.hostAnchorId())) {
                throw new IllegalArgumentException("extraction site must share system/host with its source: " + site.siteId());
            }
        }
    }

    /**
     * Finds generated latent conditions for one system.
     *
     * @param systemId system ID
     * @return conditions
     */
    public SystemResourceConditions conditions(StarSystemId systemId) {
        Objects.requireNonNull(systemId, "systemId");
        return systemConditions.stream()
                .filter(value -> value.systemId().equals(systemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown system conditions: " + systemId));
    }

    /**
     * Finds one generated occurrence by stable source ID.
     *
     * @param sourceId generated source identity
     * @return occurrence
     */
    public ResourceOccurrence occurrence(String sourceId) {
        String checked = requireText(sourceId, "sourceId");
        return occurrences.stream()
                .filter(value -> value.sourceId().equals(checked))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown generated occurrence: " + checked));
    }

    private static Set<String> immutableTags(Set<String> source) {
        Objects.requireNonNull(source, "requiredCapabilityTags");
        TreeSet<String> copy = new TreeSet<>();
        for (String value : source) {
            copy.add(requireText(value, "required capability tag"));
        }
        return Collections.unmodifiableSet(copy);
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

    private static void requirePositiveFraction(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d || value > 1d) {
            throw new IllegalArgumentException(field + " must be in (0,1]");
        }
    }

    private static void requireFractionInclusive(double value, String field) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(field + " must be in [0,1]");
        }
    }
}
