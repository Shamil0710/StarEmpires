package com.spacesim.content;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Deterministic JSON serializer for the Stage-18A resource ontology. */
public final class Stage18ResourceOntologySerializer {
    private Stage18ResourceOntologySerializer() {
        throw new AssertionError("No instances");
    }

    /**
     * Serializes an ontology into a canonical compact JSON representation.
     *
     * <p>Independent definitions and occurrence feedstock references are ordered by stable ID, so
     * parse/serialize round-trips are deterministic and suitable for persistence diagnostics.</p>
     *
     * @param catalog ontology to serialize
     * @return canonical JSON document
     */
    public static String serialize(Stage18ResourceOntologyCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");
        StringBuilder out = new StringBuilder(8192);
        out.append('{').append("\"schemaVersion\":").append(catalog.getSchemaVersion());

        out.append(",\"storageClasses\":[");
        boolean first = true;
        for (Stage18ResourceOntologyCatalog.StorageClassDefinition definition : catalog.getStorageClasses()) {
            first = separator(out, first);
            out.append('{');
            field(out, "id", definition.id()).append(',');
            field(out, "displayName", definition.displayName()).append(',');
            field(out, "legacyCategory", definition.legacyCategory().name());
            out.append('}');
        }
        out.append(']');

        out.append(",\"capabilityTags\":[");
        first = true;
        for (Stage18ResourceOntologyCatalog.CapabilityTagDefinition definition : catalog.getCapabilityTags()) {
            first = separator(out, first);
            out.append('{');
            field(out, "id", definition.id()).append(',');
            field(out, "displayName", definition.displayName());
            out.append('}');
        }
        out.append(']');

        out.append(",\"commodities\":[");
        first = true;
        for (Stage18ResourceOntologyCatalog.CommodityDefinition definition : catalog.getCommodities()) {
            first = separator(out, first);
            out.append('{');
            field(out, "id", definition.id()).append(',');
            field(out, "codeName", definition.codeName()).append(',');
            field(out, "displayName", definition.displayName()).append(',');
            field(out, "kind", definition.kind().name()).append(',');
            field(out, "storageClassId", definition.storageClassId()).append(',');
            field(out, "quantityUnit", definition.quantityUnit().name());
            out.append('}');
        }
        out.append(']');

        out.append(",\"occurrenceTypes\":[");
        first = true;
        for (Stage18ResourceOntologyCatalog.ResourceOccurrenceTypeDefinition definition : catalog.getOccurrenceTypes()) {
            first = separator(out, first);
            out.append('{');
            field(out, "id", definition.id()).append(',');
            field(out, "displayName", definition.displayName()).append(',');
            out.append("\"feedstockCommodityIds\":[");
            List<String> feeds = new ArrayList<>(definition.feedstockCommodityIds());
            feeds.sort(Comparator.naturalOrder());
            boolean firstFeed = true;
            for (String feed : feeds) {
                firstFeed = separator(out, firstFeed);
                string(out, feed);
            }
            out.append("]}");
        }
        out.append(']');

        out.append(",\"legacyMappings\":[");
        first = true;
        for (Stage18ResourceOntologyCatalog.LegacyItemMappingDefinition definition : catalog.getLegacyMappings()) {
            first = separator(out, first);
            out.append('{');
            field(out, "legacyItemContentId", definition.legacyItemContentId()).append(',');
            field(out, "disposition", definition.disposition().name()).append(',');
            out.append("\"successorCommodityId\":");
            if (definition.successorCommodityId() == null) {
                out.append("null");
            } else {
                string(out, definition.successorCommodityId());
            }
            out.append(',');
            field(out, "migrationNote", definition.migrationNote());
            out.append('}');
        }
        out.append("]}");
        return out.toString();
    }

    private static boolean separator(StringBuilder out, boolean first) {
        if (!first) {
            out.append(',');
        }
        return false;
    }

    private static StringBuilder field(StringBuilder out, String name, String value) {
        string(out, name).append(':');
        return string(out, value);
    }

    private static StringBuilder string(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"');
    }
}
