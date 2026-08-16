package com.spacesim.content.ship;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.ship.ShipEngineeringCatalog.ArcDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.CalibrationDomainDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.CompartmentDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ConstructionInputDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.ShipEngineeringCatalog.HardpointDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HardpointSize;
import com.spacesim.content.ship.ShipEngineeringCatalog.HeavyImpactResponseSurfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullArchitecture;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.IntegrationCategory;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.MaintenanceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.MaterialDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.content.ship.ShipEngineeringCatalog.ProtectionLayerDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ProtectionStackDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.SlotDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.Vector3d;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Headless loader and semantic validator for the Stage-17.5 engineering catalog. */
public final class ShipEngineeringCatalogLoader {
    /** Current JSON schema version. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Current explicit migration version. */
    public static final int CURRENT_MIGRATION_VERSION = 1;
    /** Production classpath resource. */
    public static final String DEFAULT_RESOURCE = "data/content/ship-engineering-v1.json";

    private static final int MAX_DEFINITIONS = 512;
    private static final int MAX_CHILDREN = 128;
    private static final int MAX_PARAMETERS = 64;
    private static final Pattern CONTENT_ID = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)+");
    private static final Pattern LOCAL_ID = Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");
    private static final Set<String> FORBIDDEN_BONUS_FIELDS = Set.of(
            "classBonus", "classBonuses", "roleBonus", "roleBonuses",
            "doctrineBonus", "doctrineBonuses", "performanceBonus", "performanceBonuses");

    private ShipEngineeringCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads the built-in production engineering demonstrator.
     *
     * @return validated immutable engineering catalog
     */
    public static ShipEngineeringCatalog loadDefault() {
        ClassLoader classLoader = ShipEngineeringCatalogLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing engineering catalog: " + DEFAULT_RESOURCE);
            }
            ShipEngineeringCatalog catalog = parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            if (catalog.getMaterials().isEmpty() || catalog.getHulls().isEmpty()
                    || catalog.getModules().isEmpty() || catalog.getDemonstratorFits().isEmpty()) {
                throw new IllegalStateException("Production engineering catalog is incomplete");
            }
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read engineering catalog: " + DEFAULT_RESOURCE, exception);
        }
    }

    /**
     * Parses and fully validates one engineering-catalog document.
     *
     * @param json JSON document
     * @return immutable validated catalog
     */
    public static ShipEngineeringCatalog parse(String json) {
        Objects.requireNonNull(json, "json");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Engineering catalog JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Malformed engineering catalog JSON", exception);
        }
        requireObject(root, "root");
        rejectHiddenBonusFields(root, "root");
        int schemaVersion = requireInt(root, "schemaVersion");
        int migrationVersion = requireInt(root, "migrationVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported engineering schemaVersion: " + schemaVersion);
        }
        if (migrationVersion != CURRENT_MIGRATION_VERSION) {
            throw new IllegalArgumentException("Unsupported engineering migrationVersion: " + migrationVersion);
        }

        List<HeavyImpactResponseSurfaceDefinition> responseSurfaces = parseResponseSurfaces(root);
        Map<String, HeavyImpactResponseSurfaceDefinition> responseById = uniqueIndex(responseSurfaces,
                HeavyImpactResponseSurfaceDefinition::id, "response surface");
        List<MaterialDefinition> materials = parseMaterials(root, responseById);
        Map<String, MaterialDefinition> materialsById = uniqueIndex(materials, MaterialDefinition::id, "material");
        List<ProtectionStackDefinition> protectionStacks = parseProtectionStacks(root, materialsById, responseById);
        Map<String, ProtectionStackDefinition> protectionById = uniqueIndex(
                protectionStacks, ProtectionStackDefinition::id, "protection stack");
        List<HullDefinition> hulls = parseHulls(root, protectionById);
        Map<String, HullDefinition> hullsById = uniqueIndex(hulls, HullDefinition::id, "hull");
        List<ModuleDefinition> modules = parseModules(root);
        Map<String, ModuleDefinition> modulesById = uniqueIndex(modules, ModuleDefinition::id, "module");
        List<DemonstratorFitDefinition> fits = parseFits(root, hullsById, modulesById);
        uniqueIndex(fits, DemonstratorFitDefinition::id, "demonstrator fit");

        return new ShipEngineeringCatalog(
                schemaVersion,
                migrationVersion,
                materials,
                responseSurfaces,
                protectionStacks,
                hulls,
                modules,
                fits);
    }

    private static List<HeavyImpactResponseSurfaceDefinition> parseResponseSurfaces(JsonValue root) {
        JsonValue array = requireBoundedArray(root, "responseSurfaces", MAX_DEFINITIONS);
        List<HeavyImpactResponseSurfaceDefinition> values = new ArrayList<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            node = requireObject(node, "response surface");
            rejectHiddenBonusFields(node, "response surface");
            String id = requireContentId(node, "id");
            JsonValue domainNode = requireObject(node.get("calibrationDomain"), "calibrationDomain");
            CalibrationDomainDefinition domain = new CalibrationDomainDefinition(
                    requirePositiveFinite(domainNode, "minImpactVelocityMps"),
                    requirePositiveFinite(domainNode, "maxImpactVelocityMps"),
                    requirePositiveFinite(domainNode, "minProjectileMassKg"),
                    requirePositiveFinite(domainNode, "maxProjectileMassKg"),
                    requireNonBlank(domainNode, "confidenceLabel"));
            if (domain.maxImpactVelocityMps() < domain.minImpactVelocityMps()
                    || domain.maxProjectileMassKg() < domain.minProjectileMassKg()) {
                throw new IllegalArgumentException("Invalid calibration domain bounds: " + id);
            }
            values.add(new HeavyImpactResponseSurfaceDefinition(id, domain));
        }
        return values;
    }

    private static List<MaterialDefinition> parseMaterials(
            JsonValue root,
            Map<String, HeavyImpactResponseSurfaceDefinition> responseById) {
        JsonValue array = requireBoundedArray(root, "materials", MAX_DEFINITIONS);
        if (array.size == 0) {
            throw new IllegalArgumentException("Engineering catalog requires materials");
        }
        List<MaterialDefinition> values = new ArrayList<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            node = requireObject(node, "material");
            rejectHiddenBonusFields(node, "material");
            String id = requireContentId(node, "id");
            String responseId = optionalContentId(node, "heavyImpactResponseSurfaceId");
            if (responseId != null && !responseById.containsKey(responseId)) {
                throw new IllegalArgumentException("Material references unknown response surface: " + id + " -> " + responseId);
            }
            double emissivity = requireNonNegativeFinite(node, "emissivity");
            double radarReflectivity = requireNonNegativeFinite(node, "radarReflectivity");
            requireAtMostOne(emissivity, "emissivity", id);
            requireAtMostOne(radarReflectivity, "radarReflectivity", id);
            values.add(new MaterialDefinition(
                    id,
                    requirePositiveFinite(node, "densityKgPerM3"),
                    parseStringList(node, "tags", MAX_CHILDREN, true),
                    requireNonNegativeFinite(node, "thermalConductivityWPerMK"),
                    requirePositiveFinite(node, "specificHeatJPerKgK"),
                    emissivity,
                    radarReflectivity,
                    responseId,
                    optionalContentId(node, "constructionMaterialFamilyId"),
                    optionalContentId(node, "repairMaterialFamilyId")));
        }
        return values;
    }

    private static List<ProtectionStackDefinition> parseProtectionStacks(
            JsonValue root,
            Map<String, MaterialDefinition> materialsById,
            Map<String, HeavyImpactResponseSurfaceDefinition> responseById) {
        JsonValue array = requireBoundedArray(root, "protectionStacks", MAX_DEFINITIONS);
        List<ProtectionStackDefinition> values = new ArrayList<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            node = requireObject(node, "protection stack");
            rejectHiddenBonusFields(node, "protection stack");
            String id = requireContentId(node, "id");
            JsonValue layerArray = requireBoundedArray(node, "layers", MAX_CHILDREN);
            if (layerArray.size == 0) {
                throw new IllegalArgumentException("Protection stack requires layers: " + id);
            }
            List<ProtectionLayerDefinition> layers = new ArrayList<>();
            for (JsonValue layerNode = layerArray.child; layerNode != null; layerNode = layerNode.next) {
                layerNode = requireObject(layerNode, "protection layer");
                String materialId = requireContentId(layerNode, "materialId");
                if (!materialsById.containsKey(materialId)) {
                    throw new IllegalArgumentException("Protection stack references unknown material: " + id + " -> " + materialId);
                }
                String responseId = optionalContentId(layerNode, "responseSurfaceId");
                if (responseId != null && !responseById.containsKey(responseId)) {
                    throw new IllegalArgumentException("Protection layer references unknown response surface: " + responseId);
                }
                double coverage = requirePositiveFinite(layerNode, "coverageFraction");
                requireAtMostOne(coverage, "coverageFraction", id);
                layers.add(new ProtectionLayerDefinition(
                        materialId,
                        requirePositiveFinite(layerNode, "thicknessM"),
                        requireNonNegativeFinite(layerNode, "spacingAfterM"),
                        requireFinite(layerNode, "orientationRad"),
                        coverage,
                        responseId));
            }
            values.add(new ProtectionStackDefinition(id, requireNonNegativeFinite(node, "mountMassKg"), layers));
        }
        return values;
    }

    private static List<HullDefinition> parseHulls(
            JsonValue root,
            Map<String, ProtectionStackDefinition> protectionById) {
        JsonValue array = requireBoundedArray(root, "hulls", MAX_DEFINITIONS);
        if (array.size == 0) {
            throw new IllegalArgumentException("Engineering catalog requires hulls");
        }
        List<HullDefinition> values = new ArrayList<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            node = requireObject(node, "hull");
            rejectHiddenBonusFields(node, "hull");
            String id = requireContentId(node, "id");
            String structuralProtection = requireContentId(node, "structuralProtectionStackId");
            if (!protectionById.containsKey(structuralProtection)) {
                throw new IllegalArgumentException("Hull references unknown structural protection: " + id);
            }
            Dimensions3d bounds = parseDimensions(node.get("boundingDimensionsM"), id + ".boundingDimensionsM");
            double bareMass = requirePositiveFinite(node, "bareHullMassKg");
            double maxMass = requirePositiveFinite(node, "maxOperationalMassKg");
            if (maxMass <= bareMass) {
                throw new IllegalArgumentException("Hull maxOperationalMassKg must exceed bareHullMassKg: " + id);
            }
            int crew = requireNonNegativeInt(node, "crewBaseline");
            int lifeSupport = requireNonNegativeInt(node, "lifeSupportCapacity");
            if (lifeSupport < crew) {
                throw new IllegalArgumentException("Hull life support cannot be below baseline crew: " + id);
            }
            List<SlotDefinition> slots = parseSlots(node, id);
            List<HardpointDefinition> hardpoints = parseHardpoints(node, id);
            List<CompartmentDefinition> compartments = parseCompartments(node, id, protectionById);
            ensureUniqueMountIds(id, slots, hardpoints);
            if (compartments.isEmpty()) {
                throw new IllegalArgumentException("Hull requires compartment topology: " + id);
            }
            values.add(new HullDefinition(
                    id,
                    requireNonBlank(node, "displayName"),
                    requireEnum(node, "architecture", HullArchitecture.class),
                    bounds,
                    bareMass,
                    requirePositiveFinite(node, "internalVolumeM3"),
                    slots,
                    hardpoints,
                    compartments,
                    crew,
                    lifeSupport,
                    requirePositiveFinite(node, "baseSignatureGeometryAreaM2"),
                    structuralProtection,
                    maxMass,
                    parseEnumList(node, "thrustMountCompatibility", ModuleFamily.class, MAX_CHILDREN, false)));
        }
        return values;
    }

    private static List<SlotDefinition> parseSlots(JsonValue hull, String hullId) {
        JsonValue array = requireBoundedArray(hull, "slots", MAX_CHILDREN);
        List<SlotDefinition> values = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            node = requireObject(node, "slot");
            String id = requireLocalId(node, "id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate slot ID in hull " + hullId + ": " + id);
            }
            values.add(new SlotDefinition(
                    id,
                    requireEnum(node, "category", IntegrationCategory.class),
                    parseDimensions(node.get("maxDimensionsM"), hullId + "." + id),
                    requirePositiveFinite(node, "maxMassKg")));
        }
        return values;
    }

    private static List<HardpointDefinition> parseHardpoints(JsonValue hull, String hullId) {
        JsonValue array = requireBoundedArray(hull, "hardpoints", MAX_CHILDREN);
        List<HardpointDefinition> values = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            node = requireObject(node, "hardpoint");
            String id = requireLocalId(node, "id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate hardpoint ID in hull " + hullId + ": " + id);
            }
            JsonValue arcNode = requireObject(node.get("arc"), "hardpoint arc");
            double halfArc = requirePositiveFinite(arcNode, "halfArcRad");
            if (halfArc > Math.PI) {
                throw new IllegalArgumentException("Hardpoint halfArcRad exceeds PI: " + hullId + "." + id);
            }
            List<ModuleFamily> families = parseEnumList(
                    node, "allowedModuleFamilies", ModuleFamily.class, MAX_CHILDREN, false);
            values.add(new HardpointDefinition(
                    id,
                    requireEnum(node, "size", HardpointSize.class),
                    parseVector(node.get("positionM"), hullId + "." + id),
                    new ArcDefinition(requireFinite(arcNode, "azimuthCenterRad"), halfArc),
                    parseDimensions(node.get("maxModuleDimensionsM"), hullId + "." + id),
                    requirePositiveFinite(node, "maxModuleMassKg"),
                    requireNonNegativeFinite(node, "maxRecoilImpulseNs"),
                    families));
        }
        return values;
    }

    private static List<CompartmentDefinition> parseCompartments(
            JsonValue hull,
            String hullId,
            Map<String, ProtectionStackDefinition> protectionById) {
        JsonValue array = requireBoundedArray(hull, "compartments", MAX_CHILDREN);
        List<CompartmentDefinition> values = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            node = requireObject(node, "compartment");
            String id = requireLocalId(node, "id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate compartment ID in hull " + hullId + ": " + id);
            }
            String protectionId = optionalContentId(node, "protectionStackId");
            if (protectionId != null && !protectionById.containsKey(protectionId)) {
                throw new IllegalArgumentException("Compartment references unknown protection: " + hullId + "." + id);
            }
            values.add(new CompartmentDefinition(
                    id,
                    requirePositiveFinite(node, "volumeM3"),
                    parseVector(node.get("centerM"), hullId + "." + id),
                    protectionId,
                    parseStringList(node, "tags", MAX_CHILDREN, true)));
        }
        return values;
    }

    private static void ensureUniqueMountIds(
            String hullId,
            List<SlotDefinition> slots,
            List<HardpointDefinition> hardpoints) {
        Set<String> ids = new HashSet<>();
        for (SlotDefinition slot : slots) {
            ids.add(slot.id());
        }
        for (HardpointDefinition hardpoint : hardpoints) {
            if (!ids.add(hardpoint.id())) {
                throw new IllegalArgumentException("Slot/hardpoint ID collision in hull " + hullId + ": " + hardpoint.id());
            }
        }
    }

    private static List<ModuleDefinition> parseModules(JsonValue root) {
        JsonValue array = requireBoundedArray(root, "modules", MAX_DEFINITIONS);
        if (array.size == 0) {
            throw new IllegalArgumentException("Engineering catalog requires modules");
        }
        List<ModuleDefinition> values = new ArrayList<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            node = requireObject(node, "module");
            rejectHiddenBonusFields(node, "module");
            String id = requireContentId(node, "id");
            List<IntegrationCategory> categories = parseEnumList(
                    node, "integrationCategories", IntegrationCategory.class, MAX_CHILDREN, false);
            List<HardpointSize> hardpointSizes = parseEnumList(
                    node, "compatibleHardpointSizes", HardpointSize.class, MAX_CHILDREN, true);
            List<InterfaceDefinition> interfaces = parseInterfaces(node, id);
            List<ConstructionInputDefinition> construction = parseConstructionInputs(node, id);
            JsonValue maintenanceNode = requireObject(node.get("maintenance"), "maintenance");
            MaintenanceDefinition maintenance = new MaintenanceDefinition(
                    requirePositiveFinite(maintenanceNode, "serviceIntervalSeconds"),
                    requirePositiveFinite(maintenanceNode, "maintenanceWorkSeconds"),
                    requireNonNegativeFinite(maintenanceNode, "repairComplexity"));
            values.add(new ModuleDefinition(
                    id,
                    requireNonBlank(node, "displayName"),
                    requireEnum(node, "family", ModuleFamily.class),
                    categories,
                    hardpointSizes,
                    parseDimensions(node.get("physicalDimensionsM"), id),
                    requirePositiveFinite(node, "massKg"),
                    requirePositiveFinite(node, "occupiedVolumeM3"),
                    requireNonNegativeFinite(node, "requiredMountStrengthN"),
                    requireNonNegativeFinite(node, "continuousPowerSupplyW"),
                    requireNonNegativeFinite(node, "continuousPowerDemandW"),
                    requireNonNegativeFinite(node, "peakPowerDemandW"),
                    requireNonNegativeFinite(node, "storedEnergyCapacityJ"),
                    requireNonNegativeFinite(node, "wasteHeatW"),
                    requireNonNegativeFinite(node, "localThermalCapacityJ"),
                    requireNonNegativeFinite(node, "coolantTransferDemandW"),
                    requireNonNegativeFinite(node, "heatRejectionW"),
                    requireNonNegativeInt(node, "crewRequirement"),
                    requireNonNegativeInt(node, "automationRequirement"),
                    interfaces,
                    parseDoubleMap(node, "signatureContributions", MAX_PARAMETERS),
                    construction,
                    maintenance,
                    parseDoubleMap(node, "capabilityParameters", MAX_PARAMETERS)));
        }
        return values;
    }

    private static List<InterfaceDefinition> parseInterfaces(JsonValue module, String moduleId) {
        JsonValue array = requireBoundedArray(module, "interfaces", MAX_CHILDREN);
        List<InterfaceDefinition> values = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            node = requireObject(node, "module interface");
            String id = requireLocalId(node, "id");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate interface in module " + moduleId + ": " + id);
            }
            values.add(new InterfaceDefinition(
                    requireEnum(node, "kind", InterfaceKind.class),
                    id,
                    requirePositiveFinite(node, "capacity")));
        }
        return values;
    }

    private static List<ConstructionInputDefinition> parseConstructionInputs(JsonValue module, String moduleId) {
        JsonValue array = requireBoundedArray(module, "constructionInputs", MAX_CHILDREN);
        List<ConstructionInputDefinition> values = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            node = requireObject(node, "construction input");
            String id = requireContentId(node, "contentId");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate construction input in module " + moduleId + ": " + id);
            }
            values.add(new ConstructionInputDefinition(id, requirePositiveFinite(node, "amount")));
        }
        return values;
    }

    private static List<DemonstratorFitDefinition> parseFits(
            JsonValue root,
            Map<String, HullDefinition> hullsById,
            Map<String, ModuleDefinition> modulesById) {
        JsonValue array = requireBoundedArray(root, "demonstratorFits", MAX_DEFINITIONS);
        if (array.size == 0) {
            throw new IllegalArgumentException("Engineering catalog requires a machine-readable demonstrator fit");
        }
        List<DemonstratorFitDefinition> values = new ArrayList<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            node = requireObject(node, "demonstrator fit");
            String id = requireContentId(node, "id");
            String hullId = requireContentId(node, "hullId");
            HullDefinition hull = hullsById.get(hullId);
            if (hull == null) {
                throw new IllegalArgumentException("Fit references unknown hull: " + id + " -> " + hullId);
            }
            Map<String, SlotDefinition> slots = new HashMap<>();
            for (SlotDefinition slot : hull.slots()) {
                slots.put(slot.id(), slot);
            }
            Map<String, HardpointDefinition> hardpoints = new HashMap<>();
            for (HardpointDefinition hardpoint : hull.hardpoints()) {
                hardpoints.put(hardpoint.id(), hardpoint);
            }
            JsonValue installedArray = requireBoundedArray(node, "installedModules", MAX_CHILDREN);
            List<InstalledModuleDefinition> installed = new ArrayList<>();
            Set<String> usedMounts = new HashSet<>();
            for (JsonValue item = installedArray.child; item != null; item = item.next) {
                item = requireObject(item, "installed module");
                String mountId = requireLocalId(item, "mountId");
                String moduleId = requireContentId(item, "moduleId");
                if (!usedMounts.add(mountId)) {
                    throw new IllegalArgumentException("Fit mounts more than one module at " + id + ": " + mountId);
                }
                ModuleDefinition module = modulesById.get(moduleId);
                if (module == null) {
                    throw new IllegalArgumentException("Fit references unknown module: " + id + " -> " + moduleId);
                }
                SlotDefinition slot = slots.get(mountId);
                HardpointDefinition hardpoint = hardpoints.get(mountId);
                if (slot == null && hardpoint == null) {
                    throw new IllegalArgumentException("Fit references unknown mount: " + id + " -> " + mountId);
                }
                if (slot != null) {
                    validateSlotFit(id, slot, module);
                } else {
                    validateHardpointFit(id, hardpoint, module);
                }
                installed.add(new InstalledModuleDefinition(mountId, moduleId));
            }
            if (installed.isEmpty()) {
                throw new IllegalArgumentException("Demonstrator fit must install modules: " + id);
            }
            values.add(new DemonstratorFitDefinition(id, hullId, installed));
        }
        return values;
    }

    private static void validateSlotFit(String fitId, SlotDefinition slot, ModuleDefinition module) {
        if (!module.integrationCategories().contains(slot.category())) {
            throw new IllegalArgumentException("Incompatible slot category in fit " + fitId + ": " + slot.id());
        }
        if (module.massKg() > slot.maxMassKg() || !fits(module.physicalDimensionsM(), slot.maxDimensionsM())) {
            throw new IllegalArgumentException("Module exceeds slot envelope in fit " + fitId + ": " + slot.id());
        }
    }

    private static void validateHardpointFit(String fitId, HardpointDefinition hardpoint, ModuleDefinition module) {
        if (!hardpoint.allowedModuleFamilies().contains(module.family())
                || !module.compatibleHardpointSizes().contains(hardpoint.size())) {
            throw new IllegalArgumentException("Incompatible hardpoint family/size in fit " + fitId + ": " + hardpoint.id());
        }
        if (module.massKg() > hardpoint.maxModuleMassKg()
                || !fits(module.physicalDimensionsM(), hardpoint.maxModuleDimensionsM())) {
            throw new IllegalArgumentException("Module exceeds hardpoint envelope in fit " + fitId + ": " + hardpoint.id());
        }
    }

    private static boolean fits(Dimensions3d actual, Dimensions3d limit) {
        return actual.lengthM() <= limit.lengthM()
                && actual.widthM() <= limit.widthM()
                && actual.heightM() <= limit.heightM();
    }

    private static Dimensions3d parseDimensions(JsonValue node, String label) {
        node = requireObject(node, label);
        return new Dimensions3d(
                requirePositiveFinite(node, "lengthM"),
                requirePositiveFinite(node, "widthM"),
                requirePositiveFinite(node, "heightM"));
    }

    private static Vector3d parseVector(JsonValue node, String label) {
        node = requireObject(node, label);
        return new Vector3d(
                requireFinite(node, "xM"),
                requireFinite(node, "yM"),
                requireFinite(node, "zM"));
    }

    private static Map<String, Double> parseDoubleMap(JsonValue parent, String field, int limit) {
        JsonValue object = parent.get(field);
        if (object == null || !object.isObject()) {
            throw new IllegalArgumentException("Field " + field + " must be an object");
        }
        if (object.size > limit) {
            throw new IllegalArgumentException("Field " + field + " exceeds bounded size " + limit);
        }
        Map<String, Double> result = new LinkedHashMap<>();
        for (JsonValue node = object.child; node != null; node = node.next) {
            if (!LOCAL_ID.matcher(node.name).matches()) {
                throw new IllegalArgumentException("Invalid parameter key: " + node.name);
            }
            double value;
            try {
                value = node.asDouble();
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Parameter must be numeric: " + node.name, exception);
            }
            if (!Double.isFinite(value) || value < 0d) {
                throw new IllegalArgumentException("Parameter must be finite and non-negative: " + node.name);
            }
            result.put(node.name, value);
        }
        return result;
    }

    private static List<String> parseStringList(
            JsonValue parent, String field, int limit, boolean allowEmpty) {
        JsonValue array = requireBoundedArray(parent, field, limit);
        if (!allowEmpty && array.size == 0) {
            throw new IllegalArgumentException("Field " + field + " must not be empty");
        }
        List<String> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            String value;
            try {
                value = node.asString();
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Field " + field + " must contain strings", exception);
            }
            if (value == null || value.isBlank() || !LOCAL_ID.matcher(value).matches()) {
                throw new IllegalArgumentException("Invalid value in " + field + ": " + value);
            }
            if (!unique.add(value)) {
                throw new IllegalArgumentException("Duplicate value in " + field + ": " + value);
            }
            result.add(value);
        }
        return result;
    }

    private static <E extends Enum<E>> List<E> parseEnumList(
            JsonValue parent, String field, Class<E> type, int limit, boolean allowEmpty) {
        JsonValue array = requireBoundedArray(parent, field, limit);
        if (!allowEmpty && array.size == 0) {
            throw new IllegalArgumentException("Field " + field + " must not be empty");
        }
        List<E> result = new ArrayList<>();
        Set<E> unique = new HashSet<>();
        for (JsonValue node = array.child; node != null; node = node.next) {
            E value;
            try {
                value = Enum.valueOf(type, node.asString());
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Unknown " + field + " enum value", exception);
            }
            if (!unique.add(value)) {
                throw new IllegalArgumentException("Duplicate enum value in " + field + ": " + value);
            }
            result.add(value);
        }
        return result;
    }

    private static <E extends Enum<E>> E requireEnum(JsonValue parent, String field, Class<E> type) {
        try {
            return Enum.valueOf(type, requireNonBlank(parent, field));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown enum value for " + field, exception);
        }
    }

    private static JsonValue requireBoundedArray(JsonValue parent, String field, int limit) {
        JsonValue value = parent.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException("Field " + field + " must be an array");
        }
        if (value.size > limit) {
            throw new IllegalArgumentException("Field " + field + " exceeds bounded size " + limit);
        }
        return value;
    }

    private static JsonValue requireObject(JsonValue node, String label) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(label + " must be a JSON object");
        }
        return node;
    }

    private static String requireContentId(JsonValue parent, String field) {
        String value = requireNonBlank(parent, field);
        if (!CONTENT_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid content ID in " + field + ": " + value);
        }
        return value;
    }

    private static String optionalContentId(JsonValue parent, String field) {
        JsonValue node = parent.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        String value = requireNonBlank(parent, field);
        if (!CONTENT_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid content ID in " + field + ": " + value);
        }
        return value;
    }

    private static String requireLocalId(JsonValue parent, String field) {
        String value = requireNonBlank(parent, field);
        if (!LOCAL_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid local ID in " + field + ": " + value);
        }
        return value;
    }

    private static String requireNonBlank(JsonValue parent, String field) {
        try {
            String value = parent.getString(field);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Field " + field + " must not be blank");
            }
            return value;
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException) {
                throw exception;
            }
            throw new IllegalArgumentException("Field " + field + " must be a string", exception);
        }
    }

    private static int requireInt(JsonValue parent, String field) {
        try {
            return parent.getInt(field);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Field " + field + " must be an int", exception);
        }
    }

    private static int requireNonNegativeInt(JsonValue parent, String field) {
        int value = requireInt(parent, field);
        if (value < 0) {
            throw new IllegalArgumentException("Field " + field + " must be non-negative");
        }
        return value;
    }

    private static double requireFinite(JsonValue parent, String field) {
        double value;
        try {
            value = parent.getDouble(field);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Field " + field + " must be numeric", exception);
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Field " + field + " must be finite");
        }
        return value;
    }

    private static double requireNonNegativeFinite(JsonValue parent, String field) {
        double value = requireFinite(parent, field);
        if (value < 0d) {
            throw new IllegalArgumentException("Field " + field + " must be non-negative");
        }
        return value;
    }

    private static double requirePositiveFinite(JsonValue parent, String field) {
        double value = requireFinite(parent, field);
        if (value <= 0d) {
            throw new IllegalArgumentException("Field " + field + " must be positive");
        }
        return value;
    }

    private static void requireAtMostOne(double value, String field, String id) {
        if (value > 1d) {
            throw new IllegalArgumentException(field + " must be in [0,1]: " + id);
        }
    }

    private static void rejectHiddenBonusFields(JsonValue object, String label) {
        for (String field : FORBIDDEN_BONUS_FIELDS) {
            if (object.get(field) != null) {
                throw new IllegalArgumentException("Hidden class/role performance bonus is forbidden in " + label + ": " + field);
            }
        }
    }

    private static <T> Map<String, T> uniqueIndex(
            List<T> values, Function<T, String> idFunction, String label) {
        if (values.size() > MAX_DEFINITIONS) {
            throw new IllegalArgumentException("Too many " + label + " definitions");
        }
        Map<String, T> result = new LinkedHashMap<>();
        for (T value : values) {
            String id = idFunction.apply(value);
            if (result.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException("Duplicate " + label + " ID: " + id);
            }
        }
        return result;
    }
}