package com.spacesim.content;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.economy.Stage19WarfareSupplyService;
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
import com.spacesim.world.Stage22CorePairPreparedDefenseProbe;
import com.spacesim.world.StrategicOperationService.SupplyDecision;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B09 continuous multi-wave prepared-defense sustainment over existing Stage-19 authorities.
 *
 * <p>The Stage-21D/E prepared-defense probe first proves that each exact core package is admitted only
 * with mission-capable physical readiness, that reserves attach only after physical arrival, and that
 * loss of supply access produces the ordinary withdrawal decision. This test then keeps the exact
 * engineering exit state across three Stage-19 contacts. After the first contact the prepared system
 * can provide exactly one additional authored round per core ship through the ordinary Stage-19F
 * warfare-supply bridge; after that the canonical station stock is empty and an identical reload
 * request fails closed. Damage and all remaining finite stores continue into later contacts. A second
 * run inserts ordinary entity + station-storage save/restore at the replenishment boundary and must
 * converge to the same final physical state.</p>
 *
 * <p>No readiness refill, hidden reserve scalar, faction-specific sustainment modifier or synthetic
 * campaign power score is introduced. The one-round reserve is intentionally tiny: its purpose is to
 * make the transition from prepared support to exhausted support observable inside one deterministic
 * causal lane.</p>
 */
class Stage22CorePairPreparedDefenseEnduranceAcceptanceTest {
    private static final int INITIAL_ROUNDS = 4;
    private static final int PREPARED_RESERVE_ROUNDS = 1;
    private static final long TACTICAL_TICKS = 600L;
    private static final String EMPIRE_AMMO = "ammo.empire_axial_dart_150kg_v1";
    private static final String UNION_AMMO = "ammo.industrial_union_dart_140kg_v1";

    @Test
    void b09PreparedDefenseCarriesFiniteSupportAcrossThreeContactsAndSaveLoad() {
        List<Object> archive = new ArrayList<>();
        for (Permutation permutation : Permutation.values()) {
            var prepared = Stage22CorePairPreparedDefenseProbe.run(permutation);
            assertEquals(SupplyDecision.CONTINUE, prepared.empire().preparedDecision());
            assertEquals(SupplyDecision.CONTINUE, prepared.union().preparedDecision());
            assertEquals(SupplyDecision.SUBMIT_ORDINARY_WITHDRAW_ORDER, prepared.empire().unsupportedDecision());
            assertEquals(SupplyDecision.SUBMIT_ORDINARY_WITHDRAW_ORDER, prepared.union().unsupportedDecision());
            assertTrue(prepared.empire().attachedAfterPhysicalArrival());
            assertTrue(prepared.union().attachedAfterPhysicalArrival());

            EnduranceResult direct = run(permutation, false);
            EnduranceResult restored = run(permutation, true);
            assertEquals(direct, restored,
                    "B09 multi-wave continuation must be identical across ordinary physical save/load");
            assertEquals(3, direct.waves().size());
            assertTrue(direct.firstReloadCommitted());
            assertTrue(direct.secondReloadRejectedWithoutMutation());
            assertEquals(0, direct.finalEmpireReserveRounds());
            assertEquals(0, direct.finalUnionReserveRounds());
            assertTrue(direct.totalRoundsConsumed() > 0L,
                    "B09 endurance lane must physically consume ammunition across its contacts");
            assertTrue(direct.finalEmpireRounds() <= INITIAL_ROUNDS + PREPARED_RESERVE_ROUNDS);
            assertTrue(direct.finalUnionRounds() <= INITIAL_ROUNDS + PREPARED_RESERVE_ROUNDS);

            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("permutation", permutation.name());
            row.put("preparedEmpireMissionReadinessBps", prepared.empire().defenderReadiness().missionReadinessBps());
            row.put("preparedUnionMissionReadinessBps", prepared.union().defenderReadiness().missionReadinessBps());
            row.put("preparedParticipantCountEmpire", prepared.empire().committedParticipantCount());
            row.put("preparedParticipantCountUnion", prepared.union().committedParticipantCount());
            row.put("endurance", direct);
            archive.add(row);
        }

        Stage22CorePairEvidenceArchive.write(
                "B09-prepared-defense-three-contact-finite-sustainment",
                archive,
                "Each mirrored lane first passes the ordinary Stage-21D/E prepared-defense readiness/reinforcement/supply decisions, then carries exact core engineering state through three committed Stage-19 contacts. A canonical Stage-18 station owns exactly one additional authored round per side; Stage-19F commits that finite reserve after contact one, later identical reloads fail closed on empty stock, and entity/storage save-load after the first reload converges byte-for-state with the direct continuation. This closes the previously open B09 multi-wave sustainment/save seam; full survivor repair remains covered separately by Stage-21G recovery evidence rather than being invented inside the defense operation.");
    }

    private static EnduranceResult run(Permutation permutation, boolean restoreAfterFirstReload) {
        var duel = Stage22CorePairTacticalFactory.createDestroyerDuel(permutation);
        var content = duel.content();
        Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault()
                .withAmmunitionCatalog(content.ammunition(), Provenance.STAGE22_AUTHORED);
        Stage18StationStorage station = new Stage18StationStorage(
                Stage18ResourceOntologyLoader.loadDefault(),
                products,
                "station.m22_6.b09.prepared_reserve",
                Map.of(Stage18ManufacturingProductRegistry.AMMUNITION_STORAGE_CLASS, 100_000d),
                Map.of(),
                Map.of(EMPIRE_AMMO, PREPARED_RESERVE_ROUNDS, UNION_AMMO, PREPARED_RESERVE_ROUNDS));
        Stage19WarfareSupplyService supply = new Stage19WarfareSupplyService(products);
        Stage19ExactTacticalEncounterResolver resolver = new Stage19ExactTacticalEncounterResolver(
                content.engineering(), duel.protection(), content.ammunition(), content.launchers());

        List<ImportedCombatantState> inputs = duel.weapons().battleState().combatants().stream()
                .map(actor -> new ImportedCombatantState(
                        actor.spec().entityId(),
                        actor.spec().side(),
                        withRoundCount(actor.engineering(), INITIAL_ROUNDS),
                        actor.transform().position.x,
                        actor.transform().position.y,
                        actor.transform().velocity.x,
                        actor.transform().velocity.y))
                .toList();
        List<ImportedCombatantState> geometry = inputs;
        ArrayList<WaveResult> waves = new ArrayList<>();
        boolean firstReloadCommitted = false;
        boolean secondReloadRejectedWithoutMutation = false;
        long totalConsumed = 0L;

        for (int wave = 1; wave <= 3; wave++) {
            long beforeRounds = inputs.stream()
                    .mapToLong(value -> value.engineering().runtimeState.consumables().ammunitionCount())
                    .sum();
            Stage19ExactTacticalEncounterResolver.Result result = resolver.resolve(inputs, TACTICAL_TICKS);
            long afterRounds = result.combatants().stream()
                    .mapToLong(value -> value.runtimeState().consumables().ammunitionCount())
                    .sum();
            long consumed = Math.max(0L, beforeRounds - afterRounds);
            totalConsumed += consumed;
            waves.add(new WaveResult(
                    wave,
                    beforeRounds,
                    afterRounds,
                    consumed,
                    result.termination().name(),
                    result.combatants().stream().mapToDouble(Stage22CorePairPreparedDefenseEnduranceAcceptanceTest::meanIntegrity)
                            .average().orElseThrow()));

            inputs = result.combatants().stream().map(actor -> {
                var originalGeometry = geometry.stream()
                        .filter(value -> value.entityId() == actor.entityId())
                        .findFirst()
                        .orElseThrow();
                return new ImportedCombatantState(
                        actor.entityId(), actor.side(),
                        new EngineeringComponent(actor.fit(), actor.runtimeState(), actor.instanceState()),
                        originalGeometry.xM(), originalGeometry.yM(),
                        originalGeometry.velocityXMps(), originalGeometry.velocityYMps());
            }).toList();

            if (wave == 1) {
                List<ImportedCombatantState> replenished = new ArrayList<>();
                for (ImportedCombatantState actor : inputs) {
                    Replenishment replenishment = addOneRound(actor.engineering(), content, products, supply, station);
                    assertEquals(Stage19WarfareSupplyService.Status.LOADED, replenishment.status());
                    replenished.add(copyWithEngineering(actor, replenishment.engineering()));
                }
                inputs = List.copyOf(replenished);
                firstReloadCommitted = true;
                assertEquals(0, station.productCount(EMPIRE_AMMO));
                assertEquals(0, station.productCount(UNION_AMMO));

                if (restoreAfterFirstReload) {
                    inputs = inputs.stream().map(Stage22CorePairPreparedDefenseEnduranceAcceptanceTest::roundTrip).toList();
                    station = Stage18StationStorage.restore(
                            Stage18ResourceOntologyLoader.loadDefault(), products, station.snapshot());
                }
            } else if (wave == 2) {
                boolean allRejected = true;
                List<ImportedCombatantState> unchanged = new ArrayList<>();
                for (ImportedCombatantState actor : inputs) {
                    ConsumableState before = actor.engineering().runtimeState.consumables();
                    Replenishment rejected = addOneRound(actor.engineering(), content, products, supply, station);
                    allRejected &= rejected.status() == Stage19WarfareSupplyService.Status.INSUFFICIENT_STOCK;
                    assertSame(before, rejected.engineering().runtimeState.consumables(),
                            "empty B09 reserve must not replace central consumable state");
                    unchanged.add(actor);
                }
                secondReloadRejectedWithoutMutation = allRejected;
                assertTrue(allRejected);
                assertEquals(0, station.productCount(EMPIRE_AMMO));
                assertEquals(0, station.productCount(UNION_AMMO));
            }
        }

        var empire = inputs.stream()
                .filter(value -> value.entityId() == Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID)
                .findFirst().orElseThrow();
        var union = inputs.stream()
                .filter(value -> value.entityId() == Stage22CorePairTacticalFactory.UNION_ENTITY_ID)
                .findFirst().orElseThrow();
        return new EnduranceResult(
                permutation.name(),
                List.copyOf(waves),
                firstReloadCommitted,
                secondReloadRejectedWithoutMutation,
                totalConsumed,
                empire.engineering().runtimeState.consumables().ammunitionCount(),
                union.engineering().runtimeState.consumables().ammunitionCount(),
                station.productCount(EMPIRE_AMMO),
                station.productCount(UNION_AMMO),
                EntityStateMapper.capture(new Entity()
                        .add(new EntityIdComponent(new EntityId(Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID)))
                        .add(empire.engineering())).engineering(),
                EntityStateMapper.capture(new Entity()
                        .add(new EntityIdComponent(new EntityId(Stage22CorePairTacticalFactory.UNION_ENTITY_ID)))
                        .add(union.engineering())).engineering());
    }

    private static EngineeringComponent withRoundCount(EngineeringComponent source, long rounds) {
        ConsumableState current = source.runtimeState.consumables();
        List<ConsumableLoad> loads = current.interfaceLoads().stream()
                .map(load -> {
                    if (load.kind() != InterfaceKind.AMMUNITION) return load;
                    if (load.itemCount() <= 0L) throw new AssertionError("core ammunition load is not itemized");
                    double amountPerRound = load.amount() / load.itemCount();
                    double massPerRound = load.massKg() / load.itemCount();
                    return new ConsumableLoad(
                            load.mountId(), load.interfaceId(), load.kind(),
                            amountPerRound * rounds, massPerRound * rounds, rounds);
                }).toList();
        ConsumableState updatedConsumables = new ConsumableState(
                current.cargoMassKg(), current.storesMassKg(), current.missionPayloadMassKg(),
                current.missionIntegrationVolumeM3(), loads);
        RuntimeState runtime = source.runtimeState;
        RuntimeState updatedRuntime = new RuntimeState(
                updatedConsumables,
                runtime.sharedBusEnergyJ(),
                runtime.shipHeatStoredJ(),
                runtime.localHeatJByMount(),
                runtime.thrustLimitNByMount(),
                runtime.coolantBusCapacityW(),
                runtime.ftlCooldownSecondsByMount());
        return new EngineeringComponent(source.fit, updatedRuntime, source.instanceState);
    }

    private static Replenishment addOneRound(
            EngineeringComponent component,
            com.spacesim.content.weapon.Stage22CorePairWeaponRuntimeCatalogLoader.RuntimeContent content,
            Stage18ManufacturingProductRegistry products,
            Stage19WarfareSupplyService supply,
            Stage18StationStorage station) {
        var hull = content.engineering().findHull(component.fit.hullId());
        var derived = new DerivedShipCalculator(content.engineering()).derive(
                hull, component.fit, component.runtimeState.consumables(), component.instanceState.damage().moduleDamage());
        ShipWeaponEngineeringAdapter.FittedKineticMount mount = new ShipWeaponEngineeringAdapter()
                .deriveKineticMounts(derived, content.ammunition(), content.launchers(), component.instanceState.weaponLoadout())
                .get(0);
        var module = content.engineering().findModule(mount.moduleId());
        var ammunitionInterface = module.interfaces().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .filter(value -> value.id().equals(mount.launcher().ammunitionInterfaceId()))
                .findFirst().orElseThrow();
        String productId = component.instanceState.weaponLoadout()
                .ammunitionContentId(mount.mountId(), mount.launcher().ammunitionInterfaceId())
                .orElseThrow();
        assertTrue(products.findProduct(productId) != null);
        ConsumableState before = component.runtimeState.consumables();
        var loaded = supply.loadAmmunition(
                productId, mount.mountId(), PREPARED_RESERVE_ROUNDS,
                mount.launcher(), ammunitionInterface, before, station);
        if (!loaded.committed()) {
            return new Replenishment(loaded.status(), component);
        }
        RuntimeState runtime = component.runtimeState;
        RuntimeState replenishedRuntime = new RuntimeState(
                loaded.consumables(),
                runtime.sharedBusEnergyJ(),
                runtime.shipHeatStoredJ(),
                runtime.localHeatJByMount(),
                runtime.thrustLimitNByMount(),
                runtime.coolantBusCapacityW(),
                runtime.ftlCooldownSecondsByMount());
        return new Replenishment(
                loaded.status(),
                new EngineeringComponent(component.fit, replenishedRuntime, component.instanceState));
    }

    private static ImportedCombatantState copyWithEngineering(
            ImportedCombatantState source,
            EngineeringComponent engineering) {
        return new ImportedCombatantState(
                source.entityId(), source.side(), engineering,
                source.xM(), source.yM(), source.velocityXMps(), source.velocityYMps());
    }

    private static ImportedCombatantState roundTrip(ImportedCombatantState source) {
        var captured = EntityStateMapper.capture(new Entity()
                .add(new EntityIdComponent(new EntityId(source.entityId())))
                .add(source.engineering()));
        Entity restored = EntityStateMapper.restore(captured);
        assertEquals(captured, EntityStateMapper.capture(restored));
        return copyWithEngineering(source, restored.getComponent(EngineeringComponent.class));
    }

    private static double meanIntegrity(Stage19ExactTacticalEncounterResolver.CombatantResult actor) {
        return actor.instanceState().damage().compartmentIntegrityById().values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow();
    }

    private record Replenishment(
            Stage19WarfareSupplyService.Status status,
            EngineeringComponent engineering) { }

    private record WaveResult(
            int wave,
            long roundsBefore,
            long roundsAfter,
            long roundsConsumed,
            String termination,
            double meanIntegrity) { }

    private record EnduranceResult(
            String permutation,
            List<WaveResult> waves,
            boolean firstReloadCommitted,
            boolean secondReloadRejectedWithoutMutation,
            long totalRoundsConsumed,
            long finalEmpireRounds,
            long finalUnionRounds,
            int finalEmpireReserveRounds,
            int finalUnionReserveRounds,
            com.spacesim.persistence.EntityState.EngineeringState finalEmpireEngineering,
            com.spacesim.persistence.EntityState.EngineeringState finalUnionEngineering) { }
}
