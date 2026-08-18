package com.spacesim.world;

import com.spacesim.persistence.EntityId;

import java.util.Objects;

/**
 * Immutable Stage-19E warfare intent anchored to real world identities and locations.
 *
 * <p>The record is deliberately not a second combat/economy authority. It identifies where an
 * aggressor {@link FleetId} intends to raid, interdict or blockade; physical activity is resolved
 * from current {@link WorldSimulation} placement by {@link PhysicalWarfareOperationService}.
 * No strength, production penalty, cargo loss or abstract blockade percentage is stored here.</p>
 *
 * @param type operation kind
 * @param aggressorFleetId physical fleet performing the operation
 * @param systemA raid/blockade system or canonical first interdicted edge endpoint
 * @param systemB canonical second edge endpoint for {@link Type#INTERDICTION}, otherwise {@code null}
 * @param targetEntityId concrete local raid target for {@link Type#RAID}, otherwise {@code null}
 */
public record PhysicalWarfareOperation(
        Type type,
        FleetId aggressorFleetId,
        StarSystemId systemA,
        StarSystemId systemB,
        EntityId targetEntityId) {

    /** Supported Stage-19E operation categories. */
    public enum Type {
        /** Attack a concrete physical entity in the aggressor's current system. */
        RAID,
        /** Patrol/interdict one existing inter-system topology edge from either endpoint. */
        INTERDICTION,
        /** Maintain coercive combat presence inside one concrete star system. */
        BLOCKADE
    }

    /**
     * Validates operation shape and canonicalizes undirected interdiction endpoints.
     *
     * @param type operation kind
     * @param aggressorFleetId physical aggressor fleet
     * @param systemA primary system or first edge endpoint
     * @param systemB second edge endpoint for interdiction
     * @param targetEntityId local raid target for raid operations
     */
    public PhysicalWarfareOperation {
        type = Objects.requireNonNull(type, "type");
        aggressorFleetId = Objects.requireNonNull(aggressorFleetId, "aggressorFleetId");
        systemA = Objects.requireNonNull(systemA, "systemA");
        switch (type) {
            case RAID -> {
                if (systemB != null || targetEntityId == null) {
                    throw new IllegalArgumentException("RAID requires one system and one target entity");
                }
            }
            case BLOCKADE -> {
                if (systemB != null || targetEntityId != null) {
                    throw new IllegalArgumentException("BLOCKADE requires one system only");
                }
            }
            case INTERDICTION -> {
                systemB = Objects.requireNonNull(systemB, "INTERDICTION systemB");
                if (targetEntityId != null || systemA.equals(systemB)) {
                    throw new IllegalArgumentException("INTERDICTION requires two distinct systems only");
                }
                if (systemA.compareTo(systemB) > 0) {
                    StarSystemId swap = systemA;
                    systemA = systemB;
                    systemB = swap;
                }
            }
        }
    }

    /**
     * Creates a raid against one concrete local entity.
     *
     * @param aggressorFleetId physical aggressor fleet
     * @param systemId target system
     * @param targetEntityId concrete local entity to raid
     * @return validated raid operation
     */
    public static PhysicalWarfareOperation raid(
            FleetId aggressorFleetId,
            StarSystemId systemId,
            EntityId targetEntityId) {
        return new PhysicalWarfareOperation(
                Type.RAID, aggressorFleetId, systemId, null, targetEntityId);
    }

    /**
     * Creates an interdiction operation on one real topology edge.
     *
     * @param aggressorFleetId physical aggressor fleet
     * @param first first edge endpoint
     * @param second second edge endpoint
     * @return validated canonical interdiction operation
     */
    public static PhysicalWarfareOperation interdict(
            FleetId aggressorFleetId,
            StarSystemId first,
            StarSystemId second) {
        return new PhysicalWarfareOperation(
                Type.INTERDICTION, aggressorFleetId, first, second, null);
    }

    /**
     * Creates a blockade operation inside one concrete system.
     *
     * @param aggressorFleetId physical aggressor fleet
     * @param systemId blockaded system
     * @return validated blockade operation
     */
    public static PhysicalWarfareOperation blockade(
            FleetId aggressorFleetId,
            StarSystemId systemId) {
        return new PhysicalWarfareOperation(
                Type.BLOCKADE, aggressorFleetId, systemId, null, null);
    }
}
