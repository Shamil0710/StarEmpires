package com.spacesim.ui;

import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import com.spacesim.ship.TrackState.InformationState;

import java.util.List;
import java.util.Objects;

/** Immutable read-only inspection card for one selected tactical combatant. */
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

    /** Validates and freezes one selected-ship inspection projection. */
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

    /** Aggregate authoritative shield runtime state without inventing a percentage capacity. */
    public record ShieldSummary(
            int emitterCount,
            int collapsedCount,
            double totalReserveJ,
            double totalAccumulatedHeatJ,
            double minimumEmitterIntegrity) {
        /** Validates one shield summary. */
        public ShieldSummary {
            if (emitterCount < 0 || collapsedCount < 0 || collapsedCount > emitterCount) {
                throw new IllegalArgumentException("shield counts are invalid");
            }
            requireNonNegative(totalReserveJ, "totalReserveJ");
            requireNonNegative(totalAccumulatedHeatJ, "totalAccumulatedHeatJ");
            requireUnit(minimumEmitterIntegrity, "minimumEmitterIntegrity");
        }
    }

    /** One physical ammunition-feed identity shown in the inspection panel. */
    public record WeaponFeed(String mountId, String interfaceId, String ammunitionContentId) {
        /** Validates a feed identity. */
        public WeaponFeed {
            requireNonBlank(mountId, "mountId");
            requireNonBlank(interfaceId, "interfaceId");
            requireNonBlank(ammunitionContentId, "ammunitionContentId");
        }
    }

    /** One actor-local track visible to the selected combatant. */
    public record TrackSummary(long targetId, InformationState informationState, boolean positionKnown) {
        /** Validates an actor-local track summary. */
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
