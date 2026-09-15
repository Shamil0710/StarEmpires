package com.spacesim.economy;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.weapon.Stage22CorePairWeaponRuntimeCatalogLoader.RuntimeContent;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B12 physical replenishment acceptance for the exact Empire/Industrial Union magazines.
 *
 * <p>The existing B12 tactical sensitivity batch proves that four physical rounds are exhausted and
 * firing stops. This acceptance closes the next causal seam: the same authored ammunition identities
 * are registered as Stage-18 finished products, removed from canonical station storage by the
 * Stage-19F supply service, loaded into the same Stage-17.5 consumable interfaces, and then consumed
 * again by the exact Stage-19 tactical resolver. No docking refill, readiness scalar or faction-local
 * magazine authority exists.</p>
 */
class Stage22CorePairMagazineReplenishmentAcceptanceTest {
    private static final String EMPIRE_AMMO = "ammo.empire_axial_dart_150kg_v1";
    private static final String UNION_AMMO = "ammo.industrial_union_dart_140kg_v1";
    private static final int RELOAD_ROUNDS = 4;
    private static final long TACTICAL_TICKS = 600L;

    @Test
    void b12ReloadsOnlyCountableStage18StockAndExactStage19CombatConsumesItAgain() {
        for (Permutation permutation : Permutation.values()) {
            var duel = Stage22CorePairTacticalFactory.createDestroyerDuel(permutation);
            RuntimeContent content = duel.content();
            Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault()
                    .withAmmunitionCatalog(content.ammunition(), Provenance.STAGE22_AUTHORED);
            Stage18StationStorage station = station(products, Map.of(
                    EMPIRE_AMMO, RELOAD_ROUNDS,
                    UNION_AMMO, RELOAD_ROUNDS));
            Stage19WarfareSupplyService supply = new Stage19WarfareSupplyService(products);

            ArrayList<ImportedCombatantState> reloaded = new ArrayList<>();
            for (var actor : duel.weapons().battleState().combatants()) {
                EngineeringComponent empty = withoutAmmunition(actor.engineering());
                assertEquals(0L, empty.runtimeState.consumables().ammunitionCount());

                ReplenishedShip loaded = replenish(empty, content, products, supply, station);
                assertEquals(RELOAD_ROUNDS, loaded.engineering().runtimeState.consumables().ammunitionCount());
                assertEquals(0, station.productCount(loaded.productId()),
                        "each exact four-round magazine must consume its complete staged stock");
                reloaded.add(new ImportedCombatantState(
                        actor.spec().entityId(),
                        actor.spec().side(),
                        loaded.engineering(),
                        actor.transform().position.x,
                        actor.transform().position.y,
                        actor.transform().velocity.x,
                        actor.transform().velocity.y));
            }

            var resolver = new Stage19ExactTacticalEncounterResolver(
                    content.engineering(),
                    duel.protection(),
                    content.ammunition(),
                    content.launchers());
            var result = resolver.resolve(List.copyOf(reloaded), TACTICAL_TICKS);

            long remainingRounds = result.combatants().stream()
                    .mapToLong(value -> value.runtimeState().consumables().ammunitionCount())
                    .sum();
            assertTrue(remainingRounds < (long) RELOAD_ROUNDS * 2L,
                    "after physical Stage-18/19F replenishment, exact Stage-19 combat must consume rounds again");
            for (var combatant : result.combatants()) {
                assertTrue(combatant.runtimeState().consumables().ammunitionCount() <= RELOAD_ROUNDS,
                        "tactical execution cannot manufacture ammunition above the reloaded count");
            }
            assertEquals(0, station.productCount(EMPIRE_AMMO));
            assertEquals(0, station.productCount(UNION_AMMO));
        }
    }

    @Test
    void b12EmptyStationCannotCreateAFreeReload() {
        var duel = Stage22CorePairTacticalFactory.createDestroyerDuel(Permutation.DEFAULT);
        RuntimeContent content = duel.content();
        Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault()
                .withAmmunitionCatalog(content.ammunition(), Provenance.STAGE22_AUTHORED);
        Stage18StationStorage emptyStation = station(products, Map.of());
        Stage19WarfareSupplyService supply = new Stage19WarfareSupplyService(products);
        EngineeringComponent empty = withoutAmmunition(duel.weapons().battleState().combatants().get(0).engineering());

        var mount = fittedMount(empty, content);
        var ammunitionInterface = ammunitionInterface(content, mount.moduleId(), mount.launcher().ammunitionInterfaceId());
        String productId = empty.instanceState.weaponLoadout()
                .ammunitionContentId(mount.mountId(), mount.launcher().ammunitionInterfaceId())
                .orElseThrow();
        ConsumableState before = empty.runtimeState.consumables();
        var rejected = supply.loadAmmunition(
                productId,
                mount.mountId(),
                RELOAD_ROUNDS,
                mount.launcher(),
                ammunitionInterface,
                before,
                emptyStation);

        assertEquals(Stage19WarfareSupplyService.Status.INSUFFICIENT_STOCK, rejected.status());
        assertSame(before, rejected.consumables(),
                "rejected physical reload must retain the exact central consumable state");
        assertEquals(0, emptyStation.productCount(productId));
    }

    private static ReplenishedShip replenish(
            EngineeringComponent empty,
            RuntimeContent content,
            Stage18ManufacturingProductRegistry products,
            Stage19WarfareSupplyService supply,
            Stage18StationStorage station) {
        var mount = fittedMount(empty, content);
        var ammunitionInterface = ammunitionInterface(content, mount.moduleId(), mount.launcher().ammunitionInterfaceId());
        String productId = empty.instanceState.weaponLoadout()
                .ammunitionContentId(mount.mountId(), mount.launcher().ammunitionInterfaceId())
                .orElseThrow();
        var product = products.findProduct(productId);
        int beforeStock = station.productCount(productId);

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
        assertEquals(product.unitMassKg() * RELOAD_ROUNDS, loaded.loadedMassKg(), 1e-9d);
        assertEquals(beforeStock - RELOAD_ROUNDS, station.productCount(productId));
        RuntimeState current = empty.runtimeState;
        RuntimeState replenishedRuntime = new RuntimeState(
                loaded.consumables(),
                current.sharedBusEnergyJ(),
                current.shipHeatStoredJ(),
                current.localHeatJByMount(),
                current.thrustLimitNByMount(),
                current.coolantBusCapacityW(),
                current.ftlCooldownSecondsByMount());
        return new ReplenishedShip(
                productId,
                new EngineeringComponent(empty.fit, replenishedRuntime, empty.instanceState));
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

    private static com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition ammunitionInterface(
            RuntimeContent content,
            String moduleId,
            String interfaceId) {
        var module = content.engineering().findModule(moduleId);
        return module.interfaces().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .filter(value -> value.id().equals(interfaceId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing exact ammunition interface " + moduleId + ":" + interfaceId));
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
        RuntimeState updated = new RuntimeState(
                empty,
                runtime.sharedBusEnergyJ(),
                runtime.shipHeatStoredJ(),
                runtime.localHeatJByMount(),
                runtime.thrustLimitNByMount(),
                runtime.coolantBusCapacityW(),
                runtime.ftlCooldownSecondsByMount());
        return new EngineeringComponent(source.fit, updated, source.instanceState);
    }

    private static Stage18StationStorage station(
            Stage18ManufacturingProductRegistry products,
            Map<String, Integer> productCounts) {
        return new Stage18StationStorage(
                Stage18ResourceOntologyLoader.loadDefault(),
                products,
                "station.m22_6.b12.magazine_replenishment",
                Map.of(Stage18ManufacturingProductRegistry.AMMUNITION_STORAGE_CLASS, 100_000d),
                Map.of(),
                productCounts);
    }

    private record ReplenishedShip(String productId, EngineeringComponent engineering) { }
}
