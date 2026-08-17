package com.spacesim.content;

import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable Stage-18I bindings from physical ship interfaces to Stage-18 commodities. */
public final class Stage18ShipConsumableCatalog {
    private final int schemaVersion;
    private final List<ShipConsumableBinding> bindings;
    private final Map<String, ShipConsumableBinding> bindingsById;

    Stage18ShipConsumableCatalog(int schemaVersion, List<ShipConsumableBinding> bindings) {
        this.schemaVersion = schemaVersion;
        List<ShipConsumableBinding> copy = new ArrayList<>(Objects.requireNonNull(bindings, "bindings"));
        copy.sort(Comparator.comparing(ShipConsumableBinding::id));
        LinkedHashMap<String, ShipConsumableBinding> index = new LinkedHashMap<>();
        for (ShipConsumableBinding binding : copy) {
            if (index.putIfAbsent(binding.id(), binding) != null) {
                throw new IllegalArgumentException("Duplicate ship-consumable binding: " + binding.id());
            }
        }
        this.bindings = List.copyOf(copy);
        this.bindingsById = Collections.unmodifiableMap(index);
    }

    /** @return supported Stage-18I ship-consumable binding schema */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** @return deterministic immutable physical ship-consumable bindings */
    public List<ShipConsumableBinding> getBindings() {
        return bindings;
    }

    /**
     * Finds one physical ship-consumable binding.
     *
     * @param id stable binding ID
     * @return binding or {@code null}
     */
    public ShipConsumableBinding findBinding(String id) {
        return bindingsById.get(id);
    }

    /**
     * One explicit commodity-to-module-interface servicing binding.
     *
     * @param id stable Stage-18I binding ID
     * @param moduleId Stage-17.5 module ID owning the interface
     * @param interfaceId module-local interface ID
     * @param interfaceKind physical interface kind
     * @param commodityId Stage-18 commodity consumed from station storage
     * @param amountPerKg interface-native amount added per physical kilogram
     */
    public record ShipConsumableBinding(
            String id,
            String moduleId,
            String interfaceId,
            InterfaceKind interfaceKind,
            String commodityId,
            double amountPerKg) {
        /**
         * Validates one servicing binding.
         *
         * @param id stable binding ID
         * @param moduleId owning module ID
         * @param interfaceId module-local interface ID
         * @param interfaceKind interface kind
         * @param commodityId Stage-18 commodity ID
         * @param amountPerKg interface-native amount per kilogram
         */
        public ShipConsumableBinding {
            id = requireText(id, "binding id");
            moduleId = requireText(moduleId, "moduleId");
            interfaceId = requireText(interfaceId, "interfaceId");
            Objects.requireNonNull(interfaceKind, "interfaceKind");
            commodityId = requireText(commodityId, "commodityId");
            if (!Double.isFinite(amountPerKg) || amountPerKg <= 0d) {
                throw new IllegalArgumentException("amountPerKg must be finite and positive");
            }
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
        return value;
    }
}
