package com.spacesim.world;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18ShipyardCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
import com.spacesim.content.Stage228SmallCraftShipConsumableCatalogLoader;
import com.spacesim.content.Stage228SmallCraftShipyardCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage228SmallCraftProductionProjection;
import com.spacesim.content.ship.Stage228SmallCraftShipyardIndustrialCatalogLoader;
import com.spacesim.content.ship.Stage22CorePairProtectionCatalogLoader;
import com.spacesim.content.weapon.Stage228SmallCraftWeaponRuntimeCatalogLoader;
import com.spacesim.economy.Stage18FacilityRuntime;
import com.spacesim.economy.Stage228FinalCarrierEconomyAccess;
import com.spacesim.economy.Stage18ShipConsumableService;
import com.spacesim.economy.Stage18ShipyardRuntime;
import com.spacesim.economy.Stage18StationIndustrialNode;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.ImportedCombatantState;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipyardEngineeringService;
import com.spacesim.ship.ShipyardEngineeringService.WorkSettlement;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.CombatantResult;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.Result;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.Termination;
import com.spacesim.world.CarrierWingStrategicReadinessService.CarrierWingAssignment;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationEvidence;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.SmallCraftFlightDeckOperations.DeckProfile;
import com.spacesim.world.SmallCraftFlightDeckOperations.OperationKind;
import com.spacesim.world.SmallCraftFlightDeckOperations.OperationPhase;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftMissionCommandService.DeploymentState;
import com.spacesim.world.SmallCraftMissionCommandService.MissionCommand;
import com.spacesim.world.SmallCraftMissionCommandService.MissionContext;
import com.spacesim.world.SmallCraftMissionState.MissionStatus;
import com.spacesim.world.SmallCraftMissionState.MissionTarget;
import com.spacesim.world.SmallCraftMissionState.MissionType;
import com.spacesim.world.SmallCraftMissionState.TargetKind;
import com.spacesim.world.SmallCraftPhysicalLogisticsService.DeliveryReceipt;
import com.spacesim.world.SmallCraftPhysicalLogisticsService.LogisticsState;
import com.spacesim.world.SmallCraftStationSupplyService.SupplyStatus;
import com.spacesim.world.SmallCraftTacticalEncounterService.ExternalCombatant;
import com.spacesim.world.SmallCraftTacticalEncounterService.LocalFlightState;
import com.spacesim.world.SmallCraftTacticalEncounterService.SmallCraftParticipant;
import com.spacesim.world.SmallCraftTurnaroundService.ServiceProfile;
import com.spacesim.world.SmallCraftTurnaroundService.TurnaroundRequest;
import com.spacesim.world.SmallCraftTurnaroundService.TurnaroundSettlement;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.8N final deterministic carrier/small-craft acceptance corpus.
 *
 * <p>The corpus composes already-authoritative Stage 17.5/18/19/21 and M22.8 A-M services. It owns
 * no gameplay state. Each scenario proves a different part of the final causal chain while sharing
 * the same production content, physical logistics, individual-craft identity and no-grant rules.</p>
 */
@org.junit.jupiter.api.Tag("slow")
final class Stage228FinalCarrierAcceptanceTest {
    private static final String STATION_ID = "station.m22_8n.production";
    private static final String HOST = "carrier.m22_8n.alpha";
    private static final BayId CARRIER_BAY_ID = new BayId(HOST, "bay.flight");
    private static final FleetId CARRIER_FLEET = new FleetId(22_800L);
    private static final StarSystemId SYSTEM = new StarSystemId(228L);
    private static final String WATER_ID = "commodity.material.purified_water";
    private static final String DESIGN_ID =
            Stage228SmallCraftProductionProjection.EMPIRE_INTERCEPTOR_FIT_ID;

    private static final BayDefinition CARRIER_BAY = new BayDefinition(
            CARRIER_BAY_ID,
            HostKind.SHIP,
            new Dimensions3d(120d, 120d, 50d),
            100_000_000d,
            100_000_000d,
            1d);

    @Test
    void playerIndustryMissionRecoveryAndSaveLoadFormOneContinuousPhysicalChain() {
        ContentCatalog.FactionDefinition faction = ContentCatalogLoader.loadDefault().getFactions().get(0);
        Stage228CampaignAuthority authority = campaign(CARRIER_BAY);
        ProductionFixture production = productionFixture(authority);

        SmallCraftId craftId = buildSupplyAndDeliverReady(
                authority, production, faction.id(), CARRIER_BAY, 0L);
        assertEquals(1, authority.smallCraft().size());
        assertEquals(OccupancyState.READY,
                authority.hangars().find(craftId).orElseThrow().state());

        CarrierWingAssignment wing = new CarrierWingAssignment(
                CARRIER_FLEET, HOST, faction.id(), List.of(craftId));
        authority.commitCarrierWings(List.of(wing));

        SmallCraftMissionCommandService commands = new SmallCraftMissionCommandService(
                authority.smallCraft(),
                authority.hangars(),
                authority.flightDeck(),
                production.engineering());
        long tick = authoritativeTick(authority);
        var launch = commands.submit(
                authority.missions(),
                mission(craftId, OrderSource.PLAYER, MissionType.CAP,
                        TargetKind.AREA, "area.m22_8n.player"),
                context(faction.id(), tick, DeploymentState.EMBARKED,
                        "area.m22_8n.player"));
        authority.commitMissionState(launch.state());
        assertEquals(MissionStatus.LAUNCH_QUEUED, launch.mission().status());

        advanceOneCampaignStep(authority, CARRIER_BAY);
        assertEquals(OperationPhase.AWAITING_HANDOFF,
                authority.flightDeck().activeFor(craftId).orElseThrow().phase());
        authority.commitMissionState(
                commands.confirmPhysicalLaunch(authority.missions(), launch.mission().id()));
        assertTrue(authority.hangars().find(craftId).isEmpty());
        assertEquals(MissionStatus.ACTIVE,
                authority.missions().activeMissionFor(craftId).orElseThrow().status());

        double reactionMassBefore = authority.smallCraft().find(craftId).orElseThrow()
                .runtimeState().consumables().reactionMassKg();
        SmallCraftTacticalEncounterService deterministicCombat =
                new SmallCraftTacticalEncounterService(
                        authority.smallCraft(),
                        authority.hangars(),
                        Stage228FinalCarrierAcceptanceTest::survivorConsumesReactionMass);
        var combat = deterministicCombat.resolve(
                authority.missions(),
                List.of(new SmallCraftParticipant(
                        craftId, Side.ALPHA, new LocalFlightState(0d, 0d, 0d, 0d))),
                List.of(externalOpponent(
                        authority.smallCraft().find(craftId).orElseThrow(),
                        "player-chain-opponent")),
                5L);
        authority.commitMissionState(combat.missionState());
        assertEquals(
                reactionMassBefore - 100d,
                authority.smallCraft().find(craftId).orElseThrow()
                        .runtimeState().consumables().reactionMassKg(),
                1e-9,
                "exact tactical commit-back must preserve real consumable expenditure");

        tick = authoritativeTick(authority);
        var returning = commands.submit(
                authority.missions(),
                mission(craftId, OrderSource.PLAYER, MissionType.RECOVER,
                        TargetKind.HOST, HOST),
                context(faction.id(), tick, DeploymentState.DEPLOYED, HOST));
        authority.commitMissionState(returning.state());
        authority.commitMissionState(commands.offerPhysicalRecovery(
                authority.missions(),
                returning.mission().id(),
                CARRIER_BAY,
                tick));
        assertTrue(authority.hangars().find(craftId).isEmpty(),
                "recovery intent cannot teleport the craft into its carrier");

        advanceOneCampaignStep(authority, CARRIER_BAY);
        authority.commitMissionState(commands.completePhysicalRecovery(
                authority.missions(), returning.mission().id()));
        assertEquals(OccupancyState.SERVICING,
                authority.hangars().find(craftId).orElseThrow().state());
        assertEquals(MissionStatus.COMPLETE,
                authority.missions().requireMission(returning.mission().id()).status());

        var beforeSave = authority.captureState();
        byte[] bytes = Stage228GeneratedCampaignPersistenceCodec.encode(beforeSave);
        Stage228CampaignAuthority restored = Stage228CampaignAuthority.restore(
                Stage228GeneratedCampaignPersistenceCodec.decode(bytes));

        assertEquals(beforeSave, restored.captureState());
        assertEquals(craftId, restored.smallCraft().snapshot().get(0).id());
        assertEquals(OccupancyState.SERVICING,
                restored.hangars().find(craftId).orElseThrow().state());
        assertEquals(MissionStatus.COMPLETE,
                restored.missions().requireMission(returning.mission().id()).status());
        assertEquals(reactionMassBefore - 100d,
                restored.smallCraft().find(craftId).orElseThrow()
                        .runtimeState().consumables().reactionMassKg(),
                1e-9);
    }

    @Test
    void aiPathUsesRealStage19ExactAuthorityWithoutAParallelSmallCraftCombatEngine() {
        ContentCatalog.FactionDefinition faction = ContentCatalogLoader.loadDefault().getFactions().get(0);
        Stage228CampaignAuthority authority = campaign(CARRIER_BAY);
        ProductionFixture production = productionFixture(authority);
        SmallCraftId craftId = buildSupplyAndDeliverReady(
                authority, production, faction.id(), CARRIER_BAY, 0L);

        SmallCraftMissionCommandService commands = new SmallCraftMissionCommandService(
                authority.smallCraft(),
                authority.hangars(),
                authority.flightDeck(),
                production.engineering());
        long tick = authoritativeTick(authority);
        var launch = commands.submit(
                authority.missions(),
                mission(craftId, OrderSource.AI, MissionType.CAP,
                        TargetKind.AREA, "area.m22_8n.ai"),
                context(faction.id(), tick, DeploymentState.EMBARKED, "area.m22_8n.ai"));
        authority.commitMissionState(launch.state());
        advanceOneCampaignStep(authority, CARRIER_BAY);
        authority.commitMissionState(
                commands.confirmPhysicalLaunch(authority.missions(), launch.mission().id()));

        SmallCraftState source = authority.smallCraft().find(craftId).orElseThrow();
        SmallCraftTacticalEncounterService productionCombat =
                SmallCraftTacticalEncounterService.production(
                        authority.smallCraft(), authority.hangars());
        var result = productionCombat.resolve(
                authority.missions(),
                List.of(new SmallCraftParticipant(
                        craftId, Side.ALPHA, new LocalFlightState(0d, 0d, 0d, 0d))),
                List.of(externalOpponent(source, "real-stage19-opponent")),
                1L);

        assertEquals(1L, result.ticksExecuted());
        assertEquals(1, result.smallCraft().size());
        assertEquals(1, result.externalCombatants().size());
        assertEquals("real-stage19-opponent",
                result.externalCombatants().get(0).referenceId());
        assertTrue(result.smallCraft().get(0).destroyed()
                        || authority.smallCraft().find(craftId).isPresent(),
                "real Stage-19 result must either preserve the same identity or destroy it physically");
    }

    @Test
    void physicalLossDegradesStrategicWingThenFundedIndustryCreatesOnlyFreshReplacement() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        ContentCatalog.FactionDefinition faction = content.getFactions().get(0);
        FactionIdentityResolver identities =
                FactionIdentityResolver.createDefault(content, List.of());
        Stage228CampaignAuthority authority = campaign(CARRIER_BAY);
        ProductionFixture production = productionFixture(authority);

        SmallCraftId survivor = buildSupplyAndDeliverReady(
                authority, production, faction.id(), CARRIER_BAY, 0L);
        SmallCraftId doomed = buildSupplyAndDeliverReady(
                authority, production, faction.id(), CARRIER_BAY, 0L);
        CarrierWingAssignment wing = new CarrierWingAssignment(
                CARRIER_FLEET, HOST, faction.id(), List.of(survivor, doomed));
        authority.commitCarrierWings(List.of(wing));

        FleetForceRegistry forces = carrierForces(faction);
        CarrierWingStrategicReadinessService readiness =
                new CarrierWingStrategicReadinessService(production.engineering());
        var healthy = readiness.project(
                forces,
                authority.smallCraft(),
                authority.hangars(),
                authority.missions(),
                identities,
                authority.carrierWings());
        assertEquals(2, healthy.wing(CARRIER_FLEET).orElseThrow().readyOrActiveCraft());
        assertEquals(0, healthy.wing(CARRIER_FLEET).orElseThrow().lostCraft());

        SmallCraftMissionCommandService commands = new SmallCraftMissionCommandService(
                authority.smallCraft(),
                authority.hangars(),
                authority.flightDeck(),
                production.engineering());
        long tick = authoritativeTick(authority);
        var launch = commands.submit(
                authority.missions(),
                mission(doomed, OrderSource.AI, MissionType.CAP,
                        TargetKind.AREA, "area.m22_8n.loss"),
                context(faction.id(), tick, DeploymentState.EMBARKED, "area.m22_8n.loss"));
        authority.commitMissionState(launch.state());
        advanceOneCampaignStep(authority, CARRIER_BAY);
        authority.commitMissionState(
                commands.confirmPhysicalLaunch(authority.missions(), launch.mission().id()));

        SmallCraftTacticalEncounterService lossCombat =
                new SmallCraftTacticalEncounterService(
                        authority.smallCraft(),
                        authority.hangars(),
                        Stage228FinalCarrierAcceptanceTest::destroySmallCraftParticipant);
        var loss = lossCombat.resolve(
                authority.missions(),
                List.of(new SmallCraftParticipant(
                        doomed, Side.ALPHA, new LocalFlightState(0d, 0d, 0d, 0d))),
                List.of(externalOpponent(
                        authority.smallCraft().find(doomed).orElseThrow(),
                        "loss-opponent")),
                5L);
        authority.commitMissionState(loss.missionState());

        assertTrue(authority.smallCraft().find(doomed).isEmpty());
        assertEquals(MissionStatus.FAILED,
                authority.missions().requireMission(launch.mission().id()).status());
        var degraded = readiness.project(
                forces,
                authority.smallCraft(),
                authority.hangars(),
                authority.missions(),
                identities,
                authority.carrierWings());
        assertEquals(1, degraded.wing(CARRIER_FLEET).orElseThrow().lostCraft());
        assertEquals(5_000, degraded.wing(CARRIER_FLEET).orElseThrow().availabilityBps());

        InstalledFit fit = InstalledFit.fromDemonstrator(
                production.engineering().findDemonstratorFit(DESIGN_ID));
        var replacementPlan = production.logistics().planBuild(
                faction.id(),
                DESIGN_ID,
                STATION_ID,
                fit,
                production.yard());
        loadBuildInputs(production, fit);
        var work = production.yard().openInterval(
                replacementPlan.workPlan().requirements().totalWorkSeconds()
                        / production.yard().plannerCapability().workRate() + 5d);
        WalletComponent treasury = new WalletComponent(50_000L);
        WalletComponent procurement = new WalletComponent();
        CarrierPostBattleRecoveryService recovery =
                new CarrierPostBattleRecoveryService(
                        production.logistics(),
                        (stableFaction, destination, ledger, amount) ->
                                treasury.transferTo(destination, amount));

        long treasuryBefore = treasury.getBalanceMilliCredits();
        var funded = recovery.fundAndSettleBuild(
                faction.id(),
                procurement,
                "station:m22_8n:replacement-procurement",
                12_000L,
                authority.logistics(),
                replacementPlan,
                production.station().storage(),
                work);
        authority.commitLogisticsState(funded.logisticsState());

        assertTrue(funded.treasuryFunded());
        assertTrue(funded.replacementProduced());
        assertEquals(treasuryBefore - 12_000L, treasury.getBalanceMilliCredits());
        SmallCraftId replacement = funded.build().producedCraftOptional().orElseThrow().id();
        assertNotEquals(doomed, replacement);
        assertTrue(replacement.value() > doomed.value(),
                "permanent loss must never permit identity reuse");

        var delivered = recovery.confirmReplacementDelivery(
                authority.logistics(),
                replacement,
                CARRIER_BAY,
                authoritativeTick(authority));
        authority.commitLogisticsState(delivered.logisticsState());
        assertTrue(delivered.assigned());
        assertEquals(OccupancyState.SERVICING,
                authority.hangars().find(replacement).orElseThrow().state());
        assertTrue(authority.smallCraft().find(doomed).isEmpty());

        var checkpoint = authority.captureState();
        Stage228CampaignAuthority restored = Stage228CampaignAuthority.restore(
                Stage228GeneratedCampaignPersistenceCodec.decode(
                        Stage228GeneratedCampaignPersistenceCodec.encode(checkpoint)));
        assertEquals(checkpoint, restored.captureState());
        assertTrue(restored.smallCraft().find(doomed).isEmpty());
        assertTrue(restored.smallCraft().find(replacement).isPresent());
        assertEquals(OccupancyState.SERVICING,
                restored.hangars().find(replacement).orElseThrow().state());
    }

    @Test
    void rejectedStationToCarrierRelocationLeavesReadyCraftAtPhysicalSource() {
        ContentCatalog.FactionDefinition faction =
                ContentCatalogLoader.loadDefault().getFactions().get(0);
        Stage228CampaignAuthority authority = campaign(CARRIER_BAY);
        ProductionFixture production = productionFixture(authority);
        SmallCraftId craftId = buildSupplyReadyAtStation(
                authority, production, faction.id(), 0L);
        BayDefinition impossible = new BayDefinition(
                new BayId("carrier.m22_8n.too_small", "bay.flight"),
                HostKind.SHIP,
                new Dimensions3d(1d, 1d, 1d),
                1d,
                1d,
                1d);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> production.logistics().transferReadyCraft(
                        craftId, impossible, authoritativeTick(authority)));

        var source = authority.hangars().find(craftId).orElseThrow();
        assertEquals(STATION_ID, source.bayId().hostStableId());
        assertEquals(HostKind.STATION, source.hostKind());
        assertEquals(OccupancyState.READY, source.state());
        assertTrue(authority.smallCraft().find(craftId).isPresent());
    }

    @Test
    void denseWingLongRunDoesNotGrantCraftReuseIdsOrMaterializeDormantState() {
        ContentCatalog.FactionDefinition faction = ContentCatalogLoader.loadDefault().getFactions().get(0);
        BayDefinition denseBay = new BayDefinition(
                new BayId("carrier.m22_8n.dense", "bay.flight"),
                HostKind.SHIP,
                new Dimensions3d(1_000d, 1_000d, 1_000d),
                1.0e12d,
                1.0e12d,
                1d);
        Stage228CampaignAuthority authority = campaign(denseBay);
        ProductionFixture production = productionFixture(authority);
        SmallCraftId first = buildSupplyAndDeliverReady(
                authority, production, faction.id(), denseBay, 0L);
        SmallCraftState template = authority.smallCraft().find(first).orElseThrow();

        ArrayList<SmallCraftId> ids = new ArrayList<>();
        ids.add(first);
        for (int index = 1; index < 256; index++) {
            SmallCraftId id = authority.smallCraft().reserveIdentityForCompletedProduction();
            authority.smallCraft().registerProducedCraft(new SmallCraftState(
                    id,
                    template.stableFactionId(),
                    template.designId(),
                    template.fit(),
                    template.runtimeState(),
                    template.instanceState()));
            authority.hangars().assign(id, denseBay, OccupancyState.READY);
            ids.add(id);
        }

        List<SmallCraftState> physicalBefore = authority.smallCraft().snapshot();
        for (SmallCraftId id : ids) {
            authority.flightDeck().requestLaunch(id, denseBay.id(), 0L);
        }

        ArrayList<SmallCraftId> launchOrder = new ArrayList<>();
        Map<BayId, BayDefinition> bays = Map.of(denseBay.id(), denseBay);
        long tick = 0L;
        while (!authority.flightDeck().queued().isEmpty()
                || !authority.flightDeck().active().isEmpty()) {
            authority.flightDeck().advanceFixedTick(tick++, 1d, bays);
            if (!authority.flightDeck().active().isEmpty()) {
                var active = authority.flightDeck().active().get(0);
                assertEquals(OperationKind.LAUNCH, active.request().kind());
                assertEquals(OperationPhase.AWAITING_HANDOFF, active.phase());
                launchOrder.add(active.request().craftId());
                authority.flightDeck().confirmLaunchHandoff(active.request().craftId());
            }
        }

        for (int index = 0; index < 1_000; index++) {
            authority.flightDeck().advanceFixedTick(tick++, 1d, bays);
        }

        assertEquals(ids, launchOrder,
                "dense deterministic queue must preserve canonical individual-craft order");
        assertEquals(256, authority.smallCraft().size());
        assertEquals(257L, authority.smallCraft().nextIdValue());
        assertEquals(physicalBefore, authority.smallCraft().snapshot(),
                "long-run deck scheduling may not grant or rewrite physical craft state");
        assertEquals(256, new HashSet<>(ids).size());
        assertTrue(authority.hangars().snapshot().isEmpty());
        assertTrue(authority.flightDeck().queued().isEmpty());
        assertTrue(authority.flightDeck().active().isEmpty());
    }

    private static Stage228CampaignAuthority campaign(BayDefinition bay) {
        return Stage228CampaignAuthority.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED,
                List.of(new DeckProfile(bay.id(), 0.001d, 0.001d)));
    }

    private static SmallCraftId buildSupplyAndDeliverReady(
            Stage228CampaignAuthority authority,
            ProductionFixture production,
            String factionId,
            BayDefinition carrierBay,
            long tick) {
        SmallCraftId craftId = buildSupplyReadyAtStation(
                authority, production, factionId, tick);
        SmallCraftTurnaroundService turnaround = new SmallCraftTurnaroundService(
                authority.smallCraft(),
                authority.hangars(),
                production.engineering(),
                production.engineeringService());

        var relocated = production.logistics().transferReadyCraft(
                craftId, carrierBay, tick);
        assertTrue(relocated.assigned());
        assertEquals(OccupancyState.SERVICING,
                authority.hangars().find(craftId).orElseThrow().state());
        completeInspection(
                turnaround, craftId, carrierBay, production.yard().plannerCapability());
        assertEquals(OccupancyState.READY,
                authority.hangars().find(craftId).orElseThrow().state());
        return craftId;
    }

    private static SmallCraftId buildSupplyReadyAtStation(
            Stage228CampaignAuthority authority,
            ProductionFixture production,
            String factionId,
            long tick) {
        InstalledFit fit = InstalledFit.fromDemonstrator(
                production.engineering().findDemonstratorFit(DESIGN_ID));
        var plan = production.logistics().planBuild(
                factionId, DESIGN_ID, STATION_ID, fit, production.yard());
        loadBuildInputs(production, fit);
        var budget = production.yard().openInterval(
                plan.workPlan().requirements().totalWorkSeconds()
                        / production.yard().plannerCapability().workRate() + 5d);
        var built = production.logistics().settleBuild(
                authority.logistics(),
                plan,
                production.station().storage(),
                budget);
        assertTrue(built.settlement().settled());
        SmallCraftId craftId = built.producedCraftOptional().orElseThrow().id();
        authority.commitLogisticsState(built.logisticsState());

        BayDefinition stationBay = stationBay();
        var atStation = production.logistics().confirmDelivery(
                authority.logistics(), craftId, stationBay, tick);
        assertTrue(atStation.assigned());
        authority.commitLogisticsState(atStation.logisticsState());

        double water = production.station().storage().commodityMassKg(WATER_ID);
        if (water < 1_000d) {
            Stage228FinalCarrierEconomyAccess.addCommodity(
                    production.station().storage(), WATER_ID, 1_000d - water);
        }
        var loaded = production.supply().loadCommodityAtStation(
                craftId,
                production.station(),
                stationBay,
                Stage228SmallCraftShipConsumableCatalogLoader.REACTION_MASS_BINDING_ID,
                "core_drive",
                1_000d);
        assertEquals(SupplyStatus.LOADED, loaded.status());

        SmallCraftTurnaroundService turnaround = new SmallCraftTurnaroundService(
                authority.smallCraft(),
                authority.hangars(),
                production.engineering(),
                production.engineeringService());
        completeInspection(
                turnaround, craftId, stationBay, production.yard().plannerCapability());
        assertEquals(OccupancyState.READY,
                authority.hangars().find(craftId).orElseThrow().state());
        return craftId;
    }

    private static void completeInspection(
            SmallCraftTurnaroundService turnaround,
            SmallCraftId craftId,
            BayDefinition bay,
            ShipyardEngineeringService.ShipyardCapability yard) {
        ServiceProfile profile = new ServiceProfile(bay.id(), 1d, Map.of());
        var plan = turnaround.plan(
                craftId,
                bay,
                profile,
                new TurnaroundRequest(List.of(), false, false),
                yard);
        turnaround.complete(
                plan,
                new TurnaroundSettlement(
                        List.of(),
                        plan.requiredHandlingWorkSeconds(),
                        WorkSettlement.empty(),
                        WorkSettlement.empty()),
                bay);
    }

    private static MissionCommand mission(
            SmallCraftId craftId,
            OrderSource source,
            MissionType type,
            TargetKind targetKind,
            String referenceId) {
        return new MissionCommand(
                craftId, source, type, new MissionTarget(targetKind, referenceId));
    }

    private static MissionContext context(
            String factionId,
            long tick,
            DeploymentState deployment,
            String referenceId) {
        return new MissionContext(
                factionId,
                tick,
                deployment,
                0d,
                100_000d,
                true,
                0d,
                referenceId,
                new ObservationEvidence(
                        ObservationChannel.OWNED_ASSET_REPORT,
                        "report.m22_8n." + tick + "." + referenceId,
                        tick,
                        tick));
    }

    private static void advanceOneCampaignStep(
            Stage228CampaignAuthority authority,
            BayDefinition bay) {
        authority.advanceFrame(
                1.0f,
                tick -> Map.of(bay.id(), bay));
    }

    private static long authoritativeTick(Stage228CampaignAuthority authority) {
        return authority.coordinator().runtime().world().getAuthoritativeWorldTick();
    }

    private static Result survivorConsumesReactionMass(
            List<ImportedCombatantState> imported,
            long maximumTicks) {
        ArrayList<CombatantResult> rows = new ArrayList<>();
        for (ImportedCombatantState row : imported) {
            RuntimeState runtime = row.engineering().runtimeState;
            if (row.entityId() == 1L) {
                runtime = withReactionMass(
                        runtime,
                        runtime.consumables().reactionMassKg() - 100d);
            }
            rows.add(outcome(row, runtime, false));
        }
        return new Result(
                Math.min(5L, maximumTicks),
                Termination.ENCOUNTER_HORIZON,
                List.copyOf(rows));
    }

    private static Result destroySmallCraftParticipant(
            List<ImportedCombatantState> imported,
            long maximumTicks) {
        ArrayList<CombatantResult> rows = new ArrayList<>();
        for (ImportedCombatantState row : imported) {
            rows.add(outcome(row, row.engineering().runtimeState, row.entityId() == 1L));
        }
        return new Result(
                Math.min(5L, maximumTicks),
                Termination.ENCOUNTER_HORIZON,
                List.copyOf(rows));
    }

    private static CombatantResult outcome(
            ImportedCombatantState row,
            RuntimeState runtime,
            boolean destroyed) {
        return new CombatantResult(
                row.entityId(),
                row.side(),
                row.engineering().fit,
                runtime,
                row.engineering().instanceState,
                row.xM() + 5d,
                row.yM(),
                row.velocityXMps(),
                row.velocityYMps(),
                destroyed);
    }

    private static RuntimeState withReactionMass(
            RuntimeState source,
            double reactionMassKg) {
        List<ConsumableLoad> loads = source.consumables().interfaceLoads().stream()
                .map(load -> load.kind() == ShipEngineeringCatalog.InterfaceKind.REACTION_MASS
                        ? new ConsumableLoad(
                                load.mountId(),
                                load.interfaceId(),
                                load.kind(),
                                reactionMassKg,
                                reactionMassKg,
                                load.itemCount())
                        : load)
                .toList();
        ConsumableState consumables = new ConsumableState(
                source.consumables().cargoMassKg(),
                source.consumables().storesMassKg(),
                source.consumables().missionPayloadMassKg(),
                source.consumables().missionIntegrationVolumeM3(),
                loads);
        return new RuntimeState(
                consumables,
                source.sharedBusEnergyJ(),
                source.shipHeatStoredJ(),
                source.localHeatJByMount(),
                source.thrustLimitNByMount(),
                source.coolantBusCapacityW(),
                source.ftlCooldownSecondsByMount());
    }

    private static ExternalCombatant externalOpponent(
            SmallCraftState source,
            String referenceId) {
        SmallCraftState detached = new SmallCraftState(
                new SmallCraftId(999_000L),
                "faction.m22_8n.external",
                source.designId(),
                source.fit(),
                source.runtimeState(),
                source.instanceState());
        EngineeringComponent engineering =
                SmallCraftEngineeringMaterializationBridge.materialize(detached);
        return new ExternalCombatant(
                referenceId,
                Side.BETA,
                detached.stableFactionId(),
                engineering,
                new LocalFlightState(1_400d, 0d, 0d, 0d));
    }

    private static FleetForceRegistry carrierForces(ContentCatalog.FactionDefinition faction) {
        EntityState carrier = new EntityState(
                new EntityId(22_800L),
                null, null, null, null, null, null, null,
                new EntityState.FactionState(faction.runtimeId()),
                null, null, null, null, null, null, null, null, null);
        return new FleetForceRegistry(List.of(new FleetForceRegistry.Entry(
                CARRIER_FLEET,
                faction.runtimeId(),
                FleetLocationKind.IN_SYSTEM,
                SYSTEM,
                null,
                null,
                carrier,
                new FleetReadinessState(
                        10_000, 10_000, 10_000, 10_000, 10_000, 10_000, 10_000))));
    }

    private static BayDefinition stationBay() {
        return new BayDefinition(
                new BayId(STATION_ID, "bay.service"),
                HostKind.STATION,
                new Dimensions3d(120d, 120d, 50d),
                100_000_000d,
                100_000_000d,
                1d);
    }

    private static ProductionFixture productionFixture(
            Stage228CampaignAuthority authority) {
        ShipEngineeringCatalog engineering =
                Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        var protection = Stage22CorePairProtectionCatalogLoader.project(engineering);
        var industrial =
                Stage228SmallCraftShipyardIndustrialCatalogLoader.loadEmpireDefault();
        ShipyardEngineeringService engineeringService =
                new ShipyardEngineeringService(engineering, industrial);
        Stage18ResourceOntologyCatalog ontology =
                Stage18ResourceOntologyLoader.loadDefault();
        var weaponContent =
                Stage228SmallCraftWeaponRuntimeCatalogLoader.loadCombined();
        Stage18ManufacturingProductRegistry products =
                Stage18ManufacturingProductRegistry.loadDefault()
                        .withEngineeringCatalog(engineering, Provenance.STAGE22_AUTHORED)
                        .withAmmunitionCatalog(
                                weaponContent.ammunition(), Provenance.STAGE22_AUTHORED);
        Stage18ShipyardCatalog physicalShipyard =
                Stage228SmallCraftShipyardCatalogLoader.loadEmpireDefault();
        Stage18ShipyardRuntime shipyardRuntime =
                new Stage18ShipyardRuntime(physicalShipyard, ontology, products);

        StationArchetypeDefinition stationDefinition = new StationArchetypeDefinition(
                "station.infrastructure.m22_8n",
                "M22.8N final carrier acceptance station",
                List.of(
                        "facility.fabrication.heavy",
                        "facility.fabrication.electrical",
                        "facility.fabrication.precision",
                        "facility.fabrication.assembly"),
                Map.of(
                        "storage.dry_bulk", 200_000_000d,
                        "storage.liquid_tank", 5_000d,
                        "storage.general_container", 100_000_000d,
                        "storage.hazardous_controlled", 50_000_000d,
                        "storage.high_value_controlled", 100_000_000d,
                        "storage.oversized", 100_000_000d),
                Set.of(
                        "storage.dry_bulk",
                        "storage.liquid_tank",
                        "storage.general_container",
                        "storage.hazardous_controlled",
                        "storage.high_value_controlled",
                        "storage.oversized"),
                2_000_000d,
                20_000_000d,
                Set.of("location.orbital_station"));
        Stage18StationIndustrialNode station = Stage18StationIndustrialNode.instantiate(
                STATION_ID,
                "location.orbital_station",
                stationDefinition,
                ontology,
                products);

        Stage18FacilityRuntime facilities =
                new Stage18FacilityRuntime(Stage18FacilityCatalogLoader.loadDefault());
        List<Stage18FacilityRuntime.FacilityCapabilitySnapshot> support =
                new ArrayList<>();
        for (var reference : station.installedFacilities()) {
            support.add(facilities.project(
                    new Stage18FacilityRuntime.InstalledFacilityState(
                            reference.facilityInstanceId(),
                            reference.facilityDefinitionId(),
                            1d,
                            10_000_000_000d,
                            10_000_000_000d,
                            10_000d,
                            1_000d,
                            station.locationTag(),
                            true)));
        }
        var yard = shipyardRuntime.projectYard(
                new Stage18ShipyardRuntime.InstalledYardState(
                        "yard.instance.m22_8n",
                        "yard.empire_capital_service_v1",
                        1d,
                        3_500_000_000d,
                        22d,
                        1_200,
                        700,
                        true),
                station,
                support);
        assertTrue(yard.active());

        SmallCraftPhysicalLogisticsService logistics =
                new SmallCraftPhysicalLogisticsService(
                        authority.smallCraft(),
                        authority.hangars(),
                        engineering,
                        protection,
                        engineeringService,
                        shipyardRuntime,
                        (pending, destination, tick) -> new DeliveryReceipt(
                                pending.craftId(),
                                pending.sourceStationId(),
                                destination.id().hostStableId(),
                                "transport.m22_8n." + pending.craftId().value()
                                        + "." + tick,
                                tick,
                                true));
        SmallCraftStationSupplyService supply =
                new SmallCraftStationSupplyService(
                        authority.smallCraft(),
                        authority.hangars(),
                        engineering,
                        new Stage18ShipConsumableService(
                                Stage228SmallCraftShipConsumableCatalogLoader.loadDefault(),
                                engineering),
                        products,
                        weaponContent.launchers(),
                        weaponContent.ammunition());

        return new ProductionFixture(
                engineering,
                engineeringService,
                physicalShipyard,
                station,
                yard,
                logistics,
                supply);
    }

    private static void loadBuildInputs(
            ProductionFixture fixture,
            InstalledFit fit) {
        Map<String, Double> materials = new TreeMap<>();
        Stage18ShipyardCatalog.HullPhysicalProfile hull =
                fixture.physicalShipyard().findHullProfile(fit.hullId());
        hull.buildInputsKg().forEach(input ->
                materials.merge(input.commodityId(), input.massKg(), Double::sum));
        materials.forEach((commodity, mass) ->
                Stage228FinalCarrierEconomyAccess.addCommodity(
                        fixture.station().storage(), commodity, mass));

        Map<String, Integer> modules = new LinkedHashMap<>();
        for (InstalledModuleDefinition installed : fit.installedModules()) {
            modules.merge(installed.moduleId(), 1, Integer::sum);
        }
        modules.forEach((module, count) ->
                Stage228FinalCarrierEconomyAccess.addProduct(
                        fixture.station().storage(), module, count));
    }

    private record ProductionFixture(
            ShipEngineeringCatalog engineering,
            ShipyardEngineeringService engineeringService,
            Stage18ShipyardCatalog physicalShipyard,
            Stage18StationIndustrialNode station,
            Stage18ShipyardRuntime.YardCapabilitySnapshot yard,
            SmallCraftPhysicalLogisticsService logistics,
            SmallCraftStationSupplyService supply) { }
}
