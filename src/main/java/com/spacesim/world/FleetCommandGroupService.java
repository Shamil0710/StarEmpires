package com.spacesim.world;

import com.spacesim.world.FleetCommandState.CommandGroupState;

import java.util.List;
import java.util.Objects;

/**
 * Allocates persistent Stage-21D command groups over existing ordinary fleets.
 *
 * <p>The service owns only command-group identity allocation. It validates every requested member
 * against the read-only force registry and never creates, transfers, affiliates or moves a fleet.</p>
 */
public final class FleetCommandGroupService {
    private final GalaxyTopology topology;

    /**
     * Creates the command-group formation boundary for one existing galaxy topology.
     *
     * @param topology authoritative topology used only to validate the designated home system
     */
    public FleetCommandGroupService(GalaxyTopology topology) {
        this.topology = Objects.requireNonNull(topology, "topology");
    }

    /**
     * Forms one persistent command group using the state's allocator watermark.
     *
     * @param state current persistent command state
     * @param forces current read-only reconstruction of ordinary physical fleets
     * @param factionId non-negative owning faction runtime identifier
     * @param name display name for the command group
     * @param memberFleetIds non-empty ordinary fleet identities to wrap without replacing
     * @param homeSystemId designated home system for reserve/home-defense policy
     * @param reserve whether the new group is held as a reserve formation
     * @param homeDefense whether the new group is restricted to home-defense offensive commitments
     * @param maxStrategicRiskBps maximum accepted strategic route risk in basis points
     * @return accepted group together with the immutable updated command state
     * @throws IllegalArgumentException when the home system or a member fleet is unknown
     * @throws IllegalStateException when a requested fleet is not owned by the requested faction or is already assigned
     */
    public FormationResult form(
            FleetCommandState state,
            FleetForceRegistry forces,
            int factionId,
            String name,
            List<FleetId> memberFleetIds,
            StarSystemId homeSystemId,
            boolean reserve,
            boolean homeDefense,
            int maxStrategicRiskBps) {
        FleetCommandState checkedState = Objects.requireNonNull(state, "state");
        FleetForceRegistry checkedForces = Objects.requireNonNull(forces, "forces");
        StarSystemId home = Objects.requireNonNull(homeSystemId, "homeSystemId");
        if (topology.findSystem(home).isEmpty()) {
            throw new IllegalArgumentException("unknown command-group home system: " + home);
        }
        List<FleetId> members = List.copyOf(Objects.requireNonNull(memberFleetIds, "memberFleetIds"));
        for (FleetId fleetId : members) {
            FleetForceRegistry.Entry force = checkedForces.find(Objects.requireNonNull(fleetId, "member FleetId"))
                    .orElseThrow(() -> new IllegalArgumentException("unknown command-group FleetId: " + fleetId));
            if (force.factionId() != factionId) {
                throw new IllegalStateException("fleet is not owned by command-group faction: " + fleetId);
            }
            boolean assigned = checkedState.groups().stream()
                    .anyMatch(group -> group.memberFleetIds().contains(fleetId));
            if (assigned) {
                throw new IllegalStateException("FleetId is already assigned to a command group: " + fleetId);
            }
        }
        CommandGroupState group = new CommandGroupState(
                checkedState.nextCommandGroupId(),
                factionId,
                name,
                members,
                home,
                reserve,
                homeDefense,
                maxStrategicRiskBps);
        return new FormationResult(checkedState.addGroup(group), group);
    }

    /**
     * Result of successful persistent command-group formation.
     *
     * @param state updated immutable command state
     * @param group newly allocated command group
     */
    public record FormationResult(FleetCommandState state, CommandGroupState group) {
        /**
         * Validates a successful formation result.
         *
         * @param state updated immutable command state
         * @param group newly allocated command group
         */
        public FormationResult {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(group, "group");
        }
    }
}
