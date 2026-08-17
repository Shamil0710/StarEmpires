package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.ConstructionInputDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable Stage-17.5G industrial requirement vocabulary for shipyard work.
 *
 * <p>This catalog does not define mines, refineries, factories, commodity runtime IDs or station
 * archetypes. It only states what engineering work requires. Stage 18 resolves these requirement
 * IDs and capability tags into the full physical industrial economy.</p>
 */
public final class ShipyardIndustrialCatalog {
    private final int schemaVersion;
    private final List<HullIndustrialProfile> hullProfiles;
    private final List<ModuleIndustrialProfile> moduleProfiles;
    private final Map<String, HullIndustrialProfile> hullById;
    private final Map<String, ModuleIndustrialProfile> moduleById;

    ShipyardIndustrialCatalog(
            int schemaVersion,
            List<HullIndustrialProfile> hullProfiles,
            List<ModuleIndustrialProfile> moduleProfiles) {
        this.schemaVersion = schemaVersion;
        this.hullProfiles = sortedHullCopy(hullProfiles);
        this.moduleProfiles = sortedModuleCopy(moduleProfiles);
        this.hullById = indexHull(this.hullProfiles);
        this.moduleById = indexModule(this.moduleProfiles);
    }

    /** @return supported shipyard industrial schema version */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** @return deterministic hull industrial profiles */
    public List<HullIndustrialProfile> getHullProfiles() {
        return hullProfiles;
    }

    /** @return deterministic module industrial profiles */
    public List<ModuleIndustrialProfile> getModuleProfiles() {
        return moduleProfiles;
    }

    /**
     * Finds one hull industrial profile.
     *
     * @param hullId stable engineering hull ID
     * @return profile or {@code null}
     */
    public HullIndustrialProfile findHullProfile(String hullId) {
        return hullById.get(hullId);
    }

    /**
     * Finds one module industrial profile.
     *
     * @param moduleId stable engineering module ID
     * @return profile or {@code null}
     */
    public ModuleIndustrialProfile findModuleProfile(String moduleId) {
        return moduleById.get(moduleId);
    }

    private static List<HullIndustrialProfile> sortedHullCopy(List<HullIndustrialProfile> values) {
        Objects.requireNonNull(values, "hullProfiles");
        List<HullIndustrialProfile> copy = new ArrayList<>(values);
        copy.sort(Comparator.comparing(HullIndustrialProfile::hullId));
        return List.copyOf(copy);
    }

    private static List<ModuleIndustrialProfile> sortedModuleCopy(List<ModuleIndustrialProfile> values) {
        Objects.requireNonNull(values, "moduleProfiles");
        List<ModuleIndustrialProfile> copy = new ArrayList<>(values);
        copy.sort(Comparator.comparing(ModuleIndustrialProfile::moduleId));
        return List.copyOf(copy);
    }

    private static Map<String, HullIndustrialProfile> indexHull(List<HullIndustrialProfile> values) {
        LinkedHashMap<String, HullIndustrialProfile> result = new LinkedHashMap<>();
        for (HullIndustrialProfile value : values) {
            result.put(value.hullId(), value);
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, ModuleIndustrialProfile> indexModule(List<ModuleIndustrialProfile> values) {
        LinkedHashMap<String, ModuleIndustrialProfile> result = new LinkedHashMap<>();
        for (ModuleIndustrialProfile value : values) {
            result.put(value.moduleId(), value);
        }
        return Collections.unmodifiableMap(result);
    }

    private static Set<String> immutableSortedSet(Set<String> values) {
        Objects.requireNonNull(values, "values");
        return Collections.unmodifiableSet(new TreeSet<>(values));
    }

    private static List<ConstructionInputDefinition> immutableSortedInputs(
            List<ConstructionInputDefinition> values) {
        Objects.requireNonNull(values, "values");
        List<ConstructionInputDefinition> copy = new ArrayList<>(values);
        copy.sort(Comparator.comparing(ConstructionInputDefinition::contentId));
        return List.copyOf(copy);
    }

    /**
     * Industrial requirements for producing and structurally servicing one hull.
     *
     * @param hullId referenced Stage-17.5A hull ID
     * @param constructionInputs provisional Stage-18 physical input seams
     * @param fabricationCapabilities required facility fabrication capabilities
     * @param toolingTags required facility tooling
     * @param precisionRequirement minimum normalized precision capability in [0,1]
     * @param industrialPowerW minimum available industrial power
     * @param laborRequirement minimum simultaneous labor capacity
     * @param automationRequirement minimum simultaneous automation capacity
     * @param assemblyWorkSeconds engineering work at unit work rate
     * @param compartmentRepairs full-loss structural repair requirements by compartment
     */
    public record HullIndustrialProfile(
            String hullId,
            List<ConstructionInputDefinition> constructionInputs,
            Set<String> fabricationCapabilities,
            Set<String> toolingTags,
            double precisionRequirement,
            double industrialPowerW,
            int laborRequirement,
            int automationRequirement,
            double assemblyWorkSeconds,
            List<CompartmentRepairProfile> compartmentRepairs) {
        /**
         * Freezes deterministic collection ordering after loader validation.
         *
         * @param hullId referenced hull ID
         * @param constructionInputs provisional physical inputs
         * @param fabricationCapabilities fabrication capabilities
         * @param toolingTags tooling tags
         * @param precisionRequirement normalized precision requirement
         * @param industrialPowerW industrial power requirement
         * @param laborRequirement labor requirement
         * @param automationRequirement automation requirement
         * @param assemblyWorkSeconds assembly work
         * @param compartmentRepairs compartment repair profiles
         */
        public HullIndustrialProfile {
            constructionInputs = immutableSortedInputs(constructionInputs);
            fabricationCapabilities = immutableSortedSet(fabricationCapabilities);
            toolingTags = immutableSortedSet(toolingTags);
            List<CompartmentRepairProfile> repairCopy = new ArrayList<>(
                    Objects.requireNonNull(compartmentRepairs, "compartmentRepairs"));
            repairCopy.sort(Comparator.comparing(CompartmentRepairProfile::compartmentId));
            compartmentRepairs = List.copyOf(repairCopy);
        }

        /**
         * Finds one compartment repair profile.
         *
         * @param compartmentId hull-local compartment ID
         * @return profile or {@code null}
         */
        public CompartmentRepairProfile findCompartmentRepair(String compartmentId) {
            for (CompartmentRepairProfile value : compartmentRepairs) {
                if (value.compartmentId().equals(compartmentId)) {
                    return value;
                }
            }
            return null;
        }
    }

    /**
     * Full-loss structural repair requirements for one hull compartment.
     *
     * @param compartmentId hull-local compartment ID
     * @param repairInputsAtFullLoss physical repair inputs required at zero integrity
     * @param repairWorkSecondsAtFullLoss engineering work required at zero integrity
     */
    public record CompartmentRepairProfile(
            String compartmentId,
            List<ConstructionInputDefinition> repairInputsAtFullLoss,
            double repairWorkSecondsAtFullLoss) {
        /**
         * Freezes deterministic input ordering after loader validation.
         *
         * @param compartmentId hull-local compartment ID
         * @param repairInputsAtFullLoss full-loss inputs
         * @param repairWorkSecondsAtFullLoss full-loss work
         */
        public CompartmentRepairProfile {
            repairInputsAtFullLoss = immutableSortedInputs(repairInputsAtFullLoss);
        }
    }

    /**
     * Facility and work requirements for manufacturing/integrating one module.
     *
     * @param moduleId referenced Stage-17.5A module ID
     * @param fabricationCapabilities required fabrication capabilities
     * @param toolingTags required tooling
     * @param precisionRequirement minimum normalized precision capability in [0,1]
     * @param industrialPowerW minimum available industrial power
     * @param laborRequirement minimum simultaneous labor capacity
     * @param automationRequirement minimum simultaneous automation capacity
     * @param manufacturingWorkSeconds module manufacturing work from its authored construction inputs
     * @param installationWorkSeconds installation/integration work
     * @param removalWorkSeconds non-destructive removal work
     */
    public record ModuleIndustrialProfile(
            String moduleId,
            Set<String> fabricationCapabilities,
            Set<String> toolingTags,
            double precisionRequirement,
            double industrialPowerW,
            int laborRequirement,
            int automationRequirement,
            double manufacturingWorkSeconds,
            double installationWorkSeconds,
            double removalWorkSeconds) {
        /**
         * Freezes deterministic sets after loader validation.
         *
         * @param moduleId referenced module ID
         * @param fabricationCapabilities fabrication capabilities
         * @param toolingTags tooling tags
         * @param precisionRequirement normalized precision requirement
         * @param industrialPowerW industrial power requirement
         * @param laborRequirement labor requirement
         * @param automationRequirement automation requirement
         * @param manufacturingWorkSeconds manufacturing work
         * @param installationWorkSeconds installation work
         * @param removalWorkSeconds removal work
         */
        public ModuleIndustrialProfile {
            fabricationCapabilities = immutableSortedSet(fabricationCapabilities);
            toolingTags = immutableSortedSet(toolingTags);
        }
    }
}
