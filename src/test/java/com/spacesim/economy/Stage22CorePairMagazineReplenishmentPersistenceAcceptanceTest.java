package com.spacesim.economy;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.weapon.Stage22CorePairWeaponRuntimeCatalogLoader.RuntimeContent;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.ship.DerivedShipCalculator;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.ImportedCombatantState;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipWeaponEngineeringAdapter;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver;
import com.spacesim.ship.Stage22CorePairTacticalFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B12 persistence closure for a physically replenished exact-core magazine.
 *
 * <p>The existing B12 tests already prove deterministic four-round exhaustion and the physical
 * Stage-18 stock -> Stage-19F supply -> Stage-17.5 consumable reload path. This acceptance inserts
 * the ordinary entity persistence mapper after that paid reload and before the next exact Stage-19
 * encounter. Direct and restored continuations must be identical; persistence may neither refill nor
 * discard the newly loaded ammunition.</p>
 */
class Stage22CorePairMagazineReplenishmentPersistenceAcceptanceTest {
    private static final String EMPIRE_AMMO = "ammo.empire_axial_dart_150kg_v1";
    private static final String UNION_AMMO = "ammo.industrial_union_dart_140kg_v1";
    private static final int RELOAD_ROUNDS = 4;
    private static final long TACTICAL_TICKS = 600L;

    @Test
    void b12PaidReloadSurvivesOrdinaryEntityPersistenceWithIdenticalCombatContinuation() {
        for (Permutation permutation : Permutation.values()) {
            var duel = Stage22CorePairTacticalFactory.createDestroyerDuel(permutation);
            RuntimeContent content = duel.content();
            Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault()
                    .withAmmunitionCatalog(content.ammunition(), Provenance.STAGE22_AUTHORED);
            Stage18StationStorage station = station(products, Map.of(
                    EMPIRE_AMMO, RELOAD_ROUNDS,
                    UNION_AMMO, RELOAD_ROUNDS));
            Stage19WarfareSupplyService supply = new Stage19WarfareSupplyService(products);

            ArrayList<ImportedCombatantState> direct = new ArrayList<>();
            ArrayList<ImportedCombatantState> restored = new ArrayList<>();
            for (var actor : duel.weapons().battleState().combatants()) {
                EngineeringComponent loaded = replenish(
                        withoutAmmunition(actor.engineering()), content, supply, station);
                assertEquals(RELOAD_ROUNDS, loaded.runtimeState.consumables().ammunitionCount());

                EngineeringComponent roundTripped = roundTrip(actor.spec().entityId(), loaded);
                assertEquals(loaded.fit, roundTripped.fit);
                assertEquals(loaded.runtimeState, roundTripped.runtimeState);
                assertEquals(loaded.instanceState, roundTripped.instanceState);
                assertEquals(RELOAD_ROUNDS, roundTripped.runtimeState.consumables().ammunitionCount());

                direct.add(imported(actor, loaded));
                restored.add(imported(actor, roundTripped));
            }

            assertEquals(0, station.productCount(EMPIRE_AMMO));
            assertEquals(0, station.productCount(UNION_AMMO));

            var resolver = new Stage19ExactTacticalEncounterResolver(
                    content.engineering(),
                    duel.protection(),
                    content.ammunition(),
                    content.launchers());
            var directResult = resolver.resolve(List.copyOf(direct), TACTICAL_TICKS);
            var restoredResult = resolver.resolve(List.copyOf(restored), TACTICAL_TICKS);
            assertEquals(directResult, restoredResult,
                    "post-replenishment save/load must preserve exact Stage-19 continuation");
            assertTrue(directResult.combatants().stream()
                            .mapToLong(value -> value.runtimeState().consumables().ammunitionCount())
                            .sum() < (long) RELOAD_ROUNDS * 2L,
                    "restored continuation must consume the physically reloaded rounds");
        }
    }

    private static ImportedCombatantState imported(
            com.spacesim.ship.LiveTacticalBattleRuntimeState.CombatantRuntime actor,
            EngineeringComponent engineering) {
        return new ImportedCombatantState(
                actor.spec().entityId(),
                actor.spec().side(),
                engineering,
                actor.transform().position.x,
                actor.transform().position.y,
                actor.transform().velocity.x,
                actor.transform().velocity.y);
    }

    private static EngineeringComponent roundTrip(long entityId, EngineeringComponent engineering) {
        Entity entity = new Entity()
                .add(new EntityIdComponent(new EntityId(entityId)))
                .add(engineering);
        Entity restored = EntityStateMapper.restore(EntityStateMapper.capture(entity));
        EngineeringComponent value = restored.getComponent(EngineeringComponent.class);
        if (value == null) {
            throw new AssertionError("ordinary entity persistence lost the engineering component");
        }
        return value;
    }

    private static EngineeringComponent replenish(
            EngineeringComponent empty,
            RuntimeContent content,
            Stage19WarfareSupplyService supply,
            Stage18StationStorage station) {
        var mount = fittedMount(empty, content);
        var ammunitionInterface = content.engineering().findModule(mount.moduleId()).interfaces().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .filter(value -> value.id().equals(mount.launcher().ammunitionInterfaceId()))
                .findFirst()
                .orElseThrow();
        String productId = empty.instanceState.weaponLoadout()
                .ammunitionContentId(mount.mountId(), mount.launcher().ammunitionInterfaceId())
                .orElseThrow();
        var loaded = supply.loadAmmunition(
                productId,
                mount.mountId(),
                RELOAD_ROUNDS,
                mount.launcher(),
                ammunitionInterface,
                empty.runtimeState.consumables(),
                station);
        assertEquals(Stage19WarfareSupplyService.Status.LOADED, loaded.status());
        assertEquals(RELOAD_ROUNDS, loaded.loadedRoundCount());

        RuntimeState current = empty.runtimeState;
        RuntimeState replenished = new RuntimeState(
                loaded.consumables(),
                current.sharedBusEnergyJ(),
                current.shipHeatStoredJ(),
                current.localHeatJByMount(),
                current.thrustLimitNByMount(),
                current.coolantBusCapacityW(),
                current.ftlCooldownSecondsByMount());
        return new EngineeringComponent(empty.fit, replenished, empty.instanceState);
    }

    private static ShipWeaponEngineeringAdapter.FittedKineticMount fittedMount(
            EngineeringComponent component,
            RuntimeContent content) {
        var hull = content.engineering().findHull(component.fit.hullId());
        var derived = new DerivedShipCalculator(content.engineering()).derive(
                hull,
                component.fit,
                component.runtimeState.consumables(),
                component.instanceState.damage().moduleDamage());
        return new ShipWeaponEngineeringAdapter().deriveKineticMounts(
                derived,
                content.ammunition(),
                content.launchers(),
                component.instanceState.weaponLoadout()).get(0);
    }

    private static EngineeringComponent withoutAmmunition(EngineeringComponent source) {
        ConsumableState current = source.runtimeState.consumables();
        List<ConsumableLoad> loads = current.interfaceLoads().stream()
                .map(load -> load.kind() == InterfaceKind.AMMUNITION
                        ? new ConsumableLoad(
                                load.mountId(),
                                load.interfaceId(),
                                load.kind(),
                                0d,
                                0d,
                                0L)
                        : load)
                .toList();
        ConsumableState empty = new ConsumableState(
                current.cargoMassKg(),
                current.storesMassKg(),
                current.missionPayloadMassKg(),
                current.missionIntegrationVolumeM3(),
                loads);
        RuntimeState runtime = source.runtimeState;
        return new EngineeringComponent(
                source.fit,
                new RuntimeState(
                        empty,
                        runtime.sharedBusEnergyJ(),
                        runtime.shipHeatStoredJ(),
                        runtime.localHeatJByMount(),
                        runtime.thrustLimitNByMount(),
                        runtime.coolantBusCapacityW(),
                        runtime.ftlCooldownSecondsByMount()),
                source.instanceState);
    }

    private static Stage18StationStorage station(
            Stage18ManufacturingProductRegistry products,
            Map<String, Integer> productCounts) {
        return new Stage18StationStorage(
                Stage18ResourceOntologyLoader.loadDefault(),
                products,
                "station.m22_6.b12.persistence",
                Map.of(Stage18ManufacturingProductRegistry.AMMUNITION_STORAGE_CLASS, 100_000d),
                Map.of(),
                productCounts);
    }
}
