package com.spacesim.content;

import com.spacesim.LargeDemoGalaxyFactory;
import com.spacesim.persistence.Stage22FactionProfileBindingCodec;
import com.spacesim.persistence.Stage22FactionProfileBindingState;
import com.spacesim.world.FactionIdentityResolver;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** M22.3 systemic-profile, character-overlay and persistence acceptance. */
class Stage22EmpireProfilePersistenceAcceptanceTest {
    @Test
    void promotedEmpireProfileBindsNineRolesWhileUnionRemainsSeed() {
        Stage22FactionProfileCatalog baseline = Stage22FactionProfileLoader.loadDefault();
        Stage22FactionProfileCatalog promoted = Stage22EmpireFactionProfileCatalog.loadDefault();
        var empire = promoted.findProfileForFaction("faction.imperial_directorate");
        var union = promoted.findProfileForFaction("faction.industrial_combine");

        assertNotEquals(baseline.fingerprint(), promoted.fingerprint());
        assertEquals(Stage22EmpireFactionProfileCatalog.CATALOG_VERSION, promoted.catalogVersion());
        assertEquals(9, promoted.findManifest(empire.authoredContentManifestRef()).roleBindings().size());
        assertEquals(Set.of(
                        "role.military.corvette",
                        "role.military.frigate",
                        "role.military.destroyer",
                        "role.military.cruiser",
                        "role.military.battleship",
                        "role.military.carrier",
                        "role.support.freight",
                        "role.support.tanker_replenishment",
                        "role.support.fleet_logistics_repair_salvage"),
                promoted.findManifest(empire.authoredContentManifestRef()).roleBindings().stream()
                        .map(Stage22FactionProfileCatalog.RoleProductionBindingDefinition::roleId)
                        .collect(Collectors.toSet()));
        assertEquals(Stage22ContentGovernanceCatalog.ContentMaturity.SEED,
                promoted.findManifest(union.authoredContentManifestRef()).maturity());
        assertEquals(0, promoted.findManifest(union.authoredContentManifestRef()).roleBindings().size());
    }

    @Test
    void existingGenericProfileSidecarRoundTripsPromotedEmpireFingerprintDeterministically() {
        Stage22FactionProfileCatalog promoted = Stage22EmpireFactionProfileCatalog.loadDefault();
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        var world = LargeDemoGalaxyFactory.createState(22_303L, content);
        FactionIdentityResolver resolver = FactionIdentityResolver.createDefault(content, world.factionIdentities());

        Stage22FactionProfileBindingState captured = Stage22FactionProfileBindingState.capture(promoted, resolver);
        byte[] first = Stage22FactionProfileBindingCodec.encode(captured);
        Stage22FactionProfileBindingState decoded = Stage22FactionProfileBindingCodec.decode(first);
        decoded.validateAgainst(promoted, resolver);
        byte[] second = Stage22FactionProfileBindingCodec.encode(decoded);

        assertArrayEquals(first, second);
        assertEquals(promoted.fingerprint(), decoded.catalogFingerprint());
        assertEquals(Stage22EmpireFactionProfileCatalog.CATALOG_VERSION, decoded.catalogVersion());
        assertThrows(IllegalArgumentException.class,
                () -> decoded.validateAgainst(Stage22FactionProfileLoader.loadDefault(), resolver));
    }

    @Test
    void everyRecurringEmpireNpcUsesAValidatedCharacterMasterOverlay() {
        Stage22EmpirePackageCatalog empire = Stage22EmpirePackageLoader.loadDefault();
        Stage22EmpireCharacterLineup.Catalog lineup = Stage22EmpireCharacterLineup.loadDefault();

        assertEquals(64, lineup.fingerprint().length());
        for (Stage22EmpirePackageCatalog.RecurringNpcDefinition npc : empire.recurringNpcs()) {
            assertNotNull(lineup.findOverlay(npc.characterOverlayId()), npc.id());
        }
        assertEquals(9, lineup.overlays().size());
    }
}
