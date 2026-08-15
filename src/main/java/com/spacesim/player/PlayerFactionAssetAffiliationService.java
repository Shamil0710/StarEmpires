package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.WorldSimulation;

import java.util.Objects;

/**
 * Stage-17B legal-affiliation bridge for physical assets already owned by the player.
 *
 * <p>Ownership remains authoritative in {@link PlayerState}. This service changes only the local
 * ECS {@link FactionComponent} of an already existing owned fleet. It never respawns an entity,
 * changes {@link FleetId}, moves the fleet, touches cargo/wallets or creates faction resources.</p>
 *
 * <p>The first Stage-17B slice deliberately handles only {@link FleetLocationKind#IN_SYSTEM}
 * fleets. Transit payload affiliation is a separate world-level mutation because an in-transit
 * fleet is detached from every local Ashley engine.</p>
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
        String stableFactionId = player.factionContentId();
        if (stableFactionId == null) {
            throw new IllegalStateException("Independent player has no faction for asset affiliation");
        }
        WorldSimulation world = runtime.world();
        int runtimeFactionId = world.findFactionRuntimeId(stableFactionId).orElseThrow(
                () -> new IllegalStateException(
                        "Player faction is missing from world faction identity directory: " + stableFactionId));

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

            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (faction != null && faction.factionId == runtimeFactionId) {
                alreadyAffiliated++;
                continue;
            }
            if (faction == null) {
                entity.add(new FactionComponent(runtimeFactionId));
            } else {
                faction.factionId = runtimeFactionId;
            }
            affiliated++;
        }
        return new AffiliationReport(
                inspected,
                affiliated,
                alreadyAffiliated,
                deferredTransit,
                stableFactionId,
                runtimeFactionId);
    }

    /**
     * Result of one local-owned-fleet affiliation pass.
     *
     * @param inspectedOwnedFleets number of owned FleetIds inspected
     * @param newlyAffiliatedLocalFleets number of local physical fleets whose faction changed
     * @param alreadyAffiliatedLocalFleets number of local owned fleets already in the target faction
     * @param deferredTransitFleets number of owned transit payloads deliberately left for 17B.3
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
}
