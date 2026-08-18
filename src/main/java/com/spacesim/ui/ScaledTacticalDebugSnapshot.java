package com.spacesim.ui;

import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.TacticalSurvivalPlanner.DecisionReason;
import com.spacesim.ship.TacticalSurvivalPlanner.SurvivalAction;
import com.spacesim.ship.TrackState.InformationState;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable read-only Stage-19I diagnostics for the scaled live tactical viewer.
 *
 * <p>Every value is projected from current authoritative combat state or actor-local information.
 * This record owns no simulation clock, commands or mutable combat state.</p>
 *
 * @param tick authoritative fixed tactical tick
 * @param combatants canonical per-combatant diagnostic rows
 * @param bodies current physical body populations
 */
public record ScaledTacticalDebugSnapshot(
        long tick,
        List<CombatantDebug> combatants,
        BodyCounts bodies) {

    /**
     * Validates and freezes one scaled debug snapshot.
     *
     * @param tick authoritative fixed tactical tick
     * @param combatants canonical per-combatant diagnostic rows
     * @param bodies current physical body populations
     */
    public ScaledTacticalDebugSnapshot {
        if (tick < 0L) {
            throw new IllegalArgumentException("tick must be non-negative");
        }
        Objects.requireNonNull(combatants, "combatants");
        if (combatants.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("combatants must not contain null");
        }
        combatants = combatants.stream()
                .sorted(Comparator.comparingLong(CombatantDebug::entityId))
                .toList();
        Objects.requireNonNull(bodies, "bodies");
    }

    /**
     * One actor-local track diagnostic.
     *
     * @param targetId observed target identity
     * @param informationState current actor-local information quality
     * @param positionKnown whether Cartesian position is currently known to this actor
     * @param velocityKnown whether velocity is currently known to this actor
     */
    public record TrackDebug(
            long targetId,
            InformationState informationState,
            boolean positionKnown,
            boolean velocityKnown) {
        /**
         * Validates one actor-local track diagnostic.
         *
         * @param targetId observed target identity
         * @param informationState current actor-local information quality
         * @param positionKnown whether Cartesian position is currently known to this actor
         * @param velocityKnown whether velocity is currently known to this actor
         */
        public TrackDebug {
            if (targetId <= 0L) {
                throw new IllegalArgumentException("targetId must be positive");
            }
            Objects.requireNonNull(informationState, "informationState");
        }
    }

    /**
     * One combatant's current actor/control/engineering diagnostic row.
     *
     * @param entityId stable physical combatant identity
     * @param side authored battle side
     * @param tracks actor-local visible tracks only
     * @param selectedTargetId actor-selected target or zero
     * @param fireRequested tactical policy fire request
     * @param fireAuthorized survival-filtered fire authorization
     * @param movementAxisX current normalized x maneuver intent
     * @param movementAxisY current normalized y maneuver intent
     * @param survivalAction current high-level survival action
     * @param survivalReason current high-level survival reason
     * @param ammunitionCount total physically loaded ammunition items
     * @param reactionMassKg current physical reaction mass
     * @param sharedBusEnergyJ current shared stored electrical energy
     * @param shipHeatStoredJ current ship-level stored heat
     * @param localHeatStoredJ total current local stored heat across fitted mounts
     * @param meanCompartmentIntegrity mean current compartment integrity
     * @param minimumModuleIntegrity minimum current fitted-module integrity
     */
    public record CombatantDebug(
            long entityId,
            Side side,
            List<TrackDebug> tracks,
            long selectedTargetId,
            boolean fireRequested,
            boolean fireAuthorized,
            double movementAxisX,
            double movementAxisY,
            SurvivalAction survivalAction,
            DecisionReason survivalReason,
            long ammunitionCount,
            double reactionMassKg,
            double sharedBusEnergyJ,
            double shipHeatStoredJ,
            double localHeatStoredJ,
            double meanCompartmentIntegrity,
            double minimumModuleIntegrity) {
        /**
         * Validates and freezes one combatant diagnostic row.
         *
         * @param entityId stable physical combatant identity
         * @param side authored battle side
         * @param tracks actor-local visible tracks only
         * @param selectedTargetId actor-selected target or zero
         * @param fireRequested tactical policy fire request
         * @param fireAuthorized survival-filtered fire authorization
         * @param movementAxisX current normalized x maneuver intent
         * @param movementAxisY current normalized y maneuver intent
         * @param survivalAction current high-level survival action
         * @param survivalReason current high-level survival reason
         * @param ammunitionCount total physically loaded ammunition items
         * @param reactionMassKg current physical reaction mass
         * @param sharedBusEnergyJ current shared stored electrical energy
         * @param shipHeatStoredJ current ship-level stored heat
         * @param localHeatStoredJ total current local stored heat across fitted mounts
         * @param meanCompartmentIntegrity mean current compartment integrity
         * @param minimumModuleIntegrity minimum current fitted-module integrity
         */
        public CombatantDebug {
            if (entityId <= 0L) {
                throw new IllegalArgumentException("entityId must be positive");
            }
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(tracks, "tracks");
            if (tracks.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("tracks must not contain null");
            }
            tracks = tracks.stream().sorted(Comparator.comparingLong(TrackDebug::targetId)).toList();
            if (selectedTargetId < 0L || ammunitionCount < 0L) {
                throw new IllegalArgumentException("ids/counts must be non-negative");
            }
            requireNormalized(movementAxisX, movementAxisY);
            Objects.requireNonNull(survivalAction, "survivalAction");
            Objects.requireNonNull(survivalReason, "survivalReason");
            requireNonNegativeFinite(reactionMassKg, "reactionMassKg");
            requireNonNegativeFinite(sharedBusEnergyJ, "sharedBusEnergyJ");
            requireNonNegativeFinite(shipHeatStoredJ, "shipHeatStoredJ");
            requireNonNegativeFinite(localHeatStoredJ, "localHeatStoredJ");
            requireUnit(meanCompartmentIntegrity, "meanCompartmentIntegrity");
            requireUnit(minimumModuleIntegrity, "minimumModuleIntegrity");
        }
    }

    /**
     * Current physical non-ship body populations.
     *
     * @param kinetic kinetic/residual projectile bodies
     * @param strike guided offensive bodies
     * @param interceptor guided defensive interceptor bodies
     * @param decoy physical deceptive bodies
     */
    public record BodyCounts(int kinetic, int strike, int interceptor, int decoy) {
        /**
         * Validates current physical body populations.
         *
         * @param kinetic kinetic/residual projectile bodies
         * @param strike guided offensive bodies
         * @param interceptor guided defensive interceptor bodies
         * @param decoy physical deceptive bodies
         */
        public BodyCounts {
            if (kinetic < 0 || strike < 0 || interceptor < 0 || decoy < 0) {
                throw new IllegalArgumentException("body counts must be non-negative");
            }
        }

        /** @return total current non-ship physical body population */
        public int total() {
            return Math.addExact(Math.addExact(kinetic, strike), Math.addExact(interceptor, decoy));
        }
    }

    private static void requireNormalized(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || x * x + y * y > 1d + 1e-12d) {
            throw new IllegalArgumentException("movement intent must be finite and normalized");
        }
    }

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }

    private static void requireUnit(double value, String label) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(label + " must be in [0,1]");
        }
    }
}
