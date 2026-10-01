package com.spacesim.release;

import com.spacesim.content.Stage22ContentGovernanceCatalog.ContentDisposition;
import com.spacesim.content.Stage22ContentGovernanceCatalog.SourceMaturity;
import com.spacesim.content.Stage22ContentInventory;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.content.weapon.Stage22CorePairWeaponRuntimeCatalogLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Machine gate for the Stage-23A RC scope freeze and release-governance contract.
 */
class Stage23AReleaseGovernanceContractTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void mustShipManifestHasAcceptanceOwnersAndEvidence() throws IOException {
        List<Map<String, String>> rows = readTsv(
                "docs/release/rc_feature_manifest_v1.tsv",
                List.of("id", "tier", "player_surface", "acceptance_owner", "evidence_mode", "baseline_evidence"));

        Set<String> tiers = new HashSet<>();
        Set<String> mustShip = new HashSet<>();
        for (Map<String, String> row : rows) {
            String tier = row.get("tier");
            assertTrue(Set.of("MUST_SHIP", "MAY_SHIP", "POST_RC").contains(tier));
            tiers.add(tier);
            if ("MUST_SHIP".equals(tier)) {
                mustShip.add(row.get("id"));
                assertFalse(row.get("acceptance_owner").isBlank());
                assertFalse(row.get("evidence_mode").isBlank());
                assertFalse(row.get("baseline_evidence").isBlank());
            }
        }

        assertEquals(Set.of("MUST_SHIP", "MAY_SHIP", "POST_RC"), tiers);
        assertTrue(mustShip.containsAll(Set.of(
                "campaign_boot",
                "local_navigation",
                "intersystem_travel",
                "trade_markets",
                "mining_extraction",
                "industry_construction",
                "fitting_engineering",
                "tactical_combat",
                "fleet_command",
                "carrier_operations",
                "faction_management",
                "diplomacy_crisis_war",
                "territory_peace_recovery",
                "npc_missions_reputation",
                "discovery_intelligence",
                "save_load_continuation",
                "production_ui_navigation",
                "accessibility_localization",
                "onboarding",
                "final_presentation",
                "performance_long_session",
                "clean_windows_package",
                "rc_campaign_regression",
                "exact_rc_artifact")));
    }

    @Test
    void releaseGateIssuesHaveOwnerSeverityAndClosureEvidence() throws IOException {
        List<Map<String, String>> rows = readTsv(
                "docs/release/rc_known_issues_v1.tsv",
                List.of("issue", "kind", "scope", "severity", "release_gate", "acceptance_owner",
                        "player_impact", "workaround", "closure_evidence"));

        Map<String, Map<String, String>> byIssue = new HashMap<>();
        for (Map<String, String> row : rows) {
            assertTrue(Set.of("BLOCKER", "CRITICAL", "MAJOR", "MINOR", "COSMETIC")
                    .contains(row.get("severity")));
            byIssue.put(row.get("issue"), row);
            if ("YES".equals(row.get("release_gate"))) {
                assertEquals("MUST_SHIP", row.get("scope"));
                assertFalse(row.get("acceptance_owner").isBlank());
                assertFalse(row.get("closure_evidence").isBlank());
            }
        }

        assertEquals("YES", byIssue.get("#370").get("release_gate"));
        assertEquals("YES", byIssue.get("#375").get("release_gate"));
        assertEquals("POST_RC", byIssue.get("#361").get("scope"));
        assertEquals("POST_RC", byIssue.get("#371").get("scope"));
    }

    @Test
    void provisionalInventoryCannotLeakIntoProductionCorePair() {
        Stage22ContentInventory inventory = Stage22ContentInventory.buildDefault();
        inventory.requireExplicitProvisionalDisposition();

        Set<String> provisionalIds = new HashSet<>();
        inventory.definitions().stream()
                .filter(definition -> definition.maturity() == SourceMaturity.PROVISIONAL)
                .forEach(definition -> {
                    provisionalIds.add(definition.id());
                    assertTrue(
                            definition.disposition() == ContentDisposition.REAUTHOR
                                    || definition.disposition() == ContentDisposition.REPLACE
                                    || definition.disposition() == ContentDisposition.RETIRE,
                            () -> "Provisional ID has RC-unsafe disposition: " + definition.id()
                                    + " -> " + definition.disposition());
                });
        assertFalse(provisionalIds.isEmpty());

        Set<String> productionIds = new HashSet<>();
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        engineering.getMaterials().forEach(value -> productionIds.add(value.id()));
        engineering.getResponseSurfaces().forEach(value -> productionIds.add(value.id()));
        engineering.getProtectionStacks().forEach(value -> productionIds.add(value.id()));
        engineering.getHulls().forEach(value -> productionIds.add(value.id()));
        engineering.getModules().forEach(value -> productionIds.add(value.id()));
        engineering.getDemonstratorFits().forEach(value -> productionIds.add(value.id()));

        var weapons = Stage22CorePairWeaponRuntimeCatalogLoader.loadCombined();
        weapons.ammunition().getKineticAmmunition().forEach(value -> productionIds.add(value.id()));
        weapons.ammunition().getGuidedAmmunition().forEach(value -> productionIds.add(value.id()));

        TreeSet<String> leaked = new TreeSet<>(provisionalIds);
        leaked.retainAll(productionIds);
        assertEquals(Collections.emptySet(), leaked,
                "Stage-17.5/19 provisional IDs must not enter the Stage-22 production core pair");
    }

    @Test
    void blockerAndFeatureIssueIntakeRemainSeparated() throws IOException {
        String blocker = Files.readString(ROOT.resolve(".github/ISSUE_TEMPLATE/rc_blocker.md"));
        String feature = Files.readString(ROOT.resolve(".github/ISSUE_TEMPLATE/feature_request.md"));

        assertTrue(blocker.contains("Severity: BLOCKER / CRITICAL / MAJOR / MINOR / COSMETIC"));
        assertTrue(blocker.contains("Scope: MUST_SHIP"));
        assertTrue(blocker.contains("Required closure evidence"));
        assertTrue(blocker.contains("Do not add unrelated MAY_SHIP or POST_RC feature work"));

        assertTrue(feature.contains("Requested tier: MAY_SHIP / POST_RC"));
        assertTrue(feature.contains("Why this is not a blocker fix"));
        assertTrue(feature.contains("Do not combine this feature with an unrelated RC blocker/critical fix"));
    }

    @Test
    void versionIdentityAndReleaseNotesAreFrozen() throws IOException {
        String versioning = Files.readString(ROOT.resolve("docs/release/rc_versioning_v1.md"));
        String notes = Files.readString(ROOT.resolve("docs/release/release_notes_template.md"));
        String roadmap = Files.readString(ROOT.resolve("docs/stage23_release_candidate_roadmap.md"));

        for (String token : List.of(
                "0.7.0-rc.N",
                "se-content-1",
                "core=4",
                "envelope=2",
                "campaign=4",
                "m22.8.generated-campaign.v4",
                "se-gen-1")) {
            assertTrue(versioning.contains(token), () -> "Missing version identity token: " + token);
        }

        for (String token : List.of(
                "Application:",
                "Source SHA:",
                "Content fingerprint:",
                "Generator profile:",
                "Package SHA-256:")) {
            assertTrue(notes.contains(token), () -> "Missing release-note identity field: " + token);
        }

        assertTrue(roadmap.contains("Status: COMPLETE."));
        assertTrue(roadmap.contains("**Status: NEXT.**"));
    }

    private static List<Map<String, String>> readTsv(
            String path,
            List<String> expectedHeader) throws IOException {
        List<String> lines = Files.readAllLines(ROOT.resolve(path)).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        assertFalse(lines.isEmpty(), "TSV must not be empty: " + path);

        List<String> header = List.of(lines.get(0).split("\\t", -1));
        assertEquals(expectedHeader, header, "Unexpected TSV header: " + path);

        ArrayList<Map<String, String>> rows = new ArrayList<>();
        for (int line = 1; line < lines.size(); line++) {
            String[] values = lines.get(line).split("\\t", -1);
            assertEquals(header.size(), values.length, "Column count at " + path + ":" + (line + 1));
            HashMap<String, String> row = new HashMap<>();
            for (int column = 0; column < header.size(); column++) {
                row.put(header.get(column), values[column]);
            }
            assertNotNull(row.get("id") != null ? row.get("id") : row.get("issue"));
            rows.add(Map.copyOf(row));
        }
        return List.copyOf(rows);
    }
}
