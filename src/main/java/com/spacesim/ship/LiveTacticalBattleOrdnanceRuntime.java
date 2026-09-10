package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponLauncherCatalog;
import com.spacesim.ship.GuidanceRuntime.TrackSource;
import com.spacesim.ship.LiveTacticalBattleControlRuntime.ActorControlState;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.CombatantRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.WeaponFireControl.TargetMotionEstimate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Shared Stage-19I guided-ordnance coordinator layered on the production multi-combatant runtime.
 *
 * <p>The wrapped {@link LiveTacticalBattleWeaponRuntime} remains authoritative for the shared combat
 * clock, ship-local combat state and the one production protection/residual-projectile path. Guided
 * launch, guidance and physical body propagation reuse Stage-17.5E content/runtime only; a missile
 * hit is never converted into hit probability or abstract salvo damage.</p>
 */
public final class LiveTacticalBattleOrdnanceRuntime {
    private static final double TICK_SECONDS = LiveTacticalBattleControlRuntime.TICK_SECONDS;
    private static final double EPSILON = 1e-12d;
    private static final long GUIDED_RESIDUAL_PROJECTILE_NAMESPACE = 1_000_000_000L;

    private final LiveTacticalBattleWeaponRuntime weaponRuntime;
    private final ShipEngineeringCatalog engineeringCatalog;
    private final WeaponAmmunitionCatalog ammunitionCatalog;
    private final WeaponLauncherCatalog launcherCatalog;
    private final DerivedShipCalculator calculator;
    private final ShipGuidedWeaponEngineeringAdapter guidedAdapter;
    private final GuidanceRuntime guidanceRuntime;
    private final AmmunitionRuntime ammunitionRuntime;
    private final WeaponMountRuntime weaponMountRuntime;
    private final List<GuidedWeaponBody> guidedBodies = new ArrayList<>();
    private final TreeMap<Long, String> launchMountByBodyId = new TreeMap<>();
    private final TreeMap<Long, Long> spawnTickByBodyId = new TreeMap<>();
    private final TreeMap<Long, Long> guidedLaunchesBySourceEntityId = new TreeMap<>();
    private final TreeMap<Long, Long> guidedImpactsByTargetEntityId = new TreeMap<>();

    private long nextGuidedBodyId = 195_000L;

    /**
     * Creates guided-ordnance execution over one existing shared physical-combat runtime.
     *
     * @param weaponRuntime authoritative shared battle weapon/protection runtime
     */
    public LiveTacticalBattleOrdnanceRuntime(LiveTacticalBattleWeaponRuntime weaponRuntime) {
        this.weaponRuntime = Objects.requireNonNull(weaponRuntime, "weaponRuntime");
        engineeringCatalog = battleState().engineeringCatalog();
        ammunitionCatalog = weaponRuntime.ammunitionCatalog();
        launcherCatalog = weaponRuntime.launcherCatalog();
        calculator = new DerivedShipCalculator(engineeringCatalog);
        guidedAdapter = new ShipGuidedWeaponEngineeringAdapter();
        guidanceRuntime = new GuidanceRuntime();
        ammunitionRuntime = new AmmunitionRuntime();
        weaponMountRuntime = new WeaponMountRuntime();
        for (CombatantRuntime combatant : battleState().combatants()) {
            guidedLaunchesBySourceEntityId.put(combatant.spec().entityId(), 0L);
            guidedImpactsByTargetEntityId.put(combatant.spec().entityId(), 0L);
        }
    }

    /**
     * Advances one complete shared battle tick including guided launch, guidance and physical impact.
     *
     * <p>Ship start positions are retained before the wrapped runtime advances the authoritative
     * ship/kinetic tick. Existing guided bodies therefore use the same relative swept ship motion as
     * kinetic bodies. Newly launched guided bodies use post-movement ship geometry, matching the
     * established muzzle-exit ordering.</p>
     */
    public void advanceOneTick() {
        TreeMap<Long, PositionSnapshot> shipStartPositions = snapshotShipPositions();
        weaponRuntime.advanceOneTick();
        launchAllAuthorizedGuidedWeapons();
        guideAdvanceAndResolveBodies(shipStartPositions);
    }

    /** @return authoritative shared battle tick */
    public long tick() {
        return weaponRuntime.tick();
    }

    /** @return authoritative shared battle time in seconds */
    public double elapsedSeconds() {
        return weaponRuntime.elapsedSeconds();
    }

    /** @return wrapped production kinetic/protection runtime */
    public LiveTacticalBattleWeaponRuntime weaponRuntime() {
        return weaponRuntime;
    }

    /** @return authoritative shared materialized combatant state */
    public LiveTacticalBattleRuntimeState battleState() {
        return weaponRuntime.battleState();
    }

    /** @return immutable active guided physical bodies in deterministic creation order */
    public List<GuidedWeaponBody> guidedBodies() {
        return List.copyOf(guidedBodies);
    }

    /**
     * Removes one physically intercepted guided body and releases its launcher support-channel ownership.
     *
     * <p>This package-local seam performs no collision or probability test. It may be called only by
     * exact-local physical defense integration after a swept body-body intersection has already been
     * established. Launch/ship-impact counters remain historical and are not rewritten.</p>
     *
     * @param bodyId stable guided-body identity
     * @return removed physical body, or {@code null} when it is no longer active
     */
    GuidedWeaponBody removeGuidedBody(long bodyId) {
        for (int index = 0; index < guidedBodies.size(); index++) {
            GuidedWeaponBody body = guidedBodies.get(index);
            if (body.bodyId() == bodyId) {
                guidedBodies.remove(index);
                releaseGuidedBody(bodyId);
                return body;
            }
        }
        return null;
    }

    /**
     * Returns guided bodies launched by one combatant.
     *
     * @param sourceEntityId stable launching combatant identity
     * @return non-negative physical guided launch count
     */
    public long guidedLaunches(long sourceEntityId) {
        battleState().requireCombatant(sourceEntityId);
        return guidedLaunchesBySourceEntityId.get(sourceEntityId);
    }

    /**
     * Returns physical guided-body intersections resolved on one combatant.
     *
     * @param targetEntityId stable struck combatant identity
     * @return non-negative guided impact count
     */
    public long guidedImpactsOn(long targetEntityId) {
        battleState().requireCombatant(targetEntityId);
        return guidedImpactsByTargetEntityId.get(targetEntityId);
    }

    /** @return total guided-body/ship physical intersections resolved */
    public long totalGuidedImpacts() {
        return guidedImpactsByTargetEntityId.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * Returns an equality-friendly whole-battle guided-ordnance projection.
     *
     * @return deterministic guided state fingerprint
     */
    public BattleOrdnanceFingerprint fingerprint() {
        List<SourceGuidedFingerprint> sources = battleState().combatants().stream()
                .map(combatant -> new SourceGuidedFingerprint(
                        combatant.spec().entityId(),
                        guidedLaunchesBySourceEntityId.get(combatant.spec().entityId()),
                        guidedAmmunitionRounds(combatant.engineering().runtimeState.consumables())))
                .toList();
        List<TargetGuidedFingerprint> targets = battleState().combatants().stream()
                .map(combatant -> new TargetGuidedFingerprint(
                        combatant.spec().entityId(),
                        guidedImpactsByTargetEntityId.get(combatant.spec().entityId())))
                .toList();
        List<GuidedBodyFingerprint> bodies = guidedBodies.stream()
                .map(body -> new GuidedBodyFingerprint(
                        body.bodyId(),
                        body.sourceEntityId(),
                        body.targetId(),
                        launchMountByBodyId.get(body.bodyId()),
                        spawnTickByBodyId.get(body.bodyId()),
                        body.xM(),
                        body.yM(),
                        body.velocityXMps(),
                        body.velocityYMps(),
                        body.remainingPropellantKg(),
                        body.remainingPoweredBurnSeconds(),
                        body.seekerAvailable(),
                        body.guidanceAvailable()))
                .toList();
        return new BattleOrdnanceFingerprint(
                tick(),
                weaponRuntime.fingerprint(),
                sources,
                targets,
                bodies);
    }

    private void launchAllAuthorizedGuidedWeapons() {
        for (CombatantRuntime shooter : battleState().combatants()) {
            ActorControlState control = weaponRuntime.controlRuntime().controlState(shooter.spec().entityId());
            if (!control.fireAuthorized() || !control.intent().targetSelected()) {
                continue;
            }
            TrackState selectedTrack = selectedVisibleTrack(shooter, control.intent().targetId());
            if (selectedTrack == null) {
                throw new IllegalStateException("authorized guided target disappeared from actor-visible domain");
            }
            launchGuidedMounts(shooter, selectedTrack);
        }
    }

    private void launchGuidedMounts(CombatantRuntime shooter, TrackState selectedTrack) {
        EngineeringComponent engineering = shooter.engineering();
        List<ShipGuidedWeaponEngineeringAdapter.FittedGuidedMount> mounts = guidedAdapter.deriveGuidedMounts(
                derive(shooter),
                ammunitionCatalog,
                launcherCatalog,
                engineering.instanceState.weaponLoadout());
        for (ShipGuidedWeaponEngineeringAdapter.FittedGuidedMount mount : mounts) {
            ShipInstanceRuntimeState instance = engineering.instanceState;
            if (!weaponMountRuntime.ready(instance.weaponMountRuntime(), mount.mountId())) {
                continue;
            }
            if (activeSupportChannels(shooter.spec().entityId(), mount.mountId())
                    >= mount.launcher().supportChannelCount()) {
                continue;
            }
            double launchedMassKg = mount.ammunition().wetMassKg();
            var ammunitionPlan = ammunitionRuntime.planOne(
                    engineering.runtimeState.consumables(),
                    mount.mountId(),
                    mount.launcher(),
                    launchedMassKg);
            if (!ammunitionPlan.allowed()) {
                continue;
            }

            GuidedWeaponBody body = GuidedWeaponBody.launch(
                    nextGuidedBodyId,
                    shooter.spec().entityId(),
                    selectedTrack.targetId(),
                    mount.ammunition().toRuntimeWeapon(),
                    mount.ammunition().materialId(),
                    mount.ammunition().shape(),
                    mount.ammunition().lengthM(),
                    mount.ammunition().diameterM(),
                    mount.ammunition().impactPayloadId(),
                    shooter.transform().position.x,
                    shooter.transform().position.y,
                    shooter.transform().velocity.x,
                    shooter.transform().velocity.y);
            var consumption = ammunitionRuntime.consumeOne(
                    engineering.runtimeState.consumables(),
                    mount.mountId(),
                    mount.launcher(),
                    launchedMassKg);
            WeaponMountRuntime.RuntimeState nextWeaponState = weaponMountRuntime.commitShot(
                    instance.weaponMountRuntime(),
                    mount.mountId(),
                    mount.launcher());

            replaceConsumables(engineering, consumption.consumables());
            replaceWeaponRuntime(engineering, nextWeaponState);
            guidedBodies.add(body);
            launchMountByBodyId.put(body.bodyId(), mount.mountId());
            spawnTickByBodyId.put(body.bodyId(), tick());
            nextGuidedBodyId = Math.addExact(nextGuidedBodyId, 1L);
            guidedLaunchesBySourceEntityId.compute(
                    shooter.spec().entityId(),
                    (ignored, count) -> Math.addExact(Objects.requireNonNull(count, "guided launch count"), 1L));
        }
    }

    private void guideAdvanceAndResolveBodies(TreeMap<Long, PositionSnapshot> shipStartPositions) {
        List<GuidedWeaponBody> survivors = new ArrayList<>(guidedBodies.size());
        for (GuidedWeaponBody body : guidedBodies) {
            GuidedWeaponBody guided = guide(body);
            GuidedWeaponBody next = guided.advanceBallistic(TICK_SECONDS);
            ImpactCandidate impact = firstImpact(guided, next, shipStartPositions);
            if (impact == null) {
                survivors.add(next);
                continue;
            }

            GuidedWeaponBody impactBody = atFraction(guided, next, impact.fraction);
            long spawnTick = Objects.requireNonNull(spawnTickByBodyId.get(body.bodyId()), "guided spawn tick");
            ProjectileBody physicalImpact = toProjectileBody(impactBody, spawnTick);
            KineticProtectionRuntime.Result result = weaponRuntime.resolveExternalPhysicalImpact(
                    impact.target.spec().entityId(),
                    physicalImpact,
                    impact.targetPositionAtImpact.xM,
                    impact.targetPositionAtImpact.yM);
            guidedImpactsByTargetEntityId.compute(
                    impact.target.spec().entityId(),
                    (ignored, count) -> Math.addExact(Objects.requireNonNull(count, "guided impact count"), 1L));
            releaseGuidedBody(body.bodyId());

            if (result.postProtectionProjectile() != null) {
                double remainingSeconds = TICK_SECONDS * (1d - impact.fraction);
                ProjectileBody residual = result.postProtectionProjectile();
                if (remainingSeconds > EPSILON) {
                    residual = residual.advance(remainingSeconds);
                }
                weaponRuntime.acceptExternalProjectile(residual);
            }
        }
        guidedBodies.clear();
        guidedBodies.addAll(survivors);
    }

    private GuidedWeaponBody guide(GuidedWeaponBody body) {
        TrackState track = visibleTrackForSource(body.sourceEntityId(), body.targetId());
        if (track == null) {
            return body;
        }
        GuidanceRuntime.GuidanceCommand command = guidanceRuntime.planLeadPursuit(
                body,
                track,
                new TargetMotionEstimate(0d, 0d, 0d, 0d),
                TrackSource.DATALINK,
                TICK_SECONDS);
        return command.allowed() ? guidanceRuntime.execute(body, command) : body;
    }

    private ImpactCandidate firstImpact(
            GuidedWeaponBody body,
            GuidedWeaponBody next,
            TreeMap<Long, PositionSnapshot> shipStartPositions) {
        ImpactCandidate best = null;
        boolean newlySpawned = Objects.requireNonNull(spawnTickByBodyId.get(body.bodyId()), "guided spawn tick") == tick();
        for (CombatantRuntime target : battleState().combatants()) {
            if (target.spec().entityId() == body.sourceEntityId()) {
                continue;
            }
            PositionSnapshot end = new PositionSnapshot(
                    target.transform().position.x,
                    target.transform().position.y);
            PositionSnapshot start = newlySpawned
                    ? end
                    : Objects.requireNonNull(shipStartPositions.get(target.spec().entityId()), "target start position");
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

    private ProjectileBody toProjectileBody(GuidedWeaponBody body, long spawnTick) {
        return new ProjectileBody(
                Math.addExact(GUIDED_RESIDUAL_PROJECTILE_NAMESPACE, body.bodyId()),
                body.sourceEntityId(),
                spawnTick,
                body.materialId(),
                body.shape(),
                body.lengthM(),
                body.diameterM(),
                body.currentMassKg(),
                body.xM(),
                body.yM(),
                body.velocityXMps(),
                body.velocityYMps());
    }

    private static GuidedWeaponBody atFraction(
            GuidedWeaponBody start,
            GuidedWeaponBody end,
            double fraction) {
        return new GuidedWeaponBody(
                start.bodyId(),
                start.sourceEntityId(),
                start.targetId(),
                start.definition(),
                start.materialId(),
                start.shape(),
                start.lengthM(),
                start.diameterM(),
                start.impactPayloadId(),
                start.xM() + (end.xM() - start.xM()) * fraction,
                start.yM() + (end.yM() - start.yM()) * fraction,
                start.velocityXMps(),
                start.velocityYMps(),
                start.remainingPropellantKg(),
                start.remainingPoweredBurnSeconds(),
                start.seekerAvailable(),
                start.guidanceAvailable());
    }

    private void releaseGuidedBody(long bodyId) {
        launchMountByBodyId.remove(bodyId);
        spawnTickByBodyId.remove(bodyId);
    }

    private int activeSupportChannels(long sourceEntityId, String mountId) {
        int active = 0;
        for (GuidedWeaponBody body : guidedBodies) {
            if (body.sourceEntityId() == sourceEntityId
                    && mountId.equals(launchMountByBodyId.get(body.bodyId()))) {
                active = Math.addExact(active, 1);
            }
        }
        return active;
    }

    private TrackState selectedVisibleTrack(CombatantRuntime shooter, long targetId) {
        return battleState().visibleContacts(shooter.spec().entityId()).stream()
                .map(ObservedThreatAssessmentService.ObservedContact::track)
                .filter(track -> track.targetId() == targetId)
                .findFirst()
                .orElse(null);
    }

    private TrackState visibleTrackForSource(long sourceEntityId, long targetId) {
        return battleState().visibleContacts(sourceEntityId).stream()
                .map(ObservedThreatAssessmentService.ObservedContact::track)
                .filter(track -> track.targetId() == targetId)
                .findFirst()
                .orElse(null);
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
        EngineeringComponent engineering = combatant.engineering();
        return calculator.derive(
                combatant.hull(),
                engineering.fit,
                engineering.runtimeState.consumables(),
                engineering.instanceState.damage().moduleDamage());
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

    private static long guidedAmmunitionRounds(ShipEngineeringState.ConsumableState state) {
        return state.interfaceLoads().stream()
                .filter(value -> value.kind() == ShipEngineeringCatalog.InterfaceKind.AMMUNITION)
                .filter(value -> "guided_feed".equals(value.interfaceId()))
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
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
     * Per-source finite guided ammunition projection.
     *
     * @param entityId stable launching combatant identity
     * @param guidedLaunches physically materialized guided launches
     * @param guidedAmmunitionRounds current itemized guided-feed rounds
     */
    public record SourceGuidedFingerprint(
            long entityId,
            long guidedLaunches,
            long guidedAmmunitionRounds) {
        /**
         * Validates one per-source guided projection.
         *
         * @param entityId stable launching combatant identity
         * @param guidedLaunches physically materialized guided launches
         * @param guidedAmmunitionRounds current itemized guided-feed rounds
         */
        public SourceGuidedFingerprint {
            if (entityId <= 0L || guidedLaunches < 0L || guidedAmmunitionRounds < 0L) {
                throw new IllegalArgumentException("invalid guided source fingerprint");
            }
        }
    }

    /**
     * Per-target guided physical-impact projection.
     *
     * @param entityId stable target identity
     * @param guidedImpactsResolved guided-body intersections resolved on the target
     */
    public record TargetGuidedFingerprint(long entityId, long guidedImpactsResolved) {
        /**
         * Validates one guided target projection.
         *
         * @param entityId stable target identity
         * @param guidedImpactsResolved non-negative guided impact count
         */
        public TargetGuidedFingerprint {
            if (entityId <= 0L || guidedImpactsResolved < 0L) {
                throw new IllegalArgumentException("invalid guided target fingerprint");
            }
        }
    }

    /**
     * Equality-friendly projection of one active guided physical body.
     *
     * @param bodyId stable guided body identity
     * @param sourceEntityId launching combatant identity
     * @param targetId current target hypothesis identity
     * @param launchMountId physical launcher mount owning its support channel
     * @param spawnTick deterministic physical launch tick
     * @param xM current x position
     * @param yM current y position
     * @param velocityXMps current x velocity
     * @param velocityYMps current y velocity
     * @param remainingPropellantKg current physical propellant mass
     * @param remainingPoweredBurnSeconds current physical powered-burn lifetime
     * @param seekerAvailable current seeker availability
     * @param guidanceAvailable current guidance availability
     */
    public record GuidedBodyFingerprint(
            long bodyId,
            long sourceEntityId,
            long targetId,
            String launchMountId,
            long spawnTick,
            double xM,
            double yM,
            double velocityXMps,
            double velocityYMps,
            double remainingPropellantKg,
            double remainingPoweredBurnSeconds,
            boolean seekerAvailable,
            boolean guidanceAvailable) {
        /**
         * Validates one guided body projection.
         *
         * @param bodyId stable guided body identity
         * @param sourceEntityId launching combatant identity
         * @param targetId current target hypothesis identity
         * @param launchMountId physical launcher mount owning its support channel
         * @param spawnTick deterministic physical launch tick
         * @param xM current x position
         * @param yM current y position
         * @param velocityXMps current x velocity
         * @param velocityYMps current y velocity
         * @param remainingPropellantKg current physical propellant mass
         * @param remainingPoweredBurnSeconds current physical powered-burn lifetime
         * @param seekerAvailable current seeker availability
         * @param guidanceAvailable current guidance availability
         */
        public GuidedBodyFingerprint {
            if (bodyId <= 0L || sourceEntityId <= 0L || targetId <= 0L || spawnTick < 0L) {
                throw new IllegalArgumentException("guided body identities/tick must be valid");
            }
            if (launchMountId == null || launchMountId.isBlank()) {
                throw new IllegalArgumentException("launchMountId must be non-blank");
            }
            if (!Double.isFinite(xM) || !Double.isFinite(yM)
                    || !Double.isFinite(velocityXMps) || !Double.isFinite(velocityYMps)
                    || !Double.isFinite(remainingPropellantKg) || remainingPropellantKg < 0d
                    || !Double.isFinite(remainingPoweredBurnSeconds) || remainingPoweredBurnSeconds < 0d) {
                throw new IllegalArgumentException("guided body physical projection must be finite/non-negative");
            }
        }
    }

    /**
     * Whole-battle deterministic guided-ordnance fingerprint.
     *
     * @param tick authoritative shared battle tick
     * @param weaponFingerprint wrapped physical weapon/protection fingerprint
     * @param sources canonical per-source guided ammunition projections
     * @param targets canonical per-target guided impact projections
     * @param bodies current active guided physical bodies
     */
    public record BattleOrdnanceFingerprint(
            long tick,
            LiveTacticalBattleWeaponRuntime.BattleWeaponFingerprint weaponFingerprint,
            List<SourceGuidedFingerprint> sources,
            List<TargetGuidedFingerprint> targets,
            List<GuidedBodyFingerprint> bodies) {
        /**
         * Validates and freezes one whole-battle guided projection.
         *
         * @param tick authoritative shared battle tick
         * @param weaponFingerprint wrapped physical weapon/protection fingerprint
         * @param sources canonical per-source guided ammunition projections
         * @param targets canonical per-target guided impact projections
         * @param bodies current active guided physical bodies
         */
        public BattleOrdnanceFingerprint {
            if (tick < 0L) {
                throw new IllegalArgumentException("tick must be non-negative");
            }
            Objects.requireNonNull(weaponFingerprint, "weaponFingerprint");
            sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
            targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
            bodies = List.copyOf(Objects.requireNonNull(bodies, "bodies"));
        }
    }
}
