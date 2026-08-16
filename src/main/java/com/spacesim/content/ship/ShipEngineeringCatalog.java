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
import java.util.Objects;
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
        this.materials = sortedCopy(materials, Comparator.comparing(MaterialDefinition::id));
        this.responseSurfaces = sortedCopy(responseSurfaces, Comparator.comparing(HeavyImpactResponseSurfaceDefinition::id));
        this.protectionStacks = sortedCopy(protectionStacks, Comparator.comparing(ProtectionStackDefinition::id));
        this.hulls = sortedCopy(hulls, Comparator.comparing(HullDefinition::id));
        this.modules = sortedCopy(modules, Comparator.comparing(ModuleDefinition::id));
        this.demonstratorFits = sortedCopy(demonstratorFits, Comparator.comparing(DemonstratorFitDefinition::id));
        this.materialsById = index(this.materials, MaterialDefinition::id);
        this.responseSurfacesById = index(this.responseSurfaces, HeavyImpactResponseSurfaceDefinition::id);
        this.protectionStacksById = index(this.protectionStacks, ProtectionStackDefinition::id);
        this.hullsById = index(this.hulls, HullDefinition::id);
        this.modulesById = index(this.modules, ModuleDefinition::id);
        this.demonstratorFitsById = index(this.demonstratorFits, DemonstratorFitDefinition::id);
        this.fingerprint = computeFingerprint();
    }

    /** @return supported engineering schema version */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** @return explicit migration contract version */
    public int getMigrationVersion() {
        return migrationVersion;
    }

    /** @return deterministic material definitions */
    public List<MaterialDefinition> getMaterials() {
        return materials;
    }

    /** @return deterministic bounded heavy-impact response definitions */
    public List<HeavyImpactResponseSurfaceDefinition> getResponseSurfaces() {
        return responseSurfaces;
    }

    /** @return deterministic protection stacks */
    public List<ProtectionStackDefinition> getProtectionStacks() {
        return protectionStacks;
    }

    /** @return deterministic hull definitions */
    public List<HullDefinition> getHulls() {
        return hulls;
    }

    /** @return deterministic module definitions */
    public List<ModuleDefinition> getModules() {
        return modules;
    }

    /** @return machine-readable reference fits used for schema acceptance */
    public List<DemonstratorFitDefinition> getDemonstratorFits() {
        return demonstratorFits;
    }

    /** @return lowercase SHA-256 semantic fingerprint */
    public String getFingerprint() {
        return fingerprint;
    }

    /** @param id material content ID @return material or {@code null} */
    public MaterialDefinition findMaterial(String id) {
        return materialsById.get(id);
    }

    /** @param id response-surface content ID @return response surface or {@code null} */
    public HeavyImpactResponseSurfaceDefinition findResponseSurface(String id) {
        return responseSurfacesById.get(id);
    }

    /** @param id protection-stack content ID @return stack or {@code null} */
    public ProtectionStackDefinition findProtectionStack(String id) {
        return protectionStacksById.get(id);
    }

    /** @param id hull content ID @return hull or {@code null} */
    public HullDefinition findHull(String id) {
        return hullsById.get(id);
    }

    /** @param id module content ID @return module or {@code null} */
    public ModuleDefinition findModule(String id) {
        return modulesById.get(id);
    }

    /** @param id fit content ID @return demonstrator fit or {@code null} */
    public DemonstratorFitDefinition findDemonstratorFit(String id) {
        return demonstratorFitsById.get(id);
    }

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
            CalibrationDomainDefinition domain = value.calibrationDomain();
            out.append("response|").append(value.id()).append('|')
                    .append(bits(domain.minImpactVelocityMps())).append('|')
                    .append(bits(domain.maxImpactVelocityMps())).append('|')
                    .append(bits(domain.minProjectileMassKg())).append('|')
                    .append(bits(domain.maxProjectileMassKg())).append('|')
                    .append(domain.confidenceLabel()).append('\n');
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
                    .append('|').append(bits(value.maxOperationalMassKg()))
                    .append('|');
            List<SlotDefinition> slots = new ArrayList<>(value.slots());
            slots.sort(Comparator.comparing(SlotDefinition::id));
            for (SlotDefinition slot : slots) {
                out.append("S:").append(slot.id()).append(',').append(slot.category()).append(',');
                appendDimensions(out, slot.maxDimensionsM());
                out.append(',').append(bits(slot.maxMassKg())).append(';');
            }
            List<HardpointDefinition> hardpoints = new ArrayList<>(value.hardpoints());
            hardpoints.sort(Comparator.comparing(HardpointDefinition::id));
            for (HardpointDefinition hardpoint : hardpoints) {
                out.append("H:").append(hardpoint.id()).append(',').append(hardpoint.size()).append(',');
                appendVector(out, hardpoint.positionM());
                out.append(',').append(bits(hardpoint.arc().azimuthCenterRad()))
                        .append(',').append(bits(hardpoint.arc().halfArcRad())).append(',');
                appendDimensions(out, hardpoint.maxModuleDimensionsM());
                out.append(',').append(bits(hardpoint.maxModuleMassKg()))
                        .append(',').append(bits(hardpoint.maxRecoilImpulseNs())).append(',');
                appendEnumSorted(out, hardpoint.allowedModuleFamilies());
                out.append(';');
            }
            List<CompartmentDefinition> compartments = new ArrayList<>(value.compartments());
            compartments.sort(Comparator.comparing(CompartmentDefinition::id));
            for (CompartmentDefinition compartment : compartments) {
                out.append("C:").append(compartment.id()).append(',').append(bits(compartment.volumeM3())).append(',');
                appendVector(out, compartment.centerM());
                out.append(',').append(nullable(compartment.protectionStackId())).append(',');
                appendSorted(out, compartment.tags());
                out.append(';');
            }
            out.append("T:");
            appendEnumSorted(out, value.thrustMountCompatibility());
            out.append('\n');
        }
        for (ModuleDefinition value : modules) {
            out.append("module|").append(value.id()).append('|').append(value.displayName()).append('|')
                    .append(value.family()).append('|');
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
            for (InterfaceDefinition item : interfaces) {
                out.append(item.kind()).append(',').append(item.id()).append(',').append(bits(item.capacity())).append(';');
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

    private static long bits(double value) {
        return Double.doubleToLongBits(value);
    }

    private static String nullable(String value) {
        return value == null ? "~" : value;
    }

    private static void appendDimensions(StringBuilder out, Dimensions3d value) {
        out.append(bits(value.lengthM())).append(',').append(bits(value.widthM())).append(',').append(bits(value.heightM()));
    }

    private static void appendVector(StringBuilder out, Vector3d value) {
        out.append(bits(value.xM())).append(',').append(bits(value.yM())).append(',').append(bits(value.zM()));
    }

    private static void appendSorted(StringBuilder out, List<String> values) {
        List<String> copy = new ArrayList<>(values);
        Collections.sort(copy);
        for (String value : copy) {
            out.append(value).append(',');
        }
    }

    private static <E extends Enum<E>> void appendEnumSorted(StringBuilder out, List<E> values) {
        List<String> names = values.stream().map(Enum::name).sorted().toList();
        appendSorted(out, names);
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
        for (T value : values) {
            result.put(idFunction.apply(value), value);
        }
        return Collections.unmodifiableMap(result);
    }

    /** Broad physical hull construction architecture; it never grants a performance bonus by name. */
    public enum HullArchitecture { MONOCOQUE, FRAME, TRUSS, HYBRID }

    /** Shared integration zones used by hull slots and module compatibility. */
    public enum IntegrationCategory { CORE, WEAPON, UTILITY, INTERNAL, MISSION }

    /** Geometric hardpoint size class used only as a fit constraint. */
    public enum HardpointSize { SMALL, MEDIUM, LARGE, EXTRA_LARGE }

    /** Common Stage-17.5 module families. */
    public enum ModuleFamily {
        REACTOR_POWER,
        ENERGY_STORAGE,
        MAIN_DRIVE,
        MANEUVER_THRUSTERS,
        FTL_JUMP,
        THERMAL_CONTROL,
        SENSOR_EW_FIRE_CONTROL,
        COMMUNICATION_DATALINK,
        SHIELD_FIELD,
        ARMOR_PROTECTION,
        WEAPON_AMMUNITION,
        CREW_LIFE_SUPPORT_AUTOMATION,
        CARGO_TANK_STORES,
        HANGAR_SMALL_CRAFT,
        MINING_SALVAGE_REPAIR_INDUSTRIAL_SCIENCE
    }

    /** Physical consumable interface category. */
    public enum InterfaceKind { AMMUNITION, CONSUMABLE, REACTION_MASS }

    /**
     * @param lengthM longitudinal dimension in meters
     * @param widthM transverse dimension in meters
     * @param heightM vertical dimension in meters
     */
    public record Dimensions3d(double lengthM, double widthM, double heightM) { }

    /**
     * @param xM x coordinate in meters
     * @param yM y coordinate in meters
     * @param zM z coordinate in meters
     */
    public record Vector3d(double xM, double yM, double zM) { }

    /**
     * @param azimuthCenterRad mount center direction in radians
     * @param halfArcRad half-width of allowed traverse arc in radians
     */
    public record ArcDefinition(double azimuthCenterRad, double halfArcRad) { }

    /**
     * @param minImpactVelocityMps lower calibrated impact speed
     * @param maxImpactVelocityMps upper calibrated impact speed
     * @param minProjectileMassKg lower calibrated projectile mass
     * @param maxProjectileMassKg upper calibrated projectile mass
     * @param confidenceLabel authored confidence/provenance label
     */
    public record CalibrationDomainDefinition(
            double minImpactVelocityMps,
            double maxImpactVelocityMps,
            double minProjectileMassKg,
            double maxProjectileMassKg,
            String confidenceLabel) {
        /** Validates immutable string ownership; numeric bounds are checked by the loader. */
        public CalibrationDomainDefinition {
            Objects.requireNonNull(confidenceLabel, "confidenceLabel");
        }
    }

    /**
     * @param id stable material content ID
     * @param densityKgPerM3 density
     * @param tags authored material roles
     * @param thermalConductivityWPerMK thermal conductivity
     * @param specificHeatJPerKgK specific heat
     * @param emissivity thermal emissivity [0,1]
     * @param radarReflectivity authored radar reflection coefficient [0,1]
     * @param heavyImpactResponseSurfaceId optional bounded heavy-impact surface
     * @param constructionMaterialFamilyId Stage-18 construction material-family seam
     * @param repairMaterialFamilyId Stage-18 repair material-family seam
     */
    public record MaterialDefinition(
            String id,
            double densityKgPerM3,
            List<String> tags,
            double thermalConductivityWPerMK,
            double specificHeatJPerKgK,
            double emissivity,
            double radarReflectivity,
            String heavyImpactResponseSurfaceId,
            String constructionMaterialFamilyId,
            String repairMaterialFamilyId) {
        /** Copies mutable collections defensively. */
        public MaterialDefinition {
            Objects.requireNonNull(id, "id");
            tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
        }
    }

    /**
     * @param id stable response-surface ID
     * @param calibrationDomain explicit domain where lookup is permitted
     */
    public record HeavyImpactResponseSurfaceDefinition(String id, CalibrationDomainDefinition calibrationDomain) {
        /** Validates immutable references. */
        public HeavyImpactResponseSurfaceDefinition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(calibrationDomain, "calibrationDomain");
        }
    }

    /**
     * @param materialId material reference
     * @param thicknessM physical thickness
     * @param spacingAfterM spacing before the next layer
     * @param orientationRad authored layer orientation
     * @param coverageFraction covered fraction [0,1]
     * @param responseSurfaceId optional layer-specific heavy-impact response surface
     */
    public record ProtectionLayerDefinition(
            String materialId,
            double thicknessM,
            double spacingAfterM,
            double orientationRad,
            double coverageFraction,
            String responseSurfaceId) {
        /** Validates immutable references. */
        public ProtectionLayerDefinition {
            Objects.requireNonNull(materialId, "materialId");
        }
    }

    /**
     * @param id stable protection-stack ID
     * @param mountMassKg non-layer mounting/spacing structural mass
     * @param layers ordered physical layers from outside to inside
     */
    public record ProtectionStackDefinition(String id, double mountMassKg, List<ProtectionLayerDefinition> layers) {
        /** Copies mutable collections defensively. */
        public ProtectionStackDefinition {
            Objects.requireNonNull(id, "id");
            layers = List.copyOf(Objects.requireNonNull(layers, "layers"));
        }
    }

    /**
     * @param id stable mount ID within one hull
     * @param category integration zone
     * @param maxDimensionsM dimensional envelope
     * @param maxMassKg supported module mass
     */
    public record SlotDefinition(String id, IntegrationCategory category, Dimensions3d maxDimensionsM, double maxMassKg) {
        /** Validates immutable references. */
        public SlotDefinition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(maxDimensionsM, "maxDimensionsM");
        }
    }

    /**
     * @param id stable mount ID within one hull
     * @param size hardpoint size class
     * @param positionM mount position in hull-local meters
     * @param arc traverse/coverage arc
     * @param maxModuleDimensionsM dimensional envelope
     * @param maxModuleMassKg supported module mass
     * @param maxRecoilImpulseNs supported recoil impulse
     * @param allowedModuleFamilies explicit family compatibility
     */
    public record HardpointDefinition(
            String id,
            HardpointSize size,
            Vector3d positionM,
            ArcDefinition arc,
            Dimensions3d maxModuleDimensionsM,
            double maxModuleMassKg,
            double maxRecoilImpulseNs,
            List<ModuleFamily> allowedModuleFamilies) {
        /** Copies mutable collections defensively. */
        public HardpointDefinition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(size, "size");
            Objects.requireNonNull(positionM, "positionM");
            Objects.requireNonNull(arc, "arc");
            Objects.requireNonNull(maxModuleDimensionsM, "maxModuleDimensionsM");
            allowedModuleFamilies = List.copyOf(Objects.requireNonNull(allowedModuleFamilies, "allowedModuleFamilies"));
        }
    }

    /**
     * @param id stable compartment ID within one hull
     * @param volumeM3 physical compartment volume
     * @param centerM hull-local center coordinate
     * @param protectionStackId optional local protection stack
     * @param tags authored subsystem/mission tags
     */
    public record CompartmentDefinition(
            String id, double volumeM3, Vector3d centerM, String protectionStackId, List<String> tags) {
        /** Copies mutable collections defensively. */
        public CompartmentDefinition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(centerM, "centerM");
            tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
        }
    }

    /**
     * @param id stable hull content ID
     * @param displayName display name
     * @param architecture physical architecture label without hidden bonuses
     * @param boundingDimensionsM bounding/collision geometry seam
     * @param bareHullMassKg structural mass before modules/consumables
     * @param internalVolumeM3 integration volume
     * @param slots internal/integration slots
     * @param hardpoints external hardpoints
     * @param compartments spatial damage topology seam
     * @param crewBaseline baseline crew requirement
     * @param lifeSupportCapacity supported crew capacity
     * @param baseSignatureGeometryAreaM2 reference projected signature geometry
     * @param structuralProtectionStackId structural protection stack
     * @param maxOperationalMassKg structural operating-mass limit
     * @param thrustMountCompatibility allowed drive families for thrust mounts
     */
    public record HullDefinition(
            String id,
            String displayName,
            HullArchitecture architecture,
            Dimensions3d boundingDimensionsM,
            double bareHullMassKg,
            double internalVolumeM3,
            List<SlotDefinition> slots,
            List<HardpointDefinition> hardpoints,
            List<CompartmentDefinition> compartments,
            int crewBaseline,
            int lifeSupportCapacity,
            double baseSignatureGeometryAreaM2,
            String structuralProtectionStackId,
            double maxOperationalMassKg,
            List<ModuleFamily> thrustMountCompatibility) {
        /** Copies mutable collections defensively. */
        public HullDefinition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(architecture, "architecture");
            Objects.requireNonNull(boundingDimensionsM, "boundingDimensionsM");
            slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
            hardpoints = List.copyOf(Objects.requireNonNull(hardpoints, "hardpoints"));
            compartments = List.copyOf(Objects.requireNonNull(compartments, "compartments"));
            Objects.requireNonNull(structuralProtectionStackId, "structuralProtectionStackId");
            thrustMountCompatibility = List.copyOf(Objects.requireNonNull(thrustMountCompatibility, "thrustMountCompatibility"));
        }
    }

    /**
     * @param kind interface category
     * @param id stable interface ID local to a module
     * @param capacity physical capacity in the interface-specific SI quantity
     */
    public record InterfaceDefinition(InterfaceKind kind, String id, double capacity) {
        /** Validates immutable references. */
        public InterfaceDefinition {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(id, "id");
        }
    }

    /**
     * @param contentId Stage-18 material/component content seam
     * @param amount positive physical/catalog amount
     */
    public record ConstructionInputDefinition(String contentId, double amount) {
        /** Validates immutable references. */
        public ConstructionInputDefinition {
            Objects.requireNonNull(contentId, "contentId");
        }
    }

    /**
     * @param serviceIntervalSeconds nominal service interval
     * @param maintenanceWorkSeconds nominal maintenance work
     * @param repairComplexity dimensionless authored complexity scalar, non-negative
     */
    public record MaintenanceDefinition(
            double serviceIntervalSeconds, double maintenanceWorkSeconds, double repairComplexity) { }

    /**
     * @param id stable module content ID
     * @param displayName display name
     * @param family common physical module family
     * @param integrationCategories compatible slot categories
     * @param compatibleHardpointSizes compatible external hardpoint sizes; empty for slot-only modules
     * @param physicalDimensionsM module dimensions
     * @param massKg dry module mass
     * @param occupiedVolumeM3 occupied integration volume
     * @param requiredMountStrengthN required structural mounting strength
     * @param continuousPowerSupplyW continuous supplied electrical power
     * @param continuousPowerDemandW continuous consumed electrical power
     * @param peakPowerDemandW peak consumed electrical power
     * @param storedEnergyCapacityJ local stored energy
     * @param wasteHeatW waste-heat generation
     * @param localThermalCapacityJ local thermal capacity
     * @param coolantTransferDemandW coolant transfer demand
     * @param heatRejectionW direct heat rejection capability
     * @param crewRequirement required crew
     * @param automationRequirement required automation capacity
     * @param interfaces ammunition/consumable/reaction-mass interfaces
     * @param signatureContributions channel-specific signature authoring values
     * @param constructionInputs Stage-18 construction/component seams
     * @param maintenance maintenance/repair metadata
     * @param capabilityParameters family-specific physical authoring payload
     */
    public record ModuleDefinition(
            String id,
            String displayName,
            ModuleFamily family,
            List<IntegrationCategory> integrationCategories,
            List<HardpointSize> compatibleHardpointSizes,
            Dimensions3d physicalDimensionsM,
            double massKg,
            double occupiedVolumeM3,
            double requiredMountStrengthN,
            double continuousPowerSupplyW,
            double continuousPowerDemandW,
            double peakPowerDemandW,
            double storedEnergyCapacityJ,
            double wasteHeatW,
            double localThermalCapacityJ,
            double coolantTransferDemandW,
            double heatRejectionW,
            int crewRequirement,
            int automationRequirement,
            List<InterfaceDefinition> interfaces,
            Map<String, Double> signatureContributions,
            List<ConstructionInputDefinition> constructionInputs,
            MaintenanceDefinition maintenance,
            Map<String, Double> capabilityParameters) {
        /** Copies mutable collections/maps defensively. */
        public ModuleDefinition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(family, "family");
            integrationCategories = List.copyOf(Objects.requireNonNull(integrationCategories, "integrationCategories"));
            compatibleHardpointSizes = List.copyOf(Objects.requireNonNull(compatibleHardpointSizes, "compatibleHardpointSizes"));
            Objects.requireNonNull(physicalDimensionsM, "physicalDimensionsM");
            interfaces = List.copyOf(Objects.requireNonNull(interfaces, "interfaces"));
            signatureContributions = immutableMap(signatureContributions);
            constructionInputs = List.copyOf(Objects.requireNonNull(constructionInputs, "constructionInputs"));
            Objects.requireNonNull(maintenance, "maintenance");
            capabilityParameters = immutableMap(capabilityParameters);
        }
    }

    /**
     * @param mountId hull-local slot or hardpoint ID
     * @param moduleId installed module content ID
     */
    public record InstalledModuleDefinition(String mountId, String moduleId) {
        /** Validates immutable references. */
        public InstalledModuleDefinition {
            Objects.requireNonNull(mountId, "mountId");
            Objects.requireNonNull(moduleId, "moduleId");
        }
    }

    /**
     * Machine-readable schema demonstrator; it is not yet an authoritative runtime ship instance.
     *
     * @param id stable fit ID
     * @param hullId referenced hull
     * @param installedModules module-to-mount assignments
     */
    public record DemonstratorFitDefinition(
            String id, String hullId, List<InstalledModuleDefinition> installedModules) {
        /** Copies mutable collections defensively. */
        public DemonstratorFitDefinition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(hullId, "hullId");
            installedModules = List.copyOf(Objects.requireNonNull(installedModules, "installedModules"));
        }
    }

    private static Map<String, Double> immutableMap(Map<String, Double> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(source, "map")));
    }
}