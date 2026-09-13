package com.spacesim.content;

import com.spacesim.content.Stage18FacilityCatalog.FacilityDefinition;
import com.spacesim.content.Stage18ManufacturingCatalog.ManufacturingInputDefinition;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.Stage18ResourceOntologyCatalog.CommodityDefinition;
import com.spacesim.content.Stage22IndustrialUnionProductionState.YardSeriesState;
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
import com.spacesim.persistence.Stage22IndustrialUnionProductionStateCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B02 full physical cold-start continuation through the accepted industrial authorities.
 *
 * <p>Both core packages begin with the same finite storage and handling envelope, an empty production
 * station and only their authored commodity bill at a separate depot. Ordinary Stage-18 logistics
 * moves half of every required input before an industrial checkpoint and the remainder afterwards.
 * The Industrial Union simultaneously pays half of its ordinary finite first-series retool burden,
 * persists that production sidecar, then pays the remainder before its first logistics-family unit
 * may proceed. Direct and restored continuations must produce the exact authored cargo module with
 * identical physical inventory and, for the Union, identical qualified-series state.</p>
 *
 * <p>No faction-local inventory, free manufactured stock, synthetic cold-start score or hidden
 * production multiplier is introduced. The existing B02 30-pair manufacturing/knowledge batches
 * remain the stochastic/mirrored evidence; this deterministic slice closes their previously explicit
 * L1-L4 logistics-to-production save-continuation boundary.</p>
 */
class Stage22CorePairColdStartPhysicalTrajectoryAcceptanceTest {
    private static final String EMPIRE_PRODUCT = "module.empire_cargo_secure_v1";
    private static final String UNION_PRODUCT = "module.industrial_union_cargo_section_v1";
    private static final String UNION_FAMILY = "ship_family.industrial_union.freight";
    private static final String ASSEMBLY_FACILITY = "facility.fabrication.assembly";
    private static final String TARGET_STATION = "station.m22_6.cold_start.target";
    private static final String DEPOT_STATION = "station.m22_6.cold_start.depot";
    private static final double EPSILON = 1e-7d;

    @Test
    void b02FiniteColdStartCarriesPhysicalInputsAndUnionRetoolAcrossMidpointSave() {
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        var products = Stage18ManufacturingProductRegistry.loadDefault()
                .withEngineeringCatalog(Stage22CorePairEngineeringCatalogLoader.loadDefault(), Provenance.STAGE22_AUTHORED);
        var empireCatalog = Stage22EmpireManufacturingCatalogLoader.loadDefault();
        var unionCatalog = Stage22IndustrialUnionManufacturingCatalogLoader.loadDefault();
        ProductRequirements empireRequirements = requirements(empireCatalog, products, EMPIRE_PRODUCT);
        ProductRequirements unionRequirements = requirements(unionCatalog, products, UNION_PRODUCT);
        CommonStart common = commonStart(ontology, products, empireRequirements, unionRequirements);
        InstalledFacilityState assembly = assemblyFacilityState();

        ScenarioResult empire = run(
                "empire",
                empireCatalog,
                empireRequirements,
                common,
                assembly,
                ontology,
                products,
                false);
        ScenarioResult union = run(
                "industrial_union",
                unionCatalog,
                unionRequirements,
                common,
                assembly,
                ontology,
                products,
                true);

        assertEquals(empire.capacityFingerprint(), union.capacityFingerprint(),
                "B02 core starts must use the same finite station/depot capacity envelope");
        assertEquals(empire.handlingRateKgPerSecond(), union.handlingRateKgPerSecond(), EPSILON,
                "B02 core starts must use the same physical cargo-handling rate");
        for (ScenarioResult result : List.of(empire, union)) {
            assertTrue(result.initialManufactureRejectedWithoutMutation());
            assertTrue(result.midpointIndustrialByteStable());
            assertTrue(result.directRestoredContinuationEqual());
            assertTrue(result.manufactureAccepted());
            assertEquals(1, result.finalProductCount());
            assertTrue(result.inputBillMassKg() > 0d);
            assertEquals(result.inputBillMassKg(), result.transferredInputMassKg(), EPSILON);
            assertTrue(result.finalResidualInputMassKg() <= EPSILON);
        }
        assertEquals(0L, empire.retoolWorkPaidSeconds());
        assertEquals(0L, empire.retoolEnergyPaidJ());
        assertTrue(union.retoolWorkPaidSeconds() > 0L);
        assertTrue(union.retoolEnergyPaidJ() > 0L);
        assertTrue(union.productionSidecarByteStable());
        assertTrue(union.unionSeriesQualified());

        LinkedHashMap<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("scenario", "B02");
        evidence.put("capacityFingerprint", common.capacityFingerprint());
        evidence.put("handlingRateKgPerSecond", common.handlingRateKgPerSecond());
        evidence.put("empire", empire);
        evidence.put("industrialUnion", union);
        Stage22CorePairEvidenceArchive.write(
                "B02-physical-cold-start-save-continuation",
                evidence,
                "Deterministic B02 L1-L4 continuation over the common Stage-18 storage/logistics/manufacturing authorities. Both packages start with identical finite capacity/handling infrastructure, zero authored finished-product stock and only their physical commodity bill at a depot. Half of every input crosses the ordinary logistics boundary before the Stage-18 industrial checkpoint and the remainder after restore. Industrial Union initial logistics-series qualification pays positive finite work/energy through its existing production sidecar, including a midpoint binary round trip, before the authored cargo module is manufactured. Direct/restored final inventory and Union series state are identical; no faction-local production or free stock exists.");
    }

    private static ScenarioResult run(
            String packageId,
            Stage18ManufacturingCatalog manufacturingCatalog,
            ProductRequirements requirements,
            CommonStart common,
            InstalledFacilityState assemblyState,
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products,
            boolean union) {
        Stage18StationStorage target = new Stage18StationStorage(
                ontology, products, TARGET_STATION,
                common.capacitiesByStorageClassKg(), Map.of(), Map.of());
        Stage18StationStorage depot = new Stage18StationStorage(
                ontology, products, DEPOT_STATION,
                common.capacitiesByStorageClassKg(), requirements.inputMassByCommodityKg(), Map.of());
        Stage18FacilityRuntime facilityRuntime = new Stage18FacilityRuntime(Stage18FacilityCatalogLoader.loadDefault());
        FacilityCapabilitySnapshot assembly = facilityRuntime.project(assemblyState);
        Stage18StationProductionBridge production = productionBridge(
                ontology, products, facilityRuntime, manufacturingCatalog);
        double manufactureSeconds = manufacturingDurationSeconds(
                requirements, manufacturingCatalog, products, assembly);

        var targetBefore = target.snapshot();
        var depotBefore = depot.snapshot();
        var rejected = production.manufactureProductAtStation(
                requirements.productId(), 1, target, assembly, manufactureSeconds);
        boolean rejectedWithoutMutation = rejected.status() == Stage18ManufacturingRuntime.Status.INSUFFICIENT_INPUT
                && targetBefore.equals(target.snapshot())
                && depotBefore.equals(depot.snapshot())
                && target.productCount(requirements.productId()) == 0;
        assertTrue(rejectedWithoutMutation,
                "B02 cold start must not manufacture from an empty production station");

        Stage18LogisticsRuntime logistics = new Stage18LogisticsRuntime(ontology, products);
        HandlingCapability handling = handling(common, ontology, requirements);
        double transferredBeforeCheckpoint = transferFraction(
                logistics, depot, target, requirements.inputMassByCommodityKg(), handling, 0.5d);
        assertTrue(transferredBeforeCheckpoint > 0d);
        assertEquals(0, target.productCount(requirements.productId()));

        Stage22IndustrialUnionProductionState unionCheckpoint = union
                ? unionRetoolMidpoint()
                : null;
        if (union) {
            YardSeriesState pending = unionCheckpoint.findYard(Stage22IndustrialUnionIndustrialProgram.YARD_ID);
            assertTrue(pending.retooling());
            assertTrue(pending.retoolWorkRemainingSeconds() > 0L);
            assertTrue(pending.retoolEnergyRemainingJ() > 0L);
            assertThrows(IllegalStateException.class,
                    () -> Stage22IndustrialUnionIndustrialProgram.modifierFor(pending, UNION_FAMILY),
                    "Union B02 manufacturing must remain series-gated at the midpoint");
        }

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
        byte[] industrialBytes = Stage18IndustrialStateCodec.encode(midpoint);
        Stage18IndustrialState restoredIndustrial = Stage18IndustrialStateCodec.decode(industrialBytes);
        boolean industrialByteStable = Arrays.equals(
                industrialBytes, Stage18IndustrialStateCodec.encode(restoredIndustrial));

        Stage22IndustrialUnionProductionState restoredUnion = null;
        boolean unionByteStable = !union;
        if (union) {
            byte[] unionBytes = Stage22IndustrialUnionProductionStateCodec.encode(unionCheckpoint);
            restoredUnion = Stage22IndustrialUnionProductionStateCodec.decode(unionBytes);
            unionByteStable = Arrays.equals(
                    unionBytes, Stage22IndustrialUnionProductionStateCodec.encode(restoredUnion));
        }

        ContinuationResult direct = continueFrom(
                manufacturingCatalog,
                requirements,
                common,
                assemblyState,
                target,
                depot,
                unionCheckpoint,
                ontology,
                products);
        ContinuationResult restored = continueFrom(
                manufacturingCatalog,
                requirements,
                common,
                restoredIndustrial.facilities().stream()
                        .filter(row -> row.stationId().equals(TARGET_STATION))
                        .findFirst().orElseThrow().state(),
                restoreStorage(restoredIndustrial, TARGET_STATION, ontology, products),
                restoreStorage(restoredIndustrial, DEPOT_STATION, ontology, products),
                restoredUnion,
                ontology,
                products);
        assertEquals(direct, restored,
                "B02 direct/restored cold-start continuation must converge to the same physical state");

        double inputBillMass = requirements.inputMassByCommodityKg().values().stream()
                .mapToDouble(Double::doubleValue).sum();
        long retoolWork = union ? unionRetoolTotalWork() : 0L;
        long retoolEnergy = union ? unionRetoolTotalEnergy() : 0L;
        return new ScenarioResult(
                packageId,
                requirements.productId(),
                common.capacityFingerprint(),
                common.handlingRateKgPerSecond(),
                rejectedWithoutMutation,
                industrialByteStable,
                unionByteStable,
                direct.equals(restored),
                direct.manufactureAccepted(),
                direct.finalProductCount(),
                inputBillMass,
                transferredBeforeCheckpoint + direct.transferredAfterCheckpointKg(),
                direct.finalResidualInputMassKg(),
                retoolWork,
                retoolEnergy,
                direct.unionSeriesQualified());
    }

    private static ContinuationResult continueFrom(
            Stage18ManufacturingCatalog manufacturingCatalog,
            ProductRequirements requirements,
            CommonStart common,
            InstalledFacilityState assemblyState,
            Stage18StationStorage target,
            Stage18StationStorage depot,
            Stage22IndustrialUnionProductionState unionState,
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products) {
        Stage18LogisticsRuntime logistics = new Stage18LogisticsRuntime(ontology, products);
        HandlingCapability handling = handling(common, ontology, requirements);
        double transferred = transferRemaining(
                logistics, depot, target, requirements.inputMassByCommodityKg(), handling);

        Stage22IndustrialUnionProductionState completedUnion = unionState;
        boolean unionQualified = unionState == null;
        if (unionState != null) {
            YardSeriesState pending = unionState.findYard(Stage22IndustrialUnionIndustrialProgram.YARD_ID);
            long remainingWork = pending.retoolWorkRemainingSeconds();
            long remainingEnergy = pending.retoolEnergyRemainingJ();
            YardSeriesState paid = Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(
                    pending, remainingWork, remainingEnergy);
            YardSeriesState qualified = Stage22IndustrialUnionIndustrialProgram.completeRetool(paid);
            var modifier = Stage22IndustrialUnionIndustrialProgram.modifierFor(qualified, UNION_FAMILY);
            assertTrue(modifier.workMultiplier() > 0d);
            assertTrue(modifier.energyMultiplier() > 0d);
            completedUnion = unionState.withYard(qualified);
            unionQualified = !completedUnion.findYard(Stage22IndustrialUnionIndustrialProgram.YARD_ID).retooling();
        }

        Stage18FacilityRuntime facilityRuntime = new Stage18FacilityRuntime(Stage18FacilityCatalogLoader.loadDefault());
        FacilityCapabilitySnapshot assembly = facilityRuntime.project(assemblyState);
        Stage18StationProductionBridge production = productionBridge(
                ontology, products, facilityRuntime, manufacturingCatalog);
        double manufactureSeconds = manufacturingDurationSeconds(
                requirements, manufacturingCatalog, products, assembly);
        var manufactured = production.manufactureProductAtStation(
                requirements.productId(), 1, target, assembly, manufactureSeconds);
        assertTrue(manufactured.accepted());

        if (completedUnion != null) {
            YardSeriesState completed = Stage22IndustrialUnionIndustrialProgram.recordCompletedUnit(
                    completedUnion.findYard(Stage22IndustrialUnionIndustrialProgram.YARD_ID), UNION_FAMILY);
            completedUnion = completedUnion.withYard(completed);
        }

        double residualInput = requirements.inputMassByCommodityKg().keySet().stream()
                .mapToDouble(target::commodityMassKg).sum()
                + requirements.inputMassByCommodityKg().keySet().stream()
                        .mapToDouble(depot::commodityMassKg).sum();
        String productionFingerprint = completedUnion == null
                ? "empire-no-series-sidecar"
                : java.util.HexFormat.of().formatHex(sha256(
                        Stage22IndustrialUnionProductionStateCodec.encode(completedUnion)));
        return new ContinuationResult(
                transferred,
                manufactured.accepted(),
                target.productCount(requirements.productId()),
                residualInput,
                target.snapshot(),
                depot.snapshot(),
                unionQualified,
                productionFingerprint);
    }

    private static double transferFraction(
            Stage18LogisticsRuntime logistics,
            Stage18StationStorage source,
            Stage18StationStorage destination,
            Map<String, Double> bill,
            HandlingCapability handling,
            double fraction) {
        double total = 0d;
        for (Map.Entry<String, Double> input : new TreeMap<>(bill).entrySet()) {
            double mass = input.getValue() * fraction;
            var result = logistics.transferCommodity(
                    source,
                    destination,
                    input.getKey(),
                    mass,
                    handling,
                    handling.openInterval(mass / handling.massRateKgPerSecond() + 1d));
            assertTrue(result.transferred(), "B02 midpoint transfer rejected: " + input.getKey());
            total += result.transferredMassKg();
        }
        return total;
    }

    private static double transferRemaining(
            Stage18LogisticsRuntime logistics,
            Stage18StationStorage source,
            Stage18StationStorage destination,
            Map<String, Double> bill,
            HandlingCapability handling) {
        double total = 0d;
        for (Map.Entry<String, Double> input : new TreeMap<>(bill).entrySet()) {
            double remaining = source.commodityMassKg(input.getKey());
            if (remaining <= EPSILON) continue;
            var result = logistics.transferCommodity(
                    source,
                    destination,
                    input.getKey(),
                    remaining,
                    handling,
                    handling.openInterval(remaining / handling.massRateKgPerSecond() + 1d));
            assertTrue(result.transferred(), "B02 continuation transfer rejected: " + input.getKey());
            total += result.transferredMassKg();
        }
        return total;
    }

    private static HandlingCapability handling(
            CommonStart common,
            Stage18ResourceOntologyCatalog ontology,
            ProductRequirements requirements) {
        TreeSet<String> classes = new TreeSet<>();
        for (String commodityId : requirements.inputMassByCommodityKg().keySet()) {
            CommodityDefinition commodity = ontology.findCommodity(commodityId);
            if (commodity == null) throw new AssertionError("B02 input absent from ontology: " + commodityId);
            classes.add(commodity.storageClassId());
        }
        return new HandlingCapability(
                "handling.m22_6.cold_start.common",
                Set.copyOf(classes),
                common.handlingRateKgPerSecond(),
                common.maxUnitMassKg());
    }

    private static CommonStart commonStart(
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products,
            ProductRequirements empire,
            ProductRequirements union) {
        TreeSet<String> storageClasses = new TreeSet<>();
        for (String commodityId : unionOf(empire.inputMassByCommodityKg(), union.inputMassByCommodityKg()).keySet()) {
            CommodityDefinition commodity = ontology.findCommodity(commodityId);
            if (commodity == null) throw new AssertionError("B02 input absent from ontology: " + commodityId);
            storageClasses.add(commodity.storageClassId());
        }
        storageClasses.add(products.findProduct(empire.productId()).storageClassId());
        storageClasses.add(products.findProduct(union.productId()).storageClassId());

        double totalMass = empire.inputMassByCommodityKg().values().stream().mapToDouble(Double::doubleValue).sum()
                + union.inputMassByCommodityKg().values().stream().mapToDouble(Double::doubleValue).sum()
                + empire.outputMassKg() + union.outputMassKg();
        double capacityPerClass = totalMass * 4d + 1d;
        TreeMap<String, Double> capacities = new TreeMap<>();
        storageClasses.forEach(storageClass -> capacities.put(storageClass, capacityPerClass));
        double handlingRate = Math.max(empire.outputMassKg(), union.outputMassKg()) / 60d;
        double maxUnitMass = Math.max(empire.outputMassKg(), union.outputMassKg());
        if (!(handlingRate > 0d) || !Double.isFinite(handlingRate)
                || !(maxUnitMass > 0d) || !Double.isFinite(maxUnitMass)) {
            throw new AssertionError("B02 common handling envelope is invalid");
        }
        return new CommonStart(
                Map.copyOf(capacities),
                handlingRate,
                maxUnitMass,
                fingerprint(capacities, handlingRate, maxUnitMass));
    }

    private static Map<String, Double> unionOf(Map<String, Double> first, Map<String, Double> second) {
        TreeMap<String, Double> result = new TreeMap<>(first);
        second.forEach((id, mass) -> result.merge(id, mass, Math::max));
        return result;
    }

    private static ProductRequirements requirements(
            Stage18ManufacturingCatalog catalog,
            Stage18ManufacturingProductRegistry products,
            String productId) {
        var product = products.findProduct(productId);
        var binding = catalog.findProductBinding(productId);
        if (product == null || binding == null) {
            throw new AssertionError("B02 authored product lacks ordinary Stage-18 registration: " + productId);
        }
        var profile = catalog.findProductProfile(binding.profileId());
        if (profile == null) throw new AssertionError("B02 authored product lacks manufacturing profile: " + productId);
        TreeMap<String, Double> inputs = new TreeMap<>();
        for (ManufacturingInputDefinition input : profile.inputs()) {
            double mass = product.unitMassKg() * input.fractionOfOutputMass();
            if (!(mass > 0d) || !Double.isFinite(mass)) {
                throw new AssertionError("B02 authored input mass is invalid: " + input.commodityId());
            }
            inputs.put(input.commodityId(), mass);
        }
        if (inputs.isEmpty()) throw new AssertionError("B02 authored product has no physical input bill: " + productId);
        return new ProductRequirements(productId, product.unitMassKg(), Map.copyOf(inputs));
    }

    private static InstalledFacilityState assemblyFacilityState() {
        FacilityDefinition definition = Stage18FacilityCatalogLoader.loadDefault().findFacility(ASSEMBLY_FACILITY);
        if (definition == null) throw new AssertionError("B02 assembly facility missing from Stage-18 catalog");
        return new InstalledFacilityState(
                "facility.instance.m22_6.cold_start.assembly",
                definition.id(),
                1d,
                definition.ratedProcessPowerW(),
                definition.ratedProcessPowerW() * definition.heatRejectionWPerProcessW(),
                definition.requiredLaborUnitsAtFullRate(),
                definition.maintenanceWorkRate(),
                "location.orbital_station",
                true);
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
            throw new AssertionError("B02 manufacturing duration is invalid");
        }
        return seconds + 1d;
    }

    private static Stage18StationStorage restoreStorage(
            Stage18IndustrialState state,
            String stationId,
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products) {
        return state.stationStorages().stream()
                .filter(snapshot -> snapshot.stationId().equals(stationId))
                .findFirst()
                .map(snapshot -> Stage18StationStorage.restore(ontology, products, snapshot))
                .orElseThrow(() -> new AssertionError("B02 midpoint lacks storage: " + stationId));
    }

    private static Stage22IndustrialUnionProductionState unionRetoolMidpoint() {
        YardSeriesState pending = Stage22IndustrialUnionIndustrialProgram.beginRetool(
                Stage22IndustrialUnionProductionState.unqualifiedYard(Stage22IndustrialUnionIndustrialProgram.YARD_ID),
                UNION_FAMILY);
        return new Stage22IndustrialUnionProductionState(
                Stage22IndustrialUnionProductionState.CURRENT_VERSION,
                Stage22IndustrialUnionProductionState.STABLE_FACTION_ID,
                Stage22IndustrialUnionPackageValidator.validateDefault().packageFingerprint(),
                0L,
                List.of(Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(
                        pending,
                        Math.max(1L, pending.retoolWorkRemainingSeconds() / 2L),
                        Math.max(1L, pending.retoolEnergyRemainingJ() / 2L))));
    }

    private static long unionRetoolTotalWork() {
        return Stage22IndustrialUnionIndustrialProgram.beginRetool(
                Stage22IndustrialUnionProductionState.unqualifiedYard(Stage22IndustrialUnionIndustrialProgram.YARD_ID),
                UNION_FAMILY).retoolWorkRemainingSeconds();
    }

    private static long unionRetoolTotalEnergy() {
        return Stage22IndustrialUnionIndustrialProgram.beginRetool(
                Stage22IndustrialUnionProductionState.unqualifiedYard(Stage22IndustrialUnionIndustrialProgram.YARD_ID),
                UNION_FAMILY).retoolEnergyRemainingJ();
    }

    private static String fingerprint(
            Map<String, Double> capacities,
            double handlingRate,
            double maxUnitMass) {
        StringBuilder canonical = new StringBuilder("B02.physical-cold-start.v1\n");
        new TreeMap<>(capacities).forEach((id, mass) -> canonical.append(id).append('=')
                .append(Double.toHexString(mass)).append('\n'));
        canonical.append("handlingRate=").append(Double.toHexString(handlingRate)).append('\n');
        canonical.append("maxUnitMass=").append(Double.toHexString(maxUnitMass)).append('\n');
        return java.util.HexFormat.of().formatHex(sha256(
                canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record ProductRequirements(
            String productId,
            double outputMassKg,
            Map<String, Double> inputMassByCommodityKg) { }

    private record CommonStart(
            Map<String, Double> capacitiesByStorageClassKg,
            double handlingRateKgPerSecond,
            double maxUnitMassKg,
            String capacityFingerprint) { }

    private record ContinuationResult(
            double transferredAfterCheckpointKg,
            boolean manufactureAccepted,
            int finalProductCount,
            double finalResidualInputMassKg,
            Stage18StationStorage.StationStorageSnapshot finalTarget,
            Stage18StationStorage.StationStorageSnapshot finalDepot,
            boolean unionSeriesQualified,
            String productionStateFingerprint) { }

    private record ScenarioResult(
            String packageId,
            String productId,
            String capacityFingerprint,
            double handlingRateKgPerSecond,
            boolean initialManufactureRejectedWithoutMutation,
            boolean midpointIndustrialByteStable,
            boolean productionSidecarByteStable,
            boolean directRestoredContinuationEqual,
            boolean manufactureAccepted,
            int finalProductCount,
            double inputBillMassKg,
            double transferredInputMassKg,
            double finalResidualInputMassKg,
            long retoolWorkPaidSeconds,
            long retoolEnergyPaidJ,
            boolean unionSeriesQualified) { }
}
