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
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.presentation.asset.ProjectShipSprites;
import com.spacesim.presentation.asset.ShipSpriteSpec;
import com.spacesim.presentation.asset.ShipVisualAssetSet;
import com.spacesim.presentation.asset.SpriteOrientationTransform;
import com.spacesim.presentation.asset.VisualHardpoint;
import com.spacesim.presentation.asset.VisualHardpointType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated Stage-8.5 visual inspector for the complete heavy-corvette production asset pack.
 *
 * <p>The inspector loads base, emissive, damage, idle-engine and thrust-engine PNG resources,
 * validates full-frame canvas compatibility, measures visible alpha bounds, and attaches engine VFX
 * to the same normalized hardpoints used by the normal presentation contract. It is deliberately
 * separate from the representative performance spike so visual alignment can be inspected without
 * changing benchmark semantics.</p>
 */
public final class HeavyCorvetteAssetValidationApp extends ApplicationAdapter {
    private static final float VISIBLE_ALPHA_THRESHOLD = 8f;
    private static final float ATTACHMENT_ALPHA_THRESHOLD = 64f;
    private static final float ENGINE_VISIBLE_HEIGHT_RATIO = 0.12f;
    private static final int STAR_COUNT = 160;

    private final ShipSpriteSpec shipSpec = ProjectShipSprites.whiteHeavyCorvette01();
    private final ShipVisualAssetSet assetSet = ProjectShipSprites.whiteHeavyCorvette01Assets();
    private final Vector2 transformedHardpoint = new Vector2();

    private OrthographicCamera camera;
    private SpriteBatch batch;
    private BitmapFont font;
    private Texture whiteTexture;
    private Texture glowTexture;
    private TextureRegion whiteRegion;
    private TextureRegion glowRegion;

    private LoadedTextureAsset baseAsset;
    private LoadedTextureAsset emissiveAsset;
    private LoadedTextureAsset damageAsset;
    private LoadedTextureAsset engineIdleAsset;
    private LoadedTextureAsset engineThrustAsset;

    private EnginePreviewState engineState = EnginePreviewState.THRUST;
    private boolean emissiveEnabled = true;
    private boolean damageEnabled;
    private boolean hardpointsEnabled;
    private boolean rotatePreview;
    private boolean fullFrameCanvasMatch;
    private float elapsedSeconds;

    /** Loads the five-resource visual pack and creates validation-only GPU resources. */
    @Override
    public void create() {
        camera = new OrthographicCamera();
        batch = new SpriteBatch(1024);
        font = new BitmapFont();
        font.getData().setScale(1.05f);
        whiteTexture = createSolidTexture(Color.WHITE);
        glowTexture = createGlowTexture();
        whiteRegion = new TextureRegion(whiteTexture);
        glowRegion = new TextureRegion(glowTexture);

        baseAsset = loadAsset(assetSet.baseTexturePath());
        emissiveAsset = loadAsset(assetSet.emissiveTexturePath());
        damageAsset = loadAsset(assetSet.damageTexturePath());
        engineIdleAsset = loadAsset(assetSet.engineIdleTexturePath());
        engineThrustAsset = loadAsset(assetSet.engineThrustTexturePath());
        fullFrameCanvasMatch = validateFullFrameCanvas();

        resize(Math.max(1, Gdx.graphics.getWidth()), Math.max(1, Gdx.graphics.getHeight()));
    }

    /** Advances preview-only animation, handles controls and draws the current asset state. */
    @Override
    public void render() {
        float deltaSeconds = Math.max(0.000_001f, Gdx.graphics.getDeltaTime());
        if (handleInput()) {
            return;
        }
        elapsedSeconds += deltaSeconds;

        Gdx.gl.glClearColor(0.004f, 0.009f, 0.020f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        drawBackground();
        drawShipAndDamage();
        drawAdditiveLayers();
        drawHardpoints();
        drawHud();
    }

    /** Updates the orthographic pixel-space viewport. */
    @Override
    public void resize(int width, int height) {
        if (camera == null || width <= 0 || height <= 0) {
            return;
        }
        camera.setToOrtho(false, width, height);
        camera.update();
    }

    /** Releases validation-only textures, font and batch resources. */
    @Override
    public void dispose() {
        disposeAsset(baseAsset);
        disposeAsset(emissiveAsset);
        disposeAsset(damageAsset);
        disposeAsset(engineIdleAsset);
        disposeAsset(engineThrustAsset);
        if (whiteTexture != null) {
            whiteTexture.dispose();
        }
        if (glowTexture != null) {
            glowTexture.dispose();
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
            hardpointsEnabled = !hardpointsEnabled;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            rotatePreview = !rotatePreview;
        }
        return false;
    }

    private void drawBackground() {
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.begin();
        for (int index = 0; index < STAR_COUNT; index++) {
            float x = hash01(index, 17) * camera.viewportWidth;
            float y = hash01(index, 31) * camera.viewportHeight;
            float brightness = 0.35f + hash01(index, 47) * 0.55f;
            float size = 0.8f + hash01(index, 59) * 2.4f;
            batch.setColor(brightness * 0.60f, brightness * 0.75f, brightness, 0.65f);
            batch.draw(whiteRegion, x, y, size, size);
        }
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawShipAndDamage() {
        ShipPose pose = heroPose();
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.begin();
        if (baseAsset != null) {
            drawFullFrameLayer(baseAsset.region, pose, Color.WHITE);
        }
        if (damageEnabled && damageAsset != null) {
            drawFullFrameLayer(damageAsset.region, pose, Color.WHITE);
        }
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawAdditiveLayers() {
        ShipPose pose = heroPose();
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();

        if (emissiveEnabled && emissiveAsset != null) {
            float pulse = 0.90f + MathUtils.sin(elapsedSeconds * 4.5f) * 0.08f;
            drawFullFrameLayer(emissiveAsset.region, pose, new Color(1f, 1f, 1f, pulse));
        }

        LoadedTextureAsset engineAsset = selectedEngineAsset();
        if (engineState != EnginePreviewState.OFF && engineAsset != null) {
            drawEngineVfxForAllHardpoints(pose, engineAsset);
            drawEngineGlows(pose);
        }

        batch.setColor(Color.WHITE);
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void drawHardpoints() {
        if (!hardpointsEnabled || baseAsset == null) {
            return;
        }
        ShipPose pose = heroPose();
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.begin();
        float markerSize = 16f;
        float directionLength = 38f;
        for (VisualHardpoint hardpoint : shipSpec.hardpoints()) {
            transformHardpoint(pose, hardpoint, transformedHardpoint);
            setHardpointColor(hardpoint.type());
            batch.draw(
                    glowRegion,
                    transformedHardpoint.x - markerSize * 0.5f,
                    transformedHardpoint.y - markerSize * 0.5f,
                    markerSize,
                    markerSize);
            float direction = pose.rotationDegrees
                    + SpriteOrientationTransform.directionDegrees(shipSpec, hardpoint.directionDegrees());
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
        batch.end();
    }

    private void drawHud() {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        float top = camera.viewportHeight - 20f;
        font.setColor(0.82f, 0.92f, 1f, 1f);
        font.draw(
                batch,
                String.format(
                        Locale.ROOT,
                        "Stage 8.5 Heavy Corvette Asset Pack | libGDX %s | %dx%d",
                        Version.VERSION,
                        Gdx.graphics.getWidth(),
                        Gdx.graphics.getHeight()),
                18f,
                top);
        font.draw(batch, "runtime forward RIGHT | source LEFT | full-frame canvas "
                + (fullFrameCanvasMatch ? "MATCH" : "MISMATCH"), 18f, top - 22f);

        drawAssetHudLine("BASE", baseAsset, 18f, top - 50f);
        drawAssetHudLine("EMISSIVE", emissiveAsset, 18f, top - 72f);
        drawAssetHudLine("DAMAGE", damageAsset, 18f, top - 94f);
        drawAssetHudLine("ENGINE IDLE", engineIdleAsset, 18f, top - 116f);
        drawAssetHudLine("ENGINE THRUST", engineThrustAsset, 18f, top - 138f);

        font.setColor(0.92f, 0.95f, 1f, 1f);
        font.draw(
                batch,
                "E engine " + engineState.label
                        + " | D damage " + onOff(damageEnabled)
                        + " | L emissive " + onOff(emissiveEnabled)
                        + " | H hardpoints " + onOff(hardpointsEnabled)
                        + " | R rotate " + onOff(rotatePreview)
                        + " | ESC exit",
                18f,
                top - 170f);

        LoadedTextureAsset selectedEngine = selectedEngineAsset();
        if (selectedEngine != null && engineState != EnginePreviewState.OFF) {
            AlphaBounds anchor = selectedEngine.attachmentBounds;
            font.draw(
                    batch,
                    String.format(
                            Locale.ROOT,
                            "engine attachment source LEFT_CENTER from alpha core: x=%d, yCenter=%.1f px | visible alpha %s",
                            anchor.minX,
                            sourceAnchorYFromBottom(selectedEngine),
                            selectedEngine.visibleBounds.label()),
                    18f,
                    top - 192f);
        }

        font.setColor(Color.WHITE);
        batch.end();
    }

    private void drawAssetHudLine(String label, LoadedTextureAsset asset, float x, float y) {
        if (asset == null) {
            font.setColor(1f, 0.45f, 0.36f, 1f);
            font.draw(batch, label + " MISSING", x, y);
            return;
        }
        font.setColor(0.62f, 1f, 0.70f, 1f);
        font.draw(
                batch,
                String.format(
                        Locale.ROOT,
                        "%s %dx%d | alpha %s",
                        label,
                        asset.width,
                        asset.height,
                        asset.visibleBounds.label()),
                x,
                y);
    }

    private void drawFullFrameLayer(TextureRegion region, ShipPose pose, Color tint) {
        float width = heroWidth();
        float height = heroHeight();
        batch.setColor(tint);
        batch.draw(
                region,
                pose.x - width * shipSpec.pivotX(),
                pose.y - height * shipSpec.pivotY(),
                width * shipSpec.pivotX(),
                height * shipSpec.pivotY(),
                width,
                height,
                SpriteOrientationTransform.horizontalScale(shipSpec),
                1f,
                pose.rotationDegrees);
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

        for (VisualHardpoint hardpoint : shipSpec.hardpoints()) {
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
                    SpriteOrientationTransform.horizontalScale(shipSpec),
                    1f,
                    pose.rotationDegrees);
        }
    }

    private void drawEngineGlows(ShipPose pose) {
        float stateScale = engineState == EnginePreviewState.IDLE ? 0.70f : 1f;
        float pulse = 0.90f + MathUtils.sin(elapsedSeconds * 7f) * 0.10f;
        float size = heroHeight() * 0.075f * stateScale * pulse;
        batch.setColor(0.14f, 0.50f, 1f, 0.48f * stateScale);
        for (VisualHardpoint hardpoint : shipSpec.hardpoints()) {
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

    private LoadedTextureAsset selectedEngineAsset() {
        switch (engineState) {
            case IDLE:
                return engineIdleAsset;
            case THRUST:
                return engineThrustAsset;
            case OFF:
            default:
                return null;
        }
    }

    private boolean validateFullFrameCanvas() {
        if (baseAsset == null || emissiveAsset == null || damageAsset == null) {
            return false;
        }
        return baseAsset.width == emissiveAsset.width
                && baseAsset.height == emissiveAsset.height
                && baseAsset.width == damageAsset.width
                && baseAsset.height == damageAsset.height;
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
        return new LoadedTextureAsset(path, texture, new TextureRegion(texture), width, height, visibleBounds, attachmentBounds);
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

    private float sourceAnchorYFromBottom(LoadedTextureAsset asset) {
        if (asset.attachmentBounds.isEmpty()) {
            return asset.height * 0.5f;
        }
        float centerFromTop = (asset.attachmentBounds.minY + asset.attachmentBounds.maxY + 1) * 0.5f;
        return asset.height - centerFromTop;
    }

    private Vector2 transformHardpoint(ShipPose pose, VisualHardpoint hardpoint, Vector2 output) {
        float scale = heroRenderScale();
        float localX = SpriteOrientationTransform.localX(shipSpec, hardpoint.normalizedX(), scale);
        float localY = SpriteOrientationTransform.localY(shipSpec, hardpoint.normalizedY(), scale);
        float radians = pose.rotationDegrees * MathUtils.degreesToRadians;
        float cos = MathUtils.cos(radians);
        float sin = MathUtils.sin(radians);
        return output.set(
                pose.x + localX * cos - localY * sin,
                pose.y + localX * sin + localY * cos);
    }

    private void setHardpointColor(VisualHardpointType type) {
        switch (type) {
            case ENGINE:
                batch.setColor(0.15f, 0.85f, 1f, 0.95f);
                break;
            case WEAPON:
                batch.setColor(1f, 0.25f, 0.17f, 0.95f);
                break;
            case UTILITY:
                batch.setColor(1f, 0.86f, 0.18f, 0.95f);
                break;
            default:
                batch.setColor(Color.WHITE);
                break;
        }
    }

    private ShipPose heroPose() {
        float rotation = rotatePreview ? (elapsedSeconds * 18f) % 360f : 0f;
        return new ShipPose(camera.viewportWidth * 0.55f, camera.viewportHeight * 0.45f, rotation);
    }

    private float heroRenderScale() {
        float widthScale = camera.viewportWidth * 0.45f / shipSpec.worldWidth();
        float heightScale = camera.viewportHeight * 0.52f / shipSpec.worldHeight();
        return Math.max(1f, Math.min(widthScale, heightScale));
    }

    private float heroWidth() {
        return shipSpec.worldWidth() * heroRenderScale();
    }

    private float heroHeight() {
        return shipSpec.worldHeight() * heroRenderScale();
    }

    private static String onOff(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }

    private static void disposeAsset(LoadedTextureAsset asset) {
        if (asset != null) {
            asset.texture.dispose();
        }
    }

    private static Texture createSolidTexture(Color color) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
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
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    private static float hash01(int index, int salt) {
        int value = index * 0x45d9f3b + salt * 0x27d4eb2d;
        value = (value ^ (value >>> 16)) * 0x45d9f3b;
        value ^= value >>> 16;
        return (value & 0x7fffffff) / (float) Integer.MAX_VALUE;
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
        private final String path;
        private final Texture texture;
        private final TextureRegion region;
        private final int width;
        private final int height;
        private final AlphaBounds visibleBounds;
        private final AlphaBounds attachmentBounds;

        private LoadedTextureAsset(
                String path,
                Texture texture,
                TextureRegion region,
                int width,
                int height,
                AlphaBounds visibleBounds,
                AlphaBounds attachmentBounds) {
            this.path = path;
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

        private int width() {
            return isEmpty() ? 0 : maxX - minX + 1;
        }

        private int height() {
            return isEmpty() ? 0 : maxY - minY + 1;
        }

        private String label() {
            return isEmpty()
                    ? "EMPTY"
                    : String.format(Locale.ROOT, "[%d,%d]-[%d,%d] %dx%d", minX, minY, maxX, maxY, width(), height());
        }
    }
}
