package com.spacesim.combat.acceptance;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.Vector3d;
import com.spacesim.ship.AmmunitionRuntime;
import com.spacesim.ship.BeamProtectionRuntime;
import com.spacesim.ship.BeamWeaponRuntime.BeamSolution;
import com.spacesim.ship.GuidanceRuntime;
import com.spacesim.ship.GuidedImpactRuntime;
import com.spacesim.ship.GuidedWeaponBody;
import com.spacesim.ship.KineticProtectionRuntime;
import com.spacesim.ship.LayeredDefenseScheduler;
import com.spacesim.ship.LayeredDefenseScheduler.DefendedZone;
import com.spacesim.ship.LayeredDefenseScheduler.DefenseStation;
import com.spacesim.ship.LayeredDefenseScheduler.Threat;
import com.spacesim.ship.ShipBeamEngineeringAdapter;
import com.spacesim.ship.ShipBeamEngineeringService;
import com.spacesim.ship.ShipCapabilityService;
import com.spacesim.ship.ShipGuidedWeaponEngineeringAdapter;
import com.spacesim.ship.ShipGuidedWeaponEngineeringAdapter.FittedGuidedMount;
import com.spacesim.ship.ShipInstanceRuntimeState;
import com.spacesim.ship.ShipKineticProtectionService;
import com.spacesim.ship.ShipShieldEngineeringAdapter;
import com.spacesim.ship.ShipWeaponEngineeringAdapter;
import com.spacesim.ship.ShieldFieldRuntime;
import com.spacesim.ship.TrackCovariance;
import com.spacesim.ship.TrackState;
import com.spacesim.ship.TrackState.InformationState;
import com.spacesim.ship.WeaponFireControl;
import com.spacesim.ship.WeaponFireControl.KinematicState;
import com.spacesim.ship.WeaponFireControl.TargetMotionEstimate;
import com.spacesim.ship.WeaponMountRuntime;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Bounded deterministic Stage-17.5I multi-fleet opening-exchange acceptance runner.
 *
 * <p>The runner is intentionally not a second combat model and does not produce a hidden win score.
 * It materializes ordinary production fits, places them in deterministic physical geometry, then
 * exercises existing track requirements, launcher cycles, ammunition consumption, kinetic/beam/
 * guided bodies, layered defense, shields, fitted armor and local damage. Its output is an ordered
 * event/state fingerprint suitable for regression comparison across the required fleet matrix.</p>
 */
public final class Stage175IFleetAcceptanceRunner {
    private static final int EXCHANGE_ROUNDS = 2;
    private static final double ROUND_SECONDS = 8d;
    private static final double BEAM_DWELL_SECONDS = 3d;
    private static final double GUIDED_INITIAL_SPEED_MPS = 500d;
    private static final double DEFENSE_SAFE_INTERCEPT_RANGE_M = 1_000d;

    private final Stage175ICombatTestContentPack pack;
    private final Stage175IShipMaterializer materializer;
    private final ShipCapabilityService capabilities;
    private final ShipWeaponEngineeringAdapter kineticAdapter = new ShipWeaponEngineeringAdapter();
    private final ShipGuidedWeaponEngineeringAdapter guidedAdapter = new ShipGuidedWeaponEngineeringAdapter();
    private final ShipBeamEngineeringAdapter beamAdapter;
    private final ShipBeamEngineeringService beamEngineering;
    private final ShipKineticProtectionService kineticProtection;
    private final BeamProtectionRuntime beamProtection;
    private final WeaponFireControl fireControl = new WeaponFireControl();
    private final AmmunitionRuntime ammunition = new AmmunitionRuntime();
    private final WeaponMountRuntime weaponMounts = new WeaponMountRuntime();
    private final GuidanceRuntime guidance = new GuidanceRuntime();
    private final GuidedImpactRuntime guidedImpact = new GuidedImpactRuntime();
    private final LayeredDefenseScheduler defenseScheduler = new LayeredDefenseScheduler();
    private final ShipShieldEngineeringAdapter shieldAdapter = new ShipShieldEngineeringAdapter();

    /**
     * Creates one deterministic acceptance runner over an exact content pack.
     *
     * @param pack validated production-valid/content-provisional acceptance content
     */
    public Stage175IFleetAcceptanceRunner(Stage175ICombatTestContentPack pack) {
        this.pack = Objects.requireNonNull(pack, "pack");
        this.materializer = new Stage175IShipMaterializer(pack);
        this.capabilities = new ShipCapabilityService(pack.engineering());
        this.beamAdapter = new ShipBeamEngineeringAdapter(pack.engineering());
        this.beamEngineering = new ShipBeamEngineeringService(pack.engineering());
        this.kineticProtection = new ShipKineticProtectionService(
                pack.engineering(), pack.protection(), pack.armorModules());
        this.beamProtection = new BeamProtectionRuntime(pack.engineering(), pack.beamMaterials());
    }

    /**
     * Executes one required matchup/variation pair through the bounded physical opening exchange.
     *
     * @param matchupId stable manifest matchup ID
     * @param variationId stable manifest variation ID
     * @return immutable deterministic physical result and SHA-256 result fingerprint
     */
    public ScenarioResult run(String matchupId, String variationId) {
        Stage175ICombatTestManifest.MatchupDefinition matchup = pack.manifest().matchups().stream()
                .filter(value -> value.id().equals(matchupId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Stage-17.5I matchup: " + matchupId));
        Stage175ICombatTestManifest.VariationDefinition variation = pack.manifest().findVariation(variationId);
        if (variation == null) {
            throw new IllegalArgumentException("Unknown Stage-17.5I variation: " + variationId);
        }
        Stage175ICombatTestManifest.FleetDefinition fleetA = requireFleet(matchup.fleetAId());
        Stage175ICombatTestManifest.FleetDefinition fleetB = requireFleet(matchup.fleetBId());
        List<MutableShip> ships = new ArrayList<>();
        ships.addAll(materializeFleet(fleetA, Side.A, variation, 1_000L));
        ships.addAll(materializeFleet(fleetB, Side.B, variation, 2_000L));
        ships.sort(Comparator.comparingLong(ship -> ship.entityId));

        List<CombatEvent> events = new ArrayList<>();
        long sequence = 1L;
        for (int round = 0; round < EXCHANGE_ROUNDS; round++) {
            if (round > 0) {
                for (MutableShip ship : ships) {
                    advanceWeaponCycles(ship);
                }
            }
            for (MutableShip source : ships) {
                MutableShip target = nearestOpponent(source, ships);
                if (target == null) {
                    continue;
                }
                CombatEvent event = executePrimaryAction(sequence, round, source, target, ships, variation);
                events.add(event);
                sequence++;
            }
        }

        SideSummary sideA = summarize(Side.A, ships);
        SideSummary sideB = summarize(Side.B, ships);
        Map<EventType, Integer> counts = new EnumMap<>(EventType.class);
        for (CombatEvent event : events) {
            counts.merge(event.type(), 1, Integer::sum);
        }
        String fingerprint = fingerprint(matchup.id(), variation.id(), events, sideA, sideB);
        return new ScenarioResult(
                matchup.id(),
                variation.id(),
                pack.fingerprint(),
                List.copyOf(events),
                sideA,
                sideB,
                Map.copyOf(counts),
                fingerprint);
    }

    private CombatEvent executePrimaryAction(
            long sequence,
            int round,
            MutableShip source,
            MutableShip target,
            List<MutableShip> ships,
            Stage175ICombatTestManifest.VariationDefinition variation) {
        ShipCapabilityService.Snapshot sourceSnapshot = capabilities.snapshot(source.engineering);
        TrackState track = trackFor(target, variation);

        List<ShipWeaponEngineeringAdapter.FittedKineticMount> kinetic = kineticAdapter.deriveKineticMounts(
                sourceSnapshot.derived(), pack.ammunition(), pack.launchers(),
                source.engineering.instanceState.weaponLoadout());
        for (ShipWeaponEngineeringAdapter.FittedKineticMount mount : kinetic) {
            if (weaponMounts.ready(source.engineering.instanceState.weaponMountRuntime(), mount.mountId())) {
                return executeKinetic(sequence, round, source, target, mount, track);
            }
        }

        List<ShipBeamEngineeringAdapter.FittedBeamMount> beams = beamAdapter.deriveBeamMounts(sourceSnapshot.derived());
        if (!beams.isEmpty()) {
            return executeBeam(sequence, round, source, target, beams.get(0), track);
        }

        List<FittedGuidedMount> guided = guidedAdapter.deriveGuidedMounts(
                sourceSnapshot.derived(), pack.ammunition(), pack.launchers(),
                source.engineering.instanceState.weaponLoadout()).stream()
                .filter(value -> !value.launcher().ammunitionInterfaceId().contains("interceptor"))
                .toList();
        for (FittedGuidedMount mount : guided) {
            if (weaponMounts.ready(source.engineering.instanceState.weaponMountRuntime(), mount.mountId())) {
                return executeGuided(sequence, round, source, target, ships, mount, track);
            }
        }
        return new CombatEvent(sequence, round, EventType.NO_OFFENSIVE_ACTION,
                source.entityId, target.entityId, "", 0d, 0d, "no ready offensive fitted weapon");
    }

    private CombatEvent executeKinetic(
            long sequence,
            int round,
            MutableShip source,
            MutableShip target,
            ShipWeaponEngineeringAdapter.FittedKineticMount mount,
            TrackState track) {
        WeaponFireControl.KineticSolution solution = fireControl.planKinetic(
                mount.round(),
                track,
                kinematics(source),
                new TargetMotionEstimate(target.xM, target.yM, 0d, 0d),
                mount.pointingJitterRad(),
                0d);
        if (!solution.allowed()) {
            return denied(sequence, round, source, target, mount.mountId(), track, "kinetic");
        }
        AmmunitionRuntime.ConsumptionResult consumed;
        try {
            consumed = ammunition.consumeOne(
                    source.engineering.runtimeState.consumables(),
                    mount.mountId(),
                    mount.launcher(),
                    mount.round().massKg());
        } catch (IllegalArgumentException exception) {
            return new CombatEvent(sequence, round, EventType.AMMUNITION_DENIED,
                    source.entityId, target.entityId, mount.mountId(), 0d, 0d, "kinetic feed empty");
        }
        replaceConsumables(source, consumed.consumables());
        commitCycle(source, mount.mountId(), mount.launcher());
        var projectile = fireControl.materializeKineticProjectile(
                1_000_000L + sequence,
                source.entityId,
                round,
                mount.round(),
                kinematics(source),
                solution);
        TargetProtection targetProtection = targetProtection(target);
        ShipKineticProtectionService.Result result = kineticProtection.resolve(
                projectile,
                targetProtection.kineticShield(),
                direction(target, source),
                Math.max(0.001d, solution.timeToInterceptSeconds()),
                0d,
                targetProtection.hull(),
                target.engineering.fit,
                targetProtection.layout(),
                target.engineering.instanceState.damage(),
                targetProtection.hitPoint());
        commitTargetProtection(target, targetProtection, result.shieldInteraction(), result.damageEvent());
        double internalJ = result.damageEvent() == null ? 0d : result.damageEvent().compartmentDamageEnergyJ();
        return new CombatEvent(sequence, round, EventType.KINETIC_IMPACT,
                source.entityId, target.entityId, mount.mountId(), projectile.kineticEnergyJ(), internalJ,
                result.internalDamageOccurred() ? "local damage" : "protection stopped/contained impact");
    }

    private CombatEvent executeBeam(
            long sequence,
            int round,
            MutableShip source,
            MutableShip target,
            ShipBeamEngineeringAdapter.FittedBeamMount mount,
            TrackState track) {
        BeamSolution solution = beamEngineering.planAndCommit(
                source.engineering,
                mount.mountId(),
                mount.weapon(),
                track,
                source.xM,
                source.yM,
                BEAM_DWELL_SECONDS);
        if (solution == null) {
            return new CombatEvent(sequence, round, EventType.ENGINEERING_DENIED,
                    source.entityId, target.entityId, mount.mountId(), 0d, 0d,
                    "beam power/thermal grant denied");
        }
        if (!solution.allowed()) {
            return denied(sequence, round, source, target, mount.mountId(), track, "beam");
        }
        TargetProtection targetProtection = targetProtection(target);
        BeamProtectionRuntime.Result result = beamProtection.resolve(
                solution,
                targetProtection.beamShield(),
                direction(target, source),
                kineticProtection.protectionStackIds(targetProtection.hull(), target.engineering.fit),
                targetProtection.hull(),
                target.engineering.fit,
                targetProtection.layout(),
                target.engineering.instanceState.damage(),
                targetProtection.hitPoint());
        commitTargetProtection(target, targetProtection, result.shieldInteraction(), result.damageEvent());
        return new CombatEvent(sequence, round, EventType.BEAM_DWELL,
                source.entityId, target.entityId, mount.mountId(), solution.deliveredBeamEnergyJ(),
                result.internalDamageEnergyJ(),
                result.damageEvent() == null ? "material/shield contained dwell" : "local damage");
    }

    private CombatEvent executeGuided(
            long sequence,
            int round,
            MutableShip source,
            MutableShip target,
            List<MutableShip> ships,
            FittedGuidedMount mount,
            TrackState track) {
        GuidanceRuntime.GuidanceCommand guidancePlan = guidance.planLeadPursuit(
                launchBody(sequence, source, target, mount),
                track,
                new TargetMotionEstimate(target.xM, target.yM, 0d, 0d),
                GuidanceRuntime.TrackSource.DATALINK,
                mount.ammunition().toRuntimeWeapon().burnTimeSeconds());
        if (!guidancePlan.allowed()) {
            return denied(sequence, round, source, target, mount.mountId(), track, "guided");
        }
        AmmunitionRuntime.ConsumptionResult consumed;
        try {
            consumed = ammunition.consumeOne(
                    source.engineering.runtimeState.consumables(),
                    mount.mountId(),
                    mount.launcher(),
                    mount.ammunition().wetMassKg());
        } catch (IllegalArgumentException exception) {
            return new CombatEvent(sequence, round, EventType.AMMUNITION_DENIED,
                    source.entityId, target.entityId, mount.mountId(), 0d, 0d, "guided feed empty");
        }
        replaceConsumables(source, consumed.consumables());
        commitCycle(source, mount.mountId(), mount.launcher());
        GuidedWeaponBody guidedBody = guidance.execute(launchBody(sequence, source, target, mount), guidancePlan);

        List<DefenseBinding> defenses = defenseBindings(target.side, ships);
        List<DefenseStation> stations = defenses.stream().map(DefenseBinding::station).toList();
        HullDefinition targetHull = pack.engineering().findHull(target.engineering.fit.hullId());
        double defendedRadius = Math.max(500d, targetHull.boundingDimensionsM().lengthM() * 2d);
        Threat threat = new Threat(
                guidedBody.bodyId(),
                guidedBody.xM(),
                guidedBody.yM(),
                guidedBody.velocityXMps(),
                guidedBody.velocityYMps(),
                guidedBody.currentMassKg(),
                guidedBody.guidanceAvailable());
        List<LayeredDefenseScheduler.Assignment> assignments = defenseScheduler.schedule(
                new DefendedZone(target.xM, target.yM, defendedRadius),
                List.of(threat),
                stations);
        if (!assignments.isEmpty()) {
            LayeredDefenseScheduler.Assignment assignment = assignments.get(0);
            DefenseBinding binding = defenses.stream()
                    .filter(value -> value.station().stationId() == assignment.stationId())
                    .findFirst().orElseThrow();
            consumeInterceptor(binding);
            return new CombatEvent(sequence, round, EventType.INTERCEPT_WINDOW_ASSIGNED,
                    source.entityId, target.entityId, mount.mountId(), guidedBody.kineticEnergyJ(),
                    assignment.plannedInterceptSeconds(),
                    "intercept physically feasible; kill outcome intentionally not assumed");
        }

        var impactBody = guidedImpact.toKineticImpact(guidedBody, round);
        TargetProtection targetProtection = targetProtection(target);
        ShipKineticProtectionService.Result result = kineticProtection.resolve(
                impactBody,
                targetProtection.kineticShield(),
                direction(target, source),
                Math.max(0.001d, distance(source, target) / Math.max(1d, impactBody.speedMps())),
                0d,
                targetProtection.hull(),
                target.engineering.fit,
                targetProtection.layout(),
                target.engineering.instanceState.damage(),
                targetProtection.hitPoint());
        commitTargetProtection(target, targetProtection, result.shieldInteraction(), result.damageEvent());
        double internalJ = result.damageEvent() == null ? 0d : result.damageEvent().compartmentDamageEnergyJ();
        return new CombatEvent(sequence, round, EventType.GUIDED_KINETIC_IMPACT,
                source.entityId, target.entityId, mount.mountId(), impactBody.kineticEnergyJ(), internalJ,
                result.damageEvent() == null ? "terminal body contained" : "terminal local damage");
    }

    private GuidedWeaponBody launchBody(
            long sequence,
            MutableShip source,
            MutableShip target,
            FittedGuidedMount mount) {
        double dx = target.xM - source.xM;
        double dy = target.yM - source.yM;
        double range = Math.hypot(dx, dy);
        double ux = range > 0d ? dx / range : 1d;
        double uy = range > 0d ? dy / range : 0d;
        return GuidedWeaponBody.launch(
                2_000_000L + sequence,
                source.entityId,
                target.entityId,
                mount.ammunition().toRuntimeWeapon(),
                mount.ammunition().materialId(),
                mount.ammunition().shape(),
                mount.ammunition().lengthM(),
                mount.ammunition().diameterM(),
                mount.ammunition().impactPayloadId(),
                source.xM,
                source.yM,
                ux * GUIDED_INITIAL_SPEED_MPS,
                uy * GUIDED_INITIAL_SPEED_MPS);
    }

    private void consumeInterceptor(DefenseBinding binding) {
        MutableShip defender = binding.ship();
        FittedGuidedMount mount = binding.mount();
        AmmunitionRuntime.ConsumptionResult consumed = ammunition.consumeOne(
                defender.engineering.runtimeState.consumables(),
                mount.mountId(),
                mount.launcher(),
                mount.ammunition().wetMassKg());
        replaceConsumables(defender, consumed.consumables());
        commitCycle(defender, mount.mountId(), mount.launcher());
    }

    private List<DefenseBinding> defenseBindings(Side defendedSide, List<MutableShip> ships) {
        List<DefenseBinding> result = new ArrayList<>();
        long local = 1L;
        for (MutableShip ship : ships.stream().filter(value -> value.side == defendedSide)
                .sorted(Comparator.comparingLong(value -> value.entityId)).toList()) {
            ShipCapabilityService.Snapshot snapshot = capabilities.snapshot(ship.engineering);
            List<FittedGuidedMount> mounts = guidedAdapter.deriveGuidedMounts(
                    snapshot.derived(), pack.ammunition(), pack.launchers(),
                    ship.engineering.instanceState.weaponLoadout()).stream()
                    .filter(value -> value.launcher().ammunitionInterfaceId().contains("interceptor"))
                    .toList();
            for (FittedGuidedMount mount : mounts) {
                long rounds = ammunition.roundsOnMount(
                        ship.engineering.runtimeState.consumables(), mount.mountId(), mount.launcher());
                boolean ready = weaponMounts.ready(ship.engineering.instanceState.weaponMountRuntime(), mount.mountId());
                boolean thermalReady = snapshot.thermalEndurance().minimumLocalThermalHeadroomJ() > 0d;
                DefenseStation station = new DefenseStation(
                        ship.entityId * 100L + local++,
                        ship.xM,
                        ship.yM,
                        0d,
                        mount.ammunition().toRuntimeWeapon(),
                        ready,
                        mount.launcher().supportChannelCount(),
                        rounds,
                        thermalReady,
                        DEFENSE_SAFE_INTERCEPT_RANGE_M);
                result.add(new DefenseBinding(ship, mount, station));
            }
        }
        return List.copyOf(result);
    }

    private TargetProtection targetProtection(MutableShip target) {
        ShipCapabilityService.Snapshot snapshot = capabilities.snapshot(target.engineering);
        HullDefinition hull = pack.engineering().findHull(snapshot.derived().hullId());
        var layout = pack.protection().findHullDamageLayout(hull.id());
        List<ShipShieldEngineeringAdapter.FittedShield> shields = shieldAdapter.derive(snapshot.derived());
        ShipShieldEngineeringAdapter.FittedShield fitted = shields.isEmpty() ? null : shields.get(0);
        ShieldFieldRuntime.State state = fitted == null ? null
                : target.engineering.instanceState.shieldStatesByMount().get(fitted.mountId());
        Vector3d hitPoint = hull.compartments().stream()
                .filter(value -> value.id().equals("weapons"))
                .findFirst()
                .orElseGet(() -> hull.compartments().get(0))
                .centerM();
        return new TargetProtection(hull, layout, fitted, state, hitPoint);
    }

    private void commitTargetProtection(
            MutableShip target,
            TargetProtection protection,
            ShieldFieldRuntime.Interaction shieldInteraction,
            com.spacesim.ship.ShipDamageRuntime.DamageEvent damageEvent) {
        ShipInstanceRuntimeState current = target.engineering.instanceState;
        TreeMap<String, ShieldFieldRuntime.State> shieldStates = new TreeMap<>(current.shieldStatesByMount());
        if (shieldInteraction != null && protection.fittedShield() != null) {
            shieldStates.put(protection.fittedShield().mountId(), shieldInteraction.state());
        }
        target.engineering.setInstanceState(new ShipInstanceRuntimeState(
                damageEvent == null ? current.damage() : damageEvent.snapshot(),
                Map.copyOf(shieldStates),
                current.maintenance(),
                current.weaponLoadout(),
                current.weaponMountRuntime()));
    }

    private void replaceConsumables(MutableShip ship, com.spacesim.ship.ShipEngineeringState.ConsumableState next) {
        var state = ship.engineering.runtimeState;
        ship.engineering.setRuntimeState(new com.spacesim.ship.ShipEngineeringRuntime.RuntimeState(
                next,
                state.sharedBusEnergyJ(),
                state.shipHeatStoredJ(),
                state.localHeatJByMount(),
                state.thrustLimitNByMount(),
                state.coolantBusCapacityW(),
                state.ftlCooldownSecondsByMount()));
    }

    private void commitCycle(MutableShip ship, String mountId, com.spacesim.ship.WeaponDefinition.Launcher launcher) {
        ShipInstanceRuntimeState current = ship.engineering.instanceState;
        ship.engineering.setInstanceState(new ShipInstanceRuntimeState(
                current.damage(),
                current.shieldStatesByMount(),
                current.maintenance(),
                current.weaponLoadout(),
                weaponMounts.commitShot(current.weaponMountRuntime(), mountId, launcher)));
    }

    private void advanceWeaponCycles(MutableShip ship) {
        ShipInstanceRuntimeState current = ship.engineering.instanceState;
        ship.engineering.setInstanceState(new ShipInstanceRuntimeState(
                current.damage(),
                current.shieldStatesByMount(),
                current.maintenance(),
                current.weaponLoadout(),
                weaponMounts.advance(current.weaponMountRuntime(), ROUND_SECONDS)));
    }

    private CombatEvent denied(
            long sequence,
            int round,
            MutableShip source,
            MutableShip target,
            String mountId,
            TrackState track,
            String weaponKind) {
        EventType type = track.informationState().ordinal() < InformationState.TRACKED.ordinal()
                ? EventType.INFORMATION_DENIED
                : EventType.FIRE_SOLUTION_DENIED;
        return new CombatEvent(sequence, round, type,
                source.entityId, target.entityId, mountId, 0d, 0d,
                weaponKind + " solution denied at " + track.informationState());
    }

    private TrackState trackFor(
            MutableShip target,
            Stage175ICombatTestManifest.VariationDefinition variation) {
        InformationState state = switch (variation.informationQuality()) {
            case DETECTED -> InformationState.DETECTED;
            case TRACKED -> InformationState.TRACKED;
            case FIRE_CONTROL -> InformationState.FIRE_CONTROL;
        };
        boolean positionKnown = state.ordinal() >= InformationState.TRACKED.ordinal();
        Double positionVariance = positionKnown
                ? (state == InformationState.FIRE_CONTROL ? 100d : 10_000d)
                : null;
        Double rangeVariance = positionKnown
                ? (state == InformationState.FIRE_CONTROL ? 100d : 10_000d)
                : null;
        return new TrackState(
                target.entityId,
                state,
                positionKnown,
                target.xM,
                target.yM,
                new TrackCovariance(positionVariance, positionKnown ? 1e-9d : 1e-4d, rangeVariance),
                state == InformationState.FIRE_CONTROL ? 1d : 0.75d,
                0d,
                positionKnown ? 2 : 1,
                positionKnown ? 4 : 1);
    }

    private KinematicState kinematics(MutableShip ship) {
        return new KinematicState(ship.xM, ship.yM, 0d, 0d);
    }

    private List<MutableShip> materializeFleet(
            Stage175ICombatTestManifest.FleetDefinition fleet,
            Side side,
            Stage175ICombatTestManifest.VariationDefinition variation,
            long firstEntityId) {
        List<String> fitIds = new ArrayList<>();
        fleet.ships().stream().sorted(Comparator.comparing(Stage175ICombatTestManifest.ShipEntry::fitId))
                .forEach(row -> {
                    for (int index = 0; index < row.count(); index++) {
                        fitIds.add(row.fitId());
                    }
                });
        List<MutableShip> result = new ArrayList<>();
        double x = side == Side.A ? -variation.initialSeparationM() / 2d : variation.initialSeparationM() / 2d;
        for (int index = 0; index < fitIds.size(); index++) {
            double centeredIndex = index - (fitIds.size() - 1d) / 2d;
            double y = centeredIndex * variation.formationSpacingM();
            Stage175IShipMaterializer.MaterializedShip materialized = materializer.materialize(fitIds.get(index), variation);
            result.add(new MutableShip(
                    firstEntityId + index,
                    side,
                    fitIds.get(index),
                    x,
                    y,
                    materialized.engineering(),
                    materialized.derived().totalMassKg(),
                    materialized.engineering().runtimeState.consumables().ammunitionCount(),
                    materialized.engineering().runtimeState.consumables().reactionMassKg()));
        }
        return result;
    }

    private MutableShip nearestOpponent(MutableShip source, List<MutableShip> ships) {
        return ships.stream()
                .filter(value -> value.side != source.side)
                .min(Comparator.comparingDouble((MutableShip value) -> distance(source, value))
                        .thenComparingLong(value -> value.entityId))
                .orElse(null);
    }

    private SideSummary summarize(Side side, List<MutableShip> ships) {
        List<MutableShip> selected = ships.stream().filter(value -> value.side == side).toList();
        double initialMass = selected.stream().mapToDouble(value -> value.initialMassKg).sum();
        double currentMass = selected.stream().mapToDouble(value -> capabilities.snapshot(value.engineering).derived().totalMassKg()).sum();
        long initialAmmo = selected.stream().mapToLong(value -> value.initialAmmoCount).sum();
        long currentAmmo = selected.stream().mapToLong(value -> value.engineering.runtimeState.consumables().ammunitionCount()).sum();
        double initialReaction = selected.stream().mapToDouble(value -> value.initialReactionMassKg).sum();
        double currentReaction = selected.stream().mapToDouble(value -> value.engineering.runtimeState.consumables().reactionMassKg()).sum();
        double shieldReserve = selected.stream().flatMap(value -> value.engineering.instanceState.shieldStatesByMount().values().stream())
                .mapToDouble(ShieldFieldRuntime.State::reserveJ).sum();
        double localHeat = selected.stream().flatMap(value -> value.engineering.runtimeState.localHeatJByMount().values().stream())
                .mapToDouble(Double::doubleValue).sum();
        double minCompartment = selected.stream()
                .flatMap(value -> value.engineering.instanceState.damage().compartmentIntegrityById().values().stream())
                .mapToDouble(Double::doubleValue).min().orElse(1d);
        double minModule = selected.stream()
                .flatMap(value -> value.engineering.instanceState.damage().moduleDamage().moduleIntegrityByMount().values().stream())
                .mapToDouble(Double::doubleValue).min().orElse(1d);
        return new SideSummary(
                side,
                selected.size(),
                initialMass,
                currentMass,
                initialAmmo,
                currentAmmo,
                initialReaction,
                currentReaction,
                shieldReserve,
                localHeat,
                minCompartment,
                minModule);
    }

    private String fingerprint(
            String matchupId,
            String variationId,
            List<CombatEvent> events,
            SideSummary sideA,
            SideSummary sideB) {
        StringBuilder canonical = new StringBuilder(16_384);
        canonical.append(pack.fingerprint()).append('|').append(matchupId).append('|').append(variationId).append('\n');
        for (CombatEvent event : events) {
            canonical.append(event.sequence()).append('|').append(event.round()).append('|').append(event.type())
                    .append('|').append(event.sourceEntityId()).append('|').append(event.targetEntityId())
                    .append('|').append(event.mountId()).append('|')
                    .append(Double.toHexString(event.primaryValue())).append('|')
                    .append(Double.toHexString(event.secondaryValue())).append('|').append(event.detail()).append('\n');
        }
        appendSide(canonical, sideA);
        appendSide(canonical, sideB);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JVM", exception);
        }
    }

    private static void appendSide(StringBuilder canonical, SideSummary side) {
        canonical.append(side.side()).append('|').append(side.shipCount()).append('|')
                .append(Double.toHexString(side.initialMassKg())).append('|')
                .append(Double.toHexString(side.currentMassKg())).append('|')
                .append(side.initialAmmunitionCount()).append('|').append(side.currentAmmunitionCount()).append('|')
                .append(Double.toHexString(side.initialReactionMassKg())).append('|')
                .append(Double.toHexString(side.currentReactionMassKg())).append('|')
                .append(Double.toHexString(side.shieldReserveJ())).append('|')
                .append(Double.toHexString(side.localHeatJ())).append('|')
                .append(Double.toHexString(side.minimumCompartmentIntegrity())).append('|')
                .append(Double.toHexString(side.minimumModuleIntegrity())).append('\n');
    }

    private Stage175ICombatTestManifest.FleetDefinition requireFleet(String fleetId) {
        Stage175ICombatTestManifest.FleetDefinition fleet = pack.manifest().findFleet(fleetId);
        if (fleet == null) {
            throw new IllegalArgumentException("Unknown Stage-17.5I fleet: " + fleetId);
        }
        return fleet;
    }

    private static double distance(MutableShip left, MutableShip right) {
        return Math.hypot(right.xM - left.xM, right.yM - left.yM);
    }

    private static double direction(MutableShip target, MutableShip source) {
        return Math.atan2(source.yM - target.yM, source.xM - target.xM);
    }

    /** Acceptance fleet side identifier. */
    public enum Side { A, B }

    /** Stable physical event class emitted by the acceptance runner. */
    public enum EventType {
        /** Kinetic round fired and physically resolved. */ KINETIC_IMPACT,
        /** Beam dwell physically admitted and resolved. */ BEAM_DWELL,
        /** Uncontested guided kinetic body reached protection. */ GUIDED_KINETIC_IMPACT,
        /** Layered defense found and consumed a feasible intercept resource window. */ INTERCEPT_WINDOW_ASSIGNED,
        /** Track state was insufficient for the fitted weapon. */ INFORMATION_DENIED,
        /** Geometry/covariance did not produce an allowed physical fire solution. */ FIRE_SOLUTION_DENIED,
        /** Physical ammunition was unavailable. */ AMMUNITION_DENIED,
        /** Common power/thermal admission rejected the action. */ ENGINEERING_DENIED,
        /** Ship has no ready offensive fitted weapon. */ NO_OFFENSIVE_ACTION
    }

    /**
     * One stable acceptance event.
     *
     * @param sequence deterministic global event sequence
     * @param round bounded exchange round index
     * @param type physical event class
     * @param sourceEntityId source physical acceptance entity ID
     * @param targetEntityId target physical acceptance entity ID
     * @param mountId fitted source mount or empty when no weapon exists
     * @param primaryValue event-specific physical value, normally incident energy J
     * @param secondaryValue event-specific physical consequence, normally internal energy J or intercept time s
     * @param detail stable diagnostic detail
     */
    public record CombatEvent(
            long sequence,
            int round,
            EventType type,
            long sourceEntityId,
            long targetEntityId,
            String mountId,
            double primaryValue,
            double secondaryValue,
            String detail) {
        /** Validates one immutable acceptance event. */
        public CombatEvent {
            Objects.requireNonNull(type, "type");
            mountId = mountId == null ? "" : mountId;
            detail = detail == null ? "" : detail;
            if (!Double.isFinite(primaryValue) || primaryValue < 0d
                    || !Double.isFinite(secondaryValue) || secondaryValue < 0d) {
                throw new IllegalArgumentException("event physical values must be finite and non-negative");
            }
        }
    }

    /**
     * End-of-exchange physical summary for one side.
     *
     * @param side fleet side
     * @param shipCount physical ship count
     * @param initialMassKg initial total physical mass
     * @param currentMassKg current total physical mass after consumable use
     * @param initialAmmunitionCount initial physical round count
     * @param currentAmmunitionCount remaining physical round count
     * @param initialReactionMassKg initial reaction mass
     * @param currentReactionMassKg remaining reaction mass
     * @param shieldReserveJ surviving shield reserve
     * @param localHeatJ accumulated module-local heat
     * @param minimumCompartmentIntegrity minimum surviving local structural integrity
     * @param minimumModuleIntegrity minimum surviving fitted-module integrity
     */
    public record SideSummary(
            Side side,
            int shipCount,
            double initialMassKg,
            double currentMassKg,
            long initialAmmunitionCount,
            long currentAmmunitionCount,
            double initialReactionMassKg,
            double currentReactionMassKg,
            double shieldReserveJ,
            double localHeatJ,
            double minimumCompartmentIntegrity,
            double minimumModuleIntegrity) { }

    /**
     * Deterministic multi-fleet acceptance result.
     *
     * @param matchupId manifest matchup ID
     * @param variationId manifest variation ID
     * @param contentPackFingerprint exact content pack fingerprint
     * @param events stable ordered physical event log
     * @param sideA side-A physical end state
     * @param sideB side-B physical end state
     * @param eventCounts event counts by stable class
     * @param fingerprint SHA-256 result fingerprint
     */
    public record ScenarioResult(
            String matchupId,
            String variationId,
            String contentPackFingerprint,
            List<CombatEvent> events,
            SideSummary sideA,
            SideSummary sideB,
            Map<EventType, Integer> eventCounts,
            String fingerprint) {
        /** Validates and freezes one deterministic result. */
        public ScenarioResult {
            Objects.requireNonNull(matchupId, "matchupId");
            Objects.requireNonNull(variationId, "variationId");
            Objects.requireNonNull(contentPackFingerprint, "contentPackFingerprint");
            events = List.copyOf(Objects.requireNonNull(events, "events"));
            Objects.requireNonNull(sideA, "sideA");
            Objects.requireNonNull(sideB, "sideB");
            eventCounts = Map.copyOf(Objects.requireNonNull(eventCounts, "eventCounts"));
            Objects.requireNonNull(fingerprint, "fingerprint");
        }

        /** @return count for one physical event class */
        public int count(EventType type) {
            return eventCounts.getOrDefault(type, 0);
        }
    }

    private static final class MutableShip {
        private final long entityId;
        private final Side side;
        private final String fitId;
        private final double xM;
        private final double yM;
        private final EngineeringComponent engineering;
        private final double initialMassKg;
        private final long initialAmmoCount;
        private final double initialReactionMassKg;

        private MutableShip(
                long entityId,
                Side side,
                String fitId,
                double xM,
                double yM,
                EngineeringComponent engineering,
                double initialMassKg,
                long initialAmmoCount,
                double initialReactionMassKg) {
            this.entityId = entityId;
            this.side = Objects.requireNonNull(side, "side");
            this.fitId = Objects.requireNonNull(fitId, "fitId");
            this.xM = xM;
            this.yM = yM;
            this.engineering = Objects.requireNonNull(engineering, "engineering");
            this.initialMassKg = initialMassKg;
            this.initialAmmoCount = initialAmmoCount;
            this.initialReactionMassKg = initialReactionMassKg;
        }
    }

    private record TargetProtection(
            HullDefinition hull,
            com.spacesim.content.ship.ShipProtectionCatalog.HullDamageLayout layout,
            ShipShieldEngineeringAdapter.FittedShield fittedShield,
            ShieldFieldRuntime.State shieldState,
            Vector3d hitPoint) {
        private KineticProtectionRuntime.ShieldInput kineticShield() {
            return fittedShield == null || shieldState == null ? null
                    : new KineticProtectionRuntime.ShieldInput(fittedShield.definition(), shieldState);
        }

        private BeamProtectionRuntime.ShieldInput beamShield() {
            return fittedShield == null || shieldState == null ? null
                    : new BeamProtectionRuntime.ShieldInput(fittedShield.definition(), shieldState);
        }
    }

    private record DefenseBinding(
            MutableShip ship,
            FittedGuidedMount mount,
            DefenseStation station) { }
}
