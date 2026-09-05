package com.spacesim.content;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22CorePairCommittedAuthorityAcceptanceTest {
    @Test
    void exactCoreFitsContinueAcrossCommittedEncountersWithoutRestoringSpentResources() {
        var rows = new ArrayList<Object>();
        for (var permutation : Stage22CorePairExperimentProtocol.Permutation.values()) {
            var coordinate = new Stage22CorePairExperimentProtocol.RunCoordinate(
                    Stage22CorePairExperimentProtocol.FIRST_SEED,
                    permutation);
            var direct = Stage22CorePairEncounterContinuationProbe.run(coordinate, false);
            var reloaded = Stage22CorePairEncounterContinuationProbe.run(coordinate, true);
            assertEquals(direct, reloaded);
            assertEquals(3, direct.size());
            assertTrue(direct.get(2).combatants().stream()
                    .allMatch(actor -> actor.runtimeState().consumables().ammunitionCount() < 120L));
            rows.add(Map.of("coordinate", coordinate, "encounters", direct));
        }
        Stage22CorePairEvidenceArchive.write("B01-committed-core-encounters", rows,
                "Full Stage-19 stack with exact core catalogs; three bounded seeded/mirrored exchanges and engine-state saves at committed boundaries. Detached result validation is tested; a generated-world campaign and in-flight body persistence are separate boundaries.");
    }

    @Test
    void actualCoreParticipantsRetainTreatyAccessAndBreachConsequencesAcrossBinarySaves() {
        var rows = new ArrayList<Stage22CorePairTreatyProbe.Result>();
        for (var permutation : Stage22CorePairExperimentProtocol.Permutation.values()) {
            var result = Stage22CorePairTreatyProbe.run(Stage22CorePairExperimentProtocol.FIRST_SEED, permutation);
            assertTrue(result.valid(), result.toString());
            rows.add(result);
        }
        Stage22CorePairEvidenceArchive.write("B16-core-treaty-access", rows,
                "Actual core faction identities, mutual market access/customs commands and binary persistence. Initial policies are declared; physical trade-volume loss and recovery after the access shock still need campaign evidence.");
    }
}