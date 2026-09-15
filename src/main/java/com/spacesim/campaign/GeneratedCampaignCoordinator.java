package com.spacesim.campaign;

import com.spacesim.content.ContentCatalog;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimeMigration;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState.MigrationProvenance;
import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.FactionLivingActorRuntime;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.FleetCommandState;
import com.spacesim.world.SettlementRecoveryState;
import com.spacesim.world.Stage21HNpcMissionState;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.TerritorialTransitionState;

import java.util.List;
import java.util.Objects;

/**
 * Ordinary production composition root for one generated campaign.
 *
 * <p>The coordinator owns no replacement simulation authority. It keeps the accepted Stage-20/20.5
 * {@link GeneratedCampaignSession}, the mutable Stage-21A actor scheduler and the immutable snapshots
 * owned by the accepted Stage-21B-H systems together as one lifecycle boundary. Capture delegates to
 * {@link GeneratedCampaignAuthorityCheckpoint}, so save/load uses the existing final Stage-21I
 * envelope instead of introducing another persistence format.</p>
 */
public final class GeneratedCampaignCoordinator {
    private static final long ACTOR_REVIEW_CADENCE_TICKS = 600L;
    private static final int MAX_ACTOR_REVIEWS_PER_TICK = 8;

    private final GeneratedCampaignSession session;
    private final FactionLivingActorRuntime actors;
    private final List<FactionStrategicIntentState> strategicIntents;
    private final DiplomaticLifecycleState diplomacy;
    private final Stage19ConflictState warfare;
    private final FleetCommandState commands;
    private final StrategicOperationState operations;
    private final TerritorialTransitionState transitions;
    private final SettlementRecoveryState recovery;
    private final Stage21HNpcMissionState npcMissions;
    private final MigrationProvenance migrationProvenance;

    private GeneratedCampaignCoordinator(
            GeneratedCampaignAuthorityCheckpoint.RestoredAuthorities restored,
            MigrationProvenance migrationProvenance) {
        GeneratedCampaignAuthorityCheckpoint.RestoredAuthorities checked =
                Objects.requireNonNull(restored, "restored");
        this.session = checked.session();
        this.actors = checked.actors();
        this.strategicIntents = checked.strategicIntents();
        this.diplomacy = checked.diplomacy();
        this.warfare = checked.warfare();
        this.commands = checked.commands();
        this.operations = checked.operations();
        this.transitions = checked.transitions();
        this.recovery = checked.recovery();
        this.npcMissions = checked.npcMissions();
        this.migrationProvenance = Objects.requireNonNull(migrationProvenance, "migrationProvenance");
    }

    /**
     * Creates a new generated campaign and immediately adopts it into the accepted final Stage-21
     * authority chain.
     *
     * <p>The existing Stage-21I migration logic is reused only to construct the deterministic
     * initial A-H sidecars from the accepted generated Stage-20 world. The resulting new campaign is
     * then marked as native Stage-21H adoption rather than as a legacy-save migration.</p>
     *
     * @param rootSeed deterministic generated-world seed
     * @return ordinary production campaign coordinator
     */
    public static GeneratedCampaignCoordinator create(long rootSeed) {
        GeneratedCampaignSession initial = GeneratedCampaignSession.create(rootSeed);
        Stage21IGeneratedWorldRuntimePersistentState initialized =
                Stage21IGeneratedWorldRuntimeMigration.migrate(initial.captureState());
        Stage21IGeneratedWorldRuntimePersistentState nativeCheckpoint =
                Stage21IGeneratedWorldRuntimePersistentState.compose(initialized.stage21HRuntime());
        return restore(nativeCheckpoint);
    }

    /**
     * Restores the complete accepted authority chain from one final or migrated Stage-21 checkpoint.
     *
     * @param checkpoint validated final Stage-21 checkpoint
     * @return independent restored production campaign coordinator
     */
    public static GeneratedCampaignCoordinator restore(
            Stage21IGeneratedWorldRuntimePersistentState checkpoint) {
        Stage21IGeneratedWorldRuntimePersistentState saved = Objects.requireNonNull(checkpoint, "checkpoint");
        return new GeneratedCampaignCoordinator(
                GeneratedCampaignAuthorityCheckpoint.restore(saved),
                saved.migrationProvenance());
    }

    /**
     * Captures one atomic final Stage-21 checkpoint from the current accepted owners.
     *
     * <p>Migration provenance is lineage metadata, so it remains unchanged across later native
     * saves. The embedded authoritative world tick may advance, but the migration/adoption tick must
     * not be rewritten by ordinary save operations.</p>
     *
     * @return complete final Stage-21 checkpoint
     */
    public Stage21IGeneratedWorldRuntimePersistentState captureState() {
        Stage21IGeneratedWorldRuntimePersistentState current = GeneratedCampaignAuthorityCheckpoint.capture(
                session,
                actors,
                strategicIntents,
                diplomacy,
                warfare,
                commands,
                operations,
                transitions,
                recovery,
                npcMissions);
        return new Stage21IGeneratedWorldRuntimePersistentState(
                Stage21IGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21IGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                current.stage21HRuntime(),
                migrationProvenance);
    }

    /** @return ordinary generated-world session owning physical runtime progression */
    public GeneratedCampaignSession session() {
        return session;
    }

    /** @return deterministic generated-world root seed */
    public long rootSeed() {
        return session.rootSeed();
    }

    /** @return installed content catalogue used by ordinary presentation */
    public ContentCatalog content() {
        return session.content();
    }

    /** @return the single accepted mutable generated-world authority */
    public LiveRuntime runtime() {
        return session.runtime();
    }

    /** @return mutable Stage-21A autonomous-faction lifecycle owner */
    public FactionLivingActorRuntime actors() {
        return actors;
    }

    /** @return exact Stage-21B strategic-intent snapshots */
    public List<FactionStrategicIntentState> strategicIntents() {
        return strategicIntents;
    }

    /** @return exact Stage-21C diplomacy lifecycle snapshot */
    public DiplomaticLifecycleState diplomacy() {
        return diplomacy;
    }

    /** @return exact Stage-19 warfare snapshot referenced by diplomacy */
    public Stage19ConflictState warfare() {
        return warfare;
    }

    /** @return exact Stage-21D fleet command snapshot */
    public FleetCommandState commands() {
        return commands;
    }

    /** @return exact Stage-21E strategic operation snapshot */
    public StrategicOperationState operations() {
        return operations;
    }

    /** @return exact Stage-21F territorial transition snapshot */
    public TerritorialTransitionState transitions() {
        return transitions;
    }

    /** @return exact Stage-21G settlement recovery snapshot */
    public SettlementRecoveryState recovery() {
        return recovery;
    }

    /** @return exact Stage-21H NPC/mission/reputation/story snapshot */
    public Stage21HNpcMissionState npcMissions() {
        return npcMissions;
    }

    /** @return final-format migration/adoption lineage metadata */
    public MigrationProvenance migrationProvenance() {
        return migrationProvenance;
    }

    /** @return whether campaign progression is paused */
    public boolean isPaused() {
        return session.isPaused();
    }

    /** @return current simulation speed multiplier */
    public double timeScale() {
        return session.timeScale();
    }

    /**
     * Applies one pause value through the ordinary campaign clock authority.
     *
     * @param paused new campaign pause state
     */
    public void setPaused(boolean paused) {
        session.setPaused(paused);
    }

    /**
     * Applies one time scale through the ordinary campaign clock authority.
     *
     * @param scale finite non-negative simulation speed multiplier
     */
    public void setTimeScale(double scale) {
        session.setTimeScale(scale);
    }

    /**
     * Advances the ordinary campaign and reviews due Stage-21A actors at exact authoritative ticks.
     *
     * <p>Freight, physical movement and industry advance first inside the accepted Stage-20 session.
     * A due actor then receives a read-only freight-ledger projection from that exact tick. The
     * projection reuses the persistent Stage-20 order ID as evidence/decision provenance and owns no
     * replacement gameplay state.</p>
     *
     * @param realDeltaSeconds finite non-negative presentation delta
     * @return deterministic ordinary-session advance diagnostics
     */
    public GeneratedCampaignSession.AdvanceReport advanceFrame(float realDeltaSeconds) {
        return session.advanceFrame(realDeltaSeconds, this::reviewActorsAtTick);
    }

    private void reviewActorsAtTick(long authoritativeTick) {
        actors.reviewDue(
                authoritativeTick,
                MAX_ACTOR_REVIEWS_PER_TICK,
                ACTOR_REVIEW_CADENCE_TICKS,
                factionId -> GeneratedCampaignFactionObservationPublisher.publish(
                        session.captureState(),
                        factionId,
                        authoritativeTick));
    }
}
