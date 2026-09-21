package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.economy.Stage18ShipyardRuntime;
import com.spacesim.economy.Stage18ShipyardRuntime.SettlementResult;
import com.spacesim.economy.Stage18StationIndustrialNode;
import com.spacesim.persistence.EntityId;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.ShipyardEngineeringService;
import com.spacesim.ship.ShipyardEngineeringService.MaintenanceState;
import com.spacesim.ship.WeaponLoadoutState;
import com.spacesim.ship.WeaponMountRuntime;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;

import java.util.Map;
import java.util.Objects;

/**
 * M22.8G physical production boundary for persistent individual small craft.
 *
 * <p>This service intentionally composes existing Stage-17.5 and Stage-18 authorities rather than
 * inventing a fighter-production pool. It preflights the exact next persistent craft and its
 * physically co-located station bay, settles ordinary Stage-18 shipyard materials/modules/work, then
 * and only then allocates a fresh {@link SmallCraftId}, registers the completed hull and parks it in
 * that station bay.</p>
 *
 * <p>The completed craft starts with no ammunition, reaction mass, cargo or mission payload. Those
 * stores must be loaded later through ordinary Stage-18/19 servicing authorities. The build path
 * therefore cannot act as hidden rearm/refuel/replacement replenishment.</p>
 */
public final class SmallCraftProductionLogisticsService {
    private final SmallCraftRegistry craftRegistry;
    private final SmallCraftHangarRegistry hangars;
    private final ShipEngineeringCatalog engineering;
    private final ShipProtectionCatalog protection;
    private final ShipyardEngineeringService engineeringService;
    private final Stage18ShipyardRuntime shipyardRuntime;
    private final ShipEngineeringRuntime engineeringRuntime;

    /**
     * Creates one production orchestrator over existing physical authorities.
     */
    public SmallCraftProductionLogisticsService(
            SmallCraftRegistry craftRegistry,
            SmallCraftHangarRegistry hangars,
            ShipEngineeringCatalog engineering,
            ShipProtectionCatalog protection,
            ShipyardEngineeringService engineeringService,
            Stage18ShipyardRuntime shipyardRuntime) {
        this.craftRegistry = Objects.requireNonNull(craftRegistry, "craftRegistry");
        this.hangars = Objects.requireNonNull(hangars, "hangars");
        this.engineering = Objects.requireNonNull(engineering, "engineering");
        this.protection = Objects.requireNonNull(protection, "protection");
        this.engineeringService = Objects.requireNonNull(engineeringService, "engineeringService");
        this.shipyardRuntime = Objects.requireNonNull(shipyardRuntime, "shipyardRuntime");
        this.engineeringRuntime = new ShipEngineeringRuntime(engineering);
        if (!craftRegistry.engineeringCatalogFingerprint().equals(engineering.getFingerprint())) {
            throw new IllegalArgumentException(
                    "small-craft registry and production engineering catalog must match");
        }
    }

    /**
     * Builds one new physical craft at an existing Stage-18 station shipyard.
     *
     * <p>The destination bay must be a STATION bay whose host identity equals the exact
     * {@link Stage18StationIndustrialNode#stationId()}. This is the M22.8G delivery boundary: a newly
     * built craft may appear only where the yard physically exists. Moving it to a carrier requires
     * later ordinary launch/mission/recovery or strategic movement authority.</p>
     */
    public BuildResult buildAtStation(
            String stableFactionId,
            String designId,
            Stage18StationIndustrialNode station,
            BayDefinition stationBay,
            Stage18ShipyardRuntime.YardCapabilitySnapshot yard,
            Stage18ShipyardRuntime.YardWorkBudget workBudget) {
        String faction = requireText(stableFactionId, "stableFactionId");
        String design = requireText(designId, "designId");
        Stage18StationIndustrialNode node = Objects.requireNonNull(station, "station");
        BayDefinition bay = Objects.requireNonNull(stationBay, "stationBay");
        Stage18ShipyardRuntime.YardCapabilitySnapshot checkedYard =
                Objects.requireNonNull(yard, "yard");
        Stage18ShipyardRuntime.YardWorkBudget checkedBudget =
                Objects.requireNonNull(workBudget, "workBudget");

        requireCoLocatedStationBay(node, bay);

        DemonstratorFitDefinition authored = engineering.findDemonstratorFit(design);
        if (authored == null) {
            return BuildResult.rejected(
                    BuildStatus.DESIGN_NOT_FOUND, design, craftRegistry.nextIdValue());
        }
        InstalledFit fit = InstalledFit.fromDemonstrator(authored);
        var plan = engineeringService.planBuild(fit, checkedYard.plannerCapability());
        if (!plan.feasibility().feasible()) {
            return BuildResult.rejected(
                    BuildStatus.PLAN_INFEASIBLE, design, craftRegistry.nextIdValue());
        }

        long projectedIdValue = craftRegistry.nextIdValue();
        SmallCraftId projectedId = new SmallCraftId(projectedIdValue);
        SmallCraftState candidate = pristineUnsuppliedCraft(projectedId, faction, design, fit);
        var footprint = craftRegistry.previewCompletedProduction(candidate);
        if (!SmallCraftHangarCapacity.canAccept(bay, hangars.usage(bay.id()), footprint)) {
            return BuildResult.rejected(
                    BuildStatus.STATION_BAY_CAPACITY_BLOCKED, design, projectedIdValue);
        }

        SettlementResult settlement = shipyardRuntime.settleBuild(
                plan,
                node.storage(),
                checkedYard,
                checkedBudget);
        if (!settlement.settled()) {
            return new BuildResult(
                    BuildStatus.STAGE18_SETTLEMENT_REJECTED,
                    null,
                    design,
                    settlement,
                    projectedIdValue);
        }

        // Settlement is now physical and irreversible. Identity is allocated only after it succeeds.
        SmallCraftId id = craftRegistry.reserveIdentityForCompletedProduction();
        if (!id.equals(projectedId)) {
            throw new IllegalStateException(
                    "small-craft allocator changed during deterministic production settlement");
        }
        var completed = engineeringService.completeBuild(
                new EntityId(id.value()),
                plan,
                settlement.compatibilitySettlement());
        if (!completed.fit().equals(fit)) {
            throw new IllegalStateException("shipyard completion changed the authored production fit");
        }

        // Sequential simulation authority means capacity cannot change inside this call, but recheck
        // before final registration so any accidental intervening mutation fails visibly.
        SmallCraftState built = pristineUnsuppliedCraft(id, faction, design, completed.fit());
        var finalFootprint = craftRegistry.previewCompletedProduction(built);
        if (!SmallCraftHangarCapacity.canAccept(bay, hangars.usage(bay.id()), finalFootprint)) {
            throw new IllegalStateException(
                    "station bay capacity changed after physical settlement; cannot hide built craft");
        }

        craftRegistry.registerProducedCraft(built);
        hangars.assign(id, bay, OccupancyState.PARKED);
        return new BuildResult(
                BuildStatus.COMPLETED_AT_STATION,
                id,
                design,
                settlement,
                craftRegistry.nextIdValue());
    }

    private SmallCraftState pristineUnsuppliedCraft(
            SmallCraftId id,
            String faction,
            String designId,
            InstalledFit fit) {
        HullDefinition hull = engineering.findHull(fit.hullId());
        if (hull == null) {
            throw new IllegalArgumentException("production fit references unknown hull: " + fit.hullId());
        }
        var layout = protection.findHullDamageLayout(hull.id());
        if (layout == null) {
            throw new IllegalArgumentException(
                    "production hull lacks exact damage layout: " + hull.id());
        }
        ShipDamageRuntime.Snapshot damage = ShipDamageRuntime.Snapshot.pristine(hull, layout);
        ConsumableState emptyStores = ConsumableState.empty();
        var runtime = engineeringRuntime.initialize(fit, emptyStores, damage.moduleDamage());
        ShipInstanceRuntimeState instance = new ShipInstanceRuntimeState(
                damage,
                Map.of(),
                new MaintenanceState(Map.of()),
                WeaponLoadoutState.empty(),
                WeaponMountRuntime.RuntimeState.empty());
        return new SmallCraftState(id, faction, designId, fit, runtime, instance);
    }

    private static void requireCoLocatedStationBay(
            Stage18StationIndustrialNode station,
            BayDefinition bay) {
        if (bay.hostKind() != HostKind.STATION) {
            throw new IllegalArgumentException(
                    "newly built small craft must enter a physical STATION bay");
        }
        if (!bay.id().hostStableId().equals(station.stationId())) {
            throw new IllegalArgumentException(
                    "production bay host must equal the physical Stage-18 station identity");
        }
    }

    /** Stable G build result family. */
    public enum BuildStatus {
        /** Exact Stage-18 settlement completed and craft exists physically in the station bay. */
        COMPLETED_AT_STATION,
        /** Authored design is absent from the bound production engineering catalog. */
        DESIGN_NOT_FOUND,
        /** Stage-17.5 shipyard planner rejected the fit/yard combination. */
        PLAN_INFEASIBLE,
        /** Current station bay condition/occupancy cannot accept the completed physical craft. */
        STATION_BAY_CAPACITY_BLOCKED,
        /** Stage-18 settlement rejected for stock/work/capability reasons. */
        STAGE18_SETTLEMENT_REJECTED
    }

    /**
     * Immutable production result.
     *
     * @param status stable outcome
     * @param craftId fresh identity only when the craft physically exists
     * @param designId requested authored production fit
     * @param settlement Stage-18 settlement evidence when attempted, otherwise null
     * @param nextIdValue allocator watermark after the operation
     */
    public record BuildResult(
            BuildStatus status,
            SmallCraftId craftId,
            String designId,
            SettlementResult settlement,
            long nextIdValue) {
        public BuildResult {
            Objects.requireNonNull(status, "status");
            designId = requireText(designId, "designId");
            if (nextIdValue <= 0L) {
                throw new IllegalArgumentException("nextIdValue must be positive");
            }
            if ((status == BuildStatus.COMPLETED_AT_STATION) != (craftId != null)) {
                throw new IllegalArgumentException(
                        "craftId must exist exactly for completed production");
            }
            if (status == BuildStatus.STAGE18_SETTLEMENT_REJECTED && settlement == null) {
                throw new IllegalArgumentException(
                        "Stage-18 rejection requires settlement diagnostics");
            }
        }

        static BuildResult rejected(
                BuildStatus status,
                String designId,
                long nextIdValue) {
            if (status == BuildStatus.COMPLETED_AT_STATION
                    || status == BuildStatus.STAGE18_SETTLEMENT_REJECTED) {
                throw new IllegalArgumentException("invalid simple rejection status");
            }
            return new BuildResult(status, null, designId, null, nextIdValue);
        }

        /** @return whether a new physical craft was actually created */
        public boolean completed() {
            return status == BuildStatus.COMPLETED_AT_STATION;
        }
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
