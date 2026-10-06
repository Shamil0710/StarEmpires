package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.economy.Stage18ExtractionRuntime.ExtractionResult;
import com.spacesim.economy.Stage18ExtractionRuntime.PhysicalCargoStore;
import com.spacesim.economy.Stage18ExtractionRuntime.PhysicalSourceState;
import com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot;
import com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingInventory;
import com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingResult;
import com.spacesim.economy.Stage18RefiningRuntime.PhysicalMaterialStore;
import com.spacesim.economy.Stage18RefiningRuntime.RefiningResult;

import java.util.Objects;

/**
 * Transactional Stage-18F bridge between canonical station storage and Stage-18B/18C/18D runtimes.
 *
 * <p>Earlier slices use small local stores so they remain independently testable. Stage 18F keeps
 * one canonical station inventory and creates temporary staging views whose capacities reserve mass
 * already occupied by finished products. A successful operation is committed back atomically; a
 * rejected operation leaves canonical station storage unchanged.</p>
 */
public final class Stage18StationProductionBridge {
    private final Stage18ResourceOntologyCatalog ontology;
    private final Stage18ManufacturingProductRegistry products;
    private final Stage18FacilityRuntime facilityRuntime;
    private final Stage18ExtractionRuntime extractionRuntime;
    private final Stage18RefiningRuntime refiningRuntime;
    private final Stage18ManufacturingRuntime manufacturingRuntime;

    /**
     * Creates a bridge over the authoritative Stage-18 industrial settlement runtimes.
     *
     * @param ontology authoritative Stage-18 resource ontology
     * @param products authoritative finished-product registry
     * @param facilityRuntime Stage-18E facility capability projector/adapter
     * @param extractionRuntime Stage-18B extraction settlement
     * @param refiningRuntime Stage-18C refining settlement
     * @param manufacturingRuntime Stage-18D manufacturing settlement
     */
    public Stage18StationProductionBridge(
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products,
            Stage18FacilityRuntime facilityRuntime,
            Stage18ExtractionRuntime extractionRuntime,
            Stage18RefiningRuntime refiningRuntime,
            Stage18ManufacturingRuntime manufacturingRuntime) {
        this.ontology = Objects.requireNonNull(ontology, "ontology");
        this.products = Objects.requireNonNull(products, "products");
        this.facilityRuntime = Objects.requireNonNull(facilityRuntime, "facilityRuntime");
        this.extractionRuntime = Objects.requireNonNull(extractionRuntime, "extractionRuntime");
        this.refiningRuntime = Objects.requireNonNull(refiningRuntime, "refiningRuntime");
        this.manufacturingRuntime = Objects.requireNonNull(manufacturingRuntime, "manufacturingRuntime");
    }

    /**
     * Executes Stage-18B extraction and commits recovered commodity mass to canonical station storage.
     *
     * @param source finite physical source state
     * @param methodId Stage-18B extraction method ID
     * @param requestedSourceMassKg requested gross source mass removal
     * @param stationStorage canonical destination station storage
     * @param facility projected installed extraction facility
     * @param durationSeconds finite processing interval
     * @return authoritative Stage-18B extraction result
     */
    public ExtractionResult extractToStation(
            PhysicalSourceState source,
            String methodId,
            double requestedSourceMassKg,
            Stage18StationStorage stationStorage,
            FacilityCapabilitySnapshot facility,
            double durationSeconds) {
        Objects.requireNonNull(stationStorage, "stationStorage");
        var capability = facilityRuntime.toExtractionCapability(Objects.requireNonNull(facility, "facility"));
        return extractToStorage(source, methodId, requestedSourceMassKg, stationStorage,
                capability, capability.openInterval(durationSeconds));
    }

    /**
     * Extracts into the canonical physical store using an already allocated interval budget.
     * Suitable for installed ship equipment as well as station facilities; repeated calls share
     * the same remaining energy, work and maintenance rather than opening another interval.
     * @param source actual finite source
     * @param methodId authored extraction method
     * @param requestedSourceMassKg requested gross source mass
     * @param stationStorage actual canonical destination, including a ship hold
     * @param capability installed equipment capability
     * @param budget caller-owned shared interval budget
     * @return ordinary extraction settlement
     */
    public ExtractionResult extractToStorage(
            PhysicalSourceState source, String methodId, double requestedSourceMassKg,
            Stage18StationStorage stationStorage,
            Stage18ExtractionRuntime.ExtractionCapability capability,
            Stage18ExtractionRuntime.IntervalBudget budget) {
        Objects.requireNonNull(stationStorage, "stationStorage");
        PhysicalCargoStore staged = new PhysicalCargoStore(
                ontology,
                stationStorage.commodityLayerCapacityByStorageClassKg(),
                stationStorage.snapshotCommodityMassByIdKg());
        ExtractionResult result = extractionRuntime.extract(
                source,
                methodId,
                requestedSourceMassKg,
                capability,
                budget,
                staged);
        if (result.committed()) {
            stationStorage.replaceContents(
                    staged.snapshotMassByCommodityKg(),
                    stationStorage.snapshotProductCountById());
        }
        return result;
    }

    /**
     * Executes one Stage-18C refining batch against canonical station commodity inventory.
     *
     * @param recipeId Stage-18C refining recipe ID
     * @param requestedInputMassKg gross input batch mass
     * @param stationStorage canonical station storage
     * @param facility projected installed processing facility
     * @param durationSeconds finite processing interval
     * @return authoritative Stage-18C refining result
     */
    public RefiningResult refineAtStation(
            String recipeId,
            double requestedInputMassKg,
            Stage18StationStorage stationStorage,
            FacilityCapabilitySnapshot facility,
            double durationSeconds) {
        Objects.requireNonNull(stationStorage, "stationStorage");
        PhysicalMaterialStore staged = new PhysicalMaterialStore(
                ontology,
                stationStorage.commodityLayerCapacityByStorageClassKg(),
                stationStorage.snapshotCommodityMassByIdKg());
        var capability = facilityRuntime.toRefiningCapability(Objects.requireNonNull(facility, "facility"));
        RefiningResult result = refiningRuntime.refine(
                recipeId,
                requestedInputMassKg,
                staged,
                capability.openInterval(durationSeconds));
        if (result.accepted()) {
            stationStorage.replaceContents(
                    staged.snapshotMassByCommodityKg(),
                    stationStorage.snapshotProductCountById());
        }
        return result;
    }

    /**
     * Executes bulk Stage-18D component manufacturing against canonical station inventory.
     *
     * @param recipeId Stage-18D component recipe ID
     * @param requestedOutputMassKg requested finished component mass
     * @param stationStorage canonical station storage
     * @param facility projected installed fabrication facility
     * @param durationSeconds finite manufacturing interval
     * @return authoritative Stage-18D manufacturing result
     */
    public ManufacturingResult manufactureComponentAtStation(
            String recipeId,
            double requestedOutputMassKg,
            Stage18StationStorage stationStorage,
            FacilityCapabilitySnapshot facility,
            double durationSeconds) {
        ManufacturingInventory staged = manufacturingInventory(stationStorage);
        var capability = facilityRuntime.toManufacturingCapability(Objects.requireNonNull(facility, "facility"));
        ManufacturingResult result = manufacturingRuntime.manufactureComponent(
                recipeId,
                requestedOutputMassKg,
                staged,
                capability.openInterval(durationSeconds));
        commitManufacturingIfAccepted(stationStorage, staged, result);
        return result;
    }

    /**
     * Executes countable Stage-18D module/ammunition manufacturing against canonical station inventory.
     *
     * @param productContentId existing Stage-17.5 product content ID
     * @param requestedUnitCount positive requested unit count
     * @param stationStorage canonical station storage
     * @param facility projected installed fabrication/assembly facility
     * @param durationSeconds finite manufacturing interval
     * @return authoritative Stage-18D manufacturing result
     */
    public ManufacturingResult manufactureProductAtStation(
            String productContentId,
            int requestedUnitCount,
            Stage18StationStorage stationStorage,
            FacilityCapabilitySnapshot facility,
            double durationSeconds) {
        var capability = facilityRuntime.toManufacturingCapability(Objects.requireNonNull(facility, "facility"));
        return manufactureProductAtStorage(productContentId, requestedUnitCount, stationStorage,
                capability.openInterval(durationSeconds));
    }

    /**
     * Settles finished products against canonical storage using the caller's allocated budget.
     * Multiple orders on the same production line must share this budget for the completed tick.
     * Rejections preserve both storage and the remaining budget.
     * @param productContentId authored finished product
     * @param requestedUnitCount positive whole-unit count
     * @param stationStorage actual canonical inventory
     * @param budget shared finite manufacturing interval
     * @return ordinary manufacturing settlement
     */
    public ManufacturingResult manufactureProductAtStorage(
            String productContentId, int requestedUnitCount,
            Stage18StationStorage stationStorage,
            Stage18ManufacturingRuntime.IntervalBudget budget) {
        Objects.requireNonNull(budget, "budget");
        ManufacturingInventory staged = manufacturingInventory(stationStorage);
        ManufacturingResult result = manufacturingRuntime.manufactureProduct(
                productContentId,
                requestedUnitCount,
                staged,
                budget);
        commitManufacturingIfAccepted(stationStorage, staged, result);
        return result;
    }

    private ManufacturingInventory manufacturingInventory(Stage18StationStorage storage) {
        Objects.requireNonNull(storage, "stationStorage");
        return new ManufacturingInventory(
                ontology,
                products,
                storage.manufacturingLayerCapacityByStorageClassKg(),
                storage.snapshotCommodityMassByIdKg(),
                storage.snapshotProductCountById());
    }

    private static void commitManufacturingIfAccepted(
            Stage18StationStorage stationStorage,
            ManufacturingInventory staged,
            ManufacturingResult result) {
        if (result.accepted()) {
            stationStorage.replaceContents(
                    staged.snapshotCommodityMassByIdKg(),
                    staged.snapshotProductCountById());
        }
    }
}
