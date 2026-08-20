package com.spacesim.world;

import com.spacesim.content.Stage18ExtractionCatalog;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionMethodDefinition;
import com.spacesim.content.Stage18FacilityCatalog;
import com.spacesim.content.Stage18FacilityCatalog.FacilityDefinition;
import com.spacesim.content.Stage18ManufacturingCatalog;
import com.spacesim.content.Stage18ManufacturingCatalog.ComponentRecipeDefinition;
import com.spacesim.content.Stage18RefiningCatalog;
import com.spacesim.content.Stage18RefiningCatalog.RefiningRecipeDefinition;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyCatalog.CommodityDefinition;
import com.spacesim.content.Stage18StationInfrastructureCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
import com.spacesim.world.Stage20LocalInfrastructureLayout.InfrastructurePlacement;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.InitialExtractionSite;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.ResourceOccurrence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/** Physical upper-bound capacity calculations used by Stage-20E bootstrap acceptance. */
@SuppressWarnings("doclint:missing")
public final class Stage20BootstrapProductionCapacityCalculator {
    private Stage20BootstrapProductionCapacityCalculator() { throw new AssertionError("No instances"); }

    /** Whether source-side cargo export authority is physically resolved. */
    public enum ExportHandlingStatus { RESOLVED, UNRESOLVED }

    /** Result state of required-vs-available physical throughput. */
    public enum HeadroomStatus { SUFFICIENT, INSUFFICIENT, UNRESOLVED }

    /** Stage-18 process family represented by a station capacity row. */
    public enum ProcessKind { REFINING, COMPONENT_MANUFACTURING }

    /** Explicit source-side export handling seam; empty means unresolved, never infinite. */
    @FunctionalInterface
    public interface SourceExportHandlingProvider {
        OptionalDouble exportHandlingKgPerSecond(InitialExtractionSite site, ResourceOccurrence source);
    }

    /** Calculates extraction process/export upper bounds for every generated initial site. */
    public static List<ExtractionCapacity> extractionCapacities(
            Stage20ResourceOccurrenceWorld world,
            Stage18ExtractionCatalog extraction,
            Stage18FacilityCatalog facilities,
            SourceExportHandlingProvider handlingProvider) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(extraction, "extraction");
        Objects.requireNonNull(facilities, "facilities");
        Objects.requireNonNull(handlingProvider, "handlingProvider");
        List<ExtractionCapacity> result = new ArrayList<>();
        for (InitialExtractionSite site : world.initialExtractionSites()) {
            ResourceOccurrence source = world.occurrence(site.sourceId());
            ExtractionMethodDefinition method = Objects.requireNonNull(
                    extraction.findMethod(site.extractionMethodId()), "unknown extraction method");
            FacilityDefinition facility = Objects.requireNonNull(
                    facilities.findFacility(site.facilityDefinitionId()), "unknown extraction facility");
            requireCompatible(site, source, method, facility);
            double gross = minPositive(
                    method.maxSourceKgPerSecond(),
                    facility.ratedProcessPowerW() / method.energyJPerSourceKg(),
                    facility.engineeringWorkRate() / method.workSecondsPerSourceKg(),
                    facility.maintenanceWorkRate() / method.maintenanceWorkSecondsPerSourceKg());
            double recovered = gross * source.gradeFraction()
                    * source.sourceRecoveryFraction() * method.recoveryFraction();
            positive(recovered, "recoveredOutputKgPerSecond");
            double lifetime = source.initialAccessibleMassKg() / gross;
            positive(lifetime, "reserveLifetimeSeconds");
            OptionalDouble handling = Objects.requireNonNull(
                    handlingProvider.exportHandlingKgPerSecond(site, source), "handling result");
            OptionalDouble export = OptionalDouble.empty();
            ExportHandlingStatus status = ExportHandlingStatus.UNRESOLVED;
            if (handling.isPresent()) {
                positive(handling.getAsDouble(), "source export handling rate");
                export = OptionalDouble.of(Math.min(recovered, handling.getAsDouble()));
                status = ExportHandlingStatus.RESOLVED;
            }
            result.add(new ExtractionCapacity(site.siteId(), site.sourceId(), site.systemId(),
                    source.outputCommodityId(), facility.id(), method.id(), gross, recovered, lifetime, status, export));
        }
        result.sort(Comparator.comparing(ExtractionCapacity::systemId).thenComparing(ExtractionCapacity::siteId));
        return List.copyOf(result);
    }

    /** Convenience overload that keeps source export unresolved. */
    public static List<ExtractionCapacity> extractionCapacities(
            Stage20ResourceOccurrenceWorld world,
            Stage18ExtractionCatalog extraction,
            Stage18FacilityCatalog facilities) {
        return extractionCapacities(world, extraction, facilities, (site, source) -> OptionalDouble.empty());
    }

    /** Calculates pristine process upper bounds for each actual installed facility. */
    public static List<StationProcessCapacity> stationProcessCapacities(
            List<Stage20LocalInfrastructureLayout> layouts,
            Stage18StationInfrastructureCatalog stationInfrastructure,
            Stage18FacilityCatalog facilities,
            Stage18ResourceOntologyCatalog ontology,
            Stage18RefiningCatalog refining,
            Stage18ManufacturingCatalog manufacturing) {
        Objects.requireNonNull(layouts, "layouts");
        Objects.requireNonNull(stationInfrastructure, "stationInfrastructure");
        Objects.requireNonNull(facilities, "facilities");
        Objects.requireNonNull(ontology, "ontology");
        Objects.requireNonNull(refining, "refining");
        Objects.requireNonNull(manufacturing, "manufacturing");
        List<StationProcessCapacity> result = new ArrayList<>();
        for (Stage20LocalInfrastructureLayout layout : layouts) {
            for (InfrastructurePlacement placement : layout.placements()) {
                if (!placement.isStation()) continue;
                StationArchetypeDefinition archetype = Objects.requireNonNull(
                        stationInfrastructure.findArchetype(placement.stationArchetypeId().orElseThrow()),
                        "unknown station archetype");
                for (String facilityId : archetype.installedFacilityDefinitionIds()) {
                    FacilityDefinition facility = Objects.requireNonNull(facilities.findFacility(facilityId),
                            "unknown station facility");
                    for (RefiningRecipeDefinition recipe : refining.getRecipes()) {
                        if (!facility.capabilityTags().containsAll(recipe.requiredCapabilityTags())
                                || !stores(archetype, ontology,
                                recipe.inputs().stream().map(i -> i.commodityId()).toList(), recipe.outputCommodityId())) continue;
                        double inputRate = minPositive(
                                facility.ratedProcessPowerW() / recipe.energyJPerInputKg(),
                                facility.engineeringWorkRate() / recipe.workSecondsPerInputKg(),
                                facility.maintenanceWorkRate() / recipe.maintenanceWorkSecondsPerInputKg());
                        double output = inputRate * recipe.outputMassFraction();
                        addProcess(result, layout.systemId(), placement.id(), facility.id(), ProcessKind.REFINING,
                                recipe.id(), recipe.outputCommodityId(), output, archetype.transferMassRateKgPerSecond());
                    }
                    for (ComponentRecipeDefinition recipe : manufacturing.getComponentRecipes()) {
                        if (!facility.capabilityTags().containsAll(recipe.requiredCapabilityTags())
                                || !stores(archetype, ontology,
                                recipe.inputs().stream().map(i -> i.commodityId()).toList(), recipe.outputCommodityId())) continue;
                        double output = minPositive(
                                facility.ratedProcessPowerW() / recipe.energyJPerOutputKg(),
                                facility.engineeringWorkRate() / recipe.workSecondsPerOutputKg(),
                                facility.maintenanceWorkRate() / recipe.maintenanceWorkSecondsPerOutputKg());
                        addProcess(result, layout.systemId(), placement.id(), facility.id(),
                                ProcessKind.COMPONENT_MANUFACTURING, recipe.id(), recipe.outputCommodityId(), output,
                                archetype.transferMassRateKgPerSecond());
                    }
                }
            }
        }
        result.sort(Comparator.comparing(StationProcessCapacity::systemId)
                .thenComparing(StationProcessCapacity::stationPlacementId)
                .thenComparing(StationProcessCapacity::facilityDefinitionId)
                .thenComparing(StationProcessCapacity::processId));
        return List.copyOf(result);
    }

    private static void addProcess(List<StationProcessCapacity> out, StarSystemId systemId, String stationId,
            String facilityId, ProcessKind kind, String processId, String commodityId,
            double processRate, double transferRate) {
        positive(processRate, "processRate"); positive(transferRate, "transferRate");
        out.add(new StationProcessCapacity(systemId, stationId, facilityId, kind, processId, commodityId,
                processRate, transferRate, Math.min(processRate, transferRate)));
    }

    /** Compares an explicit requirement with an available physical rate. */
    public static CapacityHeadroom assessHeadroom(
            double requiredKgPerSecond, OptionalDouble availableKgPerSecond, String sourceEvidenceId) {
        positive(requiredKgPerSecond, "requiredKgPerSecond");
        Objects.requireNonNull(availableKgPerSecond, "availableKgPerSecond");
        sourceEvidenceId = text(sourceEvidenceId, "sourceEvidenceId");
        if (availableKgPerSecond.isEmpty()) {
            return new CapacityHeadroom(requiredKgPerSecond, OptionalDouble.empty(), OptionalDouble.empty(),
                    HeadroomStatus.UNRESOLVED, sourceEvidenceId);
        }
        double available = availableKgPerSecond.getAsDouble(); positive(available, "availableKgPerSecond");
        double headroom = available - requiredKgPerSecond;
        return new CapacityHeadroom(requiredKgPerSecond, OptionalDouble.of(available), OptionalDouble.of(headroom),
                headroom >= -1e-9 ? HeadroomStatus.SUFFICIENT : HeadroomStatus.INSUFFICIENT, sourceEvidenceId);
    }

    /** Machine-readable throughput headroom diagnostic. */
    public record CapacityHeadroom(double requiredKgPerSecond, OptionalDouble availableKgPerSecond,
            OptionalDouble headroomKgPerSecond, HeadroomStatus status, String sourceEvidenceId) {
        public CapacityHeadroom {
            positive(requiredKgPerSecond, "requiredKgPerSecond");
            Objects.requireNonNull(availableKgPerSecond, "availableKgPerSecond");
            Objects.requireNonNull(headroomKgPerSecond, "headroomKgPerSecond");
            Objects.requireNonNull(status, "status"); text(sourceEvidenceId, "sourceEvidenceId");
            if (status == HeadroomStatus.UNRESOLVED) {
                if (availableKgPerSecond.isPresent() || headroomKgPerSecond.isPresent())
                    throw new IllegalArgumentException("unresolved headroom must omit values");
            } else {
                if (availableKgPerSecond.isEmpty() || headroomKgPerSecond.isEmpty())
                    throw new IllegalArgumentException("resolved headroom requires values");
                positive(availableKgPerSecond.getAsDouble(), "availableKgPerSecond");
                if (!Double.isFinite(headroomKgPerSecond.getAsDouble()))
                    throw new IllegalArgumentException("headroom must be finite");
            }
        }
    }

    /** One theoretical extraction-capacity row. */
    public record ExtractionCapacity(String siteId, String sourceId, StarSystemId systemId,
            String outputCommodityId, String facilityDefinitionId, String extractionMethodId,
            double grossSourceKgPerSecond, double recoveredOutputKgPerSecond, double reserveLifetimeSeconds,
            ExportHandlingStatus exportHandlingStatus, OptionalDouble sustainableExportKgPerSecond) {
        public ExtractionCapacity {
            text(siteId,"siteId"); text(sourceId,"sourceId"); Objects.requireNonNull(systemId,"systemId");
            text(outputCommodityId,"outputCommodityId"); text(facilityDefinitionId,"facilityDefinitionId");
            text(extractionMethodId,"extractionMethodId"); positive(grossSourceKgPerSecond,"grossSourceKgPerSecond");
            positive(recoveredOutputKgPerSecond,"recoveredOutputKgPerSecond"); positive(reserveLifetimeSeconds,"reserveLifetimeSeconds");
            Objects.requireNonNull(exportHandlingStatus,"exportHandlingStatus");
            Objects.requireNonNull(sustainableExportKgPerSecond,"sustainableExportKgPerSecond");
            if ((exportHandlingStatus == ExportHandlingStatus.RESOLVED) != sustainableExportKgPerSecond.isPresent())
                throw new IllegalArgumentException("export handling status/presence mismatch");
            if (sustainableExportKgPerSecond.isPresent()) positive(sustainableExportKgPerSecond.getAsDouble(),"sustainableExportKgPerSecond");
        }
    }

    /** One pristine process upper bound for one actual station facility. */
    public record StationProcessCapacity(StarSystemId systemId, String stationPlacementId,
            String facilityDefinitionId, ProcessKind processKind, String processId, String outputCommodityId,
            double theoreticalOutputKgPerSecond, double stationTransferRateKgPerSecond,
            double theoreticalExportableOutputKgPerSecond) {
        public StationProcessCapacity {
            Objects.requireNonNull(systemId,"systemId"); text(stationPlacementId,"stationPlacementId");
            text(facilityDefinitionId,"facilityDefinitionId"); Objects.requireNonNull(processKind,"processKind");
            text(processId,"processId"); text(outputCommodityId,"outputCommodityId");
            positive(theoreticalOutputKgPerSecond,"theoreticalOutputKgPerSecond");
            positive(stationTransferRateKgPerSecond,"stationTransferRateKgPerSecond");
            positive(theoreticalExportableOutputKgPerSecond,"theoreticalExportableOutputKgPerSecond");
            if (theoreticalExportableOutputKgPerSecond > Math.min(theoreticalOutputKgPerSecond, stationTransferRateKgPerSecond)+1e-9)
                throw new IllegalArgumentException("exportable output exceeds process/handling ceiling");
        }
    }

    private static boolean stores(StationArchetypeDefinition archetype, Stage18ResourceOntologyCatalog ontology,
            List<String> inputs, String outputId) {
        for (String id : inputs) { CommodityDefinition c=ontology.findCommodity(id); if (c==null || !archetype.storageCapacityByClassKg().containsKey(c.storageClassId())) return false; }
        CommodityDefinition out=ontology.findCommodity(outputId);
        return out!=null && archetype.storageCapacityByClassKg().containsKey(out.storageClassId());
    }

    private static void requireCompatible(InitialExtractionSite site, ResourceOccurrence source,
            ExtractionMethodDefinition method, FacilityDefinition facility) {
        if (!site.sourceId().equals(source.sourceId()) || !site.systemId().equals(source.systemId())
                || method.environment()!=source.environment()
                || !method.compatibleOccurrenceTypeIds().contains(source.occurrenceTypeId())
                || !facility.allowedLocationTags().contains(site.locationTag())
                || !facility.capabilityTags().containsAll(method.requiredCapabilityTags())
                || !facility.capabilityTags().containsAll(source.requiredCapabilityTags()))
            throw new IllegalArgumentException("incompatible generated extraction site: "+site.siteId());
    }
    private static double minPositive(double... values){ double m=Double.POSITIVE_INFINITY; for(double v:values){positive(v,"capacity limiter");m=Math.min(m,v);} return m; }
    private static String text(String v,String f){ if(v==null||v.isBlank()) throw new IllegalArgumentException(f+" must be non-blank"); return v; }
    private static void positive(double v,String f){ if(!Double.isFinite(v)||v<=0) throw new IllegalArgumentException(f+" must be positive and finite"); }
}
