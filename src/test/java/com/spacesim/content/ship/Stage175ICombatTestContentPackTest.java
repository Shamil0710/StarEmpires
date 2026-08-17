package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipFittingValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175ICombatTestContentPackTest {
    @Test
    void loadsSixRequiredRepresentativeHullFamiliesThroughProductionSchema() {
        ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.load();

        assertEquals(6, catalog.getHulls().size());
        assertNotNull(catalog.findHull("hull.test_corvette_v1"));
        assertNotNull(catalog.findHull("hull.test_frigate_v1"));
        assertNotNull(catalog.findHull("hull.test_destroyer_v1"));
        assertNotNull(catalog.findHull("hull.test_cruiser_v1"));
        assertNotNull(catalog.findHull("hull.test_bulk_freighter_v1"));
        assertNotNull(catalog.findHull("hull.test_tanker_v1"));
        assertEquals(6, catalog.getDemonstratorFits().size());
        assertEquals(64, catalog.getFingerprint().length());
    }

    @Test
    void representativeHullsHaveMateriallyDifferentPhysicalEnvelopes() {
        ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.load();
        HullDefinition corvette = catalog.findHull("hull.test_corvette_v1");
        HullDefinition cruiser = catalog.findHull("hull.test_cruiser_v1");
        HullDefinition freighter = catalog.findHull("hull.test_bulk_freighter_v1");

        assertTrue(corvette.boundingDimensionsM().lengthM() < cruiser.boundingDimensionsM().lengthM());
        assertTrue(corvette.bareHullMassKg() < cruiser.bareHullMassKg());
        assertTrue(freighter.internalVolumeM3() > cruiser.internalVolumeM3());
        assertNotEquals(corvette.baseSignatureGeometryAreaM2(), freighter.baseSignatureGeometryAreaM2());
    }

    @Test
    void everyBaselineFitPassesTheOrdinaryProductionFittingValidator() {
        ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.load();
        ShipFittingValidator validator = new ShipFittingValidator(catalog);

        for (DemonstratorFitDefinition definition : catalog.getDemonstratorFits()) {
            HullDefinition hull = catalog.findHull(definition.hullId());
            InstalledFit fit = InstalledFit.fromDemonstrator(definition);
            var result = validator.validate(hull, fit, ConsumableState.empty(), DamageState.pristine());
            assertTrue(result.isValid(), () -> definition.id() + " errors=" + result.issues());
        }
    }

    @Test
    void contentIsExplicitlyNamespacedAsProvisionalAcceptanceVocabulary() {
        ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.load();
        List<String> hullIds = catalog.getHulls().stream().map(HullDefinition::id).toList();

        assertTrue(hullIds.stream().allMatch(id -> id.startsWith("hull.test_")));
        assertTrue(catalog.findMaterial("material.stage17_5i_test_alloy_v1").tags()
                .contains("content_provisional"));
    }
}
