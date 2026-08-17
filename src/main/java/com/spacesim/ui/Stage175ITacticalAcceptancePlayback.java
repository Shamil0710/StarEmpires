package com.spacesim.ui;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.weapon.Stage175ICombatTestWeaponPack;
import com.spacesim.ship.AmmunitionRuntime;
import com.spacesim.ship.BeamWeaponRuntime;
import com.spacesim.ship.ElectronicWarfareState.DeceptionSource;
import com.spacesim.ship.GuidedWeaponBody;
import com.spacesim.ship.ProjectileBody;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import com.spacesim.ship.Stage175IFleetSaturationHarness;
import com.spacesim.ship.Stage175IPhysicalDestructionScenario;
import com.spacesim.ship.TrackCovariance;
import com.spacesim.ship.TrackState;
import com.spacesim.ship.WeaponDefinition.BeamWeapon;
import com.spacesim.ship.WeaponDefinition.Launcher;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BodyKind;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable three-frame projection used by the Stage-17.5I interactive tactical validation client.
 *
 * <p>Physical destruction is supplied by {@link Stage175IPhysicalDestructionScenario}; this class
 * only composes those authoritative snapshots with representative in-flight bodies, beam and EW
 * hypothesis glyphs. Desktop controls can therefore replay or step frames without owning combat
 * state or a mutation path back into simulation.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage175ITacticalAcceptancePlayback {
    private static final double ATTACKER_X_M = 260d;
    private static final double TARGET_X_M = 1_400d;
    private static final double CENTER_Y_M = 700d;
    private static final double MISSILE_Y_M = 1_000d;
    private static final double BEAM_Y_M = 420d;
    private static final long TARGET_ENTITY_ID = 91_004L;

    private Stage175ITacticalAcceptancePlayback() {
        throw new AssertionError("utility class");
    }

    /** One immutable labeled playback frame. */
    public record Frame(String title, TacticalPrototypeVisualSnapshot snapshot) {
        public Frame {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("title must be non-blank");
            }
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    /** Complete interactive playback and finite-resource bookkeeping. */
    public record Playback(
            List<Frame> frames,
            long initialKineticRounds,
            long remainingKineticRounds,
            long kineticRoundsConsumed,
            long missileRoundsConsumed,
            int defenseAssignments,
            double finalAccelerationMps2) {
        public Playback {
            Objects.requireNonNull(frames, "frames");
            frames = List.copyOf(frames);
            if (frames.size() != 3) {
                throw new IllegalArgumentException("Stage 17.5I tactical playback requires exactly three frames");
            }
            if (initialKineticRounds < 0L || remainingKineticRounds < 0L || kineticRoundsConsumed <= 0L
                    || missileRoundsConsumed <= 0L || defenseAssignments <= 0) {
                throw new IllegalArgumentException("physical acceptance counters are invalid");
            }
            if (!Double.isFinite(finalAccelerationMps2) || finalAccelerationMps2 < 0d) {
                throw new IllegalArgumentException("finalAccelerationMps2 must be finite and non-negative");
            }
        }
    }

    /**
     * Builds the deterministic engagement, penetration and wreck playback.
     *
     * @return immutable playback safe for repeated desktop presentation
     */
    public static Playback create() {
        Stage175IPhysicalDestructionScenario.Result destruction = Stage175IPhysicalDestructionScenario.run();
        ShipEngineeringCatalog engineering = Stage175ICombatTestContentPack.loadDoctrines();
        var ammunition = Stage175ICombatTestWeaponPack.loadAmmunition();
        var launchers = Stage175ICombatTestWeaponPack.loadLaunchers();

        ProjectileBody kinetic = inFlightKinetic(destruction.firstProjectile());
        MissileResult missile = launchPhysicalMissile(ammunition, launchers);
        GuidedWeaponBody interceptor = interceptor(ammunition, missile.body());
        int assignments = new Stage175IFleetSaturationHarness().run(
                new Stage175IFleetSaturationHarness.Scenario(1, 1, 1_000d, 1d, 1, 1L, true))
                .interceptorAssignments();
        if (assignments <= 0) {
            throw new IllegalStateException("finite interceptor fixture no longer assigns against one inbound missile");
        }
        BeamWeaponRuntime.BeamSolution beam = beamSolution(engineering);
        DeceptionSource deception = new DeceptionSource(
                TARGET_ENTITY_ID,
                "stage17_5i_interactive_false_contact",
                0.045d,
                120d,
                0.85d);

        var hull = destruction.hull();
        var pristine = destruction.pristineDamage();
        TacticalPrototypeVisualSnapshot engagement = new Stage175ITacticalVisualProjection()
                .addShip(91_001L, hull, pristine, ATTACKER_X_M, CENTER_Y_M, 0d, 0.72d, null, null)
                .addShip(91_002L, hull, pristine, ATTACKER_X_M + 45d, MISSILE_Y_M, 0d, 0.58d, null, null)
                .addShip(91_003L, hull, pristine, ATTACKER_X_M + 45d, BEAM_Y_M, 0d, 0.64d, null, null)
                .addShip(TARGET_ENTITY_ID, hull, pristine, TARGET_X_M, CENTER_Y_M, Math.PI, 0.18d,
                        destruction.fittedShield().definition(), destruction.chargedShield())
                .addKinetic(kinetic)
                .addGuided(missile.body(), false)
                .addGuided(interceptor, true)
                .addDeceptionHypothesis(
                        91_100L, ATTACKER_X_M, BEAM_Y_M, 0d, TARGET_X_M - ATTACKER_X_M, deception)
                .addBeam(91_200L, ATTACKER_X_M + 45d, BEAM_Y_M, TARGET_X_M, CENTER_Y_M, beam)
                .snapshot();

        TacticalPrototypeVisualSnapshot penetration = new Stage175ITacticalVisualProjection()
                .addShip(91_001L, hull, pristine, ATTACKER_X_M, CENTER_Y_M, 0d, 0.30d, null, null)
                .addShip(TARGET_ENTITY_ID, hull, destruction.firstPenetrationDamage(), TARGET_X_M, CENTER_Y_M,
                        Math.PI, 0d, destruction.fittedShield().definition(), destruction.firstPenetrationShield())
                .addImpact(91_201L, TARGET_X_M, CENTER_Y_M, destruction.firstPenetratingImpact())
                .snapshot();

        TacticalPrototypeVisualSnapshot wreck = new Stage175ITacticalVisualProjection()
                .addShip(91_001L, hull, pristine, ATTACKER_X_M, CENTER_Y_M, 0d, 0.10d, null, null)
                .addShip(TARGET_ENTITY_ID, hull, destruction.finalDamage(), TARGET_X_M, CENTER_Y_M,
                        Math.PI, 0d, destruction.fittedShield().definition(), destruction.finalShield())
                .addImpact(91_202L, TARGET_X_M, CENTER_Y_M, destruction.lastImpact())
                .snapshot();

        return new Playback(
                List.of(
                        new Frame("ENGAGEMENT / KINETIC + MISSILE + INTERCEPTOR + BEAM + EW", engagement),
                        new Frame("PENETRATION / SHIELD + ARMOR + LOCAL DAMAGE", penetration),
                        new Frame("WRECK / SUBSYSTEM LOSS + DEBRIS", wreck)),
                destruction.initialPrimaryRounds(),
                destruction.remainingPrimaryRounds(),
                destruction.shotsConsumed(),
                missile.roundsConsumed(),
                assignments,
                destruction.finalAccelerationMps2());
    }

    private static ProjectileBody inFlightKinetic(ProjectileBody source) {
        return new ProjectileBody(
                source.projectileId(),
                source.sourceEntityId(),
                source.spawnTick(),
                source.materialId(),
                source.shape(),
                source.lengthM(),
                source.diameterM(),
                source.massKg(),
                760d,
                CENTER_Y_M,
                source.velocityXMps(),
                source.velocityYMps());
    }

    private static MissileResult launchPhysicalMissile(
            com.spacesim.content.weapon.WeaponAmmunitionCatalog ammunition,
            com.spacesim.content.weapon.WeaponLauncherCatalog launchers) {
        var doctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.B_MISSILE_STRIKE);
        var profile = launchers.findByModuleId("module.test_weapon_missile_v1");
        var content = ammunition.findGuided("ammo.test_anti_ship_missile_2t_v1");
        Launcher launcher = new Launcher(
                "launcher.module.test_weapon_missile_v1",
                profile.ammunitionInterfaceId(),
                profile.ammunitionAmountPerShot(),
                profile.cycleTimeSeconds(),
                profile.supportChannelCount());
        long before = doctrine.initialConsumables().interfaceLoads().stream()
                .filter(value -> value.mountId().equals("weapon_primary"))
                .mapToLong(com.spacesim.ship.ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
        var after = new AmmunitionRuntime().consumeOne(
                doctrine.initialConsumables(), "weapon_primary", launcher, content.toRuntimeWeapon().wetMassKg())
                .consumables();
        long remaining = after.interfaceLoads().stream()
                .filter(value -> value.mountId().equals("weapon_primary"))
                .mapToLong(com.spacesim.ship.ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
        double dx = TARGET_X_M - (ATTACKER_X_M + 45d);
        double dy = CENTER_Y_M - MISSILE_Y_M;
        double length = Math.hypot(dx, dy);
        GuidedWeaponBody body = GuidedWeaponBody.launch(
                92_001L,
                91_002L,
                TARGET_ENTITY_ID,
                content.toRuntimeWeapon(),
                content.materialId(),
                content.shape(),
                content.lengthM(),
                content.diameterM(),
                content.impactPayloadId(),
                720d,
                900d,
                650d * dx / length,
                650d * dy / length);
        return new MissileResult(body, before - remaining);
    }

    private static GuidedWeaponBody interceptor(
            com.spacesim.content.weapon.WeaponAmmunitionCatalog ammunition,
            GuidedWeaponBody inbound) {
        var content = ammunition.findGuided("ammo.test_interceptor_750kg_v1");
        return GuidedWeaponBody.launch(
                92_002L,
                91_050L,
                inbound.bodyId(),
                content.toRuntimeWeapon(),
                content.materialId(),
                content.shape(),
                content.lengthM(),
                content.diameterM(),
                content.impactPayloadId(),
                1_120d,
                890d,
                -500d,
                -75d);
    }

    private static BeamWeaponRuntime.BeamSolution beamSolution(ShipEngineeringCatalog engineering) {
        var module = engineering.findModule("module.test_weapon_beam_v1");
        Map<String, Double> parameters = module.capabilityParameters();
        BeamWeapon beam = new BeamWeapon(
                module.id(),
                positive(parameters, "wavelength_m"),
                positive(parameters, "aperture_diameter_m"),
                positive(parameters, "pointing_jitter_rad"),
                positive(parameters, "beam_power_w"),
                module.peakPowerDemandW(),
                module.wasteHeatW(),
                positive(parameters, "max_continuous_dwell_s"));
        double dx = TARGET_X_M - (ATTACKER_X_M + 45d);
        double dy = CENTER_Y_M - BEAM_Y_M;
        double range = Math.hypot(dx, dy);
        TrackState track = new TrackState(
                TARGET_ENTITY_ID,
                TrackState.InformationState.FIRE_CONTROL,
                true,
                range,
                Math.atan2(dy, dx),
                new TrackCovariance(16d, 1e-8d, 64d),
                0.98d,
                0d,
                2,
                4);
        BeamWeaponRuntime.BeamSolution result = new BeamWeaponRuntime().plan(
                beam, track, ATTACKER_X_M + 45d, BEAM_Y_M, 1d);
        if (!result.allowed()) {
            throw new IllegalStateException("Stage 17.5I playback lost valid physical beam solution");
        }
        return result;
    }

    private static double positive(Map<String, Double> parameters, String key) {
        Double value = parameters.get(key);
        if (value == null || !Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException("missing positive beam parameter: " + key);
        }
        return value;
    }

    private record MissileResult(GuidedWeaponBody body, long roundsConsumed) {
    }
}
