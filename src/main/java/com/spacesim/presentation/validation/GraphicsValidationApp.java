package com.spacesim.presentation.validation;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Version;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.presentation.PresentationLayer;
import com.spacesim.presentation.PresentationPipeline;
import com.spacesim.presentation.asset.ProjectShipSprites;
import com.spacesim.presentation.asset.ShipSpriteSpec;
import com.spacesim.presentation.asset.VisualHardpoint;
import com.spacesim.presentation.asset.VisualHardpointType;
import java.util.Locale;

/**
 * Reproducible Stage-8.5 desktop rendering spike.
 *
 * <p>The scene is deliberately presentation-only and does not create a {@code SimulationSession}.
 * It exercises the production desktop backend with batched sprites, a fixed representative object
 * load, additive emissive/particle work, an off-screen framebuffer, a post-process shader and an
 * on-screen metrics HUD. When the project heavy-corvette texture is present in resources, ship zero
 * becomes the real authored sprite and its declared hardpoints drive engine and weapon effects.
 * Procedural textures remain a deterministic fallback and mass-load fixture.</p>
 */
public final class GraphicsValidationApp extends ApplicationAdapter {
    private static final int FRAME_WINDOW_SIZE = 240;
    private static final int SPRITE_BATCH_CAPACITY = 4096;
    private static final float SHIP_BASE_WIDTH = 64f;
    private static final float SHIP_BASE_HEIGHT = 30f;
    private static final float ASTEROID_BASE_SIZE = 24f;
    private static final float PARTICLE_SIZE = 9f;
    private static final int HERO_SHIP_INDEX = 0;

    private static final String POST_VERTEX_SHADER =
            "attribute vec4 a_position;\n"
                    + "attribute vec4 a_color;\n"
                    + "attribute vec2 a_texCoord0;\n"
                    + "uniform mat4 u_projTrans;\n"
                    + "varying vec4 v_color;\n"
                    + "varying vec2 v_texCoords;\n"
                    + "void main() {\n"
                    + "  v_color = a_color;\n"
                    + "  v_texCoords = a_texCoord0;\n"
                    + "  gl_Position = u_projTrans * a_position;\n"
                    + "}\n";

    private static final String POST_FRAGMENT_SHADER =
            "#ifdef GL_ES\n"
                    + "precision mediump float;\n"
                    + "#endif\n"
                    + "varying vec4 v_color;\n"
                    + "varying vec2 v_texCoords;\n"
                    + "uniform sampler2D u_texture;\n"
                    + "void main() {\n"
                    + "  vec4 color = texture2D(u_texture, v_texCoords) * v_color;\n"
                    + "  vec2 centered = v_texCoords - vec2(0.5);\n"
                    + "  float vignette = clamp(1.0 - dot(centered, centered) * 0.55, 0.72, 1.0);\n"
                    + "  color.rgb = pow(max(color.rgb * 1.08, vec3(0.0)), vec3(0.94));\n"
                    + "  color.rgb *= vignette;\n"
                    + "  gl_FragColor = color;\n"
                    + "}\n";

    private final GraphicsValidationProfile profile = GraphicsValidationProfile.representative();
    private final FrameTimeWindow frameTimes = new FrameTimeWindow(FRAME_WINDOW_SIZE);
    private final PresentationPipeline<Float> pipeline = new PresentationPipeline<>();
    private final ShipSpriteSpec heavyCorvetteSpec = ProjectShipSprites.whiteHeavyCorvette01();
    private final Vector2 transformedHardpoint = new Vector2();

    private OrthographicCamera camera;
    private SpriteBatch batch;
    private BitmapFont font;
    private Texture shipTexture;
    private Texture asteroidTexture;
    private Texture glowTexture;
    private Texture whiteTexture;
    private Texture heavyCorvetteTexture;
    private Texture heavyCorvetteEmissiveTexture;
    private TextureRegion shipRegion;
    private TextureRegion asteroidRegion;
    private TextureRegion glowRegion;
    private TextureRegion heavyCorvetteRegion;
    private TextureRegion heavyCorvetteEmissiveRegion;
    private FrameBuffer sceneBuffer;
    private TextureRegion sceneRegion;
    private ShaderProgram postShader;
    private float elapsedSeconds;
    private int frameDrawCalls;

    /** Creates GPU fixtures, optional authored ship resources and ordered presentation passes. */
    @Override
    public void create() {
        camera = new OrthographicCamera();
        batch = new SpriteBatch(SPRITE_BATCH_CAPACITY);
        font = new BitmapFont();
        font.getData().setScale(1.05f);

        shipTexture = createShipTexture();
        asteroidTexture = createAsteroidTexture();
        glowTexture = createGlowTexture();
        whiteTexture = createWhiteTexture();
        shipRegion = new TextureRegion(shipTexture);
        asteroidRegion = new TextureRegion(asteroidTexture);
        glowRegion = new TextureRegion(glowTexture);
        loadHeavyCorvetteAssets();

        postShader = new ShaderProgram(POST_VERTEX_SHADER, POST_FRAGMENT_SHADER);
        if (!postShader.isCompiled()) {
            throw new IllegalStateException("Graphics validation post shader failed: " + postShader.getLog());
        }

        pipeline.register(PresentationLayer.BACKGROUND, "validation-scene-begin", this::beginScene);
        pipeline.register(PresentationLayer.WORLD, "validation-world", this::drawWorld);
        pipeline.register(PresentationLayer.EFFECTS, "validation-emissive", this::drawEffects);
        pipeline.register(PresentationLayer.OVERLAY, "validation-post-process", this::compositeScene);
        pipeline.register(PresentationLayer.UI, "validation-hud", this::drawHud);

        resize(Math.max(1, Gdx.graphics.getWidth()), Math.max(1, Gdx.graphics.getHeight()));
    }

    /** Advances presentation-only animation and executes the validation pipeline. */
    @Override
    public void render() {
        float deltaSeconds = Math.max(0.000_001f, Gdx.graphics.getDeltaTime());
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
            return;
        }

        elapsedSeconds += deltaSeconds;
        frameTimes.recordSeconds(deltaSeconds);
        frameDrawCalls = 0;
        pipeline.render(deltaSeconds);
    }

    /** Rebuilds viewport-dependent framebuffer resources. */
    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0 || camera == null) {
            return;
        }
        camera.setToOrtho(false, width, height);
        camera.update();
        rebuildSceneBuffer(width, height);
    }

    /** Releases all validation-only GPU resources. */
    @Override
    public void dispose() {
        disposeSceneBuffer();
        if (postShader != null) {
            postShader.dispose();
        }
        if (heavyCorvetteTexture != null) {
            heavyCorvetteTexture.dispose();
        }
        if (heavyCorvetteEmissiveTexture != null) {
            heavyCorvetteEmissiveTexture.dispose();
        }
        if (shipTexture != null) {
            shipTexture.dispose();
        }
        if (asteroidTexture != null) {
            asteroidTexture.dispose();
        }
        if (glowTexture != null) {
            glowTexture.dispose();
        }
        if (whiteTexture != null) {
            whiteTexture.dispose();
        }
        if (font != null) {
            font.dispose();
        }
        if (batch != null) {
            batch.dispose();
        }
    }

    private void loadHeavyCorvetteAssets() {
        FileHandle baseHandle = Gdx.files.internal(heavyCorvetteSpec.baseTexturePath());
        if (baseHandle.exists()) {
            heavyCorvetteTexture = new Texture(baseHandle);
            heavyCorvetteTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            heavyCorvetteRegion = new TextureRegion(heavyCorvetteTexture);
        }

        String emissivePath = heavyCorvetteSpec.emissiveTexturePath();
        if (emissivePath != null) {
            FileHandle emissiveHandle = Gdx.files.internal(emissivePath);
            if (emissiveHandle.exists()) {
                heavyCorvetteEmissiveTexture = new Texture(emissiveHandle);
                heavyCorvetteEmissiveTexture.setFilter(
                        Texture.TextureFilter.Linear,
                        Texture.TextureFilter.Linear);
                heavyCorvetteEmissiveRegion = new TextureRegion(heavyCorvetteEmissiveTexture);
            }
        }
    }

    private void beginScene(float deltaSeconds) {
        sceneBuffer.begin();
        Gdx.gl.glClearColor(0.006f, 0.011f, 0.026f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setShader(null);
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.setColor(0.08f, 0.15f, 0.27f, 0.18f);
        for (int index = 0; index < 180; index++) {
            float x = hash01(index, 17) * camera.viewportWidth;
            float y = hash01(index, 31) * camera.viewportHeight;
            float size = 1.0f + hash01(index, 47) * 2.6f;
            batch.draw(whiteTexture, x, y, size, size);
        }
        batch.setColor(Color.WHITE);
        batch.end();
        frameDrawCalls += batch.renderCalls;
    }

    private void drawWorld(float deltaSeconds) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        for (int index = 0; index < profile.asteroidCount(); index++) {
            float x = hash01(index, 101) * camera.viewportWidth;
            float y = hash01(index, 211) * camera.viewportHeight;
            float scale = 0.45f + hash01(index, 307) * 1.55f;
            float size = ASTEROID_BASE_SIZE * scale;
            float shade = 0.48f + hash01(index, 401) * 0.28f;
            batch.setColor(shade, shade * 0.93f, shade * 0.82f, 0.92f);
            batch.draw(
                    asteroidRegion,
                    x - size * 0.5f,
                    y - size * 0.5f,
                    size * 0.5f,
                    size * 0.5f,
                    size,
                    size,
                    1f,
                    1f,
                    hash01(index, 503) * 360f);
        }

        for (int index = 0; index < profile.shipCount(); index++) {
            ShipPose pose = shipPose(index);
            if (index == HERO_SHIP_INDEX && hasHeavyCorvette()) {
                drawHeavyCorvetteBase(pose);
                drawDamageMark(pose, heavyCorvetteSpec.worldWidth(), heavyCorvetteSpec.worldHeight());
                continue;
            }

            float classScale = 0.76f + (index % 5) * 0.12f;
            float width = SHIP_BASE_WIDTH * classScale;
            float height = SHIP_BASE_HEIGHT * classScale;
            float pulse = 0.88f + MathUtils.sin(elapsedSeconds * 0.7f + index) * 0.06f;
            batch.setColor(0.57f * pulse, 0.72f * pulse, 0.90f * pulse, 1f);
            batch.draw(
                    shipRegion,
                    pose.x - width * 0.5f,
                    pose.y - height * 0.5f,
                    width * 0.5f,
                    height * 0.5f,
                    width,
                    height,
                    1f,
                    1f,
                    pose.rotationDegrees);

            if (index % 11 == 0) {
                drawDamageMark(pose, width, height);
            }
        }

        drawValidationBeam();
        batch.setColor(Color.WHITE);
        batch.end();
        frameDrawCalls += batch.renderCalls;
    }

    private void drawHeavyCorvetteBase(ShipPose pose) {
        float width = heavyCorvetteSpec.worldWidth();
        float height = heavyCorvetteSpec.worldHeight();
        batch.setColor(Color.WHITE);
        batch.draw(
                heavyCorvetteRegion,
                pose.x - width * heavyCorvetteSpec.pivotX(),
                pose.y - height * heavyCorvetteSpec.pivotY(),
                width * heavyCorvetteSpec.pivotX(),
                height * heavyCorvetteSpec.pivotY(),
                width,
                height,
                1f,
                1f,
                pose.rotationDegrees);
    }

    private void drawEffects(float deltaSeconds) {
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();

        for (int index = 0; index < profile.shipCount(); index++) {
            ShipPose pose = shipPose(index);
            if (index == HERO_SHIP_INDEX && hasHeavyCorvette()) {
                drawHeavyCorvetteEmissive(pose);
                drawHeavyCorvetteEngineGlows(pose);
            } else {
                float radians = pose.rotationDegrees * MathUtils.degreesToRadians;
                float glowX = pose.x - MathUtils.cos(radians) * 31f;
                float glowY = pose.y - MathUtils.sin(radians) * 31f;
                float pulse = 0.82f + MathUtils.sin(elapsedSeconds * 8f + index * 0.37f) * 0.18f;
                float size = 34f * pulse;
                batch.setColor(0.20f, 0.55f, 1f, 0.58f);
                batch.draw(glowRegion, glowX - size * 0.5f, glowY - size * 0.5f, size, size);
            }
        }

        int particlesPerShip = Math.max(1, profile.particleCount() / profile.shipCount());
        for (int index = 0; index < profile.particleCount(); index++) {
            int shipIndex = index % profile.shipCount();
            int localIndex = index / profile.shipCount();
            ShipPose pose = shipPose(shipIndex);
            float phase = (localIndex + elapsedSeconds * 26f + hash01(index, 607)) % particlesPerShip;

            if (shipIndex == HERO_SHIP_INDEX && hasHeavyCorvette()) {
                drawHeavyCorvetteParticle(index, phase, particlesPerShip, pose);
            } else {
                float radians = pose.rotationDegrees * MathUtils.degreesToRadians;
                float distance = 20f + phase * 2.5f;
                float side = (hash01(index, 701) - 0.5f) * (6f + phase * 0.35f);
                float x = pose.x - MathUtils.cos(radians) * distance - MathUtils.sin(radians) * side;
                float y = pose.y - MathUtils.sin(radians) * distance + MathUtils.cos(radians) * side;
                float alpha = Math.max(0.05f, 1f - phase / particlesPerShip);
                float size = PARTICLE_SIZE * (0.55f + alpha);
                batch.setColor(0.10f + alpha * 0.22f, 0.38f + alpha * 0.32f, 1f, alpha * 0.44f);
                batch.draw(glowRegion, x - size * 0.5f, y - size * 0.5f, size, size);
            }
        }

        ShipPose shieldPose = shipPose(HERO_SHIP_INDEX);
        float shieldPulse = hasHeavyCorvette()
                ? Math.max(heavyCorvetteSpec.worldWidth(), heavyCorvetteSpec.worldHeight()) * 1.18f
                : 88f + MathUtils.sin(elapsedSeconds * 4f) * 12f;
        if (hasHeavyCorvette()) {
            shieldPulse += MathUtils.sin(elapsedSeconds * 4f) * 10f;
        }
        batch.setColor(0.18f, 0.62f, 1f, 0.18f);
        batch.draw(
                glowRegion,
                shieldPose.x - shieldPulse * 0.5f,
                shieldPose.y - shieldPulse * 0.5f,
                shieldPulse,
                shieldPulse);

        batch.setColor(Color.WHITE);
        batch.end();
        frameDrawCalls += batch.renderCalls;
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void drawHeavyCorvetteEmissive(ShipPose pose) {
        if (heavyCorvetteEmissiveRegion == null) {
            return;
        }
        float width = heavyCorvetteSpec.worldWidth();
        float height = heavyCorvetteSpec.worldHeight();
        float pulse = 0.82f + MathUtils.sin(elapsedSeconds * 5.5f) * 0.12f;
        batch.setColor(1f, 0.86f, 0.58f, pulse);
        batch.draw(
                heavyCorvetteEmissiveRegion,
                pose.x - width * heavyCorvetteSpec.pivotX(),
                pose.y - height * heavyCorvetteSpec.pivotY(),
                width * heavyCorvetteSpec.pivotX(),
                height * heavyCorvetteSpec.pivotY(),
                width,
                height,
                1f,
                1f,
                pose.rotationDegrees);
    }

    private void drawHeavyCorvetteEngineGlows(ShipPose pose) {
        float pulse = 0.82f + MathUtils.sin(elapsedSeconds * 8f) * 0.18f;
        for (VisualHardpoint hardpoint : heavyCorvetteSpec.hardpoints()) {
            if (hardpoint.type() != VisualHardpointType.ENGINE) {
                continue;
            }
            transformHardpoint(pose, hardpoint, transformedHardpoint);
            float size = 30f * pulse;
            batch.setColor(0.16f, 0.52f, 1f, 0.72f);
            batch.draw(
                    glowRegion,
                    transformedHardpoint.x - size * 0.5f,
                    transformedHardpoint.y - size * 0.5f,
                    size,
                    size);
        }
    }

    private void drawHeavyCorvetteParticle(
            int particleIndex,
            float phase,
            int particlesPerShip,
            ShipPose pose) {
        VisualHardpoint engine = engineHardpoint(particleIndex);
        transformHardpoint(pose, engine, transformedHardpoint);
        float direction = (pose.rotationDegrees + engine.directionDegrees()) * MathUtils.degreesToRadians;
        float distance = 5f + phase * 2.25f;
        float side = (hash01(particleIndex, 701) - 0.5f) * (5f + phase * 0.25f);
        float x = transformedHardpoint.x
                + MathUtils.cos(direction) * distance
                - MathUtils.sin(direction) * side;
        float y = transformedHardpoint.y
                + MathUtils.sin(direction) * distance
                + MathUtils.cos(direction) * side;
        float alpha = Math.max(0.05f, 1f - phase / particlesPerShip);
        float size = PARTICLE_SIZE * (0.55f + alpha);
        batch.setColor(0.08f + alpha * 0.20f, 0.34f + alpha * 0.34f, 1f, alpha * 0.52f);
        batch.draw(glowRegion, x - size * 0.5f, y - size * 0.5f, size, size);
    }

    private void compositeScene(float deltaSeconds) {
        sceneBuffer.end();
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(camera.combined);
        batch.setShader(postShader);
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(sceneRegion, 0f, 0f, camera.viewportWidth, camera.viewportHeight);
        batch.end();
        frameDrawCalls += batch.renderCalls;
        batch.setShader(null);
    }

    private void drawHud(float deltaSeconds) {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        batch.setColor(Color.WHITE);

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        double usedMegabytes = usedMemory / (1024.0 * 1024.0);
        String line1 = String.format(
                Locale.ROOT,
                "Stage 8.5 Graphics Spike | libGDX %s | %dx%d | ESC exit",
                Version.VERSION,
                Gdx.graphics.getWidth(),
                Gdx.graphics.getHeight());
        String line2 = String.format(
                Locale.ROOT,
                "ships %d | asteroids %d | particles %d | objects %d",
                profile.shipCount(),
                profile.asteroidCount(),
                profile.particleCount(),
                profile.totalObjectCount());
        String line3 = String.format(
                Locale.ROOT,
                "FPS %d | frame avg %.2f ms | p95 %.2f ms | max %.2f ms",
                Gdx.graphics.getFramesPerSecond(),
                frameTimes.averageMilliseconds(),
                frameTimes.p95Milliseconds(),
                frameTimes.maxMilliseconds());
        String line4 = String.format(
                Locale.ROOT,
                "draw calls %d | max sprites/batch %d | heap %.1f MiB | post-process ON",
                frameDrawCalls,
                batch.maxSpritesInBatch,
                usedMegabytes);
        String line5 = hasHeavyCorvette()
                ? "hero asset REAL HEAVY CORVETTE | hardpoint VFX ON | emissive "
                        + (heavyCorvetteEmissiveRegion == null ? "MISSING/OPTIONAL" : "ON")
                : "hero asset PROCEDURAL FALLBACK | add white_heavy_corvette_01_base.png to resources";

        font.setColor(0.82f, 0.91f, 1f, 1f);
        font.draw(batch, line1, 22f, camera.viewportHeight - 22f);
        font.draw(batch, line2, 22f, camera.viewportHeight - 44f);
        font.draw(batch, line3, 22f, camera.viewportHeight - 66f);
        font.draw(batch, line4, 22f, camera.viewportHeight - 88f);
        font.setColor(hasHeavyCorvette() ? 0.58f : 1f, hasHeavyCorvette() ? 1f : 0.72f, 0.62f, 1f);
        font.draw(batch, line5, 22f, camera.viewportHeight - 110f);
        font.setColor(Color.WHITE);
        batch.end();
        frameDrawCalls += batch.renderCalls;
    }

    private void drawDamageMark(ShipPose pose, float width, float height) {
        batch.setColor(0.95f, 0.20f, 0.10f, 0.36f);
        float markSize = Math.max(5f, height * 0.28f);
        batch.draw(
                glowRegion,
                pose.x - width * 0.05f - markSize * 0.5f,
                pose.y + height * 0.08f - markSize * 0.5f,
                markSize,
                markSize);
    }

    private void drawValidationBeam() {
        ShipPose target = shipPose(3);
        float sourceX;
        float sourceY;

        if (hasHeavyCorvette()) {
            ShipPose source = shipPose(HERO_SHIP_INDEX);
            VisualHardpoint muzzle = hardpoint("weapon_nose_primary");
            transformHardpoint(source, muzzle, transformedHardpoint);
            sourceX = transformedHardpoint.x;
            sourceY = transformedHardpoint.y;
        } else {
            ShipPose source = shipPose(2);
            sourceX = source.x;
            sourceY = source.y;
        }

        float dx = target.x - sourceX;
        float dy = target.y - sourceY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float angle = MathUtils.atan2Deg(dy, dx);
        batch.setColor(0.35f, 0.92f, 1f, 0.72f);
        batch.draw(
                whiteTexture,
                sourceX,
                sourceY - 1.5f,
                0f,
                1.5f,
                length,
                3f,
                1f,
                1f,
                angle,
                0,
                0,
                1,
                1,
                false,
                false);
    }

    private VisualHardpoint engineHardpoint(int particleIndex) {
        int targetOrdinal = Math.floorMod(particleIndex, 3);
        int currentOrdinal = 0;
        for (VisualHardpoint hardpoint : heavyCorvetteSpec.hardpoints()) {
            if (hardpoint.type() != VisualHardpointType.ENGINE) {
                continue;
            }
            if (currentOrdinal == targetOrdinal) {
                return hardpoint;
            }
            currentOrdinal++;
        }
        throw new IllegalStateException("Heavy corvette must define three engine hardpoints");
    }

    private VisualHardpoint hardpoint(String id) {
        for (VisualHardpoint hardpoint : heavyCorvetteSpec.hardpoints()) {
            if (id.equals(hardpoint.id())) {
                return hardpoint;
            }
        }
        throw new IllegalStateException("Missing heavy-corvette hardpoint: " + id);
    }

    private Vector2 transformHardpoint(ShipPose pose, VisualHardpoint hardpoint, Vector2 output) {
        float width = heavyCorvetteSpec.worldWidth();
        float height = heavyCorvetteSpec.worldHeight();
        float localX = (hardpoint.normalizedX() - heavyCorvetteSpec.pivotX()) * width;
        float localY = (hardpoint.normalizedY() - heavyCorvetteSpec.pivotY()) * height;
        float radians = pose.rotationDegrees * MathUtils.degreesToRadians;
        float cos = MathUtils.cos(radians);
        float sin = MathUtils.sin(radians);
        return output.set(
                pose.x + localX * cos - localY * sin,
                pose.y + localX * sin + localY * cos);
    }

    private boolean hasHeavyCorvette() {
        return heavyCorvetteRegion != null;
    }

    private ShipPose shipPose(int index) {
        int columns = 10;
        int rows = Math.max(1, (profile.shipCount() + columns - 1) / columns);
        int column = index % columns;
        int row = index / columns;
        float spacingX = camera.viewportWidth / columns;
        float spacingY = camera.viewportHeight / rows;
        float driftX = MathUtils.sin(elapsedSeconds * 0.31f + index * 0.73f) * 24f;
        float driftY = MathUtils.cos(elapsedSeconds * 0.27f + index * 0.51f) * 18f;
        float x = (column + 0.5f) * spacingX + driftX;
        float y = (row + 0.5f) * spacingY + driftY;
        float rotation = MathUtils.sin(elapsedSeconds * 0.23f + index * 0.37f) * 16f;
        return new ShipPose(x, y, rotation);
    }

    private void rebuildSceneBuffer(int width, int height) {
        disposeSceneBuffer();
        sceneBuffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        sceneRegion = new TextureRegion(sceneBuffer.getColorBufferTexture());
        sceneRegion.flip(false, true);
    }

    private void disposeSceneBuffer() {
        if (sceneBuffer != null) {
            sceneBuffer.dispose();
            sceneBuffer = null;
            sceneRegion = null;
        }
    }

    private static Texture createShipTexture() {
        Pixmap pixmap = new Pixmap(64, 32, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        pixmap.setColor(0.48f, 0.65f, 0.84f, 1f);
        pixmap.fillTriangle(62, 16, 12, 29, 12, 3);
        pixmap.setColor(0.18f, 0.29f, 0.43f, 1f);
        pixmap.fillTriangle(44, 16, 8, 25, 8, 7);
        pixmap.setColor(0.75f, 0.88f, 1f, 1f);
        pixmap.fillRectangle(31, 13, 17, 6);
        pixmap.setColor(0.11f, 0.19f, 0.31f, 1f);
        pixmap.fillRectangle(5, 9, 12, 14);
        Texture texture = textureFrom(pixmap);
        pixmap.dispose();
        return texture;
    }

    private static Texture createAsteroidTexture() {
        Pixmap pixmap = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        pixmap.setColor(0.61f, 0.56f, 0.49f, 1f);
        pixmap.fillCircle(16, 16, 14);
        pixmap.setColor(0.34f, 0.31f, 0.28f, 0.9f);
        pixmap.fillCircle(10, 11, 4);
        pixmap.fillCircle(21, 20, 3);
        Texture texture = textureFrom(pixmap);
        pixmap.dispose();
        return texture;
    }

    private static Texture createGlowTexture() {
        int size = 64;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        float center = (size - 1) * 0.5f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x - center) / center;
                float dy = (y - center) / center;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float alpha = Math.max(0f, 1f - distance);
                alpha *= alpha;
                pixmap.setColor(1f, 1f, 1f, alpha);
                pixmap.drawPixel(x, y);
            }
        }
        Texture texture = textureFrom(pixmap);
        pixmap.dispose();
        return texture;
    }

    private static Texture createWhiteTexture() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        Texture texture = textureFrom(pixmap);
        pixmap.dispose();
        return texture;
    }

    private static Texture textureFrom(Pixmap pixmap) {
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }

    private static float hash01(int index, int salt) {
        int value = index * 0x45d9f3b + salt * 0x27d4eb2d;
        value = (value ^ (value >>> 16)) * 0x45d9f3b;
        value ^= value >>> 16;
        return (value & 0x7fffffff) / (float) Integer.MAX_VALUE;
    }

    private static final class ShipPose {
        private final float x;
        private final float y;
        private final float rotationDegrees;

        private ShipPose(float x, float y, float rotationDegrees) {
            this.x = x;
            this.y = y;
            this.rotationDegrees = rotationDegrees;
        }
    }
}
