package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.Stage18ResourceOntologyCatalog.QuantityUnit;
import com.spacesim.content.Stage18ShipConsumableCatalog.ShipConsumableBinding;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Loads and validates Stage-18I ship-consumable servicing bindings. */
public final class Stage18ShipConsumableCatalogLoader {
    /** Current supported servicing-binding schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Production Stage-18I ship-consumable binding resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage18-ship-consumables-v1.json";

    private Stage18ShipConsumableCatalogLoader() {
        throw new AssertionError("No instances");
    }

    /**
     * Loads production bindings against the authoritative Stage-18 ontology and Stage-17.5 interfaces.
     *
     * @return immutable validated servicing catalog
     */
    public static Stage18ShipConsumableCatalog loadDefault() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        ClassLoader loader = Stage18ShipConsumableCatalogLoader.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-18I ship-consumable catalog: " + DEFAULT_RESOURCE);
            }
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8), ontology, engineering);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-18I ship-consumable catalog", exception);
        }
    }

    /**
     * Parses one ship-consumable binding document.
     *
     * @param json JSON document
     * @param ontology authoritative Stage-18 resource ontology
     * @param engineering authoritative Stage-17.5 engineering catalog
     * @return immutable validated servicing catalog
     */
    public static Stage18ShipConsumableCatalog parse(
            String json,
            Stage18ResourceOntologyCatalog ontology,
            ShipEngineeringCatalog engineering) {
        Objects.requireNonNull(json, "json");
        Stage18ResourceOntologyCatalog checkedOntology = Objects.requireNonNull(ontology, "ontology");
        ShipEngineeringCatalog checkedEngineering = Objects.requireNonNull(engineering, "engineering");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Stage-18I ship-consumable JSON must not be blank");
        }
        JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed Stage-18I ship-consumable JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Stage-18I ship-consumable root must be an object");
        }
        int schema = requiredInt(root, "schemaVersion");
        if (schema != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-18I ship-consumable schema: " + schema);
        }
        JsonValue array = root.get("bindings");
        if (array == null || !array.isArray() || array.size == 0) {
            throw new IllegalArgumentException("bindings must be a non-empty array");
        }
        List<ShipConsumableBinding> bindings = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<String> addresses = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            if (!node.isObject()) {
                throw new IllegalArgumentException("ship-consumable binding must be an object");
            }
            String id = requiredString(node, "id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate ship-consumable binding: " + id);
            }
            String moduleId = requiredString(node, "moduleId");
            ModuleDefinition module = checkedEngineering.findModule(moduleId);
            if (module == null) {
                throw new IllegalArgumentException("Unknown ship-consumable module: " + moduleId);
            }
            String interfaceId = requiredString(node, "interfaceId");
            InterfaceKind kind;
            try {
                kind = InterfaceKind.valueOf(requiredString(node, "interfaceKind"));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown ship-consumable interfaceKind", exception);
            }
            InterfaceDefinition physicalInterface = module.interfaces().stream()
                    .filter(value -> value.id().equals(interfaceId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown module interface: " + moduleId + " -> " + interfaceId));
            if (physicalInterface.kind() != kind) {
                throw new IllegalArgumentException("Ship-consumable interface kind mismatch: " + id);
            }
            String address = moduleId + "|" + interfaceId;
            if (!addresses.add(address)) {
                throw new IllegalArgumentException("Duplicate ship-consumable interface binding: " + address);
            }
            String commodityId = requiredString(node, "commodityId");
            Stage18ResourceOntologyCatalog.CommodityDefinition commodity = checkedOntology.findCommodity(commodityId);
            if (commodity == null || commodity.quantityUnit() != QuantityUnit.KILOGRAM) {
                throw new IllegalArgumentException("Unknown/non-mass ship-consumable commodity: " + commodityId);
            }
            if (commodity.kind() == Stage18ResourceOntologyCatalog.CommodityKind.EXTRACTED_FEEDSTOCK) {
                throw new IllegalArgumentException("Ship servicing cannot load raw feedstock directly: " + commodityId);
            }
            bindings.add(new ShipConsumableBinding(
                    id, moduleId, interfaceId, kind, commodityId, positive(node, "amountPerKg")));
        }
        return new Stage18ShipConsumableCatalog(schema, bindings);
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
        if (value == null || !value.isNumber() || value.asDouble() != value.asInt()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return value.asInt();
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
