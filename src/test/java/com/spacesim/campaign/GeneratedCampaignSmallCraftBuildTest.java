package com.spacesim.campaign;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.*;
import com.spacesim.economy.*;
import com.spacesim.economy.Stage18ShipyardRuntime.*;
import com.spacesim.persistence.*;
import com.spacesim.ship.ShipyardEngineeringService.ShipyardCapability;
import com.spacesim.ui.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignSmallCraftBuildTest {
    private static YardCapabilitySnapshot rated(Stage18ShipyardCatalog.YardDefinition d) {
        return new YardCapabilitySnapshot("design.only", d.id(), YardStatus.ACTIVE,
                new ShipyardCapability("design.only", d.berthDimensionsM(), d.maxServiceMassKg(), d.stage175FabricationCapabilities(),
                        d.stage175HandledRequirementIds(), d.toolingTags(), d.precisionCapability(), d.ratedEngineeringWorkRate(),
                        d.laborCapacity(), d.automationCapacity(), d.ratedIntegrationPowerW()), d.handledStorageClassIds(), d.maxHandledUnitMassKg());
    }
    static Stage228CampaignAuthority fixture() { return fixture(.999999); }
    static Stage228CampaignAuthority fixture(double integrity) {
        var c = FoundedCampaignFixture.restore(); var world = c.coordinator().runtime().world();
        var f = world.findFleet(c.playerState().orElseThrow().activeFleetId()).orElseThrow();
        var ship = world.findSession(f.systemId()).orElseThrow().getEntityRegistry().require(f.localEntityId()).getComponent(EngineeringComponent.class);
        var definition = Stage23SmallCraftProductionCatalogs.shipyards().getYards().stream()
                .filter(d -> ShipyardRepairWorkQueue.plan(f.localEntityId(), ship, rated(d)).feasibility().feasible())
                .filter(d -> Stage23SmallCraftProductionCatalogs.designIds().stream()
                        .anyMatch(id -> ShipyardSmallCraftBuildWorkQueue.plan(id, rated(d)).feasibility().feasible()))
                .findFirst().orElseThrow();
        return GeneratedCampaignPersonalRepairTest.fixture("utility_sensor", integrity, c, false, definition.id());
    }
    static String station(Stage228CampaignAuthority c) {
        return c.coordinator().runtime().industry().industrial().stations().stream().filter(s -> c.ownsProductionStation(s.stationId()))
                .findFirst().orElseThrow().stationId();
    }
    static String design(Stage228CampaignAuthority c) {
        var station = c.coordinator().runtime().industry().industrial().station(station(c));
        return Stage23SmallCraftProductionCatalogs.designIds().stream().filter(id -> station.yardCapabilities().stream()
                .anyMatch(y -> y.active() && ShipyardSmallCraftBuildWorkQueue.plan(id, y).feasibility().feasible())).findFirst().orElseThrow();
    }
    static void supply(Stage228CampaignAuthority c, String design) {
        // Explicit physical material/module fixture, not ordinary acquisition evidence.
        var store = c.coordinator().runtime().infrastructure().endpoint(station(c)).storage();
        var fit = Stage23SmallCraftProductionCatalogs.fit(design);
        var raw = new TreeMap<String, Double>(); var stock = new TreeMap<String, Integer>(); var capacities = new TreeMap<String, Double>();
        var ontology = Stage18ResourceOntologyLoader.loadDefault(); var products = Stage23SmallCraftProductionCatalogs.products();
        Stage23SmallCraftProductionCatalogs.shipyards().findHullProfile(fit.hullId()).buildInputsKg()
                .forEach(i -> raw.merge(i.commodityId(), i.massKg(), Double::sum));
        fit.installedModules().forEach(m -> stock.merge(m.moduleId(), 1, Math::addExact));
        raw.forEach((id, kg) -> capacities.merge(ontology.findCommodity(id).storageClassId(), kg, Double::sum));
        stock.forEach((id, n) -> capacities.merge(products.findProduct(id).storageClassId(), products.findProduct(id).unitMassKg() * n, Double::sum));
        var source = new Stage18StationStorage(ontology, products, "fixture.build-bill", capacities, raw, stock);
        var handler = new Stage18LogisticsRuntime.HandlingCapability("fixture.build-bill", capacities.keySet(), 1e9, 1e9);
        var budget = handler.openInterval(1); var transfer = new Stage18LogisticsRuntime(ontology, products);
        raw.forEach((id, kg) -> assertTrue(transfer.transferCommodity(source, store, id, kg, handler, budget).transferred()));
        stock.forEach((id, n) -> assertTrue(transfer.transferProduct(source, store, id, n, handler, budget).transferred()));
    }
    static void step(Stage228CampaignAuthority c) {
        c.coordinator().setPaused(false); c.advanceFrame(c.coordinator().session().fixedStepSeconds()); c.coordinator().setPaused(true);
    }
    static Stage228CampaignAuthority roundtrip(Stage228CampaignAuthority c) {
        var state = c.captureState();
        var next = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(Stage228GeneratedCampaignPersistenceCodec.encode(state)));
        assertEquals(state, next.captureState()); return next;
    }
    @Test void actualCommandsReserveWholeBillPauseReloadAndCancelWithoutEarlyIdentity() {
        var c = fixture(); String station = station(c), design = design(c); var before = c.captureState();
        assertFalse(c.previewPilotAction("START_SMALL_CRAFT_BUILD", station, design, 1).allowed()); assertEquals(before, c.captureState());
        supply(c, design); var stock = c.coordinator().runtime().infrastructure().endpoint(station).storage().snapshot();
        long allocator = c.smallCraft().nextIdValue(); var original = c.captureState();
        assertFalse(c.previewPilotAction("START_SMALL_CRAFT_BUILD", station, "fit.missing", 1).allowed());
        var preview = c.previewPilotAction("START_SMALL_CRAFT_BUILD", station, design, 1); assertTrue(preview.allowed()); assertEquals(original, c.captureState());
        c.submitPilotAction(preview); assertEquals(allocator, c.smallCraft().nextIdValue()); assertEquals(0, c.smallCraft().size());
        var pending = c.captureState(); c.advanceFrame(c.coordinator().session().fixedStepSeconds()); assertEquals(pending, c.captureState());
        assertTrue(GeneratedCampaignSmallCraftBuildUi.rows(c).stream().anyMatch(r -> r.selection().stableId().startsWith("pilot-craft-build-cancel|")));
        var cancelled = roundtrip(c); var id = cancelled.smallCraftBuilds().orders().get(0).orderId();
        cancelled.submitPilotAction(cancelled.previewPilotAction("CANCEL_SMALL_CRAFT_BUILD", station, id, 1));
        assertEquals(stock, cancelled.coordinator().runtime().infrastructure().endpoint(station).storage().snapshot());
        assertTrue(cancelled.smallCraftBuilds().orders().isEmpty());
        step(c); c = roundtrip(c); var partial = c.smallCraftBuilds().orders().get(0);
        assertTrue(partial.completedWorkSeconds() > 0); assertFalse(c.previewPilotAction("CANCEL_SMALL_CRAFT_BUILD", station, partial.orderId(), 1).allowed());
        assertFalse(c.previewPilotAction("CLAIM_SMALL_CRAFT_BUILD", station, partial.orderId(), 1).allowed());
        assertEquals(allocator, c.smallCraft().nextIdValue());
        // A government/world recomposition must retain the same new sidecar rather than using the old overload.
        var state = c.captureState();
        assertEquals(state.smallCraftBuilds(), GeneratedCampaignPlayerWorldTransition.composeRuntime(state,
                c.coordinator().runtime().captureState(), state.playerState()).smallCraftBuilds());
    }
    @Test void nativeV28MigrationIsEmptyAndOldFramingCannotSmuggleNewProducts() throws Exception {
        var c = fixture(); var original = c.captureState(); byte[] historical = asV28(original);
        var migrated = Stage228GeneratedCampaignPersistenceCodec.decode(historical); assertEquals(original, migrated);
        assertEquals(ShipyardSmallCraftBuildQueueState.empty(), migrated.smallCraftBuilds());
        supply(c, design(c)); var stocked = c.captureState();
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistenceCodec.decode(asV28(stocked)));
        byte[] legacyContainer = Stage21IGeneratedWorldRuntimePersistenceCodec.encode(stocked.stage21Runtime());
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistenceCodec.decodeOrMigrate(legacyContainer));
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistentState.adoptStage21(stocked.stage21Runtime()));
        assertThrows(IllegalArgumentException.class, () -> Stage228CampaignAuthority.restoreStage21(stocked.stage21Runtime()));
        var preview = c.previewPilotAction("START_SMALL_CRAFT_BUILD", station(c), design(c), 1); assertTrue(preview.allowed()); c.submitPilotAction(preview);
        assertThrows(AssertionError.class, () -> asV28(c.captureState()));
        byte[] relabelled = Stage228GeneratedCampaignPersistenceCodec.encode(c.captureState());
        java.nio.ByteBuffer.wrap(relabelled).putInt(4, 28).putInt(8, 28);
        System.arraycopy("m22.8.generated-campaign.v28".getBytes(java.nio.charset.StandardCharsets.UTF_8), 0, relabelled, 14, 26);
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistenceCodec.decode(relabelled));
    }
    @Test void actualSharedTicksCompleteOneEmptyOwnedCraftWithNoEarlyAllocation() {
        var c = fixture(); String station = station(c), design = design(c); supply(c, design);
        c.submitPilotAction(c.previewPilotAction("START_REPAIR", station, "", 1));
        var repair = c.repairQueue().orders().get(0);
        c.submitPilotAction(c.previewPilotAction("START_SMALL_CRAFT_BUILD", station, design, 1));
        var build = c.smallCraftBuilds().orders().get(0);
        assertEquals(repair.yardInstanceId(), build.yardInstanceId());
        var yard = c.coordinator().runtime().industry().industrial().station(station).yardCapabilities().stream()
                .filter(y -> y.yardInstanceId().equals(build.yardInstanceId())).findFirst().orElseThrow();
        double offered = yard.plannerCapability().workRate() * c.coordinator().session().fixedStepSeconds();
        long id = c.smallCraft().nextIdValue(); step(c);
        assertEquals(Math.max(0, offered - Math.min(offered, repair.requiredWorkSeconds() - repair.completedWorkSeconds())),
                c.smallCraftBuilds().orders().get(0).completedWorkSeconds(), 1e-8);
        long bound = 2 + (long) Math.ceil((build.requiredWorkSeconds() + repair.requiredWorkSeconds()) / offered);
        System.out.println("SMALL_CRAFT_BUILD actual shared interval bound=" + bound + " requiredWork=" + build.requiredWorkSeconds());
        assertTrue(bound < 2_000_000, "Explicit fixture must have a bounded physical scenario");
        for (long n = 0; n < bound && !c.smallCraftBuilds().orders().get(0).ready(); n++) {
            step(c);
            if (n % Math.max(1, bound / 4) == 0) System.out.println("SMALL_CRAFT_BUILD actual ticks=" + n
                    + " paidWork=" + c.smallCraftBuilds().orders().get(0).completedWorkSeconds());
            if (n == bound / 2) c = roundtrip(c);
        }
        assertTrue(c.smallCraftBuilds().orders().get(0).ready()); assertEquals(id, c.smallCraft().nextIdValue());
        System.out.println("SMALL_CRAFT_BUILD completed at worldTick=" + c.coordinator().runtime().world().getAuthoritativeWorldTick()
                + " requiredWork=" + c.smallCraftBuilds().orders().get(0).requiredWorkSeconds());
        c = roundtrip(c); long wallet = c.playerState().orElseThrow().walletMilliCredits();
        var ready = c.smallCraftBuilds().orders().get(0); var before = c.captureState();
        for (var faction : List.of("faction.unknown", "faction.alpha", "faction.beta")) if (!faction.equals(ready.builderFactionId())) {
            var forged = new ShipyardSmallCraftBuildQueueState.Order(ready.orderId(), ready.ownerActorId(), faction, ready.stationId(),
                    ready.yardInstanceId(), ready.yardDefinitionId(), ready.designId(), ready.startedAtTick(), ready.requiredWorkSeconds(),
                    ready.completedWorkSeconds(), ready.reservedCommodityMassKg(), ready.reservedProductCounts());
            assertThrows(IllegalArgumentException.class, () -> withBuilds(before,
                    new ShipyardSmallCraftBuildQueueState(before.smallCraftBuilds().lastProcessedTick(), List.of(forged))));
        }
        var claim = c.previewPilotAction("CLAIM_SMALL_CRAFT_BUILD", station, ready.orderId(), 1);
        assertTrue(claim.allowed()); assertEquals(before, c.captureState()); c.submitPilotAction(claim);
        assertTrue(c.smallCraftBuilds().orders().isEmpty()); assertEquals(id + 1, c.smallCraft().nextIdValue());
        assertEquals(wallet, c.playerState().orElseThrow().walletMilliCredits());
        var craft = c.smallCraft().snapshot().get(0); assertEquals(0, craft.runtimeState().consumables().reactionMassKg());
        assertTrue(c.hangars().find(craft.id()).isEmpty()); assertTrue(c.captureState().personalCraftOwnership().contains(craft.id()));
        assertEquals(1, c.logistics().pendingDeliveries().size());
        assertFalse(c.previewPilotAction("CLAIM_SMALL_CRAFT_BUILD", station, ready.orderId(), 1).allowed());
        roundtrip(c);
    }
    static Stage228GeneratedCampaignPersistentState withBuilds(Stage228GeneratedCampaignPersistentState s, ShipyardSmallCraftBuildQueueState builds) {
        return new Stage228GeneratedCampaignPersistentState(s.schemaVersion(), s.runtimeVersion(), s.stage21Runtime(), s.smallCraft(), s.hangars(),
                s.flightDeck(), s.operations(), s.playerState(), s.playerJournal(), s.moduleCustody(), s.repairQueue(), s.refitQueue(),
                s.moduleTransfers(), s.productTransfers(), s.yardConstruction(), s.timeSkip(), s.crewManifest(), s.localFlights(),
                s.personalCraftOwnership(), s.industrialAdmissions(), builds);
    }
    @Test void availableYardResourcesConserveRealSizedTotalsRequireSupportsAndReloadExactly() {
        var c = fixture(); String station = station(c), target = "fixture.personal.repair.yard";
        var state = c.captureState(); var runtime = c.coordinator().runtime().captureState();
        var old = runtime.campaign().industrialState();
        String donor = old.yards().stream().filter(y -> y.stationId().equals(station)
                && !y.state().yardInstanceId().equals(target)).findFirst().orElseThrow().state().yardInstanceId();
        var yards = old.yards().stream().map(y -> {
            if (!y.stationId().equals(station)) return y;
            var s = y.state(); boolean supplies = s.yardInstanceId().equals(donor);
            return new Stage18IndustrialState.YardInstallationSnapshot(station, new InstalledYardState(
                    s.yardInstanceId(), s.yardDefinitionId(), s.conditionFraction(), supplies ? 1.2e9 : 0,
                    supplies ? 12 : 0, supplies ? 500 : 0, supplies ? 500 : 0, supplies));
        }).toList();
        java.util.function.Function<Boolean, Stage228CampaignAuthority> prepare = disableSupports -> {
            var facilities = old.facilities().stream().map(f -> {
                if (!disableSupports || !f.stationId().equals(station)) return f;
                var s = f.state();
                return new Stage18IndustrialState.FacilityInstallationSnapshot(f.stationId(),
                        new Stage18FacilityRuntime.InstalledFacilityState(s.facilityInstanceId(), s.definitionId(), s.conditionFraction(),
                                s.allocatedProcessPowerW(), s.availableHeatRejectionW(), s.availableLaborUnits(),
                                s.availableMaintenanceWorkRate(), s.locationTag(), false));
            }).toList();
            var industry = new Stage18IndustrialState(old.schemaVersion(), old.contentFingerprint(), old.simulationTick(), old.sources(),
                    old.stationStorages(), facilities, yards, old.constructionOrders(), old.processOrders());
            var original = runtime.campaign();
            var campaign = new Stage20GeneratedCampaignPersistentState(original.schemaVersion(), original.generationIdentity(), original.materializedWorld(),
                    original.materializationState(), industry, original.discoveryState(), original.openRuntimeBoundaries());
            var next = new Stage20GeneratedWorldRuntimePersistentState(runtime.schemaVersion(), runtime.bridgeVersion(), campaign, runtime.worldState(),
                    runtime.activeSystemId(), runtime.strategicStepTicks(), runtime.remoteUpdateBudgetPerFrame(), runtime.freight(), runtime.localFleetPhysicalStates());
            return Stage228CampaignAuthority.restore(GeneratedCampaignPlayerWorldTransition.composeRuntime(state, next, state.playerState()));
        };
        var unsupported = prepare.apply(true); var untouched = unsupported.captureState();
        assertFalse(unsupported.previewPilotAction("ALLOCATE_YARD_RESOURCES_AVAILABLE", station, target, 1).allowed());
        assertEquals(untouched, unsupported.captureState());
        c = prepare.apply(false); var before = c.captureState();
        assertFalse(c.previewPilotAction("ALLOCATE_YARD_RESOURCES", station, target, 1).allowed());
        var allocation = c.previewPilotAction("ALLOCATE_YARD_RESOURCES_AVAILABLE", station, target, 1);
        assertTrue(allocation.allowed()); assertEquals(before, c.captureState()); c.submitPilotAction(allocation);
        var actual = c.coordinator().runtime().industry().industrial().station(station);
        assertEquals(1.2e9, actual.yards().stream().mapToDouble(InstalledYardState::allocatedIntegrationPowerW).sum(), 1e-6);
        assertEquals(12, actual.yards().stream().mapToDouble(InstalledYardState::availableIntegrationWorkRate).sum(), 1e-12);
        assertEquals(500, actual.yards().stream().mapToInt(InstalledYardState::availableLaborCapacity).sum());
        assertEquals(500, actual.yards().stream().mapToInt(InstalledYardState::availableAutomationCapacity).sum());
        var capability = actual.yardCapabilities().stream().filter(y -> y.yardInstanceId().equals(target)).findFirst().orElseThrow();
        assertTrue(capability.active());
        assertTrue(Stage23SmallCraftProductionCatalogs.designIds().stream()
                .anyMatch(id -> ShipyardSmallCraftBuildWorkQueue.plan(id, capability).feasibility().feasible()));
        assertEquals(before.smallCraft(), c.captureState().smallCraft());
        assertEquals(state.playerState().walletMilliCredits(), c.playerState().orElseThrow().walletMilliCredits());
        roundtrip(c);
    }
    @Test void combinedRepairAndBuildEscrowCannotOverflowOneCanonicalStoreOnImport() throws Exception {
        var c = fixture(.5); String station = station(c), design = design(c); supply(c, design);
        c.submitPilotAction(c.previewPilotAction("START_REPAIR", station, "", 1));
        c.submitPilotAction(c.previewPilotAction("START_SMALL_CRAFT_BUILD", station, design, 1));
        var before = c.captureState(); var runtime = c.coordinator().runtime().captureState(); var old = runtime.campaign().industrialState();
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var build = before.smallCraftBuilds().orders().get(0); var repair = before.repairQueue().orders().get(0);
        var buildByClass = new TreeMap<String, Double>(); var repairByClass = new TreeMap<String, Double>();
        build.reservedCommodityMassKg().forEach((id, kg) -> buildByClass.merge(ontology.findCommodity(id).storageClassId(), kg, Double::sum));
        build.reservedProductCounts().forEach((id, n) -> {
            var product = Stage23SmallCraftProductionCatalogs.products().findProduct(id);
            buildByClass.merge(product.storageClassId(), n * product.unitMassKg(), Double::sum);
        });
        repair.reservedCommodityMassByIdKg().forEach((id, kg) -> repairByClass.merge(ontology.findCommodity(id).storageClassId(), kg, Double::sum));
        var storageClass = buildByClass.keySet().stream().filter(id -> repairByClass.getOrDefault(id, 0d) > 1e-6).findFirst().orElseThrow();
        double buildMass = buildByClass.get(storageClass), repairMass = repairByClass.get(storageClass);
        var original = old.stationStorages().stream().filter(s -> s.stationId().equals(station)).findFirst().orElseThrow();
        var raw = new TreeMap<>(original.commodityMassByIdKg());
        double ordinary = Stage18StationStorage.restore(ontology, Stage23SmallCraftProductionCatalogs.products(), original).usedCapacityKg(storageClass);
        String fillCommodity = ontology.getCommodities().stream().filter(item -> item.storageClassId().equals(storageClass)
                && item.quantityUnit() == Stage18ResourceOntologyCatalog.QuantityUnit.KILOGRAM).findFirst().orElseThrow().id();
        raw.merge(fillCommodity, original.capacityByStorageClassKg().get(storageClass) - Math.max(buildMass, repairMass) - ordinary, Double::sum);
        var filled = new Stage18StationStorage.StationStorageSnapshot(station, original.capacityByStorageClassKg(), raw, original.productCountById());
        var industry = new Stage18IndustrialState(old.schemaVersion(), old.contentFingerprint(), old.simulationTick(), old.sources(),
                old.stationStorages().stream().map(s -> s.stationId().equals(station) ? filled : s).toList(), old.facilities(), old.yards(), old.constructionOrders(), old.processOrders());
        var campaign = runtime.campaign();
        var nextCampaign = new Stage20GeneratedCampaignPersistentState(campaign.schemaVersion(), campaign.generationIdentity(), campaign.materializedWorld(),
                campaign.materializationState(), industry, campaign.discoveryState(), campaign.openRuntimeBoundaries());
        var nextRuntime = new Stage20GeneratedWorldRuntimePersistentState(runtime.schemaVersion(), runtime.bridgeVersion(), nextCampaign, runtime.worldState(),
                runtime.activeSystemId(), runtime.strategicStepTicks(), runtime.remoteUpdateBudgetPerFrame(), runtime.freight(), runtime.localFleetPhysicalStates());
        var noBuild = withBuilds(before, ShipyardSmallCraftBuildQueueState.empty());
        var individuallyFits = GeneratedCampaignPlayerWorldTransition.composeRuntime(noBuild, nextRuntime, before.playerState());
        assertThrows(IllegalArgumentException.class, () -> withBuilds(individuallyFits, before.smallCraftBuilds()));
        // Same current-version framing with only the build section replaced: the empty build decodes,
        // the combined escrow does not, so the rejection is about the overflow and nothing else.
        var control = withReplacedBuilds(individuallyFits, ShipyardSmallCraftBuildQueueState.empty());
        assertEquals(individuallyFits, Stage228GeneratedCampaignPersistenceCodec.decode(control));
        var forged = withReplacedBuilds(individuallyFits, before.smallCraftBuilds());
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistenceCodec.decode(forged));
        assertEquals(before, c.captureState());
    }
    private static byte[] withReplacedBuilds(Stage228GeneratedCampaignPersistentState state, ShipyardSmallCraftBuildQueueState builds)
            throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var in = new DataInputStream(new ByteArrayInputStream(Stage228GeneratedCampaignPersistenceCodec.encode(state)));
                var out = new DataOutputStream(bytes)) {
            out.writeInt(in.readInt()); out.writeInt(in.readInt()); out.writeInt(in.readInt()); out.writeUTF(in.readUTF());
            for (int i = 0; i < 19; i++) { int length = in.readInt(); out.writeInt(length); out.write(in.readNBytes(length)); }
            HistoricalCampaignContentFixture.skipEmptyBuild(in);
            int observationLength = in.readInt(); byte[] observations = in.readNBytes(observationLength);
            int termsLength = in.readInt(); byte[] terms = in.readNBytes(termsLength); assertEquals(-1, in.read());
            byte[] payload = ShipyardSmallCraftBuildQueuePersistenceCodec.encode(builds); out.writeInt(payload.length); out.write(payload);
            out.writeInt(observationLength); out.write(observations);
            out.writeInt(termsLength); out.write(terms);
        }
        return bytes.toByteArray();
    }
    static byte[] asV28(Stage228GeneratedCampaignPersistentState state) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var in = new DataInputStream(new ByteArrayInputStream(Stage228GeneratedCampaignPersistenceCodec.encode(state)));
                var out = new DataOutputStream(bytes)) {
            out.writeInt(in.readInt()); in.readInt(); in.readInt(); in.readUTF(); out.writeInt(28); out.writeInt(28); out.writeUTF("m22.8.generated-campaign.v28");
            for (int i = 0; i < 19; i++) { int length = in.readInt(); byte[] payload = in.readNBytes(length); assertEquals(length, payload.length); out.writeInt(length); out.write(payload); }
            HistoricalCampaignContentFixture.skipEmptyBuild(in); HistoricalCampaignContentFixture.skipEmptyObservations(in); assertEquals(-1, in.read());
        }
        return bytes.toByteArray();
    }
}
