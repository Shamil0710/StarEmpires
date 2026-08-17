package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.model.ItemCategory;
import com.spacesim.model.ItemType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Loads and strictly validates the versioned Stage-18A resource ontology. */
public final class Stage18ResourceOntologyLoader {
    /** Current supported ontology schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Default production ontology resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage18-resource-ontology-v1.json";

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+");
    private static final Set<String> REQUIRED_FEEDSTOCKS = Set.of(
            "commodity.feedstock.water_ice",
            "commodity.feedstock.volatile_feedstock",
            "commodity.feedstock.carbonaceous_feedstock",
            "commodity.feedstock.metallic_ore",
            "commodity.feedstock.light_metal_minerals",
            "commodity.feedstock.conductor_ore",
            "commodity.feedstock.strategic_metal_ore",
            "commodity.feedstock.silicate_minerals",
            "commodity.feedstock.fissile_minerals");
    private static final Set<String> REQUIRED_COMPONENTS = Set.of(
            "commodity.component.heavy_components",
            "commodity.component.electrical_components",
            "commodity.component.precision_components");
    private static final Set<String> REQUIRED_MATERIALS_AND_CONSUMABLES = Set.of(
            "commodity.material.purified_water",
            "commodity.material.industrial_gases",
            "commodity.material.industrial_chemicals",
            "commodity.material.structural_alloy",
            "commodity.material.light_alloy",
            "commodity.material.conductor_metal",
            "commodity.material.refractory_alloy",
            "commodity.material.ceramic_glass",
            "commodity.material.carbon_material",
            "commodity.material.electronic_grade_material",
            "commodity.consumable.reactor_fuel");

    private Stage18ResourceOntologyLoader() {
        throw new AssertionError("No instances");
    }

    /**
     * Loads the built-in Stage-18A production ontology and validates its legacy bridge.
     *
     * @return immutable production ontology
     */
    public static Stage18ResourceOntologyCatalog loadDefault() {
        ClassLoader classLoader = Stage18ResourceOntologyLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-18 resource ontology: " + DEFAULT_RESOURCE);
            }
            Stage18ResourceOntologyCatalog catalog = parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            validateProductionBaseline(catalog, ContentCatalogLoader.loadDefault());
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-18 resource ontology", exception);
        }
    }

    /**
     * Parses a standalone ontology document.
     *
     * @param json non-empty JSON document
     * @return immutable validated ontology
     */
    public static Stage18ResourceOntologyCatalog parse(String json) {
        Objects.requireNonNull(json, "json");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Ontology JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Stage-18 ontology JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Ontology root must be an object");
        }
        int schemaVersion = requireInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-18 ontology schema: " + schemaVersion);
        }

        List<Stage18ResourceOntologyCatalog.StorageClassDefinition> storageClasses = new ArrayList<>();
        Map<String, Stage18ResourceOntologyCatalog.StorageClassDefinition> storageById = new LinkedHashMap<>();
        for (JsonValue node = requireArray(root, "storageClasses").child; node != null; node = node.next) {
            String id = requireId(node, "id");
            ItemCategory legacyCategory = enumValue(
                    ItemCategory.class, requireString(node, "legacyCategory"), "storage legacyCategory", id);
            Stage18ResourceOntologyCatalog.StorageClassDefinition definition =
                    new Stage18ResourceOntologyCatalog.StorageClassDefinition(
                            id, requireNonBlank(node, "displayName"), legacyCategory);
            putUnique(storageById, id, definition, "storage class");
            storageClasses.add(definition);
        }
        if (storageClasses.isEmpty()) {
            throw new IllegalArgumentException("Ontology must contain storage classes");
        }

        List<Stage18ResourceOntologyCatalog.CapabilityTagDefinition> capabilityTags = new ArrayList<>();
        Map<String, Stage18ResourceOntologyCatalog.CapabilityTagDefinition> capabilitiesById = new LinkedHashMap<>();
        for (JsonValue node = requireArray(root, "capabilityTags").child; node != null; node = node.next) {
            String id = requireId(node, "id");
            Stage18ResourceOntologyCatalog.CapabilityTagDefinition definition =
                    new Stage18ResourceOntologyCatalog.CapabilityTagDefinition(id, requireNonBlank(node, "displayName"));
            putUnique(capabilitiesById, id, definition, "capability tag");
            capabilityTags.add(definition);
        }
        if (capabilityTags.isEmpty()) {
            throw new IllegalArgumentException("Ontology must contain capability tags");
        }

        List<Stage18ResourceOntologyCatalog.CommodityDefinition> commodities = new ArrayList<>();
        Map<String, Stage18ResourceOntologyCatalog.CommodityDefinition> commoditiesById = new LinkedHashMap<>();
        for (JsonValue node = requireArray(root, "commodities").child; node != null; node = node.next) {
            String id = requireId(node, "id");
            Stage18ResourceOntologyCatalog.CommodityKind kind = enumValue(
                    Stage18ResourceOntologyCatalog.CommodityKind.class,
                    requireString(node, "kind"), "commodity kind", id);
            Stage18ResourceOntologyCatalog.QuantityUnit quantityUnit = enumValue(
                    Stage18ResourceOntologyCatalog.QuantityUnit.class,
                    requireString(node, "quantityUnit"), "quantity unit", id);
            String storageClassId = requireId(node, "storageClassId");
            if (!storageById.containsKey(storageClassId)) {
                throw new IllegalArgumentException("Unknown storage class " + storageClassId + " for " + id);
            }
            Stage18ResourceOntologyCatalog.CommodityDefinition definition =
                    new Stage18ResourceOntologyCatalog.CommodityDefinition(
                            id,
                            requireNonBlank(node, "codeName"),
                            requireNonBlank(node, "displayName"),
                            kind,
                            storageClassId,
                            quantityUnit);
            putUnique(commoditiesById, id, definition, "commodity");
            commodities.add(definition);
        }
        if (commodities.isEmpty()) {
            throw new IllegalArgumentException("Ontology must contain commodities");
        }

        List<Stage18ResourceOntologyCatalog.ResourceOccurrenceTypeDefinition> occurrenceTypes = new ArrayList<>();
        Map<String, Stage18ResourceOntologyCatalog.ResourceOccurrenceTypeDefinition> occurrencesById = new LinkedHashMap<>();
        for (JsonValue node = requireArray(root, "occurrenceTypes").child; node != null; node = node.next) {
            String id = requireId(node, "id");
            List<String> feedstockIds = parseIdArray(node, "feedstockCommodityIds");
            Set<String> uniqueFeedstocks = new HashSet<>();
            for (String feedstockId : feedstockIds) {
                Stage18ResourceOntologyCatalog.CommodityDefinition commodity = commoditiesById.get(feedstockId);
                if (commodity == null) {
                    throw new IllegalArgumentException("Unknown feedstock " + feedstockId + " for " + id);
                }
                if (commodity.kind() != Stage18ResourceOntologyCatalog.CommodityKind.EXTRACTED_FEEDSTOCK) {
                    throw new IllegalArgumentException("Occurrence may reference only extracted feedstocks: " + feedstockId);
                }
                if (!uniqueFeedstocks.add(feedstockId)) {
                    throw new IllegalArgumentException("Duplicate feedstock reference " + feedstockId + " for " + id);
                }
            }
            Stage18ResourceOntologyCatalog.ResourceOccurrenceTypeDefinition definition =
                    new Stage18ResourceOntologyCatalog.ResourceOccurrenceTypeDefinition(
                            id, requireNonBlank(node, "displayName"), feedstockIds);
            putUnique(occurrencesById, id, definition, "occurrence type");
            occurrenceTypes.add(definition);
        }
        if (occurrenceTypes.isEmpty()) {
            throw new IllegalArgumentException("Ontology must contain occurrence types");
        }

        List<Stage18ResourceOntologyCatalog.LegacyItemMappingDefinition> legacyMappings = new ArrayList<>();
        Map<String, Stage18ResourceOntologyCatalog.LegacyItemMappingDefinition> legacyById = new LinkedHashMap<>();
        for (JsonValue node = requireArray(root, "legacyMappings").child; node != null; node = node.next) {
            String legacyItemContentId = requireId(node, "legacyItemContentId");
            Stage18ResourceOntologyCatalog.LegacyDisposition disposition = enumValue(
                    Stage18ResourceOntologyCatalog.LegacyDisposition.class,
                    requireString(node, "disposition"), "legacy disposition", legacyItemContentId);
            String successor = optionalId(node, "successorCommodityId");
            if (successor != null && !commoditiesById.containsKey(successor)) {
                throw new IllegalArgumentException("Unknown successor commodity " + successor + " for " + legacyItemContentId);
            }
            Stage18ResourceOntologyCatalog.LegacyItemMappingDefinition definition =
                    new Stage18ResourceOntologyCatalog.LegacyItemMappingDefinition(
                            legacyItemContentId, disposition, successor, requireNonBlank(node, "migrationNote"));
            putUnique(legacyById, legacyItemContentId, definition, "legacy mapping");
            legacyMappings.add(definition);
        }

        return new Stage18ResourceOntologyCatalog(
                schemaVersion, storageClasses, capabilityTags, commodities, occurrenceTypes, legacyMappings);
    }

    private static void validateProductionBaseline(
            Stage18ResourceOntologyCatalog ontology, ContentCatalog legacyCatalog) {
        requireCommoditySet(ontology, REQUIRED_FEEDSTOCKS, Stage18ResourceOntologyCatalog.CommodityKind.EXTRACTED_FEEDSTOCK);
        requireCommoditySet(ontology, REQUIRED_COMPONENTS, Stage18ResourceOntologyCatalog.CommodityKind.COMPONENT_FAMILY);
        for (String id : REQUIRED_MATERIALS_AND_CONSUMABLES) {
            if (ontology.findCommodity(id) == null) {
                throw new IllegalStateException("Production Stage-18 ontology missing commodity: " + id);
            }
        }
        for (String feedstockId : REQUIRED_FEEDSTOCKS) {
            boolean represented = ontology.getOccurrenceTypes().stream()
                    .anyMatch(type -> type.feedstockCommodityIds().contains(feedstockId));
            if (!represented) {
                throw new IllegalStateException("No occurrence type exposes required feedstock: " + feedstockId);
            }
        }
        for (ItemType legacy : ItemType.values()) {
            ContentCatalog.ItemDefinition item = legacyCatalog.findItem(legacy.getId());
            if (item == null) {
                throw new IllegalStateException("Default content catalog lost legacy runtime item: " + legacy.name());
            }
            Stage18ResourceOntologyCatalog.LegacyItemMappingDefinition mapping =
                    ontology.findLegacyMapping(item.id());
            if (mapping == null) {
                throw new IllegalStateException("Stage-18 ontology has no migration disposition for " + item.id());
            }
        }
    }

    private static void requireCommoditySet(
            Stage18ResourceOntologyCatalog ontology,
            Set<String> ids,
            Stage18ResourceOntologyCatalog.CommodityKind expectedKind) {
        for (String id : ids) {
            Stage18ResourceOntologyCatalog.CommodityDefinition definition = ontology.findCommodity(id);
            if (definition == null || definition.kind() != expectedKind) {
                throw new IllegalStateException("Missing or misclassified production commodity: " + id);
            }
        }
    }

    private static List<String> parseIdArray(JsonValue node, String field) {
        JsonValue array = requireArray(node, field);
        List<String> result = new ArrayList<>();
        for (JsonValue child = array.child; child != null; child = child.next) {
            if (!child.isString()) {
                throw new IllegalArgumentException(field + " must contain strings");
            }
            String id = child.asString();
            validateId(id, field);
            result.add(id);
        }
        return result;
    }

    private static JsonValue requireArray(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value;
    }

    private static int requireInt(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return value.asInt();
    }

    private static String requireString(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.asString();
    }

    private static String requireNonBlank(JsonValue node, String field) {
        String value = requireString(node, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }

    private static String requireId(JsonValue node, String field) {
        String value = requireString(node, field);
        validateId(value, field);
        return value;
    }

    private static String optionalId(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw new IllegalArgumentException(field + " must be a string or null");
        }
        String id = value.asString();
        validateId(id, field);
        return id;
    }

    private static void validateId(String id, String label) {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid " + label + ": " + id);
        }
    }

    private static <E extends Enum<E>> E enumValue(
            Class<E> type, String raw, String label, String ownerId) {
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + label + " for " + ownerId + ": " + raw, exception);
        }
    }

    private static <T> void putUnique(Map<String, T> map, String id, T value, String label) {
        if (map.putIfAbsent(id, value) != null) {
            throw new IllegalArgumentException("Duplicate " + label + " ID: " + id);
        }
    }
}
