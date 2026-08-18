package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.CalibrationDomainDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HeavyImpactResponseSurfaceDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Loads the production-valid but content-provisional Stage 17.5I engineering packs.
 *
 * <p>Both representative hull vocabulary and doctrine fits deliberately use
 * {@link ShipEngineeringCatalogLoader} rather than a parallel test schema. Stage 19 promotes only
 * the provisional doctrine heavy-impact mass domain needed to contain the already-authored 2,000 kg
 * strike round; Stage 22 may replace or explicitly promote the wider provisional definitions.</p>
 */
public final class Stage175ICombatTestContentPack {
    /** Classpath resource for the Stage 17.5I representative hull engineering pack. */
    public static final String RESOURCE = "data/content/stage17_5i-combat-test-engineering-v1.json";
    /** Classpath resource for the Stage 17.5I five-doctrine engineering pack. */
    public static final String DOCTRINE_RESOURCE = "data/content/stage17_5i-doctrine-engineering-v1.json";
    /** Response surface promoted narrowly for the authored Stage-19 2 t strike missile envelope. */
    public static final String STAGE19_PROMOTED_RESPONSE_ID = "response.stage17_5i_doctrine_v1";
    /** Maximum projectile mass admitted by the Stage-19 provisional promotion. */
    public static final double STAGE19_MAX_PROJECTILE_MASS_KG = 2_000d;

    private Stage175ICombatTestContentPack() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads the representative hull pack through the ordinary production engineering parser.
     *
     * @return immutable validated representative engineering catalog
     */
    public static ShipEngineeringCatalog load() {
        return loadResource(RESOURCE);
    }

    /**
     * Loads the five-doctrine combat pack and applies the explicit Stage-19 strike-mass promotion.
     *
     * <p>The authored Stage-17.5 response domain stopped at 1,500 kg while the already-authored strike
     * missile has 2,000 kg wet mass. Stage 19 must not extrapolate the material response silently, so
     * the accepted runtime widens that single provisional upper bound exactly to 2,000 kg and changes
     * its confidence label. Velocity bounds, materials, protection stacks, hulls, modules and fits are
     * untouched. The reconstructed catalog receives a new semantic fingerprint automatically.</p>
     *
     * @return immutable validated doctrine engineering catalog with explicit Stage-19 mass envelope
     */
    public static ShipEngineeringCatalog loadDoctrines() {
        return promoteStage19StrikeMass(loadResource(DOCTRINE_RESOURCE));
    }

    private static ShipEngineeringCatalog promoteStage19StrikeMass(ShipEngineeringCatalog catalog) {
        boolean found = false;
        List<HeavyImpactResponseSurfaceDefinition> promoted = catalog.getResponseSurfaces().stream()
                .map(surface -> {
                    if (!surface.id().equals(STAGE19_PROMOTED_RESPONSE_ID)) {
                        return surface;
                    }
                    CalibrationDomainDefinition domain = surface.calibrationDomain();
                    if (domain.maxProjectileMassKg() > STAGE19_MAX_PROJECTILE_MASS_KG) {
                        throw new IllegalStateException("Stage-19 promotion would narrow an already wider domain");
                    }
                    return new HeavyImpactResponseSurfaceDefinition(
                            surface.id(),
                            new CalibrationDomainDefinition(
                                    domain.minImpactVelocityMps(),
                                    domain.maxImpactVelocityMps(),
                                    domain.minProjectileMassKg(),
                                    STAGE19_MAX_PROJECTILE_MASS_KG,
                                    "stage19_strike_2t_provisional_test_only"));
                })
                .toList();
        for (HeavyImpactResponseSurfaceDefinition surface : promoted) {
            if (surface.id().equals(STAGE19_PROMOTED_RESPONSE_ID)) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalStateException("Missing Stage-19 promoted response surface");
        }
        return new ShipEngineeringCatalog(
                catalog.getSchemaVersion(),
                catalog.getMigrationVersion(),
                catalog.getMaterials(),
                promoted,
                catalog.getProtectionStacks(),
                catalog.getHulls(),
                catalog.getModules(),
                catalog.getDemonstratorFits());
    }

    private static ShipEngineeringCatalog loadResource(String resource) {
        ClassLoader classLoader = Stage175ICombatTestContentPack.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage 17.5I combat-test content: " + resource);
            }
            return ShipEngineeringCatalogLoader.parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage 17.5I combat-test content: " + resource, exception);
        }
    }
}