package com.spacesim.economy;

import com.spacesim.content.Stage18ExtractionCatalogLoader;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ManufacturingCatalogLoader;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18ShipyardCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18SalvageRuntimeTest {
    @Test
    void pristineWreckNeverContainsMoreThanActuallyConstructedHullAndModules() {
        Fixture fixture = fixture();
        Stage18SalvageRuntime.WreckSalvage wreck = fixture.salvage.deriveShipWreck(
                "wreck.test.pristine",
                fixture.fit,
                damage(Map.of(), 1d, 1d, 1d));

        assertEquals(19_520_000d, wreck.totalConstructedMassKg(), 1e-5d);
        assertEquals(wreck.totalConstructedMassKg(), wreck.totalAccessibleMassKg(), 1e-5d);
        assertEquals(0d, wreck.totalIrrecoverableDamageLossKg(), 1e-5d);
        assertTrue(wreck.streams().stream()
                .allMatch(stream -> stream.accessibleMassKg() <= stream.constructedMassKg() + 1e-9d));
        assertEquals(wreck.totalConstructedMassKg(),
                wreck.streams().stream().mapToDouble(Stage18SalvageRuntime.SalvageStream::constructedMassKg).sum(),
                1e-5d);
    }

    @Test
    void damageCanOnlyReduceAccessibleSalvageMass() {
        Fixture fixture = fixture();
        Stage18SalvageRuntime.WreckSalvage pristine = fixture.salvage.deriveShipWreck(
                "wreck.test.base",
                fixture.fit,
                damage(Map.of(), 1d, 1d, 1d));
        Stage18SalvageRuntime.WreckSalvage damaged = fixture.salvage.deriveShipWreck(
                "wreck.test.damaged",
                fixture.fit,
                damage(Map.of("core_drive", 0.25d, "weapon_spinal", 0.5d), 0.5d, 0.75d, 0.25d));

        assertEquals(pristine.totalConstructedMassKg(), damaged.totalConstructedMassKg(), 1e-5d);
        assertTrue(damaged.totalAccessibleMassKg() < pristine.totalAccessibleMassKg());
        assertTrue(damaged.totalIrrecoverableDamageLossKg() > 0d);
        assertEquals(damaged.totalConstructedMassKg(),
                damaged.totalAccessibleMassKg() + damaged.totalIrrecoverableDamageLossKg(),
                1e-5d);
    }

    @Test
    void preAccountedStreamUsesExistingRecoveryMethodAndAddsAnotherBoundedLoss() {
        Fixture fixture = fixture();
        Stage18SalvageRuntime.WreckSalvage wreck = fixture.salvage.deriveShipWreck(
                "wreck.test.recycling",
                fixture.fit,
                damage(Map.of(), 1d, 1d, 1d));
        Stage18SalvageRuntime.SalvageStream stream = wreck.streams().stream()
                .filter(value -> value.commodityId().equals("commodity.material.structural_alloy"))
                .findFirst()
                .orElseThrow();
        Stage18ExtractionRuntime.PhysicalSourceState source = stream.toPhysicalSource();
        assertNotNull(source);

        Stage18FacilityRuntime facilityRuntime = new Stage18FacilityRuntime(Stage18FacilityCatalogLoader.loadDefault());
        Stage18FacilityRuntime.FacilityCapabilitySnapshot recycling = facilityRuntime.project(
                new Stage18FacilityRuntime.InstalledFacilityState(
                        "facility.test.recycling",
                        "facility.processing.recycling",
                        1d,
                        30_000_000d,
                        20_000_000d,
                        60d,
                        3d,
                        "location.orbital_station",
                        true));
        assertEquals(Stage18FacilityRuntime.Status.ACTIVE, recycling.status());

        Stage18ExtractionRuntime extraction = new Stage18ExtractionRuntime(
                fixture.ontology,
                Stage18ExtractionCatalogLoader.loadDefault());
        Stage18ExtractionRuntime.ExtractionCapability capability = facilityRuntime.toExtractionCapability(recycling);
        Stage18ExtractionRuntime.PhysicalCargoStore destination = new Stage18ExtractionRuntime.PhysicalCargoStore(
                fixture.ontology,
                Map.of("storage.dry_bulk", 10_000_000d),
                Map.of());

        Stage18ExtractionRuntime.ExtractionResult result = extraction.extract(
                source,
                "extraction.salvage_recovery",
                800d,
                capability,
                capability.openInterval(100d),
                destination);

        assertTrue(result.committed());
        assertEquals(800d, result.sourceMassRemovedKg(), 1e-9d);
        assertEquals(560d, result.outputMassStoredKg(), 1e-9d);
        assertEquals(240d, result.discardedMassKg(), 1e-9d);
        assertEquals(560d, destination.massKg("commodity.material.structural_alloy"), 1e-9d);
        assertTrue(result.outputMassStoredKg() < stream.accessibleMassKg());
        assertTrue(result.outputMassStoredKg() < stream.constructedMassKg());
    }

    private static Fixture fixture() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        InstalledFit fit = InstalledFit.fromDemonstrator(
                engineering.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
        Stage18SalvageRuntime salvage = new Stage18SalvageRuntime(
                ontology,
                Stage18ShipyardCatalogLoader.loadDefault(),
                Stage18ManufacturingCatalogLoader.loadDefault(),
                Stage18ManufacturingProductRegistry.loadDefault());
        return new Fixture(ontology, fit, salvage);
    }

    private static ShipDamageRuntime.Snapshot damage(
            Map<String, Double> moduleIntegrity,
            double engineering,
            double missionCore,
            double weapons) {
        Map<String, Double> compartments = new LinkedHashMap<>();
        compartments.put("engineering", engineering);
        compartments.put("mission_core", missionCore);
        compartments.put("weapons", weapons);
        return new ShipDamageRuntime.Snapshot(compartments, new DamageState(moduleIntegrity));
    }

    private record Fixture(
            Stage18ResourceOntologyCatalog ontology,
            InstalledFit fit,
            Stage18SalvageRuntime salvage) { }
}
