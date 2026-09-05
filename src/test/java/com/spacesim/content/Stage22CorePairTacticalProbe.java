package com.spacesim.content;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.content.Stage22CorePairExperimentProtocol.RunCoordinate;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.ship.*;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.ImportedCombatantState;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.persistence.EntityId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/** Physical B07/B11/B12 controls; equal role is explicitly not an equal-burden balance claim. */
final class Stage22CorePairTacticalProbe {
    static final int TICKS = 600;
    static final String POLICY = "stage22.common_stage19_tactical_policy.v1";

    enum Variant { PATROL, EMPIRE_SENSOR_LOSS, UNION_SENSOR_LOSS, LIMITED_MAGAZINES }

    private Stage22CorePairTacticalProbe() { }

    static Evidence run(Variant variant, RunCoordinate coordinate, boolean restoredStart) {
        var template = Stage22CorePairTacticalFactory.createDestroyerDuel(coordinate.permutation());
        var content = template.content();
        var random = new SplittableRandom(coordinate.seed());
        double separationM = 900d + random.nextDouble() * 900d;
        double lateralM = random.nextDouble(-180d, 180d);
        double velocityMps = random.nextDouble(0d, 12d);
        int orientation = coordinate.permutation() == Stage22CorePairExperimentProtocol.Permutation.DEFAULT ? 1 : -1;
        long rounds = variant == Variant.LIMITED_MAGAZINES ? 4L : 120L;
        var imports = new ArrayList<ImportedCombatantState>();
        var burdens = new ArrayList<StartingBurden>();
        for (var actor : template.weapons().battleState().combatants()) {
            long id = actor.spec().entityId();
            boolean empire = id == Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID;
            int direction = (empire ? 1 : -1) * orientation;
            EngineeringComponent component = actor.engineering();
            boolean sensorLoss = empire ? variant == Variant.EMPIRE_SENSOR_LOSS : variant == Variant.UNION_SENSOR_LOSS;
            if (sensorLoss && component.fit.installedModules().stream().noneMatch(row -> row.mountId().equals("utility_sensor"))) {
                throw new AssertionError("Sensor-loss fixture does not reference an installed sensor");
            }
            var original = component.runtimeState.consumables();
            List<ConsumableLoad> loads = original.interfaceLoads().stream().map(load ->
                    load.kind() == InterfaceKind.AMMUNITION
                            ? new ConsumableLoad(load.mountId(), load.interfaceId(), load.kind(), rounds,
                                    rounds * load.massKg() / load.itemCount(), rounds) : load).toList();
            var stores = new ConsumableState(original.cargoMassKg(), original.storesMassKg(),
                    original.missionPayloadMassKg(), original.missionIntegrationVolumeM3(), loads);
            var before = component.instanceState;
            var damage = new ShipDamageRuntime.Snapshot(before.damage().compartmentIntegrityById(),
                    sensorLoss ? new DamageState(Map.of("utility_sensor", 0d)) : before.damage().moduleDamage());
            component = new EngineeringComponent(component.fit,
                    new ShipEngineeringRuntime(content.engineering()).initialize(component.fit, stores, damage.moduleDamage()),
                    new ShipInstanceRuntimeState(damage, before.shieldStatesByMount(), before.maintenance(),
                            before.weaponLoadout(), before.weaponMountRuntime()));
            var derived = new DerivedShipCalculator(content.engineering()).derive(actor.hull(), component.fit,
                    stores, damage.moduleDamage());
            burdens.add(new StartingBurden(empire ? Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID
                    : Stage22CorePairBalanceEvidence.UNION_FACTION_ID, id, component.fit.hullId(),
                    derived.installedDryMassKg(), derived.totalMassKg(), derived.crewRequired(),
                    derived.continuousPowerDemandW(), derived.ammunitionMassKg(), rounds, derived.reactionMassKg(),
                    derived.accelerationMps2(), sensorLoss));
            if (restoredStart) {
                var entity = new Entity().add(new EntityIdComponent(new EntityId(id))).add(component);
                var snapshot = EntityStateMapper.capture(entity);
                var restored = EntityStateMapper.restore(snapshot);
                if (!snapshot.equals(EntityStateMapper.capture(restored))) throw new AssertionError("Physical-start persistence drift");
                component = restored.getComponent(EngineeringComponent.class);
            }
            imports.add(new ImportedCombatantState(id, actor.spec().side(), component,
                    1000d - direction * separationM / 2d, 700d - direction * lateralM / 2d,
                    direction * velocityMps, 0d));
        }
        var battle = LiveTacticalBattleRuntimeState.importExact(imports, content.engineering(), template.protection());
        var weapons = new LiveTacticalBattleWeaponRuntime(new LiveTacticalBattleControlRuntime(battle),
                content.ammunition(), content.launchers());
        var phases = new ArrayList<Phase>();
        phases.add(phase(weapons));
        long empireVisibleTicks = 0L;
        long unionVisibleTicks = 0L;
        long unauthorizedTargetTicks = 0L;
        for (int tick = 1; tick <= TICKS; tick++) {
            weapons.advanceOneTick();
            for (var control : weapons.controlRuntime().fingerprint().combatants()) {
                if (!control.visibleTargetIds().isEmpty()) {
                    if (control.entityId() == Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID) empireVisibleTicks++;
                    else unionVisibleTicks++;
                }
                if (control.selectedTargetId() != 0L && !control.visibleTargetIds().contains(control.selectedTargetId())) {
                    unauthorizedTargetTicks++;
                }
            }
            if (tick % 150 == 0) phases.add(phase(weapons));
        }
        return new Evidence(variant, coordinate, POLICY, separationM, lateralM, velocityMps, List.copyOf(burdens),
                empireVisibleTicks, unionVisibleTicks, unauthorizedTargetTicks, List.copyOf(phases));
    }

    private static Phase phase(LiveTacticalBattleWeaponRuntime runtime) {
        var fingerprint = runtime.fingerprint();
        return new Phase(runtime.tick(), runtime.elapsedSeconds(), fingerprint.sources(), fingerprint.targets(),
                fingerprint.controlFingerprint().combatants(), runtime.projectiles().size());
    }

    record StartingBurden(String factionId, long entityId, String hullId, double dryMassKg, double loadedMassKg,
            int crew, double continuousPowerW, double ammunitionMassKg, long rounds, double reactionMassKg,
            double accelerationMps2, boolean sensorDisabled) { }

    record Phase(long tick, double seconds, List<LiveTacticalBattleWeaponRuntime.SourceWeaponFingerprint> weapons,
            List<LiveTacticalBattleWeaponRuntime.TargetProtectionFingerprint> protection,
            List<LiveTacticalBattleControlRuntime.CombatantControlFingerprint> control, int flyingBodies) { }

    record Evidence(Variant variant, RunCoordinate coordinate, String policyId, double separationM,
            double lateralM, double closingVelocityPerShipMps, List<StartingBurden> startingBurden,
            long empireVisibleTicks, long unionVisibleTicks, long unauthorizedTargetTicks, List<Phase> phases) {
        Phase last() { return phases.get(phases.size() - 1); }

        boolean valid() {
            if (unauthorizedTargetTicks != 0L) return false;
            for (var source : last().weapons()) {
                var start = startingBurden.stream().filter(row -> row.entityId() == source.entityId()).findFirst().orElseThrow();
                if (source.shotsFired() + source.ammunitionRounds() != start.rounds()) return false;
                if (start.sensorDisabled() && source.shotsFired() != 0L) return false;
            }
            if (variant == Variant.EMPIRE_SENSOR_LOSS && empireVisibleTicks != 0L) return false;
            return variant != Variant.UNION_SENSOR_LOSS || unionVisibleTicks == 0L;
        }
    }

    public static void main(String[] args) {
        int pairs = args.length == 0 ? 1 : Integer.parseInt(args[0]);
        for (Variant variant : Variant.values()) {
            var rows = new ArrayList<Evidence>();
            for (var coordinate : Stage22CorePairExperimentProtocol.pairedSchedule(pairs)) {
                var row = run(variant, coordinate, false);
                if (!row.valid()) throw new AssertionError(row);
                rows.add(row);
            }
            Stage22CorePairEvidenceArchive.write("tactical-" + variant.name().toLowerCase(java.util.Locale.ROOT), rows,
                    "Equal-role physical sensitivity controls; fixed common tactical policy, not equal economic burden or faction AI doctrine. Physical initial-state persistence is supported; no mid-flight whole-battle save is claimed. Finite-duration observations do not establish a winner or satisfy campaign B06-B14.");
            System.out.println(variant + "|pairs=" + pairs + "|" + rows.get(0).last());
        }
    }
}
