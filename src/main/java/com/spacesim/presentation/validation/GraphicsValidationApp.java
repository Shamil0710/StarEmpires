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
import com.spacesim.presentation.asset.ShipVisualAssetSet;
import com.spacesim.presentation.asset.SpriteOrientationTransform;
import com.spacesim.presentation.asset.VisualHardpoint;
import com.spacesim.presentation.asset.VisualHardpointType;
import java.util.Locale;

/**
 * Reproducible Stage-8.5 rendering benchmark and authored-asset review application.
 *
 * <p>The representative view preserves the roadmap workload of 50 ships, 500 asteroid/background
 * objects and 2,000 procedural particles. The production-like heavy-corvette hero uses the approved
 * authored base/emissive/engine asset pack; its former procedural exhaust particles are redistributed
 * across the remaining ships so the particle workload remains comparable. Tactical and close-up views
 * retain the same orientation, framebuffer, shader and hardpoint path for manual review.</p>
 */
public final class GraphicsValidationApp extends ApplicationAdapter {
    private static final int FRAME_WINDOW_SIZE = 240;
    private static final int SPRITE_BATCH_CAPACITY = 4096;
    private static final float SHIP_BASE_WIDTH = 64f;
    private static final float SHIP_BASE_HEIGHT = 30f;
    private static final float ASTEROID_BASE_SIZE = 24f;
    private static final float PARTICLE_SIZE = 9f;
    private static final float VISIBLE_ALPHA_THRESHOLD = 8f;
    private static final float ATTACHMENT_ALPHA_THRESHOLD = 64f;
    private static final float ENGINE_VISIBLE_HEIGHT_RATIO = 0.12f;
    private static final int HERO_SHIP_INDEX = 0;
    private static final int TACTICAL_SHIP_COUNT = 7;
    private static final int TACTICAL_ASTEROID_COUNT = 140;
    private static final int TACTICAL_PARTICLE_COUNT = 560;
    private static final int CLOSE_UP_ASTEROID_COUNT = 40;

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
    private final ShipVisualAssetSet heavyCorvetteAssets = ProjectShipSprites.whiteHeavyCorvette01Assets();
    private final Vector2 transformedHardpoint = new Vector2();

    private OrthographicCamera camera;
    private SpriteBatch batch;
    private BitmapFont font;
    private Texture shipTexture;
    private Texture asteroidTexture;
    private Texture glowTexture;
    private Texture whiteTexture;
    private TextureRegion shipRegion;
    private TextureRegion asteroidRegion;
    private TextureRegion glowRegion;
    private TextureRegion whiteRegion;
    private LoadedTextureAsset heavyCorvetteBase;
    private LoadedTextureAsset heavyCorvetteEmissive;
    private LoadedTextureAsset heavyCorvetteDamage;
    private LoadedTextureAsset heavyCorvetteEngineIdle;
    private LoadedTextureAsset heavyCorvetteEngineThrust;
    private FrameBuffer sceneBuffer;
    private TextureRegion sceneRegion;
    private ShaderProgram postShader;
    private ValidationViewMode viewMode = ValidationViewMode.REPRESENTATIVE;
    private EnginePreviewState engineState = EnginePreviewState.THRUST;
    private boolean emissiveEnabled = true;
    private boolean damageEnabled;
    private boolean showHardpoints;
    private boolean rotatePreview;
    private boolean fullFrameCanvasMatch;
    private float elapsedSeconds;
    private int frameDrawCalls;

    /** Creates validation-only GPU fixtures, authored resources and ordered presentation passes. */
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
        whiteRegion = new TextureRegion(whiteTexture);
        loadHeavyCorvetteAssets();

        postShader = new ShaderProgram(POST_VERTEX_SHADER, POST_FRAGMENT_SHADER);
        if (!postShader.isCompiled()) {
            throw new IllegalStateException("Graphics validation post shader failed: " + postShader.getLog());
        }

        pipeline.register(PresentationLayer.BACKGROUND, "validation-scene-begin", this::beginScene);
        pipeline.register(PresentationLayer.WORLD, "validation-world", this::drawWorld);
        pipeline.register(PresentationLayer.EFFECTS, "validation-effects", this::drawEffects);
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

    /** Releases validation-only GPU resources. */
    @Override
    public void dispose() {
        disposeSceneBuffer();
        disposeAsset(heavyCorvetteBase);
        disposeAsset(heavyCorvetteEmissive);
        disposeAsset(heavyCorvetteDamage);
        disposeAsset(heavyCorvetteEngineIdle);
        disposeAsset(heavyCorvetteEngineThrust);
        if (postShader != null) {
            postShader.dispose();
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
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            engineState = engineState.next();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.D)) {
            damageEnabled = !damageEnabled;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.L)) {
            emissiveEnabled = !emissiveEnabled;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.H)) {
            showHardpoints = !showHardpoints;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            rotatePreview = !rotatePreview;
        }
        return false;
    }

    private void loadHeavyCorvetteAssets() {
        heavyCorvetteBase = loadAsset(heavyCorvetteAssets.baseTexturePath());
        heavyCorvetteEmissive = loadAsset(heavyCorvetteAssets.emissiveTexturePath());
        heavyCorvetteDamage = loadAsset(heavyCorvetteAssets.damageTexturePath());
        heavyCorvetteEngineIdle = loadAsset(heavyCorvetteAssets.engineIdleTexturePath());
        heavyCorvetteEngineThrust = loadAsset(heavyCorvetteAssets.engineThrustTexturePath());
        fullFrameCanvasMatch = validateFullFrameCanvas();
    }

    private LoadedTextureAsset loadAsset(String path) {
        FileHandle handle = Gdx.files.internal(path);
        if (!handle.exists()) {
            return null;
        }
        Pixmap pixmap = new Pixmap(handle);
        int width = pixmap.getWidth();
        int height = pixmap.getHeight();
        AlphaBounds visibleBounds = alphaBounds(pixmap, (int) VISIBLE_ALPHA_THRESHOLD);
        AlphaBounds attachmentBounds = alphaBounds(pixmap, (int) ATTACHMENT_ALPHA_THRESHOLD);
        if (attachmentBounds.isEmpty()) {
            attachmentBounds = visibleBounds;
        }
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return new LoadedTextureAsset(texture, new TextureRegion(texture), width, height, visibleBounds, attachmentBounds);
    }

    private AlphaBounds alphaBounds(Pixmap pixmap, int threshold) {
        int minX = pixmap.getWidth();
        int minY = pixmap.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < pixmap.getHeight(); y++) {
            for (int x = 0; x < pixmap.getWidth(); x++) {
                int alpha = pixmap.getPixel(x, y) & 0xff;
                if (alpha < threshold) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        return maxX < minX || maxY < minY
                ? AlphaBounds.empty()
                : new AlphaBounds(minX, minY, maxX, maxY);
    }

    private boolean validateFullFrameCanvas() {
        if (heavyCorvetteBase == null || heavyCorvetteEmissive == null || heavyCorvetteDamage == null) {
            return false;
        }
        return heavyCorvetteBase.width == heavyCorvetteEmissive.width
                && heavyCorvetteBase.height == heavyCorvetteEmissive.height
                && heavyCorvetteBase.width == heavyCorvetteDamage.width
                && heavyCorvetteBase.height == heavyCorvetteDamage.height;
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
            batch.draw(whiteRegion, x, y, size, size);
        }
        batch.setColor(Color.WHITE);
        batch.end();
        frameDrawCalls += batch.renderCalls;
    }

    private void drawWorld(float deltaSeconds) {
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
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
                drawFullFrameLayer(heavyCorvetteBase.region, pose, Color.WHITE);
                if (damageEnabled && heavyCorvetteDamage != null) {
                    drawFullFrameLayer(heavyCorvetteDamage.region, pose, Color.WHITE);
                }
            } else {
                drawProceduralShip(index, pose);
            }
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

    private void drawFullFrameLayer(TextureRegion region, ShipPose pose, Color tint) {
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
                drawHeavyCorvetteAuthoredEffects(pose);
            } else {
                drawProceduralEngineGlow(index, pose);
            }
        }

        drawProceduralParticleWorkload();
        drawShieldEffect();

        batch.setColor(Color.WHITE);
        batch.end();
        frameDrawCalls += batch.renderCalls;
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void drawHeavyCorvetteAuthoredEffects(ShipPose pose) {
        if (emissiveEnabled && heavyCorvetteEmissive != null) {
            float pulse = 0.90f + MathUtils.sin(elapsedSeconds * 4.5f) * 0.08f;
            drawFullFrameLayer(heavyCorvetteEmissive.region, pose, new Color(1f, 1f, 1f, pulse));
        }
        LoadedTextureAsset engineAsset = selectedEngineAsset();
        if (engineState != EnginePreviewState.OFF && engineAsset != null) {
            drawEngineVfxForAllHardpoints(pose, engineAsset);
            drawEngineGlows(pose);
        }
    }

    private void drawEngineVfxForAllHardpoints(ShipPose pose, LoadedTextureAsset engineAsset) {
        if (engineAsset.visibleBounds.isEmpty()) {
            return;
        }
        float targetVisibleHeight = heroHeight() * ENGINE_VISIBLE_HEIGHT_RATIO;
        float pixelScale = targetVisibleHeight / Math.max(1f, engineAsset.visibleBounds.height());
        float drawWidth = engineAsset.width * pixelScale;
        float drawHeight = engineAsset.height * pixelScale;
        float originX = engineAsset.attachmentBounds.minX * pixelScale;
        float originY = sourceAnchorYFromBottom(engineAsset) * pixelScale;
        float alpha = engineState == EnginePreviewState.IDLE ? 0.72f : 1f;
        batch.setColor(1f, 1f, 1f, alpha);

        for (VisualHardpoint hardpoint : heavyCorvetteSpec.hardpoints()) {
            if (hardpoint.type() != VisualHardpointType.ENGINE) {
                continue;
            }
            transformHardpoint(pose, hardpoint, transformedHardpoint);
            batch.draw(
                    engineAsset.region,
                    transformedHardpoint.x - originX,
                    transformedHardpoint.y - originY,
                    originX,
                    originY,
                    drawWidth,
                    drawHeight,
                    SpriteOrientationTransform.horizontalScale(heavyCorvetteSpec),
                    1f,
                    pose.rotationDegrees);
        }
    }

    private void drawEngineGlows(ShipPose pose) {
        float stateScale = engineState == EnginePreviewState.IDLE ? 0.70f : 1f;
        float pulse = 0.90f + MathUtils.sin(elapsedSeconds * 7f) * 0.10f;
        float size = heroHeight() * 0.075f * stateScale * pulse;
        batch.setColor(0.14f, 0.50f, 1f, 0.48f * stateScale);
        for (VisualHardpoint hardpoint : heavyCorvetteSpec.hardpoints()) {
            if (hardpoint.type() != VisualHardpointType.ENGINE) {
                continue;
            }
            transformHardpoint(pose, hardpoint, transformedHardpoint);
            batch.draw(
                    glowRegion,
                    transformedHardpoint.x - size * 0.5f,
                    transformedHardpoint.y - size * 0.5f,
                    size,
                    size);
        }
    }

    private void drawProceduralEngineGlow(int index, ShipPose pose) {
        float radians = pose.rotationDegrees * MathUtils.degreesToRadians;
        float glowX = pose.x - MathUtils.cos(radians) * 31f;
        float glowY = pose.y - MathUtils.sin(radians) * 31f;
        float pulse = 0.82f + MathUtils.sin(elapsedSeconds * 8f + index * 0.37f) * 0.18f;
        float size = 34f * pulse * (viewMode == ValidationViewMode.TACTICAL ? 1.4f : 1f);
        batch.setColor(0.20f, 0.55f, 1f, 0.58f);
        batch.draw(glowRegion, glowX - size * 0.5f, glowY - size * 0.5f, size, size);
    }

    private void drawProceduralParticleWorkload() {
        int particleCount = activeParticleCount();
        int candidateShipCount = activeShipCount() - (hasHeavyCorvette() ? 1 : 0);
        if (particleCount <= 0 || candidateShipCount <= 0) {
            return;
        }
        int particlesPerShip = Math.max(1, particleCount / candidateShipCount);
        for (int index = 0; index < particleCount; index++) {
            int shipIndex = hasHeavyCorvette() ? 1 + index % candidateShipCount : index % candidateShipCount;
            int localIndex = index / candidateShipCount;
            ShipPose pose = shipPose(shipIndex);
            float phase = (localIndex + elapsedSeconds * 26f + hash01(index, 607)) % particlesPerShip;
            drawProceduralParticle(index, phase, particlesPerShip, pose);
        }
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

    private void drawShieldEffect() {
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
    }

    private LoadedTextureAsset selectedEngineAsset() {
        switch (engineState) {
            case IDLE:
                return heavyCorvetteEngineIdle;
            case THRUST:
                return heavyCorvetteEngineThrust;
            case OFF:
            default:
                return null;
        }
    }

    private float sourceAnchorYFromBottom(LoadedTextureAsset asset) {
        if (asset.attachmentBounds.isEmpty()) {
            return asset.height * 0.5f;
        }
        float centerFromTop = (asset.attachmentBounds.minY + asset.attachmentBounds.maxY + 1) * 0.5f;
        return asset.height - centerFromTop;
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
                ? "hero REAL HEAVY CORVETTE | engine " + engineState.label
                        + " | emissive " + onOff(emissiveEnabled && heavyCorvetteEmissive != null)
                        + " | damage " + onOff(damageEnabled)
                        + " | canvas " + (fullFrameCanvasMatch ? "MATCH" : "MISMATCH")
                : "hero PROCEDURAL FALLBACK | missing " + heavyCorvetteAssets.baseTexturePath();
        String line6 = "1 representative | 2 tactical | 3 close-up | E engine | D damage | L emissive | H hardpoints "
                + onOff(showHardpoints)
                + " | R rotate " + onOff(rotatePreview)
                + " | ESC exit";
        String line7 = "authored hero exhaust | 2000 procedural particles redistributed to non-hero ships | runtime forward RIGHT";

        font.setColor(0.82f, 0.91f, 1f, 1f);
        float top = camera.viewportHeight - 22f;
        font.draw(batch, line1, 22f, top);
        font.draw(batch, line2, 22f, top - 22f);
        font.draw(batch, line3, 22f, top - 44f);
        font.draw(batch, line4, 22f, top - 66f);
        font.setColor(hasHeavyCorvette() ? 0.58f : 1f, hasHeavyCorvette() ? 1f : 0.72f, 0.62f, 1f);
        font.draw(batch, line5, 22f, top - 88f);
        font.setColor(0.82f, 0.91f, 1f, 1f);
        font.draw(batch, line6, 22f, top - 110f);
        font.draw(batch, line7, 22f, top - 132f);
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
                whiteRegion,
                sourceX,
                sourceY - 1.5f,
                0f,
                1.5f,
                length,
                3f,
                1f,
                1f,
                angle);
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
                    whiteRegion,
                    transformedHardpoint.x,
                    transformedHardpoint.y - 1f,
                    0f,
                    1f,
                    directionLength,
                    2f,
                    1f,
                    1f,
                    direction);
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

    private VisualHardpoint hardpoint(String id) {
        for (VisualHardpoint hardpoint : heavyCorvetteSpec.hardpoints()) {
            if (id.equals(hardpoint.id())) {
                return hardpoint;
            }
        }
        throw new IllegalStateException("Missing heavy-corvette hardpoint: " + id);
    }

    private Vector2 transformHardpoint(ShipPose pose, VisualHardpoint hardpoint, Vector2 output) {
        float scale = heroRenderScale();
        float localX = SpriteOrientationTransform.localX(
                heavyCorvetteSpec,
                hardpoint.normalizedX(),
                scale);
        float localY = SpriteOrientationTransform.localY(
                heavyCorvetteSpec,
                hardpoint.normalizedY(),
                scale);
        float radians = pose.rotationDegrees * MathUtils.degreesToRadians;
        float cos = MathUtils.cos(radians);
        float sin = MathUtils.sin(radians);
        return output.set(
                pose.x + localX * cos - localY * sin,
                pose.y + localX * sin + localY * cos);
    }

    private boolean hasHeavyCorvette() {
        return heavyCorvetteBase != null;
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
                return 0;
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
                return closeUpShipPose();
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

    private ShipPose closeUpShipPose() {
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

    private static void disposeAsset(LoadedTextureAsset asset) {
        if (asset != null) {
            asset.texture.dispose();
        }
    }

    private static String onOff(boolean enabled) {
        return enabled ? "ON" : "OFF";
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

    private enum EnginePreviewState {
        OFF("OFF"),
        IDLE("IDLE"),
        THRUST("THRUST");

        private final String label;

        EnginePreviewState(String label) {
            this.label = label;
        }

        private EnginePreviewState next() {
            switch (this) {
                case OFF:
                    return IDLE;
                case IDLE:
                    return THRUST;
                case THRUST:
                default:
                    return OFF;
            }
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

    private static final class LoadedTextureAsset {
        private final Texture texture;
        private final TextureRegion region;
        private final int width;
        private final int height;
        private final AlphaBounds visibleBounds;
        private final AlphaBounds attachmentBounds;

        private LoadedTextureAsset(
                Texture texture,
                TextureRegion region,
                int width,
                int height,
                AlphaBounds visibleBounds,
                AlphaBounds attachmentBounds) {
            this.texture = texture;
            this.region = region;
            this.width = width;
            this.height = height;
            this.visibleBounds = visibleBounds;
            this.attachmentBounds = attachmentBounds;
        }
    }

    private static final class AlphaBounds {
        private final int minX;
        private final int minY;
        private final int maxX;
        private final int maxY;

        private AlphaBounds(int minX, int minY, int maxX, int maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        private static AlphaBounds empty() {
            return new AlphaBounds(0, 0, -1, -1);
        }

        private boolean isEmpty() {
            return maxX < minX || maxY < minY;
        }

        private int height() {
            return isEmpty() ? 0 : maxY - minY + 1;
        }
    }
}
