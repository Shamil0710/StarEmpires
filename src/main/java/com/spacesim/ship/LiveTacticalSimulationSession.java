package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.Vector3d;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.ship.Stage175ICombatTestProtectionPack;
import com.spacesim.content.weapon.Stage175ICombatTestWeaponPack;
import com.spacesim.ship.SensorDefinition.Mode;
import com.spacesim.ship.ShipEngineeringRuntime.OperatingCommand;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipSensorEngineeringAdapter.FittedSensor;
import com.spacesim.ship.ShipSensorRuntime.Position2d;
import com.spacesim.ship.ShipSensorRuntime.TrackQualityPolicy;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.Doctrine;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import com.spacesim.ship.WeaponFireControl.KinematicState;
import com.spacesim.ship.WeaponFireControl.TargetMotionEstimate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Fixed-step headless tactical simulation used by the post-17.5 live viewer.
 *
 * <p>The session advances authoritative Stage-17.5 runtime state only when
 * {@link #advanceOneTick()} is called. It uses production radar observation, track fusion,
 * kinetic fire control, finite ammunition, independent projectile motion, fitted shields,
 * bounded heavy-impact material response and local subsystem damage. It has no libGDX dependency
 * and therefore cannot obtain combat results from renderer state or frame timing.</p>
 */
public final class LiveTacticalSimulationSession {
    /** Fixed authoritative simulation interval in seconds. */
    public static final double TICK_SECONDS = 0.05d;
    /** Stable live-viewer attacker identity. */
    public static final long ATTACKER_ENTITY_ID = 175_101L;
    /** Stable live-viewer target identity. */
    public static final long TARGET_ENTITY_ID = 175_201L;

    private static final String PRIMARY_MOUNT = "weapon_primary";
    private static final String SHIELD_MOUNT = "utility_shield";
    private static final double ATTACKER_X_M = 260d;
    private static final double TARGET_X_M = 1_690d;
    private static final double CENTER_Y_M = 700d;
    private static final double SENSOR_INTERVAL_SECONDS = 0.20d;
    private static final long SENSOR_INTERVAL_TICKS = Math.round(SENSOR_INTERVAL_SECONDS / TICK_SECONDS);
    private static final int MAX_TRACK_MEASUREMENTS = 8;
    private static final int IMPACT_VISIBILITY_TICKS = 8;
    private static final double MAX_BODY_DISTANCE_M = 5_000d;
    private static final double EPSILON = 1e-9d;

    private final ShipEngineeringCatalog engineeringCatalog;
    private final ShipProtectionCatalog protectionCatalog;
    private final com.spacesim.content.weapon.WeaponAmmunitionCatalog ammunitionCatalog;
    private final com.spacesim.content.weapon.WeaponLauncherCatalog launcherCatalog;
    private final DerivedShipCalculator calculator;
    private final ShipEngineeringRuntime engineeringRuntime;
    private final ShipEngineeringGrantService grantService;
    private final ShipObservationEngineeringService observationService;
    private final ShipSensorRuntime sensorRuntime;
    private final WeaponFireControl fireControl;
    private final AmmunitionRuntime ammunitionRuntime;
    private final ShieldFieldRuntime shieldRuntime;
    private final KineticProtectionRuntime protectionRuntime;
    private final ShipSensorEngineeringAdapter sensorAdapter;
    private final ShipWeaponEngineeringAdapter weaponAdapter;
    private final Doctrine attackerDoctrine;
    private final Doctrine targetDoctrine;
    private final InstalledFit attackerFit;
    private final InstalledFit targetFit;
    private final HullDefinition attackerHull;
    private final HullDefinition targetHull;
    private final ShipProtectionCatalog.HullDamageLayout targetDamageLayout;
    private final EngineeringComponent attackerEngineering;
    private final FittedSensor attackerRadar;
    private final ShipShieldEngineeringAdapter.FittedShield targetFittedShield;
    private final List<SensorMeasurement> trackMeasurements = new ArrayList<>();
    private final List<ProjectileBody> projectiles = new ArrayList<>();

    private ShipDamageRuntime.Snapshot targetDamage;
    private ShieldFieldRuntime.State targetShield;
    private TrackState attackerTrack;
    private KineticProtectionRuntime.Result recentImpact;
    private long recentImpactTick = Long.MIN_VALUE;
    private long tick;
    private long nextProjectileId = 176_000L;
    private long shotsFired;
    private long impactsResolved;
    private double kineticCooldownSeconds;
    private double targetAccelerationMps2;

    /**
     * Creates a fresh deterministic balanced-control versus balanced-control live scenario.
     *
     * <p>Doctrine IDs select only production-valid test fits and stores; they grant no numeric combat
     * modifiers.</p>
     */
    public LiveTacticalSimulationSession() {
        engineeringCatalog = Stage175ICombatTestContentPack.loadDoctrines();
        protectionCatalog = Stage175ICombatTestProtectionPack.load();
        ammunitionCatalog = Stage175ICombatTestWeaponPack.loadAmmunition();
        launcherCatalog = Stage175ICombatTestWeaponPack.loadLaunchers();
        calculator = new DerivedShipCalculator(engineeringCatalog);
        engineeringRuntime = new ShipEngineeringRuntime(engineeringCatalog);
        grantService = new ShipEngineeringGrantService(engineeringCatalog);
        observationService = new ShipObservationEngineeringService(engineeringCatalog);
        sensorRuntime = new ShipSensorRuntime();
        fireControl = new WeaponFireControl();
        ammunitionRuntime = new AmmunitionRuntime();
        shieldRuntime = new ShieldFieldRuntime();
        sensorAdapter = new ShipSensorEngineeringAdapter();
        weaponAdapter = new ShipWeaponEngineeringAdapter();

        attackerDoctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.E_BALANCED_CONTROL);
        targetDoctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.E_BALANCED_CONTROL);
        attackerFit = InstalledFit.fromDemonstrator(
                engineeringCatalog.findDemonstratorFit(attackerDoctrine.fitId()));
        targetFit = InstalledFit.fromDemonstrator(
                engineeringCatalog.findDemonstratorFit(targetDoctrine.fitId()));
        attackerHull = engineeringCatalog.findHull(attackerFit.hullId());
        targetHull = engineeringCatalog.findHull(targetFit.hullId());
        targetDamageLayout = protectionCatalog.findHullDamageLayout(targetHull.id());

        RuntimeState attackerRuntime = engineeringRuntime.initialize(
                attackerFit, attackerDoctrine.initialConsumables(), DamageState.pristine());
        attackerEngineering = new EngineeringComponent(
                attackerFit, attackerRuntime, ShipInstanceRuntimeState.legacyNeutral());

        DerivedShipState attackerDerived = calculator.derive(
                attackerHull,
                attackerFit,
                attackerEngineering.runtimeState.consumables(),
                DamageState.pristine());
        attackerRadar = sensorAdapter.derive(attackerDerived).sensors().stream()
                .filter(value -> value.definition().mode() == Mode.ACTIVE_RADAR)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Stage 17.5I balanced live fit must retain an active radar"));

        targetDamage = ShipDamageRuntime.Snapshot.pristine(targetHull, targetDamageLayout);
        DerivedShipState pristineTarget = deriveTarget();
        targetFittedShield = new ShipShieldEngineeringAdapter().derive(pristineTarget).stream()
                .filter(value -> value.mountId().equals(SHIELD_MOUNT))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Stage 17.5I balanced live target must retain its fitted shield"));
        targetShield = targetFittedShield.chargedState(shieldRuntime);
        targetAccelerationMps2 = pristineTarget.accelerationMps2();
        protectionRuntime = new KineticProtectionRuntime(
                shieldRuntime,
                new HeavyImpactResolver(engineeringCatalog, protectionCatalog),
                new ShipDamageRuntime());
    }

    /**
     * Advances exactly one fixed authoritative simulation tick.
     *
     * <p>Calling presentation methods does not advance this clock.</p>
     */
    public void advanceOneTick() {
        tick++;
        stepAttackerEngineering();
        kineticCooldownSeconds = Math.max(0d, kineticCooldownSeconds - TICK_SECONDS);
        if (tick == 1L || tick % SENSOR_INTERVAL_TICKS == 0L) {
            scanTarget();
        }
        tryFireKinetic();
        advanceProjectiles();
        if (recentImpact != null && tick - recentImpactTick > IMPACT_VISIBILITY_TICKS) {
            recentImpact = null;
        }
    }

    /**
     * Returns an immutable read snapshot of the current live tactical state.
     *
     * @return current authoritative state suitable for tests or presentation projection
     */
    public Snapshot snapshot() {
        return new Snapshot(
                tick,
                elapsedSeconds(),
                attackerHull,
                targetHull,
                ShipDamageRuntime.Snapshot.pristine(
                        attackerHull,
                        protectionCatalog.findHullDamageLayout(attackerHull.id())),
                targetDamage,
                targetFittedShield.definition(),
                targetShield,
                attackerTrack,
                List.copyOf(projectiles),
                recentImpact,
                recentImpactTick,
                roundsOnMount(attackerEngineering.runtimeState.consumables(), PRIMARY_MOUNT),
                shotsFired,
                impactsResolved,
                targetAccelerationMps2,
                attackerEngineering.runtimeState.sharedBusEnergyJ(),
                attackerEngineering.runtimeState.shipHeatStoredJ());
    }

    /** @return current deterministic simulation tick */
    public long tick() {
        return tick;
    }

    /** @return current deterministic simulation time in seconds */
    public double elapsedSeconds() {
        return tick * TICK_SECONDS;
    }

    /**
     * Returns a compact deterministic state fingerprint for regression comparisons.
     *
     * @return immutable physical-state fingerprint
     */
    public StateFingerprint fingerprint() {
        double projectilePositionSum = projectiles.stream()
                .mapToDouble(value -> value.xM() * 31d + value.yM() * 17d)
                .sum();
        double meanCompartmentIntegrity = targetHull.compartments().stream()
                .mapToDouble(value -> targetDamage.compartmentIntegrityById().getOrDefault(value.id(), 1d))
                .average()
                .orElse(1d);
        return new StateFingerprint(
                tick,
                roundsOnMount(attackerEngineering.runtimeState.consumables(), PRIMARY_MOUNT),
                shotsFired,
                impactsResolved,
                projectiles.size(),
                projectilePositionSum,
                targetShield.reserveJ(),
                targetShield.collapsed(),
                meanCompartmentIntegrity,
                targetAccelerationMps2,
                attackerTrack == null ? null : attackerTrack.informationState());
    }

    private void stepAttackerEngineering() {
        var result = engineeringRuntime.advance(
                attackerFit,
                attackerEngineering.runtimeState,
                DamageState.pristine(),
                OperatingCommand.idle(),
                TICK_SECONDS);
        attackerEngineering.setRuntimeState(result.state());
    }

    private void scanTarget() {
        DerivedShipState targetDerived = deriveTarget();
        var targetSignature = sensorAdapter.derive(targetDerived).staticSignature();
        var budget = grantService.beginInterval(attackerEngineering, SENSOR_INTERVAL_SECONDS);
        var observation = observationService.observe(
                attackerEngineering,
                attackerRadar,
                SensorRuntimeState.nominal(),
                SENSOR_INTERVAL_SECONDS,
                ATTACKER_ENTITY_ID,
                TARGET_ENTITY_ID,
                new Position2d(ATTACKER_X_M, CENTER_Y_M),
                new Position2d(TARGET_X_M, CENTER_Y_M),
                targetSignature,
                ElectronicWarfareState.empty(),
                elapsedSeconds(),
                budget);
        observation.measurement().ifPresent(measurement -> {
            trackMeasurements.add(measurement);
            while (trackMeasurements.size() > MAX_TRACK_MEASUREMENTS) {
                trackMeasurements.remove(0);
            }
            attackerTrack = sensorRuntime.fuse(
                    TARGET_ENTITY_ID,
                    List.copyOf(trackMeasurements),
                    DatalinkState.local(),
                    TrackQualityPolicy.defaultPolicy(),
                    elapsedSeconds());
        });
    }

    private void tryFireKinetic() {
        if (attackerTrack == null || kineticCooldownSeconds > EPSILON) {
            return;
        }
        if (attackerTrack.informationState() != TrackState.InformationState.TRACKED
                && attackerTrack.informationState() != TrackState.InformationState.FIRE_CONTROL) {
            return;
        }
        if (roundsOnMount(attackerEngineering.runtimeState.consumables(), PRIMARY_MOUNT) <= 0L) {
            return;
        }

        DerivedShipState attackerDerived = calculator.derive(
                attackerHull,
                attackerFit,
                attackerEngineering.runtimeState.consumables(),
                DamageState.pristine());
        var mount = weaponAdapter.deriveKineticMounts(
                        attackerDerived,
                        ammunitionCatalog,
                        launcherCatalog,
                        attackerDoctrine.weaponLoadout()).stream()
                .filter(value -> value.mountId().equals(PRIMARY_MOUNT))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Stage 17.5I balanced live fit lost its primary kinetic mount"));
        var solution = fireControl.planKinetic(
                mount.round(),
                attackerTrack,
                new KinematicState(ATTACKER_X_M, CENTER_Y_M, 0d, 0d),
                new TargetMotionEstimate(0d, 0d, 0d, 0d),
                mount.pointingJitterRad(),
                elapsedSeconds());
        if (!solution.allowed()) {
            return;
        }

        var consumption = ammunitionRuntime.consumeOne(
                attackerEngineering.runtimeState.consumables(),
                mount.mountId(),
                mount.launcher(),
                mount.round().massKg());
        replaceAttackerConsumables(consumption.consumables());
        ProjectileBody body = fireControl.materializeKineticProjectile(
                nextProjectileId++,
                ATTACKER_ENTITY_ID,
                tick,
                mount.round(),
                new KinematicState(ATTACKER_X_M, CENTER_Y_M, 0d, 0d),
                solution);
        projectiles.add(body);
        shotsFired++;
        kineticCooldownSeconds = mount.launcher().cycleTimeSeconds();
    }

    private void advanceProjectiles() {
        if (projectiles.isEmpty()) {
            return;
        }
        double collisionRadius = 0.5d * Math.max(
                targetHull.boundingDimensionsM().lengthM(),
                targetHull.boundingDimensionsM().widthM());
        List<ProjectileBody> survivors = new ArrayList<>(projectiles.size());
        for (ProjectileBody body : projectiles) {
            ProjectileBody next = body.advance(TICK_SECONDS);
            if (segmentIntersectsCircle(
                    body.xM(), body.yM(), next.xM(), next.yM(), TARGET_X_M, CENTER_Y_M, collisionRadius)) {
                resolveTargetImpact(next);
            } else if (Math.hypot(next.xM() - ATTACKER_X_M, next.yM() - CENTER_Y_M) <= MAX_BODY_DISTANCE_M) {
                survivors.add(next);
            }
        }
        projectiles.clear();
        projectiles.addAll(survivors);
    }

    private void resolveTargetImpact(ProjectileBody projectile) {
        KineticProtectionRuntime.ShieldInput shieldInput = targetShield.emitterIntegrity() > 0d
                ? new KineticProtectionRuntime.ShieldInput(targetFittedShield.definition(), targetShield)
                : null;
        KineticProtectionRuntime.Result result = protectionRuntime.resolve(
                projectile,
                shieldInput,
                Math.PI,
                TICK_SECONDS,
                targetHull.structuralProtectionStackId(),
                0d,
                targetHull,
                targetFit,
                targetDamageLayout,
                targetDamage,
                new Vector3d(0d, 0d, 0d));
        if (result.shieldInteraction() != null) {
            targetShield = result.shieldInteraction().state();
        }
        if (result.damageEvent() != null) {
            targetDamage = result.damageEvent().snapshot();
            double emitterIntegrity = targetDamage.moduleDamage().moduleIntegrityByMount()
                    .getOrDefault(SHIELD_MOUNT, 1d);
            targetShield = shieldRuntime.withEmitterIntegrity(
                    targetFittedShield.definition(), targetShield, emitterIntegrity);
            targetAccelerationMps2 = deriveTarget().accelerationMps2();
        }
        recentImpact = result;
        recentImpactTick = tick;
        impactsResolved++;
    }

    private DerivedShipState deriveTarget() {
        return calculator.derive(
                targetHull,
                targetFit,
                targetDoctrine.initialConsumables(),
                targetDamage.moduleDamage());
    }

    private void replaceAttackerConsumables(ShipEngineeringState.ConsumableState consumables) {
        RuntimeState state = attackerEngineering.runtimeState;
        attackerEngineering.setRuntimeState(new RuntimeState(
                Objects.requireNonNull(consumables, "consumables"),
                state.sharedBusEnergyJ(),
                state.shipHeatStoredJ(),
                state.localHeatJByMount(),
                state.thrustLimitNByMount(),
                state.coolantBusCapacityW(),
                state.ftlCooldownSecondsByMount()));
    }

    private static long roundsOnMount(ShipEngineeringState.ConsumableState state, String mountId) {
        return state.interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .filter(value -> value.mountId().equals(mountId))
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }

    private static boolean segmentIntersectsCircle(
            double x0,
            double y0,
            double x1,
            double y1,
            double centerX,
            double centerY,
            double radius) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double lengthSquared = dx * dx + dy * dy;
        if (lengthSquared <= EPSILON) {
            return Math.hypot(x0 - centerX, y0 - centerY) <= radius;
        }
        double parameter = ((centerX - x0) * dx + (centerY - y0) * dy) / lengthSquared;
        double clamped = Math.max(0d, Math.min(1d, parameter));
        double nearestX = x0 + dx * clamped;
        double nearestY = y0 + dy * clamped;
        return Math.hypot(nearestX - centerX, nearestY - centerY) <= radius;
    }

    /**
     * Immutable read model of one live tactical simulation instant.
     *
     * @param tick authoritative fixed-step tick
     * @param elapsedSeconds authoritative simulation time
     * @param attackerHull attacker physical hull definition
     * @param targetHull target physical hull definition
     * @param attackerDamage attacker local damage snapshot
     * @param targetDamage target local damage snapshot
     * @param targetShieldDefinition fitted target shield definition
     * @param targetShieldState current target shield state
     * @param attackerTrack current target track, or null before detection
     * @param projectiles current independent physical kinetic bodies
     * @param recentImpact most recent production protection result, or null
     * @param recentImpactTick tick on which the recent impact occurred
     * @param primaryRoundsRemaining physical primary ammunition remaining
     * @param shotsFired physical shots materialized so far
     * @param impactsResolved physical target intersections resolved so far
     * @param targetAccelerationMps2 current production-derived target acceleration capability
     * @param attackerSharedBusEnergyJ current attacker shared electrical storage energy
     * @param attackerShipHeatStoredJ current attacker ship-bus stored heat
     */
    public record Snapshot(
            long tick,
            double elapsedSeconds,
            HullDefinition attackerHull,
            HullDefinition targetHull,
            ShipDamageRuntime.Snapshot attackerDamage,
            ShipDamageRuntime.Snapshot targetDamage,
            ShieldFieldRuntime.Definition targetShieldDefinition,
            ShieldFieldRuntime.State targetShieldState,
            TrackState attackerTrack,
            List<ProjectileBody> projectiles,
            KineticProtectionRuntime.Result recentImpact,
            long recentImpactTick,
            long primaryRoundsRemaining,
            long shotsFired,
            long impactsResolved,
            double targetAccelerationMps2,
            double attackerSharedBusEnergyJ,
            double attackerShipHeatStoredJ) {
        /**
         * Validates and freezes one read-only live snapshot.
         *
         * @param tick authoritative fixed-step tick
         * @param elapsedSeconds authoritative simulation time
         * @param attackerHull attacker physical hull definition
         * @param targetHull target physical hull definition
         * @param attackerDamage attacker local damage snapshot
         * @param targetDamage target local damage snapshot
         * @param targetShieldDefinition fitted target shield definition
         * @param targetShieldState current target shield state
         * @param attackerTrack current target track, or null
         * @param projectiles current physical kinetic bodies
         * @param recentImpact recent protection result, or null
         * @param recentImpactTick recent impact tick
         * @param primaryRoundsRemaining physical ammunition remaining
         * @param shotsFired physical shots fired
         * @param impactsResolved physical impacts resolved
         * @param targetAccelerationMps2 current derived acceleration capability
         * @param attackerSharedBusEnergyJ current stored electrical energy
         * @param attackerShipHeatStoredJ current ship-bus heat
         */
        public Snapshot {
            if (tick < 0L || !Double.isFinite(elapsedSeconds) || elapsedSeconds < 0d) {
                throw new IllegalArgumentException("live snapshot clock must be finite and non-negative");
            }
            Objects.requireNonNull(attackerHull, "attackerHull");
            Objects.requireNonNull(targetHull, "targetHull");
            Objects.requireNonNull(attackerDamage, "attackerDamage");
            Objects.requireNonNull(targetDamage, "targetDamage");
            Objects.requireNonNull(targetShieldDefinition, "targetShieldDefinition");
            Objects.requireNonNull(targetShieldState, "targetShieldState");
            projectiles = List.copyOf(Objects.requireNonNull(projectiles, "projectiles"));
            if (primaryRoundsRemaining < 0L || shotsFired < 0L || impactsResolved < 0L) {
                throw new IllegalArgumentException("live snapshot counters must be non-negative");
            }
            if (!Double.isFinite(targetAccelerationMps2) || targetAccelerationMps2 < 0d
                    || !Double.isFinite(attackerSharedBusEnergyJ) || attackerSharedBusEnergyJ < 0d
                    || !Double.isFinite(attackerShipHeatStoredJ) || attackerShipHeatStoredJ < 0d) {
                throw new IllegalArgumentException("live snapshot physical scalars must be finite and non-negative");
            }
        }
    }

    /**
     * Compact equality-friendly deterministic state fingerprint.
     *
     * @param tick authoritative tick
     * @param primaryRoundsRemaining physical primary ammunition remaining
     * @param shotsFired number of materialized shots
     * @param impactsResolved number of resolved target impacts
     * @param projectileCount active physical projectile count
     * @param projectilePositionSum deterministic aggregate of projectile positions
     * @param targetShieldReserveJ current target shield reserve
     * @param targetShieldCollapsed current target shield collapsed state
     * @param meanTargetCompartmentIntegrity current mean local compartment integrity
     * @param targetAccelerationMps2 current derived target acceleration
     * @param trackState current attacker information state, or null before detection
     */
    public record StateFingerprint(
            long tick,
            long primaryRoundsRemaining,
            long shotsFired,
            long impactsResolved,
            int projectileCount,
            double projectilePositionSum,
            double targetShieldReserveJ,
            boolean targetShieldCollapsed,
            double meanTargetCompartmentIntegrity,
            double targetAccelerationMps2,
            TrackState.InformationState trackState) {
    }
}
