package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.Stage18RefiningCatalog.RecipeInputDefinition;
import com.spacesim.content.Stage18RefiningCatalog.RefiningRecipeDefinition;
import com.spacesim.content.Stage18ResourceOntologyCatalog.CommodityDefinition;
import com.spacesim.content.Stage18ResourceOntologyCatalog.CommodityKind;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Loads and strictly validates the versioned Stage-18C refining recipe catalog. */
public final class Stage18RefiningCatalogLoader {
    /** Current supported Stage-18C refining schema version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Default production Stage-18C refining recipe resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage18-refining-v1.json";

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+");
    private static final Set<String> REQUIRED_OUTPUTS = Set.of(
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

    private Stage18RefiningCatalogLoader() {
        throw new AssertionError("No instances");
    }

    /**
     * Loads the production Stage-18C refining catalog against the production resource ontology.
     *
     * @return immutable validated production refining catalog
     */
    public static Stage18RefiningCatalog loadDefault() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        ClassLoader classLoader = Stage18RefiningCatalogLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-18 refining catalog: " + DEFAULT_RESOURCE);
            }
            Stage18RefiningCatalog catalog = parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8), ontology);
            validateProductionBaseline(catalog);
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-18 refining catalog", exception);
        }
    }

    /**
     * Parses a refining document and validates all commodity and capability references.
     *
     * @param json non-empty refining JSON document
     * @param ontology Stage-18 resource ontology referenced by the document
     * @return immutable validated refining catalog
     */
    public static Stage18RefiningCatalog parse(String json, Stage18ResourceOntologyCatalog ontology) {
        Objects.requireNonNull(json, "json");
        Stage18ResourceOntologyCatalog checkedOntology = Objects.requireNonNull(ontology, "ontology");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Refining JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Stage-18 refining JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Refining root must be an object");
        }
        int schemaVersion = requireInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-18 refining schema: " + schemaVersion);
        }

        JsonValue recipesNode = requireArray(root, "recipes");
        List<RefiningRecipeDefinition> recipes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = recipesNode.child; node != null; node = node.next) {
            String id = requireId(node, "id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate refining recipe: " + id);
            }
            List<RecipeInputDefinition> inputs = parseInputs(node, checkedOntology, id);
            String outputCommodityId = requireId(node, "outputCommodityId");
            CommodityDefinition output = checkedOntology.findCommodity(outputCommodityId);
            if (output == null || (output.kind() != CommodityKind.ENGINEERING_MATERIAL
                    && output.kind() != CommodityKind.INDUSTRIAL_CONSUMABLE)) {
                throw new IllegalArgumentException("Refining output must be a material/consumable: " + outputCommodityId);
            }
            Set<String> capabilities = parseIdSet(node, "requiredCapabilityTags");
            for (String capability : capabilities) {
                if (checkedOntology.findCapabilityTag(capability) == null) {
                    throw new IllegalArgumentException("Unknown capability tag " + capability + " for " + id);
                }
            }
            recipes.add(new RefiningRecipeDefinition(
                    id,
                    requireNonBlank(node, "displayName"),
                    inputs,
                    outputCommodityId,
                    requireDouble(node, "outputMassFraction"),
                    requireDouble(node, "discardedMassFraction"),
                    capabilities,
                    requireDouble(node, "energyJPerInputKg"),
                    requireDouble(node, "workSecondsPerInputKg"),
                    requireDouble(node, "maintenanceWorkSecondsPerInputKg")));
        }
        if (recipes.isEmpty()) {
            throw new IllegalArgumentException("Refining catalog must contain recipes");
        }
        return new Stage18RefiningCatalog(schemaVersion, recipes);
    }

    private static List<RecipeInputDefinition> parseInputs(
            JsonValue node, Stage18ResourceOntologyCatalog ontology, String recipeId) {
        JsonValue array = requireArray(node, "inputs");
        List<RecipeInputDefinition> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue child = array.child; child != null; child = child.next) {
            if (!child.isObject()) {
                throw new IllegalArgumentException("inputs must contain objects");
            }
            String commodityId = requireId(child, "commodityId");
            if (!ids.add(commodityId)) {
                throw new IllegalArgumentException("Duplicate input " + commodityId + " for " + recipeId);
            }
            CommodityDefinition commodity = ontology.findCommodity(commodityId);
            if (commodity == null || commodity.kind() != CommodityKind.EXTRACTED_FEEDSTOCK) {
                throw new IllegalArgumentException("Refining input must be extracted feedstock: " + commodityId);
            }
            result.add(new RecipeInputDefinition(commodityId, requireDouble(child, "fractionOfInputMass")));
        }
        return result;
    }

    private static void validateProductionBaseline(Stage18RefiningCatalog catalog) {
        Set<String> missing = new HashSet<>(REQUIRED_OUTPUTS);
        for (RefiningRecipeDefinition recipe : catalog.getRecipes()) {
            missing.remove(recipe.outputCommodityId());
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Production refining catalog missing outputs: " + missing);
        }
    }

    private static Set<String> parseIdSet(JsonValue node, String field) {
        JsonValue array = requireArray(node, field);
        Set<String> result = new LinkedHashSet<>();
        for (JsonValue child = array.child; child != null; child = child.next) {
            if (!child.isString()) {
                throw new IllegalArgumentException(field + " must contain strings");
            }
            String id = child.asString();
            validateId(id, field);
            if (!result.add(id)) {
                throw new IllegalArgumentException("Duplicate " + field + " entry: " + id);
            }
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
        double number = value.asDouble();
        int integer = value.asInt();
        if (!Double.isFinite(number) || number != integer) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return integer;
    }

    private static double requireDouble(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        return value.asDouble();
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

    private static void validateId(String value, String field) {
        if (value == null || !ID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " has invalid stable ID: " + value);
        }
    }
}
