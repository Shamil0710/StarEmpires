package com.spacesim.ship;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Explicit Stage-17.5B compatibility seam from legacy persistent archetype references to production
 * engineering fits.
 *
 * <p>The adapter is intentionally read-only: it does not respawn, clone, replace or mutate the
 * Ashley entity and therefore cannot change its persistent EntityId/FleetId lifecycle. Mapping is
 * explicit content migration configuration, never a {@code ShipType}-based performance bonus.</p>
 */
public final class LegacyShipEngineeringAdapter {
    private final ShipEngineeringCatalog engineeringCatalog;
    private final Map<String, String> fitIdByLegacyArchetype;

    /**
     * Creates an explicit legacy-archetype mapping.
     *
     * @param engineeringCatalog production engineering catalog
     * @param fitIdByLegacyArchetype legacy archetype content ID to engineering fit ID
     */
    public LegacyShipEngineeringAdapter(
            ShipEngineeringCatalog engineeringCatalog,
            Map<String, String> fitIdByLegacyArchetype) {
        this.engineeringCatalog = Objects.requireNonNull(engineeringCatalog, "engineeringCatalog");
        Objects.requireNonNull(fitIdByLegacyArchetype, "fitIdByLegacyArchetype");
        TreeMap<String, String> copy = new TreeMap<>();
        for (Map.Entry<String, String> entry : fitIdByLegacyArchetype.entrySet()) {
            String archetypeId = requireNonBlank(entry.getKey(), "legacy archetype ID");
            String fitId = requireNonBlank(entry.getValue(), "engineering fit ID");
            if (engineeringCatalog.findDemonstratorFit(fitId) == null) {
                throw new IllegalArgumentException("Legacy mapping references unknown engineering fit: " + fitId);
            }
            copy.put(archetypeId, fitId);
        }
        this.fitIdByLegacyArchetype = Collections.unmodifiableMap(copy);
    }

    /**
     * Resolves the engineering fit for the existing entity without modifying it.
     *
     * @param entity existing persistent ship entity
     * @return mapped fit, or empty when the legacy archetype has not been migrated yet
     */
    public Optional<InstalledFit> resolve(Entity entity) {
        Entity checked = Objects.requireNonNull(entity, "entity");
        ArchetypeComponent archetype = checked.getComponent(ArchetypeComponent.class);
        if (archetype == null) {
            return Optional.empty();
        }
        String fitId = fitIdByLegacyArchetype.get(archetype.contentId);
        if (fitId == null) {
            return Optional.empty();
        }
        DemonstratorFitDefinition definition = engineeringCatalog.findDemonstratorFit(fitId);
        if (definition == null) {
            throw new IllegalStateException("Validated legacy mapping lost engineering fit: " + fitId);
        }
        return Optional.of(InstalledFit.fromDemonstrator(definition));
    }

    /** @return immutable deterministic migration mapping */
    public Map<String, String> getMappings() {
        return fitIdByLegacyArchetype;
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
