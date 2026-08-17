package com.spacesim.presentation.validation;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.spacesim.ship.LiveTacticalSimulationSession;
import com.spacesim.ui.LiveTacticalSimulationProjection;
import com.spacesim.ui.TacticalPrototypeRenderer;
import com.spacesim.ui.WorldMapLayout;

import java.util.Locale;

/**
 * Desktop real-time viewer for the deterministic post-17.5 tactical simulation session.
 *
 * <p>libGDX frame time never becomes combat authority. Wall-clock time is accumulated only to decide
 * how many complete fixed {@link LiveTacticalSimulationSession#TICK_SECONDS} intervals to execute.
 * Rendering consumes the current immutable snapshot through {@link LiveTacticalSimulationProjection}.</p>
 */
public final class LiveTacticalSimulationApp extends ApplicationAdapter {
    private static final float VIEW_PADDING_PX = 28f;
    private static final double MAX_FRAME_SECONDS = 0.25d;
    private static final int MAX_TICKS_PER_RENDER = 512;

    private LiveTacticalSimulationSession session;
    private LiveTacticalSimulationProjection projection;
    private TacticalPrototypeRenderer tacticalRenderer;
    private OrthographicCamera camera;
    private SpriteBatch batch;
    private BitmapFont font;
    private WorldMapLayout layout;
    private boolean paused;
    private boolean debugHud = true;
    private double simulationSpeed = 1d;
    private double accumulatedSimulationSeconds;

    /** Allocates presentation resources and creates one fresh authoritative live session. */
    @Override
    public void create() {
        resetSession();
        projection = new LiveTacticalSimulationProjection();
        tacticalRenderer = new TacticalPrototypeRenderer();
        camera = new OrthographicCamera();
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(1.05f);
        resize(Math.max(1, Gdx.graphics.getWidth()), Math.max(1, Gdx.graphics.getHeight()));
    }

    /** Advances whole fixed simulation ticks and renders the resulting current state. */
    @Override
    public void render() {
        handleInput();
        advanceFromWallClock(Math.max(0d, Math.min(MAX_FRAME_SECONDS, Gdx.graphics.getDeltaTime())));

        Gdx.gl.glClearColor(0.006f, 0.010f, 0.020f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        var state = session.snapshot();
        tacticalRenderer.render(camera.combined, layout, projection.project(state));
        drawHud(state);
    }

    /** Rebuilds only the presentation-space mapping after a window resize. */
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
            paused = !paused;
            accumulatedSimulationSeconds = 0d;
        }
        if (paused && (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)
                || Gdx.input.isKeyJustPressed(Input.Keys.N))) {
            session.advanceOneTick();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            resetSession();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
            simulationSpeed = 0.25d;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
            simulationSpeed = 0.5d;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) {
            simulationSpeed = 1d;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4)) {
            simulationSpeed = 2d;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_5)) {
            simulationSpeed = 4d;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_6)) {
            simulationSpeed = 8d;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            debugHud = !debugHud;
        }
    }

    private void advanceFromWallClock(double frameSeconds) {
        if (paused) {
            return;
        }
        accumulatedSimulationSeconds += frameSeconds * simulationSpeed;
        int executed = 0;
        while (accumulatedSimulationSeconds + 1e-12d >= LiveTacticalSimulationSession.TICK_SECONDS
                && executed < MAX_TICKS_PER_RENDER) {
            session.advanceOneTick();
            accumulatedSimulationSeconds -= LiveTacticalSimulationSession.TICK_SECONDS;
            executed++;
        }
    }

    private void resetSession() {
        session = new LiveTacticalSimulationSession();
        accumulatedSimulationSeconds = 0d;
        paused = false;
    }

    private void drawHud(LiveTacticalSimulationSession.Snapshot state) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(new Color(0.82f, 0.92f, 1f, 1f));
        float top = camera.viewportHeight - 18f;
        font.draw(batch, "LIVE TACTICAL SIMULATION — POST 17.5 TOOLING", 22f, top);
        font.draw(batch,
                String.format(Locale.ROOT,
                        "Tick %d | sim %.2f s | %s | speed %.2fx",
                        state.tick(),
                        state.elapsedSeconds(),
                        paused ? "PAUSED" : "RUNNING",
                        simulationSpeed),
                22f,
                top - 24f);
        if (debugHud) {
            String track = state.attackerTrack() == null
                    ? "NO CONTACT"
                    : state.attackerTrack().informationState().name();
            double shieldCapacity = state.targetShieldDefinition().reserveCapacityJ()
                    * state.targetShieldState().emitterIntegrity();
            double shieldPercent = shieldCapacity <= 0d
                    ? 0d
                    : 100d * Math.min(1d, state.targetShieldState().reserveJ() / shieldCapacity);
            font.draw(batch,
                    "Track: " + track
                            + " | kinetic bodies: " + state.projectiles().size()
                            + " | shots: " + state.shotsFired()
                            + " | impacts: " + state.impactsResolved(),
                    22f,
                    top - 48f);
            font.draw(batch,
                    String.format(Locale.ROOT,
                            "Primary ammo: %d | target shield: %.1f%% | target mean integrity: %.3f | accel: %.6f m/s^2",
                            state.primaryRoundsRemaining(),
                            shieldPercent,
                            meanIntegrity(state),
                            state.targetAccelerationMps2()),
                    22f,
                    top - 72f);
            font.draw(batch,
                    String.format(Locale.ROOT,
                            "Attacker bus energy: %.3e J | ship heat: %.3e J",
                            state.attackerSharedBusEnergyJ(),
                            state.attackerShipHeatStoredJ()),
                    22f,
                    top - 96f);
        }
        font.draw(batch,
                "SPACE pause | N/RIGHT one tick while paused | R reset | 1..6 speed 0.25x..8x | F1 HUD | ESC exit",
                22f,
                18f);
        batch.end();
    }

    private static double meanIntegrity(LiveTacticalSimulationSession.Snapshot state) {
        return state.targetHull().compartments().stream()
                .mapToDouble(value -> state.targetDamage().compartmentIntegrityById()
                        .getOrDefault(value.id(), 1d))
                .average()
                .orElse(1d);
    }
}
