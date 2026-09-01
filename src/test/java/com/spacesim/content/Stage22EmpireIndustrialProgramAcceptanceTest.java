package com.spacesim.content;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M22.3 industrial lineage, bottleneck, reserves and priced-substitution acceptance. */
class Stage22EmpireIndustrialProgramAcceptanceTest {
    @Test
    void everyEmpireFamilyResolvesToDeclaredCommonProcurementLineage() {
        var empire = Stage22EmpirePackageLoader.loadDefault();
        Set<String> lineages = Stage22EmpireIndustrialProgram.lineages().stream()
                .map(Stage22CoreContentSeamCatalog.LineageDefinition::id)
                .collect(Collectors.toSet());

        assertEquals(3, lineages.size());
        empire.shipFamilies().forEach(family -> assertTrue(lineages.contains(family.lineageId()), family.familyId()));
    }

    @Test
    void reserveAndBottleneckDefinitionsUseRealStage18Commodities() {
        var report = Stage22EmpireIndustrialProgram.validateDefault();
        assertEquals(3, report.lineageCount());
        assertEquals(3, report.bottleneckCount());
        assertEquals(3, report.reservePolicyCount());
    }

    @Test
    void structuralSubstitutionIsMassClosedAndExplicitlyMoreExpensive() {
        var base = Stage22CommonManufacturingProfiles.definitions().stream()
                .filter(value -> value.id().equals(Stage22CommonManufacturingProfiles.CARGO_TANK_STORES))
                .findFirst().orElseThrow();
        var alternate = Stage22EmpireIndustrialProgram.cargoStructuralSubstitution();

        assertEquals(1d, alternate.inputs().stream().mapToDouble(value -> value.fractionOfOutputMass()).sum(), 1e-9d);
        assertTrue(alternate.energyJPerOutputKg() > base.energyJPerOutputKg());
        assertTrue(alternate.workSecondsPerOutputKg() > base.workSecondsPerOutputKg());
        assertEquals(1.25d, alternate.energyJPerOutputKg() / base.energyJPerOutputKg(), 1e-9d);
        assertEquals(1.35d, alternate.workSecondsPerOutputKg() / base.workSecondsPerOutputKg(), 1e-9d);
    }
}
