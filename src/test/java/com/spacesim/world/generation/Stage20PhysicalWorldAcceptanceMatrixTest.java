package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistence;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.OpenRuntimeBoundary;
import com.spacesim.persistence.Stage20MaterializationPersistence;
import com.spacesim.persistence.Stage20MaterializationPersistentState;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.simulation.Stage20MaterializationService;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.Stage20GeneratedDiscoveryBootstrapPlan;
import com.spacesim.world.Stage20GeneratedDiscoveryBootstrapPlan.BootstrapAuthority;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.OperationalSpecializationReport;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.RuntimeBridgeRequirement;
import com.spacesim.world.Stage20SpecialLocationGenerator;
import com.spacesim.world.generation.Stage20GeneratedEconomyCadenceAcceptance.AcceptanceReport;
import com.spacesim.world.generation.Stage20OperationalIndustrialSpecializationProductionIntegrationTest
        .CadenceFixture;
import com.spacesim.world.generation.Stage20PhysicalWorldAcceptanceMatrix.Category;
import com.spacesim.world.generation.Stage20PhysicalWorldAcceptanceMatrix.CheckId;
import com.spacesim.world.generation.Stage20PhysicalWorldAcceptanceMatrix.CheckStatus;
import com.spacesim.world.generation.Stage20PhysicalWorldAcceptanceMatrix.MatrixReport;
import com.spacesim.world.generation.Stage20PhysicalWorldAcceptanceMatrix.WorldQualityGateOutcome;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20PhysicalWorldAcceptanceMatrixTest {
    private static volatile Fixture sharedFixture;

    @Test
    void acceptedSeedClosesAllPhysicalCategoriesAndRetainsOnlyNamedDeferrals() {
        Fixture fixture = fixture();
        MatrixReport report = Stage20PhysicalWorldAcceptanceMatrix.evaluate(
                fixture.cadenceFixture().resolved(),
                fixture.cadenceFixture().specialization(),
                fixture.cadence(),
                fixture.persistentState());

        assertTrue(report.stage20Complete());
        assertEquals(1, report.topologyRepairPasses());
        assertEquals(WorldQualityGateOutcome.DETERMINISTIC_REPAIR, report.outcome());
        assertEquals(EnumSet.allOf(Category.class), report.checks().stream()
                .map(Stage20PhysicalWorldAcceptanceMatrix.Check::category)
                .collect(Collectors.toSet()));
        assertEquals(EnumSet.allOf(CheckId.class), report.checks().stream()
                .map(Stage20PhysicalWorldAcceptanceMatrix.Check::id)
                .collect(Collectors.toSet()));
        assertFalse(report.checks().stream().anyMatch(value -> value.status() == CheckStatus.FAIL));
        assertEquals(
                Set.of(CheckId.STAGE22_CONTENT_PROMOTION, CheckId.OPEN_RUNTIME_BRIDGE_BOUNDARIES),
                report.deferredChecks().stream()
                        .map(Stage20PhysicalWorldAcceptanceMatrix.Check::id)
                        .collect(Collectors.toSet()));
        assertEquals(
                IntStream.rangeClosed(1, Stage20PhysicalWorldAcceptanceMatrix.HARD_INVARIANT_COUNT)
                        .boxed().collect(Collectors.toSet()),
                report.coveredHardInvariants());
        assertEquals(EnumSet.allOf(OpenRuntimeBoundary.class),
                Set.copyOf(report.openRuntimeBoundaries()));
        assertEquals(EnumSet.allOf(RuntimeBridgeRequirement.class),
                fixture.persistentState().stage20fRuntimeBridgeRequirements());
    }

    @Test
    void evaluationIsDeterministicAndBoundToOneExactAuthorityChain() {
        Fixture fixture = fixture();
        MatrixReport first = Stage20PhysicalWorldAcceptanceMatrix.evaluate(
                fixture.cadenceFixture().resolved(),
                fixture.cadenceFixture().specialization(),
                fixture.cadence(),
                fixture.persistentState());
        MatrixReport second = Stage20PhysicalWorldAcceptanceMatrix.evaluate(
                fixture.cadenceFixture().resolved(),
                fixture.cadenceFixture().specialization(),
                fixture.cadence(),
                fixture.persistentState());
        assertEquals(first, second);

        AcceptanceReport mismatchedCadence = new AcceptanceReport(
                fixture.cadence().version(),
                2L,
                fixture.cadence().resolvedProbeVersion(),
                fixture.cadence().specializationVersion(),
                fixture.cadence().status(),
                fixture.cadence().extraction(),
                fixture.cadence().generatedProcesses(),
                fixture.cadence().operationalProcesses(),
                fixture.cadence().freight(),
                fixture.cadence().buffers(),
                fixture.cadence().shipyards(),
                fixture.cadence().tradePotential(),
                fixture.cadence().remainingRuntimeBridgeRequirements(),
                fixture.cadence().hiddenRestockUsed());

        assertThrows(IllegalArgumentException.class, () ->
                Stage20PhysicalWorldAcceptanceMatrix.evaluate(
                        fixture.cadenceFixture().resolved(),
                        fixture.cadenceFixture().specialization(),
                        mismatchedCadence,
                        fixture.persistentState()));
    }

    @Test
    void reportRejectsIncompleteStableCheckCoverage() {
        Fixture fixture = fixture();
        MatrixReport report = Stage20PhysicalWorldAcceptanceMatrix.evaluate(
                fixture.cadenceFixture().resolved(),
                fixture.cadenceFixture().specialization(),
                fixture.cadence(),
                fixture.persistentState());
        List<Stage20PhysicalWorldAcceptanceMatrix.Check> incomplete = report.checks().stream()
                .filter(value -> value.id() != CheckId.CANONICAL_PERSISTENCE)
                .toList();

        assertThrows(IllegalArgumentException.class, () -> new MatrixReport(
                report.version(),
                report.rootSeed(),
                report.representativeProfileVersion(),
                report.representativeCorpusVersion(),
                report.representativeCorpusSeeds(),
                report.worldFingerprint(),
                report.qualityFingerprint(),
                report.topologyRepairPasses(),
                report.outcome(),
                incomplete,
                report.openRuntimeBoundaries()));
    }

    private static synchronized Fixture fixture() {
        if (sharedFixture != null) {
            return sharedFixture;
        }
        CadenceFixture cadenceFixture =
                Stage20OperationalIndustrialSpecializationProductionIntegrationTest.cadenceFixture();
        AcceptanceReport cadence = Stage20GeneratedEconomyCadenceAcceptance.evaluate(
                cadenceFixture.resolved(), cadenceFixture.specialization());
        var specials = Stage20SpecialLocationGenerator.generateCurrent(cadenceFixture.resolved());
        Stage18IndustrialState industrial = Stage18IndustrialState.empty(0L);
        var snapshot = Stage20GeneratedCampaignPersistence.captureMaterializedWorld(
                cadenceFixture.resolved(), specials, cadenceFixture.specialization(), industrial);
        var discovery = Stage20GeneratedDiscoveryBootstrapPlan.plan(
                cadenceFixture.resolved(),
                cadenceFixture.specialization(),
                new BootstrapAuthority(
                        "stage20l.acceptance-fixture.discovery-authority.v1",
                        cadenceFixture.resolved().rootSeed(),
                        snapshot.worldFingerprint(),
                        100d,
                        3_600d,
                        List.of()));

        SimulationSession session = SimulationSession.createDemo(cadenceFixture.resolved().rootSeed());
        Stage20MaterializationService materialization = Stage20MaterializationService.forSession(session);
        Entity entity = firstPersistentEntity(session);
        EntityId entityId = entity.getComponent(EntityIdComponent.class).id;
        materialization.registerPhysicalState(entityId, new LocalPhysicalKinematics(
                new LocalPhysicalPosition(
                        8_500_000_000_000L,
                        -7_250_000_000_000L,
                        48_125.5d,
                        -31_250.25d),
                12_750.125d,
                -9_500.875d));
        materialization.dematerialize(entityId);
        Stage20MaterializationPersistentState physical =
                Stage20MaterializationPersistence.capture(session, materialization);
        Stage20GeneratedCampaignPersistentState persistentState =
                Stage20GeneratedCampaignPersistence.capture(
                        cadenceFixture.resolved(),
                        specials,
                        cadenceFixture.specialization(),
                        physical,
                        industrial,
                        discovery.ownerKnowledge());
        sharedFixture = new Fixture(cadenceFixture, cadence, persistentState);
        return sharedFixture;
    }

    private static Entity firstPersistentEntity(SimulationSession session) {
        for (Entity entity : session.getEngine().getEntities()) {
            if (entity.getComponent(EntityIdComponent.class) != null) {
                return entity;
            }
        }
        throw new AssertionError("demo session has no persistent entity");
    }

    private record Fixture(
            CadenceFixture cadenceFixture,
            AcceptanceReport cadence,
            Stage20GeneratedCampaignPersistentState persistentState) {}
}
