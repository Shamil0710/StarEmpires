package com.spacesim.systems;

import com.spacesim.components.CombatCommandComponent;
import com.spacesim.components.FlightCommandComponent;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.ObservedTacticalIntentPlanner.TacticalIntent;

import java.util.Objects;

/**
 * Writes pure Stage-19 tactical intent into the shared production flight/combat command seams.
 *
 * <p>This adapter does not integrate transforms or resolve weapons. {@link AutonomousFlightSystem}
 * remains responsible for physical movement limits, while the authoritative combat runtime remains
 * responsible for range, cooldown, ammunition and damage validation.</p>
 */
public final class TacticalIntentCommandAdapter {
    /**
     * Applies one tactical intent to existing transient command components.
     *
     * @param intent pure Stage-19 tactical intent
     * @param physicalSpeedCap finite positive speed cap derived from the actor's own physical state
     * @param flight shared movement command component
     * @param combat shared combat command component
     */
    public void apply(
            TacticalIntent intent,
            float physicalSpeedCap,
            FlightCommandComponent flight,
            CombatCommandComponent combat) {
        TacticalIntent checkedIntent = Objects.requireNonNull(intent, "intent");
        FlightCommandComponent checkedFlight = Objects.requireNonNull(flight, "flight");
        CombatCommandComponent checkedCombat = Objects.requireNonNull(combat, "combat");
        if (!Float.isFinite(physicalSpeedCap) || physicalSpeedCap <= 0f) {
            throw new IllegalArgumentException("physicalSpeedCap must be finite and positive");
        }

        checkedFlight.set(
                (float) checkedIntent.movementAxisX(),
                (float) checkedIntent.movementAxisY(),
                physicalSpeedCap);
        if (!checkedIntent.targetSelected()) {
            checkedCombat.clear();
            return;
        }
        checkedCombat.targetId = new EntityId(checkedIntent.targetId());
        checkedCombat.fireRequested = checkedIntent.fireRequested();
    }
}
