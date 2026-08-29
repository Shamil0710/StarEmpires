package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22ContentIdSyntaxAcceptanceTest {
    private static final Pattern CONTENT_ID = Pattern.compile(
            "[a-z][a-z0-9_-]*(?:\\.[a-z0-9][a-z0-9_-]*)+");
    private static final Set<String> LOCAL_ID_COLLECTIONS = Set.of(
            "slots",
            "hardpoints",
            "compartments",
            "interfaces");

    @Test
    void everyGovernedJsonDefinitionIdUsesStableDottedContentSyntax() throws IOException {
        Stage22ContentGovernanceCatalog governance = Stage22ContentGovernanceLoader.loadDefault();
        ClassLoader loader = Stage22ContentIdSyntaxAcceptanceTest.class.getClassLoader();
        List<String> invalid = new ArrayList<>();

        for (var source : governance.getSources()) {
            try (InputStream stream = loader.getResourceAsStream(source.resourcePath())) {
                if (stream == null) {
                    invalid.add(source.resourcePath() + " -> missing resource");
                    continue;
                }
                JsonValue root = new JsonReader().parse(
                        new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                inspect(root, source.resourcePath(), "$", false, invalid);
            }
        }

        assertTrue(invalid.isEmpty(), () -> "Governed definition IDs outside Stage-22 inventory syntax: " + invalid);
    }

    private static void inspect(
            JsonValue node,
            String source,
            String path,
            boolean localIdCollection,
            List<String> invalid) {
        if (node.isObject()) {
            for (JsonValue child = node.child; child != null; child = child.next) {
                String name = child.name == null ? "?" : child.name;
                String childPath = path + "." + name;
                if ("id".equals(name) && !localIdCollection) {
                    if (!child.isString() || !CONTENT_ID.matcher(child.asString()).matches()) {
                        invalid.add(source + " -> " + childPath + " = " + child.asString());
                    }
                }
                inspect(child, source, childPath, LOCAL_ID_COLLECTIONS.contains(name), invalid);
            }
            return;
        }
        if (node.isArray()) {
            int index = 0;
            for (JsonValue child = node.child; child != null; child = child.next) {
                inspect(child, source, path + "[" + index++ + "]", localIdCollection, invalid);
            }
        }
    }
}
