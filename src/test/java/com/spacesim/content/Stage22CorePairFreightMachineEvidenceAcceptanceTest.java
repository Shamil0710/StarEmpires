package com.spacesim.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** B05 finite freight loss and surviving-route delivery through the common Stage-18/20 authorities. */
class Stage22CorePairFreightMachineEvidenceAcceptanceTest {
    @Test
    void hubLossPreservesPhysicalCargoAccountingAcrossMirroringAndSaveContinuation() {
        var result = Stage22CorePairMachineEvidenceBatch.runScenario("B05", "finite_freight_loss",
                "two_routes.v1", Stage22CorePairExperimentProtocol.releaseCandidateSchedule(),
                (scenario, variant, profile, coordinate) -> Stage22CorePairFreightProbe.observe(coordinate));
        Stage22CorePairEvidenceArchive.write("B05-freight", result,
                "Controlled two-route cargo/load sensitivity sweep. Alternate transport already exists; no automatic in-flight reroute, salvage or replacement is claimed.");
        assertEquals(0, result.hardRuleBreachCount(), result.toString());
        assertEquals(200, result.runCount());
        assertEquals(1d, result.metricMeans().get("remaining_owned_freighters"));
        assertEquals(result.metricMeans().get("lost_cargo_kg"), result.metricMeans().get("delivered_cargo_kg"));
    }
}
