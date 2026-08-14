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
import com.spacesim.components.EntityIdComponent;
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
import com.spacesim.player.PlayerMarketItemView;
import com.spacesim.player.PlayerMarketService;
import com.spacesim.player.PlayerMarketView;
import com.spacesim.player.PlayerRuntime;
import com.spacesim.player.PlayerShipView;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.ui.WorldMapLayout;
import com.spacesim.ui.WorldMapRenderer;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import com.spacesim.world.WorldSimulation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Manually playable Stage-12 desktop test harness.
 *
 * <p>The application is intentionally thin: it translates keyboard input into PlayerRuntime
 * commands, follows the active physical ship, exposes a read-only HUD and delegates every world
 * mutation to the already verified fixed-tick, jump and TradeController pipelines. It is a test
 * client for production core behavior rather than a second gameplay implementation.</p>
 */
public final class PlayableTestGame extends ApplicationAdapter {
    private static final float MAP_PADDING = 18f;
    private static final float FOLLOW_ZOOM = 2.4f;
    private static final float TRANSIT_ZOOM = 1.0f;
    private static final float HUD_WIDTH = 650f;
    private static final float HUD_UPDATE_INTERVAL_SECONDS = 0.1f;
    private static final String SAVE_FILE = "saves/playable-test-world.sav";

    private PlayableTestWorldFactory.Scenario scenario;
    private ContentCatalog content;
    private PlayerRuntime playerRuntime;
    private PlayerMarketService marketService;
    private WorldSimulation world;
    private SimulationSession activeSession;
    private StarSystemId boundSystemId;

    private Stage stage;
    private Label hudLabel;
    private WorldMapRenderer worldMapRenderer;
    private WorldMapLayout mapLayout;
    private Path savePath;

    private boolean moveUp;
    private boolean moveDown;
    private boolean moveLeft;
    private boolean moveRight;
    private int selectedMarketIndex;
    private DiscoveredObjectRef lastDockedAt;
    private float hudAccumulator;
    private String statusMessage = "Fresh curated world loaded.";

    /** Creates an uninitialized desktop test client; libGDX resources are allocated in create(). */
    public PlayableTestGame() {
    }

    /** Creates the deterministic test world, HUD, follow camera and keyboard command adapter. */
    @Override
    public void create() {
        VisUI.load();
        resetScenario();

        stage = new Stage(new ScreenViewport());
        Skin skin = VisUI.getSkin();
        worldMapRenderer = new WorldMapRenderer(skin.get(Label.LabelStyle.class).font);
        hudLabel = new Label("", skin);
        hudLabel.setAlignment(Align.topLeft);
        hudLabel.setWrap(true);
        Table hud = new Table();
        hud.setFillParent(true);
        hud.top().left().pad(12f);
        hud.add(hudLabel).width(HUD_WIDTH).top().left();
        hud.setTouchable(Touchable.disabled);
        stage.addActor(hud);

        savePath = Gdx.files.local(SAVE_FILE).file().toPath();
        Gdx.input.setInputProcessor(new InputMultiplexer(createInputAdapter(), stage));
        updateMapLayout();
        updateHud();
        Gdx.gl.glClearColor(0.018f, 0.025f, 0.045f, 1f);
    }

    private void resetScenario() {
        scenario = PlayableTestWorldFactory.create(PlayableTestWorldFactory.DEFAULT_TEST_SEED);
        content = scenario.content();
        playerRuntime = scenario.runtime();
        marketService = new PlayerMarketService(playerRuntime, content);
        world = playerRuntime.world();
        bindActiveSession();
        clearMovementKeys();
        selectedMarketIndex = 0;
        lastDockedAt = null;
    }

    private void bindActiveSession() {
        world = playerRuntime.world();
        boundSystemId = world.getActiveSystemId();
        activeSession = world.findSession(boundSystemId).orElseThrow(
                () -> new IllegalStateException("Active test StarSystem has no SimulationSession"));
    }

    private InputAdapter createInputAdapter() {
        return new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                switch (keycode) {
                    case Input.Keys.W -> moveUp = true;
                    case Input.Keys.S -> moveDown = true;
                    case Input.Keys.A -> moveLeft = true;
                    case Input.Keys.D -> moveRight = true;
                    case Input.Keys.E -> toggleDocking();
                    case Input.Keys.J -> jumpAlongTestRoute();
                    case Input.Keys.B -> tradeSelected(true);
                    case Input.Keys.V -> tradeSelected(false);
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
                applyMovementIntent();
                return true;
            }
        };
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
                statusMessage = "Undocked. Press J to jump when ready.";
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
            statusMessage = "Docked at " + target.name() + ". B buys, V sells.";
            lastDockedAt = null;
        } else {
            statusMessage = String.format(
                    Locale.ROOT,
                    "Too far from %s: %.1f units. Move closer and press E.",
                    target.name(),
                    target.distance());
        }
    }

    private void jumpAlongTestRoute() {
        PlayerShipView ship = playerRuntime.activeShipView().orElse(null);
        if (ship == null) {
            statusMessage = world.findFleetJump(playerRuntime.player().activeFleetId()).isPresent()
                    ? "Jump already in progress." : "Active ship is not locally materialized.";
            return;
        }
        if (playerRuntime.player().docked()) {
            statusMessage = "Undock with E before jumping.";
            return;
        }
        StarSystemId destination = scenario.route().otherEnd(ship.systemId());
        if (destination == null) {
            statusMessage = "Current system is outside the curated Anchor-Corona test route.";
            return;
        }
        clearMovementKeys();
        if (playerRuntime.requestJump(destination)) {
            statusMessage = "Stage-10 jump accepted. Transit is physical and persistent.";
        } else {
            statusMessage = "Jump request rejected by the authoritative travel pipeline.";
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
            statusMessage = "Dock at a market before trading.";
            return;
        }
        synchronizeDockedSelection(view);
        List<PlayerMarketItemView> rows = tradableRows(view);
        if (rows.isEmpty()) {
            statusMessage = "This market has no tradable items.";
            return;
        }
        PlayerMarketItemView item = rows.get(Math.min(selectedMarketIndex, rows.size() - 1));
        boolean success = buy
                ? marketService.buy(item.itemContentId(), 1)
                : marketService.sell(item.itemContentId(), 1);
        statusMessage = success
                ? (buy ? "Bought 1 " : "Sold 1 ") + item.displayName() + " through TradeController."
                : (buy ? "Buy" : "Sell") + " rejected by live market/wallet/cargo rules.";
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
            marketService = new PlayerMarketService(playerRuntime, content);
            world = playerRuntime.world();
            bindActiveSession();
            clearMovementKeys();
            selectedMarketIndex = 0;
            lastDockedAt = null;
            statusMessage = "Save loaded. FleetId, cargo, wallet and transit state restored.";
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
        updateMapLayout();
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
            float dx = transform.position.x - ship.x();
            float dy = transform.position.y - ship.y();
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (best == null || distance < best.distance()) {
                best = new MarketTarget(entityId.id, identity.name, distance);
            }
        }
        return best;
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
        for (StarSystemNode node : world.getTopology().systems()) {
            if (node.id().equals(systemId)) {
                return node.name();
            }
        }
        return "System " + systemId.value();
    }

    private void updateHud() {
        StringBuilder text = new StringBuilder(1024);
        text.append("STAR EMPIRES — PLAYABLE TEST WORLD\n");
        text.append("Stage 12 manual acceptance build\n\n");

        FleetId activeFleet = playerRuntime.player().activeFleetId();
        PlayerShipView ship = playerRuntime.activeShipView().orElse(null);
        text.append("Fleet: ").append(activeFleet == null ? "none" : "#" + activeFleet.value());
        text.append("   Credits: ").append(formatCredits(playerRuntime.player().walletMilliCredits()));
        text.append("   Time: x").append(String.format(Locale.ROOT, "%.0f", playerRuntime.getTimeScale()));
        if (playerRuntime.isPaused()) {
            text.append(" [PAUSED]");
        }
        text.append('\n');

        if (ship != null) {
            text.append("System: ").append(systemName(ship.systemId()));
            text.append(String.format(Locale.ROOT, "   Position: %.1f, %.1f", ship.x(), ship.y()));
            InventoryComponent inventory = activeShipInventory();
            if (inventory != null) {
                text.append("   Cargo: ").append(inventory.getTotalStock()).append('/').append(inventory.capacity);
            }
            text.append(ship.docked() ? "   [DOCKED]\n" : "\n");
        } else if (activeFleet != null && world.findFleetJump(activeFleet).isPresent()) {
            text.append("Status: IN TRANSIT through Stage-10 jump FSM\n");
        } else {
            text.append("Status: active ship is not locally materialized\n");
        }

        PlayableTestWorldFactory.Route route = scenario.route();
        text.append("\nRECOMMENDED TEST ROUTE\n");
        text.append("Buy ").append(route.itemDisplayName())
                .append(" at ").append(route.sourceStationName())
                .append(" in ").append(systemName(route.sourceSystem())).append('\n');
        text.append("J -> ").append(systemName(route.destinationSystem()))
                .append(" -> dock at ").append(route.destinationStationName())
                .append(" -> sell the same physical cargo\n");
        text.append(String.format(
                Locale.ROOT,
                "Bootstrap base prices: source sells %.2f cr, destination buys %.2f cr\n",
                route.sourceSellPriceCredits(),
                route.destinationBuyPriceCredits()));

        PlayerMarketView market = marketService.view().orElse(null);
        if (market != null) {
            synchronizeDockedSelection(market);
            List<PlayerMarketItemView> rows = tradableRows(market);
            text.append("\nMARKET — access ").append(market.marketAccessAllowed() ? "ALLOWED" : "DENIED").append('\n');
            text.append("Cargo: ").append(market.cargoUsed()).append('/').append(market.cargoCapacity()).append('\n');
            if (!rows.isEmpty()) {
                selectedMarketIndex = Math.min(selectedMarketIndex, rows.size() - 1);
                PlayerMarketItemView item = rows.get(selectedMarketIndex);
                text.append("Selected: ").append(item.displayName())
                        .append("   Station stock: ").append(item.stationStock())
                        .append("   Ship cargo: ").append(item.playerCargo()).append('\n');
                text.append(String.format(
                        Locale.ROOT,
                        "Player buy %.2f cr   Player sell %.2f cr   [UP/DOWN select, B buy 1, V sell 1]\n",
                        item.playerBuyPrice(),
                        item.playerSellPrice()));
            }
        } else {
            MarketTarget nearest = nearestMarket();
            if (nearest != null) {
                text.append(String.format(
                        Locale.ROOT,
                        "\nNearest market: %s — %.1f units [E docks inside physical range]\n",
                        nearest.name(),
                        nearest.distance()));
            }
        }

        text.append("\nCONTROLS: WASD fly | E dock/undock | J test-route jump | SPACE pause | 1/2/3/4 time x1/x2/x4/x8\n");
        text.append("F5 save | F9 load | F2 reset fresh world | save: ").append(SAVE_FILE).append('\n');
        text.append("\nSTATUS: ").append(statusMessage);
        hudLabel.setText(text.toString());
    }

    private static String formatCredits(long milliCredits) {
        return String.format(Locale.ROOT, "%,.2f cr", Money.toCredits(milliCredits));
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private void updateMapLayout() {
        if (stage == null) {
            return;
        }
        float width = Math.max(64f, stage.getWidth());
        float height = Math.max(64f, stage.getHeight());
        PlayerShipView ship = playerRuntime.activeShipView().orElse(null);
        float centerX = ship == null ? WorldMapLayout.WORLD_WIDTH / 2f : ship.x();
        float centerY = ship == null ? WorldMapLayout.WORLD_HEIGHT / 2f : ship.y();
        float zoom = ship == null ? TRANSIT_ZOOM : FOLLOW_ZOOM;
        mapLayout = new WorldMapLayout(0f, 0f, width, height, MAP_PADDING, centerX, centerY, zoom);
    }

    /** Advances the playable fixed-tick world, follows inter-system travel and renders the HUD. */
    @Override
    public void render() {
        float renderDelta = Gdx.graphics.getDeltaTime();
        playerRuntime.advanceFrame(renderDelta);
        if (!world.getActiveSystemId().equals(boundSystemId)) {
            bindActiveSession();
            statusMessage = "Arrived in " + systemName(boundSystemId) + ". Active local EntityId was rebound.";
            lastDockedAt = null;
        }
        updateMapLayout();
        hudAccumulator += renderDelta;
        if (hudAccumulator >= HUD_UPDATE_INTERVAL_SECONDS) {
            hudAccumulator %= HUD_UPDATE_INTERVAL_SECONDS;
            updateHud();
        }
        stage.act(renderDelta);

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        worldMapRenderer.render(
                stage.getCamera().combined,
                activeSession.getEngine().getEntities(),
                mapLayout,
                activeShipEntity());
        stage.draw();
    }

    /** Updates the Scene2D viewport; the follow map is rebuilt on the next frame. */
    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0 || stage == null) {
            return;
        }
        stage.getViewport().update(width, height, true);
        updateMapLayout();
    }

    /** Releases Scene2D and renderer resources owned by the test client. */
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
        if (VisUI.isLoaded()) {
            VisUI.dispose();
        }
    }

    private record MarketTarget(EntityId entityId, String name, float distance) {
    }
}
