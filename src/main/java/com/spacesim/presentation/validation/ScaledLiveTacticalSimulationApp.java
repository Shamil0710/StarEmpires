package com.spacesim.presentation.validation;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.ship.LiveTacticalBattleControlRuntime;
import com.spacesim.ui.ScaledLiveTacticalSimulationSession;
import com.spacesim.ui.ScaledLiveTacticalSimulationSession.SimulationSpeed;
import com.spacesim.ui.ScaledTacticalDebugSnapshot;
import com.spacesim.ui.ShipInspectionPanelRenderer;
import com.spacesim.ui.ShipInspectionSnapshot;
import com.spacesim.ui.ShipSelectionController;
import com.spacesim.ui.TacticalCameraController;
import com.spacesim.ui.TacticalPrototypeRenderer;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.TacticalSide;
import com.spacesim.ui.TacticalScenarioId;
import com.spacesim.ui.TacticalSelectionOverlayRenderer;
import com.spacesim.ui.TacticalSidePalette;
import com.spacesim.ui.WorldMapLayout;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Runnable Stage-19J desktop viewer over one selected exact-local production tactical scenario.
 *
 * <p>Wall-clock time and input own presentation scheduling only. The viewer never advances partial
 * simulation intervals and never owns movement, AI, sensors, weapons, ammunition, damage, power,
 * heat or body state. Rendering, camera, selection, inspection and diagnostics consume read-only
 * projections.</p>
 */
public final class ScaledLiveTacticalSimulationApp extends ApplicationAdapter {
    private static final float VIEW_PADDING_PX = 28f;
    private static final double MAX_FRAME_SECONDS = 0.25d;
    private static final int MAX_BATCHES_PER_RENDER = 64;
    private static final Color HUD_COLOR = new Color(0.82f, 0.92f, 1f, 1f);

    private final TacticalScenarioId scenarioId;
    private ScaledLiveTacticalSimulationSession session;
    private TacticalPrototypeRenderer tacticalRenderer;
    private TacticalSelectionOverlayRenderer selectionRenderer;
    private ShipInspectionPanelRenderer inspectionRenderer;
    private ShipSelectionController selectionController;
    private TacticalCameraController cameraController;
    private OrthographicCamera camera;
    private SpriteBatch batch;
    private BitmapFont font;
    private boolean debugHud = true;
    private boolean showShipLabels;
    private boolean panning;
    private float lastPanX;
    private float lastPanY;
    private int selectedCombatantIndex;
    private double accumulatedWallSeconds;
    private final Vector2 labelPoint = new Vector2();

    /** Creates the historical saturation viewer for backwards-compatible launch paths. */
    public ScaledLiveTacticalSimulationApp() {
        this(TacticalScenarioId.SATURATION_16V16);
    }

    /**
     * Creates a viewer that will own only presentation state for the selected canonical scenario.
     *
     * @param scenarioId canonical Stage-19J tactical validation scenario identity
     */
    public ScaledLiveTacticalSimulationApp(TacticalScenarioId scenarioId) {
        this.scenarioId = Objects.requireNonNull(scenarioId, "scenarioId");
    }

    /** Allocates presentation resources and creates the selected shared-factory live session. */
    @Override
    public void create() {
        session = new ScaledLiveTacticalSimulationSession(scenarioId);
        tacticalRenderer = TacticalPrototypeRenderer.withMinimumPlayableSprites();
        selectionRenderer = new TacticalSelectionOverlayRenderer();
        inspectionRenderer = new ShipInspectionPanelRenderer();
        selectionController = new ShipSelectionController();
        cameraController = new TacticalCameraController(VIEW_PADDING_PX);
        camera = new OrthographicCamera();
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(1.0f);
        resize(Math.max(1, Gdx.graphics.getWidth()), Math.max(1, Gdx.graphics.getHeight()));
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                if (cameraController == null) {
                    return false;
                }
                WorldMapLayout layout = cameraController.layout();
                float screenX = Gdx.input.getX();
                float screenY = bottomLeftScreenY();
                if (!layout.containsMapPoint(screenX, screenY)) {
                    return false;
                }
                cameraController.zoomByScroll(screenX, screenY, amountY);
                return true;
            }
        });
    }

    /** Processes viewer controls, whole fixed-tick batches and current read-only presentation. */
    @Override
    public void render() {
        handleInput();
        advanceFromWallClock(Math.max(0d, Math.min(MAX_FRAME_SECONDS, Gdx.graphics.getDeltaTime())));

        Gdx.gl.glClearColor(0.006f, 0.010f, 0.020f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        TacticalPrototypeVisualSnapshot snapshot = session.snapshot();
        selectionController.reconcile(snapshot);
        long selectedEntityId = selectionController.selectedEntityId().orElse(-1L);
        Optional<ShipInspectionSnapshot> inspection = selectedEntityId > 0L
                ? session.inspectionSnapshot(selectedEntityId)
                : Optional.empty();
        WorldMapLayout layout = cameraController.layout();

        tacticalRenderer.render(camera.combined, layout, snapshot);
        selectionRenderer.render(camera.combined, layout, snapshot, selectedEntityId);
        if (showShipLabels) {
            drawShipLabels(snapshot, layout);
        }
        drawHud(session.debugSnapshot(), snapshot, layout);
        inspectionRenderer.render(
                camera.combined,
                camera.viewportWidth,
                camera.viewportHeight,
                inspection);
    }

    /** Rebuilds the screen rectangle while preserving current tactical camera center and zoom. */
    @Override
    public void resize(int width, int height) {
        if (camera == null || cameraController == null || width <= 0 || height <= 0) {
            return;
        }
        camera.setToOrtho(false, width, height);
        camera.update();
        float panelWidth = ShipInspectionPanelRenderer.panelWidth(width);
        float mapWidth = Math.max(320f, width - panelWidth);
        cameraController.resize(mapWidth, height);
    }

    /** Releases presentation-only resources. */
    @Override
    public void dispose() {
        if (Gdx.input != null) {
            Gdx.input.setInputProcessor(null);
        }
        if (inspectionRenderer != null) {
            inspectionRenderer.dispose();
        }
        if (selectionRenderer != null) {
            selectionRenderer.dispose();
        }
        if (tacticalRenderer != null) {
            tacticalRenderer.dispose();
        }
        if (font != null) {
            font.dispose();
        }
        if (batch != null) {
            batch.dispose();
        }
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            if (session.paused()) {
                session.resume();
            } else {
                session.pause();
            }
            accumulatedWallSeconds = 0d;
        }
        if (session.paused() && (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)
                || Gdx.input.isKeyJustPressed(Input.Keys.N))) {
            session.stepOneTick();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            session.reset();
            selectionController.clear();
            selectedCombatantIndex = 0;
            accumulatedWallSeconds = 0d;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.C)) {
            cameraController.resetView();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
            session.setSimulationSpeed(SimulationSpeed.X1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
            session.setSimulationSpeed(SimulationSpeed.X2);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4)) {
            session.setSimulationSpeed(SimulationSpeed.X4);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_8)) {
            session.setSimulationSpeed(SimulationSpeed.X8);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            debugHud = !debugHud;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F2)) {
            showShipLabels = !showShipLabels;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) {
            selectedCombatantIndex++;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) {
            selectedCombatantIndex--;
        }

        WorldMapLayout layout = cameraController.layout();
        float screenX = Gdx.input.getX();
        float screenY = bottomLeftScreenY();
        handlePan(layout, screenX, screenY);

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && layout.containsMapPoint(screenX, screenY)) {
            selectionController.selectAt(screenX, screenY, layout, session.snapshot());
        }
    }

    private void handlePan(WorldMapLayout layout, float screenX, float screenY) {
        if (Gdx.input.isButtonJustPressed(Input.Buttons.MIDDLE) && layout.containsMapPoint(screenX, screenY)) {
            panning = true;
            lastPanX = screenX;
            lastPanY = screenY;
        }
        if (!Gdx.input.isButtonPressed(Input.Buttons.MIDDLE)) {
            panning = false;
            return;
        }
        if (!panning) {
            return;
        }
        float deltaX = screenX - lastPanX;
        float deltaY = screenY - lastPanY;
        cameraController.panByScreen(deltaX, deltaY);
        lastPanX = screenX;
        lastPanY = screenY;
    }

    private float bottomLeftScreenY() {
        return Gdx.graphics.getHeight() - 1f - Gdx.input.getY();
    }

    private void advanceFromWallClock(double frameSeconds) {
        if (session.paused()) {
            return;
        }
        accumulatedWallSeconds += frameSeconds;
        int batches = 0;
        while (accumulatedWallSeconds + 1e-12d >= LiveTacticalBattleControlRuntime.TICK_SECONDS
                && batches < MAX_BATCHES_PER_RENDER) {
            session.advanceScheduledBatch();
            accumulatedWallSeconds -= LiveTacticalBattleControlRuntime.TICK_SECONDS;
            batches++;
        }
    }

    private void drawShipLabels(TacticalPrototypeVisualSnapshot snapshot, WorldMapLayout layout) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (var ship : snapshot.ships()) {
            if (!layout.containsVisibleWorldPoint((float) ship.xM(), (float) ship.yM())
                    || !layout.worldToScreen((float) ship.xM(), (float) ship.yM(), labelPoint)) {
                continue;
            }
            setFontColor(TacticalSidePalette.outline(ship.side()));
            font.draw(batch,
                    ship.entityId() + " " + shortRole(ship.role().name()),
                    labelPoint.x + 9f,
                    labelPoint.y + 16f);
        }
        batch.end();
    }

    private void drawHud(
            ScaledTacticalDebugSnapshot debug,
            TacticalPrototypeVisualSnapshot snapshot,
            WorldMapLayout layout) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(HUD_COLOR);
        float top = camera.viewportHeight - 18f;
        font.draw(batch,
                String.format(Locale.ROOT,
                        "STAGE 19J — %s | %d SHIPS | key=%s",
                        session.scenario().displayName(),
                        session.scenario().totalShips(),
                        session.scenario().id().cliKey()),
                22f,
                top);
        font.draw(batch,
                String.format(Locale.ROOT,
                        "Tick %d | %s | %s | zoom %.2fx | bodies K/G/I/D %d/%d/%d/%d",
                        debug.tick(),
                        session.paused() ? "PAUSED" : "RUNNING",
                        session.simulationSpeed(),
                        layout.getZoom(),
                        debug.bodies().kinetic(),
                        debug.bodies().strike(),
                        debug.bodies().interceptor(),
                        debug.bodies().decoy()),
                22f,
                top - 24f);

        setFontColor(TacticalSidePalette.outline(TacticalSide.ALPHA));
        font.draw(batch,
                String.format(Locale.ROOT, "ALPHA ALIVE %d/%d",
                        aliveCount(snapshot, TacticalSide.ALPHA), session.scenario().alphaShips()),
                22f,
                top - 48f);
        setFontColor(TacticalSidePalette.outline(TacticalSide.BETA));
        font.draw(batch,
                String.format(Locale.ROOT, "BETA ALIVE %d/%d",
                        aliveCount(snapshot, TacticalSide.BETA), session.scenario().betaShips()),
                180f,
                top - 48f);
        font.setColor(HUD_COLOR);

        var selectedShip = selectionController.selectedShip(snapshot);
        if (selectedShip.isPresent()) {
            var ship = selectedShip.get();
            setFontColor(TacticalSidePalette.outline(ship.side()));
            font.draw(batch,
                    String.format(Locale.ROOT,
                            "SELECTED %d [%s] %s | integrity %.3f%s",
                            ship.entityId(),
                            ship.side(),
                            ship.role(),
                            ship.integrityFraction(),
                            ship.wreck() ? " | WRECK" : ""),
                    22f,
                    top - 72f);
            font.setColor(HUD_COLOR);
        } else {
            font.draw(batch, "SELECTED NONE", 22f, top - 72f);
        }

        if (debugHud && !debug.combatants().isEmpty()) {
            int size = debug.combatants().size();
            int canonicalIndex = Math.floorMod(selectedCombatantIndex, size);
            var actor = debug.combatants().get(canonicalIndex);
            var formation = actor.formation();
            font.draw(batch,
                    String.format(Locale.ROOT,
                            "DEBUG ACTOR %d [%s] | tracks %d | target %d | fire req/auth %s/%s | intent (%.2f, %.2f)",
                            actor.entityId(),
                            actor.side(),
                            actor.tracks().size(),
                            actor.selectedTargetId(),
                            actor.fireRequested(),
                            actor.fireAuthorized(),
                            actor.movementAxisX(),
                            actor.movementAxisY()),
                    22f,
                    top - 96f);
            font.draw(batch,
                    String.format(Locale.ROOT,
                            "AI %s / %s | ammo %d | reaction mass %.1f kg | bus %.3e J",
                            actor.survivalAction(),
                            actor.survivalReason(),
                            actor.ammunitionCount(),
                            actor.reactionMassKg(),
                            actor.sharedBusEnergyJ()),
                    22f,
                    top - 120f);
            String formationText = formation.objectiveKnown()
                    ? String.format(Locale.ROOT,
                            "%s %s/%s slot %d/%d err %.1f m",
                            formation.mode(),
                            formation.status(),
                            formation.reason(),
                            formation.slotIndex() + 1,
                            formation.slotCount(),
                            formation.errorM())
                    : "NONE";
            font.draw(batch,
                    String.format(Locale.ROOT,
                            "heat ship/local %.3e / %.3e J | integrity mean/min-module %.3f / %.3f | formation %s",
                            actor.shipHeatStoredJ(),
                            actor.localHeatStoredJ(),
                            actor.meanCompartmentIntegrity(),
                            actor.minimumModuleIntegrity(),
                            formationText),
                    22f,
                    top - 144f);
        }
        font.draw(batch,
                "Wheel zoom | MMB drag pan | C reset view | F2 labels | LMB select | SPACE pause | R scenario reset | F1 HUD",
                22f,
                18f);
        batch.end();
    }

    private static String shortRole(String role) {
        return switch (role) {
            case "DEFENSIVE_EW" -> "EW";
            case "UNCLASSIFIED" -> "?";
            default -> role;
        };
    }

    private static long aliveCount(TacticalPrototypeVisualSnapshot snapshot, TacticalSide side) {
        return snapshot.ships().stream()
                .filter(ship -> ship.side() == side && !ship.wreck())
                .count();
    }

    private void setFontColor(TacticalSidePalette.Rgba color) {
        font.setColor(color.r(), color.g(), color.b(), color.a());
    }
}
