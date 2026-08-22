package com.spacesim.world;

import com.spacesim.ship.TrackState;
import com.spacesim.ship.TrackState.InformationState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState;

import java.util.Objects;

/**
 * Read-only Stage-20G discovery projection over the authoritative Stage-17.5 mobile track model.
 *
 * <p>The projection retains the original {@link TrackState}; it does not copy truth coordinates,
 * create a persistent static row or promote a system-local target ID into a {@code FleetId}.
 * `FIRE_CONTROL` remains visible as a track-quality flag while the coarse Stage-20G discovery index
 * remains `TRACKED`.</p>
 */
public final class Stage20MobileSensorVisibility {
    private Stage20MobileSensorVisibility() {
        throw new AssertionError("No instances");
    }

    /**
     * Observer-local mobile contact projection.
     *
     * @param track authoritative Stage-17.5 track, covariance and freshness state
     * @param discoveryState coarse Stage-20G discovery index derived from the track
     * @param fireControlQualified whether the underlying track currently has fire-control quality
     */
    public record MobileContactView(
            TrackState track,
            DiscoveryState discoveryState,
            boolean fireControlQualified) {
        /**
         * Validates that the projection cannot diverge from its authoritative track.
         *
         * @param track authoritative Stage-17.5 track
         * @param discoveryState derived Stage-20G discovery index
         * @param fireControlQualified whether the track has fire-control quality
         */
        public MobileContactView {
            Objects.requireNonNull(track, "track");
            Objects.requireNonNull(discoveryState, "discoveryState");
            DiscoveryState expected = Stage20MobileSensorVisibility.discoveryState(
                    track.informationState());
            if (discoveryState != expected
                    || fireControlQualified != (track.informationState() == InformationState.FIRE_CONTROL)) {
                throw new IllegalArgumentException("mobile visibility projection differs from Stage-17.5 track");
            }
        }

        /** @return whether classification evidence is currently available */
        public boolean classified() {
            return track.informationState().ordinal() >= InformationState.CLASSIFIED.ordinal();
        }

        /** @return whether an uncertain Cartesian position estimate is available */
        public boolean positionEstimateAvailable() {
            return track.positionKnown();
        }

        /** @return whether an uncertain range estimate is available */
        public boolean rangeEstimateAvailable() {
            return track.covariance().hasRangeCovariance();
        }

        /** @return false because a sensor estimate is never exact physical range truth */
        public boolean exactRangeAvailable() {
            return false;
        }

        /** @return false because classification confidence is not exact identity authority */
        public boolean exactIdentityAvailable() {
            return false;
        }

        /** @return false because the current production TrackState has no velocity-estimate channel */
        public boolean velocityEstimateAvailable() {
            return false;
        }

        /** @return false because TrackState does not expose physical loadout truth */
        public boolean loadoutAvailable() {
            return false;
        }

        /** @return false because a mobile hypothesis is never durable static-location knowledge */
        public boolean knownStaticLocation() {
            return false;
        }
    }

    /**
     * Projects one existing Stage-17.5 track into the coarse Stage-20G discovery vocabulary.
     *
     * @param track authoritative observer-local mobile track
     * @return read-only visibility projection
     */
    public static MobileContactView fromTrack(TrackState track) {
        TrackState checked = Objects.requireNonNull(track, "track");
        return new MobileContactView(
                checked,
                discoveryState(checked.informationState()),
                checked.informationState() == InformationState.FIRE_CONTROL);
    }

    private static DiscoveryState discoveryState(InformationState state) {
        return switch (Objects.requireNonNull(state, "state")) {
            case DETECTED -> DiscoveryState.DETECTED;
            case CLASSIFIED -> DiscoveryState.CLASSIFIED;
            case TRACKED, FIRE_CONTROL -> DiscoveryState.TRACKED;
        };
    }
}
