package com.spacesim.persistence;

import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetJumpState;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Atomic Stage-20.5 checkpoint joining the generated campaign, ordinary live world and physical
 * freight sidecar without turning any one of them into a second authority for the others.
 *
 * @param schemaVersion checkpoint schema version
 * @param bridgeVersion exact runtime-composition contract
 * @param campaign current generated campaign and Stage-18 industrial state
 * @param worldState ordinary multi-system ECS/fleet/jump state
 * @param activeSystemId active full-rate local system
 * @param freight current physical fleet, cargo-lot and transport-order sidecar
 * @param localFleetPhysicalStates exact Stage-20 kinematics for every in-system ordinary fleet
 */
@SuppressWarnings("doclint:missing")
public record Stage20GeneratedWorldRuntimePersistentState(
        int schemaVersion,
        String bridgeVersion,
        Stage20GeneratedCampaignPersistentState campaign,
        WorldState worldState,
        StarSystemId activeSystemId,
        Stage20FreightPersistentState freight,
        List<LocalFleetPhysicalState> localFleetPhysicalStates) {
    /** Current atomic Stage-20.5 generated-runtime checkpoint schema. */
    public static final int CURRENT_VERSION = 2;

    /**
     * Validates all cross-envelope identity and active-route invariants.
     *
     * @param schemaVersion checkpoint schema version
     * @param bridgeVersion exact runtime-composition contract
     * @param campaign current generated campaign and Stage-18 industrial state
     * @param worldState ordinary multi-system ECS/fleet/jump state
     * @param activeSystemId active full-rate local system
     * @param freight current physical fleet, cargo-lot and transport-order sidecar
     * @param localFleetPhysicalStates exact Stage-20 kinematics for every in-system ordinary fleet
     */
    public Stage20GeneratedWorldRuntimePersistentState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported Stage-20.5 runtime checkpoint schema: " + schemaVersion);
        }
        if (!Stage20GeneratedWorldRuntimeBridge.CURRENT_VERSION.equals(bridgeVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported Stage-20.5 runtime bridge version: " + bridgeVersion);
        }
        Objects.requireNonNull(campaign, "campaign");
        Objects.requireNonNull(worldState, "worldState");
        Objects.requireNonNull(activeSystemId, "activeSystemId");
        Objects.requireNonNull(freight, "freight");
        ArrayList<LocalFleetPhysicalState> physical = new ArrayList<>(
                Objects.requireNonNull(localFleetPhysicalStates, "localFleetPhysicalStates"));
        physical.sort(Comparator.comparing(LocalFleetPhysicalState::fleetId));
        localFleetPhysicalStates = List.copyOf(physical);
        if (worldState.topology().findSystem(activeSystemId).isEmpty()) {
            throw new IllegalArgumentException("active system is absent from checkpoint topology");
        }
        if (freight.rootSeed() != campaign.generationIdentity().worldSeed()
                || !freight.generatorVersion().equals(campaign.generationIdentity().generatorVersion())
                || !freight.worldFingerprint().equals(campaign.materializedWorld().worldFingerprint())) {
            throw new IllegalArgumentException("freight checkpoint differs from generated campaign authority");
        }
        if (worldState.nextFleetIdValue() < freight.nextFleetIdValue()) {
            throw new IllegalArgumentException("ordinary world FleetId watermark trails freight identity");
        }

        Map<FleetId, FleetPlacementState> placements = new HashMap<>();
        for (FleetPlacementState placement : worldState.fleets()) {
            placements.put(placement.id(), placement);
        }
        Map<FleetId, LocalFleetPhysicalState> physicalByFleet = new HashMap<>();
        for (LocalFleetPhysicalState state : localFleetPhysicalStates) {
            if (physicalByFleet.putIfAbsent(state.fleetId(), state) != null) {
                throw new IllegalArgumentException(
                        "duplicate local fleet physical state: " + state.fleetId());
            }
            FleetPlacementState placement = placements.get(state.fleetId());
            if (placement == null
                    || placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !placement.systemId().equals(state.systemId())) {
                throw new IllegalArgumentException(
                        "fleet physical state lacks matching local world placement: " + state.fleetId());
            }
        }
        for (FleetPlacementState placement : worldState.fleets()) {
            boolean hasPhysical = physicalByFleet.containsKey(placement.id());
            if ((placement.locationKind() == FleetLocationKind.IN_SYSTEM) != hasPhysical) {
                throw new IllegalArgumentException(
                        "local/transit fleet physical-state coverage differs from world placement: "
                                + placement.id());
            }
        }
        Map<FleetId, FleetJumpState> jumps = new HashMap<>();
        for (FleetJumpState jump : worldState.fleetJumps()) {
            jumps.put(jump.fleetId(), jump);
        }
        for (var fleet : freight.freighters()) {
            FleetPlacementState placement = placements.get(fleet.fleetId());
            if (fleet.phase() == FreightPhase.DESTROYED) {
                if (placement != null || jumps.containsKey(fleet.fleetId())) {
                    throw new IllegalArgumentException(
                            "destroyed freight identity remains in ordinary world: " + fleet.fleetId());
                }
                continue;
            }
            if (placement == null) {
                throw new IllegalArgumentException(
                        "operational freight identity is absent from ordinary world: " + fleet.fleetId());
            }
            if (placement.locationKind() == FleetLocationKind.IN_SYSTEM) {
                if (!fleet.currentSystemId().equals(placement.systemId())) {
                    throw new IllegalArgumentException(
                            "local freight system differs between sidecar and ordinary world");
                }
                continue;
            }
            if (placement.transitState() == null
                    || !fleet.currentSystemId().equals(placement.transitState().originSystemId())) {
                throw new IllegalArgumentException(
                        "transit freight origin differs between sidecar and ordinary world");
            }
            FleetJumpState jump = jumps.get(fleet.fleetId());
            if (jump == null
                    || !jump.originSystemId().equals(placement.transitState().originSystemId())
                    || !jump.destinationSystemId().equals(
                            placement.transitState().destinationSystemId())) {
                throw new IllegalArgumentException(
                        "transit freight placement lacks its ordinary jump FSM state");
            }
        }
    }

    /** Exact persisted physical state owned by one ordinary in-system fleet placement. */
    public record LocalFleetPhysicalState(
            FleetId fleetId,
            StarSystemId systemId,
            LocalPhysicalKinematics physicalState) {
        /** Validates one immutable physical sidecar entry. */
        public LocalFleetPhysicalState {
            Objects.requireNonNull(fleetId, "fleetId");
            Objects.requireNonNull(systemId, "systemId");
            Objects.requireNonNull(physicalState, "physicalState");
        }
    }
}
