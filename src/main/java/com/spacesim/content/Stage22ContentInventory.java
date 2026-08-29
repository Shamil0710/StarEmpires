package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.Stage22ContentGovernanceCatalog.ContentDisposition;
import com.spacesim.content.Stage22ContentGovernanceCatalog.HardcodedDefinition;
import com.spacesim.content.Stage22ContentGovernanceCatalog.SourceDefinition;
import com.spacesim.content.Stage22ContentGovernanceCatalog.SourceMaturity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Machine-readable Stage-22.0 inventory and reverse-reference report over governed content sources.
 *
 * <p>The inventory is diagnostic/governance data only. Existing catalog loaders remain the runtime
 * authorities for engineering, industry and legacy content. This class intentionally does not merge
 * the separate catalogs into a second gameplay registry.</p>
 */
public final class Stage22ContentInventory {
    private static final Pattern DOTTED_ID = Pattern.compile(
            "[a-z][a-z0-9_-]*(?:\\.[a-z0-9][a-z0-9_-]*)+");

    private final Stage22ContentGovernanceCatalog governance;
    private final List<DefinitionRecord> definitions;
    private final List<ReferenceRecord> references;
    private final List<SourceDigest> sourceDigests;
    private final Map<String, List<DefinitionRecord>> definitionsById;
    private final Map<String, List<ReferenceRecord>> referencesByTargetId;
    private final String fingerprint;

    private Stage22ContentInventory(
            Stage22ContentGovernanceCatalog governance,
            List<DefinitionRecord> definitions,
            List<ReferenceRecord> references,
            List<SourceDigest> sourceDigests) {
        this.governance = Objects.requireNonNull(governance, "Governance catalog not set");
        ArrayList<DefinitionRecord> orderedDefinitions = new ArrayList<>(definitions);
        orderedDefinitions.sort(Comparator
                .comparing(DefinitionRecord::id)
                .thenComparing(DefinitionRecord::source)
                .thenComparing(DefinitionRecord::jsonPath));
        this.definitions = List.copyOf(orderedDefinitions);

        ArrayList<ReferenceRecord> orderedReferences = new ArrayList<>(references);
        orderedReferences.sort(Comparator
                .comparing(ReferenceRecord::targetId)
                .thenComparing(ReferenceRecord::source)
                .thenComparing(ReferenceRecord::jsonPath)
                .thenComparing(value -> value.ownerDefinitionId() == null ? "" : value.ownerDefinitionId()));
        this.references = List.copyOf(orderedReferences);

        ArrayList<SourceDigest> orderedDigests = new ArrayList<>(sourceDigests);
        orderedDigests.sort(Comparator.comparing(SourceDigest::source));
        this.sourceDigests = List.copyOf(orderedDigests);
        this.definitionsById = groupDefinitions(this.definitions);
        this.referencesByTargetId = groupReferences(this.references);
        this.fingerprint = computeFingerprint();
    }

    /**
     * Loads the default Stage-22 governance and scans every governed content source from the classpath.
     *
     * @return deterministic immutable inventory
     */
    public static Stage22ContentInventory buildDefault() {
        Stage22ContentGovernanceCatalog governance = Stage22ContentGovernanceLoader.loadDefault();
        return build(governance, Stage22ContentInventory.class.getClassLoader());
    }

    /**
     * Builds an inventory from one governance catalog and class loader.
     *
     * @param governance validated governance catalog
     * @param classLoader resource class loader
     * @return immutable inventory
     */
    static Stage22ContentInventory build(
            Stage22ContentGovernanceCatalog governance,
            ClassLoader classLoader) {
        Objects.requireNonNull(governance, "Governance catalog not set");
        Objects.requireNonNull(classLoader, "Class loader not set");
        ArrayList<DefinitionRecord> definitions = new ArrayList<>();
        ArrayList<ReferenceRecord> references = new ArrayList<>();
        ArrayList<SourceDigest> sourceDigests = new ArrayList<>();

        for (SourceDefinition source : governance.getSources()) {
            byte[] bytes = readResource(classLoader, source.resourcePath());
            String json = new String(bytes, StandardCharsets.UTF_8);
            final JsonValue root;
            try {
                root = new JsonReader().parse(json);
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Governed JSON source cannot be parsed: " + source.resourcePath(), exception);
            }
            scan(root, source, "$", null, definitions, references);
            sourceDigests.add(new SourceDigest(source.resourcePath(), sha256(bytes), bytes.length));
        }

        for (HardcodedDefinition definition : governance.getHardcodedDefinitions()) {
            definitions.add(new DefinitionRecord(
                    definition.id(),
                    definition.source(),
                    definition.maturity(),
                    definition.disposition(),
                    "$hardcoded"));
            int ordinal = 0;
            for (String reference : definition.references()) {
                references.add(new ReferenceRecord(
                        definition.id(),
                        reference,
                        definition.source(),
                        "$hardcoded.references[" + ordinal++ + "]"));
            }
        }
        return new Stage22ContentInventory(governance, definitions, references, sourceDigests);
    }

    /** @return governance catalog that authorized this inventory */
    public Stage22ContentGovernanceCatalog governance() {
        return governance;
    }

    /** @return every discovered governed definition occurrence */
    public List<DefinitionRecord> definitions() {
        return definitions;
    }

    /** @return every discovered reverse-reference occurrence */
    public List<ReferenceRecord> references() {
        return references;
    }

    /** @return content-byte digests for every governed source */
    public List<SourceDigest> sourceDigests() {
        return sourceDigests;
    }

    /** @return deterministic inventory fingerprint */
    public String fingerprint() {
        return fingerprint;
    }

    /**
     * Returns all definition occurrences for one stable ID.
     *
     * @param id stable content ID
     * @return all definition occurrences for the ID
     */
    public List<DefinitionRecord> definitions(String id) {
        return definitionsById.getOrDefault(id, List.of());
    }

    /**
     * Returns every governed reference targeting one stable ID.
     *
     * @param id stable target content ID
     * @return every governed reference targeting the ID
     */
    public List<ReferenceRecord> referencesTo(String id) {
        return referencesByTargetId.getOrDefault(id, List.of());
    }

    /**
     * Returns references whose target has no definition occurrence in the governed source inventory.
     *
     * <p>Some external runtime identifiers can be legitimate; callers decide whether an unresolved
     * reference is an error. Keeping the raw diagnostic avoids silently treating unknown content as
     * valid or neutral.</p>
     *
     * @return immutable unresolved reference list
     */
    public List<ReferenceRecord> unresolvedReferences() {
        return references.stream()
                .filter(reference -> definitions(reference.targetId()).isEmpty())
                .toList();
    }

    /**
     * Verifies that all definitions sourced from provisional content have a non-preserve disposition.
     *
     * @throws IllegalStateException if any provisional definition is still implicitly preserved
     */
    public void requireExplicitProvisionalDisposition() {
        for (DefinitionRecord definition : definitions) {
            if (definition.maturity() == SourceMaturity.PROVISIONAL
                    && definition.disposition() == ContentDisposition.PRESERVE) {
                throw new IllegalStateException("Provisional Stage-22 definition has no disposition: "
                        + definition.id() + " from " + definition.source());
            }
        }
    }

    private static void scan(
            JsonValue node,
            SourceDefinition source,
            String path,
            String ownerDefinitionId,
            List<DefinitionRecord> definitions,
            List<ReferenceRecord> references) {
        if (node.isObject()) {
            String localOwner = ownerDefinitionId;
            JsonValue idNode = node.get("id");
            if (idNode != null && idNode.isString() && isDottedId(idNode.asString())) {
                localOwner = idNode.asString();
                definitions.add(new DefinitionRecord(
                        localOwner,
                        source.resourcePath(),
                        source.maturity(),
                        source.defaultDisposition(),
                        path + ".id"));
            }
            for (JsonValue child = node.child; child != null; child = child.next) {
                String childName = child.name == null ? "?" : child.name;
                String childPath = path + "." + childName;
                if (!"id".equals(childName) && isDottedId(childName)) {
                    references.add(new ReferenceRecord(localOwner, childName, source.resourcePath(), childPath + "#key"));
                }
                scanValue(child, source, childPath, localOwner, definitions, references, "id".equals(childName));
            }
            return;
        }
        scanValue(node, source, path, ownerDefinitionId, definitions, references, false);
    }

    private static void scanValue(
            JsonValue node,
            SourceDefinition source,
            String path,
            String ownerDefinitionId,
            List<DefinitionRecord> definitions,
            List<ReferenceRecord> references,
            boolean definitionIdField) {
        if (node.isObject()) {
            scan(node, source, path, ownerDefinitionId, definitions, references);
            return;
        }
        if (node.isArray()) {
            int index = 0;
            for (JsonValue child = node.child; child != null; child = child.next) {
                scanValue(child, source, path + "[" + index++ + "]", ownerDefinitionId, definitions, references, false);
            }
            return;
        }
        if (!definitionIdField && node.isString() && isDottedId(node.asString())) {
            references.add(new ReferenceRecord(ownerDefinitionId, node.asString(), source.resourcePath(), path));
        }
    }

    private static boolean isDottedId(String value) {
        return value != null && DOTTED_ID.matcher(value).matches();
    }

    private static byte[] readResource(ClassLoader classLoader, String resource) {
        try (InputStream stream = classLoader.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Governed content source is missing: " + resource);
            }
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read governed content source: " + resource, exception);
        }
    }

    private static Map<String, List<DefinitionRecord>> groupDefinitions(List<DefinitionRecord> values) {
        LinkedHashMap<String, ArrayList<DefinitionRecord>> mutable = new LinkedHashMap<>();
        for (DefinitionRecord value : values) {
            mutable.computeIfAbsent(value.id(), ignored -> new ArrayList<>()).add(value);
        }
        LinkedHashMap<String, List<DefinitionRecord>> immutable = new LinkedHashMap<>();
        mutable.forEach((id, records) -> immutable.put(id, List.copyOf(records)));
        return java.util.Collections.unmodifiableMap(immutable);
    }

    private static Map<String, List<ReferenceRecord>> groupReferences(List<ReferenceRecord> values) {
        LinkedHashMap<String, ArrayList<ReferenceRecord>> mutable = new LinkedHashMap<>();
        for (ReferenceRecord value : values) {
            mutable.computeIfAbsent(value.targetId(), ignored -> new ArrayList<>()).add(value);
        }
        LinkedHashMap<String, List<ReferenceRecord>> immutable = new LinkedHashMap<>();
        mutable.forEach((id, records) -> immutable.put(id, List.copyOf(records)));
        return java.util.Collections.unmodifiableMap(immutable);
    }

    private String computeFingerprint() {
        StringBuilder canonical = new StringBuilder(16_384);
        canonical.append("governance=").append(governance.getFingerprint()).append('\n');
        for (SourceDigest digest : sourceDigests) {
            canonical.append("source|").append(digest.source()).append('|').append(digest.sha256()).append('|')
                    .append(digest.byteLength()).append('\n');
        }
        for (DefinitionRecord definition : definitions) {
            canonical.append("definition|").append(definition.id()).append('|').append(definition.source()).append('|')
                    .append(definition.maturity()).append('|').append(definition.disposition()).append('|')
                    .append(definition.jsonPath()).append('\n');
        }
        for (ReferenceRecord reference : references) {
            canonical.append("reference|")
                    .append(reference.ownerDefinitionId() == null ? "" : reference.ownerDefinitionId()).append('|')
                    .append(reference.targetId()).append('|').append(reference.source()).append('|')
                    .append(reference.jsonPath()).append('\n');
        }
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    /** One governed content definition occurrence. */
    public record DefinitionRecord(
            String id,
            String source,
            SourceMaturity maturity,
            ContentDisposition disposition,
            String jsonPath) {
        /** Validates one governed definition occurrence. */
        public DefinitionRecord {
            id = Stage22ContentGovernanceCatalog.requireContentId(id, "Inventory definition ID");
            source = Stage22ContentGovernanceCatalog.requireNonBlank(source, "Inventory definition source");
            maturity = Objects.requireNonNull(maturity, "Inventory maturity not set");
            disposition = Objects.requireNonNull(disposition, "Inventory disposition not set");
            jsonPath = Stage22ContentGovernanceCatalog.requireNonBlank(jsonPath, "Inventory definition path");
        }
    }

    /** One reverse-reference occurrence to a dotted content/faction ID. */
    public record ReferenceRecord(
            String ownerDefinitionId,
            String targetId,
            String source,
            String jsonPath) {
        /** Validates one reverse-reference occurrence. */
        public ReferenceRecord {
            if (ownerDefinitionId != null) {
                ownerDefinitionId = Stage22ContentGovernanceCatalog.requireContentId(
                        ownerDefinitionId, "Reference owner definition ID");
            }
            targetId = Stage22ContentGovernanceCatalog.requireContentId(targetId, "Reference target ID");
            source = Stage22ContentGovernanceCatalog.requireNonBlank(source, "Reference source");
            jsonPath = Stage22ContentGovernanceCatalog.requireNonBlank(jsonPath, "Reference path");
        }
    }

    /** Raw-byte source digest contributing to the Stage-22 inventory fingerprint. */
    public record SourceDigest(String source, String sha256, int byteLength) {
        /** Validates one source digest record. */
        public SourceDigest {
            source = Stage22ContentGovernanceCatalog.requireNonBlank(source, "Digest source");
            sha256 = Stage22ContentGovernanceCatalog.requireNonBlank(sha256, "Digest SHA-256");
            if (sha256.length() != 64 || byteLength <= 0) {
                throw new IllegalArgumentException("Invalid Stage-22 source digest metadata");
            }
        }
    }
}
