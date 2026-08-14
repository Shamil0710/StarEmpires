package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.persistence.EntityId;

/**
 * Transient combat intent consumed by the shared authoritative combat system.
 *
 * <p>Player input and CombatAI write the same component. The command contains no damage numbers;
 * weapon statistics always come from the data-driven content catalog.</p>
 */
public final class CombatCommandComponent implements Component {
    /** Persistent local target identity, or {@code null} when no target is selected. */
    public EntityId targetId;
    /** Whether the owner currently requests fire whenever cooldown/range validation allows it. */
    public boolean fireRequested;

    /** Clears target and fire intent. */
    public void clear() {
        targetId = null;
        fireRequested = false;
    }
}
