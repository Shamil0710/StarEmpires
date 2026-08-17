package com.spacesim.content;

import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;

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
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable Stage-18G physical shipyard catalog.
 *
 * <p>The catalog deliberately does not assign kilograms to the provisional Stage-17.5G requirement
 * units. It authors an independent physical Stage-18 mass specification for hull construction,
 * compartment repair and module service while keeping Stage-17.5G capability/tooling tokens only as
 * compatibility inputs to the existing engineering planner.</p>
 */
public final class Stage18ShipyardCatalog {
    private final int schemaVersion;
    private final List<YardDefinition> yards;
    private final List<HullPhysicalProfile> hullProfiles;
    private final List<ModuleServiceProfile> moduleProfiles;
    private final Map<String, YardDefinition> yardById;
    private final Map<String, HullPhysicalProfile> hullById;
    private final Map<String, ModuleServiceProfile> moduleById;
    private final String fingerprint;

    Stage18ShipyardCatalog(
            int schemaVersion,
            List<YardDefinition> yards,
            List<HullPhysicalProfile> hullProfiles,
            List<ModuleServiceProfile> moduleProfiles) {
        this.schemaVersion = schemaVersion;
        this.yards = sortedCopy(yards, YardDefinition::id, "yards");
        this.hullProfiles = sortedCopy(hullProfiles, HullPhysicalProfile::hullId, "hullProfiles");
        this.moduleProfiles = sortedCopy(moduleProfiles, ModuleServiceProfile::moduleId, "moduleProfiles");
        this.yardById = index(this.yards, YardDefinition::id, "yard");
        this.hullById = index(this.hullProfiles, HullPhysicalProfile::hullId, "hull profile");
        this.moduleById = index(this.moduleProfiles, ModuleServiceProfile::moduleId, "module profile");
        this.fingerprint = computeFingerprint();
    }

    /** @return supported Stage-18G shipyard schema version */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** @return deterministic immutable installed-yard definitions */
    public List<YardDefinition> getYards() {
        return yards;
    }

    /** @return deterministic immutable physical hull profiles */
    public List<HullPhysicalProfile> getHullProfiles() {
        return hullProfiles;
    }

    /** @return deterministic immutable physical module service profiles */
    public List<ModuleServiceProfile> getModuleProfiles() {
        return moduleProfiles;
    }

    /** @return lowercase SHA-256 fingerprint of Stage-18G physical semantics */
    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * Finds one installed-yard definition.
     *
     * @param id stable Stage-18G yard definition ID
     * @return yard definition, or {@code null}
     */
    public YardDefinition findYard(String id) {
        return yardById.get(id);
    }

    /**
     * Finds one physical hull profile.
     *
     * @param hullId existing Stage-17.5 hull ID
     * @return hull profile, or {@code null}
     */
    public HullPhysicalProfile findHullProfile(String hullId) {
        return hullById.get(hullId);
    }

    /**
     * Finds one physical module service profile.
     *
     * @param moduleId existing Stage-17.5 module ID
     * @return module service profile, or {@code null}
     */
    public ModuleServiceProfile findModuleProfile(String moduleId) {
        return moduleById.get(moduleId);
    }

    private String computeFingerprint() {
        StringBuilder canonical = new StringBuilder(16_384);
        canonical.append("schema=").append(schemaVersion).append('\n');
        for (YardDefinition yard : yards) {
            canonical.append("yard|").append(yard.id()).append('|').append(yard.displayName()).append('|')
                    .append(String.join(",", yard.requiredSupportFacilityDefinitionIds())).append('|')
                    .append(dimensions(yard.berthDimensionsM())).append('|')
                    .append(Double.toHexString(yard.maxServiceMassKg())).append('|')
                    .append(String.join(",", yard.stage175FabricationCapabilities())).append('|')
                    .append(String.join(",", yard.stage175HandledRequirementIds())).append('|')
                    .append(String.join(",", yard.toolingTags())).append('|')
                    .append(Double.toHexString(yard.precisionCapability())).append('|')
                    .append(Double.toHexString(yard.ratedIntegrationPowerW())).append('|')
                    .append(Double.toHexString(yard.ratedEngineeringWorkRate())).append('|')
                    .append(yard.laborCapacity()).append('|').append(yard.automationCapacity()).append('|')
                    .append(String.join(",", yard.handledStorageClassIds())).append('|')
                    .append(Double.toHexString(yard.maxHandledUnitMassKg())).append('|')
                    .append(String.join(",", yard.allowedLocationTags())).append('\n');
        }
        for (HullPhysicalProfile hull : hullProfiles) {
            canonical.append("hull|").append(hull.hullId()).append('|');
            appendInputs(canonical, hull.buildInputsKg());
            for (CompartmentRepairProfile repair : hull.compartmentRepairs()) {
                canonical.append("compartment=").append(repair.compartmentId()).append('[');
                appendInputs(canonical, repair.inputsAtFullLossKg());
                canonical.append(']');
            }
            canonical.append('\n');
        }
        for (ModuleServiceProfile module : moduleProfiles) {
            canonical.append("module|").append(module.moduleId()).append("|repair=");
            appendInputs(canonical, module.repairInputsAtFullLossKg());
            canonical.append("|maintenance=");
            appendInputs(canonical, module.maintenanceInputsKg());
            canonical.append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    private static void appendInputs(StringBuilder target, List<PhysicalInputDefinition> inputs) {
        for (PhysicalInputDefinition input : inputs) {
            target.append(input.commodityId()).append('=')
                    .append(Double.toHexString(input.massKg())).append(',');
        }
    }

    private static String dimensions(Dimensions3d dimensions) {
        return Double.toHexString(dimensions.lengthM()) + ','
                + Double.toHexString(dimensions.widthM()) + ','
                + Double.toHexString(dimensions.heightM());
    }

    /**
     * One installed physical shipyard design envelope.
     *
     * @param id stable Stage-18G yard ID
     * @param displayName diagnostic/display name
     * @param requiredSupportFacilityDefinitionIds Stage-18E facilities that must be installed and active
     * @param berthDimensionsM maximum ship envelope accepted by the berth
     * @param maxServiceMassKg maximum supported ship mass at pristine yard condition
     * @param stage175FabricationCapabilities compatibility capabilities consumed by Stage-17.5G planning
     * @param stage175HandledRequirementIds provisional Stage-17.5G input IDs accepted only for planner compatibility
     * @param toolingTags installed physical tooling expected by Stage-17.5G plans
     * @param precisionCapability pristine normalized integration precision
     * @param ratedIntegrationPowerW pristine yard integration power
     * @param ratedEngineeringWorkRate pristine engineering work-seconds per simulation second
     * @param laborCapacity maximum simultaneous staffed labor capacity
     * @param automationCapacity maximum simultaneous automation capacity
     * @param handledStorageClassIds Stage-18 physical storage classes the yard can receive
     * @param maxHandledUnitMassKg maximum single finished module mass handled by the yard
     * @param allowedLocationTags physical station locations where the yard may operate
     */
    public record YardDefinition(
            String id,
            String displayName,
            Set<String> requiredSupportFacilityDefinitionIds,
            Dimensions3d berthDimensionsM,
            double maxServiceMassKg,
            Set<String> stage175FabricationCapabilities,
            Set<String> stage175HandledRequirementIds,
            Set<String> toolingTags,
            double precisionCapability,
            double ratedIntegrationPowerW,
            double ratedEngineeringWorkRate,
            int laborCapacity,
            int automationCapacity,
            Set<String> handledStorageClassIds,
            double maxHandledUnitMassKg,
            Set<String> allowedLocationTags) {
        /**
         * Validates and freezes one yard definition.
         *
         * @param id stable yard ID
         * @param displayName display name
         * @param requiredSupportFacilityDefinitionIds required Stage-18E support facilities
         * @param berthDimensionsM physical berth envelope
         * @param maxServiceMassKg maximum supported ship mass
         * @param stage175FabricationCapabilities Stage-17.5G compatibility capabilities
         * @param stage175HandledRequirementIds Stage-17.5G compatibility requirement IDs
         * @param toolingTags installed tooling
         * @param precisionCapability integration precision
         * @param ratedIntegrationPowerW integration power rating
         * @param ratedEngineeringWorkRate engineering work rate
         * @param laborCapacity staffed labor capacity
         * @param automationCapacity automation capacity
         * @param handledStorageClassIds Stage-18 storage interfaces
         * @param maxHandledUnitMassKg maximum handled module mass
         * @param allowedLocationTags allowed physical locations
         */
        public YardDefinition {
            id = requireText(id, "yard id");
            displayName = requireText(displayName, "yard displayName");
            requiredSupportFacilityDefinitionIds = immutableSet(
                    requiredSupportFacilityDefinitionIds, "requiredSupportFacilityDefinitionIds", true);
            Objects.requireNonNull(berthDimensionsM, "berthDimensionsM");
            requirePositive(berthDimensionsM.lengthM(), "berth length");
            requirePositive(berthDimensionsM.widthM(), "berth width");
            requirePositive(berthDimensionsM.heightM(), "berth height");
            requirePositive(maxServiceMassKg, "maxServiceMassKg");
            stage175FabricationCapabilities = immutableSet(
                    stage175FabricationCapabilities, "stage175FabricationCapabilities", true);
            stage175HandledRequirementIds = immutableSet(
                    stage175HandledRequirementIds, "stage175HandledRequirementIds", true);
            toolingTags = immutableSet(toolingTags, "toolingTags", true);
            requireFraction(precisionCapability, "precisionCapability");
            requirePositive(ratedIntegrationPowerW, "ratedIntegrationPowerW");
            requirePositive(ratedEngineeringWorkRate, "ratedEngineeringWorkRate");
            if (laborCapacity < 0 || automationCapacity < 0) {
                throw new IllegalArgumentException("yard labor/automation capacity must be non-negative");
            }
            handledStorageClassIds = immutableSet(handledStorageClassIds, "handledStorageClassIds", true);
            requirePositive(maxHandledUnitMassKg, "maxHandledUnitMassKg");
            allowedLocationTags = immutableSet(allowedLocationTags, "allowedLocationTags", true);
        }
    }

    /**
     * One real Stage-18 mass input.
     *
     * @param commodityId Stage-18 material, consumable or component commodity ID
     * @param massKg required physical mass in kilograms
     */
    public record PhysicalInputDefinition(String commodityId, double massKg) {
        /**
         * Validates one physical input.
         *
         * @param commodityId Stage-18 commodity ID
         * @param massKg positive physical mass
         */
        public PhysicalInputDefinition {
            commodityId = requireText(commodityId, "commodityId");
            requirePositive(massKg, "massKg");
        }
    }

    /**
     * Physical material profile for one hull.
     *
     * @param hullId existing Stage-17.5 hull ID
     * @param buildInputsKg bare-hull construction mass inputs; total must equal authored bare hull mass
     * @param compartmentRepairs full-loss replacement inputs by every hull compartment
     */
    public record HullPhysicalProfile(
            String hullId,
            List<PhysicalInputDefinition> buildInputsKg,
            List<CompartmentRepairProfile> compartmentRepairs) {
        /**
         * Freezes deterministic physical hull inputs.
         *
         * @param hullId existing hull ID
         * @param buildInputsKg bare-hull physical inputs
         * @param compartmentRepairs compartment repair profiles
         */
        public HullPhysicalProfile {
            hullId = requireText(hullId, "hullId");
            buildInputsKg = immutableInputs(buildInputsKg, "buildInputsKg");
            Objects.requireNonNull(compartmentRepairs, "compartmentRepairs");
            List<CompartmentRepairProfile> repairs = new ArrayList<>(compartmentRepairs);
            repairs.sort(Comparator.comparing(CompartmentRepairProfile::compartmentId));
            compartmentRepairs = List.copyOf(repairs);
        }

        /**
         * Finds a compartment repair profile.
         *
         * @param compartmentId hull-local compartment ID
         * @return physical repair profile, or {@code null}
         */
        public CompartmentRepairProfile findCompartmentRepair(String compartmentId) {
            for (CompartmentRepairProfile profile : compartmentRepairs) {
                if (profile.compartmentId().equals(compartmentId)) {
                    return profile;
                }
            }
            return null;
        }
    }

    /**
     * Full-loss physical repair inputs for one hull compartment.
     *
     * @param compartmentId hull-local compartment ID
     * @param inputsAtFullLossKg Stage-18 material/component replacement mass at zero integrity
     */
    public record CompartmentRepairProfile(
            String compartmentId,
            List<PhysicalInputDefinition> inputsAtFullLossKg) {
        /**
         * Freezes one compartment repair profile.
         *
         * @param compartmentId hull-local compartment ID
         * @param inputsAtFullLossKg full-loss physical inputs
         */
        public CompartmentRepairProfile {
            compartmentId = requireText(compartmentId, "compartmentId");
            inputsAtFullLossKg = immutableInputs(inputsAtFullLossKg, "inputsAtFullLossKg");
        }
    }

    /**
     * Physical repair/service material profile for one manufactured module.
     *
     * @param moduleId existing Stage-17.5 module ID
     * @param repairInputsAtFullLossKg replacement inputs at zero module integrity
     * @param maintenanceInputsKg physical spares/consumables for one scheduled service event
     */
    public record ModuleServiceProfile(
            String moduleId,
            List<PhysicalInputDefinition> repairInputsAtFullLossKg,
            List<PhysicalInputDefinition> maintenanceInputsKg) {
        /**
         * Freezes one module service profile.
         *
         * @param moduleId existing module ID
         * @param repairInputsAtFullLossKg full-loss repair inputs
         * @param maintenanceInputsKg scheduled maintenance inputs
         */
        public ModuleServiceProfile {
            moduleId = requireText(moduleId, "moduleId");
            repairInputsAtFullLossKg = immutableInputs(repairInputsAtFullLossKg, "repairInputsAtFullLossKg");
            maintenanceInputsKg = immutableInputs(maintenanceInputsKg, "maintenanceInputsKg");
        }
    }

    private static List<PhysicalInputDefinition> immutableInputs(
            List<PhysicalInputDefinition> source, String name) {
        Objects.requireNonNull(source, name);
        if (source.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        List<PhysicalInputDefinition> copy = new ArrayList<>(source);
        copy.sort(Comparator.comparing(PhysicalInputDefinition::commodityId));
        String previous = null;
        for (PhysicalInputDefinition input : copy) {
            Objects.requireNonNull(input, name + " entry");
            if (input.commodityId().equals(previous)) {
                throw new IllegalArgumentException("Duplicate physical input: " + input.commodityId());
            }
            previous = input.commodityId();
        }
        return List.copyOf(copy);
    }

    private static Set<String> immutableSet(Set<String> source, String name, boolean requireNonEmpty) {
        Objects.requireNonNull(source, name);
        TreeSet<String> copy = new TreeSet<>();
        for (String value : source) {
            copy.add(requireText(value, name + " entry"));
        }
        if (requireNonEmpty && copy.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return Collections.unmodifiableSet(copy);
    }

    private static <T> List<T> sortedCopy(
            List<T> source,
            java.util.function.Function<T, String> id,
            String name) {
        Objects.requireNonNull(source, name);
        List<T> copy = new ArrayList<>(source);
        copy.sort(Comparator.comparing(id));
        return List.copyOf(copy);
    }

    private static <T> Map<String, T> index(
            List<T> values,
            java.util.function.Function<T, String> id,
            String label) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T value : values) {
            String key = id.apply(value);
            if (result.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate " + label + ": " + key);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
        return value;
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(name + " must be in [0,1]");
        }
    }
}
