package com.spacesim.content.ship;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;

/** Common physical vocabulary for existing strategic freight and accepted carrier craft. */
public final class Stage23RuntimeEngineeringCatalogLoader {
    private Stage23RuntimeEngineeringCatalogLoader() { }

    /**
     * Combines immutable accepted definitions without replacing any shared content identity.
     * This runtime vocabulary does not authorize new world assets or checkpoint migrations.
     * @return complete engineering vocabulary for ordinary movement and operation grants
     */
    public static ShipEngineeringCatalog loadDefault() {
        var freight = Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
        var craft = Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        if (freight.getSchemaVersion() != craft.getSchemaVersion()
                || freight.getMigrationVersion() != craft.getMigrationVersion())
            throw new IllegalArgumentException("Incompatible engineering catalog schemas");
        return new ShipEngineeringCatalog(freight.getSchemaVersion(), freight.getMigrationVersion(),
                merge(freight.getMaterials(), craft.getMaterials(), ShipEngineeringCatalog.MaterialDefinition::id),
                merge(freight.getResponseSurfaces(), craft.getResponseSurfaces(), ShipEngineeringCatalog.HeavyImpactResponseSurfaceDefinition::id),
                merge(freight.getProtectionStacks(), craft.getProtectionStacks(), ShipEngineeringCatalog.ProtectionStackDefinition::id),
                merge(freight.getHulls(), craft.getHulls(), ShipEngineeringCatalog.HullDefinition::id),
                merge(freight.getModules(), craft.getModules(), ShipEngineeringCatalog.ModuleDefinition::id),
                merge(freight.getDemonstratorFits(), craft.getDemonstratorFits(), ShipEngineeringCatalog.DemonstratorFitDefinition::id));
    }

    private static <T> List<T> merge(List<T> existing, List<T> additional, Function<T, String> identity) {
        var result = new LinkedHashMap<String, T>();
        for (var definition : existing) result.put(identity.apply(definition), definition);
        for (var definition : additional) {
            String id = identity.apply(definition);
            var previous = result.putIfAbsent(id, definition);
            if (previous != null && !previous.equals(definition))
                throw new IllegalArgumentException("Conflicting accepted engineering definition: " + id);
        }
        return List.copyOf(result.values());
    }
}
