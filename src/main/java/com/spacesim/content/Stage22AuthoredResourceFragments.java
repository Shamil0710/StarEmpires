package com.spacesim.content;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Fail-closed reader for large authored Stage-22 text resources stored as deterministic fragments.
 *
 * <p>Fragments are a repository-storage detail only. Their bytes are concatenated without separators
 * and the resulting document is still parsed by the ordinary accepted catalog loader. This helper
 * owns no gameplay state and rejects missing, empty or oversized fragment sets.</p>
 */
public final class Stage22AuthoredResourceFragments {
    private static final int MAX_DOCUMENT_BYTES = 2 * 1024 * 1024;

    private Stage22AuthoredResourceFragments() {
        throw new AssertionError("utility class");
    }

    /**
     * Reads a complete UTF-8 document from an explicitly ordered fragment list.
     *
     * @param anchor class whose class loader resolves the resources
     * @param resources ordered non-empty classpath resource paths
     * @param label diagnostic document label
     * @return exact concatenation of all fragment bytes decoded as UTF-8
     */
    public static String read(
            Class<?> anchor,
            List<String> resources,
            String label) {
        ClassLoader loader = Objects.requireNonNull(anchor, "anchor").getClassLoader();
        List<String> ordered = List.copyOf(Objects.requireNonNull(resources, "resources"));
        String checkedLabel = requireText(label, "label");
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException(checkedLabel + " fragment list must not be empty");
        }
        StringBuilder document = new StringBuilder();
        int totalBytes = 0;
        for (String resource : ordered) {
            String path = requireText(resource, "resource");
            try (InputStream stream = loader.getResourceAsStream(path)) {
                if (stream == null) {
                    throw new IllegalStateException("Missing " + checkedLabel + " fragment: " + path);
                }
                byte[] bytes = stream.readAllBytes();
                if (bytes.length == 0) {
                    throw new IllegalStateException("Empty " + checkedLabel + " fragment: " + path);
                }
                totalBytes = Math.addExact(totalBytes, bytes.length);
                if (totalBytes > MAX_DOCUMENT_BYTES) {
                    throw new IllegalStateException(checkedLabel + " fragment document exceeds size limit");
                }
                document.append(new String(bytes, StandardCharsets.UTF_8));
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot read " + checkedLabel + " fragment: " + path, exception);
            }
        }
        if (document.isEmpty()) {
            throw new IllegalStateException(checkedLabel + " fragment document is empty");
        }
        return document.toString();
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return checked;
    }
}
