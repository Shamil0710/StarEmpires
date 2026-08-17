package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.Vector3d;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.ship.Stage175ICombatTestProtectionPack;
import com.spacesim.content.weapon.Stage175ICombatTestWeaponPack;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponLauncherCatalog;
import com.spacesim.ship.ElectronicWarfareState.NoiseJammer;
import com.spacesim.ship.GuidanceRuntime.TrackSource;
import com.spacesim.ship.LayeredDefenseScheduler.Assignment;
import com.spacesim.ship.LayeredDefenseScheduler.DefendedZone;
import com.spacesim.ship.LayeredDefenseScheduler.DefenseStation;
import com.spacesim.ship.LayeredDefenseScheduler.Threat;
import com.spacesim.ship.SensorDefinition.Mode;
import com.spacesim.ship.ShipDamageRuntime.Snapshot;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.ShipSensorEngineeringAdapter.FittedSensor;
import com.spacesim.ship.ShipSensorEngineeringAdapter.FittedSensorSuite;
import com.spacesim.ship.ShipSensorRuntime.Position2d;
import com.spacesim.ship.ShipSensorRuntime.TrackQualityPolicy;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.Doctrine;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import com.spacesim.ship.TrackState.InformationState;
import com.spacesim.ship.WeaponDefinition.BeamWeapon;
import com.spacesim.ship.WeaponDefinition.GuidedWeapon;
import com.spacesim.ship.WeaponDefinition.ProjectileShape;
import com.spacesim.ship.WeaponFireControl.KinematicState;
import com.spacesim.ship.WeaponFireControl.TargetMotionEstimate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Deterministic Stage-17.5I aggregate combat acceptance harness.
 *
 * <p>The harness is not tactical AI and never returns a synthetic winner score. It runs fixed,
 * inspectable geometry through production Stage-17.5 capability, sensor/track, fire-control,
 * ammunition, guided-body, layered-defense, shield, material and compartment-damage seams. Results
 * are physical measurements plus a stable SHA-256 fingerprint suitable for CI regression.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage175ICombatAcceptanceHarness {
    private static final double DEFAULT_RANGE_M = 300_000d;
    private static final double JAMMER_WAVEFORM_OVERLAP = 1e-8d;
    private static final double SHIELD_INTERACTION_SECONDS = 1d;
    private static final double KINETIC_HIT_Y_M = 65d;
    private static final long TARGET_ID_LEFT = 10_001L;
    private static final long TARGET_ID_RIGHT = 20_001L;

    private final ShipEngineeringCatalog engineering = Stage175ICombatTestContentPack.loadDoctrines();
    private final ShipProtectionCatalog protection = Stage175ICombatTestProtectionPack.load();
    private final WeaponAmmunitionCatalog ammunition = Stage175ICombatTestWeaponPack.loadAmmunition();
    private final WeaponLauncherCatalog launchers = Stage175ICombatTestWeaponPack.loadLaunchers();
    private final DerivedShipCalculator calculator = new DerivedShipCalculator(engineering);
    private final ShipSensorEngineeringAdapter sensorAdapter = new ShipSensorEngineeringAdapter();
    private final ShipSensorRuntime sensorRuntime = new ShipSensorRuntime();
    private final WeaponFireControl fireControl = new WeaponFireControl();
    private final ShipWeaponEngineeringAdapter weaponAdapter = new ShipWeaponEngineeringAdapter();
    private final AmmunitionRuntime ammunitionRuntime = new AmmunitionRuntime();
    private final GuidanceRuntime guidanceRuntime = new GuidanceRuntime();
    private final LayeredDefenseScheduler defenseScheduler = new LayeredDefenseScheduler();
    private final ShieldFieldRuntime shieldRuntime = new ShieldFieldRuntime();
    private final ShipDamageRuntime damageRuntime = new ShipDamageRuntime();
    private final HeavyImpactResolver impactResolver = new HeavyImpactResolver(engineering, protection);
    private final KineticProtectionRuntime kineticProtection =
            new KineticProtectionRuntime(shieldRuntime, impactResolver, damageRuntime);

    /** Information-state variant used by deterministic matrix scenarios. */
    public enum InformationPreset {
        /** Current active ranging evidence with no artificial aging. */ NOMINAL,
        /** Current evidence is aged before fire-control use. */ DEGRADED
    }

    /**
     * One deterministic aggregate scenario.
     *
     * @param leftDoctrine left-side physical fit/stores fixture
     * @param rightDoctrine right-side physical fit/stores fixture
     * @param leftCount physical ship count represented by the side
     * @param rightCount physical ship count represented by the side
     * @param spacingM formation spacing used by defense geometry
     * @param ammunitionFraction fraction [0,1] of authored ammunition loaded
     * @param initialIntegrity common pre-damage integrity [0,1] applied to compartments/modules
     * @param thermalStressFraction fraction [0,1] used to make defense thermal availability explicit
     * @param informationPreset sensor-information freshness variant
     * @param protectedLogisticsAsset whether an explicit defended logistics zone/interceptor probe is active
     */
    public record Scenario(
            DoctrineId leftDoctrine,
            DoctrineId rightDoctrine,
            int leftCount,
            int rightCount,
            double spacingM,
            double ammunitionFraction,
            double initialIntegrity,
            double thermalStressFraction,
            InformationPreset informationPreset,
            boolean protectedLogisticsAsset) {
        public Scenario {
            Objects.requireNonNull(leftDoctrine, "leftDoctrine");
            Objects.requireNonNull(rightDoctrine, "rightDoctrine");
            Objects.requireNonNull(informationPreset, "informationPreset");
            if (leftCount <= 0 || rightCount <= 0) {
                throw new IllegalArgumentException("fleet counts must be positive");
            }
            requirePositiveFinite(spacingM, "spacingM");
            requireUnitInterval(ammunitionFraction, "ammunitionFraction");
            requireUnitInterval(initialIntegrity, "initialIntegrity");
            requireUnitInterval(thermalStressFraction, "thermalStressFraction");
        }

        /** @return canonical equal-count nominal scenario for one required doctrine pair */
        public static Scenario nominal(DoctrineId left, DoctrineId right) {
            Doctrine leftFixture = Stage175IFleetDoctrineCatalog.get(left);
            Doctrine rightFixture = Stage175IFleetDoctrineCatalog.get(right);
            return new Scenario(
                    left,
                    right,
                    leftFixture.defaultFleetCount(),
                    rightFixture.defaultFleetCount(),
                    Math.max(leftFixture.defaultSpacingM(), rightFixture.defaultSpacingM()),
                    1d,
                    1d,
                    0d,
                    InformationPreset.NOMINAL,
                    false);
        }
    }

    /** Physical output for one side of a scenario; no synthetic combat score is present. */
    public record SideResult(
            DoctrineId doctrine,
            int representedShipCount,
            double loadedMassKg,
            double accelerationMps2,
            double deltaVMps,
            double powerMarginW,
            double heatMarginW,
            long ammunitionBefore,
            long ammunitionAfter,
            InformationState trackQuality,
            int kineticShots,
            int guidedLaunches,
            int beamDwells,
            int defenseAssignments,
            double guidedPropellantBurnKg,
            double deliveredBeamEnergyJ,
            double shieldAbsorbedJ,
            double internalDamageJ,
            double postExchangePowerMarginW,
            double postExchangeSensorApertureM2) { }

    /**
     * Aggregate deterministic result.
     *
     * @param scenario exact scenario inputs
     * @param left physical left-side measurements
     * @param right physical right-side measurements
     * @param fingerprint lowercase SHA-256 over canonical inputs/outputs
     */
    public record Result(Scenario scenario, SideResult left, SideResult right, String fingerprint) { }

    /**
     * Executes one deterministic two-sided acceptance exchange.
     *
     * @param scenario exact physical/information fixture
     * @return immutable physical metrics and stable fingerprint
     */
    public Result run(Scenario scenario) {
        Scenario checked = Objects.requireNonNull(scenario, "scenario");
        SideState left = createSide(
                Stage175IFleetDoctrineCatalog.get(checked.leftDoctrine()),
                checked.leftCount(), checked.ammunitionFraction(), checked.initialIntegrity());
        SideState right = createSide(
                Stage175IFleetDoctrineCatalog.get(checked.rightDoctrine()),
                checked.rightCount(), checked.ammunitionFraction(), checked.initialIntegrity());

        Exchange leftAttack = attack(left, right, checked, true);
        Exchange rightAttack = attack(right, left, checked, false);
        SideResult leftResult = resultFor(left, leftAttack, rightAttack);
        SideResult rightResult = resultFor(right, rightAttack, leftAttack);
        return new Result(checked, leftResult, rightResult, fingerprint(checked, leftResult, rightResult));
    }

    /** @return required unordered A-E pair matrix including the A-A repeatability control */
    public static List<Scenario> requiredPairMatrix() {
        return List.of(
                Scenario.nominal(DoctrineId.A_KINETIC_LINE, DoctrineId.A_KINETIC_LINE),
                Scenario.nominal(DoctrineId.A_KINETIC_LINE, DoctrineId.B_MISSILE_STRIKE),
                Scenario.nominal(DoctrineId.A_KINETIC_LINE, DoctrineId.C_HIGH_MOBILITY_BEAM),
                Scenario.nominal(DoctrineId.A_KINETIC_LINE, DoctrineId.D_DEFENSIVE_EW),
                Scenario.nominal(DoctrineId.A_KINETIC_LINE, DoctrineId.E_BALANCED_CONTROL),
                Scenario.nominal(DoctrineId.B_MISSILE_STRIKE, DoctrineId.C_HIGH_MOBILITY_BEAM),
                Scenario.nominal(DoctrineId.B_MISSILE_STRIKE, DoctrineId.D_DEFENSIVE_EW),
                Scenario.nominal(DoctrineId.B_MISSILE_STRIKE, DoctrineId.E_BALANCED_CONTROL),
                Scenario.nominal(DoctrineId.C_HIGH_MOBILITY_BEAM, DoctrineId.D_DEFENSIVE_EW),
                Scenario.nominal(DoctrineId.C_HIGH_MOBILITY_BEAM, DoctrineId.E_BALANCED_CONTROL),
                Scenario.nominal(DoctrineId.D_DEFENSIVE_EW, DoctrineId.E_BALANCED_CONTROL));
    }

    private SideState createSide(
            Doctrine doctrine,
            int representedCount,
            double ammunitionFraction,
            double initialIntegrity) {
        HullDefinition hull = engineering.findHull("hull.test_doctrine_destroyer_v1");
        InstalledFit fit = InstalledFit.fromDemonstrator(engineering.findDemonstratorFit(doctrine.fitId()));
        ConsumableState consumables = scaleAmmunition(doctrine.initialConsumables(), ammunitionFraction);
        Snapshot damage = preDamagedSnapshot(hull, fit, initialIntegrity);
        DerivedShipState derived = calculator.derive(hull, fit, consumables, damage.moduleDamage());
        FittedSensorSuite sensors = sensorAdapter.derive(derived);
        ShieldFieldRuntime.Definition shieldDefinition = new ShipShieldEngineeringAdapter()
                .derive(derived).stream().findFirst().map(ShipShieldEngineeringAdapter.FittedShield::definition).orElse(null);
        ShieldFieldRuntime.State shieldState = shieldDefinition == null
                ? null : shieldRuntime.withEmitterIntegrity(
                        shieldDefinition,
                        ShieldFieldRuntime.State.charged(shieldDefinition),
                        initialIntegrity);
        return new SideState(
                doctrine,
                representedCount,
                hull,
                fit,
                consumables,
                damage,
                derived,
                sensors,
                shieldDefinition,
                shieldState,
                doctrine.initialConsumables().ammunitionCount());
    }

    private Exchange attack(SideState attacker, SideState defender, Scenario scenario, boolean leftToRight) {
        long attackerEntityId = leftToRight ? TARGET_ID_LEFT : TARGET_ID_RIGHT;
        long targetEntityId = leftToRight ? TARGET_ID_RIGHT : TARGET_ID_LEFT;
        double attackerX = leftToRight ? 0d : DEFAULT_RANGE_M;
        double targetX = leftToRight ? DEFAULT_RANGE_M : 0d;
        TrackState track = acquireTrack(
                attacker, defender, attackerEntityId, targetEntityId,
                attackerX, targetX, scenario.informationPreset());

        int kineticShots = 0;
        int guidedLaunches = 0;
        int beamDwells = 0;
        int defenseAssignments = 0;
        double guidedBurnKg = 0d;
        double beamEnergyJ = 0d;
        double shieldAbsorbedJ = 0d;
        double internalDamageJ = 0d;
        ConsumableState afterFire = attacker.consumables;
        Snapshot targetDamage = defender.damage;
        ShieldFieldRuntime.State targetShield = defender.shieldState;

        if (track != null && track.informationState() == InformationState.FIRE_CONTROL
                && (attacker.doctrine.id() == DoctrineId.A_KINETIC_LINE
                || attacker.doctrine.id() == DoctrineId.E_BALANCED_CONTROL)) {
            List<ShipWeaponEngineeringAdapter.FittedKineticMount> mounts = weaponAdapter.deriveKineticMounts(
                    attacker.derived, ammunition, launchers, attacker.doctrine.weaponLoadout());
            var primary = mounts.stream()
                    .filter(value -> value.mountId().equals("weapon_primary"))
                    .findFirst()
                    .orElse(null);
            if (primary != null && afterFire.ammunitionCount() > 0L) {
                var solution = fireControl.planKinetic(
                        primary.round(),
                        track,
                        new KinematicState(attackerX, 0d, 0d, 0d),
                        new TargetMotionEstimate(0d, 0d, 0d, 0d),
                        primary.pointingJitterRad(),
                        0d);
                if (solution.allowed()) {
                    AmmunitionRuntime.FireResult spent = ammunitionRuntime.consumeOne(
                            afterFire, primary.mountId(), primary.launcher(), primary.round().massKg());
                    afterFire = spent.consumables();
                    ProjectileBody body = fireControl.materializeKineticProjectile(
                            leftToRight ? 101L : 201L,
                            attackerEntityId,
                            1L,
                            primary.round(),
                            new KinematicState(attackerX, 0d, 0d, 0d),
                            solution);
                    ProjectileBody atImpact = body.advance(solution.timeOfFlightSeconds());
                    double missDistance = Math.hypot(atImpact.xM() - targetX, atImpact.yM());
                    if (missDistance <= Math.max(1d, defender.hull.boundingDimensionsM().widthM() * 0.5d)) {
                        KineticProtectionRuntime.ShieldInput shieldInput = defender.shieldDefinition == null
                                || targetShield == null ? null
                                : new KineticProtectionRuntime.ShieldInput(defender.shieldDefinition, targetShield);
                        KineticProtectionRuntime.Result protectionResult = kineticProtection.resolve(
                                atImpact,
                                shieldInput,
                                leftToRight ? Math.PI : 0d,
                                SHIELD_INTERACTION_SECONDS,
                                defender.hull.structuralProtectionStackId(),
                                0d,
                                defender.hull,
                                defender.fit,
                                protection.findHullDamageLayout(defender.hull.id()),
                                targetDamage,
                                new Vector3d(0d, KINETIC_HIT_Y_M, 0d));
                        if (protectionResult.shieldInteraction() != null) {
                            shieldAbsorbedJ += protectionResult.shieldInteraction().absorbedEnergyJ();
                            targetShield = protectionResult.shieldInteraction().state();
                        }
                        if (protectionResult.damageEvent() != null) {
                            internalDamageJ += protectionResult.damageEvent().compartmentDamageEnergyJ();
                            targetDamage = protectionResult.damageEvent().snapshot();
                        }
                    }
                    kineticShots++;
                }
            }
        }

        if (track != null && track.positionKnown()
                && (attacker.doctrine.id() == DoctrineId.B_MISSILE_STRIKE
                || attacker.doctrine.id() == DoctrineId.E_BALANCED_CONTROL)) {
            String mount = attacker.doctrine.id() == DoctrineId.B_MISSILE_STRIKE
                    ? "weapon_primary" : "weapon_secondary";
            WeaponLauncherCatalog.LauncherProfile profile = launchers.findByModuleId("module.test_weapon_missile_v1");
            String ammoId = attacker.doctrine.weaponLoadout()
                    .ammunitionContentId(mount, profile.ammunitionInterfaceId()).orElse(null);
            if (ammoId != null) {
                var ammo = ammunition.findGuided(ammoId);
                GuidedWeapon definition = ammo.toRuntimeWeapon();
                WeaponDefinition.Launcher launcher = new WeaponDefinition.Launcher(
                        "launcher.module.test_weapon_missile_v1",
                        profile.ammunitionInterfaceId(),
                        profile.ammunitionAmountPerShot(),
                        profile.cycleTimeSeconds(),
                        profile.supportChannelCount());
                if (ammunitionOnMount(afterFire, mount, profile.ammunitionInterfaceId()) >= 1L) {
                    AmmunitionRuntime.FireResult spent = ammunitionRuntime.consumeOne(
                            afterFire, mount, launcher, definition.wetMassKg());
                    afterFire = spent.consumables();
                    GuidedWeaponBody body = GuidedWeaponBody.launch(
                            leftToRight ? 301L : 401L,
                            attackerEntityId,
                            targetEntityId,
                            definition,
                            ammo.materialId(),
                            ammo.shape(),
                            ammo.lengthM(),
                            ammo.diameterM(),
                            ammo.impactPayloadId(),
                            attackerX,
                            0d,
                            leftToRight ? 500d : -500d,
                            0d);
                    GuidanceRuntime.GuidanceCommand command = guidanceRuntime.planLeadPursuit(
                            body,
                            track,
                            new TargetMotionEstimate(0d, 0d, 0d, 0d),
                            TrackSource.ONBOARD_SEEKER,
                            1d);
                    if (command.allowed()) {
                        GuidedWeaponBody burned = guidanceRuntime.execute(body, command);
                        guidedBurnKg += body.remainingPropellantKg() - burned.remainingPropellantKg();
                    }
                    guidedLaunches++;
                    if (scenario.protectedLogisticsAsset()) {
                        defenseAssignments += scheduleInterceptorProbe(
                                body, defender, scenario, targetX, leftToRight);
                    }
                }
            }
        }

        if (track != null && track.informationState() == InformationState.FIRE_CONTROL
                && attacker.doctrine.id() == DoctrineId.C_HIGH_MOBILITY_BEAM) {
            ModuleDefinition module = engineering.findModule("module.test_weapon_beam_v1");
            BeamWeapon beam = new BeamWeapon(
                    module.id(),
                    parameter(module, "wavelength_m"),
                    parameter(module, "aperture_diameter_m"),
                    parameter(module, "pointing_jitter_rad"),
                    parameter(module, "beam_power_w"),
                    module.peakPowerDemandW(),
                    module.wasteHeatW(),
                    parameter(module, "max_continuous_dwell_s"));
            BeamWeaponRuntime.BeamSolution solution = new BeamWeaponRuntime().plan(
                    beam, track, attackerX, 0d, 1d);
            if (solution.allowed()) {
                beamDwells++;
                beamEnergyJ += solution.deliveredBeamEnergyJ();
            }
        }

        return new Exchange(
                afterFire,
                targetDamage,
                targetShield,
                track == null ? InformationState.DETECTED : track.informationState(),
                kineticShots,
                guidedLaunches,
                beamDwells,
                defenseAssignments,
                guidedBurnKg,
                beamEnergyJ,
                shieldAbsorbedJ,
                internalDamageJ);
    }

    private TrackState acquireTrack(
            SideState attacker,
            SideState defender,
            long observerId,
            long targetId,
            double observerX,
            double targetX,
            InformationPreset preset) {
        FittedSensor radar = attacker.sensors.sensors().stream()
                .filter(value -> value.definition().mode() == Mode.ACTIVE_RADAR)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Stage 17.5I doctrine lost active radar"));
        ElectronicWarfareState ew = defender.sensors.staticSignature().jammerEmissionPowerW() <= 0d
                ? ElectronicWarfareState.empty()
                : new ElectronicWarfareState(
                        List.of(new NoiseJammer(
                                targetId,
                                targetX,
                                0d,
                                defender.sensors.staticSignature().jammerEmissionPowerW(),
                                1d,
                                JAMMER_WAVEFORM_OVERLAP)),
                        List.of());
        var observation = sensorRuntime.observe(
                observerId,
                targetId,
                radar.definition(),
                new SensorRuntimeState(true, true, 1d, 1d),
                new Position2d(observerX, 0d),
                new Position2d(targetX, 0d),
                defender.sensors.staticSignature(),
                ew,
                0d);
        if (observation.measurement().isEmpty()) {
            return null;
        }
        TrackState track = sensorRuntime.fuse(
                targetId,
                List.of(observation.measurement().orElseThrow()),
                DatalinkState.local(),
                TrackQualityPolicy.defaultPolicy(),
                0d);
        if (preset == InformationPreset.DEGRADED) {
            track = sensorRuntime.ageTrack(track, 120d, TrackQualityPolicy.defaultPolicy());
        }
        return track;
    }

    private int scheduleInterceptorProbe(
            GuidedWeaponBody inbound,
            SideState defender,
            Scenario scenario,
            double protectedCenterX,
            boolean inboundFromLeft) {
        GuidedWeapon interceptor = ammunition.findGuided("ammo.test_interceptor_750kg_v1").toRuntimeWeapon();
        double stationX = protectedCenterX + (inboundFromLeft ? -scenario.spacingM() : scenario.spacingM());
        DefenseStation station = new DefenseStation(
                inboundFromLeft ? 501L : 601L,
                stationX,
                0d,
                0d,
                interceptor,
                true,
                2,
                4L,
                scenario.thermalStressFraction() < 0.95d,
                5_000d);
        Threat threat = new Threat(
                inbound.bodyId(),
                inbound.xM(),
                inbound.yM(),
                inbound.velocityXMps(),
                inbound.velocityYMps(),
                inbound.currentMassKg(),
                inbound.guidanceAvailable());
        List<Assignment> assignments = defenseScheduler.schedule(
                new DefendedZone(protectedCenterX, 0d, 1_500d),
                List.of(threat),
                List.of(station));
        return assignments.size();
    }

    private SideResult resultFor(SideState own, Exchange ownAttack, Exchange incomingAttack) {
        DerivedShipState post = calculator.derive(
                own.hull,
                own.fit,
                ownAttack.afterFire,
                incomingAttack.targetDamage.moduleDamage());
        double sensorAperture = sensorAdapter.derive(post).sensors().stream()
                .mapToDouble(value -> value.definition().apertureAreaM2())
                .max().orElse(0d);
        return new SideResult(
                own.doctrine.id(),
                own.representedCount,
                own.derived.totalMassKg(),
                own.derived.maximumAccelerationMps2(),
                own.derived.deltaVMps(),
                own.derived.continuousPowerMarginW(),
                own.derived.heatMarginW(),
                own.consumables.ammunitionCount(),
                ownAttack.afterFire.ammunitionCount(),
                ownAttack.trackQuality,
                ownAttack.kineticShots,
                ownAttack.guidedLaunches,
                ownAttack.beamDwells,
                incomingAttack.defenseAssignments,
                ownAttack.guidedBurnKg,
                ownAttack.beamEnergyJ,
                incomingAttack.shieldAbsorbedJ,
                incomingAttack.internalDamageJ,
                post.continuousPowerMarginW(),
                sensorAperture);
    }

    private static ConsumableState scaleAmmunition(ConsumableState original, double fraction) {
        List<ConsumableLoad> loads = new ArrayList<>();
        for (ConsumableLoad load : original.interfaceLoads()) {
            if (load.kind() != ShipEngineeringCatalog.InterfaceKind.AMMUNITION) {
                loads.add(load);
                continue;
            }
            long count = (long) Math.floor(load.itemCount() * fraction + 1e-12d);
            double massPerItem = load.itemCount() <= 0L ? 0d : load.massKg() / load.itemCount();
            loads.add(new ConsumableLoad(
                    load.mountId(),
                    load.interfaceId(),
                    load.kind(),
                    count,
                    count * massPerItem,
                    count));
        }
        return new ConsumableState(
                original.cargoMassKg(),
                original.storesMassKg(),
                original.missionPayloadMassKg(),
                original.missionIntegrationVolumeM3(),
                loads);
    }

    private static Snapshot preDamagedSnapshot(HullDefinition hull, InstalledFit fit, double integrity) {
        TreeMap<String, Double> compartments = new TreeMap<>();
        hull.compartments().forEach(value -> compartments.put(value.id(), integrity));
        TreeMap<String, Double> mounts = new TreeMap<>();
        fit.installedModules().forEach(value -> mounts.put(value.mountId(), integrity));
        return new Snapshot(compartments, new DamageState(mounts));
    }

    private static long ammunitionOnMount(ConsumableState state, String mount, String interfaceId) {
        return state.interfaceLoads().stream()
                .filter(value -> value.kind() == ShipEngineeringCatalog.InterfaceKind.AMMUNITION)
                .filter(value -> value.mountId().equals(mount) && value.interfaceId().equals(interfaceId))
                .mapToLong(ConsumableLoad::itemCount)
                .sum();
    }

    private static double parameter(ModuleDefinition module, String key) {
        Double value = module.capabilityParameters().get(key);
        if (value == null || !Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException("module " + module.id() + " requires positive " + key);
        }
        return value;
    }

    private static String fingerprint(Scenario scenario, SideResult left, SideResult right) {
        String canonical = canonicalScenario(scenario) + "|" + canonicalSide(left) + "|" + canonicalSide(right);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String canonicalScenario(Scenario value) {
        return value.leftDoctrine().name() + ',' + value.rightDoctrine().name() + ','
                + value.leftCount() + ',' + value.rightCount() + ','
                + hex(value.spacingM()) + ',' + hex(value.ammunitionFraction()) + ','
                + hex(value.initialIntegrity()) + ',' + hex(value.thermalStressFraction()) + ','
                + value.informationPreset().name() + ',' + value.protectedLogisticsAsset();
    }

    private static String canonicalSide(SideResult value) {
        return value.doctrine().name() + ',' + value.representedShipCount() + ','
                + hex(value.loadedMassKg()) + ',' + hex(value.accelerationMps2()) + ',' + hex(value.deltaVMps()) + ','
                + hex(value.powerMarginW()) + ',' + hex(value.heatMarginW()) + ','
                + value.ammunitionBefore() + ',' + value.ammunitionAfter() + ',' + value.trackQuality().name() + ','
                + value.kineticShots() + ',' + value.guidedLaunches() + ',' + value.beamDwells() + ','
                + value.defenseAssignments() + ',' + hex(value.guidedPropellantBurnKg()) + ','
                + hex(value.deliveredBeamEnergyJ()) + ',' + hex(value.shieldAbsorbedJ()) + ','
                + hex(value.internalDamageJ()) + ',' + hex(value.postExchangePowerMarginW()) + ','
                + hex(value.postExchangeSensorApertureM2());
    }

    private static String hex(double value) {
        return Double.toHexString(value);
    }

    private static void requirePositiveFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }

    private static void requireUnitInterval(double value, String label) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(label + " must be in [0,1]");
        }
    }

    private static final class SideState {
        private final Doctrine doctrine;
        private final int representedCount;
        private final HullDefinition hull;
        private final InstalledFit fit;
        private final ConsumableState consumables;
        private final Snapshot damage;
        private final DerivedShipState derived;
        private final FittedSensorSuite sensors;
        private final ShieldFieldRuntime.Definition shieldDefinition;
        private final ShieldFieldRuntime.State shieldState;
        @SuppressWarnings("unused")
        private final long authoredAmmunitionCount;

        private SideState(
                Doctrine doctrine,
                int representedCount,
                HullDefinition hull,
                InstalledFit fit,
                ConsumableState consumables,
                Snapshot damage,
                DerivedShipState derived,
                FittedSensorSuite sensors,
                ShieldFieldRuntime.Definition shieldDefinition,
                ShieldFieldRuntime.State shieldState,
                long authoredAmmunitionCount) {
            this.doctrine = doctrine;
            this.representedCount = representedCount;
            this.hull = hull;
            this.fit = fit;
            this.consumables = consumables;
            this.damage = damage;
            this.derived = derived;
            this.sensors = sensors;
            this.shieldDefinition = shieldDefinition;
            this.shieldState = shieldState;
            this.authoredAmmunitionCount = authoredAmmunitionCount;
        }
    }

    private record Exchange(
            ConsumableState afterFire,
            Snapshot targetDamage,
            ShieldFieldRuntime.State targetShield,
            InformationState trackQuality,
            int kineticShots,
            int guidedLaunches,
            int beamDwells,
            int defenseAssignments,
            double guidedBurnKg,
            double beamEnergyJ,
            double shieldAbsorbedJ,
            double internalDamageJ) { }
}
