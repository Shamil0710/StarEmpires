package com.spacesim.world.generation;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.economy.Stage18LogisticsRuntime.HandlingCapability;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage20FreightPersistentState.AssignmentKind;
import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage20FreightRuntime;
import com.spacesim.persistence.Stage20FreightRuntimeMaterializer;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistence;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage20MaterializationPersistence;
import com.spacesim.persistence.Stage20MaterializationPersistentState;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.simulation.Stage20MaterializationService;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.Stage20DiscoveryKnowledgeState;
import com.spacesim.world.Stage20SpecialLocationGenerator;
import com.spacesim.world.generation.Stage20OperationalIndustrialSpecializationProductionIntegrationTest
        .CadenceFixture;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20FreightRuntimeMaterializerTest {
    private static volatile CadenceFixture sharedFixture;

    @Test
    void exactOwnedPoolGetsUniqueFleetIdsWithoutReusingEssentialSlotsOrCreatingCargo() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState campaign = savedState(fixture);
        var freight = Stage20FreightRuntimeMaterializer.materializeBootstrap(
                campaign, fixture.specialization(), 40_000L);
        var ownership = fixture.specialization().yardInstallation().inventory()
                .operatingState().freightOwnership().bootstrapOwnership();
        var industrial = fixture.specialization().yardInstallation().inventory()
                .operatingState().freightOwnership();

        assertEquals(ownership.totalOwnedFreighters(), freight.freighters().size());
        assertEquals(40_000L, freight.freighters().get(0).fleetId().value());
        assertEquals(40_000L + freight.freighters().size(), freight.nextFleetIdValue());
        assertEquals(freight.freighters().size(), freight.freighters().stream()
                .map(value -> value.fleetId()).distinct().count());
        assertEquals(freight.freighters().size(), freight.freighters().stream()
                .map(value -> value.stableFactionId() + ':' + value.ownershipOrdinal())
                .distinct().count());
        assertTrue(freight.freighters().stream().allMatch(value -> value.cargoMassKg() == 0d));
        assertTrue(freight.cargoLots().isEmpty());

        long expectedEssential = ownership.totalCommittedFreighters();
        long expectedIndustrial = industrial.allocations().stream()
                .mapToLong(value -> value.assignedSlots().size()).sum();
        assertEquals(expectedEssential, freight.orders().stream()
                .filter(value -> value.assignmentKind() == AssignmentKind.ESSENTIAL_BOOTSTRAP)
                .count());
        assertEquals(expectedIndustrial, freight.orders().stream()
                .filter(value -> value.assignmentKind() == AssignmentKind.INDUSTRIAL_INPUT)
                .count());
        assertEquals(expectedEssential + expectedIndustrial, freight.orders().size());

        Set<String> essentialSlots = new HashSet<>();
        ownership.factions().forEach(owner -> owner.materializationSlots().stream()
                .filter(value -> value.commitment().isPresent())
                .forEach(value -> essentialSlots.add(
                        value.stableFactionId() + ':' + value.ownershipOrdinal())));
        freight.freighters().stream()
                .filter(value -> !value.activeOrderId().isEmpty())
                .forEach(value -> {
                    var order = freight.orders().stream()
                            .filter(row -> row.orderId().equals(value.activeOrderId()))
                            .findFirst().orElseThrow();
                    if (order.assignmentKind() == AssignmentKind.INDUSTRIAL_INPUT) {
                        assertFalse(essentialSlots.contains(
                                value.stableFactionId() + ':' + value.ownershipOrdinal()));
                    }
                });
    }

    @Test
    void physicalLoadNeighborTravelAndUnloadConserveMassAndLotProvenance() {
        CadenceFixture fixture = fixture();
        var freight = Stage20FreightRuntimeMaterializer.materializeBootstrap(
                savedState(fixture), fixture.specialization(), 50_000L);
        Stage20FreightRuntime runtime = Stage20FreightRuntime.restore(freight);
        var order = freight.orders().get(0);
        var fleetId = order.fleetId();
        double massKg = Math.min(250_000d,
                runtime.findFreighter(fleetId).orElseThrow().cargoCapacityKg());
        StoragePair storage = storagePair(order.sourceEndpointId(), order.destinationEndpointId(),
                order.commodityId(), massKg);
        HandlingCapability handling = handling(order.commodityId(), massKg);

        var loaded = runtime.loadCommodity(
                fleetId,
                storage.source(),
                massKg,
                order.sourceProvenanceId(),
                10d,
                handling,
                handling.openInterval(1d));
        assertTrue(loaded.transferred());
        assertEquals(0d, storage.source().commodityMassKg(order.commodityId()), 0d);
        assertEquals(massKg, runtime.cargoHoldSnapshot(fleetId)
                .commodityMassByIdKg().get(order.commodityId()), 0d);
        assertEquals(order.sourceProvenanceId(), runtime.cargoLots().get(0).sourceProvenanceId());

        runtime.dispatchOutbound(fleetId, 20d);
        for (int index = 1; index < order.orderedSystems().size(); index++) {
            runtime.completeNextOutboundHop(
                    fleetId,
                    order.orderedSystems().get(index),
                    new LocalPhysicalKinematics(
                            new LocalPhysicalPosition(index, -index, index * 10d, index * -10d),
                            120d + index,
                            -30d - index));
        }
        assertEquals(FreightPhase.AT_DESTINATION,
                runtime.findFreighter(fleetId).orElseThrow().phase());

        var unloaded = runtime.unloadCommodity(
                fleetId,
                storage.destination(),
                massKg,
                handling,
                handling.openInterval(1d));
        assertTrue(unloaded.transferred());
        assertEquals(massKg, storage.destination().commodityMassKg(order.commodityId()), 0d);
        assertEquals(0d, runtime.findFreighter(fleetId).orElseThrow().cargoMassKg(), 0d);
        assertTrue(runtime.cargoLots().isEmpty());
        assertEquals(massKg,
                runtime.findOrder(order.orderId()).orElseThrow().deliveredMassKg(), 0d);
        assertEquals(runtime.capture(), Stage20FreightRuntime.restore(runtime.capture()).capture());
    }

    @Test
    void destructionLosesAboardSupplyAndNeverCreatesReplacementFleet() {
        CadenceFixture fixture = fixture();
        var freight = Stage20FreightRuntimeMaterializer.materializeBootstrap(
                savedState(fixture), fixture.specialization(), 60_000L);
        Stage20FreightRuntime runtime = Stage20FreightRuntime.restore(freight);
        var order = freight.orders().get(0);
        double massKg = 100_000d;
        StoragePair storage = storagePair(order.sourceEndpointId(), order.destinationEndpointId(),
                order.commodityId(), massKg);
        HandlingCapability handling = handling(order.commodityId(), massKg);
        runtime.loadCommodity(
                order.fleetId(), storage.source(), massKg, order.sourceProvenanceId(), 1d,
                handling, handling.openInterval(1d));
        int fleetCount = runtime.capture().freighters().size();

        var destruction = runtime.destroy(order.fleetId());

        assertTrue(destruction.destroyedNow());
        assertEquals(massKg, destruction.lostCargoMassKg(), 0d);
        assertEquals(1, destruction.lostLots().size());
        assertEquals(FreightPhase.DESTROYED,
                runtime.findFreighter(order.fleetId()).orElseThrow().phase());
        assertEquals(0d, runtime.findFreighter(order.fleetId()).orElseThrow().cargoMassKg(), 0d);
        assertTrue(runtime.cargoLots().isEmpty());
        assertEquals(fleetCount, runtime.capture().freighters().size());
        assertEquals(0d, storage.destination().commodityMassKg(order.commodityId()), 0d);
    }

    @Test
    void restoreFailsClosedForDifferentSavedWorldFingerprint() {
        CadenceFixture fixture = fixture();
        Stage20GeneratedCampaignPersistentState campaign = savedState(fixture);
        var freight = Stage20FreightRuntimeMaterializer.materializeBootstrap(
                campaign, fixture.specialization(), 70_000L);
        var forged = new com.spacesim.persistence.Stage20FreightPersistentState(
                freight.schemaVersion(),
                freight.rootSeed(),
                freight.generatorVersion(),
                "0".repeat(64),
                freight.materializationVersion(),
                freight.compatibilityAuthorityVersion(),
                freight.nextFleetIdValue(),
                freight.nextCargoLotOrdinal(),
                freight.freighters(),
                freight.cargoLots(),
                freight.orders());
        var compatibility = Stage20FreightRuntimeMaterializer
                .FreighterCompatibilityAuthority.currentProvisional();

        assertThrows(IllegalArgumentException.class, () ->
                Stage20FreightRuntimeMaterializer.validateRestore(
                        campaign,
                        fixture.specialization(),
                        forged,
                        compatibility,
                        com.spacesim.content.ship.Stage175ICombatTestContentPack.load()));
    }

    private static StoragePair storagePair(
            String sourceId,
            String destinationId,
            String commodityId,
            double massKg) {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault();
        String storageClass = ontology.findCommodity(commodityId).storageClassId();
        Stage18StationStorage source = new Stage18StationStorage(
                ontology, products, sourceId, Map.of(storageClass, massKg),
                Map.of(commodityId, massKg), Map.of());
        Stage18StationStorage destination = new Stage18StationStorage(
                ontology, products, destinationId, Map.of(storageClass, massKg), Map.of(), Map.of());
        return new StoragePair(source, destination);
    }

    private static HandlingCapability handling(String commodityId, double massKg) {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        return new HandlingCapability(
                "handling.stage20_5.freight-test",
                Set.of(ontology.findCommodity(commodityId).storageClassId()),
                massKg,
                Double.MAX_VALUE);
    }

    private static Stage20GeneratedCampaignPersistentState savedState(CadenceFixture fixture) {
        SimulationSession session = SimulationSession.createDemo(fixture.resolved().rootSeed());
        Stage20MaterializationPersistentState physical = Stage20MaterializationPersistence.capture(
                session,
                Stage20MaterializationService.forSession(session));
        return Stage20GeneratedCampaignPersistence.capture(
                fixture.resolved(),
                Stage20SpecialLocationGenerator.generateCurrent(fixture.resolved()),
                fixture.specialization(),
                physical,
                Stage18IndustrialState.empty(0L),
                List.of(new Stage20DiscoveryKnowledgeState(
                        "faction.stage20_5.freight-materialization",
                        List.of())));
    }

    private static synchronized CadenceFixture fixture() {
        if (sharedFixture == null) {
            sharedFixture = Stage20OperationalIndustrialSpecializationProductionIntegrationTest.cadenceFixture();
        }
        return sharedFixture;
    }

    private record StoragePair(
            Stage18StationStorage source,
            Stage18StationStorage destination) { }
}
