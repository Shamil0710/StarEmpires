package com.spacesim.content;

import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.economy.Stage18FacilityConstructionRuntime;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.ConstructionOrderSnapshot;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.OrderStatus;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.WorkStatus;
import com.spacesim.economy.Stage18FacilityRuntime;
import com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.economy.Stage18ShipyardRuntime;
import com.spacesim.economy.Stage18ShipyardRuntime.InstalledYardState;
import com.spacesim.economy.Stage18StationIndustrialNode;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.persistence.Stage18IndustrialContentFingerprint;
import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage18IndustrialStateCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * M22.6 B03 planned-expansion trajectory through ordinary Stage-18 construction/persistence.
 *
 * <p>Both core packages require the same four support capabilities for their authored capital yard.
 * The canonical industrial-station start already owns heavy, electrical and assembly fabrication,
 * leaving precision fabrication as the real missing dependency. This probe therefore measures the
 * actual blocked -> materials committed -> half-built -> save/load -> complete -> yard-active path;
 * it does not construct an already-present support facility or invent an abstract expansion score.</p>
 */
class Stage22CorePairExpansionTrajectoryPersistenceAcceptanceTest {
    private static final String STATION_ARCHETYPE = "station.infrastructure.industrial_station";
    private static final String LOCATION = "location.orbital_station";
    private static final String EXPECTED_MISSING_SUPPORT = "facility.fabrication.precision";

    @Test
    void b03MatchedExpansionTrajectorySurvivesMidConstructionCheckpoint() {
        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B03",
                "stage18_required_support_expansion_trajectory",
                "stage18+stage22.current",
                Stage22CorePairExperimentProtocol.tuningSchedule(),
                (scenario, variant, profile, coordinate) -> {
                    ExpansionTrajectory empire = run(true, coordinate.seed());
                    ExpansionTrajectory union = run(false, coordinate.seed());
                    ExpansionTrajectory empireReplay = run(true, coordinate.seed());
                    ExpansionTrajectory unionReplay = run(false, coordinate.seed());

                    ArrayList<String> breaches = new ArrayList<>();
                    if (!empire.equals(empireReplay)) breaches.add("empire_expansion_exact_replay_diverged");
                    if (!union.equals(unionReplay)) breaches.add("union_expansion_exact_replay_diverged");
                    if (!empire.valid()) breaches.add("empire_expansion_trajectory_invalid");
                    if (!union.valid()) breaches.add("union_expansion_trajectory_invalid");
                    if (!empire.physicalBurden().equals(union.physicalBurden())) {
                        breaches.add("core_pair_support_expansion_burden_not_matched");
                    }

                    return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(
                            Map.of(
                                    "empire_support_mass_kg", empire.physicalBurden().installedMassKg(),
                                    "union_support_mass_kg", union.physicalBurden().installedMassKg(),
                                    "empire_support_work_seconds", empire.physicalBurden().requiredWorkSeconds(),
                                    "union_support_work_seconds", union.physicalBurden().requiredWorkSeconds(),
                                    "empire_activation_seconds", empire.physicalBurden().activationSeconds(),
                                    "union_activation_seconds", union.physicalBurden().activationSeconds()),
                            Map.of(
                                    "empire_midwork_save_continuation", empire.saveContinuation() ? 1d : 0d,
                                    "union_midwork_save_continuation", union.saveContinuation() ? 1d : 0d,
                                    "empire_yard_activated", empire.yardActivated() ? 1d : 0d,
                                    "union_yard_activated", union.yardActivated() ? 1d : 0d,
                                    "matched_support_burden", empire.physicalBurden().equals(union.physicalBurden()) ? 1d : 0d),
                            breaches);
                });

        assertEquals(0, vector.hardRuleBreachCount());
        assertEquals(1d, vector.guardMetricMeans().get("empire_midwork_save_continuation"));
        assertEquals(1d, vector.guardMetricMeans().get("union_midwork_save_continuation"));
        assertEquals(1d, vector.guardMetricMeans().get("matched_support_burden"));
        Stage22CorePairEvidenceArchive.write(
                "B03-stage18-expansion-trajectory-tuning",
                vector,
                "Canonical 30-seed/default+mirrored B03 batch over the actual missing precision-fabrication dependency. Both core yards begin blocked, pay the same Stage-18 kg/work support burden, survive an ordinary mid-construction industrial checkpoint, and activate only after the required facility completes. IDs vary by coordinate only; no gameplay RNG or faction bonus is introduced.");
    }

    private static ExpansionTrajectory run(boolean empire, long seed) {
        String key = (empire ? "empire" : "union") + "." + seed;
        var engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var products = Stage18ManufacturingProductRegistry.loadDefault()
                .withEngineeringCatalog(engineering, Provenance.STAGE22_AUTHORED);
        var facilityCatalog = Stage18FacilityCatalogLoader.loadDefault();
        var facilityRuntime = new Stage18FacilityRuntime(facilityCatalog);
        var construction = new Stage18FacilityConstructionRuntime(
                Stage18FacilityConstructionCatalogLoader.loadDefault(), facilityCatalog, ontology);
        var yards = empire ? Stage22EmpireShipyardCatalogLoader.loadDefault()
                : Stage22IndustrialUnionShipyardCatalogLoader.loadDefault();
        var yardDefinition = yards.getYards().get(0);
        var yardRuntime = new Stage18ShipyardRuntime(yards, ontology, products);
        Stage18StationIndustrialNode station = Stage18StationIndustrialNode.instantiate(
                "b03.station." + key,
                LOCATION,
                Stage18StationInfrastructureCatalogLoader.loadDefault().findArchetype(STATION_ARCHETYPE),
                ontology,
                products);
        InstalledYardState installedYard = new InstalledYardState(
                "b03.yard." + key,
                yardDefinition.id(),
                1d,
                yardDefinition.ratedIntegrationPowerW(),
                yardDefinition.ratedEngineeringWorkRate(),
                yardDefinition.laborCapacity(),
                yardDefinition.automationCapacity(),
                true);

        List<InstalledFacilityState> facilities = new ArrayList<>();
        for (var reference : station.installedFacilities()) {
            facilities.add(allocatedFacility(reference, facilityCatalog));
        }
        List<FacilityCapabilitySnapshot> projections = facilities.stream().map(facilityRuntime::project).toList();
        boolean initiallyBlocked = !yardRuntime.projectYard(installedYard, station, projections).active();

        List<String> missing = yardDefinition.requiredSupportFacilityDefinitionIds().stream()
                .filter(required -> station.installedFacilities().stream()
                        .noneMatch(row -> row.facilityDefinitionId().equals(required)))
                .sorted()
                .toList();
        if (!missing.equals(List.of(EXPECTED_MISSING_SUPPORT))) {
            throw new AssertionError("B03 canonical start changed missing support set: " + missing);
        }

        ConstructionOrderSnapshot created = construction.createOrder(
                "b03.build." + key,
                "b03.extra." + key,
                EXPECTED_MISSING_SUPPORT,
                station.stationId(),
                station.locationTag());
        OrderStatus createdStatus = created.status();
        Stage18StationStorage kit = new Stage18StationStorage(
                ontology,
                products,
                station.stationId(),
                station.storage().snapshotCapacityByStorageClassKg(),
                created.requiredMassByCommodityKg(),
                Map.of());
        ConstructionOrderSnapshot materialized = created;
        for (var input : created.requiredMassByCommodityKg().entrySet()) {
            materialized = construction.deliver(materialized, kit, input.getKey(), input.getValue()).order();
        }
        OrderStatus materialsStatus = materialized.status();
        double residualKitKg = kit.snapshotCommodityMassByIdKg().values().stream()
                .mapToDouble(Double::doubleValue).sum();

        var capability = construction.projectCapability("b03.existing_constructors." + key, projections);
        double activationSeconds = materialized.requiredWorkSeconds() / capability.engineeringWorkRate();
        var half = construction.advanceWork(
                materialized,
                capability.openInterval(activationSeconds / 2d));
        if (half.status() != WorkStatus.ADVANCED || half.order().status() != OrderStatus.BUILDING) {
            throw new AssertionError("B03 half-work milestone did not remain BUILDING: " + half.status());
        }

        Stage18IndustrialState checkpoint = new Stage18IndustrialState(
                Stage18IndustrialState.CURRENT_VERSION,
                Stage18IndustrialContentFingerprint.current(),
                0L,
                List.of(),
                List.of(station.storage().snapshot()),
                facilities.stream()
                        .map(row -> new Stage18IndustrialState.FacilityInstallationSnapshot(station.stationId(), row))
                        .toList(),
                List.of(new Stage18IndustrialState.YardInstallationSnapshot(station.stationId(), installedYard)),
                List.of(half.order()),
                List.of());
        byte[] encoded = Stage18IndustrialStateCodec.encode(checkpoint);
        Stage18IndustrialState decoded = Stage18IndustrialStateCodec.decode(encoded);
        boolean byteStable = Arrays.equals(encoded, Stage18IndustrialStateCodec.encode(decoded));
        ConstructionOrderSnapshot restoredHalf = decoded.constructionOrders().get(0);

        double remainingSeconds = half.order().remainingWorkSeconds() / capability.engineeringWorkRate();
        var directComplete = construction.advanceWork(
                half.order(), capability.openInterval(remainingSeconds + 1d));
        var restoredComplete = construction.advanceWork(
                restoredHalf, capability.openInterval(remainingSeconds + 1d));
        boolean continuationEqual = directComplete.equals(restoredComplete)
                && directComplete.status() == WorkStatus.COMPLETED
                && restoredComplete.order().status() == OrderStatus.COMPLETE;

        Stage18StationIndustrialNode directStation = station.withCompletedConstruction(directComplete.order(), construction);
        Stage18StationIndustrialNode restoredStation = station.withCompletedConstruction(restoredComplete.order(), construction);
        facilities.add(allocatedFacility(restoredComplete.installedFacility(), facilityCatalog));
        projections = facilities.stream().map(facilityRuntime::project).toList();
        boolean yardActivated = yardRuntime.projectYard(installedYard, restoredStation, projections).active();
        boolean stationContinuationEqual = directStation.installedFacilities().equals(restoredStation.installedFacilities());

        ExpansionBurden burden = new ExpansionBurden(
                created.installedMassKg(),
                created.requiredWorkSeconds(),
                activationSeconds);
        return new ExpansionTrajectory(
                burden,
                List.of(createdStatus, materialsStatus, half.order().status(), restoredComplete.order().status()),
                initiallyBlocked,
                residualKitKg <= 1e-6d,
                byteStable && continuationEqual && stationContinuationEqual,
                yardActivated);
    }

    private static InstalledFacilityState allocatedFacility(
            Stage18StationIndustrialNode.InstalledFacilityReference reference,
            Stage18FacilityCatalog catalog) {
        var definition = catalog.findFacility(reference.facilityDefinitionId());
        return new InstalledFacilityState(
                reference.facilityInstanceId(),
                definition.id(),
                1d,
                definition.ratedProcessPowerW(),
                definition.ratedProcessPowerW() * definition.heatRejectionWPerProcessW(),
                definition.requiredLaborUnitsAtFullRate(),
                definition.maintenanceWorkRate(),
                LOCATION,
                true);
    }

    private record ExpansionBurden(
            double installedMassKg,
            double requiredWorkSeconds,
            double activationSeconds) { }

    private record ExpansionTrajectory(
            ExpansionBurden physicalBurden,
            List<OrderStatus> milestones,
            boolean initiallyBlocked,
            boolean materialsConsumed,
            boolean saveContinuation,
            boolean yardActivated) {
        boolean valid() {
            return initiallyBlocked
                    && materialsConsumed
                    && saveContinuation
                    && yardActivated
                    && physicalBurden.installedMassKg() > 0d
                    && physicalBurden.requiredWorkSeconds() > 0d
                    && physicalBurden.activationSeconds() > 0d
                    && milestones.equals(List.of(
                            OrderStatus.AWAITING_MATERIALS,
                            OrderStatus.READY_FOR_WORK,
                            OrderStatus.BUILDING,
                            OrderStatus.COMPLETE));
        }
    }
}
