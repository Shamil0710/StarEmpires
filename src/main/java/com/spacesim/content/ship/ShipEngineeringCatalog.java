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
 *
 * <p>The nested records are immutable content DTOs. Their component names and SI suffixes are the
 * public schema documentation; compact constructors only make defensive collection copies. Missing
 * member prose is therefore intentionally suppressed while all other doclint categories stay active.</p>
 */
@SuppressWarnings("doclint:missing")
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

    /** @param id material content ID @return material or {@code null} */
    public MaterialDefinition findMaterial(String id) { return materialsById.get(id); }

    /** @param id response-surface content ID @return response surface or {@code null} */
    public HeavyImpactResponseSurfaceDefinition findResponseSurface(String id) { return responseSurfacesById.get(id); }

    /** @param id protection-stack content ID @return stack or {@code null} */
    public ProtectionStackDefinition findProtectionStack(String id) { return protectionStacksById.get(id); }

    /** @param id hull content ID @return hull or {@code null} */
    public HullDefinition findHull(String id) { return hullsById.get(id); }

    /** @param id module content ID @return module or {@code null} */
    public ModuleDefinition findModule(String id) { return modulesById.get(id); }

    /** @param id fit content ID @return demonstrator fit or {@code null} */
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
    private static Map<String, Double> immutableMap(Map<String, Double> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(source, "map")));
    }

    /** Broad physical hull construction architecture; names never grant bonuses. */
    public enum HullArchitecture { MONOCOQUE, FRAME, TRUSS, HYBRID }
    /** Shared integration zones used by hull slots and module compatibility. */
    public enum IntegrationCategory { CORE, WEAPON, UTILITY, INTERNAL, MISSION }
    /** Geometric hardpoint size class used only as a fit constraint. */
    public enum HardpointSize { SMALL, MEDIUM, LARGE, EXTRA_LARGE }
    /** Common Stage-17.5 module families. */
    public enum ModuleFamily {
        REACTOR_POWER, ENERGY_STORAGE, MAIN_DRIVE, MANEUVER_THRUSTERS, FTL_JUMP,
        THERMAL_CONTROL, SENSOR_EW_FIRE_CONTROL, COMMUNICATION_DATALINK, SHIELD_FIELD,
        ARMOR_PROTECTION, WEAPON_AMMUNITION, CREW_LIFE_SUPPORT_AUTOMATION,
        CARGO_TANK_STORES, HANGAR_SMALL_CRAFT, MINING_SALVAGE_REPAIR_INDUSTRIAL_SCIENCE
    }
    /** Physical consumable interface category. */
    public enum InterfaceKind { AMMUNITION, CONSUMABLE, REACTION_MASS }

    public record Dimensions3d(double lengthM, double widthM, double heightM) { }
    public record Vector3d(double xM, double yM, double zM) { }
    public record ArcDefinition(double azimuthCenterRad, double halfArcRad) { }
    public record CalibrationDomainDefinition(
            double minImpactVelocityMps, double maxImpactVelocityMps,
            double minProjectileMassKg, double maxProjectileMassKg, String confidenceLabel) {
        public CalibrationDomainDefinition { Objects.requireNonNull(confidenceLabel, "confidenceLabel"); }
    }
    public record MaterialDefinition(
            String id, double densityKgPerM3, List<String> tags,
            double thermalConductivityWPerMK, double specificHeatJPerKgK,
            double emissivity, double radarReflectivity,
            String heavyImpactResponseSurfaceId, String constructionMaterialFamilyId,
            String repairMaterialFamilyId) {
        public MaterialDefinition {
            Objects.requireNonNull(id, "id");
            tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
        }
    }
    public record HeavyImpactResponseSurfaceDefinition(String id, CalibrationDomainDefinition calibrationDomain) {
        public HeavyImpactResponseSurfaceDefinition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(calibrationDomain, "calibrationDomain");
        }
    }
    public record ProtectionLayerDefinition(
            String materialId, double thicknessM, double spacingAfterM,
            double orientationRad, double coverageFraction, String responseSurfaceId) {
        public ProtectionLayerDefinition { Objects.requireNonNull(materialId, "materialId"); }
    }
    public record ProtectionStackDefinition(String id, double mountMassKg, List<ProtectionLayerDefinition> layers) {
        public ProtectionStackDefinition {
            Objects.requireNonNull(id, "id");
            layers = List.copyOf(Objects.requireNonNull(layers, "layers"));
        }
    }
    public record SlotDefinition(String id, IntegrationCategory category, Dimensions3d maxDimensionsM, double maxMassKg) {
        public SlotDefinition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(maxDimensionsM, "maxDimensionsM");
        }
    }
    public record HardpointDefinition(
            String id, HardpointSize size, Vector3d positionM, ArcDefinition arc,
            Dimensions3d maxModuleDimensionsM, double maxModuleMassKg, double maxRecoilImpulseNs,
            List<ModuleFamily> allowedModuleFamilies) {
        public HardpointDefinition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(size, "size");
            Objects.requireNonNull(positionM, "positionM");
            Objects.requireNonNull(arc, "arc");
            Objects.requireNonNull(maxModuleDimensionsM, "maxModuleDimensionsM");
            allowedModuleFamilies = List.copyOf(Objects.requireNonNull(allowedModuleFamilies, "allowedModuleFamilies"));
        }
    }
    public record CompartmentDefinition(
            String id, double volumeM3, Vector3d centerM, String protectionStackId, List<String> tags) {
        public CompartmentDefinition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(centerM, "centerM");
            tags = List.copyOf(Objects.requireNonNull(tags, "tags"));
        }
    }
    public record HullDefinition(
            String id, String displayName, HullArchitecture architecture, Dimensions3d boundingDimensionsM,
            double bareHullMassKg, double internalVolumeM3, List<SlotDefinition> slots,
            List<HardpointDefinition> hardpoints, List<CompartmentDefinition> compartments,
            int crewBaseline, int lifeSupportCapacity, double baseSignatureGeometryAreaM2,
            String structuralProtectionStackId, double maxOperationalMassKg,
            List<ModuleFamily> thrustMountCompatibility) {
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
    public record InterfaceDefinition(InterfaceKind kind, String id, double capacity) {
        public InterfaceDefinition {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(id, "id");
        }
    }
    public record ConstructionInputDefinition(String contentId, double amount) {
        public ConstructionInputDefinition { Objects.requireNonNull(contentId, "contentId"); }
    }
    public record MaintenanceDefinition(double serviceIntervalSeconds, double maintenanceWorkSeconds, double repairComplexity) { }
    public record ModuleDefinition(
            String id, String displayName, ModuleFamily family,
            List<IntegrationCategory> integrationCategories, List<HardpointSize> compatibleHardpointSizes,
            Dimensions3d physicalDimensionsM, double massKg, double occupiedVolumeM3,
            double requiredMountStrengthN, double continuousPowerSupplyW, double continuousPowerDemandW,
            double peakPowerDemandW, double storedEnergyCapacityJ, double wasteHeatW,
            double localThermalCapacityJ, double coolantTransferDemandW, double heatRejectionW,
            int crewRequirement, int automationRequirement, List<InterfaceDefinition> interfaces,
            Map<String, Double> signatureContributions, List<ConstructionInputDefinition> constructionInputs,
            MaintenanceDefinition maintenance, Map<String, Double> capabilityParameters) {
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
    public record InstalledModuleDefinition(String mountId, String moduleId) {
        public InstalledModuleDefinition {
            Objects.requireNonNull(mountId, "mountId");
            Objects.requireNonNull(moduleId, "moduleId");
        }
    }
    public record DemonstratorFitDefinition(String id, String hullId, List<InstalledModuleDefinition> installedModules) {
        public DemonstratorFitDefinition {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(hullId, "hullId");
            installedModules = List.copyOf(Objects.requireNonNull(installedModules, "installedModules"));
        }
    }
}
