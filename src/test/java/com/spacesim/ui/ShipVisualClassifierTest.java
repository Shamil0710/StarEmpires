package com.spacesim.ui;

import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipVisualClassifierTest {
    @Test
    void everyAuthoredAcceptanceDoctrineHasOneStableVisualRole() {
        assertEquals(ShipVisualRole.KINETIC, ShipVisualClassifier.classify(DoctrineId.A_KINETIC_LINE));
        assertEquals(ShipVisualRole.MISSILE, ShipVisualClassifier.classify(DoctrineId.B_MISSILE_STRIKE));
        assertEquals(ShipVisualRole.BEAM, ShipVisualClassifier.classify(DoctrineId.C_HIGH_MOBILITY_BEAM));
        assertEquals(ShipVisualRole.DEFENSIVE_EW, ShipVisualClassifier.classify(DoctrineId.D_DEFENSIVE_EW));
        assertEquals(ShipVisualRole.BALANCED, ShipVisualClassifier.classify(DoctrineId.E_BALANCED_CONTROL));
    }

    @Test
    void mixed8v8ProjectionCarriesDistinctAuthoredRolesWithoutUnclassifiedFallback() {
        ScaledLiveTacticalSimulationSession session =
                new ScaledLiveTacticalSimulationSession(TacticalScenarioId.MIXED_8V8);
        Set<ShipVisualRole> roles = session.snapshot().ships().stream()
                .map(TacticalPrototypeVisualSnapshot.ShipGlyph::role)
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(roles.contains(ShipVisualRole.KINETIC));
        assertTrue(roles.contains(ShipVisualRole.MISSILE));
        assertTrue(roles.contains(ShipVisualRole.DEFENSIVE_EW));
        assertTrue(roles.contains(ShipVisualRole.BALANCED));
        assertFalse(roles.contains(ShipVisualRole.UNCLASSIFIED));
    }

    @Test
    void legacyGlyphDoesNotInventRoleMetadata() {
        var legacy = new TacticalPrototypeVisualSnapshot.ShipGlyph(
                1L, 0d, 0d, 0d, 100d, 30d, 0d, 1d, false);

        assertEquals(ShipVisualRole.UNCLASSIFIED, legacy.role());
    }
}
