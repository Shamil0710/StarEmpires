package com.spacesim.systems;

import com.spacesim.components.CombatCommandComponent;
import com.spacesim.components.FlightCommandComponent;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.ObservedTacticalIntentPlanner.TacticalIntent;
import com.spacesim.ship.ObservedTacticalIntentPlanner.TacticalPosture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalIntentCommandAdapterTest {
    private final TacticalIntentCommandAdapter adapter = new TacticalIntentCommandAdapter();

    @Test
    void writesSharedFlightAndCombatIntentWithoutInventingPhysicalStats() {
        FlightCommandComponent flight = new FlightCommandComponent();
        CombatCommandComponent combat = new CombatCommandComponent();
        TacticalIntent intent = new TacticalIntent(
                TacticalPosture.INTERCEPT,
                true,
                42L,
                0.6d,
                0.8d,
                true,
                0.5d);

        adapter.apply(intent, 75f, flight, combat);

        assertEquals(0.6f, flight.axisX, 1e-6f);
        assertEquals(0.8f, flight.axisY, 1e-6f);
        assertEquals(75f, flight.speedCap, 0f);
        assertEquals(new EntityId(42L), combat.targetId);
        assertTrue(combat.fireRequested);
    }

    @Test
    void noTargetClearsExistingCombatCommandAndStopsMovement() {
        FlightCommandComponent flight = new FlightCommandComponent();
        flight.set(1f, 0f, 50f);
        CombatCommandComponent combat = new CombatCommandComponent();
        combat.targetId = new EntityId(99L);
        combat.fireRequested = true;

        adapter.apply(TacticalIntent.noTarget(TacticalPosture.HOLD), 60f, flight, combat);

        assertEquals(0f, flight.axisX, 0f);
        assertEquals(0f, flight.axisY, 0f);
        assertEquals(60f, flight.speedCap, 0f);
        assertNull(combat.targetId);
        assertFalse(combat.fireRequested);
    }

    @Test
    void requiresCallerSuppliedPhysicalSpeedCapAndExistingCommandSinks() {
        TacticalIntent intent = TacticalIntent.noTarget(TacticalPosture.HOLD);
        FlightCommandComponent flight = new FlightCommandComponent();
        CombatCommandComponent combat = new CombatCommandComponent();

        assertThrows(IllegalArgumentException.class,
                () -> adapter.apply(intent, 0f, flight, combat));
        assertThrows(NullPointerException.class,
                () -> adapter.apply(intent, 10f, null, combat));
        assertThrows(NullPointerException.class,
                () -> adapter.apply(intent, 10f, flight, null));
    }
}
