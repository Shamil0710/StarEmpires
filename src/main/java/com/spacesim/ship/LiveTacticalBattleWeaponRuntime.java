package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.Vector3d;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.ship.Stage175ICombatTestProtectionPack;
import com.spacesim.content.weapon.Stage175ICombatTestWeaponPack;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponLauncherCatalog;
import com.spacesim.ship.LiveTacticalBattleControlRuntime.ActorControlState;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.CombatantRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.WeaponFireControl.KinematicState;
import com.spacesim.ship.WeaponFireControl.TargetMotionEstimate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Shared Stage-19I physical weapon/body/protection execution over one multi-combatant runtime.
 *
 * <p>The runtime owns the production kinetic body pool and the common shield → material → local
 * damage composition. External physical ordnance may enter through the package-private impact/body
 * seams, so guided weapons do not need a second protection model or residual projectile pool.</p>
 */
public final class LiveTacticalBattleWeaponRuntime {
    private static final double EPSILON = 1e-12d;

    private final LiveTacticalBattleControlRuntime controlRuntime;
    private final ShipEngineeringCatalog engineeringCatalog;
    private final ShipProtectionCatalog protectionCatalog;
    private final WeaponAmmunitionCatalog ammunitionCatalog;
    private final WeaponLauncherCatalog launcherCatalog;
    private final DerivedShipCalculator calculator;
    private final ShipWeaponEngineeringAdapter weaponAdapter;
    private final ShipShieldEngineeringAdapter shieldAdapter;
    private final WeaponFireControl fireControl;
    private final AmmunitionRuntime ammunitionRuntime;
    private final WeaponMountRuntime weaponMountRuntime;
    private final ShieldFieldRuntime shieldRuntime;
    private final KineticProtectionRuntime protectionRuntime;
    private final List<ProjectileBody> projectiles = new ArrayList<>();
    private final TreeMap<Long, Long> shotsBySourceEntityId = new TreeMap<>();
    private final TreeMap<Long, Long> impactsByTargetEntityId = new TreeMap<>();

    private long nextProjectileId = 190_000L;

    /**
     * Creates shared weapon/protection execution over one materialized/control-driven battle.
     *
     * @param controlRuntime production multi-combatant sensing/AI/engineering/flight runtime
     */
    public LiveTacticalBattleWeaponRuntime(LiveTacticalBattleControlRuntime controlRuntime) {
        this.controlRuntime = Objects.requireNonNull(controlRuntime, "controlRuntime");
        engineeringCatalog = battleState().engineeringCatalog();
        protectionCatalog = Stage175ICombatTestProtectionPack.load();
        ammunitionCatalog = Stage175ICombatTestWeaponPack.loadAmmunition();
        launcherCatalog = Stage175ICombatTestWeaponPack.loadLaunchers();
        calculator = new DerivedShipCalculator(engineeringCatalog);
        weaponAdapter = new ShipWeaponEngineeringAdapter();
        shieldAdapter = new ShipShieldEngineeringAdapter();
        fireControl = new WeaponFireControl();
        ammunitionRuntime = new AmmunitionRuntime();
        weaponMountRuntime = new WeaponMountRuntime();
        shieldRuntime = new ShieldFieldRuntime();
        protectionRuntime = new KineticProtectionRuntime(
                shieldRuntime,
                new HeavyImpactResolver(engineeringCatalog, protectionCatalog),
                new ShipDamageRuntime());
        for (CombatantRuntime combatant : battleState().combatants()) {
            shotsBySourceEntityId.put(combatant.spec().entityId(), 0L);
            impactsByTargetEntityId.put(combatant.spec().entityId(), 0L);
        }
    }

    /**
     * Advances one complete shared control/flight/kinetic-impact tick.
     *
     * <p>Existing projectile bodies use relative swept body/ship motion. Newly spawned projectiles use
     * post-movement ship geometry because their muzzle exit occurs after the control/flight phase.</p>
     */
    public void advanceOneTick() {
        TreeMap<Long, PositionSnapshot> shipStartPositions = snapshotShipPositions();
        advanceLauncherCycles();
        controlRuntime.advanceOneTick();
        fireAllAuthorizedActors();
        advanceProjectilesAndResolveImpacts(shipStartPositions);
    }

    /** @return authoritative shared simulation tick */
    public long tick() {
        return controlRuntime.tick();
    }

    /** @return authoritative shared simulation time in seconds */
    public double elapsedSeconds() {
        return controlRuntime.elapsedSeconds();
    }

    /** @return shared production battle state */
    public LiveTacticalBattleRuntimeState battleState() {
        return controlRuntime.battleState();
    }

    /** @return shared production control runtime */
    public LiveTacticalBattleControlRuntime controlRuntime() {
        return controlRuntime;
    }

    /** @return immutable current physical projectile-body set in deterministic creation order */
    public List<ProjectileBody> projectiles() {
        return List.copyOf(projectiles);
    }

    /**
     * Returns the number of physically materialized kinetic shots from one combatant.
     *
     * @param sourceEntityId stable firing combatant identity
     * @return non-negative kinetic shot count
     */
    public long shotsFired(long sourceEntityId) {
        battleState().requireCombatant(sourceEntityId);
        return shotsBySourceEntityId.get(sourceEntityId);
    }

    /**
     * Returns the number of physical-body protection interactions on one combatant.
     *
     * <p>The counter includes both native kinetic bodies and external guided/residual bodies routed
     * through the shared protection seam.</p>
     *
     * @param targetEntityId stable struck combatant identity
     * @return non-negative physical impact count
     */
    public long impactsOn(long targetEntityId) {
        battleState().requireCombatant(targetEntityId);
        return impactsByTargetEntityId.get(targetEntityId);
    }

    /** @return total physical-body protection interactions resolved in the battle */
    public long totalImpacts() {
        return impactsByTargetEntityId.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * Routes one already-detected external physical-body impact through the exact production
     * shield/material/local-damage path owned by this runtime.
     *
     * <p>This package seam performs no hit test and grants no damage. The caller supplies the physical
     * body at its swept intersection and the target position at that same instant. The impact is
     * counted exactly once here, alongside native kinetic impacts.</p>
     *
     * @param targetEntityId physically intersected combatant
     * @param impactBody physical body at the intersection point
     * @param targetXM target x position at the intersection instant
     * @param targetYM target y position at the intersection instant
     * @return ordinary production protection result
     */
    KineticProtectionRuntime.Result resolveExternalPhysicalImpact(
            long targetEntityId,
            ProjectileBody impactBody,
            double targetXM,
            double targetYM) {
        CombatantRuntime target = battleState().requireCombatant(targetEntityId);
        ProjectileBody checkedBody = Objects.requireNonNull(impactBody, "impactBody");
        PositionSnapshot targetPosition = new PositionSnapshot(targetXM, targetYM);
        KineticProtectionRuntime.Result result = resolveImpact(target, checkedBody, targetPosition);
        recordImpact(targetEntityId);
        return result;
    }

    /**
     * Transfers an external surviving physical residual into the single production projectile pool.
     *
     * @param body surviving physical projectile/debris body
     */
    void acceptExternalProjectile(ProjectileBody body) {
        projectiles.add(Objects.requireNonNull(body, "body"));
    }

    /**
     * Returns an equality-friendly deterministic weapon/body/protection projection.
     *
     * @return whole-battle weapon fingerprint
     */
    public BattleWeaponFingerprint fingerprint() {
        List<SourceWeaponFingerprint> sources = battleState().combatants().stream()
                .map(combatant -> new SourceWeaponFingerprint(
                        combatant.spec().entityId(),
                        shotsBySourceEntityId.get(combatant.spec().entityId()),
                        ammunitionRounds(combatant.engineering().runtimeState.consumables()),
                        combatant.engineering().instanceState.weaponMountRuntime().cooldownSecondsByMount()))
                .toList();
        List<TargetProtectionFingerprint> targets = battleState().combatants().stream()
                .map(combatant -> new TargetProtectionFingerprint(
                        combatant.spec().entityId(),
                        impactsByTargetEntityId.get(combatant.spec().entityId()),
                        meanIntegrity(combatant),
                        totalShieldReserveJ(combatant)))
                .toList();
        return new BattleWeaponFingerprint(
                tick(),
                controlRuntime.fingerprint(),
                sources,
                targets,
                List.copyOf(projectiles));
    }

    private void advanceLauncherCycles() {
        for (CombatantRuntime combatant : battleState().combatants()) {
            EngineeringComponent engineering = combatant.engineering();
            ShipInstanceRuntimeState instance = engineering.instanceState;
            WeaponMountRuntime.RuntimeState next = weaponMountRuntime.advance(
                    instance.weaponMountRuntime(),
                    LiveTacticalBattleControlRuntime.TICK_SECONDS);
            replaceWeaponRuntime(engineering, next);
        }
    }

    private void fireAllAuthorizedActors() {
        for (CombatantRuntime shooter : battleState().combatants()) {
            ActorControlState control = controlRuntime.controlState(shooter.spec().entityId());
            if (!control.fireAuthorized() || !control.intent().targetSelected()) {
                continue;
            }
            TrackState selectedTrack = selectedVisibleTrack(shooter, control.intent().targetId());
            if (selectedTrack == null) {
                throw new IllegalStateException("authorized tactical target disappeared from actor-visible domain");
            }
            fireKineticMounts(shooter, selectedTrack);
        }
    }

    private void fireKineticMounts(CombatantRuntime shooter, TrackState selectedTrack) {
        EngineeringComponent engineering = shooter.engineering();
        DerivedShipState derived = derive(shooter);
        List<ShipWeaponEngineeringAdapter.FittedKineticMount> mounts = weaponAdapter.deriveKineticMounts(
                derived,
                ammunitionCatalog,
                launcherCatalog,
                engineering.instanceState.weaponLoadout());
        KinematicState shooterMotion = new KinematicState(
                shooter.transform().position.x,
                shooter.transform().position.y,
                shooter.transform().velocity.x,
                shooter.transform().velocity.y);

        for (ShipWeaponEngineeringAdapter.FittedKineticMount mount : mounts) {
            ShipInstanceRuntimeState instance = engineering.instanceState;
            if (!weaponMountRuntime.ready(instance.weaponMountRuntime(), mount.mountId())) {
                continue;
            }
            var ammunitionPlan = ammunitionRuntime.planOne(
                    engineering.runtimeState.consumables(),
                    mount.mountId(),
                    mount.launcher(),
                    mount.round().massKg());
            if (!ammunitionPlan.allowed()) {
                continue;
            }
            var solution = fireControl.planKinetic(
                    mount.round(),
                    selectedTrack,
                    shooterMotion,
                    new TargetMotionEstimate(0d, 0d, 0d, 0d),
                    mount.pointingJitterRad(),
                    elapsedSeconds());
            if (!solution.allowed()) {
                continue;
            }

            ProjectileBody projectile = fireControl.materializeKineticProjectile(
                    nextProjectileId,
                    shooter.spec().entityId(),
                    tick(),
                    mount.round(),
                    shooterMotion,
                    solution);
            var consumption = ammunitionRuntime.consumeOne(
                    engineering.runtimeState.consumables(),
                    mount.mountId(),
                    mount.launcher(),
                    mount.round().massKg());
            WeaponMountRuntime.RuntimeState nextWeaponState = weaponMountRuntime.commitShot(
                    instance.weaponMountRuntime(),
                    mount.mountId(),
                    mount.launcher());

            replaceConsumables(engineering, consumption.consumables());
            replaceWeaponRuntime(engineering, nextWeaponState);
            projectiles.add(projectile);
            nextProjectileId = Math.addExact(nextProjectileId, 1L);
            shotsBySourceEntityId.compute(
                    shooter.spec().entityId(),
                    (ignored, count) -> Math.addExact(Objects.requireNonNull(count, "shot count"), 1L));
        }
    }

    private TrackState selectedVisibleTrack(CombatantRuntime shooter, long targetId) {
        return battleState().visibleContacts(shooter.spec().entityId()).stream()
                .map(ObservedThreatAssessmentService.ObservedContact::track)
                .filter(track -> track.targetId() == targetId)
                .findFirst()
                .orElse(null);
    }

    private void advanceProjectilesAndResolveImpacts(TreeMap<Long, PositionSnapshot> shipStartPositions) {
        List<ProjectileBody> survivors = new ArrayList<>(projectiles.size());
        for (ProjectileBody body : projectiles) {
            ProjectileBody next = body.advance(LiveTacticalBattleControlRuntime.TICK_SECONDS);
            ImpactCandidate impact = firstImpact(body, next, shipStartPositions);
            if (impact == null) {
                survivors.add(next);
                continue;
            }

            ProjectileBody impactBody = atFraction(body, next, impact.fraction);
            KineticProtectionRuntime.Result result = resolveImpact(
                    impact.target,
                    impactBody,
                    impact.targetPositionAtImpact);
            recordImpact(impact.target.spec().entityId());

            if (result.postProtectionProjectile() != null) {
                double remainingSeconds = LiveTacticalBattleControlRuntime.TICK_SECONDS * (1d - impact.fraction);
                ProjectileBody residual = result.postProtectionProjectile();
                survivors.add(remainingSeconds > EPSILON ? residual.advance(remainingSeconds) : residual);
            }
        }
        projectiles.clear();
        projectiles.addAll(survivors);
    }

    private ImpactCandidate firstImpact(
            ProjectileBody body,
            ProjectileBody next,
            TreeMap<Long, PositionSnapshot> shipStartPositions) {
        ImpactCandidate best = null;
        boolean newlySpawned = body.spawnTick() == tick();
        for (CombatantRuntime target : battleState().combatants()) {
            if (target.spec().entityId() == body.sourceEntityId()) {
                continue;
            }
            PositionSnapshot end = new PositionSnapshot(
                    target.transform().position.x,
                    target.transform().position.y);
            PositionSnapshot start = newlySpawned
                    ? end
                    : Objects.requireNonNull(
                            shipStartPositions.get(target.spec().entityId()),
                            "target start position");
            double halfLength = target.hull().boundingDimensionsM().lengthM() * 0.5d;
            double halfWidth = target.hull().boundingDimensionsM().widthM() * 0.5d;
            var fraction = TacticalCollisionGeometry.firstSegmentAabbHitFraction(
                    body.xM() - start.xM,
                    body.yM() - start.yM,
                    next.xM() - end.xM,
                    next.yM() - end.yM,
                    halfLength,
                    halfWidth);
            if (fraction.isEmpty()) {
                continue;
            }
            double value = fraction.getAsDouble();
            if (best == null
                    || value < best.fraction - EPSILON
                    || (Math.abs(value - best.fraction) <= EPSILON
                    && target.spec().entityId() < best.target.spec().entityId())) {
                best = new ImpactCandidate(target, value, interpolate(start, end, value));
            }
        }
        return best;
    }

    private KineticProtectionRuntime.Result resolveImpact(
            CombatantRuntime target,
            ProjectileBody impactBody,
            PositionSnapshot targetPositionAtImpact) {
        EngineeringComponent engineering = target.engineering();
        ShipInstanceRuntimeState beforeInstance = engineering.instanceState;
        DerivedShipState beforeDerived = derive(target);
        List<ShipShieldEngineeringAdapter.FittedShield> beforeFittedShields = shieldAdapter.derive(beforeDerived);
        double threatDirectionRad = Math.atan2(-impactBody.velocityYMps(), -impactBody.velocityXMps());
        ShieldSelection shieldSelection = selectShield(
                beforeFittedShields,
                beforeInstance,
                impactBody,
                threatDirectionRad);
        KineticProtectionRuntime.ShieldInput shieldInput = shieldSelection == null
                ? null
                : new KineticProtectionRuntime.ShieldInput(
                        shieldSelection.fitted.definition(),
                        shieldSelection.state);
        Vector3d localHitPoint = new Vector3d(
                impactBody.xM() - targetPositionAtImpact.xM,
                impactBody.yM() - targetPositionAtImpact.yM,
                0d);
        KineticProtectionRuntime.Result result = protectionRuntime.resolve(
                impactBody,
                shieldInput,
                threatDirectionRad,
                LiveTacticalBattleControlRuntime.TICK_SECONDS,
                target.hull().structuralProtectionStackId(),
                0d,
                target.hull(),
                engineering.fit,
                target.damageLayout(),
                beforeInstance.damage(),
                localHitPoint);
        applyProtectionResult(
                target,
                beforeInstance,
                beforeFittedShields,
                shieldSelection,
                result);
        return result;
    }

    private ShieldSelection selectShield(
            List<ShipShieldEngineeringAdapter.FittedShield> fittedShields,
            ShipInstanceRuntimeState instance,
            ProjectileBody body,
            double threatDirectionRad) {
        for (ShipShieldEngineeringAdapter.FittedShield fitted : fittedShields) {
            ShieldFieldRuntime.State state = instance.shieldStatesByMount().get(fitted.mountId());
            if (state == null) {
                continue;
            }
            ShieldFieldRuntime.Interaction probe = shieldRuntime.interact(
                    fitted.definition(),
                    state,
                    body.kineticEnergyJ(),
                    threatDirectionRad,
                    LiveTacticalBattleControlRuntime.TICK_SECONDS);
            if (probe.covered()) {
                return new ShieldSelection(fitted, state);
            }
        }
        return null;
    }

    private void applyProtectionResult(
            CombatantRuntime target,
            ShipInstanceRuntimeState beforeInstance,
            List<ShipShieldEngineeringAdapter.FittedShield> beforeFittedShields,
            ShieldSelection shieldSelection,
            KineticProtectionRuntime.Result result) {
        TreeMap<String, ShieldFieldRuntime.State> shields =
                new TreeMap<>(beforeInstance.shieldStatesByMount());
        if (shieldSelection != null && result.shieldInteraction() != null) {
            shields.put(shieldSelection.fitted.mountId(), result.shieldInteraction().state());
        }
        ShipDamageRuntime.Snapshot damage = result.damageEvent() == null
                ? beforeInstance.damage()
                : result.damageEvent().snapshot();
        if (result.damageEvent() != null) {
            DerivedShipState afterDerived = deriveWithDamage(target, damage);
            TreeMap<String, ShipShieldEngineeringAdapter.FittedShield> afterByMount = new TreeMap<>();
            for (ShipShieldEngineeringAdapter.FittedShield fitted : shieldAdapter.derive(afterDerived)) {
                afterByMount.put(fitted.mountId(), fitted);
            }
            for (ShipShieldEngineeringAdapter.FittedShield before : beforeFittedShields) {
                ShieldFieldRuntime.State state = shields.get(before.mountId());
                if (state == null) {
                    continue;
                }
                ShipShieldEngineeringAdapter.FittedShield after = afterByMount.get(before.mountId());
                double integrity = after == null ? 0d : after.emitterIntegrity();
                shields.put(
                        before.mountId(),
                        shieldRuntime.withEmitterIntegrity(before.definition(), state, integrity));
            }
        }
        target.engineering().setInstanceState(new ShipInstanceRuntimeState(
                damage,
                shields,
                beforeInstance.maintenance(),
                beforeInstance.weaponLoadout(),
                beforeInstance.weaponMountRuntime()));
    }

    private void recordImpact(long targetEntityId) {
        impactsByTargetEntityId.compute(
                targetEntityId,
                (ignored, count) -> Math.addExact(Objects.requireNonNull(count, "impact count"), 1L));
    }

    private TreeMap<Long, PositionSnapshot> snapshotShipPositions() {
        TreeMap<Long, PositionSnapshot> result = new TreeMap<>();
        for (CombatantRuntime combatant : battleState().combatants()) {
            result.put(
                    combatant.spec().entityId(),
                    new PositionSnapshot(
                            combatant.transform().position.x,
                            combatant.transform().position.y));
        }
        return result;
    }

    private DerivedShipState derive(CombatantRuntime combatant) {
        return deriveWithDamage(combatant, combatant.engineering().instanceState.damage());
    }

    private DerivedShipState deriveWithDamage(
            CombatantRuntime combatant,
            ShipDamageRuntime.Snapshot damage) {
        EngineeringComponent engineering = combatant.engineering();
        return calculator.derive(
                combatant.hull(),
                engineering.fit,
                engineering.runtimeState.consumables(),
                Objects.requireNonNull(damage, "damage").moduleDamage());
    }

    private static ProjectileBody atFraction(
            ProjectileBody start,
            ProjectileBody end,
            double fraction) {
        double x = start.xM() + (end.xM() - start.xM()) * fraction;
        double y = start.yM() + (end.yM() - start.yM()) * fraction;
        return new ProjectileBody(
                start.projectileId(),
                start.sourceEntityId(),
                start.spawnTick(),
                start.materialId(),
                start.shape(),
                start.lengthM(),
                start.diameterM(),
                start.massKg(),
                x,
                y,
                start.velocityXMps(),
                start.velocityYMps());
    }

    private static PositionSnapshot interpolate(
            PositionSnapshot start,
            PositionSnapshot end,
            double fraction) {
        return new PositionSnapshot(
                start.xM + (end.xM - start.xM) * fraction,
                start.yM + (end.yM - start.yM) * fraction);
    }

    private static void replaceConsumables(
            EngineeringComponent engineering,
            ShipEngineeringState.ConsumableState consumables) {
        RuntimeState state = engineering.runtimeState;
        engineering.setRuntimeState(new RuntimeState(
                Objects.requireNonNull(consumables, "consumables"),
                state.sharedBusEnergyJ(),
                state.shipHeatStoredJ(),
                state.localHeatJByMount(),
                state.thrustLimitNByMount(),
                state.coolantBusCapacityW(),
                state.ftlCooldownSecondsByMount()));
    }

    private static void replaceWeaponRuntime(
            EngineeringComponent engineering,
            WeaponMountRuntime.RuntimeState weaponState) {
        ShipInstanceRuntimeState instance = engineering.instanceState;
        engineering.setInstanceState(new ShipInstanceRuntimeState(
                instance.damage(),
                instance.shieldStatesByMount(),
                instance.maintenance(),
                instance.weaponLoadout(),
                Objects.requireNonNull(weaponState, "weaponState")));
    }

    private static long ammunitionRounds(ShipEngineeringState.ConsumableState state) {
        return state.interfaceLoads().stream()
                .filter(value -> value.kind() == ShipEngineeringCatalog.InterfaceKind.AMMUNITION)
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }

    private static double meanIntegrity(CombatantRuntime combatant) {
        ShipDamageRuntime.Snapshot damage = combatant.engineering().instanceState.damage();
        return combatant.hull().compartments().stream()
                .mapToDouble(compartment -> damage.compartmentIntegrityById()
                        .getOrDefault(compartment.id(), 1d))
                .average()
                .orElse(1d);
    }

    private static double totalShieldReserveJ(CombatantRuntime combatant) {
        return combatant.engineering().instanceState.shieldStatesByMount().values().stream()
                .mapToDouble(ShieldFieldRuntime.State::reserveJ)
                .sum();
    }

    private static final class ShieldSelection {
        private final ShipShieldEngineeringAdapter.FittedShield fitted;
        private final ShieldFieldRuntime.State state;

        private ShieldSelection(
                ShipShieldEngineeringAdapter.FittedShield fitted,
                ShieldFieldRuntime.State state) {
            this.fitted = Objects.requireNonNull(fitted, "fitted");
            this.state = Objects.requireNonNull(state, "state");
        }
    }

    private static final class ImpactCandidate {
        private final CombatantRuntime target;
        private final double fraction;
        private final PositionSnapshot targetPositionAtImpact;

        private ImpactCandidate(
                CombatantRuntime target,
                double fraction,
                PositionSnapshot targetPositionAtImpact) {
            this.target = Objects.requireNonNull(target, "target");
            this.fraction = fraction;
            this.targetPositionAtImpact = Objects.requireNonNull(
                    targetPositionAtImpact,
                    "targetPositionAtImpact");
        }
    }

    private static final class PositionSnapshot {
        private final double xM;
        private final double yM;

        private PositionSnapshot(double xM, double yM) {
            if (!Double.isFinite(xM) || !Double.isFinite(yM)) {
                throw new IllegalArgumentException("position snapshot must be finite");
            }
            this.xM = xM;
            this.yM = yM;
        }
    }

    /**
     * Per-source finite-ammunition and launcher-continuity projection.
     *
     * @param entityId stable combatant identity
     * @param shotsFired physically materialized kinetic shot count
     * @param ammunitionRounds current itemized physical ammunition rounds
     * @param cooldownSecondsByMount physical launcher cycle state
     */
    public record SourceWeaponFingerprint(
            long entityId,
            long shotsFired,
            long ammunitionRounds,
            Map<String, Double> cooldownSecondsByMount) {
        /**
         * Validates and freezes one source weapon projection.
         *
         * @param entityId stable combatant identity
         * @param shotsFired physically materialized kinetic shot count
         * @param ammunitionRounds current itemized physical ammunition rounds
         * @param cooldownSecondsByMount physical launcher cycle state
         */
        public SourceWeaponFingerprint {
            if (entityId <= 0L || shotsFired < 0L || ammunitionRounds < 0L) {
                throw new IllegalArgumentException("weapon fingerprint counters/identity must be valid");
            }
            cooldownSecondsByMount = Collections.unmodifiableMap(new TreeMap<>(
                    Objects.requireNonNull(cooldownSecondsByMount, "cooldownSecondsByMount")));
        }
    }

    /**
     * Per-target physical protection projection.
     *
     * @param entityId stable target combatant identity
     * @param impactsResolved physical body interactions resolved on this target
     * @param meanCompartmentIntegrity current mean production compartment integrity
     * @param totalShieldReserveJ current summed persistent fitted shield reserve
     */
    public record TargetProtectionFingerprint(
            long entityId,
            long impactsResolved,
            double meanCompartmentIntegrity,
            double totalShieldReserveJ) {
        /**
         * Validates one target protection projection.
         *
         * @param entityId stable target combatant identity
         * @param impactsResolved physical body interactions resolved on this target
         * @param meanCompartmentIntegrity current mean production compartment integrity
         * @param totalShieldReserveJ current summed persistent fitted shield reserve
         */
        public TargetProtectionFingerprint {
            if (entityId <= 0L || impactsResolved < 0L
                    || !Double.isFinite(meanCompartmentIntegrity)
                    || meanCompartmentIntegrity < 0d || meanCompartmentIntegrity > 1d
                    || !Double.isFinite(totalShieldReserveJ) || totalShieldReserveJ < 0d) {
                throw new IllegalArgumentException("invalid target protection fingerprint");
            }
        }
    }

    /**
     * Whole-battle deterministic physical weapon/body/protection fingerprint.
     *
     * @param tick authoritative shared tick
     * @param controlFingerprint production control/flight fingerprint
     * @param sources canonical stable-entity weapon projections
     * @param targets canonical stable-entity protection projections
     * @param projectiles current independent physical projectile bodies
     */
    public record BattleWeaponFingerprint(
            long tick,
            LiveTacticalBattleControlRuntime.BattleControlFingerprint controlFingerprint,
            List<SourceWeaponFingerprint> sources,
            List<TargetProtectionFingerprint> targets,
            List<ProjectileBody> projectiles) {
        /**
         * Validates and freezes the whole-battle weapon projection.
         *
         * @param tick authoritative shared tick
         * @param controlFingerprint production control/flight fingerprint
         * @param sources canonical stable-entity weapon projections
         * @param targets canonical stable-entity protection projections
         * @param projectiles current independent physical projectile bodies
         */
        public BattleWeaponFingerprint {
            if (tick < 0L) {
                throw new IllegalArgumentException("tick must be non-negative");
            }
            Objects.requireNonNull(controlFingerprint, "controlFingerprint");
            sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
            targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
            projectiles = List.copyOf(Objects.requireNonNull(projectiles, "projectiles"));
        }
    }
}
