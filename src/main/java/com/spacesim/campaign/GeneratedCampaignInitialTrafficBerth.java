package com.spacesim.campaign;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.persistence.Stage20FreightPersistentState;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState.LocalFleetPhysicalState;
import com.spacesim.world.FleetId;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * One-time M22.7 new-campaign normalization that places generated freight assets in deterministic
 * physical traffic berths instead of materializing every ship at the exact major-hub center.
 *
 * <p>This is not a presentation offset and does not create a second movement authority. The method
 * rewrites the initial atomic Stage-20.5 checkpoint before the ordinary campaign session adopts it;
 * both the freight compatibility mirror and the authoritative local fleet physical state receive the
 * same exact SI position. Restore then proceeds through the existing runtime bridge.</p>
 *
 * <p>Berth spacing is deliberately derived from the installed physical hull envelope. Stage-20A's
 * {@code STATION_TO_STATION} semantic band describes travel between major facilities and therefore
 * must not be reused as a docking/traffic-berth radius.</p>
 */
final class GeneratedCampaignInitialTrafficBerth {
    private static final long ANGLE_BUCKETS = 1_000_003L;
    /**
     * Consecutive berth shells retain slightly more than twice the sum of their top-down bounding
     * radii. The small physical margin prevents subtraction/translation rounding at large local SI
     * coordinates from erasing the promised one-hull-radius center clearance.
     */
    private static final double BERTH_CLEARANCE_FACTOR = 2.05d;

    private GeneratedCampaignInitialTrafficBerth() {
        throw new AssertionError("No instances");
    }

    /**
     * Moves only operational freight rows in a just-created campaign to stable local traffic berths.
     *
     * <p>Stable FleetId determines azimuth. Within each system, FleetId order determines concentric
     * berth shells whose radii are derived from each hull's exact top-down bounding circle. Different
     * shells therefore remain physically separated even if two deterministic azimuths are nearly
     * identical.</p>
     *
     * @param checkpoint exact freshly-created runtime checkpoint
     * @return equivalent checkpoint with non-overlapping physical freight berths
     */
    static Stage20GeneratedWorldRuntimePersistentState apply(
            Stage20GeneratedWorldRuntimePersistentState checkpoint) {
        Stage20GeneratedWorldRuntimePersistentState source = Objects.requireNonNull(checkpoint, "checkpoint");
        ShipEngineeringCatalog engineering = Stage175ICombatTestContentPack.load();
        Map<FleetId, Double> berthRadiusByFleet = berthRadii(source.freight(), engineering);

        Stage20FreightPersistentState freight = source.freight();
        ArrayList<FreighterState> movedFreighters = new ArrayList<>(freight.freighters().size());
        Map<FleetId, LocalPhysicalKinematics> movedByFleet = new HashMap<>();
        for (FreighterState fleet : freight.freighters()) {
            Double berthRadiusM = berthRadiusByFleet.get(fleet.fleetId());
            if (berthRadiusM == null) {
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

    private static Map<FleetId, Double> berthRadii(
            Stage20FreightPersistentState freight,
            ShipEngineeringCatalog engineering) {
        TreeMap<StarSystemId, List<FreighterState>> bySystem = new TreeMap<>();
        for (FreighterState fleet : freight.freighters()) {
            if (fleet.operational()) {
                bySystem.computeIfAbsent(fleet.currentSystemId(), ignored -> new ArrayList<>()).add(fleet);
            }
        }

        HashMap<FleetId, Double> result = new HashMap<>();
        for (List<FreighterState> fleets : bySystem.values()) {
            fleets.sort(Comparator.comparingLong(value -> value.fleetId().value()));
            double previousHullRadiusM = 0d;
            double berthRadiusM = 0d;
            for (FreighterState fleet : fleets) {
                double hullRadiusM = topDownBoundingRadiusM(engineering, fleet);
                if (berthRadiusM == 0d) {
                    berthRadiusM = BERTH_CLEARANCE_FACTOR * hullRadiusM;
                } else {
                    berthRadiusM += BERTH_CLEARANCE_FACTOR * (previousHullRadiusM + hullRadiusM);
                }
                result.put(fleet.fleetId(), berthRadiusM);
                previousHullRadiusM = hullRadiusM;
            }
        }
        return Map.copyOf(result);
    }

    private static double topDownBoundingRadiusM(
            ShipEngineeringCatalog engineering,
            FreighterState fleet) {
        ShipEngineeringCatalog.HullDefinition hull = engineering.findHull(fleet.hullId());
        if (hull == null) {
            throw new IllegalStateException(
                    "freight berth requires installed hull geometry: " + fleet.hullId());
        }
        double halfLengthM = hull.boundingDimensionsM().lengthM() * 0.5d;
        double halfWidthM = hull.boundingDimensionsM().widthM() * 0.5d;
        double radiusM = StrictMath.hypot(halfLengthM, halfWidthM);
        if (!Double.isFinite(radiusM) || radiusM <= 0d) {
            throw new IllegalStateException(
                    "freight berth requires positive finite top-down hull geometry: " + fleet.hullId());
        }
        return radiusM;
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
