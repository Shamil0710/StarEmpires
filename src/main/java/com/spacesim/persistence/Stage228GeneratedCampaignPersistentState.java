package com.spacesim.persistence;

import com.spacesim.player.PlayerState;
import com.spacesim.player.PlayerJournalState;
import com.spacesim.economy.ShipyardModuleCustodyState;
import com.spacesim.economy.ShipyardRepairQueueState;
import com.spacesim.economy.ShipyardRefitQueueState;

import java.util.Objects;

/**
 * Current M22.8 campaign persistence envelope extending the accepted Stage-21I checkpoint.
 *
 * <p>The embedded Stage-21 checkpoint remains authoritative for all pre-M22.8 systems. Sidecars own
 * only newly introduced durable carrier/small-craft state and the existing player contract. Native
 * M22.8A/B/C/M and supported Stage-21
 * checkpoints migrate without synthesizing missions, craft, hangar occupancy, deck work, supplies,
 * pending replacements, carrier-wing membership or player assets. Version 5 added exact optional
 * player persistence; version 6 retains actual personal commits and notification acknowledgement.
 * Version 8 retains individual used equipment and reserved repair work without granting products or healing.
 * Version 9 retains pending physical refits alongside ships, storage, repair and removed equipment.
 * Version 10 retains finite module handling and exact equipment aboard personal freight ships.
 * Version 11 retains reserved finished-product handling and completed physical work without inventory grants.
 * Version 12 retains physical yard construction without supplying structures, materials or working resources during migration.
 * Version 13 retains held money for civilian foreign repair without fabricating old payments.
 * Version 14 retains paid refit obligations and actual equipment ownership independently of storage.
 * Historical checkpoints receive empty non-granting journal and equipment sidecars where absent.</p>
 *
 * @param schemaVersion exact current M22.8 envelope schema
 * @param runtimeVersion exact current M22.8 runtime identifier
 * @param stage21Runtime complete accepted Stage-21I checkpoint
 * @param smallCraft individual M22.8A craft persistence sidecar
 * @param hangars individual M22.8B physical hangar occupancy sidecar
 * @param flightDeck M22.8C deterministic launch/recovery sidecar
 * @param operations M22.8M D/G/H mission/logistics/carrier-wing sidecar
 * @param playerState existing durable player state, or null for an uninitialized campaign
 * @param playerJournal actual committed personal history; empty for historical migrations
 * @param moduleCustody individual removed modules with exact damage and service age
 * @param repairQueue exact pending personal repair custody and actual completed work
 * @param refitQueue exact pending same-hull refits and incoming equipment custody
 * @param moduleTransfers exact pending handling of individual equipment
 * @param productTransfers exact pending handling of reserved fresh products
 * @param yardConstruction exact retained physical yard construction
 */
public record Stage228GeneratedCampaignPersistentState(
        int schemaVersion,
        String runtimeVersion,
        Stage21IGeneratedWorldRuntimePersistentState stage21Runtime,
        Stage228SmallCraftPersistentState smallCraft,
        Stage228HangarPersistentState hangars,
        Stage228FlightDeckPersistentState flightDeck,
        Stage228OperationsPersistentState operations,
        PlayerState playerState,
        PlayerJournalState playerJournal,
        ShipyardModuleCustodyState moduleCustody,
        ShipyardRepairQueueState repairQueue,
        ShipyardRefitQueueState refitQueue,
        com.spacesim.economy.ShipyardModuleTransferWorkQueue.State moduleTransfers,
        com.spacesim.economy.FinishedProductTransferWorkQueue.State productTransfers,
        com.spacesim.economy.Stage23YardConstructionWorkQueue.State yardConstruction) {

    /** Current M22.8 campaign envelope schema. */
    public static final int CURRENT_VERSION = 16;
    /** Current M22.8 campaign runtime contract. */
    public static final String CURRENT_RUNTIME_VERSION = "m22.8.generated-campaign.v16";

    /**
     * Source-compatible envelope without granting yard structures or work.
     * @param schemaVersion current envelope schema
     * @param runtimeVersion current runtime identity
     * @param stage21Runtime accepted authorities
     * @param smallCraft actual craft
     * @param hangars actual occupancy
     * @param flightDeck actual deck work
     * @param operations actual operations
     * @param playerState durable player
     * @param playerJournal committed history
     * @param moduleCustody individual equipment
     * @param repairQueue actual repair
     * @param refitQueue actual refit
     * @param moduleTransfers actual module handling
     * @param productTransfers actual product handling
     */
    public Stage228GeneratedCampaignPersistentState(int schemaVersion, String runtimeVersion,
            Stage21IGeneratedWorldRuntimePersistentState stage21Runtime, Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars, Stage228FlightDeckPersistentState flightDeck,
            Stage228OperationsPersistentState operations, PlayerState playerState, PlayerJournalState playerJournal,
            ShipyardModuleCustodyState moduleCustody, ShipyardRepairQueueState repairQueue, ShipyardRefitQueueState refitQueue,
            com.spacesim.economy.ShipyardModuleTransferWorkQueue.State moduleTransfers,
            com.spacesim.economy.FinishedProductTransferWorkQueue.State productTransfers) {
        this(schemaVersion, runtimeVersion, stage21Runtime, smallCraft, hangars, flightDeck, operations, playerState,
                playerJournal, moduleCustody, repairQueue, refitQueue, moduleTransfers, productTransfers,
                com.spacesim.economy.Stage23YardConstructionWorkQueue.State.empty());
    }

    /**
     * Source-compatible envelope without product handling grants.
     * @param schemaVersion current envelope schema
     * @param runtimeVersion current runtime identity
     * @param stage21Runtime accepted physical authorities
     * @param smallCraft exact small craft
     * @param hangars actual occupancy
     * @param flightDeck actual deck work
     * @param operations actual operations
     * @param playerState durable player
     * @param playerJournal committed history
     * @param moduleCustody exact equipment
     * @param repairQueue actual repair work
     * @param refitQueue actual refit work
     * @param moduleTransfers actual individual equipment handling
     */
    public Stage228GeneratedCampaignPersistentState(int schemaVersion, String runtimeVersion,
            Stage21IGeneratedWorldRuntimePersistentState stage21Runtime, Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars, Stage228FlightDeckPersistentState flightDeck,
            Stage228OperationsPersistentState operations, PlayerState playerState, PlayerJournalState playerJournal,
            ShipyardModuleCustodyState moduleCustody, ShipyardRepairQueueState repairQueue, ShipyardRefitQueueState refitQueue,
            com.spacesim.economy.ShipyardModuleTransferWorkQueue.State moduleTransfers) {
        this(schemaVersion, runtimeVersion, stage21Runtime, smallCraft, hangars, flightDeck, operations, playerState,
                playerJournal, moduleCustody, repairQueue, refitQueue, moduleTransfers, com.spacesim.economy.FinishedProductTransferWorkQueue.State.empty());
    }

    /**
     * Source-compatible envelope without granting equipment handling work.
     * @param schemaVersion current native schema
     * @param runtimeVersion current native identity
     * @param stage21Runtime accepted physical authorities
     * @param smallCraft exact individual craft
     * @param hangars actual occupancy
     * @param flightDeck actual deck work
     * @param operations actual operations
     * @param playerState durable player
     * @param playerJournal committed history
     * @param moduleCustody exact equipment
     * @param repairQueue actual repair work
     * @param refitQueue actual refit work
     */
    public Stage228GeneratedCampaignPersistentState(int schemaVersion, String runtimeVersion,
            Stage21IGeneratedWorldRuntimePersistentState stage21Runtime, Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars, Stage228FlightDeckPersistentState flightDeck,
            Stage228OperationsPersistentState operations, PlayerState playerState, PlayerJournalState playerJournal,
            ShipyardModuleCustodyState moduleCustody, ShipyardRepairQueueState repairQueue, ShipyardRefitQueueState refitQueue) {
        this(schemaVersion, runtimeVersion, stage21Runtime, smallCraft, hangars, flightDeck, operations, playerState,
                playerJournal, moduleCustody, repairQueue, refitQueue, com.spacesim.economy.ShipyardModuleTransferWorkQueue.State.empty());
    }

    /**
     * Composes earlier owners without inventing equipment or refit progress.
     * @param schemaVersion current schema
     * @param runtimeVersion current runtime
     * @param stage21Runtime accepted authorities
     * @param smallCraft exact craft
     * @param hangars exact occupancy
     * @param flightDeck exact deck
     * @param operations exact operations
     * @param playerState optional player
     * @param playerJournal actual history
     * @param moduleCustody exact used modules
     * @param repairQueue exact repair work
     */
    public Stage228GeneratedCampaignPersistentState(int schemaVersion, String runtimeVersion,
            Stage21IGeneratedWorldRuntimePersistentState stage21Runtime, Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars, Stage228FlightDeckPersistentState flightDeck,
            Stage228OperationsPersistentState operations, PlayerState playerState, PlayerJournalState playerJournal,
            ShipyardModuleCustodyState moduleCustody, ShipyardRepairQueueState repairQueue) {
        this(schemaVersion, runtimeVersion, stage21Runtime, smallCraft, hangars, flightDeck, operations,
                playerState, playerJournal, moduleCustody, repairQueue, ShipyardRefitQueueState.empty());
    }

    /**
     * Source-compatible composition without granting repair jobs or completed work.
     * @param schemaVersion current schema
     * @param runtimeVersion current runtime
     * @param stage21Runtime accepted authorities
     * @param smallCraft exact craft
     * @param hangars exact occupancy
     * @param flightDeck exact deck
     * @param operations exact operations
     * @param playerState optional player
     * @param playerJournal exact history
     * @param moduleCustody exact used equipment
     */
    public Stage228GeneratedCampaignPersistentState(int schemaVersion, String runtimeVersion,
            Stage21IGeneratedWorldRuntimePersistentState stage21Runtime, Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars, Stage228FlightDeckPersistentState flightDeck,
            Stage228OperationsPersistentState operations, PlayerState playerState, PlayerJournalState playerJournal,
            ShipyardModuleCustodyState moduleCustody) {
        this(schemaVersion, runtimeVersion, stage21Runtime, smallCraft, hangars, flightDeck, operations,
                playerState, playerJournal, moduleCustody, ShipyardRepairQueueState.empty());
    }

    /**
     * Source-compatible construction without synthesizing removed equipment.
     * @param schemaVersion current schema
     * @param runtimeVersion current runtime
     * @param stage21Runtime accepted authorities
     * @param smallCraft exact craft
     * @param hangars exact occupancy
     * @param flightDeck exact deck
     * @param operations exact operations
     * @param playerState optional player
     * @param playerJournal exact personal history
     */
    public Stage228GeneratedCampaignPersistentState(int schemaVersion, String runtimeVersion,
            Stage21IGeneratedWorldRuntimePersistentState stage21Runtime, Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars, Stage228FlightDeckPersistentState flightDeck,
            Stage228OperationsPersistentState operations, PlayerState playerState, PlayerJournalState playerJournal) {
        this(schemaVersion, runtimeVersion, stage21Runtime, smallCraft, hangars, flightDeck, operations,
                playerState, playerJournal, ShipyardModuleCustodyState.empty());
    }

    /**
     * Source-compatible composition without synthetic historical events.
     * @param schemaVersion current schema
     * @param runtimeVersion current runtime
     * @param stage21Runtime accepted authorities
     * @param smallCraft exact craft
     * @param hangars exact occupancy
     * @param flightDeck exact deck
     * @param operations exact operations
     * @param playerState existing optional player
     */
    public Stage228GeneratedCampaignPersistentState(int schemaVersion, String runtimeVersion,
            Stage21IGeneratedWorldRuntimePersistentState stage21Runtime, Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars, Stage228FlightDeckPersistentState flightDeck,
            Stage228OperationsPersistentState operations, PlayerState playerState) {
        this(schemaVersion, runtimeVersion, stage21Runtime, smallCraft, hangars, flightDeck, operations,
                playerState, PlayerJournalState.empty());
    }

    /**
     * Source-compatible composition without an initialized player; never invents player assets.
     *
     * @param schemaVersion current envelope schema
     * @param runtimeVersion current runtime identifier
     * @param stage21Runtime complete Stage-21 checkpoint
     * @param smallCraft exact craft state
     * @param hangars exact hangar state
     * @param flightDeck exact deck state
     * @param operations exact operations state
     */
    public Stage228GeneratedCampaignPersistentState(
            int schemaVersion, String runtimeVersion,
            Stage21IGeneratedWorldRuntimePersistentState stage21Runtime,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState flightDeck,
            Stage228OperationsPersistentState operations) {
        this(schemaVersion, runtimeVersion, stage21Runtime, smallCraft, hangars,
                flightDeck, operations, null);
    }

    /**
     * Compatibility constructor for pre-M22.8M call sites; adds an empty non-granting operations sidecar.
     *
     * @param schemaVersion exact current schema
     * @param runtimeVersion exact current runtime identifier
     * @param stage21Runtime accepted Stage-21I checkpoint
     * @param smallCraft exact A sidecar
     * @param hangars exact B sidecar
     * @param flightDeck exact C sidecar
     */
    public Stage228GeneratedCampaignPersistentState(
            int schemaVersion,
            String runtimeVersion,
            Stage21IGeneratedWorldRuntimePersistentState stage21Runtime,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState flightDeck) {
        this(
                schemaVersion,
                runtimeVersion,
                stage21Runtime,
                smallCraft,
                hangars,
                flightDeck,
                Stage228OperationsPersistentState.empty());
    }

    /**
     * Validates current envelope identity and embedded accepted authorities.
     *
     * @param schemaVersion exact current schema
     * @param runtimeVersion exact current runtime identifier
     * @param stage21Runtime accepted Stage-21I checkpoint
     * @param smallCraft exact A sidecar
     * @param hangars exact B sidecar
     * @param flightDeck exact C sidecar
     * @param operations exact M operations sidecar
     * @param playerState exact optional player checkpoint
     * @param playerJournal actual personal commit history
     * @param moduleCustody exact individual removed equipment
     * @param repairQueue exact actual repair progress and material custody
     * @param refitQueue exact actual refit progress and equipment custody
     * @param moduleTransfers actual incomplete equipment handling
     * @param productTransfers actual incomplete fresh-product handling
     * @param yardConstruction actual retained physical yard construction
     */
    public Stage228GeneratedCampaignPersistentState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported current M22.8 campaign schema: " + schemaVersion);
        }
        runtimeVersion = requireText(runtimeVersion, "runtimeVersion");
        if (!CURRENT_RUNTIME_VERSION.equals(runtimeVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported current M22.8 campaign runtime: " + runtimeVersion);
        }
        Objects.requireNonNull(stage21Runtime, "stage21Runtime");
        Objects.requireNonNull(smallCraft, "smallCraft");
        Objects.requireNonNull(hangars, "hangars");
        Objects.requireNonNull(flightDeck, "flightDeck");
        Objects.requireNonNull(operations, "operations");
        GeneratedCampaignPlayerCheckpointValidator.validate(stage21Runtime, playerState);
        Objects.requireNonNull(playerJournal, "playerJournal");
        if (playerState == null && !playerJournal.equals(PlayerJournalState.empty()))
            throw new IllegalArgumentException("Personal history requires an initialized player");
        var stage20 = stage21Runtime.stage21HRuntime().stage21GRuntime().stage21FRuntime().stage21ERuntime()
                .stage21DRuntime().stage21CRuntime().stage21BRuntime().stage21ARuntime().stage20Runtime();
        long tick = stage20.worldState().systems().stream().filter(s -> s.systemId().equals(stage20.activeSystemId()))
                .findFirst().orElseThrow().simulationState().clock().tick();
        if (playerJournal.entries().stream().anyMatch(e -> e.tick() > tick))
            throw new IllegalArgumentException("Personal history cannot contain future commits");
        Objects.requireNonNull(moduleCustody, "moduleCustody");
        Objects.requireNonNull(repairQueue, "repairQueue");
        Objects.requireNonNull(refitQueue, "refitQueue");
        ShipyardRepairCheckpointValidator.validate(repairQueue, stage20, playerState, tick);
        ShipyardRefitCheckpointValidator.validate(refitQueue, stage20, playerState, tick, repairQueue);
        Objects.requireNonNull(moduleTransfers);
        ShipyardModuleCustodyCheckpointValidator.validate(moduleCustody, stage20, playerState, tick, repairQueue, refitQueue, moduleTransfers);
        FinishedProductTransferCheckpointValidator.validate(productTransfers, stage20, playerState, tick, repairQueue, refitQueue, moduleTransfers);
        YardConstructionCheckpointValidator.validate(yardConstruction, stage20, playerState, tick);
    }

    /**
     * Composes all current owners without dropping pending refits.
     * @param stage21 accepted authorities
     * @param smallCraft exact craft
     * @param hangars exact occupancy
     * @param flightDeck exact deck
     * @param operations exact operations
     * @param playerState optional player
     * @param playerJournal actual history
     * @param moduleCustody exact used equipment
     * @param repairQueue exact repair work
     * @param refitQueue exact refit work
     * @return validated current envelope
     */
    public static Stage228GeneratedCampaignPersistentState compose(Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft, Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState flightDeck, Stage228OperationsPersistentState operations,
            PlayerState playerState, PlayerJournalState playerJournal, ShipyardModuleCustodyState moduleCustody,
            ShipyardRepairQueueState repairQueue, ShipyardRefitQueueState refitQueue) {
        return new Stage228GeneratedCampaignPersistentState(CURRENT_VERSION, CURRENT_RUNTIME_VERSION, stage21,
                smallCraft, hangars, flightDeck, operations, playerState, playerJournal, moduleCustody, repairQueue, refitQueue);
    }

    /**
     * Composes all physical owners, including incomplete module handling.
     * @param stage21 accepted physical authorities
     * @param smallCraft exact individual craft
     * @param hangars actual occupancy
     * @param flightDeck actual deck work
     * @param operations actual operations
     * @param playerState durable player
     * @param playerJournal committed history
     * @param moduleCustody exact equipment
     * @param repairQueue actual repair work
     * @param refitQueue actual refit work
     * @param moduleTransfers incomplete equipment handling
     * @return validated current envelope retaining all physical owners
     */
    public static Stage228GeneratedCampaignPersistentState compose(Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft, Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState flightDeck, Stage228OperationsPersistentState operations,
            PlayerState playerState, PlayerJournalState playerJournal, ShipyardModuleCustodyState moduleCustody,
            ShipyardRepairQueueState repairQueue, ShipyardRefitQueueState refitQueue,
            com.spacesim.economy.ShipyardModuleTransferWorkQueue.State moduleTransfers) {
        return new Stage228GeneratedCampaignPersistentState(CURRENT_VERSION, CURRENT_RUNTIME_VERSION, stage21,
                smallCraft, hangars, flightDeck, operations, playerState, playerJournal, moduleCustody, repairQueue, refitQueue, moduleTransfers);
    }

    /**
     * Composes all exact physical owners including pending fresh-product handling.
     * @param stage21 accepted authorities
     * @param smallCraft exact craft
     * @param hangars actual occupancy
     * @param flightDeck actual deck work
     * @param operations actual operations
     * @param playerState durable player
     * @param playerJournal committed history
     * @param moduleCustody exact equipment
     * @param repairQueue actual repair work
     * @param refitQueue actual refit work
     * @param moduleTransfers exact individual equipment handling
     * @param productTransfers exact reserved fresh-product handling
     * @return validated current native envelope
     */
    public static Stage228GeneratedCampaignPersistentState compose(Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft, Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState flightDeck, Stage228OperationsPersistentState operations,
            PlayerState playerState, PlayerJournalState playerJournal, ShipyardModuleCustodyState moduleCustody,
            ShipyardRepairQueueState repairQueue, ShipyardRefitQueueState refitQueue,
            com.spacesim.economy.ShipyardModuleTransferWorkQueue.State moduleTransfers,
            com.spacesim.economy.FinishedProductTransferWorkQueue.State productTransfers) {
        return new Stage228GeneratedCampaignPersistentState(CURRENT_VERSION, CURRENT_RUNTIME_VERSION, stage21, smallCraft,
                hangars, flightDeck, operations, playerState, playerJournal, moduleCustody, repairQueue, refitQueue, moduleTransfers, productTransfers);
    }

    /**
     * Composes all exact owners including actual pending repair work.
     * @param stage21 accepted authorities
     * @param smallCraft exact craft
     * @param hangars exact occupancy
     * @param flightDeck exact deck
     * @param operations exact operations
     * @param playerState optional player
     * @param playerJournal exact history
     * @param moduleCustody exact equipment
     * @param repairQueue exact repair custody/progress
     * @return current validated envelope
     */
    public static Stage228GeneratedCampaignPersistentState compose(Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft, Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState flightDeck, Stage228OperationsPersistentState operations,
            PlayerState playerState, PlayerJournalState playerJournal, ShipyardModuleCustodyState moduleCustody,
            ShipyardRepairQueueState repairQueue) {
        return new Stage228GeneratedCampaignPersistentState(CURRENT_VERSION, CURRENT_RUNTIME_VERSION, stage21,
                smallCraft, hangars, flightDeck, operations, playerState, playerJournal, moduleCustody, repairQueue);
    }

    /**
     * Composes exact current authorities, including individual used equipment.
     * @param stage21 accepted authorities
     * @param smallCraft exact craft
     * @param hangars exact occupancy
     * @param flightDeck exact deck
     * @param operations exact operations
     * @param playerState optional player
     * @param playerJournal actual personal history
     * @param moduleCustody exact individual used equipment
     * @return current validated envelope
     */
    public static Stage228GeneratedCampaignPersistentState compose(Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft, Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState flightDeck, Stage228OperationsPersistentState operations,
            PlayerState playerState, PlayerJournalState playerJournal, ShipyardModuleCustodyState moduleCustody) {
        return new Stage228GeneratedCampaignPersistentState(CURRENT_VERSION, CURRENT_RUNTIME_VERSION,
                stage21, smallCraft, hangars, flightDeck, operations, playerState, playerJournal, moduleCustody);
    }

    /**
     * Composes exact current state, retaining personal journal and acknowledgement.
     * @param stage21 accepted authorities
     * @param smallCraft exact craft
     * @param hangars exact occupancy
     * @param flightDeck exact deck
     * @param operations exact operations
     * @param playerState existing optional player
     * @param playerJournal actual personal history
     * @return current validated envelope
     */
    public static Stage228GeneratedCampaignPersistentState compose(Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft, Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState flightDeck, Stage228OperationsPersistentState operations,
            PlayerState playerState, PlayerJournalState playerJournal) {
        return new Stage228GeneratedCampaignPersistentState(CURRENT_VERSION, CURRENT_RUNTIME_VERSION,
                stage21, smallCraft, hangars, flightDeck, operations, playerState, playerJournal);
    }

    /**
     * Composes the existing authoritative checkpoint with an exact durable player state.
     *
     * @param stage21 complete Stage-21 checkpoint
     * @param smallCraft exact craft state
     * @param hangars exact hangar state
     * @param flightDeck exact deck state
     * @param operations exact operations state
     * @param playerState existing player state, or null before player initialization
     * @return current validated envelope
     */
    public static Stage228GeneratedCampaignPersistentState compose(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState flightDeck,
            Stage228OperationsPersistentState operations,
            PlayerState playerState) {
        return new Stage228GeneratedCampaignPersistentState(
                CURRENT_VERSION, CURRENT_RUNTIME_VERSION, stage21, smallCraft,
                hangars, flightDeck, operations, playerState);
    }

    /**
     * Composes current state from accepted Stage-21 and exact M22.8 sidecars.
     *
     * @param stage21 accepted Stage-21I state
     * @param smallCraft exact A sidecar
     * @param hangars exact B sidecar
     * @param flightDeck exact C sidecar
     * @param operations exact D/G/H sidecar
     * @return current envelope
     */
    public static Stage228GeneratedCampaignPersistentState compose(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState flightDeck,
            Stage228OperationsPersistentState operations) {
        return new Stage228GeneratedCampaignPersistentState(
                CURRENT_VERSION,
                CURRENT_RUNTIME_VERSION,
                Objects.requireNonNull(stage21, "stage21"),
                Objects.requireNonNull(smallCraft, "smallCraft"),
                Objects.requireNonNull(hangars, "hangars"),
                Objects.requireNonNull(flightDeck, "flightDeck"),
                Objects.requireNonNull(operations, "operations"));
    }

    /**
     * Compatibility composition seam that adds empty non-granting M state.
     *
     * @param stage21 accepted Stage-21I state
     * @param smallCraft exact A sidecar
     * @param hangars exact B sidecar
     * @param flightDeck exact C sidecar
     * @return current envelope with empty operations
     */
    public static Stage228GeneratedCampaignPersistentState compose(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState flightDeck) {
        return compose(
                stage21,
                smallCraft,
                hangars,
                flightDeck,
                Stage228OperationsPersistentState.empty());
    }

    /**
     * Compatibility composition seam that adds empty C/M state.
     *
     * @param stage21 accepted Stage-21I state
     * @param smallCraft exact A sidecar
     * @param hangars exact B sidecar
     * @return current envelope with no deck or operations state
     */
    public static Stage228GeneratedCampaignPersistentState compose(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars) {
        return compose(
                stage21,
                smallCraft,
                hangars,
                Stage228FlightDeckPersistentState.empty(),
                Stage228OperationsPersistentState.empty());
    }

    /**
     * Adopts Stage-21I without inventing any M22.8 assets/state.
     *
     * @param stage21 accepted Stage-21I checkpoint
     * @return current envelope with empty non-granting A/B/C/M sidecars
     */
    public static Stage228GeneratedCampaignPersistentState adoptStage21(
            Stage21IGeneratedWorldRuntimePersistentState stage21) {
        return compose(
                Objects.requireNonNull(stage21, "stage21"),
                Stage228SmallCraftPersistentState.empty(),
                Stage228HangarPersistentState.empty(),
                Stage228FlightDeckPersistentState.empty(),
                Stage228OperationsPersistentState.empty());
    }

    /**
     * Migrates native M22.8A into v6 without inventing later state.
     *
     * @param stage21 accepted Stage-21I checkpoint embedded by A
     * @param smallCraft exact A craft sidecar
     * @return current envelope preserving A and adding empty B/C/M state
     */
    public static Stage228GeneratedCampaignPersistentState adoptM22_8A(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft) {
        return compose(
                Objects.requireNonNull(stage21, "stage21"),
                Objects.requireNonNull(smallCraft, "smallCraft"),
                Stage228HangarPersistentState.empty(),
                Stage228FlightDeckPersistentState.empty(),
                Stage228OperationsPersistentState.empty());
    }

    /**
     * Migrates native M22.8B into v6 without inventing later state.
     *
     * @param stage21 accepted Stage-21I checkpoint embedded by B
     * @param smallCraft exact A craft sidecar
     * @param hangars exact B occupancy sidecar
     * @return current envelope preserving A/B with empty C/M state
     */
    public static Stage228GeneratedCampaignPersistentState adoptM22_8B(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars) {
        return compose(
                Objects.requireNonNull(stage21, "stage21"),
                Objects.requireNonNull(smallCraft, "smallCraft"),
                Objects.requireNonNull(hangars, "hangars"),
                Stage228FlightDeckPersistentState.empty(),
                Stage228OperationsPersistentState.empty());
    }

    /**
     * Migrates native M22.8C/v3 into v6 without inventing D/G/H state.
     *
     * @param stage21 accepted Stage-21I checkpoint
     * @param smallCraft exact A sidecar
     * @param hangars exact B sidecar
     * @param flightDeck exact C sidecar
     * @return current envelope preserving A/B/C with empty operations
     */
    public static Stage228GeneratedCampaignPersistentState adoptM22_8C(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState flightDeck) {
        return compose(
                Objects.requireNonNull(stage21, "stage21"),
                Objects.requireNonNull(smallCraft, "smallCraft"),
                Objects.requireNonNull(hangars, "hangars"),
                Objects.requireNonNull(flightDeck, "flightDeck"),
                Stage228OperationsPersistentState.empty());
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
