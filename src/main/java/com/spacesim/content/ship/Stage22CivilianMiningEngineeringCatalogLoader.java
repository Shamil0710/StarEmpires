package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * M22.5 immutable engineering overlay for the shared civilian asteroid-mining replacement fit.
 *
 * <p>The overlay reuses the reviewed Industrial Union fleet-support hull, reactor, drive, sensor and
 * thermal modules, but replaces the repair/salvage workshop with one explicitly authored asteroid
 * excavation section. It does not modify the accepted M22.4 Union catalog and owns no runtime state.</p>
 */
public final class Stage22CivilianMiningEngineeringCatalogLoader {
    /** Compatibility-era workshop module replaced by the mining mission section. */
    public static final String BASE_WORKSHOP_MODULE_ID = "module.industrial_union_workshop_section_v1";
    /** Reviewed Union support fit whose physical envelope is reused by the civilian mining refit. */
    public static final String BASE_SUPPORT_FIT_ID = "fit.industrial_union.fleet_support.repair_v1";
    /** Shared M22.5 asteroid-excavation module ID. */
    public static final String MINING_MODULE_ID = "module.civilian.miners.asteroid_excavation_section_v1";
    /** Shared M22.5 physically buildable mining fit ID. */
    public static final String MINING_FIT_ID = "fit.civilian.miners.asteroid_excavator_v1";
    /** Authored available process power exposed to Stage-18 extraction. */
    public static final String PARAM_AVAILABLE_POWER_W = "extraction_available_power_w";
    /** Authored engineering work rate exposed to Stage-18 extraction. */
    public static final String PARAM_WORK_RATE = "extraction_work_rate";
    /** Authored maintenance work rate exposed to Stage-18 extraction. */
    public static final String PARAM_MAINTENANCE_WORK_RATE = "extraction_maintenance_work_rate";
    /** Authored maximum source throughput of the excavation section. */
    public static final String PARAM_MAX_SOURCE_KG_S = "extraction_max_source_kg_s";

    private Stage22CivilianMiningEngineeringCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads the accepted Union engineering content plus one non-sovereign M22.5 mining module/fit.
     *
     * @return immutable Stage-17.5 engineering catalog containing the civilian mining fit
     */
    public static ShipEngineeringCatalog loadDefault() {
        ShipEngineeringCatalog base = Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        ModuleDefinition workshop = Objects.requireNonNull(
                base.findModule(BASE_WORKSHOP_MODULE_ID), "reviewed Union workshop module");
        DemonstratorFitDefinition supportFit = Objects.requireNonNull(
                base.findDemonstratorFit(BASE_SUPPORT_FIT_ID), "reviewed Union support fit");
        if (base.findModule(MINING_MODULE_ID) != null || base.findDemonstratorFit(MINING_FIT_ID) != null) {
            throw new IllegalStateException("M22.5 civilian mining IDs collide with accepted engineering content");
        }

        ModuleDefinition miningModule = new ModuleDefinition(
                MINING_MODULE_ID,
                "Civilian Asteroid Excavation Section",
                workshop.family(),
                workshop.integrationCategories(),
                workshop.compatibleHardpointSizes(),
                workshop.physicalDimensionsM(),
                workshop.massKg(),
                workshop.occupiedVolumeM3(),
                workshop.requiredMountStrengthN(),
                workshop.continuousPowerSupplyW(),
                workshop.continuousPowerDemandW(),
                workshop.peakPowerDemandW(),
                workshop.storedEnergyCapacityJ(),
                workshop.wasteHeatW(),
                workshop.localThermalCapacityJ(),
                workshop.coolantTransferDemandW(),
                workshop.heatRejectionW(),
                workshop.crewRequirement(),
                workshop.automationRequirement(),
                List.of(new InterfaceDefinition(InterfaceKind.CONSUMABLE, "excavation_stores", 9_000_000d)),
                workshop.signatureContributions(),
                workshop.constructionInputs(),
                workshop.maintenance(),
                Map.of(
                        PARAM_AVAILABLE_POWER_W, 4_000_000d,
                        PARAM_WORK_RATE, 2.5d,
                        PARAM_MAINTENANCE_WORK_RATE, 0.125d,
                        PARAM_MAX_SOURCE_KG_S, 25d));

        List<InstalledModuleDefinition> installed = new ArrayList<>();
        int replaced = 0;
        for (InstalledModuleDefinition value : supportFit.installedModules()) {
            if (value.moduleId().equals(BASE_WORKSHOP_MODULE_ID)) {
                installed.add(new InstalledModuleDefinition(value.mountId(), MINING_MODULE_ID));
                replaced++;
            } else {
                installed.add(value);
            }
        }
        if (replaced != 1) {
            throw new IllegalStateException("Reviewed support fit must contain exactly one workshop mission module");
        }
        DemonstratorFitDefinition miningFit = new DemonstratorFitDefinition(
                MINING_FIT_ID, supportFit.hullId(), List.copyOf(installed));

        List<ModuleDefinition> modules = new ArrayList<>(base.getModules());
        modules.add(miningModule);
        List<DemonstratorFitDefinition> fits = new ArrayList<>(base.getDemonstratorFits());
        fits.add(miningFit);
        return new ShipEngineeringCatalog(
                base.getSchemaVersion(),
                base.getMigrationVersion(),
                base.getMaterials(),
                base.getResponseSurfaces(),
                base.getProtectionStacks(),
                base.getHulls(),
                modules,
                fits);
    }
}
