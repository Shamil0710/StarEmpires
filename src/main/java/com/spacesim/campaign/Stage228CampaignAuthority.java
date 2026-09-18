package com.spacesim.campaign;

import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage228SmallCraftPersistenceMapper;
import com.spacesim.world.SmallCraftFitAuthority;
import com.spacesim.world.SmallCraftRegistry;

import java.util.Objects;

/**
 * M22.8 extension seam over the accepted {@link GeneratedCampaignCoordinator}.
 *
 * <p>This class does not create a parallel campaign simulation. Stage-20/21 progression remains
 * owned by the embedded coordinator; M22.8A adds only the individual small-craft identity registry
 * and a versioned persistence envelope around the accepted Stage-21 checkpoint. Small-craft fit
 * admission reuses the accepted Stage-22 core-pair production engineering catalog and ordinary
 * Stage-17.5 fitting authority.</p>
 */
public final class Stage228CampaignAuthority {
    private final GeneratedCampaignCoordinator coordinator;
    private final SmallCraftRegistry smallCraft;

    private Stage228CampaignAuthority(
            GeneratedCampaignCoordinator coordinator,
            SmallCraftRegistry smallCraft) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.smallCraft = Objects.requireNonNull(smallCraft, "smallCraft");
    }

    /**
     * Creates a new campaign with no seeded or free small craft.
     *
     * @param rootSeed deterministic generated-world root seed
     * @return M22.8 authority extension over the ordinary campaign
     */
    public static Stage228CampaignAuthority create(long rootSeed) {
        SmallCraftFitAuthority fitAuthority = productionFitAuthority();
        return new Stage228CampaignAuthority(
                GeneratedCampaignCoordinator.create(rootSeed),
                SmallCraftRegistry.empty(fitAuthority));
    }

    /**
     * Restores a current M22.8 checkpoint exactly.
     *
     * <p>Decoded craft rows are admitted only after their design IDs resolve through the current
     * Stage-22 production catalog and their exact physical state passes the ordinary Stage-17.5
     * fitting validator.</p>
     *
     * @param checkpoint current M22.8 campaign envelope
     * @return independent restored authority
     */
    public static Stage228CampaignAuthority restore(Stage228GeneratedCampaignPersistentState checkpoint) {
        Stage228GeneratedCampaignPersistentState saved = Objects.requireNonNull(checkpoint, "checkpoint");
        SmallCraftFitAuthority fitAuthority = productionFitAuthority();
        return new Stage228CampaignAuthority(
                GeneratedCampaignCoordinator.restore(saved.stage21Runtime()),
                Stage228SmallCraftPersistenceMapper.restore(saved.smallCraft(), fitAuthority));
    }

    /**
     * Adopts an accepted Stage-21I checkpoint into M22.8 without granting small craft.
     *
     * @param checkpoint existing Stage-21I checkpoint
     * @return restored authority with an empty small-craft registry
     */
    public static Stage228CampaignAuthority restoreStage21(
            Stage21IGeneratedWorldRuntimePersistentState checkpoint) {
        return restore(Stage228GeneratedCampaignPersistentState.adoptStage21(
                Objects.requireNonNull(checkpoint, "checkpoint")));
    }

    /**
     * Captures the accepted Stage-21 state and exact M22.8A small-craft sidecar atomically at the caller boundary.
     *
     * @return current versioned M22.8 campaign checkpoint
     */
    public Stage228GeneratedCampaignPersistentState captureState() {
        return Stage228GeneratedCampaignPersistentState.compose(
                coordinator.captureState(),
                Stage228SmallCraftPersistenceMapper.capture(smallCraft));
    }

    /** @return accepted Stage-20/21 campaign composition root */
    public GeneratedCampaignCoordinator coordinator() {
        return coordinator;
    }

    /** @return individual physical small-craft identity registry */
    public SmallCraftRegistry smallCraft() {
        return smallCraft;
    }

    /**
     * Advances only the accepted ordinary campaign authority.
     *
     * @param realDeltaSeconds finite non-negative presentation delta
     * @return ordinary campaign advance diagnostics
     */
    public GeneratedCampaignSession.AdvanceReport advanceFrame(float realDeltaSeconds) {
        return coordinator.advanceFrame(realDeltaSeconds);
    }

    private static SmallCraftFitAuthority productionFitAuthority() {
        return new SmallCraftFitAuthority(Stage22CorePairEngineeringCatalogLoader.loadDefault());
    }
}
