package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
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
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Shared Stage-19I kinetic weapon-body execution over one multi-combatant control runtime.
 *
 * <p>This class composes the existing production weapon adapter, fire-control mathematics,
 * ammunition runtime, launcher-cycle runtime and {@link ProjectileBody}. It does not create a new
 * damage model, hit probability, weapon-range wall or abstract fleet-ammunition counter.</p>
 *
 * <p>A shot may be materialized only when the Stage-19 control runtime authorizes fire, the selected
 * target exists in that actor's visible {@link TrackState} domain, the fitted launcher is physically
 * ready, the central consumable state contains a round, and production fire control yields a valid
 * intercept solution. The actual target transform/velocity is never read to improve that solution.
 * Production TrackState currently has no velocity channel, so this slice uses the same explicit zero
 * target-motion estimate as the established live duel rather than leaking authoritative enemy
 * velocity into actor knowledge.</p>
 */
public final class LiveTacticalBattleWeaponRuntime {
    private final LiveTacticalBattleControlRuntime controlRuntime;
    private final ShipEngineeringCatalog engineeringCatalog;
    private final WeaponAmmunitionCatalog ammunitionCatalog;
    private final WeaponLauncherCatalog launcherCatalog;
    private final DerivedShipCalculator calculator;
    private final ShipWeaponEngineeringAdapter weaponAdapter;
    private final WeaponFireControl fireControl;
    private final AmmunitionRuntime ammunitionRuntime;
    private final WeaponMountRuntime weaponMountRuntime;
    private final List<ProjectileBody> projectiles = new ArrayList<>();
    private final TreeMap<Long, Long> shotsBySourceEntityId = new TreeMap<>();

    private long nextProjectileId = 190_000L;

    /**
     * Creates shared weapon execution over one already materialized/control-driven battle.
     *
     * @param controlRuntime production multi-combatant sensing/AI/engineering/flight runtime
     */
    public LiveTacticalBattleWeaponRuntime(LiveTacticalBattleControlRuntime controlRuntime) {
        this.controlRuntime = Objects.requireNonNull(controlRuntime, "controlRuntime");
        engineeringCatalog = Stage175ICombatTestContentPack.loadDoctrines();
        ammunitionCatalog = Stage175ICombatTestWeaponPack.loadAmmunition();
        launcherCatalog = Stage175ICombatTestWeaponPack.loadLaunchers();
        calculator = new DerivedShipCalculator(engineeringCatalog);
        weaponAdapter = new ShipWeaponEngineeringAdapter();
        fireControl = new WeaponFireControl();
        ammunitionRuntime = new AmmunitionRuntime();
        weaponMountRuntime = new WeaponMountRuntime();
        for (CombatantRuntime combatant : battleState().combatants()) {
            shotsBySourceEntityId.put(combatant.spec().entityId(), 0L);
        }
    }

    /**
     * Advances one complete shared control/flight/kinetic-body tick.
     *
     * <p>Launcher cycles advance once, then the existing shared control runtime resolves sensing,
     * actor-local decisions, engineering and movement. Authorized shots are materialized from the
     * resulting physical shooter state and existing actor-local tracks. Finally every physical body
     * advances ballistically through the same fixed interval.</p>
     */
    public void advanceOneTick() {
        advanceLauncherCycles();
        controlRuntime.advanceOneTick();
        fireAllAuthorizedActors();
        advanceProjectiles();
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
     * Returns the number of physically materialized shots from one combatant.
     *
     * @param sourceEntityId stable firing combatant identity
     * @return non-negative shot count
     */
    public long shotsFired(long sourceEntityId) {
        battleState().requireCombatant(sourceEntityId);
        return shotsBySourceEntityId.get(sourceEntityId);
    }

    /**
     * Returns an equality-friendly deterministic weapon/body projection.
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
        return new BattleWeaponFingerprint(
                tick(),
                controlRuntime.fingerprint(),
                sources,
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

    private void advanceProjectiles() {
        for (int index = 0; index < projectiles.size(); index++) {
            projectiles.set(index, projectiles.get(index).advance(LiveTacticalBattleControlRuntime.TICK_SECONDS));
        }
    }

    private DerivedShipState derive(CombatantRuntime combatant) {
        EngineeringComponent engineering = combatant.engineering();
        return calculator.derive(
                combatant.hull(),
                engineering.fit,
                engineering.runtimeState.consumables(),
                engineering.instanceState.damage().moduleDamage());
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

    /**
     * Per-source finite-ammunition and launcher-continuity projection.
     *
     * @param entityId stable firing combatant identity
     * @param shotsFired physically materialized shot count
     * @param ammunitionRounds current itemized physical ammunition rounds
     * @param cooldownSecondsByMount physical launcher cycle state
     */
    public record SourceWeaponFingerprint(
            long entityId,
            long shotsFired,
            long ammunitionRounds,
            java.util.Map<String, Double> cooldownSecondsByMount) {
        /**
         * Validates and freezes one source weapon projection.
         *
         * @param entityId stable firing combatant identity
         * @param shotsFired physically materialized shot count
         * @param ammunitionRounds current itemized physical ammunition rounds
         * @param cooldownSecondsByMount physical launcher cycle state
         */
        public SourceWeaponFingerprint {
            if (entityId <= 0L || shotsFired < 0L || ammunitionRounds < 0L) {
                throw new IllegalArgumentException("weapon fingerprint counters/identity must be valid");
            }
            cooldownSecondsByMount = java.util.Map.copyOf(
                    Objects.requireNonNull(cooldownSecondsByMount, "cooldownSecondsByMount"));
        }
    }

    /**
     * Whole-battle deterministic kinetic weapon/body fingerprint.
     *
     * @param tick authoritative shared tick
     * @param controlFingerprint production control/flight fingerprint
     * @param sources canonical stable-entity weapon projections
     * @param projectiles current independent physical projectile bodies
     */
    public record BattleWeaponFingerprint(
            long tick,
            LiveTacticalBattleControlRuntime.BattleControlFingerprint controlFingerprint,
            List<SourceWeaponFingerprint> sources,
            List<ProjectileBody> projectiles) {
        /**
         * Validates and freezes the whole-battle weapon projection.
         *
         * @param tick authoritative shared tick
         * @param controlFingerprint production control/flight fingerprint
         * @param sources canonical stable-entity weapon projections
         * @param projectiles current independent physical projectile bodies
         */
        public BattleWeaponFingerprint {
            if (tick < 0L) {
                throw new IllegalArgumentException("tick must be non-negative");
            }
            Objects.requireNonNull(controlFingerprint, "controlFingerprint");
            sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
            projectiles = List.copyOf(Objects.requireNonNull(projectiles, "projectiles"));
        }
    }
}
