package com.spacesim.world;

import com.spacesim.content.ContentCatalog;
import com.spacesim.model.ItemCategory;

import java.util.Map;
import java.util.Objects;

/**
 * Deterministic Stage-16 policy for deriving station build time from physical requirements.
 *
 * <p>The old archetype {@code buildSeconds} value is retained only as a base setup/complexity
 * allowance. It is no longer the complete duration. Most work is derived from the exact material
 * bill: every required unit contributes a category-specific normalized construction-handling
 * weight, and the accumulated work is divided by one explicit baseline assembly rate.</p>
 *
 * <p>These weights are deliberately <em>not kilograms</em>. The current content catalog has no
 * authoritative per-item physical mass yet, so they represent handling/fabrication work units.
 * When real component mass is introduced, this policy is the single seam where normalized work can
 * be replaced or combined with mass without changing project persistence or construction sites.</p>
 */
public final class ConstructionDurationPolicy {
    /** Baseline construction work processed per simulation second by one standard site. */
    public static final double BASE_ASSEMBLY_WORK_PER_SECOND = 12d;

    private ConstructionDurationPolicy() {
        throw new AssertionError("ConstructionDurationPolicy does not create instances");
    }

    /**
     * Calculates a reproducible duration breakdown for a constructible station archetype.
     *
     * @param catalog authoritative content catalog resolving physical required items
     * @param station constructible station archetype
     * @return positive finite deterministic duration estimate
     */
    public static Estimate estimate(
            ContentCatalog catalog,
            ContentCatalog.StationArchetypeDefinition station) {
        ContentCatalog checkedCatalog = Objects.requireNonNull(catalog, "Construction catalog not set");
        ContentCatalog.StationArchetypeDefinition checkedStation = Objects.requireNonNull(
                station, "Construction station archetype not set");
        ContentCatalog.ConstructionDefinition construction = checkedStation.construction();
        if (construction == null) {
            throw new IllegalArgumentException("Station archetype is not constructible: " + checkedStation.id());
        }

        double materialWork = 0d;
        long totalUnits = 0L;
        for (Map.Entry<String, Integer> requirement : construction.materials().entrySet()) {
            ContentCatalog.ItemDefinition item = checkedCatalog.findItem(requirement.getKey());
            if (item == null) {
                throw new IllegalArgumentException(
                        "Construction material references unknown item: " + requirement.getKey());
            }
            int amount = requirement.getValue();
            if (amount <= 0) {
                throw new IllegalArgumentException("Construction material amount must be positive");
            }
            totalUnits = saturatedAdd(totalUnits, amount);
            materialWork += amount * handlingWeight(item.category());
            if (!Double.isFinite(materialWork)) {
                throw new IllegalArgumentException("Construction material work overflow");
            }
        }

        double baseSetupSeconds = construction.buildSeconds();
        double materialAssemblySeconds = materialWork / BASE_ASSEMBLY_WORK_PER_SECOND;
        double totalSeconds = baseSetupSeconds + materialAssemblySeconds;
        if (!Double.isFinite(totalSeconds) || totalSeconds <= 0d) {
            throw new IllegalArgumentException("Calculated construction duration is invalid");
        }
        return new Estimate(
                totalUnits,
                materialWork,
                baseSetupSeconds,
                materialAssemblySeconds,
                totalSeconds);
    }

    /**
     * Returns normalized construction work for one unit of a cargo category.
     *
     * <p>Raw structural material is the 1.0 reference. Tanked energy/fluid cargo is easier to
     * transfer in bulk, while finished goods represent assemblies requiring more placement,
     * integration and testing work.</p>
     *
     * @param category physical cargo category
     * @return strictly positive normalized handling/fabrication work
     */
    public static double handlingWeight(ItemCategory category) {
        return switch (Objects.requireNonNull(category, "Construction item category not set")) {
            case MATERIAL -> 1d;
            case GAS_LIQUID -> 0.55d;
            case FINISHED_GOODS -> 1.60d;
        };
    }

    private static long saturatedAdd(long current, int amount) {
        return Long.MAX_VALUE - current < amount ? Long.MAX_VALUE : current + amount;
    }

    /**
     * Read-only deterministic duration breakdown for UI, balancing and acceptance tests.
     *
     * @param totalMaterialUnits total required whole item units
     * @param materialWorkUnits weighted handling/fabrication work
     * @param baseSetupSeconds authored archetype setup/complexity allowance
     * @param materialAssemblySeconds time caused specifically by the material bill
     * @param totalSeconds final calculated build duration after all materials are present
     */
    public record Estimate(
            long totalMaterialUnits,
            double materialWorkUnits,
            double baseSetupSeconds,
            double materialAssemblySeconds,
            double totalSeconds) {
        /** Validates a finite, internally consistent duration breakdown. */
        public Estimate {
            if (totalMaterialUnits <= 0L
                    || !Double.isFinite(materialWorkUnits) || materialWorkUnits <= 0d
                    || !Double.isFinite(baseSetupSeconds) || baseSetupSeconds <= 0d
                    || !Double.isFinite(materialAssemblySeconds) || materialAssemblySeconds <= 0d
                    || !Double.isFinite(totalSeconds) || totalSeconds <= 0d) {
                throw new IllegalArgumentException("Construction duration estimate is invalid");
            }
            double expected = baseSetupSeconds + materialAssemblySeconds;
            if (Math.abs(expected - totalSeconds) > 1e-9d * Math.max(1d, totalSeconds)) {
                throw new IllegalArgumentException("Construction duration estimate is inconsistent");
            }
        }
    }
}
