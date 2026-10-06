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

import com.spacesim.player.PlayerState;
import com.spacesim.components.WalletComponent;
import com.spacesim.world.Stage21HNpcMissionService.PlayerCommand;
import com.spacesim.world.Stage21HNpcMissionState.MissionContract;
import com.spacesim.world.Stage21HPlayerMissionAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private PlayerState playerState;
    private com.spacesim.player.PlayerJournalState playerJournal = com.spacesim.player.PlayerJournalState.empty();
    private com.spacesim.economy.ShipyardModuleCustodyState moduleCustody =
            com.spacesim.economy.ShipyardModuleCustodyState.empty();
    private com.spacesim.economy.ShipyardRepairWorkQueue repairQueue = new com.spacesim.economy.ShipyardRepairWorkQueue(
            com.spacesim.economy.ShipyardRepairQueueState.empty());
    private com.spacesim.economy.ShipyardRefitWorkQueue refitQueue = new com.spacesim.economy.ShipyardRefitWorkQueue(
            com.spacesim.economy.ShipyardRefitQueueState.empty());
    private boolean freshPilotStart;
    private com.spacesim.economy.ShipyardModuleTransferWorkQueue moduleTransfers = new com.spacesim.economy.ShipyardModuleTransferWorkQueue(
            com.spacesim.content.Stage22CivilianMiningProductionPath.loadProducts(), com.spacesim.economy.ShipyardModuleTransferWorkQueue.State.empty());
    private com.spacesim.economy.FinishedProductTransferWorkQueue productTransfers = new com.spacesim.economy.FinishedProductTransferWorkQueue(
            com.spacesim.content.Stage22CivilianMiningProductionPath.loadProducts(), com.spacesim.economy.FinishedProductTransferWorkQueue.State.empty());
    private float thrustAxisX;
    private com.spacesim.economy.Stage23YardConstructionWorkQueue yardConstruction = new com.spacesim.economy.Stage23YardConstructionWorkQueue(
            com.spacesim.content.Stage23YardConstructionCatalog.loadDefault(), com.spacesim.content.Stage18ResourceOntologyLoader.loadDefault(),
            com.spacesim.economy.Stage23YardConstructionWorkQueue.State.empty());
    private float thrustAxisY;
    private boolean braking;
    private final com.spacesim.systems.PlayerDirectControlSystem playerFlight = new com.spacesim.systems.PlayerDirectControlSystem();

    /** Explicit, finite new-game savings; never issued during restore. */
    public static final long PILOT_SAVINGS_MILLI_CREDITS = 100_000_000L;
    /** Disclosed price of the already materialized reserve hull. */
    public static final long PILOT_SHIP_PRICE_MILLI_CREDITS = 25_000_000L;
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
            Collection<CarrierWingAssignment> carrierWings,
            PlayerState playerState) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.playerState = playerState;
        this.coordinator.runtime().world().setExternalFittedRecoveryOwner(id ->
                this.playerState != null && this.playerState.ownedFleetIds().contains(id)
                && (!id.equals(this.playerState.activeFleetId()) || !this.playerState.docked())
                && this.coordinator.runtime().world().findFleetJump(id).isEmpty()
                && canAdvanceExactPersonalFleet(id));
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
                this.carrierWings,
                this.coordinator.runtime().world().getAuthoritativeWorldTick());
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
        return create(rootSeed, flightDeckProfiles,
                com.spacesim.persistence.Stage20FreightRuntimeMaterializer.ReserveLoadoutPolicy.CIVILIAN_MINING_RESERVE);
    }

    /**
     * Creates explicit initial NPC manifests, including historical baseline worlds when requested.
     * @param rootSeed deterministic world seed
     * @param flightDeckProfiles actual deck profiles
     * @param reservePolicy initial NPC capital loadouts
     * @return ordinary campaign authority
     */
    public static Stage228CampaignAuthority create(long rootSeed, Collection<DeckProfile> flightDeckProfiles,
            com.spacesim.persistence.Stage20FreightRuntimeMaterializer.ReserveLoadoutPolicy reservePolicy) {
        SmallCraftFitAuthority fitAuthority = productionFitAuthority();
        SmallCraftRegistry smallCraft = SmallCraftRegistry.empty(fitAuthority);
        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(smallCraft);
        Stage228CampaignAuthority created = new Stage228CampaignAuthority(
                GeneratedCampaignCoordinator.create(rootSeed, reservePolicy),
                smallCraft,
                hangars,
                new SmallCraftFlightDeckOperations(
                        hangars,
                        Objects.requireNonNull(flightDeckProfiles, "flightDeckProfiles")),
                SmallCraftMissionState.empty(),
                LogisticsState.empty(),
                List.of(),
                null);
        GeneratedCampaignNpcPlacement.install(created.coordinator);
        created.freshPilotStart = true;
        return created;
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
                operations.carrierWings(),
                authoritativeTick);
        var restored = new Stage228CampaignAuthority(
                coordinator,
                smallCraft,
                hangars,
                flightDeck,
                operations.missions(),
                operations.logistics(),
                operations.carrierWings(),
                saved.playerState());
        restored.playerJournal = saved.playerJournal();
        restored.moduleCustody = saved.moduleCustody();
        com.spacesim.economy.ShipyardModuleCustodyStorage.bind(restored.moduleCustody,
                com.spacesim.content.Stage22CivilianMiningProductionPath.loadProducts(),
                restored::moduleStorage);
        coordinator.runtime().freight().synchronizeModuleCustody(restored.moduleCustody);
        restored.repairQueue = new com.spacesim.economy.ShipyardRepairWorkQueue(saved.repairQueue());
        restored.repairQueue.restoreReservations(id -> coordinator.runtime().infrastructure().endpoint(id).storage());
        restored.refitQueue = new com.spacesim.economy.ShipyardRefitWorkQueue(saved.refitQueue());
        restored.refitQueue.validateUsedReservations(restored.moduleCustody);
        restored.refitQueue.restoreReservations(id -> coordinator.runtime().infrastructure().endpoint(id).storage());
        restored.moduleTransfers = new com.spacesim.economy.ShipyardModuleTransferWorkQueue(
                com.spacesim.content.Stage22CivilianMiningProductionPath.loadProducts(), saved.moduleTransfers());
        restored.moduleTransfers.validateCustody(restored.moduleCustody);
        restored.productTransfers = new com.spacesim.economy.FinishedProductTransferWorkQueue(
                com.spacesim.content.Stage22CivilianMiningProductionPath.loadProducts(), saved.productTransfers());
        restored.productTransfers.bindReservations(restored::productStorage);
        restored.yardConstruction = new com.spacesim.economy.Stage23YardConstructionWorkQueue(
                com.spacesim.content.Stage23YardConstructionCatalog.loadDefault(), com.spacesim.content.Stage18ResourceOntologyLoader.loadDefault(),
                saved.yardConstruction());
        restored.yardConstruction.bindReservations(id -> restored.coordinator.runtime().infrastructure().endpoint(id).storage());
        return restored;
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
        var accepted = coordinator.captureState();
        reconcileModuleLosses();
        return new Stage228GeneratedCampaignPersistentState(Stage228GeneratedCampaignPersistentState.CURRENT_VERSION,
                Stage228GeneratedCampaignPersistentState.CURRENT_RUNTIME_VERSION,
                accepted,
                Stage228SmallCraftPersistenceMapper.capture(smallCraft),
                Stage228HangarPersistenceMapper.capture(hangars),
                Stage228FlightDeckPersistenceMapper.capture(flightDeck),
                Stage228OperationsPersistenceMapper.capture(
                        missions, logistics, carrierWings),
                playerState, playerJournal, moduleCustody, repairQueue.capture(), refitQueue.capture(), moduleTransfers.capture(), productTransfers.capture(), yardConstruction.capture());
    }

    /** @return immutable personal committed history, independent of the selected knowledge viewer */
    public com.spacesim.player.PlayerJournalState playerJournal() { return playerJournal; }

    /** @return exact individual used equipment inventory, never pristine product counts */
    public com.spacesim.economy.ShipyardModuleCustodyState moduleCustody() { return moduleCustody; }
    /** @return exact pending individual equipment handling */
    public com.spacesim.economy.ShipyardModuleTransferWorkQueue.State moduleTransfers() { return moduleTransfers.capture(); }
    /** @return exact pending reserved handling of fresh finished goods */
    public com.spacesim.economy.FinishedProductTransferWorkQueue.State productTransfers() { return productTransfers.capture(); }

    /** @return retained physical yard construction and actual completed-work watermark */
    public com.spacesim.economy.Stage23YardConstructionWorkQueue.State yardConstruction() { return yardConstruction.capture(); }

    private com.spacesim.economy.Stage18StationStorage productStorage(String id) {
        var carrier = moduleCarrier(id);
        return carrier.isPresent() ? coordinator.runtime().freight().moduleCargoStorage(carrier.orElseThrow())
                : coordinator.runtime().infrastructure().endpoint(id).storage();
    }

    /** @return actual pending repair custody/progress, independent of presentation viewer */
    public com.spacesim.economy.ShipyardRepairQueueState repairQueue() { return repairQueue.capture(); }

    /** @return actual pending refit equipment custody and performed work */
    public com.spacesim.economy.ShipyardRefitQueueState refitQueue() { return refitQueue.capture(); }

    private void recordPersonalCommit(com.spacesim.player.PlayerJournalState.Kind kind, String action,
            String endpoint, String subject, long quantity, long walletDelta, FleetId fleet) {
        if (playerState == null) return;
        playerJournal = playerJournal.append(coordinator.runtime().world().getAuthoritativeWorldTick(), kind,
                action, endpoint == null ? "" : endpoint, subject == null ? "" : subject,
                Math.max(0, quantity), walletDelta, fleet == null ? 0 : fleet.value());
    }

    /**
     * Returns exact durable player data without treating the knowledge viewer as ownership.
     *
     * <p>Mission settlement may update the personal balance through the existing escrow service.
     * This does not install legacy cargo/control services or grant independent-pilot starter assets.</p>
     *
     * @return existing player state, or empty for historical/uninitialized campaigns
     */
    public Optional<PlayerState> playerState() {
        return Optional.ofNullable(playerState);
    }

    /** @return whether this unsaved new-game session can still initialize a pilot */
    public boolean canStartIndependentPilot() { return freshPilotStart && playerState == null; }

    /** Immutable campaign-bound new-game confirmation. */
    public static final class PilotStartPreview {
        private final Stage228CampaignAuthority owner;
        private final Stage228GeneratedCampaignPersistentState baseline;
        private final FleetId fleet;
        private final String seller;
        private PilotStartPreview(Stage228CampaignAuthority owner,
                Stage228GeneratedCampaignPersistentState baseline, FleetId fleet, String seller) {
            this.owner = owner; this.baseline = baseline; this.fleet = fleet; this.seller = seller;
        }
        /** @return whether an existing reserve asset is available */
        public boolean allowed() { return fleet != null; }
        /** @return offered physical asset, used internally by the interface */
        public FleetId fleetId() { return fleet; }
    }

    /**
     * Validates the disclosed new-game purchase on an isolated checkpoint.
     * @return pure confirmation token; restoring a historical save cannot enable this action
     */
    public PilotStartPreview previewIndependentPilotStart() {
        var baseline = captureState();
        if (!canStartIndependentPilot()) return new PilotStartPreview(this, baseline, null, null);
        var reserve = coordinator.runtime().freight().capture().freighters().stream()
                .filter(f -> f.phase() == com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase.IDLE)
                .filter(f -> f.cargoMassKg() == 0d && coordinator.runtime().world().findFleet(f.fleetId()).isPresent())
                .findFirst().orElse(null);
        if (reserve == null) return new PilotStartPreview(this, baseline, null, null);
        var candidate = restore(baseline);
        try {
            candidate.initializePilot(reserve.fleetId(), reserve.stableFactionId());
            candidate.captureState();
            return new PilotStartPreview(this, baseline, reserve.fleetId(), reserve.stableFactionId());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return new PilotStartPreview(this, baseline, null, null);
        }
    }

    /**
     * Commits a once-only start after verifying the exact campaign and sale again.
     * @param preview current confirmation token issued by this campaign
     * @return initialized existing PlayerState
     */
    public PlayerState submitIndependentPilotStart(PilotStartPreview preview) {
        var checked = Objects.requireNonNull(preview);
        if (checked.owner != this || !checked.allowed() || !canStartIndependentPilot()
                || !checked.baseline.equals(captureState())) throw new IllegalStateException("Pilot start is stale or unavailable");
        int briefingCount = initializePilot(checked.fleet, checked.seller);
        freshPilotStart = false;
        recordPersonalCommit(com.spacesim.player.PlayerJournalState.Kind.COMMAND, "PILOT_START", checked.seller,
                "", 1, PILOT_SAVINGS_MILLI_CREDITS - PILOT_SHIP_PRICE_MILLI_CREDITS, checked.fleet);
        if (briefingCount > 0) recordPersonalCommit(com.spacesim.player.PlayerJournalState.Kind.COMMAND,
                "SELLER_STATION_BRIEFING", checked.seller, "paid-ship-sale", briefingCount, 0, checked.fleet);
        return playerState;
    }

    private int initializePilot(FleetId fleetId, String seller) {
        if (playerState != null) throw new IllegalStateException("Player already initialized");
        var runtime = coordinator.runtime();
        var reserve = runtime.freight().findFreighter(fleetId).orElseThrow();
        var placement = runtime.world().findFleet(fleetId).orElseThrow();
        if (reserve.phase() != com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase.IDLE
                || reserve.cargoMassKg() != 0d || !reserve.stableFactionId().equals(seller)
                || placement.locationKind() != com.spacesim.world.FleetLocationKind.IN_SYSTEM)
            throw new IllegalStateException("Asset is not an uncommitted local reserve");
        var initial = new PlayerState(PILOT_SAVINGS_MILLI_CREDITS, null, List.of(), List.of(), null,
                List.of(placement.systemId()), List.of(), placement.systemId());
        runtime.world().findSession(runtime.world().getActiveSystemId()).orElseThrow().getLedger()
                .recordMoneySource("PLAYER", PILOT_SAVINGS_MILLI_CREDITS, "new-game-pilot-personal-savings.v1");
        var adapter = com.spacesim.player.PlayerRuntime.attachToCampaign(runtime.world(), coordinator.content(), initial);
        if (!new com.spacesim.player.PlayerOwnershipService(adapter)
                .purchaseFactionFleet(fleetId, seller, PILOT_SHIP_PRICE_MILLI_CREDITS))
            throw new IllegalStateException("Reserve purchase cannot settle");
        playerState = adapter.player();
        initializePilotMarkets();
        var permitted = runtime.industry().industrial().stations().stream()
                .filter(s -> s.stableFactionId().equals(seller)
                        && GeneratedCampaignStationSalePolicy.priceMilliCredits(s.stationArchetypeId()) > 0)
                .map(s -> new com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef(s.systemId(),
                        com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind.INFRASTRUCTURE, s.stationId())).toList();
        var received = runtime.receiveSellerStationBriefing(playerState, fleetId, seller, permitted);
        var references = new java.util.ArrayList<>(playerState.discoveredObjects());
        for (var ref : received) pilotMarketReference(ref.objectId()).ifPresent(reference -> {
            if (!references.contains(reference)) references.add(reference);
        });
        var systems = new java.util.ArrayList<>(playerState.discoveredSystemIds());
        for (var reference : references) if (!systems.contains(reference.systemId())) systems.add(reference.systemId());
        var p = playerState;
        playerState = new PlayerState(p.walletMilliCredits(), p.factionContentId(), p.reputations(),
                p.ownedFleetIds(), p.activeFleetId(), systems, references, p.homeSystemId(), p.dockedAt(),
                p.fleetOrders(), p.threatIntel(), p.ownedConstructionProjectIds(), p.ownedStations());
        return received.size();
    }

    /** Stable binding of an ordinary station wallet to its existing physical storage endpoint. */
    public static final String PILOT_MARKET_IDENTITY_PREFIX = "Generated market ";
    /** Persisted ordinary identity tag for stock-responsive new-game market policy v2. */
    public static final String PILOT_MARKET_V2_IDENTITY_PREFIX = PILOT_MARKET_IDENTITY_PREFIX + "v2 ";
    /** Finite disclosed per-station opening liquidity, created only by new-game confirmation. */
    public static final long PILOT_MARKET_INITIAL_LIQUIDITY = 10_000_000L;

    /**
     * Previews the existing zero-grant faction foundation transition on an isolated checkpoint.
     * @param factionId new stable world identity
     * @param displayName disclosed public name
     * @return exact-state confirmation, without changes to the live world
     */
    public PlayerFactionFoundationPreview previewPlayerFactionFoundation(String factionId, String displayName) {
        var baseline = captureState();
        com.spacesim.persistence.Stage228GeneratedCampaignPersistentState candidate = null;
        try {
            var s = coordinator.runtime().captureState();
            var updated = com.spacesim.player.PlayerFactionFoundationService.foundFaction(
                    new com.spacesim.player.PlayableWorldState(com.spacesim.player.PlayableWorldState.CURRENT_VERSION,
                            s.worldState(), playerState), coordinator.content(), factionId, displayName);
            candidate = GeneratedCampaignPlayerWorldTransition.compose(baseline, updated);
            restore(candidate).captureState();
        } catch (IllegalStateException | IllegalArgumentException exception) { candidate = null; }
        return new PlayerFactionFoundationPreview(this, baseline, candidate);
    }

    /** Single-use confirmation of the existing world-defined faction transition. */
    public static final class PlayerFactionFoundationPreview {
        private final Stage228CampaignAuthority owner;
        private final com.spacesim.persistence.Stage228GeneratedCampaignPersistentState baseline, candidate;
        private boolean used;
        private PlayerFactionFoundationPreview(Stage228CampaignAuthority owner,
                com.spacesim.persistence.Stage228GeneratedCampaignPersistentState baseline,
                com.spacesim.persistence.Stage228GeneratedCampaignPersistentState candidate) {
            this.owner = owner; this.baseline = baseline; this.candidate = candidate;
        }
        /** @return whether existing foundation and composed checkpoint validation accepted */
        public boolean allowed() { return candidate != null && !used; }
    }

    /**
     * Adopts a prevalidated ordinary world transition as one replacement campaign binding.
     * Existing simulation/cargo/craft authorities are restored exactly, with no elapsed time.
     * @param preview current single-use confirmation
     * @return replacement binding containing the actually founded faction and existing player
     */
    public Stage228CampaignAuthority submitPlayerFactionFoundation(PlayerFactionFoundationPreview preview) {
        var p = Objects.requireNonNull(preview);
        if (p.owner != this || !p.allowed() || !p.baseline.equals(captureState()))
            throw new IllegalStateException("Faction foundation is stale or unauthorized");
        var adopted = restore(p.candidate);
        adopted.recordPersonalCommit(com.spacesim.player.PlayerJournalState.Kind.GOVERNMENT_COMMAND,
                "FOUND_FACTION", adopted.playerState.factionContentId(), "", 0, 0, null);
        p.used = true;
        return adopted;
    }

    /**
     * Previews shared policy authoring/application for the actual player faction.
     * @param command ordinary immutable policy intent
     * @return pure exact-state confirmation
     */
    public PlayerFactionCommandPreview previewPlayerFactionPolicy(com.spacesim.world.FactionPolicyCommand command) {
        return previewFactionCommand("POLICY", service -> service.submitPolicy(Objects.requireNonNull(command)));
    }

    /**
     * Previews the ordinary treaty lifecycle without impersonating a foreign actor.
     * @param command shared treaty command
     * @return pure exact-state confirmation
     */
    public PlayerFactionCommandPreview previewPlayerFactionTreaty(com.spacesim.world.DiplomaticTreatyCommand command) {
        return previewFactionCommand("TREATY", service -> service.submitTreaty(Objects.requireNonNull(command)));
    }

    /**
     * Previews shared market-access embargo changes for the actual player faction.
     * @param command shared embargo command
     * @return pure exact-state confirmation
     */
    public PlayerFactionCommandPreview previewPlayerFactionEmbargo(com.spacesim.world.DiplomaticEmbargoCommand command) {
        return previewFactionCommand("EMBARGO", service -> service.submitEmbargo(Objects.requireNonNull(command)));
    }

    /**
     * Previews existing territorial rules at a personally discovered system.
     * @param action CLAIM, WITHDRAW, RELINQUISH, RECOGNIZE_CLAIM, RECOGNIZE_CONTROL, GRANT_RIGHT or REVOKE_RIGHT
     * @param system discovered target system
     * @param targetFaction target of recognition/concession, otherwise ignored
     * @param expiresTick ordinary concession expiry, otherwise ignored
     * @return pure exact-state confirmation; claims never grant immediate control
     */
    public PlayerFactionCommandPreview previewPlayerFactionTerritory(String action,
            com.spacesim.world.StarSystemId system, String targetFaction, long expiresTick) {
        var preview = previewFactionCommand("TERRITORY_" + action, service -> {
            if (playerState == null || !playerState.discoveredSystemIds().contains(system))
                throw new IllegalStateException("Territorial target has not been personally discovered");
            switch (action) {
                case "CLAIM" -> service.declareClaim(system);
                case "WITHDRAW" -> { if (!service.withdrawClaim(system)) throw new IllegalStateException("No withdrawable own claim"); }
                case "RELINQUISH" -> { if (!service.relinquishControl(system)) throw new IllegalStateException("No own control"); }
                case "RECOGNIZE_CLAIM" -> service.recognizeClaim(targetFaction, system);
                case "RECOGNIZE_CONTROL" -> service.recognizeControl(targetFaction, system);
                case "GRANT_RIGHT" -> service.grantConstructionRight(targetFaction, system, expiresTick);
                case "REVOKE_RIGHT" -> { if (!service.revokeConstructionRight(targetFaction, system)) throw new IllegalStateException("No own concession"); }
                default -> throw new IllegalArgumentException("Unknown territorial command");
            }
        });
        return new PlayerFactionCommandPreview(this, preview.baseline, preview.candidate, preview.action,
                Long.toString(system.value()), targetFaction, null);
    }

    /**
     * Previews explicit affiliation of already-owned assets through the existing shared service.
     * Only idle personally owned freight can change its legal mirror; no bootstrap slot is reassigned.
     * @return pure exact-state confirmation preserving IDs, resources and bootstrap provenance
     */
    public PlayerFactionCommandPreview previewPlayerAssetAffiliation() {
        return previewFactionCommand("AFFILIATE_ASSETS", (service, isolated) -> {
            var runtime = isolated.coordinator.runtime();
            var player = isolated.playerState;
            for (var id : player.ownedFleetIds()) runtime.freight().findFreighter(id).ifPresent(f -> {
                if (f.phase() != com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase.IDLE)
                    throw new IllegalStateException("Only personally controlled idle freight can affiliate");
            });
            service.affiliateOwnedAssets();
            for (var id : player.ownedFleetIds()) if (runtime.freight().findFreighter(id).isPresent())
                runtime.freight().synchronizeLegalAffiliation(id, player.factionContentId());
        });
    }

    /**
     * Previews a durable order for an existing inactive personal fleet using ordinary player rules.
     * @param order shared HOLD, MOVE, FOLLOW, ESCORT or PATROL intent
     * @return pure single-use exact-checkpoint confirmation
     */
    public PlayerFactionCommandPreview previewPlayerFleetOrder(com.spacesim.player.PlayerFleetOrderState order) {
        return previewPersonalCommand("FLEET_" + order.type().name(), order.fleetId(),
                order.targetSystemId() == null ? "" : Long.toString(order.targetSystemId().value()),
                order.targetFleetId() == null ? "" : Long.toString(order.targetFleetId().value()), isolated -> {
            var player = isolated.playerState;
            if (order.fleetId().equals(player.activeFleetId())) throw new IllegalStateException("Direct control takes priority");
            if (!java.util.Set.of(com.spacesim.player.FleetOrderType.HOLD, com.spacesim.player.FleetOrderType.MOVE,
                    com.spacesim.player.FleetOrderType.FOLLOW, com.spacesim.player.FleetOrderType.ESCORT,
                    com.spacesim.player.FleetOrderType.PATROL).contains(order.type()))
                throw new IllegalArgumentException("Unsupported exact fleet order");
            if (order.type() == com.spacesim.player.FleetOrderType.MOVE
                    && (order.targetX() != com.spacesim.world.LocalSystemCoordinates.ARRIVAL_X
                    || order.targetY() != com.spacesim.world.LocalSystemCoordinates.ARRIVAL_Y))
                throw new IllegalArgumentException("Generated MOVE selects system arrival, not legacy float coordinates");
            var freight = isolated.coordinator.runtime().freight().findFreighter(order.fleetId()).orElseThrow(() -> new IllegalStateException("No existing personal freight"));
            if (freight.phase() != com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase.IDLE)
                throw new IllegalStateException("Assigned freight remains under its ordinary transport owner");
            if (order.targetFleetId() != null && !player.ownedFleetIds().contains(order.targetFleetId()))
                throw new IllegalStateException("Target must be personally owned");
            var adapter = com.spacesim.player.PlayerRuntime.attachToCampaign(
                    isolated.coordinator.runtime().world(), isolated.coordinator.content(), player);
            if (!new com.spacesim.player.PlayerFleetOrderService(adapter).issue(order))
                throw new IllegalStateException("Unknown target or unowned fleet");
            if (order.type() == com.spacesim.player.FleetOrderType.MOVE || order.type() == com.spacesim.player.FleetOrderType.PATROL) {
                var world = isolated.coordinator.runtime().world();
                var placement = world.findFleet(order.fleetId()).orElseThrow();
                if (placement.locationKind() == com.spacesim.world.FleetLocationKind.IN_SYSTEM
                        && world.findFleetJump(order.fleetId()).isEmpty()) {
                    var destination = order.type() == com.spacesim.player.FleetOrderType.MOVE ? order.targetSystemId()
                            : order.patrolSystemIds().get(order.patrolSystemIds().indexOf(placement.systemId()) == 0 ? 1 : 0);
                    var route = new com.spacesim.player.PlayerFleetRoutePlanner(adapter)
                            .plan(order.fleetId(), placement.systemId(), destination).orElseThrow(
                                    () -> new IllegalStateException("No personally known feasible route"));
                    if (route.path().size() > 1) {
                        var fuel = world.planFleetRouteFuel(order.fleetId(), route.path());
                        if (!fuel.supported() || !fuel.feasible()) throw new IllegalStateException("Onboard fuel cannot complete the route");
                    }
                }
            }
            isolated.playerState = adapter.player();
        });
    }

    private PlayerFactionCommandPreview previewPersonalCommand(String action, FleetId fleet, String target, String subject,
            java.util.function.Consumer<Stage228CampaignAuthority> command) {
        var baseline = captureState();
        com.spacesim.persistence.Stage228GeneratedCampaignPersistentState candidate = null;
        try {
            var isolated = restore(baseline);
            if (isolated.playerState == null) throw new IllegalStateException("No personal authority");
            command.accept(isolated);
            candidate = isolated.captureState();
            restore(candidate).captureState();
        } catch (IllegalStateException | IllegalArgumentException exception) { candidate = null; }
        return new PlayerFactionCommandPreview(this, baseline, candidate, action, target, subject, fleet);
    }

    private PlayerFactionCommandPreview previewFactionCommand(
            String action, java.util.function.Consumer<com.spacesim.player.PlayerFactionManagementService> command) {
        return previewFactionCommand(action, (service, isolated) -> command.accept(service));
    }

    private PlayerFactionCommandPreview previewFactionCommand(
            String action, java.util.function.BiConsumer<com.spacesim.player.PlayerFactionManagementService, Stage228CampaignAuthority> command) {
        var baseline = captureState();
        com.spacesim.persistence.Stage228GeneratedCampaignPersistentState candidate = null;
        try {
            var isolated = restore(baseline);
            if (isolated.playerState == null || !isolated.playerState.affiliated())
                throw new IllegalStateException("No personal faction authority");
            var adapter = com.spacesim.player.PlayerRuntime.attachToCampaign(
                    isolated.coordinator.runtime().world(), isolated.coordinator.content(), isolated.playerState);
            command.accept(new com.spacesim.player.PlayerFactionManagementService(adapter), isolated);
            isolated.playerState = adapter.player();
            candidate = isolated.captureState();
            restore(candidate).captureState();
        } catch (IllegalStateException | IllegalArgumentException exception) { candidate = null; }
        return new PlayerFactionCommandPreview(this, baseline, candidate, action);
    }

    /** Exact-state single-use confirmation of shared faction rules, never a faction impersonation token. */
    public static final class PlayerFactionCommandPreview {
        private final String action;
        private final String target, subject;
        private final FleetId fleet;
        private final Stage228CampaignAuthority owner;
        private final com.spacesim.persistence.Stage228GeneratedCampaignPersistentState baseline, candidate;
        private boolean used;
        private PlayerFactionCommandPreview(Stage228CampaignAuthority owner,
                com.spacesim.persistence.Stage228GeneratedCampaignPersistentState baseline,
                com.spacesim.persistence.Stage228GeneratedCampaignPersistentState candidate, String action) {
            this(owner, baseline, candidate, action, "", "", null);
        }
        private PlayerFactionCommandPreview(Stage228CampaignAuthority owner,
                com.spacesim.persistence.Stage228GeneratedCampaignPersistentState baseline,
                com.spacesim.persistence.Stage228GeneratedCampaignPersistentState candidate, String action,
                String target, String subject, FleetId fleet) {
            this.owner = owner; this.baseline = baseline; this.candidate = candidate;
            this.action = action;
            this.target = target; this.subject = subject; this.fleet = fleet;
        }
        /** @return whether the shared command and composed checkpoint validation accepted */
        public boolean allowed() { return candidate != null && !used; }
    }

    /**
     * Adopts the validated policy/diplomatic/territorial transition without advancing any clock.
     * @param preview current single-use confirmation owned by this campaign
     * @return replacement binding preserving all adjacent physical and campaign owners
     */
    public Stage228CampaignAuthority submitPlayerFactionCommand(PlayerFactionCommandPreview preview) {
        var p = Objects.requireNonNull(preview);
        if (p.owner != this || !p.allowed() || !p.baseline.equals(captureState()))
            throw new IllegalStateException("Faction command is stale or unauthorized");
        var adopted = restore(p.candidate);
        adopted.recordPersonalCommit(com.spacesim.player.PlayerJournalState.Kind.GOVERNMENT_COMMAND,
                p.action, p.target.isEmpty() ? adopted.playerState.factionContentId() : p.target, p.subject, 0,
                adopted.playerState.walletMilliCredits() - playerState.walletMilliCredits(), p.fleet);
        p.used = true; return adopted;
    }

    private void initializePilotMarkets() {
        var runtime = coordinator.runtime();
        for (var endpoint : runtime.infrastructure().endpoints()) {
            var system = endpoint.systemId();
            var session = runtime.world().findSession(system).orElseThrow();
            var transform = new com.spacesim.components.TransformComponent();
            transform.position.set((float) endpoint.position().offsetXM(), (float) endpoint.position().offsetYM());
            var entity = new com.badlogic.ashley.core.Entity()
                    .add(new com.spacesim.components.IdentityComponent(PILOT_MARKET_V2_IDENTITY_PREFIX + endpoint.stationId(),
                            com.spacesim.components.IdentityComponent.Kind.STATION))
                    .add(transform).add(new com.spacesim.components.MarketComponent())
                    .add(new WalletComponent());
            // A station's economic owner can differ from the faction controlling its territory.
            // Bind newly commissioned station wallets to their actual existing asset owner.
            var owner = runtime.industry().industrial().stations().stream()
                    .filter(s -> s.stationId().equals(endpoint.stationId())).map(s -> s.stableFactionId()).findFirst()
                    .or(() -> runtime.world().controllingFaction(system));
            owner.ifPresent(f -> entity.add(new com.spacesim.components.FactionComponent(
                    runtime.world().findFactionRuntimeId(f).orElseThrow())));
            runtime.world().createEntity(system, entity);
            if (!entity.getComponent(WalletComponent.class).creditFromSource(PILOT_MARKET_INITIAL_LIQUIDITY))
                throw new IllegalStateException("Opening market liquidity cannot be credited");
            session.getLedger().recordMoneySource(PILOT_MARKET_V2_IDENTITY_PREFIX + endpoint.stationId(),
                    PILOT_MARKET_INITIAL_LIQUIDITY, "new-game-market-working-capital.v2");
        }
    }

    /**
     * Returns an existing station entity representing the endpoint's ordinary wallet, never its cargo.
     * @param stationId exact physical endpoint
     * @return durable station reference, or empty where no market has been commissioned
     */
    public Optional<com.spacesim.player.DiscoveredObjectRef> pilotMarketReference(String stationId) {
        var endpoint = coordinator.runtime().infrastructure().endpoint(stationId);
        var session = coordinator.runtime().world().findSession(endpoint.systemId()).orElseThrow();
        for (var entity : session.getEngine().getEntities()) {
            var name = entity.getComponent(com.spacesim.components.IdentityComponent.class);
            if (name != null && (name.name.equals(PILOT_MARKET_IDENTITY_PREFIX + stationId)
                    || name.name.equals(PILOT_MARKET_V2_IDENTITY_PREFIX + stationId))) {
                var id = entity.getComponent(com.spacesim.components.EntityIdComponent.class);
                if (id != null) return Optional.of(new com.spacesim.player.DiscoveredObjectRef(endpoint.systemId(), id.id));
            }
        }
        return Optional.empty();
    }

    /**
     * Existing ordinary-state preview used for docking and physical cargo trades.
     * @param action docking, trade, flight, asset, finance, mining, supply, report or manufacturing verb
     * @param stationId endpoint, destination for JUMP, fleet for PURCHASE/SWITCH, or milli-credits for CAPITALIZE/WITHDRAW; ignored by UNDOCK
     * @param commodityId physical commodity, manufacturing product, or cancellation order identity
     * @param kilograms whole kilograms, or whole output units for START_MANUFACTURING
     * @return pure exact-state confirmation with no live changes
     */
    public PlayerPhysicalPreview previewPilotAction(String action, String stationId, String commodityId, int kilograms) {
        var baseline = captureState();
        var candidate = restore(baseline);
        boolean allowed;
        long walletChange = 0;
        try { candidate.executePilotAction(action, stationId, commodityId, kilograms); candidate.captureState(); allowed = true;
            walletChange = candidate.playerState.walletMilliCredits() - playerState.walletMilliCredits(); }
        catch (IllegalStateException | IllegalArgumentException | ArithmeticException | java.util.NoSuchElementException exception) { allowed = false; }
        return new PlayerPhysicalPreview(this, baseline, action, stationId, commodityId, kilograms, allowed, walletChange);
    }

    /** Exact-state confirmation for one physical player action. */
    public static final class PlayerPhysicalPreview {
        private boolean used;
        private final Stage228CampaignAuthority owner;
        private final Stage228GeneratedCampaignPersistentState baseline;
        private final String action, station, commodity;
        private final int kilograms;
        private final boolean allowed;
        private final long walletChange;
        private PlayerPhysicalPreview(Stage228CampaignAuthority owner, Stage228GeneratedCampaignPersistentState baseline,
                String action, String station, String commodity, int kilograms, boolean allowed, long walletChange) {
            this.owner = owner; this.baseline = baseline; this.action = action; this.station = station;
            this.commodity = commodity; this.kilograms = kilograms; this.allowed = allowed; this.walletChange = walletChange;
        }
        /** @return whether the shared domain path accepts the command */
        public boolean allowed() { return allowed && !used; }
        /** @return exact personal wallet delta including ordinary customs */
        public long walletChangeMilliCredits() { return walletChange; }
    }

    /**
     * Submits the same prevalidated physical action against the unchanged live checkpoint.
     * @param preview exact current token
     * @return updated existing durable player state
     */
    public PlayerState submitPilotAction(PlayerPhysicalPreview preview) {
        var p = Objects.requireNonNull(preview);
        if (p.owner != this || !p.allowed() || !p.baseline.equals(captureState()))
            throw new IllegalStateException("Physical action is stale or unauthorized");
        long beforeWallet = playerState.walletMilliCredits();
        var beforeFleet = playerState.activeFleetId();
        long beforeJournalSequence = playerJournal.nextSequence();
        executePilotAction(p.action, p.station, p.commodity, p.kilograms);
        p.used = true;
        if (!p.action.equals("ACKNOWLEDGE_JOURNAL")) recordPersonalCommit(
                com.spacesim.player.PlayerJournalState.Kind.COMMAND, p.action, p.station, p.commodity,
                switch (p.action) {
                    case "BUY", "SELL", "LOAD_CONSUMABLE", "START_MANUFACTURING", "LOAD_PRODUCT", "UNLOAD_PRODUCT" -> p.kilograms;
                    case "PURCHASE", "PURCHASE_STATION" -> 1;
                    default -> 0;
                }, p.action.equals("REPORT_DISCOVERY") ? 0 : playerState.walletMilliCredits() - beforeWallet
                        - (p.action.equals("SELL") ? playerJournal.entries().stream().filter(e -> e.sequence() >= beforeJournalSequence
                                && e.kind() == com.spacesim.player.PlayerJournalState.Kind.MISSION_CHANGED)
                                .mapToLong(com.spacesim.player.PlayerJournalState.Entry::walletDeltaMilliCredits).sum() : 0),
                p.action.equals("PURCHASE") || p.action.equals("SWITCH") ? new FleetId(Long.parseLong(p.station))
                        : p.action.equals("CAPITALIZE") || p.action.equals("WITHDRAW") ? null : beforeFleet);
        return playerState;
    }

    /**
     * Plans one discovered personal route and previews only its first ordinary departure.
     * The remaining hops are diagnostics, not delegated orders or automatic refuelling.
     * @param destination personally discovered destination
     * @return immutable route and exact first-hop token, or empty without a local route
     */
    public Optional<PilotRoutePreview> previewPilotRoute(com.spacesim.world.StarSystemId destination) {
        Objects.requireNonNull(destination, "destination");
        if (playerState == null || playerState.activeFleetId() == null) return Optional.empty();
        var world = coordinator.runtime().world();
        var placement = world.findFleet(playerState.activeFleetId()).orElse(null);
        if (placement == null || placement.locationKind() != com.spacesim.world.FleetLocationKind.IN_SYSTEM
                || world.findFleetJump(placement.fleetId()).isPresent()) return Optional.empty();
        var route = new com.spacesim.player.PlayerFleetRoutePlanner(world, coordinator.content(), playerState)
                .plan(placement.fleetId(), placement.systemId(), destination);
        if (route.isEmpty() || !route.orElseThrow().travels()) return Optional.empty();
        var checked = route.orElseThrow();
        return Optional.of(new PilotRoutePreview(checked,
                previewPilotAction("JUMP", Long.toString(checked.path().get(1).value()), "", 1)));
    }

    /**
     * Read-only route diagnostics paired with an authority-bound first-hop preview.
     * @param route ordinary planner result; no saved travel intent
     * @param departure exact first-hop validation token
     */
    public record PilotRoutePreview(com.spacesim.player.PlayerRouteRiskView route, PlayerPhysicalPreview departure) {
        /**
         * Requires both shared route diagnostics and its ordinary departure token.
         * @param route read-only path
         * @param departure first-hop token
         */
        public PilotRoutePreview { Objects.requireNonNull(route); Objects.requireNonNull(departure); }
    }

    private void replacePilotFinancialDocking(long wallet, com.spacesim.player.DiscoveredObjectRef dock,
            List<com.spacesim.player.DiscoveredObjectRef> discovered) {
        var p = playerState;
        playerState = new PlayerState(wallet, p.factionContentId(), p.reputations(), p.ownedFleetIds(), p.activeFleetId(),
                p.discoveredSystemIds(), discovered, p.homeSystemId(), dock, p.fleetOrders(), p.threatIntel(),
                p.ownedConstructionProjectIds(), p.ownedStations());
    }

    private void executePilotAction(String action, String stationId, String commodity, int kg) {
        if (action.equals("ACKNOWLEDGE_JOURNAL")) {
            if (playerState == null) throw new IllegalStateException("No initialized personal authority");
            playerJournal = playerJournal.acknowledge(Long.parseLong(stationId));
            return;
        }
        if ("CAPITALIZE".equals(action) || "WITHDRAW".equals(action)) {
            if (playerState == null) throw new IllegalStateException("No initialized personal authority");
            var adapter = com.spacesim.player.PlayerRuntime.attachToCampaign(coordinator.runtime().world(), coordinator.content(), playerState);
            var finance = new com.spacesim.player.PlayerFactionManagementService(adapter);
            long amount = Long.parseLong(stationId);
            boolean committed = "CAPITALIZE".equals(action) ? finance.capitalizeTreasury(amount) : finance.transferTreasuryToPersonal(amount);
            if (!committed) throw new IllegalStateException("Faction authority, funds or wallet capacity rejected transfer");
            playerState = adapter.player(); return;
        }
        if (playerState == null || playerState.activeFleetId() == null) throw new IllegalStateException("No active personal ship");
        var runtime = coordinator.runtime();
        var world = runtime.world();
        if ("CANCEL_PRODUCT_TRANSFER".equals(action)) {
            var job = productTransfers.capture().orders().stream().filter(o -> o.orderId().equals(commodity)).findFirst().orElseThrow();
            if (!ownsProductionStation(stationId) || !job.stationId().equals(stationId))
                throw new IllegalStateException("Wrong product handling owner");
            productTransfers.cancel(commodity, this::productStorage); return;
        }
        if ("LOAD_PRODUCT".equals(action) || "UNLOAD_PRODUCT".equals(action)) {
            if (!productTransfers.capture().orders().isEmpty() || !moduleTransfers.capture().orders().isEmpty()
                    || !refitQueue.capture().orders().isEmpty() || !repairQueue.capture().orders().isEmpty()
                    || !runtime.freight().personalMiningOrders().isEmpty()) throw new IllegalStateException("Finish current physical work first");
            var context = productTransferContext(stationId, "LOAD_PRODUCT".equals(action));
            if (context == null || kg <= 0) throw new IllegalStateException("Product handling requires actual own stationary docked ship and station");
            if (handlingUsedThisTick(stationId)) throw new IllegalStateException("This tick's endpoint handling is already spent");
            var fleet = runtime.freight().findFreighter(playerState.activeFleetId()).orElseThrow();
            var order = new com.spacesim.economy.FinishedProductTransferWorkQueue.Order(
                    "player-product-transfer:" + fleet.fleetId().value() + ':' + world.getAuthoritativeWorldTick(), fleet.fleetId(),
                    context.source().stationId(), context.destination().stationId(), commodity, kg, world.getAuthoritativeWorldTick(), 0d);
            productTransfers.start(order, context, this::productStorage, world.getAuthoritativeWorldTick()); return;
        }
        if (!productTransfers.capture().orders().isEmpty() && java.util.Set.of("START_REFIT", "START_REFIT_USED", "START_REPAIR",
                "UNDOCK", "JUMP", "SWITCH", "START_MINING", "BUY", "SELL", "LOAD_CONSUMABLE", "LOAD_MODULE", "UNLOAD_MODULE").contains(action))
            throw new IllegalStateException("Finish or cancel product handling first");
        if ("CANCEL_MODULE_TRANSFER".equals(action)) {
            var job = moduleTransfers.capture().orders().stream().filter(o -> o.orderId().equals(commodity)).findFirst().orElseThrow();
            if (!ownsStoredModule(job.source().custodyId()) || !stationId.equals(transferStation(job))) throw new IllegalStateException("Wrong handling owner");
            moduleTransfers.cancel(commodity); return;
        }
        if ("LOAD_MODULE".equals(action) || "UNLOAD_MODULE".equals(action)) {
            if (!moduleTransfers.capture().orders().isEmpty() || !refitQueue.capture().orders().isEmpty() || !repairQueue.capture().orders().isEmpty()
                    || !runtime.freight().personalMiningOrders().isEmpty()) throw new IllegalStateException("Finish current physical work first");
            var context = moduleTransferContext(stationId, "LOAD_MODULE".equals(action));
            if (context == null) throw new IllegalStateException("Handling requires an owned docked stationary ship and station");
            var fleet = runtime.freight().findFreighter(playerState.activeFleetId()).orElseThrow();
            var row = moduleCustody.modules().stream().filter(m -> m.custodyId().equals(commodity)).findFirst().orElseThrow();
            if (!ownsStoredModule(commodity)) throw new IllegalStateException("Equipment belongs to another actor");
            double mass = com.spacesim.content.Stage22CivilianMiningProductionPath.loadProducts().findProduct(row.condition().assignment().moduleId()).unitMassKg();
            if ("LOAD_MODULE".equals(action) && fleet.cargoMassKg() + mass > fleet.cargoCapacityKg()) throw new IllegalStateException("Total ship hold is full");
            if (handlingUsedThisTick(stationId)) throw new IllegalStateException("This tick's endpoint handling is already spent");
            if (row.ownerActorId() == null) moduleCustody = moduleCustody.declareOwner(commodity, Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
            moduleTransfers.start("player-module-transfer:" + fleet.fleetId().value() + ':' + world.getAuthoritativeWorldTick(),
                    moduleCustody, commodity, context, reservedRefitModules(), world.getAuthoritativeWorldTick()); return;
        }
        if (!moduleTransfers.capture().orders().isEmpty() && java.util.Set.of("START_REFIT", "START_REFIT_USED", "START_REPAIR",
                "UNDOCK", "JUMP", "SWITCH", "START_MINING", "BUY", "SELL", "LOAD_CONSUMABLE").contains(action))
            throw new IllegalStateException("Finish or cancel equipment handling first");
        if ("CANCEL_REFIT".equals(action)) {
            var job = refitQueue.capture().orders().stream().filter(o -> o.orderId().equals(commodity)
                    && o.stationId().equals(stationId) && o.fleetId() == playerState.activeFleetId().value()).findFirst().orElseThrow();
            if (!ownsProductionStation(stationId) && (!civilianRepairOperator(stationId, null)
                    || job.servicePayment() == null || !job.servicePayment().sellerFactionId().equals(runtime.industry().industrial().station(stationId).stableFactionId())
                    || personalYards(stationId, job.fleetId(), job.assetId(), true) == null))
                throw new IllegalStateException("Refit cancellation requires its actual authorized service berth");
            long refund = job.servicePayment() == null ? 0 : job.servicePayment().reservedMilliCredits();
            long wallet = Math.addExact(playerState.walletMilliCredits(), refund);
            refitQueue.cancel(commodity, runtime.infrastructure().endpoint(stationId).storage());
            if (refund > 0) {
                replacePilotFinancialDocking(wallet, playerState.dockedAt(), playerState.discoveredObjects());
                world.findSession(fleetSystemForPlayer()).orElseThrow().getLedger().recordMoneyTransfer(
                        "PLAYER_REFIT_ESCROW:" + job.orderId(), "PLAYER", refund, "paid-refit-cancel.v1");
            }
            return;
        }
        if ("START_REFIT".equals(action) || "START_REFIT_USED".equals(action)) {
            var fleet = world.findFleet(playerState.activeFleetId()).orElseThrow();
            if (repairQueue.capture().orders().stream().anyMatch(o -> o.fleetId() == fleet.id().value()))
                throw new IllegalStateException("Finish or cancel repair before refitting");
            var used = "START_REFIT_USED".equals(action) ? moduleCustody.modules().stream()
                    .filter(row -> row.custodyId().equals(commodity) && row.stationId().equals(stationId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown used module at this station")) : null;
            var target = personalRefitTarget(used == null ? commodity : storedModuleTargetId(used));
            if (used != null && !ownsStoredModule(used.custodyId())) throw new IllegalStateException("Used module is not personal property");
            var context = refitContext(stationId, null, target);
            if (context == null || !refitCapacityAvailable(fleet.id(), context.ship(), target))
                throw new IllegalStateException("Refit requires an owned docked compatible ship, installed yard and sufficient target cargo space");
            var usedInputs = used == null ? java.util.Map.<String, com.spacesim.economy.ShipyardModuleCustodyState.StoredModule>of()
                    : java.util.Map.of("mission_primary", used);
            com.spacesim.economy.ShipyardRefitServicePayment payment = null;
            if (!ownsProductionStation(stationId)) {
                long fee = com.spacesim.economy.ShipyardRefitWorkQueue.serviceQuote(fleet.localEntityId(), context.ship(), target, context.yard(), usedInputs);
                if (playerState.walletMilliCredits() < fee) throw new IllegalStateException("Insufficient money for foreign refit");
                payment = new com.spacesim.economy.ShipyardRefitServicePayment(runtime.industry().industrial().station(stationId).stableFactionId(), fee);
            }
            String refitId = "player-refit:" + fleet.id().value() + ':' + world.getAuthoritativeWorldTick();
            refitQueue.start(refitId,
                    fleet.id().value(), fleet.localEntityId(), context.ship(), target,
                    runtime.infrastructure().endpoint(stationId).storage(), context.yard(), world.getAuthoritativeWorldTick(),
                    usedInputs, moduleCustody, payment);
            if (payment != null) {
                replacePilotFinancialDocking(playerState.walletMilliCredits() - payment.reservedMilliCredits(), playerState.dockedAt(), playerState.discoveredObjects());
                world.findSession(fleet.systemId()).orElseThrow().getLedger().recordMoneyTransfer("PLAYER", "PLAYER_REFIT_ESCROW:" + refitId,
                        payment.reservedMilliCredits(), "paid-refit-reserve.v1");
            }
            return;
        }
        if ("CANCEL_REPAIR".equals(action)) {
            var order = repairQueue.capture().orders().stream().filter(o -> o.orderId().equals(commodity)
                    && o.stationId().equals(stationId) && o.fleetId() == playerState.activeFleetId().value()).findFirst().orElseThrow();
            if (!ownsProductionStation(stationId) && (!civilianRepairOperator(stationId, order)
                    || personalYards(stationId, order.fleetId(), order.assetId(), true) == null))
                throw new IllegalStateException("Repair cancellation requires its authorized physical service berth");
            long refund = order.servicePayment() == null ? 0 : order.servicePayment().reservedMilliCredits();
            long wallet = Math.addExact(playerState.walletMilliCredits(), refund);
            repairQueue.cancel(commodity, runtime.infrastructure().endpoint(stationId).storage());
            if (refund > 0) {
                replacePilotFinancialDocking(wallet, playerState.dockedAt(), playerState.discoveredObjects());
                world.findSession(fleetSystemForPlayer()).orElseThrow().getLedger().recordMoneyTransfer(
                        "PLAYER_REPAIR_ESCROW:" + order.orderId(), "PLAYER", refund, "paid-repair-cancel.v1");
            }
            return;
        }
        if ("START_REPAIR".equals(action)) {
            if (refitQueue.capture().orders().stream().anyMatch(o -> o.fleetId() == playerState.activeFleetId().value()))
                throw new IllegalStateException("Finish or cancel refit before repair");
            var context = repairContext(stationId, null);
            if (context == null) throw new IllegalStateException("Repair requires the actual own docked ship and compatible installed yard");
            var fleet = world.findFleet(playerState.activeFleetId()).orElseThrow();
            com.spacesim.economy.ShipyardRepairServicePayment payment = null;
            if (!ownsProductionStation(stationId)) {
                long fee = repairQueue.serviceQuote(fleet.localEntityId(), context.ship(), context.yard());
                if (playerState.walletMilliCredits() < fee) throw new IllegalStateException("Insufficient personal money for paid repair");
                payment = new com.spacesim.economy.ShipyardRepairServicePayment(
                        runtime.industry().industrial().station(stationId).stableFactionId(), fee);
            }
            String repairId = "player-repair:" + fleet.id().value() + ':' + world.getAuthoritativeWorldTick();
            repairQueue.start(repairId,
                    fleet.id().value(), fleet.localEntityId(), context.ship(), runtime.infrastructure().endpoint(stationId).storage(),
                    context.yard(), world.getAuthoritativeWorldTick(), payment);
            if (payment != null) {
                replacePilotFinancialDocking(playerState.walletMilliCredits() - payment.reservedMilliCredits(),
                        playerState.dockedAt(), playerState.discoveredObjects());
                world.findSession(fleet.systemId()).orElseThrow().getLedger().recordMoneyTransfer("PLAYER",
                        "PLAYER_REPAIR_ESCROW:" + repairId, payment.reservedMilliCredits(), "paid-repair-reserve.v1");
            }
            return;
        }
        if ((repairQueue.capture().orders().stream().anyMatch(o -> o.fleetId() == playerState.activeFleetId().value())
                || refitQueue.capture().orders().stream().anyMatch(o -> o.fleetId() == playerState.activeFleetId().value()))
                && java.util.Set.of("UNDOCK", "JUMP", "SWITCH", "START_MINING").contains(action))
            throw new IllegalStateException("Finish or cancel physical yard work before departure or handover");
        if (java.util.Set.of("START_MANUFACTURING", "CANCEL_MANUFACTURING", "START_FACILITY_CONSTRUCTION",
                "CANCEL_FACILITY_CONSTRUCTION", "ALLOCATE_FACILITY_RESOURCES", "START_YARD_CONSTRUCTION",
                "CANCEL_YARD_CONSTRUCTION", "ALLOCATE_YARD_RESOURCES").contains(action)) {
            if (!ownsProductionStation(stationId)) throw new IllegalStateException("Production requires the personally owned station");
            var ref = pilotMarketReference(stationId).orElseThrow();
            if (!ref.equals(playerState.dockedAt())) throw new IllegalStateException("Dock at your production station first");
            var placed = world.findFleet(playerState.activeFleetId()).orElseThrow();
            var endpoint = runtime.infrastructure().endpoint(stationId);
            if (!playerState.ownedFleetIds().contains(placed.fleetId())
                    || placed.locationKind() != com.spacesim.world.FleetLocationKind.IN_SYSTEM
                    || !placed.systemId().equals(endpoint.systemId()) || world.findFleetJump(placed.fleetId()).isPresent())
                throw new IllegalStateException("Production command requires the owned local docked ship");
            var physical = runtime.arrival().materialization(placed.systemId()).physicalState(placed.localEntityId()).orElseThrow();
            if (physical.position().distanceTo(endpoint.position()) > 1000d
                    || Math.hypot(physical.velocityXMps(), physical.velocityYMps()) > 1d)
                throw new IllegalStateException("Production command requires actual berth geometry");
            var queue = runtime.manufacturingQueue();
            var storage = runtime.infrastructure().endpoint(stationId).storage();
            if ("ALLOCATE_FACILITY_RESOURCES".equals(action)) runtime.allocateFacilityResources(stationId, commodity);
            else if ("ALLOCATE_YARD_RESOURCES".equals(action)) runtime.allocateYardResources(stationId, commodity);
            else if ("CANCEL_YARD_CONSTRUCTION".equals(action)) yardConstruction.cancel(commodity, storage);
            else if ("START_YARD_CONSTRUCTION".equals(action)) {
                if (yardConstructionCapability(stationId, commodity) == null)
                    throw new IllegalStateException("No active installed line can construct this yard");
                String id = "player-yard-construction:" + stationId + ':' + commodity + ':' + world.getAuthoritativeWorldTick();
                if (runtime.industry().industrial().stations().stream().flatMap(s -> s.yards().stream())
                        .anyMatch(y -> y.yardInstanceId().equals(id + ":installed")))
                    throw new IllegalStateException("Yard installation identity already exists");
                yardConstruction.plan(id, id + ":installed", commodity,
                        runtime.industry().industrial().station(stationId).stationNode().locationTag(), storage,
                        world.getAuthoritativeWorldTick());
            }
            else if ("CANCEL_FACILITY_CONSTRUCTION".equals(action)) runtime.constructionQueue().cancel(commodity, storage);
            else if ("START_FACILITY_CONSTRUCTION".equals(action)) {
                if (constructionCapability(stationId, commodity) == null)
                    throw new IllegalStateException("No active installed line can construct this facility");
                String id = com.spacesim.economy.Stage18FacilityConstructionWorkQueue.PREFIX + stationId + ':'
                        + commodity + ':' + world.getAuthoritativeWorldTick();
                runtime.constructionQueue().start(id, id + ":installed", commodity, "location.orbital_station", storage,
                        world.getAuthoritativeWorldTick());
            }
            else if ("CANCEL_MANUFACTURING".equals(action)) queue.cancel(commodity, storage);
            else {
                var capability = manufacturingCapability(stationId, commodity);
                if (capability == null) throw new IllegalStateException("No installed compatible manufacturing line");
                String id = com.spacesim.economy.Stage18ManufacturingWorkQueue.PREFIX + stationId + ':' + commodity;
                queue.start(id, commodity, kg, storage, capability, world.getAuthoritativeWorldTick());
            }
            return;
        }
        var fleet = world.findFleet(playerState.activeFleetId()).orElseThrow();
        if (fleet.locationKind() != com.spacesim.world.FleetLocationKind.IN_SYSTEM
                || world.findFleetJump(fleet.fleetId()).isPresent()) throw new IllegalStateException("Ship in transit");
        if ("STOP_MINING".equals(action)) {
            if (!playerState.ownedFleetIds().contains(fleet.fleetId())) throw new IllegalStateException("Mining requires own fleet");
            if (runtime.freight().personalMiningOrders().stream().noneMatch(o -> o.fleetId().equals(fleet.fleetId())))
                throw new IllegalStateException("No personal mining work assigned");
            runtime.freight().stopPersonalMining(fleet.fleetId()); return;
        }
        if ("START_MINING".equals(action)) {
            if (kg <= 0) throw new IllegalArgumentException("Mining mass must be positive");
            var contact = requirePersonalMiningContact(fleet.fleetId(), stationId);
            if (contact.source().sourceState().isDepleted()) throw new IllegalStateException("Source depleted");
            runtime.freight().startPersonalMining(new com.spacesim.persistence.Stage20FreightPersistentState.PersonalMiningOrder(
                    fleet.fleetId(), stationId, com.spacesim.content.Stage22CivilianMiningProductionPath.EXTRACTION_METHOD_ID,
                    kg, world.getAuthoritativeWorldTick()));
            thrustAxisX = 0; thrustAxisY = 0; braking = false;
            return;
        }
        if ("REPORT_DISCOVERY".equals(action)) {
            if (!playerState.ownedFleetIds().contains(fleet.fleetId())) throw new IllegalStateException("Report requires own fleet");
            var service = coordinator.npcMissionService();
            var mission = service.snapshot().missions().stream().filter(m -> m.missionId().equals(stationId)).findFirst().orElseThrow();
            long tick = world.getAuthoritativeWorldTick();
            if (mission.status() != com.spacesim.world.Stage21HNpcMissionState.MissionStatus.ACCEPTED
                    || tick > mission.deadlineTick()
                    || mission.objective().kind() != com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind.DISCOVERY_AT_LEAST)
                throw new IllegalStateException("No active discovery contract");
            var npc = service.snapshot().npcs().stream().filter(n -> n.npcId().equals(mission.issuerNpcId())).findFirst().orElseThrow();
            if (!canContactNpc(npc.npcId())) throw new IllegalStateException("Discovery report requires access to its actual recipient");
            if (npc.availability() != com.spacesim.world.Stage21HNpcMissionState.NpcAvailability.AVAILABLE
                    || !npc.locationSystemId().equals(fleet.systemId()))
                throw new IllegalStateException("Discovery recipient is not locally available");
            var objective = mission.objective();
            var personal = runtime.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
            if (Stage21HPlayerMissionAuthority.evaluate(world, runtime.freight().capture(), personal,
                    coordinator.operations(), playerState, objective).result() != Stage21HPlayerMissionAuthority.Result.PARTICIPATED)
                throw new IllegalStateException("Personal discovery evidence is insufficient");
            String[] requirement = objective.requiredState().split(":", -1);
            var object = new com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef(
                    new com.spacesim.world.StarSystemId(objective.systemId()),
                    com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind.valueOf(requirement[0]), objective.subjectId());
            String report = "personal-discovery-report:" + mission.missionId();
            var received = runtime.sharePersonalDiscovery(mission.issuerFactionId(), object, report);
            service.receiveDiscovery(npc.npcId(), received, object, tick, report);
            // Reconcile at the actual delivery tick, including the inclusive final deadline tick.
            // Waiting until the next tick could incorrectly expire a timely delivered report.
            reconcileOnePlayerMission(mission.missionId(), runtime.captureState());
            return;
        }
        if ("LOAD_CONSUMABLE".equals(action)) {
            if (!playerState.docked() || kg <= 0) throw new IllegalStateException("Physical servicing requires docking and positive mass");
            var endpoint = runtime.infrastructure().endpoints().stream()
                    .filter(e -> pilotMarketReference(e.stationId()).filter(playerState.dockedAt()::equals).isPresent())
                    .findFirst().orElseThrow();
            var physical = runtime.arrival().materialization(fleet.systemId()).physicalState(fleet.localEntityId()).orElseThrow();
            if (!endpoint.systemId().equals(fleet.systemId()) || physical.position().distanceTo(endpoint.position()) > 1000d
                    || Math.hypot(physical.velocityXMps(), physical.velocityYMps()) > 1d)
                throw new IllegalStateException("Servicing requires current physical berth conditions");
            var fitted = world.findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId())
                    .getComponent(com.spacesim.components.EngineeringComponent.class);
            if (fitted == null) throw new IllegalStateException("No fitted interface");
            var bindings = com.spacesim.content.Stage22ShipConsumableCatalogLoader.loadDefault();
            var binding = bindings.findBinding(stationId);
            if (binding == null) throw new IllegalArgumentException("Unknown consumable binding");
            var old = fitted.runtimeState;
            var result = runtime.freight().loadManualConsumable(fleet.fleetId(), bindings,
                    com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.loadDefault(),
                    binding.id(), commodity, kg, fitted.fit, old.consumables());
            if (!result.committed()) throw new IllegalStateException("Physical consumable service rejected load: " + result.status());
            fitted.setRuntimeState(new com.spacesim.ship.ShipEngineeringRuntime.RuntimeState(result.consumables(),
                    old.sharedBusEnergyJ(), old.shipHeatStoredJ(), old.localHeatJByMount(), old.thrustLimitNByMount(),
                    old.coolantBusCapacityW(), old.ftlCooldownSecondsByMount()));
            runtime.synchronizeFreightEngineeringCargo(fleet.fleetId());
            return;
        }
        if ("PURCHASE".equals(action)) {
            if (!playerState.docked() || !playerState.dockedAt().systemId().equals(fleet.systemId()))
                throw new IllegalStateException("An existing local seller dock is required");
            var offeredId = new com.spacesim.world.FleetId(Long.parseLong(stationId));
            var offered = runtime.freight().findFreighter(offeredId).orElseThrow();
            var placement = world.findFleet(offeredId).orElseThrow();
            var seller = world.findSession(playerState.dockedAt().systemId()).orElseThrow()
                    .getEntityRegistry().require(playerState.dockedAt().entityId());
            var legal = seller.getComponent(com.spacesim.components.FactionComponent.class);
            boolean commissioned = runtime.infrastructure().endpoints().stream()
                    .filter(e -> e.systemId().equals(fleet.systemId()))
                    .anyMatch(e -> pilotMarketReference(e.stationId()).filter(playerState.dockedAt()::equals).isPresent());
            if (!commissioned || legal == null || legal.factionId != world.findFactionRuntimeId(offered.stableFactionId()).orElseThrow()
                    || placement.locationKind() != com.spacesim.world.FleetLocationKind.IN_SYSTEM
                    || !placement.systemId().equals(fleet.systemId()) || world.findFleetJump(offeredId).isPresent()
                    || offered.phase() != com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase.IDLE
                    || offered.cargoMassKg() != 0d)
                throw new IllegalStateException("Existing reserve is not offered by this local seller");
            var adapter = com.spacesim.player.PlayerRuntime.attachToCampaign(world, coordinator.content(), playerState);
            if (!new com.spacesim.player.PlayerOwnershipService(adapter).purchaseFactionFleet(
                    offeredId, offered.stableFactionId(), PILOT_SHIP_PRICE_MILLI_CREDITS))
                throw new IllegalStateException("Reserve ownership or consideration rejected");
            playerState = adapter.player(); return;
        }
        if ("SWITCH".equals(action)) {
            var selected = new com.spacesim.world.FleetId(Long.parseLong(stationId));
            var target = world.findFleet(selected).orElseThrow();
            var exact = runtime.arrival().materialization(fleet.systemId()).physicalState(fleet.localEntityId()).orElseThrow();
            if (selected.equals(fleet.fleetId()) || target.locationKind() != com.spacesim.world.FleetLocationKind.IN_SYSTEM
                    || !target.systemId().equals(fleet.systemId()) || Math.hypot(exact.velocityXMps(), exact.velocityYMps()) > 0.01d)
                throw new IllegalStateException("Stop before handing over control to a local owned ship");
            var adapter = com.spacesim.player.PlayerRuntime.attachToCampaign(world, coordinator.content(), playerState);
            if (!new com.spacesim.player.PlayerShipProgressionService(adapter).switchActiveFleet(selected))
                throw new IllegalStateException("Personal control cannot be handed over");
            playerState = adapter.player(); thrustAxisX = 0; thrustAxisY = 0; braking = false; return;
        }
        if ("JUMP".equals(action)) {
            if (playerState.docked()) throw new IllegalStateException("Undock before departure");
            var destination = new com.spacesim.world.StarSystemId(Long.parseLong(stationId));
            if (!world.getTopology().neighbors(fleet.systemId()).contains(destination))
                throw new IllegalStateException("Destination requires a direct topology edge");
            var plan = world.planFleetRouteFuel(fleet.fleetId(), List.of(fleet.systemId(), destination));
            if (!plan.supported() || !plan.feasible())
                throw new IllegalStateException("Existing onboard propulsion cannot safely complete this hop");
            world.requestFleetJump(fleet.fleetId(), destination);
            thrustAxisX = 0; thrustAxisY = 0; braking = false;
            return;
        }
        if ("UNDOCK".equals(action)) {
            if (!playerState.docked()) throw new IllegalStateException("Ship not docked");
            replacePilotFinancialDocking(playerState.walletMilliCredits(), null, playerState.discoveredObjects()); return;
        }
        var endpoint = runtime.infrastructure().endpoint(stationId);
        var ref = pilotMarketReference(stationId).orElseThrow(() -> new IllegalStateException("No commissioned market"));
        var physical = runtime.arrival().materialization(fleet.systemId()).physicalState(fleet.localEntityId()).orElseThrow();
        if (!fleet.systemId().equals(endpoint.systemId()) || physical.position().distanceTo(endpoint.position()) > 1000d
                || Math.hypot(physical.velocityXMps(), physical.velocityYMps()) > 1d)
            throw new IllegalStateException("Dock requires a local ship within 1 km and at most 1 m/s");
        if ("DOCK".equals(action)) {
            if (playerState.docked()) throw new IllegalStateException("Already docked");
            var discovered = new java.util.ArrayList<>(playerState.discoveredObjects());
            if (!discovered.contains(ref)) discovered.add(ref);
            replacePilotFinancialDocking(playerState.walletMilliCredits(), ref, discovered);
            runtime.recordPersonalStationVisit(playerState, stationId); return;
        }
        if ("PURCHASE_STATION".equals(action)) {
            if (!ref.equals(playerState.dockedAt())) throw new IllegalStateException("Station purchase requires this physical dock");
            long price = stationSalePriceMilliCredits(stationId);
            if (price <= 0) throw new IllegalStateException("No current civilian station sale offer");
            var industrial = runtime.industry().industrial().station(stationId);
            var adapter = com.spacesim.player.PlayerRuntime.attachToCampaign(world, coordinator.content(), playerState);
            if (!new com.spacesim.player.PlayerOwnershipService(adapter).purchaseFactionStation(
                    new com.spacesim.player.OwnedStationRef(ref.systemId(), ref.entityId()), industrial.stableFactionId(), price))
                throw new IllegalStateException("Station ownership or conserved consideration rejected");
            playerState = adapter.player(); return;
        }
        boolean buying = "BUY".equals(action);
        if (handlingUsedThisTick(stationId)) throw new IllegalStateException("This tick's handling has already been used");
        if (!buying && !"SELL".equals(action) || !ref.equals(playerState.dockedAt()) || kg <= 0)
            throw new IllegalStateException("Trade requires this dock and a positive amount");
        var station = world.findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId());
        var proxy = new com.badlogic.ashley.core.Entity().add(new WalletComponent(playerState.walletMilliCredits()))
                .add(new com.spacesim.components.IdentityComponent("PLAYER", com.spacesim.components.IdentityComponent.Kind.FLEET));
        if (playerState.factionContentId() != null) proxy.add(new com.spacesim.components.FactionComponent(
                world.findFactionRuntimeId(playerState.factionContentId()).orElseThrow()));
        var session = world.findSession(ref.systemId()).orElseThrow();
        long tick = world.getAuthoritativeWorldTick();
        String handlingReason = "player-market-handling:" + fleet.fleetId().value() + ":" + tick + ":";
        if (tick == 0 || session.getLedger().getEntries().stream().anyMatch(e -> e.reason().startsWith(handlingReason)))
            throw new IllegalStateException("This tick's physical handling budget is unavailable");
        long unitPrice = pilotCommodityPrice(stationId, commodity, buying);
        var ontology = com.spacesim.content.Stage18ResourceOntologyLoader.loadDefault();
        var definition = ontology.findCommodity(commodity);
        var hold = runtime.freight().cargoHoldSnapshot(fleet.fleetId());
        if (definition == null || !hold.capacityByStorageClassKg().containsKey(definition.storageClassId())
                || !endpoint.handlingCapability().supportedStorageClassIds().contains(definition.storageClassId()))
            throw new IllegalStateException("Cargo interface incompatible");
        var handling = endpoint.handlingCapability();
        var delivery = new java.util.concurrent.atomic.AtomicReference<com.spacesim.persistence.Stage20FreightRuntime.PersonalCommodityDeliveryReceipt>();
        if (!world.createTradeController(session).settlePhysicalCargo(station, proxy,
                buying ? com.spacesim.controllers.TradeTransactionPolicy.Direction.BUY_FROM_STATION
                        : com.spacesim.controllers.TradeTransactionPolicy.Direction.SELL_TO_STATION,
                Math.multiplyExact(unitPrice, kg), handlingReason + commodity + ":kg=" + kg,
                () -> {
                    double seconds = tick * coordinator.session().fixedStepSeconds();
                    var budget = handling.openInterval(coordinator.session().fixedStepSeconds());
                    if (buying) return runtime.freight().exchangeManualCommodity(fleet.fleetId(), endpoint.storage(), commodity, kg,
                            true, seconds, handling, budget);
                    var receipt = runtime.freight().deliverPersonalCommodity(fleet.fleetId(), endpoint.storage(), commodity, kg,
                            seconds, handling, budget);
                    receipt.ifPresent(delivery::set);
                    return receipt.isPresent();
                }))
            throw new IllegalStateException("Cargo, wallet, access or handling budget rejected trade");
        runtime.synchronizeFreightEngineeringCargo(fleet.fleetId());
        replacePilotFinancialDocking(proxy.getComponent(WalletComponent.class).getBalanceMilliCredits(), ref, playerState.discoveredObjects());
        if (!buying) {
            var recipient = new WalletComponent(playerState.walletMilliCredits());
            boolean completed = false;
            for (var mission : coordinator.npcMissions().missions().stream().filter(m ->
                    m.status() == com.spacesim.world.Stage21HNpcMissionState.MissionStatus.ACCEPTED
                            && m.objective().kind() == com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind.PLAYER_SUPPLY_DELIVERY_KG_AT_LEAST
                            && m.objective().subjectId().equals(stationId) && m.objective().requiredState().equals(commodity))
                    .sorted(java.util.Comparator.comparing(MissionContract::missionId)).toList()) {
                var result = coordinator.npcMissionService().tryCompletePersonalSupplyDelivery(world, runtime.freight(),
                        runtime.captureState().campaign().industrialState(), runtime.discoveryState().knowledgeFor(mission.issuerFactionId()),
                        playerState, mission.missionId(), delivery.get(), recipient);
                if (result.isEmpty()) continue;
                replacePilotFinancialDocking(recipient.getBalanceMilliCredits(), ref, playerState.discoveredObjects());
                recordPersonalCommit(com.spacesim.player.PlayerJournalState.Kind.MISSION_CHANGED, "COMPLETED",
                        stationId, mission.missionId(), kg, mission.rewardMilliCredits(), fleet.fleetId());
                completed = true; break;
            }
            if (!completed && !runtime.freight().claimPersonalDelivery(delivery.get()))
                throw new IllegalStateException("Committed personal sale requires its exact one-use physical delivery receipt");
        }
    }

    /**
     * Quotes an existing commissioned market without changing storage, money or prices in a save.
     * Legacy unversioned markers retain their static opening quotes. Versioned v2 markers reuse
     * the shared stock-scarcity rule against real kilograms and an authored role/capacity target.
     * @param stationId existing physical endpoint and ordinary market identity
     * @param commodityId admitted physical commodity
     * @param buying whether the pilot buys from the station
     * @return finite milli-credit quote for one kilogram
     */
    public long pilotCommodityPrice(String stationId, String commodityId, boolean buying) {
        long base = pilotCommodityPrice(commodityId, true);
        var ref = pilotMarketReference(stationId).orElseThrow(() -> new IllegalStateException("No commissioned market"));
        var identity = coordinator.runtime().world().findSession(ref.systemId()).orElseThrow()
                .getEntityRegistry().require(ref.entityId()).getComponent(com.spacesim.components.IdentityComponent.class);
        if (!identity.name.startsWith(PILOT_MARKET_V2_IDENTITY_PREFIX)) return pilotCommodityPrice(commodityId, buying);
        var endpoint = coordinator.runtime().infrastructure().endpoint(stationId);
        var commodity = com.spacesim.content.Stage18ResourceOntologyLoader.loadDefault().findCommodity(commodityId);
        var capacity = endpoint.storage().snapshot().capacityByStorageClassKg().get(commodity.storageClassId());
        if (capacity == null || capacity <= 0) throw new IllegalArgumentException("Commodity storage class unavailable");
        double fraction = switch (endpoint.stationArchetypeId()) {
            case "station.infrastructure.refinery_complex" -> 0.75d;
            case "station.infrastructure.frontier_multipurpose" -> 0.5d;
            default -> 0.25d;
        };
        double ratio = fraction * capacity / Math.max(1d, endpoint.storage().commodityMassKg(commodityId));
        double scarcity = com.spacesim.systems.MarketSystem.scarcityMultiplier(ratio);
        double bounded = Math.max(0.5d, Math.min(2d, scarcity));
        long sell = Math.max(1L, Math.round(base * bounded));
        return buying ? sell : Math.max(1L, sell * 9L / 10L);
    }

    /**
     * Disclosed opening market profile v1, in milli-credits per SI kilogram.
     * @param commodityId exact ordinary physical commodity
     * @param buying whether the pilot buys from the market
     * @return station quote; the bounded opening spread is part of the explicit new-game profile
     */
    public static long pilotCommodityPrice(String commodityId, boolean buying) {
        long base = switch (commodityId) {
            case "commodity.material.purified_water" -> 5000L;
            case "commodity.material.structural_alloy" -> 50000L;
            case "commodity.ore.metallic", "commodity.feedstock.metallic_ore" -> 10000L;
            case "commodity.feedstock.water_ice" -> 2500L;
            case "commodity.feedstock.volatile_feedstock", "commodity.feedstock.carbonaceous_feedstock",
                    "commodity.feedstock.silicate_minerals" -> 4000L;
            case "commodity.feedstock.light_metal_minerals" -> 12000L;
            case "commodity.feedstock.conductor_ore" -> 15000L;
            case "commodity.feedstock.strategic_metal_ore" -> 100000L;
            case "commodity.feedstock.fissile_minerals" -> 60000L;
            case "commodity.material.industrial_gases" -> 6000L;
            case "commodity.material.industrial_chemicals" -> 20000L;
            case "commodity.material.light_alloy" -> 80000L;
            case "commodity.material.conductor_metal" -> 75000L;
            case "commodity.material.refractory_alloy", "commodity.material.electronic_grade_material" -> 200000L;
            case "commodity.material.ceramic_glass", "commodity.material.carbon_material" -> 30000L;
            case "commodity.consumable.reactor_fuel", "commodity.component.heavy_components" -> 120000L;
            case "commodity.component.electrical_components" -> 150000L;
            case "commodity.component.precision_components" -> 300000L;
            default -> throw new IllegalArgumentException("Commodity has no admitted opening market quote");
        };
        return buying ? base : base * 9 / 10;
    }

    /**
     * Sets transient direct thrust, never coordinates or simulation time.
     * @param axisX normalized horizontal thrust
     * @param axisY normalized vertical thrust
     * @param brake explicit finite counter-thrust
     * @return whether a local undocked personally owned ship can accept input
     */
    public boolean setPilotThrust(float axisX, float axisY, boolean brake) {
        if (!Float.isFinite(axisX) || !Float.isFinite(axisY)) throw new IllegalArgumentException("Non-finite thrust axes");
        if (playerState == null || playerState.activeFleetId() == null || playerState.docked()
                || coordinator.runtime().world().findFleetJump(playerState.activeFleetId()).isPresent()) {
            thrustAxisX = 0; thrustAxisY = 0; braking = false; return false;
        }
        float magnitude = (float) Math.hypot(axisX, axisY);
        thrustAxisX = magnitude > 1 ? axisX / magnitude : axisX;
        thrustAxisY = magnitude > 1 ? axisY / magnitude : axisY;
        braking = brake; return true;
    }

    private boolean canAdvanceExactPersonalFleet(com.spacesim.world.FleetId id) {
        var freight = coordinator.runtime().freight().findFreighter(id).orElse(null);
        return freight == null ? playerState != null && id.equals(playerState.activeFleetId())
                : freight.phase() == com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase.IDLE;
    }

    private void advancePersonalFleetOrdersAtTick() {
        if (playerState == null) return;
        var runtime = coordinator.runtime();
        playerState = com.spacesim.player.PlayerRuntime.reconcileAuthorityReferences(runtime.world(), playerState);
        if (playerState.ownedFleetIds().isEmpty()) return;
        var adapter = com.spacesim.player.PlayerRuntime.attachToCampaign(runtime.world(), coordinator.content(), playerState);
        adapter.advanceComposedFleetOrders(runtime.arrival(), playerFlight,
                this::canAdvanceExactPersonalFleet,
                coordinator.session().fixedStepSeconds());
        playerState = adapter.player();
    }

    private void advancePilotAtTick(long tick) {
        if (playerState == null || playerState.activeFleetId() == null || playerState.docked()
                || !canAdvanceExactPersonalFleet(playerState.activeFleetId())) return;
        var runtime = coordinator.runtime();
        var placement = runtime.world().findFleet(playerState.activeFleetId()).orElse(null);
        if (placement == null || placement.locationKind() != com.spacesim.world.FleetLocationKind.IN_SYSTEM
                || runtime.world().findFleetJump(playerState.activeFleetId()).isPresent()
                || runtime.world().processedFleetJumpInLastInterval(playerState.activeFleetId())) return;
        var entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
        // Legacy checkpoint fixtures may have a non-fitted ship. They get no physical-control grant.
        if (entity.getComponent(com.spacesim.components.EngineeringComponent.class) == null) return;
        var materialization = runtime.arrival().materialization(placement.systemId());
        var physical = materialization.physicalState(placement.localEntityId()).orElseThrow();
        materialization.updatePhysicalState(placement.localEntityId(), playerFlight.advanceExact(entity, physical,
                thrustAxisX, thrustAxisY, braking, coordinator.session().fixedStepSeconds()));
    }

    private record PersonalMiningContact(com.spacesim.persistence.Stage20SourceSupplyMaterializer.MaterializedSource source,
            com.spacesim.ship.ShipMiningEngineeringAdapter.MiningCapability capability) { }

    private PersonalMiningContact requirePersonalMiningContact(com.spacesim.world.FleetId id, String sourceId) {
        if (playerState == null || !id.equals(playerState.activeFleetId())
                || !playerState.ownedFleetIds().contains(id) || playerState.docked()
                || playerState.fleetOrders().stream().anyMatch(o -> o.fleetId().equals(id)))
            throw new IllegalStateException("Mining requires undocked active personal ship without another order");
        var runtime = coordinator.runtime(); var world = runtime.world();
        var fleet = world.findFleet(id).orElseThrow();
        if (!canAdvanceExactPersonalFleet(id) || fleet.locationKind() != com.spacesim.world.FleetLocationKind.IN_SYSTEM
                || world.findFleetJump(id).isPresent() || world.processedFleetJumpInLastInterval(id))
            throw new IllegalStateException("Mining cannot run during travel");
        var source = runtime.industry().sourceOutposts().sources().source(sourceId);
        if (!source.systemId().equals(fleet.systemId())
                || source.sourceState().sourceKind() != com.spacesim.content.Stage18ExtractionCatalog.SourceKind.NATURAL_OCCURRENCE
                || source.sourceState().environment() != com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment.FREE_BODY)
            throw new IllegalStateException("Excavation requires a local free-body source");
        var fitted = world.findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId())
                .getComponent(com.spacesim.components.EngineeringComponent.class);
        if (fitted == null) throw new IllegalStateException("No physical mining equipment");
        var capability = new com.spacesim.ship.ShipMiningEngineeringAdapter()
                .derive(new com.spacesim.ship.ProductionEngineeringRuntimeResolver().derive(fitted)).orElseThrow(
                        () -> new IllegalStateException("No operating installed excavation section"));
        var physical = runtime.arrival().materialization(fleet.systemId()).physicalState(fleet.localEntityId()).orElseThrow();
        if (physical.position().distanceTo(source.position()) > capability.workingRangeM()
                || Math.hypot(physical.velocityXMps(), physical.velocityYMps()) > capability.maximumDriftMps())
            throw new IllegalStateException("Source outside installed mining contact envelope");
        return new PersonalMiningContact(source, capability);
    }

    private void advancePersonalMiningAtTick(long tick) {
        var runtime = coordinator.runtime();
        for (var order : runtime.freight().personalMiningOrders()) {
            if (!runtime.freight().claimPersonalMiningTick(order.fleetId(), tick)) continue;
            if (thrustAxisX != 0 || thrustAxisY != 0 || braking) {
                stopMiningWithEvidence(order, "DIRECT_CONTROL"); continue;
            }
            PersonalMiningContact contact;
            try { contact = requirePersonalMiningContact(order.fleetId(), order.sourceId()); }
            catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException unavailable) {
                stopMiningWithEvidence(order, "CONTACT_UNAVAILABLE"); continue;
            }
            double duration = coordinator.session().fixedStepSeconds();
            double requested = Math.min(order.requestedSourceKgPerTick(), contact.capability().maximumSourceKgPerSecond() * duration);
            var result = runtime.freight().extractPersonalCommodity(order.fleetId(), contact.source().sourceState(),
                    order.methodId(), requested, contact.capability().extraction(), contact.capability().openInterval(duration),
                    tick * duration);
            if (result.committed()) {
                runtime.synchronizeFreightEngineeringCargo(order.fleetId());
                if (result.outputMassStoredKg() > 0) runtime.recordPersonalExtractionSample(playerState, order.sourceId());
            }
            if (!result.committed() || contact.source().sourceState().isDepleted())
                stopMiningWithEvidence(order, contact.source().sourceState().isDepleted() ? "SOURCE_DEPLETED" : "EXTRACTION_UNAVAILABLE");
        }
    }

    private void stopMiningWithEvidence(com.spacesim.persistence.Stage20FreightPersistentState.PersonalMiningOrder order, String reason) {
        coordinator.runtime().freight().stopPersonalMining(order.fleetId());
        recordPersonalCommit(com.spacesim.player.PlayerJournalState.Kind.MINING_STOPPED, reason,
                order.sourceId(), order.methodId(), 0, 0, order.fleetId());
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
        validateOperations(
                smallCraft, hangars, flightDeck, checked, logistics, carrierWings,
                coordinator.runtime().world().getAuthoritativeWorldTick());
        missions = checked;
    }

    /**
     * Commits a validated G logistics-state transition without granting delivery or inventory.
     *
     * @param next immutable pending-delivery state returned by the G physical logistics authority
     */
    public void commitLogisticsState(LogisticsState next) {
        LogisticsState checked = Objects.requireNonNull(next, "next");
        validateOperations(
                smallCraft, hangars, flightDeck, missions, checked, carrierWings,
                coordinator.runtime().world().getAuthoritativeWorldTick());
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
        validateOperations(
                smallCraft, hangars, flightDeck, missions, logistics, checked,
                coordinator.runtime().world().getAuthoritativeWorldTick());
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
        return coordinator.advanceFrame(realDeltaSeconds, tick -> {
            advancePersonalFleetOrdersAtTick();
            advancePilotAtTick(tick);
            advancePersonalMiningAtTick(tick);
            var handlingBudgets = new java.util.HashMap<String, com.spacesim.economy.Stage18LogisticsRuntime.TransferBudget>();
            advancePersonalManufacturingAtTick(tick, handlingBudgets);
            advancePersonalRepairsAtTick(tick);
            advanceModuleTransfersAtTick(tick, handlingBudgets);
            reconcilePlayerMissionsAtTick(tick);
        });
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
            advancePersonalFleetOrdersAtTick();
            advancePilotAtTick(tick);
            advancePersonalMiningAtTick(tick);
            var handlingBudgets = new java.util.HashMap<String, com.spacesim.economy.Stage18LogisticsRuntime.TransferBudget>();
            advancePersonalManufacturingAtTick(tick, handlingBudgets);
            advancePersonalRepairsAtTick(tick);
            advanceModuleTransfersAtTick(tick, handlingBudgets);
            reconcilePlayerMissionsAtTick(tick);
        });
    }

    /**
     * Immutable, non-forgeable preview issued by one live campaign authority.
     * Its exact checkpoint is a stale-state guard, never a replacement live world.
     */
    public static final class MissionCommandPreview {
        private final Stage228CampaignAuthority owner;
        private final Stage228GeneratedCampaignPersistentState baseline;
        private final PlayerCommand command;
        private final String missionId;
        private final int supplyKilograms;
        private final boolean allowed;
        private final String reasonCode;

        private MissionCommandPreview(Stage228CampaignAuthority owner,
                Stage228GeneratedCampaignPersistentState baseline, PlayerCommand command,
                String missionId, int supplyKilograms, boolean allowed, String reasonCode) {
            this.owner = owner;
            this.baseline = baseline;
            this.command = command;
            this.missionId = missionId;
            this.supplyKilograms = supplyKilograms;
            this.allowed = allowed;
            this.reasonCode = reasonCode;
        }

        /** @return whether the existing domain authority accepted the candidate command */
        public boolean allowed() { return allowed; }
        /** @return bounded rejection/explanation code, never an exception message */
        public String reasonCode() { return reasonCode; }
        /** @return requested lifecycle transition */
        public PlayerCommand command() { return command; }
        /** @return existing contract identity for internal routing */
        public String missionId() { return missionId; }
        /** @return exact proposed reward, including proportional supply acceptance */
        public long proposedRewardMilliCredits() {
            var mission = baseline.stage21Runtime().stage21HRuntime().npcMissionState().missions().stream()
                    .filter(m -> m.missionId().equals(missionId)).findFirst().orElseThrow();
            return supplyKilograms == 0 ? mission.rewardMilliCredits()
                    : java.math.BigInteger.valueOf(mission.rewardMilliCredits()).multiply(java.math.BigInteger.valueOf(supplyKilograms))
                            .divide(java.math.BigInteger.valueOf(mission.objective().threshold())).longValueExact();
        }
    }

    /**
     * Executes validation on an isolated exact checkpoint with the same submission path.
     * No live treasury, escrow, player balance, knowledge or clock is changed by preview.
     *
     * @param command requested player transition
     * @param missionId selected existing contract identity
     * @return authority-bound preview with an exact stale-state guard
     */
    public MissionCommandPreview previewMissionCommand(PlayerCommand command, String missionId) {
        return previewMissionCommand(command, missionId, 0);
    }

    /**
     * Previews acceptance of a player-selected supply portion with exact refund/re-funding.
     * @param command ordinary mission transition
     * @param missionId existing offered proposal
     * @param supplyKilograms zero for the full proposal, otherwise requested supply kilograms
     * @return isolated authority preview
     */
    public MissionCommandPreview previewMissionCommand(PlayerCommand command, String missionId, int supplyKilograms) {
        Objects.requireNonNull(command, "command");
        if (supplyKilograms < 0 || supplyKilograms > 0 && command != PlayerCommand.ACCEPT)
            throw new IllegalArgumentException("Only acceptance can select a positive supply portion");
        String id = Objects.requireNonNull(missionId, "missionId").strip();
        if (id.isEmpty()) throw new IllegalArgumentException("missionId is empty");
        Stage228GeneratedCampaignPersistentState baseline = captureState();
        if (playerState == null) {
            return new MissionCommandPreview(this, baseline, command, id, supplyKilograms, false, "player.uninitialized");
        }
        try {
            // Reuse every domain validator, including escrow/treasury capacity, on a candidate.
            // Candidate changes are discarded; live submission calls this same command function.
            Stage228CampaignAuthority candidate = restore(baseline);
            candidate.executePlayerMissionCommand(command, id, supplyKilograms);
            return new MissionCommandPreview(this, baseline, command, id, supplyKilograms, true, "command.allowed");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return new MissionCommandPreview(this, baseline, command, id, supplyKilograms, false, "mission.unavailable");
        }
    }

    /**
     * Revalidates and submits one preview to the existing mission/treasury owners.
     * Foreign, rejected and stale previews fail before any mutation. Repeated submission also fails.
     *
     * @param preview token returned by this live campaign
     * @return resulting durable contract
     */
    public MissionContract submitMissionCommand(MissionCommandPreview preview) {
        MissionCommandPreview checked = Objects.requireNonNull(preview, "preview");
        if (checked.owner != this || !checked.allowed) {
            throw new IllegalStateException("Mission preview is not authorized by this campaign");
        }
        if (!checked.baseline.equals(captureState())) {
            throw new IllegalStateException("Mission preview is stale");
        }
        long beforeWallet = playerState.walletMilliCredits();
        var result = executePlayerMissionCommand(checked.command, checked.missionId, checked.supplyKilograms);
        recordPersonalCommit(com.spacesim.player.PlayerJournalState.Kind.MISSION_CHANGED, result.status().name(),
                result.issuerFactionId(), result.missionId(), 0, playerState.walletMilliCredits() - beforeWallet, null);
        return result;
    }

    private MissionContract executePlayerMissionCommand(PlayerCommand command, String missionId) {
        return executePlayerMissionCommand(command, missionId, 0);
    }

    private MissionContract executePlayerMissionCommand(PlayerCommand command, String missionId, int supplyKilograms) {
        if (playerState == null) throw new IllegalStateException("Player is not initialized");
        var service = coordinator.npcMissionService();
        long tick = coordinator.runtime().world().getAuthoritativeWorldTick();
        MissionContract current = service.validatePlayerCommand(command, missionId, tick);
        var issuer = service.snapshot().npcs().stream()
                .filter(npc -> npc.npcId().equals(current.issuerNpcId())).findFirst().orElseThrow();
        if (current.status() == com.spacesim.world.Stage21HNpcMissionState.MissionStatus.OFFERED
                && !canContactNpc(issuer.npcId()))
            throw new IllegalStateException("Contact the available mission issuer at its actual posting first");
        // Existing accepted contracts belong to the single human contractor. New offers require
        // that contractor's own discovered posting; the knowledge viewer never grants permission.
        if (current.status() == com.spacesim.world.Stage21HNpcMissionState.MissionStatus.OFFERED
                && !playerState.discoveredSystemIds().contains(issuer.locationSystemId())) {
            throw new IllegalStateException("Player has not discovered this mission issuer's posting");
        }
        if (supplyKilograms > 0) {
            if (command != PlayerCommand.ACCEPT
                    || current.objective().kind() != com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind.PLAYER_SUPPLY_DELIVERY_KG_AT_LEAST
                    || supplyKilograms > current.objective().threshold())
                throw new IllegalArgumentException("Requested supply portion is incompatible with the offered contract");
            if (supplyKilograms < current.objective().threshold()) {
                var runtime = coordinator.runtime();
                var replacement = service.offerPersonalSupplyPortion(runtime.world(), runtime.freight().capture(),
                        runtime.captureState().campaign().industrialState(), runtime.discoveryState().knowledgeFor(current.issuerFactionId()),
                        coordinator.operations(), missionId, supplyKilograms);
                return service.acceptMission(replacement.missionId(), tick);
            }
        }
        return switch (command) {
            case ACCEPT -> service.acceptMission(missionId, tick);
            case REJECT -> service.rejectMission(coordinator.runtime().world(), missionId);
            case CANCEL -> service.cancelMission(coordinator.runtime().world(), missionId);
        };
    }

    private void reconcilePlayerMissionsAtTick(long tick) {
        if (tick != coordinator.runtime().world().getAuthoritativeWorldTick()) {
            throw new IllegalStateException("Player missions require the exact campaign tick");
        }
        if (playerState == null) return;
        playerState = com.spacesim.player.PlayerRuntime.reconcileAuthorityReferences(
                coordinator.runtime().world(), playerState);
        playerState = com.spacesim.player.PlayerRuntime.discoverActiveFleetLocation(
                coordinator.runtime().world(), playerState);
        var service = coordinator.npcMissionService();
        var due = new java.util.ArrayList<>(service.dueMissionIds(tick, 8));
        // Existing physical services need not emit a mission-specific event for every change.
        // A bounded, stateless campaign-tick sweep closes that seam without inventing outcomes.
        if (tick % 60L == 0L && due.size() < 8) {
            var active = service.snapshot().missions().stream().filter(MissionContract::active)
                    .sorted(java.util.Comparator.comparing(MissionContract::missionId)).toList();
            if (!active.isEmpty()) {
                long batches = (active.size() + 7L) / 8L;
                int start = (int) (((tick / 60L - 1L) % batches) * 8L);
                for (int i = start; i < Math.min(active.size(), start + 8) && due.size() < 8; i++) {
                    String id = active.get(i).missionId();
                    if (!due.contains(id)) due.add(id);
                }
            }
        }
        if (due.isEmpty()) return;
        var runtimeCheckpoint = coordinator.runtime().captureState();
        for (String id : due) {
            reconcileOnePlayerMission(id, runtimeCheckpoint);
        }
    }

    /**
     * Checks personal contact access without consuming money or revealing NPC knowledge.
     * Production dispatchers require their saved posting and the player's actual stationary berth.
     * @param npcId persistent character identity
     * @return whether this player can currently communicate with the character
     */
    public boolean canContactNpc(String npcId) {
        var npc = coordinator.npcMissions().npcs().stream().filter(n -> n.npcId().equals(npcId)).findFirst().orElse(null);
        if (npc == null || playerState == null
                || npc.availability() != com.spacesim.world.Stage21HNpcMissionState.NpcAvailability.AVAILABLE
                || !playerState.discoveredSystemIds().contains(npc.locationSystemId())) return false;
        if (!npcId.startsWith("npc.stage23b.dispatcher:")) return true;
        var posting = npc.knowledge().stream().filter(k -> k.factId().equals(npcId + ":posting")
                && k.claimCode().startsWith("DISCOVERY.INFRASTRUCTURE.")).findFirst().orElse(null);
        if (posting == null || !playerState.docked() || playerState.activeFleetId() == null
                || !playerState.ownedFleetIds().contains(playerState.activeFleetId())) return false;
        if (pilotMarketReference(posting.subjectId()).filter(playerState.dockedAt()::equals).isEmpty()) return false;
        var world = coordinator.runtime().world();
        var fleet = world.findFleet(playerState.activeFleetId()).orElse(null);
        if (fleet == null || fleet.locationKind() != com.spacesim.world.FleetLocationKind.IN_SYSTEM
                || !fleet.systemId().equals(npc.locationSystemId()) || world.findFleetJump(fleet.fleetId()).isPresent()) return false;
        var station = coordinator.runtime().industry().industrial().stations().stream()
                .filter(s -> s.stationId().equals(posting.subjectId()) && s.systemId().equals(npc.locationSystemId())
                        && s.stableFactionId().equals(npc.factionContentId())).findFirst().orElse(null);
        if (station == null) return false;
        var endpoint = coordinator.runtime().infrastructure().endpoint(station.stationId());
        var physical = coordinator.runtime().arrival().materialization(fleet.systemId()).physicalState(fleet.localEntityId()).orElse(null);
        return physical != null && physical.position().distanceTo(endpoint.position()) <= 1000d
                && Math.hypot(physical.velocityXMps(), physical.velocityYMps()) <= 1d;
    }

    /**
     * Resolves station ownership independently of the presentation viewer.
     * @param stationId generated station identity
     * @return actual personal ownership
     */
    public boolean ownsProductionStation(String stationId) {
        if (playerState == null) return false;
        var ref = pilotMarketReference(stationId).orElse(null);
        return ref != null && playerState.ownedStations().stream().anyMatch(s -> s.systemId().equals(ref.systemId())
                && s.stationEntityId().equals(ref.entityId()));
    }

    /**
     * Discloses an authored sale price only for known civilian stations.
     * Legal registration and the existing physical infrastructure survive the purchase.
     * @param stationId actual generated industrial station
     * @return current offer in milli-credits, or zero when not offered to this player
     */
    public long stationSalePriceMilliCredits(String stationId) {
        if (playerState == null || ownsProductionStation(stationId)) return 0;
        var ref = pilotMarketReference(stationId).orElse(null);
        if (ref == null || !playerState.discoveredObjects().contains(ref)) return 0;
        var station = coordinator.runtime().industry().industrial().station(stationId);
        var legal = coordinator.runtime().world().findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId())
                .getComponent(com.spacesim.components.FactionComponent.class);
        var seller = coordinator.runtime().world().findFactionRuntimeId(station.stableFactionId());
        if (legal == null || seller.isEmpty() || legal.factionId != seller.get()) return 0;
        long price = GeneratedCampaignStationSalePolicy.priceMilliCredits(station.stationArchetypeId());
        if (price == 0 || Objects.equals(station.stableFactionId(), playerState.factionContentId())) return 0;
        return price;
    }

    private com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingCapability manufacturingCapability(
            String stationId, String productId) {
        var catalog = com.spacesim.content.Stage22CivilianMiningProductionPath.loadManufacturing();
        var binding = catalog.findProductBinding(productId);
        if (binding == null) return null;
        var profile = catalog.findProductProfile(binding.profileId());
        var required = profile.requiredCapabilityTags();
        var product = com.spacesim.content.Stage22CivilianMiningProductionPath.loadProducts().findProduct(productId);
        if (product == null) return null;
        var ontology = com.spacesim.content.Stage18ResourceOntologyLoader.loadDefault();
        var storageClasses = new java.util.HashSet<String>();
        storageClasses.add(product.storageClassId());
        profile.inputs().forEach(i -> storageClasses.add(ontology.findCommodity(i.commodityId()).storageClassId()));
        var station = coordinator.runtime().industry().industrial().station(stationId);
        return station.facilityCapabilities().stream()
                .filter(c -> c.capabilityTags().containsAll(required) && c.effectiveProcessPowerW() > 0
                        && c.effectiveEngineeringWorkRate() > 0
                        && (profile.maintenanceWorkSecondsPerOutputKg() == 0 || c.effectiveMaintenanceWorkRate() > 0)
                        && c.storageClassInterfaces().containsAll(storageClasses)
                        && c.maxHandledUnitMassKg() >= product.unitMassKg())
                .sorted(java.util.Comparator.comparing(com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot::facilityInstanceId))
                .map(c -> new com.spacesim.economy.Stage18ManufacturingRuntime.ManufacturingCapability(c.facilityInstanceId(),
                        c.capabilityTags(), c.effectiveProcessPowerW(), c.effectiveEngineeringWorkRate(), c.effectiveMaintenanceWorkRate()))
                .findFirst().orElse(null);
    }

    /**
     * Checks installed handling and assembly limits without reserving materials.
     * @param stationId actual station
     * @param productId authored product
     * @return real installed handling/assembly feasibility
     */
    public boolean hasManufacturingLine(String stationId, String productId) {
        return manufacturingCapability(stationId, productId) != null;
    }

    private com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot yardConstructionCapability(
            String stationId, String definitionId) {
        var specification = com.spacesim.content.Stage23YardConstructionCatalog.loadDefault().find(definitionId);
        if (specification == null) return null;
        return coordinator.runtime().industry().industrial().station(stationId).facilityCapabilities().stream()
                .filter(c -> c.status() == com.spacesim.economy.Stage18FacilityRuntime.Status.ACTIVE
                        && c.effectiveEngineeringWorkRate() > 0d
                        && c.capabilityTags().containsAll(specification.requiredCapabilityTags()))
                .sorted(java.util.Comparator.comparing(com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot::facilityInstanceId))
                .findFirst().orElse(null);
    }

    /**
     * Checks an actual installed line against the authored physical yard bill.
     * @param stationId actual station
     * @param definitionId authored yard design
     * @return whether a compatible active physical construction line exists
     */
    public boolean hasYardConstructionLine(String stationId, String definitionId) {
        return yardConstructionCapability(stationId, definitionId) != null;
    }

    private com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot constructionCapability(
            String stationId, String definitionId) {
        var catalog = com.spacesim.content.Stage18FacilityConstructionCatalogLoader.loadDefault();
        var binding = catalog.findFacility(definitionId);
        if (binding == null) return null;
        var profile = catalog.findProfile(binding.profileId());
        return coordinator.runtime().industry().industrial().station(stationId).facilityCapabilities().stream()
                .filter(c -> c.status() == com.spacesim.economy.Stage18FacilityRuntime.Status.ACTIVE
                        && c.effectiveEngineeringWorkRate() > 0d
                        && c.capabilityTags().containsAll(profile.requiredCapabilityTags()))
                .sorted(java.util.Comparator.comparing(com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot::facilityInstanceId))
                .findFirst().orElse(null);
    }

    /**
     * Checks an installed physical construction line without reserving materials or work.
     * @param stationId actual station identity
     * @param definitionId authored facility definition
     * @return whether a compatible active installed line is available
     */
    public boolean hasFacilityConstructionLine(String stationId, String definitionId) {
        return constructionCapability(stationId, definitionId) != null;
    }

    /**
     * Checks actual berth and compatible installed work without consuming stock or time.
     * @param stationId actual personal station
     * @return whether the current personal ship has feasible local repair capability
     */
    public boolean hasPersonalRepairAccess(String stationId) { return repairContext(stationId, null) != null; }
    /**
     * Returns the disclosed money reservation for an actual docked foreign repair.
     * @param stationId actual station
     * @return zero for owner work or no current service, otherwise the full foreign quote
     */
    public long personalRepairPriceMilliCredits(String stationId) {
        if (ownsProductionStation(stationId)) return 0;
        var context = repairContext(stationId, null);
        if (context == null) return 0;
        try { return repairQueue.serviceQuote(coordinator.runtime().world().findFleet(playerState.activeFleetId())
                .orElseThrow().localEntityId(), context.ship(), context.yard()); }
        catch (IllegalArgumentException | IllegalStateException unavailable) { return 0; }
    }

    private com.spacesim.world.StarSystemId fleetSystemForPlayer() {
        return coordinator.runtime().world().findFleet(playerState.activeFleetId()).orElseThrow().systemId();
    }

    private boolean civilianRepairOperator(String stationId, com.spacesim.economy.ShipyardRepairQueueState.RepairOrder order) {
        if (playerState == null) return false;
        var runtime = coordinator.runtime();
        var station = runtime.industry().industrial().station(stationId);
        if (GeneratedCampaignStationSalePolicy.priceMilliCredits(station.stationArchetypeId()) <= 0) return false;
        var seller = runtime.world().findFactionEconomicState(station.stableFactionId());
        var ref = pilotMarketReference(stationId).orElse(null);
        var runtimeId = runtime.world().findFactionRuntimeId(station.stableFactionId());
        if (seller.isEmpty() || ref == null || runtimeId.isEmpty()) return false;
        var legal = runtime.world().findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId())
                .getComponent(com.spacesim.components.FactionComponent.class);
        if (legal == null || legal.factionId != runtimeId.orElseThrow()) return false;
        if (order == null) return true;
        var payment = order.servicePayment();
        return payment != null && payment.sellerFactionId().equals(station.stableFactionId());
    }

    private com.spacesim.economy.ShipyardRepairWorkQueue.RepairContext repairContext(String stationId,
            com.spacesim.economy.ShipyardRepairQueueState.RepairOrder order) {
        if (order != null && order.servicePayment() != null && coordinator.runtime().world()
                .findFactionEconomicState(order.servicePayment().sellerFactionId()).orElseThrow().treasuryMilliCredits()
                > Long.MAX_VALUE - order.servicePayment().reservedMilliCredits()) return null;
        var local = personalYards(stationId, order == null ? null : order.fleetId(), order == null ? null : order.assetId(),
                !ownsProductionStation(stationId) && civilianRepairOperator(stationId, order));
        if (local == null) return null;
        return local.yards().stream().filter(y -> order == null || y.yardInstanceId().equals(order.yardInstanceId()))
                .filter(y -> com.spacesim.economy.ShipyardRepairWorkQueue.plan(local.asset(), local.ship(), y).feasibility().feasible())
                .map(y -> new com.spacesim.economy.ShipyardRepairWorkQueue.RepairContext(local.ship(), y)).findFirst().orElse(null);
    }

    private record PersonalYards(com.spacesim.persistence.EntityId asset,
            com.spacesim.components.EngineeringComponent ship,
            java.util.List<com.spacesim.economy.Stage18ShipyardRuntime.YardCapabilitySnapshot> yards) { }

    private PersonalYards personalYards(String stationId, Long fleetId, Long assetId) {
        return personalYards(stationId, fleetId, assetId, false);
    }

    private PersonalYards personalYards(String stationId, Long fleetId, Long assetId, boolean authorizedForeignRepair) {
        if (playerState == null || playerState.activeFleetId() == null
                || !ownsProductionStation(stationId) && !authorizedForeignRepair) return null;
        var ref = pilotMarketReference(stationId).orElse(null);
        if (ref == null || !ref.equals(playerState.dockedAt())) return null;
        var runtime = coordinator.runtime(); var world = runtime.world();
        var fleet = world.findFleet(playerState.activeFleetId()).orElse(null);
        var endpoint = runtime.infrastructure().endpoint(stationId);
        if (fleet == null || !playerState.ownedFleetIds().contains(fleet.id())
                || fleet.locationKind() != com.spacesim.world.FleetLocationKind.IN_SYSTEM
                || !fleet.systemId().equals(endpoint.systemId()) || world.findFleetJump(fleet.id()).isPresent()
                || fleetId != null && (fleetId != fleet.id().value() || assetId != fleet.localEntityId().value())) return null;
        var position = runtime.arrival().materialization(fleet.systemId()).physicalState(fleet.localEntityId()).orElse(null);
        if (position == null || position.position().distanceTo(endpoint.position()) > 1000d
                || Math.hypot(position.velocityXMps(), position.velocityYMps()) > 1d) return null;
        var ship = world.findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId())
                .getComponent(com.spacesim.components.EngineeringComponent.class);
        if (ship == null) return null;
        var yards = runtime.industry().industrial().station(stationId).yardCapabilities().stream()
                .filter(y -> y.active())
                .sorted(java.util.Comparator.comparing(com.spacesim.economy.Stage18ShipyardRuntime.YardCapabilitySnapshot::yardInstanceId))
                .toList();
        return new PersonalYards(fleet.localEntityId(), ship, yards);
    }

    private static final class RefitCatalog {
        private static final com.spacesim.content.ship.ShipEngineeringCatalog ENGINEERING =
                com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.loadDefault();
    }

    private static com.spacesim.ship.ShipEngineeringState.InstalledFit personalRefitTarget(String id) {
        if (!java.util.Set.of(com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT,
                com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT).contains(id))
            throw new IllegalArgumentException("Unsupported personal refit target");
        return com.spacesim.ship.ShipEngineeringState.InstalledFit.fromDemonstrator(RefitCatalog.ENGINEERING.findDemonstratorFit(id));
    }

    private static String personalRefitTargetId(com.spacesim.ship.ShipEngineeringState.InstalledFit fit) {
        for (String id : java.util.List.of(com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT,
                com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT))
            if (personalRefitTarget(id).equals(fit)) return id;
        throw new IllegalArgumentException("Unsupported personal refit fitting");
    }

    private boolean refitCapacityAvailable(FleetId fleetId, com.spacesim.components.EngineeringComponent ship,
            com.spacesim.ship.ShipEngineeringState.InstalledFit target) {
        try {
            var freight = coordinator.runtime().freight().findFreighter(fleetId).orElseThrow();
            String sourceId = freight.fitId().equals("fit.test_bulk_freighter_baseline_v1")
                    ? com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT : freight.fitId();
            if (!ship.fit.equals(personalRefitTarget(sourceId))) return false;
            coordinator.runtime().freight().previewPersonalRefit(fleetId, personalRefitTargetId(target));
            return new com.spacesim.ship.ShipEngineeringRuntime(RefitCatalog.ENGINEERING).derive(target, ship.runtimeState,
                    ship.instanceState.damage().moduleDamage()).validation().isValid();
        } catch (IllegalArgumentException | IllegalStateException unavailable) { return false; }
    }

    private com.spacesim.economy.ShipyardRefitWorkQueue.Context refitContext(String stationId,
            com.spacesim.economy.ShipyardRefitQueueState.Order order,
            com.spacesim.ship.ShipEngineeringState.InstalledFit target) {
        boolean foreign = !ownsProductionStation(stationId);
        if (foreign && (!civilianRepairOperator(stationId, null) || order != null && (order.servicePayment() == null
                || !order.servicePayment().sellerFactionId().equals(coordinator.runtime().industry().industrial().station(stationId).stableFactionId())
                || coordinator.runtime().world().findFactionEconomicState(order.servicePayment().sellerFactionId()).orElseThrow().treasuryMilliCredits()
                        > Long.MAX_VALUE - order.servicePayment().reservedMilliCredits()))) return null;
        var local = personalYards(stationId, order == null ? null : order.fleetId(), order == null ? null : order.assetId(), foreign);
        if (local == null) return null;
        return local.yards().stream().filter(y -> order == null || y.yardInstanceId().equals(order.yardInstanceId()))
                .filter(y -> com.spacesim.economy.ShipyardRefitWorkQueue.plan(local.asset(), local.ship(), target, y).feasibility().feasible())
                .map(y -> new com.spacesim.economy.ShipyardRefitWorkQueue.Context(local.ship(), y,
                        completion -> refitCapacityAvailable(playerState.activeFleetId(), local.ship(), completion.fit())))
                .findFirst().orElse(null);
    }

    /**
     * Checks actual berth, fitting, hold and installed yard without reserving equipment.
     * @param stationId personally owned station
     * @param targetFitId supported same-hull target
     * @return whether physical context and cargo capacity permit planning
     */
    public boolean hasPersonalRefitAccess(String stationId, String targetFitId) {
        if (playerState == null || playerState.activeFleetId() == null) return false;
        try {
            var target = personalRefitTarget(targetFitId); var context = refitContext(stationId, null, target);
            return context != null && refitCapacityAvailable(playerState.activeFleetId(), context.ship(), target);
        } catch (IllegalArgumentException | IllegalStateException unavailable) { return false; }
    }
    /**
     * Discloses the exact foreign pristine-input refit reservation at the actual berth.
     * @param stationId actual station
     * @param targetFitId supported same-hull target
     * @return zero for owner work or unavailable service, otherwise the full reservation
     */
    public long personalRefitPriceMilliCredits(String stationId, String targetFitId) {
        if (ownsProductionStation(stationId) || playerState == null || playerState.activeFleetId() == null) return 0;
        try {
            var target = personalRefitTarget(targetFitId); var context = refitContext(stationId, null, target);
            if (context == null) return 0;
            return com.spacesim.economy.ShipyardRefitWorkQueue.serviceQuote(coordinator.runtime().world()
                    .findFleet(playerState.activeFleetId()).orElseThrow().localEntityId(), context.ship(), target, context.yard(), java.util.Map.of());
        } catch (IllegalArgumentException | IllegalStateException unavailable) { return 0; }
    }
    private static String storedModuleTargetId(com.spacesim.economy.ShipyardModuleCustodyState.StoredModule row) {
        String module = row.condition().assignment().moduleId();
        if (module.equals("module.industrial_union_cargo_section_v1"))
            return com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT;
        if (module.equals(com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID))
            return com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT;
        throw new IllegalArgumentException("Stored module has no supported personal fitting");
    }
    /**
     * Reports an exact used module reservation without modifying custody.
     * @param custodyId physical equipment identity
     * @return whether an incomplete refit owns this input
     */
    public boolean isStoredModuleReserved(String custodyId) {
        return moduleTransfers.capture().orders().stream().anyMatch(o -> o.source().custodyId().equals(custodyId))
                || refitQueue.capture().orders().stream().flatMap(o -> o.reservedUsedModulesByTargetMount().values().stream())
                .anyMatch(row -> row.custodyId().equals(custodyId));
    }
    /**
     * Checks the actual used module, ship, yard and docking context without reserving anything.
     * @param custodyId exact stored equipment identity
     * @return whether a used-equipment refit can be previewed
     */
    public boolean hasPersonalStoredModuleRefitAccess(String custodyId) {
        if (!ownsStoredModule(custodyId)) return false;
        if (isStoredModuleReserved(custodyId) || playerState == null || playerState.activeFleetId() == null
                || refitQueue.capture().orders().stream().anyMatch(o -> o.fleetId() == playerState.activeFleetId().value())
                || repairQueue.capture().orders().stream().anyMatch(o -> o.fleetId() == playerState.activeFleetId().value())) return false;
        var row = moduleCustody.modules().stream().filter(m -> m.custodyId().equals(custodyId)).findFirst().orElse(null);
        if (row == null) return false;
        try { return hasPersonalRefitAccess(row.stationId(), storedModuleTargetId(row)); }
        catch (IllegalArgumentException unavailable) { return false; }
    }

    /**
     * Checks equipment ownership independently from its physical storage address.
     * @param custodyId exact equipment
     * @return actual personal ownership or historical owner-storage access
     */
    public boolean ownsStoredModule(String custodyId) {
        if (playerState == null) return false;
        var row = moduleCustody.modules().stream().filter(m -> m.custodyId().equals(custodyId)).findFirst().orElse(null);
        if (row == null) return false;
        if (row.ownerActorId() != null) return row.ownerActorId().equals(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID);
        return moduleCarrier(row.stationId()).map(playerState.ownedFleetIds()::contains).orElseGet(() -> ownsProductionStation(row.stationId()));
    }

    private boolean settleRefitPayment(com.spacesim.economy.ShipyardRefitQueueState.Order order) {
        if (order.servicePayment() == null) return true;
        var payment = order.servicePayment();
        return coordinator.runtime().world().transferToFactionTreasury(payment.sellerFactionId(),
                new com.spacesim.components.WalletComponent(payment.reservedMilliCredits()), "PLAYER_REFIT_ESCROW:" + order.orderId(),
                payment.reservedMilliCredits(), "paid-refit-complete.v1");
    }

    private void advancePersonalRepairsAtTick(long tick) {
        var budgets = new java.util.HashMap<String, com.spacesim.economy.Stage18ShipyardRuntime.YardWorkBudget>();
        var completed = repairQueue.advance(tick, id -> coordinator.runtime().infrastructure().endpoint(id).storage(),
                order -> repairContext(order.stationId(), order), order -> {
                    var context = repairContext(order.stationId(), order);
                    return context == null ? null : budgets.computeIfAbsent(order.stationId() + '|' + order.yardInstanceId(),
                            id -> context.yard().openInterval(coordinator.session().fixedStepSeconds()));
                });
        for (var order : completed) {
            if (order.servicePayment() != null) {
                var payment = order.servicePayment();
                var held = new com.spacesim.components.WalletComponent(payment.reservedMilliCredits());
                if (!coordinator.runtime().world().transferToFactionTreasury(payment.sellerFactionId(), held,
                        "PLAYER_REPAIR_ESCROW:" + order.orderId(), payment.reservedMilliCredits(), "paid-repair-complete.v1"))
                    throw new IllegalStateException("Validated paid repair settlement failed");
                recordPersonalCommit(com.spacesim.player.PlayerJournalState.Kind.COMMAND, "REPAIR_SERVICE_SETTLED",
                        order.stationId(), payment.sellerFactionId(), payment.reservedMilliCredits(), 0, new FleetId(order.fleetId()));
            }
            recordPersonalCommit(com.spacesim.player.PlayerJournalState.Kind.REPAIR_COMPLETED,
                    "REPAIR_COMPLETED", order.stationId(), order.orderId(), 0, 0, new FleetId(order.fleetId()));
        }
        var candidates = new java.util.HashMap<String, com.spacesim.persistence.Stage20FreightPersistentState.FreighterState>();
        var refitted = refitQueue.advance(tick, id -> coordinator.runtime().infrastructure().endpoint(id).storage(), order -> {
            var context = refitContext(order.stationId(), order, order.targetFit());
            if (context == null) return null;
            return new com.spacesim.economy.ShipyardRefitWorkQueue.Context(context.ship(), context.yard(), completion -> {
                if (!context.capacityPreflight().test(completion)) return false;
                candidates.put(order.orderId(), coordinator.runtime().freight().previewPersonalRefit(
                        new FleetId(order.fleetId()), personalRefitTargetId(completion.fit())));
                return true;
            }, Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID,
                    job -> job.servicePayment() == null || refitContext(job.stationId(), job, job.targetFit()) != null,
                    this::settleRefitPayment);
        }, order -> {
            var context = refitContext(order.stationId(), order, order.targetFit());
            return context == null ? null : budgets.computeIfAbsent(order.stationId() + '|' + order.yardInstanceId(),
                    id -> context.yard().openInterval(coordinator.session().fixedStepSeconds()));
        }, moduleCustody);
        moduleCustody = refitted.custody();
        for (var order : refitted.completed()) {
            if (order.servicePayment() != null) recordPersonalCommit(com.spacesim.player.PlayerJournalState.Kind.COMMAND,
                    "REFIT_SERVICE_SETTLED", order.stationId(), order.servicePayment().sellerFactionId(),
                    order.servicePayment().reservedMilliCredits(), 0, new FleetId(order.fleetId()));
            coordinator.runtime().freight().applyPersonalRefit(candidates.get(order.orderId()));
            recordPersonalCommit(com.spacesim.player.PlayerJournalState.Kind.REFIT_COMPLETED,
                    "REFIT_COMPLETED", order.stationId(), order.orderId(), 0, 0, new FleetId(order.fleetId()));
        }
    }

    /**
     * Identifies the actual freight owner of an equipment storage node.
     * @param storageId exact physical owner identity
     * @return existing carrying fleet, if this is a real ship hold
     */
    public java.util.Optional<FleetId> moduleCarrier(String storageId) {
        return coordinator.runtime().freight().moduleCargoCarrier(storageId);
    }

    /** @return the personally owned station at the current docking reference, if any */
    public java.util.Optional<String> dockedModuleStationId() {
        if (playerState == null || !playerState.docked()) return java.util.Optional.empty();
        return coordinator.runtime().industry().industrial().stations().stream().map(s -> s.stationId())
                .filter(id -> ownsProductionStation(id) || civilianRepairOperator(id, null))
                .filter(id -> pilotMarketReference(id).filter(playerState.dockedAt()::equals).isPresent()).findFirst();
    }

    private com.spacesim.economy.Stage18StationStorage moduleStorage(String id) {
        var carrier = moduleCarrier(id);
        return carrier.isPresent() ? coordinator.runtime().freight().moduleCargoStorage(carrier.get())
                : coordinator.runtime().infrastructure().endpoint(id).storage();
    }

    private java.util.Set<String> reservedRefitModules() {
        return refitQueue.capture().orders().stream().flatMap(o -> o.reservedUsedModulesByTargetMount().values().stream())
                .map(com.spacesim.economy.ShipyardModuleCustodyState.StoredModule::custodyId).collect(java.util.stream.Collectors.toSet());
    }

    private String transferStation(com.spacesim.economy.ShipyardModuleTransferWorkQueue.Order order) {
        return moduleCarrier(order.source().stationId()).isPresent() ? order.destinationStorageId() : order.source().stationId();
    }

    private boolean handlingUsedThisTick(String stationId) {
        long tick = coordinator.runtime().world().getAuthoritativeWorldTick();
        return playerJournal.entries().stream().anyMatch(e -> e.tick() == tick && e.endpoint().equals(stationId)
                && (e.kind() == com.spacesim.player.PlayerJournalState.Kind.MODULE_TRANSFER_COMPLETED
                || java.util.Set.of("BUY", "SELL", "PRODUCT_TRANSFER_COMPLETED", "YARD_MATERIAL_DELIVERED").contains(e.action())));
    }

    private com.spacesim.economy.ShipyardModuleTransferWorkQueue.Context moduleTransferContext(String stationId, boolean loading) {
        var physical = personalYards(stationId, null, null, !ownsProductionStation(stationId) && civilianRepairOperator(stationId, null));
        if (physical == null) return null;
        var freight = coordinator.runtime().freight(); var fleet = freight.findFreighter(playerState.activeFleetId()).orElse(null);
        if (fleet == null || fleet.phase() != com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase.IDLE) return null;
        var endpoint = coordinator.runtime().infrastructure().endpoint(stationId);
        var hold = freight.moduleCargoStorage(fleet.fleetId());
        return new com.spacesim.economy.ShipyardModuleTransferWorkQueue.Context(loading ? endpoint.storage() : hold,
                loading ? hold : endpoint.storage(), endpoint.handlingCapability());
    }

    private void advanceModuleTransfersAtTick(long tick,
            java.util.Map<String, com.spacesim.economy.Stage18LogisticsRuntime.TransferBudget> budgets) {
        reconcileModuleLosses();
        for (var order : productTransfers.capture().orders()) {
            var fleet = coordinator.runtime().freight().findFreighter(order.fleetId()).orElse(null);
            if (fleet == null || !fleet.operational()) productTransfers.cancel(order.orderId(), this::productStorage);
        }
        var result = moduleTransfers.advance(tick, moduleCustody, order -> {
            String station = transferStation(order);
            if (handlingUsedThisTick(station) && !budgets.containsKey(station)) return null;
            boolean loading = !moduleCarrier(order.source().stationId()).isPresent();
            var context = moduleTransferContext(station, loading); if (context == null) return null;
            if (loading) {
                var f = coordinator.runtime().freight().findFreighter(playerState.activeFleetId()).orElseThrow();
                double kg = com.spacesim.content.Stage22CivilianMiningProductionPath.loadProducts()
                        .findProduct(order.source().condition().assignment().moduleId()).unitMassKg();
                if (f.cargoMassKg() + kg > f.cargoCapacityKg()) return null;
            }
            return context;
        }, order -> budgets.computeIfAbsent(transferStation(order), station -> coordinator.runtime().infrastructure()
                .endpoint(station).handlingCapability().openInterval(coordinator.session().fixedStepSeconds())), reservedRefitModules());
        moduleCustody = result.custody();
        if (!result.completed().isEmpty()) {
            coordinator.runtime().freight().synchronizeModuleCustody(moduleCustody);
            coordinator.runtime().synchronizeFreightEngineeringCargo(playerState.activeFleetId());
            for (var order : result.completed()) recordPersonalCommit(com.spacesim.player.PlayerJournalState.Kind.MODULE_TRANSFER_COMPLETED,
                    "MODULE_TRANSFER_COMPLETED", transferStation(order), order.source().custodyId(), 1, 0, playerState.activeFleetId());
        }
        advanceProductTransfersAtTick(tick, budgets);
    }

    private com.spacesim.economy.FinishedProductTransferWorkQueue.Context productTransferContext(String stationId, boolean loading) {
        var physical = moduleTransferContext(stationId, loading); if (physical == null) return null;
        var fleet = coordinator.runtime().freight().findFreighter(playerState.activeFleetId()).orElseThrow();
        return new com.spacesim.economy.FinishedProductTransferWorkQueue.Context(physical.source(), physical.destination(), physical.handling(),
                loading ? Math.max(0d, fleet.cargoCapacityKg() - fleet.cargoMassKg()) : Double.MAX_VALUE);
    }

    private void advanceProductTransfersAtTick(long tick,
            java.util.Map<String, com.spacesim.economy.Stage18LogisticsRuntime.TransferBudget> budgets) {
        var runtime = coordinator.runtime();
        var completed = productTransfers.advance(tick, this::productStorage, order -> {
            if (playerState == null || !order.fleetId().equals(playerState.activeFleetId())
                    || handlingUsedThisTick(order.stationId()) && !budgets.containsKey(order.stationId())) return null;
            return productTransferContext(order.stationId(), order.loading());
        }, order -> budgets.computeIfAbsent(order.stationId(), station -> runtime.infrastructure().endpoint(station)
                .handlingCapability().openInterval(coordinator.session().fixedStepSeconds())),
                permit -> runtime.freight().publishHandledPersonalProduct(permit, tick * (double) coordinator.session().fixedStepSeconds()));
        for (var order : completed) {
            runtime.synchronizeFreightEngineeringCargo(order.fleetId());
            recordPersonalCommit(com.spacesim.player.PlayerJournalState.Kind.COMMAND, "PRODUCT_TRANSFER_COMPLETED",
                    order.stationId(), order.productId(), order.count(), 0, order.fleetId());
        }
    }

    private void reconcileModuleLosses() {
        if (moduleCustody.modules().isEmpty() && moduleTransfers.capture().orders().isEmpty()) return;
        var lost = coordinator.runtime().freight().capture().freighters().stream().filter(f -> !f.operational())
                .map(f -> f.cargoStorage().stationId()).collect(java.util.stream.Collectors.toSet());
        if (lost.isEmpty()) return;
        for (var order : moduleTransfers.capture().orders()) if (lost.contains(order.source().stationId()) || lost.contains(order.destinationStorageId()))
            moduleTransfers.cancel(order.orderId());
        var lostModules = moduleCustody.modules().stream().filter(m -> lost.contains(m.stationId()))
                .map(com.spacesim.economy.ShipyardModuleCustodyState.StoredModule::custodyId).collect(java.util.stream.Collectors.toSet());
        if (!lostModules.isEmpty()) {
            moduleCustody = moduleCustody.withdraw(lostModules); coordinator.runtime().freight().synchronizeModuleCustody(moduleCustody);
        }
    }

    private void advancePersonalManufacturingAtTick(long tick,
            java.util.Map<String, com.spacesim.economy.Stage18LogisticsRuntime.TransferBudget> handlingBudgets) {
        var runtime = coordinator.runtime();
        var before = runtime.manufacturingQueue().capture().stream()
                .filter(com.spacesim.economy.Stage18ManufacturingWorkQueue::managed).toList();
        var budgets = new java.util.HashMap<String, com.spacesim.economy.Stage18ManufacturingRuntime.IntervalBudget>();
        runtime.manufacturingQueue().advance(tick, id -> runtime.infrastructure().endpoint(id).storage(), order -> {
            if (!ownsProductionStation(order.stationId())) return null;
            var capability = manufacturingCapability(order.stationId(), order.operationId());
            if (capability == null) return null;
            return budgets.computeIfAbsent(order.stationId() + '|' + capability.capabilityId(),
                    ignored -> capability.openInterval(coordinator.session().fixedStepSeconds()));
        });
        var remaining = runtime.manufacturingQueue().capture().stream().map(o -> o.orderId())
                .collect(java.util.stream.Collectors.toSet());
        for (var order : before) if (!remaining.contains(order.orderId())) recordPersonalCommit(
                com.spacesim.player.PlayerJournalState.Kind.MANUFACTURING_COMPLETED, "MANUFACTURING_COMPLETED",
                order.stationId(), order.operationId(), order.requestedUnits(), 0, null);
        advancePersonalFacilityConstructionAtTick(tick, budgets, handlingBudgets);
    }

    private void advancePersonalFacilityConstructionAtTick(long tick,
            java.util.Map<String, com.spacesim.economy.Stage18ManufacturingRuntime.IntervalBudget> manufacturingBudgets,
            java.util.Map<String, com.spacesim.economy.Stage18LogisticsRuntime.TransferBudget> handlingBudgets) {
        var runtime = coordinator.runtime();
        var work = new java.util.HashMap<String, com.spacesim.economy.Stage18FacilityConstructionRuntime.WorkBudget>();
        var completed = runtime.constructionQueue().advance(tick, id -> runtime.infrastructure().endpoint(id).storage(), order -> {
            if (!ownsProductionStation(order.stationId())) return null;
            var capability = constructionCapability(order.stationId(), order.facilityDefinitionId());
            if (capability == null) return null;
            String key = order.stationId() + '|' + capability.facilityInstanceId();
            return work.computeIfAbsent(key, ignored -> {
                var used = manufacturingBudgets.get(key);
                double seconds = used == null ? capability.effectiveEngineeringWorkRate() * coordinator.session().fixedStepSeconds()
                        : used.remainingWorkSeconds();
                return new com.spacesim.economy.Stage18FacilityConstructionRuntime.ConstructionCapability(
                        capability.facilityInstanceId(), capability.capabilityTags(), seconds).openInterval(1d);
            });
        });
        runtime.adoptCompletedFacilityConstruction(completed);
        for (var order : completed) recordPersonalCommit(com.spacesim.player.PlayerJournalState.Kind.COMMAND,
                "FACILITY_CONSTRUCTION_COMPLETED", order.stationId(), order.facilityDefinitionId(), 1, 0, null);
        var beforeYards = yardConstruction.capture();
        var completedYards = yardConstruction.advance(tick, id -> runtime.infrastructure().endpoint(id).storage(), order -> {
            if (!ownsProductionStation(order.stationId())) return null;
            var capability = yardConstructionCapability(order.stationId(), order.yardDefinitionId());
            if (capability == null) return null;
            String key = order.stationId() + '|' + capability.facilityInstanceId();
            return work.computeIfAbsent(key, ignored -> {
                var used = manufacturingBudgets.get(key);
                double seconds = used == null ? capability.effectiveEngineeringWorkRate() * coordinator.session().fixedStepSeconds()
                        : used.remainingWorkSeconds();
                return new com.spacesim.economy.Stage18FacilityConstructionRuntime.ConstructionCapability(
                        capability.facilityInstanceId(), capability.capabilityTags(), seconds).openInterval(1d);
            });
        }, order -> {
            if (!ownsProductionStation(order.stationId())
                    || handlingUsedThisTick(order.stationId()) && !handlingBudgets.containsKey(order.stationId())) return null;
            var endpoint = runtime.infrastructure().endpoint(order.stationId());
            return new com.spacesim.economy.Stage23YardConstructionWorkQueue.MaterialDeliveryContext(endpoint.handlingCapability(),
                    handlingBudgets.computeIfAbsent(order.stationId(), ignored -> endpoint.handlingCapability()
                            .openInterval(coordinator.session().fixedStepSeconds())));
        });
        for (var order : yardConstruction.capture().orders()) {
            var previous = beforeYards.orders().stream().filter(o -> o.orderId().equals(order.orderId())).findFirst().orElseThrow();
            double moved = order.deliveredMassByCommodityKg().values().stream().mapToDouble(Double::doubleValue).sum()
                    - previous.deliveredMassByCommodityKg().values().stream().mapToDouble(Double::doubleValue).sum();
            if (moved > 0) recordPersonalCommit(com.spacesim.player.PlayerJournalState.Kind.COMMAND,
                    "YARD_MATERIAL_DELIVERED", order.stationId(), order.yardDefinitionId(), Math.round(moved), 0, null);
        }
        runtime.adoptCompletedYardConstruction(completedYards, yardConstruction);
        for (var order : completedYards) recordPersonalCommit(com.spacesim.player.PlayerJournalState.Kind.COMMAND,
                "YARD_CONSTRUCTION_COMPLETED", order.stationId(), order.yardDefinitionId(), 1, 0, null);
    }

    private void reconcileOnePlayerMission(String id, com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState runtimeCheckpoint) {
        var service = coordinator.npcMissionService();
        var discovery = runtimeCheckpoint.campaign().discoveryState();
        MissionContract mission = service.snapshot().missions().stream()
                .filter(value -> value.missionId().equals(id)).findFirst().orElseThrow();
        long beforeWallet = playerState.walletMilliCredits();
        // Transaction-local adapter to the existing service's exact escrow-transfer API.
        // Only PlayerState survives the call; this is not an additional persistent wallet.
        WalletComponent recipient = new WalletComponent(playerState.walletMilliCredits());
        service.reconcilePlayerMission(coordinator.runtime().world(), runtimeCheckpoint.freight(),
                runtimeCheckpoint.campaign().industrialState(), discovery.knowledgeFor(mission.issuerFactionId()),
                discovery.knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID),
                coordinator.operations(), playerState, id, recipient);
        if (recipient.getBalanceMilliCredits() != playerState.walletMilliCredits()) {
            playerState = new PlayerState(recipient.getBalanceMilliCredits(), playerState.factionContentId(),
                    playerState.reputations(), playerState.ownedFleetIds(), playerState.activeFleetId(),
                    playerState.discoveredSystemIds(), playerState.discoveredObjects(), playerState.homeSystemId(),
                    playerState.dockedAt(), playerState.fleetOrders(), playerState.threatIntel(),
                    playerState.ownedConstructionProjectIds(), playerState.ownedStations());
        }
        var after = service.snapshot().missions().stream().filter(value -> value.missionId().equals(id)).findFirst().orElseThrow();
        if (!mission.status().equals(after.status())) recordPersonalCommit(
                com.spacesim.player.PlayerJournalState.Kind.MISSION_CHANGED, after.status().name(),
                mission.issuerFactionId(), id, 0, playerState.walletMilliCredits() - beforeWallet, null);
    }

    private static void validateOperations(
            SmallCraftRegistry craft,
            SmallCraftHangarRegistry hangars,
            SmallCraftFlightDeckOperations flightDeck,
            SmallCraftMissionState missions,
            LogisticsState logistics,
            Collection<CarrierWingAssignment> wings,
            long authoritativeTick) {
        Objects.requireNonNull(craft, "craft");
        Objects.requireNonNull(hangars, "hangars");
        SmallCraftFlightDeckOperations checkedDeck =
                Objects.requireNonNull(flightDeck, "flightDeck");
        SmallCraftMissionState checkedMissions =
                Objects.requireNonNull(missions, "missions");
        LogisticsState checkedLogistics = Objects.requireNonNull(logistics, "logistics");
        Objects.requireNonNull(wings, "wings");
        if (authoritativeTick < 0L) {
            throw new IllegalArgumentException("authoritativeTick cannot be negative");
        }
        for (var active : checkedDeck.active()) {
            if (active.request().requestedTick() > authoritativeTick) {
                throw new IllegalArgumentException(
                        "active flight-deck request is future-dated: "
                                + active.request().craftId());
            }
        }

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
            if (mission.submittedTick() > authoritativeTick) {
                throw new IllegalArgumentException(
                        "mission is future-dated relative to authoritative world tick: "
                                + mission.id());
            }
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
