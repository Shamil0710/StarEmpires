package com.spacesim.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import com.spacesim.content.Stage22FactionProfileCatalog;
import com.spacesim.content.Stage22FactionProfileLoader;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;

/**
 * Presentation-only character roster strip for the two production-complete core factions.
 *
 * <p>The overlay owns no simulation state. It resolves visual art from the selected faction and
 * renders six transparent character cut-outs over the faction inspector. Missing art degrades to
 * no overlay and therefore cannot affect generation, persistence, AI, economy or combat.</p>
 *
 * <p>Faction identity is resolved only through the governed Stage-22 systemic-profile catalog.
 * The overlay never invents, aliases or rewrites runtime faction IDs. It selects presentation art
 * from the profile package key after the catalog has resolved the authoritative stable faction ID.</p>
 */
public final class FactionCharacterPortraitOverlay implements Disposable {
    private static final String EMPIRE_ASSET = "assets/characters/empire/character_roster.png";
    private static final String INDUSTRIAL_UNION_ASSET =
            "assets/characters/industrial_union/character_roster.png";
    private static final String EMPIRE_PACKAGE = "core.empire";
    private static final String INDUSTRIAL_UNION_PACKAGE = "core.industrial_union";
    private static final int PORTRAIT_COUNT = 6;
    private static final int SOURCE_WIDTH = 56;
    private static final int SOURCE_HEIGHT = 84;
    private static final int ALPHA_CLEANUP_THRESHOLD = 8;
    private static final float OUTER_MARGIN = 18f;
    private static final float PANEL_PADDING = 10f;
    private static final float CARD_GAP = 6f;
    private static final Color PANEL = new Color(0.018f, 0.025f, 0.036f, 0.94f);
    private static final Color BORDER = new Color(0.42f, 0.35f, 0.20f, 0.90f);
    private static final Color CARD = new Color(0.055f, 0.065f, 0.078f, 0.98f);

    private final OrthographicCamera camera = new OrthographicCamera();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final Stage22FactionProfileCatalog factionProfiles = loadFactionProfilesOptional();
    private final Texture empire = loadOptional(EMPIRE_ASSET);
    private final Texture industrialUnion = loadOptional(INDUSTRIAL_UNION_ASSET);
    private int width;
    private int height;
    private boolean disposed;

    /** Loads optional presentation assets and binds the initial screen projection. */
    public FactionCharacterPortraitOverlay() {
        resize(Math.max(1, Gdx.graphics.getWidth()), Math.max(1, Gdx.graphics.getHeight()));
    }

    /** Updates the overlay projection after a window resize. */
    public void resize(int viewportWidth, int viewportHeight) {
        if (disposed || viewportWidth <= 0 || viewportHeight <= 0) {
            return;
        }
        width = viewportWidth;
        height = viewportHeight;
        camera.setToOrtho(false, viewportWidth, viewportHeight);
        camera.update();
        shapes.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);
    }

    /** Renders a six-person roster only while a core faction is selected on the factions tab. */
    public void render(GeneratedWorldUiSnapshot snapshot, Tab tab, UiSelection selection) {
        if (disposed || snapshot == null || tab != Tab.FACTIONS || selection == null
                || selection.kind() != SelectionKind.FACTION) {
            return;
        }
        var faction = snapshot.galaxy().factions().stream()
                .filter(value -> selection.stableId().equals(value.factionId()))
                .findFirst().orElse(null);
        if (faction == null) {
            return;
        }
        Texture roster = resolveRoster(faction.factionId());
        if (roster != null) {
            drawRoster(roster);
        }
    }

    private void drawRoster(Texture roster) {
        float maxStripWidth = Math.min(width * 0.48f, 430f);
        float cardWidth = (maxStripWidth - PANEL_PADDING * 2f
                - CARD_GAP * (PORTRAIT_COUNT - 1)) / PORTRAIT_COUNT;
        cardWidth = Math.max(42f, Math.min(SOURCE_WIDTH, cardWidth));
        float cardHeight = cardWidth * SOURCE_HEIGHT / SOURCE_WIDTH;
        float panelWidth = PANEL_PADDING * 2f + cardWidth * PORTRAIT_COUNT
                + CARD_GAP * (PORTRAIT_COUNT - 1);
        float panelHeight = PANEL_PADDING * 2f + cardHeight;
        float x = Math.max(OUTER_MARGIN, width - OUTER_MARGIN - panelWidth);
        float y = Math.max(OUTER_MARGIN, Math.min(54f, height - panelHeight - OUTER_MARGIN));

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(PANEL);
        shapes.rect(x, y, panelWidth, panelHeight);
        for (int index = 0; index < PORTRAIT_COUNT; index++) {
            float cardX = x + PANEL_PADDING + index * (cardWidth + CARD_GAP);
            shapes.setColor(CARD);
            shapes.rect(cardX, y + PANEL_PADDING, cardWidth, cardHeight);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(BORDER);
        shapes.rect(x, y, panelWidth, panelHeight);
        shapes.end();

        batch.begin();
        batch.setColor(Color.WHITE);
        for (int index = 0; index < PORTRAIT_COUNT; index++) {
            float drawX = x + PANEL_PADDING + index * (cardWidth + CARD_GAP);
            float drawY = y + PANEL_PADDING;
            batch.draw(roster, drawX, drawY, cardWidth, cardHeight,
                    index * SOURCE_WIDTH, 0, SOURCE_WIDTH, SOURCE_HEIGHT, false, false);
        }
        batch.end();
    }

    private Texture resolveRoster(String factionId) {
        RosterKind kind = resolveRosterKind(factionProfiles, factionId);
        if (kind == null) {
            return null;
        }
        return switch (kind) {
            case EMPIRE -> empire;
            case INDUSTRIAL_UNION -> industrialUnion;
        };
    }

    static RosterKind resolveRosterKind(Stage22FactionProfileCatalog profiles, String factionId) {
        if (profiles == null || factionId == null || factionId.isBlank()) {
            return null;
        }
        var profile = profiles.findProfileForFaction(factionId.strip());
        if (profile == null) {
            return null;
        }
        return switch (profile.packageKey()) {
            case EMPIRE_PACKAGE -> RosterKind.EMPIRE;
            case INDUSTRIAL_UNION_PACKAGE -> RosterKind.INDUSTRIAL_UNION;
            default -> null;
        };
    }

    private static Stage22FactionProfileCatalog loadFactionProfilesOptional() {
        try {
            return Stage22FactionProfileLoader.loadDefault();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Texture loadOptional(String path) {
        FileHandle handle = Gdx.files.internal(path);
        if (!handle.exists()) {
            return null;
        }
        Pixmap pixmap = new Pixmap(handle);
        for (int y = 0; y < pixmap.getHeight(); y++) {
            for (int x = 0; x < pixmap.getWidth(); x++) {
                int rgba = pixmap.getPixel(x, y);
                if ((rgba & 0xff) < ALPHA_CLEANUP_THRESHOLD) {
                    pixmap.drawPixel(x, y, rgba & 0xffffff00);
                }
            }
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }

    /** Releases portrait textures and graphics resources. */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (empire != null) {
            empire.dispose();
        }
        if (industrialUnion != null) {
            industrialUnion.dispose();
        }
        batch.dispose();
        shapes.dispose();
    }

    enum RosterKind {
        EMPIRE,
        INDUSTRIAL_UNION
    }
}
