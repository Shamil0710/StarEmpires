package com.spacesim.world;

import java.util.Objects;

/**
 * Shared Stage-17D legal authorization boundary for ordinary station construction.
 *
 * <p>The decision is independent from who pays for the project. A personally funded project may be
 * authorized through a faction without necessarily becoming that faction's asset. Relationship and
 * market-access thresholds never grant construction rights.</p>
 *
 * <p>Unfactioned private construction remains politically neutral. Factional construction is legal
 * in ordinary unclaimed space, in the faction's own territory, or under an explicit unexpired
 * concession from the current controller. Materially contested territory suspends this ordinary
 * legal path; negotiated or coercive mechanisms belong to later diplomacy/warfare stages.</p>
 */
public final class TerritorialConstructionAuthorization {
    private TerritorialConstructionAuthorization() {
        throw new AssertionError("TerritorialConstructionAuthorization does not create instances");
    }

    /** Reason explaining one deterministic construction decision. */
    public enum Reason {
        /** Unfactioned private project; it has no territorial political identity. */
        PRIVATE_UNAFFILIATED,
        /** Factional construction in a system with no established controller or material dispute. */
        UNCLAIMED_FRONTIER,
        /** Factional construction inside the builder's own established territory. */
        DOMESTIC_CONTROL,
        /** Foreign faction construction explicitly authorized by the current controller. */
        EXPLICIT_CONCESSION,
        /** Ordinary construction is suspended while material territorial claims are contested. */
        CONTESTED_NO_ORDINARY_RIGHT,
        /** Foreign faction construction in controlled territory without an explicit right. */
        FOREIGN_CONTROL_NO_RIGHT
    }

    /**
     * Immutable authorization result.
     *
     * @param allowed whether ordinary legal construction may proceed
     * @param reason deterministic reason for the decision
     * @param controllingFactionContentId current stable controller, or {@code null} when unclaimed
     */
    public record Decision(
            boolean allowed,
            Reason reason,
            String controllingFactionContentId) {
        /** Validates reason/controller coherence. */
        public Decision {
            reason = Objects.requireNonNull(reason, "Construction authorization reason not set");
            if (controllingFactionContentId != null) {
                controllingFactionContentId = controllingFactionContentId.strip();
                if (controllingFactionContentId.isEmpty()) {
                    throw new IllegalArgumentException("Construction authorization controller cannot be blank");
                }
            }
            if (reason == Reason.UNCLAIMED_FRONTIER && controllingFactionContentId != null) {
                throw new IllegalArgumentException("UNCLAIMED_FRONTIER cannot have a controller");
            }
            if ((reason == Reason.DOMESTIC_CONTROL
                    || reason == Reason.EXPLICIT_CONCESSION
                    || reason == Reason.FOREIGN_CONTROL_NO_RIGHT)
                    && controllingFactionContentId == null) {
                throw new IllegalArgumentException("Controlled-territory decision requires a controller");
            }
            boolean deniedReason = reason == Reason.FOREIGN_CONTROL_NO_RIGHT
                    || reason == Reason.CONTESTED_NO_ORDINARY_RIGHT;
            if (allowed == deniedReason) {
                throw new IllegalArgumentException(
                        "Construction authorization allowed flag disagrees with reason");
            }
        }
    }

    /**
     * Evaluates ordinary legal construction using current authoritative territorial state.
     *
     * @param world authoritative world
     * @param authorizationFactionContentId faction whose territorial rights are exercised, or {@code null} for private unaffiliated construction
     * @param systemId target star system
     * @return immutable deterministic decision
     * @throws NullPointerException when world/system is missing
     * @throws IllegalArgumentException when system or non-null faction identity is unknown
     */
    public static Decision evaluate(
            WorldSimulation world,
            String authorizationFactionContentId,
            StarSystemId systemId) {
        WorldSimulation checkedWorld = Objects.requireNonNull(world, "WorldSimulation not set");
        StarSystemId system = Objects.requireNonNull(systemId, "Construction StarSystemId not set");
        if (checkedWorld.getTopology().findSystem(system).isEmpty()) {
            throw new IllegalArgumentException("Unknown construction StarSystem: " + system);
        }

        String controller = checkedWorld.controllingFaction(system).orElse(null);
        if (checkedWorld.isTerritoriallyContested(system)) {
            return new Decision(false, Reason.CONTESTED_NO_ORDINARY_RIGHT, controller);
        }
        if (authorizationFactionContentId == null) {
            return new Decision(true, Reason.PRIVATE_UNAFFILIATED, controller);
        }
        String builder = authorizationFactionContentId.strip();
        if (builder.isEmpty() || checkedWorld.findFactionRuntimeId(builder).isEmpty()) {
            throw new IllegalArgumentException("Unknown construction authorization faction: " + builder);
        }

        if (controller == null) {
            return new Decision(true, Reason.UNCLAIMED_FRONTIER, null);
        }
        if (controller.equals(builder)) {
            return new Decision(true, Reason.DOMESTIC_CONTROL, controller);
        }
        if (checkedWorld.hasTerritorialConstructionRight(controller, builder, system)) {
            return new Decision(true, Reason.EXPLICIT_CONCESSION, controller);
        }
        return new Decision(false, Reason.FOREIGN_CONTROL_NO_RIGHT, controller);
    }
}
