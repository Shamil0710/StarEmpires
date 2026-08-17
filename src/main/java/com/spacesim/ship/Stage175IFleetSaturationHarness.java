package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.weapon.Stage175ICombatTestWeaponPack;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponLauncherCatalog;
import com.spacesim.ship.LayeredDefenseScheduler.Assignment;
import com.spacesim.ship.LayeredDefenseScheduler.DefendedZone;
import com.spacesim.ship.LayeredDefenseScheduler.DefenseStation;
import com.spacesim.ship.LayeredDefenseScheduler.Threat;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.Doctrine;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import com.spacesim.ship.WeaponDefinition.GuidedWeapon;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Physical multi-body saturation seam for Stage-17.5I deterministic fleet acceptance.
 *
 * <p>Unlike the representative pair-exchange harness, this class materializes every attacking ship
 * copy as its own physical ammunition state and every launched missile as its own
 * {@link GuidedWeaponBody}. The defended screen is likewise an explicit list of finite-resource
 * {@link DefenseStation} states. No probability, aggregate hit score, free ammunition or class-name
 * multiplier is introduced.</p>
 *
 * <p>The interceptor screen is an acceptance fixture for the generic layered-defense subsystem. Its
 * finite ammunition and support-channel quantities are explicit scenario inputs rather than being
 * silently borrowed from the current provisional doctrine-D ship fit. Final doctrine composition
 * remains Stage-22 content work.</p>
 */
public final class Stage175IFleetSaturationHarness {
    private static final double TARGET_X_M = 300_000d;
    private static final double DEFENSE_LINE_OFFSET_M = 60_000d;
    private static final double DEFENDED_RADIUS_M = 1_500d;
    private static final double SAFE_INTERCEPT_DISTANCE_M = 5_000d;
    private static final double MISSILE_INITIAL_SPEED_MPS = 1_000d;
    private static final String MISSILE_MODULE_ID = "module.test_weapon_missile_v1";
    private static final String MISSILE_AMMO_ID = "ammo.test_anti_ship_missile_2t_v1";
    private static final String INTERCEPTOR_AMMO_ID = "ammo.test_interceptor_750kg_v1";

    private final ShipEngineeringCatalog engineering = Stage175ICombatTestContentPack.loadDoctrines();
    private final WeaponAmmunitionCatalog ammunition = Stage175ICombatTestWeaponPack.loadAmmunition();
    private final WeaponLauncherCatalog launchers = Stage175ICombatTestWeaponPack.loadLaunchers();
    private final AmmunitionRuntime ammunitionRuntime = new AmmunitionRuntime();
    private final LayeredDefenseScheduler defenseScheduler = new LayeredDefenseScheduler();
    private final DerivedShipCalculator calculator = new DerivedShipCalculator(engineering);

    /**
     * One physical saturation scenario.
     *
     * @param attackingShips number of independently materialized missile-strike ships
     * @param defenseStations number of independently materialized interceptor-screen stations
     * @param formationSpacingM center-to-center formation spacing in meters
     * @param ammunitionFraction fraction [0,1] of each attacking ship's authored ammunition loaded
     * @param supportChannelsPerStation finite guidance/fire-control channels per defense station
     * @param interceptorRoundsPerStation finite physical interceptor rounds per defense station
     * @param thermalAvailable whether defense launcher thermal duty currently permits launch
     */
    public record Scenario(
            int attackingShips,
            int defenseStations,
            double formationSpacingM,
            double ammunitionFraction,
            int supportChannelsPerStation,
            long interceptorRoundsPerStation,
            boolean thermalAvailable) {
        /**
         * Validates one saturation scenario.
         *
         * @param attackingShips number of independently materialized missile-strike ships
         * @param defenseStations number of independently materialized interceptor-screen stations
         * @param formationSpacingM center-to-center formation spacing in meters
         * @param ammunitionFraction fraction [0,1] of each attacking ship's authored ammunition loaded
         * @param supportChannelsPerStation finite guidance/fire-control channels per defense station
         * @param interceptorRoundsPerStation finite physical interceptor rounds per defense station
         * @param thermalAvailable whether defense launcher thermal duty currently permits launch
         */
        public Scenario {
            if (attackingShips <= 0) {
                throw new IllegalArgumentException("attackingShips must be positive");
            }
            if (defenseStations < 0) {
                throw new IllegalArgumentException("defenseStations must be non-negative");
            }
            requirePositiveFinite(formationSpacingM, "formationSpacingM");
            if (!Double.isFinite(ammunitionFraction) || ammunitionFraction < 0d || ammunitionFraction > 1d) {
                throw new IllegalArgumentException("ammunitionFraction must be in [0,1]");
            }
            if (supportChannelsPerStation < 0) {
                throw new IllegalArgumentException("supportChannelsPerStation must be non-negative");
            }
            if (interceptorRoundsPerStation < 0L) {
                throw new IllegalArgumentException("interceptorRoundsPerStation must be non-negative");
            }
        }

        /** @return canonical four-ship/four-station saturation fixture */
        public static Scenario nominal() {
            return new Scenario(4, 4, 12_000d, 1d, 2, 4L, true);
        }
    }

    /**
     * Deterministic physical saturation result.
     *
     * @param scenario exact scenario inputs
     * @param attackingFleetMassKg production-derived loaded mass of all attacking ship copies
     * @param attackingMissileRoundsBefore physical primary-feed rounds before launch across all attackers
     * @param attackingMissileRoundsAfter physical primary-feed rounds after launch across all attackers
     * @param missilesLaunched number of actual guided bodies materialized
     * @param launchedMissileMassKg total wet physical mass of materialized guided bodies
     * @param interceptorRoundsAvailable explicit physical interceptor rounds across the defense screen
     * @param interceptorAssignments number of finite-resource defense assignments committed by scheduler
     * @param unassignedThreats launched bodies with no feasible/available interceptor assignment
     * @param meanPlannedInterceptSeconds arithmetic mean planned intercept time, zero when no assignment exists
     * @param fingerprint lowercase SHA-256 over canonical physical inputs and assignments
     */
    public record Result(
            Scenario scenario,
            double attackingFleetMassKg,
            long attackingMissileRoundsBefore,
            long attackingMissileRoundsAfter,
            int missilesLaunched,
            double launchedMissileMassKg,
            long interceptorRoundsAvailable,
            int interceptorAssignments,
            int unassignedThreats,
            double meanPlannedInterceptSeconds,
            String fingerprint) {
        /**
         * Validates an immutable saturation result.
         *
         * @param scenario exact scenario inputs
         * @param attackingFleetMassKg production-derived loaded mass of all attacking ship copies
         * @param attackingMissileRoundsBefore physical primary-feed rounds before launch across all attackers
         * @param attackingMissileRoundsAfter physical primary-feed rounds after launch across all attackers
         * @param missilesLaunched number of actual guided bodies materialized
         * @param launchedMissileMassKg total wet physical mass of materialized guided bodies
         * @param interceptorRoundsAvailable explicit physical interceptor rounds across the defense screen
         * @param interceptorAssignments number of finite-resource defense assignments committed by scheduler
         * @param unassignedThreats launched bodies with no feasible/available interceptor assignment
         * @param meanPlannedInterceptSeconds arithmetic mean planned intercept time, zero when no assignment exists
         * @param fingerprint lowercase SHA-256 over canonical physical inputs and assignments
         */
        public Result {
            Objects.requireNonNull(scenario, "scenario");
            requirePositiveFinite(attackingFleetMassKg, "attackingFleetMassKg");
            if (attackingMissileRoundsBefore < 0L || attackingMissileRoundsAfter < 0L) {
                throw new IllegalArgumentException("attacking missile round counts must be non-negative");
            }
            if (missilesLaunched < 0 || interceptorAssignments < 0 || unassignedThreats < 0) {
                throw new IllegalArgumentException("body/assignment counts must be non-negative");
            }
            requireNonNegativeFinite(launchedMissileMassKg, "launchedMissileMassKg");
            if (interceptorRoundsAvailable < 0L) {
                throw new IllegalArgumentException("interceptorRoundsAvailable must be non-negative");
            }
            requireNonNegativeFinite(meanPlannedInterceptSeconds, "meanPlannedInterceptSeconds");
            if (fingerprint == null || fingerprint.length() != 64) {
                throw new IllegalArgumentException("fingerprint must be a SHA-256 hex string");
            }
        }
    }

    /**
     * Materializes and schedules one physical multi-body missile saturation scenario.
     *
     * @param scenario exact scenario inputs
     * @return deterministic physical result
     */
    public Result run(Scenario scenario) {
        Scenario checked = Objects.requireNonNull(scenario, "scenario");
        Doctrine missileDoctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.B_MISSILE_STRIKE);
        WeaponLauncherCatalog.LauncherProfile launcherProfile = launchers.findByModuleId(MISSILE_MODULE_ID);
        GuidedWeapon missile = ammunition.findGuided(MISSILE_AMMO_ID).toRuntimeWeapon();
        GuidedWeapon interceptor = ammunition.findGuided(INTERCEPTOR_AMMO_ID).toRuntimeWeapon();
        WeaponDefinition.Launcher launcher = new WeaponDefinition.Launcher(
                "launcher." + MISSILE_MODULE_ID,
                launcherProfile.ammunitionInterfaceId(),
                launcherProfile.ammunitionAmountPerShot(),
                launcherProfile.cycleTimeSeconds(),
                launcherProfile.supportChannelCount());

        double perShipMassKg = deriveLoadedShipMass(missileDoctrine, checked.ammunitionFraction());
        long primaryRoundsBefore = 0L;
        long primaryRoundsAfter = 0L;
        double launchedMassKg = 0d;
        List<Threat> threats = new ArrayList<>();
        for (int index = 0; index < checked.attackingShips(); index++) {
            ConsumableState before = scaleAmmunition(missileDoctrine.initialConsumables(), checked.ammunitionFraction());
            long roundsBefore = ammunitionOnMount(
                    before, "weapon_primary", launcherProfile.ammunitionInterfaceId());
            primaryRoundsBefore = Math.addExact(primaryRoundsBefore, roundsBefore);
            ConsumableState after = before;
            if (roundsBefore > 0L) {
                after = ammunitionRuntime.consumeOne(
                        before, "weapon_primary", launcher, missile.wetMassKg()).consumables();
                GuidedWeaponBody body = launchThreat(index, missile);
                threats.add(new Threat(
                        body.bodyId(),
                        body.xM(),
                        body.yM(),
                        body.velocityXMps(),
                        body.velocityYMps(),
                        body.currentMassKg(),
                        body.guidanceAvailable()));
                launchedMassKg += body.currentMassKg();
            }
            primaryRoundsAfter = Math.addExact(
                    primaryRoundsAfter,
                    ammunitionOnMount(after, "weapon_primary", launcherProfile.ammunitionInterfaceId()));
        }

        List<DefenseStation> stations = new ArrayList<>();
        for (int index = 0; index < checked.defenseStations(); index++) {
            stations.add(defenseStation(index, checked, interceptor));
        }
        List<Assignment> assignments = defenseScheduler.schedule(
                new DefendedZone(TARGET_X_M, 0d, DEFENDED_RADIUS_M),
                threats,
                stations);
        long interceptorRoundsAvailable = Math.multiplyExact(
                checked.interceptorRoundsPerStation(),
                (long) checked.defenseStations());
        double meanInterceptSeconds = assignments.stream()
                .mapToDouble(Assignment::plannedInterceptSeconds)
                .average()
                .orElse(0d);
        String fingerprint = fingerprint(checked, threats, stations, assignments);
        return new Result(
                checked,
                perShipMassKg * checked.attackingShips(),
                primaryRoundsBefore,
                primaryRoundsAfter,
                threats.size(),
                launchedMassKg,
                interceptorRoundsAvailable,
                assignments.size(),
                threats.size() - assignments.size(),
                meanInterceptSeconds,
                fingerprint);
    }

    private double deriveLoadedShipMass(Doctrine doctrine, double ammunitionFraction) {
        var hull = engineering.findHull("hull.test_doctrine_destroyer_v1");
        InstalledFit fit = InstalledFit.fromDemonstrator(engineering.findDemonstratorFit(doctrine.fitId()));
        return calculator.derive(
                hull,
                fit,
                scaleAmmunition(doctrine.initialConsumables(), ammunitionFraction),
                DamageState.pristine()).totalMassKg();
    }

    private GuidedWeaponBody launchThreat(int index, GuidedWeapon missile) {
        double yM = centeredOffset(index, 1, 0d);
        return launchThreatAt(index, missile, yM);
    }

    private GuidedWeaponBody launchThreatAt(int index, GuidedWeapon missile, double yM) {
        double dx = TARGET_X_M;
        double dy = -yM;
        double length = Math.hypot(dx, dy);
        double velocityXMps = MISSILE_INITIAL_SPEED_MPS * dx / length;
        double velocityYMps = MISSILE_INITIAL_SPEED_MPS * dy / length;
        var content = ammunition.findGuided(MISSILE_AMMO_ID);
        return GuidedWeaponBody.launch(
                100_000L + index,
                10_000L + index,
                900_000L,
                missile,
                content.materialId(),
                content.shape(),
                content.lengthM(),
                content.diameterM(),
                content.impactPayloadId(),
                0d,
                yM,
                velocityXMps,
                velocityYMps);
    }

    private DefenseStation defenseStation(int index, Scenario scenario, GuidedWeapon interceptor) {
        double yM = centeredOffset(index, scenario.defenseStations(), scenario.formationSpacingM());
        return new DefenseStation(
                200_000L + index,
                TARGET_X_M - DEFENSE_LINE_OFFSET_M,
                yM,
                0d,
                interceptor,
                true,
                scenario.supportChannelsPerStation(),
                scenario.interceptorRoundsPerStation(),
                scenario.thermalAvailable(),
                SAFE_INTERCEPT_DISTANCE_M);
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

    private static long ammunitionOnMount(ConsumableState state, String mountId, String interfaceId) {
        return state.interfaceLoads().stream()
                .filter(load -> load.kind() == ShipEngineeringCatalog.InterfaceKind.AMMUNITION)
                .filter(load -> load.mountId().equals(mountId) && load.interfaceId().equals(interfaceId))
                .mapToLong(ConsumableLoad::itemCount)
                .sum();
    }

    private static double centeredOffset(int index, int count, double spacingM) {
        if (count <= 1) {
            return 0d;
        }
        return (index - (count - 1d) * 0.5d) * spacingM;
    }

    private static String fingerprint(
            Scenario scenario,
            List<Threat> threats,
            List<DefenseStation> stations,
            List<Assignment> assignments) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(scenario.attackingShips()).append('|')
                .append(scenario.defenseStations()).append('|')
                .append(Double.toHexString(scenario.formationSpacingM())).append('|')
                .append(Double.toHexString(scenario.ammunitionFraction())).append('|')
                .append(scenario.supportChannelsPerStation()).append('|')
                .append(scenario.interceptorRoundsPerStation()).append('|')
                .append(scenario.thermalAvailable());
        for (Threat threat : threats) {
            canonical.append("|T:").append(threat.threatId()).append(',')
                    .append(Double.toHexString(threat.xM())).append(',')
                    .append(Double.toHexString(threat.yM())).append(',')
                    .append(Double.toHexString(threat.velocityXMps())).append(',')
                    .append(Double.toHexString(threat.velocityYMps())).append(',')
                    .append(Double.toHexString(threat.physicalMassKg()));
        }
        for (DefenseStation station : stations) {
            canonical.append("|S:").append(station.stationId()).append(',')
                    .append(Double.toHexString(station.xM())).append(',')
                    .append(Double.toHexString(station.yM())).append(',')
                    .append(station.supportChannelsAvailable()).append(',')
                    .append(station.ammunitionRounds()).append(',')
                    .append(station.thermalAvailable());
        }
        for (Assignment assignment : assignments) {
            canonical.append("|A:").append(assignment.threatId()).append(',')
                    .append(assignment.stationId()).append(',')
                    .append(Double.toHexString(assignment.plannedInterceptSeconds())).append(',')
                    .append(Double.toHexString(assignment.interceptXM())).append(',')
                    .append(Double.toHexString(assignment.interceptYM()));
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void requirePositiveFinite(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }

    private static void requireNonNegativeFinite(double value, String label) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }
}
