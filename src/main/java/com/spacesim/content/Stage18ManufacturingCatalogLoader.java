package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.Stage18ManufacturingCatalog.ComponentRecipeDefinition;
import com.spacesim.content.Stage18ManufacturingCatalog.ManufacturingInputDefinition;
import com.spacesim.content.Stage18ManufacturingCatalog.ProductBindingDefinition;
import com.spacesim.content.Stage18ManufacturingCatalog.ProductProfileDefinition;
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

/** Loads and strictly validates the versioned Stage-18D manufacturing catalog. */
public final class Stage18ManufacturingCatalogLoader {
    /** Current supported manufacturing schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Default production manufacturing resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage18-manufacturing-v1.json";

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+");
    private static final Set<String> REQUIRED_COMPONENT_OUTPUTS = Set.of(
            "commodity.component.heavy_components",
            "commodity.component.electrical_components",
            "commodity.component.precision_components");
    private static final Set<String> REQUIRED_PROFILES = Set.of(
            "manufacturing.profile.reactor",
            "manufacturing.profile.drive",
            "manufacturing.profile.energy_storage",
            "manufacturing.profile.sensor",
            "manufacturing.profile.thermal_control",
            "manufacturing.profile.shield",
            "manufacturing.profile.datalink",
            "manufacturing.profile.kinetic_launcher",
            "manufacturing.profile.missile_launcher",
            "manufacturing.profile.beam_launcher",
            "manufacturing.profile.pd_launcher",
            "manufacturing.profile.kinetic_ammunition",
            "manufacturing.profile.guided_ammunition");

    private Stage18ManufacturingCatalogLoader() {
        throw new AssertionError("No instances");
    }

    /**
     * Loads the Stage-18D production manufacturing catalog against authoritative resource/product registries.
     *
     * @return immutable validated manufacturing catalog
     */
    public static Stage18ManufacturingCatalog loadDefault() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault();
        ClassLoader classLoader = Stage18ManufacturingCatalogLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-18 manufacturing catalog: " + DEFAULT_RESOURCE);
            }
            Stage18ManufacturingCatalog catalog = parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8), ontology, products);
            validateProductionBaseline(catalog, products);
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-18 manufacturing catalog", exception);
        }
    }

    /**
     * Parses one manufacturing document and validates all ontology/product references.
     *
     * @param json manufacturing JSON document
     * @param ontology authoritative Stage-18 resource ontology
     * @param products authoritative existing Stage-17.5 manufactured-product registry
     * @return immutable validated manufacturing catalog
     */
    public static Stage18ManufacturingCatalog parse(
            String json,
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products) {
        Objects.requireNonNull(json, "json");
        Stage18ResourceOntologyCatalog checkedOntology = Objects.requireNonNull(ontology, "ontology");
        Stage18ManufacturingProductRegistry checkedProducts = Objects.requireNonNull(products, "products");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Manufacturing JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Stage-18 manufacturing JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Manufacturing root must be an object");
        }
        int schemaVersion = requireInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-18 manufacturing schema: " + schemaVersion);
        }

        List<ComponentRecipeDefinition> componentRecipes = parseComponentRecipes(root, checkedOntology);
        List<ProductProfileDefinition> productProfiles = parseProductProfiles(root, checkedOntology);
        Set<String> profileIds = new HashSet<>();
        for (ProductProfileDefinition profile : productProfiles) {
            if (!profileIds.add(profile.id())) {
                throw new IllegalArgumentException("Duplicate product profile: " + profile.id());
            }
        }
        List<ProductBindingDefinition> bindings = parseBindings(root, checkedProducts, profileIds);
        return new Stage18ManufacturingCatalog(schemaVersion, componentRecipes, productProfiles, bindings);
    }

    private static List<ComponentRecipeDefinition> parseComponentRecipes(
            JsonValue root, Stage18ResourceOntologyCatalog ontology) {
        List<ComponentRecipeDefinition> recipes = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> outputs = new HashSet<>();
        for (JsonValue node = requireArray(root, "componentRecipes").child; node != null; node = node.next) {
            String id = requireId(node, "id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate component recipe: " + id);
            }
            String outputId = requireId(node, "outputCommodityId");
            CommodityDefinition output = ontology.findCommodity(outputId);
            if (output == null || output.kind() != CommodityKind.COMPONENT_FAMILY) {
                throw new IllegalArgumentException("Component recipe output must be COMPONENT_FAMILY: " + outputId);
            }
            if (!outputs.add(outputId)) {
                throw new IllegalArgumentException("Duplicate component output: " + outputId);
            }
            recipes.add(new ComponentRecipeDefinition(
                    id,
                    requireNonBlank(node, "displayName"),
                    parseInputs(node, ontology, false, id),
                    outputId,
                    parseCapabilities(node, ontology, id),
                    requireDouble(node, "energyJPerOutputKg"),
                    requireDouble(node, "workSecondsPerOutputKg"),
                    requireDouble(node, "maintenanceWorkSecondsPerOutputKg")));
        }
        if (recipes.isEmpty()) {
            throw new IllegalArgumentException("Manufacturing catalog requires component recipes");
        }
        return recipes;
    }

    private static List<ProductProfileDefinition> parseProductProfiles(
            JsonValue root, Stage18ResourceOntologyCatalog ontology) {
        List<ProductProfileDefinition> profiles = new ArrayList<>();
        for (JsonValue node = requireArray(root, "productProfiles").child; node != null; node = node.next) {
            String id = requireId(node, "id");
            profiles.add(new ProductProfileDefinition(
                    id,
                    requireNonBlank(node, "displayName"),
                    parseInputs(node, ontology, true, id),
                    parseCapabilities(node, ontology, id),
                    requireDouble(node, "energyJPerOutputKg"),
                    requireDouble(node, "workSecondsPerOutputKg"),
                    requireDouble(node, "maintenanceWorkSecondsPerOutputKg")));
        }
        if (profiles.isEmpty()) {
            throw new IllegalArgumentException("Manufacturing catalog requires product profiles");
        }
        return profiles;
    }

    private static List<ProductBindingDefinition> parseBindings(
            JsonValue root,
            Stage18ManufacturingProductRegistry products,
            Set<String> profileIds) {
        List<ProductBindingDefinition> bindings = new ArrayList<>();
        Set<String> productIds = new HashSet<>();
        for (JsonValue node = requireArray(root, "productBindings").child; node != null; node = node.next) {
            String productId = requireId(node, "productContentId");
            String profileId = requireId(node, "profileId");
            if (!productIds.add(productId)) {
                throw new IllegalArgumentException("Duplicate product binding: " + productId);
            }
            if (products.findProduct(productId) == null) {
                throw new IllegalArgumentException("Manufacturing binding references unknown product: " + productId);
            }
            if (!profileIds.contains(profileId)) {
                throw new IllegalArgumentException("Manufacturing binding references unknown profile: " + profileId);
            }
            bindings.add(new ProductBindingDefinition(productId, profileId));
        }
        if (bindings.isEmpty()) {
            throw new IllegalArgumentException("Manufacturing catalog requires product bindings");
        }
        return bindings;
    }

    private static List<ManufacturingInputDefinition> parseInputs(
            JsonValue node,
            Stage18ResourceOntologyCatalog ontology,
            boolean allowComponents,
            String subject) {
        List<ManufacturingInputDefinition> inputs = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue child = requireArray(node, "inputs").child; child != null; child = child.next) {
            String commodityId = requireId(child, "commodityId");
            if (!ids.add(commodityId)) {
                throw new IllegalArgumentException("Duplicate input " + commodityId + " for " + subject);
            }
            CommodityDefinition commodity = ontology.findCommodity(commodityId);
            if (commodity == null || !isManufacturingInputKind(commodity.kind(), allowComponents)) {
                throw new IllegalArgumentException("Invalid manufacturing input " + commodityId + " for " + subject);
            }
            inputs.add(new ManufacturingInputDefinition(
                    commodityId, requireDouble(child, "fractionOfOutputMass")));
        }
        return inputs;
    }

    private static boolean isManufacturingInputKind(CommodityKind kind, boolean allowComponents) {
        return kind == CommodityKind.ENGINEERING_MATERIAL
                || kind == CommodityKind.INDUSTRIAL_CONSUMABLE
                || (allowComponents && kind == CommodityKind.COMPONENT_FAMILY);
    }

    private static Set<String> parseCapabilities(
            JsonValue node, Stage18ResourceOntologyCatalog ontology, String subject) {
        Set<String> capabilities = new LinkedHashSet<>();
        for (JsonValue child = requireArray(node, "requiredCapabilityTags").child;
                child != null; child = child.next) {
            if (!child.isString()) {
                throw new IllegalArgumentException("requiredCapabilityTags must contain strings");
            }
            String capabilityId = child.asString();
            validateId(capabilityId, "requiredCapabilityTags");
            if (!capabilities.add(capabilityId)) {
                throw new IllegalArgumentException("Duplicate capability " + capabilityId + " for " + subject);
            }
            if (ontology.findCapabilityTag(capabilityId) == null) {
                throw new IllegalArgumentException("Unknown capability " + capabilityId + " for " + subject);
            }
        }
        return capabilities;
    }

    private static void validateProductionBaseline(
            Stage18ManufacturingCatalog catalog, Stage18ManufacturingProductRegistry products) {
        Set<String> missingComponents = new HashSet<>(REQUIRED_COMPONENT_OUTPUTS);
        for (ComponentRecipeDefinition recipe : catalog.getComponentRecipes()) {
            missingComponents.remove(recipe.outputCommodityId());
        }
        if (!missingComponents.isEmpty()) {
            throw new IllegalStateException("Production manufacturing catalog missing components: " + missingComponents);
        }

        Set<String> missingProfiles = new HashSet<>(REQUIRED_PROFILES);
        for (ProductProfileDefinition profile : catalog.getProductProfiles()) {
            missingProfiles.remove(profile.id());
        }
        if (!missingProfiles.isEmpty()) {
            throw new IllegalStateException("Production manufacturing catalog missing profiles: " + missingProfiles);
        }

        Set<String> expectedProducts = new HashSet<>();
        for (Stage18ManufacturingProductRegistry.ProductDefinition product : products.getProducts()) {
            expectedProducts.add(product.contentId());
        }
        Set<String> boundProducts = new HashSet<>();
        for (ProductBindingDefinition binding : catalog.getProductBindings()) {
            boundProducts.add(binding.productContentId());
        }
        if (!boundProducts.equals(expectedProducts)) {
            Set<String> missing = new HashSet<>(expectedProducts);
            missing.removeAll(boundProducts);
            Set<String> extra = new HashSet<>(boundProducts);
            extra.removeAll(expectedProducts);
            throw new IllegalStateException(
                    "Manufacturing bindings must exactly cover existing products; missing=" + missing + ", extra=" + extra);
        }
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
