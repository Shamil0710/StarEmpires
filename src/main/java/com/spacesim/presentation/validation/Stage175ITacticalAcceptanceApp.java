package com.spacesim.presentation.validation;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.spacesim.ui.Stage175ITacticalAcceptancePlayback;
import com.spacesim.ui.TacticalPrototypeRenderer;
import com.spacesim.ui.WorldMapLayout;

/**
 * Interactive desktop viewer for the Stage-17.5I deterministic tactical exit-gate playback.
 *
 * <p>The application owns only presentation resources and a frame index. All combat state is built
 * before rendering as immutable {@link Stage175ITacticalAcceptancePlayback} frames; keyboard input
 * can pause, step or restart playback but cannot fire weapons, apply damage, replenish resources or
 * otherwise mutate authoritative simulation state.</p>
 */
public final class Stage175ITacticalAcceptanceApp extends ApplicationAdapter {
    private static final float FRAME_SECONDS = 2.5f;
    private static final float VIEW_PADDING_PX = 28f;

    private Stage175ITacticalAcceptancePlayback.Playback playback;
    private TacticalPrototypeRenderer tacticalRenderer;
    private OrthographicCamera camera;
    private SpriteBatch batch;
    private BitmapFont font;
    private WorldMapLayout layout;
    private int frameIndex;
    private float frameElapsedSeconds;
    private boolean paused;

    /** Builds the immutable acceptance playback and allocates presentation-only graphics resources. */
    @Override
    public void create() {
        playback = Stage175ITacticalAcceptancePlayback.create();
        tacticalRenderer = TacticalPrototypeRenderer.withMinimumPlayableSprites();
        camera = new OrthographicCamera();
        batch = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(1.05f);
        resize(Math.max(1, Gdx.graphics.getWidth()), Math.max(1, Gdx.graphics.getHeight()));
    }

    /** Handles playback controls and renders the currently selected immutable tactical frame. */
    @Override
    public void render() {
        handleInput();
        advancePlayback(Math.max(0f, Gdx.graphics.getDeltaTime()));

        Gdx.gl.glClearColor(0.006f, 0.010f, 0.020f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        Stage175ITacticalAcceptancePlayback.Frame frame = playback.frames().get(frameIndex);
        tacticalRenderer.render(camera.combined, layout, frame.snapshot());
        drawHud(frame);
    }

    /** Rebuilds only the screen-space world mapping after a window resize. */
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

    /** Releases presentation-only renderer, font and batch resources. */
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
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.N)) {
            step(1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.P)) {
            step(-1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            frameIndex = 0;
            frameElapsedSeconds = 0f;
            paused = true;
        }
    }

    private void advancePlayback(float deltaSeconds) {
        if (paused) {
            return;
        }
        frameElapsedSeconds += deltaSeconds;
        while (frameElapsedSeconds >= FRAME_SECONDS) {
            frameElapsedSeconds -= FRAME_SECONDS;
            frameIndex = (frameIndex + 1) % playback.frames().size();
        }
    }

    private void step(int direction) {
        int size = playback.frames().size();
        frameIndex = Math.floorMod(frameIndex + direction, size);
        frameElapsedSeconds = 0f;
        paused = true;
    }

    private void drawHud(Stage175ITacticalAcceptancePlayback.Frame frame) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(new Color(0.82f, 0.92f, 1f, 1f));
        float top = camera.viewportHeight - 18f;
        font.draw(batch, "STAGE 17.5I — TACTICAL ACCEPTANCE", 22f, top);
        font.draw(batch, frame.title(), 22f, top - 24f);
        font.draw(batch,
                "Frame " + (frameIndex + 1) + "/" + playback.frames().size()
                        + (paused ? "  [PAUSED]" : "  [PLAYING]"),
                22f,
                top - 48f);
        font.draw(batch,
                "Kinetic rounds: " + playback.kineticRoundsConsumed() + "/" + playback.initialKineticRounds()
                        + " consumed | missile rounds: " + playback.missileRoundsConsumed()
                        + " | interceptor assignments: " + playback.defenseAssignments(),
                22f,
                50f);
        font.draw(batch,
                "Final target acceleration: " + String.format(java.util.Locale.ROOT, "%.6f m/s^2", playback.finalAccelerationMps2()),
                22f,
                30f);
        font.draw(batch, "SPACE play/pause | LEFT/RIGHT or P/N step | R reset | ESC exit", 22f, 12f);
        batch.end();
    }
}
