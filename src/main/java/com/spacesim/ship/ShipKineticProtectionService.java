package com.spacesim.ship;

import com.spacesim.content.ship.ArmorModuleProtectionCatalog;
import com.spacesim.content.ship.ArmorModuleProtectionCatalog.ArmorProfile;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.content.ship.ShipEngineeringCatalog.Vector3d;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.content.ship.ShipProtectionCatalog.HullDamageLayout;
import com.spacesim.ship.HeavyImpactResolver.ImpactResult;
import com.spacesim.ship.HeavyImpactResolver.Outcome;
import com.spacesim.ship.KineticProtectionRuntime.ShieldInput;
import com.spacesim.ship.ShipDamageRuntime.DamageEvent;
import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Fitted-ship kinetic protection composition including physical armor modules.
 *
 * <p>Installed {@link ModuleFamily#ARMOR_PROTECTION} modules contribute additional external material
 * stacks through {@link ArmorModuleProtectionCatalog}. Their mass/volume was already paid by the
 * ordinary fit calculator; this service only resolves the represented material geometry before the
 * hull's structural stack. Spall from perforated external layers becomes internal only if every
 * remaining stack is also perforated. No armor percentage or class bonus is introduced.</p>
 */
public final class ShipKineticProtectionService {
    private final ShipEngineeringCatalog engineering;
    private final ArmorModuleProtectionCatalog armorCatalog;
    private final ShieldFieldRuntime shields = new ShieldFieldRuntime();
    private final HeavyImpactResolver impacts;
    private final ShipDamageRuntime damage = new ShipDamageRuntime();

    /**
     * Creates one fitted protection service.
     *
     * @param engineering ordinary engineering catalog
     * @param protection heavy-impact/local-damage response catalog
     * @param armorCatalog fitted armor-module response mapping
     */
    public ShipKineticProtectionService(
            ShipEngineeringCatalog engineering,
            ShipProtectionCatalog protection,
            ArmorModuleProtectionCatalog armorCatalog) {
        this.engineering = Objects.requireNonNull(engineering, "engineering");
        this.armorCatalog = Objects.requireNonNull(armorCatalog, "armorCatalog");
        this.impacts = new HeavyImpactResolver(engineering, Objects.requireNonNull(protection, "protection"));
    }

    /**
     * Resolves one projectile against shield, fitted external armor, hull stack and local damage.
     *
     * @param projectile physical projectile
     * @param shield optional current fitted shield
     * @param threatDirectionRad hull-local incoming direction
     * @param shieldInteractionSeconds shield interaction duration
     * @param incidenceAngleRad signed material incidence angle
     * @param hull target hull
     * @param fit current installed fit
     * @param layout target damage layout
     * @param snapshot current damage snapshot
     * @param hitPointM hull-local impact point
     * @return deterministic fitted protection result
     */
    public Result resolve(
            ProjectileBody projectile,
            ShieldInput shield,
            double threatDirectionRad,
            double shieldInteractionSeconds,
            double incidenceAngleRad,
            HullDefinition hull,
            InstalledFit fit,
            HullDamageLayout layout,
            Snapshot snapshot,
            Vector3d hitPointM) {
        ProjectileBody checkedProjectile = Objects.requireNonNull(projectile, "projectile");
        HullDefinition checkedHull = Objects.requireNonNull(hull, "hull");
        InstalledFit checkedFit = Objects.requireNonNull(fit, "fit");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(hitPointM, "hitPointM");
        if (!checkedHull.id().equals(checkedFit.hullId())) {
            throw new IllegalArgumentException("hull and fit IDs differ");
        }

        ShieldFieldRuntime.Interaction shieldInteraction = null;
        double residualEnergyJ = checkedProjectile.kineticEnergyJ();
        if (shield != null) {
            shieldInteraction = shields.interact(
                    shield.definition(), shield.state(), residualEnergyJ,
                    threatDirectionRad, shieldInteractionSeconds);
            residualEnergyJ = shieldInteraction.residualEnergyJ();
            if (residualEnergyJ <= 0d) {
                return new Result(shieldInteraction, null, List.of(), List.of(), null, null);
            }
        }

        ProjectileBody entry = withKineticEnergy(checkedProjectile, residualEnergyJ);
        ProjectileBody current = entry;
        List<String> stackIds = protectionStackIds(checkedHull, checkedFit);
        List<ImpactResult> stackImpacts = new ArrayList<>();
        double accumulatedSpallEnergyJ = 0d;
        boolean penetratedAll = true;
        for (String stackId : stackIds) {
            ImpactResult impact = impacts.resolve(current, stackId, incidenceAngleRad);
            stackImpacts.add(impact);
            accumulatedSpallEnergyJ += impact.fragments().kineticEnergyJ();
            if (impact.outcome() != Outcome.PERFORATED) {
                current = impact.residualProjectile();
                penetratedAll = false;
                break;
            }
            current = impact.residualProjectile();
        }

        DamageEvent damageEvent = null;
        if (penetratedAll && current != null) {
            double internalEnergyJ = current.kineticEnergyJ() + accumulatedSpallEnergyJ;
            damageEvent = damage.applyInternalEnergy(
                    checkedHull, checkedFit, layout, snapshot, internalEnergyJ, hitPointM);
        }
        return new Result(
                shieldInteraction,
                entry,
                stackIds,
                List.copyOf(stackImpacts),
                current,
                damageEvent);
    }

    /**
     * Resolves ordered protection stacks contributed by the current fit.
     *
     * @param hull target hull
     * @param fit current fit
     * @return outside-to-inside stack IDs, ending with the hull structural stack
     */
    public List<String> protectionStackIds(HullDefinition hull, InstalledFit fit) {
        HullDefinition checkedHull = Objects.requireNonNull(hull, "hull");
        InstalledFit checkedFit = Objects.requireNonNull(fit, "fit");
        List<InstalledModuleDefinition> installed = new ArrayList<>(checkedFit.installedModules());
        installed.sort(Comparator.comparing(InstalledModuleDefinition::mountId)
                .thenComparing(InstalledModuleDefinition::moduleId));
        List<String> result = new ArrayList<>();
        for (InstalledModuleDefinition assignment : installed) {
            ModuleDefinition module = engineering.findModule(assignment.moduleId());
            if (module == null) {
                throw new IllegalArgumentException("unknown fitted module: " + assignment.moduleId());
            }
            if (module.family() != ModuleFamily.ARMOR_PROTECTION) {
                continue;
            }
            ArmorProfile profile = armorCatalog.findByModuleId(module.id());
            if (profile == null) {
                throw new IllegalArgumentException("fitted armor module lacks protection profile: " + module.id());
            }
            result.add(profile.externalProtectionStackId());
        }
        result.add(checkedHull.structuralProtectionStackId());
        return List.copyOf(result);
    }

    /**
     * Complete fitted protection result.
     *
     * @param shieldInteraction optional shield result
     * @param materialEntryProjectile projectile body reaching material protection
     * @param protectionStackIds ordered fitted external + hull stack IDs
     * @param stackImpacts ordered material responses
     * @param postProtectionProjectile residual or ricochet body, or null when stopped
     * @param damageEvent local internal damage only when all stacks were perforated
     */
    public record Result(
            ShieldFieldRuntime.Interaction shieldInteraction,
            ProjectileBody materialEntryProjectile,
            List<String> protectionStackIds,
            List<ImpactResult> stackImpacts,
            ProjectileBody postProtectionProjectile,
            DamageEvent damageEvent) {
        /**
         * Validates one fitted kinetic-protection result.
         *
         * @param shieldInteraction optional shield result
         * @param materialEntryProjectile projectile reaching material protection
         * @param protectionStackIds ordered protection stack IDs
         * @param stackImpacts ordered material responses
         * @param postProtectionProjectile residual body
         * @param damageEvent optional internal damage
         */
        public Result {
            protectionStackIds = List.copyOf(Objects.requireNonNull(protectionStackIds, "protectionStackIds"));
            stackImpacts = List.copyOf(Objects.requireNonNull(stackImpacts, "stackImpacts"));
        }

        /** @return whether any material protection was reached */
        public boolean materialReached() {
            return materialEntryProjectile != null;
        }

        /** @return whether local internal damage occurred */
        public boolean internalDamageOccurred() {
            return damageEvent != null;
        }
    }

    private static ProjectileBody withKineticEnergy(ProjectileBody body, double energyJ) {
        if (!Double.isFinite(energyJ) || energyJ <= 0d) {
            throw new IllegalArgumentException("energyJ must be finite and positive");
        }
        double oldSpeed = body.speedMps();
        if (oldSpeed <= 0d) {
            throw new IllegalArgumentException("projectile must have positive speed");
        }
        double targetSpeed = Math.sqrt(2d * energyJ / body.massKg());
        double scale = targetSpeed / oldSpeed;
        return new ProjectileBody(
                body.projectileId(), body.sourceEntityId(), body.spawnTick(), body.materialId(), body.shape(),
                body.lengthM(), body.diameterM(), body.massKg(), body.xM(), body.yM(),
                body.velocityXMps() * scale, body.velocityYMps() * scale);
    }
}
