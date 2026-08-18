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
 * Stage-19I actor-bounded deception coordinator over the accepted physical decoy and defense runtimes.
 *
 * <p>This class owns policy ordering only. It never creates a sensor hypothesis, ammunition item,
 * launcher cycle or physical body directly. Automatic deployment is delegated to
 * {@link LiveTacticalBattleDecoyRuntime#deployOne(long, String, double, double)}, so finite stores,
 * physical launcher readiness, damage-aware fitted capability and body materialization remain owned by
 * the existing production paths.</p>
 *
 * <p>A combatant may automatically accompany an already-active own STRIKE body with one physical
 * decoy only when its production tactical controller currently has an actor-local selected target and
 * that target has an actor-local TRACKED/FIRE_CONTROL Cartesian solution. Hostile authoritative
 * transforms are never read by deployment policy. One active decoy per source is the provisional
 * Stage-19I anti-spam policy; later doctrine work may replace that choice without changing physics.</p>
 */
public final class LiveTacticalBattleDeceptionRuntime {
    private static final double EPSILON = 1e-9d;

    private final LiveTacticalBattleOrdnanceRuntime ordnanceRuntime;
    private final LiveTacticalBattleDecoyRuntime decoyRuntime;
    private final LiveTacticalBattleDefenseRuntime defenseRuntime;
    private final ShipEngineeringCatalog engineeringCatalog;
    private final WeaponAmmunitionCatalog ammunitionCatalog;
    private final WeaponLauncherCatalog launcherCatalog;
    private final DerivedShipCalculator calculator;
    private final ShipGuidedWeaponEngineeringAdapter guidedAdapter;
    private final TreeMap<Long, Long> automaticDeploymentsByEntityId = new TreeMap<>();

    /**
     * Creates automatic deception over one authoritative shared battle/ordnance runtime.
     *
     * @param ordnanceRuntime authoritative shared physical ordnance runtime
     */
    public LiveTacticalBattleDeceptionRuntime(LiveTacticalBattleOrdnanceRuntime ordnanceRuntime) {
        this.ordnanceRuntime = Objects.requireNonNull(ordnanceRuntime, "ordnanceRuntime");
        decoyRuntime = new LiveTacticalBattleDecoyRuntime(ordnanceRuntime);
        defenseRuntime = new LiveTacticalBattleDefenseRuntime(ordnanceRuntime, decoyRuntime);
        engineeringCatalog = Stage175ICombatTestContentPack.loadDoctrines();
        ammunitionCatalog = Stage175ICombatTestWeaponPack.loadAmmunition();
        launcherCatalog = Stage175ICombatTestWeaponPack.loadLaunchers();
        calculator = new DerivedShipCalculator(engineeringCatalog);
        guidedAdapter = new ShipGuidedWeaponEngineeringAdapter();
        for (CombatantRuntime combatant : battleState().combatants()) {
            automaticDeploymentsByEntityId.put(combatant.spec().entityId(), 0L);
        }
    }

    /**
     * Advances one authoritative battle tick with actor-bounded automatic decoy policy.
     *
     * <p>Deployment policy executes at the start of the tick from information and own physical bodies
     * established by previous authoritative ticks. The shared defense runtime then advances ordnance,
     * decoys, sensing, interceptor guidance and physical collisions exactly once.</p>
     */
    public void advanceOneTick() {
        deployFromCurrentActorKnowledge();
        defenseRuntime.advanceOneTick();
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
     * Equality-friendly deterministic deception/defense projection.
     *
     * @return immutable whole-runtime fingerprint
     */
    public DeceptionFingerprint fingerprint() {
        return new DeceptionFingerprint(
                tick(),
                new TreeMap<>(automaticDeploymentsByEntityId),
                decoyRuntime.fingerprint(),
                defenseRuntime.fingerprint());
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
     * Whole-runtime deterministic deception projection.
     *
     * @param tick authoritative shared battle tick
     * @param automaticDeploymentsByEntityId automatic physical deployment counts by combatant
     * @param decoyFingerprint authoritative physical decoy state
     * @param defenseFingerprint actor-bounded physical defense/ordnance state
     */
    public record DeceptionFingerprint(
            long tick,
            Map<Long, Long> automaticDeploymentsByEntityId,
            LiveTacticalBattleDecoyRuntime.DecoyFingerprint decoyFingerprint,
            LiveTacticalBattleDefenseRuntime.BattleDefenseFingerprint defenseFingerprint) {
        /**
         * Validates and freezes one deterministic deception projection.
         *
         * @param tick authoritative shared battle tick
         * @param automaticDeploymentsByEntityId automatic deployment counts by combatant
         * @param decoyFingerprint physical decoy projection
         * @param defenseFingerprint physical defense projection
         */
        public DeceptionFingerprint {
            if (tick < 0L) {
                throw new IllegalArgumentException("tick must be non-negative");
            }
            automaticDeploymentsByEntityId = Map.copyOf(new TreeMap<>(Objects.requireNonNull(
                    automaticDeploymentsByEntityId, "automaticDeploymentsByEntityId")));
            Objects.requireNonNull(decoyFingerprint, "decoyFingerprint");
            Objects.requireNonNull(defenseFingerprint, "defenseFingerprint");
        }
    }
}
