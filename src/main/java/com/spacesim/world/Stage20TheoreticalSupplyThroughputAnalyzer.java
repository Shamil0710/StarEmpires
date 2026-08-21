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

/**
 * Non-reserved Stage-20E physical supply-throughput upper-bound closure.
 *
 * <p>Besides aggregate capacity, the report retains every candidate input-supply key and the exact
 * physical route assessment used to admit, time-reject or reject that key. This is provenance for a
 * later Stage-20F reservation; it does not reserve producer capacity or freight.</p>
 */
public final class Stage20TheoreticalSupplyThroughputAnalyzer {
    private static final double EPSILON = 1.0e-9d;

    private Stage20TheoreticalSupplyThroughputAnalyzer() { throw new AssertionError("No instances"); }

    /**
     * Calibrated physical time boundary for intermediate production logistics.
     *
     * @param version stable analysis profile version
     * @param maxIntermediateRouteTimeS maximum accepted physical input-delivery route time
     */
    public record AnalysisProfile(String version, double maxIntermediateRouteTimeS) {
        /**
         * Validates one immutable analysis profile.
         *
         * @param version stable analysis profile version
         * @param maxIntermediateRouteTimeS maximum accepted physical input-delivery route time
         */
        public AnalysisProfile { version=text(version,"version"); positive(maxIntermediateRouteTimeS,"maxIntermediateRouteTimeS"); }
    }

    /**
     * Commodity capacity at one concrete producer system.
     *
     * @param commodityId authoritative Stage-18 commodity ID
     * @param systemId physical producer system
     */
    public record SupplyKey(String commodityId, StarSystemId systemId) implements Comparable<SupplyKey> {
        /**
         * Validates one immutable supply key.
         *
         * @param commodityId authoritative Stage-18 commodity ID
         * @param systemId physical producer system
         */
        public SupplyKey { commodityId=text(commodityId,"commodityId"); Objects.requireNonNull(systemId,"systemId"); }

        /**
         * Orders supply identities by commodity and then system.
         *
         * @param other other supply key
         * @return deterministic comparison result
         */
        @Override public int compareTo(SupplyKey other){ int c=commodityId.compareTo(other.commodityId); return c!=0?c:systemId.compareTo(other.systemId); }
    }

    /** Admission state of one candidate physical input-supply route. */
    public enum RouteAdmissionStatus {
        /** The route exists inside the time boundary and contributes its finite deliverable capacity. */
        ADMITTED,
        /** No physical route assessment exists for this candidate supply key. */
        NO_FEASIBLE_ROUTE,
        /** A physical route exists, but its travel time exceeds the explicit analysis boundary. */
        ROUTE_TIME_EXCEEDED
    }

    /**
     * One candidate supply key and its exact non-reserved physical route evidence.
     *
     * @param supplyKey commodity/system source identity used by the Stage-20E closure
     * @param sourceCapacityKgPerSecond finite non-reserved source capacity visible at this step
     * @param route physical route assessment, absent only when no feasible route exists
     * @param status route admission state
     * @param admittedInputKgPerSecond finite capacity admitted through this route, or zero
     */
    public record InputSupplyRouteEvidence(
            SupplyKey supplyKey,
            double sourceCapacityKgPerSecond,
            Optional<RouteAssessment> route,
            RouteAdmissionStatus status,
            double admittedInputKgPerSecond) {
        /**
         * Validates one immutable candidate input-supply route.
         *
         * @param supplyKey commodity/system source identity
         * @param sourceCapacityKgPerSecond finite non-reserved source capacity
         * @param route physical route assessment when one exists
         * @param status route admission state
         * @param admittedInputKgPerSecond admitted finite input capacity
         */
        public InputSupplyRouteEvidence {
            Objects.requireNonNull(supplyKey, "supplyKey");
            positive(sourceCapacityKgPerSecond, "sourceCapacityKgPerSecond");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(status, "status");
            nonNegative(admittedInputKgPerSecond, "admittedInputKgPerSecond");
            if (route.isEmpty()) {
                if (status != RouteAdmissionStatus.NO_FEASIBLE_ROUTE
                        || admittedInputKgPerSecond != 0d) {
                    throw new IllegalArgumentException(
                            "missing route must be non-admitted NO_FEASIBLE_ROUTE evidence");
                }
            } else {
                RouteAssessment physicalRoute = route.orElseThrow();
                if (!physicalRoute.orderedSystems().get(0).equals(supplyKey.systemId())) {
                    throw new IllegalArgumentException("input route must start at its supply system");
                }
                if (status == RouteAdmissionStatus.NO_FEASIBLE_ROUTE) {
                    throw new IllegalArgumentException("present route cannot be NO_FEASIBLE_ROUTE");
                }
                if (status == RouteAdmissionStatus.ROUTE_TIME_EXCEEDED) {
                    if (admittedInputKgPerSecond != 0d) {
                        throw new IllegalArgumentException("time-rejected route cannot admit capacity");
                    }
                } else {
                    double expected = Math.min(
                            sourceCapacityKgPerSecond,
                            physicalRoute.sustainableCargoThroughputKgPerSecond());
                    close(admittedInputKgPerSecond, expected, "admittedInputKgPerSecond");
                    positive(admittedInputKgPerSecond, "admittedInputKgPerSecond");
                }
            }
        }
    }

    /**
     * Complete candidate-supply evidence for one required process input commodity.
     *
     * @param commodityId required Stage-18 input commodity
     * @param inputKgPerOutputKg recipe input mass required per kilogram of process output
     * @param maxRouteTimeS explicit physical route-time admission boundary
     * @param supplyRoutes every visible candidate supply key and its route admission evidence
     * @param admittedInputKgPerSecond summed non-reserved input capacity on admitted routes
     * @param inputSupportedOutputKgPerSecond output supported by this input before process ceiling
     */
    public record ProcessInputThroughputEvidence(
            String commodityId,
            double inputKgPerOutputKg,
            double maxRouteTimeS,
            List<InputSupplyRouteEvidence> supplyRoutes,
            double admittedInputKgPerSecond,
            double inputSupportedOutputKgPerSecond) {
        /**
         * Validates and canonicalizes one immutable process-input evidence row.
         *
         * @param commodityId required Stage-18 input commodity
         * @param inputKgPerOutputKg recipe input mass per output mass
         * @param maxRouteTimeS explicit route-time boundary
         * @param supplyRoutes candidate source/route evidence
         * @param admittedInputKgPerSecond summed admitted input capacity
         * @param inputSupportedOutputKgPerSecond input-supported process output
         */
        public ProcessInputThroughputEvidence {
            commodityId = text(commodityId, "commodityId");
            positive(inputKgPerOutputKg, "inputKgPerOutputKg");
            positive(maxRouteTimeS, "maxRouteTimeS");
            Objects.requireNonNull(supplyRoutes, "supplyRoutes");
            ArrayList<InputSupplyRouteEvidence> routes = new ArrayList<>(supplyRoutes);
            if (routes.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("supplyRoutes cannot contain nulls");
            }
            routes.sort(Comparator.comparing(InputSupplyRouteEvidence::supplyKey));
            TreeSet<SupplyKey> keys = new TreeSet<>();
            double admitted = 0d;
            for (InputSupplyRouteEvidence candidate : routes) {
                if (!candidate.supplyKey().commodityId().equals(commodityId)
                        || !keys.add(candidate.supplyKey())) {
                    throw new IllegalArgumentException(
                            "process input routes must be unique and match their input commodity");
                }
                if (candidate.route().isPresent()) {
                    boolean exceeds = candidate.route().orElseThrow().travelTimeS() > maxRouteTimeS;
                    RouteAdmissionStatus expected = exceeds
                            ? RouteAdmissionStatus.ROUTE_TIME_EXCEEDED
                            : RouteAdmissionStatus.ADMITTED;
                    if (candidate.status() != expected) {
                        throw new IllegalArgumentException(
                                "input route admission status differs from its route-time boundary");
                    }
                }
                admitted = finiteAdd(admitted, candidate.admittedInputKgPerSecond());
            }
            supplyRoutes = List.copyOf(routes);
            nonNegative(admittedInputKgPerSecond, "admittedInputKgPerSecond");
            nonNegative(inputSupportedOutputKgPerSecond, "inputSupportedOutputKgPerSecond");
            close(admittedInputKgPerSecond, admitted, "admittedInputKgPerSecond");
            close(inputSupportedOutputKgPerSecond,
                    admittedInputKgPerSecond / inputKgPerOutputKg,
                    "inputSupportedOutputKgPerSecond");
        }
    }

    /**
     * Machine-readable throughput closure.
     *
     * @param profileVersion exact analysis profile version
     * @param capacityKgPerSecondBySupply resolved non-reserved physical capacity by supply identity
     * @param unresolvedExtractionSiteIds extraction sites whose export handling remains unresolved
     * @param processEvidence deterministic process-capacity evidence after physical input constraints
     */
    public record SupplyThroughputReport(String profileVersion, Map<SupplyKey,Double> capacityKgPerSecondBySupply,
            Set<String> unresolvedExtractionSiteIds, List<ProcessThroughputEvidence> processEvidence) {
        /**
         * Validates and freezes one throughput report.
         *
         * @param profileVersion exact analysis profile version
         * @param capacityKgPerSecondBySupply resolved non-reserved physical capacity by supply identity
         * @param unresolvedExtractionSiteIds extraction sites whose export handling remains unresolved
         * @param processEvidence deterministic process-capacity evidence after physical input constraints
         */
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
            ev.sort(Comparator.comparing(ProcessThroughputEvidence::systemId)
                    .thenComparing(ProcessThroughputEvidence::stationPlacementId)
                    .thenComparing(ProcessThroughputEvidence::facilityDefinitionId)
                    .thenComparing(ProcessThroughputEvidence::processId)
                    .thenComparing(ProcessThroughputEvidence::outputCommodityId));
            processEvidence=List.copyOf(ev);
        }

        /**
         * Returns resolved capacity for one commodity/system pair.
         *
         * @param commodityId authoritative Stage-18 commodity ID
         * @param systemId physical producer system
         * @return resolved capacity in kilograms per second, or zero when absent
         */
        public double capacityKgPerSecond(String commodityId, StarSystemId systemId){ return capacityKgPerSecondBySupply.getOrDefault(new SupplyKey(commodityId,systemId),0d); }
    }

    /**
     * Capacity evidence for one physical process row after input delivery constraints.
     *
     * @param systemId processing system
     * @param stationPlacementId physical station placement identity
     * @param facilityDefinitionId exact installed Stage-18 facility definition providing the process
     * @param processId authoritative Stage-18 process/recipe identity
     * @param outputCommodityId produced commodity
     * @param processAndStationCeilingKgPerSecond pristine process/station export ceiling
     * @param inputEvidence exact per-input candidate source/route evidence
     * @param inputLimitedOutputKgPerSecond output ceiling after physical input-delivery constraints
     */
    public record ProcessThroughputEvidence(
            StarSystemId systemId,
            String stationPlacementId,
            String facilityDefinitionId,
            String processId,
            String outputCommodityId,
            double processAndStationCeilingKgPerSecond,
            List<ProcessInputThroughputEvidence> inputEvidence,
            double inputLimitedOutputKgPerSecond) {
        /**
         * Validates one immutable process evidence row.
         *
         * @param systemId processing system
         * @param stationPlacementId physical station placement identity
         * @param facilityDefinitionId exact installed Stage-18 facility definition
         * @param processId authoritative Stage-18 process/recipe identity
         * @param outputCommodityId produced commodity
         * @param processAndStationCeilingKgPerSecond pristine process/station export ceiling
         * @param inputEvidence exact per-input candidate source/route evidence
         * @param inputLimitedOutputKgPerSecond output ceiling after physical input-delivery constraints
         */
        public ProcessThroughputEvidence {
            Objects.requireNonNull(systemId,"systemId"); stationPlacementId=text(stationPlacementId,"stationPlacementId");
            facilityDefinitionId=text(facilityDefinitionId,"facilityDefinitionId");
            processId=text(processId,"processId"); outputCommodityId=text(outputCommodityId,"outputCommodityId");
            positive(processAndStationCeilingKgPerSecond,"process ceiling"); nonNegative(inputLimitedOutputKgPerSecond,"input limited output");
            if(inputLimitedOutputKgPerSecond>processAndStationCeilingKgPerSecond+1e-9) throw new IllegalArgumentException("input output exceeds process ceiling");
            Objects.requireNonNull(inputEvidence, "inputEvidence");
            ArrayList<ProcessInputThroughputEvidence> inputs = new ArrayList<>(inputEvidence);
            if (inputs.isEmpty() || inputs.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("process evidence requires non-empty input evidence");
            }
            inputs.sort(Comparator.comparing(ProcessInputThroughputEvidence::commodityId));
            TreeSet<String> commodities = new TreeSet<>();
            double expectedOutput = processAndStationCeilingKgPerSecond;
            for (ProcessInputThroughputEvidence input : inputs) {
                if (!commodities.add(input.commodityId())) {
                    throw new IllegalArgumentException("process input commodities must be unique");
                }
                for (InputSupplyRouteEvidence route : input.supplyRoutes()) {
                    if (route.route().isPresent()
                            && !route.route().orElseThrow().orderedSystems()
                            .get(route.route().orElseThrow().orderedSystems().size() - 1)
                            .equals(systemId)) {
                        throw new IllegalArgumentException("input route must end at the processing system");
                    }
                }
                expectedOutput = Math.min(expectedOutput, input.inputSupportedOutputKgPerSecond());
            }
            inputEvidence = List.copyOf(inputs);
            close(inputLimitedOutputKgPerSecond, expectedOutput, "inputLimitedOutputKgPerSecond");
        }
    }

    /**
     * Computes resolved feedstock -> material -> component physical throughput closure.
     *
     * @param topology authoritative explicit neighbor topology
     * @param profile calibrated intermediate-route analysis profile
     * @param routes physical route evaluator
     * @param extractionCapacities generated extraction/export upper bounds
     * @param processCapacities station refining/manufacturing upper bounds
     * @param refining authoritative Stage-18 refining recipes
     * @param manufacturing authoritative Stage-18 component manufacturing recipes
     * @return deterministic non-reserved physical supply-throughput report
     */
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
            ArrayList<ProcessInputThroughputEvidence> inputs = new ArrayList<>();
            for(var input:recipe.inputs()) {
                ProcessInputThroughputEvidence inputEvidence = inputEvidence(
                        topology,
                        routes,
                        supply,
                        input.commodityId(),
                        input.fractionOfInputMass() / recipe.outputMassFraction(),
                        row.systemId(),
                        profile.maxIntermediateRouteTimeS());
                inputs.add(inputEvidence);
                output=Math.min(output,inputEvidence.inputSupportedOutputKgPerSecond());
            }
            record(evidence,supply,row,inputs,output);
        }
        for(StationProcessCapacity row:ordered(processCapacities,ProcessKind.COMPONENT_MANUFACTURING)){
            ComponentRecipeDefinition recipe=manufacturing.findComponentRecipe(row.processId()); if(recipe==null) throw new IllegalArgumentException("unknown component recipe: "+row.processId());
            double output=row.theoreticalExportableOutputKgPerSecond();
            ArrayList<ProcessInputThroughputEvidence> inputs = new ArrayList<>();
            for(var input:recipe.inputs()) {
                ProcessInputThroughputEvidence inputEvidence = inputEvidence(
                        topology,
                        routes,
                        supply,
                        input.commodityId(),
                        input.fractionOfOutputMass(),
                        row.systemId(),
                        profile.maxIntermediateRouteTimeS());
                inputs.add(inputEvidence);
                output=Math.min(output,inputEvidence.inputSupportedOutputKgPerSecond());
            }
            record(evidence,supply,row,inputs,output);
        }
        return new SupplyThroughputReport(profile.version(),supply,unresolved,evidence);
    }

    private static List<StationProcessCapacity> ordered(List<StationProcessCapacity> rows, ProcessKind kind){
        return rows.stream().filter(r->r.processKind()==kind).sorted(PROCESS_ORDER).toList();
    }
    private static void record(List<ProcessThroughputEvidence> evidence, Map<SupplyKey,Double> supply,
            StationProcessCapacity row, List<ProcessInputThroughputEvidence> inputs, double value){
        double output=normalize(value); evidence.add(new ProcessThroughputEvidence(row.systemId(),row.stationPlacementId(),row.facilityDefinitionId(),row.processId(),row.outputCommodityId(),row.theoreticalExportableOutputKgPerSecond(),inputs,output));
        if(output>0) add(supply,new SupplyKey(row.outputCommodityId(),row.systemId()),output);
    }
    private static ProcessInputThroughputEvidence inputEvidence(
            GalaxyTopology topology,
            RouteEvaluator routes,
            Map<SupplyKey,Double> supply,
            String commodityId,
            double inputKgPerOutputKg,
            StarSystemId destination,
            double maxTime){
        ArrayList<InputSupplyRouteEvidence> candidates = new ArrayList<>();
        double total=0d;
        for(var e:supply.entrySet()) if(e.getKey().commodityId().equals(commodityId)){
            Optional<RouteAssessment> maybe=routes.assess(e.getKey().systemId(),destination);
            if(maybe.isEmpty()) {
                candidates.add(new InputSupplyRouteEvidence(
                        e.getKey(),
                        e.getValue(),
                        Optional.empty(),
                        RouteAdmissionStatus.NO_FEASIBLE_ROUTE,
                        0d));
                continue;
            }
            RouteAssessment route=validate(topology,e.getKey().systemId(),destination,maybe.orElseThrow());
            if(route.travelTimeS()>maxTime) {
                candidates.add(new InputSupplyRouteEvidence(
                        e.getKey(),
                        e.getValue(),
                        Optional.of(route),
                        RouteAdmissionStatus.ROUTE_TIME_EXCEEDED,
                        0d));
                continue;
            }
            double admitted=Math.min(e.getValue(),route.sustainableCargoThroughputKgPerSecond());
            candidates.add(new InputSupplyRouteEvidence(
                    e.getKey(),
                    e.getValue(),
                    Optional.of(route),
                    RouteAdmissionStatus.ADMITTED,
                    admitted));
            total=finiteAdd(total,admitted);
        }
        double admitted=normalize(total);
        return new ProcessInputThroughputEvidence(
                commodityId,
                inputKgPerOutputKg,
                maxTime,
                candidates,
                admitted,
                normalize(admitted/inputKgPerOutputKg));
    }
    private static RouteAssessment validate(GalaxyTopology topology, StarSystemId origin, StarSystemId destination, RouteAssessment route){
        List<StarSystemId> path=route.orderedSystems();
        if(!path.get(0).equals(origin)||!path.get(path.size()-1).equals(destination)) throw new IllegalArgumentException("route endpoints mismatch");
        if(origin.equals(destination)){ if(path.size()!=1) throw new IllegalArgumentException("same-system route must be one node"); return route; }
        for(int i=0;i<path.size()-1;i++) if(!topology.neighbors(path.get(i)).contains(path.get(i+1))) throw new IllegalArgumentException("throughput route contains non-neighbor shortcut");
        return route;
    }
    private static void add(Map<SupplyKey,Double> supply, SupplyKey key, double value){ positive(value,"capacity"); supply.merge(key,value,(a,b)->{double s=a+b;if(!Double.isFinite(s))throw new IllegalStateException("capacity overflow");return s;}); }
    private static double finiteAdd(double first,double second){ double result=first+second;if(!Double.isFinite(result))throw new IllegalStateException("throughput overflow");return result; }
    private static double normalize(double v){ if(!Double.isFinite(v)||v< -1e-9) throw new IllegalStateException("invalid throughput"); return Math.max(0,v); }
    private static void close(double actual,double expected,String field){ double scale=Math.max(1d,Math.max(Math.abs(actual),Math.abs(expected)));if(Math.abs(actual-expected)>EPSILON*scale)throw new IllegalArgumentException(field+" differs from derived physical evidence"); }
    private static String text(String v,String f){ if(v==null||v.isBlank()) throw new IllegalArgumentException(f+" must be non-blank"); return v; }
    private static void positive(double v,String f){ if(!Double.isFinite(v)||v<=0) throw new IllegalArgumentException(f+" must be positive and finite"); }
    private static void nonNegative(double v,String f){ if(!Double.isFinite(v)||v<0) throw new IllegalArgumentException(f+" must be non-negative and finite"); }
    private static final Comparator<StationProcessCapacity> PROCESS_ORDER=Comparator.comparing(StationProcessCapacity::systemId).thenComparing(StationProcessCapacity::stationPlacementId).thenComparing(StationProcessCapacity::facilityDefinitionId).thenComparing(StationProcessCapacity::processId);
}
