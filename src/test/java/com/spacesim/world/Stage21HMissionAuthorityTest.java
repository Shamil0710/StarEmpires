package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.Stage18IndustrialState;
import com.spacesim.persistence.Stage18IndustrialState.PhysicalSourceSnapshot;
import com.spacesim.persistence.Stage20FreightPersistentState;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryEvidence;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoverySource;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledge;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticKnowledge;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef;
import com.spacesim.world.Stage21HMissionAuthority.Result;
import com.spacesim.world.Stage21HNpcMissionState.MissionObjective;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21HMissionAuthorityTest {

    @Test
    void freightDeliveryUsesPhysicalOrderAndRejectsForeignIssuer() {
        Fixture fixture = fixture();
        TransportOrderState order = fixture.freight().orders().get(0);
        long threshold = (long) Math.floor(order.deliveredMassKg()) + 1L;
        MissionObjective objective = new MissionObjective(
                ObjectiveAuthority.FREIGHT,
                ObjectiveKind.FREIGHT_ORDER_DELIVERED_KG_AT_LEAST,
                order.orderId(), 0L, threshold, "");

        Stage21HMissionAuthority.requireIssuerAuthority(
                fixture.world(), fixture.freight(), fixture.industry(), fixture.discovery(order.stableFactionId()),
                StrategicOperationState.empty(), order.stableFactionId(), objective);
        assertEquals(Result.PENDING, Stage21HMissionAuthority.evaluate(
                fixture.world(), fixture.freight(), fixture.industry(), fixture.discovery(order.stableFactionId()),
                StrategicOperationState.empty(), objective).result());

        String foreign = fixture.world().snapshot().factions().stream()
                .map(FactionEconomicState::factionContentId)
                .filter(value -> !value.equals(order.stableFactionId()))
                .findFirst().orElseThrow();
        assertThrows(IllegalStateException.class, () -> Stage21HMissionAuthority.requireIssuerAuthority(
                fixture.world(), fixture.freight(), fixture.industry(), fixture.discovery(foreign),
                StrategicOperationState.empty(), foreign, objective));
    }

    @Test
    void movingThenRemovingUnderlyingFleetChangesPredicateDeterministically() {
        Fixture fixture = fixture();
        WorldSimulation world = fixture.world();
        List<StarSystemId> systems = world.getTopology().systems().stream().map(value -> value.id()).toList();
        StarSystemId origin = systems.get(0);
        StarSystemId other = systems.stream().filter(value -> !value.equals(origin)).findFirst().orElseThrow();
        String owner = world.snapshot().factions().get(0).factionContentId();
        int runtimeFaction = world.findFactionRuntimeId(owner).orElseThrow();
        Entity entity = new Entity()
                .add(new IdentityComponent("Stage21H target", IdentityComponent.Kind.FLEET))
                .add(new FactionComponent(runtimeFaction));
        EntityId localId = world.createEntity(origin, entity);
        FleetId fleetId = world.findFleetByLocal(origin, localId).orElseThrow();
        MissionObjective objective = new MissionObjective(
                ObjectiveAuthority.FLEET,
                ObjectiveKind.FLEET_PRESENT_IN_SYSTEM,
                Long.toString(fleetId.value()), other.value(), 0L, "");

        assertEquals(Result.PENDING, Stage21HMissionAuthority.evaluate(
                world, fixture.freight(), fixture.industry(), fixture.discovery(owner),
                StrategicOperationState.empty(), objective).result());
        assertTrue(world.removeEntity(origin, localId));
        assertEquals(Result.FAILED, Stage21HMissionAuthority.evaluate(
                world, fixture.freight(), fixture.industry(), fixture.discovery(owner),
                StrategicOperationState.empty(), objective).result());
    }

    @Test
    void derelictRequiresBothOwnerLocalDiscoveryAndFinitePhysicalRecovery() {
        Fixture fixture = fixture();
        String owner = fixture.world().snapshot().factions().get(0).factionContentId();
        StarSystemId system = fixture.world().getTopology().systems().get(0).id();
        String objectId = "special.test.derelict";
        String sourceId = "salvage.test.derelict";
        Stage20DiscoveryKnowledgeState discovery = discoveredDerelict(owner, system, objectId);
        Stage18IndustrialState untouched = withSalvage(fixture.industry(), sourceId, 100d, 100d);
        Stage18IndustrialState recovered = withSalvage(fixture.industry(), sourceId, 100d, 40d);
        MissionObjective objective = new MissionObjective(
                ObjectiveAuthority.INDUSTRY,
                ObjectiveKind.DERELICT_DISCOVERED_AND_SALVAGED_KG_AT_LEAST,
                objectId + "|" + sourceId,
                system.value(),
                50L,
                "SPECIAL_LOCATION:KNOWN_STATIC_LOCATION");

        Stage21HMissionAuthority.requireIssuerAuthority(
                fixture.world(), fixture.freight(), untouched, discovery,
                StrategicOperationState.empty(), owner, objective);
        assertEquals(Result.PENDING, Stage21HMissionAuthority.evaluate(
                fixture.world(), fixture.freight(), untouched, discovery,
                StrategicOperationState.empty(), objective).result());
        assertEquals(Result.SATISFIED, Stage21HMissionAuthority.evaluate(
                fixture.world(), fixture.freight(), recovered, discovery,
                StrategicOperationState.empty(), objective).result());

        Stage20DiscoveryKnowledgeState unknown = new Stage20DiscoveryKnowledgeState(owner, List.of());
        assertEquals(Result.PENDING, Stage21HMissionAuthority.evaluate(
                fixture.world(), fixture.freight(), recovered, unknown,
                StrategicOperationState.empty(), objective).result());
    }

    private static Fixture fixture() {
        var generated = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        var runtime = generated.runtime();
        Stage20GeneratedWorldRuntimePersistentState saved = runtime.captureState();
        return new Fixture(runtime.world(), saved.freight(), saved.campaign().industrialState(),
                saved.campaign().discoveryState());
    }

    private static Stage20DiscoveryKnowledgeState discoveredDerelict(
            String owner,
            StarSystemId system,
            String objectId) {
        StaticObjectRef ref = new StaticObjectRef(system, StaticObjectKind.SPECIAL_LOCATION, objectId);
        DiscoveryEvidence evidence = new DiscoveryEvidence(
                DiscoverySource.PHYSICAL_VISIT_OR_SURVEY,
                "survey.test.derelict",
                1d,
                OptionalDouble.empty());
        StaticKnowledge knowledge = new StaticKnowledge(
                ref,
                DiscoveryState.KNOWN_STATIC_LOCATION,
                Optional.of("special.derelict"),
                Optional.of(LocalPhysicalPosition.origin()),
                ResourceKnowledge.none(),
                List.of(evidence),
                1d,
                1d);
        return new Stage20DiscoveryKnowledgeState(owner, List.of(knowledge));
    }

    private static Stage18IndustrialState withSalvage(
            Stage18IndustrialState base,
            String sourceId,
            double initial,
            double remaining) {
        ArrayList<PhysicalSourceSnapshot> sources = new ArrayList<>(base.sources());
        sources.add(new PhysicalSourceSnapshot(
                sourceId,
                SourceKind.SALVAGE_STREAM,
                "salvage.stream.test",
                ExtractionEnvironment.SALVAGE_SITE,
                "commodity.metal.ferrous_structural",
                initial,
                remaining,
                1d,
                1d,
                Set.of("capability.process.recycling")));
        return new Stage18IndustrialState(
                base.schemaVersion(), base.contentFingerprint(), base.simulationTick(),
                sources, base.stationStorages(), base.facilities(), base.yards(),
                base.constructionOrders(), base.processOrders());
    }

    private record Fixture(
            WorldSimulation world,
            Stage20FreightPersistentState freight,
            Stage18IndustrialState industry,
            com.spacesim.persistence.Stage20DiscoveryPersistentState discoveryState) {
        private Stage20DiscoveryKnowledgeState discovery(String owner) {
            return discoveryState.knowledgeFor(owner);
        }
    }
}
