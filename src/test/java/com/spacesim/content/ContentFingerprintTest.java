package com.spacesim.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentFingerprintTest {
    @Test
    void fingerprintИмеетSha256ФорматИНеЗависитОтJsonWhitespace() throws IOException {
        String json = defaultJson();
        ContentCatalog compact = ContentCatalogLoader.parse(json);
        ContentCatalog padded = ContentCatalogLoader.parse("\n  " + json + "  \n");

        assertTrue(compact.getFingerprint().matches("[0-9a-f]{64}"));
        assertEquals(compact.getFingerprint(), padded.getFingerprint());
    }

    @Test
    void изменениеИгровогоПараметраМеняетSemanticFingerprint() throws IOException {
        String json = defaultJson();
        ContentCatalog original = ContentCatalogLoader.parse(json);
        String changedJson = json.replaceFirst("\\\"basePrice\\\":10\\.0", "\\\"basePrice\\\":11.0");
        ContentCatalog changed = ContentCatalogLoader.parse(changedJson);

        assertNotEquals(original.getFingerprint(), changed.getFingerprint());
    }

    private String defaultJson() throws IOException {
        try (InputStream stream = ContentFingerprintTest.class.getClassLoader()
                .getResourceAsStream(ContentCatalogLoader.DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IOException("Не найден test resource: " + ContentCatalogLoader.DEFAULT_RESOURCE);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
