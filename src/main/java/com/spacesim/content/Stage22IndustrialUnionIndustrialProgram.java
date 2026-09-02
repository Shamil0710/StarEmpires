package com.spacesim.content;

import com.spacesim.content.Stage18ManufacturingCatalog.ProductProfileDefinition;
import com.spacesim.content.Stage22IndustrialUnionProductionState.YardSeriesState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * M22.4 Industrial Union serial-production/commonality authoring contract.
 *
 * <p>The program converts authored series qualification into explicit work/energy inputs for the
 * existing Stage-18 production grammar. It never creates inventory, ships, construction progress or
 * treasury value. Same-series efficiency is earned only after repeated production; changing series
 * incurs finite positive retool work and energy before the common production authority can continue.</p>
 */
public final class Stage22IndustrialUnionIndustrialProgram {
    /** Ordinary M22.4 yard used by the first production package. */
    public static final String YARD_ID = "yard.industrial_union.series_complex_v1";
    /** Existing common procurement-policy binding from M22.1. */
    public static final String PROCUREMENT_POLICY = "policy.core.industrial_union.procurement.v1";

    private static final long BASE_RETOOL_WORK_SECONDS = 259_200L;
    private static final long BASE_RETOOL_ENERGY_J = 2_400_000_000_000L;

    private Stage22IndustrialUnionIndustrialProgram() {
        throw new AssertionError("utility class");
    }

    /** @return three compact serial-production families covering the nine shared Stage-22 roles */
    public static List<AssemblySeriesDefinition> seriesDefinitions() {
        return List.of(
                new AssemblySeriesDefinition(
                        "assembly_series.industrial_union.screen",
                        Set.of(
                                "ship_family.industrial_union.corvette",
                                "ship_family.industrial_union.frigate",
                                "ship_family.industrial_union.destroyer"),
                        Set.of(
                                "module.industrial_union_reactor_bank_v1",
                                "module.industrial_union_drive_bank_v1",
                                "module.industrial_union_sensor_block_v1",
                                "module.industrial_union_radiator_panel_v1"),
                        3,
                        0.88d,
                        0.94d),
                new AssemblySeriesDefinition(
                        "assembly_series.industrial_union.capital",
                        Set.of(
                                "ship_family.industrial_union.cruiser",
                                "ship_family.industrial_union.battleship",
                                "ship_family.industrial_union.carrier"),
                        Set.of(
                                "module.industrial_union_reactor_bank_v1",
                                "module.industrial_union_drive_bank_v1",
                                "module.industrial_union_sensor_block_v1",
                                "module.industrial_union_radiator_panel_v1"),
                        4,
                        0.86d,
                        0.93d),
                new AssemblySeriesDefinition(
                        "assembly_series.industrial_union.logistics",
                        Set.of(
                                "ship_family.industrial_union.freight",
                                "ship_family.industrial_union.tanker",
                                "ship_family.industrial_union.fleet_support"),
                        Set.of(
                                "module.industrial_union_reactor_bank_v1",
                                "module.industrial_union_drive_bank_v1",
                                "module.industrial_union_sensor_block_v1",
                                "module.industrial_union_radiator_panel_v1"),
                        3,
                        0.84d,
                        0.92d));
    }

    /**
     * Starts a finite changeover. Initial qualification costs the same work/energy as later family
     * changes, so there is no free first-series special case.
     *
     * @param state current yard qualification state
     * @param targetShipFamilyId requested Union ship family
     * @return yard state with explicit pending series and positive retool burden
     */
    public static YardSeriesState beginRetool(YardSeriesState state, String targetShipFamilyId) {
        YardSeriesState checked = Objects.requireNonNull(state, "state");
        if (checked.retooling()) {
            throw new IllegalStateException("Cannot begin a second retool while changeover is unfinished");
        }
        AssemblySeriesDefinition target = requireSeriesForFamily(targetShipFamilyId);
        if (target.id().equals(checked.activeSeriesId())) {
            return checked;
        }
        return new YardSeriesState(
                checked.yardId(),
                checked.activeSeriesId(),
                target.id(),
                checked.completedUnitsInSeries(),
                checked.commonalityStreak(),
                BASE_RETOOL_WORK_SECONDS,
                BASE_RETOOL_ENERGY_J);
    }

    /**
     * Records finite work/energy supplied by common authorities toward an already-started changeover.
     * Values are clamped at zero and cannot create negative debt.
     *
     * @param state unfinished retool state
     * @param suppliedWorkSeconds finite work supplied by ordinary production capacity
     * @param suppliedEnergyJ finite energy supplied by ordinary industrial capacity
     * @return updated unfinished or ready-to-finalize retool state
     */
    public static YardSeriesState applyRetoolInputs(
            YardSeriesState state,
            long suppliedWorkSeconds,
            long suppliedEnergyJ) {
        YardSeriesState checked = Objects.requireNonNull(state, "state");
        if (!checked.retooling()) {
            throw new IllegalStateException("No Industrial Union retool is active");
        }
        if (suppliedWorkSeconds < 0L || suppliedEnergyJ < 0L) {
            throw new IllegalArgumentException("Retool inputs must be non-negative");
        }
        return new YardSeriesState(
                checked.yardId(),
                checked.activeSeriesId(),
                checked.pendingSeriesId(),
                checked.completedUnitsInSeries(),
                checked.commonalityStreak(),
                Math.max(0L, checked.retoolWorkRemainingSeconds() - suppliedWorkSeconds),
                Math.max(0L, checked.retoolEnergyRemainingJ() - suppliedEnergyJ));
    }

    /**
     * Finalizes a fully paid changeover and resets commonality. This method fails closed while any
     * required work or energy remains.
     *
     * @param state pending retool state
     * @return newly qualified series state
     */
    public static YardSeriesState completeRetool(YardSeriesState state) {
        YardSeriesState checked = Objects.requireNonNull(state, "state");
        if (!checked.retooling()) {
            throw new IllegalStateException("No Industrial Union retool is active");
        }
        if (checked.retoolWorkRemainingSeconds() != 0L || checked.retoolEnergyRemainingJ() != 0L) {
            throw new IllegalStateException("Cannot complete Industrial Union retool before all finite costs are paid");
        }
        return new YardSeriesState(
                checked.yardId(), checked.pendingSeriesId(), Stage22IndustrialUnionProductionState.NO_SERIES,
                0, 0, 0L, 0L);
    }

    /**
     * Computes the process modifier for one legal same-series build. An abrupt family switch is
     * rejected until {@link #beginRetool(YardSeriesState, String)} is paid and completed.
     *
     * @param state qualified yard state
     * @param targetShipFamilyId requested Union ship family
     * @return deterministic common-authority process modifier
     */
    public static ProductionModifier modifierFor(YardSeriesState state, String targetShipFamilyId) {
        YardSeriesState checked = Objects.requireNonNull(state, "state");
        if (checked.retooling()) {
            throw new IllegalStateException("Manufacturing is blocked while Industrial Union retool is unfinished");
        }
        AssemblySeriesDefinition target = requireSeriesForFamily(targetShipFamilyId);
        if (!target.id().equals(checked.activeSeriesId())) {
            throw new IllegalStateException("Requested family requires explicit Industrial Union retool: " + targetShipFamilyId);
        }
        if (checked.commonalityStreak() >= target.minimumSeriesUnits()) {
            return new ProductionModifier(target.steadyWorkMultiplier(), target.steadyEnergyMultiplier(), true);
        }
        return new ProductionModifier(0.96d, 0.98d, false);
    }

    /**
     * Projects a modifier into the ordinary Stage-18 product profile grammar. The returned profile is
     * still executed by Stage-18 manufacturing authority and preserves all finite material inputs.
     *
     * @param base ordinary manufacturing profile
     * @param derivedProfileId stable content ID for the qualified-series projection
     * @param modifier deterministic series modifier
     * @return profile with unchanged inputs/capabilities and explicit work/energy causality
     */
    public static ProductProfileDefinition deriveProfile(
            ProductProfileDefinition base,
            String derivedProfileId,
            ProductionModifier modifier) {
        ProductProfileDefinition checked = Objects.requireNonNull(base, "base");
        ProductionModifier applied = Objects.requireNonNull(modifier, "modifier");
        return new ProductProfileDefinition(
                Objects.requireNonNull(derivedProfileId, "derivedProfileId"),
                checked.displayName() + " / Industrial Union qualified series",
                checked.inputs(),
                checked.requiredCapabilityTags(),
                checked.energyJPerOutputKg() * applied.energyMultiplier(),
                checked.workSecondsPerOutputKg() * applied.workMultiplier(),
                checked.maintenanceWorkSecondsPerOutputKg());
    }

    /**
     * Records one completed legal unit. The bonus is earned from actual same-series completions, not
     * granted from the faction ID.
     *
     * @param state qualified yard state
     * @param targetShipFamilyId completed Union ship family
     * @return updated series counters
     */
    public static YardSeriesState recordCompletedUnit(YardSeriesState state, String targetShipFamilyId) {
        modifierFor(state, targetShipFamilyId);
        return new YardSeriesState(
                state.yardId(), state.activeSeriesId(), Stage22IndustrialUnionProductionState.NO_SERIES,
                state.completedUnitsInSeries() + 1, state.commonalityStreak() + 1, 0L, 0L);
    }

    /**
     * Validates authored series coverage against the accepted M22.1 profile/policy contract.
     *
     * @return immutable diagnostic validation report
     */
    public static ValidationReport validateDefault() {
        Stage22FactionProfileCatalog profiles = Stage22FactionProfileLoader.loadDefault();
        if (profiles.findPolicy(PROCUREMENT_POLICY) == null) {
            throw new IllegalStateException("Industrial Union series program lacks common procurement policy");
        }
        if (profiles.findProfileForFaction(Stage22IndustrialUnionProductionState.STABLE_FACTION_ID) == null) {
            throw new IllegalStateException("Industrial Union stable identity is absent from M22.1 profile catalog");
        }
        Map<String, String> familyToSeries = familyIndex();
        if (familyToSeries.size() != 9) {
            throw new IllegalStateException("Industrial Union series program must cover all nine shared role families");
        }
        long sharedCoreUseCount = seriesDefinitions().stream()
                .flatMap(value -> value.commonAssemblyIds().stream())
                .filter(id -> id.equals("module.industrial_union_reactor_bank_v1")
                        || id.equals("module.industrial_union_drive_bank_v1")
                        || id.equals("module.industrial_union_sensor_block_v1")
                        || id.equals("module.industrial_union_radiator_panel_v1"))
                .count();
        return new ValidationReport(seriesDefinitions().size(), familyToSeries.size(), sharedCoreUseCount,
                BASE_RETOOL_WORK_SECONDS, BASE_RETOOL_ENERGY_J);
    }

    private static AssemblySeriesDefinition requireSeriesForFamily(String shipFamilyId) {
        String checked = Objects.requireNonNull(shipFamilyId, "shipFamilyId");
        return seriesDefinitions().stream()
                .filter(value -> value.shipFamilyIds().contains(checked))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Industrial Union ship family: " + checked));
    }

    private static Map<String, String> familyIndex() {
        Map<String, String> result = new LinkedHashMap<>();
        List<AssemblySeriesDefinition> ordered = new ArrayList<>(seriesDefinitions());
        ordered.sort(Comparator.comparing(AssemblySeriesDefinition::id));
        for (AssemblySeriesDefinition series : ordered) {
            for (String family : series.shipFamilyIds().stream().sorted().toList()) {
                if (result.putIfAbsent(family, series.id()) != null) {
                    throw new IllegalStateException("Industrial Union ship family appears in multiple series: " + family);
                }
            }
        }
        return Map.copyOf(result);
    }

    /** One compact yard-qualification series sharing repeated physical assemblies. */
    public record AssemblySeriesDefinition(
            String id,
            Set<String> shipFamilyIds,
            Set<String> commonAssemblyIds,
            int minimumSeriesUnits,
            double steadyWorkMultiplier,
            double steadyEnergyMultiplier) {
        /**
         * @param id stable assembly-series ID
         * @param shipFamilyIds package ship families qualified by the same tooling
         * @param commonAssemblyIds repeated modules/assemblies creating spares commonality
         * @param minimumSeriesUnits same-series completions required before steady throughput
         * @param steadyWorkMultiplier explicit common-authority work multiplier after qualification
         * @param steadyEnergyMultiplier explicit common-authority energy multiplier after qualification
         */
        public AssemblySeriesDefinition {
            Objects.requireNonNull(id, "id");
            shipFamilyIds = Set.copyOf(Objects.requireNonNull(shipFamilyIds, "shipFamilyIds"));
            commonAssemblyIds = Set.copyOf(Objects.requireNonNull(commonAssemblyIds, "commonAssemblyIds"));
            if (shipFamilyIds.isEmpty() || commonAssemblyIds.isEmpty()) {
                throw new IllegalArgumentException("Industrial Union series requires families and common assemblies");
            }
            if (minimumSeriesUnits <= 0) {
                throw new IllegalArgumentException("minimumSeriesUnits must be positive");
            }
            if (!finiteDiscount(steadyWorkMultiplier) || !finiteDiscount(steadyEnergyMultiplier)) {
                throw new IllegalArgumentException("Steady-series multipliers must be finite in (0,1]");
            }
        }
    }

    /** Explicit modifier supplied into existing manufacturing grammar. */
    public record ProductionModifier(double workMultiplier, double energyMultiplier, boolean steadySeries) {
        /**
         * @param workMultiplier explicit engineering-work multiplier
         * @param energyMultiplier explicit process-energy multiplier
         * @param steadySeries whether repeated-series qualification has reached its reviewed floor
         */
        public ProductionModifier {
            if (!finiteDiscount(workMultiplier) || !finiteDiscount(energyMultiplier)) {
                throw new IllegalArgumentException("Industrial Union production multipliers must be finite in (0,1]");
            }
        }
    }

    /** Immutable validation evidence for the serial-production contract. */
    public record ValidationReport(
            int seriesCount,
            int coveredFamilyCount,
            long sharedCoreAssemblyReferences,
            long retoolWorkSeconds,
            long retoolEnergyJ) { }

    private static boolean finiteDiscount(double value) {
        return Double.isFinite(value) && value > 0d && value <= 1d;
    }
}
