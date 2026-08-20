package com.spacesim.world;

import com.spacesim.content.Stage18ManufacturingCatalog;
import com.spacesim.content.Stage18ManufacturingCatalog.ComponentRecipeDefinition;
import com.spacesim.content.Stage18RefiningCatalog;
import com.spacesim.content.Stage18RefiningCatalog.RefiningRecipeDefinition;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.ExportHandlingStatus;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.ExtractionCapacity;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.ProcessKind;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.StationProcessCapacity;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteEvaluator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Non-reserved Stage-20E physical supply-throughput upper-bound closure. */
public final class Stage20TheoreticalSupplyThroughputAnalyzer {
    private Stage20TheoreticalSupplyThroughputAnalyzer() { throw new AssertionError("No instances"); }

    /** Calibrated physical time boundary for intermediate production logistics. */
    public record AnalysisProfile(String version, double maxIntermediateRouteTimeS) {
        public AnalysisProfile { version=text(version,"version"); positive(maxIntermediateRouteTimeS,"maxIntermediateRouteTimeS"); }
    }

    /** Commodity capacity at one concrete producer system. */
    public record SupplyKey(String commodityId, StarSystemId systemId) implements Comparable<SupplyKey> {
        public SupplyKey { commodityId=text(commodityId,"commodityId"); Objects.requireNonNull(systemId,"systemId"); }
        @Override public int compareTo(SupplyKey other){ int c=commodityId.compareTo(other.commodityId); return c!=0?c:systemId.compareTo(other.systemId); }
    }

    /** Machine-readable throughput closure. */
    public record SupplyThroughputReport(String profileVersion, Map<SupplyKey,Double> capacityKgPerSecondBySupply,
            Set<String> unresolvedExtractionSiteIds, List<ProcessThroughputEvidence> processEvidence) {
        public SupplyThroughputReport {
            profileVersion=text(profileVersion,"profileVersion");
            Objects.requireNonNull(capacityKgPerSecondBySupply,"capacityKgPerSecondBySupply");
            TreeMap<SupplyKey,Double> caps=new TreeMap<>();
            for(var e:capacityKgPerSecondBySupply.entrySet()){ Objects.requireNonNull(e.getKey()); positive(e.getValue(),"supply capacity"); caps.put(e.getKey(),e.getValue()); }
            capacityKgPerSecondBySupply=Collections.unmodifiableMap(caps);
            Objects.requireNonNull(unresolvedExtractionSiteIds,"unresolvedExtractionSiteIds");
            TreeSet<String> unresolved=new TreeSet<>(); for(String id:unresolvedExtractionSiteIds) unresolved.add(text(id,"unresolved site"));
            unresolvedExtractionSiteIds=Collections.unmodifiableSet(unresolved);
            Objects.requireNonNull(processEvidence,"processEvidence"); ArrayList<ProcessThroughputEvidence> ev=new ArrayList<>(processEvidence);
            if(ev.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("null process evidence");
            ev.sort(Comparator.comparing(ProcessThroughputEvidence::systemId).thenComparing(ProcessThroughputEvidence::stationPlacementId).thenComparing(ProcessThroughputEvidence::processId));
            processEvidence=List.copyOf(ev);
        }
        public double capacityKgPerSecond(String commodityId, StarSystemId systemId){ return capacityKgPerSecondBySupply.getOrDefault(new SupplyKey(commodityId,systemId),0d); }
    }

    /** Capacity evidence for one physical process row after input delivery constraints. */
    public record ProcessThroughputEvidence(StarSystemId systemId, String stationPlacementId, String processId,
            String outputCommodityId, double processAndStationCeilingKgPerSecond, double inputLimitedOutputKgPerSecond) {
        public ProcessThroughputEvidence {
            Objects.requireNonNull(systemId,"systemId"); stationPlacementId=text(stationPlacementId,"stationPlacementId");
            processId=text(processId,"processId"); outputCommodityId=text(outputCommodityId,"outputCommodityId");
            positive(processAndStationCeilingKgPerSecond,"process ceiling"); nonNegative(inputLimitedOutputKgPerSecond,"input limited output");
            if(inputLimitedOutputKgPerSecond>processAndStationCeilingKgPerSecond+1e-9) throw new IllegalArgumentException("input output exceeds process ceiling");
        }
    }

    /** Computes resolved feedstock -> material -> component physical throughput closure. */
    public static SupplyThroughputReport analyze(GalaxyTopology topology, AnalysisProfile profile, RouteEvaluator routes,
            List<ExtractionCapacity> extractionCapacities, List<StationProcessCapacity> processCapacities,
            Stage18RefiningCatalog refining, Stage18ManufacturingCatalog manufacturing) {
        Objects.requireNonNull(topology,"topology"); Objects.requireNonNull(profile,"profile"); Objects.requireNonNull(routes,"routes");
        Objects.requireNonNull(extractionCapacities,"extractionCapacities"); Objects.requireNonNull(processCapacities,"processCapacities");
        Objects.requireNonNull(refining,"refining"); Objects.requireNonNull(manufacturing,"manufacturing");
        TreeMap<SupplyKey,Double> supply=new TreeMap<>(); TreeSet<String> unresolved=new TreeSet<>();
        for(ExtractionCapacity row:extractionCapacities){ Objects.requireNonNull(row); if(topology.findSystem(row.systemId()).isEmpty()) throw new IllegalArgumentException("source outside topology");
            if(row.exportHandlingStatus()!=ExportHandlingStatus.RESOLVED || row.sustainableExportKgPerSecond().isEmpty()){ unresolved.add(row.siteId()); continue; }
            add(supply,new SupplyKey(row.outputCommodityId(),row.systemId()),row.sustainableExportKgPerSecond().orElseThrow()); }
        ArrayList<ProcessThroughputEvidence> evidence=new ArrayList<>();
        for(StationProcessCapacity row:ordered(processCapacities,ProcessKind.REFINING)){
            RefiningRecipeDefinition recipe=refining.findRecipe(row.processId()); if(recipe==null) throw new IllegalArgumentException("unknown refining recipe: "+row.processId());
            double output=row.theoreticalExportableOutputKgPerSecond();
            for(var input:recipe.inputs()) output=Math.min(output,deliverable(topology,routes,supply,input.commodityId(),row.systemId(),profile.maxIntermediateRouteTimeS())*recipe.outputMassFraction()/input.fractionOfInputMass());
            record(evidence,supply,row,output);
        }
        for(StationProcessCapacity row:ordered(processCapacities,ProcessKind.COMPONENT_MANUFACTURING)){
            ComponentRecipeDefinition recipe=manufacturing.findComponentRecipe(row.processId()); if(recipe==null) throw new IllegalArgumentException("unknown component recipe: "+row.processId());
            double output=row.theoreticalExportableOutputKgPerSecond();
            for(var input:recipe.inputs()) output=Math.min(output,deliverable(topology,routes,supply,input.commodityId(),row.systemId(),profile.maxIntermediateRouteTimeS())/input.fractionOfOutputMass());
            record(evidence,supply,row,output);
        }
        return new SupplyThroughputReport(profile.version(),supply,unresolved,evidence);
    }

    private static List<StationProcessCapacity> ordered(List<StationProcessCapacity> rows, ProcessKind kind){
        return rows.stream().filter(r->r.processKind()==kind).sorted(PROCESS_ORDER).toList();
    }
    private static void record(List<ProcessThroughputEvidence> evidence, Map<SupplyKey,Double> supply, StationProcessCapacity row, double value){
        double output=normalize(value); evidence.add(new ProcessThroughputEvidence(row.systemId(),row.stationPlacementId(),row.processId(),row.outputCommodityId(),row.theoreticalExportableOutputKgPerSecond(),output));
        if(output>0) add(supply,new SupplyKey(row.outputCommodityId(),row.systemId()),output);
    }
    private static double deliverable(GalaxyTopology topology, RouteEvaluator routes, Map<SupplyKey,Double> supply,
            String commodityId, StarSystemId destination, double maxTime){
        double total=0;
        for(var e:supply.entrySet()) if(e.getKey().commodityId().equals(commodityId)){
            Optional<RouteAssessment> maybe=routes.assess(e.getKey().systemId(),destination); if(maybe.isEmpty()) continue;
            RouteAssessment route=validate(topology,e.getKey().systemId(),destination,maybe.orElseThrow()); if(route.travelTimeS()>maxTime) continue;
            total+=Math.min(e.getValue(),route.sustainableCargoThroughputKgPerSecond()); if(!Double.isFinite(total)) throw new IllegalStateException("throughput overflow");
        }
        return total;
    }
    private static RouteAssessment validate(GalaxyTopology topology, StarSystemId origin, StarSystemId destination, RouteAssessment route){
        List<StarSystemId> path=route.orderedSystems();
        if(!path.get(0).equals(origin)||!path.get(path.size()-1).equals(destination)) throw new IllegalArgumentException("route endpoints mismatch");
        if(origin.equals(destination)){ if(path.size()!=1) throw new IllegalArgumentException("same-system route must be one node"); return route; }
        for(int i=0;i<path.size()-1;i++) if(!topology.neighbors(path.get(i)).contains(path.get(i+1))) throw new IllegalArgumentException("throughput route contains non-neighbor shortcut");
        return route;
    }
    private static void add(Map<SupplyKey,Double> supply, SupplyKey key, double value){ positive(value,"capacity"); supply.merge(key,value,(a,b)->{double s=a+b;if(!Double.isFinite(s))throw new IllegalStateException("capacity overflow");return s;}); }
    private static double normalize(double v){ if(!Double.isFinite(v)||v< -1e-9) throw new IllegalStateException("invalid throughput"); return Math.max(0,v); }
    private static String text(String v,String f){ if(v==null||v.isBlank()) throw new IllegalArgumentException(f+" must be non-blank"); return v; }
    private static void positive(double v,String f){ if(!Double.isFinite(v)||v<=0) throw new IllegalArgumentException(f+" must be positive and finite"); }
    private static void nonNegative(double v,String f){ if(!Double.isFinite(v)||v<0) throw new IllegalArgumentException(f+" must be non-negative and finite"); }
    private static final Comparator<StationProcessCapacity> PROCESS_ORDER=Comparator.comparing(StationProcessCapacity::systemId).thenComparing(StationProcessCapacity::stationPlacementId).thenComparing(StationProcessCapacity::facilityDefinitionId).thenComparing(StationProcessCapacity::processId);
}
