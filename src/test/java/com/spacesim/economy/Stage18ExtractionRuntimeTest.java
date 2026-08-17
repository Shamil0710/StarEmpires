package com.spacesim.economy;

import com.spacesim.content.Stage18ExtractionCatalog;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.content.Stage18ExtractionCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.economy.Stage18ExtractionRuntime.ExtractionCapability;
import com.spacesim.economy.Stage18ExtractionRuntime.ExtractionResult;
import com.spacesim.economy.Stage18ExtractionRuntime.IntervalBudget;
import com.spacesim.economy.Stage18ExtractionRuntime.PhysicalCargoStore;
import com.spacesim.economy.Stage18ExtractionRuntime.PhysicalSourceState;
import com.spacesim.economy.Stage18ExtractionRuntime.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18ExtractionRuntimeTest {
    private static final double TOLERANCE = 1e-8d;
    private static final String METALLIC_ORE = "commodity.feedstock.metallic_ore";

    private Stage18ResourceOntologyCatalog ontology;
    private Stage18ExtractionCatalog catalog;
    private Stage18ExtractionRuntime runtime;

    @BeforeEach
    void setUp() {
        ontology = Stage18ResourceOntologyLoader.loadDefault();
        catalog = Stage18ExtractionCatalogLoader.loadDefault();
        runtime = new Stage18ExtractionRuntime(ontology, catalog);
    }

    @Test
    void extractionConsumesFiniteMassEnergyWorkMaintenanceAndCompatibleStorage() {
        PhysicalSourceState source = metallicSource(1000d, 1000d, 0.5d, 0.8d, Set.of());
        ExtractionCapability capability = asteroidCapability(2_000_000d, 2d, 0.1d);
        IntervalBudget budget = capability.openInterval(10d);
        PhysicalCargoStore cargo = dryBulkCargo(1000d);

        ExtractionResult result = runtime.extract(
                source, "extraction.asteroid_excavation", 100d, capability, budget, cargo);

        assertEquals(Status.EXTRACTED, result.status());
        assertTrue(result.committed());
        assertEquals(100d, result.sourceMassRemovedKg(), TOLERANCE);
        assertEquals(36.8d, result.outputMassStoredKg(), TOLERANCE);
        assertEquals(63.2d, result.discardedMassKg(), TOLERANCE);
        assertEquals(result.sourceMassRemovedKg(),
                result.outputMassStoredKg() + result.discardedMassKg(), TOLERANCE);
        assertEquals(900d, source.remainingAccessibleMassKg(), TOLERANCE);
        assertEquals(36.8d, cargo.massKg(METALLIC_ORE), TOLERANCE);
        assertEquals(8_000_000d, budget.remainingEnergyJ(), TOLERANCE);
        assertEquals(12d, budget.remainingWorkSeconds(), TOLERANCE);
        assertEquals(0.6d, budget.remainingMaintenanceWorkSeconds(), TOLERANCE);
    }

    @Test
    void sharedIntervalBudgetCannotReusePowerAlreadyCommitted() {
        PhysicalSourceState source = metallicSource(1000d, 1000d, 0.5d, 0.8d, Set.of());
        ExtractionCapability capability = asteroidCapability(2_000_000d, 2d, 0.1d);
        IntervalBudget budget = capability.openInterval(10d);
        PhysicalCargoStore cargo = dryBulkCargo(1000d);

        ExtractionResult first = runtime.extract(
                source, "extraction.asteroid_excavation", 100d, capability, budget, cargo);
        double reserveAfterFirst = source.remainingAccessibleMassKg();
        double cargoAfterFirst = cargo.massKg(METALLIC_ORE);
        double workAfterFirst = budget.remainingWorkSeconds();
        ExtractionResult second = runtime.extract(
                source, "extraction.asteroid_excavation", 100d, capability, budget, cargo);

        assertEquals(Status.EXTRACTED, first.status());
        assertEquals(Status.INSUFFICIENT_POWER, second.status());
        assertFalse(second.committed());
        assertEquals(reserveAfterFirst, source.remainingAccessibleMassKg(), TOLERANCE);
        assertEquals(cargoAfterFirst, cargo.massKg(METALLIC_ORE), TOLERANCE);
        assertEquals(8_000_000d, budget.remainingEnergyJ(), TOLERANCE);
        assertEquals(workAfterFirst, budget.remainingWorkSeconds(), TOLERANCE);
    }

    @Test
    void requestBeyondFiniteReserveExtractsOnlyRemainderAndDepletesSource() {
        PhysicalSourceState source = metallicSource(50d, 50d, 1d, 1d, Set.of());
        ExtractionCapability capability = asteroidCapability(2_000_000d, 10d, 1d);
        IntervalBudget budget = capability.openInterval(10d);
        PhysicalCargoStore cargo = dryBulkCargo(1000d);

        ExtractionResult result = runtime.extract(
                source, "extraction.asteroid_excavation", 100d, capability, budget, cargo);

        assertEquals(Status.EXTRACTED_DEPLETED, result.status());
        assertEquals(50d, result.sourceMassRemovedKg(), TOLERANCE);
        assertEquals(46d, result.outputMassStoredKg(), TOLERANCE);
        assertEquals(0d, source.remainingAccessibleMassKg(), TOLERANCE);
        assertTrue(source.isDepleted());

        ExtractionResult repeat = runtime.extract(
                source, "extraction.asteroid_excavation", 1d, capability, budget, cargo);
        assertEquals(Status.DEPLETED, repeat.status());
        assertFalse(repeat.committed());
    }

    @Test
    void incompatibleSourceMethodEnvironmentOccurrenceAndOutputAreRejectedWithoutMutation() {
        ExtractionCapability capability = asteroidCapability(10_000_000d, 100d, 10d);
        PhysicalCargoStore cargo = dryBulkCargo(10_000d);

        PhysicalSourceState salvage = new PhysicalSourceState(
                "source.salvage", SourceKind.SALVAGE_STREAM, "salvage.preaccounted",
                ExtractionEnvironment.SALVAGE_SITE, "commodity.material.structural_alloy",
                100d, 100d, 1d, 1d, Set.of());
        assertRejectedUnchanged(
                salvage, capability, capability.openInterval(10d), cargo,
                "extraction.asteroid_excavation", Status.SOURCE_KIND_INCOMPATIBLE);

        PhysicalSourceState surface = new PhysicalSourceState(
                "source.surface", SourceKind.NATURAL_OCCURRENCE, "occurrence.metallic",
                ExtractionEnvironment.SURFACE, METALLIC_ORE,
                100d, 100d, 1d, 1d, Set.of());
        assertRejectedUnchanged(
                surface, capability, capability.openInterval(10d), cargo,
                "extraction.asteroid_excavation", Status.ENVIRONMENT_INCOMPATIBLE);

        PhysicalSourceState volatileFreeBody = new PhysicalSourceState(
                "source.volatiles", SourceKind.NATURAL_OCCURRENCE, "occurrence.volatiles",
                ExtractionEnvironment.FREE_BODY, "commodity.feedstock.volatile_feedstock",
                100d, 100d, 1d, 1d, Set.of());
        PhysicalCargoStore liquidCargo = new PhysicalCargoStore(
                ontology, Map.of("storage.liquid_tank", 1000d), Map.of());
        assertRejectedUnchanged(
                volatileFreeBody, capability, capability.openInterval(10d), liquidCargo,
                "extraction.asteroid_excavation", Status.OCCURRENCE_INCOMPATIBLE);

        PhysicalSourceState wrongOutput = new PhysicalSourceState(
                "source.wrong-output", SourceKind.NATURAL_OCCURRENCE, "occurrence.metallic",
                ExtractionEnvironment.FREE_BODY, "commodity.feedstock.water_ice",
                100d, 100d, 1d, 1d, Set.of());
        assertRejectedUnchanged(
                wrongOutput, capability, capability.openInterval(10d), cargo,
                "extraction.asteroid_excavation", Status.OUTPUT_INCOMPATIBLE);
    }

    @Test
    void missingMethodCapabilityAndThroughputRejectAtomically() {
        PhysicalSourceState source = metallicSource(
                1000d, 1000d, 1d, 1d, Set.of("capability.process.beneficiation"));
        PhysicalCargoStore cargo = dryBulkCargo(10_000d);
        ExtractionCapability noExtraCapability = asteroidCapability(10_000_000d, 100d, 10d);

        assertRejectedUnchanged(
                source, noExtraCapability, noExtraCapability.openInterval(20d), cargo,
                "extraction.missing", Status.METHOD_NOT_FOUND);
        assertRejectedUnchanged(
                source, noExtraCapability, noExtraCapability.openInterval(20d), cargo,
                "extraction.asteroid_excavation", Status.MISSING_CAPABILITY);

        ExtractionCapability withExtraCapability = new ExtractionCapability(
                "capability.test.full",
                Set.of("capability.extraction.asteroid_excavation", "capability.process.beneficiation"),
                10_000_000d, 100d, 10d);
        assertRejectedUnchanged(
                source, withExtraCapability, withExtraCapability.openInterval(10d), cargo,
                "extraction.asteroid_excavation", 300d, Status.THROUGHPUT_LIMIT);
    }

    @Test
    void finitePowerWorkMaintenanceAndStorageEachBlockWithoutPartialConsumption() {
        PhysicalSourceState source = metallicSource(1000d, 1000d, 0.5d, 0.8d, Set.of());

        ExtractionCapability lowPower = asteroidCapability(1000d, 100d, 100d);
        assertRejectedUnchanged(
                source, lowPower, lowPower.openInterval(10d), dryBulkCargo(1000d),
                "extraction.asteroid_excavation", Status.INSUFFICIENT_POWER);

        ExtractionCapability lowWork = asteroidCapability(10_000_000d, 0.1d, 100d);
        assertRejectedUnchanged(
                source, lowWork, lowWork.openInterval(10d), dryBulkCargo(1000d),
                "extraction.asteroid_excavation", Status.INSUFFICIENT_WORK);

        ExtractionCapability lowMaintenance = asteroidCapability(10_000_000d, 100d, 0.01d);
        assertRejectedUnchanged(
                source, lowMaintenance, lowMaintenance.openInterval(10d), dryBulkCargo(1000d),
                "extraction.asteroid_excavation", Status.INSUFFICIENT_MAINTENANCE);

        ExtractionCapability full = asteroidCapability(10_000_000d, 100d, 100d);
        assertRejectedUnchanged(
                source, full, full.openInterval(10d), dryBulkCargo(10d),
                "extraction.asteroid_excavation", Status.STORAGE_FULL);
    }

    @Test
    void salvageRecoveryConsumesOnlyPreAccountedFiniteStreamWithBoundedYield() {
        PhysicalSourceState source = new PhysicalSourceState(
                "source.salvage.structural",
                SourceKind.SALVAGE_STREAM,
                "salvage.preaccounted.structural",
                ExtractionEnvironment.SALVAGE_SITE,
                "commodity.material.structural_alloy",
                100d,
                100d,
                1d,
                0.9d,
                Set.of());
        ExtractionCapability capability = new ExtractionCapability(
                "capability.salvage-rig",
                Set.of("capability.process.recycling"),
                10_000_000d,
                100d,
                100d);
        IntervalBudget budget = capability.openInterval(20d);
        PhysicalCargoStore cargo = dryBulkCargo(1000d);

        ExtractionResult result = runtime.extract(
                source, "extraction.salvage_recovery", 100d, capability, budget, cargo);

        assertEquals(Status.EXTRACTED_DEPLETED, result.status());
        assertEquals(63d, result.outputMassStoredKg(), TOLERANCE);
        assertEquals(37d, result.discardedMassKg(), TOLERANCE);
        assertEquals(63d, cargo.massKg("commodity.material.structural_alloy"), TOLERANCE);
        assertEquals(100d, result.outputMassStoredKg() + result.discardedMassKg(), TOLERANCE);
    }

    @Test
    void invalidRequestsAndInvalidCargoStateAreRejectedExplicitly() {
        PhysicalSourceState source = metallicSource(100d, 100d, 1d, 1d, Set.of());
        ExtractionCapability capability = asteroidCapability(10_000_000d, 100d, 100d);
        IntervalBudget budget = capability.openInterval(10d);
        PhysicalCargoStore cargo = dryBulkCargo(1000d);

        assertEquals(Status.INVALID_REQUEST,
                runtime.extract(source, "extraction.asteroid_excavation", 0d, capability, budget, cargo).status());
        assertEquals(Status.INVALID_REQUEST,
                runtime.extract(null, "extraction.asteroid_excavation", 1d, capability, budget, cargo).status());
        assertThrows(IllegalArgumentException.class, () -> capability.openInterval(0d));
        assertThrows(IllegalArgumentException.class, () -> new PhysicalCargoStore(
                ontology, Map.of("storage.dry_bulk", 10d), Map.of(METALLIC_ORE, 11d)));
        assertThrows(IllegalArgumentException.class, () -> new PhysicalCargoStore(
                ontology, Map.of("storage.liquid_tank", 100d), Map.of(METALLIC_ORE, 1d)));
    }

    private void assertRejectedUnchanged(
            PhysicalSourceState source,
            ExtractionCapability capability,
            IntervalBudget budget,
            PhysicalCargoStore cargo,
            String methodId,
            Status expected) {
        assertRejectedUnchanged(source, capability, budget, cargo, methodId, 100d, expected);
    }

    private void assertRejectedUnchanged(
            PhysicalSourceState source,
            ExtractionCapability capability,
            IntervalBudget budget,
            PhysicalCargoStore cargo,
            String methodId,
            double requestedMassKg,
            Status expected) {
        double reserve = source.remainingAccessibleMassKg();
        Map<String, Double> stock = cargo.snapshotMassByCommodityKg();
        double energy = budget.remainingEnergyJ();
        double work = budget.remainingWorkSeconds();
        double maintenance = budget.remainingMaintenanceWorkSeconds();

        ExtractionResult result = runtime.extract(
                source, methodId, requestedMassKg, capability, budget, cargo);

        assertEquals(expected, result.status());
        assertFalse(result.committed());
        assertEquals(0d, result.sourceMassRemovedKg(), TOLERANCE);
        assertEquals(reserve, source.remainingAccessibleMassKg(), TOLERANCE);
        assertEquals(stock, cargo.snapshotMassByCommodityKg());
        assertEquals(energy, budget.remainingEnergyJ(), TOLERANCE);
        assertEquals(work, budget.remainingWorkSeconds(), TOLERANCE);
        assertEquals(maintenance, budget.remainingMaintenanceWorkSeconds(), TOLERANCE);
    }

    private PhysicalSourceState metallicSource(
            double initialMassKg,
            double remainingMassKg,
            double grade,
            double sourceRecovery,
            Set<String> requiredCapabilities) {
        return new PhysicalSourceState(
                "source.metallic",
                SourceKind.NATURAL_OCCURRENCE,
                "occurrence.metallic",
                ExtractionEnvironment.FREE_BODY,
                METALLIC_ORE,
                initialMassKg,
                remainingMassKg,
                grade,
                sourceRecovery,
                requiredCapabilities);
    }

    private static ExtractionCapability asteroidCapability(
            double powerW,
            double workRate,
            double maintenanceWorkRate) {
        return new ExtractionCapability(
                "capability.asteroid-rig",
                Set.of("capability.extraction.asteroid_excavation"),
                powerW,
                workRate,
                maintenanceWorkRate);
    }

    private PhysicalCargoStore dryBulkCargo(double capacityKg) {
        return new PhysicalCargoStore(
                ontology,
                Map.of("storage.dry_bulk", capacityKg),
                Map.of());
    }
}
