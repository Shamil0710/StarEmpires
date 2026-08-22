package com.spacesim.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;

import java.util.Objects;

/** Owns resolution-sized Latin/Cyrillic fonts for the generated-world command UI. */
public final class GeneratedWorldUiFonts {
    private static final String REGULAR = "assets/fonts/DejaVuSans.ttf";
    private static final String BOLD = "assets/fonts/DejaVuSans-Bold.ttf";
    private static final String CYRILLIC =
            "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ"
                    + "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"
                    + "→Δ²—№«»×₽";

    private final BitmapFont title;
    private final BitmapFont body;
    private final BitmapFont small;
    private boolean disposed;

    /** Generates fonts for one immutable resolution metrics set. */
    public GeneratedWorldUiFonts(ResponsiveUiMetrics metrics) {
        ResponsiveUiMetrics checked = Objects.requireNonNull(metrics, "metrics");
        FreeTypeFontGenerator regular = new FreeTypeFontGenerator(Gdx.files.internal(REGULAR));
        FreeTypeFontGenerator bold = new FreeTypeFontGenerator(Gdx.files.internal(BOLD));
        try {
            this.title = generate(bold, checked.titleFontPixels());
            this.body = generate(regular, checked.bodyFontPixels());
            this.small = generate(regular, checked.smallFontPixels());
        } finally {
            regular.dispose();
            bold.dispose();
        }
    }

    /** @return bold title/navigation font */
    public BitmapFont title() {
        requireLive();
        return title;
    }

    /** @return primary body/inspector font */
    public BitmapFont body() {
        requireLive();
        return body;
    }

    /** @return secondary labels/status font */
    public BitmapFont small() {
        requireLive();
        return small;
    }

    /** Disposes all generated font textures exactly once. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        title.dispose();
        body.dispose();
        small.dispose();
    }

    private static BitmapFont generate(FreeTypeFontGenerator generator, int size) {
        FreeTypeFontGenerator.FreeTypeFontParameter parameters =
                new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameters.size = size;
        parameters.characters = FreeTypeFontGenerator.DEFAULT_CHARS + CYRILLIC;
        parameters.minFilter = Texture.TextureFilter.Linear;
        parameters.magFilter = Texture.TextureFilter.Linear;
        parameters.kerning = true;
        return generator.generateFont(parameters);
    }

    private void requireLive() {
        if (disposed) {
            throw new IllegalStateException("UI fonts are disposed");
        }
    }
}
