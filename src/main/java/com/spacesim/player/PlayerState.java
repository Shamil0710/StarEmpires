package com.spacesim.player;

import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Persistent state of the human player, independent from simulation entities.
 *
 * <p>{@code WorldState} remains player-agnostic. The durable actor layer references physical
 * fleets through {@link FleetId}, world construction projects through
 * {@link ConstructionProjectId}, completed physical stations through {@link OwnedStationRef},
 * discovered local objects through system-qualified IDs, delegated work through declarative
 * {@link PlayerFleetOrderState} values and non-omniscient danger through explicit
 * {@link PlayerThreatIntelState} observations.</p>
 *
 * <p>Stage 16 deliberately keeps asset ownership independent from legal/faction affiliation. An
 * independent player may therefore own projects and stations without being represented as a hidden
 * simulation faction.</p>
 *
 * @param walletMilliCredits personal non-negative balance in authoritative milli-credits
 * @param factionContentId optional faction/legal affiliation; {@code null} means independent
 * @param reputations persistent faction reputation entries
 * @param ownedFleetIds fleets currently owned by the player
 * @param activeFleetId currently directly controlled/selected fleet, or {@code null}
 * @param discoveredSystemIds systems known to the player
 * @param discoveredObjects system-qualified discovered object references
 * @param homeSystemId optional home/start system; must already be discovered
 * @param dockedAt optional currently docked market/station reference; must already be discovered
 * @param fleetOrders persistent delegated orders, at most one per owned FleetId
 * @param threatIntel persistent observed system/link danger intelligence
 * @param ownedConstructionProjectIds world construction projects economically owned by the player
 * @param ownedStations physical completed stations owned by the player
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
        DiscoveredObjectRef dockedAt,
        List<PlayerFleetOrderState> fleetOrders,
        List<PlayerThreatIntelState> threatIntel,
        List<ConstructionProjectId> ownedConstructionProjectIds,
        List<OwnedStationRef> ownedStations) {

    /**
     * Source-compatible Stage-15 constructor before player construction ownership.
     *
     * @param walletMilliCredits personal non-negative balance
     * @param factionContentId optional faction affiliation
     * @param reputations reputation entries
     * @param ownedFleetIds owned fleets
     * @param activeFleetId active fleet
     * @param discoveredSystemIds discovered systems
     * @param discoveredObjects discovered objects
     * @param homeSystemId optional home system
     * @param dockedAt optional current docking reference
     * @param fleetOrders persistent delegated orders
     * @param threatIntel persistent observed danger intelligence
     */
    public PlayerState(
            long walletMilliCredits,
            String factionContentId,
            List<PlayerReputationState> reputations,
            List<FleetId> ownedFleetIds,
            FleetId activeFleetId,
            List<StarSystemId> discoveredSystemIds,
            List<DiscoveredObjectRef> discoveredObjects,
            StarSystemId homeSystemId,
            DiscoveredObjectRef dockedAt,
            List<PlayerFleetOrderState> fleetOrders,
            List<PlayerThreatIntelState> threatIntel) {
        this(walletMilliCredits, factionContentId, reputations, ownedFleetIds, activeFleetId,
                discoveredSystemIds, discoveredObjects, homeSystemId, dockedAt, fleetOrders,
                threatIntel, List.of(), List.of());
    }

    /**
     * Source-compatible Stage-15A constructor before persistent threat intelligence.
     *
     * @param walletMilliCredits personal non-negative balance
     * @param factionContentId optional faction affiliation
     * @param reputations reputation entries
     * @param ownedFleetIds owned fleets
     * @param activeFleetId active fleet
     * @param discoveredSystemIds discovered systems
     * @param discoveredObjects discovered objects
     * @param homeSystemId optional home system
     * @param dockedAt optional current docking reference
     * @param fleetOrders persistent delegated orders
     */
    public PlayerState(
            long walletMilliCredits,
            String factionContentId,
            List<PlayerReputationState> reputations,
            List<FleetId> ownedFleetIds,
            FleetId activeFleetId,
            List<StarSystemId> discoveredSystemIds,
            List<DiscoveredObjectRef> discoveredObjects,
            StarSystemId homeSystemId,
            DiscoveredObjectRef dockedAt,
            List<PlayerFleetOrderState> fleetOrders) {
        this(walletMilliCredits, factionContentId, reputations, ownedFleetIds, activeFleetId,
                discoveredSystemIds, discoveredObjects, homeSystemId, dockedAt, fleetOrders,
                List.of(), List.of(), List.of());
    }

    /**
     * Source-compatible pre-Stage-15 constructor with persistent docking and no delegated orders.
     *
     * @param walletMilliCredits personal non-negative balance
     * @param factionContentId optional faction affiliation
     * @param reputations reputation entries
     * @param ownedFleetIds owned fleets
     * @param activeFleetId active fleet
     * @param discoveredSystemIds discovered systems
     * @param discoveredObjects discovered objects
     * @param homeSystemId optional home system
     * @param dockedAt optional current docking reference
     */
    public PlayerState(
            long walletMilliCredits,
            String factionContentId,
            List<PlayerReputationState> reputations,
            List<FleetId> ownedFleetIds,
            FleetId activeFleetId,
            List<StarSystemId> discoveredSystemIds,
            List<DiscoveredObjectRef> discoveredObjects,
            StarSystemId homeSystemId,
            DiscoveredObjectRef dockedAt) {
        this(walletMilliCredits, factionContentId, reputations, ownedFleetIds, activeFleetId,
                discoveredSystemIds, discoveredObjects, homeSystemId, dockedAt,
                List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Source-compatible Stage-12A constructor for an undocked player without delegated orders.
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
                discoveredSystemIds, discoveredObjects, homeSystemId, null,
                List.of(), List.of(), List.of(), List.of());
    }

    /** Validates and canonicalizes player state. */
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

        List<FleetId> fleetCopy = canonicalUnique(
                ownedFleetIds, "Owned FleetIds not set", "Owned FleetId not set", "Duplicate owned FleetId");
        ownedFleetIds = List.copyOf(fleetCopy);
        Set<FleetId> fleets = Set.copyOf(fleetCopy);
        if (activeFleetId != null && !fleets.contains(activeFleetId)) {
            throw new IllegalArgumentException("Active FleetId must be player-owned");
        }

        List<StarSystemId> systemCopy = canonicalUnique(
                discoveredSystemIds, "Discovered systems not set", "Discovered system ID not set",
                "Duplicate discovered StarSystem");
        discoveredSystemIds = List.copyOf(systemCopy);
        Set<StarSystemId> systems = Set.copyOf(systemCopy);
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

        List<PlayerFleetOrderState> orderCopy = new ArrayList<>(Objects.requireNonNull(
                fleetOrders, "Player fleet orders not set"));
        Set<FleetId> orderedFleets = new HashSet<>();
        for (PlayerFleetOrderState order : orderCopy) {
            PlayerFleetOrderState value = Objects.requireNonNull(order, "Player fleet order not set");
            if (!fleets.contains(value.fleetId())) {
                throw new IllegalArgumentException("Fleet order references unowned FleetId: " + value.fleetId());
            }
            if (!orderedFleets.add(value.fleetId())) {
                throw new IllegalArgumentException("Duplicate fleet order for FleetId: " + value.fleetId());
            }
        }
        orderCopy.sort(PlayerFleetOrderState::compareTo);
        fleetOrders = List.copyOf(orderCopy);

        List<PlayerThreatIntelState> intelCopy = new ArrayList<>(Objects.requireNonNull(
                threatIntel, "Player threat intel not set"));
        Set<String> intelKeys = new HashSet<>();
        for (PlayerThreatIntelState intel : intelCopy) {
            PlayerThreatIntelState value = Objects.requireNonNull(intel, "Threat intel entry not set");
            if (!systems.contains(value.systemA())
                    || value.systemB() != null && !systems.contains(value.systemB())) {
                throw new IllegalArgumentException("Threat intel references an undiscovered StarSystem");
            }
            String key = value.kind() + ":" + value.systemA().value() + ":"
                    + (value.systemB() == null ? 0L : value.systemB().value());
            if (!intelKeys.add(key)) {
                throw new IllegalArgumentException("Duplicate threat intel key: " + key);
            }
        }
        intelCopy.sort(PlayerThreatIntelState::compareTo);
        threatIntel = List.copyOf(intelCopy);

        List<ConstructionProjectId> projectCopy = canonicalUnique(
                ownedConstructionProjectIds,
                "Owned construction project IDs not set",
                "Owned ConstructionProjectId not set",
                "Duplicate owned ConstructionProjectId");
        ownedConstructionProjectIds = List.copyOf(projectCopy);

        List<OwnedStationRef> stationCopy = new ArrayList<>(Objects.requireNonNull(
                ownedStations, "Owned stations not set"));
        Set<OwnedStationRef> stationRefs = new HashSet<>();
        for (OwnedStationRef station : stationCopy) {
            OwnedStationRef value = Objects.requireNonNull(station, "Owned station ref not set");
            if (!systems.contains(value.systemId())) {
                throw new IllegalArgumentException("Owned station belongs to an undiscovered system: "
                        + value.systemId());
            }
            if (!stationRefs.add(value)) {
                throw new IllegalArgumentException("Duplicate owned station: " + value);
            }
        }
        stationCopy.sort(OwnedStationRef::compareTo);
        ownedStations = List.copyOf(stationCopy);
    }

    /** @return whether the player currently has a named faction affiliation */
    public boolean affiliated() {
        return factionContentId != null;
    }

    /** @return whether the active ship is currently docked */
    public boolean docked() {
        return dockedAt != null;
    }

    private static <T extends Comparable<? super T>> List<T> canonicalUnique(
            List<T> source,
            String listMessage,
            String entryMessage,
            String duplicateMessage) {
        List<T> copy = new ArrayList<>(Objects.requireNonNull(source, listMessage));
        Set<T> seen = new HashSet<>();
        for (T entry : copy) {
            T checked = Objects.requireNonNull(entry, entryMessage);
            if (!seen.add(checked)) {
                throw new IllegalArgumentException(duplicateMessage + ": " + checked);
            }
        }
        copy.sort(null);
        return copy;
    }
}
