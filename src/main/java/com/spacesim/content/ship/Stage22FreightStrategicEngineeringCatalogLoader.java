package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Post-M22.6 production bridge that gives the two reviewed core freight hulls explicit fitted FTL.
 *
 * <p>The accepted core package itself remains unchanged. Each freight strategic variant occupies the
 * already-authored but unused {@code utility_defense} slot with the existing common strategic FTL
 * module. The ship therefore pays real module mass, power, thermal and integration costs and no
 * legacy/free jump capability is granted.</p>
 */
public final class Stage22FreightStrategicEngineeringCatalogLoader {
    /** Empire inter-system bulk freight fit. */
    public static final String EMPIRE_FREIGHT_STRATEGIC_FIT =
            "fit.empire.freight.strategic_v1";
    /** Industrial Union inter-system bulk freight fit. */
    public static final String UNION_FREIGHT_STRATEGIC_FIT =
            "fit.industrial_union.freight.strategic_v1";

    private static final String EMPIRE_BASE = "fit.empire.freight.bulk_v1";
    private static final String UNION_BASE = "fit.industrial_union.freight.bulk_v1";
    private static final String FTL_MOUNT = "utility_defense";

    private Stage22FreightStrategicEngineeringCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads the core engineering universe plus explicit physical strategic freight variants.
     *
     * @return immutable core engineering catalog including both fitted strategic freight variants
     */
    public static ShipEngineeringCatalog loadDefault() {
        ShipEngineeringCatalog base = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        ArrayList<DemonstratorFitDefinition> fits = new ArrayList<>(base.getDemonstratorFits());
        fits.add(strategicFreight(base, EMPIRE_BASE, EMPIRE_FREIGHT_STRATEGIC_FIT));
        fits.add(strategicFreight(base, UNION_BASE, UNION_FREIGHT_STRATEGIC_FIT));
        return new ShipEngineeringCatalog(
                base.getSchemaVersion(),
                base.getMigrationVersion(),
                base.getMaterials(),
                base.getResponseSurfaces(),
                base.getProtectionStacks(),
                base.getHulls(),
                base.getModules(),
                List.copyOf(fits));
    }

    private static DemonstratorFitDefinition strategicFreight(
            ShipEngineeringCatalog catalog,
            String baseFitId,
            String strategicFitId) {
        if (catalog.findDemonstratorFit(strategicFitId) != null) {
            throw new IllegalStateException("strategic freight fit already exists: " + strategicFitId);
        }
        DemonstratorFitDefinition base = Objects.requireNonNull(
                catalog.findDemonstratorFit(baseFitId), "missing freight base fit " + baseFitId);
        boolean mountAlreadyUsed = base.installedModules().stream()
                .anyMatch(value -> FTL_MOUNT.equals(value.mountId()));
        if (mountAlreadyUsed) {
            throw new IllegalStateException(
                    "freight strategic FTL requires the authored utility_defense slot to be free: "
                            + baseFitId);
        }
        ArrayList<InstalledModuleDefinition> installed = new ArrayList<>(base.installedModules());
        installed.add(new InstalledModuleDefinition(
                FTL_MOUNT,
                Stage22CorePairStrategicMobilityProjection.FTL_MODULE_ID));
        return new DemonstratorFitDefinition(
                strategicFitId,
                base.hullId(),
                List.copyOf(installed));
    }
}
