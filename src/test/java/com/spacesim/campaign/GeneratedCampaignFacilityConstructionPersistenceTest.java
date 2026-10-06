package com.spacesim.campaign;

import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18FacilityConstructionCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.economy.Stage18FacilityConstructionRuntime;
import com.spacesim.economy.Stage18FacilityConstructionWorkQueue;
import com.spacesim.economy.Stage18LogisticsRuntime;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistenceCodec;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistentState;
import com.spacesim.player.OwnedStationRef;
import com.spacesim.player.PlayerState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("slow")
class GeneratedCampaignFacilityConstructionPersistenceTest {
    @Test void nativeCheckpointPreservesConstructionCustodyAndRequiresItsExistingStationOwner() {
        var campaign = FoundedCampaignFixture.restore();
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var products = Stage22CivilianMiningProductionPath.loadProducts();
        var construction = new Stage18FacilityConstructionRuntime(Stage18FacilityConstructionCatalogLoader.loadDefault(),
                Stage18FacilityCatalogLoader.loadDefault(), ontology);
        String id = Stage18FacilityConstructionWorkQueue.PREFIX + "checkpoint-test";
        String definition = "facility.processing.recycling";
        String location = "location.orbital_station";
        var template = construction.createOrder(id, "player-facility-test", definition, "template", location);
        var byStorageClass = new TreeMap<String, Double>();
        template.requiredMassByCommodityKg().forEach((commodity, kg) ->
                byStorageClass.merge(ontology.findCommodity(commodity).storageClassId(), kg, Double::sum));
        var initialCampaign = campaign;
        String module = com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID;
        var manufacturing = Stage22CivilianMiningProductionPath.loadManufacturing();
        var manufacturingProfile = manufacturing.findProductProfile(manufacturing.findProductBinding(module).profileId());
        double moduleMass = products.findProduct(module).unitMassKg();
        assertTrue(com.spacesim.ui.GeneratedCampaignConstructionUi.rows(campaign).isEmpty());
        var station = campaign.coordinator().runtime().industry().industrial().stations().stream()
                .filter(s -> initialCampaign.hasFacilityConstructionLine(s.stationId(), definition))
                .filter(s -> initialCampaign.hasManufacturingLine(s.stationId(), module))
                .filter(s -> initialCampaign.hasYardConstructionLine(s.stationId(), "yard.orbital_escort_v1"))
                .filter(s -> byStorageClass.entrySet().stream().allMatch(e -> s.storage().remainingCapacityKg(e.getKey()) >= e.getValue()))
                .findFirst().orElseThrow();
        var baseline = campaign.captureState();
        assertFalse(campaign.previewPilotAction("START_FACILITY_CONSTRUCTION", station.stationId(), definition, 1).allowed());
        assertEquals(baseline, campaign.captureState(), "A foreign-station preview cannot reserve materials");
        var ref = campaign.pilotMarketReference(station.stationId()).orElseThrow();
        var original = baseline.playerState();
        var discovered = new ArrayList<>(original.discoveredSystemIds());
        if (!discovered.contains(ref.systemId())) discovered.add(ref.systemId());
        // Explicit ownership fixture checks composition only, never ordinary station acquisition.
        var owner = new PlayerState(original.walletMilliCredits(), original.factionContentId(), original.reputations(),
                original.ownedFleetIds(), original.activeFleetId(), discovered, original.discoveredObjects(),
                original.homeSystemId(), original.dockedAt(), original.fleetOrders(), original.threatIntel(),
                original.ownedConstructionProjectIds(), List.of(new OwnedStationRef(ref.systemId(), ref.entityId())));
        campaign = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistentState.compose(
                baseline.stage21Runtime(), baseline.smallCraft(), baseline.hangars(), baseline.flightDeck(), baseline.operations(), owner));
        var runtime = campaign.coordinator().runtime();
        var storage = runtime.infrastructure().endpoint(station.stationId()).storage();
        // Finite input fixture proves reserve/save; it is not a player delivery journey.
        var supply = new Stage18StationStorage(ontology, products, "fixture.construction-supply", byStorageClass,
                template.requiredMassByCommodityKg(), Map.of());
        var handling = new Stage18LogisticsRuntime.HandlingCapability("fixture.handling", byStorageClass.keySet(),
                template.installedMassKg(), template.installedMassKg());
        var logistics = new Stage18LogisticsRuntime(ontology, products);
        template.requiredMassByCommodityKg().forEach((commodity, kg) ->
                assertTrue(logistics.transferCommodity(supply, storage, commodity, kg,
                        handling, handling.openInterval(1)).transferred()));
        var originalContents = storage.snapshot();
        runtime.constructionQueue().start(id, "player-facility-test", definition, location, storage,
                runtime.world().getAuthoritativeWorldTick());
        assertTrue(supply.snapshotCommodityMassByIdKg().isEmpty());
        var saved = campaign.captureState();
        var rows = com.spacesim.ui.GeneratedCampaignConstructionUi.rows(campaign);
        assertTrue(rows.stream().anyMatch(r -> r.selection().stableId().equals("pilot-construction-cancel|" + station.stationId() + '|' + id)));
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistentState.compose(
                saved.stage21Runtime(), saved.smallCraft(), saved.hangars(), saved.flightDeck(), saved.operations(), original));
        assertThrows(IllegalArgumentException.class, () -> Stage228GeneratedCampaignPersistentState.compose(
                saved.stage21Runtime(), saved.smallCraft(), saved.hangars(), saved.flightDeck(), saved.operations(), null));
        var restored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(saved)));
        assertEquals(saved, restored.captureState());
        var restoredRuntime = restored.coordinator().runtime();
        var restoredStorage = restoredRuntime.infrastructure().endpoint(station.stationId()).storage();
        for (String storageClass : byStorageClass.keySet())
            assertEquals(storage.usedCapacityKg(storageClass), restoredStorage.usedCapacityKg(storageClass), 1e-6);
        assertEquals(runtime.constructionQueue().capture(), restoredRuntime.constructionQueue().capture());
        restored.advanceFrame(0);
        assertEquals(saved, restored.captureState());
        restoredRuntime.constructionQueue().cancel(id, restoredStorage);
        assertEquals(originalContents, restoredStorage.snapshot());
        assertTrue(restoredRuntime.constructionQueue().capture().isEmpty());
        assertEquals(1, runtime.constructionQueue().capture().size(), "Restored cancellation cannot alter the original owner");

        var working = Stage228CampaignAuthority.restore(saved);
        working.coordinator().setPaused(false);
        working.advanceFrame(working.coordinator().session().fixedStepSeconds());
        var partialOrder = working.coordinator().runtime().constructionQueue().capture().get(0);
        assertTrue(partialOrder.completedWorkSeconds() > 0d && partialOrder.remainingWorkSeconds() > 0d,
                "An actual installed line performs bounded partial work on a world tick");
        var partial = working.captureState();
        assertTrue(com.spacesim.ui.GeneratedCampaignConstructionUi.rows(working).stream()
                .anyMatch(r -> r.selection().stableId().startsWith("personal-construction|")));
        var continuing = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(partial)));
        working.advanceFrame(working.coordinator().session().fixedStepSeconds());
        continuing.advanceFrame(continuing.coordinator().session().fixedStepSeconds());
        assertEquals(working.captureState(), continuing.captureState(), "Actual partial construction resumes identically");

        var simultaneous = Stage228CampaignAuthority.restore(saved);
        var simultaneousRuntime = simultaneous.coordinator().runtime();
        var simultaneousStorage = simultaneousRuntime.infrastructure().endpoint(station.stationId()).storage();
        var manufacturingInputs = new TreeMap<String, Double>();
        var inputCapacities = new TreeMap<String, Double>();
        manufacturingProfile.inputs().forEach(input -> {
            double kg = moduleMass * input.fractionOfOutputMass();
            manufacturingInputs.put(input.commodityId(), kg);
            inputCapacities.merge(ontology.findCommodity(input.commodityId()).storageClassId(), kg, Double::sum);
        });
        var moduleSupply = new Stage18StationStorage(ontology, products, "fixture.manufacturing-inputs",
                inputCapacities, manufacturingInputs, Map.of());
        var moduleHandling = new Stage18LogisticsRuntime.HandlingCapability("fixture.module-handling",
                inputCapacities.keySet(), moduleMass, moduleMass);
        manufacturingInputs.forEach((commodity, kg) -> assertTrue(logistics.transferCommodity(moduleSupply,
                simultaneousStorage, commodity, kg, moduleHandling, moduleHandling.openInterval(1)).transferred()));
        var sharedLine = simultaneousRuntime.industry().industrial().station(station.stationId()).facilityCapabilities().stream()
                .filter(c -> c.capabilityTags().containsAll(manufacturingProfile.requiredCapabilityTags())
                        && c.effectiveProcessPowerW() > 0 && c.effectiveEngineeringWorkRate() > 0 && c.effectiveMaintenanceWorkRate() > 0)
                .sorted(java.util.Comparator.comparing(com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot::facilityInstanceId))
                .findFirst().orElseThrow();
        simultaneousRuntime.manufacturingQueue().start(com.spacesim.economy.Stage18ManufacturingWorkQueue.PREFIX + "shared-work-test",
                module, 1, simultaneousStorage, new com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingCapability(
                        sharedLine.facilityInstanceId(), sharedLine.capabilityTags(), sharedLine.effectiveProcessPowerW(),
                        sharedLine.effectiveEngineeringWorkRate(), sharedLine.effectiveMaintenanceWorkRate()),
                simultaneousRuntime.world().getAuthoritativeWorldTick());
        simultaneous.coordinator().setPaused(false);
        simultaneous.advanceFrame(simultaneous.coordinator().session().fixedStepSeconds());
        double productionWork = simultaneousRuntime.manufacturingQueue().capture().get(0).completedFraction()
                * moduleMass * manufacturingProfile.workSecondsPerOutputKg();
        double constructionWork = simultaneousRuntime.constructionQueue().capture().get(0).completedWorkSeconds();
        assertTrue(productionWork > 0d);
        assertTrue(productionWork + constructionWork <= sharedLine.effectiveEngineeringWorkRate()
                * simultaneous.coordinator().session().fixedStepSeconds() + 1e-6,
                "One installed assembly line cannot supply its engineering work twice in a tick");

        // Explicit finite work fixture tests installation, not an ordinary player engineering source.
        float step = campaign.coordinator().session().fixedStepSeconds();
        var budget = new Stage18FacilityConstructionRuntime.ConstructionCapability("fixture.construction-work",
                java.util.Set.of("capability.fabrication.heavy", "capability.fabrication.assembly"),
                template.requiredWorkSeconds() / step).openInterval(step);
        var completed = runtime.constructionQueue().advance(runtime.world().getAuthoritativeWorldTick() + 1,
                stationId -> runtime.infrastructure().endpoint(stationId).storage(), order -> budget);
        assertEquals(1, completed.size());
        assertThrows(IllegalStateException.class, () -> runtime.adoptCompletedFacilityConstruction(completed),
                "An installation cannot arrive before its actual world tick");
        campaign.advanceFrame(step);
        assertThrows(IllegalArgumentException.class, campaign::captureState,
                "Completed work cannot be saved without its physical installation");
        runtime.adoptCompletedFacilityConstruction(completed);
        var installed = runtime.industry().industrial().station(station.stationId()).facilities().stream()
                .filter(f -> f.facilityInstanceId().equals("player-facility-test")).findFirst().orElseThrow();
        assertFalse(installed.enabled());
        assertEquals(0d, installed.allocatedProcessPowerW());
        var finished = campaign.captureState();
        runtime.adoptCompletedFacilityConstruction(completed);
        assertEquals(finished, campaign.captureState(), "Replaying adoption must preserve the installed state");
        var finishedRestored = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(finished)));
        assertEquals(finished, finishedRestored.captureState());
        assertTrue(finishedRestored.coordinator().runtime().industry().industrial().station(station.stationId())
                .facilities().contains(installed));
        var beforeAllocation = runtime.industry().industrial().station(station.stationId()).facilities();
        runtime.allocateFacilityResources(station.stationId(), installed.facilityInstanceId());
        var afterAllocation = runtime.industry().industrial().station(station.stationId()).facilities();
        assertEquals(beforeAllocation.stream().mapToDouble(f -> f.allocatedProcessPowerW()).sum(),
                afterAllocation.stream().mapToDouble(f -> f.allocatedProcessPowerW()).sum(), 1e-6);
        assertEquals(beforeAllocation.stream().mapToDouble(f -> f.availableHeatRejectionW()).sum(),
                afterAllocation.stream().mapToDouble(f -> f.availableHeatRejectionW()).sum(), 1e-6);
        assertEquals(beforeAllocation.stream().mapToDouble(f -> f.availableLaborUnits()).sum(),
                afterAllocation.stream().mapToDouble(f -> f.availableLaborUnits()).sum(), 1e-6);
        assertEquals(beforeAllocation.stream().mapToDouble(f -> f.availableMaintenanceWorkRate()).sum(),
                afterAllocation.stream().mapToDouble(f -> f.availableMaintenanceWorkRate()).sum(), 1e-6);
        assertTrue(runtime.industry().industrial().station(station.stationId()).facilityCapabilities().stream()
                .filter(f -> f.facilityInstanceId().equals(installed.facilityInstanceId())).findFirst().orElseThrow()
                .effectiveEngineeringWorkRate() > 0);
        var allocated = campaign.captureState();
        runtime.adoptCompletedFacilityConstruction(completed);
        assertEquals(allocated, campaign.captureState(), "Repeated completion cannot reset actual allocated resources");
        assertEquals(allocated, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(allocated))).captureState());

        // Explicit berth fixture verifies command authorization; it does not replace ordinary player travel.
        var commandWorld = restoredRuntime.world();
        var commandFleet = commandWorld.findFleet(restored.playerState().orElseThrow().activeFleetId()).orElseThrow();
        boolean transferred = !commandFleet.systemId().equals(station.systemId());
        if (transferred) {
            commandWorld.beginFleetTransfer(commandFleet.id(), station.systemId());
            commandWorld.completeFleetTransfer(commandFleet.id(), 0, 0);
            commandFleet = commandWorld.findFleet(commandFleet.id()).orElseThrow();
        }
        var local = restoredRuntime.arrival().materialization(station.systemId());
        if (transferred) local.registerPhysicalState(commandFleet.localEntityId(),
                com.spacesim.world.LocalPhysicalKinematics.stationary(station.position()));
        local.updatePhysicalState(commandFleet.localEntityId(), com.spacesim.world.LocalPhysicalKinematics.stationary(station.position()));
        var dock = restored.previewPilotAction("DOCK", station.stationId(), "", 0);
        assertTrue(dock.allowed()); restored.submitPilotAction(dock);
        var beforeCommand = restored.captureState();
        var start = restored.previewPilotAction("START_FACILITY_CONSTRUCTION", station.stationId(), definition, 1);
        assertTrue(start.allowed());
        assertEquals(beforeCommand, restored.captureState(), "An accepted preview cannot reserve stock or write a journal event");
        restored.submitPilotAction(start);
        assertEquals(beforeCommand.playerJournal().nextSequence() + 1, restored.playerJournal().nextSequence());
        assertThrows(IllegalStateException.class, () -> restored.submitPilotAction(start));
        String commandedOrder = restoredRuntime.constructionQueue().capture().get(0).orderId();
        var cancel = restored.previewPilotAction("CANCEL_FACILITY_CONSTRUCTION", station.stationId(), commandedOrder, 0);
        assertTrue(cancel.allowed()); restored.submitPilotAction(cancel);
        assertEquals(originalContents, restoredStorage.snapshot());
        assertTrue(restoredRuntime.constructionQueue().capture().isEmpty());

        restored.coordinator().setPaused(false);
        restored.advanceFrame(restored.coordinator().session().fixedStepSeconds());
        restored.coordinator().setPaused(true);
        var beforeYard = restored.captureState();
        long actualTick = restoredRuntime.world().getAuthoritativeWorldTick();
        assertTrue(actualTick > 0);
        var yardCatalog = com.spacesim.content.Stage23YardConstructionCatalog.loadDefault();
        var yardSpec = yardCatalog.find("yard.orbital_escort_v1");
        // Explicit completed structure fixture proves composed admission only, not ordinary construction/delivery.
        var yardOrder = new com.spacesim.economy.Stage23YardConstructionWorkQueue.Order("native-yard-test", "native-yard-instance",
                yardSpec.yardDefinitionId(), station.stationId(), "location.orbital_station", actualTick - 1,
                yardSpec.requiredWorkSeconds());
        var yardState = new com.spacesim.economy.Stage23YardConstructionWorkQueue.State(yardCatalog.fingerprint(), actualTick, List.of(yardOrder));
        var yardQueue = new com.spacesim.economy.Stage23YardConstructionWorkQueue(yardCatalog, ontology, yardState);
        assertThrows(IllegalArgumentException.class, () -> withYard(beforeYard, yardState),
                "Completed evidence cannot invent an absent installed structure");
        restoredRuntime.adoptCompletedYardConstruction(List.of(yardOrder), yardQueue);
        var installedYardCheckpoint = restored.captureState();
        var joint = withYard(installedYardCheckpoint, yardState);
        var resumedYard = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(joint)));
        assertEquals(joint, resumedYard.captureState());
        assertTrue(com.spacesim.ui.GeneratedCampaignYardConstructionUi.rows(resumedYard).stream()
                .anyMatch(r -> r.selection().stableId().equals("personal-yard-construction|" + station.stationId() + "|native-yard-test")));
        assertFalse(resumedYard.coordinator().runtime().industry().industrial().station(station.stationId()).yards().stream()
                .filter(y -> y.yardInstanceId().equals("native-yard-instance")).findFirst().orElseThrow().enabled());
        var incomplete = new com.spacesim.economy.Stage23YardConstructionWorkQueue.Order(yardOrder.orderId(), yardOrder.yardInstanceId(),
                yardOrder.yardDefinitionId(), yardOrder.stationId(), yardOrder.locationTag(), actualTick - 1, 0);
        assertThrows(IllegalArgumentException.class, () -> withYard(installedYardCheckpoint,
                new com.spacesim.economy.Stage23YardConstructionWorkQueue.State(yardCatalog.fingerprint(), actualTick, List.of(incomplete))));
        assertThrows(IllegalArgumentException.class, () -> withYard(installedYardCheckpoint,
                new com.spacesim.economy.Stage23YardConstructionWorkQueue.State(yardCatalog.fingerprint(), actualTick + 1, List.of(yardOrder))));
        var beforePlannedYard = restored.captureState();
        var plan = restored.previewPilotAction("START_YARD_CONSTRUCTION", station.stationId(), yardSpec.yardDefinitionId(), 1);
        assertTrue(plan.allowed(), "Planning grants no materials or completed structure");
        assertEquals(beforePlannedYard, restored.captureState());
        restored.submitPilotAction(plan);
        assertTrue(restored.yardConstruction().orders().get(0).stagedAtSite());
        assertTrue(restored.yardConstruction().orders().get(0).deliveredMassByCommodityKg().isEmpty());
        assertEquals(0, restored.yardConstruction().orders().get(0).completedWorkSeconds());
        var planned = restored.captureState();
        var resumedPlan = Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(planned)));
        assertEquals(planned, resumedPlan.captureState());
        var warehouseBeforeDelivery = restoredStorage.snapshot();
        resumedPlan.coordinator().setPaused(false);
        resumedPlan.advanceFrame(resumedPlan.coordinator().session().fixedStepSeconds());
        var deliveredOrder = resumedPlan.yardConstruction().orders().get(0);
        var deliveredStore = resumedPlan.coordinator().runtime().infrastructure().endpoint(station.stationId()).storage();
        assertEquals(0, deliveredOrder.completedWorkSeconds(), "Incomplete actual deliveries cannot grant work");
        warehouseBeforeDelivery.commodityMassByIdKg().forEach((commodity, kg) -> assertEquals(kg,
                deliveredStore.commodityMassKg(commodity) + deliveredOrder.deliveredMassByCommodityKg().getOrDefault(commodity, 0d), 1e-6));
        assertTrue(deliveredOrder.deliveredMassByCommodityKg().values().stream().mapToDouble(Double::doubleValue).sum()
                <= resumedPlan.coordinator().runtime().infrastructure().endpoint(station.stationId()).handlingCapability()
                        .openInterval(resumedPlan.coordinator().session().fixedStepSeconds()).remainingMassKg() + 1e-6);
        var afterDelivery = resumedPlan.captureState();
        assertEquals(afterDelivery, Stage228CampaignAuthority.restore(Stage228GeneratedCampaignPersistenceCodec.decode(
                Stage228GeneratedCampaignPersistenceCodec.encode(afterDelivery))).captureState());
    }

    private static Stage228GeneratedCampaignPersistentState withYard(Stage228GeneratedCampaignPersistentState saved,
            com.spacesim.economy.Stage23YardConstructionWorkQueue.State yard) {
        return new Stage228GeneratedCampaignPersistentState(Stage228GeneratedCampaignPersistentState.CURRENT_VERSION,
                Stage228GeneratedCampaignPersistentState.CURRENT_RUNTIME_VERSION, saved.stage21Runtime(), saved.smallCraft(),
                saved.hangars(), saved.flightDeck(), saved.operations(), saved.playerState(), saved.playerJournal(), saved.moduleCustody(),
                saved.repairQueue(), saved.refitQueue(), saved.moduleTransfers(), saved.productTransfers(), yard);
    }
}
