package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.ship.BeamWeaponRuntime.BeamSolution;
import com.spacesim.ship.LiveTacticalBattleControlRuntime.ActorControlState;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.CombatantRuntime;
import com.spacesim.ship.ShipEngineeringGrantService.IntervalBudget;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipWeaponEngineeringAdapter.FittedBeamMount;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Shared Stage-19 fitted directed-energy execution on the authoritative tactical runtime.
 *
 * <p>This runtime owns no clock, targeting policy, movement or target damage model. It consumes the
 * already actor-bounded selected {@link TrackState}, derives physical beam emitters from the fitted
 * and damaged ship, executes Stage-17.5 beam geometry and commits real incremental power/local heat
 * through {@link ShipBeamEngineeringService}. The result is a deterministic physical exposure at the
 * target plane. Stage 17.5 does not author optical absorption/ablation material data, so this layer
 * deliberately does not invent a laser-DPS shortcut or bypass calibrated protection physics.</p>
 */
public final class LiveTacticalBattleBeamRuntime {
    private final LiveTacticalBattleWeaponRuntime weaponRuntime;
    private final ShipEngineeringCatalog engineeringCatalog;
    private final DerivedShipCalculator calculator;
    private final ShipWeaponEngineeringAdapter weaponAdapter;
    private final ShipBeamEngineeringService beamService;
    private final TreeMap<Long, Long> dwellsBySourceEntityId = new TreeMap<>();
    private final TreeMap<Long, Double> deliveredEnergyBySourceEntityId = new TreeMap<>();
    private List<BeamExposure> lastExposures = List.of();
    private long lastExecutedTick = Long.MIN_VALUE;

    /**
     * Creates beam execution over one authoritative shared battle weapon/control runtime.
     *
     * @param weaponRuntime authoritative shared tactical weapon/control runtime
     */
    public LiveTacticalBattleBeamRuntime(LiveTacticalBattleWeaponRuntime weaponRuntime) {
        this.weaponRuntime = Objects.requireNonNull(weaponRuntime, "weaponRuntime");
        engineeringCatalog = Stage175ICombatTestContentPack.loadDoctrines();
        calculator = new DerivedShipCalculator(engineeringCatalog);
        weaponAdapter = new ShipWeaponEngineeringAdapter();
        beamService = new ShipBeamEngineeringService(engineeringCatalog);
        for (CombatantRuntime combatant : battleState().combatants()) {
            dwellsBySourceEntityId.put(combatant.spec().entityId(), 0L);
            deliveredEnergyBySourceEntityId.put(combatant.spec().entityId(), 0d);
        }
    }

    /**
     * Executes all currently authorized fitted beams exactly once for the already-advanced tick.
     *
     * <p>The caller must first advance the shared control/weapon clock. Multiple beam mounts on one
     * ship share a single engineering reservation interval, preventing simultaneous emitters from
     * independently spending the same incremental power/storage headroom.</p>
     */
    public void executeCurrentTick() {
        long tick = weaponRuntime.tick();
        if (tick <= 0L) {
            throw new IllegalStateException("beam execution requires an advanced authoritative tick");
        }
        if (tick == lastExecutedTick) {
            throw new IllegalStateException("beam execution already performed for tick " + tick);
        }
        List<BeamExposure> exposures = new ArrayList<>();
        for (CombatantRuntime shooter : battleState().combatants()) {
            ActorControlState control = weaponRuntime.controlRuntime().controlState(shooter.spec().entityId());
            if (!control.fireAuthorized() || !control.intent().targetSelected()) {
                continue;
            }
            TrackState selectedTrack = selectedVisibleTrack(shooter.spec().entityId(), control.intent().targetId());
            if (selectedTrack == null) {
                throw new IllegalStateException("authorized beam target disappeared from actor-visible domain");
            }
            fireBeamMounts(shooter, selectedTrack, exposures);
        }
        lastExposures = List.copyOf(exposures);
        lastExecutedTick = tick;
    }

    /** @return immutable physical beam exposures created on the latest executed tick */
    public List<BeamExposure> lastExposures() {
        return lastExposures;
    }

    /**
     * Returns cumulative physically admitted beam dwells by source.
     *
     * @param sourceEntityId stable firing combatant identity
     * @return non-negative admitted dwell count
     */
    public long dwellsFired(long sourceEntityId) {
        battleState().requireCombatant(sourceEntityId);
        return dwellsBySourceEntityId.get(sourceEntityId);
    }

    /**
     * Returns cumulative delivered beam energy at the target plane by source.
     *
     * @param sourceEntityId stable firing combatant identity
     * @return non-negative emitted beam energy in joules
     */
    public double deliveredEnergyJ(long sourceEntityId) {
        battleState().requireCombatant(sourceEntityId);
        return deliveredEnergyBySourceEntityId.get(sourceEntityId);
    }

    /** @return deterministic read-only beam execution fingerprint */
    public BeamFingerprint fingerprint() {
        return new BeamFingerprint(
                weaponRuntime.tick(),
                new TreeMap<>(dwellsBySourceEntityId),
                new TreeMap<>(deliveredEnergyBySourceEntityId),
                lastExposures);
    }

    private void fireBeamMounts(
            CombatantRuntime shooter,
            TrackState selectedTrack,
            List<BeamExposure> exposures) {
        EngineeringComponent engineering = shooter.engineering();
        List<FittedBeamMount> mounts = weaponAdapter.deriveBeamMounts(derive(shooter), engineeringCatalog);
        if (mounts.isEmpty()) {
            return;
        }
        IntervalBudget budget = beamService.beginInterval(
                engineering,
                LiveTacticalBattleControlRuntime.TICK_SECONDS);
        for (FittedBeamMount mount : mounts) {
            BeamSolution solution = beamService.planAndCommit(
                    engineering,
                    mount.mountId(),
                    mount.weapon(),
                    selectedTrack,
                    shooter.transform().position.x,
                    shooter.transform().position.y,
                    LiveTacticalBattleControlRuntime.TICK_SECONDS,
                    budget);
            if (solution == null || !solution.allowed()) {
                continue;
            }
            BeamExposure exposure = new BeamExposure(
                    weaponRuntime.tick(),
                    shooter.spec().entityId(),
                    selectedTrack.targetId(),
                    mount.mountId(),
                    mount.moduleId(),
                    solution.rangeM(),
                    solution.effectiveSpotRadiusM(),
                    solution.dwellSeconds(),
                    solution.deliveredBeamEnergyJ(),
                    solution.meanIrradianceWPerM2(),
                    solution.electricalEnergyDemandJ(),
                    solution.wasteHeatJ());
            exposures.add(exposure);
            dwellsBySourceEntityId.compute(
                    shooter.spec().entityId(),
                    (ignored, count) -> Math.addExact(Objects.requireNonNull(count, "beam dwell count"), 1L));
            deliveredEnergyBySourceEntityId.compute(
                    shooter.spec().entityId(),
                    (ignored, energy) -> Objects.requireNonNull(energy, "beam energy")
                            + solution.deliveredBeamEnergyJ());
        }
    }

    private TrackState selectedVisibleTrack(long observerEntityId, long targetId) {
        return battleState().visibleContacts(observerEntityId).stream()
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

    private LiveTacticalBattleRuntimeState battleState() {
        return weaponRuntime.battleState();
    }

    /**
     * One admitted physical beam exposure at the current target plane.
     *
     * @param tick authoritative fixed tick
     * @param sourceEntityId firing combatant identity
     * @param targetEntityId actor-selected observed target identity
     * @param mountId fitted emitter mount
     * @param moduleId beam module content ID
     * @param rangeM observed Cartesian target range used by beam geometry
     * @param effectiveSpotRadiusM combined diffraction/pointing/track one-sigma radius
     * @param dwellSeconds physically admitted dwell duration
     * @param deliveredBeamEnergyJ emitted beam energy during dwell
     * @param meanIrradianceWPerM2 mean target-plane irradiance
     * @param electricalEnergyDemandJ incremental electrical energy demand admitted by engineering
     * @param wasteHeatJ local physical emitter heat committed by engineering
     */
    public record BeamExposure(
            long tick,
            long sourceEntityId,
            long targetEntityId,
            String mountId,
            String moduleId,
            double rangeM,
            double effectiveSpotRadiusM,
            double dwellSeconds,
            double deliveredBeamEnergyJ,
            double meanIrradianceWPerM2,
            double electricalEnergyDemandJ,
            double wasteHeatJ) {
        /**
         * Validates one immutable beam exposure.
         *
         * @param tick authoritative fixed tick
         * @param sourceEntityId firing combatant identity
         * @param targetEntityId observed target identity
         * @param mountId fitted emitter mount
         * @param moduleId beam module content ID
         * @param rangeM observed target range
         * @param effectiveSpotRadiusM combined exposure radius
         * @param dwellSeconds admitted dwell duration
         * @param deliveredBeamEnergyJ emitted beam energy
         * @param meanIrradianceWPerM2 target-plane mean irradiance
         * @param electricalEnergyDemandJ admitted incremental electrical energy
         * @param wasteHeatJ committed local emitter heat
         */
        public BeamExposure {
            if (tick <= 0L || sourceEntityId <= 0L || targetEntityId <= 0L) {
                throw new IllegalArgumentException("beam exposure identities/tick must be positive");
            }
            requireNonBlank(mountId, "mountId");
            requireNonBlank(moduleId, "moduleId");
            requireNonNegativeFinite(rangeM, "rangeM");
            requireNonNegativeFinite(effectiveSpotRadiusM, "effectiveSpotRadiusM");
            requirePositiveFinite(dwellSeconds, "dwellSeconds");
            requirePositiveFinite(deliveredBeamEnergyJ, "deliveredBeamEnergyJ");
            requireNonNegativeFinite(meanIrradianceWPerM2, "meanIrradianceWPerM2");
            requireNonNegativeFinite(electricalEnergyDemandJ, "electricalEnergyDemandJ");
            requireNonNegativeFinite(wasteHeatJ, "wasteHeatJ");
        }
    }

    /**
     * Whole-runtime deterministic fitted-beam execution projection.
     *
     * @param tick current authoritative battle tick
     * @param dwellsBySourceEntityId cumulative admitted dwells by source
     * @param deliveredEnergyBySourceEntityId cumulative emitted energy by source
     * @param lastExposures physical exposures created on the latest beam execution tick
     */
    public record BeamFingerprint(
            long tick,
            Map<Long, Long> dwellsBySourceEntityId,
            Map<Long, Double> deliveredEnergyBySourceEntityId,
            List<BeamExposure> lastExposures) {
        /**
         * Validates and freezes one beam fingerprint.
         *
         * @param tick current authoritative battle tick
         * @param dwellsBySourceEntityId cumulative admitted dwells by source
         * @param deliveredEnergyBySourceEntityId cumulative emitted energy by source
         * @param lastExposures latest physical exposures
         */
        public BeamFingerprint {
            if (tick < 0L) {
                throw new IllegalArgumentException("tick must be non-negative");
            }
            dwellsBySourceEntityId = Map.copyOf(new TreeMap<>(Objects.requireNonNull(
                    dwellsBySourceEntityId, "dwellsBySourceEntityId")));
            deliveredEnergyBySourceEntityId = Map.copyOf(new TreeMap<>(Objects.requireNonNull(
                    deliveredEnergyBySourceEntityId, "deliveredEnergyBySourceEntityId")));
            lastExposures = List.copyOf(Objects.requireNonNull(lastExposures, "lastExposures"));
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
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