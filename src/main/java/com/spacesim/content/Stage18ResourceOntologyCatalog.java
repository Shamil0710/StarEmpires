package com.spacesim.content;

import com.spacesim.model.ItemCategory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable Stage-18A ontology for physical resource and industrial commodity families.
 *
 * <p>The ontology intentionally does not contain reserves, extraction yields, recipes, facility
 * throughput or world placement. Those belong to later Stage-18 slices. It defines the stable
 * language those systems will reference while keeping legacy early-game item IDs explicit.</p>
 */
public final class Stage18ResourceOntologyCatalog {
    private final int schemaVersion;
    private final List<StorageClassDefinition> storageClasses;
    private final List<CapabilityTagDefinition> capabilityTags;
    private final List<CommodityDefinition> commodities;
    private final List<ResourceOccurrenceTypeDefinition> occurrenceTypes;
    private final List<LegacyItemMappingDefinition> legacyMappings;
    private final Map<String, StorageClassDefinition> storageClassesById;
    private final Map<String, CapabilityTagDefinition> capabilityTagsById;
    private final Map<String, CommodityDefinition> commoditiesById;
    private final Map<String, ResourceOccurrenceTypeDefinition> occurrenceTypesById;
    private final Map<String, LegacyItemMappingDefinition> legacyMappingsByItemId;
    private final String fingerprint;

    Stage18ResourceOntologyCatalog(
            int schemaVersion,
            List<StorageClassDefinition> storageClasses,
            List<CapabilityTagDefinition> capabilityTags,
            List<CommodityDefinition> commodities,
            List<ResourceOccurrenceTypeDefinition> occurrenceTypes,
            List<LegacyItemMappingDefinition> legacyMappings) {
        this.schemaVersion = schemaVersion;
        this.storageClasses = sortedCopy(storageClasses, Comparator.comparing(StorageClassDefinition::id));
        this.capabilityTags = sortedCopy(capabilityTags, Comparator.comparing(CapabilityTagDefinition::id));
        this.commodities = sortedCopy(commodities, Comparator.comparing(CommodityDefinition::id));
        this.occurrenceTypes = sortedCopy(occurrenceTypes, Comparator.comparing(ResourceOccurrenceTypeDefinition::id));
        this.legacyMappings = sortedCopy(
                legacyMappings, Comparator.comparing(LegacyItemMappingDefinition::legacyItemContentId));
        this.storageClassesById = index(this.storageClasses, StorageClassDefinition::id);
        this.capabilityTagsById = index(this.capabilityTags, CapabilityTagDefinition::id);
        this.commoditiesById = index(this.commodities, CommodityDefinition::id);
        this.occurrenceTypesById = index(this.occurrenceTypes, ResourceOccurrenceTypeDefinition::id);
        this.legacyMappingsByItemId = index(
                this.legacyMappings, LegacyItemMappingDefinition::legacyItemContentId);
        this.fingerprint = computeFingerprint();
    }

    /** @return ontology schema version */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** @return deterministic immutable storage-class definitions */
    public List<StorageClassDefinition> getStorageClasses() {
        return storageClasses;
    }

    /** @return deterministic immutable process/capability tags */
    public List<CapabilityTagDefinition> getCapabilityTags() {
        return capabilityTags;
    }

    /** @return deterministic immutable extracted/material/consumable/component families */
    public List<CommodityDefinition> getCommodities() {
        return commodities;
    }

    /** @return deterministic immutable natural occurrence types */
    public List<ResourceOccurrenceTypeDefinition> getOccurrenceTypes() {
        return occurrenceTypes;
    }

    /** @return deterministic immutable legacy migration mappings */
    public List<LegacyItemMappingDefinition> getLegacyMappings() {
        return legacyMappings;
    }

    /** @return lowercase SHA-256 semantic fingerprint */
    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * Finds a storage class by stable content ID.
     *
     * @param id stable storage-class ID
     * @return definition or {@code null}
     */
    public StorageClassDefinition findStorageClass(String id) {
        return storageClassesById.get(id);
    }

    /**
     * Finds a capability tag by stable content ID.
     *
     * @param id stable capability ID
     * @return definition or {@code null}
     */
    public CapabilityTagDefinition findCapabilityTag(String id) {
        return capabilityTagsById.get(id);
    }

    /**
     * Finds a commodity family by stable content ID.
     *
     * @param id stable commodity ID
     * @return definition or {@code null}
     */
    public CommodityDefinition findCommodity(String id) {
        return commoditiesById.get(id);
    }

    /**
     * Finds a natural occurrence type by stable content ID.
     *
     * @param id stable occurrence type ID
     * @return definition or {@code null}
     */
    public ResourceOccurrenceTypeDefinition findOccurrenceType(String id) {
        return occurrenceTypesById.get(id);
    }

    /**
     * Finds the explicit Stage-18 migration disposition for one legacy item.
     *
     * @param legacyItemContentId persistent legacy item ID
     * @return mapping or {@code null}
     */
    public LegacyItemMappingDefinition findLegacyMapping(String legacyItemContentId) {
        return legacyMappingsByItemId.get(legacyItemContentId);
    }

    private String computeFingerprint() {
        StringBuilder canonical = new StringBuilder(8192);
        canonical.append("schema=").append(schemaVersion).append('\n');
        for (StorageClassDefinition definition : storageClasses) {
            canonical.append("storage|").append(definition.id()).append('|')
                    .append(definition.displayName()).append('|')
                    .append(definition.legacyCategory().name()).append('\n');
        }
        for (CapabilityTagDefinition definition : capabilityTags) {
            canonical.append("capability|").append(definition.id()).append('|')
                    .append(definition.displayName()).append('\n');
        }
        for (CommodityDefinition definition : commodities) {
            canonical.append("commodity|").append(definition.id()).append('|')
                    .append(definition.codeName()).append('|').append(definition.displayName()).append('|')
                    .append(definition.kind().name()).append('|').append(definition.storageClassId()).append('|')
                    .append(definition.quantityUnit().name()).append('\n');
        }
        for (ResourceOccurrenceTypeDefinition definition : occurrenceTypes) {
            canonical.append("occurrence|").append(definition.id()).append('|')
                    .append(definition.displayName()).append('|');
            List<String> feeds = new ArrayList<>(definition.feedstockCommodityIds());
            Collections.sort(feeds);
            canonical.append(String.join(",", feeds)).append('\n');
        }
        for (LegacyItemMappingDefinition definition : legacyMappings) {
            canonical.append("legacy|").append(definition.legacyItemContentId()).append('|')
                    .append(definition.disposition().name()).append('|')
                    .append(definition.successorCommodityId() == null ? "-" : definition.successorCommodityId())
                    .append('|').append(definition.migrationNote()).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    private static <T> List<T> sortedCopy(List<T> source, Comparator<? super T> comparator) {
        List<T> copy = new ArrayList<>(Objects.requireNonNull(source, "source"));
        copy.sort(comparator);
        return List.copyOf(copy);
    }

    private static <T> Map<String, T> index(
            List<T> source, java.util.function.Function<T, String> idFunction) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T value : source) {
            result.put(idFunction.apply(value), value);
        }
        return Collections.unmodifiableMap(result);
    }

    /** Stage-18 material-chain role of a transportable commodity family. */
    public enum CommodityKind {
        /** Material recovered from a natural occurrence before refining. */
        EXTRACTED_FEEDSTOCK,
        /** Refined material used by fabrication or construction. */
        ENGINEERING_MATERIAL,
        /** Processed operating/industrial consumable that is physically stocked. */
        INDUSTRIAL_CONSUMABLE,
        /** Aggregated manufactured component family used by higher-level recipes. */
        COMPONENT_FAMILY
    }

    /** Physical quantity basis used by the ontology family. */
    public enum QuantityUnit {
        /** Aggregate bulk quantities are measured by mass. */
        KILOGRAM,
        /** Reserved for discrete future products that cannot be represented as bulk mass. */
        COUNT
    }

    /** Explicit migration disposition for an early-game legacy item. */
    public enum LegacyDisposition {
        /** The legacy item has a clear Stage-18 semantic successor, without applying quantity conversion yet. */
        SEMANTIC_SUCCESSOR,
        /** The item remains legacy until a later Stage-18 slice introduces a physically correct replacement. */
        RETAIN_LEGACY_UNTIL_PHYSICAL_REPLACEMENT
    }

    /**
     * @param id stable storage-class ID
     * @param displayName diagnostic/display name
     * @param legacyCategory current cargo-policy compatibility category
     */
    public record StorageClassDefinition(
            String id, String displayName, ItemCategory legacyCategory) {
        /**
         * Validates immutable storage-class metadata.
         *
         * @param id stable storage-class ID
         * @param displayName diagnostic/display name
         * @param legacyCategory current cargo-policy compatibility category
         */
        public StorageClassDefinition {
            requireText(id, "storage class id");
            requireText(displayName, "storage class displayName");
            Objects.requireNonNull(legacyCategory, "legacyCategory");
        }
    }

    /**
     * @param id stable process/capability tag ID
     * @param displayName diagnostic/display name
     */
    public record CapabilityTagDefinition(String id, String displayName) {
        /**
         * Validates immutable capability metadata.
         *
         * @param id stable process/capability tag ID
         * @param displayName diagnostic/display name
         */
        public CapabilityTagDefinition {
            requireText(id, "capability tag id");
            requireText(displayName, "capability tag displayName");
        }
    }

    /**
     * @param id stable commodity-family ID
     * @param codeName stable technical name
     * @param displayName diagnostic/display name
     * @param kind material-chain role
     * @param storageClassId stable storage-class reference
     * @param quantityUnit physical quantity basis
     */
    public record CommodityDefinition(
            String id,
            String codeName,
            String displayName,
            CommodityKind kind,
            String storageClassId,
            QuantityUnit quantityUnit) {
        /**
         * Validates immutable commodity metadata.
         *
         * @param id stable commodity-family ID
         * @param codeName stable technical name
         * @param displayName diagnostic/display name
         * @param kind material-chain role
         * @param storageClassId stable storage-class reference
         * @param quantityUnit physical quantity basis
         */
        public CommodityDefinition {
            requireText(id, "commodity id");
            requireText(codeName, "commodity codeName");
            requireText(displayName, "commodity displayName");
            Objects.requireNonNull(kind, "kind");
            requireText(storageClassId, "storageClassId");
            Objects.requireNonNull(quantityUnit, "quantityUnit");
        }
    }

    /**
     * Defines a type of natural occurrence without creating an actual reserve.
     *
     * @param id stable occurrence-type ID
     * @param displayName diagnostic/display name
     * @param feedstockCommodityIds extracted feedstock families potentially supplied by this occurrence type
     */
    public record ResourceOccurrenceTypeDefinition(
            String id, String displayName, List<String> feedstockCommodityIds) {
        /**
         * Validates immutable occurrence-type metadata.
         *
         * @param id stable occurrence-type ID
         * @param displayName diagnostic/display name
         * @param feedstockCommodityIds extracted feedstock families potentially supplied by this occurrence type
         */
        public ResourceOccurrenceTypeDefinition {
            requireText(id, "occurrence type id");
            requireText(displayName, "occurrence type displayName");
            feedstockCommodityIds = List.copyOf(
                    Objects.requireNonNull(feedstockCommodityIds, "feedstockCommodityIds"));
            if (feedstockCommodityIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "Occurrence type must reference at least one feedstock: " + id);
            }
        }
    }

    /**
     * @param legacyItemContentId persistent ID from the existing content catalog
     * @param disposition migration policy
     * @param successorCommodityId semantic successor, required only for {@link LegacyDisposition#SEMANTIC_SUCCESSOR}
     * @param migrationNote human-readable reason/boundary of the mapping
     */
    public record LegacyItemMappingDefinition(
            String legacyItemContentId,
            LegacyDisposition disposition,
            String successorCommodityId,
            String migrationNote) {
        /**
         * Validates immutable migration metadata.
         *
         * @param legacyItemContentId persistent ID from the existing content catalog
         * @param disposition migration policy
         * @param successorCommodityId semantic successor, required only for {@link LegacyDisposition#SEMANTIC_SUCCESSOR}
         * @param migrationNote human-readable reason/boundary of the mapping
         */
        public LegacyItemMappingDefinition {
            requireText(legacyItemContentId, "legacyItemContentId");
            Objects.requireNonNull(disposition, "disposition");
            requireText(migrationNote, "migrationNote");
            if (disposition == LegacyDisposition.SEMANTIC_SUCCESSOR) {
                requireText(successorCommodityId, "successorCommodityId");
            } else if (successorCommodityId != null) {
                throw new IllegalArgumentException(
                        "Retained legacy item cannot declare a successor: " + legacyItemContentId);
            }
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }
}
