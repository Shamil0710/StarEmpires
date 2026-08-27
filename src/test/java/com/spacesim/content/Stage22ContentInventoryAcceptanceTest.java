package com.spacesim.content;

import com.spacesim.content.Stage22ContentGovernanceCatalog.ContentDisposition;
import com.spacesim.content.Stage22ContentGovernanceCatalog.SourceMaturity;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22ContentInventoryAcceptanceTest {
    @Test
    void defaultInventoryCoversGovernedSourcesAndHardcodedStage21Bridge() {
        Stage22ContentInventory inventory = Stage22ContentInventory.buildDefault();

        assertEquals(20, inventory.sourceDigests().size());
        assertFalse(inventory.definitions().isEmpty());
        assertFalse(inventory.references().isEmpty());
        assertEquals(64, inventory.fingerprint().length());

        assertEquals(1, inventory.definitions("module.test_stage21_strategic_ftl_v1").size());
        assertEquals(ContentDisposition.REPLACE,
                inventory.definitions("module.test_stage21_strategic_ftl_v1").get(0).disposition());

        Set<String> strategicFits = Set.of(
                "fit.test_doctrine_a_kinetic_v1.stage21_strategic_v1",
                "fit.test_doctrine_b_missile_v1.stage21_strategic_v1",
                "fit.test_doctrine_c_beam_v1.stage21_strategic_v1",
                "fit.test_doctrine_d_defensive_ew_v1.stage21_strategic_v1",
                "fit.test_doctrine_e_balanced_v1.stage21_strategic_v1");
        for (String fitId : strategicFits) {
            assertEquals(1, inventory.definitions(fitId).size(), fitId);
            assertEquals(ContentDisposition.REPLACE, inventory.definitions(fitId).get(0).disposition(), fitId);
        }
        assertTrue(inventory.referencesTo("module.test_stage21_strategic_ftl_v1").size() >= strategicFits.size());

        inventory.requireExplicitProvisionalDisposition();
        assertTrue(inventory.definitions().stream()
                .filter(definition -> definition.maturity() == SourceMaturity.PROVISIONAL)
                .allMatch(definition -> definition.disposition() != ContentDisposition.PRESERVE));
    }

    @Test
    void repeatedInventoryBuildsAreDeterministicAndCoverEveryGovernedSourceDigest() {
        Stage22ContentInventory first = Stage22ContentInventory.buildDefault();
        Stage22ContentInventory second = Stage22ContentInventory.buildDefault();

        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(first.definitions(), second.definitions());
        assertEquals(first.references(), second.references());
        assertEquals(first.sourceDigests(), second.sourceDigests());

        Set<String> governedSources = first.governance().getSources().stream()
                .map(Stage22ContentGovernanceCatalog.SourceDefinition::resourcePath)
                .collect(Collectors.toSet());
        Set<String> digestedSources = first.sourceDigests().stream()
                .map(Stage22ContentInventory.SourceDigest::source)
                .collect(Collectors.toSet());
        assertEquals(governedSources, digestedSources);
    }

    @Test
    void reverseReferenceReportKeepsUnknownTargetsVisibleInsteadOfInventingFallbackDefinitions() {
        Stage22ContentInventory inventory = Stage22ContentInventory.buildDefault();

        for (Stage22ContentInventory.ReferenceRecord unresolved : inventory.unresolvedReferences()) {
            assertTrue(inventory.definitions(unresolved.targetId()).isEmpty());
        }
        assertTrue(inventory.unresolvedReferences().stream()
                .noneMatch(reference -> reference.targetId().equals("faction.neutral")),
                "Authored faction references must resolve through the governed catalog rather than a fabricated fallback");
    }
}
