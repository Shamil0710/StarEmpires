package com.spacesim.persistence;

import com.spacesim.economy.Stage18SalvageRuntime.SalvageStream;
import com.spacesim.persistence.Stage18IndustrialState.PhysicalSourceSnapshot;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.CanonicalRow;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.GenerationIdentity;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.MaterializedWorldSnapshot;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.OpenRuntimeBoundary;
import com.spacesim.ship.SignatureState;
import com.spacesim.world.AsteroidFieldNode;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.JumpConnection;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.PlanetNode;
import com.spacesim.world.SectorId;
import com.spacesim.world.SectorNode;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan.CommitmentKey;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan.FactionFleetOwnership;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan.OwnershipReport;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan.OwnershipSlot;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan.RemoteCommitmentAllocation;
import com.spacesim.world.Stage20DiscoveryKnowledgeState;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance;
import com.spacesim.world.Stage20JumpEdgeState;
import com.spacesim.world.Stage20LocalInfrastructureLayout;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.FactionStationSpecialization;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.OperationalProcessEvidence;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.OperationalSpecializationReport;
import com.spacesim.world.Stage20ResourceOccurrenceWorld;
import com.spacesim.world.Stage20SpecialLocationWorld;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import com.spacesim.world.generation.Stage20LocalPhysicalResourceHostGenerator;
import com.spacesim.world.generation.Stage20MacroGalaxyGeometryGenerator.MacroGeometryResult;
import com.spacesim.world.generation.Stage20MacroGalaxyGeometryGenerator.SectorGeometryEvidence;
import com.spacesim.world.generation.Stage20MacroGalaxyGeometryGenerator.SystemGeometryEvidence;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;
import com.spacesim.world.generation.Stage20TopologyQualityReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Captures the accepted Stage-20 generated authority as a stable ordered persistence snapshot.
 *
 * <p>Capture reconstructs the exact Stage-20E physical freight/ownership chain, retains the closed
 * Stage-20F operational plan as rows, and records every Stage-20D arrival endpoint. It creates no
 * runtime fleets, cargo lots, source producers, industrial entities or arrival transition behavior;
 * those boundaries remain explicit in the resulting state.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20GeneratedCampaignPersistence {
    private Stage20GeneratedCampaignPersistence() {
        throw new AssertionError("No instances");
    }

    /**
     * Captures one complete generated campaign without invoking generation during load.
     *
     * @param resolved exact accepted resolved generated-world evidence
     * @param specialLocations exact generated Stage-20H special-location world
     * @param specialization closed Stage-20F operational specialization
     * @param materialization current core/far-local physical persistence state
     * @param industrial current Stage-18 industrial persistence state
     * @param discoveryKnowledge observer-local durable knowledge snapshots
     * @return complete validated Stage-20K campaign state
     */
    public static Stage20GeneratedCampaignPersistentState capture(
            ResolvedProbeResult resolved,
            Stage20SpecialLocationWorld specialLocations,
            OperationalSpecializationReport specialization,
            Stage20MaterializationPersistentState materialization,
            Stage18IndustrialState industrial,
            List<Stage20DiscoveryKnowledgeState> discoveryKnowledge) {
        ResolvedProbeResult accepted = requireAccepted(resolved);
        Stage20SpecialLocationWorld specials = Objects.requireNonNull(specialLocations, "specialLocations");
        OperationalSpecializationReport operations = Objects.requireNonNull(specialization, "specialization");
        Stage20MaterializationPersistentState physical = Objects.requireNonNull(
                materialization, "materialization");
        Stage18IndustrialState industry = Objects.requireNonNull(industrial, "industrial");
        if (specials.rootSeed() != accepted.rootSeed()
                || !specials.resolvedProbeVersion().equals(accepted.version())) {
            throw new IllegalArgumentException("special locations differ from accepted generated authority");
        }
        if (operations.rootSeed() != accepted.rootSeed()
                || !operations.resolvedProbeVersion().equals(accepted.version())
                || !operations.readyForRuntimeBridge()) {
            throw new IllegalArgumentException("Stage-20F specialization is not the matching closed authority");
        }
        if (physical.gameState().rootSeed() != accepted.rootSeed()) {
            throw new IllegalArgumentException("materialization GameState differs from generated root seed");
        }

        GenerationIdentity identity = new GenerationIdentity(
                accepted.rootSeed(),
                accepted.version(),
                accepted.sourceProbeVersion(),
                accepted.representativeProfileVersion(),
                industry.contentFingerprint());
        MaterializedWorldSnapshot snapshot = captureMaterializedWorld(
                accepted, specials, operations, industry);
        Stage20DiscoveryPersistentState discovery = new Stage20DiscoveryPersistentState(
                Stage20DiscoveryPersistentState.CURRENT_VERSION,
                accepted.rootSeed(),
                accepted.version(),
                snapshot.worldFingerprint(),
                discoveryKnowledge);
        return new Stage20GeneratedCampaignPersistentState(
                Stage20GeneratedCampaignPersistentState.CURRENT_VERSION,
                identity,
                snapshot,
                physical,
                industry,
                discovery,
                List.of(OpenRuntimeBoundary.values()));
    }

    /**
     * Captures the canonical generated-world/quality rows used for deterministic headless evidence.
     *
     * @param resolved exact accepted resolved generated-world evidence
     * @param specialLocations matching generated Stage-20H special-location world
     * @param specialization matching closed Stage-20F operational specialization
     * @param industrial current industrial state supplying any consumed reserve values
     * @return fingerprinted canonical materialized-world snapshot
     */
    public static MaterializedWorldSnapshot captureMaterializedWorld(
            ResolvedProbeResult resolved,
            Stage20SpecialLocationWorld specialLocations,
            OperationalSpecializationReport specialization,
            Stage18IndustrialState industrial) {
        ResolvedProbeResult accepted = requireAccepted(resolved);
        Stage20SpecialLocationWorld specials = Objects.requireNonNull(specialLocations, "specialLocations");
        OperationalSpecializationReport operations = Objects.requireNonNull(specialization, "specialization");
        Stage18IndustrialState industry = Objects.requireNonNull(industrial, "industrial");
        if (specials.rootSeed() != accepted.rootSeed()
                || !specials.resolvedProbeVersion().equals(accepted.version())
                || operations.rootSeed() != accepted.rootSeed()
                || !operations.resolvedProbeVersion().equals(accepted.version())
                || !operations.readyForRuntimeBridge()) {
            throw new IllegalArgumentException("captured generated layers do not share one accepted authority");
        }

        ArrayList<CanonicalRow> world = new ArrayList<>();
        addMacroAndTopology(world, accepted);
        addJumpEdges(world, accepted);
        addLocalInfrastructure(world, accepted);
        addPhysicalHosts(world, accepted);
        addResources(world, accepted, industry);
        addFactionStartsAndOwnership(world, accepted);
        addSpecialLocations(world, specials, industry);
        addOperationalIndustry(world, operations);

        ArrayList<CanonicalRow> quality = new ArrayList<>();
        addQualityRows(quality, accepted);
        return MaterializedWorldSnapshot.create(world, quality);
    }

    private static ResolvedProbeResult requireAccepted(ResolvedProbeResult value) {
        ResolvedProbeResult accepted = Objects.requireNonNull(value, "resolved");
        if (accepted.seedAcceptance().status() != Stage20GeneratedWorldSeedAcceptance.Status.ACCEPTED
                || accepted.coordinatedFreightAcceptance().isEmpty()) {
            throw new IllegalArgumentException("Stage-20K persistence requires an accepted resolved generated seed");
        }
        return accepted;
    }

    private static void addMacroAndTopology(List<CanonicalRow> rows, ResolvedProbeResult accepted) {
        MacroGeometryResult macro = accepted.generation().macroGeometry();
        row(rows, "MACRO_PROFILE", "root",
                macro.version(), macro.rootSeed(), macro.request().sectorCount(),
                macro.request().minSystemsPerSector(), macro.request().maxSystemsPerSector(),
                macro.coordinateSemantics());
        Map<SectorId, SectorGeometryEvidence> sectorEvidence = new HashMap<>();
        macro.sectorEvidence().forEach(value -> sectorEvidence.put(value.sectorId(), value));
        Map<StarSystemId, SystemGeometryEvidence> systemEvidence = new HashMap<>();
        macro.systemEvidence().forEach(value -> systemEvidence.put(value.systemId(), value));
        for (SectorNode sector : macro.sectors()) {
            SectorGeometryEvidence evidence = Objects.requireNonNull(
                    sectorEvidence.get(sector.id()), "sector geometry evidence");
            row(rows, "SECTOR", id(sector.id().value()),
                    sector.name(), evidence.centerX(), evidence.centerY(), evidence.clusterRadius(),
                    evidence.aspectRatio(), evidence.orientationRad(), evidence.systemCount());
            for (StarSystemNode system : sector.systems()) {
                SystemGeometryEvidence systemRow = Objects.requireNonNull(
                        systemEvidence.get(system.id()), "system geometry evidence");
                row(rows, "STAR_SYSTEM", id(system.id().value()),
                        sector.id().value(), system.name(), system.x(), system.y(),
                        systemRow.placementClass(), systemRow.normalizedClusterRadius());
                for (PlanetNode planet : system.planets()) {
                    row(rows, "PLANET", id(planet.id().value()),
                            system.id().value(), planet.name(), planet.orbitRadius());
                }
                for (AsteroidFieldNode field : system.asteroidFields()) {
                    row(rows, "ASTEROID_FIELD", id(field.id().value()),
                            system.id().value(), field.name(), field.x(), field.y(), field.radius());
                }
            }
        }

        GalaxyTopology topology = accepted.generation().topology().requireAcceptedTopology();
        row(rows, "TOPOLOGY_META", "root",
                topology.id().value(), topology.name(), accepted.generation().topology().repairPasses());
        for (JumpConnection connection : topology.connections()) {
            row(rows, "TOPOLOGY_CONNECTION", connectionId(connection),
                    connection.first().value(), connection.second().value());
        }
    }

    private static void addJumpEdges(List<CanonicalRow> rows, ResolvedProbeResult accepted) {
        for (Stage20JumpEdgeState edge : accepted.generation().jumpEdges().orElseThrow().edges()) {
            ArrayList<String> values = values(
                    edge.version(), edge.connection().first().value(), edge.connection().second().value(),
                    edge.operationalAccessState(), edge.discoveryPolicy(),
                    edge.transitParameters().fittedTransitMultiplier(),
                    edge.transitParameters().ftlProfileVersion(), edge.transitParameters().semantics());
            appendEndpoint(values, edge.firstEndpoint());
            appendEndpoint(values, edge.secondEndpoint());
            values.add(edge.hazardSecurityMetadata().observationState().name());
            values.add(edge.hazardSecurityMetadata().provenance().orElse(""));
            appendStrings(values, edge.hazardSecurityMetadata().hazardTags());
            appendStrings(values, edge.hazardSecurityMetadata().securityTags());
            values.add(edge.topologyQualityProfileVersion());
            values.add(edge.intersystemCadenceProfileVersion());
            rows.add(new CanonicalRow("JUMP_EDGE", edge.edgeId(), values));
        }
    }

    private static void appendEndpoint(List<String> values, Stage20JumpEdgeState.ArrivalEndpoint endpoint) {
        values.add(id(endpoint.systemId().value()));
        values.add(endpoint.anchorId());
        appendPosition(values, endpoint.position());
        values.add(scalar(endpoint.arrivalVelocityMps()));
        values.add(endpoint.localInfrastructureVersion());
        values.add(endpoint.jumpArrivalCalibrationVersion());
    }

    private static void addLocalInfrastructure(List<CanonicalRow> rows, ResolvedProbeResult accepted) {
        for (Stage20LocalInfrastructureLayout layout
                : accepted.generation().localLayouts().orElseThrow()) {
            String system = id(layout.systemId().value());
            row(rows, "LOCAL_LAYOUT", system,
                    layout.version(), layout.rootSeed(), layout.majorHubId(),
                    layout.systemGeometryVersion(), layout.routeCalibrationVersion(),
                    layout.stationGeometryVersion(), layout.stationDefenseVersion());
            for (Stage20LocalInfrastructureLayout.InfrastructurePlacement placement : layout.placements()) {
                ArrayList<String> values = values(
                        layout.systemId().value(), placement.kind(),
                        placement.stationArchetypeId().orElse(""));
                appendPosition(values, placement.position());
                values.add(scalar(placement.operationalRadiusM()));
                values.add(scalar(placement.defensiveExclusionReferenceM()));
                rows.add(new CanonicalRow(
                        "INFRASTRUCTURE_PLACEMENT", system + ":" + placement.id(), values));
            }
            int ordinal = 0;
            for (Stage20LocalInfrastructureLayout.CalibratedConnection connection : layout.connections()) {
                var consequence = connection.logisticsConsequences();
                row(rows, "LOCAL_CONNECTION",
                        system + ":" + ordinal++ + ":" + connection.fromId() + ":" + connection.toId(),
                        layout.systemId().value(), connection.fromId(), connection.toId(), connection.bandId(),
                        connection.distanceM(), connection.minDistanceM(), connection.maxDistanceM(),
                        connection.sourceEvidenceId(), consequence.civilianRoutineTravelTimeMinS(),
                        consequence.civilianRoutineTravelTimeMaxS(), consequence.militaryResponseTimeMinS(),
                        consequence.militaryResponseTimeMaxS(), consequence.civilianRoundTripDeltaVMinMps(),
                        consequence.civilianRoundTripDeltaVMaxMps(),
                        consequence.civilianTransitOnlyCargoCycleMinS(),
                        consequence.civilianTransitOnlyCargoCycleMaxS(), consequence.sourceProfileVersion());
            }
        }
    }

    private static void addPhysicalHosts(List<CanonicalRow> rows, ResolvedProbeResult accepted) {
        Stage20LocalPhysicalResourceHostGenerator.GenerationResult hosts =
                accepted.generation().physicalHosts().orElseThrow();
        row(rows, "PHYSICAL_HOST_PROFILE", "root",
                hosts.version(), hosts.rootSeed(), hosts.extractionCatalogFingerprint());
        for (Stage20LocalPhysicalResourceHostGenerator.PhysicalHost host : hosts.hosts()) {
            ArrayList<String> values = values(
                    host.version(), host.systemId().value(), host.anchorId(), host.hostClass(),
                    host.hostClass().hostClassId(), host.hostClass().environment(),
                    host.hostClass().locationTag());
            appendPosition(values, host.position());
            appendMap(values, host.occurrenceAffinityByTypeId());
            appendStrings(values, host.sourceRequiredCapabilityTags().stream().sorted().toList());
            rows.add(new CanonicalRow(
                    "PHYSICAL_RESOURCE_HOST", id(host.systemId().value()) + ":" + host.anchorId(), values));
        }
    }

    private static void addResources(
            List<CanonicalRow> rows,
            ResolvedProbeResult accepted,
            Stage18IndustrialState industrial) {
        Stage20ResourceOccurrenceWorld resources = accepted.generation().resourceWorld().orElseThrow();
        row(rows, "RESOURCE_WORLD_PROFILE", "root",
                resources.version(), resources.rootSeed(), resources.ontologyFingerprint(),
                resources.extractionFingerprint(), resources.facilityFingerprint(),
                resources.generationProfileVersion());
        for (Stage20ResourceOccurrenceWorld.SystemResourceConditions conditions
                : resources.systemConditions()) {
            ArrayList<String> values = values(conditions.systemId().value());
            appendMap(values, conditions.occurrencePotentialByTypeId());
            rows.add(new CanonicalRow(
                    "RESOURCE_CONDITIONS", id(conditions.systemId().value()), values));
        }

        Map<String, PhysicalSourceSnapshot> persistedSources = new TreeMap<>();
        for (PhysicalSourceSnapshot source : industrial.sources()) {
            persistedSources.put(source.sourceId(), source);
        }
        for (Stage20ResourceOccurrenceWorld.ResourceOccurrence occurrence : resources.occurrences()) {
            PhysicalSourceSnapshot runtime = persistedSources.get(occurrence.sourceId());
            double remaining = occurrence.initialAccessibleMassKg();
            if (runtime != null) {
                if (Double.compare(runtime.initialAccessibleMassKg(), occurrence.initialAccessibleMassKg()) != 0
                        || !runtime.outputCommodityId().equals(occurrence.outputCommodityId())) {
                    throw new IllegalArgumentException(
                            "runtime source differs from generated occurrence: " + occurrence.sourceId());
                }
                remaining = runtime.remainingAccessibleMassKg();
            }
            ArrayList<String> values = values(
                    occurrence.systemId().value(), occurrence.hostAnchorId(), occurrence.hostClassId());
            appendPosition(values, occurrence.position());
            values.addAll(values(
                    occurrence.occurrenceTypeId(), occurrence.environment(), occurrence.outputCommodityId(),
                    occurrence.generationScore(), occurrence.initialAccessibleMassKg(), remaining,
                    occurrence.gradeFraction(), occurrence.sourceRecoveryFraction()));
            appendStrings(values, occurrence.requiredCapabilityTags().stream().sorted().toList());
            rows.add(new CanonicalRow("RESOURCE_OCCURRENCE", occurrence.sourceId(), values));
        }
        for (Stage20ResourceOccurrenceWorld.InitialExtractionSite site : resources.initialExtractionSites()) {
            row(rows, "INITIAL_EXTRACTION_SITE", site.siteId(),
                    site.sourceId(), site.systemId().value(), site.hostAnchorId(), site.locationTag(),
                    site.facilityDefinitionId(), site.extractionMethodId());
        }
    }

    private static void addFactionStartsAndOwnership(
            List<CanonicalRow> rows,
            ResolvedProbeResult accepted) {
        var placement = accepted.generation().placement().orElseThrow();
        row(rows, "FACTION_START_PROFILE", "root",
                placement.version(), placement.rootSeed(), placement.profileVersion(),
                placement.status(), placement.searchNodes());
        placement.assignments().forEach(assignment -> row(rows, "FACTION_START",
                assignment.stableFactionId(), assignment.systemId().value(),
                assignment.candidateSelectionPenalty()));

        OwnershipReport ownership = Stage20BootstrapFreightOwnershipPlan.plan(accepted);
        row(rows, "FREIGHT_OWNERSHIP_PROFILE", "root",
                ownership.version(), ownership.rootSeed(), ownership.placementProfileVersion(),
                ownership.physicalPlan().version(), ownership.totalOwnedFreighters(),
                ownership.totalCommittedFreighters());
        for (FactionFleetOwnership faction : ownership.factions()) {
            row(rows, "FREIGHT_OWNERSHIP_POOL", faction.stableFactionId(),
                    faction.homeStartSystemId().value(), faction.ownedFreighterCount(),
                    faction.committedFreighterCount(), faction.reserveFreighterCount());
            for (RemoteCommitmentAllocation allocation : faction.remoteCommitments()) {
                CommitmentKey key = allocation.commitmentKey();
                ArrayList<String> values = commitmentValues(key);
                values.add(scalar(allocation.allocatedFreighters()));
                values.add(scalar(allocation.deliveredKgPerSecond()));
                values.add(scalar(allocation.route().travelTimeS()));
                values.add(scalar(allocation.route().sustainableCargoThroughputKgPerSecond()));
                appendSystemIds(values, allocation.route().orderedSystems());
                rows.add(new CanonicalRow("FREIGHT_COMMITMENT", commitmentId(key), values));
            }
            for (OwnershipSlot slot : faction.materializationSlots()) {
                ArrayList<String> values = values(slot.stableFactionId(), slot.ownershipOrdinal());
                if (slot.commitment().isPresent()) {
                    values.add("COMMITTED");
                    values.addAll(commitmentValues(slot.commitment().orElseThrow().commitmentKey()));
                    values.add(scalar(slot.commitment().orElseThrow().freighterOrdinal()));
                } else {
                    values.add("RESERVE");
                }
                rows.add(new CanonicalRow(
                        "FREIGHT_OWNERSHIP_SLOT",
                        slot.stableFactionId() + ":" + slot.ownershipOrdinal(),
                        values));
            }
        }
    }

    private static ArrayList<String> commitmentValues(CommitmentKey key) {
        return values(
                key.frontierVersion(), key.optionId(), key.stableFactionId(), key.commodityId(),
                key.producerSystemId().value(), key.consumerStartSystemId().value(),
                key.sourceCommitmentOrdinal());
    }

    private static String commitmentId(CommitmentKey key) {
        return key.stableFactionId() + ':' + key.commodityId() + ':'
                + key.producerSystemId().value() + ':' + key.consumerStartSystemId().value()
                + ':' + key.sourceCommitmentOrdinal();
    }

    private static void addSpecialLocations(
            List<CanonicalRow> rows,
            Stage20SpecialLocationWorld specials,
            Stage18IndustrialState industrial) {
        row(rows, "SPECIAL_LOCATION_PROFILE", "root",
                specials.version(), specials.rootSeed(), specials.resolvedProbeVersion(),
                specials.generationProfileVersion(), specials.shipyardFingerprint());
        Map<String, PhysicalSourceSnapshot> runtimeSources = new TreeMap<>();
        industrial.sources().forEach(value -> runtimeSources.put(value.sourceId(), value));
        for (Stage20SpecialLocationWorld.SpecialLocation location : specials.locations()) {
            SignatureState signature = location.signature();
            ArrayList<String> values = values(
                    location.systemId().value(), location.coordinateDomain());
            appendPosition(values, location.position());
            values.addAll(values(
                    location.archetypeId(), location.kind(), location.rarity(),
                    signature.thermalRadiantPowerW(), signature.enginePlumeRadiantPowerW(),
                    signature.radarCrossSectionM2(), signature.reflectedOpticalPowerW(),
                    signature.activeRadioEmissionPowerW(), signature.jammerEmissionPowerW(),
                    location.scanRequirement(), location.hazardBand()));
            appendStrings(values, location.hazardTags());
            values.addAll(values(
                    location.nearestTrafficAnchorId(), location.nearestTrafficAnchorKind(),
                    location.nearestTrafficDistanceM(), location.miningShipApproachTimeS(),
                    location.securityAssessment(), location.linkedResourceSourceId().orElse(""),
                    location.finiteRecoverableValueKg(), location.signatureProvenanceId()));
            rows.add(new CanonicalRow("SPECIAL_LOCATION", location.locationId(), values));
            for (SalvageStream stream : location.salvageStreams()) {
                PhysicalSourceSnapshot runtime = runtimeSources.get(stream.streamId());
                double remaining = runtime == null ? stream.accessibleMassKg() : runtime.remainingAccessibleMassKg();
                row(rows, "SPECIAL_LOCATION_SALVAGE", stream.streamId(),
                        location.locationId(), stream.commodityId(), stream.constructedMassKg(),
                        stream.accessibleMassKg(), remaining, stream.irrecoverableDamageLossKg());
            }
        }
    }

    private static void addOperationalIndustry(
            List<CanonicalRow> rows,
            OperationalSpecializationReport operations) {
        row(rows, "INDUSTRIAL_SPECIALIZATION_PROFILE", "root",
                operations.version(), operations.rootSeed(), operations.resolvedProbeVersion(),
                operations.status(), operations.totalSelectedOutputKgPerSecond(),
                operations.activeYardCount());
        for (FactionStationSpecialization specialization : operations.specializations()) {
            String stationId = stationId(
                    specialization.key().station().systemId(),
                    specialization.key().station().stationPlacementId());
            ArrayList<String> values = values(
                    specialization.key().station().systemId().value(),
                    specialization.key().station().stationPlacementId(),
                    specialization.key().stableFactionId());
            appendStrings(values, specialization.roles().stream().map(Enum::name).sorted().toList());
            rows.add(new CanonicalRow(
                    "INDUSTRIAL_SPECIALIZATION",
                    stationId + ":" + specialization.key().stableFactionId(),
                    values));
            for (OperationalProcessEvidence process : specialization.processes()) {
                var demand = process.demand();
                var key = demand.process();
                ArrayList<String> processValues = values(
                        key.systemId().value(), key.stationPlacementId(), key.facilityDefinitionId(),
                        key.processId(), key.outputCommodityId(), demand.stableFactionId(),
                        process.processKind(), demand.requestedOutputKgPerSecond(),
                        demand.requiredProcessPowerW(), demand.requiredEngineeringWorkRate(),
                        demand.requiredMaintenanceWorkRate());
                appendStrings(processValues, demand.requiredCapabilityTags().stream().sorted().toList());
                rows.add(new CanonicalRow(
                        "INDUSTRIAL_PROCESS_PLAN",
                        stationId + ":" + key.facilityDefinitionId() + ":" + key.processId(),
                        processValues));
            }
            specialization.activeYards().forEach(yard -> {
                var assignment = yard.assignment();
                var state = assignment.state();
                var snapshot = yard.snapshot();
                var planner = snapshot.plannerCapability();
                ArrayList<String> yardValues = values(
                        assignment.slot().station().systemId().value(),
                        assignment.slot().station().stationPlacementId(),
                        assignment.slot().yardOrdinal(), assignment.stableFactionId(),
                        state.yardInstanceId(), state.yardDefinitionId(), state.conditionFraction(),
                        state.allocatedIntegrationPowerW(), state.availableIntegrationWorkRate(),
                        state.availableLaborCapacity(), state.availableAutomationCapacity(), state.enabled(),
                        snapshot.status(), snapshot.maxHandledUnitMassKg(),
                        yard.availableResidualSupportWorkRate(), yard.effectiveYardWorkRate(), yard.status());
                appendStrings(yardValues, snapshot.handledStorageClassIds().stream().sorted().toList());
                if (planner != null) {
                    yardValues.addAll(values(
                            planner.yardId(), planner.berthDimensionsM().lengthM(),
                            planner.berthDimensionsM().widthM(), planner.berthDimensionsM().heightM(),
                            planner.maxServiceMassKg(), planner.precisionCapability(), planner.workRate(),
                            planner.laborCapacity(), planner.automationCapacity(), planner.industrialPowerW()));
                    appendStrings(yardValues, planner.fabricationCapabilities().stream().sorted().toList());
                    appendStrings(yardValues, planner.handledInputContentIds().stream().sorted().toList());
                    appendStrings(yardValues, planner.toolingTags().stream().sorted().toList());
                }
                rows.add(new CanonicalRow(
                        "INDUSTRIAL_YARD_PLAN", state.yardInstanceId(), yardValues));
            });
        }
        operations.runtimeBridgeRequirements().stream()
                .sorted(Comparator.comparing(Enum::name))
                .forEach(requirement -> row(rows, "OPEN_STAGE20F_RUNTIME_BOUNDARY",
                        requirement.name(), requirement.name()));
        row(rows, "OPEN_STAGE20D_RUNTIME_BOUNDARY", "LIVE_ARRIVAL_AUTHORITY_INTEGRATION",
                "LIVE_ARRIVAL_AUTHORITY_INTEGRATION");
    }

    private static void addQualityRows(List<CanonicalRow> rows, ResolvedProbeResult accepted) {
        addSeedQuality(rows, "resolved", accepted.seedAcceptance());
        addSeedQuality(rows, "source", accepted.generation().seedAcceptance());
        Stage20TopologyQualityReport report = accepted.generation().topology().qualityReport();
        row(rows, "TOPOLOGY_QUALITY_SUMMARY", "root",
                report.connectedComponents(), report.degreeOneFraction(), report.degreeTwoFraction(),
                report.meanDegree(), report.medianDegree(), report.longestLinearCorridorEdges(),
                report.p90LinearCorridorEdges(), report.cycleParticipationFraction(),
                report.corePairsChecked(), report.corePairsWithAlternateRoute(),
                report.coreRouteRedundancyCoverage(), report.maxSingleGatewayDependency(),
                report.medianRegionalHubHopDistance().isPresent()
                        ? report.medianRegionalHubHopDistance().getAsDouble() : "", report.accepted());
        report.degreeHistogram().forEach((degree, count) -> row(
                rows, "TOPOLOGY_QUALITY_DEGREE", scalar(degree), count));
        report.sectorExitCounts().forEach((sector, count) -> row(
                rows, "TOPOLOGY_QUALITY_SECTOR_EXIT", id(sector.value()), count));
        report.sectorInternalCycleCoverage().forEach((sector, coverage) -> row(
                rows, "TOPOLOGY_QUALITY_SECTOR_CYCLE", id(sector.value()), coverage));
        report.sectorInternalBridgeCounts().forEach((sector, count) -> row(
                rows, "TOPOLOGY_QUALITY_SECTOR_BRIDGE", id(sector.value()), count));
        report.sectorMotifFingerprints().forEach((sector, fingerprint) -> row(
                rows, "TOPOLOGY_QUALITY_SECTOR_MOTIF", id(sector.value()), fingerprint));
        for (int index = 0; index < report.linearCorridorLengths().size(); index++) {
            row(rows, "TOPOLOGY_QUALITY_CORRIDOR", scalar(index),
                    report.linearCorridorLengths().get(index));
        }
        for (int index = 0; index < report.regionalHubHopDistances().size(); index++) {
            row(rows, "TOPOLOGY_QUALITY_REGIONAL_HOP", scalar(index),
                    report.regionalHubHopDistances().get(index));
        }
        report.unreachableSystems().forEach(system -> row(
                rows, "TOPOLOGY_QUALITY_UNREACHABLE_SYSTEM", id(system.value()), system.value()));
        report.unreachableSectors().forEach(sector -> row(
                rows, "TOPOLOGY_QUALITY_UNREACHABLE_SECTOR", id(sector.value()), sector.value()));
        report.hubSystems().forEach(system -> row(
                rows, "TOPOLOGY_QUALITY_HUB", id(system.value()), system.value()));
        report.articulationSystems().forEach(system -> row(
                rows, "TOPOLOGY_QUALITY_ARTICULATION", id(system.value()), system.value()));
        report.bridgeEdges().forEach(edge -> row(
                rows, "TOPOLOGY_QUALITY_BRIDGE", connectionId(edge),
                edge.first().value(), edge.second().value()));
        for (int index = 0; index < report.violations().size(); index++) {
            Stage20TopologyQualityReport.Violation violation = report.violations().get(index);
            row(rows, "TOPOLOGY_QUALITY_VIOLATION", scalar(index),
                    violation.type(), violation.subject(), violation.observed(), violation.limit(),
                    violation.normalizedSeverity());
        }
    }

    private static void addSeedQuality(
            List<CanonicalRow> rows,
            String id,
            Stage20GeneratedWorldSeedAcceptance.SeedResult result) {
        row(rows, "SEED_ACCEPTANCE", id,
                result.version(), result.rootSeed(), result.status(), result.topologyStatus(),
                result.topologyRepairPasses(), result.economicAcceptancePresent(),
                result.placementStatus().map(Enum::name).orElse(""));
        for (int index = 0; index < result.failures().size(); index++) {
            var failure = result.failures().get(index);
            row(rows, "SEED_ACCEPTANCE_FAILURE", id + ':' + index,
                    failure.reason(), failure.subject(), failure.detail());
        }
    }

    private static String stationId(StarSystemId systemId, String placementId) {
        return id(systemId.value()) + ':' + placementId;
    }

    private static String connectionId(JumpConnection connection) {
        return id(connection.first().value()) + ':' + id(connection.second().value());
    }

    private static void appendPosition(List<String> values, LocalPhysicalPosition position) {
        values.add(scalar(position.cellX()));
        values.add(scalar(position.cellY()));
        values.add(scalar(position.offsetXM()));
        values.add(scalar(position.offsetYM()));
    }

    private static void appendSystemIds(List<String> values, List<StarSystemId> systems) {
        values.add(scalar(systems.size()));
        systems.forEach(system -> values.add(id(system.value())));
    }

    private static void appendStrings(List<String> values, List<String> strings) {
        values.add(scalar(strings.size()));
        values.addAll(strings);
    }

    private static void appendMap(List<String> values, Map<String, Double> map) {
        TreeMap<String, Double> sorted = new TreeMap<>(map);
        values.add(scalar(sorted.size()));
        sorted.forEach((key, value) -> {
            values.add(key);
            values.add(scalar(value));
        });
    }

    private static void row(List<CanonicalRow> rows, String domain, String stableId, Object... values) {
        rows.add(new CanonicalRow(domain, stableId, values(values)));
    }

    private static ArrayList<String> values(Object... values) {
        ArrayList<String> result = new ArrayList<>(values.length);
        for (Object value : values) {
            result.add(scalar(value));
        }
        return result;
    }

    private static String scalar(Object value) {
        Objects.requireNonNull(value, "canonical scalar");
        if (value instanceof Double number) {
            return Double.toHexString(number);
        }
        if (value instanceof Float number) {
            return Float.toHexString(number);
        }
        if (value instanceof Enum<?> enumeration) {
            return enumeration.name();
        }
        return value.toString();
    }

    private static String id(long value) {
        return Long.toString(value);
    }
}
