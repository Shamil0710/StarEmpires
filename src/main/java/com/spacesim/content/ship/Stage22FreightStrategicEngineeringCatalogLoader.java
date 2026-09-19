package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Post-M22.6 production bridge that gives the two reviewed core freight hulls explicit fitted FTL
 * and physically sufficient long-haul reaction-mass storage.
 *
 * <p>The accepted core package itself remains unchanged. Each strategic freight variant occupies the
 * already-authored but unused {@code utility_defense} slot with the existing common strategic FTL
 * module and replaces only its ordinary main-drive package with a freight long-haul derivative.
 * Thrust and exhaust velocity are unchanged. The derivative represents extra feed/tankage plumbing:
 * it adds dry integration mass and raises the finite {@code propellant_feed} capacity so accepted
 * Stage-20 freight routes can be flown without weakening the rocket equation or route preflight.</p>
 */
public final class Stage22FreightStrategicEngineeringCatalogLoader {
    /** Empire inter-system bulk freight fit. */
    public static final String EMPIRE_FREIGHT_STRATEGIC_FIT =
            "fit.empire.freight.strategic_v1";
    /** Industrial Union inter-system bulk freight fit. */
    public static final String UNION_FREIGHT_STRATEGIC_FIT =
            "fit.industrial_union.freight.strategic_v1";
    /** Empire freight-only long-haul drive package. */
    public static final String EMPIRE_LONG_HAUL_DRIVE =
            "module.empire_drive_longhaul_freight_v1";
    /** Industrial Union freight-only long-haul drive package. */
    public static final String UNION_LONG_HAUL_DRIVE =
            "module.industrial_union_drive_longhaul_freight_v1";
    /** Finite installed reaction-mass capacity of each reviewed long-haul freight feed. */
    public static final double LONG_HAUL_REACTION_MASS_KG = 12_000_000d;

    private static final String EMPIRE_BASE = "fit.empire.freight.bulk_v1";
    private static final String UNION_BASE = "fit.industrial_union.freight.bulk_v1";
    private static final String EMPIRE_BASE_DRIVE = "module.empire_drive_endurance_v1";
    private static final String UNION_BASE_DRIVE = "module.industrial_union_drive_bank_v1";
    private static final String DRIVE_MOUNT = "core_drive";
    private static final String FTL_MOUNT = "utility_defense";
    private static final double LONG_HAUL_DRY_MASS_ADDITION_KG = 400_000d;
    private static final double LONG_HAUL_VOLUME_ADDITION_M3 = 2_500d;

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
        ArrayList<ModuleDefinition> modules = new ArrayList<>(base.getModules());
        modules.add(longHaulDrive(
                base, EMPIRE_BASE_DRIVE, EMPIRE_LONG_HAUL_DRIVE,
                "Imperial Long-Haul Freight Drive"));
        modules.add(longHaulDrive(
                base, UNION_BASE_DRIVE, UNION_LONG_HAUL_DRIVE,
                "Union Long-Haul Freight Drive"));

        ArrayList<DemonstratorFitDefinition> fits = new ArrayList<>(base.getDemonstratorFits());
        fits.add(strategicFreight(
                base, EMPIRE_BASE, EMPIRE_FREIGHT_STRATEGIC_FIT, EMPIRE_LONG_HAUL_DRIVE));
        fits.add(strategicFreight(
                base, UNION_BASE, UNION_FREIGHT_STRATEGIC_FIT, UNION_LONG_HAUL_DRIVE));
        return new ShipEngineeringCatalog(
                base.getSchemaVersion(),
                base.getMigrationVersion(),
                base.getMaterials(),
                base.getResponseSurfaces(),
                base.getProtectionStacks(),
                base.getHulls(),
                List.copyOf(modules),
                List.copyOf(fits));
    }

    private static ModuleDefinition longHaulDrive(
            ShipEngineeringCatalog catalog,
            String sourceModuleId,
            String projectedModuleId,
            String displayName) {
        ModuleDefinition source = Objects.requireNonNull(
                catalog.findModule(sourceModuleId), "missing freight base drive " + sourceModuleId);
        ArrayList<InterfaceDefinition> interfaces = new ArrayList<>();
        int propellantFeeds = 0;
        for (InterfaceDefinition definition : source.interfaces()) {
            if (definition.kind() == InterfaceKind.REACTION_MASS
                    && "propellant_feed".equals(definition.id())) {
                interfaces.add(new InterfaceDefinition(
                        definition.kind(), definition.id(), LONG_HAUL_REACTION_MASS_KG));
                propellantFeeds++;
            } else {
                interfaces.add(definition);
            }
        }
        if (propellantFeeds != 1) {
            throw new IllegalStateException(
                    "freight base drive must expose exactly one propellant_feed: " + sourceModuleId);
        }
        return new ModuleDefinition(
                projectedModuleId,
                displayName,
                source.family(),
                source.integrationCategories(),
                source.compatibleHardpointSizes(),
                source.physicalDimensionsM(),
                source.massKg() + LONG_HAUL_DRY_MASS_ADDITION_KG,
                source.occupiedVolumeM3() + LONG_HAUL_VOLUME_ADDITION_M3,
                source.requiredMountStrengthN(),
                source.continuousPowerSupplyW(),
                source.continuousPowerDemandW(),
                source.peakPowerDemandW(),
                source.storedEnergyCapacityJ(),
                source.wasteHeatW(),
                source.localThermalCapacityJ(),
                source.coolantTransferDemandW(),
                source.heatRejectionW(),
                source.crewRequirement(),
                source.automationRequirement(),
                List.copyOf(interfaces),
                source.signatureContributions(),
                source.constructionInputs(),
                source.maintenance(),
                source.capabilityParameters());
    }

    private static DemonstratorFitDefinition strategicFreight(
            ShipEngineeringCatalog catalog,
            String baseFitId,
            String strategicFitId,
            String longHaulDriveId) {
        if (catalog.findDemonstratorFit(strategicFitId) != null) {
            throw new IllegalStateException("strategic freight fit already exists: " + strategicFitId);
        }
        DemonstratorFitDefinition base = Objects.requireNonNull(
                catalog.findDemonstratorFit(baseFitId), "missing freight base fit " + baseFitId);
        boolean ftlMountAlreadyUsed = base.installedModules().stream()
                .anyMatch(value -> FTL_MOUNT.equals(value.mountId()));
        if (ftlMountAlreadyUsed) {
            throw new IllegalStateException(
                    "freight strategic FTL requires the authored utility_defense slot to be free: "
                            + baseFitId);
        }
        ArrayList<InstalledModuleDefinition> installed = new ArrayList<>();
        int replacedDrive = 0;
        for (InstalledModuleDefinition value : base.installedModules()) {
            if (DRIVE_MOUNT.equals(value.mountId())) {
                installed.add(new InstalledModuleDefinition(DRIVE_MOUNT, longHaulDriveId));
                replacedDrive++;
            } else {
                installed.add(value);
            }
        }
        if (replacedDrive != 1) {
            throw new IllegalStateException(
                    "strategic freight requires exactly one core_drive replacement: " + baseFitId);
        }
        installed.add(new InstalledModuleDefinition(
                FTL_MOUNT,
                Stage22CorePairStrategicMobilityProjection.FTL_MODULE_ID));
        return new DemonstratorFitDefinition(
                strategicFitId,
                base.hullId(),
                List.copyOf(installed));
    }
}
