package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.weapon.Stage175ICombatTestWeaponPack;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.ship.ElectronicWarfareState.NoiseJammer;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.CombatantRuntime;
import com.spacesim.ship.SensorDefinition.Mode;
import com.spacesim.ship.ShipObservationService.OperationPlan;
import com.spacesim.ship.ShipSensorEngineeringAdapter.FittedSensor;
import com.spacesim.ship.ShipSensorRuntime.Position2d;
import com.spacesim.ship.ShipSensorRuntime.TrackQualityPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Actor-bounded Stage-19I guided-ordnance observation over ordinary fitted production radar.
 *
 * <p>This runtime advances no combat clock and owns no physical body state. It projects active hostile
 * strike and, when supplied, physical decoy bodies through current fitted/damaged ACTIVE_RADAR
 * hardware, the common engineering power/heat grant layer, {@link ShipObservationService} signal
 * equations and {@link ShipSensorRuntime} fusion. Consumers receive only observer-local
 * {@link TrackState} plus a velocity estimate derived from successive observer-local Cartesian track
 * solutions. The observation result deliberately exposes no hidden STRIKE/DECOY role.</p>
 *
 * <p>Ordnance radar work is phase-shifted from the shared ship-target radar scan: ship scans occur on
 * tick 1 and multiples of four, while this runtime scans on ticks congruent to 2 modulo four. Each
 * ordnance scan represents one 0.05 s radar operation. A fitted radar receives one physical
 * engineering grant for that operation and the emitted search operation is then evaluated against
 * every current hostile guided body. This avoids both a free simultaneous second radar operation and
 * transmitter-power multiplication per target.</p>
 *
 * <p>Noise jammers are projected from the same damage-aware fitted engineering state as all other
 * capabilities and propagate through the ordinary sensor solver. If external in-band interference is
 * present the receiver requests ECCM processing; its incremental power and heat are part of the same
 * physical radar-operation grant. If the ECCM request cannot be funded, the receiver may fall back to
 * the ordinary non-ECCM radar operation rather than receiving free processing.</p>
 *
 * <p>Velocity continuity is explicitly actor-bounded. When ordinary track aging degrades a hypothesis
 * below TRACKED, the last Cartesian velocity baseline is discarded. A later reacquisition therefore
 * needs two temporally distinct new observer-local Cartesian solutions before velocity becomes known
 * again; one fresh measurement cannot resurrect stale kinematics from a previously lost track.</p>
 */
public final class LiveTacticalOrdnanceObservationRuntime {
    private static final long SCAN_PERIOD_TICKS = 4L;
    private static final long SCAN_PHASE_TICK = 2L;
    private static final double OPERATION_SECONDS = LiveTacticalBattleControlRuntime.TICK_SECONDS;
    private static final int MAX_MEASUREMENTS_PER_TARGET = 12;
    private static final double EPSILON = 1e-9d;

    private final LiveTacticalBattleOrdnanceRuntime ordnanceRuntime;
    private final LiveTacticalBattleDecoyRuntime decoyRuntime;
    private final ShipEngineeringCatalog engineeringCatalog;
    private final WeaponAmmunitionCatalog ammunitionCatalog;
    private final DerivedShipCalculator calculator;
    private final ShipSensorEngineeringAdapter sensorAdapter;
    private final ShipElectronicWarfareEngineeringAdapter ewAdapter;
    private final ShipObservationService observationService;
    private final ShipEngineeringGrantService grantService;
    private final ShipSensorRuntime sensorRuntime;
    private final TrackQualityPolicy trackPolicy;
    private final TreeMap<Long, TreeMap<Long, List<SensorMeasurement>>> measurementsByObserver = new TreeMap<>();
    private final TreeMap<Long, TreeMap<Long, ObservedOrdnanceTrack>> tracksByObserver = new TreeMap<>();
    private final TreeMap<Long, TreeMap<Long, PositionSample>> latestSamplesByObserver = new TreeMap<>();
    private final TreeMap<Long, ScanDiagnostics> lastScanByObserver = new TreeMap<>();

    private long lastObservedTick = -1L;

    /**
     * Creates actor-bounded observation of offensive guided bodies only.
     *
     * @param ordnanceRuntime physical guided-ordnance runtime; not advanced by this observer
     */
    public LiveTacticalOrdnanceObservationRuntime(LiveTacticalBattleOrdnanceRuntime ordnanceRuntime) {
        this(ordnanceRuntime, null);
    }

    /**
     * Creates actor-bounded observation of offensive guided bodies plus physical decoys.
     *
     * @param ordnanceRuntime physical guided-ordnance runtime; not advanced by this observer
     * @param decoyRuntime physical decoy source sharing the same authoritative ordnance runtime
     */
    public LiveTacticalOrdnanceObservationRuntime(
            LiveTacticalBattleOrdnanceRuntime ordnanceRuntime,
            LiveTacticalBattleDecoyRuntime decoyRuntime) {
        this(ordnanceRuntime, decoyRuntime, TrackQualityPolicy.defaultPolicy());
    }

    LiveTacticalOrdnanceObservationRuntime(
            LiveTacticalBattleOrdnanceRuntime ordnanceRuntime,
            LiveTacticalBattleDecoyRuntime decoyRuntime,
            TrackQualityPolicy trackPolicy) {
        this.ordnanceRuntime = Objects.requireNonNull(ordnanceRuntime, "ordnanceRuntime");
        this.decoyRuntime = decoyRuntime;
        if (decoyRuntime != null && decoyRuntime.ordnanceRuntime() != ordnanceRuntime) {
            throw new IllegalArgumentException("decoyRuntime must wrap the same ordnanceRuntime instance");
        }
        engineeringCatalog = Stage175ICombatTestContentPack.loadDoctrines();
        ammunitionCatalog = Stage175ICombatTestWeaponPack.loadAmmunition();
        calculator = new DerivedShipCalculator(engineeringCatalog);
        sensorAdapter = new ShipSensorEngineeringAdapter();
        ewAdapter = new ShipElectronicWarfareEngineeringAdapter();
        observationService = new ShipObservationService();
        grantService = new ShipEngineeringGrantService(engineeringCatalog);
        sensorRuntime = new ShipSensorRuntime();
        this.trackPolicy = Objects.requireNonNull(trackPolicy, "trackPolicy");
        for (CombatantRuntime combatant : battleState().combatants()) {
            long entityId = combatant.spec().entityId();
            measurementsByObserver.put(entityId, new TreeMap<>());
            tracksByObserver.put(entityId, new TreeMap<>());
            latestSamplesByObserver.put(entityId, new TreeMap<>());
            lastScanByObserver.put(entityId, ScanDiagnostics.none());
        }
    }

    /**
     * Updates observer-local ordnance information for the current already-advanced battle tick.
     *
     * <p>The call is idempotent within one tick. Known tracks age every new tick; a physical radar
     * scan is attempted only in the dedicated scan phase. When a decoy runtime is attached, its body
     * state must already have been advanced to the current authoritative tick by the caller.</p>
     */
    public void observeCurrentTick() {
        long tick = ordnanceRuntime.tick();
        if (tick <= 0L) {
            return;
        }
        if (tick == lastObservedTick) {
            return;
        }
        if (lastObservedTick > tick) {
            throw new IllegalStateException("ordnance observation tick moved backwards");
        }
        ageKnownTracks(ordnanceRuntime.elapsedSeconds());
        if (tick % SCAN_PERIOD_TICKS == SCAN_PHASE_TICK) {
            scanAllObservers();
        }
        lastObservedTick = tick;
    }

    /**
     * Returns current observer-local ordnance tracks in deterministic target-ID order.
     *
     * @param observerEntityId stable observing combatant identity
     * @return immutable track projections; empty when nothing has been observed
     */
    public List<ObservedOrdnanceTrack> tracksForObserver(long observerEntityId) {
        battleState().requireCombatant(observerEntityId);
        return List.copyOf(tracksByObserver.get(observerEntityId).values());
    }

    /**
     * Returns one observer-local guided-body track.
     *
     * @param observerEntityId stable observing combatant identity
     * @param bodyId guided-body identity hypothesis
     * @return current actor-bounded track or {@code null}
     */
    public ObservedOrdnanceTrack track(long observerEntityId, long bodyId) {
        battleState().requireCombatant(observerEntityId);
        return tracksByObserver.get(observerEntityId).get(bodyId);
    }

    /**
     * Returns diagnostics for the most recent physical ordnance-radar scan of one combatant.
     *
     * @param observerEntityId stable observing combatant identity
     * @return immutable receiver-local diagnostic projection
     */
    public ScanDiagnostics lastScanDiagnostics(long observerEntityId) {
        battleState().requireCombatant(observerEntityId);
        return lastScanByObserver.get(observerEntityId);
    }

    /** @return wrapped physical guided-ordnance runtime */
    public LiveTacticalBattleOrdnanceRuntime ordnanceRuntime() {
        return ordnanceRuntime;
    }

    private LiveTacticalBattleRuntimeState battleState() {
        return ordnanceRuntime.battleState();
    }

    private void scanAllObservers() {
        for (CombatantRuntime observer : battleState().combatants()) {
            scanObserver(observer);
        }
    }

    private void scanObserver(CombatantRuntime observer) {
        List<GuidedWeaponBody> hostileBodies = hostileSensorBodies(observer);
        if (hostileBodies.isEmpty()) {
            lastScanByObserver.put(observer.spec().entityId(), ScanDiagnostics.none());
            return;
        }
        List<FittedSensor> radars = sensorAdapter.derive(derive(observer)).sensors().stream()
                .filter(sensor -> sensor.definition().mode() == Mode.ACTIVE_RADAR)
                .toList();
        if (radars.isEmpty()) {
            lastScanByObserver.put(observer.spec().entityId(), ScanDiagnostics.none());
            return;
        }

        ElectronicWarfareState ewState = electronicWarfareState(observer);
        EngineeringComponent engineering = observer.engineering();
        var budget = grantService.beginInterval(engineering, OPERATION_SECONDS);
        TreeMap<Long, Integer> measurementsBefore = new TreeMap<>();
        TreeMap<Long, List<SensorMeasurement>> observerHistory = measurementsByObserver.get(observer.spec().entityId());
        for (GuidedWeaponBody body : hostileBodies) {
            measurementsBefore.put(body.bodyId(), observerHistory.getOrDefault(body.bodyId(), List.of()).size());
        }

        boolean eccmRequested = !ewState.noiseJammers().isEmpty();
        boolean eccmCommitted = false;
        int measurementsProduced = 0;
        double committedPowerW = 0d;
        double committedHeatW = 0d;
        for (FittedSensor radar : radars) {
            SensorRuntimeState sensorState = new SensorRuntimeState(true, eccmRequested, 1d, 1d);
            OperationPlan plan = observationService.planOperation(radar, sensorState);
            var grant = grantService.grantAndCommit(
                    engineering,
                    radar.mountId(),
                    plan.requiredPowerW(),
                    plan.requiredHeatW(),
                    OPERATION_SECONDS,
                    budget);
            if (!grant.committed() && eccmRequested) {
                sensorState = SensorRuntimeState.nominal();
                plan = observationService.planOperation(radar, sensorState);
                grant = grantService.grantAndCommit(
                        engineering,
                        radar.mountId(),
                        plan.requiredPowerW(),
                        plan.requiredHeatW(),
                        OPERATION_SECONDS,
                        budget);
            }
            if (!grant.committed()) {
                continue;
            }
            eccmCommitted |= sensorState.eccmEnabled();
            committedPowerW += plan.requiredPowerW();
            committedHeatW += plan.requiredHeatW();
            for (GuidedWeaponBody body : hostileBodies) {
                var ammunition = ammunitionCatalog.findGuided(body.definition().id());
                if (ammunition == null) {
                    throw new IllegalStateException(
                            "active guided body lacks ammunition content: " + body.definition().id());
                }
                var execution = observationService.execute(
                        plan,
                        grant.grant(),
                        radar,
                        sensorState,
                        observer.spec().entityId(),
                        body.bodyId(),
                        new Position2d(observer.transform().position.x, observer.transform().position.y),
                        new Position2d(body.xM(), body.yM()),
                        ammunition.signature().toRuntimeSignature(),
                        ewState,
                        ordnanceRuntime.elapsedSeconds());
                if (execution.measurement().isPresent()) {
                    measurementsProduced = Math.addExact(measurementsProduced, 1);
                    appendMeasurement(observerHistory, body.bodyId(), execution.measurement().orElseThrow());
                }
            }
        }

        lastScanByObserver.put(observer.spec().entityId(), new ScanDiagnostics(
                ewState.noiseJammers().size(),
                eccmRequested,
                eccmCommitted,
                measurementsProduced,
                committedPowerW,
                committedHeatW));

        for (GuidedWeaponBody body : hostileBodies) {
            List<SensorMeasurement> history = observerHistory.getOrDefault(body.bodyId(), List.of());
            int before = measurementsBefore.getOrDefault(body.bodyId(), 0);
            if (history.size() <= before) {
                continue;
            }
            TrackState fused = sensorRuntime.fuse(
                    body.bodyId(),
                    history,
                    DatalinkState.local(),
                    trackPolicy,
                    ordnanceRuntime.elapsedSeconds());
            updateObservedTrack(observer.spec().entityId(), fused);
        }
    }

    private ElectronicWarfareState electronicWarfareState(CombatantRuntime observer) {
        List<NoiseJammer> jammers = new ArrayList<>();
        for (CombatantRuntime emitter : battleState().combatants()) {
            if (emitter.spec().entityId() == observer.spec().entityId()) {
                continue;
            }
            ewAdapter.deriveNoiseJammer(
                    emitter.spec().entityId(),
                    emitter.transform().position.x,
                    emitter.transform().position.y,
                    derive(emitter)).ifPresent(jammers::add);
        }
        return new ElectronicWarfareState(jammers, List.of());
    }

    private void updateObservedTrack(long observerEntityId, TrackState fused) {
        TreeMap<Long, PositionSample> samples = latestSamplesByObserver.get(observerEntityId);
        PositionSample previous = samples.get(fused.targetId());
        boolean velocityKnown = false;
        double velocityX = 0d;
        double velocityY = 0d;
        double velocitySigma = 0d;
        if (fused.positionKnown() && previous != null) {
            double deltaSeconds = fused.lastMeasurementSeconds() - previous.timestampSeconds();
            if (deltaSeconds > EPSILON) {
                velocityKnown = true;
                velocityX = (fused.estimatedXM() - previous.xM()) / deltaSeconds;
                velocityY = (fused.estimatedYM() - previous.yM()) / deltaSeconds;
                double variance = fused.covariance().positionVarianceM2()
                        + previous.positionVarianceM2();
                velocitySigma = Math.sqrt(Math.max(0d, variance)) / deltaSeconds;
            }
        }
        if (fused.positionKnown()) {
            samples.put(fused.targetId(), new PositionSample(
                    fused.lastMeasurementSeconds(),
                    fused.estimatedXM(),
                    fused.estimatedYM(),
                    fused.covariance().positionVarianceM2()));
        }
        ObservedOrdnanceTrack old = tracksByObserver.get(observerEntityId).get(fused.targetId());
        if (!velocityKnown && supportsVelocityContinuity(fused) && old != null && old.velocityKnown()) {
            velocityKnown = true;
            velocityX = old.estimatedVelocityXMps();
            velocityY = old.estimatedVelocityYMps();
            velocitySigma = old.oneSigmaVelocityMps();
        }
        tracksByObserver.get(observerEntityId).put(
                fused.targetId(),
                new ObservedOrdnanceTrack(fused, velocityKnown, velocityX, velocityY, velocitySigma));
    }

    private void ageKnownTracks(double nowSeconds) {
        for (Map.Entry<Long, TreeMap<Long, ObservedOrdnanceTrack>> observerEntry : tracksByObserver.entrySet()) {
            long observerEntityId = observerEntry.getKey();
            TreeMap<Long, ObservedOrdnanceTrack> observerTracks = observerEntry.getValue();
            List<Map.Entry<Long, ObservedOrdnanceTrack>> values = new ArrayList<>(observerTracks.entrySet());
            for (Map.Entry<Long, ObservedOrdnanceTrack> entry : values) {
                ObservedOrdnanceTrack current = entry.getValue();
                TrackState aged = sensorRuntime.ageTrack(current.track(), nowSeconds, trackPolicy);
                if (!supportsVelocityContinuity(aged)) {
                    latestSamplesByObserver.get(observerEntityId).remove(entry.getKey());
                    observerTracks.put(entry.getKey(), new ObservedOrdnanceTrack(
                            aged,
                            false,
                            0d,
                            0d,
                            0d));
                    continue;
                }
                observerTracks.put(entry.getKey(), new ObservedOrdnanceTrack(
                        aged,
                        current.velocityKnown(),
                        current.estimatedVelocityXMps(),
                        current.estimatedVelocityYMps(),
                        current.oneSigmaVelocityMps()));
            }
        }
    }

    private static boolean supportsVelocityContinuity(TrackState track) {
        if (!track.positionKnown()) {
            return false;
        }
        return track.informationState() == TrackState.InformationState.TRACKED
                || track.informationState() == TrackState.InformationState.FIRE_CONTROL;
    }

    private List<GuidedWeaponBody> hostileSensorBodies(CombatantRuntime observer) {
        ArrayList<GuidedWeaponBody> bodies = new ArrayList<>();
        bodies.addAll(ordnanceRuntime.guidedBodies());
        if (decoyRuntime != null) {
            bodies.addAll(decoyRuntime.decoyBodies());
        }
        bodies.removeIf(body -> battleState().requireCombatant(body.sourceEntityId()).spec().side()
                == observer.spec().side());
        bodies.sort(java.util.Comparator.comparingLong(GuidedWeaponBody::bodyId));
        return List.copyOf(bodies);
    }

    private ShipEngineeringState.DerivedShipState derive(CombatantRuntime combatant) {
        EngineeringComponent engineering = combatant.engineering();
        return calculator.derive(
                combatant.hull(),
                engineering.fit,
                engineering.runtimeState.consumables(),
                engineering.instanceState.damage().moduleDamage());
    }

    private static void appendMeasurement(
            TreeMap<Long, List<SensorMeasurement>> historyByTarget,
            long targetId,
            SensorMeasurement measurement) {
        ArrayList<SensorMeasurement> values = new ArrayList<>(historyByTarget.getOrDefault(targetId, List.of()));
        values.add(Objects.requireNonNull(measurement, "measurement"));
        while (values.size() > MAX_MEASUREMENTS_PER_TARGET) {
            values.remove(0);
        }
        historyByTarget.put(targetId, List.copyOf(values));
    }

    /**
     * Receiver-local diagnostic state from the latest ordnance radar scan.
     *
     * @param noiseJammerCount physical external noise emitters included in the receiver environment
     * @param eccmRequested whether receiver policy requested fitted ECCM processing
     * @param eccmCommitted whether the engineering layer physically admitted an ECCM radar operation
     * @param measurementsProduced true-target measurements emitted by the accepted operations
     * @param committedPowerW total incremental radar/ECCM power admitted for this scan
     * @param committedHeatW total incremental radar/ECCM heat admitted for this scan
     */
    public record ScanDiagnostics(
            int noiseJammerCount,
            boolean eccmRequested,
            boolean eccmCommitted,
            int measurementsProduced,
            double committedPowerW,
            double committedHeatW) {
        /**
         * Validates deterministic non-negative scan diagnostics.
         *
         * @param noiseJammerCount physical external noise emitters included in the receiver environment
         * @param eccmRequested whether receiver policy requested fitted ECCM processing
         * @param eccmCommitted whether engineering physically admitted an ECCM radar operation
         * @param measurementsProduced true-target measurements emitted by accepted operations
         * @param committedPowerW total incremental radar/ECCM power admitted for this scan
         * @param committedHeatW total incremental radar/ECCM heat admitted for this scan
         */
        public ScanDiagnostics {
            if (noiseJammerCount < 0 || measurementsProduced < 0
                    || !Double.isFinite(committedPowerW) || committedPowerW < 0d
                    || !Double.isFinite(committedHeatW) || committedHeatW < 0d) {
                throw new IllegalArgumentException("invalid ordnance scan diagnostics");
            }
            if (eccmCommitted && !eccmRequested) {
                throw new IllegalArgumentException("ECCM cannot be committed when it was not requested");
            }
        }

        private static ScanDiagnostics none() {
            return new ScanDiagnostics(0, false, false, 0, 0d, 0d);
        }
    }

    /**
     * Observer-local ordnance track plus velocity inferred only from successive observed positions.
     *
     * @param track ordinary production fused target track
     * @param velocityKnown whether two temporally distinct Cartesian observer-local solutions exist
     * @param estimatedVelocityXMps actor-bounded x velocity estimate or canonical zero
     * @param estimatedVelocityYMps actor-bounded y velocity estimate or canonical zero
     * @param oneSigmaVelocityMps scalar one-sigma velocity uncertainty or canonical zero
     */
    public record ObservedOrdnanceTrack(
            TrackState track,
            boolean velocityKnown,
            double estimatedVelocityXMps,
            double estimatedVelocityYMps,
            double oneSigmaVelocityMps) {
        /**
         * Validates one immutable observer-local ordnance track.
         *
         * @param track ordinary production fused target track
         * @param velocityKnown whether velocity is supported by observation history
         * @param estimatedVelocityXMps actor-bounded x velocity estimate or zero
         * @param estimatedVelocityYMps actor-bounded y velocity estimate or zero
         * @param oneSigmaVelocityMps non-negative velocity uncertainty or zero
         */
        public ObservedOrdnanceTrack {
            Objects.requireNonNull(track, "track");
            if (!Double.isFinite(estimatedVelocityXMps) || !Double.isFinite(estimatedVelocityYMps)
                    || !Double.isFinite(oneSigmaVelocityMps) || oneSigmaVelocityMps < 0d) {
                throw new IllegalArgumentException("observed ordnance velocity state must be finite/non-negative");
            }
            if (!velocityKnown
                    && (estimatedVelocityXMps != 0d || estimatedVelocityYMps != 0d || oneSigmaVelocityMps != 0d)) {
                throw new IllegalArgumentException("unknown velocity must use canonical zero values");
            }
        }
    }

    private record PositionSample(
            double timestampSeconds,
            double xM,
            double yM,
            double positionVarianceM2) {
        private PositionSample {
            if (!Double.isFinite(timestampSeconds) || !Double.isFinite(xM) || !Double.isFinite(yM)
                    || !Double.isFinite(positionVarianceM2) || positionVarianceM2 <= 0d) {
                throw new IllegalArgumentException("position sample must be finite with positive variance");
            }
        }
    }
}
