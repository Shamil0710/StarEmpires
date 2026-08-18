package com.spacesim.ui;

import com.spacesim.ship.GuidedWeaponBody;
import com.spacesim.ship.LiveTacticalBattleDeceptionRuntime;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.CombatantRuntime;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.ProjectileBody;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BodyGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BodyKind;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ShipGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.TacticalSide;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Strict read-only presentation projection for the scaled Stage-19 production tactical runtime.
 *
 * <p>The projection has no clock and performs no mutation. It exposes every materialized ship plus
 * current kinetic/residual, STRIKE, INTERCEPTOR and DECOY bodies through the existing immutable
 * tactical snapshot consumed by {@link TacticalPrototypeRenderer}. Fields that do not yet have a
 * scaled authoritative read model (current thrust fraction, historical beam/impact events and shield
 * arcs) are deliberately left at neutral/empty presentation values rather than reconstructed from
 * guesses.</p>
 */
public final class ScaledLiveTacticalSimulationProjection {
    /**
     * Projects the current authoritative state without advancing or mutating it.
     *
     * @param runtime scaled authoritative tactical runtime
     * @return immutable presentation-only snapshot
     */
    public TacticalPrototypeVisualSnapshot project(LiveTacticalBattleDeceptionRuntime runtime) {
        LiveTacticalBattleDeceptionRuntime checked = Objects.requireNonNull(runtime, "runtime");
        List<ShipGlyph> ships = checked.battleState().combatants().stream()
                .map(this::shipGlyph)
                .toList();
        ArrayList<BodyGlyph> bodies = new ArrayList<>();
        for (ProjectileBody body : checked.ordnanceRuntime().weaponRuntime().projectiles()) {
            bodies.add(projectileGlyph(body));
        }
        for (GuidedWeaponBody body : checked.ordnanceRuntime().guidedBodies()) {
            bodies.add(guidedGlyph(BodyKind.GUIDED_MISSILE, body));
        }
        for (GuidedWeaponBody body : checked.defenseRuntime().interceptorBodies()) {
            bodies.add(guidedGlyph(BodyKind.INTERCEPTOR, body));
        }
        for (GuidedWeaponBody body : checked.decoyRuntime().decoyBodies()) {
            bodies.add(guidedGlyph(BodyKind.DECOY, body));
        }
        return new TacticalPrototypeVisualSnapshot(
                ships,
                bodies,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private ShipGlyph shipGlyph(CombatantRuntime combatant) {
        double integrity = combatant.hull().compartments().stream()
                .mapToDouble(value -> combatant.engineering().instanceState.damage()
                        .compartmentIntegrityById().getOrDefault(value.id(), 1d))
                .average()
                .orElse(1d);
        double speedSquared = combatant.transform().velocity.x * combatant.transform().velocity.x
                + combatant.transform().velocity.y * combatant.transform().velocity.y;
        double heading = speedSquared > 1e-12d
                ? Math.atan2(combatant.transform().velocity.y, combatant.transform().velocity.x)
                : (combatant.spec().side() == Side.ALPHA ? 0d : Math.PI);
        return new ShipGlyph(
                combatant.spec().entityId(),
                tacticalSide(combatant.spec().side()),
                combatant.transform().position.x,
                combatant.transform().position.y,
                heading,
                combatant.hull().boundingDimensionsM().lengthM(),
                combatant.hull().boundingDimensionsM().widthM(),
                0d,
                integrity,
                integrity <= 0d);
    }

    private static TacticalSide tacticalSide(Side side) {
        return switch (Objects.requireNonNull(side, "side")) {
            case ALPHA -> TacticalSide.ALPHA;
            case BETA -> TacticalSide.BETA;
        };
    }

    private static BodyGlyph projectileGlyph(ProjectileBody body) {
        return new BodyGlyph(
                BodyKind.KINETIC_PROJECTILE,
                body.projectileId(),
                body.xM(),
                body.yM(),
                heading(body.velocityXMps(), body.velocityYMps()),
                body.lengthM(),
                body.diameterM(),
                0d);
    }

    private static BodyGlyph guidedGlyph(BodyKind kind, GuidedWeaponBody body) {
        return new BodyGlyph(
                kind,
                body.bodyId(),
                body.xM(),
                body.yM(),
                heading(body.velocityXMps(), body.velocityYMps()),
                body.lengthM(),
                body.diameterM(),
                0d);
    }

    private static double heading(double velocityX, double velocityY) {
        return velocityX * velocityX + velocityY * velocityY > 1e-12d
                ? Math.atan2(velocityY, velocityX)
                : 0d;
    }
}
