package com.spacesim.content;

import com.spacesim.content.Stage22ContentGovernanceCatalog.IdentityClass;
import com.spacesim.world.generation.Stage20RepresentativeGeneratedWorldProbeProfileV3;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22GeneratedWorldIdentityGovernanceAcceptanceTest {
    @Test
    void currentPlayableGeneratedWorldPolicyIdentitiesAreExplicitCompatibilityEntries() {
        Stage22ContentGovernanceCatalog governance = Stage22ContentGovernanceLoader.loadDefault();
        var profile = Stage20RepresentativeGeneratedWorldProbeProfileV3.deriveCurrent();
        Set<String> generatedIds = Set.copyOf(profile.inputs().acceptance().stableFactionIds());

        assertEquals(Set.of("faction.alpha", "faction.beta"), generatedIds);
        for (String stableId : generatedIds) {
            var definition = governance.findFactionIdentity(stableId);
            assertEquals(IdentityClass.WORLD_GENERATED, definition.identityClass(), stableId);
            assertNull(definition.canonicalPackageKey(), stableId);
            assertTrue(definition.saveBehavior().contains("Preserve exact stable ID"), stableId);
            assertTrue(definition.collisionBehavior().contains("Must not bind"), stableId);
            assertEquals(Stage22ContentGovernanceCatalog.ContentDisposition.PRESERVE,
                    governance.findHardcodedDefinition(stableId).disposition(), stableId);
        }
    }

    @Test
    void identityEvidenceCoversEveryGovernedIdentityAndIsDeterministic() {
        Stage22ContentGovernanceCatalog governance = Stage22ContentGovernanceLoader.loadDefault();
        Stage22FactionIdentityEvidence first = Stage22FactionIdentityEvidence.loadDefault();
        Stage22FactionIdentityEvidence second = Stage22FactionIdentityEvidence.loadDefault();

        assertEquals(10, first.records().size());
        assertEquals(first.records(), second.records());
        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(64, first.fingerprint().length());
        assertEquals(
                governance.getFactionIdentities().stream()
                        .map(Stage22ContentGovernanceCatalog.FactionIdentityDefinition::stableFactionId)
                        .sorted().toList(),
                first.records().stream().map(Stage22FactionIdentityEvidence.EvidenceRecord::stableFactionId)
                        .sorted().toList());
        assertTrue(first.records().stream().allMatch(record -> record.telemetryEvent().startsWith("stage22.identity.")));
        assertTrue(first.records().stream().allMatch(record -> !record.fixture().isBlank()));
    }
}
