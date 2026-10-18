package com.spacesim.ui;

import com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlHintsTest {
    @Test
    void systemMapHintsCarryTheControlsFormerlyInTheMapTitle() {
        String hints = ControlHints.forTab(Tab.SYSTEM, false);
        assertTrue(hints.contains("двойной клик — сопровождение"));
        assertTrue(hints.contains("Home — обзор"));
        assertTrue(hints.contains("СКМ — панорама"));
        assertFalse(hints.contains("WASD"), "thrust keys only while flying");
        assertTrue(ControlHints.forTab(Tab.SYSTEM, true).endsWith("WASD — тяга  •  X — торможение"));
    }

    @Test
    void otherTabsGetMapOrListHints() {
        assertTrue(ControlHints.forTab(Tab.GALAXY, true).contains("Колесо — масштаб"));
        assertFalse(ControlHints.forTab(Tab.GALAXY, false).contains("Home"), "Home resets only the system map");
        assertFalse(ControlHints.forTab(Tab.GALAXY, true).contains("WASD"));
        for (Tab tab : new Tab[] {Tab.LOGISTICS, Tab.SETTINGS, Tab.HISTORY}) {
            assertTrue(ControlHints.forTab(tab, false).contains("Enter — действие"), tab.name());
        }
    }

    @Test
    void statusMessageKeepsPriorityOverHints() {
        // Room for both: the message gets everything left of the hints.
        var both = ControlHints.split(1_000f, 300f, 400f, 20f, 200f);
        assertTrue(both.hintsShown());
        assertEquals(580f, both.statusWidth(), 1e-4f);

        // Long message, but still readable beside the hints: it is truncated, hints stay.
        var truncated = ControlHints.split(1_000f, 900f, 400f, 20f, 200f);
        assertTrue(truncated.hintsShown());
        assertEquals(580f, truncated.statusWidth(), 1e-4f);

        // Narrow bar: hints would squeeze the message below its minimum, so they are hidden.
        var narrow = ControlHints.split(500f, 400f, 400f, 20f, 200f);
        assertFalse(narrow.hintsShown());
        assertEquals(500f, narrow.statusWidth(), 1e-4f);

        // A short message fits beside the hints even when that space is under the minimum.
        assertTrue(ControlHints.split(500f, 60f, 400f, 20f, 200f).hintsShown());
    }
}
