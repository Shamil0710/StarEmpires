package com.spacesim.world.calibration;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.weapon.Stage175ICombatTestWeaponPack;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.GuidedAmmunitionDefinition;
import com.spacesim.content.weapon.WeaponLauncherCatalog;
import com.spacesim.ship.BeamWeaponRuntime;
import com.spacesim.ship.DerivedShipCalculator;
import com.spacesim.ship.GuidanceRuntime;
import com.spacesim.ship.GuidanceRuntime.TrackSource;
import com.spacesim.ship.GuidedWeaponBody;
import com.spacesim.ship.LayeredDefenseScheduler;
import com.spacesim.ship.LayeredDefenseScheduler.Assignment;
import com.spacesim.ship.LayeredDefenseScheduler.DefendedZone;
import com.spacesim.ship.LayeredDefenseScheduler.DefenseStation;
import com.spacesim.ship.LayeredDefenseScheduler.ObservedThreatKinematics;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipWeaponEngineeringAdapter;
import com.spacesim.ship.ShipWeaponEngineeringAdapter.FittedBeamMount;
import com.spacesim.ship.ShipWeaponEngineeringAdapter.FittedKineticMount;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.Doctrine;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import com.spacesim.ship.TrackCovariance;
import com.spacesim.ship.TrackState;
import com.spacesim.ship.TrackState.InformationState;
import com.spacesim.ship.WeaponDefinition.GuidedWeapon;
import com.spacesim.ship.WeaponFireControl;
import com.spacesim.ship.WeaponFireControl.KinematicState;
import com.spacesim.ship.WeaponFireControl.TargetMotionEstimate;
import com.spacesim.world.calibration.Stage20WeaponSpatialCalibrationProfile.BeamSample;
import com.spacesim.world.calibration.Stage20WeaponSpatialCalibrationProfile.DefenseSample;
import com.spacesim.world.calibration.Stage20WeaponSpatialCalibrationProfile.GuidedSample;
import com.spacesim.world.calibration.Stage20WeaponSpatialCalibrationProfile.KineticSample;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic Stage-20A.5 probe suite over production weapon and layered-defense runtimes.
 *
 * <p>The calculator deliberately owns no weapon equations. It supplies controlled geometry to the
 * ordinary fitted-weapon adapter, fire-control solver, beam runtime, guidance runtime and layered
 * defense scheduler, then records their physical outputs. Probe distances and motion inputs are
 * sensitivity coordinates only and cannot be consumed as hard gameplay ranges.</p>
 */
public final class Stage20WeaponSpatialCalibrationCalculator {
    private static final long TARGET_ID = 20_005L;
    private static final String STRIKE_AMMUNITION_ID = "ammo.test_anti_ship_missile_2t_v1";
    private static final String INTERCEPTOR_AMMUNITION_ID = "ammo.test_interceptor_750kg_v1";
    private static final List<Double> DIRECT_FIRE_PROBE_RANGES_M = List.of(
            300_000d,
            3_000_000d,
            10_000_000d);
    private static final List<Double> BEAM_PROBE_RANGES_M = List.of(
            300_000d,
            3_000_000d,
            10_000_000d,
            30_000_000d);
    private static final List<Double> GUIDED_PROBE_RANGES_M = List.of(
            100_000d,
            300_000d,
            1_000_000d);
    private static final List<Double> DEFENSE_THREAT_RANGES_M = List.of(
            50_000d,
            100_000d,
            300_000d);
    private static final List<Double> SAFE_INTERCEPT_PROBES_M = List.of(0d, 5_000d);
    private static final double TRACK_POSITION_SIGMA_M = 10d;
    private static final double GUIDED_INITIAL_SPEED_MPS = 500d;
    private static final double GUIDANCE_STEP_SECONDS = 1d;
    private static final double DEFENDED_ZONE_RADIUS_M = 1_500d;
    private static final double DEFENSE_STATION_X_M = 12_000d;
    private static final double THREAT_CLOSING_SPEED_MPS = 2_500d;

    private Stage20WeaponSpatialCalibrationCalculator() {
        throw new AssertionError("utility class");
    }

    /**
     * Executes the current deterministic Stage-20A.5 production-backed calibration suite.
     *
     * @return immutable weapon/defense spatial evidence profile
     */
    public static Stage20WeaponSpatialCalibrationProfile calibrate() {
        ShipEngineeringCatalog engineering = Stage175ICombatTestContentPack.loadDoctrines();
        WeaponAmmunitionCatalog ammunition = Stage175ICombatTestWeaponPack.loadAmmunition();
        WeaponLauncherCatalog launchers = Stage175ICombatTestWeaponPack.loadLaunchers();
        DerivedShipCalculator shipCalculator = new DerivedShipCalculator(engineering);
        ShipWeaponEngineeringAdapter weaponAdapter = new ShipWeaponEngineeringAdapter();

        FittedKineticMount kineticMount = representativeKineticMount(
                shipCalculator, weaponAdapter, ammunition, launchers);
        FittedBeamMount beamMount = representativeBeamMount(shipCalculator, weaponAdapter, engineering);
        GuidedAmmunitionDefinition strikeAmmunition = requireGuided(ammunition, STRIKE_AMMUNITION_ID);
        GuidedAmmunitionDefinition interceptorAmmunition = requireGuided(ammunition, INTERCEPTOR_AMMUNITION_ID);

        Stage20PdSafeInterceptCalibrationProfile pdClosure =
                Stage20PdSafeInterceptCalibrationProfile.deriveCurrent();
        return new Stage20WeaponSpatialCalibrationProfile(
                Stage20WeaponSpatialCalibrationProfile.CURRENT_VERSION,
                kineticSamples(kineticMount),
                beamSamples(beamMount),
                guidedSamples(strikeAmmunition),
                defenseSamples(interceptorAmmunition.toRuntimeWeapon()),
                List.of(
                        "beam_runtime_has_no_hard_range_wall_effectiveness_requires_target_material_response",
                        "safe_intercept_distance_superseded_by=" + pdClosure.version()
                                + ";scheduler_input_m=" + pdClosure.selectedMinimumInterceptDistanceM()
                                + ";residual_risk_zero=" + pdClosure.residualRiskZero(),
                        "kinetic_point_defense_has_no_separate_range_owning_scheduler_stage20a5_uses_guided_layered_defense_geometry",
                        "fire_control_track_age_and_uncertainty_limits_remain_weapon_target_motion_dependent_not_global_thresholds"));
    }

    private static FittedKineticMount representativeKineticMount(
            DerivedShipCalculator calculator,
            ShipWeaponEngineeringAdapter adapter,
            WeaponAmmunitionCatalog ammunition,
            WeaponLauncherCatalog launchers) {
        Doctrine doctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.A_KINETIC_LINE);
        DerivedShipState ship = calculator.deriveDemonstrator(
                doctrine.fitId(), doctrine.initialConsumables(), DamageState.pristine());
        return adapter.deriveKineticMounts(ship, ammunition, launchers, doctrine.weaponLoadout()).stream()
                .filter(value -> value.mountId().equals("weapon_primary"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Stage 20A.5 representative kinetic mount missing"));
    }

    private static FittedBeamMount representativeBeamMount(
            DerivedShipCalculator calculator,
            ShipWeaponEngineeringAdapter adapter,
            ShipEngineeringCatalog engineering) {
        Doctrine doctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.C_HIGH_MOBILITY_BEAM);
        DerivedShipState ship = calculator.deriveDemonstrator(
                doctrine.fitId(), doctrine.initialConsumables(), DamageState.pristine());
        return adapter.deriveBeamMounts(ship, engineering).stream()
                .filter(value -> value.mountId().equals("weapon_primary"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Stage 20A.5 representative beam mount missing"));
    }

    private static List<KineticSample> kineticSamples(FittedKineticMount mount) {
        WeaponFireControl fireControl = new WeaponFireControl();
        ArrayList<KineticSample> samples = new ArrayList<>();
        for (double rangeM : DIRECT_FIRE_PROBE_RANGES_M) {
            samples.add(kineticSample(
                    fireControl, mount, rangeM,
                    new TargetMotionEstimate(0d, 0d, 0d, 0d)));
            samples.add(kineticSample(
                    fireControl, mount, rangeM,
                    new TargetMotionEstimate(0d, 1_000d, 25d, 0.5d)));
        }
        return List.copyOf(samples);
    }

    private static KineticSample kineticSample(
            WeaponFireControl fireControl,
            FittedKineticMount mount,
            double rangeM,
            TargetMotionEstimate motion) {
        TrackState track = track(rangeM, InformationState.FIRE_CONTROL);
        WeaponFireControl.KineticFireSolution solution = fireControl.planKinetic(
                mount.round(),
                track,
                new KinematicState(0d, 0d, 0d, 0d),
                motion,
                mount.pointingJitterRad(),
                0d);
        return new KineticSample(
                rangeM,
                motion.velocityYMps(),
                motion.oneSigmaVelocityMps(),
                motion.maneuverAccelerationMps2(),
                solution.allowed(),
                solution.timeOfFlightSeconds(),
                solution.oneSigmaAimUncertaintyM(),
                solution.maneuverEnvelopeRadiusM(),
                mount.round().kineticEnergyJ(),
                "ShipWeaponEngineeringAdapter+WeaponFireControl.planKinetic");
    }

    private static List<BeamSample> beamSamples(FittedBeamMount mount) {
        BeamWeaponRuntime runtime = new BeamWeaponRuntime();
        ArrayList<BeamSample> samples = new ArrayList<>();
        double dwellSeconds = Math.min(1d, mount.weapon().maxContinuousDwellSeconds());
        for (double rangeM : BEAM_PROBE_RANGES_M) {
            BeamWeaponRuntime.BeamSolution solution = runtime.plan(
                    mount.weapon(), track(rangeM, InformationState.FIRE_CONTROL), 0d, 0d, dwellSeconds);
            samples.add(new BeamSample(
                    rangeM,
                    solution.allowed(),
                    dwellSeconds,
                    solution.effectiveSpotRadiusM(),
                    solution.meanIrradianceWPerM2(),
                    solution.deliveredBeamEnergyJ(),
                    "ShipWeaponEngineeringAdapter+BeamWeaponRuntime.plan"));
        }
        return List.copyOf(samples);
    }

    private static List<GuidedSample> guidedSamples(GuidedAmmunitionDefinition ammunition) {
        GuidanceRuntime runtime = new GuidanceRuntime();
        ArrayList<GuidedSample> samples = new ArrayList<>();
        for (double rangeM : GUIDED_PROBE_RANGES_M) {
            samples.add(guidedSample(runtime, ammunition, rangeM, 0d));
            samples.add(guidedSample(runtime, ammunition, rangeM, 1_000d));
        }
        return List.copyOf(samples);
    }

    private static GuidedSample guidedSample(
            GuidanceRuntime runtime,
            GuidedAmmunitionDefinition ammunition,
            double rangeM,
            double targetLateralVelocityMps) {
        GuidedWeapon definition = ammunition.toRuntimeWeapon();
        GuidedWeaponBody body = GuidedWeaponBody.launch(
                30_005L,
                10_005L,
                TARGET_ID,
                definition,
                ammunition.materialId(),
                ammunition.shape(),
                ammunition.lengthM(),
                ammunition.diameterM(),
                ammunition.impactPayloadId(),
                0d,
                0d,
                GUIDED_INITIAL_SPEED_MPS,
                0d);
        double initialDeltaV = body.remainingDeltaVMps();
        GuidanceRuntime.GuidanceCommand command = runtime.planLeadPursuit(
                body,
                track(rangeM, InformationState.TRACKED),
                new TargetMotionEstimate(0d, targetLateralVelocityMps, 0d, 0d),
                TrackSource.ONBOARD_SEEKER,
                GUIDANCE_STEP_SECONDS);
        double propellantConsumed = 0d;
        if (command.allowed()) {
            GuidedWeaponBody after = runtime.execute(body, command);
            propellantConsumed = body.remainingPropellantKg() - after.remainingPropellantKg();
        }
        return new GuidedSample(
                ammunition.id(),
                rangeM,
                targetLateralVelocityMps,
                command.allowed(),
                command.predictedInterceptSeconds(),
                initialDeltaV,
                definition.terminalReserveMps(),
                command.burnSeconds(),
                propellantConsumed,
                "GuidedWeaponBody+GuidanceRuntime.planLeadPursuit/execute");
    }

    private static List<DefenseSample> defenseSamples(GuidedWeapon interceptor) {
        LayeredDefenseScheduler scheduler = new LayeredDefenseScheduler();
        DefendedZone zone = new DefendedZone(0d, 0d, DEFENDED_ZONE_RADIUS_M);
        ArrayList<DefenseSample> samples = new ArrayList<>();
        for (double rangeM : DEFENSE_THREAT_RANGES_M) {
            for (double safeDistanceM : SAFE_INTERCEPT_PROBES_M) {
                DefenseStation station = new DefenseStation(
                        50_005L,
                        DEFENSE_STATION_X_M,
                        0d,
                        0d,
                        interceptor,
                        true,
                        2,
                        4L,
                        true,
                        safeDistanceM);
                ObservedThreatKinematics threat = new ObservedThreatKinematics(
                        60_005L,
                        rangeM,
                        0d,
                        -THREAT_CLOSING_SPEED_MPS,
                        0d);
                List<Assignment> assignments = scheduler.scheduleObserved(
                        zone, List.of(threat), List.of(station));
                if (assignments.isEmpty()) {
                    samples.add(new DefenseSample(
                            rangeM,
                            THREAT_CLOSING_SPEED_MPS,
                            safeDistanceM,
                            false,
                            0d,
                            0d,
                            0d,
                            "LayeredDefenseScheduler.scheduleObserved"));
                } else {
                    Assignment assignment = assignments.get(0);
                    samples.add(new DefenseSample(
                            rangeM,
                            THREAT_CLOSING_SPEED_MPS,
                            safeDistanceM,
                            true,
                            assignment.predictedImpactSeconds(),
                            assignment.plannedInterceptSeconds(),
                            Math.hypot(assignment.interceptXM(), assignment.interceptYM()),
                            "LayeredDefenseScheduler.scheduleObserved"));
                }
            }
        }
        return List.copyOf(samples);
    }

    private static TrackState track(double rangeM, InformationState state) {
        double varianceM2 = TRACK_POSITION_SIGMA_M * TRACK_POSITION_SIGMA_M;
        return new TrackState(
                TARGET_ID,
                state,
                true,
                rangeM,
                0d,
                new TrackCovariance(varianceM2, 1e-12d, varianceM2),
                1d,
                0d,
                1,
                1);
    }

    private static GuidedAmmunitionDefinition requireGuided(
            WeaponAmmunitionCatalog ammunition,
            String ammunitionId) {
        GuidedAmmunitionDefinition result = ammunition.findGuided(ammunitionId);
        if (result == null) {
            throw new IllegalStateException("Stage 20A.5 guided ammunition missing: " + ammunitionId);
        }
        return result;
    }
}
