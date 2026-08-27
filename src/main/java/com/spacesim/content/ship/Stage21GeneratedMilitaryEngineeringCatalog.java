package com.spacesim.content.ship;

/**
 * Production-facing catalog authority for the provisional Stage-21 generated-faction military fit set.
 *
 * <p>Generated military materialization and every read-only interpreter of those persisted engineering
 * payloads must resolve hull/module IDs against the same catalog boundary. The underlying A-E doctrine
 * definitions remain the production-valid but content-provisional Stage-17.5I/19 set with the explicit
 * Stage-21 strategic-mobility tradeoff; this class does not promote that content into Stage-22 canon or
 * create a parallel engineering schema.</p>
 */
public final class Stage21GeneratedMilitaryEngineeringCatalog {
    private Stage21GeneratedMilitaryEngineeringCatalog() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads the immutable catalog accepted for generated Stage-21 military engineering payloads.
     *
     * @return validated Stage-21 strategic doctrine engineering catalog
     */
    public static ShipEngineeringCatalog load() {
        return Stage175ICombatTestContentPack.loadStage21StrategicDoctrines();
    }
}
