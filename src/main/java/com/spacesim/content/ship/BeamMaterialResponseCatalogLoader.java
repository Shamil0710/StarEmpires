package com.spacesim.content.ship;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.ship.BeamMaterialResponseCatalog.MaterialResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Strict bounded loader for production beam/material response content. */
public final class BeamMaterialResponseCatalogLoader {
    /** Current supported schema version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final int MAX_RESPONSES = 512;

    private BeamMaterialResponseCatalogLoader() {
        throw new AssertionError("utility namespace");
    }

    /**
     * Parses beam response content and validates every material reference against engineering content.
     *
     * @param json JSON document
     * @param engineering ordinary production engineering catalog
     * @return immutable response catalog
     */
    public static BeamMaterialResponseCatalog parse(String json, ShipEngineeringCatalog engineering) {
        String source = Objects.requireNonNull(json, "json");
        ShipEngineeringCatalog checkedEngineering = Objects.requireNonNull(engineering, "engineering");
        JsonValue root;
        try {
            root = new JsonReader().parse(source);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed beam material response JSON", exception);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Beam material response root must be an object");
        }
        int schemaVersion = root.getInt("schemaVersion", -1);
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported beam material response schema: " + schemaVersion);
        }
        JsonValue array = root.get("responses");
        if (array == null || !array.isArray() || array.size < 1 || array.size > MAX_RESPONSES) {
            throw new IllegalArgumentException("responses must be a non-empty bounded array");
        }
        List<MaterialResponse> values = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            if (!node.isObject()) {
                throw new IllegalArgumentException("beam response row must be an object");
            }
            String materialId = node.getString("materialId", null);
            if (materialId == null || materialId.isBlank()) {
                throw new IllegalArgumentException("beam response materialId must be non-blank");
            }
            if (checkedEngineering.findMaterial(materialId) == null) {
                throw new IllegalArgumentException("beam response references unknown material: " + materialId);
            }
            if (!ids.add(materialId)) {
                throw new IllegalArgumentException("duplicate beam response material: " + materialId);
            }
            values.add(new MaterialResponse(
                    materialId,
                    node.getDouble("absorptionFraction", Double.NaN),
                    node.getDouble("ablationSpecificEnergyJPerKg", Double.NaN),
                    node.getDouble("internalResidualCouplingFraction", Double.NaN)));
        }
        return new BeamMaterialResponseCatalog(schemaVersion, values);
    }
}
