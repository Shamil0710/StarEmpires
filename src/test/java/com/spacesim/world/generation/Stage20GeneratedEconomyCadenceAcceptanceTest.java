package com.spacesim.world.generation;

import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.ProcessKind;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.RuntimeBridgeRequirement;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20GeneratedEconomyCadenceAcceptanceTest {
    @Test
    void acceptedOperationalWorldClosesEveryRoadmapCadenceWithoutHiddenRestock() {
        var fixture = Stage20OperationalIndustrialSpecializationProductionIntegrationTest
                .cadenceFixture();

        var report = Stage20GeneratedEconomyCadenceAcceptance.evaluate(
                fixture.resolved(), fixture.specialization());
        var repeated = Stage20GeneratedEconomyCadenceAcceptance.evaluate(
                fixture.resolved(), fixture.specialization());

        assertEquals(report, repeated);
        assertEquals(Stage20GeneratedEconomyCadenceAcceptance.CURRENT_VERSION, report.version());
        assertEquals(Stage20GeneratedEconomyCadenceAcceptance.Status.ACCEPTED, report.status());
        assertFalse(report.hiddenRestockUsed());
        assertFalse(report.extraction().isEmpty());
        assertTrue(report.extraction().stream().allMatch(value ->
                value.mineOutputKgPerSecond() > 0d
                        && value.reserveEnduranceSeconds() > 0d));
        assertTrue(report.generatedProcesses().stream().anyMatch(value ->
                value.processKind() == ProcessKind.REFINING));
        assertTrue(report.generatedProcesses().stream().anyMatch(value ->
                value.processKind() == ProcessKind.COMPONENT_MANUFACTURING));
        assertTrue(report.operationalProcesses().stream().allMatch(value ->
                value.aggregateInputConsumptionKgPerSecond() > 0d
                        && value.outputKgPerSecond() > 0d));
        assertTrue(report.freight().stream().allMatch(value ->
                value.allocatedFreighters() > 0
                        && value.roundTripCycleSeconds() > value.oneWayDeliverySeconds()
                        && value.loadingAndUnloadingSeconds() > 0d
                        && value.sustainableThroughputKgPerSecond()
                        >= value.reservedInputKgPerSecond()));
        assertTrue(report.buffers().stream().allMatch(value ->
                value.bufferDepletionSeconds() > 0d
                        && value.availableMassKg() >= value.requiredPipelineMassKg()));
        assertTrue(report.shipyards().stream().allMatch(value ->
                value.hullInputMassKg() > 0d
                        && value.serialConstructionSupplyEtaSeconds()
                        > value.serialShipyardReplenishmentSeconds()
                        && !value.inputs().isEmpty()));
        assertTrue(report.tradePotential().stream().allMatch(value ->
                value.comparativeCapacityAdvantageKgPerSecond() > 0d
                        && value.oneFreighterDeliveredPotentialKgPerSecond() > 0d
                        && value.ordinaryJumpHops() > 0));
        assertEquals(
                EnumSet.allOf(RuntimeBridgeRequirement.class),
                report.remainingRuntimeBridgeRequirements());
    }

    @Test
    void cadenceEvaluationRejectsAuthorityFromAnotherGeneratedSeed() {
        var fixture = Stage20OperationalIndustrialSpecializationProductionIntegrationTest
                .cadenceFixture();
        var otherSeed = Stage20ResolvedGeneratedWorldProductionProbe.runCurrent(2L);

        assertThrows(IllegalArgumentException.class, () ->
                Stage20GeneratedEconomyCadenceAcceptance.evaluate(
                        otherSeed, fixture.specialization()));
    }
}
