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
    private boolean freshPilotStart;
    private float thrustAxisX;
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
        SmallCraftFitAuthority fitAuthority = productionFitAuthority();
        SmallCraftRegistry smallCraft = SmallCraftRegistry.empty(fitAuthority);
        SmallCraftHangarRegistry hangars = SmallCraftHangarRegistry.empty(smallCraft);
        Stage228CampaignAuthority created = new Stage228CampaignAuthority(
                GeneratedCampaignCoordinator.create(rootSeed),
                smallCraft,
                hangars,
                new SmallCraftFlightDeckOperations(
                        hangars,
                        Objects.requireNonNull(flightDeckProfiles, "flightDeckProfiles")),
                SmallCraftMissionState.empty(),
                LogisticsState.empty(),
                List.of(),
                null);
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
        return new Stage228CampaignAuthority(
                coordinator,
                smallCraft,
                hangars,
                flightDeck,
                operations.missions(),
                operations.logistics(),
                operations.carrierWings(),
                saved.playerState());
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
                        missions, logistics, carrierWings),
                playerState);
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
        initializePilot(checked.fleet, checked.seller);
        freshPilotStart = false;
        return playerState;
    }

    private void initializePilot(FleetId fleetId, String seller) {
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
        initializePilotMarkets(placement.systemId());
    }

    /** Stable binding of an ordinary station wallet to its existing physical storage endpoint. */
    public static final String PILOT_MARKET_IDENTITY_PREFIX = "Generated market ";
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
        p.used = true;
        return adopted;
    }

    private void initializePilotMarkets(com.spacesim.world.StarSystemId system) {
        var runtime = coordinator.runtime();
        var session = runtime.world().findSession(system).orElseThrow();
        for (var endpoint : runtime.infrastructure().endpoints()) {
            if (!endpoint.systemId().equals(system)) continue;
            var transform = new com.spacesim.components.TransformComponent();
            transform.position.set((float) endpoint.position().offsetXM(), (float) endpoint.position().offsetYM());
            var entity = new com.badlogic.ashley.core.Entity()
                    .add(new com.spacesim.components.IdentityComponent(PILOT_MARKET_IDENTITY_PREFIX + endpoint.stationId(),
                            com.spacesim.components.IdentityComponent.Kind.STATION))
                    .add(transform).add(new com.spacesim.components.MarketComponent())
                    .add(new WalletComponent());
            runtime.world().controllingFaction(system).ifPresent(f -> entity.add(new com.spacesim.components.FactionComponent(
                    runtime.world().findFactionRuntimeId(f).orElseThrow())));
            runtime.world().createEntity(system, entity);
            if (!entity.getComponent(WalletComponent.class).creditFromSource(PILOT_MARKET_INITIAL_LIQUIDITY))
                throw new IllegalStateException("Opening market liquidity cannot be credited");
            session.getLedger().recordMoneySource(PILOT_MARKET_IDENTITY_PREFIX + endpoint.stationId(),
                    PILOT_MARKET_INITIAL_LIQUIDITY, "new-game-market-working-capital.v1");
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
            if (name != null && name.name.equals(PILOT_MARKET_IDENTITY_PREFIX + stationId)) {
                var id = entity.getComponent(com.spacesim.components.EntityIdComponent.class);
                if (id != null) return Optional.of(new com.spacesim.player.DiscoveredObjectRef(endpoint.systemId(), id.id));
            }
        }
        return Optional.empty();
    }

    /**
     * Existing ordinary-state preview used for docking and physical cargo trades.
     * @param action DOCK, UNDOCK, BUY, SELL, JUMP, PURCHASE, SWITCH, CAPITALIZE or WITHDRAW
     * @param stationId endpoint, destination for JUMP, fleet for PURCHASE/SWITCH, or milli-credits for CAPITALIZE/WITHDRAW; ignored by UNDOCK
     * @param commodityId physical commodity for a trade
     * @param kilograms positive whole kilograms for a trade
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
        public boolean allowed() { return allowed; }
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
        if (p.owner != this || !p.allowed || !p.baseline.equals(captureState()))
            throw new IllegalStateException("Physical action is stale or unauthorized");
        executePilotAction(p.action, p.station, p.commodity, p.kilograms);
        return playerState;
    }

    private void replacePilotFinancialDocking(long wallet, com.spacesim.player.DiscoveredObjectRef dock,
            List<com.spacesim.player.DiscoveredObjectRef> discovered) {
        var p = playerState;
        playerState = new PlayerState(wallet, p.factionContentId(), p.reputations(), p.ownedFleetIds(), p.activeFleetId(),
                p.discoveredSystemIds(), discovered, p.homeSystemId(), dock, p.fleetOrders(), p.threatIntel(),
                p.ownedConstructionProjectIds(), p.ownedStations());
    }

    private void executePilotAction(String action, String stationId, String commodity, int kg) {
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
        var fleet = world.findFleet(playerState.activeFleetId()).orElseThrow();
        if (fleet.locationKind() != com.spacesim.world.FleetLocationKind.IN_SYSTEM
                || world.findFleetJump(fleet.fleetId()).isPresent()) throw new IllegalStateException("Ship in transit");
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
            replacePilotFinancialDocking(playerState.walletMilliCredits(), ref, discovered); return;
        }
        boolean buying = "BUY".equals(action);
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
        long unitPrice = pilotCommodityPrice(commodity, buying);
        var ontology = com.spacesim.content.Stage18ResourceOntologyLoader.loadDefault();
        var definition = ontology.findCommodity(commodity);
        var hold = runtime.freight().cargoHoldSnapshot(fleet.fleetId());
        if (definition == null || !hold.capacityByStorageClassKg().containsKey(definition.storageClassId())
                || !endpoint.handlingCapability().supportedStorageClassIds().contains(definition.storageClassId()))
            throw new IllegalStateException("Cargo interface incompatible");
        var handling = endpoint.handlingCapability();
        if (!world.createTradeController(session).settlePhysicalCargo(station, proxy,
                buying ? com.spacesim.controllers.TradeTransactionPolicy.Direction.BUY_FROM_STATION
                        : com.spacesim.controllers.TradeTransactionPolicy.Direction.SELL_TO_STATION,
                Math.multiplyExact(unitPrice, kg), handlingReason + commodity + ":kg=" + kg,
                () -> runtime.freight().exchangeManualCommodity(fleet.fleetId(), endpoint.storage(), commodity, kg,
                        buying, tick * coordinator.session().fixedStepSeconds(), handling,
                        handling.openInterval(coordinator.session().fixedStepSeconds()))))
            throw new IllegalStateException("Cargo, wallet, access or handling budget rejected trade");
        runtime.synchronizeFreightEngineeringCargo(fleet.fleetId());
        replacePilotFinancialDocking(proxy.getComponent(WalletComponent.class).getBalanceMilliCredits(), ref, playerState.discoveredObjects());
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
            case "commodity.ore.metallic" -> 10000L;
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

    private void advancePilotAtTick(long tick) {
        if (playerState == null || playerState.activeFleetId() == null || playerState.docked()) return;
        var runtime = coordinator.runtime();
        var placement = runtime.world().findFleet(playerState.activeFleetId()).orElse(null);
        if (placement == null || placement.locationKind() != com.spacesim.world.FleetLocationKind.IN_SYSTEM
                || runtime.world().findFleetJump(playerState.activeFleetId()).isPresent()) return;
        var entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
        // Legacy checkpoint fixtures may have a non-fitted ship. They get no physical-control grant.
        if (entity.getComponent(com.spacesim.components.EngineeringComponent.class) == null) return;
        var materialization = runtime.arrival().materialization(placement.systemId());
        var physical = materialization.physicalState(placement.localEntityId()).orElseThrow();
        materialization.updatePhysicalState(placement.localEntityId(), playerFlight.advanceExact(entity, physical,
                thrustAxisX, thrustAxisY, braking, coordinator.session().fixedStepSeconds()));
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
            advancePilotAtTick(tick);
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
            advancePilotAtTick(tick);
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
        private final boolean allowed;
        private final String reasonCode;

        private MissionCommandPreview(Stage228CampaignAuthority owner,
                Stage228GeneratedCampaignPersistentState baseline, PlayerCommand command,
                String missionId, boolean allowed, String reasonCode) {
            this.owner = owner;
            this.baseline = baseline;
            this.command = command;
            this.missionId = missionId;
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
        Objects.requireNonNull(command, "command");
        String id = Objects.requireNonNull(missionId, "missionId").strip();
        if (id.isEmpty()) throw new IllegalArgumentException("missionId is empty");
        Stage228GeneratedCampaignPersistentState baseline = captureState();
        if (playerState == null) {
            return new MissionCommandPreview(this, baseline, command, id, false, "player.uninitialized");
        }
        try {
            // Reuse every domain validator, including escrow/treasury capacity, on a candidate.
            // Candidate changes are discarded; live submission calls this same command function.
            Stage228CampaignAuthority candidate = restore(baseline);
            candidate.executePlayerMissionCommand(command, id);
            return new MissionCommandPreview(this, baseline, command, id, true, "command.allowed");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return new MissionCommandPreview(this, baseline, command, id, false, "mission.unavailable");
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
        return executePlayerMissionCommand(checked.command, checked.missionId);
    }

    private MissionContract executePlayerMissionCommand(PlayerCommand command, String missionId) {
        if (playerState == null) throw new IllegalStateException("Player is not initialized");
        var service = coordinator.npcMissionService();
        long tick = coordinator.runtime().world().getAuthoritativeWorldTick();
        MissionContract current = service.validatePlayerCommand(command, missionId, tick);
        var issuer = service.snapshot().npcs().stream()
                .filter(npc -> npc.npcId().equals(current.issuerNpcId())).findFirst().orElseThrow();
        // Existing accepted contracts belong to the single human contractor. New offers require
        // that contractor's own discovered posting; the knowledge viewer never grants permission.
        if (current.status() == com.spacesim.world.Stage21HNpcMissionState.MissionStatus.OFFERED
                && !playerState.discoveredSystemIds().contains(issuer.locationSystemId())) {
            throw new IllegalStateException("Player has not discovered this mission issuer's posting");
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
        var discovery = runtimeCheckpoint.campaign().discoveryState();
        for (String id : due) {
            MissionContract mission = service.snapshot().missions().stream()
                    .filter(value -> value.missionId().equals(id)).findFirst().orElseThrow();
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
        }
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
