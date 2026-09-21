package com.spacesim.world;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22CorePairProtectionCatalogLoader;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.ImportedCombatantState;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.CombatantResult;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.Result;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver.Termination;
import com.spacesim.world.FleetCommandState.OrderSource;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftMissionState.MissionOrder;
import com.spacesim.world.SmallCraftMissionState.MissionStatus;
import com.spacesim.world.SmallCraftMissionState.MissionTarget;
import com.spacesim.world.SmallCraftMissionState.MissionType;
import com.spacesim.world.SmallCraftMissionState.TargetKind;
import com.spacesim.world.SmallCraftTacticalEncounterService.ExternalCombatant;
import com.spacesim.world.SmallCraftTacticalEncounterService.LocalFlightState;
import com.spacesim.world.SmallCraftTacticalEncounterService.SmallCraftParticipant;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SmallCraftTacticalEncounterServiceTest {

    @Test
    void survivorStateCommitsAndDestroyedCraftIsPermanentWithoutIdentityReuse() {
        Fixture fixture = fixtureTwoCraft();
        double initialReactionMass = fixture.registry.find(fixture.alpha).orElseThrow()
                .runtimeState().consumables().reactionMassKg();
        long allocatorBefore = fixture.registry.nextIdValue();

        SmallCraftTacticalEncounterService service = new SmallCraftTacticalEncounterService(
                fixture.registry,
                fixture.hangars,
                (imported, maximumTicks) -> deterministicOutcome(imported, true));

        var result = service.resolve(
                fixture.missions,
                List.of(
                        new SmallCraftParticipant(
                                fixture.alpha, Side.ALPHA, new LocalFlightState(0d, 0d, 10d, 0d)),
                        new SmallCraftParticipant(
                                fixture.beta, Side.BETA, new LocalFlightState(1_000d, 0d, -10d, 0d))),
                List.of(),
                10L);

        assertEquals(
                initialReactionMass - 100d,
                fixture.registry.find(fixture.alpha).orElseThrow()
                        .runtimeState().consumables().reactionMassKg(),
                1e-9);
        assertTrue(fixture.registry.find(fixture.beta).isEmpty());
        assertEquals(
                MissionStatus.ACTIVE,
                result.missionState().requireMission(1L).status());
        assertEquals(
                MissionStatus.FAILED,
                result.missionState().requireMission(2L).status());
        assertEquals(allocatorBefore, fixture.registry.nextIdValue(),
                "physical loss must not rewind or advance the production allocator");

        SmallCraftId next = fixture.registry.reserveIdentityForCompletedProduction();
        assertEquals(allocatorBefore, next.value());
        assertNotEquals(fixture.beta, next);
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.registry.removeDestroyedCraft(fixture.beta));
    }

    @Test
    void embarkedCraftFailsBeforeDetachedTacticalAuthorityRuns() {
        SmallCraftRegistry registry = SmallCraftRegistry.empty(ProductionSmallCraftFixture.fitAuthority());
        SmallCraftId id = registry.reserveIdentityForCompletedProduction();
        registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                id, 20L, 2_000d, 200_000d, 1d, 0d));
        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(registry);
        BayDefinition bay = new BayDefinition(
                new BayId("carrier.e-test", "bay.1"),
                HostKind.SHIP,
                new Dimensions3d(500d, 500d, 500d),
                10_000_000d,
                100_000_000d,
                1d);
        hangars.assign(id, bay, OccupancyState.READY);
        SmallCraftMissionState missions = SmallCraftMissionState.empty().add(activeMission(1L, id));
        AtomicBoolean invoked = new AtomicBoolean();

        SmallCraftTacticalEncounterService service = new SmallCraftTacticalEncounterService(
                registry,
                hangars,
                (imported, maximumTicks) -> {
                    invoked.set(true);
                    return deterministicOutcome(imported, false);
                });

        assertThrows(IllegalStateException.class, () -> service.resolve(
                missions,
                List.of(new SmallCraftParticipant(
                        id, Side.ALPHA, new LocalFlightState(0d, 0d, 0d, 0d))),
                List.of(externalOpponent(new SmallCraftId(999L))),
                1L));
        assertFalse(invoked.get());
        assertTrue(registry.find(id).isPresent());
    }

    @Test
    void productionBridgeUsesRealStage19ResolverForSmallCraftAndExternalCombatant() {
        SmallCraftRegistry registry = SmallCraftRegistry.empty(ProductionSmallCraftFixture.fitAuthority());
        SmallCraftId id = registry.reserveIdentityForCompletedProduction();
        SmallCraftState state = stage19CompatibleCraft(
                id, "faction.empire");
        registry.registerProducedCraft(state);
        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(registry);
        SmallCraftMissionState missions = SmallCraftMissionState.empty().add(activeMission(1L, id));

        SmallCraftId externalId = new SmallCraftId(900L);
        SmallCraftState externalState = stage19CompatibleCraft(
                externalId, "faction.industrial_union");
        ExternalCombatant external = new ExternalCombatant(
                "carrier.external",
                Side.BETA,
                externalState.stableFactionId(),
                SmallCraftEngineeringMaterializationBridge.materialize(externalState),
                new LocalFlightState(1_400d, 0d, 0d, 0d));

        SmallCraftTacticalEncounterService service =
                SmallCraftTacticalEncounterService.production(registry, hangars);
        var result = service.resolve(
                missions,
                List.of(new SmallCraftParticipant(
                        id, Side.ALPHA, new LocalFlightState(0d, 0d, 0d, 0d))),
                List.of(external),
                1L);

        assertEquals(1L, result.ticksExecuted());
        assertEquals(1, result.smallCraft().size());
        assertEquals(1, result.externalCombatants().size());
        assertEquals("carrier.external", result.externalCombatants().get(0).referenceId());
        assertTrue(
                result.smallCraft().get(0).destroyed() || registry.find(id).isPresent(),
                "survivor must remain the same registry identity; physical destruction may remove it");
    }

    private static SmallCraftState stage19CompatibleCraft(
            SmallCraftId id,
            String stableFactionId) {
        SmallCraftState base = ProductionSmallCraftFixture.craft(
                id, 20L, 2_000d, 200_000d, 1d, 0d);
        var engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        var hull = engineering.findHull(base.fit().hullId());
        var protection = Stage22CorePairProtectionCatalogLoader.project(engineering);
        var damage = ShipDamageRuntime.Snapshot.pristine(
                hull,
                protection.findHullDamageLayout(hull.id()));
        ShipInstanceRuntimeState instance = new ShipInstanceRuntimeState(
                damage,
                base.instanceState().shieldStatesByMount(),
                base.instanceState().maintenance(),
                base.instanceState().weaponLoadout(),
                base.instanceState().weaponMountRuntime());
        return new SmallCraftState(
                id,
                stableFactionId,
                base.designId(),
                base.fit(),
                base.runtimeState(),
                instance);
    }

    private static Result deterministicOutcome(
            List<ImportedCombatantState> imported,
            boolean destroySecond) {
        ArrayList<CombatantResult> rows = new ArrayList<>();
        for (ImportedCombatantState row : imported) {
            RuntimeState runtime = row.engineering().runtimeState;
            if (row.entityId() == 1L) {
                runtime = withReactionMass(runtime, runtime.consumables().reactionMassKg() - 100d);
            }
            rows.add(new CombatantResult(
                    row.entityId(),
                    row.side(),
                    row.engineering().fit,
                    runtime,
                    row.engineering().instanceState,
                    row.xM() + 5d,
                    row.yM(),
                    row.velocityXMps(),
                    row.velocityYMps(),
                    destroySecond && row.entityId() == 2L));
        }
        return new Result(1L, Termination.ENCOUNTER_HORIZON, List.copyOf(rows));
    }

    private static RuntimeState withReactionMass(
            RuntimeState source,
            double reactionMassKg) {
        List<ConsumableLoad> loads = source.consumables().interfaceLoads().stream()
                .map(load -> load.kind() == InterfaceKind.REACTION_MASS
                        ? new ConsumableLoad(
                                load.mountId(),
                                load.interfaceId(),
                                load.kind(),
                                reactionMassKg,
                                reactionMassKg,
                                load.itemCount())
                        : load)
                .toList();
        ConsumableState consumables = new ConsumableState(
                source.consumables().cargoMassKg(),
                source.consumables().storesMassKg(),
                source.consumables().missionPayloadMassKg(),
                source.consumables().missionIntegrationVolumeM3(),
                loads);
        return new RuntimeState(
                consumables,
                source.sharedBusEnergyJ(),
                source.shipHeatStoredJ(),
                source.localHeatJByMount(),
                source.thrustLimitNByMount(),
                source.coolantBusCapacityW(),
                source.ftlCooldownSecondsByMount());
    }

    private static ExternalCombatant externalOpponent(SmallCraftId id) {
        SmallCraftState state = ProductionSmallCraftFixture.craft(
                id, 20L, 2_000d, 200_000d, 1d, 0d);
        return new ExternalCombatant(
                "external." + id.value(),
                Side.BETA,
                "faction.external",
                SmallCraftEngineeringMaterializationBridge.materialize(state),
                new LocalFlightState(1_000d, 0d, 0d, 0d));
    }

    private static Fixture fixtureTwoCraft() {
        SmallCraftRegistry registry = SmallCraftRegistry.empty(ProductionSmallCraftFixture.fitAuthority());
        SmallCraftId alpha = registry.reserveIdentityForCompletedProduction();
        SmallCraftId beta = registry.reserveIdentityForCompletedProduction();
        registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                alpha, 20L, 2_000d, 200_000d, 1d, 0d));
        SmallCraftState betaState = ProductionSmallCraftFixture.craft(
                beta, 20L, 2_000d, 200_000d, 1d, 0d);
        registry.registerProducedCraft(new SmallCraftState(
                betaState.id(),
                "faction.industrial_union",
                betaState.designId(),
                betaState.fit(),
                betaState.runtimeState(),
                betaState.instanceState()));
        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(registry);
        SmallCraftMissionState missions = SmallCraftMissionState.empty()
                .add(activeMission(1L, alpha))
                .add(activeMission(2L, beta));
        return new Fixture(registry, hangars, missions, alpha, beta);
    }

    private static MissionOrder activeMission(long id, SmallCraftId craftId) {
        return new MissionOrder(
                id,
                craftId,
                OrderSource.AI,
                MissionType.CAP,
                new MissionTarget(TargetKind.AREA, "area.e-test"),
                10L,
                MissionStatus.ACTIVE);
    }

    private record Fixture(
            SmallCraftRegistry registry,
            SmallCraftHangarRegistry hangars,
            SmallCraftMissionState missions,
            SmallCraftId alpha,
            SmallCraftId beta) { }
}
