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
import com.spacesim.presentation.asset.SpriteOrientationTransform;
import com.spacesim.presentation.asset.VisualHardpoint;
import com.spacesim.presentation.asset.VisualHardpointType;
import java.util.Locale;

/**
 * Reproducible Stage-8.5 desktop rendering and authored-asset review application.
 *
 * <p>The scene is presentation-only and never creates authoritative simulation state. View 1 keeps
 * the representative benchmark load. Views 2 and 3 provide tactical and close-up review of the
 * authored heavy corvette while preserving the same framebuffer/shader/VFX path. Runtime forward
 * is normalized to the right even when an authored source texture faces left.</p>
 */
public final class GraphicsValidationApp extends ApplicationAdapter {
    private static final int FRAME_WINDOW_SIZE = 240;
    private static final int SPRITE_BATCH_CAPACITY = 4096;
    private static final float SHIP_BASE_WIDTH = 64f;
    private static final float SHIP_BASE_HEIGHT = 30f;
    private static final float ASTEROID_BASE_SIZE = 24f;
    private static final float PARTICLE_SIZE = 9f;
    private static final int HERO_SHIP_INDEX = 0;
    private static final int TACTICAL_SHIP_COUNT = 7;
    private static final int TACTICAL_ASTEROID_COUNT = 140;
    private static final int TACTICAL_PARTICLE_COUNT = 560;
    private static final int CLOSE_UP_ASTEROID_COUNT = 40;
    private static final int CLOSE_UP_PARTICLE_COUNT = 240;

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
    private ValidationViewMode viewMode = ValidationViewMode.REPRESENTATIVE;
    private boolean showHardpoints;
    private boolean rotatePreview;
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

    /** Advances presentation-only animation and executes the selected validation view. */
    @Override
    public void render() {
        float deltaSeconds = Math.max(0.000_001f, Gdx.graphics.getDeltaTime());
        if (handleInput()) {
            return;
        }

        elapsedSeconds += deltaSeconds;
        frameTimes.recordSeconds(deltaSeconds);
        frameDrawCalls = 0;
        pipeline.render(deltaSeconds);
    }

    private boolean handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
            return true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
            viewMode = ValidationViewMode.REPRESENTATIVE;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
            viewMode = ValidationViewMode.TACTICAL;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) {
            viewMode = ValidationViewMode.CLOSE_UP;
            showHardpoints = true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.H)) {
            showHardpoints = !showHardpoints;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            rotatePreview = !rotatePreview;
        }
        return false;
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

        for (int index = 0; index < activeAsteroidCount(); index++) {
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

        for (int index = 0; index < activeShipCount(); index++) {
            ShipPose pose = shipPose(index);
            if (index == HERO_SHIP_INDEX && hasHeavyCorvette()) {
                drawHeavyCorvetteBase(pose);
                if (viewMode == ValidationViewMode.REPRESENTATIVE) {
                    drawDamageMark(pose, heroWidth(), heroHeight());
                }
                continue;
            }
            drawProceduralShip(index, pose);
        }

        drawValidationBeam();
        if (showHardpoints && hasHeavyCorvette()) {
            drawHardpointMarkers(shipPose(HERO_SHIP_INDEX));
        }
        batch.setColor(Color.WHITE);
        batch.end();
        frameDrawCalls += batch.renderCalls;
    }

    private void drawProceduralShip(int index, ShipPose pose) {
        float classScale = 0.76f + (index % 5) * 0.12f;
        if (viewMode == ValidationViewMode.TACTICAL) {
            classScale *= 1.65f;
        }
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
        if (viewMode == ValidationViewMode.REPRESENTATIVE && index % 11 == 0) {
            drawDamageMark(pose, width, height);
        }
    }

    private void drawHeavyCorvetteBase(ShipPose pose) {
        drawOrientedHeavyCorvetteRegion(heavyCorvetteRegion, pose, Color.WHITE);
    }

    private void drawOrientedHeavyCorvetteRegion(TextureRegion region, ShipPose pose, Color tint) {
        float width = heroWidth();
        float height = heroHeight();
        batch.setColor(tint);
        batch.draw(
                region,
                pose.x - width * heavyCorvetteSpec.pivotX(),
                pose.y - height * heavyCorvetteSpec.pivotY(),
                width * heavyCorvetteSpec.pivotX(),
                height * heavyCorvetteSpec.pivotY(),
                width,
                height,
                SpriteOrientationTransform.horizontalScale(heavyCorvetteSpec),
                1f,
                pose.rotationDegrees);
    }

    private void drawEffects(float deltaSeconds) {
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();

        for (int index = 0; index < activeShipCount(); index++) {
            ShipPose pose = shipPose(index);
            if (index == HERO_SHIP_INDEX && hasHeavyCorvette()) {
                drawHeavyCorvetteEmissive(pose);
                drawHeavyCorvetteEngineGlows(pose);
            } else {
                float radians = pose.rotationDegrees * MathUtils.degreesToRadians;
                float glowX = pose.x - MathUtils.cos(radians) * 31f;
                float glowY = pose.y - MathUtils.sin(radians) * 31f;
                float pulse = 0.82f + MathUtils.sin(elapsedSeconds * 8f + index * 0.37f) * 0.18f;
                float size = 34f * pulse * (viewMode == ValidationViewMode.TACTICAL ? 1.4f : 1f);
                batch.setColor(0.20f, 0.55f, 1f, 0.58f);
                batch.draw(glowRegion, glowX - size * 0.5f, glowY - size * 0.5f, size, size);
            }
        }

        int particlesPerShip = Math.max(1, activeParticleCount() / activeShipCount());
        for (int index = 0; index < activeParticleCount(); index++) {
            int shipIndex = index % activeShipCount();
            int localIndex = index / activeShipCount();
            ShipPose pose = shipPose(shipIndex);
            float phase = (localIndex + elapsedSeconds * 26f + hash01(index, 607)) % particlesPerShip;

            if (shipIndex == HERO_SHIP_INDEX && hasHeavyCorvette()) {
                drawHeavyCorvetteParticle(index, phase, particlesPerShip, pose);
            } else {
                drawProceduralParticle(index, phase, particlesPerShip, pose);
            }
        }

        ShipPose shieldPose = shipPose(HERO_SHIP_INDEX);
        float shieldPulse = hasHeavyCorvette()
                ? Math.max(heroWidth(), heroHeight()) * 1.18f
                : 88f + MathUtils.sin(elapsedSeconds * 4f) * 12f;
        if (hasHeavyCorvette()) {
            shieldPulse += MathUtils.sin(elapsedSeconds * 4f) * 10f * heroRenderScale();
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

    private void drawProceduralParticle(int index, float phase, int particlesPerShip, ShipPose pose) {
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

    private void drawHeavyCorvetteEmissive(ShipPose pose) {
        if (heavyCorvetteEmissiveRegion == null) {
            return;
        }
        float pulse = 0.82f + MathUtils.sin(elapsedSeconds * 5.5f) * 0.12f;
        drawOrientedHeavyCorvetteRegion(
                heavyCorvetteEmissiveRegion,
                pose,
                new Color(1f, 0.86f, 0.58f, pulse));
    }

    private void drawHeavyCorvetteEngineGlows(ShipPose pose) {
        float pulse = 0.82f + MathUtils.sin(elapsedSeconds * 8f) * 0.18f;
        for (VisualHardpoint hardpoint : heavyCorvetteSpec.hardpoints()) {
            if (hardpoint.type() != VisualHardpointType.ENGINE) {
                continue;
            }
            transformHardpoint(pose, hardpoint, transformedHardpoint);
            float size = 30f * pulse * (float) Math.sqrt(heroRenderScale());
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
        float direction = (pose.rotationDegrees
                        + SpriteOrientationTransform.directionDegrees(
                                heavyCorvetteSpec,
                                engine.directionDegrees()))
                * MathUtils.degreesToRadians;
        float visualScale = (float) Math.sqrt(heroRenderScale());
        float distance = 5f * visualScale + phase * 2.25f * visualScale;
        float side = (hash01(particleIndex, 701) - 0.5f) * (5f + phase * 0.25f) * visualScale;
        float x = transformedHardpoint.x
                + MathUtils.cos(direction) * distance
                - MathUtils.sin(direction) * side;
        float y = transformedHardpoint.y
                + MathUtils.sin(direction) * distance
                + MathUtils.cos(direction) * side;
        float alpha = Math.max(0.05f, 1f - phase / particlesPerShip);
        float size = PARTICLE_SIZE * (0.55f + alpha) * visualScale;
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
                "Stage 8.5 Graphics Validation | libGDX %s | %dx%d | mode %s",
                Version.VERSION,
                Gdx.graphics.getWidth(),
                Gdx.graphics.getHeight(),
                viewMode.label);
        String line2 = String.format(
                Locale.ROOT,
                "ships %d | asteroids %d | particles %d | review objects %d",
                activeShipCount(),
                activeAsteroidCount(),
                activeParticleCount(),
                activeShipCount() + activeAsteroidCount() + activeParticleCount());
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
                ? "hero REAL HEAVY CORVETTE | source "
                        + heavyCorvetteSpec.sourceFacing()
                        + " -> runtime RIGHT | emissive "
                        + (heavyCorvetteEmissiveRegion == null ? "MISSING/OPTIONAL" : "ON")
                : "hero PROCEDURAL FALLBACK | missing " + heavyCorvetteSpec.baseTexturePath();
        String line6 = "1 representative | 2 tactical | 3 close-up | H hardpoints "
                + (showHardpoints ? "ON" : "OFF")
                + " | R rotate "
                + (rotatePreview ? "ON" : "OFF")
                + " | ESC exit";
        String line7 = "hardpoints: ENGINE cyan | WEAPON red | UTILITY yellow | runtime forward = RIGHT";

        font.setColor(0.82f, 0.91f, 1f, 1f);
        font.draw(batch, line1, 22f, camera.viewportHeight - 22f);
        font.draw(batch, line2, 22f, camera.viewportHeight - 44f);
        font.draw(batch, line3, 22f, camera.viewportHeight - 66f);
        font.draw(batch, line4, 22f, camera.viewportHeight - 88f);
        font.setColor(hasHeavyCorvette() ? 0.58f : 1f, hasHeavyCorvette() ? 1f : 0.72f, 0.62f, 1f);
        font.draw(batch, line5, 22f, camera.viewportHeight - 110f);
        font.setColor(0.82f, 0.91f, 1f, 1f);
        font.draw(batch, line6, 22f, camera.viewportHeight - 132f);
        if (showHardpoints) {
            font.draw(batch, line7, 22f, camera.viewportHeight - 154f);
        }
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
        ShipPose source = hasHeavyCorvette()
                ? shipPose(HERO_SHIP_INDEX)
                : shipPose(Math.min(2, activeShipCount() - 1));
        float sourceX = source.x;
        float sourceY = source.y;
        if (hasHeavyCorvette()) {
            VisualHardpoint muzzle = hardpoint("weapon_nose_primary");
            transformHardpoint(source, muzzle, transformedHardpoint);
            sourceX = transformedHardpoint.x;
            sourceY = transformedHardpoint.y;
        }

        float targetX;
        float targetY;
        if (viewMode == ValidationViewMode.CLOSE_UP) {
            float forward = SpriteOrientationTransform.directionDegrees(
                    heavyCorvetteSpec,
                    hardpoint("weapon_nose_primary").directionDegrees());
            float radians = (source.rotationDegrees + forward) * MathUtils.degreesToRadians;
            float beamLength = Math.min(camera.viewportWidth * 0.28f, 620f);
            targetX = sourceX + MathUtils.cos(radians) * beamLength;
            targetY = sourceY + MathUtils.sin(radians) * beamLength;
        } else {
            ShipPose target = shipPose(Math.min(
                    viewMode == ValidationViewMode.TACTICAL ? 1 : 3,
                    activeShipCount() - 1));
            targetX = target.x;
            targetY = target.y;
        }

        float dx = targetX - sourceX;
        float dy = targetY - sourceY;
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

    private void drawHardpointMarkers(ShipPose pose) {
        float markerSize = viewMode == ValidationViewMode.CLOSE_UP ? 22f : 14f;
        float directionLength = viewMode == ValidationViewMode.CLOSE_UP ? 46f : 28f;
        for (VisualHardpoint hardpoint : heavyCorvetteSpec.hardpoints()) {
            transformHardpoint(pose, hardpoint, transformedHardpoint);
            setHardpointColor(hardpoint.type());
            batch.draw(
                    glowRegion,
                    transformedHardpoint.x - markerSize * 0.5f,
                    transformedHardpoint.y - markerSize * 0.5f,
                    markerSize,
                    markerSize);

            float direction = pose.rotationDegrees
                    + SpriteOrientationTransform.directionDegrees(
                            heavyCorvetteSpec,
                            hardpoint.directionDegrees());
            batch.draw(
                    whiteTexture,
                    transformedHardpoint.x,
                    transformedHardpoint.y - 1f,
                    0f,
                    1f,
                    directionLength,
                    2f,
                    1f,
                    1f,
                    direction,
                    0,
                    0,
                    1,
                    1,
                    false,
                    false);
        }
        batch.setColor(Color.WHITE);
    }

    private void setHardpointColor(VisualHardpointType type) {
        switch (type) {
            case ENGINE:
                batch.setColor(0.15f, 0.85f, 1f, 0.92f);
                break;
            case WEAPON:
                batch.setColor(1f, 0.24f, 0.16f, 0.92f);
                break;
            case UTILITY:
                batch.setColor(1f, 0.86f, 0.16f, 0.92f);
                break;
            default:
                batch.setColor(Color.WHITE);
                break;
        }
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
        float localX = SpriteOrientationTransform.localX(
                heavyCorvetteSpec,
                hardpoint.normalizedX(),
                heroRenderScale());
        float localY = SpriteOrientationTransform.localY(
                heavyCorvetteSpec,
                hardpoint.normalizedY(),
                heroRenderScale());
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

    private int activeShipCount() {
        switch (viewMode) {
            case TACTICAL:
                return TACTICAL_SHIP_COUNT;
            case CLOSE_UP:
                return 1;
            case REPRESENTATIVE:
            default:
                return profile.shipCount();
        }
    }

    private int activeAsteroidCount() {
        switch (viewMode) {
            case TACTICAL:
                return TACTICAL_ASTEROID_COUNT;
            case CLOSE_UP:
                return CLOSE_UP_ASTEROID_COUNT;
            case REPRESENTATIVE:
            default:
                return profile.asteroidCount();
        }
    }

    private int activeParticleCount() {
        switch (viewMode) {
            case TACTICAL:
                return TACTICAL_PARTICLE_COUNT;
            case CLOSE_UP:
                return CLOSE_UP_PARTICLE_COUNT;
            case REPRESENTATIVE:
            default:
                return profile.particleCount();
        }
    }

    private float heroRenderScale() {
        switch (viewMode) {
            case TACTICAL:
                return 2.2f;
            case CLOSE_UP:
                return 6.0f;
            case REPRESENTATIVE:
            default:
                return 1f;
        }
    }

    private float heroWidth() {
        return heavyCorvetteSpec.worldWidth() * heroRenderScale();
    }

    private float heroHeight() {
        return heavyCorvetteSpec.worldHeight() * heroRenderScale();
    }

    private ShipPose shipPose(int index) {
        switch (viewMode) {
            case TACTICAL:
                return tacticalShipPose(index);
            case CLOSE_UP:
                return closeUpShipPose(index);
            case REPRESENTATIVE:
            default:
                return representativeShipPose(index);
        }
    }

    private ShipPose representativeShipPose(int index) {
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
        float rotation = index == HERO_SHIP_INDEX && rotatePreview
                ? previewRotationDegrees()
                : MathUtils.sin(elapsedSeconds * 0.23f + index * 0.37f) * 16f;
        return new ShipPose(x, y, rotation);
    }

    private ShipPose tacticalShipPose(int index) {
        if (index == HERO_SHIP_INDEX) {
            return new ShipPose(
                    camera.viewportWidth * 0.30f,
                    camera.viewportHeight * 0.48f,
                    rotatePreview ? previewRotationDegrees() : 0f);
        }
        float[][] normalized = {
            {0.62f, 0.48f},
            {0.77f, 0.68f},
            {0.79f, 0.27f},
            {0.52f, 0.73f},
            {0.54f, 0.22f},
            {0.90f, 0.48f}
        };
        int point = Math.min(index - 1, normalized.length - 1);
        return new ShipPose(
                camera.viewportWidth * normalized[point][0],
                camera.viewportHeight * normalized[point][1],
                0f);
    }

    private ShipPose closeUpShipPose(int index) {
        return new ShipPose(
                camera.viewportWidth * 0.47f,
                camera.viewportHeight * 0.44f,
                rotatePreview ? previewRotationDegrees() : 0f);
    }

    private float previewRotationDegrees() {
        return (elapsedSeconds * 18f) % 360f;
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

    private enum ValidationViewMode {
        REPRESENTATIVE("REPRESENTATIVE"),
        TACTICAL("TACTICAL"),
        CLOSE_UP("CLOSE-UP");

        private final String label;

        ValidationViewMode(String label) {
            this.label = label;
        }
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
