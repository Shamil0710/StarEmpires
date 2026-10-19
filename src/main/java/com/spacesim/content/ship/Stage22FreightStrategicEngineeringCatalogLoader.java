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
    /** Same Union strategic freight hull with its cargo section replaced by excavation equipment. */
    public static final String UNION_MINING_FREIGHT_STRATEGIC_FIT =
            "fit.industrial_union.freight.strategic_mining_v1";
    /** Same Union strategic freight hull carrying the Union kinetic cassette on its weapon hardpoint. */
    public static final String UNION_ARMED_FREIGHT_STRATEGIC_FIT =
            "fit.industrial_union.freight.armed_escort_v1";
    /** Weapon hardpoint added to the Union freight hull, copied from the Union corvette's primary mount. */
    public static final String FREIGHT_WEAPON_HARDPOINT = "weapon_primary";
    /** Union kinetic cassette installed by the armed freight fit. */
    public static final String UNION_KINETIC_CASSETTE = "module.industrial_union_weapon_cassette_v1";
    private static final String UNION_FREIGHT_HULL = "hull.industrial_union_freight_v1";
    private static final String UNION_CORVETTE_HULL = "hull.industrial_union_corvette_v1";
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
        // Admit the already authored civilian mining hardware to the common runtime catalog.
        // Existing fits are untouched; this supplies no installed equipment or new physical asset.
        ShipEngineeringCatalog mining = Stage22CivilianMiningEngineeringCatalogLoader.loadDefault();
        modules.add(Objects.requireNonNull(mining.findModule(Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID)));
        modules.add(Objects.requireNonNull(mining.findModule(Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID)));
        modules.add(longHaulDrive(
                base, EMPIRE_BASE_DRIVE, EMPIRE_LONG_HAUL_DRIVE,
                "Imperial Long-Haul Freight Drive"));
        modules.add(longHaulDrive(
                base, UNION_BASE_DRIVE, UNION_LONG_HAUL_DRIVE,
                "Union Long-Haul Freight Drive"));

        ArrayList<DemonstratorFitDefinition> fits = new ArrayList<>(base.getDemonstratorFits());
        fits.add(Objects.requireNonNull(mining.findDemonstratorFit(Stage22CivilianMiningEngineeringCatalogLoader.MINING_FIT_ID)));
        fits.add(strategicFreight(
                base, EMPIRE_BASE, EMPIRE_FREIGHT_STRATEGIC_FIT, EMPIRE_LONG_HAUL_DRIVE));
        fits.add(strategicFreight(
                base, UNION_BASE, UNION_FREIGHT_STRATEGIC_FIT, UNION_LONG_HAUL_DRIVE));
        DemonstratorFitDefinition unionStrategic = fits.get(fits.size() - 1);
        fits.add(miningFreight(unionStrategic));
        fits.add(armedFreight(unionStrategic));
        return new ShipEngineeringCatalog(
                base.getSchemaVersion(),
                base.getMigrationVersion(),
                base.getMaterials(),
                base.getResponseSurfaces(),
                base.getProtectionStacks(),
                hullsWithFreightWeaponHardpoint(base),
                List.copyOf(modules),
                List.copyOf(fits));
    }

    /**
     * The Union freight hull gains one empty weapon hardpoint with the size, arc and envelope of the
     * Union corvette's primary mount. Existing freight fits leave it empty and are physically unchanged.
     */
    private static List<ShipEngineeringCatalog.HullDefinition> hullsWithFreightWeaponHardpoint(ShipEngineeringCatalog base) {
        var corvette = Objects.requireNonNull(base.findHull(UNION_CORVETTE_HULL), UNION_CORVETTE_HULL);
        var weapon = corvette.hardpoints().stream().filter(h -> h.id().equals(FREIGHT_WEAPON_HARDPOINT)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Union corvette lacks its primary weapon hardpoint"));
        var hulls = new ArrayList<ShipEngineeringCatalog.HullDefinition>();
        for (var hull : base.getHulls()) {
            if (!hull.id().equals(UNION_FREIGHT_HULL)) { hulls.add(hull); continue; }
            var dims = hull.boundingDimensionsM();
            // Dorsal, forward of the cargo section: inside the hull envelope, same limits as the corvette mount.
            var mount = new ShipEngineeringCatalog.HardpointDefinition(weapon.id(), weapon.size(),
                    new ShipEngineeringCatalog.Vector3d(dims.lengthM() * 0.25, 0d, dims.heightM() * 0.5),
                    weapon.arc(), weapon.maxModuleDimensionsM(), weapon.maxModuleMassKg(), weapon.maxRecoilImpulseNs(),
                    weapon.allowedModuleFamilies());
            var hardpoints = new ArrayList<>(hull.hardpoints());
            hardpoints.add(mount);
            hulls.add(new ShipEngineeringCatalog.HullDefinition(hull.id(), hull.displayName(), hull.architecture(),
                    dims, hull.bareHullMassKg(), hull.internalVolumeM3(), hull.slots(), List.copyOf(hardpoints),
                    hull.compartments(), hull.crewBaseline(), hull.lifeSupportCapacity(), hull.baseSignatureGeometryAreaM2(),
                    hull.structuralProtectionStackId(), hull.maxOperationalMassKg(), hull.thrustMountCompatibility()));
        }
        return List.copyOf(hulls);
    }

    /**
     * @param extended hull from this catalogue
     * @param original same-identity hull from another accepted catalogue
     * @return whether {@code extended} is {@code original} plus only the freight weapon hardpoint
     */
    public static boolean onlyAddsFreightWeaponHardpoint(ShipEngineeringCatalog.HullDefinition extended,
            ShipEngineeringCatalog.HullDefinition original) {
        if (!extended.id().equals(UNION_FREIGHT_HULL) || !original.id().equals(UNION_FREIGHT_HULL)) return false;
        var withoutWeapon = extended.hardpoints().stream().filter(h -> !h.id().equals(FREIGHT_WEAPON_HARDPOINT)).toList();
        return withoutWeapon.size() == extended.hardpoints().size() - 1 && withoutWeapon.equals(original.hardpoints())
                && new ShipEngineeringCatalog.HullDefinition(extended.id(), extended.displayName(), extended.architecture(),
                        extended.boundingDimensionsM(), extended.bareHullMassKg(), extended.internalVolumeM3(), extended.slots(),
                        original.hardpoints(), extended.compartments(), extended.crewBaseline(), extended.lifeSupportCapacity(),
                        extended.baseSignatureGeometryAreaM2(), extended.structuralProtectionStackId(), extended.maxOperationalMassKg(),
                        extended.thrustMountCompatibility()).equals(original);
    }

    private static DemonstratorFitDefinition armedFreight(DemonstratorFitDefinition source) {
        var installed = new ArrayList<>(source.installedModules());
        installed.add(new InstalledModuleDefinition(FREIGHT_WEAPON_HARDPOINT, UNION_KINETIC_CASSETTE));
        return new DemonstratorFitDefinition(UNION_ARMED_FREIGHT_STRATEGIC_FIT, source.hullId(), List.copyOf(installed));
    }

    private static DemonstratorFitDefinition miningFreight(DemonstratorFitDefinition source) {
        ArrayList<InstalledModuleDefinition> installed = new ArrayList<>();
        int replaced = 0;
        for (InstalledModuleDefinition assignment : source.installedModules()) {
            if ("module.industrial_union_cargo_section_v1".equals(assignment.moduleId())) {
                installed.add(new InstalledModuleDefinition(assignment.mountId(),
                        Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID));
                replaced++;
            } else {
                installed.add(assignment);
            }
        }
        if (replaced != 1) {
            throw new IllegalStateException("Mining freight refit requires exactly one cargo section");
        }
        return new DemonstratorFitDefinition(UNION_MINING_FREIGHT_STRATEGIC_FIT,
                source.hullId(), List.copyOf(installed));
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
