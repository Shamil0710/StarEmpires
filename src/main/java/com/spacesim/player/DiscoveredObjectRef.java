package com.spacesim.player;

import com.spacesim.persistence.EntityId;
import com.spacesim.world.StarSystemId;

import java.util.Objects;

/**
 * Persistent reference to a discovered system-local world object.
 *
 * <p>The reference intentionally stores a StarSystem-qualified EntityId. Discovery memory may
 * outlive the referenced entity, so restore validation checks the system but does not require the
 * entity to still exist.</p>
 *
 * @param systemId system that owned the object when it was discovered
 * @param entityId persistent system-local entity ID
 */
public record DiscoveredObjectRef(
        StarSystemId systemId,
        EntityId entityId) implements Comparable<DiscoveredObjectRef> {

    /**
     * Validates one discovery reference.
     *
     * @param systemId discovery system
     * @param entityId persistent system-local entity ID
     */
    public DiscoveredObjectRef {
        Objects.requireNonNull(systemId, "Discovery system not set");
        Objects.requireNonNull(entityId, "Discovery entity ID not set");
    }

    @Override
    public int compareTo(DiscoveredObjectRef other) {
        int system = systemId.compareTo(other.systemId);
        return system != 0 ? system : Long.compare(entityId.value(), other.entityId.value());
    }
}
