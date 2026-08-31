package com.spacesim.content;

import com.spacesim.content.Stage22ContentGovernanceCatalog.AssetStatus;
import com.spacesim.content.Stage22ContentGovernanceCatalog.BindingKind;
import com.spacesim.content.Stage22ContentGovernanceCatalog.ContentDisposition;
import com.spacesim.content.Stage22ContentGovernanceCatalog.ContentMaturity;
import com.spacesim.content.Stage22ContentGovernanceCatalog.IdentityClass;
import com.spacesim.content.Stage22ContentGovernanceCatalog.IdentityDisposition;
import com.spacesim.content.Stage22ContentGovernanceCatalog.SourceMaturity;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22ContentGovernanceLoaderTest {
    @Test
    void defaultBaselineLocksEntryInventoryCoreBindingsGeneratedCompatibilityAndAlphaFloor() {
        Stage22ContentGovernanceCatalog catalog = Stage22ContentGovernanceLoader.loadDefault();

        assertEquals(1, catalog.getSchemaVersion());
        assertEquals(20, catalog.getSources().size());
        assertEquals(13, catalog.getHardcodedDefinitions().size());
        assertEquals(10, catalog.getFactionIdentities().size());
        assertEquals(64, catalog.getFingerprint().length());

        assertEquals("core.empire", catalog.canonicalPackageKey("faction.imperial_directorate"));
        assertEquals("Империя", catalog.canonicalDisplayName("faction.imperial_directorate", "legacy"));
        assertEquals("core.industrial_union", catalog.canonicalPackageKey("faction.industrial_combine"));
        assertEquals("Индустриальный Союз", catalog.canonicalDisplayName("faction.industrial_combine", "legacy"));

        assertNull(catalog.canonicalPackageKey("faction.frontier_union"));
        assertNull(catalog.canonicalPackageKey("faction.free_ports"));
        assertNull(catalog.canonicalPackageKey("faction.research_consortium"));
        assertNull(catalog.canonicalPackageKey("faction.alpha"));
        assertNull(catalog.canonicalPackageKey("faction.beta"));
        assertEquals("fallback", catalog.canonicalDisplayName("faction.player.dynamic", "fallback"));

        assertEquals(IdentityClass.MINOR_AUTHORED,
                catalog.findFactionIdentity("faction.neutral").identityClass());
        assertEquals(IdentityClass.TRANSNATIONAL_NETWORK,
                catalog.findFactionIdentity("faction.trade_league").identityClass());
        assertEquals(IdentityClass.MINOR_AUTHORED,
                catalog.findFactionIdentity("faction.miners").identityClass());
        assertEquals(IdentityClass.WORLD_GENERATED,
                catalog.findFactionIdentity("faction.alpha").identityClass());
        assertEquals(IdentityClass.WORLD_GENERATED,
                catalog.findFactionIdentity("faction.beta").identityClass());
        assertTrue(catalog.getFactionIdentities().stream()
                .allMatch(identity -> identity.disposition() == IdentityDisposition.PRESERVE));
        assertTrue(catalog.getFactionIdentities().stream()
                .allMatch(identity -> identity.targetStableFactionId() == null));

        assertEquals(EnumSet.allOf(BindingKind.class),
                EnumSet.copyOf(catalog.getAuthoringContract().requiredBindingKinds()));
        assertEquals(EnumSet.allOf(AssetStatus.class),
                EnumSet.copyOf(catalog.getAuthoringContract().requiredAssetStatuses()));
        assertEquals(EnumSet.allOf(ContentMaturity.class),
                EnumSet.copyOf(catalog.getAuthoringContract().requiredContentMaturities()));
        assertEquals(Set.of("ru", "en"), Set.copyOf(catalog.getAuthoringContract().localizationLanguages()));
        assertTrue(catalog.getAuthoringContract().requireProvenance());
        assertTrue(catalog.getAuthoringContract().requireFitFingerprintVisualBinding());

        var floor = catalog.getAlphaFloor();
        assertEquals(2, floor.productionCoreFactions());
        assertEquals(0, floor.requiredPostCoreFactions());
        assertEquals(6, floor.militaryBaseHullsPerCoreFaction());
        assertEquals(3, floor.civilianSupportBaseHullsPerCoreFaction());
        assertEquals(8, floor.sharedCivilianHulls());
        assertEquals(10, floor.stationExteriorRoles());
        assertEquals(3, floor.signatureStationsPerCoreFaction());
        assertEquals(6, floor.recurringNamedNpcsPerCoreFaction());
        assertEquals(24, floor.generatedNpcRoleArchetypes());
        assertEquals(48, floor.gameWideMissionTemplates());
        assertEquals(20, floor.specialLocationArchetypes());
    }

    @Test
    void repeatedDefaultLoadsHaveIdenticalGovernanceFingerprint() {
        assertEquals(
                Stage22ContentGovernanceLoader.loadDefault().getFingerprint(),
                Stage22ContentGovernanceLoader.loadDefault().getFingerprint());
    }

    @Test
    void everyProvisionalSourceHasExplicitNonPreserveDispositionAndStage18RemainsFoundation() {
        Stage22ContentGovernanceCatalog catalog = Stage22ContentGovernanceLoader.loadDefault();

        assertTrue(catalog.getSources().stream()
                .filter(source -> source.maturity() == SourceMaturity.PROVISIONAL)
                .allMatch(source -> source.defaultDisposition() != ContentDisposition.PRESERVE));
        assertTrue(catalog.getSources().stream()
                .filter(source -> source.resourcePath().contains("stage18-"))
                .allMatch(source -> source.maturity() == SourceMaturity.PRODUCTION_FOUNDATION
                        && source.defaultDisposition() == ContentDisposition.PRESERVE));

        Set<ContentDisposition> provisionalDecisions = catalog.getSources().stream()
                .filter(source -> source.maturity() == SourceMaturity.PROVISIONAL)
                .map(Stage22ContentGovernanceCatalog.SourceDefinition::defaultDisposition)
                .collect(Collectors.toSet());
        assertFalse(provisionalDecisions.isEmpty());
        assertTrue(provisionalDecisions.contains(ContentDisposition.REAUTHOR));
        assertEquals(ContentDisposition.REPLACE,
                catalog.findHardcodedDefinition("module.test_stage21_strategic_ftl_v1").disposition());
        assertEquals(ContentDisposition.PRESERVE,
                catalog.findHardcodedDefinition("faction.alpha").disposition());
        assertEquals(ContentDisposition.PRESERVE,
                catalog.findHardcodedDefinition("faction.beta").disposition());
    }

    @Test
    void modelRejectsUnsafeAliasAndImplicitlyPreservedProvisionalContent() {
        assertThrows(IllegalArgumentException.class, () -> new Stage22ContentGovernanceCatalog.FactionIdentityDefinition(
                "faction.legacy",
                IdentityClass.LEGACY_COMPATIBILITY,
                IdentityDisposition.ALIAS,
                null,
                null,
                null,
                "v1",
                "preserve",
                "fail",
                "unsafe missing alias target"));

        assertThrows(IllegalArgumentException.class, () -> new Stage22ContentGovernanceCatalog.SourceDefinition(
                "data/content/test-v1.json",
                "test",
                SourceMaturity.PROVISIONAL,
                ContentDisposition.PRESERVE,
                "unsafe implicit promotion"));
    }
}
