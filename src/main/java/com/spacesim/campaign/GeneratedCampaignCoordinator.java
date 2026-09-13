package com.spacesim.campaign;

import com.spacesim.campaign.GeneratedCampaignAuthorityCheckpoint.RestoredAuthorities;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimeMigration;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistenceCodec;

import java.util.Objects;

/**
 * Production orchestration boundary for one complete generated campaign.
 *
 * <p>The coordinator deliberately owns no competing gameplay authority. The mutable physical world,
 * economy, freight and clocks remain inside {@link GeneratedCampaignSession}; Stage-21 autonomous
 * actor state remains inside its accepted owner; later Stage-21 authority snapshots keep their
 * existing persistence contracts. This class only keeps those accepted owners together so the
 * ordinary client creates, advances, saves and restores one coherent campaign instead of saving the
 * Stage-20 runtime in isolation.</p>
 *
 * <p>New campaigns are lifted through the accepted Stage-21 migration path. Existing Stage-20.5
 * saves can therefore enter the same coordinator without ad-hoc default reconstruction, while
 * native Stage-21I saves retain the complete accepted authority chain.</p>
 */
public final class GeneratedCampaignCoordinator {
    private final RestoredAuthorities authorities;

    private GeneratedCampaignCoordinator(RestoredAuthorities authorities) {
        this.authorities = Objects.requireNonNull(authorities, "authorities");
    }

    /**
     * Creates a new generated campaign and composes the accepted Stage-21 authority chain around it.
     *
     * @param rootSeed deterministic generated-world seed
     * @return one production campaign coordinator
     */
    public static GeneratedCampaignCoordinator create(long rootSeed) {
        GeneratedCampaignSession session = GeneratedCampaignSession.create(rootSeed);
        Stage21IGeneratedWorldRuntimePersistentState initial =
                Stage21IGeneratedWorldRuntimeMigration.migrate(session.captureState());
        return restore(initial);
    }

    /**
     * Restores one native or already migrated final Stage-21 checkpoint.
     *
     * @param checkpoint complete accepted campaign checkpoint
     * @return restored coordinator
     */
    public static GeneratedCampaignCoordinator restore(
            Stage21IGeneratedWorldRuntimePersistentState checkpoint) {
        return new GeneratedCampaignCoordinator(
                GeneratedCampaignAuthorityCheckpoint.restore(
                        Objects.requireNonNull(checkpoint, "checkpoint")));
    }

    /**
     * Restores a native Stage-21I payload or migrates a supported Stage-20.5/21A-H payload.
     *
     * @param bytes persisted generated-campaign bytes
     * @return restored coordinator
     */
    public static GeneratedCampaignCoordinator decodeOrMigrate(byte[] bytes) {
        return restore(Stage21IGeneratedWorldRuntimePersistenceCodec.decodeOrMigrate(
                Objects.requireNonNull(bytes, "bytes")));
    }

    /** @return the single mutable ordinary generated-world session used by simulation and UI */
    public GeneratedCampaignSession session() {
        return authorities.session();
    }

    /**
     * Captures one native Stage-21I checkpoint from the currently composed accepted authorities.
     *
     * @return complete campaign checkpoint
     */
    public Stage21IGeneratedWorldRuntimePersistentState captureState() {
        return GeneratedCampaignAuthorityCheckpoint.capture(
                authorities.session(),
                authorities.actors(),
                authorities.strategicIntents(),
                authorities.diplomacy(),
                authorities.warfare(),
                authorities.commands(),
                authorities.operations(),
                authorities.transitions(),
                authorities.recovery(),
                authorities.npcMissions());
    }

    /**
     * Encodes the complete current campaign with the accepted final Stage-21 codec.
     *
     * @return deterministic native Stage-21I bytes
     */
    public byte[] encode() {
        return Stage21IGeneratedWorldRuntimePersistenceCodec.encode(captureState());
    }

    /**
     * Compatibility helper used by migration tests to prove that an old Stage-20.5 checkpoint enters
     * exactly the same production composition path as a native save.
     *
     * @param session ordinary campaign whose accepted Stage-20.5 state should be migrated
     * @return coordinator restored through the public migration codec boundary
     */
    static GeneratedCampaignCoordinator migrateStage20(GeneratedCampaignSession session) {
        byte[] legacy = Stage20GeneratedWorldRuntimePersistenceCodec.encode(
                Objects.requireNonNull(session, "session").captureState());
        return decodeOrMigrate(legacy);
    }

    /**
     * Exposes the accepted handoff only to package integration code and tests. Gameplay systems must
     * continue to mutate their original owners rather than this orchestration object.
     *
     * @return currently composed authority references and snapshots
     */
    RestoredAuthorities authorities() {
        return authorities;
    }
}
