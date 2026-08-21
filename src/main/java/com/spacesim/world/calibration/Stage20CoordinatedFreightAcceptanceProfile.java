package com.spacesim.world.calibration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Explicit Stage-20E production policy for bounded coordinated freight acceptance.
 *
 * <p>The physical fleet capacity is not authored here: it is the independently derived
 * {@link Stage20BootstrapFreightCapacityRequirementProfile}. This profile only binds that physical
 * requirement to an explicit deterministic exact-search work budget. Exhausting the work budget is
 * an unresolved-authority outcome, never physical infeasibility.</p>
 *
 * <p>The v1 search budget is promoted from the verified resolved-freight fixed-corpus evidence after
 * the same 2,000-node/commodity bound reproduced the accepted physical frontier closure with zero
 * unresolved cases in the representative 1..16 corpus. That observation justifies a bounded
 * production computation policy; it does not change the physical feasible set and is not an
 * accepted-seed-rate target.</p>
 *
 * @param version stable production-policy version
 * @param freightCapacityRequirement independently derived physical capacity authority
 * @param searchNodeBudgetPerCommodity deterministic exact-search work bound per essential commodity
 * @param evidenceIds deterministic provenance identifiers
 * @param stage22ReviewRequired whether the provisional physical sizing remains subject to Stage-22 review
 */
public record Stage20CoordinatedFreightAcceptanceProfile(
        String version,
        Stage20BootstrapFreightCapacityRequirementProfile freightCapacityRequirement,
        int searchNodeBudgetPerCommodity,
        List<String> evidenceIds,
        boolean stage22ReviewRequired) {

    /** Current deterministic coordinated-freight production acceptance policy. */
    public static final String CURRENT_VERSION = "stage20e.coordinated-freight-acceptance-profile.v1";
    /** Verified bounded search work used by the production coordinated-freight gate. */
    public static final int VERIFIED_SEARCH_NODE_BUDGET_PER_COMMODITY = 2_000;

    /**
     * Validates and freezes one explicit production acceptance policy.
     *
     * @param version stable policy version
     * @param freightCapacityRequirement independently derived physical capacity requirement
     * @param searchNodeBudgetPerCommodity positive deterministic search work bound
     * @param evidenceIds provenance identifiers
     * @param stage22ReviewRequired Stage-22 review boundary inherited from physical sizing
     */
    public Stage20CoordinatedFreightAcceptanceProfile {
        version = requireText(version, "version");
        Objects.requireNonNull(freightCapacityRequirement, "freightCapacityRequirement");
        if (searchNodeBudgetPerCommodity <= 0) {
            throw new IllegalArgumentException("searchNodeBudgetPerCommodity must be positive");
        }
        ArrayList<String> evidence = new ArrayList<>(Objects.requireNonNull(evidenceIds, "evidenceIds"));
        if (evidence.isEmpty() || evidence.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("evidenceIds must be non-empty and contain no blanks");
        }
        evidence.replaceAll(String::strip);
        evidence.sort(String::compareTo);
        for (int index = 1; index < evidence.size(); index++) {
            if (evidence.get(index - 1).equals(evidence.get(index))) {
                throw new IllegalArgumentException("evidenceIds must be unique");
            }
        }
        evidenceIds = List.copyOf(evidence);
        if (stage22ReviewRequired != freightCapacityRequirement.stage22ReviewRequired()) {
            throw new IllegalArgumentException(
                    "coordinated freight policy must preserve the physical capacity Stage-22 review boundary");
        }
    }

    /**
     * Derives the current production policy without consulting generated seed outcomes at runtime.
     *
     * @return deterministic current coordinated-freight acceptance policy
     */
    public static Stage20CoordinatedFreightAcceptanceProfile deriveCurrent() {
        Stage20BootstrapFreightCapacityRequirementProfile capacity =
                Stage20BootstrapFreightCapacityRequirementProfile.deriveCurrent();
        return new Stage20CoordinatedFreightAcceptanceProfile(
                CURRENT_VERSION,
                capacity,
                VERIFIED_SEARCH_NODE_BUDGET_PER_COMMODITY,
                List.of(
                        "capacity:" + capacity.version(),
                        "contract:docs/stage20_physical_world_generation_plan.md:search-uncertainty-not-infeasibility",
                        "verified-corpus:stage20e.resolved-freight-acceptance-corpus-diagnostics.v1:fixed-1..16:budget-2000:unresolved-0"),
                capacity.stage22ReviewRequired());
    }

    /** @return derived finite physical freight capacity per ordinary accepted faction start */
    public int requiredFreighterCountPerFactionStart() {
        return freightCapacityRequirement.requiredFreighterCountPerFactionStart();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }
}
