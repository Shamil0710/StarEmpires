package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.Vector3d;
import com.spacesim.content.ship.ShipProtectionCatalog.HullDamageLayout;
import com.spacesim.ship.HeavyImpactResolver.ImpactResult;
import com.spacesim.ship.ShipDamageRuntime.DamageEvent;
import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;

import java.util.Objects;

/**
 * Stage-17.5F authoritative composition for one kinetic impact:
 * shield interaction → residual physical body → armor response → local compartment/subsystem damage.
 */
public final class KineticProtectionRuntime {
    private final ShieldFieldRuntime shieldRuntime;
    private final HeavyImpactResolver impactResolver;
    private final ShipDamageRuntime damageRuntime;

    /**
     * Creates the common kinetic protection path.
     *
     * @param shieldRuntime finite shield runtime
     * @param impactResolver bounded material response
     * @param damageRuntime local compartment/subsystem router
     */
    public KineticProtectionRuntime(
            ShieldFieldRuntime shieldRuntime,
            HeavyImpactResolver impactResolver,
            ShipDamageRuntime damageRuntime) {
        this.shieldRuntime = Objects.requireNonNull(shieldRuntime, "shieldRuntime");
        this.impactResolver = Objects.requireNonNull(impactResolver, "impactResolver");
        this.damageRuntime = Objects.requireNonNull(damageRuntime, "damageRuntime");
    }

    /**
     * Optional fitted shield input for one impact.
     *
     * @param definition fitted shield definition
     * @param state current persistent shield state
     */
    public record ShieldInput(
            ShieldFieldRuntime.Definition definition,
            ShieldFieldRuntime.State state) {
        // Compact-constructor validation; record-level Javadoc owns the public parameter contract.
        public ShieldInput {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(state, "state");
        }
    }

    /**
     * Result of one composed protection interaction.
     *
     * @param shieldInteraction shield result or {@code null} when no field was fitted
     * @param residualProjectile physical residual body reaching/deflecting from armor, or null when shield stopped it
     * @param armorImpact armor response or null when shield stopped it
     * @param damageEvent local internal damage or null when no internal energy entered the hull
     */
    public record Result(
            ShieldFieldRuntime.Interaction shieldInteraction,
            ProjectileBody residualProjectile,
            ImpactResult armorImpact,
            DamageEvent damageEvent) {
        /** @return whether armor was physically reached */
        public boolean armorReached() {
            return armorImpact != null;
        }

        /** @return whether internal compartment/subsystem damage occurred */
        public boolean internalDamageOccurred() {
            return damageEvent != null;
        }
    }

    /**
     * Resolves one projectile against optional shield, ordered protection and local damage topology.
     *
     * @param projectile physical projectile body
     * @param shield optional shield input; null means no fitted/operational field
     * @param threatDirectionRad hull-local incoming direction for shield coverage
     * @param shieldInteractionSeconds interaction duration used by shield power limit
     * @param protectionStackId ordered armor/protection stack
     * @param incidenceAngleRad impact angle from armor normal
     * @param hull target hull
     * @param fit target installed fit
     * @param layout target explicit damage layout
     * @param damageState current local damage snapshot
     * @param hitPointM target hull-local hit point
     * @return composed deterministic result
     */
    public Result resolve(
            ProjectileBody projectile,
            ShieldInput shield,
            double threatDirectionRad,
            double shieldInteractionSeconds,
            String protectionStackId,
            double incidenceAngleRad,
            HullDefinition hull,
            InstalledFit fit,
            HullDamageLayout layout,
            Snapshot damageState,
            Vector3d hitPointM) {
        ProjectileBody checkedProjectile = Objects.requireNonNull(projectile, "projectile");
        Objects.requireNonNull(hull, "hull");
        Objects.requireNonNull(fit, "fit");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(damageState, "damageState");
        Objects.requireNonNull(hitPointM, "hitPointM");

        ShieldFieldRuntime.Interaction shieldInteraction = null;
        double residualEnergyJ = checkedProjectile.kineticEnergyJ();
        if (shield != null) {
            shieldInteraction = shieldRuntime.interact(
                    shield.definition(), shield.state(), residualEnergyJ,
                    threatDirectionRad, shieldInteractionSeconds);
            residualEnergyJ = shieldInteraction.residualEnergyJ();
            if (residualEnergyJ <= 0d) {
                return new Result(shieldInteraction, null, null, null);
            }
        }

        ProjectileBody residual = withKineticEnergy(checkedProjectile, residualEnergyJ);
        ImpactResult impact = impactResolver.resolve(residual, protectionStackId, incidenceAngleRad);
        DamageEvent damage = impact.internalDamageEnergyJ() > 0d
                ? damageRuntime.applyImpact(hull, fit, layout, damageState, impact, hitPointM)
                : null;
        return new Result(shieldInteraction, residual, impact, damage);
    }

    private static ProjectileBody withKineticEnergy(ProjectileBody body, double energyJ) {
        if (!Double.isFinite(energyJ) || energyJ <= 0d) {
            throw new IllegalArgumentException("energyJ must be finite and positive");
        }
        double oldSpeed = body.speedMps();
        if (oldSpeed <= 0d) {
            throw new IllegalArgumentException("projectile must have positive speed");
        }
        double newSpeed = Math.sqrt(2d * energyJ / body.massKg());
        double scale = newSpeed / oldSpeed;
        return new ProjectileBody(
                body.projectileId(),
                body.sourceEntityId(),
                body.spawnTick(),
                body.materialId(),
                body.shape(),
                body.lengthM(),
                body.diameterM(),
                body.massKg(),
                body.xM(),
                body.yM(),
                body.velocityXMps() * scale,
                body.velocityYMps() * scale);
    }
}
