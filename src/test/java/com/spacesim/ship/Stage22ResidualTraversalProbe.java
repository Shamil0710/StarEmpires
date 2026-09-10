package com.spacesim.ship;

import com.spacesim.content.Stage22CorePairExperimentProtocol.Permutation;
import com.spacesim.ship.ShipEngineeringState.DamageState;

import java.util.Map;

/** Reproduces a penetrating residual's repeated surface damage during a single hull crossing. */
final class Stage22ResidualTraversalProbe {
    private Stage22ResidualTraversalProbe() { }

    static Result run(boolean externalImpact) {
        var duel = Stage22CorePairTacticalFactory.createDestroyerDuel(Permutation.DEFAULT);
        var runtime = duel.weapons();
        for (var actor : runtime.battleState().combatants()) {
            var component = actor.engineering();
            var before = component.instanceState;
            component.setInstanceState(new ShipInstanceRuntimeState(new ShipDamageRuntime.Snapshot(
                    before.damage().compartmentIntegrityById(), new DamageState(Map.of("utility_sensor", 0d, "core_drive", 0d))),
                    before.shieldStatesByMount(), before.maintenance(), before.weaponLoadout(), before.weaponMountRuntime()));
        }
        var target = runtime.battleState().requireCombatant(Stage22CorePairTacticalFactory.UNION_ENTITY_ID);
        double x = target.transform().position.x - target.hull().boundingDimensionsM().lengthM() * 0.5d;
        var body = new ProjectileBody(226_990L, Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID, 0L,
                "material.empire_service_alloy_v1", WeaponDefinition.ProjectileShape.DART,
                1.8d, 0.075d, 150d, x, target.transform().position.y, 12_000d, 0d);
        if (externalImpact) {
            var result = runtime.resolveExternalPhysicalImpact(target.spec().entityId(), body,
                    target.transform().position.x, target.transform().position.y);
            if (result.postProtectionProjectile() == null) throw new AssertionError("Traversal requires a physical residual");
            runtime.acceptExternalProjectile(result.postProtectionProjectile());
        } else {
            // An unprocessed body starting on the surface still needs its first collision response.
            runtime.acceptExternalProjectile(body);
        }
        for (int i = 0; i < 12; i++) runtime.advanceOneTick();
        if (runtime.projectiles().stream().noneMatch(row -> row.projectileId() == body.projectileId())) {
            throw new AssertionError("Residual must remain a physical body");
        }
        long firstCrossing = runtime.impactsOn(target.spec().entityId());
        boolean releasedAfterExit = runtime.fingerprint().surfaceContactsByProjectileId().isEmpty();
        var residual = runtime.projectiles().stream().filter(row -> row.projectileId() == body.projectileId())
                .findFirst().orElseThrow();
        // Explicit scenario repositioning causes a new outside-to-inside crossing of the same hull.
        target.transform().position.x = (float) (residual.xM()
                + target.hull().boundingDimensionsM().lengthM() * 0.5d + 10d);
        for (int i = 0; i < 12; i++) runtime.advanceOneTick();
        return new Result(firstCrossing, runtime.impactsOn(target.spec().entityId()), releasedAfterExit,
                runtime.fingerprint());
    }

    record Result(long firstCrossingImpacts, long afterReentryImpacts, boolean releasedAfterExit,
            LiveTacticalBattleWeaponRuntime.BattleWeaponFingerprint fingerprint) { }

    public static void main(String[] args) {
        for (boolean external : new boolean[] {true, false}) {
            var result = run(external);
            System.out.println("external=" + external + "|first=" + result.firstCrossingImpacts()
                    + "|reentry=" + result.afterReentryImpacts() + "|released=" + result.releasedAfterExit());
            if (result.firstCrossingImpacts() != 1L || result.afterReentryImpacts() != 2L
                    || !result.releasedAfterExit()) throw new AssertionError(result);
        }
    }
}
