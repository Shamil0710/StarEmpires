package com.spacesim.ui;

import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import com.spacesim.ship.TrackState.InformationState;

import java.util.List;
import java.util.Objects;

/**
 * Immutable read-only inspection card for one selected tactical combatant.
 *
 * @param entityId stable physical combatant identity
 * @param side authored battle side
 * @param role presentation-only schematic role
 * @param hullId authoritative hull content identity
 * @param fitId authored installed-fit content identity
 * @param doctrineId acceptance doctrine identity selecting the physical fit/stores
 * @param wreck whether the visual projection currently represents a wreck
 * @param meanIntegrity mean current compartment integrity
 * @param minimumModuleIntegrity minimum current fitted-module integrity
 * @param shields aggregate current authoritative shield runtime state
 * @param sharedBusEnergyJ current shared stored electrical energy
 * @param shipHeatStoredJ current ship-level stored heat
 * @param localHeatStoredJ total local stored heat across fitted mounts
 * @param reactionMassKg current physical reaction mass
 * @param ammunitionCount current total physical ammunition item count
 * @param xM current local x position
 * @param yM current local y position
 * @param velocityXMps current x velocity
 * @param velocityYMps current y velocity
 * @param speedMps current scalar speed derived from authoritative velocity components
 * @param headingRad current visual heading projected from authoritative motion/scenario orientation
 * @param currentTargetId actor-selected authoritative target, or zero
 * @param fireRequested tactical policy fire request
 * @param fireAuthorized survival-filtered fire authorization
 * @param weaponFeeds physical ammunition-feed identity bindings
 * @param tracks current actor-local visible track summaries
 * @param survivalAction current survival action name
 * @param survivalReason current survival decision reason name
 * @param formation current read-only formation diagnostic
 * @param acceleration explicit unavailable-value text until an authoritative acceleration field exists
 * @param ecmEccm explicit unavailable-value text until an aggregate selected-ship ECM/ECCM field exists
 */
public record ShipInspectionSnapshot(
        long entityId,
        Side side,
        ShipVisualRole role,
        String hullId,
        String fitId,
        DoctrineId doctrineId,
        boolean wreck,
        double meanIntegrity,
        double minimumModuleIntegrity,
        ShieldSummary shields,
        double sharedBusEnergyJ,
        double shipHeatStoredJ,
        double localHeatStoredJ,
        double reactionMassKg,
        long ammunitionCount,
        double xM,
        double yM,
        double velocityXMps,
        double velocityYMps,
        double speedMps,
        double headingRad,
        long currentTargetId,
        boolean fireRequested,
        boolean fireAuthorized,
        List<WeaponFeed> weaponFeeds,
        List<TrackSummary> tracks,
        String survivalAction,
        String survivalReason,
        String formation,
        String acceleration,
        String ecmEccm) {

    /**
     * Validates and freezes one selected-ship inspection projection.
     *
     * @param entityId stable physical combatant identity
     * @param side authored battle side
     * @param role presentation-only schematic role
     * @param hullId authoritative hull content identity
     * @param fitId authored installed-fit content identity
     * @param doctrineId acceptance doctrine identity selecting the physical fit/stores
     * @param wreck whether the visual projection currently represents a wreck
     * @param meanIntegrity mean current compartment integrity
     * @param minimumModuleIntegrity minimum current fitted-module integrity
     * @param shields aggregate current authoritative shield runtime state
     * @param sharedBusEnergyJ current shared stored electrical energy
     * @param shipHeatStoredJ current ship-level stored heat
     * @param localHeatStoredJ total local stored heat across fitted mounts
     * @param reactionMassKg current physical reaction mass
     * @param ammunitionCount current total physical ammunition item count
     * @param xM current local x position
     * @param yM current local y position
     * @param velocityXMps current x velocity
     * @param velocityYMps current y velocity
     * @param speedMps current scalar speed
     * @param headingRad current projected heading
     * @param currentTargetId actor-selected authoritative target, or zero
     * @param fireRequested tactical policy fire request
     * @param fireAuthorized survival-filtered fire authorization
     * @param weaponFeeds physical ammunition-feed identity bindings
     * @param tracks current actor-local visible track summaries
     * @param survivalAction current survival action name
     * @param survivalReason current survival decision reason name
     * @param formation current read-only formation diagnostic
     * @param acceleration explicit unavailable-value text where applicable
     * @param ecmEccm explicit unavailable-value text where applicable
     */
    public ShipInspectionSnapshot {
        if (entityId <= 0L) {
            throw new IllegalArgumentException("entityId must be positive");
        }
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(role, "role");
        requireNonBlank(hullId, "hullId");
        requireNonBlank(fitId, "fitId");
        Objects.requireNonNull(doctrineId, "doctrineId");
        requireUnit(meanIntegrity, "meanIntegrity");
        requireUnit(minimumModuleIntegrity, "minimumModuleIntegrity");
        Objects.requireNonNull(shields, "shields");
        requireNonNegative(sharedBusEnergyJ, "sharedBusEnergyJ");
        requireNonNegative(shipHeatStoredJ, "shipHeatStoredJ");
        requireNonNegative(localHeatStoredJ, "localHeatStoredJ");
        requireNonNegative(reactionMassKg, "reactionMassKg");
        if (ammunitionCount < 0L || currentTargetId < 0L) {
            throw new IllegalArgumentException("counts/target id must be non-negative");
        }
        requireFinite(xM, "xM");
        requireFinite(yM, "yM");
        requireFinite(velocityXMps, "velocityXMps");
        requireFinite(velocityYMps, "velocityYMps");
        requireNonNegative(speedMps, "speedMps");
        requireFinite(headingRad, "headingRad");
        weaponFeeds = List.copyOf(Objects.requireNonNull(weaponFeeds, "weaponFeeds"));
        tracks = List.copyOf(Objects.requireNonNull(tracks, "tracks"));
        if (weaponFeeds.stream().anyMatch(Objects::isNull) || tracks.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("inspection lists must not contain null");
        }
        requireNonBlank(survivalAction, "survivalAction");
        requireNonBlank(survivalReason, "survivalReason");
        requireNonBlank(formation, "formation");
        requireNonBlank(acceleration, "acceleration");
        requireNonBlank(ecmEccm, "ecmEccm");
    }

    /**
     * Aggregate authoritative shield runtime state without inventing a percentage capacity.
     *
     * @param emitterCount current fitted shield-emitter state count
     * @param collapsedCount current collapsed emitter count
     * @param totalReserveJ sum of current shield field reserve energy
     * @param totalAccumulatedHeatJ sum of shield-runtime accumulated interaction/recharge heat
     * @param minimumEmitterIntegrity minimum current emitter integrity, or zero when no emitter exists
     */
    public record ShieldSummary(
            int emitterCount,
            int collapsedCount,
            double totalReserveJ,
            double totalAccumulatedHeatJ,
            double minimumEmitterIntegrity) {
        /**
         * Validates one shield summary.
         *
         * @param emitterCount current fitted shield-emitter state count
         * @param collapsedCount current collapsed emitter count
         * @param totalReserveJ sum of current shield field reserve energy
         * @param totalAccumulatedHeatJ sum of shield-runtime accumulated heat
         * @param minimumEmitterIntegrity minimum emitter integrity, or zero when no emitter exists
         */
        public ShieldSummary {
            if (emitterCount < 0 || collapsedCount < 0 || collapsedCount > emitterCount) {
                throw new IllegalArgumentException("shield counts are invalid");
            }
            requireNonNegative(totalReserveJ, "totalReserveJ");
            requireNonNegative(totalAccumulatedHeatJ, "totalAccumulatedHeatJ");
            requireUnit(minimumEmitterIntegrity, "minimumEmitterIntegrity");
        }
    }

    /**
     * One physical ammunition-feed identity shown in the inspection panel.
     *
     * @param mountId fitted module mount identity
     * @param interfaceId module-local ammunition interface identity
     * @param ammunitionContentId stable ammunition content identity occupying the feed
     */
    public record WeaponFeed(String mountId, String interfaceId, String ammunitionContentId) {
        /**
         * Validates a feed identity.
         *
         * @param mountId fitted module mount identity
         * @param interfaceId module-local ammunition interface identity
         * @param ammunitionContentId stable ammunition content identity
         */
        public WeaponFeed {
            requireNonBlank(mountId, "mountId");
            requireNonBlank(interfaceId, "interfaceId");
            requireNonBlank(ammunitionContentId, "ammunitionContentId");
        }
    }

    /**
     * One actor-local track visible to the selected combatant.
     *
     * @param targetId observed physical target identity
     * @param informationState actor-local current information quality
     * @param positionKnown whether this actor currently knows Cartesian target position
     */
    public record TrackSummary(long targetId, InformationState informationState, boolean positionKnown) {
        /**
         * Validates an actor-local track summary.
         *
         * @param targetId observed physical target identity
         * @param informationState actor-local current information quality
         * @param positionKnown whether Cartesian target position is currently known
         */
        public TrackSummary {
            if (targetId <= 0L) {
                throw new IllegalArgumentException("targetId must be positive");
            }
            Objects.requireNonNull(informationState, "informationState");
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }

    private static void requireUnit(double value, String label) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(label + " must be in [0,1]");
        }
    }

    private static void requireNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }
}
