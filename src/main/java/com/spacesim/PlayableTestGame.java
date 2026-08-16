package com.spacesim;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.spacesim.components.CombatCommandComponent;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.CombatRuntimeComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.Money;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.PlayableWorldStateCodec;
import com.spacesim.player.DiscoveredObjectRef;
import com.spacesim.player.PlayableTestWorldFactory;
import com.spacesim.player.PlayableWorldState;
import com.spacesim.player.PlayerJumpNavigationModel;
import com.spacesim.player.PlayerMarketItemView;
import com.spacesim.player.PlayerMarketService;
import com.spacesim.player.PlayerMarketView;
import com.spacesim.player.PlayerMiningService;
import com.spacesim.player.PlayerMiningView;
import com.spacesim.player.PlayerRuntime;
import com.spacesim.player.PlayerShipProgressionService;
import com.spacesim.player.PlayerShipView;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.ui.GalaxyStrategicMapModel;
import com.spacesim.ui.GalaxyStrategicMapRenderer;
import com.spacesim.ui.GalaxyStrategicMapSnapshot;
import com.spacesim.ui.LocalMinimapModel;
import com.spacesim.ui.LocalMinimapRenderer;
import com.spacesim.ui.LocalMinimapSnapshot;
import com.spacesim.ui.PlayableCameraState;
import com.spacesim.ui.PlayableMapEntityFilter;
import com.spacesim.ui.WorldMapLayout;
import com.spacesim.ui.WorldMapRenderer;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import com.spacesim.world.WorldFactionIdentityState;
import com.spacesim.world.WorldSimulation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Manually playable desktop test harness for the current production simulation.
 *
 * <p>The application remains intentionally thin: keyboard/mouse input is translated into ordinary
 * {@link PlayerRuntime}, market, mining, jump and ownership/progression commands. World-space
 * rendering, HUD, minimap and the global strategic overlay read authoritative state only.</p>
 */
public final class PlayableTestGame extends ApplicationAdapter {
    private static final float MAP_PADDING = 18f;
    private static final float TRANSIT_ZOOM = 1f;
    private static final float HUD_UPDATE_INTERVAL_SECONDS = 0.1f;
    private static final float SHIP_PANEL_WIDTH = 470f;
    private static final float CONTEXT_PANEL_WIDTH = 650f;
    private static final float MINIMAP_WIDTH = 310f;
    private static final float MINIMAP_HEIGHT = 220f;
    private static final float MINIMAP_MARGIN = 16f;
    private static final float MINIMAP_PADDING = 8f;
    private static final float MINIMAP_ZOOM = 2.2f;
    private static final String SAVE_FILE = "saves/playable-test-world.sav";

    private PlayableTestWorldFactory.Scenario scenario;
    private ContentCatalog content;
    private PlayerRuntime playerRuntime;
    private PlayerMarketService marketService;
    private PlayerMiningService miningService;
    private PlayerShipProgressionService progressionService;
    private WorldSimulation world;
    private SimulationSession activeSession;
    private StarSystemId boundSystemId;

    private Stage stage;
    private Label shipHudLabel;
    private Label contextHudLabel;
    private Label statusHudLabel;
    private WorldMapRenderer worldMapRenderer;
    private LocalMinimapRenderer minimapRenderer;
    private GalaxyStrategicMapRenderer galaxyMapRenderer;
    private WorldMapLayout mapLayout;
    private WorldMapLayout minimapLayout;
    private final PlayableCameraState cameraState = new PlayableCameraState();
    private Path savePath;

    private boolean moveUp;
    private boolean moveDown;
    private boolean moveLeft;
    private boolean moveRight;
    private boolean galaxyMapVisible;
    private int selectedMarketIndex;
    private int selectedJumpNeighborIndex;
    private DiscoveredObjectRef lastDockedAt;
    private float hudAccumulator;
    private String statusMessage = "Fresh curated world loaded.";

    public PlayableTestGame() {
    }

    @Override
    public void create() {
        VisUI.load();
        resetScenario();

        stage = new Stage(new ScreenViewport());
        Skin skin = VisUI.getSkin();
        worldMapRenderer = new WorldMapRenderer(skin.get(Label.LabelStyle.class).font);
        minimapRenderer = new LocalMinimapRenderer(skin.get(Label.LabelStyle.class).font);
        galaxyMapRenderer = new GalaxyStrategicMapRenderer(skin.get(Label.LabelStyle.class).font);
        createHud(skin);

        savePath = Gdx.files.local(SAVE_FILE).file().toPath();
        Gdx.input.setInputProcessor(new InputMultiplexer(createInputAdapter(), stage));
        updateMapLayouts();
        updateHud();
        Gdx.gl.glClearColor(0.018f, 0.025f, 0.045f, 1f);
    }

    private void createHud(Skin skin) {
        shipHudLabel = new Label("", skin);
        shipHudLabel.setAlignment(Align.topLeft);
        shipHudLabel.setWrap(true);
        shipHudLabel.setTouchable(Touchable.disabled);

        contextHudLabel = new Label("", skin);
        contextHudLabel.setAlignment(Align.bottomLeft);
        contextHudLabel.setWrap(true);
        contextHudLabel.setTouchable(Touchable.disabled);

        statusHudLabel = new Label("", skin);
        statusHudLabel.setAlignment(Align.center);
        statusHudLabel.setWrap(true);
        statusHudLabel.setTouchable(Touchable.disabled);

        Table root = new Table();
        root.setFillParent(true);
        root.top().left().pad(12f);
        root.add(shipHudLabel).width(SHIP_PANEL_WIDTH).top().left();
        root.row();
        root.add().expandY();
        root.row();
        root.add(contextHudLabel).width(CONTEXT_PANEL_WIDTH).bottom().left();
        root.setTouchable(Touchable.disabled);
        stage.addActor(root);

        Table status = new Table();
        status.setFillParent(true);
        status.bottom().padBottom(10f);
        status.add(statusHudLabel).width(760f).center();
        status.setTouchable(Touchable.disabled);
        stage.addActor(status);
    }

    private void resetScenario() {
        scenario = PlayableTestWorldFactory.create(PlayableTestWorldFactory.DEFAULT_TEST_SEED);
        content = scenario.content();
        playerRuntime = scenario.runtime();
        rebindPlayerServices();
        world = playerRuntime.world();
        bindActiveSession();
        clearMovementKeys();
        galaxyMapVisible = false;
        selectedMarketIndex = 0;
        lastDockedAt = null;
        cameraState.reset();
    }

    private void rebindPlayerServices() {
        marketService = new PlayerMarketService(playerRuntime, content);
        miningService = new PlayerMiningService(playerRuntime);
        progressionService = new PlayerShipProgressionService(playerRuntime);
    }

    private void bindActiveSession() {
        world = playerRuntime.world();
        boundSystemId = world.getActiveSystemId();
        activeSession = world.findSession(boundSystemId).orElseThrow(
                () -> new IllegalStateException("Active test StarSystem has no SimulationSession"));
        selectedJumpNeighborIndex = 0;
    }

    private InputAdapter createInputAdapter() {
        return new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.G) {
                    toggleGalaxyMap();
                    return true;
                }
                if (galaxyMapVisible) {
                    if (keycode == Input.Keys.ESCAPE) {
                        toggleGalaxyMap();
                    }
                    return true;
                }
                switch (keycode) {
                    case Input.Keys.W -> moveUp = true;
                    case Input.Keys.S -> moveDown = true;
                    case Input.Keys.A -> moveLeft = true;
                    case Input.Keys.D -> moveRight = true;
                    case Input.Keys.E -> toggleDocking();
                    case Input.Keys.K -> cycleJumpDestination(1);
                    case Input.Keys.J -> jumpToSelectedNeighbor();
                    case Input.Keys.B -> tradeSelected(true);
                    case Input.Keys.V -> tradeSelected(false);
                    case Input.Keys.M -> selectNearestAsteroid();
                    case Input.Keys.R -> toggleMining();
                    case Input.Keys.T -> selectNearestCombatTarget();
                    case Input.Keys.F -> toggleFire();
                    case Input.Keys.TAB -> switchOwnedShip();
                    case Input.Keys.UP, Input.Keys.LEFT_BRACKET -> cycleMarketItem(-1);
                    case Input.Keys.DOWN, Input.Keys.RIGHT_BRACKET -> cycleMarketItem(1);
                    case Input.Keys.SPACE -> togglePause();
                    case Input.Keys.NUM_1 -> setTimeScale(1d);
                    case Input.Keys.NUM_2 -> setTimeScale(2d);
                    case Input.Keys.NUM_3 -> setTimeScale(4d);
                    case Input.Keys.NUM_4 -> setTimeScale(8d);
                    case Input.Keys.F5 -> saveGame();
                    case Input.Keys.F9 -> loadGame();
                    case Input.Keys.F2 -> resetWorldFromInput();
                    case Input.Keys.HOME -> {
                        cameraState.reset();
                        statusMessage = "Camera zoom reset.";
                    }
                    default -> {
                        return false;
                    }
                }
                applyMovementIntent();
                return true;
            }

            @Override
            public boolean keyUp(int keycode) {
                switch (keycode) {
                    case Input.Keys.W -> moveUp = false;
                    case Input.Keys.S -> moveDown = false;
                    case Input.Keys.A -> moveLeft = false;
                    case Input.Keys.D -> moveRight = false;
                    default -> {
                        return false;
                    }
                }
                if (!galaxyMapVisible) {
                    applyMovementIntent();
                }
                return true;
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                if (galaxyMapVisible) {
                    return true;
                }
                cameraState.scroll(amountY);
                statusMessage = String.format(Locale.ROOT, "Camera zoom %.2fx.", cameraState.zoom());
                return true;
            }
        };
    }

    private void toggleGalaxyMap() {
        galaxyMapVisible = !galaxyMapVisible;
        clearMovementKeys();
        statusMessage = galaxyMapVisible
                ? "Global strategic map opened. Gameplay commands are blocked until it is closed."
                : "Global strategic map closed.";
    }

    private void applyMovementIntent() {
        float x = (moveRight ? 1f : 0f) - (moveLeft ? 1f : 0f);
        float y = (moveUp ? 1f : 0f) - (moveDown ? 1f : 0f);
        if (x == 0f && y == 0f) {
            playerRuntime.stopMovement();
        } else {
            playerRuntime.setMovementIntent(x, y);
        }
    }

    private void clearMovementKeys() {
        moveUp = false;
        moveDown = false;
        moveLeft = false;
        moveRight = false;
        if (playerRuntime != null) {
            playerRuntime.stopMovement();
        }
    }

    private void toggleDocking() {
        if (playerRuntime.player().docked()) {
            if (playerRuntime.undock()) {
                statusMessage = "Undocked.";
                lastDockedAt = null;
            }
            return;
        }
        MarketTarget target = nearestMarket();
        if (target == null) {
            statusMessage = "No local market found.";
            return;
        }
        if (playerRuntime.dockAt(target.entityId())) {
            clearMovementKeys();
            miningService.clear();
            statusMessage = "Docked at " + target.name() + ".";
            lastDockedAt = null;
        } else {
            statusMessage = String.format(Locale.ROOT,
                    "Docking rejected: %s is %.1f units away; move inside docking range.",
                    target.name(), target.distance());
        }
    }

    private void cycleJumpDestination(int delta) {
        PlayerShipView ship = playerRuntime.activeShipView().orElse(null);
        if (ship == null) {
            statusMessage = "Jump destination cannot be selected while the active ship is in transit.";
            return;
        }
        List<StarSystemId> neighbors = PlayerJumpNavigationModel.neighbors(world.getTopology(), ship.systemId());
        if (neighbors.isEmpty()) {
            selectedJumpNeighborIndex = 0;
            statusMessage = "Current system has no direct jump neighbors.";
            return;
        }
        int normalized = PlayerJumpNavigationModel.normalizeSelectionIndex(
                world.getTopology(), ship.systemId(), selectedJumpNeighborIndex);
        selectedJumpNeighborIndex = Math.floorMod(normalized + delta, neighbors.size());
        StarSystemId destination = PlayerJumpNavigationModel.selectedDestination(
                world.getTopology(), ship.systemId(), selectedJumpNeighborIndex);
        statusMessage = "Selected direct jump: #" + destination.value() + " " + systemName(destination) + ".";
    }

    private StarSystemId selectedJumpDestination() {
        PlayerShipView ship = playerRuntime.activeShipView().orElse(null);
        if (ship == null) {
            return null;
        }
        selectedJumpNeighborIndex = PlayerJumpNavigationModel.normalizeSelectionIndex(
                world.getTopology(), ship.systemId(), selectedJumpNeighborIndex);
        return PlayerJumpNavigationModel.selectedDestination(
                world.getTopology(), ship.systemId(), selectedJumpNeighborIndex);
    }

    private void jumpToSelectedNeighbor() {
        PlayerShipView ship = playerRuntime.activeShipView().orElse(null);
        if (ship == null) {
            FleetId activeFleet = playerRuntime.player().activeFleetId();
            statusMessage = activeFleet != null && world.findFleetJump(activeFleet).isPresent()
                    ? "Jump already in progress." : "Active ship is not locally materialized.";
            return;
        }
        if (playerRuntime.player().docked()) {
            statusMessage = "Undock before jumping.";
            return;
        }
        StarSystemId destination = selectedJumpDestination();
        if (destination == null) {
            statusMessage = "Current system has no direct jump connection.";
            return;
        }
        clearMovementKeys();
        miningService.clear();
        if (playerRuntime.requestJump(destination)) {
            statusMessage = "Jump to #" + destination.value() + " " + systemName(destination)
                    + " accepted by the authoritative neighbor-only transit FSM.";
        } else {
            statusMessage = "Jump rejected by live travel/topology rules.";
        }
    }

    private void cycleMarketItem(int delta) {
        PlayerMarketView view = marketService.view().orElse(null);
        if (view == null) {
            statusMessage = "Dock at a market before selecting goods.";
            return;
        }
        List<PlayerMarketItemView> rows = tradableRows(view);
        if (rows.isEmpty()) {
            statusMessage = "This market has no tradable items.";
            return;
        }
        selectedMarketIndex = Math.floorMod(selectedMarketIndex + delta, rows.size());
        statusMessage = "Selected " + rows.get(selectedMarketIndex).displayName() + ".";
    }

    private void tradeSelected(boolean buy) {
        PlayerMarketView view = marketService.view().orElse(null);
        if (view == null) {
            statusMessage = "Trade rejected: dock at a market first.";
            return;
        }
        synchronizeDockedSelection(view);
        List<PlayerMarketItemView> rows = tradableRows(view);
        if (rows.isEmpty()) {
            statusMessage = "Trade rejected: this market has no tradable goods.";
            return;
        }
        PlayerMarketItemView item = rows.get(Math.min(selectedMarketIndex, rows.size() - 1));
        boolean success = buy
                ? marketService.buy(item.itemContentId(), 1)
                : marketService.sell(item.itemContentId(), 1);
        statusMessage = success
                ? (buy ? "Bought " : "Sold ") + "1 " + item.displayName() + "."
                : (buy ? "Purchase" : "Sale") + " rejected by live price/wallet/cargo/access rules.";
    }

    private void selectNearestAsteroid() {
        Entity asteroid = nearestEntity(IdentityComponent.Kind.ASTEROID, false);
        EntityIdComponent id = asteroid == null ? null : asteroid.getComponent(EntityIdComponent.class);
        IdentityComponent identity = asteroid == null ? null : asteroid.getComponent(IdentityComponent.class);
        if (id == null || !miningService.selectTarget(id.id)) {
            statusMessage = "Mining target unavailable or active ship lacks compatible mining equipment.";
            return;
        }
        statusMessage = "Mining target selected: " + (identity == null ? id.id : identity.name) + ".";
    }

    private void toggleMining() {
        PlayerMiningView view = miningService.view().orElse(null);
        if (view == null) {
            statusMessage = "Mining unavailable while the active fleet is not locally materialized.";
            return;
        }
        boolean requested = !view.miningRequested();
        if (miningService.setMiningRequested(requested)) {
            statusMessage = requested ? "Mining requested." : "Mining stopped.";
        } else {
            PlayerMiningView refreshed = miningService.view().orElse(view);
            statusMessage = "Mining request rejected: " + refreshed.status().getDisplayName() + ".";
        }
    }

    private void selectNearestCombatTarget() {
        Entity target = nearestEntity(IdentityComponent.Kind.FLEET, true);
        EntityIdComponent id = target == null ? null : target.getComponent(EntityIdComponent.class);
        IdentityComponent identity = target == null ? null : target.getComponent(IdentityComponent.class);
        if (id == null || !playerRuntime.selectCombatTarget(id.id)) {
            statusMessage = "No valid hostile combat target found for the active ship.";
            return;
        }
        statusMessage = "Combat target selected: " + (identity == null ? id.id : identity.name) + ".";
    }

    private void toggleFire() {
        Entity ship = activeShipEntity();
        CombatCommandComponent command = ship == null ? null : ship.getComponent(CombatCommandComponent.class);
        boolean requested = command == null || !command.fireRequested;
        if (playerRuntime.setFireIntent(requested)) {
            statusMessage = requested ? "Fire requested." : "Fire stopped.";
        } else {
            statusMessage = "Fire request rejected: select a valid target and remain undocked.";
        }
    }

    private void switchOwnedShip() {
        if (playerRuntime.player().docked()) {
            statusMessage = "Undock before switching direct control.";
            return;
        }
        FleetId active = playerRuntime.player().activeFleetId();
        List<FleetId> owned = playerRuntime.player().ownedFleetIds();
        if (active == null || owned.size() < 2) {
            statusMessage = "No second owned ship is available for direct-control switching.";
            return;
        }
        int start = Math.max(0, owned.indexOf(active));
        for (int offset = 1; offset <= owned.size(); offset++) {
            FleetId candidate = owned.get((start + offset) % owned.size());
            FleetPlacementState placement = world.findFleet(candidate).orElse(null);
            if (placement == null
                    || placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || !boundSystemId.equals(placement.systemId())) {
                continue;
            }
            clearMovementKeys();
            if (progressionService.switchActiveFleet(candidate)) {
                statusMessage = "Direct control switched to Fleet #" + candidate.value() + ".";
                return;
            }
        }
        statusMessage = "Ship switch rejected: another owned local ship is not currently switchable.";
    }

    private void togglePause() {
        boolean paused = !playerRuntime.isPaused();
        playerRuntime.setPaused(paused);
        statusMessage = paused ? "Simulation paused." : "Simulation resumed.";
    }

    private void setTimeScale(double scale) {
        playerRuntime.setTimeScale(scale);
        statusMessage = String.format(Locale.ROOT, "Time scale set to x%.0f.", scale);
    }

    private void saveGame() {
        try {
            PlayableWorldStateCodec.write(savePath, playerRuntime.snapshot());
            statusMessage = "Saved atomically to " + SAVE_FILE + ".";
        } catch (IOException | RuntimeException exception) {
            statusMessage = "Save failed: " + safeMessage(exception);
        }
    }

    private void loadGame() {
        try {
            PlayableWorldState state = PlayableWorldStateCodec.read(savePath);
            if (state.playerState() == null) {
                throw new IllegalStateException("Save contains no initialized player");
            }
            StarSystemId restoreSystem = restoreSystemFor(state);
            playerRuntime = PlayerRuntime.restore(state, content, restoreSystem);
            rebindPlayerServices();
            world = playerRuntime.world();
            bindActiveSession();
            clearMovementKeys();
            selectedMarketIndex = 0;
            lastDockedAt = null;
            statusMessage = "Save loaded: FleetId, cargo, ownership, wallet and transit state restored.";
        } catch (IOException | RuntimeException exception) {
            statusMessage = "Load failed: " + safeMessage(exception);
        }
    }

    private StarSystemId restoreSystemFor(PlayableWorldState state) {
        FleetId activeFleet = state.playerState().activeFleetId();
        if (activeFleet != null) {
            for (FleetPlacementState placement : state.worldState().fleets()) {
                if (activeFleet.equals(placement.id())
                        && placement.locationKind() == FleetLocationKind.IN_SYSTEM) {
                    return placement.systemId();
                }
            }
        }
        return scenario.route().sourceSystem();
    }

    private void resetWorldFromInput() {
        resetScenario();
        statusMessage = "Fresh curated world reset. Existing save file was not deleted.";
        updateMapLayouts();
    }

    private MarketTarget nearestMarket() {
        PlayerShipView ship = playerRuntime.activeShipView().orElse(null);
        if (ship == null) {
            return null;
        }
        SimulationSession session = world.findSession(ship.systemId()).orElse(null);
        if (session == null) {
            return null;
        }
        MarketTarget best = null;
        for (Entity entity : session.getEngine().getEntities()) {
            MarketComponent market = entity.getComponent(MarketComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            EntityIdComponent entityId = entity.getComponent(EntityIdComponent.class);
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            if (market == null || transform == null || entityId == null || identity == null) {
                continue;
            }
            float distance = distance(ship.x(), ship.y(), transform.position.x, transform.position.y);
            if (best == null || distance < best.distance()) {
                best = new MarketTarget(entityId.id, identity.name, distance);
            }
        }
        return best;
    }

    private Entity nearestEntity(IdentityComponent.Kind requiredKind, boolean hostileCombatOnly) {
        PlayerShipView shipView = playerRuntime.activeShipView().orElse(null);
        Entity playerEntity = activeShipEntity();
        if (shipView == null || playerEntity == null) {
            return null;
        }
        FactionComponent playerFaction = playerEntity.getComponent(FactionComponent.class);
        Set<EntityId> ownedLocal = ownedLocalEntityIds();
        Entity best = null;
        float bestDistance = Float.POSITIVE_INFINITY;
        for (Entity entity : activeSession.getEngine().getEntities()) {
            if (entity == playerEntity) {
                continue;
            }
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            EntityIdComponent id = entity.getComponent(EntityIdComponent.class);
            if (identity == null || transform == null || id == null || identity.kind != requiredKind) {
                continue;
            }
            if (hostileCombatOnly) {
                FactionComponent faction = entity.getComponent(FactionComponent.class);
                if (ownedLocal.contains(id.id)
                        || entity.getComponent(CombatComponent.class) == null
                        || playerFaction == null
                        || faction == null
                        || faction.factionId == playerFaction.factionId) {
                    continue;
                }
            }
            float currentDistance = distance(
                    shipView.x(), shipView.y(), transform.position.x, transform.position.y);
            if (currentDistance < bestDistance
                    || currentDistance == bestDistance && lowerEntityId(entity, best)) {
                best = entity;
                bestDistance = currentDistance;
            }
        }
        return best;
    }

    private Set<EntityId> ownedLocalEntityIds() {
        Set<FleetId> owned = Set.copyOf(playerRuntime.player().ownedFleetIds());
        Set<EntityId> localIds = new HashSet<>();
        for (FleetPlacementState placement : world.getFleetPlacements()) {
            if (placement.locationKind() == FleetLocationKind.IN_SYSTEM
                    && boundSystemId.equals(placement.systemId())
                    && owned.contains(placement.id())) {
                localIds.add(placement.localEntityId());
            }
        }
        return Set.copyOf(localIds);
    }

    private static boolean lowerEntityId(Entity candidate, Entity current) {
        if (current == null) {
            return true;
        }
        EntityIdComponent candidateId = candidate.getComponent(EntityIdComponent.class);
        EntityIdComponent currentId = current.getComponent(EntityIdComponent.class);
        return candidateId != null && currentId != null && candidateId.id.compareTo(currentId.id) < 0;
    }

    private void synchronizeDockedSelection(PlayerMarketView view) {
        if (Objects.equals(lastDockedAt, view.station())) {
            return;
        }
        lastDockedAt = view.station();
        List<PlayerMarketItemView> rows = tradableRows(view);
        selectedMarketIndex = 0;
        for (int index = 0; index < rows.size(); index++) {
            if (scenario.route().itemContentId().equals(rows.get(index).itemContentId())) {
                selectedMarketIndex = index;
                break;
            }
        }
    }

    private static List<PlayerMarketItemView> tradableRows(PlayerMarketView view) {
        List<PlayerMarketItemView> result = new ArrayList<>();
        for (PlayerMarketItemView row : view.items()) {
            if (row.tradable()) {
                result.add(row);
            }
        }
        return result;
    }

    private Entity activeShipEntity() {
        PlayerShipView view = playerRuntime.activeShipView().orElse(null);
        if (view == null) {
            return null;
        }
        SimulationSession session = world.findSession(view.systemId()).orElse(null);
        return session == null ? null : session.getEntityRegistry().find(view.localEntityId());
    }

    private InventoryComponent activeShipInventory() {
        Entity ship = activeShipEntity();
        return ship == null ? null : ship.getComponent(InventoryComponent.class);
    }

    private String systemName(StarSystemId systemId) {
        return world.getTopology().findSystem(systemId)
                .map(StarSystemNode::name)
                .orElse("System " + systemId.value());
    }

    private String controllerName(StarSystemId systemId) {
        return world.controllingFaction(systemId).map(this::factionDisplayName).orElse("Unclaimed");
    }

    private String factionDisplayName(String factionContentId) {
        ContentCatalog.FactionDefinition authored = content.findFaction(factionContentId);
        if (authored != null) {
            return authored.displayName();
        }
        for (WorldFactionIdentityState identity : world.getWorldFactionIdentities()) {
            if (identity.stableFactionId().equals(factionContentId)) {
                return identity.displayName();
            }
        }
        return factionContentId;
    }

    private void updateHud() {
        updateShipHud();
        updateContextHud();
        statusHudLabel.setText("STATUS — " + statusMessage);
    }

    private void updateShipHud() {
        StringBuilder text = new StringBuilder(720);
        int systemCount = world.getTopology().systems().size();
        text.append("STAR EMPIRES — ").append(systemCount).append(" SYSTEM LIVE DEMO\n");
        FleetId activeFleet = playerRuntime.player().activeFleetId();
        PlayerShipView shipView = playerRuntime.activeShipView().orElse(null);
        Entity ship = activeShipEntity();
        IdentityComponent identity = ship == null ? null : ship.getComponent(IdentityComponent.class);
        InventoryComponent inventory = activeShipInventory();

        text.append("SHIP\n");
        text.append(identity == null ? "Active ship" : identity.name);
        text.append("  Fleet ").append(activeFleet == null ? "—" : "#" + activeFleet.value());
        text.append("  Owned: ").append(playerRuntime.player().ownedFleetIds().size()).append('\n');
        text.append("Credits: ").append(formatCredits(playerRuntime.player().walletMilliCredits()));
        text.append("   Time x").append(String.format(Locale.ROOT, "%.0f", playerRuntime.getTimeScale()));
        if (playerRuntime.isPaused()) {
            text.append(" [PAUSED]");
        }
        text.append('\n');

        if (shipView != null) {
            float speed = (float) Math.sqrt(shipView.velocityX() * shipView.velocityX()
                    + shipView.velocityY() * shipView.velocityY());
            text.append("System: #").append(shipView.systemId().value()).append(' ')
                    .append(systemName(shipView.systemId()));
            text.append("   Controller: ").append(controllerName(shipView.systemId())).append('\n');
            text.append(String.format(Locale.ROOT, "Pos %.0f, %.0f   Speed %.1f\n",
                    shipView.x(), shipView.y(), speed));
            if (inventory != null) {
                text.append("Cargo: ").append(inventory.getTotalStock()).append('/').append(inventory.capacity);
                String cargo = cargoSummary(inventory);
                if (!cargo.isEmpty()) {
                    text.append("  [").append(cargo).append(']');
                }
                text.append('\n');
            }
            text.append(shipView.docked() ? "State: DOCKED\n" : "State: FLIGHT\n");
        } else if (activeFleet != null && world.findFleetJump(activeFleet).isPresent()) {
            text.append("State: JUMP TRANSIT\n");
        } else {
            text.append("State: active ship not locally materialized\n");
        }

        appendCombatHud(text, ship);
        shipHudLabel.setText(text.toString());
    }

    private void appendCombatHud(StringBuilder text, Entity ship) {
        CombatComponent combat = ship == null ? null : ship.getComponent(CombatComponent.class);
        if (combat == null) {
            text.append("Combat: no combat system on this hull\n");
            return;
        }
        CombatRuntimeComponent runtime = ship.getComponent(CombatRuntimeComponent.class);
        CombatCommandComponent command = ship.getComponent(CombatCommandComponent.class);
        text.append(String.format(Locale.ROOT,
                "Combat: Hull %.0f/%.0f  Shield %.0f/%.0f  Range %.0f  Cooldown %.2fs\n",
                combat.hull, combat.maxHull, combat.shields, combat.maxShields, combat.weaponRange,
                runtime == null ? 0f : Math.max(0f, runtime.cooldownRemaining)));
        if (command == null || command.targetId == null) {
            text.append("Target: none [T nearest hostile]\n");
            return;
        }
        Entity target = activeSession.getEntityRegistry().find(command.targetId);
        IdentityComponent targetIdentity = target == null ? null : target.getComponent(IdentityComponent.class);
        TransformComponent targetTransform = target == null ? null : target.getComponent(TransformComponent.class);
        PlayerShipView shipView = playerRuntime.activeShipView().orElse(null);
        float targetDistance = targetTransform == null || shipView == null ? Float.NaN : distance(
                shipView.x(), shipView.y(), targetTransform.position.x, targetTransform.position.y);
        text.append("Target: ").append(targetIdentity == null ? command.targetId : targetIdentity.name);
        if (Float.isFinite(targetDistance)) {
            text.append(String.format(Locale.ROOT, "  %.1f/%.1f", targetDistance, combat.weaponRange));
            text.append(targetDistance <= combat.weaponRange ? " [IN RANGE]" : " [OUT OF RANGE]");
        }
        text.append(command.fireRequested ? "  FIRE REQUESTED\n" : "  [F fire]\n");
    }

    private void updateContextHud() {
        StringBuilder text = new StringBuilder(1200);
        PlayerMarketView market = marketService.view().orElse(null);
        PlayerMiningView mining = miningService.view().orElse(null);

        text.append("INTERACTION / ECONOMY\n");
        if (market != null) {
            synchronizeDockedSelection(market);
            List<PlayerMarketItemView> rows = tradableRows(market);
            text.append("Market access: ").append(market.marketAccessAllowed() ? "ALLOWED" : "DENIED");
            text.append("  Cargo ").append(market.cargoUsed()).append('/').append(market.cargoCapacity()).append('\n');
            if (!rows.isEmpty()) {
                selectedMarketIndex = Math.min(selectedMarketIndex, rows.size() - 1);
                PlayerMarketItemView item = rows.get(selectedMarketIndex);
                text.append("Selected: ").append(item.displayName())
                        .append("  station ").append(item.stationStock())
                        .append("  aboard ").append(item.playerCargo()).append('\n');
                text.append(String.format(Locale.ROOT,
                        "Buy %.2f cr  Sell %.2f cr  [UP/DOWN select | B buy | V sell]\n",
                        item.playerBuyPrice(), item.playerSellPrice()));
            }
        } else {
            MarketTarget nearest = nearestMarket();
            if (nearest != null) {
                text.append(String.format(Locale.ROOT,
                        "Nearest market: %s  %.1f units [E dock]\n",
                        nearest.name(), nearest.distance()));
            }
        }

        if (mining != null) {
            text.append("Mining: ").append(mining.status().getDisplayName());
            if (mining.targetId() != null) {
                text.append("  target ").append(mining.targetId());
            }
            if (mining.targetDistance() != null) {
                text.append(String.format(Locale.ROOT, "  distance %.1f / range %.1f",
                        mining.targetDistance(), mining.extractionRange()));
            }
            if (mining.targetRemainingResource() != null) {
                text.append("  reserve ").append(mining.targetRemainingResource());
            }
            text.append("  free cargo ").append(mining.freeCargoCapacity());
            if (mining.extractedLastTick() > 0) {
                text.append("  +").append(mining.extractedLastTick()).append(" last tick");
            }
            text.append(" [M target | R mine]\n");
        }

        appendNavigationHud(text);
        text.append(String.format(Locale.ROOT,
                "Camera %.2fx [wheel, HOME reset]   [TAB switch owned local ship]\n",
                cameraState.zoom()));
        text.append("[G global galaxy/factions] | WASD fly | E dock | K select jump | J jump | ")
                .append("T target | F fire | SPACE pause | 1/2/3/4 time | F5 save | F9 load | F2 reset");
        contextHudLabel.setText(text.toString());
    }

    private void appendNavigationHud(StringBuilder text) {
        PlayerShipView ship = playerRuntime.activeShipView().orElse(null);
        if (ship == null) {
            text.append("Navigation: JUMP TRANSIT — destination selection resumes on arrival\n");
            return;
        }

        StarSystemId current = ship.systemId();
        List<StarSystemId> neighbors = PlayerJumpNavigationModel.neighbors(world.getTopology(), current);
        text.append("Navigation: #").append(current.value()).append(' ').append(systemName(current));
        text.append("   Controller: ").append(controllerName(current)).append('\n');
        if (neighbors.isEmpty()) {
            selectedJumpNeighborIndex = 0;
            text.append("Direct neighbors: none\n");
            return;
        }

        selectedJumpNeighborIndex = PlayerJumpNavigationModel.normalizeSelectionIndex(
                world.getTopology(), current, selectedJumpNeighborIndex);
        text.append("Direct neighbors [K select]: ");
        for (int index = 0; index < neighbors.size(); index++) {
            if (index > 0) {
                text.append(" | ");
            }
            StarSystemId neighbor = neighbors.get(index);
            if (index == selectedJumpNeighborIndex) {
                text.append("> ");
            }
            text.append('#').append(neighbor.value()).append(' ').append(systemName(neighbor));
            text.append(" {").append(controllerName(neighbor)).append('}');
        }
        text.append("\n[J jump to selected direct neighbor]\n");
    }

    private String cargoSummary(InventoryComponent inventory) {
        StringBuilder result = new StringBuilder();
        int shown = 0;
        for (ContentCatalog.ItemDefinition item : content.getItems()) {
            int amount = inventory.stock[item.runtimeId()];
            if (amount <= 0) {
                continue;
            }
            if (shown > 0) {
                result.append(", ");
            }
            result.append(item.displayName()).append(' ').append(amount);
            shown++;
            if (shown >= 3) {
                break;
            }
        }
        return result.toString();
    }

    private static String formatCredits(long milliCredits) {
        return String.format(Locale.ROOT, "%,.2f cr", Money.toCredits(milliCredits));
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private void updateMapLayouts() {
        if (stage == null) {
            return;
        }
        float width = Math.max(64f, stage.getWidth());
        float height = Math.max(64f, stage.getHeight());
        PlayerShipView ship = playerRuntime.activeShipView().orElse(null);
        float centerX = ship == null ? WorldMapLayout.WORLD_WIDTH / 2f : ship.x();
        float centerY = ship == null ? WorldMapLayout.WORLD_HEIGHT / 2f : ship.y();
        float zoom = ship == null ? TRANSIT_ZOOM : cameraState.zoom();
        mapLayout = new WorldMapLayout(0f, 0f, width, height, MAP_PADDING, centerX, centerY, zoom);

        float minimapWidth = Math.min(MINIMAP_WIDTH, Math.max(120f, width * 0.28f));
        float minimapHeight = Math.min(MINIMAP_HEIGHT, Math.max(100f, height * 0.26f));
        float minimapX = Math.max(0f, width - minimapWidth - MINIMAP_MARGIN);
        float minimapY = MINIMAP_MARGIN;
        float minimapZoom = ship == null ? WorldMapLayout.MIN_ZOOM : MINIMAP_ZOOM;
        minimapLayout = new WorldMapLayout(
                minimapX, minimapY, minimapWidth, minimapHeight, MINIMAP_PADDING,
                centerX, centerY, minimapZoom);
    }

    @Override
    public void render() {
        float renderDelta = Gdx.graphics.getDeltaTime();
        playerRuntime.advanceFrame(renderDelta);
        if (!world.getActiveSystemId().equals(boundSystemId)) {
            bindActiveSession();
            statusMessage = "Arrived in #" + boundSystemId.value() + " " + systemName(boundSystemId)
                    + ". Direct-neighbor selection reset.";
            lastDockedAt = null;
        }
        updateMapLayouts();
        hudAccumulator += renderDelta;
        if (hudAccumulator >= HUD_UPDATE_INTERVAL_SECONDS) {
            hudAccumulator %= HUD_UPDATE_INTERVAL_SECONDS;
            updateHud();
        }
        stage.act(renderDelta);

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (galaxyMapVisible) {
            GalaxyStrategicMapSnapshot snapshot = GalaxyStrategicMapModel.capture(
                    world, content, boundSystemId, selectedJumpDestination());
            galaxyMapRenderer.render(
                    stage.getCamera().combined, snapshot, stage.getWidth(), stage.getHeight());
            return;
        }

        Entity playerEntity = activeShipEntity();
        worldMapRenderer.render(
                stage.getCamera().combined,
                PlayableMapEntityFilter.filter(
                        activeSession.getEngine().getEntities(), playerEntity, mapLayout.getZoom()),
                mapLayout,
                playerEntity);
        LocalMinimapSnapshot minimap = LocalMinimapModel.capture(
                activeSession.getEngine().getEntities(), playerEntity, ownedLocalEntityIds());
        minimapRenderer.render(
                stage.getCamera().combined,
                minimapLayout,
                minimap,
                "LOCAL — #" + boundSystemId.value() + " " + systemName(boundSystemId));
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0 || stage == null) {
            return;
        }
        stage.getViewport().update(width, height, true);
        updateMapLayouts();
    }

    @Override
    public void dispose() {
        if (Gdx.input != null) {
            Gdx.input.setInputProcessor(null);
        }
        if (stage != null) {
            stage.dispose();
            stage = null;
        }
        if (worldMapRenderer != null) {
            worldMapRenderer.dispose();
            worldMapRenderer = null;
        }
        if (minimapRenderer != null) {
            minimapRenderer.dispose();
            minimapRenderer = null;
        }
        if (galaxyMapRenderer != null) {
            galaxyMapRenderer.dispose();
            galaxyMapRenderer = null;
        }
        if (VisUI.isLoaded()) {
            VisUI.dispose();
        }
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private record MarketTarget(EntityId entityId, String name, float distance) {
    }
}
