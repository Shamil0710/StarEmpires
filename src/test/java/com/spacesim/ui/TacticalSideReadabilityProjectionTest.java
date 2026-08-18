package com.spacesim.ui;

import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ShipGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.TacticalSide;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalSideReadabilityProjectionTest {
    @Test
    void scaledProjectionCarriesAuthoritativeSideForEveryBalanced4v4Ship() {
        ScaledLiveTacticalSimulationSession session =
                new ScaledLiveTacticalSimulationSession(TacticalScenarioId.BALANCED_4V4);
        TacticalPrototypeVisualSnapshot snapshot = session.snapshot();

        assertEquals(8, snapshot.ships().size());
        assertEquals(4L, snapshot.ships().stream().filter(ship -> ship.side() == TacticalSide.ALPHA).count());
        assertEquals(4L, snapshot.ships().stream().filter(ship -> ship.side() == TacticalSide.BETA).count());
        assertTrue(snapshot.ships().stream().noneMatch(ship -> ship.side() == TacticalSide.NEUTRAL));
    }

    @Test
    void legacyShipGlyphConstructorRemainsExplicitlyNeutral() {
        ShipGlyph legacy = new ShipGlyph(1L, 10d, 20d, 0d, 100d, 30d, 0d, 1d, false);

        assertEquals(TacticalSide.NEUTRAL, legacy.side());
    }

    @Test
    void alphaBetaAndNeutralPalettesHaveDistinctFillAndOutlineFamilies() {
        assertNotEquals(TacticalSidePalette.fill(TacticalSide.ALPHA), TacticalSidePalette.fill(TacticalSide.BETA));
        assertNotEquals(TacticalSidePalette.fill(TacticalSide.ALPHA), TacticalSidePalette.fill(TacticalSide.NEUTRAL));
        assertNotEquals(TacticalSidePalette.outline(TacticalSide.ALPHA), TacticalSidePalette.outline(TacticalSide.BETA));
        assertNotEquals(TacticalSidePalette.outline(TacticalSide.BETA), TacticalSidePalette.outline(TacticalSide.NEUTRAL));
    }
}
