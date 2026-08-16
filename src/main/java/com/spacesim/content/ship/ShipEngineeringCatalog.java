package com.spacesim.content.ship;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable, versioned Stage-17.5 engineering content catalog.
 *
 * <p>The catalog describes physical hull, material, protection and module inputs. It deliberately
 * does not derive acceleration, power balance, heat balance, damage or combat outcomes; those are
 * Stage 17.5B+ responsibilities. All IDs are persistent content IDs and the semantic fingerprint is
 * independent from JSON whitespace and ordering of independent definitions.</p>
 */
public final class ShipEngineeringCatalog {
    private final int schemaVersion;
    private final int migrationVersion;
    private final List<MaterialDefinition> materials;
    private final List<HeavyImpactResponseSurfaceDefinition> responseSurfaces;
    private final List<ProtectionStackDefinition> protectionStacks;
    private final List<HullDefinition> hulls;
    private final List<ModuleDefinition> modules;
    private final List<DemonstratorFitDefinition> demonstratorFits;
    private final Map<String, MaterialDefinition> materialsById;
    private final Map<String, HeavyImpactResponseSurfaceDefinition> responseSurfacesById;
    private final Map<String, ProtectionStackDefinition> protectionStacksById;
    private final Map<String, HullDefinition> hullsById;
    private final Map<String, ModuleDefinition> modulesById;
    private final Map<String, DemonstratorFitDefinition> demonstratorFitsById;
    private final String fingerprint;

    ShipEngineeringCatalog(
            int schemaVersion,
            int migrationVersion,
            List<MaterialDefinition> materials,
            List<HeavyImpactResponseSurfaceDefinition> responseSurfaces,
            List<ProtectionStackDefinition> protectionStacks,
            List<HullDefinition> hulls,
            List<ModuleDefinition> modules,
            List<DemonstratorFitDefinition> demonstratorFits) {
        this.schemaVersion = schemaVersion;
        this.migrationVersion = migrationVersion;
        this.materials = sortedCopy(materials.stream().map(ShipEngineeringCatalog::copyMaterial).toList(),
                Comparator.comparing(MaterialDefinition::id));
        this.responseSurfaces = sortedCopy(responseSurfaces,
                Comparator.comparing(HeavyImpactResponseSurfaceDefinition::id));
        this.protectionStacks = sortedCopy(protectionStacks.stream().map(ShipEngineeringCatalog::copyProtectionStack).toList(),
                Comparator.comparing(ProtectionStackDefinition::id));
        this.hulls = sortedCopy(hulls.stream().map(ShipEngineeringCatalog::copyHull).toList(),
                Comparator.comparing(HullDefinition::id));
        this.modules = sortedCopy(modules.stream().map(ShipEngineeringCatalog::copyModule).toList(),
                Comparator.comparing(ModuleDefinition::id));
        this.demonstratorFits = sortedCopy(demonstratorFits.stream().map(ShipEngineeringCatalog::copyFit).toList(),
                Comparator.comparing(DemonstratorFitDefinition::id));
        this.materialsById = index(this.materials, MaterialDefinition::id);
        this.responseSurfacesById = index(this.responseSurfaces, HeavyImpactResponseSurfaceDefinition::id);
        this.protectionStacksById = index(this.protectionStacks, ProtectionStackDefinition::id);
        this.hullsById = index(this.hulls, HullDefinition::id);
        this.modulesById = index(this.modules, ModuleDefinition::id);
        this.demonstratorFitsById = index(this.demonstratorFits, DemonstratorFitDefinition::id);
        this.fingerprint = computeFingerprint();
    }

    /** @return supported engineering schema version */
    public int getSchemaVersion() { return schemaVersion; }

    /** @return explicit migration contract version */
    public int getMigrationVersion() { return migrationVersion; }

    /** @return deterministic material definitions */
    public List<MaterialDefinition> getMaterials() { return materials; }

    /** @return deterministic bounded heavy-impact response definitions */
    public List<HeavyImpactResponseSurfaceDefinition> getResponseSurfaces() { return responseSurfaces; }

    /** @return deterministic protection stacks */
    public List<ProtectionStackDefinition> getProtectionStacks() { return protectionStacks; }

    /** @return deterministic hull definitions */
    public List<HullDefinition> getHulls() { return hulls; }

    /** @return deterministic module definitions */
    public List<ModuleDefinition> getModules() { return modules; }

    /** @return machine-readable reference fits used for schema acceptance */
    public List<DemonstratorFitDefinition> getDemonstratorFits() { return demonstratorFits; }

    /** @return lowercase SHA-256 semantic fingerprint */
    public String getFingerprint() { return fingerprint; }

    /**
     * Finds a material by stable content ID.
     *
     * @param id material content ID
     * @return material definition or {@code null}
     */
    public MaterialDefinition findMaterial(String id) { return materialsById.get(id); }

    /**
     * Finds a heavy-impact response surface by stable content ID.
     *
     * @param id response-surface content ID
     * @return response-surface definition or {@code null}
     */
    public HeavyImpactResponseSurfaceDefinition findResponseSurface(String id) { return responseSurfacesById.get(id); }

    /**
     * Finds a protection stack by stable content ID.
     *
     * @param id protection-stack content ID
     * @return protection-stack definition or {@code null}
     */
    public ProtectionStackDefinition findProtectionStack(String id) { return protectionStacksById.get(id); }

    /**
     * Finds a hull by stable content ID.
     *
     * @param id hull content ID
     * @return hull definition or {@code null}
     */
    public HullDefinition findHull(String id) { return hullsById.get(id); }

    /**
     * Finds a module by stable content ID.
     *
     * @param id module content ID
     * @return module definition or {@code null}
     */
    public ModuleDefinition findModule(String id) { return modulesById.get(id); }

    /**
     * Finds a machine-readable demonstrator fit by stable content ID.
     *
     * @param id fit content ID
     * @return demonstrator fit or {@code null}
     */
    public DemonstratorFitDefinition findDemonstratorFit(String id) { return demonstratorFitsById.get(id); }

    private String computeFingerprint() {
        StringBuilder out = new StringBuilder(16_384);
        out.append("schema|").append(schemaVersion).append('|').append(migrationVersion).append('\n');
        for (MaterialDefinition value : materials) {
            out.append("material|").append(value.id()).append('|')
                    .append(bits(value.densityKgPerM3())).append('|')
                    .append(bits(value.thermalConductivityWPerMK())).append('|')
                    .append(bits(value.specificHeatJPerKgK())).append('|')
                    .append(bits(value.emissivity())).append('|')
                    .append(bits(value.radarReflectivity())).append('|')
                    .append(nullable(value.heavyImpactResponseSurfaceId())).append('|')
                    .append(nullable(value.constructionMaterialFamilyId())).append('|')
                    .append(nullable(value.repairMaterialFamilyId())).append('|');
            appendSorted(out, value.tags());
            out.append('\n');
        }
        for (HeavyImpactResponseSurfaceDefinition value : responseSurfaces) {
            CalibrationDomainDefinition d = value.calibrationDomain();
            out.append("response|").append(value.id()).append('|')
                    .append(bits(d.minImpactVelocityMps())).append('|')
                    .append(bits(d.maxImpactVelocityMps())).append('|')
                    .append(bits(d.minProjectileMassKg())).append('|')
                    .append(bits(d.maxProjectileMassKg())).append('|')
                    .append(d.confidenceLabel()).append('\n');
        }
        for (ProtectionStackDefinition value : protectionStacks) {
            out.append("protection|").append(value.id()).append('|').append(bits(value.mountMassKg())).append('|');
            for (ProtectionLayerDefinition layer : value.layers()) {
                out.append(layer.materialId()).append(',')
                        .append(bits(layer.thicknessM())).append(',')
                        .append(bits(layer.spacingAfterM())).append(',')
                        .append(bits(layer.orientationRad())).append(',')
                        .append(bits(layer.coverageFraction())).append(',')
                        .append(nullable(layer.responseSurfaceId())).append(';');
            }
            out.append('\n');
        }
        for (HullDefinition value : hulls) {
            out.append("hull|").append(value.id()).append('|').append(value.displayName()).append('|')
                    .append(value.architecture()).append('|');
            appendDimensions(out, value.boundingDimensionsM());
            out.append('|').append(bits(value.bareHullMassKg()))
                    .append('|').append(bits(value.internalVolumeM3()))
                    .append('|').append(value.crewBaseline())
                    .append('|').append(value.lifeSupportCapacity())
                    .append('|').append(bits(value.baseSignatureGeometryAreaM2()))
                    .append('|').append(value.structuralProtectionStackId())
                    .append('|').append(bits(value.maxOperationalMassKg())).append('|');
            List<SlotDefinition> slots = new ArrayList<>(value.slots());
            slots.sort(Comparator.comparing(SlotDefinition::id));
            for (SlotDefinition slot : slots) {
                out.append("S:").append(slot.id()).append(',').append(slot.category()).append(',');
                appendDimensions(out, slot.maxDimensionsM());
                out.append(',').append(bits(slot.maxMassKg())).append(';');
            }
            List<HardpointDefinition> hardpoints = new ArrayList<>(value.hardpoints());
            hardpoints.sort(Comparator.comparing(HardpointDefinition::id));
            for (HardpointDefinition h : hardpoints) {
                out.append("H:").append(h.id()).append(',').append(h.size()).append(',');
                appendVector(out, h.positionM());
                out.append(',').append(bits(h.arc().azimuthCenterRad())).append(',').append(bits(h.arc().halfArcRad())).append(',');
                appendDimensions(out, h.maxModuleDimensionsM());
                out.append(',').append(bits(h.maxModuleMassKg())).append(',').append(bits(h.maxRecoilImpulseNs())).append(',');
                appendEnumSorted(out, h.allowedModuleFamilies());
                out.append(';');
            }
            List<CompartmentDefinition> compartments = new ArrayList<>(value.compartments());
            compartments.sort(Comparator.comparing(CompartmentDefinition::id));
            for (CompartmentDefinition c : compartments) {
                out.append("C:").append(c.id()).append(',').append(bits(c.volumeM3())).append(',');
                appendVector(out, c.centerM());
                out.append(',').append(nullable(c.protectionStackId())).append(',');
                appendSorted(out, c.tags());
                out.append(';');
            }
            out.append("T:");
            appendEnumSorted(out, value.thrustMountCompatibility());
            out.append('\n');
        }
        for (ModuleDefinition value : modules) {
            out.append("module|").append(value.id()).append('|').append(value.displayName()).append('|').append(value.family()).append('|');
            appendEnumSorted(out, value.integrationCategories());
            out.append('|');
            appendEnumSorted(out, value.compatibleHardpointSizes());
            out.append('|');
            appendDimensions(out, value.physicalDimensionsM());
            out.append('|').append(bits(value.massKg()))
                    .append('|').append(bits(value.occupiedVolumeM3()))
                    .append('|').append(bits(value.requiredMountStrengthN()))
                    .append('|').append(bits(value.continuousPowerSupplyW()))
                    .append('|').append(bits(value.continuousPowerDemandW()))
                    .append('|').append(bits(value.peakPowerDemandW()))
                    .append('|').append(bits(value.storedEnergyCapacityJ()))
                    .append('|').append(bits(value.wasteHeatW()))
                    .append('|').append(bits(value.localThermalCapacityJ()))
                    .append('|').append(bits(value.coolantTransferDemandW()))
                    .append('|').append(bits(value.heatRejectionW()))
                    .append('|').append(value.crewRequirement())
                    .append('|').append(value.automationRequirement()).append('|');
            List<InterfaceDefinition> interfaces = new ArrayList<>(value.interfaces());
            interfaces.sort(Comparator.comparing(InterfaceDefinition::id));
            for (InterfaceDefinition i : interfaces) {
                out.append(i.kind()).append(',').append(i.id()).append(',').append(bits(i.capacity())).append(';');
            }
            out.append('|');
            appendSortedDoubleMap(out, value.signatureContributions());
            out.append('|');
            List<ConstructionInputDefinition> construction = new ArrayList<>(value.constructionInputs());
            construction.sort(Comparator.comparing(ConstructionInputDefinition::contentId));
            for (ConstructionInputDefinition input : construction) {
                out.append(input.contentId()).append('=').append(bits(input.amount())).append(';');
            }
            out.append('|').append(bits(value.maintenance().serviceIntervalSeconds()))
                    .append(',').append(bits(value.maintenance().maintenanceWorkSeconds()))
                    .append(',').append(bits(value.maintenance().repairComplexity())).append('|');
            appendSortedDoubleMap(out, value.capabilityParameters());
            out.append('\n');
        }
        for (DemonstratorFitDefinition value : demonstratorFits) {
            out.append("fit|").append(value.id()).append('|').append(value.hullId()).append('|');
            List<InstalledModuleDefinition> installed = new ArrayList<>(value.installedModules());
            installed.sort(Comparator.comparing(InstalledModuleDefinition::mountId)
                    .thenComparing(InstalledModuleDefinition::moduleId));
            for (InstalledModuleDefinition module : installed) {
                out.append(module.mountId()).append('=').append(module.moduleId()).append(';');
            }
            out.append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(out.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide required SHA-256", exception);
        }
    }

    private static MaterialDefinition copyMaterial(MaterialDefinition value) {
        return new MaterialDefinition(value.id(), value.densityKgPerM3(), List.copyOf(value.tags()),
                value.thermalConductivityWPerMK(), value.specificHeatJPerKgK(), value.emissivity(),
                value.radarReflectivity(), value.heavyImpactResponseSurfaceId(), value.constructionMaterialFamilyId(),
                value.repairMaterialFamilyId());
    }

    private static ProtectionStackDefinition copyProtectionStack(ProtectionStackDefinition value) {
        return new ProtectionStackDefinition(value.id(), value.mountMassKg(), List.copyOf(value.layers()));
    }

    private static HullDefinition copyHull(HullDefinition value) {
        List<HardpointDefinition> hardpoints = value.hardpoints().stream()
                .map(h -> new HardpointDefinition(h.id(), h.size(), h.positionM(), h.arc(), h.maxModuleDimensionsM(),
                        h.maxModuleMassKg(), h.maxRecoilImpulseNs(), List.copyOf(h.allowedModuleFamilies())))
                .toList();
        List<CompartmentDefinition> compartments = value.compartments().stream()
                .map(c -> new CompartmentDefinition(c.id(), c.volumeM3(), c.centerM(), c.protectionStackId(),
                        List.copyOf(c.tags())))
                .toList();
        return new HullDefinition(value.id(), value.displayName(), value.architecture(), value.boundingDimensionsM(),
                value.bareHullMassKg(), value.internalVolumeM3(), List.copyOf(value.slots()), hardpoints, compartments,
                value.crewBaseline(), value.lifeSupportCapacity(), value.baseSignatureGeometryAreaM2(),
                value.structuralProtectionStackId(), value.maxOperationalMassKg(),
                List.copyOf(value.thrustMountCompatibility()));
    }

    private static ModuleDefinition copyModule(ModuleDefinition value) {
        return new ModuleDefinition(value.id(), value.displayName(), value.family(),
                List.copyOf(value.integrationCategories()), List.copyOf(value.compatibleHardpointSizes()),
                value.physicalDimensionsM(), value.massKg(), value.occupiedVolumeM3(), value.requiredMountStrengthN(),
                value.continuousPowerSupplyW(), value.continuousPowerDemandW(), value.peakPowerDemandW(),
                value.storedEnergyCapacityJ(), value.wasteHeatW(), value.localThermalCapacityJ(),
                value.coolantTransferDemandW(), value.heatRejectionW(), value.crewRequirement(),
                value.automationRequirement(), List.copyOf(value.interfaces()),
                Collections.unmodifiableMap(new LinkedHashMap<>(value.signatureContributions())),
                List.copyOf(value.constructionInputs()), value.maintenance(),
                Collections.unmodifiableMap(new LinkedHashMap<>(value.capabilityParameters())));
    }

    private static DemonstratorFitDefinition copyFit(DemonstratorFitDefinition value) {
        return new DemonstratorFitDefinition(value.id(), value.hullId(), List.copyOf(value.installedModules()));
    }

    private static long bits(double value) { return Double.doubleToLongBits(value); }
    private static String nullable(String value) { return value == null ? "~" : value; }
    private static void appendDimensions(StringBuilder out, Dimensions3d v) {
        out.append(bits(v.lengthM())).append(',').append(bits(v.widthM())).append(',').append(bits(v.heightM()));
    }
    private static void appendVector(StringBuilder out, Vector3d v) {
        out.append(bits(v.xM())).append(',').append(bits(v.yM())).append(',').append(bits(v.zM()));
    }
    private static void appendSorted(StringBuilder out, List<String> values) {
        List<String> copy = new ArrayList<>(values);
        Collections.sort(copy);
        for (String value : copy) out.append(value).append(',');
    }
    private static <E extends Enum<E>> void appendEnumSorted(StringBuilder out, List<E> values) {
        appendSorted(out, values.stream().map(Enum::name).sorted().toList());
    }
    private static void appendSortedDoubleMap(StringBuilder out, Map<String, Double> values) {
        for (Map.Entry<String, Double> entry : new TreeMap<>(values).entrySet()) {
            out.append(entry.getKey()).append('=').append(bits(entry.getValue())).append(';');
        }
    }
    private static <T> List<T> sortedCopy(List<T> source, Comparator<? super T> comparator) {
        ArrayList<T> copy = new ArrayList<>(source);
        copy.sort(comparator);
        return List.copyOf(copy);
    }
    private static <T> Map<String, T> index(List<T> values, java.util.function.Function<T, String> idFunction) {
        LinkedHashMap<String, T> result = new LinkedHashMap<>();
        for (T value : values) result.put(idFunction.apply(value), value);
        return Collections.unmodifiableMap(result);
    }

    /** Broad physical hull construction architecture; names never grant bonuses. */
    public enum HullArchitecture {
        /** Monocoque shell architecture. */ MONOCOQUE,
        /** Internal load-bearing frame architecture. */ FRAME,
        /** Open truss architecture. */ TRUSS,
        /** Mixed structural architecture. */ HYBRID
    }

    /** Shared integration zones used by hull slots and module compatibility. */
    public enum IntegrationCategory {
        /** Primary ship-system integration zone. */ CORE,
        /** Weapon-system integration zone. */ WEAPON,
        /** General utility integration zone. */ UTILITY,
        /** Internal equipment integration zone. */ INTERNAL,
        /** Mission-specific integration zone. */ MISSION
    }

    /** Geometric hardpoint size class used only as a fit constraint. */
    public enum HardpointSize {
        /** Small hardpoint. */ SMALL,
        /** Medium hardpoint. */ MEDIUM,
        /** Large hardpoint. */ LARGE,
        /** Extra-large hardpoint. */ EXTRA_LARGE
    }

    /** Common Stage-17.5 module families. */
    public enum ModuleFamily {
        /** Electrical generation. */ REACTOR_POWER,
        /** Stored electrical energy. */ ENERGY_STORAGE,
        /** Main propulsion. */ MAIN_DRIVE,
        /** Maneuvering propulsion. */ MANEUVER_THRUSTERS,
        /** Inter-system translation hardware. */ FTL_JUMP,
        /** Heat transport, storage and rejection. */ THERMAL_CONTROL,
        /** Sensors, electronic warfare and fire control. */ SENSOR_EW_FIRE_CONTROL,
        /** Communications and datalink. */ COMMUNICATION_DATALINK,
        /** Energetic field protection. */ SHIELD_FIELD,
        /** Passive material protection. */ ARMOR_PROTECTION,
        /** Weapons, launchers and ammunition handling. */ WEAPON_AMMUNITION,
        /** Crew support and automation. */ CREW_LIFE_SUPPORT_AUTOMATION,
        /** Cargo, tanks and mission stores. */ CARGO_TANK_STORES,
        /** Hangars and embarked craft support. */ HANGAR_SMALL_CRAFT,
        /** Industrial, mining, salvage, repair and science equipment. */ MINING_SALVAGE_REPAIR_INDUSTRIAL_SCIENCE
    }

    /** Physical consumable interface category. */
    public enum InterfaceKind {
        /** Ammunition feed or storage interface. */ AMMUNITION,
        /** Generic physical consumable interface. */ CONSUMABLE,
        /** Reaction-mass feed or storage interface. */ REACTION_MASS
    }

    /**
     * Three-dimensional physical envelope.
     * @param lengthM length in meters
     * @param widthM width in meters
     * @param heightM height in meters
     */
    public record Dimensions3d(double lengthM, double widthM, double heightM) { }

    /**
     * Hull-local or world-local vector expressed in meters.
     * @param xM x component in meters
     * @param yM y component in meters
     * @param zM z component in meters
     */
    public record Vector3d(double xM, double yM, double zM) { }

    /**
     * Angular traverse or coverage arc.
     * @param azimuthCenterRad center azimuth in radians
     * @param halfArcRad half-width in radians
     */
    public record ArcDefinition(double azimuthCenterRad, double halfArcRad) { }

    /**
     * Explicit bounded calibration domain for heavy-impact response lookup.
     * @param minImpactVelocityMps minimum calibrated impact speed
     * @param maxImpactVelocityMps maximum calibrated impact speed
     * @param minProjectileMassKg minimum calibrated projectile mass
     * @param maxProjectileMassKg maximum calibrated projectile mass
     * @param confidenceLabel authored calibration/provenance label
     */
    public record CalibrationDomainDefinition(
            double minImpactVelocityMps, double maxImpactVelocityMps,
            double minProjectileMassKg, double maxProjectileMassKg, String confidenceLabel) { }

    /**
     * Engineering material definition.
     * @param id stable content ID
     * @param densityKgPerM3 material density
     * @param tags authored material-role tags
     * @param thermalConductivityWPerMK thermal conductivity
     * @param specificHeatJPerKgK specific heat
     * @param emissivity thermal emissivity
     * @param radarReflectivity authored radar-reflection coefficient
     * @param heavyImpactResponseSurfaceId optional bounded response-surface ID
     * @param constructionMaterialFamilyId Stage-18 construction-family seam
     * @param repairMaterialFamilyId Stage-18 repair-family seam
     */
    public record MaterialDefinition(
            String id, double densityKgPerM3, List<String> tags,
            double thermalConductivityWPerMK, double specificHeatJPerKgK,
            double emissivity, double radarReflectivity,
            String heavyImpactResponseSurfaceId, String constructionMaterialFamilyId,
            String repairMaterialFamilyId) { }

    /**
     * Named heavy-impact response surface with an explicit valid domain.
     * @param id stable content ID
     * @param calibrationDomain bounded calibration domain
     */
    public record HeavyImpactResponseSurfaceDefinition(String id, CalibrationDomainDefinition calibrationDomain) { }

    /**
     * One ordered layer in a physical protection stack.
     * @param materialId material content ID
     * @param thicknessM layer thickness
     * @param spacingAfterM spacing after the layer
     * @param orientationRad layer orientation
     * @param coverageFraction covered fraction
     * @param responseSurfaceId optional layer-specific response surface
     */
    public record ProtectionLayerDefinition(
            String materialId, double thicknessM, double spacingAfterM,
            double orientationRad, double coverageFraction, String responseSurfaceId) { }

    /**
     * Ordered physical protection stack.
     * @param id stable content ID
     * @param mountMassKg non-layer structural/mounting mass
     * @param layers ordered outside-to-inside layers
     */
    public record ProtectionStackDefinition(String id, double mountMassKg, List<ProtectionLayerDefinition> layers) { }

    /**
     * Internal or integration slot envelope.
     * @param id hull-local stable mount ID
     * @param category integration category
     * @param maxDimensionsM maximum module dimensions
     * @param maxMassKg maximum supported module mass
     */
    public record SlotDefinition(String id, IntegrationCategory category, Dimensions3d maxDimensionsM, double maxMassKg) { }

    /**
     * External hardpoint envelope and geometry.
     * @param id hull-local stable mount ID
     * @param size hardpoint size class
     * @param positionM hull-local position
     * @param arc traverse/coverage arc
     * @param maxModuleDimensionsM maximum module dimensions
     * @param maxModuleMassKg maximum supported module mass
     * @param maxRecoilImpulseNs maximum supported recoil impulse
     * @param allowedModuleFamilies allowed module families
     */
    public record HardpointDefinition(
            String id, HardpointSize size, Vector3d positionM, ArcDefinition arc,
            Dimensions3d maxModuleDimensionsM, double maxModuleMassKg, double maxRecoilImpulseNs,
            List<ModuleFamily> allowedModuleFamilies) { }

    /**
     * Spatial compartment in the future subsystem-damage topology.
     * @param id hull-local stable compartment ID
     * @param volumeM3 compartment volume
     * @param centerM hull-local center
     * @param protectionStackId optional local protection stack
     * @param tags authored subsystem or mission tags
     */
    public record CompartmentDefinition(
            String id, double volumeM3, Vector3d centerM, String protectionStackId, List<String> tags) { }

    /**
     * Physical hull definition before fitted modules and consumables.
     * @param id stable content ID
     * @param displayName display name
     * @param architecture physical architecture label without hidden bonuses
     * @param boundingDimensionsM hull bounding dimensions
     * @param bareHullMassKg structural bare-hull mass
     * @param internalVolumeM3 integration volume
     * @param slots internal/integration slots
     * @param hardpoints external hardpoints
     * @param compartments compartment topology
     * @param crewBaseline baseline crew requirement
     * @param lifeSupportCapacity supported crew capacity
     * @param baseSignatureGeometryAreaM2 reference projected signature area
     * @param structuralProtectionStackId structural protection-stack ID
     * @param maxOperationalMassKg structural operating-mass limit
     * @param thrustMountCompatibility supported propulsion families
     */
    public record HullDefinition(
            String id, String displayName, HullArchitecture architecture, Dimensions3d boundingDimensionsM,
            double bareHullMassKg, double internalVolumeM3, List<SlotDefinition> slots,
            List<HardpointDefinition> hardpoints, List<CompartmentDefinition> compartments,
            int crewBaseline, int lifeSupportCapacity, double baseSignatureGeometryAreaM2,
            String structuralProtectionStackId, double maxOperationalMassKg,
            List<ModuleFamily> thrustMountCompatibility) { }

    /**
     * Physical ammunition, consumable or reaction-mass interface.
     * @param kind interface category
     * @param id module-local stable interface ID
     * @param capacity interface-specific physical capacity
     */
    public record InterfaceDefinition(InterfaceKind kind, String id, double capacity) { }

    /**
     * Stage-18 construction/material input seam.
     * @param contentId material or component content ID
     * @param amount positive physical/catalog amount
     */
    public record ConstructionInputDefinition(String contentId, double amount) { }

    /**
     * Maintenance and repair metadata.
     * @param serviceIntervalSeconds nominal service interval
     * @param maintenanceWorkSeconds nominal maintenance work
     * @param repairComplexity non-negative authored repair complexity
     */
    public record MaintenanceDefinition(double serviceIntervalSeconds, double maintenanceWorkSeconds, double repairComplexity) { }

    /**
     * Shared production module definition.
     * @param id stable content ID
     * @param displayName display name
     * @param family common physical module family
     * @param integrationCategories compatible integration categories
     * @param compatibleHardpointSizes compatible external hardpoint sizes
     * @param physicalDimensionsM module dimensions
     * @param massKg dry module mass
     * @param occupiedVolumeM3 occupied integration volume
     * @param requiredMountStrengthN required structural mounting strength
     * @param continuousPowerSupplyW continuous supplied electrical power
     * @param continuousPowerDemandW continuous consumed electrical power
     * @param peakPowerDemandW peak consumed electrical power
     * @param storedEnergyCapacityJ local stored energy
     * @param wasteHeatW waste heat generation
     * @param localThermalCapacityJ local thermal capacity
     * @param coolantTransferDemandW coolant-transfer demand
     * @param heatRejectionW direct heat-rejection capability
     * @param crewRequirement required crew
     * @param automationRequirement required automation capacity
     * @param interfaces physical consumable interfaces
     * @param signatureContributions channel-specific signature authoring values
     * @param constructionInputs Stage-18 construction/component seams
     * @param maintenance maintenance and repair metadata
     * @param capabilityParameters family-specific physical authoring parameters
     */
    public record ModuleDefinition(
            String id, String displayName, ModuleFamily family,
            List<IntegrationCategory> integrationCategories, List<HardpointSize> compatibleHardpointSizes,
            Dimensions3d physicalDimensionsM, double massKg, double occupiedVolumeM3,
            double requiredMountStrengthN, double continuousPowerSupplyW, double continuousPowerDemandW,
            double peakPowerDemandW, double storedEnergyCapacityJ, double wasteHeatW,
            double localThermalCapacityJ, double coolantTransferDemandW, double heatRejectionW,
            int crewRequirement, int automationRequirement, List<InterfaceDefinition> interfaces,
            Map<String, Double> signatureContributions, List<ConstructionInputDefinition> constructionInputs,
            MaintenanceDefinition maintenance, Map<String, Double> capabilityParameters) { }

    /**
     * One module-to-mount assignment in a schema demonstrator.
     * @param mountId hull-local slot or hardpoint ID
     * @param moduleId module content ID
     */
    public record InstalledModuleDefinition(String mountId, String moduleId) { }

    /**
     * Machine-readable schema demonstrator, not yet an authoritative runtime ship instance.
     * @param id stable fit ID
     * @param hullId referenced hull ID
     * @param installedModules module-to-mount assignments
     */
    public record DemonstratorFitDefinition(String id, String hullId, List<InstalledModuleDefinition> installedModules) { }
}
