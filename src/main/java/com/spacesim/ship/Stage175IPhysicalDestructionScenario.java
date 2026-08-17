package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.CompartmentDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.Vector3d;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.ship.Stage175ICombatTestProtectionPack;
import com.spacesim.content.weapon.Stage175ICombatTestWeaponPack;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;

import java.util.List;
import java.util.Objects;

/**
 * Reusable deterministic Stage-17.5I destruction scenario built only from production subsystem APIs.
 *
 * <p>The scenario consumes the real doctrine-A primary magazine, resolves every round through the
 * fitted doctrine-E shield, material stack and local damage topology, and continues until both each
 * compartment and all mounts physically located in it reach zero integrity. It exists only as the
 * Stage-17.5 aggregate acceptance seam; it does not introduce a combat score, hidden damage bonus or
 * alternate projectile model.</p>
 */
public final class Stage175IPhysicalDestructionScenario {
    private static final String PRIMARY_MOUNT = "weapon_primary";
    private static final String SHIELD_MOUNT = "utility_shield";
    private static final double SHIELD_INTERACTION_SECONDS = 1d;

    private Stage175IPhysicalDestructionScenario() {
        throw new AssertionError("utility class");
    }

    /** Complete physical state snapshots needed by headless and presentation acceptance clients. */
    public record Result(
            ShipEngineeringCatalog.HullDefinition hull,
            ShipDamageRuntime.Snapshot pristineDamage,
            ShipShieldEngineeringAdapter.FittedShield fittedShield,
            ShieldFieldRuntime.State chargedShield,
            ProjectileBody firstProjectile,
            KineticProtectionRuntime.Result firstPenetratingImpact,
            ShipDamageRuntime.Snapshot firstPenetrationDamage,
            ShieldFieldRuntime.State firstPenetrationShield,
            KineticProtectionRuntime.Result lastImpact,
            ShipDamageRuntime.Snapshot finalDamage,
            ShieldFieldRuntime.State finalShield,
            long initialPrimaryRounds,
            long remainingPrimaryRounds,
            long shotsConsumed,
            double pristineAccelerationMps2,
            double finalAccelerationMps2) {
        /**
         * Validates one complete physical destruction result.
         *
         * @param hull target hull used by the scenario
         * @param pristineDamage pristine target damage snapshot
         * @param fittedShield fitted target shield definition
         * @param chargedShield initial charged shield state
         * @param firstProjectile first physical projectile consumed by the scenario
         * @param firstPenetratingImpact first impact that produced local damage
         * @param firstPenetrationDamage damage snapshot after the first penetration
         * @param firstPenetrationShield shield state after the first penetration
         * @param lastImpact final resolved physical impact
         * @param finalDamage final fully destroyed local damage snapshot
         * @param finalShield final shield state after destruction
         * @param initialPrimaryRounds initial physical primary-magazine round count
         * @param remainingPrimaryRounds physical primary-magazine rounds remaining after destruction
         * @param shotsConsumed number of physical rounds consumed
         * @param pristineAccelerationMps2 production-derived target acceleration before damage
         * @param finalAccelerationMps2 production-derived target acceleration after final damage
         */
        public Result {
            Objects.requireNonNull(hull, "hull");
            Objects.requireNonNull(pristineDamage, "pristineDamage");
            Objects.requireNonNull(fittedShield, "fittedShield");
            Objects.requireNonNull(chargedShield, "chargedShield");
            Objects.requireNonNull(firstProjectile, "firstProjectile");
            Objects.requireNonNull(firstPenetratingImpact, "firstPenetratingImpact");
            Objects.requireNonNull(firstPenetrationDamage, "firstPenetrationDamage");
            Objects.requireNonNull(firstPenetrationShield, "firstPenetrationShield");
            Objects.requireNonNull(lastImpact, "lastImpact");
            Objects.requireNonNull(finalDamage, "finalDamage");
            Objects.requireNonNull(finalShield, "finalShield");
            if (initialPrimaryRounds < 0L || remainingPrimaryRounds < 0L || shotsConsumed <= 0L) {
                throw new IllegalArgumentException("physical magazine counters are invalid");
            }
            if (!Double.isFinite(pristineAccelerationMps2) || pristineAccelerationMps2 < 0d
                    || !Double.isFinite(finalAccelerationMps2) || finalAccelerationMps2 < 0d) {
                throw new IllegalArgumentException("acceleration values must be finite and non-negative");
            }
        }
    }

    /**
     * Executes the canonical finite-magazine destruction acceptance scenario.
     *
     * @return immutable physical snapshots and resource counters
     */
    public static Result run() {
        ShipEngineeringCatalog engineering = Stage175ICombatTestContentPack.loadDoctrines();
        ShipProtectionCatalog protection = Stage175ICombatTestProtectionPack.load();
        var ammunition = Stage175ICombatTestWeaponPack.loadAmmunition();
        var launchers = Stage175ICombatTestWeaponPack.loadLaunchers();
        var attackerDoctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.A_KINETIC_LINE);
        var targetDoctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.E_BALANCED_CONTROL);
        InstalledFit attackerFit = InstalledFit.fromDemonstrator(
                engineering.findDemonstratorFit(attackerDoctrine.fitId()));
        InstalledFit targetFit = InstalledFit.fromDemonstrator(
                engineering.findDemonstratorFit(targetDoctrine.fitId()));
        var hull = engineering.findHull(targetFit.hullId());
        var layout = protection.findHullDamageLayout(hull.id());
        var calculator = new DerivedShipCalculator(engineering);
        var pristineTarget = calculator.derive(
                hull, targetFit, targetDoctrine.initialConsumables(), DamageState.pristine());
        var attackerDerived = calculator.derive(
                engineering.findHull(attackerFit.hullId()),
                attackerFit,
                attackerDoctrine.initialConsumables(),
                DamageState.pristine());
        var primary = new ShipWeaponEngineeringAdapter().deriveKineticMounts(
                        attackerDerived, ammunition, launchers, attackerDoctrine.weaponLoadout()).stream()
                .filter(value -> value.mountId().equals(PRIMARY_MOUNT))
                .findFirst()
                .orElseThrow();

        ShieldFieldRuntime shieldRuntime = new ShieldFieldRuntime();
        var fittedShield = new ShipShieldEngineeringAdapter().derive(pristineTarget).stream()
                .filter(value -> value.mountId().equals(SHIELD_MOUNT))
                .findFirst()
                .orElseThrow();
        ShieldFieldRuntime.State chargedShield = fittedShield.chargedState(shieldRuntime);
        ShipDamageRuntime.Snapshot pristineDamage = ShipDamageRuntime.Snapshot.pristine(hull, layout);
        ShipDamageRuntime.Snapshot damage = pristineDamage;
        ShieldFieldRuntime.State shield = chargedShield;
        KineticProtectionRuntime protectionRuntime = new KineticProtectionRuntime(
                shieldRuntime,
                new HeavyImpactResolver(engineering, protection),
                new ShipDamageRuntime());
        AmmunitionRuntime ammunitionRuntime = new AmmunitionRuntime();
        ConsumableState stores = attackerDoctrine.initialConsumables();
        long initialRounds = roundsOnMount(stores, PRIMARY_MOUNT);
        long shots = 0L;
        ProjectileBody firstProjectile = null;
        KineticProtectionRuntime.Result firstPenetratingImpact = null;
        ShipDamageRuntime.Snapshot firstPenetrationDamage = null;
        ShieldFieldRuntime.State firstPenetrationShield = null;
        KineticProtectionRuntime.Result lastImpact = null;

        while (!fullyDestroyed(hull.compartments(), layout, damage)
                && roundsOnMount(stores, PRIMARY_MOUNT) > 0L) {
            CompartmentDefinition target = nextIncompleteCompartment(hull.compartments(), layout, damage);
            stores = ammunitionRuntime.consumeOne(
                    stores, primary.mountId(), primary.launcher(), primary.round().massKg()).consumables();
            shots++;
            ProjectileBody projectile = new ProjectileBody(
                    90_000L + shots,
                    90_001L,
                    shots,
                    primary.round().materialId(),
                    primary.round().shape(),
                    primary.round().lengthM(),
                    primary.round().diameterM(),
                    primary.round().massKg(),
                    0d,
                    0d,
                    primary.round().muzzleVelocityMps(),
                    0d);
            if (firstProjectile == null) {
                firstProjectile = projectile;
            }
            KineticProtectionRuntime.ShieldInput shieldInput = shield.emitterIntegrity() > 0d
                    ? new KineticProtectionRuntime.ShieldInput(fittedShield.definition(), shield)
                    : null;
            lastImpact = protectionRuntime.resolve(
                    projectile,
                    shieldInput,
                    Math.PI,
                    SHIELD_INTERACTION_SECONDS,
                    hull.structuralProtectionStackId(),
                    0d,
                    hull,
                    targetFit,
                    layout,
                    damage,
                    new Vector3d(target.centerM().xM(), target.centerM().yM(), target.centerM().zM()));
            if (lastImpact.shieldInteraction() != null) {
                shield = lastImpact.shieldInteraction().state();
            }
            if (lastImpact.damageEvent() != null) {
                damage = lastImpact.damageEvent().snapshot();
                double emitterIntegrity = damage.moduleDamage().moduleIntegrityByMount()
                        .getOrDefault(SHIELD_MOUNT, 1d);
                shield = shieldRuntime.withEmitterIntegrity(fittedShield.definition(), shield, emitterIntegrity);
                if (firstPenetratingImpact == null) {
                    firstPenetratingImpact = lastImpact;
                    firstPenetrationDamage = damage;
                    firstPenetrationShield = shield;
                }
            }
        }

        if (firstProjectile == null || firstPenetratingImpact == null || firstPenetrationDamage == null
                || firstPenetrationShield == null || lastImpact == null
                || !fullyDestroyed(hull.compartments(), layout, damage)) {
            throw new IllegalStateException("finite Stage 17.5I destruction scenario did not reach full local destruction");
        }
        var destroyed = calculator.derive(
                hull, targetFit, targetDoctrine.initialConsumables(), damage.moduleDamage());
        return new Result(
                hull,
                pristineDamage,
                fittedShield,
                chargedShield,
                firstProjectile,
                firstPenetratingImpact,
                firstPenetrationDamage,
                firstPenetrationShield,
                lastImpact,
                damage,
                shield,
                initialRounds,
                roundsOnMount(stores, PRIMARY_MOUNT),
                shots,
                pristineTarget.accelerationMps2(),
                destroyed.accelerationMps2());
    }

    private static CompartmentDefinition nextIncompleteCompartment(
            List<CompartmentDefinition> compartments,
            ShipProtectionCatalog.HullDamageLayout layout,
            ShipDamageRuntime.Snapshot damage) {
        return compartments.stream()
                .filter(value -> !compartmentDestroyed(value.id(), layout, damage))
                .findFirst()
                .orElseThrow();
    }

    private static boolean fullyDestroyed(
            List<CompartmentDefinition> compartments,
            ShipProtectionCatalog.HullDamageLayout layout,
            ShipDamageRuntime.Snapshot damage) {
        return compartments.stream().allMatch(value -> compartmentDestroyed(value.id(), layout, damage));
    }

    private static boolean compartmentDestroyed(
            String compartmentId,
            ShipProtectionCatalog.HullDamageLayout layout,
            ShipDamageRuntime.Snapshot damage) {
        if (damage.compartmentIntegrityById().getOrDefault(compartmentId, 1d) > 0d) {
            return false;
        }
        return layout.mounts().stream()
                .filter(value -> value.compartmentId().equals(compartmentId))
                .allMatch(value -> damage.moduleDamage().moduleIntegrityByMount()
                        .getOrDefault(value.mountId(), 1d) <= 0d);
    }

    private static long roundsOnMount(ConsumableState state, String mountId) {
        return state.interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .filter(value -> value.mountId().equals(mountId))
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }
}
