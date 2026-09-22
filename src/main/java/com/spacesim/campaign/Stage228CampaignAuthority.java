package com.spacesim.campaign;

import com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage228FlightDeckPersistenceMapper;
import com.spacesim.persistence.Stage228GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage228HangarPersistenceMapper;
import com.spacesim.persistence.Stage228OperationsPersistenceMapper;
import com.spacesim.persistence.Stage228SmallCraftPersistenceMapper;
import com.spacesim.world.FleetId;
import com.spacesim.world.SmallCraftFitAuthority;
import com.spacesim.world.SmallCraftFlightDeckOperations;
import com.spacesim.world.SmallCraftFlightDeckOperations.DeckProfile;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.CarrierWingStrategicReadinessService.CarrierWingAssignment;
import com.spacesim.world.SmallCraftHangarRegistry;
import com.spacesim.world.SmallCraftMissionState;
import com.spacesim.world.SmallCraftPhysicalLogisticsService.LogisticsState;
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
    private SmallCraftMissionState missions;
    private LogisticsState logistics;
    private List<CarrierWingAssignment> carrierWings;

    private Stage228CampaignAuthority(
            GeneratedCampaignCoordinator coordinator,
            SmallCraftRegistry smallCraft,
            SmallCraftHangarRegistry hangars,
            SmallCraftFlightDeckOperations flightDeck,
            SmallCraftMissionState missions,
            LogisticsState logistics,
            Collection<CarrierWingAssignment> carrierWings) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.smallCraft = Objects.requireNonNull(smallCraft, "smallCraft");
        this.hangars = Objects.requireNonNull(hangars, "hangars");
        this.flightDeck = Objects.requireNonNull(flightDeck, "flightDeck");
        this.missions = Objects.requireNonNull(missions, "missions");
        this.logistics = Objects.requireNonNull(logistics, "logistics");
        this.carrierWings = List.copyOf(Objects.requireNonNull(carrierWings, "carrierWings"));
        validateOperations(
                this.smallCraft,
                this.hangars,
                this.flightDeck,
                this.missions,
                this.logistics,
                this.carrierWings);
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
                        Objects.requireNonNull(flightDeckProfiles, "flightDeckProfiles")),
                SmallCraftMissionState.empty(),
                LogisticsState.empty(),
                List.of());
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
        Stage228OperationsPersistenceMapper.RuntimeState operations =
                Stage228OperationsPersistenceMapper.restore(saved.operations());
        validateOperations(
                smallCraft,
                hangars,
                flightDeck,
                operations.missions(),
                operations.logistics(),
                operations.carrierWings());
        return new Stage228CampaignAuthority(
                coordinator,
                smallCraft,
                hangars,
                flightDeck,
                operations.missions(),
                operations.logistics(),
                operations.carrierWings());
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
     * Captures accepted Stage-21 state plus exact A/B/C and D/G/H operations sidecars.
     *
     * @return current versioned M22.8 campaign checkpoint
     */
    public Stage228GeneratedCampaignPersistentState captureState() {
        return Stage228GeneratedCampaignPersistentState.compose(
                coordinator.captureState(),
                Stage228SmallCraftPersistenceMapper.capture(smallCraft),
                Stage228HangarPersistenceMapper.capture(hangars),
                Stage228FlightDeckPersistenceMapper.capture(flightDeck),
                Stage228OperationsPersistenceMapper.capture(
                        missions, logistics, carrierWings));
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

    /** @return current immutable M22.8D mission state */
    public SmallCraftMissionState missions() {
        return missions;
    }

    /** @return current immutable M22.8G pending-delivery state */
    public LogisticsState logistics() {
        return logistics;
    }

    /** @return current immutable M22.8H carrier-wing associations */
    public List<CarrierWingAssignment> carrierWings() {
        return carrierWings;
    }

    /**
     * Commits a validated D mission-state transition into the campaign composition root.
     *
     * @param next validated immutable mission state returned by the shared D command authority
     */
    public void commitMissionState(SmallCraftMissionState next) {
        SmallCraftMissionState checked = Objects.requireNonNull(next, "next");
        validateOperations(smallCraft, hangars, flightDeck, checked, logistics, carrierWings);
        missions = checked;
    }

    /**
     * Commits a validated G logistics-state transition without granting delivery or inventory.
     *
     * @param next immutable pending-delivery state returned by the G physical logistics authority
     */
    public void commitLogisticsState(LogisticsState next) {
        LogisticsState checked = Objects.requireNonNull(next, "next");
        validateOperations(smallCraft, hangars, flightDeck, missions, checked, carrierWings);
        logistics = checked;
    }

    /**
     * Commits explicit H carrier-wing associations after cross-link validation.
     *
     * @param next current strategic associations
     */
    public void commitCarrierWings(Collection<CarrierWingAssignment> next) {
        List<CarrierWingAssignment> checked =
                List.copyOf(Objects.requireNonNull(next, "next"));
        validateOperations(smallCraft, hangars, flightDeck, missions, logistics, checked);
        carrierWings = checked;
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

    private static void validateOperations(
            SmallCraftRegistry craft,
            SmallCraftHangarRegistry hangars,
            SmallCraftFlightDeckOperations flightDeck,
            SmallCraftMissionState missions,
            LogisticsState logistics,
            Collection<CarrierWingAssignment> wings) {
        Objects.requireNonNull(craft, "craft");
        Objects.requireNonNull(hangars, "hangars");
        SmallCraftFlightDeckOperations checkedDeck =
                Objects.requireNonNull(flightDeck, "flightDeck");
        SmallCraftMissionState checkedMissions =
                Objects.requireNonNull(missions, "missions");
        LogisticsState checkedLogistics = Objects.requireNonNull(logistics, "logistics");
        Objects.requireNonNull(wings, "wings");

        java.util.HashSet<com.spacesim.world.SmallCraftId> pending =
                new java.util.HashSet<>();
        for (var delivery : checkedLogistics.pendingDeliveries()) {
            var state = craft.find(delivery.craftId()).orElseThrow(
                    () -> new IllegalArgumentException(
                            "pending delivery references absent produced craft: "
                                    + delivery.craftId()));
            if (!state.designId().equals(delivery.designId())) {
                throw new IllegalArgumentException(
                        "pending delivery design differs from produced craft: "
                                + delivery.craftId());
            }
            if (hangars.find(delivery.craftId()).isPresent()) {
                throw new IllegalArgumentException(
                        "pending-delivery craft cannot already occupy a bay: "
                                + delivery.craftId());
            }
            if (checkedMissions.activeMissionFor(delivery.craftId()).isPresent()) {
                throw new IllegalArgumentException(
                        "pending-delivery craft cannot have an active mission: "
                                + delivery.craftId());
            }
            pending.add(delivery.craftId());
        }

        java.util.HashMap<com.spacesim.world.SmallCraftId,
                SmallCraftFlightDeckOperations.OperationKind> deckOperationByCraft =
                new java.util.HashMap<>();
        for (var request : checkedDeck.queued()) {
            deckOperationByCraft.put(request.craftId(), request.kind());
        }
        for (var active : checkedDeck.active()) {
            deckOperationByCraft.put(active.request().craftId(), active.request().kind());
        }

        for (var mission : checkedMissions.missions()) {
            if (mission.craftId().value() >= craft.nextIdValue()) {
                throw new IllegalArgumentException(
                        "mission references never-issued small-craft identity: "
                                + mission.craftId());
            }
            if (mission.status().active()
                    && craft.find(mission.craftId()).isEmpty()) {
                throw new IllegalArgumentException(
                        "active mission references lost/absent craft: "
                                + mission.craftId());
            }

            var occupancy = hangars.find(mission.craftId());
            var deckKind = deckOperationByCraft.get(mission.craftId());
            switch (mission.status()) {
                case LAUNCH_QUEUED -> {
                    if (occupancy.isEmpty()
                            || deckKind != SmallCraftFlightDeckOperations.OperationKind.LAUNCH) {
                        throw new IllegalArgumentException(
                                "launch-queued mission requires matching physical launch operation: "
                                        + mission.craftId());
                    }
                    var state = occupancy.orElseThrow().state();
                    if (state != com.spacesim.world.SmallCraftHangarCapacity.OccupancyState.READY
                            && state
                            != com.spacesim.world.SmallCraftHangarCapacity.OccupancyState.LAUNCHING) {
                        throw new IllegalArgumentException(
                                "launch-queued mission requires READY/LAUNCHING bay occupancy: "
                                        + mission.craftId());
                    }
                }
                case ACTIVE, RETURNING -> {
                    if (occupancy.isPresent() || deckKind != null) {
                        throw new IllegalArgumentException(
                                "deployed mission cannot retain bay/deck occupancy: "
                                        + mission.craftId());
                    }
                }
                case RECOVERY_PENDING -> {
                    if (deckKind != null
                            && deckKind
                            != SmallCraftFlightDeckOperations.OperationKind.RECOVERY) {
                        throw new IllegalArgumentException(
                                "recovery-pending mission cannot have a launch operation: "
                                        + mission.craftId());
                    }
                    if (deckKind == null
                            && (occupancy.isEmpty()
                            || occupancy.orElseThrow().state()
                            != com.spacesim.world.SmallCraftHangarCapacity.OccupancyState.SERVICING)) {
                        throw new IllegalArgumentException(
                                "recovery-pending mission must be recovering or physically serviced: "
                                        + mission.craftId());
                    }
                }
                case COMPLETE, CANCELLED, FAILED -> {
                    // Terminal mission rows are provenance only; physical state is validated elsewhere.
                }
            }
        }

        java.util.HashSet<FleetId> carrierIds = new java.util.HashSet<>();
        java.util.HashSet<com.spacesim.world.SmallCraftId> wingCraft =
                new java.util.HashSet<>();
        for (CarrierWingAssignment wing :
                List.copyOf(wings)) {
            CarrierWingAssignment checked = Objects.requireNonNull(wing, "carrierWing");
            if (!carrierIds.add(checked.carrierFleetId())) {
                throw new IllegalArgumentException(
                        "duplicate carrier wing FleetId: " + checked.carrierFleetId());
            }
            for (var id : checked.craftIds()) {
                if (!wingCraft.add(id)) {
                    throw new IllegalArgumentException(
                            "craft belongs to multiple carrier wings: " + id);
                }
                if (id.value() >= craft.nextIdValue()) {
                    throw new IllegalArgumentException(
                            "carrier wing references never-issued small-craft identity: " + id);
                }
                if (pending.contains(id)) {
                    throw new IllegalArgumentException(
                            "pending-delivery craft cannot belong to a carrier wing: " + id);
                }
                var state = craft.find(id);
                if (state.isEmpty()) {
                    continue; // retained lost identity; never synthesize it.
                }
                if (!state.orElseThrow().stableFactionId()
                        .equals(checked.stableFactionId())) {
                    throw new IllegalArgumentException(
                            "carrier wing craft ownership differs from carrier faction: " + id);
                }
                var assignment = hangars.find(id);
                if (assignment.isPresent()) {
                    if (!assignment.orElseThrow().bayId().hostStableId()
                            .equals(checked.hostStableId())) {
                        throw new IllegalArgumentException(
                                "carrier wing craft occupies another physical host: " + id);
                    }
                } else if (checkedMissions.activeMissionFor(id).isEmpty()) {
                    throw new IllegalArgumentException(
                            "surviving carrier-wing craft must be embarked or on an active mission: "
                                    + id);
                }
            }
        }
    }

    private static SmallCraftFitAuthority productionFitAuthority() {
        return new SmallCraftFitAuthority(
                Stage228SmallCraftEngineeringCatalogLoader.loadDefault());
    }
}
