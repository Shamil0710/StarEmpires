package com.spacesim.economy;

import com.spacesim.content.Stage18ExtractionCatalog;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionMethodDefinition;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.content.Stage18ExtractionCatalogLoader;
import com.spacesim.content.Stage18FacilityCatalog;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18FacilityConstructionCatalog;
import com.spacesim.content.Stage18FacilityConstructionCatalogLoader;
import com.spacesim.content.Stage18ManufacturingCatalog;
import com.spacesim.content.Stage18ManufacturingCatalog.ComponentRecipeDefinition;
import com.spacesim.content.Stage18ManufacturingCatalog.ProductBindingDefinition;
import com.spacesim.content.Stage18ManufacturingCatalog.ProductProfileDefinition;
import com.spacesim.content.Stage18ManufacturingCatalogLoader;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.ProductDefinition;
import com.spacesim.content.Stage18RefiningCatalog;
import com.spacesim.content.Stage18RefiningCatalog.RefiningRecipeDefinition;
import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyCatalog.CommodityKind;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18ShipConsumableCatalogLoader;
import com.spacesim.content.Stage18ShipyardCatalog;
import com.spacesim.content.Stage18ShipyardCatalogLoader;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.ship.ShipyardIndustrialCatalogLoader;
import com.spacesim.economy.Stage18ExtractionRuntime.ExtractionCapability;
import com.spacesim.economy.Stage18ExtractionRuntime.ExtractionResult;
import com.spacesim.economy.Stage18ExtractionRuntime.PhysicalSourceState;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.ConstructionCapability;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.ConstructionOrderSnapshot;
import com.spacesim.economy.Stage18FacilityConstructionRuntime.WorkResult;
import com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.economy.Stage18LogisticsRuntime.HandlingCapability;
import com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingCapability;
import com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingInventory;
import com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingResult;
import com.spacesim.economy.Stage18RefiningRuntime.PhysicalMaterialStore;
import com.spacesim.economy.Stage18RefiningRuntime.RefiningCapability;
import com.spacesim.economy.Stage18RefiningRuntime.RefiningResult;
import com.spacesim.economy.Stage18ShipyardRuntime.InstalledYardState;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.Stage18IndustrialContentFingerprint;
import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage18IndustrialState.FacilityInstallationSnapshot;
import com.spacesim.persistence.Stage18IndustrialState.PhysicalSourceSnapshot;
import com.spacesim.persistence.Stage18IndustrialState.ProcessKind;
import com.spacesim.persistence.Stage18IndustrialState.ProcessOrderSnapshot;
import com.spacesim.persistence.Stage18IndustrialState.YardInstallationSnapshot;
import com.spacesim.persistence.Stage18IndustrialStateCodec;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipyardEngineeringService;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Deterministic Stage-18I hand-authored minimal industrial-universe acceptance harness.
 *
 * <p>The scenario begins with finite natural occurrences and explicit physical facilities, not
 * credits or hidden market supply. It runs the ordinary Stage-18B-H runtime chain through extraction,
 * logistics, refining, three distinct component families, module/ammunition manufacture, shipyard
 * build, physical reaction-mass servicing, repair, bounded wreck salvage/recycling and physical
 * facility construction. A mid-construction industrial checkpoint is encoded/decoded and both
 * continuations must produce byte-identical final industrial state.</p>
 */
public final class Stage18IndustrialAcceptanceHarness {
    private Stage18IndustrialAcceptanceHarness() {
        throw new AssertionError("No instances");
    }

    /**
     * Runs the complete deterministic Stage-18 industrial acceptance scenario.
     *
     * @return immutable acceptance metrics
     */
    public static AcceptanceReport run() {
        return new Scenario().run();
    }

    /**
     * Final metrics proving the Stage-18 exit contract.
     *
     * @param contentFingerprint industrial semantic fingerprint
     * @param waterProducedKg physically produced purified water
     * @param reactorFuelProducedKg physically produced reactor fuel
     * @param reactionMassLoadedKg station commodity mass physically loaded into the ship drive
     * @param heavyComponentsProducedKg cumulative heavy-component production
     * @param electricalComponentsProducedKg cumulative electrical-component production
     * @param precisionComponentsProducedKg cumulative precision-component production
     * @param strategicSourceMassRemovedKg depleted strategic-metal source mass
     * @param ammunitionProducedUnits countable physical ammunition produced
     * @param ammunitionTransferredUnits countable ammunition physically transferred to a depot
     * @param builtShipId persistent identity of the built ship
     * @param repairInputMassKg physical repair material/component mass consumed
     * @param wreckConstructedMassKg represented constructed hull/module mass before destruction loss
     * @param wreckAccessibleMassKg bounded pre-recycling wreck mass
     * @param recycledMassKg recovered mass after ordinary recycling loss
     * @param constructedFacilityDefinitionId facility completed from physical delivery/work
     * @param saveLoadEquivalent whether checkpoint replay produced identical final industrial bytes
     * @param finalIndustrialStateSha256 deterministic hash of the final industrial payload
     */
    public record AcceptanceReport(
            String contentFingerprint,
            double waterProducedKg,
            double reactorFuelProducedKg,
            double reactionMassLoadedKg,
            double heavyComponentsProducedKg,
            double electricalComponentsProducedKg,
            double precisionComponentsProducedKg,
            double strategicSourceMassRemovedKg,
            int ammunitionProducedUnits,
            int ammunitionTransferredUnits,
            EntityId builtShipId,
            double repairInputMassKg,
            double wreckConstructedMassKg,
            double wreckAccessibleMassKg,
            double recycledMassKg,
            String constructedFacilityDefinitionId,
            boolean saveLoadEquivalent,
            String finalIndustrialStateSha256) {
        /**
         * Validates the identity-bearing report fields.
         *
         * @param contentFingerprint industrial semantic fingerprint
         * @param waterProducedKg produced purified-water mass
         * @param reactorFuelProducedKg produced reactor-fuel mass
         * @param reactionMassLoadedKg loaded reaction-mass commodity mass
         * @param heavyComponentsProducedKg produced heavy-component mass
         * @param electricalComponentsProducedKg produced electrical-component mass
         * @param precisionComponentsProducedKg produced precision-component mass
         * @param strategicSourceMassRemovedKg depleted strategic-source mass
         * @param ammunitionProducedUnits produced ammunition count
         * @param ammunitionTransferredUnits transferred ammunition count
         * @param builtShipId built ship identity
         * @param repairInputMassKg consumed repair input mass
         * @param wreckConstructedMassKg represented constructed wreck mass
         * @param wreckAccessibleMassKg accessible wreck mass
         * @param recycledMassKg recovered recycling output mass
         * @param constructedFacilityDefinitionId completed facility definition
         * @param saveLoadEquivalent deterministic replay result
         * @param finalIndustrialStateSha256 final payload hash
         */
        public AcceptanceReport {
            Objects.requireNonNull(contentFingerprint, "contentFingerprint");
            Objects.requireNonNull(builtShipId, "builtShipId");
            Objects.requireNonNull(constructedFacilityDefinitionId, "constructedFacilityDefinitionId");
            Objects.requireNonNull(finalIndustrialStateSha256, "finalIndustrialStateSha256");
        }
    }

    private static final class Scenario {
        private static final double EPSILON = 1e-6d;
        private static final double SOURCE_RESERVE_KG = 5_000_000_000d;
        private static final double STORAGE_CAPACITY_KG = 2_000_000_000d;
        private static final String MINE = "station.acceptance.mine";
        private static final String VOLATILE = "station.acceptance.volatile";
        private static final String INDUSTRY = "station.acceptance.industry";
        private static final String DEPOT = "station.acceptance.depot";

        private final Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        private final Stage18ExtractionCatalog extractionCatalog = Stage18ExtractionCatalogLoader.loadDefault();
        private final Stage18RefiningCatalog refiningCatalog = Stage18RefiningCatalogLoader.loadDefault();
        private final Stage18ManufacturingCatalog manufacturingCatalog = Stage18ManufacturingCatalogLoader.loadDefault();
        private final Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault();
        private final Stage18FacilityCatalog facilityCatalog = Stage18FacilityCatalogLoader.loadDefault();
        private final Stage18ShipyardCatalog shipyardCatalog = Stage18ShipyardCatalogLoader.loadDefault();
        private final Stage18FacilityConstructionCatalog constructionCatalog =
                Stage18FacilityConstructionCatalogLoader.loadDefault();
        private final ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();

        private final Stage18ExtractionRuntime extraction = new Stage18ExtractionRuntime(ontology, extractionCatalog);
        private final Stage18RefiningRuntime refining = new Stage18RefiningRuntime(ontology, refiningCatalog);
        private final Stage18ManufacturingRuntime manufacturing =
                new Stage18ManufacturingRuntime(ontology, manufacturingCatalog, products);
        private final Stage18FacilityRuntime facilityRuntime = new Stage18FacilityRuntime(facilityCatalog);
        private final Stage18LogisticsRuntime logistics = new Stage18LogisticsRuntime(ontology, products);
        private final Stage18ShipyardRuntime shipyard = new Stage18ShipyardRuntime(shipyardCatalog, ontology, products);
        private final Stage18SalvageRuntime salvage = new Stage18SalvageRuntime(
                ontology, shipyardCatalog, manufacturingCatalog, products);
        private final Stage18FacilityConstructionRuntime construction = new Stage18FacilityConstructionRuntime(
                constructionCatalog, facilityCatalog, ontology);
        private final Stage18ShipConsumableService shipConsumables = new Stage18ShipConsumableService(
                Stage18ShipConsumableCatalogLoader.loadDefault(), engineering);
        private final ShipyardEngineeringService engineeringService = new ShipyardEngineeringService(
                engineering, ShipyardIndustrialCatalogLoader.loadDefault(engineering));

        private final Stage18StationStorage mine = newStorage(MINE);
        private final Stage18StationStorage volatileSite = newStorage(VOLATILE);
        private final Stage18StationStorage industry = newStorage(INDUSTRY);
        private final Stage18StationStorage depot = newStorage(DEPOT);
        private final HandlingCapability handling = new HandlingCapability(
                "handling.acceptance", allStorageClasses(), 5_000_000d, 5_000_000d);

        private final Map<String, PhysicalSourceState> sources = new TreeMap<>();
        private final Map<String, String> facilityOwners = new HashMap<>();
        private final List<InstalledFacilityState> installedFacilities = new ArrayList<>();
        private final List<FacilityCapabilitySnapshot> processFacilities = new ArrayList<>();
        private final List<FacilityCapabilitySnapshot> fabricationFacilities = new ArrayList<>();
        private final List<FacilityCapabilitySnapshot> constructionFacilities = new ArrayList<>();

        private final FacilityCapabilitySnapshot asteroidFacility;
        private final FacilityCapabilitySnapshot volatileFacility;
        private final FacilityCapabilitySnapshot recyclingFacility;
        private final RefiningCapability refiningNetwork;
        private final ManufacturingCapability manufacturingNetwork;
        private final InstalledYardState installedYard;
        private final Stage18ShipyardRuntime.YardCapabilitySnapshot yardCapability;

        private double heavyProduced;
        private double electricalProduced;
        private double precisionProduced;

        private Scenario() {
            seedSources();
            asteroidFacility = install(MINE, "facility.acceptance.asteroid", "facility.extraction.asteroid", "location.free_body");
            volatileFacility = install(VOLATILE, "facility.acceptance.volatile", "facility.processing.volatiles", "location.orbital_station");

            addProcess("facility.processing.bulk_refinery");
            addProcess("facility.processing.advanced_materials");
            addProcess("facility.processing.chemical");
            addProcess("facility.processing.volatiles");
            recyclingFacility = addProcess("facility.processing.recycling");

            addFabrication("facility.fabrication.heavy");
            addFabrication("facility.fabrication.electrical");
            addFabrication("facility.fabrication.precision");
            addFabrication("facility.fabrication.assembly");
            addFabrication("facility.fabrication.ordnance");

            refiningNetwork = Stage18FacilityCapabilityNetwork.refining(
                    "network.acceptance.refining", processFacilities);
            manufacturingNetwork = Stage18FacilityCapabilityNetwork.manufacturing(
                    "network.acceptance.manufacturing", fabricationFacilities);

            var archetype = Stage18StationInfrastructureCatalogLoader.loadDefault()
                    .findArchetype("station.infrastructure.industrial_station");
            Stage18StationIndustrialNode yardNode = Stage18StationIndustrialNode.instantiate(
                    INDUSTRY, "location.orbital_station", archetype, ontology, products);
            List<FacilityCapabilitySnapshot> support = new ArrayList<>();
            for (Stage18StationIndustrialNode.InstalledFacilityReference reference : yardNode.installedFacilities()) {
                if (reference.facilityDefinitionId().equals("facility.fabrication.heavy")
                        || reference.facilityDefinitionId().equals("facility.fabrication.assembly")) {
                    support.add(install(
                            INDUSTRY,
                            reference.facilityInstanceId(),
                            reference.facilityDefinitionId(),
                            "location.orbital_station"));
                }
            }
            installedYard = new InstalledYardState(
                    "yard.acceptance.escort", "yard.orbital_escort_v1",
                    1d, 1_200_000_000d, 12d, 500, 500, true);
            yardCapability = shipyard.projectYard(installedYard, yardNode, support);
            require(yardCapability.active(), "yard projection", yardCapability.status());
        }

        private AcceptanceReport run() {
            double waterBefore = industry.commodityMassKg("commodity.material.purified_water");
            ensureCommodity("commodity.material.purified_water", waterBefore + 100_000d);
            double waterProduced = industry.commodityMassKg("commodity.material.purified_water") - waterBefore;

            double fuelBefore = industry.commodityMassKg("commodity.consumable.reactor_fuel");
            ensureCommodity("commodity.consumable.reactor_fuel", fuelBefore + 10_000d);
            double reactorFuelProduced = industry.commodityMassKg("commodity.consumable.reactor_fuel") - fuelBefore;

            ensureCommodity("commodity.component.heavy_components", 25_000d);
            ensureCommodity("commodity.component.electrical_components", 25_000d);
            ensureCommodity("commodity.component.precision_components", 25_000d);

            ensureProduct("ammo.rail_dart_150kg_v1", 10);
            int ammunitionProduced = industry.productCount("ammo.rail_dart_150kg_v1");
            Stage18LogisticsRuntime.TransferResult ammoTransfer = transferProduct(
                    industry, depot, "ammo.rail_dart_150kg_v1", 5);
            require(ammoTransfer.transferred(), "ammunition logistics", ammoTransfer.status());

            InstalledFit fit = InstalledFit.fromDemonstrator(
                    engineering.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
            for (InstalledModuleDefinition assignment : fit.installedModules()) {
                ensureProduct(assignment.moduleId(), 1);
            }
            for (Stage18ShipyardCatalog.PhysicalInputDefinition input
                    : shipyardCatalog.findHullProfile(fit.hullId()).buildInputsKg()) {
                ensureCommodity(input.commodityId(), industry.commodityMassKg(input.commodityId()) + input.massKg());
            }
            ShipyardEngineeringService.WorkPlan buildPlan = engineeringService.planBuild(
                    fit, yardCapability.plannerCapability());
            require(buildPlan.feasibility().feasible(), "shipyard build plan", buildPlan.feasibility().issues());
            var buildBudget = yardCapability.openInterval(
                    buildPlan.requirements().totalWorkSeconds() / yardCapability.plannerCapability().workRate() + 1d);
            Stage18ShipyardRuntime.SettlementResult buildSettlement = shipyard.settleBuild(
                    buildPlan, industry, yardCapability, buildBudget);
            require(buildSettlement.settled(), "physical ship build", buildSettlement.status());
            EntityId shipId = new EntityId(18_001L);
            ShipyardEngineeringService.BuildCompletion build = engineeringService.completeBuild(
                    shipId, buildPlan, buildSettlement.compatibilitySettlement());

            ensureCommodity("commodity.material.purified_water", 20_000d);
            Stage18ShipConsumableService.LoadResult reactionMass = shipConsumables.load(
                    "ship_consumable.reaction_mass.escort_water_v1",
                    "core_drive",
                    20_000d,
                    build.fit(),
                    ConsumableState.empty(),
                    industry);
            require(reactionMass.committed(), "physical reaction-mass loading", reactionMass.status());

            ShipDamageRuntime.Snapshot repairDamage = damage(Map.of("core_drive", 0.7d), 0.8d, 1d, 1d);
            ShipyardEngineeringService.WorkPlan repairPlan = engineeringService.planRepair(
                    shipId, build.fit(), reactionMass.consumables(), repairDamage, yardCapability.plannerCapability());
            ensureRepairInputs(build.fit(), repairDamage);
            var repairBudget = yardCapability.openInterval(
                    repairPlan.requirements().totalWorkSeconds() / yardCapability.plannerCapability().workRate() + 1d);
            Stage18ShipyardRuntime.SettlementResult repairSettlement = shipyard.settleRepair(
                    repairPlan, repairDamage, industry, yardCapability, repairBudget);
            require(repairSettlement.settled(), "physical repair", repairSettlement.status());
            ShipyardEngineeringService.RepairCompletion repair = engineeringService.completeRepair(
                    repairPlan, repairSettlement.compatibilitySettlement());
            require(repair.assetId().equals(shipId), "repair identity", repair.assetId());
            double repairMass = repairSettlement.consumedCommodityMassKg().values().stream()
                    .mapToDouble(Double::doubleValue).sum();

            ShipDamageRuntime.Snapshot destruction = damage(
                    Map.of("core_reactor", 0.25d, "core_drive", 0.3d, "utility_sensor", 0.4d,
                            "utility_thermal", 0.5d, "weapon_spinal", 0.2d),
                    0.2d, 0.45d, 0.1d);
            Stage18SalvageRuntime.WreckSalvage wreck = salvage.deriveShipWreck(
                    "wreck.acceptance.escort", build.fit(), destruction);
            Stage18SalvageRuntime.SalvageStream structural = wreck.streams().stream()
                    .filter(value -> value.commodityId().equals("commodity.material.structural_alloy"))
                    .findFirst().orElseThrow();
            PhysicalSourceState salvageSource = structural.toPhysicalSource();
            double requestedSalvage = Math.min(10_000d, structural.accessibleMassKg());
            ExtractionCapability recycling = Stage18FacilityCapabilityNetwork.extraction(
                    "network.acceptance.recycling", List.of(recyclingFacility));
            ExtractionMethodDefinition salvageMethod = extractionCatalog.findMethod("extraction.salvage_recovery");
            var recycleCargo = new Stage18ExtractionRuntime.PhysicalCargoStore(ontology, capacityMap(), Map.of());
            ExtractionResult recycled = extraction.extract(
                    salvageSource,
                    salvageMethod.id(),
                    requestedSalvage,
                    recycling,
                    recycling.openInterval(extractionDuration(requestedSalvage, salvageMethod, recycling)),
                    recycleCargo);
            require(recycled.committed(), "bounded recycling", recycled.status());
            require(recycled.outputMassStoredKg() <= structural.accessibleMassKg() + EPSILON,
                    "recycling mass bound", recycled.outputMassStoredKg());

            ConstructionOrderSnapshot partial = createPartialConstruction();
            ProcessOrderSnapshot processOrder = createReservedProcessOrder();
            Stage18IndustrialState checkpoint = snapshot(100L, industry, partial, processOrder);
            Stage18IndustrialState restored = Stage18IndustrialStateCodec.decode(
                    Stage18IndustrialStateCodec.encode(checkpoint));
            require(checkpoint.equals(restored), "industrial checkpoint roundtrip", "state mismatch");

            Continuation originalFinal = continueConstruction(partial, industry, processOrder, 101L);
            Stage18StationStorage restoredIndustry = restoreStorage(restored, INDUSTRY);
            Continuation restoredFinal = continueConstruction(
                    restored.constructionOrders().get(0), restoredIndustry, restored.processOrders().get(0), 101L);
            boolean equivalent = Arrays.equals(originalFinal.bytes(), restoredFinal.bytes());
            require(equivalent, "save/load replay equivalence", "final bytes differ");

            double strategicRemoved = SOURCE_RESERVE_KG
                    - sources.get("commodity.feedstock.strategic_metal_ore").remainingAccessibleMassKg();
            return new AcceptanceReport(
                    Stage18IndustrialContentFingerprint.current(),
                    waterProduced,
                    reactorFuelProduced,
                    reactionMass.loadedMassKg(),
                    heavyProduced,
                    electricalProduced,
                    precisionProduced,
                    strategicRemoved,
                    ammunitionProduced,
                    ammoTransfer.transferredUnitCount(),
                    shipId,
                    repairMass,
                    wreck.totalConstructedMassKg(),
                    wreck.totalAccessibleMassKg(),
                    recycled.outputMassStoredKg(),
                    originalFinal.installedFacilityDefinitionId(),
                    equivalent,
                    sha256(originalFinal.bytes()));
        }

        private void ensureCommodity(String id, double targetKg) {
            if (industry.commodityMassKg(id) + EPSILON >= targetKg) {
                return;
            }
            Stage18ResourceOntologyCatalog.CommodityDefinition commodity = ontology.findCommodity(id);
            if (commodity == null) {
                throw new IllegalArgumentException("Unknown acceptance commodity: " + id);
            }
            if (commodity.kind() == CommodityKind.EXTRACTED_FEEDSTOCK) {
                ensureFeedstock(id, targetKg);
            } else if (commodity.kind() == CommodityKind.COMPONENT_FAMILY) {
                ensureComponent(id, targetKg);
            } else {
                ensureRefined(id, targetKg);
            }
        }

        private void ensureFeedstock(String id, double targetKg) {
            double missing = targetKg - industry.commodityMassKg(id);
            if (missing <= EPSILON) {
                return;
            }
            SourceSite site = sourceSite(id);
            if (site.storage().commodityMassKg(id) + EPSILON < missing) {
                double desiredOutput = missing - site.storage().commodityMassKg(id);
                ExtractionMethodDefinition method = extractionCatalog.findMethod(site.methodId());
                double sourceMass = desiredOutput / method.recoveryFraction();
                ExtractionCapability capability = facilityRuntime.toExtractionCapability(site.facility());
                Stage18ExtractionRuntime.PhysicalCargoStore cargo = new Stage18ExtractionRuntime.PhysicalCargoStore(
                        ontology,
                        site.storage().snapshot().capacityByStorageClassKg(),
                        site.storage().snapshot().commodityMassByIdKg());
                ExtractionResult result = extraction.extract(
                        sources.get(id), method.id(), sourceMass, capability,
                        capability.openInterval(extractionDuration(sourceMass, method, capability)), cargo);
                require(result.committed(), "extract " + id, result.status());
                site.storage().restore(new StationStorageSnapshot(
                        site.storage().stationId(),
                        site.storage().snapshot().capacityByStorageClassKg(),
                        cargo.snapshotMassByCommodityKg(),
                        site.storage().snapshot().productCountById()));
            }
            transferCommodity(site.storage(), industry, id, missing);
        }

        private void ensureRefined(String id, double targetKg) {
            double missing = targetKg - industry.commodityMassKg(id);
            if (missing <= EPSILON) {
                return;
            }
            RefiningRecipeDefinition recipe = refiningCatalog.getRecipes().stream()
                    .filter(value -> value.outputCommodityId().equals(id)).findFirst().orElseThrow();
            double inputMass = missing / recipe.outputMassFraction();
            for (Stage18RefiningCatalog.RecipeInputDefinition input : recipe.inputs()) {
                ensureFeedstock(input.commodityId(),
                        industry.commodityMassKg(input.commodityId()) + inputMass * input.fractionOfInputMass());
            }
            StationStorageSnapshot before = industry.snapshot();
            PhysicalMaterialStore staging = new PhysicalMaterialStore(
                    ontology, before.capacityByStorageClassKg(), before.commodityMassByIdKg());
            RefiningResult result = refining.refine(
                    recipe.id(), inputMass, staging,
                    refiningNetwork.openInterval(refiningDuration(inputMass, recipe, refiningNetwork)));
            require(result.accepted(), "refine " + id, result.status());
            industry.restore(new StationStorageSnapshot(
                    INDUSTRY, before.capacityByStorageClassKg(), staging.snapshotMassByCommodityKg(),
                    before.productCountById()));
        }

        private void ensureComponent(String id, double targetKg) {
            double missing = targetKg - industry.commodityMassKg(id);
            if (missing <= EPSILON) {
                return;
            }
            ComponentRecipeDefinition recipe = manufacturingCatalog.getComponentRecipes().stream()
                    .filter(value -> value.outputCommodityId().equals(id)).findFirst().orElseThrow();
            for (Stage18ManufacturingCatalog.ManufacturingInputDefinition input : recipe.inputs()) {
                ensureCommodity(input.commodityId(),
                        industry.commodityMassKg(input.commodityId()) + missing * input.fractionOfOutputMass());
            }
            StationStorageSnapshot before = industry.snapshot();
            ManufacturingInventory staging = new ManufacturingInventory(
                    ontology, products, before.capacityByStorageClassKg(), before.commodityMassByIdKg(),
                    before.productCountById());
            ManufacturingResult result = manufacturing.manufactureComponent(
                    recipe.id(), missing, staging,
                    manufacturingNetwork.openInterval(manufacturingDuration(
                            missing, recipe.energyJPerOutputKg(), recipe.workSecondsPerOutputKg(),
                            recipe.maintenanceWorkSecondsPerOutputKg(), manufacturingNetwork)));
            require(result.accepted(), "manufacture component " + id, result.status());
            industry.restore(new StationStorageSnapshot(
                    INDUSTRY, before.capacityByStorageClassKg(), staging.snapshotCommodityMassByIdKg(),
                    staging.snapshotProductCountById()));
            if (id.equals("commodity.component.heavy_components")) {
                heavyProduced += result.outputMassKg();
            } else if (id.equals("commodity.component.electrical_components")) {
                electricalProduced += result.outputMassKg();
            } else if (id.equals("commodity.component.precision_components")) {
                precisionProduced += result.outputMassKg();
            }
        }

        private void ensureProduct(String id, int targetCount) {
            int missing = targetCount - industry.productCount(id);
            if (missing <= 0) {
                return;
            }
            ProductDefinition product = products.findProduct(id);
            ProductBindingDefinition binding = manufacturingCatalog.findProductBinding(id);
            if (product == null || binding == null) {
                throw new IllegalArgumentException("Unknown product: " + id);
            }
            ProductProfileDefinition profile = manufacturingCatalog.findProductProfile(binding.profileId());
            double outputMass = product.unitMassKg() * missing;
            for (Stage18ManufacturingCatalog.ManufacturingInputDefinition input : profile.inputs()) {
                ensureCommodity(input.commodityId(),
                        industry.commodityMassKg(input.commodityId()) + outputMass * input.fractionOfOutputMass());
            }
            StationStorageSnapshot before = industry.snapshot();
            ManufacturingInventory staging = new ManufacturingInventory(
                    ontology, products, before.capacityByStorageClassKg(), before.commodityMassByIdKg(),
                    before.productCountById());
            ManufacturingResult result = manufacturing.manufactureProduct(
                    id, missing, staging,
                    manufacturingNetwork.openInterval(manufacturingDuration(
                            outputMass, profile.energyJPerOutputKg(), profile.workSecondsPerOutputKg(),
                            profile.maintenanceWorkSecondsPerOutputKg(), manufacturingNetwork)));
            require(result.accepted(), "manufacture product " + id, result.status());
            industry.restore(new StationStorageSnapshot(
                    INDUSTRY, before.capacityByStorageClassKg(), staging.snapshotCommodityMassByIdKg(),
                    staging.snapshotProductCountById()));
        }

        private void ensureRepairInputs(InstalledFit fit, ShipDamageRuntime.Snapshot damage) {
            Map<String, Double> required = new TreeMap<>();
            Stage18ShipyardCatalog.HullPhysicalProfile hull = shipyardCatalog.findHullProfile(fit.hullId());
            for (Map.Entry<String, Double> entry : damage.compartmentIntegrityById().entrySet()) {
                double loss = 1d - entry.getValue();
                if (loss > EPSILON) {
                    hull.findCompartmentRepair(entry.getKey()).inputsAtFullLossKg().forEach(input ->
                            required.merge(input.commodityId(), input.massKg() * loss, Double::sum));
                }
            }
            Map<String, String> moduleByMount = new HashMap<>();
            fit.installedModules().forEach(value -> moduleByMount.put(value.mountId(), value.moduleId()));
            for (Map.Entry<String, Double> entry : damage.moduleDamage().moduleIntegrityByMount().entrySet()) {
                String moduleId = moduleByMount.get(entry.getKey());
                if (moduleId != null) {
                    double loss = 1d - entry.getValue();
                    shipyardCatalog.findModuleProfile(moduleId).repairInputsAtFullLossKg().forEach(input ->
                            required.merge(input.commodityId(), input.massKg() * loss, Double::sum));
                }
            }
            for (Map.Entry<String, Double> entry : required.entrySet()) {
                ensureCommodity(entry.getKey(), industry.commodityMassKg(entry.getKey()) + entry.getValue());
            }
        }

        private ConstructionOrderSnapshot createPartialConstruction() {
            ConstructionOrderSnapshot order = construction.createOrder(
                    "construction.acceptance.recycling", "facility.acceptance.recycling.new",
                    "facility.processing.recycling", INDUSTRY, "location.orbital_station");
            for (Map.Entry<String, Double> entry : order.requiredMassByCommodityKg().entrySet()) {
                ensureCommodity(entry.getKey(), industry.commodityMassKg(entry.getKey()) + entry.getValue());
            }
            for (Map.Entry<String, Double> entry : order.requiredMassByCommodityKg().entrySet()) {
                Stage18FacilityConstructionRuntime.DeliveryResult delivery = construction.deliver(
                        order, industry, entry.getKey(), entry.getValue());
                require(delivery.status() == Stage18FacilityConstructionRuntime.DeliveryStatus.DELIVERED,
                        "construction delivery", delivery.status());
                order = delivery.order();
            }
            ConstructionCapability capability = construction.projectCapability(
                    "construction.acceptance", constructionFacilities);
            double halfWork = order.requiredWorkSeconds() * 0.5d;
            WorkResult result = construction.advanceWork(
                    order, capability.openInterval(halfWork / capability.engineeringWorkRate()));
            require(result.status() == Stage18FacilityConstructionRuntime.WorkStatus.ADVANCED,
                    "partial construction", result.status());
            return result.order();
        }

        private ProcessOrderSnapshot createReservedProcessOrder() {
            ensureFeedstock("commodity.feedstock.metallic_ore",
                    industry.commodityMassKg("commodity.feedstock.metallic_ore") + 1_000d);
            industry.removeCommodity("commodity.feedstock.metallic_ore", 1_000d);
            return new ProcessOrderSnapshot(
                    "process.acceptance.refining", ProcessKind.REFINING, "refining.structural_alloy",
                    INDUSTRY, "", 1_000d, 0, 0.5d,
                    Map.of("commodity.feedstock.metallic_ore", 1_000d), Map.of());
        }

        private Continuation continueConstruction(
                ConstructionOrderSnapshot order,
                Stage18StationStorage storage,
                ProcessOrderSnapshot process,
                long tick) {
            ConstructionCapability capability = construction.projectCapability(
                    "construction.acceptance", constructionFacilities);
            WorkResult result = construction.advanceWork(
                    order,
                    capability.openInterval(order.remainingWorkSeconds() / capability.engineeringWorkRate() + 1d));
            require(result.status() == Stage18FacilityConstructionRuntime.WorkStatus.COMPLETED,
                    "construction completion", result.status());
            Stage18IndustrialState finalState = snapshot(tick, storage, result.order(), process);
            return new Continuation(
                    result.installedFacility().facilityDefinitionId(),
                    Stage18IndustrialStateCodec.encode(finalState));
        }

        private Stage18IndustrialState snapshot(
                long tick,
                Stage18StationStorage industryStorage,
                ConstructionOrderSnapshot order,
                ProcessOrderSnapshot process) {
            List<PhysicalSourceSnapshot> sourceSnapshots = sources.values().stream()
                    .map(PhysicalSourceSnapshot::capture).toList();
            List<FacilityInstallationSnapshot> facilities = installedFacilities.stream()
                    .map(value -> new FacilityInstallationSnapshot(
                            Objects.requireNonNull(facilityOwners.get(value.facilityInstanceId())), value))
                    .toList();
            return new Stage18IndustrialState(
                    Stage18IndustrialState.CURRENT_VERSION,
                    Stage18IndustrialContentFingerprint.current(),
                    tick,
                    sourceSnapshots,
                    List.of(mine.snapshot(), volatileSite.snapshot(), industryStorage.snapshot(), depot.snapshot()),
                    facilities,
                    List.of(new YardInstallationSnapshot(INDUSTRY, installedYard)),
                    List.of(order),
                    List.of(process));
        }

        private Stage18StationStorage restoreStorage(Stage18IndustrialState state, String stationId) {
            StationStorageSnapshot snapshot = state.stationStorages().stream()
                    .filter(value -> value.stationId().equals(stationId)).findFirst().orElseThrow();
            return Stage18StationStorage.restore(ontology, products, snapshot);
        }

        private void transferCommodity(
                Stage18StationStorage source, Stage18StationStorage destination, String id, double massKg) {
            double duration = massKg / handling.massRateKgPerSecond() + 1d;
            Stage18LogisticsRuntime.TransferResult result = logistics.transferCommodity(
                    source, destination, id, massKg, handling, handling.openInterval(duration));
            require(result.transferred(), "commodity logistics " + id, result.status());
        }

        private Stage18LogisticsRuntime.TransferResult transferProduct(
                Stage18StationStorage source, Stage18StationStorage destination, String id, int units) {
            ProductDefinition product = products.findProduct(id);
            double mass = product.unitMassKg() * units;
            return logistics.transferProduct(
                    source, destination, id, units, handling,
                    handling.openInterval(mass / handling.massRateKgPerSecond() + 1d));
        }

        private FacilityCapabilitySnapshot addProcess(String definitionId) {
            FacilityCapabilitySnapshot snapshot = install(
                    INDUSTRY, "facility.acceptance." + suffix(definitionId), definitionId, "location.orbital_station");
            processFacilities.add(snapshot);
            constructionFacilities.add(snapshot);
            return snapshot;
        }

        private FacilityCapabilitySnapshot addFabrication(String definitionId) {
            FacilityCapabilitySnapshot snapshot = install(
                    INDUSTRY, "facility.acceptance." + suffix(definitionId), definitionId, "location.orbital_station");
            fabricationFacilities.add(snapshot);
            constructionFacilities.add(snapshot);
            return snapshot;
        }

        private FacilityCapabilitySnapshot install(
                String owner, String instanceId, String definitionId, String location) {
            Stage18FacilityCatalog.FacilityDefinition definition = facilityCatalog.findFacility(definitionId);
            InstalledFacilityState state = new InstalledFacilityState(
                    instanceId, definitionId, 1d,
                    definition.ratedProcessPowerW(),
                    definition.ratedProcessPowerW() * definition.heatRejectionWPerProcessW(),
                    definition.requiredLaborUnitsAtFullRate(),
                    definition.maintenanceWorkRate(),
                    location, true);
            FacilityCapabilitySnapshot snapshot = facilityRuntime.project(state);
            require(snapshot.status() == Stage18FacilityRuntime.Status.ACTIVE,
                    "facility projection " + definitionId, snapshot.status());
            installedFacilities.add(state);
            facilityOwners.put(instanceId, owner);
            return snapshot;
        }

        private SourceSite sourceSite(String commodityId) {
            if (commodityId.equals("commodity.feedstock.volatile_feedstock")) {
                return new SourceSite(volatileSite, volatileFacility, "extraction.thermal_volatiles");
            }
            return new SourceSite(mine, asteroidFacility, "extraction.asteroid_excavation");
        }

        private void seedSources() {
            addSource("commodity.feedstock.water_ice", "occurrence.water_ice", ExtractionEnvironment.FREE_BODY);
            addSource("commodity.feedstock.volatile_feedstock", "occurrence.volatiles", ExtractionEnvironment.VOLATILE_BEARING);
            addSource("commodity.feedstock.carbonaceous_feedstock", "occurrence.carbonaceous", ExtractionEnvironment.FREE_BODY);
            addSource("commodity.feedstock.metallic_ore", "occurrence.metallic", ExtractionEnvironment.FREE_BODY);
            addSource("commodity.feedstock.light_metal_minerals", "occurrence.light_metals", ExtractionEnvironment.FREE_BODY);
            addSource("commodity.feedstock.conductor_ore", "occurrence.conductors", ExtractionEnvironment.FREE_BODY);
            addSource("commodity.feedstock.strategic_metal_ore", "occurrence.strategic_metals", ExtractionEnvironment.FREE_BODY);
            addSource("commodity.feedstock.silicate_minerals", "occurrence.silicates", ExtractionEnvironment.FREE_BODY);
            addSource("commodity.feedstock.fissile_minerals", "occurrence.fissiles", ExtractionEnvironment.FREE_BODY);
        }

        private void addSource(String commodity, String occurrence, ExtractionEnvironment environment) {
            sources.put(commodity, new PhysicalSourceState(
                    "source.acceptance." + suffix(commodity), SourceKind.NATURAL_OCCURRENCE,
                    occurrence, environment, commodity,
                    SOURCE_RESERVE_KG, SOURCE_RESERVE_KG, 1d, 1d, Set.of()));
        }

        private double extractionDuration(
                double massKg, ExtractionMethodDefinition method, ExtractionCapability capability) {
            return safetyDuration(
                    massKg / method.maxSourceKgPerSecond(),
                    massKg * method.energyJPerSourceKg() / capability.availablePowerW(),
                    massKg * method.workSecondsPerSourceKg() / capability.workRate(),
                    massKg * method.maintenanceWorkSecondsPerSourceKg() / capability.maintenanceWorkRate());
        }

        private double refiningDuration(
                double massKg, RefiningRecipeDefinition recipe, RefiningCapability capability) {
            return safetyDuration(
                    massKg * recipe.energyJPerInputKg() / capability.availablePowerW(),
                    massKg * recipe.workSecondsPerInputKg() / capability.workRate(),
                    massKg * recipe.maintenanceWorkSecondsPerInputKg() / capability.maintenanceWorkRate());
        }

        private double manufacturingDuration(
                double massKg, double energyPerKg, double workPerKg, double maintenancePerKg,
                ManufacturingCapability capability) {
            return safetyDuration(
                    massKg * energyPerKg / capability.availablePowerW(),
                    massKg * workPerKg / capability.workRate(),
                    massKg * maintenancePerKg / capability.maintenanceWorkRate());
        }

        private static double safetyDuration(double... candidates) {
            double maximum = 0d;
            for (double candidate : candidates) {
                if (!Double.isFinite(candidate) || candidate < 0d) {
                    throw new IllegalArgumentException("Invalid acceptance duration: " + candidate);
                }
                maximum = Math.max(maximum, candidate);
            }
            return Math.max(1e-6d, maximum * 1.000001d + 1e-6d);
        }

        private Stage18StationStorage newStorage(String id) {
            return new Stage18StationStorage(ontology, products, id, capacityMap(), Map.of(), Map.of());
        }

        private Map<String, Double> capacityMap() {
            TreeMap<String, Double> result = new TreeMap<>();
            ontology.getStorageClasses().forEach(value -> result.put(value.id(), STORAGE_CAPACITY_KG));
            return result;
        }

        private Set<String> allStorageClasses() {
            TreeSet<String> result = new TreeSet<>();
            ontology.getStorageClasses().forEach(value -> result.add(value.id()));
            return Set.copyOf(result);
        }

        private static ShipDamageRuntime.Snapshot damage(
                Map<String, Double> modules, double engineering, double mission, double weapons) {
            LinkedHashMap<String, Double> compartments = new LinkedHashMap<>();
            compartments.put("engineering", engineering);
            compartments.put("mission_core", mission);
            compartments.put("weapons", weapons);
            return new ShipDamageRuntime.Snapshot(compartments, new DamageState(modules));
        }

        private static String suffix(String id) {
            return id.replace('.', '_').replace('-', '_');
        }

        private static void require(boolean condition, String step, Object detail) {
            if (!condition) {
                throw new IllegalStateException("Stage-18 acceptance failed at " + step + ": " + detail);
            }
        }

        private static String sha256(byte[] bytes) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
            }
        }

        private record SourceSite(
                Stage18StationStorage storage,
                FacilityCapabilitySnapshot facility,
                String methodId) { }

        private record Continuation(String installedFacilityDefinitionId, byte[] bytes) { }
    }
}
