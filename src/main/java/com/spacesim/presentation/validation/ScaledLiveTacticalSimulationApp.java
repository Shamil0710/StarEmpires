package com.spacesim.presentation.validation;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.spacesim.ship.LiveTacticalBattleControlRuntime;
import com.spacesim.ui.ScaledLiveTacticalSimulationSession;
import com.spacesim.ui.ScaledLiveTacticalSimulationSession.SimulationSpeed;
import com.spacesim.ui.ScaledTacticalDebugSnapshot;
import com.spacesim.ui.TacticalPrototypeRenderer;
import com.spacesim.ui.TacticalScenarioId;
import com.spacesim.ui.WorldMapLayout;

import java.util.Locale;
import java.util.Objects;

/**
 * Runnable Stage-19J desktop viewer over one selected exact-local production tactical scenario.
 *
 * <p>Wall-clock time and input own presentation scheduling only. The viewer never advances partial
 * simulation intervals and never owns movement, AI, sensors, weapons, ammunition, damage, power,
 * heat or body state. Rendering and diagnostics consume immutable read-only projections.</p>
 */
public final class ScaledLiveTacticalSimulationApp extends ApplicationAdapter {
    private static final float VIEW_PADDING_PX = 28f;
    private static final double MAX_FRAME_SECONDS = 0.25d;
    private static final int MAX_BATCHES_PER_RENDER = 64;

    private final TacticalScenarioId scenarioId;
    private ScaledLiveTacticalSimulationSession session;
    private TacticalPrototypeRenderer tacticalRenderer;
    private OrthographicCamera camera;
    private SpriteBatch batch;
    private BitmapFont font;
    private WorldMapLayout layout;
    private boolean debugHud = true;
    private int selectedCombatantIndex;
    private double accumulatedWallSeconds;

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
        tacticalRenderer = new TacticalPrototypeRenderer();
        camera = new OrthographicCamera();
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(1.0f);
        resize(Math.max(1, Gdx.graphics.getWidth()), Math.max(1, Gdx.graphics.getHeight()));
    }

    /** Processes viewer controls, whole fixed-tick batches and current read-only presentation. */
    @Override
    public void render() {
        handleInput();
        advanceFromWallClock(Math.max(0d, Math.min(MAX_FRAME_SECONDS, Gdx.graphics.getDeltaTime())));

        Gdx.gl.glClearColor(0.006f, 0.010f, 0.020f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        tacticalRenderer.render(camera.combined, layout, session.snapshot());
        drawHud(session.debugSnapshot());
    }

    /** Rebuilds only presentation-space mapping after a window resize. */
    @Override
    public void resize(int width, int height) {
        if (camera == null || width <= 0 || height <= 0) {
            return;
        }
        camera.setToOrtho(false, width, height);
        camera.update();
        layout = new WorldMapLayout(
                0f,
                0f,
                width,
                height,
                VIEW_PADDING_PX,
                WorldMapLayout.WORLD_WIDTH * 0.5f,
                WorldMapLayout.WORLD_HEIGHT * 0.5f,
                1f);
    }

    /** Releases presentation-only resources. */
    @Override
    public void dispose() {
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
            selectedCombatantIndex = 0;
            accumulatedWallSeconds = 0d;
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
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) {
            selectedCombatantIndex++;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) {
            selectedCombatantIndex--;
        }
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

    private void drawHud(ScaledTacticalDebugSnapshot debug) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(new Color(0.82f, 0.92f, 1f, 1f));
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
                        "Tick %d | %s | %s | bodies K/G/I/D %d/%d/%d/%d",
                        debug.tick(),
                        session.paused() ? "PAUSED" : "RUNNING",
                        session.simulationSpeed(),
                        debug.bodies().kinetic(),
                        debug.bodies().strike(),
                        debug.bodies().interceptor(),
                        debug.bodies().decoy()),
                22f,
                top - 24f);
        if (debugHud && !debug.combatants().isEmpty()) {
            int size = debug.combatants().size();
            int canonicalIndex = Math.floorMod(selectedCombatantIndex, size);
            var actor = debug.combatants().get(canonicalIndex);
            var formation = actor.formation();
            font.draw(batch,
                    String.format(Locale.ROOT,
                            "Actor %d [%s] | tracks %d | target %d | fire req/auth %s/%s | intent (%.2f, %.2f)",
                            actor.entityId(),
                            actor.side(),
                            actor.tracks().size(),
                            actor.selectedTargetId(),
                            actor.fireRequested(),
                            actor.fireAuthorized(),
                            actor.movementAxisX(),
                            actor.movementAxisY()),
                    22f,
                    top - 48f);
            font.draw(batch,
                    String.format(Locale.ROOT,
                            "AI %s / %s | ammo %d | reaction mass %.1f kg | bus %.3e J",
                            actor.survivalAction(),
                            actor.survivalReason(),
                            actor.ammunitionCount(),
                            actor.reactionMassKg(),
                            actor.sharedBusEnergyJ()),
                    22f,
                    top - 72f);
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
                    top - 96f);
        }
        font.draw(batch,
                "SPACE pause | N/RIGHT single tick | R reset CURRENT scenario | 1/2/4/8 speed | UP/DOWN actor | F1 HUD | ESC exit",
                22f,
                18f);
        batch.end();
    }
}
