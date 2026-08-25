package com.spacesim.ship;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.weapon.Stage175ICombatTestWeaponPack;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog.GuidedEngagementRole;
import com.spacesim.content.weapon.WeaponLauncherCatalog;
import com.spacesim.ship.LiveTacticalBattleControlRuntime.ActorControlState;
import com.spacesim.ship.LiveTacticalBattleRuntimeState.CombatantRuntime;
import com.spacesim.ship.ObservedThreatAssessmentService.ObservedContact;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.TrackState.InformationState;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Stage-19I actor-bounded deception coordinator over the accepted physical combat runtimes.
 *
 * <p>This class owns top-level policy ordering only. It never creates a sensor hypothesis,
 * ammunition item, launcher cycle, kinetic/guided body or beam solution directly. Automatic decoy
 * deployment remains delegated to {@link LiveTacticalBattleDecoyRuntime}; fitted directed-energy
 * execution is delegated to {@link LiveTacticalBattleBeamRuntime}. The known outer interceptor phase
 * ordering is observed by {@link Stage19GuidedImpactOrderingAudit}, which is read-only and cannot
 * affect physical outcomes.</p>
 */
public final class LiveTacticalBattleDeceptionRuntime {
    private static final double EPSILON = 1e-9d;

    private final LiveTacticalBattleOrdnanceRuntime ordnanceRuntime;
    private final LiveTacticalBattleDecoyRuntime decoyRuntime;
    private final LiveTacticalBattleDefenseRuntime defenseRuntime;
    private final LiveTacticalBattleBeamRuntime beamRuntime;
    private final Stage19GuidedImpactOrderingAudit orderingAudit;
    private final ShipEngineeringCatalog engineeringCatalog;
    private final WeaponAmmunitionCatalog ammunitionCatalog;
    private final WeaponLauncherCatalog launcherCatalog;
    private final DerivedShipCalculator calculator;
    private final ShipGuidedWeaponEngineeringAdapter guidedAdapter;
    private final TreeMap<Long, Long> automaticDeploymentsByEntityId = new TreeMap<>();

    private long auditedShipImpactCandidates;
    private long orderingAmbiguities;
    private Stage19GuidedImpactOrderingAudit.Result lastOrderingAudit;

    /**
     * Creates automatic deception, beam execution and read-only ordering audit over one authority.
     *
     * @param ordnanceRuntime authoritative shared physical ordnance runtime
     */
    public LiveTacticalBattleDeceptionRuntime(LiveTacticalBattleOrdnanceRuntime ordnanceRuntime) {
        this.ordnanceRuntime = Objects.requireNonNull(ordnanceRuntime, "ordnanceRuntime");
        decoyRuntime = new LiveTacticalBattleDecoyRuntime(ordnanceRuntime);
        defenseRuntime = new LiveTacticalBattleDefenseRuntime(ordnanceRuntime, decoyRuntime);
        beamRuntime = new LiveTacticalBattleBeamRuntime(ordnanceRuntime.weaponRuntime());
        orderingAudit = new Stage19GuidedImpactOrderingAudit();
        engineeringCatalog = battleState().engineeringCatalog();
        ammunitionCatalog = Stage175ICombatTestWeaponPack.loadAmmunition();
        launcherCatalog = Stage175ICombatTestWeaponPack.loadLaunchers();
        calculator = new DerivedShipCalculator(engineeringCatalog);
        guidedAdapter = new ShipGuidedWeaponEngineeringAdapter();
        for (CombatantRuntime combatant : battleState().combatants()) {
            automaticDeploymentsByEntityId.put(combatant.spec().entityId(), 0L);
        }
    }

    /**
     * Advances one authoritative battle tick with actor-bounded deception and directed-energy policy.
     *
     * <p>Decoy deployment policy executes from information and own physical bodies established by
     * previous ticks. A read-only ordering snapshot is then captured. The shared defense runtime
     * advances ordnance, sensing, interceptor guidance and physical collisions exactly once. The
     * audit compares that completed motion for any physically earlier interceptor contact suppressed
     * by ship-priority phase ordering. Finally fitted beams execute from the same tick's actor-local
     * fire-control state without advancing a second clock.</p>
     */
    public void advanceOneTick() {
        deployFromCurrentActorKnowledge();
        Stage19GuidedImpactOrderingAudit.Snapshot orderingStart = orderingAudit.capture(
                ordnanceRuntime,
                defenseRuntime);
        defenseRuntime.advanceOneTick();
        lastOrderingAudit = orderingAudit.evaluate(orderingStart, ordnanceRuntime, defenseRuntime);
        auditedShipImpactCandidates = Math.addExact(
                auditedShipImpactCandidates,
                lastOrderingAudit.shipImpactCandidates());
        orderingAmbiguities = Math.addExact(
                orderingAmbiguities,
                lastOrderingAudit.ambiguityCount());
        beamRuntime.executeCurrentTick();
    }

    /** @return authoritative shared battle tick */
    public long tick() {
        return defenseRuntime.tick();
    }

    /** @return wrapped authoritative ordnance runtime */
    public LiveTacticalBattleOrdnanceRuntime ordnanceRuntime() {
        return ordnanceRuntime;
    }

    /** @return physical decoy owner used by automatic policy */
    public LiveTacticalBattleDecoyRuntime decoyRuntime() {
        return decoyRuntime;
    }

    /** @return actor-bounded physical layered-defense runtime */
    public LiveTacticalBattleDefenseRuntime defenseRuntime() {
        return defenseRuntime;
    }

    /** @return fitted actor-bounded physical beam execution runtime */
    public LiveTacticalBattleBeamRuntime beamRuntime() {
        return beamRuntime;
    }

    /** @return cumulative previously-active guided paths audited as same-tick ship impacts */
    public long auditedShipImpactCandidates() {
        return auditedShipImpactCandidates;
    }

    /** @return cumulative physically earlier interceptor contacts suppressed by current ordering */
    public long orderingAmbiguities() {
        return orderingAmbiguities;
    }

    /** @return latest read-only ordering result, or {@code null} before the first tick */
    public Stage19GuidedImpactOrderingAudit.Result lastOrderingAudit() {
        return lastOrderingAudit;
    }

    /** @return authoritative materialized combatant state */
    public LiveTacticalBattleRuntimeState battleState() {
        return ordnanceRuntime.battleState();
    }

    /**
     * Returns automatic physical deployments by one combatant.
     *
     * @param entityId stable combatant identity
     * @return non-negative automatic deployment count
     */
    public long automaticDeployments(long entityId) {
        battleState().requireCombatant(entityId);
        return automaticDeploymentsByEntityId.get(entityId);
    }

    /**
     * Equality-friendly deterministic whole-combat projection.
     *
     * @return immutable whole-runtime fingerprint
     */
    public DeceptionFingerprint fingerprint() {
        return new DeceptionFingerprint(
                tick(),
                new TreeMap<>(automaticDeploymentsByEntityId),
                decoyRuntime.fingerprint(),
                defenseRuntime.fingerprint(),
                beamRuntime.fingerprint(),
                auditedShipImpactCandidates,
                orderingAmbiguities,
                lastOrderingAudit);
    }

    private void deployFromCurrentActorKnowledge() {
        for (CombatantRuntime source : battleState().combatants()) {
            long sourceId = source.spec().entityId();
            if (hasActiveDecoy(sourceId)) {
                continue;
            }
            ActorControlState control = ordnanceRuntime.weaponRuntime().controlRuntime().controlState(sourceId);
            if (!control.intent().targetSelected()) {
                continue;
            }
            long targetId = control.intent().targetId();
            if (!hasActiveOwnStrike(sourceId, targetId)) {
                continue;
            }
            TrackState observedTarget = selectedActorTrack(sourceId, targetId);
            if (!actionableDeploymentTrack(observedTarget)) {
                continue;
            }
            double directionX = observedTarget.estimatedXM() - source.transform().position.x;
            double directionY = observedTarget.estimatedYM() - source.transform().position.y;
            if (directionX * directionX + directionY * directionY <= EPSILON) {
                continue;
            }
            for (ShipGuidedWeaponEngineeringAdapter.FittedGuidedMount mount : decoyMounts(source)) {
                if (decoyRuntime.deployOne(sourceId, mount.mountId(), directionX, directionY)) {
                    automaticDeploymentsByEntityId.compute(
                            sourceId,
                            (ignored, count) -> Math.addExact(
                                    Objects.requireNonNull(count, "automatic deployment count"), 1L));
                    break;
                }
            }
        }
    }

    private boolean hasActiveDecoy(long sourceEntityId) {
        return decoyRuntime.decoyBodies().stream()
                .anyMatch(body -> body.sourceEntityId() == sourceEntityId);
    }

    private boolean hasActiveOwnStrike(long sourceEntityId, long targetId) {
        return ordnanceRuntime.guidedBodies().stream()
                .anyMatch(body -> body.sourceEntityId() == sourceEntityId && body.targetId() == targetId);
    }

    private TrackState selectedActorTrack(long observerEntityId, long targetId) {
        for (ObservedContact contact : battleState().visibleContacts(observerEntityId)) {
            if (contact.track().targetId() == targetId) {
                return contact.track();
            }
        }
        return null;
    }

    private static boolean actionableDeploymentTrack(TrackState track) {
        if (track == null || !track.positionKnown()) {
            return false;
        }
        return track.informationState() == InformationState.TRACKED
                || track.informationState() == InformationState.FIRE_CONTROL;
    }

    private List<ShipGuidedWeaponEngineeringAdapter.FittedGuidedMount> decoyMounts(CombatantRuntime source) {
        EngineeringComponent engineering = source.engineering();
        return guidedAdapter.deriveGuidedMounts(
                derive(source),
                ammunitionCatalog,
                launcherCatalog,
                engineering.instanceState.weaponLoadout(),
                GuidedEngagementRole.DECOY);
    }

    private DerivedShipState derive(CombatantRuntime source) {
        EngineeringComponent engineering = source.engineering();
        return calculator.derive(
                source.hull(),
                engineering.fit,
                engineering.runtimeState.consumables(),
                engineering.instanceState.damage().moduleDamage());
    }

    /**
     * Whole-runtime deterministic deception/defense/beam/ordering projection.
     *
     * @param tick authoritative shared battle tick
     * @param automaticDeploymentsByEntityId automatic physical deployment counts by combatant
     * @param decoyFingerprint authoritative physical decoy state
     * @param defenseFingerprint actor-bounded physical defense/ordnance state
     * @param beamFingerprint fitted directed-energy execution state
     * @param auditedShipImpactCandidates cumulative ordering-audited ship-impact paths
     * @param orderingAmbiguities cumulative physically earlier interceptor contacts
     * @param lastOrderingAudit latest tick ordering evidence, nullable before first advancement
     */
    public record DeceptionFingerprint(
            long tick,
            Map<Long, Long> automaticDeploymentsByEntityId,
            LiveTacticalBattleDecoyRuntime.DecoyFingerprint decoyFingerprint,
            LiveTacticalBattleDefenseRuntime.BattleDefenseFingerprint defenseFingerprint,
            LiveTacticalBattleBeamRuntime.BeamFingerprint beamFingerprint,
            long auditedShipImpactCandidates,
            long orderingAmbiguities,
            Stage19GuidedImpactOrderingAudit.Result lastOrderingAudit) {
        /**
         * Validates and freezes one deterministic whole-combat projection.
         *
         * @param tick authoritative shared battle tick
         * @param automaticDeploymentsByEntityId automatic deployment counts by combatant
         * @param decoyFingerprint physical decoy projection
         * @param defenseFingerprint physical defense projection
         * @param beamFingerprint fitted directed-energy execution projection
         * @param auditedShipImpactCandidates cumulative audited ship-impact paths
         * @param orderingAmbiguities cumulative earlier interceptor contacts
         * @param lastOrderingAudit latest ordering evidence, nullable before first advancement
         */
        public DeceptionFingerprint {
            if (tick < 0L || auditedShipImpactCandidates < 0L || orderingAmbiguities < 0L) {
                throw new IllegalArgumentException("invalid deception/ordering fingerprint counters");
            }
            automaticDeploymentsByEntityId = Map.copyOf(new TreeMap<>(Objects.requireNonNull(
                    automaticDeploymentsByEntityId, "automaticDeploymentsByEntityId")));
            Objects.requireNonNull(decoyFingerprint, "decoyFingerprint");
            Objects.requireNonNull(defenseFingerprint, "defenseFingerprint");
            Objects.requireNonNull(beamFingerprint, "beamFingerprint");
        }
    }
}