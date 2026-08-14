package com.spacesim.combat;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.CombatRuntimeComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;

import java.util.Objects;

/**
 * Shared authoritative boundary for one deterministic weapon-fire attempt.
 *
 * <p>The controller is intentionally agnostic to player/AI ownership. Both callers submit ordinary
 * ECS entities and receive the same validation, range, cooldown, shield and hull rules. Damage is
 * applied instantaneously for the Stage-13 vertical slice; projectile simulation is not implied.</p>
 */
public final class CombatController {
    private CombatController() {
        throw new AssertionError("CombatController does not create instances");
    }

    /**
     * Resolves or creates transient weapon runtime state from the persistent ship archetype.
     *
     * @param entity combat entity created from a ship archetype
     * @param catalog authoritative content catalog
     * @return configured runtime component attached to the entity
     * @throws IllegalArgumentException if the entity has no valid combat weapon archetype
     */
    public static CombatRuntimeComponent ensureRuntime(Entity entity, ContentCatalog catalog) {
        Entity checkedEntity = Objects.requireNonNull(entity, "Combat entity not set");
        ContentCatalog checkedCatalog = Objects.requireNonNull(catalog, "ContentCatalog not set");
        CombatRuntimeComponent existing = checkedEntity.getComponent(CombatRuntimeComponent.class);
        if (existing != null) {
            if (checkedCatalog.findWeapon(existing.weaponId) == null) {
                throw new IllegalStateException("Combat runtime references unknown weapon: " + existing.weaponId);
            }
            return existing;
        }
        ArchetypeComponent archetype = checkedEntity.getComponent(ArchetypeComponent.class);
        if (archetype == null) {
            throw new IllegalArgumentException("Combat entity has no ArchetypeComponent");
        }
        ContentCatalog.ShipArchetypeDefinition ship = checkedCatalog.findShipArchetype(archetype.contentId);
        if (ship == null || !ship.role().isCombat() || ship.weaponId() == null
                || checkedCatalog.findWeapon(ship.weaponId()) == null) {
            throw new IllegalArgumentException("Combat entity has no valid data-driven weapon: " + archetype.contentId);
        }
        CombatRuntimeComponent runtime = new CombatRuntimeComponent(ship.weaponId());
        checkedEntity.add(runtime);
        return runtime;
    }

    /**
     * Attempts one shot through the common Stage-13 damage path.
     *
     * @param attacker firing entity
     * @param target target entity
     * @param catalog authoritative content catalog
     * @return immutable outcome including shield/hull damage and destruction flag
     */
    public static FireResult tryFire(Entity attacker, Entity target, ContentCatalog catalog) {
        if (attacker == null || target == null || attacker == target) {
            return FireResult.rejected(FireStatus.INVALID_TARGET);
        }
        CombatComponent attackerCombat = attacker.getComponent(CombatComponent.class);
        CombatComponent targetCombat = target.getComponent(CombatComponent.class);
        TransformComponent attackerTransform = attacker.getComponent(TransformComponent.class);
        TransformComponent targetTransform = target.getComponent(TransformComponent.class);
        if (attackerCombat == null || targetCombat == null
                || attackerTransform == null || targetTransform == null
                || attackerCombat.hull <= 0f || targetCombat.hull <= 0f) {
            return FireResult.rejected(FireStatus.INVALID_TARGET);
        }

        CombatRuntimeComponent runtime;
        try {
            runtime = ensureRuntime(attacker, catalog);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return FireResult.rejected(FireStatus.NO_WEAPON);
        }
        ContentCatalog.WeaponDefinition weapon = catalog.findWeapon(runtime.weaponId);
        if (weapon == null) {
            return FireResult.rejected(FireStatus.NO_WEAPON);
        }
        if (!runtime.ready()) {
            return FireResult.rejected(FireStatus.COOLDOWN);
        }
        if (!Float.isFinite(attackerTransform.position.x) || !Float.isFinite(attackerTransform.position.y)
                || !Float.isFinite(targetTransform.position.x) || !Float.isFinite(targetTransform.position.y)) {
            return FireResult.rejected(FireStatus.INVALID_TARGET);
        }
        float rangeSquared = weapon.range() * weapon.range();
        if (attackerTransform.position.dst2(targetTransform.position) > rangeSquared) {
            return FireResult.rejected(FireStatus.OUT_OF_RANGE);
        }

        float remainingDamage = weapon.damagePerShot();
        float shieldDamage = Math.min(Math.max(0f, targetCombat.shields), remainingDamage);
        targetCombat.shields = Math.max(0f, targetCombat.shields - shieldDamage);
        remainingDamage -= shieldDamage;
        float hullDamage = Math.min(Math.max(0f, targetCombat.hull), remainingDamage);
        targetCombat.hull = Math.max(0f, targetCombat.hull - hullDamage);
        runtime.cooldownRemaining = weapon.cooldownSeconds();
        return new FireResult(
                FireStatus.FIRED,
                shieldDamage,
                hullDamage,
                targetCombat.hull <= 0f);
    }

    /** Result classification for a fire attempt. */
    public enum FireStatus {
        /** Shot was accepted and damage was applied. */
        FIRED,
        /** Target reference/state is invalid or already destroyed. */
        INVALID_TARGET,
        /** Attacker has no resolvable data-driven weapon. */
        NO_WEAPON,
        /** Weapon has not completed its cooldown. */
        COOLDOWN,
        /** Target is outside physical weapon range. */
        OUT_OF_RANGE
    }

    /**
     * Immutable damage diagnostics for one fire attempt.
     *
     * @param status accepted/rejected status
     * @param shieldDamage damage absorbed by shields
     * @param hullDamage damage applied to hull
     * @param targetDestroyed whether hull reached zero from this shot
     */
    public record FireResult(
            FireStatus status,
            float shieldDamage,
            float hullDamage,
            boolean targetDestroyed) {
        /**
         * Creates a zero-damage rejection result.
         *
         * @param status rejection status
         * @return zero-damage result
         */
        public static FireResult rejected(FireStatus status) {
            if (status == FireStatus.FIRED) {
                throw new IllegalArgumentException("FIRED is not a rejection status");
            }
            return new FireResult(Objects.requireNonNull(status, "FireStatus not set"), 0f, 0f, false);
        }

        /** @return true only when a shot was actually executed */
        public boolean fired() {
            return status == FireStatus.FIRED;
        }
    }
}
