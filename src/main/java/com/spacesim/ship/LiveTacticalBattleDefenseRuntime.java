package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.weapon.Stage175ICombatTestWeaponPack;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.GuidedEngagementRole;
import com.spacesim.content.weapon.WeaponLauncherCatalog;
import com.spacesim.ship.GuidanceRuntime.TrackSource;
import com.spacesim.ship.LayeredDefenseScheduler.Assignment;
import com.spacesim.ship.LayeredDefenseScheduler.DefendedZone;
import com.spacesim.ship.LayeredDefenseScheduler.DefenseStation;
import com.spacesim.ship.LayeredDefenseScheduler.Threat;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.CombatantRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.WeaponFireControl.TargetMotionEstimate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Stage-19I exact-local layered-defense integration over the shared production guided runtime.
 *
 * <p>Defense stations are real fitted guided mounts loaded with authored
 * {@link GuidedEngagementRole#INTERCEPTOR} ammunition. Scheduler assignments consume central
 * itemized ammunition/mass, persistent launcher cycles and authored support channels before
 * materializing ordinary {@link GuidedWeaponBody} interceptors. Successful interception requires a
 * swept physical body-body contact; assignment alone never deletes a threat.</p>
 *
 * <p>The current Stage-19I-C bridge still supplies exact-local hostile guided-body state to the
 * scheduler and interceptor guidance. This is temporary acceptance plumbing. Actor-bounded ordnance
 * sensing, EW/ECCM and decoys remain mandatory before Stage 19 can close.</p>
 */
public final class LiveTacticalBattleDefenseRuntime {
    private static final double TICK_SECONDS = LiveTacticalBattleControlRuntime.TICK_SECONDS;
    private static final double EPSILON = 1e-9d;
    private static final double EXACT_LOCAL_POSITION_VARIANCE_M2 = 1d;
    private static final double EXACT_LOCAL_BEARING_VARIANCE_RAD2 = 1e-12d;

    private final LiveTacticalBattleOrdnanceRuntime ordnanceRuntime;
    private final ShipEngineeringCatalog engineeringCatalog;
    private final WeaponAmmunitionCatalog ammunitionCatalog;
    private final WeaponLauncherCatalog launcherCatalog;
    private final DerivedShipCalculator calculator;
    private final ShipGuidedWeaponEngineeringAdapter guidedAdapter;
    private final LayeredDefenseScheduler defenseScheduler;
    private final GuidanceRuntime guidanceRuntime;
    private final AmmunitionRuntime ammunitionRuntime;
    private final WeaponMountRuntime weaponMountRuntime;
    private final GuidedBodyCollisionResolver collisionResolver;
    private final List<InterceptorRuntime> interceptors = new ArrayList<>();
    private final TreeMap<Long, Long> interceptorLaunchesByDefender = new TreeMap<>();
    private final TreeMap<Long, Long> successfulInterceptionsByDefender = new TreeMap<>();

    private long nextInterceptorBodyId = 198_000L;

    /**
     * Creates layered-defense execution over one authoritative shared ordnance runtime.
     *
     * @param ordnanceRuntime production guided/kinetic/ship runtime
     */
    public LiveTacticalBattleDefenseRuntime(LiveTacticalBattleOrdnanceRuntime ordnanceRuntime) {
        this.ordnanceRuntime = Objects.requireNonNull(ordnanceRuntime, "ordnanceRuntime");
        engineeringCatalog = Stage175ICombatTestContentPack.loadDoctrines();
        ammunitionCatalog = Stage175ICombatTestWeaponPack.loadAmmunition();
        launcherCatalog = Stage175ICombatTestWeaponPack.loadLaunchers();
        calculator = new DerivedShipCalculator(engineeringCatalog);
        guidedAdapter = new ShipGuidedWeaponEngineeringAdapter();
        defenseScheduler = new LayeredDefenseScheduler();
        guidanceRuntime = new GuidanceRuntime();
        ammunitionRuntime = new AmmunitionRuntime();
        weaponMountRuntime = new WeaponMountRuntime();
        collisionResolver = new GuidedBodyCollisionResolver();
        for (CombatantRuntime combatant : battleState().combatants()) {
            interceptorLaunchesByDefender.put(combatant.spec().entityId(), 0L);
            successfulInterceptionsByDefender.put(combatant.spec().entityId(), 0L);
        }
    }

    /**
     * Advances one shared combat tick through ordnance, interceptor motion, physical collision and assignment.
     *
     * <p>Strike/interceptor start positions are captured before either moves. The wrapped ordnance
     * runtime then advances the one authoritative battle clock and strike bodies. Existing
     * interceptors execute one bounded guidance/ballistic step. Relative swept body-body collision is
     * resolved next, and only surviving threats may cause new defensive assignments. Newly launched
     * interceptors begin moving on the following fixed tick.</p>
     *
     * <p>The wrapped ordnance runtime currently resolves guided-body/ship contact before this outer
     * interceptor collision phase. Therefore a same-tick threat that reaches a ship before this phase
     * retains ship-impact priority. Final Stage-19 integration must revisit this ordering if scale
     * evidence exposes materially ambiguous same-tick contacts.</p>
     */
    public void advanceOneTick() {
        TreeMap<Long, BodyPosition> strikeStarts = snapshotStrikePositions();
        TreeMap<Long, BodyPosition> interceptorStarts = snapshotInterceptorPositions();
        ordnanceRuntime.advanceOneTick();
        advanceExistingInterceptors();
        resolvePhysicalInterceptions(strikeStarts, interceptorStarts);
        scheduleAndLaunchInterceptors();
    }

    /** @return authoritative shared battle tick */
    public long tick() {
        return ordnanceRuntime.tick();
    }

    /** @return authoritative shared battle time in seconds */
    public double elapsedSeconds() {
        return ordnanceRuntime.elapsedSeconds();
    }

    /** @return wrapped shared guided-ordnance runtime */
    public LiveTacticalBattleOrdnanceRuntime ordnanceRuntime() {
        return ordnanceRuntime;
    }

    /** @return authoritative materialized combatant state */
    public LiveTacticalBattleRuntimeState battleState() {
        return ordnanceRuntime.battleState();
    }

    /** @return immutable active physically materialized interceptor bodies */
    public List<GuidedWeaponBody> interceptorBodies() {
        return interceptors.stream().map(InterceptorRuntime::body).toList();
    }

    /**
     * Returns physical interceptor launches by one defending combatant.
     *
     * @param defenderEntityId stable defender identity
     * @return non-negative launch count
     */
    public long interceptorLaunches(long defenderEntityId) {
        battleState().requireCombatant(defenderEntityId);
        return interceptorLaunchesByDefender.get(defenderEntityId);
    }

    /**
     * Returns swept physical interceptor/threat contacts resolved for one defender.
     *
     * @param defenderEntityId stable defender identity
     * @return non-negative successful physical interception count
     */
    public long successfulInterceptions(long defenderEntityId) {
        battleState().requireCombatant(defenderEntityId);
        return successfulInterceptionsByDefender.get(defenderEntityId);
    }

    /** @return total swept physical interceptor/threat contacts resolved */
    public long totalSuccessfulInterceptions() {
        return successfulInterceptionsByDefender.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * Equality-friendly whole-defense projection for deterministic acceptance.
     *
     * @return immutable layered-defense fingerprint
     */
    public BattleDefenseFingerprint fingerprint() {
        List<DefenderFingerprint> defenders = battleState().combatants().stream()
                .map(combatant -> new DefenderFingerprint(
                        combatant.spec().entityId(),
                        interceptorLaunchesByDefender.get(combatant.spec().entityId()),
                        successfulInterceptionsByDefender.get(combatant.spec().entityId()),
                        interceptorRounds(combatant)))
                .toList();
        List<InterceptorFingerprint> active = interceptors.stream()
                .map(value -> new InterceptorFingerprint(
                        value.body().bodyId(),
                        value.defenderEntityId(),
                        value.mountId(),
                        value.body().targetId(),
                        value.body().xM(),
                        value.body().yM(),
                        value.body().velocityXMps(),
                        value.body().velocityYMps(),
                        value.body().remainingPropellantKg(),
                        value.body().remainingPoweredBurnSeconds()))
                .toList();
        return new BattleDefenseFingerprint(
                tick(),
                ordnanceRuntime.fingerprint(),
                defenders,
                active);
    }

    private void advanceExistingInterceptors() {
        List<InterceptorRuntime> survivors = new ArrayList<>(interceptors.size());
        for (InterceptorRuntime interceptor : interceptors) {
            GuidedWeaponBody threat = strikeBody(interceptor.body().targetId());
            if (threat == null) {
                transferOrphanToProjectilePool(interceptor.body());
                continue;
            }
            TrackState exactLocalTrack = exactLocalThreatTrack(threat);
            GuidanceRuntime.GuidanceCommand command = guidanceRuntime.planLeadPursuit(
                    interceptor.body(),
                    exactLocalTrack,
                    new TargetMotionEstimate(
                            threat.velocityXMps(),
                            threat.velocityYMps(),
                            0d,
                            0d),
                    TrackSource.DATALINK,
                    TICK_SECONDS);
            GuidedWeaponBody guided = command.allowed()
                    ? guidanceRuntime.execute(interceptor.body(), command)
                    : interceptor.body();
            survivors.add(interceptor.withBody(guided.advanceBallistic(TICK_SECONDS)));
        }
        interceptors.clear();
        interceptors.addAll(survivors);
    }

    private void resolvePhysicalInterceptions(
            TreeMap<Long, BodyPosition> strikeStarts,
            TreeMap<Long, BodyPosition> interceptorStarts) {
        if (interceptors.isEmpty() || ordnanceRuntime.guidedBodies().isEmpty()) {
            return;
        }
        List<InterceptorRuntime> survivors = new ArrayList<>(interceptors.size());
        for (InterceptorRuntime interceptor : interceptors) {
            BodyPosition interceptorStart = interceptorStarts.get(interceptor.body().bodyId());
            if (interceptorStart == null) {
                survivors.add(interceptor);
                continue;
            }
            CollisionCandidate collision = firstCollision(interceptor, interceptorStart, strikeStarts);
            if (collision == null) {
                survivors.add(interceptor);
                continue;
            }
            GuidedWeaponBody removedThreat = ordnanceRuntime.removeGuidedBody(collision.threat().bodyId());
            if (removedThreat == null) {
                survivors.add(interceptor);
                continue;
            }

            double interceptorMass = interceptor.body().currentMassKg();
            double threatMass = removedThreat.currentMassKg();
            double totalMass = interceptorMass + threatMass;
            double collisionX = (interceptorMass * collision.interceptorXM()
                    + threatMass * collision.threatXM()) / totalMass;
            double collisionY = (interceptorMass * collision.interceptorYM()
                    + threatMass * collision.threatYM()) / totalMass;
            GuidedBodyCollisionResolver.ResidualPair result = collisionResolver.resolve(
                    interceptor.body(),
                    removedThreat,
                    collisionX,
                    collisionY,
                    tick());
            for (ProjectileBody residual : result.residuals()) {
                ordnanceRuntime.weaponRuntime().acceptExternalProjectile(residual);
            }
            successfulInterceptionsByDefender.compute(
                    interceptor.defenderEntityId(),
                    (ignored, count) -> Math.addExact(Objects.requireNonNull(count, "interception count"), 1L));
        }
        interceptors.clear();
        interceptors.addAll(survivors);
    }

    private CollisionCandidate firstCollision(
            InterceptorRuntime interceptor,
            BodyPosition interceptorStart,
            TreeMap<Long, BodyPosition> strikeStarts) {
        CollisionCandidate best = null;
        GuidedWeaponBody interceptorEnd = interceptor.body();
        for (GuidedWeaponBody threat : ordnanceRuntime.guidedBodies()) {
            BodyPosition threatStart = strikeStarts.get(threat.bodyId());
            if (threatStart == null) {
                threatStart = new BodyPosition(
                        threat.xM() - threat.velocityXMps() * TICK_SECONDS,
                        threat.yM() - threat.velocityYMps() * TICK_SECONDS);
            }
            double combinedRadius = bodyRadius(interceptorEnd) + bodyRadius(threat);
            var fraction = TacticalCollisionGeometry.firstSegmentCircleHitFraction(
                    interceptorStart.xM() - threatStart.xM(),
                    interceptorStart.yM() - threatStart.yM(),
                    interceptorEnd.xM() - threat.xM(),
                    interceptorEnd.yM() - threat.yM(),
                    combinedRadius);
            if (fraction.isEmpty()) {
                continue;
            }
            double value = fraction.getAsDouble();
            if (best == null
                    || value < best.fraction() - EPSILON
                    || (Math.abs(value - best.fraction()) <= EPSILON
                    && threat.bodyId() < best.threat().bodyId())) {
                best = new CollisionCandidate(
                        threat,
                        value,
                        interpolate(interceptorStart.xM(), interceptorEnd.xM(), value),
                        interpolate(interceptorStart.yM(), interceptorEnd.yM(), value),
                        interpolate(threatStart.xM(), threat.xM(), value),
                        interpolate(threatStart.yM(), threat.yM(), value));
            }
        }
        return best;
    }

    private void scheduleAndLaunchInterceptors() {
        if (ordnanceRuntime.guidedBodies().isEmpty()) {
            return;
        }
        for (CombatantRuntime defender : battleState().combatants()) {
            List<ShipGuidedWeaponEngineeringAdapter.FittedGuidedMount> mounts = interceptorMounts(defender);
            if (mounts.isEmpty()) {
                continue;
            }
            List<GuidedWeaponBody> hostileBodies = hostileStrikeBodies(defender);
            if (hostileBodies.isEmpty()) {
                continue;
            }

            double hullRadiusM = hullCircumscribedRadius(defender);
            DefendedZone zone = new DefendedZone(
                    defender.transform().position.x,
                    defender.transform().position.y,
                    hullRadiusM);
            TreeMap<Long, MountStation> stationById = new TreeMap<>();
            List<DefenseStation> stations = new ArrayList<>();
            long stationId = 1L;
            for (ShipGuidedWeaponEngineeringAdapter.FittedGuidedMount mount : mounts) {
                long rounds = roundsOnMount(defender, mount);
                int activeSupported = activeInterceptors(defender.spec().entityId(), mount.mountId());
                boolean channelAvailable = activeSupported < mount.launcher().supportChannelCount();
                boolean ready = weaponMountRuntime.ready(
                        defender.engineering().instanceState.weaponMountRuntime(),
                        mount.mountId());
                DefenseStation station = new DefenseStation(
                        stationId,
                        defender.transform().position.x,
                        defender.transform().position.y,
                        0d,
                        mount.ammunition().toRuntimeWeapon(),
                        ready,
                        channelAvailable ? 1 : 0,
                        rounds,
                        true,
                        hullRadiusM);
                stations.add(station);
                stationById.put(stationId, new MountStation(defender, mount));
                stationId = Math.addExact(stationId, 1L);
            }

            List<Threat> threats = hostileBodies.stream()
                    .map(body -> new Threat(
                            body.bodyId(),
                            body.xM(),
                            body.yM(),
                            body.velocityXMps(),
                            body.velocityYMps(),
                            body.currentMassKg(),
                            body.guidanceAvailable()))
                    .toList();
            List<Assignment> assignments = defenseScheduler.schedule(zone, threats, stations);
            for (Assignment assignment : assignments) {
                MountStation station = Objects.requireNonNull(
                        stationById.get(assignment.stationId()),
                        "defense station mapping");
                GuidedWeaponBody threat = hostileBodies.stream()
                        .filter(body -> body.bodyId() == assignment.threatId())
                        .findFirst()
                        .orElse(null);
                if (threat != null) {
                    launchInterceptor(station.defender(), station.mount(), threat);
                }
            }
        }
    }

    private void launchInterceptor(
            CombatantRuntime defender,
            ShipGuidedWeaponEngineeringAdapter.FittedGuidedMount mount,
            GuidedWeaponBody threat) {
        EngineeringComponent engineering = defender.engineering();
        ShipInstanceRuntimeState instance = engineering.instanceState;
        if (!weaponMountRuntime.ready(instance.weaponMountRuntime(), mount.mountId())) {
            return;
        }
        if (activeInterceptors(defender.spec().entityId(), mount.mountId())
                >= mount.launcher().supportChannelCount()) {
            return;
        }
        var plan = ammunitionRuntime.planOne(
                engineering.runtimeState.consumables(),
                mount.mountId(),
                mount.launcher(),
                mount.ammunition().wetMassKg());
        if (!plan.allowed()) {
            return;
        }

        GuidedWeaponBody body = GuidedWeaponBody.launch(
                nextInterceptorBodyId,
                defender.spec().entityId(),
                threat.bodyId(),
                mount.ammunition().toRuntimeWeapon(),
                mount.ammunition().materialId(),
                mount.ammunition().shape(),
                mount.ammunition().lengthM(),
                mount.ammunition().diameterM(),
                mount.ammunition().impactPayloadId(),
                defender.transform().position.x,
                defender.transform().position.y,
                defender.transform().velocity.x,
                defender.transform().velocity.y);
        var consumption = ammunitionRuntime.consumeOne(
                engineering.runtimeState.consumables(),
                mount.mountId(),
                mount.launcher(),
                mount.ammunition().wetMassKg());
        WeaponMountRuntime.RuntimeState nextWeaponState = weaponMountRuntime.commitShot(
                instance.weaponMountRuntime(),
                mount.mountId(),
                mount.launcher());
        replaceConsumables(engineering, consumption.consumables());
        replaceWeaponRuntime(engineering, nextWeaponState);

        interceptors.add(new InterceptorRuntime(
                body,
                defender.spec().entityId(),
                mount.mountId()));
        nextInterceptorBodyId = Math.addExact(nextInterceptorBodyId, 1L);
        interceptorLaunchesByDefender.compute(
                defender.spec().entityId(),
                (ignored, count) -> Math.addExact(Objects.requireNonNull(count, "interceptor launch count"), 1L));
    }

    private List<ShipGuidedWeaponEngineeringAdapter.FittedGuidedMount> interceptorMounts(
            CombatantRuntime defender) {
        EngineeringComponent engineering = defender.engineering();
        return guidedAdapter.deriveGuidedMounts(
                derive(defender),
                ammunitionCatalog,
                launcherCatalog,
                engineering.instanceState.weaponLoadout(),
                GuidedEngagementRole.INTERCEPTOR);
    }

    private List<GuidedWeaponBody> hostileStrikeBodies(CombatantRuntime defender) {
        return ordnanceRuntime.guidedBodies().stream()
                .filter(body -> battleState().requireCombatant(body.sourceEntityId()).spec().side()
                        != defender.spec().side())
                .toList();
    }

    private GuidedWeaponBody strikeBody(long bodyId) {
        return ordnanceRuntime.guidedBodies().stream()
                .filter(body -> body.bodyId() == bodyId)
                .findFirst()
                .orElse(null);
    }

    private TrackState exactLocalThreatTrack(GuidedWeaponBody threat) {
        return new TrackState(
                threat.bodyId(),
                TrackState.InformationState.FIRE_CONTROL,
                true,
                threat.xM(),
                threat.yM(),
                new TrackCovariance(
                        EXACT_LOCAL_POSITION_VARIANCE_M2,
                        EXACT_LOCAL_BEARING_VARIANCE_RAD2,
                        EXACT_LOCAL_POSITION_VARIANCE_M2),
                1d,
                elapsedSeconds(),
                1,
                1);
    }

    private void transferOrphanToProjectilePool(GuidedWeaponBody body) {
        ordnanceRuntime.weaponRuntime().acceptExternalProjectile(new ProjectileBody(
                Math.addExact(1_200_000_000L, body.bodyId()),
                body.sourceEntityId(),
                tick(),
                body.materialId(),
                body.shape(),
                body.lengthM(),
                body.diameterM(),
                body.currentMassKg(),
                body.xM(),
                body.yM(),
                body.velocityXMps(),
                body.velocityYMps()));
    }

    private TreeMap<Long, BodyPosition> snapshotStrikePositions() {
        TreeMap<Long, BodyPosition> result = new TreeMap<>();
        for (GuidedWeaponBody body : ordnanceRuntime.guidedBodies()) {
            result.put(body.bodyId(), new BodyPosition(body.xM(), body.yM()));
        }
        return result;
    }

    private TreeMap<Long, BodyPosition> snapshotInterceptorPositions() {
        TreeMap<Long, BodyPosition> result = new TreeMap<>();
        for (InterceptorRuntime interceptor : interceptors) {
            result.put(
                    interceptor.body().bodyId(),
                    new BodyPosition(interceptor.body().xM(), interceptor.body().yM()));
        }
        return result;
    }

    private int activeInterceptors(long defenderEntityId, String mountId) {
        int count = 0;
        for (InterceptorRuntime interceptor : interceptors) {
            if (interceptor.defenderEntityId() == defenderEntityId
                    && interceptor.mountId().equals(mountId)) {
                count = Math.addExact(count, 1);
            }
        }
        return count;
    }

    private long roundsOnMount(
            CombatantRuntime defender,
            ShipGuidedWeaponEngineeringAdapter.FittedGuidedMount mount) {
        return defender.engineering().runtimeState.consumables().interfaceLoads().stream()
                .filter(value -> value.kind() == ShipEngineeringCatalog.InterfaceKind.AMMUNITION)
                .filter(value -> value.mountId().equals(mount.mountId()))
                .filter(value -> value.interfaceId().equals(mount.launcher().ammunitionInterfaceId()))
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }

    private long interceptorRounds(CombatantRuntime defender) {
        List<ShipGuidedWeaponEngineeringAdapter.FittedGuidedMount> mounts = interceptorMounts(defender);
        long total = 0L;
        for (ShipGuidedWeaponEngineeringAdapter.FittedGuidedMount mount : mounts) {
            total = Math.addExact(total, roundsOnMount(defender, mount));
        }
        return total;
    }

    private DerivedShipState derive(CombatantRuntime combatant) {
        EngineeringComponent engineering = combatant.engineering();
        return calculator.derive(
                combatant.hull(),
                engineering.fit,
                engineering.runtimeState.consumables(),
                engineering.instanceState.damage().moduleDamage());
    }

    private static double hullCircumscribedRadius(CombatantRuntime combatant) {
        double length = combatant.hull().boundingDimensionsM().lengthM();
        double width = combatant.hull().boundingDimensionsM().widthM();
        return 0.5d * Math.hypot(length, width);
    }

    private static double bodyRadius(GuidedWeaponBody body) {
        return 0.5d * Math.hypot(body.lengthM(), body.diameterM());
    }

    private static double interpolate(double start, double end, double fraction) {
        return start + (end - start) * fraction;
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

    private record BodyPosition(double xM, double yM) {
        private BodyPosition {
            if (!Double.isFinite(xM) || !Double.isFinite(yM)) {
                throw new IllegalArgumentException("body position must be finite");
            }
        }
    }

    private record CollisionCandidate(
            GuidedWeaponBody threat,
            double fraction,
            double interceptorXM,
            double interceptorYM,
            double threatXM,
            double threatYM) {
        private CollisionCandidate {
            Objects.requireNonNull(threat, "threat");
            if (!Double.isFinite(fraction) || fraction < 0d || fraction > 1d
                    || !Double.isFinite(interceptorXM) || !Double.isFinite(interceptorYM)
                    || !Double.isFinite(threatXM) || !Double.isFinite(threatYM)) {
                throw new IllegalArgumentException("invalid physical collision candidate");
            }
        }
    }

    private record MountStation(
            CombatantRuntime defender,
            ShipGuidedWeaponEngineeringAdapter.FittedGuidedMount mount) {
        private MountStation {
            Objects.requireNonNull(defender, "defender");
            Objects.requireNonNull(mount, "mount");
        }
    }

    private record InterceptorRuntime(
            GuidedWeaponBody body,
            long defenderEntityId,
            String mountId) {
        private InterceptorRuntime {
            Objects.requireNonNull(body, "body");
            if (defenderEntityId <= 0L) {
                throw new IllegalArgumentException("defenderEntityId must be positive");
            }
            if (mountId == null || mountId.isBlank()) {
                throw new IllegalArgumentException("mountId must be non-blank");
            }
        }

        private InterceptorRuntime withBody(GuidedWeaponBody nextBody) {
            return new InterceptorRuntime(nextBody, defenderEntityId, mountId);
        }
    }

    /**
     * Per-defender physical interceptor stores/launch/interception projection.
     *
     * @param entityId stable defender combatant identity
     * @param interceptorLaunches physical interceptor launch count
     * @param successfulInterceptions swept physical interceptor/threat collision count
     * @param remainingInterceptorRounds current itemized INTERCEPTOR rounds on fitted mounts
     */
    public record DefenderFingerprint(
            long entityId,
            long interceptorLaunches,
            long successfulInterceptions,
            long remainingInterceptorRounds) {
        /**
         * Validates one defender projection.
         *
         * @param entityId stable defender combatant identity
         * @param interceptorLaunches physical interceptor launch count
         * @param successfulInterceptions swept physical interceptor/threat collision count
         * @param remainingInterceptorRounds current itemized interceptor rounds
         */
        public DefenderFingerprint {
            if (entityId <= 0L || interceptorLaunches < 0L
                    || successfulInterceptions < 0L || remainingInterceptorRounds < 0L) {
                throw new IllegalArgumentException("invalid defender fingerprint");
            }
        }
    }

    /**
     * Equality-friendly projection of one active physical interceptor.
     *
     * @param bodyId stable interceptor-body identity
     * @param defenderEntityId launching defender identity
     * @param mountId physical fitted launcher mount
     * @param targetThreatId physical guided threat body identity
     * @param xM current x position
     * @param yM current y position
     * @param velocityXMps current x velocity
     * @param velocityYMps current y velocity
     * @param remainingPropellantKg current physical interceptor propellant
     * @param remainingPoweredBurnSeconds current authored powered-burn lifetime
     */
    public record InterceptorFingerprint(
            long bodyId,
            long defenderEntityId,
            String mountId,
            long targetThreatId,
            double xM,
            double yM,
            double velocityXMps,
            double velocityYMps,
            double remainingPropellantKg,
            double remainingPoweredBurnSeconds) {
        /**
         * Validates one interceptor projection.
         *
         * @param bodyId stable interceptor-body identity
         * @param defenderEntityId launching defender identity
         * @param mountId physical fitted launcher mount
         * @param targetThreatId physical guided threat body identity
         * @param xM current x position
         * @param yM current y position
         * @param velocityXMps current x velocity
         * @param velocityYMps current y velocity
         * @param remainingPropellantKg current physical interceptor propellant
         * @param remainingPoweredBurnSeconds current powered-burn lifetime
         */
        public InterceptorFingerprint {
            if (bodyId <= 0L || defenderEntityId <= 0L || targetThreatId <= 0L) {
                throw new IllegalArgumentException("interceptor identities must be positive");
            }
            if (mountId == null || mountId.isBlank()) {
                throw new IllegalArgumentException("mountId must be non-blank");
            }
            if (!Double.isFinite(xM) || !Double.isFinite(yM)
                    || !Double.isFinite(velocityXMps) || !Double.isFinite(velocityYMps)
                    || !Double.isFinite(remainingPropellantKg) || remainingPropellantKg < -EPSILON
                    || !Double.isFinite(remainingPoweredBurnSeconds) || remainingPoweredBurnSeconds < -EPSILON) {
                throw new IllegalArgumentException("invalid interceptor physical projection");
            }
        }
    }

    /**
     * Whole-battle layered-defense deterministic fingerprint.
     *
     * @param tick authoritative shared battle tick
     * @param ordnanceFingerprint wrapped ship/weapon/guided physical state
     * @param defenders stable per-defender launch/store/interception projections
     * @param activeInterceptors current physical interceptor bodies
     */
    public record BattleDefenseFingerprint(
            long tick,
            LiveTacticalBattleOrdnanceRuntime.BattleOrdnanceFingerprint ordnanceFingerprint,
            List<DefenderFingerprint> defenders,
            List<InterceptorFingerprint> activeInterceptors) {
        /**
         * Validates and freezes one defense fingerprint.
         *
         * @param tick authoritative shared battle tick
         * @param ordnanceFingerprint wrapped physical ordnance fingerprint
         * @param defenders stable defender projections
         * @param activeInterceptors current interceptor projections
         */
        public BattleDefenseFingerprint {
            if (tick < 0L) {
                throw new IllegalArgumentException("tick must be non-negative");
            }
            Objects.requireNonNull(ordnanceFingerprint, "ordnanceFingerprint");
            defenders = List.copyOf(Objects.requireNonNull(defenders, "defenders"));
            activeInterceptors = List.copyOf(Objects.requireNonNull(activeInterceptors, "activeInterceptors"));
        }
    }
}
