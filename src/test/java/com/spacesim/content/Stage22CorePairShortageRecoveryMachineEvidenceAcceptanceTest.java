package com.spacesim.content;

import com.spacesim.content.Stage18FacilityCatalog.FacilityDefinition;
import com.spacesim.content.Stage18ManufacturingCatalog.ManufacturingInputDefinition;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.Stage18ResourceOntologyCatalog.CommodityDefinition;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.economy.Stage18ExtractionRuntime;
import com.spacesim.economy.Stage18FacilityRuntime;
import com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.economy.Stage18LogisticsRuntime;
import com.spacesim.economy.Stage18LogisticsRuntime.HandlingCapability;
import com.spacesim.economy.Stage18ManufacturingRuntime;
import com.spacesim.economy.Stage18RefiningRuntime;
import com.spacesim.economy.Stage18StationProductionBridge;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.persistence.Stage18IndustrialContentFingerprint;
import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage18IndustrialState.FacilityInstallationSnapshot;
import com.spacesim.persistence.Stage18IndustrialStateCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B04 matched physical shortage/recovery through the accepted Stage-18 production chain.
 *
 * <p>Both exact core cargo modules begin from the same finite station capacities, the same physical
 * non-critical component masses and the same separate heavy-component depot stock. The critical
 * component is absent at the manufacturing station, so the ordinary station-production bridge must
 * reject without mutation. The midpoint is serialized with the Stage-18 industrial codec. Direct and
 * restored continuations then use the same finite Stage-18 logistics interface to deliver only the
 * authored heavy-component requirement before retrying the exact product through one real assembly
 * facility. No faction-local inventory, free recovery or synthetic manufacturing authority exists.</p>
 */
class Stage22CorePairShortageRecoveryMachineEvidenceAcceptanceTest {
    private static final String EMPIRE_PRODUCT = "module.empire_cargo_secure_v1";
    private static final String UNION_PRODUCT = "module.industrial_union_cargo_section_v1";
    private static final String HEAVY_COMPONENT = "commodity.component.heavy_components";
    private static final String ASSEMBLY_FACILITY = "facility.fabrication.assembly";
    private static final String TARGET_STATION = "station.m22_6.shortage.target";
    private static final String DEPOT_STATION = "station.m22_6.shortage.depot";
    private static final double EPSILON = 1e-7d;

    @Test
    void b04MatchedShortagePersistsThenFinitePhysicalDeliveryRestoresBothAuthoredProducts() {
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var products = Stage18ManufacturingProductRegistry.loadDefault()
                .withEngineeringCatalog(Stage22CorePairEngineeringCatalogLoader.loadDefault(), Provenance.STAGE22_AUTHORED);
        var empireCatalog = Stage22EmpireManufacturingCatalogLoader.loadDefault();
        var unionCatalog = Stage22IndustrialUnionManufacturingCatalogLoader.loadDefault();

        ProductRequirements empireRequirements = requirements(empireCatalog, products, EMPIRE_PRODUCT);
        ProductRequirements unionRequirements = requirements(unionCatalog, products, UNION_PRODUCT);
        MatchedStart start = matchedStart(ontology, products, empireRequirements, unionRequirements);
        InstalledFacilityState assemblyState = assemblyFacilityState();

        ScenarioResult empire = run(
                "empire",
                EMPIRE_PRODUCT,
                empireCatalog,
                empireRequirements,
                start,
                assemblyState,
                ontology,
                products);
        ScenarioResult union = run(
                "industrial_union",
                UNION_PRODUCT,
                unionCatalog,
                unionRequirements,
                start,
                assemblyState,
                ontology,
                products);

        assertEquals(empire.startFingerprint(), union.startFingerprint(),
                "paired B04 coordinates must begin from the exact same physical inventory/capacity state");
        assertEquals(empire.handlingRateKgPerSecond(), union.handlingRateKgPerSecond(), EPSILON,
                "paired B04 coordinates must use the same physical handling equipment");
        assertTrue(empire.shortageRejectedWithoutMutation());
        assertTrue(union.shortageRejectedWithoutMutation());
        assertTrue(empire.midpointByteStable());
        assertTrue(union.midpointByteStable());
        assertTrue(empire.directRestoredContinuationEqual());
        assertTrue(union.directRestoredContinuationEqual());
        assertTrue(empire.recoveredManufactureAccepted());
        assertTrue(union.recoveredManufactureAccepted());
        assertTrue(empire.heavyMassDeliveredKg() > 0d);
        assertTrue(union.heavyMassDeliveredKg() > 0d);

        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("matchedStartFingerprint", empire.startFingerprint());
        evidence.put("empire", empire);
        evidence.put("industrialUnion", union);
        Stage22CorePairEvidenceArchive.write(
                "B04-matched-shortage-physical-recovery",
                evidence,
                "Both core packages use an identical finite physical start and one real Stage-18 assembly facility. Missing heavy components reject without station mutation; the midpoint survives the Stage-18 industrial binary codec; finite Stage-18 logistics then delivers each authored bill and direct/restored continuations manufacture the exact authored module identically. Generated Stage-20 station storage still uses the baseline Stage-17.5 product vocabulary, so generated-world authored-product storage composition remains an explicit integration gap and B04 is not promoted to COMPLETE by this slice alone.");
    }

    private static ScenarioResult run(
            String packageId,
            String productId,
            Stage18ManufacturingCatalog manufacturingCatalog,
            ProductRequirements requirements,
            MatchedStart start,
            InstalledFacilityState assemblyState,
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products) {
        Stage18StationStorage target = new Stage18StationStorage(
                ontology,
                products,
                TARGET_STATION,
                start.capacitiesByStorageClassKg(),
                start.targetCommodityMassByIdKg(),
                Map.of());
        Stage18StationStorage depot = new Stage18StationStorage(
                ontology,
                products,
                DEPOT_STATION,
                start.capacitiesByStorageClassKg(),
                start.depotCommodityMassByIdKg(),
                Map.of());
        assertEquals(0d, target.commodityMassKg(HEAVY_COMPONENT), EPSILON);

        Stage18FacilityRuntime facilityRuntime = new Stage18FacilityRuntime(Stage18FacilityCatalogLoader.loadDefault());
        FacilityCapabilitySnapshot assembly = facilityRuntime.project(assemblyState);
        Stage18StationProductionBridge bridge = productionBridge(
                ontology, products, facilityRuntime, manufacturingCatalog);
        double manufactureSeconds = manufacturingDurationSeconds(
                requirements, manufacturingCatalog, products, assembly);

        var targetBeforeReject = target.snapshot();
        var depotBeforeReject = depot.snapshot();
        var rejected = bridge.manufactureProductAtStation(
                productId,
                1,
                target,
                assembly,
                manufactureSeconds);
        boolean rejectedWithoutMutation = rejected.status() == Stage18ManufacturingRuntime.Status.INSUFFICIENT_INPUT
                && targetBeforeReject.equals(target.snapshot())
                && depotBeforeReject.equals(depot.snapshot());
        assertTrue(rejectedWithoutMutation,
                "B04 shortage must reject before mutating canonical physical station/depot inventory");

        Stage18IndustrialState midpoint = new Stage18IndustrialState(
                Stage18IndustrialState.CURRENT_VERSION,
                Stage18IndustrialContentFingerprint.current(),
                0L,
                List.of(),
                List.of(target.snapshot(), depot.snapshot()),
                List.of(new FacilityInstallationSnapshot(TARGET_STATION, assemblyState)),
                List.of(),
                List.of(),
                List.of());
        byte[] midpointBytes = Stage18IndustrialStateCodec.encode(midpoint);
        Stage18IndustrialState decoded = Stage18IndustrialStateCodec.decode(midpointBytes);
        boolean midpointByteStable = java.util.Arrays.equals(
                midpointBytes, Stage18IndustrialStateCodec.encode(decoded));
        assertArrayEquals(midpointBytes, Stage18IndustrialStateCodec.encode(decoded));

        ContinuationResult direct = continueFrom(
                productId,
                manufacturingCatalog,
                requirements,
                target,
                depot,
                assemblyState,
                start.handlingRateKgPerSecond(),
                ontology,
                products);
        ContinuationResult restored = continueFrom(
                productId,
                manufacturingCatalog,
                requirements,
                restoredStorage(decoded, TARGET_STATION, ontology, products),
                restoredStorage(decoded, DEPOT_STATION, ontology, products),
                decoded.facilities().stream()
                        .filter(row -> row.stationId().equals(TARGET_STATION))
                        .findFirst()
                        .orElseThrow()
                        .state(),
                start.handlingRateKgPerSecond(),
                ontology,
                products);

        boolean continuationsEqual = direct.equals(restored);
        assertEquals(direct, restored,
                "save/load may not change shortage recovery logistics or manufacturing outcome");
        assertTrue(direct.transferAccepted());
        assertTrue(direct.manufactureAccepted());
        assertEquals(1, direct.finalProductCount());
        assertEquals(0d, direct.finalTargetHeavyMassKg(), EPSILON,
                "the exact delivered heavy-component bill must be consumed by one manufactured unit");

        return new ScenarioResult(
                packageId,
                productId,
                start.fingerprint(),
                rejectedWithoutMutation,
                midpointByteStable,
                continuationsEqual,
                direct.manufactureAccepted(),
                requirements.inputMassByCommodityKg().get(HEAVY_COMPONENT),
                start.handlingRateKgPerSecond(),
                direct.manufacturingDurationSeconds(),
                direct.finalProductCount());
    }

    private static ContinuationResult continueFrom(
            String productId,
            Stage18ManufacturingCatalog manufacturingCatalog,
            ProductRequirements requirements,
            Stage18StationStorage target,
            Stage18StationStorage depot,
            InstalledFacilityState assemblyState,
            double handlingRateKgPerSecond,
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products) {
        Stage18FacilityRuntime facilityRuntime = new Stage18FacilityRuntime(Stage18FacilityCatalogLoader.loadDefault());
        FacilityCapabilitySnapshot assembly = facilityRuntime.project(assemblyState);
        Stage18StationProductionBridge bridge = productionBridge(
                ontology, products, facilityRuntime, manufacturingCatalog);
        Stage18LogisticsRuntime logistics = new Stage18LogisticsRuntime(ontology, products);

        double heavyMass = requirements.inputMassByCommodityKg().get(HEAVY_COMPONENT);
        CommodityDefinition heavy = ontology.findCommodity(HEAVY_COMPONENT);
        if (heavy == null) throw new AssertionError("B04 critical component missing from Stage-18 ontology");
        HandlingCapability handling = new HandlingCapability(
                "handling.m22_6.shortage.common",
                Set.of(heavy.storageClassId()),
                handlingRateKgPerSecond,
                1d);
        double transferSeconds = heavyMass / handlingRateKgPerSecond + 1d;
        var transferred = logistics.transferCommodity(
                depot,
                target,
                HEAVY_COMPONENT,
                heavyMass,
                handling,
                handling.openInterval(transferSeconds));
        assertTrue(transferred.transferred());
        assertEquals(heavyMass, transferred.transferredMassKg(), EPSILON);

        double manufacturingSeconds = manufacturingDurationSeconds(
                requirements, manufacturingCatalog, products, assembly);
        var manufactured = bridge.manufactureProductAtStation(
                productId,
                1,
                target,
                assembly,
                manufacturingSeconds);
        return new ContinuationResult(
                transferred.transferred(),
                transferred.transferredMassKg(),
                manufactured.accepted(),
                manufactured.status().name(),
                manufactured.outputMassKg(),
                manufactured.energyConsumedJ(),
                manufactured.workConsumedSeconds(),
                manufactured.maintenanceWorkConsumedSeconds(),
                manufacturingSeconds,
                target.productCount(productId),
                target.commodityMassKg(HEAVY_COMPONENT),
                target.snapshot(),
                depot.snapshot());
    }

    private static Stage18StationProductionBridge productionBridge(
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products,
            Stage18FacilityRuntime facilityRuntime,
            Stage18ManufacturingCatalog manufacturingCatalog) {
        return new Stage18StationProductionBridge(
                ontology,
                products,
                facilityRuntime,
                new Stage18ExtractionRuntime(ontology, Stage18ExtractionCatalogLoader.loadDefault()),
                new Stage18RefiningRuntime(ontology, Stage18RefiningCatalogLoader.loadDefault()),
                new Stage18ManufacturingRuntime(ontology, manufacturingCatalog, products));
    }

    private static InstalledFacilityState assemblyFacilityState() {
        FacilityDefinition definition = Stage18FacilityCatalogLoader.loadDefault().findFacility(ASSEMBLY_FACILITY);
        if (definition == null) throw new AssertionError("B04 assembly facility missing from Stage-18 catalog");
        return new InstalledFacilityState(
                "facility.instance.m22_6.shortage.assembly",
                definition.id(),
                1d,
                definition.ratedProcessPowerW(),
                definition.ratedProcessPowerW() * definition.heatRejectionWPerProcessW(),
                definition.requiredLaborUnitsAtFullRate(),
                definition.maintenanceWorkRate(),
                "location.orbital_station",
                true);
    }

    private static ProductRequirements requirements(
            Stage18ManufacturingCatalog catalog,
            Stage18ManufacturingProductRegistry products,
            String productId) {
        var product = products.findProduct(productId);
        var binding = catalog.findProductBinding(productId);
        if (product == null || binding == null) {
            throw new AssertionError("B04 authored product lacks ordinary Stage-18 registration: " + productId);
        }
        var profile = catalog.findProductProfile(binding.profileId());
        if (profile == null) throw new AssertionError("B04 authored product lacks manufacturing profile: " + productId);
        TreeMap<String, Double> inputs = new TreeMap<>();
        for (ManufacturingInputDefinition input : profile.inputs()) {
            inputs.put(input.commodityId(), product.unitMassKg() * input.fractionOfOutputMass());
        }
        if (!inputs.containsKey(HEAVY_COMPONENT) || inputs.get(HEAVY_COMPONENT) <= 0d) {
            throw new AssertionError("B04 product does not physically depend on declared critical component: " + productId);
        }
        return new ProductRequirements(productId, product.unitMassKg(), binding.profileId(), Map.copyOf(inputs));
    }

    private static MatchedStart matchedStart(
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products,
            ProductRequirements empire,
            ProductRequirements union) {
        TreeMap<String, Double> matchedInputs = new TreeMap<>();
        mergeMaximums(matchedInputs, empire.inputMassByCommodityKg());
        mergeMaximums(matchedInputs, union.inputMassByCommodityKg());
        double maximumHeavy = matchedInputs.remove(HEAVY_COMPONENT);
        if (maximumHeavy <= 0d) throw new AssertionError("B04 matched critical stock is empty");

        TreeMap<String, Double> targetInputs = new TreeMap<>(matchedInputs);
        TreeMap<String, Double> depotInputs = new TreeMap<>();
        depotInputs.put(HEAVY_COMPONENT, maximumHeavy);

        double totalMatchedMass = matchedInputs.values().stream().mapToDouble(Double::doubleValue).sum()
                + maximumHeavy
                + Math.max(empire.outputMassKg(), union.outputMassKg());
        double perClassCapacity = totalMatchedMass * 2d + 1d;
        TreeMap<String, Double> capacities = new TreeMap<>();
        for (String commodityId : matchedInputs.keySet()) {
            CommodityDefinition commodity = ontology.findCommodity(commodityId);
            if (commodity == null) throw new AssertionError("B04 input absent from ontology: " + commodityId);
            capacities.put(commodity.storageClassId(), perClassCapacity);
        }
        CommodityDefinition heavy = ontology.findCommodity(HEAVY_COMPONENT);
        if (heavy == null) throw new AssertionError("B04 critical component absent from ontology");
        capacities.put(heavy.storageClassId(), perClassCapacity);
        capacities.put(products.findProduct(EMPIRE_PRODUCT).storageClassId(), perClassCapacity);
        capacities.put(products.findProduct(UNION_PRODUCT).storageClassId(), perClassCapacity);

        double handlingRate = maximumHeavy / 60d;
        if (!Double.isFinite(handlingRate) || handlingRate <= 0d) {
            throw new AssertionError("B04 matched physical handling rate is invalid");
        }
        String fingerprint = canonicalStartFingerprint(capacities, targetInputs, depotInputs, handlingRate);
        return new MatchedStart(
                Map.copyOf(capacities),
                Map.copyOf(targetInputs),
                Map.copyOf(depotInputs),
                handlingRate,
                fingerprint);
    }

    private static double manufacturingDurationSeconds(
            ProductRequirements requirements,
            Stage18ManufacturingCatalog catalog,
            Stage18ManufacturingProductRegistry products,
            FacilityCapabilitySnapshot assembly) {
        var binding = catalog.findProductBinding(requirements.productId());
        var profile = catalog.findProductProfile(binding.profileId());
        double outputMass = products.findProduct(requirements.productId()).unitMassKg();
        double energy = outputMass * profile.energyJPerOutputKg();
        double work = outputMass * profile.workSecondsPerOutputKg();
        double maintenance = outputMass * profile.maintenanceWorkSecondsPerOutputKg();
        double seconds = Math.max(
                energy / assembly.effectiveProcessPowerW(),
                Math.max(
                        work / assembly.effectiveEngineeringWorkRate(),
                        maintenance / assembly.effectiveMaintenanceWorkRate()));
        if (!Double.isFinite(seconds) || seconds <= 0d) {
            throw new AssertionError("B04 manufacturing duration is invalid");
        }
        return seconds + 1d;
    }

    private static Stage18StationStorage restoredStorage(
            Stage18IndustrialState state,
            String stationId,
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products) {
        return state.stationStorages().stream()
                .filter(snapshot -> snapshot.stationId().equals(stationId))
                .findFirst()
                .map(snapshot -> Stage18StationStorage.restore(ontology, products, snapshot))
                .orElseThrow(() -> new AssertionError("B04 midpoint lacks station storage: " + stationId));
    }

    private static void mergeMaximums(Map<String, Double> destination, Map<String, Double> source) {
        source.forEach((commodity, mass) -> destination.merge(commodity, mass, Math::max));
    }

    private static String canonicalStartFingerprint(
            Map<String, Double> capacities,
            Map<String, Double> target,
            Map<String, Double> depot,
            double handlingRate) {
        StringBuilder canonical = new StringBuilder("B04.matched-start.v1\n");
        appendMap(canonical, "capacity", capacities);
        appendMap(canonical, "target", target);
        appendMap(canonical, "depot", depot);
        canonical.append("handlingRate=").append(Double.toHexString(handlingRate)).append('\n');
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void appendMap(StringBuilder canonical, String label, Map<String, Double> values) {
        new TreeMap<>(values).forEach((id, mass) -> canonical.append(label).append('|')
                .append(id).append('=').append(Double.toHexString(mass)).append('\n'));
    }

    private record ProductRequirements(
            String productId,
            double outputMassKg,
            String profileId,
            Map<String, Double> inputMassByCommodityKg) { }

    private record MatchedStart(
            Map<String, Double> capacitiesByStorageClassKg,
            Map<String, Double> targetCommodityMassByIdKg,
            Map<String, Double> depotCommodityMassByIdKg,
            double handlingRateKgPerSecond,
            String fingerprint) { }

    private record ContinuationResult(
            boolean transferAccepted,
            double transferredMassKg,
            boolean manufactureAccepted,
            String manufactureStatus,
            double outputMassKg,
            double energyConsumedJ,
            double workConsumedSeconds,
            double maintenanceWorkConsumedSeconds,
            double manufacturingDurationSeconds,
            int finalProductCount,
            double finalTargetHeavyMassKg,
            Stage18StationStorage.StationStorageSnapshot finalTarget,
            Stage18StationStorage.StationStorageSnapshot finalDepot) { }

    private record ScenarioResult(
            String packageId,
            String productId,
            String startFingerprint,
            boolean shortageRejectedWithoutMutation,
            boolean midpointByteStable,
            boolean directRestoredContinuationEqual,
            boolean recoveredManufactureAccepted,
            double heavyMassDeliveredKg,
            double handlingRateKgPerSecond,
            double manufacturingDurationSeconds,
            int finalProductCount) { }
}
