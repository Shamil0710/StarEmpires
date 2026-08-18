package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.flight.FlightDynamics;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.CombatantRuntime;
import com.spacesim.ship.LiveTacticalBattleScenario.CombatantSpec;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;
import com.spacesim.ship.ObservedThreatAssessmentService.ContactDisposition;
import com.spacesim.ship.ObservedThreatAssessmentService.ObservedContact;
import com.spacesim.ship.ObservedTacticalIntentPlanner.TacticalContext;
import com.spacesim.ship.ObservedTacticalIntentPlanner.TacticalIntent;
import com.spacesim.ship.ObservedTacticalIntentPlanner.TacticalPosture;
import com.spacesim.ship.SensorDefinition.Mode;
import com.spacesim.ship.ShipEngineeringRuntime.OperatingCommand;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipSensorEngineeringAdapter.FittedSensor;
import com.spacesim.ship.ShipSensorRuntime.Position2d;
import com.spacesim.ship.ShipSensorRuntime.TrackQualityPolicy;
import com.spacesim.ship.TacticalFormationPlanner.Command;
import com.spacesim.ship.TacticalFormationPlanner.Objective;
import com.spacesim.ship.TacticalSurvivalPlanner.DecisionReason;
import com.spacesim.ship.TacticalSurvivalPlanner.OwnReadiness;
import com.spacesim.ship.TacticalSurvivalPlanner.SafePoint;
import com.spacesim.ship.TacticalSurvivalPlanner.SurvivalAction;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Shared Stage-19I production control coordinator for one materialized tactical battle.
 *
 * <p>This class is deliberately not a second combat engine. It coordinates production observation,
 * track fusion, Stage-19 tactical/survival/formation policy, Stage-17.5 engineering runtime and the
 * shared {@link FlightDynamics} integrator for every combatant in one canonical fixed tick.
 * Projectile/guided-body, point-defense, EW and damage resolution remain owned by the existing live
 * combat stack and are not reimplemented here.</p>
 *
 * <p>All observers sense before any actor plans, and all actors plan before any physical movement is
 * applied. Tactical planners receive only actor-bounded hostile {@link ObservedContact} state. An
 * optional formation objective uses only the actor's own kinematics plus authored own-side slot
 * geometry; an optional battle objective contributes only own-side mission intent/safe-point geometry.
 * Neither objective reads hidden hostile transforms or mutates physical state directly.</p>
 */
public final class LiveTacticalBattleControlRuntime {
    /** Fixed control/flight interval shared with the existing live tactical session. */
    public static final double TICK_SECONDS = LiveTacticalSimulationSession.TICK_SECONDS;

    private static final double SENSOR_INTERVAL_SECONDS = 0.20d;
    private static final long SENSOR_INTERVAL_TICKS = Math.round(SENSOR_INTERVAL_SECONDS / TICK_SECONDS);
    private static final double TACTICAL_REFERENCE_RANGE_M = 5_000d;
    private static final double TRACK_FRESHNESS_REFERENCE_SECONDS = 3d;
    private static final float LIVE_COMMAND_SPEED_CAP_MPS = 500f;
    private static final int MAX_TRACK_MEASUREMENTS = 8;
    private static final double EPSILON = 1e-9d;
    private static final TacticalSurvivalPlanner.Policy SURVIVAL_POLICY =
            new TacticalSurvivalPlanner.Policy(
                    0.15d,
                    0.15d,
                    EPSILON,
                    EPSILON,
                    EPSILON,
                    2d);

    private final LiveTacticalBattleRuntimeState battleState;
    private final ShipEngineeringCatalog engineeringCatalog;
    private final DerivedShipCalculator calculator;
    private final ShipEngineeringRuntime engineeringRuntime;
    private final ShipEngineeringGrantService grantService;
    private final ShipObservationEngineeringService observationService;
    private final ShipSensorRuntime sensorRuntime;
    private final ShipSensorEngineeringAdapter sensorAdapter;
    private final ObservedTacticalIntentPlanner tacticalPlanner;
    private final TacticalSurvivalPlanner survivalPlanner;
    private final TacticalFormationPlanner formationPlanner;
    private final TreeMap<Long, TreeMap<Long, List<SensorMeasurement>>> measurementsByObserver = new TreeMap<>();
    private final TreeMap<Long, ActorControlState> controlByEntityId = new TreeMap<>();
    private final TreeMap<Long, FormationSlot> formationSlotByEntityId = new TreeMap<>();
    private final TreeMap<Long, Command> formationByEntityId = new TreeMap<>();
    private final EnumMap<Side, TacticalBattleObjective> battleObjectiveBySide = new EnumMap<>(Side.class);

    private long tick;

    /**
     * Creates the shared coordinator with no authored formation or withdrawal objective.
     *
     * @param battleState authoritative battle-local physical state
     */
    public LiveTacticalBattleControlRuntime(LiveTacticalBattleRuntimeState battleState) {
        this(battleState, Map.of(), Map.of());
    }

    /**
     * Creates the shared coordinator with optional explicit side formation objectives.
     *
     * <p>Formation objectives contain physical scenario geometry only. They do not alter mass, thrust,
     * weapon performance, sensors or any doctrine statistic.</p>
     *
     * @param battleState authoritative battle-local physical state
     * @param formationObjectives optional authored formation objective by battle side
     */
    public LiveTacticalBattleControlRuntime(
            LiveTacticalBattleRuntimeState battleState,
            Map<Side, Objective> formationObjectives) {
        this(battleState, formationObjectives, Map.of());
    }

    /**
     * Creates the shared coordinator with optional formation and mission-level battle objectives.
     *
     * <p>Battle objectives carry only mission intent and own-side safe-point geometry. A withdrawal
     * objective is routed through the existing {@link TacticalSurvivalPlanner} and therefore cannot
     * manufacture thrust, reaction mass or target information.</p>
     *
     * @param battleState authoritative battle-local physical state
     * @param formationObjectives optional authored formation objective by battle side
     * @param battleObjectives optional authored mission objective by battle side
     */
    public LiveTacticalBattleControlRuntime(
            LiveTacticalBattleRuntimeState battleState,
            Map<Side, Objective> formationObjectives,
            Map<Side, TacticalBattleObjective> battleObjectives) {
        this.battleState = Objects.requireNonNull(battleState, "battleState");
        Objects.requireNonNull(formationObjectives, "formationObjectives");
        Objects.requireNonNull(battleObjectives, "battleObjectives");
        engineeringCatalog = Stage175ICombatTestContentPack.loadDoctrines();
        calculator = new DerivedShipCalculator(engineeringCatalog);
        engineeringRuntime = new ShipEngineeringRuntime(engineeringCatalog);
        grantService = new ShipEngineeringGrantService(engineeringCatalog);
        observationService = new ShipObservationEngineeringService(engineeringCatalog);
        sensorRuntime = new ShipSensorRuntime();
        sensorAdapter = new ShipSensorEngineeringAdapter();
        tacticalPlanner = new ObservedTacticalIntentPlanner();
        survivalPlanner = new TacticalSurvivalPlanner();
        formationPlanner = new TacticalFormationPlanner();

        EnumMap<Side, Objective> checkedFormationObjectives = new EnumMap<>(Side.class);
        formationObjectives.forEach((side, objective) -> checkedFormationObjectives.put(
                Objects.requireNonNull(side, "formation objective side"),
                Objects.requireNonNull(objective, "formation objective")));
        for (Side side : Side.values()) {
            battleObjectiveBySide.put(side, TacticalBattleObjective.engage());
        }
        battleObjectives.forEach((side, objective) -> battleObjectiveBySide.put(
                Objects.requireNonNull(side, "battle objective side"),
                Objects.requireNonNull(objective, "battle objective")));

        for (CombatantRuntime combatant : battleState.combatants()) {
            long entityId = combatant.spec().entityId();
            measurementsByObserver.put(entityId, new TreeMap<>());
            controlByEntityId.put(entityId, ActorControlState.initial());
            formationByEntityId.put(entityId, Command.none());
        }
        for (Side side : Side.values()) {
            Objective objective = checkedFormationObjectives.get(side);
            if (objective == null) {
                continue;
            }
            List<CombatantSpec> sideRoster = battleState.scenario().combatantsFor(side);
            for (int index = 0; index < sideRoster.size(); index++) {
                formationSlotByEntityId.put(
                        sideRoster.get(index).entityId(),
                        new FormationSlot(objective, index, sideRoster.size()));
            }
        }
    }

    /**
     * Advances one canonical shared tactical control/flight tick for every combatant.
     *
     * <p>Sensing is resolved from start-of-tick physical geometry. Planning then consumes only the
     * resulting actor-local tracks. Finally all engineering/flight commands are applied. Presentation
     * calls cannot advance this clock.</p>
     */
    public void advanceOneTick() {
        tick++;
        if (tick == 1L || tick % SENSOR_INTERVAL_TICKS == 0L) {
            scanAllObservers();
        }
        planAllActors();
        moveAllActors();
    }

    /** @return authoritative shared fixed tick */
    public long tick() {
        return tick;
    }

    /** @return authoritative simulation time in seconds */
    public double elapsedSeconds() {
        return tick * TICK_SECONDS;
    }

    /** @return materialized battle state driven by this coordinator */
    public LiveTacticalBattleRuntimeState battleState() {
        return battleState;
    }

    /**
     * Returns the authored mission objective for one side.
     *
     * @param side battle side
     * @return canonical engagement or explicit withdrawal objective
     */
    public TacticalBattleObjective battleObjective(Side side) {
        return battleObjectiveBySide.get(Objects.requireNonNull(side, "side"));
    }

    /**
     * Returns the latest production policy output.
     *
     * @param entityId stable combatant identity
     * @return current control state
     */
    public ActorControlState controlState(long entityId) {
        battleState.requireCombatant(entityId);
        return controlByEntityId.get(entityId);
    }

    /**
     * Returns the latest read-only formation policy output for one combatant.
     *
     * @param entityId stable combatant identity
     * @return current formation command, or canonical no-objective state
     */
    public Command formationState(long entityId) {
        battleState.requireCombatant(entityId);
        return formationByEntityId.get(entityId);
    }

    /**
     * Returns a deterministic equality-friendly projection for scaled acceptance tests.
     *
     * @return canonical per-combatant physical/control fingerprint
     */
    public BattleControlFingerprint fingerprint() {
        List<CombatantControlFingerprint> combatants = battleState.combatants().stream()
                .map(combatant -> {
                    long entityId = combatant.spec().entityId();
                    ActorControlState control = controlByEntityId.get(entityId);
                    return new CombatantControlFingerprint(
                            entityId,
                            combatant.transform().position.x,
                            combatant.transform().position.y,
                            combatant.transform().velocity.x,
                            combatant.transform().velocity.y,
                            reactionMassKg(combatant.engineering().runtimeState.consumables()),
                            battleState.visibleContacts(entityId).stream()
                                    .map(value -> value.track().targetId())
                                    .toList(),
                            control.intent().targetId(),
                            control.intent().fireRequested(),
                            control.fireAuthorized(),
                            control.survivalDecision().action());
                })
                .toList();
        return new BattleControlFingerprint(tick, combatants);
    }

    /**
     * Returns deterministic formation diagnostics without mutating authority.
     *
     * @return canonical formation state ordered by stable entity identity
     */
    public BattleFormationFingerprint formationFingerprint() {
        List<CombatantFormationFingerprint> combatants = battleState.combatants().stream()
                .map(value -> new CombatantFormationFingerprint(
                        value.spec().entityId(),
                        formationByEntityId.get(value.spec().entityId())))
                .toList();
        return new BattleFormationFingerprint(tick, combatants);
    }

    private void scanAllObservers() {
        for (CombatantRuntime observer : battleState.combatants()) {
            scanObserver(observer);
        }
    }

    private void scanObserver(CombatantRuntime observer) {
        DerivedShipState observerDerived = derive(observer);
        FittedSensor radar = sensorAdapter.derive(observerDerived).sensors().stream()
                .filter(value -> value.definition().mode() == Mode.ACTIVE_RADAR)
                .findFirst()
                .orElse(null);
        TreeMap<Long, List<SensorMeasurement>> history = measurementsByObserver.get(observer.spec().entityId());

        if (radar != null) {
            var budget = grantService.beginInterval(observer.engineering(), SENSOR_INTERVAL_SECONDS);
            for (CombatantRuntime target : battleState.combatants()) {
                if (target.spec().side() == observer.spec().side()) {
                    continue;
                }
                DerivedShipState targetDerived = derive(target);
                var targetSignature = sensorAdapter.derive(targetDerived).staticSignature();
                var observation = observationService.observe(
                        observer.engineering(),
                        radar,
                        SensorRuntimeState.nominal(),
                        SENSOR_INTERVAL_SECONDS,
                        observer.spec().entityId(),
                        target.spec().entityId(),
                        new Position2d(observer.transform().position.x, observer.transform().position.y),
                        new Position2d(target.transform().position.x, target.transform().position.y),
                        targetSignature,
                        ElectronicWarfareState.empty(),
                        elapsedSeconds(),
                        budget);
                observation.measurement().ifPresent(measurement -> appendMeasurement(
                        history,
                        target.spec().entityId(),
                        measurement));
            }
        }

        DatalinkState localLink = DatalinkState.local();
        TrackQualityPolicy qualityPolicy = TrackQualityPolicy.defaultPolicy();
        double nowSeconds = elapsedSeconds();
        List<ObservedContact> contacts = new ArrayList<>();
        for (Map.Entry<Long, List<SensorMeasurement>> entry : history.entrySet()) {
            if (!hasDeliveredFreshMeasurement(entry.getKey(), entry.getValue(), localLink, nowSeconds)) {
                continue;
            }
            TrackState track = sensorRuntime.fuse(
                    entry.getKey(),
                    entry.getValue(),
                    localLink,
                    qualityPolicy,
                    nowSeconds);
            contacts.add(new ObservedContact(track, ContactDisposition.HOSTILE));
        }
        battleState.replaceVisibleContacts(observer.spec().entityId(), contacts);
    }

    private void planAllActors() {
        for (CombatantRuntime combatant : battleState.combatants()) {
            long entityId = combatant.spec().entityId();
            List<ObservedContact> contacts = battleState.visibleContacts(entityId);
            TacticalIntent intent = tacticalPlanner.plan(
                    contacts,
                    new TacticalContext(
                            TacticalPosture.INTERCEPT,
                            combatant.transform().position.x,
                            combatant.transform().position.y,
                            false,
                            0d,
                            0d,
                            0d,
                            elapsedSeconds(),
                            TACTICAL_REFERENCE_RANGE_M,
                            TRACK_FRESHNESS_REFERENCE_SECONDS));

            DerivedShipState derived = derive(combatant);
            ShipDamageRuntime.Snapshot damage = combatant.engineering().instanceState.damage();
            OwnReadiness readiness = new OwnReadiness(
                    meanIntegrity(combatant.hull(), damage),
                    minimumModuleIntegrity(combatant.engineering().fit, damage.moduleDamage()),
                    reactionMassKg(combatant.engineering().runtimeState.consumables()),
                    derived.deltaVMps(),
                    derived.accelerationMps2(),
                    finiteAmmunitionDependent(combatant, damage.moduleDamage()),
                    ammunitionCount(combatant.engineering().runtimeState.consumables()));
            TacticalBattleObjective battleObjective = battleObjectiveBySide.get(combatant.spec().side());
            SafePoint retreatPoint = battleObjective.withdrawalPoint().known()
                    ? battleObjective.withdrawalPoint()
                    : new SafePoint(true, combatant.spec().xM(), combatant.spec().yM());
            TacticalSurvivalPlanner.Decision survival = survivalPlanner.decide(
                    readiness,
                    SURVIVAL_POLICY,
                    contacts,
                    combatant.transform().position.x,
                    combatant.transform().position.y,
                    retreatPoint,
                    battleObjective.survivalDirective(),
                    false,
                    elapsedSeconds(),
                    TACTICAL_REFERENCE_RANGE_M,
                    TRACK_FRESHNESS_REFERENCE_SECONDS);

            FormationSlot slot = formationSlotByEntityId.get(entityId);
            Command formation = slot == null
                    ? Command.none()
                    : formationPlanner.plan(
                            slot.objective(),
                            slot.slotIndex(),
                            slot.slotCount(),
                            combatant.transform().position.y,
                            combatant.transform().velocity.y,
                            derived.accelerationMps2(),
                            survival.action() == SurvivalAction.CONTINUE);
            formationByEntityId.put(entityId, formation);

            double axisX = intent.movementAxisX();
            double axisY = intent.movementAxisY();
            boolean fireAuthorized = intent.fireRequested();
            switch (survival.action()) {
                case RETREAT, PURSUE -> {
                    axisX = survival.movementAxisX();
                    axisY = survival.movementAxisY();
                    if (survival.action() == SurvivalAction.RETREAT) {
                        fireAuthorized = false;
                    }
                }
                case DISENGAGE -> {
                    axisX = 0d;
                    axisY = 0d;
                    fireAuthorized = false;
                }
                case CONTINUE -> {
                    if (formation.objectiveKnown()) {
                        double[] combined = normalizedAxes(axisX, formation.correctionAxisY());
                        axisX = combined[0];
                        axisY = combined[1];
                    }
                }
            }
            controlByEntityId.put(entityId, new ActorControlState(
                    intent,
                    survival,
                    fireAuthorized,
                    axisX,
                    axisY));
        }
    }

    private void moveAllActors() {
        for (CombatantRuntime combatant : battleState.combatants()) {
            ActorControlState control = controlByEntityId.get(combatant.spec().entityId());
            EngineeringComponent engineering = combatant.engineering();
            boolean maneuverRequested = control.movementAxisX() * control.movementAxisX()
                    + control.movementAxisY() * control.movementAxisY() > EPSILON;
            boolean brakingRequested = combatant.transform().velocity.len2() > 1e-8f && !maneuverRequested;
            double throttle = maneuverRequested || brakingRequested ? 1d : 0d;
            OperatingCommand command = new OperatingCommand(
                    driveThrottleByMount(combatant, throttle),
                    Map.of(),
                    Set.of());
            var result = engineeringRuntime.advance(
                    engineering.fit,
                    engineering.runtimeState,
                    engineering.instanceState.damage().moduleDamage(),
                    command,
                    TICK_SECONDS);
            engineering.setRuntimeState(result.state());
            FlightDynamics.advancePhysical(
                    combatant.transform(),
                    result.derivedState().totalMassKg(),
                    result.actualThrustN(),
                    LIVE_COMMAND_SPEED_CAP_MPS,
                    (float) control.movementAxisX(),
                    (float) control.movementAxisY(),
                    (float) TICK_SECONDS);
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

    private Map<String, Double> driveThrottleByMount(CombatantRuntime combatant, double throttle) {
        if (throttle <= 0d) {
            return Map.of();
        }
        EngineeringComponent engineering = combatant.engineering();
        Set<String> loadedReactionMassMounts = engineering.runtimeState.consumables().interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.REACTION_MASS)
                .filter(value -> value.massKg() > EPSILON)
                .map(ShipEngineeringState.ConsumableLoad::mountId)
                .collect(java.util.stream.Collectors.toSet());
        TreeMap<String, Double> result = new TreeMap<>();
        for (ShipEngineeringCatalog.InstalledModuleDefinition installed : engineering.fit.installedModules()) {
            var module = engineeringCatalog.findModule(installed.moduleId());
            if (module != null
                    && (module.family() == ModuleFamily.MAIN_DRIVE
                    || module.family() == ModuleFamily.MANEUVER_THRUSTERS)
                    && loadedReactionMassMounts.contains(installed.mountId())) {
                result.put(installed.mountId(), throttle);
            }
        }
        return Map.copyOf(result);
    }

    private boolean finiteAmmunitionDependent(CombatantRuntime combatant, DamageState damage) {
        boolean hasOperationalAmmoWeapon = false;
        boolean hasOperationalNonAmmoWeapon = false;
        for (ShipEngineeringCatalog.InstalledModuleDefinition installed : combatant.engineering().fit.installedModules()) {
            ShipEngineeringCatalog.ModuleDefinition module = engineeringCatalog.findModule(installed.moduleId());
            if (module == null
                    || module.family() != ModuleFamily.WEAPON_AMMUNITION
                    || damage.moduleIntegrityByMount().getOrDefault(installed.mountId(), 1d) <= EPSILON) {
                continue;
            }
            boolean requiresAmmunition = module.interfaces().stream()
                    .anyMatch(value -> value.kind() == InterfaceKind.AMMUNITION);
            if (requiresAmmunition) {
                hasOperationalAmmoWeapon = true;
            } else {
                hasOperationalNonAmmoWeapon = true;
            }
        }
        return hasOperationalAmmoWeapon && !hasOperationalNonAmmoWeapon;
    }

    private static void appendMeasurement(
            TreeMap<Long, List<SensorMeasurement>> history,
            long targetId,
            SensorMeasurement measurement) {
        ArrayList<SensorMeasurement> values = new ArrayList<>(history.getOrDefault(targetId, List.of()));
        values.add(Objects.requireNonNull(measurement, "measurement"));
        while (values.size() > MAX_TRACK_MEASUREMENTS) {
            values.remove(0);
        }
        history.put(targetId, List.copyOf(values));
    }

    private static boolean hasDeliveredFreshMeasurement(
            long targetId,
            List<SensorMeasurement> measurements,
            DatalinkState link,
            double nowSeconds) {
        return measurements.stream()
                .filter(Objects::nonNull)
                .anyMatch(value -> value.targetId() == targetId
                        && value.timestampSeconds() + link.latencySeconds() <= nowSeconds
                        && nowSeconds - value.timestampSeconds() <= link.maxMeasurementAgeSeconds());
    }

    private static long ammunitionCount(ShipEngineeringState.ConsumableState state) {
        return state.interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }

    private static double reactionMassKg(ShipEngineeringState.ConsumableState state) {
        return state.interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.REACTION_MASS)
                .mapToDouble(ShipEngineeringState.ConsumableLoad::massKg)
                .sum();
    }

    private static double meanIntegrity(
            ShipEngineeringCatalog.HullDefinition hull,
            ShipDamageRuntime.Snapshot damage) {
        return hull.compartments().stream()
                .mapToDouble(value -> damage.compartmentIntegrityById().getOrDefault(value.id(), 1d))
                .average()
                .orElse(1d);
    }

    private static double minimumModuleIntegrity(
            ShipEngineeringState.InstalledFit fit,
            DamageState damage) {
        return fit.installedModules().stream()
                .mapToDouble(value -> damage.moduleIntegrityByMount().getOrDefault(value.mountId(), 1d))
                .min()
                .orElse(1d);
    }

    private static double[] normalizedAxes(double x, double y) {
        double length = Math.hypot(x, y);
        if (length <= 1d) {
            return new double[]{x, y};
        }
        return new double[]{x / length, y / length};
    }

    /**
     * Latest production policy output for one actor before physical execution.
     *
     * @param intent actor-bounded tactical intent
     * @param survivalDecision survival-policy decision for the current tick
     * @param fireAuthorized whether survival policy permits the tactical fire request
     * @param movementAxisX final normalized x maneuver command
     * @param movementAxisY final normalized y maneuver command
     */
    public record ActorControlState(
            TacticalIntent intent,
            TacticalSurvivalPlanner.Decision survivalDecision,
            boolean fireAuthorized,
            double movementAxisX,
            double movementAxisY) {
        /**
         * Validates immutable actor control output.
         *
         * @param intent actor-bounded tactical intent
         * @param survivalDecision survival-policy decision for the current tick
         * @param fireAuthorized whether survival policy permits the tactical fire request
         * @param movementAxisX final normalized x maneuver command
         * @param movementAxisY final normalized y maneuver command
         */
        public ActorControlState {
            Objects.requireNonNull(intent, "intent");
            Objects.requireNonNull(survivalDecision, "survivalDecision");
            if (!Double.isFinite(movementAxisX) || !Double.isFinite(movementAxisY)
                    || movementAxisX * movementAxisX + movementAxisY * movementAxisY > 1d + 1e-12d) {
                throw new IllegalArgumentException("control movement must be finite and normalized");
            }
        }

        private static ActorControlState initial() {
            return new ActorControlState(
                    TacticalIntent.noTarget(TacticalPosture.INTERCEPT),
                    new TacticalSurvivalPlanner.Decision(
                            SurvivalAction.CONTINUE,
                            DecisionReason.READY,
                            false,
                            0L,
                            0d,
                            0d),
                    false,
                    0d,
                    0d);
        }
    }

    /**
     * Deterministic per-combatant control/physical projection.
     *
     * @param entityId stable combatant identity
     * @param xM current physical x position
     * @param yM current physical y position
     * @param velocityXMps current physical x velocity
     * @param velocityYMps current physical y velocity
     * @param reactionMassKg current physical reaction mass
     * @param visibleTargetIds actor-visible target identities
     * @param selectedTargetId actor-selected target identity or zero
     * @param fireRequested tactical fire request
     * @param fireAuthorized survival-filtered fire authorization
     * @param survivalAction current survival action
     */
    public record CombatantControlFingerprint(
            long entityId,
            double xM,
            double yM,
            double velocityXMps,
            double velocityYMps,
            double reactionMassKg,
            List<Long> visibleTargetIds,
            long selectedTargetId,
            boolean fireRequested,
            boolean fireAuthorized,
            SurvivalAction survivalAction) {
        /**
         * Freezes and validates one combatant fingerprint.
         *
         * @param entityId stable combatant identity
         * @param xM current physical x position
         * @param yM current physical y position
         * @param velocityXMps current x velocity
         * @param velocityYMps current y velocity
         * @param reactionMassKg current physical reaction mass
         * @param visibleTargetIds actor-visible target identities
         * @param selectedTargetId actor-selected target identity or zero
         * @param fireRequested tactical fire request
         * @param fireAuthorized survival-filtered fire authorization
         * @param survivalAction current survival action
         */
        public CombatantControlFingerprint {
            visibleTargetIds = List.copyOf(Objects.requireNonNull(visibleTargetIds, "visibleTargetIds"));
            Objects.requireNonNull(survivalAction, "survivalAction");
        }
    }

    /**
     * Equality-friendly whole-battle control fingerprint.
     *
     * @param tick authoritative shared tactical tick
     * @param combatants canonical stable-entity control projections
     */
    public record BattleControlFingerprint(long tick, List<CombatantControlFingerprint> combatants) {
        /**
         * Freezes and validates the whole-battle fingerprint.
         *
         * @param tick authoritative shared tactical tick
         * @param combatants canonical stable-entity control projections
         */
        public BattleControlFingerprint {
            if (tick < 0L) {
                throw new IllegalArgumentException("tick must be non-negative");
            }
            combatants = List.copyOf(Objects.requireNonNull(combatants, "combatants"));
        }
    }

    /**
     * Deterministic per-combatant formation projection.
     *
     * @param entityId stable combatant identity
     * @param command current actor-local formation command
     */
    public record CombatantFormationFingerprint(long entityId, Command command) {
        /**
         * Validates one immutable formation fingerprint.
         *
         * @param entityId stable combatant identity
         * @param command current actor-local formation command
         */
        public CombatantFormationFingerprint {
            if (entityId <= 0L) {
                throw new IllegalArgumentException("entityId must be positive");
            }
            Objects.requireNonNull(command, "command");
        }
    }

    /**
     * Equality-friendly whole-battle formation fingerprint.
     *
     * @param tick authoritative shared tactical tick
     * @param combatants canonical stable-entity formation projections
     */
    public record BattleFormationFingerprint(long tick, List<CombatantFormationFingerprint> combatants) {
        /**
         * Freezes and validates the whole-battle formation fingerprint.
         *
         * @param tick authoritative shared tactical tick
         * @param combatants canonical stable-entity formation projections
         */
        public BattleFormationFingerprint {
            if (tick < 0L) {
                throw new IllegalArgumentException("tick must be non-negative");
            }
            combatants = List.copyOf(Objects.requireNonNull(combatants, "combatants"));
        }
    }

    private record FormationSlot(Objective objective, int slotIndex, int slotCount) {
        private FormationSlot {
            Objects.requireNonNull(objective, "objective");
            if (slotCount <= 0 || slotIndex < 0 || slotIndex >= slotCount) {
                throw new IllegalArgumentException("invalid formation slot");
            }
        }
    }
}
