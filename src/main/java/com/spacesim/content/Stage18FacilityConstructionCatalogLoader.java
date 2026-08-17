package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.Stage18FacilityConstructionCatalog.ConstructionInputDefinition;
import com.spacesim.content.Stage18FacilityConstructionCatalog.ConstructionProfileDefinition;
import com.spacesim.content.Stage18FacilityConstructionCatalog.FacilityConstructionDefinition;
import com.spacesim.content.Stage18ResourceOntologyCatalog.CommodityKind;
import com.spacesim.content.Stage18ResourceOntologyCatalog.QuantityUnit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Loads and validates Stage-18H physical facility-construction content. */
public final class Stage18FacilityConstructionCatalogLoader {
    /** Current Stage-18H construction schema version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Default production facility-construction resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage18-facility-construction-v1.json";

    private Stage18FacilityConstructionCatalogLoader() {
        throw new AssertionError("No instances");
    }

    /**
     * Loads the production Stage-18H facility-construction catalog.
     *
     * @return immutable validated catalog covering every Stage-18E facility definition
     */
    public static Stage18FacilityConstructionCatalog loadDefault() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18FacilityCatalog facilities = Stage18FacilityCatalogLoader.loadDefault();
        ClassLoader loader = Stage18FacilityConstructionCatalogLoader.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-18H construction resource: " + DEFAULT_RESOURCE);
            }
            Stage18FacilityConstructionCatalog catalog = parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8), ontology, facilities);
            validateCoverage(catalog, facilities);
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-18H construction resource", exception);
        }
    }

    /**
     * Parses one Stage-18H facility-construction document.
     *
     * @param json JSON document
     * @param ontology authoritative Stage-18 resource ontology
     * @param facilities authoritative Stage-18E facility definitions
     * @return immutable validated construction catalog
     */
    public static Stage18FacilityConstructionCatalog parse(
            String json,
            Stage18ResourceOntologyCatalog ontology,
            Stage18FacilityCatalog facilities) {
        Objects.requireNonNull(json, "json");
        Stage18ResourceOntologyCatalog checkedOntology = Objects.requireNonNull(ontology, "ontology");
        Stage18FacilityCatalog checkedFacilities = Objects.requireNonNull(facilities, "facilities");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Stage-18H construction JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed Stage-18H construction JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Stage-18H construction root must be an object");
        }
        int schemaVersion = requiredInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-18H construction schema: " + schemaVersion);
        }
        List<ConstructionProfileDefinition> profiles = parseProfiles(root, checkedOntology);
        Set<String> profileIds = new HashSet<>();
        profiles.forEach(value -> profileIds.add(value.id()));
        List<FacilityConstructionDefinition> bindings = parseFacilities(root, checkedFacilities, profileIds);
        if (profiles.isEmpty() || bindings.isEmpty()) {
            throw new IllegalArgumentException("Stage-18H construction catalog requires profiles and facilities");
        }
        return new Stage18FacilityConstructionCatalog(schemaVersion, profiles, bindings);
    }

    private static List<ConstructionProfileDefinition> parseProfiles(
            JsonValue root,
            Stage18ResourceOntologyCatalog ontology) {
        List<ConstructionProfileDefinition> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = requiredArray(root, "profiles").child; node != null; node = node.next) {
            requireObject(node, "construction profile");
            String id = requiredString(node, "id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate construction profile: " + id);
            }
            List<ConstructionInputDefinition> inputs = new ArrayList<>();
            Set<String> commodityIds = new HashSet<>();
            for (JsonValue input = requiredArray(node, "inputs").child; input != null; input = input.next) {
                requireObject(input, "construction input");
                String commodityId = requiredString(input, "commodityId");
                Stage18ResourceOntologyCatalog.CommodityDefinition commodity = ontology.findCommodity(commodityId);
                if (commodity == null || commodity.quantityUnit() != QuantityUnit.KILOGRAM) {
                    throw new IllegalArgumentException("Unknown/non-mass construction commodity: " + commodityId);
                }
                if (commodity.kind() == CommodityKind.EXTRACTED_FEEDSTOCK) {
                    throw new IllegalArgumentException("Facility construction cannot consume raw feedstock: " + commodityId);
                }
                if (!commodityIds.add(commodityId)) {
                    throw new IllegalArgumentException("Duplicate construction input: " + commodityId);
                }
                inputs.add(new ConstructionInputDefinition(
                        commodityId,
                        positive(input, "fractionOfInstalledMass")));
            }
            Set<String> capabilities = stringSet(node, "requiredCapabilityTags");
            for (String capability : capabilities) {
                if (ontology.findCapabilityTag(capability) == null) {
                    throw new IllegalArgumentException("Unknown construction capability: " + capability);
                }
            }
            result.add(new ConstructionProfileDefinition(
                    id,
                    requiredString(node, "displayName"),
                    inputs,
                    capabilities,
                    positive(node, "workSecondsPerInstalledKg")));
        }
        return result;
    }

    private static List<FacilityConstructionDefinition> parseFacilities(
            JsonValue root,
            Stage18FacilityCatalog facilities,
            Set<String> profileIds) {
        List<FacilityConstructionDefinition> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = requiredArray(root, "facilities").child; node != null; node = node.next) {
            requireObject(node, "facility construction binding");
            String facilityId = requiredString(node, "facilityDefinitionId");
            if (facilities.findFacility(facilityId) == null) {
                throw new IllegalArgumentException("Unknown Stage-18E facility construction target: " + facilityId);
            }
            if (!ids.add(facilityId)) {
                throw new IllegalArgumentException("Duplicate facility construction binding: " + facilityId);
            }
            String profileId = requiredString(node, "profileId");
            if (!profileIds.contains(profileId)) {
                throw new IllegalArgumentException("Unknown construction profile binding: " + profileId);
            }
            result.add(new FacilityConstructionDefinition(
                    facilityId,
                    profileId,
                    positive(node, "installedMassKg")));
        }
        return result;
    }

    private static void validateCoverage(
            Stage18FacilityConstructionCatalog catalog,
            Stage18FacilityCatalog facilities) {
        for (Stage18FacilityCatalog.FacilityDefinition facility : facilities.getFacilities()) {
            if (catalog.findFacility(facility.id()) == null) {
                throw new IllegalStateException("Missing Stage-18H construction binding: " + facility.id());
            }
        }
        if (catalog.getFacilities().size() != facilities.getFacilities().size()) {
            throw new IllegalStateException("Stage-18H construction coverage differs from Stage-18E facility set");
        }
    }

    private static Set<String> stringSet(JsonValue parent, String field) {
        JsonValue array = requiredArray(parent, field);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (JsonValue value = array.child; value != null; value = value.next) {
            if (!value.isString() || value.asString().isBlank()) {
                throw new IllegalArgumentException(field + " must contain non-blank strings");
            }
            if (!result.add(value.asString())) {
                throw new IllegalArgumentException("Duplicate " + field + " entry: " + value.asString());
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return result;
    }

    private static JsonValue requiredArray(JsonValue parent, String field) {
        JsonValue value = parent.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value;
    }

    private static void requireObject(JsonValue value, String label) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
    }

    private static String requiredString(JsonValue parent, String field) {
        JsonValue value = parent.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank text");
        }
        return value.asString();
    }

    private static int requiredInt(JsonValue parent, String field) {
        JsonValue value = parent.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        int result = value.asInt();
        if (value.asDouble() != result) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return result;
    }

    private static double positive(JsonValue parent, String field) {
        JsonValue value = parent.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(field + " must be numeric");
        }
        double result = value.asDouble();
        if (!Double.isFinite(result) || result <= 0d) {
            throw new IllegalArgumentException(field + " must be finite and positive");
        }
        return result;
    }
}
