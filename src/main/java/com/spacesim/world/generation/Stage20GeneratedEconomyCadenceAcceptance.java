package com.spacesim.world.generation;

import com.spacesim.content.Stage18ManufacturingCatalogLoader;
import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.content.Stage18ShipyardCatalog.HullPhysicalProfile;
import com.spacesim.content.Stage18ShipyardCatalog.PhysicalInputDefinition;
import com.spacesim.content.Stage18ShipyardCatalogLoader;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator.ProcessKind;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.ProcessOperatingDemand;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.StationKey;
import com.spacesim.world.Stage20IndustrialInitialInventoryPlan.CommodityBufferEvidence;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.FreightDemandEvidence;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.IndustrialFreightReport;
import com.spacesim.world.Stage20IndustrialInputReservationPlan.ProcessSelectionKey;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.OperationalSpecializationReport;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.RuntimeBridgeRequirement;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator.FreightCycleAssessment;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.ResourceOccurrence;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.ProcessThroughputEvidence;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Stage-20J acceptance over generated extraction, production, freight, buffers and shipyard supply.
 *
 * <p>The acceptance consumes the exact resolved generated world and the closed Stage-20F operational
 * specialization report. It does not create inventory, production, cargo, ships or ownership. Every
 * cadence is a projection of finite Stage-18 recipes/reserves and the already fitted Stage-20
 * movement/handling model. The four explicit runtime bridge requirements therefore remain open and
 * unchanged.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20GeneratedEconomyCadenceAcceptance {
    /** Stable generated economy-cadence acceptance version. */
    public static final String CURRENT_VERSION = "stage20j.generated-economy-cadence-acceptance.v1";
    private static final double EPSILON = 1.0e-9d;

    private Stage20GeneratedEconomyCadenceAcceptance() {
        throw new AssertionError("No instances");
    }

    /** Final Stage-20J result state. */
    public enum Status {
        /** Every required generated/operational cadence has explicit physical evidence. */ ACCEPTED
    }

    /** Finite generated extraction cadence for one commodity/system supply identity. */
    public record ExtractionCadence(
            SupplyKey supply,
            double mineOutputKgPerSecond,
            double finiteRecoverableMassKg,
            double reserveEnduranceSeconds) {
        /**
         * Validates one finite extraction row.
         *
         * @param supply generated commodity/system supply identity
         * @param mineOutputKgPerSecond physical extraction output rate
         * @param finiteRecoverableMassKg finite useful occurrence mass
         * @param reserveEnduranceSeconds reserve mass divided by output rate
         */
        public ExtractionCadence {
            Objects.requireNonNull(supply, "supply");
            positive(mineOutputKgPerSecond, "mineOutputKgPerSecond");
            positive(finiteRecoverableMassKg, "finiteRecoverableMassKg");
            positive(reserveEnduranceSeconds, "reserveEnduranceSeconds");
            close(finiteRecoverableMassKg / mineOutputKgPerSecond,
                    reserveEnduranceSeconds, "reserveEnduranceSeconds");
        }
    }

    /** Generated Stage-18 process throughput before caller-selected operational reservation. */
    public record GeneratedProcessCadence(
            ProcessSelectionKey process,
            ProcessKind processKind,
            double aggregateInputConsumptionKgPerSecond,
            double outputKgPerSecond,
            double oneFreighterPayloadProductionSeconds) {
        /**
         * Validates one generated process cadence.
         *
         * @param process exact generated process identity
         * @param processKind exact Stage-18 process kind
         * @param aggregateInputConsumptionKgPerSecond summed recipe input rate
         * @param outputKgPerSecond input-limited output rate
         * @param oneFreighterPayloadProductionSeconds time to produce one payload
         */
        public GeneratedProcessCadence {
            Objects.requireNonNull(process, "process");
            Objects.requireNonNull(processKind, "processKind");
            positive(aggregateInputConsumptionKgPerSecond,
                    "aggregateInputConsumptionKgPerSecond");
            positive(outputKgPerSecond, "outputKgPerSecond");
            positive(oneFreighterPayloadProductionSeconds,
                    "oneFreighterPayloadProductionSeconds");
        }
    }

    /** Exact selected Stage-20F process cadence and owner. */
    public record OperationalProcessCadence(
            ProcessSelectionKey process,
            String stableFactionId,
            ProcessKind processKind,
            double aggregateInputConsumptionKgPerSecond,
            double outputKgPerSecond,
            double oneFreighterPayloadProductionSeconds) {
        /**
         * Validates one selected operational process cadence.
         *
         * @param process exact selected process identity
         * @param stableFactionId exact owner
         * @param processKind exact Stage-18 process kind
         * @param aggregateInputConsumptionKgPerSecond summed reserved input rate
         * @param outputKgPerSecond selected active output rate
         * @param oneFreighterPayloadProductionSeconds time to produce one payload
         */
        public OperationalProcessCadence {
            Objects.requireNonNull(process, "process");
            stableFactionId = requireText(stableFactionId, "stableFactionId");
            Objects.requireNonNull(processKind, "processKind");
            positive(aggregateInputConsumptionKgPerSecond,
                    "aggregateInputConsumptionKgPerSecond");
            positive(outputKgPerSecond, "outputKgPerSecond");
            positive(oneFreighterPayloadProductionSeconds,
                    "oneFreighterPayloadProductionSeconds");
        }
    }

    /** Exact owned industrial input route cadence. */
    public record FreightCadence(
            String inputIdentity,
            String stableFactionId,
            List<StarSystemId> orderedSystems,
            int allocatedFreighters,
            double payloadMassKgPerFreighter,
            double oneWayDeliverySeconds,
            double roundTripCycleSeconds,
            double loadingAndUnloadingSeconds,
            double sustainableThroughputKgPerSecond,
            double reservedInputKgPerSecond) {
        /**
         * Validates one owned physical freight cadence.
         *
         * @param inputIdentity stable process/input/source identity
         * @param stableFactionId exact freight owner
         * @param orderedSystems loaded outbound neighbor route
         * @param allocatedFreighters exact assigned owned ship count
         * @param payloadMassKgPerFreighter delivered mass per trip
         * @param oneWayDeliverySeconds physical one-way delivery time
         * @param roundTripCycleSeconds physical ready-again round trip
         * @param loadingAndUnloadingSeconds endpoint handling overhead
         * @param sustainableThroughputKgPerSecond final route throughput
         * @param reservedInputKgPerSecond exact reserved industrial demand
         */
        public FreightCadence {
            inputIdentity = requireText(inputIdentity, "inputIdentity");
            stableFactionId = requireText(stableFactionId, "stableFactionId");
            orderedSystems = List.copyOf(Objects.requireNonNull(orderedSystems, "orderedSystems"));
            if (orderedSystems.size() < 2 || allocatedFreighters <= 0) {
                throw new IllegalArgumentException("industrial freight cadence must be remote and owned");
            }
            positive(payloadMassKgPerFreighter, "payloadMassKgPerFreighter");
            positive(oneWayDeliverySeconds, "oneWayDeliverySeconds");
            positive(roundTripCycleSeconds, "roundTripCycleSeconds");
            positive(loadingAndUnloadingSeconds, "loadingAndUnloadingSeconds");
            positive(sustainableThroughputKgPerSecond, "sustainableThroughputKgPerSecond");
            positive(reservedInputKgPerSecond, "reservedInputKgPerSecond");
            if (roundTripCycleSeconds <= oneWayDeliverySeconds
                    || sustainableThroughputKgPerSecond + EPSILON < reservedInputKgPerSecond) {
                throw new IllegalArgumentException("owned freight cadence cannot sustain its reservation");
            }
        }
    }

    /** Exact initial physical buffer endurance at one operational station. */
    public record BufferCadence(
            StationKey station,
            String commodityId,
            double availableMassKg,
            double consumptionKgPerSecond,
            double bufferDepletionSeconds,
            double requiredPipelineMassKg) {
        /**
         * Validates one finite non-restocking buffer.
         *
         * @param station exact generated station
         * @param commodityId exact Stage-18 commodity
         * @param availableMassKg canonical initial stored mass
         * @param consumptionKgPerSecond selected process consumption
         * @param bufferDepletionSeconds stored mass divided by consumption
         * @param requiredPipelineMassKg retained first-delivery pipeline mass
         */
        public BufferCadence {
            Objects.requireNonNull(station, "station");
            commodityId = requireText(commodityId, "commodityId");
            positive(availableMassKg, "availableMassKg");
            positive(consumptionKgPerSecond, "consumptionKgPerSecond");
            positive(bufferDepletionSeconds, "bufferDepletionSeconds");
            positive(requiredPipelineMassKg, "requiredPipelineMassKg");
            close(availableMassKg / consumptionKgPerSecond,
                    bufferDepletionSeconds, "bufferDepletionSeconds");
            if (availableMassKg + EPSILON < requiredPipelineMassKg) {
                throw new IllegalArgumentException("accepted buffer cannot be below pipeline-fill mass");
            }
        }
    }

    /** One Stage-18 hull-input delivery row for an active generated shipyard. */
    public record ConstructionInputCadence(
            String commodityId,
            double requiredMassKg,
            StarSystemId sourceSystemId,
            double sourceOutputKgPerSecond,
            double deliveredThroughputKgPerSecond,
            double oneWayDeliverySeconds,
            double roundTripCycleSeconds,
            double emptyPipelineSupplyEtaSeconds,
            double steadyStateReplenishmentSeconds) {
        /**
         * Validates one real source/route construction-input cadence.
         *
         * @param commodityId Stage-18 hull input commodity
         * @param requiredMassKg exact hull BOM mass
         * @param sourceSystemId selected generated source system
         * @param sourceOutputKgPerSecond physical source output rate
         * @param deliveredThroughputKgPerSecond source/route-capped throughput
         * @param oneWayDeliverySeconds physical first delivery latency
         * @param roundTripCycleSeconds ready-again one-freighter cycle
         * @param emptyPipelineSupplyEtaSeconds latency plus sustained mass delivery
         * @param steadyStateReplenishmentSeconds sustained mass delivery duration
         */
        public ConstructionInputCadence {
            commodityId = requireText(commodityId, "commodityId");
            positive(requiredMassKg, "requiredMassKg");
            Objects.requireNonNull(sourceSystemId, "sourceSystemId");
            positive(sourceOutputKgPerSecond, "sourceOutputKgPerSecond");
            positive(deliveredThroughputKgPerSecond, "deliveredThroughputKgPerSecond");
            positive(oneWayDeliverySeconds, "oneWayDeliverySeconds");
            positive(roundTripCycleSeconds, "roundTripCycleSeconds");
            positive(emptyPipelineSupplyEtaSeconds, "emptyPipelineSupplyEtaSeconds");
            positive(steadyStateReplenishmentSeconds, "steadyStateReplenishmentSeconds");
            close(requiredMassKg / deliveredThroughputKgPerSecond,
                    steadyStateReplenishmentSeconds, "steadyStateReplenishmentSeconds");
            close(oneWayDeliverySeconds + steadyStateReplenishmentSeconds,
                    emptyPipelineSupplyEtaSeconds, "emptyPipelineSupplyEtaSeconds");
        }
    }

    /** Conservative serial one-freighter construction supply cadence for one active yard. */
    public record ShipyardConstructionCadence(
            String yardInstanceId,
            StationKey station,
            String stableFactionId,
            String hullId,
            double hullInputMassKg,
            List<ConstructionInputCadence> inputs,
            double serialConstructionSupplyEtaSeconds,
            double serialShipyardReplenishmentSeconds) {
        /**
         * Validates one mass-closed shipyard supply projection.
         *
         * @param yardInstanceId exact installed yard identity
         * @param station exact generated station
         * @param stableFactionId exact yard owner
         * @param hullId exact Stage-18 hull physical profile
         * @param hullInputMassKg complete bare-hull input mass
         * @param inputs complete commodity delivery rows
         * @param serialConstructionSupplyEtaSeconds empty-pipeline serial supply ETA
         * @param serialShipyardReplenishmentSeconds steady-state serial replenishment
         */
        public ShipyardConstructionCadence {
            yardInstanceId = requireText(yardInstanceId, "yardInstanceId");
            Objects.requireNonNull(station, "station");
            stableFactionId = requireText(stableFactionId, "stableFactionId");
            hullId = requireText(hullId, "hullId");
            positive(hullInputMassKg, "hullInputMassKg");
            ArrayList<ConstructionInputCadence> copy = new ArrayList<>(
                    Objects.requireNonNull(inputs, "inputs"));
            copy.sort(Comparator.comparing(ConstructionInputCadence::commodityId));
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)
                    || copy.stream().map(ConstructionInputCadence::commodityId).distinct().count()
                    != copy.size()) {
                throw new IllegalArgumentException("construction inputs must be non-empty and unique");
            }
            inputs = List.copyOf(copy);
            positive(serialConstructionSupplyEtaSeconds, "serialConstructionSupplyEtaSeconds");
            positive(serialShipyardReplenishmentSeconds,
                    "serialShipyardReplenishmentSeconds");
            close(inputs.stream().mapToDouble(ConstructionInputCadence::requiredMassKg).sum(),
                    hullInputMassKg, "hullInputMassKg");
            close(inputs.stream().mapToDouble(
                            ConstructionInputCadence::emptyPipelineSupplyEtaSeconds).sum(),
                    serialConstructionSupplyEtaSeconds,
                    "serialConstructionSupplyEtaSeconds");
            close(inputs.stream().mapToDouble(
                            ConstructionInputCadence::steadyStateReplenishmentSeconds).sum(),
                    serialShipyardReplenishmentSeconds,
                    "serialShipyardReplenishmentSeconds");
        }
    }

    /** Measurable cross-sector surplus-to-deficit freight opportunity. */
    public record TradePotential(
            String commodityId,
            StarSystemId sourceSystemId,
            StarSystemId destinationSystemId,
            double sourceCapacityKgPerSecond,
            double destinationCapacityKgPerSecond,
            double comparativeCapacityAdvantageKgPerSecond,
            double oneFreighterDeliveredPotentialKgPerSecond,
            int ordinaryJumpHops) {
        /**
         * Validates one positive physical cross-sector trade opportunity.
         *
         * @param commodityId exact Stage-18 commodity
         * @param sourceSystemId higher-capacity source system
         * @param destinationSystemId lower-capacity destination system
         * @param sourceCapacityKgPerSecond source production capacity
         * @param destinationCapacityKgPerSecond destination production capacity
         * @param comparativeCapacityAdvantageKgPerSecond positive source advantage
         * @param oneFreighterDeliveredPotentialKgPerSecond route-capped potential
         * @param ordinaryJumpHops explicit neighbor-edge hop count
         */
        public TradePotential {
            commodityId = requireText(commodityId, "commodityId");
            Objects.requireNonNull(sourceSystemId, "sourceSystemId");
            Objects.requireNonNull(destinationSystemId, "destinationSystemId");
            positive(sourceCapacityKgPerSecond, "sourceCapacityKgPerSecond");
            nonNegative(destinationCapacityKgPerSecond, "destinationCapacityKgPerSecond");
            positive(comparativeCapacityAdvantageKgPerSecond,
                    "comparativeCapacityAdvantageKgPerSecond");
            positive(oneFreighterDeliveredPotentialKgPerSecond,
                    "oneFreighterDeliveredPotentialKgPerSecond");
            if (ordinaryJumpHops <= 0) {
                throw new IllegalArgumentException("trade potential must cross ordinary jump edges");
            }
            close(sourceCapacityKgPerSecond - destinationCapacityKgPerSecond,
                    comparativeCapacityAdvantageKgPerSecond,
                    "comparativeCapacityAdvantageKgPerSecond");
        }
    }

    /** Complete deterministic Stage-20J evidence. */
    public record AcceptanceReport(
            String version,
            long rootSeed,
            String resolvedProbeVersion,
            String specializationVersion,
            Status status,
            List<ExtractionCadence> extraction,
            List<GeneratedProcessCadence> generatedProcesses,
            List<OperationalProcessCadence> operationalProcesses,
            List<FreightCadence> freight,
            List<BufferCadence> buffers,
            List<ShipyardConstructionCadence> shipyards,
            List<TradePotential> tradePotential,
            Set<RuntimeBridgeRequirement> remainingRuntimeBridgeRequirements,
            boolean hiddenRestockUsed) {
        /**
         * Canonicalizes and validates complete cadence acceptance.
         *
         * @param version Stage-20J contract version
         * @param rootSeed exact generated root seed
         * @param resolvedProbeVersion exact resolved-world version
         * @param specializationVersion exact Stage-20F specialization version
         * @param status final acceptance state
         * @param extraction finite extraction rows
         * @param generatedProcesses generated recipe rows
         * @param operationalProcesses selected active process rows
         * @param freight exact owned industrial freight rows
         * @param buffers finite station buffer rows
         * @param shipyards active-yard construction supply rows
         * @param tradePotential measurable cross-sector trade rows
         * @param remainingRuntimeBridgeRequirements exact unchanged Stage-20F seams
         * @param hiddenRestockUsed whether any non-physical restock was credited
         */
        public AcceptanceReport {
            version = requireText(version, "version");
            resolvedProbeVersion = requireText(resolvedProbeVersion, "resolvedProbeVersion");
            specializationVersion = requireText(specializationVersion, "specializationVersion");
            Objects.requireNonNull(status, "status");
            extraction = sorted(extraction, Comparator.comparing(ExtractionCadence::supply));
            generatedProcesses = sorted(generatedProcesses,
                    Comparator.comparing(GeneratedProcessCadence::process));
            operationalProcesses = sorted(operationalProcesses,
                    Comparator.comparing(OperationalProcessCadence::process));
            freight = sorted(freight, Comparator.comparing(FreightCadence::inputIdentity));
            buffers = sorted(buffers, Comparator.comparing(BufferCadence::station)
                    .thenComparing(BufferCadence::commodityId));
            shipyards = sorted(shipyards, Comparator.comparing(ShipyardConstructionCadence::station)
                    .thenComparing(ShipyardConstructionCadence::yardInstanceId));
            tradePotential = sorted(tradePotential, Comparator.comparing(TradePotential::commodityId));
            remainingRuntimeBridgeRequirements = immutableBridgeRequirements(
                    remainingRuntimeBridgeRequirements);
            if (status != Status.ACCEPTED
                    || extraction.isEmpty()
                    || generatedProcesses.stream().noneMatch(value ->
                    value.processKind() == ProcessKind.REFINING)
                    || generatedProcesses.stream().noneMatch(value ->
                    value.processKind() == ProcessKind.COMPONENT_MANUFACTURING)
                    || operationalProcesses.isEmpty()
                    || freight.isEmpty()
                    || buffers.isEmpty()
                    || shipyards.isEmpty()
                    || tradePotential.isEmpty()
                    || hiddenRestockUsed) {
                throw new IllegalArgumentException(
                        "Stage-20J acceptance requires every physical cadence and no hidden restock");
            }
        }
    }

    /**
     * Evaluates the complete current Stage-20J cadence contract.
     *
     * @param resolved exact accepted generated world
     * @param specialization exact closed Stage-20F operational specialization
     * @return deterministic accepted cadence evidence
     */
    public static AcceptanceReport evaluate(
            ResolvedProbeResult resolved,
            OperationalSpecializationReport specialization) {
        ResolvedProbeResult world = requireAccepted(resolved);
        OperationalSpecializationReport industry = Objects.requireNonNull(
                specialization, "specialization");
        if (industry.rootSeed() != world.rootSeed()
                || !industry.resolvedProbeVersion().equals(world.version())
                || !industry.readyForRuntimeBridge()) {
            throw new IllegalArgumentException(
                    "Stage-20J requires matching closed Stage-20F operational authority");
        }

        SupplyThroughputReport supply = world.generation().supplyThroughput().orElseThrow();
        var currentProfile = Stage20RepresentativeGeneratedWorldProbeProfileV3.deriveCurrent();
        IndustrialFreightReport industrialFreight = industry.yardInstallation().inventory()
                .operatingState().freightOwnership();
        int maximumOwnedFreighters = industrialFreight.capacityProfile().maximumValidatedFreighters();
        Stage20PhysicalFreightRouteEvaluator routes = Stage20PhysicalFreightRouteEvaluatorFactory.create(
                world.generation().topology().requireAcceptedTopology(),
                world.generation().jumpEdges().orElseThrow(),
                world.generation().localLayouts().orElseThrow(),
                Stage18StationInfrastructureCatalogLoader.loadDefault(),
                currentProfile.inputs().transport(),
                maximumOwnedFreighters);
        double payloadKg = industrialFreight.capacityProfile().payloadMassKgPerFreighter();

        List<ExtractionCadence> extraction = extractionCadence(world, supply);
        List<GeneratedProcessCadence> generatedProcesses = generatedProcessCadence(supply, payloadKg);
        List<OperationalProcessCadence> operationalProcesses = operationalProcessCadence(
                industry, industrialFreight, payloadKg);
        List<FreightCadence> freight = freightCadence(industrialFreight, routes);
        List<BufferCadence> buffers = bufferCadence(industry);
        List<ShipyardConstructionCadence> shipyards = shipyardCadence(
                industry, supply, routes);
        List<TradePotential> tradePotential = tradePotential(
                world.generation().topology().requireAcceptedTopology(), supply, routes);

        return new AcceptanceReport(
                CURRENT_VERSION,
                world.rootSeed(),
                world.version(),
                industry.version(),
                Status.ACCEPTED,
                extraction,
                generatedProcesses,
                operationalProcesses,
                freight,
                buffers,
                shipyards,
                tradePotential,
                industry.runtimeBridgeRequirements(),
                false);
    }

    private static List<ExtractionCadence> extractionCadence(
            ResolvedProbeResult world,
            SupplyThroughputReport supply) {
        TreeMap<SupplyKey, Double> reserves = new TreeMap<>();
        for (ResourceOccurrence occurrence
                : world.generation().resourceWorld().orElseThrow().occurrences()) {
            double recoverable = occurrence.initialAccessibleMassKg()
                    * occurrence.gradeFraction()
                    * occurrence.sourceRecoveryFraction();
            reserves.merge(new SupplyKey(occurrence.outputCommodityId(), occurrence.systemId()),
                    recoverable, Stage20GeneratedEconomyCadenceAcceptance::finiteAdd);
        }
        ArrayList<ExtractionCadence> result = new ArrayList<>();
        for (Map.Entry<SupplyKey, Double> entry : reserves.entrySet()) {
            double rate = supply.capacityKgPerSecond(
                    entry.getKey().commodityId(), entry.getKey().systemId());
            if (rate > 0d) {
                result.add(new ExtractionCadence(
                        entry.getKey(), rate, entry.getValue(), entry.getValue() / rate));
            }
        }
        return List.copyOf(result);
    }

    private static List<GeneratedProcessCadence> generatedProcessCadence(
            SupplyThroughputReport supply,
            double payloadKg) {
        var refining = Stage18RefiningCatalogLoader.loadDefault();
        var manufacturing = Stage18ManufacturingCatalogLoader.loadDefault();
        ArrayList<GeneratedProcessCadence> result = new ArrayList<>();
        for (ProcessThroughputEvidence process : supply.processEvidence()) {
            if (process.inputLimitedOutputKgPerSecond() <= 0d) {
                continue;
            }
            ProcessSelectionKey key = new ProcessSelectionKey(
                    process.systemId(),
                    process.stationPlacementId(),
                    process.facilityDefinitionId(),
                    process.processId(),
                    process.outputCommodityId());
            ProcessKind kind;
            if (refining.findRecipe(process.processId()) != null) {
                kind = ProcessKind.REFINING;
            } else if (manufacturing.findComponentRecipe(process.processId()) != null) {
                kind = ProcessKind.COMPONENT_MANUFACTURING;
            } else {
                throw new IllegalArgumentException(
                        "unknown Stage-18 process: " + process.processId());
            }
            double inputPerOutput = process.inputEvidence().stream()
                    .mapToDouble(value -> value.inputKgPerOutputKg()).sum();
            double output = process.inputLimitedOutputKgPerSecond();
            result.add(new GeneratedProcessCadence(
                    key,
                    kind,
                    output * inputPerOutput,
                    output,
                    payloadKg / output));
        }
        return List.copyOf(result);
    }

    private static List<OperationalProcessCadence> operationalProcessCadence(
            OperationalSpecializationReport industry,
            IndustrialFreightReport freight,
            double payloadKg) {
        TreeMap<ProcessSelectionKey, Double> inputByProcess = new TreeMap<>();
        freight.reservation().inputDemands().forEach(value -> inputByProcess.merge(
                value.process(), value.requiredInputKgPerSecond(),
                Stage20GeneratedEconomyCadenceAcceptance::finiteAdd));
        ArrayList<OperationalProcessCadence> result = new ArrayList<>();
        industry.specializations().forEach(specialization -> {
            for (var evidence : specialization.processes()) {
                ProcessOperatingDemand demand = evidence.demand();
                Double input = inputByProcess.get(demand.process());
                if (input == null) {
                    throw new IllegalArgumentException("operational process lost reserved input cadence");
                }
                result.add(new OperationalProcessCadence(
                        demand.process(),
                        demand.stableFactionId(),
                        evidence.processKind(),
                        input,
                        demand.requestedOutputKgPerSecond(),
                        payloadKg / demand.requestedOutputKgPerSecond()));
            }
        });
        return List.copyOf(result);
    }

    private static List<FreightCadence> freightCadence(
            IndustrialFreightReport freight,
            Stage20PhysicalFreightRouteEvaluator routes) {
        ArrayList<FreightCadence> result = new ArrayList<>();
        for (var allocation : freight.allocations()) {
            FreightDemandEvidence demand = allocation.demand();
            int count = allocation.assignedSlots().size();
            FreightCycleAssessment cycle = routes.assessCycleWithAllocatedFreighters(
                    demand.input().supplyKey().systemId(),
                    demand.input().process().systemId(),
                    count).orElseThrow(() -> new IllegalArgumentException(
                    "owned industrial freight route lost physical cycle evidence"));
            result.add(new FreightCadence(
                    freightIdentity(demand),
                    demand.stableFactionId(),
                    cycle.orderedSystems(),
                    count,
                    cycle.payloadMassKgPerFreighter(),
                    cycle.deliverySeconds(),
                    cycle.roundTripCycleSeconds(),
                    cycle.handlingOverheadSeconds(),
                    cycle.sustainableCargoThroughputKgPerSecond(),
                    demand.reservedInputKgPerSecond()));
        }
        return List.copyOf(result);
    }

    private static List<BufferCadence> bufferCadence(
            OperationalSpecializationReport industry) {
        var inventory = industry.yardInstallation().inventory();
        TreeMap<StationCommodityKey, Double> consumption = new TreeMap<>();
        inventory.operatingState().freightOwnership().reservation().inputDemands().forEach(value -> {
            StationKey station = new StationKey(
                    value.process().systemId(), value.process().stationPlacementId());
            consumption.merge(
                    new StationCommodityKey(station, value.inputCommodityId()),
                    value.requiredInputKgPerSecond(),
                    Stage20GeneratedEconomyCadenceAcceptance::finiteAdd);
        });
        ArrayList<BufferCadence> result = new ArrayList<>();
        inventory.stations().forEach(station -> {
            for (CommodityBufferEvidence buffer : station.buffers()) {
                StationCommodityKey key = new StationCommodityKey(
                        station.assignment().station(), buffer.commodityId());
                Double rate = consumption.get(key);
                if (rate == null) {
                    throw new IllegalArgumentException("inventory buffer lost process consumption rate");
                }
                result.add(new BufferCadence(
                        key.station(),
                        key.commodityId(),
                        buffer.availableMassKg(),
                        rate,
                        buffer.availableMassKg() / rate,
                        buffer.requiredMassKg()));
            }
        });
        return List.copyOf(result);
    }

    private static List<ShipyardConstructionCadence> shipyardCadence(
            OperationalSpecializationReport industry,
            SupplyThroughputReport supply,
            Stage20PhysicalFreightRouteEvaluator routes) {
        var shipyardCatalog = Stage18ShipyardCatalogLoader.loadDefault();
        ArrayList<ShipyardConstructionCadence> result = new ArrayList<>();
        industry.specializations().forEach(specialization -> {
            for (var installed : specialization.activeYards()) {
                HullPhysicalProfile hull = shipyardCatalog.getHullProfiles().stream()
                        .filter(value -> hullMass(value)
                                <= installed.snapshot().plannerCapability().maxServiceMassKg() + EPSILON)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "active yard has no compatible Stage-18 hull profile"));
                ArrayList<ConstructionInputCadence> inputs = new ArrayList<>();
                for (PhysicalInputDefinition input : hull.buildInputsKg()) {
                    inputs.add(bestConstructionInput(
                            input,
                            installed.assignment().slot().station().systemId(),
                            supply,
                            routes));
                }
                result.add(new ShipyardConstructionCadence(
                        installed.snapshot().yardInstanceId(),
                        installed.assignment().slot().station(),
                        installed.assignment().stableFactionId(),
                        hull.hullId(),
                        hullMass(hull),
                        inputs,
                        inputs.stream().mapToDouble(
                                ConstructionInputCadence::emptyPipelineSupplyEtaSeconds).sum(),
                        inputs.stream().mapToDouble(
                                ConstructionInputCadence::steadyStateReplenishmentSeconds).sum()));
            }
        });
        return List.copyOf(result);
    }

    private static ConstructionInputCadence bestConstructionInput(
            PhysicalInputDefinition input,
            StarSystemId destination,
            SupplyThroughputReport supply,
            Stage20PhysicalFreightRouteEvaluator routes) {
        ConstructionInputCadence best = null;
        for (Map.Entry<SupplyKey, Double> entry : supply.capacityKgPerSecondBySupply().entrySet()) {
            if (!entry.getKey().commodityId().equals(input.commodityId())) {
                continue;
            }
            FreightCycleAssessment cycle = routes.assessCycleWithAllocatedFreighters(
                    entry.getKey().systemId(), destination, 1).orElse(null);
            if (cycle == null) {
                continue;
            }
            double delivered = Math.min(
                    entry.getValue(), cycle.sustainableCargoThroughputKgPerSecond());
            double replenishment = input.massKg() / delivered;
            ConstructionInputCadence candidate = new ConstructionInputCadence(
                    input.commodityId(),
                    input.massKg(),
                    entry.getKey().systemId(),
                    entry.getValue(),
                    delivered,
                    cycle.deliverySeconds(),
                    cycle.roundTripCycleSeconds(),
                    cycle.deliverySeconds() + replenishment,
                    replenishment);
            if (best == null || compareConstructionInput(candidate, best) < 0) {
                best = candidate;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException(
                    "generated world lacks physical construction supply for " + input.commodityId());
        }
        return best;
    }

    private static List<TradePotential> tradePotential(
            GalaxyTopology topology,
            SupplyThroughputReport supply,
            Stage20PhysicalFreightRouteEvaluator routes) {
        TreeMap<String, TreeMap<StarSystemId, Double>> capacities = new TreeMap<>();
        supply.capacityKgPerSecondBySupply().forEach((key, value) -> capacities
                .computeIfAbsent(key.commodityId(), ignored -> new TreeMap<>())
                .put(key.systemId(), value));
        ArrayList<TradePotential> result = new ArrayList<>();
        for (Map.Entry<String, TreeMap<StarSystemId, Double>> commodity : capacities.entrySet()) {
            TradePotential best = null;
            for (Map.Entry<StarSystemId, Double> source : commodity.getValue().entrySet()) {
                for (var destinationNode : topology.systems()) {
                    StarSystemId destination = destinationNode.id();
                    if (source.getKey().equals(destination)
                            || topology.sectorOf(source.getKey()).orElseThrow().id().equals(
                            topology.sectorOf(destination).orElseThrow().id())) {
                        continue;
                    }
                    double destinationCapacity = commodity.getValue().getOrDefault(destination, 0d);
                    double advantage = source.getValue() - destinationCapacity;
                    if (advantage <= EPSILON) {
                        continue;
                    }
                    FreightCycleAssessment cycle = routes.assessCycleWithAllocatedFreighters(
                            source.getKey(), destination, 1).orElse(null);
                    if (cycle == null || cycle.orderedSystems().size() < 2) {
                        continue;
                    }
                    double delivered = Math.min(
                            advantage, cycle.sustainableCargoThroughputKgPerSecond());
                    TradePotential candidate = new TradePotential(
                            commodity.getKey(),
                            source.getKey(),
                            destination,
                            source.getValue(),
                            destinationCapacity,
                            advantage,
                            delivered,
                            cycle.orderedSystems().size() - 1);
                    if (best == null || compareTrade(candidate, best) < 0) {
                        best = candidate;
                    }
                }
            }
            if (best != null) {
                result.add(best);
            }
        }
        return List.copyOf(result);
    }

    private static ResolvedProbeResult requireAccepted(ResolvedProbeResult resolved) {
        ResolvedProbeResult value = Objects.requireNonNull(resolved, "resolved");
        if (value.seedAcceptance().status()
                != com.spacesim.world.Stage20GeneratedWorldSeedAcceptance.Status.ACCEPTED) {
            throw new IllegalArgumentException("Stage-20J requires an accepted generated world");
        }
        return value;
    }

    private static double hullMass(HullPhysicalProfile hull) {
        return hull.buildInputsKg().stream().mapToDouble(PhysicalInputDefinition::massKg).sum();
    }

    private static String freightIdentity(FreightDemandEvidence demand) {
        var input = demand.input();
        var process = input.process();
        return "process-system-" + Long.toUnsignedString(process.systemId().value())
                + ":station-" + process.stationPlacementId()
                + ":facility-" + process.facilityDefinitionId()
                + ":process-" + process.processId()
                + ":input-" + input.inputCommodityId()
                + ":source-system-" + Long.toUnsignedString(input.supplyKey().systemId().value());
    }

    private static int compareConstructionInput(
            ConstructionInputCadence left,
            ConstructionInputCadence right) {
        int comparison = Double.compare(
                left.emptyPipelineSupplyEtaSeconds(), right.emptyPipelineSupplyEtaSeconds());
        return comparison != 0
                ? comparison
                : left.sourceSystemId().compareTo(right.sourceSystemId());
    }

    private static int compareTrade(TradePotential left, TradePotential right) {
        int comparison = -Double.compare(
                left.oneFreighterDeliveredPotentialKgPerSecond(),
                right.oneFreighterDeliveredPotentialKgPerSecond());
        if (comparison != 0) return comparison;
        comparison = left.sourceSystemId().compareTo(right.sourceSystemId());
        return comparison != 0
                ? comparison
                : left.destinationSystemId().compareTo(right.destinationSystemId());
    }

    private static Set<RuntimeBridgeRequirement> immutableBridgeRequirements(
            Set<RuntimeBridgeRequirement> values) {
        Objects.requireNonNull(values, "remainingRuntimeBridgeRequirements");
        EnumSet<RuntimeBridgeRequirement> copy = values.isEmpty()
                ? EnumSet.noneOf(RuntimeBridgeRequirement.class)
                : EnumSet.copyOf(values);
        if (!copy.equals(EnumSet.allOf(RuntimeBridgeRequirement.class))) {
            throw new IllegalArgumentException(
                    "Stage-20J cannot close or invent Stage-20F runtime bridge requirements");
        }
        return Collections.unmodifiableSet(copy);
    }

    private static <T> List<T> sorted(List<T> values, Comparator<? super T> comparator) {
        ArrayList<T> copy = new ArrayList<>(Objects.requireNonNull(values, "values"));
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("cadence evidence cannot contain nulls");
        }
        copy.sort(comparator);
        return List.copyOf(copy);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }

    private static double finiteAdd(double left, double right) {
        double result = left + right;
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("cadence aggregate overflow");
        }
        return result;
    }

    private static void positive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }

    private static void nonNegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }

    private static void close(double expected, double actual, String field) {
        double tolerance = 1.0e-9d * Math.max(1d, Math.max(Math.abs(expected), Math.abs(actual)));
        if (Math.abs(expected - actual) > tolerance) {
            throw new IllegalArgumentException(field + " differs from physical components");
        }
    }

    private record StationCommodityKey(
            StationKey station,
            String commodityId) implements Comparable<StationCommodityKey> {
        private StationCommodityKey {
            Objects.requireNonNull(station, "station");
            commodityId = requireText(commodityId, "commodityId");
        }

        @Override
        public int compareTo(StationCommodityKey other) {
            int comparison = station.compareTo(other.station);
            return comparison != 0 ? comparison : commodityId.compareTo(other.commodityId);
        }
    }
}
