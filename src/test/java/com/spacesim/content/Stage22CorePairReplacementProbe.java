package com.spacesim.content;

import com.spacesim.content.ship.*;
import com.spacesim.economy.*;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipyardEngineeringService;
import com.spacesim.world.*;
import com.spacesim.world.SettlementRecoveryState.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Paid replacement of an explicitly declared post-war loss obligation for either actual core owner. */
final class Stage22CorePairReplacementProbe {
    private static final FleetId LOST_FLEET = new FleetId(9_999_999L);

    private Stage22CorePairReplacementProbe() { }

    static Result run(boolean empire) {
        var setup = Stage22CorePairRecoveryProbe.prepareYard(empire);
        var fit = fit(setup, empire);
        var world = Stage22CorePairWorldFixture.create(Stage22CorePairExperimentProtocol.FIRST_SEED);
        var recovery = recovery(world, fit, empire);
        var identities = FactionIdentityResolver.createDefault(ContentCatalogLoader.loadDefault(), world.snapshot().factionIdentities());
        var service = service(setup, setup.engineering());
        var plan = new ShipyardEngineeringService(setup.engineering(), setup.industrial())
                .planBuild(fit, setup.yard().plannerCapability());
        double seconds = plan.requirements().totalWorkSeconds() / setup.yard().plannerCapability().workRate();
        var before = world.snapshot();
        var denied = service.buildReplacement(recovery, 1L, world, identities, world.getActiveSystemId(), "Core replacement",
                0f, 0f, fit, setup.station().storage(), setup.yard(), setup.yard().openInterval(seconds + 1d), 0L);
        boolean noFreeFleet = !denied.settlement().settled() && before.equals(world.snapshot());
        var stock = stock(setup, fit);
        var initialStock = stock.snapshot();
        var initialMaterials = stock.snapshotCommodityMassByIdKg();
        var initialProducts = stock.snapshotProductCountById();
        var shortAttempt = service.buildReplacement(recovery, 1L, world, identities, world.getActiveSystemId(), "Core replacement",
                0f, 0f, fit, stock, setup.yard(), setup.yard().openInterval(seconds / 2d), 0L);
        boolean finiteWork = !shortAttempt.settlement().settled() && initialStock.equals(stock.snapshot())
                && before.equals(world.snapshot());
        byte[] recoveryBytes = SettlementRecoveryStateCodec.encode(recovery.snapshot());
        var replayRecovery = new SettlementRecoveryService(SettlementRecoveryStateCodec.decode(recoveryBytes));
        var replayWorld = Stage22CorePairWorldFixture.roundTrip(world);
        var replayStock = Stage18StationStorage.restore(Stage18ResourceOntologyLoader.loadDefault(), setup.products(), initialStock);
        var built = service.buildReplacement(recovery, 1L, world, identities, world.getActiveSystemId(), "Core replacement",
                0f, 0f, fit, stock, setup.yard(), setup.yard().openInterval(seconds + 1d), 0L);
        var replayed = service.buildReplacement(replayRecovery, 1L, replayWorld, identities, replayWorld.getActiveSystemId(), "Core replacement",
                0f, 0f, fit, replayStock, setup.yard(), setup.yard().openInterval(seconds + 1d), 0L);
        if (!built.settlement().settled()) throw new AssertionError("Declared finite build stock failed: " + built.settlement());
        boolean continuation = built.equals(replayed) && world.snapshot().equals(replayWorld.snapshot())
                && stock.snapshot().equals(replayStock.snapshot())
                && Arrays.equals(recoveryBytes, SettlementRecoveryStateCodec.encode(SettlementRecoveryStateCodec.decode(recoveryBytes)));
        var placed = world.findFleet(built.commissionedFleetId()).orElseThrow();
        var ship = world.snapshot().systems().stream().filter(system -> system.systemId().equals(placed.systemId()))
                .flatMap(system -> system.simulationState().entities().stream())
                .filter(entity -> entity.id().equals(placed.localEntityId())).findFirst().orElseThrow().engineering();
        boolean emptyCommission = ship.consumables().interfaceLoads().isEmpty() && ship.sharedBusEnergyJ() == 0d
                && ship.instanceState().shieldsByMount().stream().allMatch(shield -> shield.reserveJ() == 0d)
                && ship.instanceState().weaponFeeds().isEmpty();
        boolean newIdentity = !LOST_FLEET.equals(built.commissionedFleetId())
                && world.getFleetPlacements().size() == before.fleets().size() + 1
                && recovery.snapshot().requireReplacementDemand(1L).status() == ReplacementStatus.COMMISSIONED;
        boolean materialsClosed = initialMaterials.equals(built.settlement().consumedCommodityMassKg())
                && initialProducts.equals(built.settlement().consumedProductCount())
                && stock.snapshotCommodityMassByIdKg().isEmpty() && stock.snapshotProductCountById().isEmpty();
        double moduleMassKg = built.settlement().consumedProductCount().entrySet().stream()
                .mapToDouble(row -> setup.products().findProduct(row.getKey()).unitMassKg() * row.getValue()).sum();
        var completedWorld = world.snapshot();
        var completedStock = stock.snapshot();
        boolean duplicateRejected = false;
        try {
            service.buildReplacement(recovery, 1L, world, identities, world.getActiveSystemId(), "Duplicate",
                    0f, 0f, fit, stock, setup.yard(), setup.yard().openInterval(seconds + 1d), 0L);
        } catch (IllegalStateException expected) {
            duplicateRejected = completedWorld.equals(world.snapshot()) && completedStock.equals(stock.snapshot());
        }
        var raw = empire ? Stage22EmpireEngineeringCatalogLoader.loadDefault() : Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        boolean incompatibleRejected = rejectionPreservesStock(setup, fit, empire, raw, false, false);
        boolean badSystemRejected = rejectionPreservesStock(setup, fit, empire, setup.engineering(), true, false);
        boolean negativeTickRejected = rejectionPreservesStock(setup, fit, empire, setup.engineering(), false, true);
        return new Result(empire ? Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID : Stage22CorePairBalanceEvidence.UNION_FACTION_ID,
                seconds, built.settlement().consumedCommodityMassKg().values().stream().mapToDouble(Double::doubleValue).sum(),
                moduleMassKg, materialsClosed, noFreeFleet, finiteWork, continuation, emptyCommission, newIdentity, duplicateRejected,
                incompatibleRejected, badSystemRejected, negativeTickRejected);
    }

    private static boolean rejectionPreservesStock(Stage22CorePairRecoveryProbe.PreparedYard setup, InstalledFit fit,
            boolean empire, ShipEngineeringCatalog engineering, boolean badSystem, boolean negativeTick) {
        var world = Stage22CorePairWorldFixture.create(Stage22CorePairExperimentProtocol.FIRST_SEED);
        var recovery = recovery(world, fit, empire);
        var before = world.snapshot();
        var recoveryBefore = recovery.snapshot();
        var stock = stock(setup, fit);
        var beforeStock = stock.snapshot();
        var plan = new ShipyardEngineeringService(engineering, setup.industrial()).planBuild(fit, setup.yard().plannerCapability());
        var budget = setup.yard().openInterval(plan.requirements().totalWorkSeconds() / setup.yard().plannerCapability().workRate() + 1d);
        double workBefore = budget.remainingWorkSeconds();
        try {
            service(setup, engineering).buildReplacement(recovery, 1L, world,
                    FactionIdentityResolver.createDefault(ContentCatalogLoader.loadDefault(), world.snapshot().factionIdentities()),
                    badSystem ? new StarSystemId(999_999L) : world.getActiveSystemId(), "Rejected replacement", 0f, 0f, fit,
                    stock, setup.yard(), budget, negativeTick ? -1L : 0L);
        } catch (IllegalArgumentException expected) {
            return before.equals(world.snapshot()) && recoveryBefore.equals(recovery.snapshot())
                    && beforeStock.equals(stock.snapshot()) && workBefore == budget.remainingWorkSeconds();
        }
        return false;
    }

    private static InstalledFit fit(Stage22CorePairRecoveryProbe.PreparedYard setup, boolean empire) {
        return InstalledFit.fromDemonstrator(setup.engineering().findDemonstratorFit(empire
                ? "fit.empire.destroyer.screen_v1" : "fit.industrial_union.destroyer.line_v1"));
    }

    private static Stage18StationStorage stock(Stage22CorePairRecoveryProbe.PreparedYard setup, InstalledFit fit) {
        Map<String, Double> materials = new TreeMap<>();
        setup.yards().findHullProfile(fit.hullId()).buildInputsKg()
                .forEach(input -> materials.merge(input.commodityId(), input.massKg(), Double::sum));
        Map<String, Integer> modules = new TreeMap<>();
        fit.installedModules().forEach(row -> modules.merge(row.moduleId(), 1, Integer::sum));
        return new Stage18StationStorage(Stage18ResourceOntologyLoader.loadDefault(), setup.products(), setup.station().stationId(),
                setup.station().storage().snapshotCapacityByStorageClassKg(), materials, modules);
    }

    private static Stage21GPhysicalRecoveryService service(Stage22CorePairRecoveryProbe.PreparedYard setup, ShipEngineeringCatalog engineering) {
        return new Stage21GPhysicalRecoveryService(Stage18ShipConsumableCatalogLoader.loadDefault(),
                new Stage18ShipConsumableService(Stage18ShipConsumableCatalogLoader.loadDefault(), engineering),
                new Stage19WarfareSupplyService(setup.products()), new ShipyardEngineeringService(engineering, setup.industrial()),
                setup.yardRuntime(), engineering);
    }

    private static SettlementRecoveryService recovery(WorldSimulation world, InstalledFit fit, boolean empire) {
        String owner = empire ? Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID : Stage22CorePairBalanceEvidence.UNION_FACTION_ID;
        long tick = world.getAuthoritativeWorldTick();
        var settlement = new Settlement(1L, "proposal.core.postwar", "war.core.postwar",
                Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID, Stage22CorePairBalanceEvidence.UNION_FACTION_ID,
                tick, tick, SettlementStatus.PENDING, false);
        // The probe begins at the post-war obligation boundary; this is declared initial loss provenance.
        var result = new SettlementRecoveryService(new SettlementRecoveryState(SettlementRecoveryState.CURRENT_VERSION,
                tick, 2L, 1L, List.of(settlement), List.of(), List.of(),
                List.of(new FleetLossRecord(1L, 1L, LOST_FLEET, owner, tick)), List.of()));
        result.requestReplacement(1L, LOST_FLEET, fit, tick);
        result.finalizeRecoveryPlan(1L, tick);
        return result;
    }

    record Result(String factionId, double buildSeconds, double hullInputMassKg, double moduleInputMassKg,
            boolean materialsClosed, boolean noFreeFleet,
            boolean finiteWork, boolean continuation, boolean emptyCommission, boolean newIdentity,
            boolean duplicateRejected, boolean incompatibleRejected, boolean badSystemRejected, boolean negativeTickRejected) {
        boolean valid() { return materialsClosed && noFreeFleet && finiteWork && continuation && emptyCommission && newIdentity
                && duplicateRejected && incompatibleRejected && badSystemRejected && negativeTickRejected; }
    }

    public static void main(String[] args) {
        for (boolean empire : new boolean[] {true, false}) {
            var result = run(empire);
            System.out.println(result);
            if (!result.valid()) throw new AssertionError(result);
        }
    }
}
