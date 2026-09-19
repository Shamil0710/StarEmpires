package com.spacesim.campaign;

import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage228FlightDeckPersistenceMapper;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage228HangarPersistenceMapper;
import com.spacesim.persistence.Stage228SmallCraftPersistenceMapper;
import com.spacesim.world.SmallCraftFitAuthority;
import com.spacesim.world.SmallCraftFlightDeckOperations;
import com.spacesim.world.SmallCraftFlightDeckOperations.DeckProfile;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarRegistry;
import com.spacesim.world.SmallCraftRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongFunction;

/**
 * M22.8 extension seam over the accepted {@link GeneratedCampaignCoordinator}.
 *
 * <p>This class does not create a parallel campaign simulation. Stage-20/21 progression remains
 * owned by the embedded coordinator; M22.8 adds adjacent individual-craft, physical-hangar and
 * deterministic flight-deck sidecars around the accepted Stage-21 checkpoint. Small-craft fit
 * admission reuses the accepted Stage-22 core-pair engineering catalog and ordinary Stage-17.5
 * fitting authority.</p>
 */
public final class Stage228CampaignAuthority {
    private final GeneratedCampaignCoordinator coordinator;
    private final SmallCraftRegistry smallCraft;
    private final SmallCraftHangarRegistry hangars;
    private final SmallCraftFlightDeckOperations flightDeck;

    private Stage228CampaignAuthority(
            GeneratedCampaignCoordinator coordinator,
            SmallCraftRegistry smallCraft,
            SmallCraftHangarRegistry hangars,
            SmallCraftFlightDeckOperations flightDeck) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.smallCraft = Objects.requireNonNull(smallCraft, "smallCraft");
        this.hangars = Objects.requireNonNull(hangars, "hangars");
        this.flightDeck = Objects.requireNonNull(flightDeck, "flightDeck");
    }

    /**
     * Creates a new campaign with no seeded/free small craft, occupancy or deck operations.
     *
     * @param rootSeed deterministic generated-world root seed
     * @return M22.8 authority extension over ordinary campaign progression
     */
    public static Stage228CampaignAuthority create(long rootSeed) {
        return create(rootSeed, List.of());
    }

    /**
     * Creates a new campaign with explicit physical flight-deck profiles and no free craft.
     *
     * <p>Profiles configure handling throughput only; they do not create bay occupancy or craft.</p>
     *
     * @param rootSeed deterministic generated-world root seed
     * @param flightDeckProfiles explicit physical handling profiles
     * @return M22.8 authority extension
     */
    public static Stage228CampaignAuthority create(
            long rootSeed,
            Collection<DeckProfile> flightDeckProfiles) {
        SmallCraftFitAuthority fitAuthority = productionFitAuthority();
        SmallCraftRegistry smallCraft = SmallCraftRegistry.empty(fitAuthority);
        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(smallCraft);
        return new Stage228CampaignAuthority(
                GeneratedCampaignCoordinator.create(rootSeed),
                smallCraft,
                hangars,
                new SmallCraftFlightDeckOperations(
                        hangars,
                        Objects.requireNonNull(flightDeckProfiles, "flightDeckProfiles")));
    }

    /**
     * Restores a current M22.8 checkpoint exactly.
     *
     * <p>Craft is restored first, then physical occupancy, then launch/recovery state. This order
     * makes a persisted deck operation incapable of creating either its craft or its bay occupancy.</p>
     *
     * @param checkpoint current M22.8 campaign envelope
     * @return independent restored authority
     */
    public static Stage228CampaignAuthority restore(
            Stage228GeneratedCampaignPersistentState checkpoint) {
        Stage228GeneratedCampaignPersistentState saved =
                Objects.requireNonNull(checkpoint, "checkpoint");
        GeneratedCampaignCoordinator coordinator =
                GeneratedCampaignCoordinator.restore(saved.stage21Runtime());
        long authoritativeTick = coordinator.runtime().world().getAuthoritativeWorldTick();
        if (saved.flightDeck().lastProcessedTick() > authoritativeTick) {
            throw new IllegalArgumentException(
                    "Flight-deck watermark cannot exceed restored authoritative world tick");
        }
        SmallCraftFitAuthority fitAuthority = productionFitAuthority();
        SmallCraftRegistry smallCraft =
                Stage228SmallCraftPersistenceMapper.restore(saved.smallCraft(), fitAuthority);
        SmallCraftHangarRegistry hangars =
                Stage228HangarPersistenceMapper.restore(saved.hangars(), smallCraft);
        SmallCraftFlightDeckOperations flightDeck =
                Stage228FlightDeckPersistenceMapper.restore(saved.flightDeck(), hangars);
        return new Stage228CampaignAuthority(
                coordinator,
                smallCraft,
                hangars,
                flightDeck);
    }

    /**
     * Adopts an accepted Stage-21I checkpoint without granting any M22.8 state.
     *
     * @param checkpoint existing Stage-21I checkpoint
     * @return restored authority with empty craft/hangar/deck state
     */
    public static Stage228CampaignAuthority restoreStage21(
            Stage21IGeneratedWorldRuntimePersistentState checkpoint) {
        return restore(Stage228GeneratedCampaignPersistentState.adoptStage21(
                Objects.requireNonNull(checkpoint, "checkpoint")));
    }

    /**
     * Captures accepted Stage-21 state plus exact A/B/C sidecars.
     *
     * @return current versioned M22.8 campaign checkpoint
     */
    public Stage228GeneratedCampaignPersistentState captureState() {
        return Stage228GeneratedCampaignPersistentState.compose(
                coordinator.captureState(),
                Stage228SmallCraftPersistenceMapper.capture(smallCraft),
                Stage228HangarPersistenceMapper.capture(hangars),
                Stage228FlightDeckPersistenceMapper.capture(flightDeck));
    }

    /** @return accepted Stage-20/21 campaign composition root */
    public GeneratedCampaignCoordinator coordinator() {
        return coordinator;
    }

    /** @return individual physical small-craft identity registry */
    public SmallCraftRegistry smallCraft() {
        return smallCraft;
    }

    /** @return exact individual physical hangar occupancy registry */
    public SmallCraftHangarRegistry hangars() {
        return hangars;
    }

    /** @return deterministic M22.8C launch/recovery authority */
    public SmallCraftFlightDeckOperations flightDeck() {
        return flightDeck;
    }

    /**
     * Advances only the accepted ordinary campaign authority.
     *
     * <p>This compatibility overload is appropriate while no live flight-deck profiles are bound.
     * Production carrier operation should use the overload with a per-tick bay projection.</p>
     *
     * @param realDeltaSeconds finite non-negative presentation delta
     * @return ordinary campaign advance diagnostics
     */
    public GeneratedCampaignSession.AdvanceReport advanceFrame(float realDeltaSeconds) {
        if (!flightDeck.queued().isEmpty() || !flightDeck.active().isEmpty()) {
            throw new IllegalStateException(
                    "Active flight-deck work requires per-tick physical bay projection");
        }
        return coordinator.advanceFrame(realDeltaSeconds);
    }

    /**
     * Advances ordinary campaign progression and M22.8C on the same authoritative fixed ticks.
     *
     * <p>The supplied function is queried after each completed Stage-20/21 tick. The flight-deck
     * sequencer receives the exact session fixed-step duration and cannot advance the campaign clock
     * itself. Bay damage/capacity may therefore be projected freshly for every tick.</p>
     *
     * @param realDeltaSeconds finite non-negative presentation delta
     * @param bayProjectionByTick current physical bay definitions for each completed tick
     * @return ordinary campaign advance diagnostics
     */
    public GeneratedCampaignSession.AdvanceReport advanceFrame(
            float realDeltaSeconds,
            LongFunction<Map<BayId, BayDefinition>> bayProjectionByTick) {
        LongFunction<Map<BayId, BayDefinition>> provider =
                Objects.requireNonNull(bayProjectionByTick, "bayProjectionByTick");
        double fixedStepSeconds = coordinator.session().fixedStepSeconds();
        return coordinator.advanceFrame(realDeltaSeconds, tick -> {
            Map<BayId, BayDefinition> bays =
                    Objects.requireNonNull(provider.apply(tick), "bay projection");
            flightDeck.advanceFixedTick(tick, fixedStepSeconds, bays);
        });
    }

    private static SmallCraftFitAuthority productionFitAuthority() {
        return new SmallCraftFitAuthority(
                Stage22CorePairEngineeringCatalogLoader.loadDefault());
    }
}
