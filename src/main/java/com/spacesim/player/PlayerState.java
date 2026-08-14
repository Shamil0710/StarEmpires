package com.spacesim.player;

import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent Stage-12 state of the human player, independent from simulation entities.
 *
 * <p>World simulation remains player-agnostic. This record stores the durable actor layer that
 * references physical fleets through stable world-level FleetIds and discovered local objects
 * through StarSystem-qualified EntityIds.</p>
 *
 * @param walletMilliCredits personal non-negative balance in authoritative milli-credits
 * @param factionContentId optional faction/legal affiliation; {@code null} means independent
 * @param reputations persistent faction reputation entries
 * @param ownedFleetIds fleets currently owned by the player
 * @param activeFleetId currently controlled/selected fleet, or {@code null}
 * @param discoveredSystemIds systems known to the player
 * @param discoveredObjects system-qualified discovered object references
 * @param homeSystemId optional home/start system; must already be discovered
 * @param dockedAt optional currently docked market/station reference; must already be discovered
 */
public record PlayerState(
        long walletMilliCredits,
        String factionContentId,
        List<PlayerReputationState> reputations,
        List<FleetId> ownedFleetIds,
        FleetId activeFleetId,
        List<StarSystemId> discoveredSystemIds,
        List<DiscoveredObjectRef> discoveredObjects,
        StarSystemId homeSystemId,
        DiscoveredObjectRef dockedAt) {

    /**
     * Source-compatible Stage-12A constructor for an undocked player.
     *
     * @param walletMilliCredits personal non-negative balance
     * @param factionContentId optional faction affiliation
     * @param reputations reputation entries
     * @param ownedFleetIds owned fleets
     * @param activeFleetId active fleet
     * @param discoveredSystemIds discovered systems
     * @param discoveredObjects discovered objects
     * @param homeSystemId optional home system
     */
    public PlayerState(
            long walletMilliCredits,
            String factionContentId,
            List<PlayerReputationState> reputations,
            List<FleetId> ownedFleetIds,
            FleetId activeFleetId,
            List<StarSystemId> discoveredSystemIds,
            List<DiscoveredObjectRef> discoveredObjects,
            StarSystemId homeSystemId) {
        this(walletMilliCredits, factionContentId, reputations, ownedFleetIds, activeFleetId,
                discoveredSystemIds, discoveredObjects, homeSystemId, null);
    }

    /**
     * Validates and canonicalizes player state.
     *
     * @param walletMilliCredits personal non-negative balance
     * @param factionContentId optional faction content ID
     * @param reputations reputation entries
     * @param ownedFleetIds owned world-level fleet IDs
     * @param activeFleetId active fleet or {@code null}
     * @param discoveredSystemIds discovered systems
     * @param discoveredObjects discovered system-local objects
     * @param homeSystemId optional home/start system
     * @param dockedAt optional current docking reference
     */
    public PlayerState {
        if (walletMilliCredits < 0L) {
            throw new IllegalArgumentException("Player wallet cannot be negative");
        }
        if (factionContentId != null) {
            factionContentId = factionContentId.strip();
            if (factionContentId.isEmpty()) {
                throw new IllegalArgumentException("Player faction affiliation cannot be blank");
            }
        }

        List<PlayerReputationState> reputationCopy = new ArrayList<>(
                Objects.requireNonNull(reputations, "Player reputations not set"));
        Set<String> reputationFactions = new HashSet<>();
        for (PlayerReputationState reputation : reputationCopy) {
            PlayerReputationState value = Objects.requireNonNull(reputation, "Player reputation entry not set");
            if (!reputationFactions.add(value.factionContentId())) {
                throw new IllegalArgumentException("Duplicate player reputation faction: "
                        + value.factionContentId());
            }
        }
        reputationCopy.sort(PlayerReputationState::compareTo);
        reputations = List.copyOf(reputationCopy);

        List<FleetId> fleetCopy = new ArrayList<>(
                Objects.requireNonNull(ownedFleetIds, "Owned FleetIds not set"));
        Set<FleetId> fleets = new HashSet<>();
        for (FleetId fleetId : fleetCopy) {
            if (!fleets.add(Objects.requireNonNull(fleetId, "Owned FleetId not set"))) {
                throw new IllegalArgumentException("Duplicate owned FleetId: " + fleetId);
            }
        }
        fleetCopy.sort(FleetId::compareTo);
        ownedFleetIds = List.copyOf(fleetCopy);
        if (activeFleetId != null && !fleets.contains(activeFleetId)) {
            throw new IllegalArgumentException("Active FleetId must be player-owned");
        }

        List<StarSystemId> systemCopy = new ArrayList<>(
                Objects.requireNonNull(discoveredSystemIds, "Discovered systems not set"));
        Set<StarSystemId> systems = new HashSet<>();
        for (StarSystemId systemId : systemCopy) {
            if (!systems.add(Objects.requireNonNull(systemId, "Discovered system ID not set"))) {
                throw new IllegalArgumentException("Duplicate discovered StarSystem: " + systemId);
            }
        }
        systemCopy.sort(StarSystemId::compareTo);
        discoveredSystemIds = List.copyOf(systemCopy);
        if (homeSystemId != null && !systems.contains(homeSystemId)) {
            throw new IllegalArgumentException("Home StarSystem must be discovered");
        }

        List<DiscoveredObjectRef> objectCopy = new ArrayList<>(
                Objects.requireNonNull(discoveredObjects, "Discovered objects not set"));
        Set<DiscoveredObjectRef> objects = new HashSet<>();
        for (DiscoveredObjectRef reference : objectCopy) {
            DiscoveredObjectRef value = Objects.requireNonNull(reference, "Discovered object reference not set");
            if (!systems.contains(value.systemId())) {
                throw new IllegalArgumentException("Discovered object belongs to an undiscovered system: "
                        + value.systemId());
            }
            if (!objects.add(value)) {
                throw new IllegalArgumentException("Duplicate discovered object: " + value);
            }
        }
        objectCopy.sort(DiscoveredObjectRef::compareTo);
        discoveredObjects = List.copyOf(objectCopy);
        if (dockedAt != null && !objects.contains(dockedAt)) {
            throw new IllegalArgumentException("Docked station must be a discovered object");
        }
    }

    /** @return whether the player currently has a named faction affiliation */
    public boolean affiliated() {
        return factionContentId != null;
    }

    /** @return whether the active ship is currently docked */
    public boolean docked() {
        return dockedAt != null;
    }
}
