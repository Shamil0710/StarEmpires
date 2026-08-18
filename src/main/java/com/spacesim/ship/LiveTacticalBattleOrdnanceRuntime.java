package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.weapon.Stage175ICombatTestWeaponPack;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponLauncherCatalog;
import com.spacesim.ship.GuidanceRuntime.TrackSource;
import com.spacesim.ship.LiveTacticalBattleControlRuntime.ActorControlState;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.CombatantRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.WeaponFireControl.TargetMotionEstimate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Shared Stage-19I guided-ordnance coordinator layered on the production multi-combatant runtime.
 *
 * <p>The wrapped {@link LiveTacticalBattleWeaponRuntime} remains authoritative for the shared combat
 * clock, sensing/AI, ship engineering/flight, fitted kinetic weapons and kinetic protection. This
 * coordinator adds only the existing Stage-17.5E guided path: fitted guided launchers, finite central
 * ammunition, physical launcher cycles/support channels, {@link GuidedWeaponBody},
 * {@link GuidanceRuntime} and real propellant consumption. It does not create missile hit chance,
 * abstract salvo damage, virtual ammunition or a second ship-motion/combat engine.</p>
 *
 * <p>Until the later EW/seeker slice, guidance consumes the launching actor's existing visible target
 * track through the explicit DATALINK path. Production {@link TrackState} has no target-velocity
 * estimate channel yet, so guidance uses a zero velocity estimate rather than reading authoritative
 * enemy transform velocity. Missing/insufficient actor-visible tracks leave the body ballistic.</p>
 */
public final class LiveTacticalBattleOrdnanceRuntime {
    private static final double TICK_SECONDS = LiveTacticalBattleControlRuntime.TICK_SECONDS;

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
    private final TreeMap<Long, Long> guidedLaunchesBySourceEntityId = new TreeMap<>();

    private long nextGuidedBodyId = 195_000L;

    /**
     * Creates guided-ordnance execution over one existing shared kinetic/control runtime.
     *
     * @param weaponRuntime authoritative shared battle weapon runtime
     */
    public LiveTacticalBattleOrdnanceRuntime(LiveTacticalBattleWeaponRuntime weaponRuntime) {
        this.weaponRuntime = Objects.requireNonNull(weaponRuntime, "weaponRuntime");
        engineeringCatalog = Stage175ICombatTestContentPack.loadDoctrines();
        ammunitionCatalog = Stage175ICombatTestWeaponPack.loadAmmunition();
        launcherCatalog = Stage175ICombatTestWeaponPack.loadLaunchers();
        calculator = new DerivedShipCalculator(engineeringCatalog);
        guidedAdapter = new ShipGuidedWeaponEngineeringAdapter();
        guidanceRuntime = new GuidanceRuntime();
        ammunitionRuntime = new AmmunitionRuntime();
        weaponMountRuntime = new WeaponMountRuntime();
        for (CombatantRuntime combatant : battleState().combatants()) {
            guidedLaunchesBySourceEntityId.put(combatant.spec().entityId(), 0L);
        }
    }

    /**
     * Advances one complete shared battle tick including guided launch, guidance and propagation.
     *
     * <p>The wrapped runtime first advances the one authoritative combat clock and all ship-local
     * production systems. Guided launch requests then consume that same tick's actor-bounded tactical
     * authorization and already-advanced launcher continuity. Finally every active guided body gets
     * at most one production guidance burn and one ballistic propagation step.</p>
     */
    public void advanceOneTick() {
        weaponRuntime.advanceOneTick();
        launchAllAuthorizedGuidedWeapons();
        guideAndAdvanceBodies();
    }

    /** @return authoritative shared battle tick */
    public long tick() {
        return weaponRuntime.tick();
    }

    /** @return authoritative shared battle time in seconds */
    public double elapsedSeconds() {
        return weaponRuntime.elapsedSeconds();
    }

    /** @return wrapped production kinetic/control runtime */
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
        List<GuidedBodyFingerprint> bodies = guidedBodies.stream()
                .map(body -> new GuidedBodyFingerprint(
                        body.bodyId(),
                        body.sourceEntityId(),
                        body.targetId(),
                        launchMountByBodyId.get(body.bodyId()),
                        body.xM(),
                        body.yM(),
                        body.velocityXMps(),
                        body.velocityYMps(),
                        body.remainingPropellantKg(),
                        body.seekerAvailable(),
                        body.guidanceAvailable()))
                .toList();
        return new BattleOrdnanceFingerprint(
                tick(),
                weaponRuntime.fingerprint(),
                sources,
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
            nextGuidedBodyId = Math.addExact(nextGuidedBodyId, 1L);
            guidedLaunchesBySourceEntityId.compute(
                    shooter.spec().entityId(),
                    (ignored, count) -> Math.addExact(Objects.requireNonNull(count, "guided launch count"), 1L));
        }
    }

    private void guideAndAdvanceBodies() {
        for (int index = 0; index < guidedBodies.size(); index++) {
            GuidedWeaponBody body = guidedBodies.get(index);
            TrackState track = visibleTrackForSource(body.sourceEntityId(), body.targetId());
            GuidedWeaponBody guided = body;
            if (track != null) {
                GuidanceRuntime.GuidanceCommand command = guidanceRuntime.planLeadPursuit(
                        body,
                        track,
                        new TargetMotionEstimate(0d, 0d, 0d, 0d),
                        TrackSource.DATALINK,
                        TICK_SECONDS);
                if (command.allowed()) {
                    guided = guidanceRuntime.execute(body, command);
                }
            }
            guidedBodies.set(index, guided.advanceBallistic(TICK_SECONDS));
        }
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

    private static long guidedAmmunitionRounds(ShipEngineeringState.ConsumableState state) {
        return state.interfaceLoads().stream()
                .filter(value -> value.kind() == ShipEngineeringCatalog.InterfaceKind.AMMUNITION)
                .filter(value -> "guided_feed".equals(value.interfaceId()))
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
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
     * Equality-friendly projection of one active guided physical body.
     *
     * @param bodyId stable simulation-local guided body identity
     * @param sourceEntityId launching combatant identity
     * @param targetId current target hypothesis identity
     * @param launchMountId physical launcher mount that owns the support channel
     * @param xM current x position
     * @param yM current y position
     * @param velocityXMps current x velocity
     * @param velocityYMps current y velocity
     * @param remainingPropellantKg current physical propellant mass
     * @param seekerAvailable current seeker availability
     * @param guidanceAvailable current guidance availability
     */
    public record GuidedBodyFingerprint(
            long bodyId,
            long sourceEntityId,
            long targetId,
            String launchMountId,
            double xM,
            double yM,
            double velocityXMps,
            double velocityYMps,
            double remainingPropellantKg,
            boolean seekerAvailable,
            boolean guidanceAvailable) {
        /**
         * Validates one guided body projection.
         *
         * @param bodyId stable simulation-local guided body identity
         * @param sourceEntityId launching combatant identity
         * @param targetId current target hypothesis identity
         * @param launchMountId physical launcher mount that owns the support channel
         * @param xM current x position
         * @param yM current y position
         * @param velocityXMps current x velocity
         * @param velocityYMps current y velocity
         * @param remainingPropellantKg current physical propellant mass
         * @param seekerAvailable current seeker availability
         * @param guidanceAvailable current guidance availability
         */
        public GuidedBodyFingerprint {
            if (bodyId <= 0L || sourceEntityId <= 0L || targetId <= 0L) {
                throw new IllegalArgumentException("guided body identities must be positive");
            }
            if (launchMountId == null || launchMountId.isBlank()) {
                throw new IllegalArgumentException("launchMountId must be non-blank");
            }
            if (!Double.isFinite(xM) || !Double.isFinite(yM)
                    || !Double.isFinite(velocityXMps) || !Double.isFinite(velocityYMps)
                    || !Double.isFinite(remainingPropellantKg) || remainingPropellantKg < 0d) {
                throw new IllegalArgumentException("guided body physical projection must be finite");
            }
        }
    }

    /**
     * Whole-battle deterministic guided-ordnance fingerprint.
     *
     * @param tick authoritative shared battle tick
     * @param weaponFingerprint wrapped kinetic/control/protection fingerprint
     * @param sources canonical per-source guided ammunition projections
     * @param bodies current active guided physical bodies
     */
    public record BattleOrdnanceFingerprint(
            long tick,
            LiveTacticalBattleWeaponRuntime.BattleWeaponFingerprint weaponFingerprint,
            List<SourceGuidedFingerprint> sources,
            List<GuidedBodyFingerprint> bodies) {
        /**
         * Validates and freezes one whole-battle guided projection.
         *
         * @param tick authoritative shared battle tick
         * @param weaponFingerprint wrapped kinetic/control/protection fingerprint
         * @param sources canonical per-source guided ammunition projections
         * @param bodies current active guided physical bodies
         */
        public BattleOrdnanceFingerprint {
            if (tick < 0L) {
                throw new IllegalArgumentException("tick must be non-negative");
            }
            Objects.requireNonNull(weaponFingerprint, "weaponFingerprint");
            sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
            bodies = List.copyOf(Objects.requireNonNull(bodies, "bodies"));
        }
    }
}
