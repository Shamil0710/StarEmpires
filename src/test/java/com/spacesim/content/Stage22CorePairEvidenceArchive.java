package com.spacesim.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Retains raw probe vectors and honest source provenance for review; never assigns a balance pass. */
public final class Stage22CorePairEvidenceArchive {
    private Stage22CorePairEvidenceArchive() { }

    public static void write(String id, Object evidence, String limitation) {
        if (!id.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("Invalid evidence ID");
        Map<String, Object> row = new TreeMap<>();
        row.put("evidenceId", id);
        row.put("buildCommitSha", git("rev-parse", "HEAD"));
        row.put("workingTreeDirty", !git("status", "--porcelain", "--untracked-files=no").isEmpty());
        row.put("scenarioSuiteVersion", Stage22CorePairBalanceCatalog.SUITE_VERSION);
        row.put("contentFingerprint", Stage22CorePairFreezeManifest.captureCurrent().freezeFingerprint());
        row.put("knownLimitations", List.of(limitation));
        row.put("evidence", evidence);
        Path target = Path.of("target", "stage22-evidence", id + ".json");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, json(row) + "\n", StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot retain M22.6 evidence", exception);
        }
        System.out.println("M22_6_EVIDENCE|" + row.get("buildCommitSha") + "|" + target);
    }

    private static String git(String... arguments) {
        try {
            var command = new java.util.ArrayList<String>();
            command.add("git");
            command.addAll(List.of(arguments));
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (process.waitFor() != 0) throw new IllegalStateException("Evidence source provenance unavailable: " + value);
            return value;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot determine evidence build SHA", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Evidence provenance interrupted", exception);
        }
    }

    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof String || value instanceof Enum<?>) return quote(value.toString());
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map) {
            var sorted = new TreeMap<String, Object>();
            map.forEach((key, item) -> sorted.put(key.toString(), item));
            return sorted.entrySet().stream().map(entry -> quote(entry.getKey()) + ":" + json(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value instanceof Iterable<?> values) {
            var rows = new java.util.ArrayList<String>();
            values.forEach(item -> rows.add(json(item)));
            return String.join(",", rows).transform(body -> "[" + body + "]");
        }
        if (value.getClass().isRecord()) {
            var fields = new TreeMap<String, Object>();
            for (var component : value.getClass().getRecordComponents()) {
                try {
                    fields.put(component.getName(), component.getAccessor().invoke(value));
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalArgumentException("Cannot serialize evidence record", exception);
                }
            }
            return json(fields);
        }
        throw new IllegalArgumentException("Unsupported evidence value: " + value.getClass());
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (c < 32) result.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    else result.append(c);
                }
            }
        }
        return result.append('"').toString();
    }
}
