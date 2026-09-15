package com.spacesim.campaign;

import com.spacesim.persistence.Stage20FreightPersistentState;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState.LocalFleetPhysicalState;
import com.spacesim.world.FleetId;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalogLoader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One-time M22.7 new-campaign normalization that places generated freight assets in deterministic
 * traffic berths instead of materializing every ship at the exact major-hub center coordinate.
 *
 * <p>This is not a presentation offset and does not create a second movement authority. The method
 * rewrites the initial atomic Stage-20.5 checkpoint before the ordinary campaign session adopts it;
 * both the freight compatibility mirror and the authoritative local fleet physical state receive the
 * same exact SI position. Restore then proceeds through the existing runtime bridge.</p>
 */
final class GeneratedCampaignInitialTrafficBerth {
    private static final long ANGLE_BUCKETS = 1_000_003L;

    private GeneratedCampaignInitialTrafficBerth() {
        throw new AssertionError("No instances");
    }

    /**
     * Moves only operational freight rows in a just-created campaign to stable local traffic berths.
     *
     * <p>The berth radius is not an arbitrary screen-space constant. It reuses the accepted minimum
     * {@code STATION_TO_STATION} operational distance from the Stage-20A SI calibration authority.
     * Stable FleetId determines azimuth, so the same seed and physical identity always obtain the
     * same berth and ships do not stack on one another.</p>
     *
     * @param checkpoint exact freshly-created runtime checkpoint
     * @return equivalent checkpoint with non-overlapping physical freight berths
     */
    static Stage20GeneratedWorldRuntimePersistentState apply(
            Stage20GeneratedWorldRuntimePersistentState checkpoint) {
        Stage20GeneratedWorldRuntimePersistentState source = Objects.requireNonNull(checkpoint, "checkpoint");
        double berthRadiusM = Stage20LocalRouteSemanticBandCatalogLoader.loadDefault().bands().stream()
                .filter(band -> band.id() == BandId.STATION_TO_STATION)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing STATION_TO_STATION calibration"))
                .minDistanceM();

        Stage20FreightPersistentState freight = source.freight();
        ArrayList<FreighterState> movedFreighters = new ArrayList<>(freight.freighters().size());
        Map<FleetId, LocalPhysicalKinematics> movedByFleet = new HashMap<>();
        for (FreighterState fleet : freight.freighters()) {
            if (!fleet.operational()) {
                movedFreighters.add(fleet);
                continue;
            }
            double angle = deterministicAngle(fleet.fleetId());
            LocalPhysicalPosition berth = fleet.physicalState().position().translated(
                    StrictMath.cos(angle) * berthRadiusM,
                    StrictMath.sin(angle) * berthRadiusM);
            LocalPhysicalKinematics physical = LocalPhysicalKinematics.stationary(berth);
            movedByFleet.put(fleet.fleetId(), physical);
            movedFreighters.add(new FreighterState(
                    fleet.fleetId(),
                    fleet.stableFactionId(),
                    fleet.ownershipOrdinal(),
                    fleet.hullId(),
                    fleet.fitId(),
                    fleet.cargoCapacityKg(),
                    fleet.currentSystemId(),
                    physical,
                    fleet.phase(),
                    fleet.activeOrderId(),
                    fleet.routeIndex(),
                    fleet.cargoStorage()));
        }

        Stage20FreightPersistentState movedFreight = new Stage20FreightPersistentState(
                freight.schemaVersion(),
                freight.rootSeed(),
                freight.generatorVersion(),
                freight.worldFingerprint(),
                freight.materializationVersion(),
                freight.compatibilityAuthorityVersion(),
                freight.nextFleetIdValue(),
                freight.nextCargoLotOrdinal(),
                movedFreighters,
                freight.cargoLots(),
                freight.orders());

        List<LocalFleetPhysicalState> movedPhysical = source.localFleetPhysicalStates().stream()
                .map(state -> {
                    LocalPhysicalKinematics replacement = movedByFleet.get(state.fleetId());
                    return replacement == null
                            ? state
                            : new LocalFleetPhysicalState(state.fleetId(), state.systemId(), replacement);
                })
                .toList();

        return new Stage20GeneratedWorldRuntimePersistentState(
                source.schemaVersion(),
                source.bridgeVersion(),
                source.campaign(),
                source.worldState(),
                source.activeSystemId(),
                source.strategicStepTicks(),
                source.remoteUpdateBudgetPerFrame(),
                movedFreight,
                movedPhysical);
    }

    private static double deterministicAngle(FleetId fleetId) {
        long value = Objects.requireNonNull(fleetId, "fleetId").value();
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        mixed ^= mixed >>> 31;
        long bucket = Long.remainderUnsigned(mixed, ANGLE_BUCKETS);
        return (bucket / (double) ANGLE_BUCKETS) * (StrictMath.PI * 2d);
    }
}
