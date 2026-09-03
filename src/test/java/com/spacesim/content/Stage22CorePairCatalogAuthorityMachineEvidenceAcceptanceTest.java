package com.spacesim.content;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** B00 M22.6 cross-package authority/content audit expressed through the common evidence batch. */
class Stage22CorePairCatalogAuthorityMachineEvidenceAcceptanceTest {
    @Test
    void b00CorePairAuditUsesAcceptedValidatorsAndStableAuthorityBindings() {
        var schedule = Stage22CorePairExperimentProtocol.pairedSchedule(1);
        var first = run(schedule);
        var replay = run(schedule);

        assertEquals(1, first.pairedSeedCount());
        assertEquals(2, first.runCount());
        assertEquals(0, first.hardRuleBreachCount());
        assertEquals(1d, first.guardMetricMeans().get("stable_core_pair_ids"));
        assertEquals(1d, first.guardMetricMeans().get("equal_role_floor"));
        assertEquals(1d, first.guardMetricMeans().get("all_scenarios_required"));
        assertEquals(1d, first.guardMetricMeans().get("package_fingerprints_distinct"));
        assertEquals(1d, first.guardMetricMeans().get("no_faction_name_shortcut"));
        assertFalse(first.evidenceFingerprint().isBlank());
        assertEquals(first.evidenceFingerprint(), replay.evidenceFingerprint());
    }

    private static Stage22CorePairMachineEvidenceBatch.ResultVector run(
            List<Stage22CorePairExperimentProtocol.RunCoordinate> schedule) {
        return Stage22CorePairMachineEvidenceBatch.runScenario(
                "B00",
                "cross_package_catalog_authority_audit",
                "stage22.current",
                schedule,
                (scenario, variant, profile, coordinate) -> observe());
    }

    private static Stage22CorePairMachineEvidenceBatch.ObservationPayload observe() {
        Stage22CorePairBalanceEvidence.PairEvidence evidence = Stage22CorePairBalanceEvidence.deriveCurrent();
        Stage22EmpirePackageValidator.ValidationReport empire = Stage22EmpirePackageValidator.validateDefault();
        Stage22IndustrialUnionPackageValidator.ValidationReport union =
                Stage22IndustrialUnionPackageValidator.validateDefault();

        boolean stableIds = evidence.empire().stableFactionId().equals(Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID)
                && evidence.industrialUnion().stableFactionId().equals(Stage22CorePairBalanceEvidence.UNION_FACTION_ID);
        boolean equalRoleFloor = evidence.empire().roleFamilyCount() == 9
                && evidence.industrialUnion().roleFamilyCount() == 9
                && empire.familyMetrics().size() == union.familyMetrics().size();
        boolean scenariosRequired = evidence.card().requiredScenarios().size() == 21
                && Stage22CorePairBalanceCatalog.scenarios().stream()
                        .allMatch(value -> value.requirement() == Stage22CorePairBalanceCatalog.Requirement.REQUIRED);
        boolean distinctPackages = !empire.packageFingerprint().equals(union.packageFingerprint())
                && empire.packageFingerprint().length() == 64
                && union.packageFingerprint().length() == 64;
        boolean noShortcut = evidence.card().prohibitedShortcut().contains("faction-name")
                && !evidence.card().prohibitedShortcut().isBlank();

        ArrayList<String> breaches = new ArrayList<>();
        if (!stableIds) breaches.add("core_pair_stable_identity_drift");
        if (!equalRoleFloor) breaches.add("core_pair_role_floor_drift");
        if (!scenariosRequired) breaches.add("core_pair_required_scenario_coverage_drift");
        if (!distinctPackages) breaches.add("core_pair_package_fingerprint_drift");
        if (!noShortcut) breaches.add("core_pair_prohibited_shortcut_contract_drift");

        return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(
                Map.of(
                        "required_scenario_count", (double) evidence.card().requiredScenarios().size(),
                        "empire_role_family_count", (double) evidence.empire().roleFamilyCount(),
                        "union_role_family_count", (double) evidence.industrialUnion().roleFamilyCount(),
                        "empire_primary_fit_mass_kg", evidence.empire().totalPrimaryFittedMassKg(),
                        "union_primary_fit_mass_kg", evidence.industrialUnion().totalPrimaryFittedMassKg()),
                Map.of(
                        "stable_core_pair_ids", stableIds ? 1d : 0d,
                        "equal_role_floor", equalRoleFloor ? 1d : 0d,
                        "all_scenarios_required", scenariosRequired ? 1d : 0d,
                        "package_fingerprints_distinct", distinctPackages ? 1d : 0d,
                        "no_faction_name_shortcut", noShortcut ? 1d : 0d),
                breaches);
    }
}
