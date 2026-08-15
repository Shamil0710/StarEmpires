package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FactionPolicyRefreshService;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.WorldSimulation;

import java.util.Objects;

/**
 * Stage-17B legal-affiliation bridge for physical assets already owned by the player.
 *
 * <p>Ownership remains authoritative in {@link PlayerState}. This service changes only legal
 * {@link FactionComponent} affiliation of already existing owned assets, or the equivalent
 * persistent faction field while a fleet is detached in transit. It never respawns an entity,
 * changes {@link FleetId}, moves assets, touches cargo/wallets or creates faction resources.</p>
 */
public final class PlayerFactionAssetAffiliationService {
    private final PlayerRuntime runtime;

    /**
     * Creates the affiliation service for one playable runtime.
     *
     * @param runtime authoritative player/world runtime
     */
    public PlayerFactionAssetAffiliationService(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
    }

    /**
     * Affiliates every currently local player-owned fleet with the player's current faction.
     *
     * <p>The operation is idempotent. Fleets already carrying the target runtime faction ID are
     * counted as unchanged. Owned fleets in transit are reported as deferred rather than partially
     * materialized or respawned.</p>
     *
     * @return immutable mutation report
     * @throws IllegalStateException if the player is independent, faction identity is unresolved,
     *         or an owned local fleet violates world placement/entity invariants
     */
    public AffiliationReport affiliateLocalOwnedFleets() {
        PlayerState player = runtime.player();
        ResolvedFaction target = requirePlayerFaction(player);
        WorldSimulation world = runtime.world();

        int inspected = 0;
        int affiliated = 0;
        int alreadyAffiliated = 0;
        int deferredTransit = 0;
        for (FleetId fleetId : player.ownedFleetIds()) {
            inspected++;
            FleetPlacementState placement = world.findFleet(fleetId).orElseThrow(
                    () -> new IllegalStateException("Owned FleetId disappeared during affiliation: " + fleetId));
            if (placement.locationKind() == FleetLocationKind.IN_TRANSIT) {
                deferredTransit++;
                continue;
            }
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || placement.systemId() == null
                    || placement.localEntityId() == null) {
                throw new IllegalStateException("Owned fleet has unsupported placement: " + fleetId);
            }
            SimulationSession session = world.findSession(placement.systemId()).orElseThrow(
                    () -> new IllegalStateException(
                            "Owned fleet system has no live SimulationSession: " + placement.systemId()));
            Entity entity = session.getEntityRegistry().find(placement.localEntityId());
            if (entity == null) {
                throw new IllegalStateException("Owned local fleet entity is missing: " + fleetId);
            }

            if (affiliateEntity(entity, target.runtimeFactionId())) {
                affiliated++;
            } else {
                alreadyAffiliated++;
            }
        }
        return new AffiliationReport(
                inspected,
                affiliated,
                alreadyAffiliated,
                deferredTransit,
                target.stableFactionId(),
                target.runtimeFactionId());
    }

    /**
     * Affiliates every in-transit player-owned fleet without materializing it in a StarSystem.
     *
     * <p>The authoritative world mutation replaces only the detached payload faction field. The
     * same FleetId, transit origin/destination, jump phase/timing and every other EntityState field
     * remain unchanged. Local owned fleets are reported as deferred for the explicit local command.</p>
     *
     * @return immutable transit affiliation report
     * @throws IllegalStateException if the player is independent, faction identity is unresolved,
     *         or an owned FleetId disappears during the pass
     */
    public TransitAffiliationReport affiliateTransitOwnedFleets() {
        PlayerState player = runtime.player();
        ResolvedFaction target = requirePlayerFaction(player);
        WorldSimulation world = runtime.world();

        int inspected = 0;
        int affiliated = 0;
        int alreadyAffiliated = 0;
        int deferredLocal = 0;
        for (FleetId fleetId : player.ownedFleetIds()) {
            inspected++;
            FleetPlacementState placement = world.findFleet(fleetId).orElseThrow(
                    () -> new IllegalStateException("Owned FleetId disappeared during affiliation: " + fleetId));
            if (placement.locationKind() == FleetLocationKind.IN_SYSTEM) {
                deferredLocal++;
                continue;
            }
            if (placement.locationKind() != FleetLocationKind.IN_TRANSIT) {
                throw new IllegalStateException("Owned fleet has unsupported placement: " + fleetId);
            }
            if (world.affiliateTransitFleetFaction(fleetId, target.stableFactionId())) {
                affiliated++;
            } else {
                alreadyAffiliated++;
            }
        }
        return new TransitAffiliationReport(
                inspected,
                affiliated,
                alreadyAffiliated,
                deferredLocal,
                target.stableFactionId(),
                target.runtimeFactionId());
    }

    /**
     * Affiliates every live player-owned completed station with the player's current faction.
     *
     * <p>Each {@link OwnedStationRef} continues to point to the same system-local EntityId. No
     * wallet, market inventory, archetype, transform or ownership state changes. After the legal
     * affiliation pass, transient market-access components are rebuilt from persistent diplomacy so
     * live behavior immediately matches save/load behavior.</p>
     *
     * @return immutable station affiliation report
     * @throws IllegalStateException if player is independent, faction identity is unresolved, or an
     *         owned station reference no longer points to a live station entity
     */
    public StationAffiliationReport affiliateOwnedStations() {
        PlayerState player = runtime.player();
        ResolvedFaction target = requirePlayerFaction(player);
        WorldSimulation world = runtime.world();

        int inspected = 0;
        int affiliated = 0;
        int alreadyAffiliated = 0;
        for (OwnedStationRef reference : player.ownedStations()) {
            inspected++;
            SimulationSession session = world.findSession(reference.systemId()).orElseThrow(
                    () -> new IllegalStateException(
                            "Owned station system has no live SimulationSession: " + reference.systemId()));
            Entity station = session.getEntityRegistry().find(reference.stationEntityId());
            IdentityComponent identity = station == null ? null : station.getComponent(IdentityComponent.class);
            if (station == null || identity == null || identity.kind != IdentityComponent.Kind.STATION) {
                throw new IllegalStateException("OwnedStationRef no longer points to a live station: " + reference);
            }
            if (affiliateEntity(station, target.runtimeFactionId())) {
                affiliated++;
            } else {
                alreadyAffiliated++;
            }
        }

        int refreshedSessions = inspected == 0
                ? 0
                : FactionPolicyRefreshService.refresh(world, runtime.content());
        return new StationAffiliationReport(
                inspected,
                affiliated,
                alreadyAffiliated,
                refreshedSessions,
                target.stableFactionId(),
                target.runtimeFactionId());
    }

    private ResolvedFaction requirePlayerFaction(PlayerState player) {
        String stableFactionId = player.factionContentId();
        if (stableFactionId == null) {
            throw new IllegalStateException("Independent player has no faction for asset affiliation");
        }
        int runtimeFactionId = runtime.world().findFactionRuntimeId(stableFactionId).orElseThrow(
                () -> new IllegalStateException(
                        "Player faction is missing from world faction identity directory: " + stableFactionId));
        return new ResolvedFaction(stableFactionId, runtimeFactionId);
    }

    private static boolean affiliateEntity(Entity entity, int runtimeFactionId) {
        FactionComponent faction = entity.getComponent(FactionComponent.class);
        if (faction != null && faction.factionId == runtimeFactionId) {
            return false;
        }
        if (faction == null) {
            entity.add(new FactionComponent(runtimeFactionId));
        } else {
            faction.factionId = runtimeFactionId;
        }
        return true;
    }

    /**
     * Result of one local-owned-fleet affiliation pass.
     *
     * @param inspectedOwnedFleets number of owned FleetIds inspected
     * @param newlyAffiliatedLocalFleets number of local physical fleets whose faction changed
     * @param alreadyAffiliatedLocalFleets number of local owned fleets already in the target faction
     * @param deferredTransitFleets number of owned transit payloads deliberately left for transit command
     * @param stableFactionId player's stable faction identity
     * @param runtimeFactionId resolved dense local ECS faction slot
     */
    public record AffiliationReport(
            int inspectedOwnedFleets,
            int newlyAffiliatedLocalFleets,
            int alreadyAffiliatedLocalFleets,
            int deferredTransitFleets,
            String stableFactionId,
            int runtimeFactionId) {
        /**
         * Validates non-negative counters and faction identity metadata.
         *
         * @param inspectedOwnedFleets number of owned FleetIds inspected
         * @param newlyAffiliatedLocalFleets number of local physical fleets whose faction changed
         * @param alreadyAffiliatedLocalFleets number of local owned fleets already in the target faction
         * @param deferredTransitFleets number of owned transit payloads deliberately deferred
         * @param stableFactionId player's stable faction identity
         * @param runtimeFactionId resolved dense local ECS faction slot
         */
        public AffiliationReport {
            if (inspectedOwnedFleets < 0
                    || newlyAffiliatedLocalFleets < 0
                    || alreadyAffiliatedLocalFleets < 0
                    || deferredTransitFleets < 0) {
                throw new IllegalArgumentException("Affiliation counters cannot be negative");
            }
            stableFactionId = Objects.requireNonNull(stableFactionId, "Stable faction ID not set");
            if (runtimeFactionId < 0) {
                throw new IllegalArgumentException("Runtime faction ID cannot be negative");
            }
            if (newlyAffiliatedLocalFleets
                    + alreadyAffiliatedLocalFleets
                    + deferredTransitFleets != inspectedOwnedFleets) {
                throw new IllegalArgumentException("Affiliation report counters do not cover inspected fleets");
            }
        }
    }

    /**
     * Result of one in-transit-owned-fleet affiliation pass.
     *
     * @param inspectedOwnedFleets number of owned FleetIds inspected
     * @param newlyAffiliatedTransitFleets number of detached transit payloads whose faction changed
     * @param alreadyAffiliatedTransitFleets number of transit payloads already in target faction
     * @param deferredLocalFleets number of local owned fleets left to the local affiliation command
     * @param stableFactionId player's stable faction identity
     * @param runtimeFactionId resolved dense runtime faction slot
     */
    public record TransitAffiliationReport(
            int inspectedOwnedFleets,
            int newlyAffiliatedTransitFleets,
            int alreadyAffiliatedTransitFleets,
            int deferredLocalFleets,
            String stableFactionId,
            int runtimeFactionId) {
        /**
         * Validates transit report counters and faction metadata.
         *
         * @param inspectedOwnedFleets number of owned FleetIds inspected
         * @param newlyAffiliatedTransitFleets number of changed transit payloads
         * @param alreadyAffiliatedTransitFleets number of unchanged target-affiliated transit payloads
         * @param deferredLocalFleets number of local owned fleets deliberately deferred
         * @param stableFactionId player's stable faction identity
         * @param runtimeFactionId resolved dense runtime faction slot
         */
        public TransitAffiliationReport {
            if (inspectedOwnedFleets < 0
                    || newlyAffiliatedTransitFleets < 0
                    || alreadyAffiliatedTransitFleets < 0
                    || deferredLocalFleets < 0) {
                throw new IllegalArgumentException("Transit affiliation counters cannot be negative");
            }
            stableFactionId = Objects.requireNonNull(stableFactionId, "Stable faction ID not set");
            if (runtimeFactionId < 0) {
                throw new IllegalArgumentException("Runtime faction ID cannot be negative");
            }
            if (newlyAffiliatedTransitFleets
                    + alreadyAffiliatedTransitFleets
                    + deferredLocalFleets != inspectedOwnedFleets) {
                throw new IllegalArgumentException(
                        "Transit affiliation report counters do not cover inspected fleets");
            }
        }
    }

    /**
     * Result of one completed-owned-station affiliation pass.
     *
     * @param inspectedOwnedStations number of persistent OwnedStationRefs inspected
     * @param newlyAffiliatedStations number of station entities whose faction changed
     * @param alreadyAffiliatedStations number of station entities already in the target faction
     * @param refreshedPolicySessions number of local sessions whose transient access policy rebuilt
     * @param stableFactionId player's stable faction identity
     * @param runtimeFactionId resolved dense local ECS faction slot
     */
    public record StationAffiliationReport(
            int inspectedOwnedStations,
            int newlyAffiliatedStations,
            int alreadyAffiliatedStations,
            int refreshedPolicySessions,
            String stableFactionId,
            int runtimeFactionId) {
        /**
         * Validates report counters and identity metadata.
         *
         * @param inspectedOwnedStations number of persistent OwnedStationRefs inspected
         * @param newlyAffiliatedStations number of station entities whose faction changed
         * @param alreadyAffiliatedStations number of station entities already in the target faction
         * @param refreshedPolicySessions number of local sessions whose transient access policy rebuilt
         * @param stableFactionId player's stable faction identity
         * @param runtimeFactionId resolved dense local ECS faction slot
         */
        public StationAffiliationReport {
            if (inspectedOwnedStations < 0
                    || newlyAffiliatedStations < 0
                    || alreadyAffiliatedStations < 0
                    || refreshedPolicySessions < 0) {
                throw new IllegalArgumentException("Station affiliation counters cannot be negative");
            }
            stableFactionId = Objects.requireNonNull(stableFactionId, "Stable faction ID not set");
            if (runtimeFactionId < 0) {
                throw new IllegalArgumentException("Runtime faction ID cannot be negative");
            }
            if (newlyAffiliatedStations + alreadyAffiliatedStations != inspectedOwnedStations) {
                throw new IllegalArgumentException(
                        "Station affiliation report counters do not cover inspected stations");
            }
        }
    }

    private record ResolvedFaction(String stableFactionId, int runtimeFactionId) {
    }
}
