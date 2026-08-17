package com.spacesim.economy;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
import com.spacesim.economy.Stage18LogisticsRuntime.HandlingCapability;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One instantiated Stage-18F station/outpost industrial node.
 *
 * <p>The node materializes only explicit infrastructure from its archetype: facility references,
 * physical station storage and a cargo-handling interface. Dynamic power/heat/labor allocation is
 * intentionally not invented here; Stage-18E projects each referenced facility only after the
 * world supplies an {@link Stage18FacilityRuntime.InstalledFacilityState}.</p>
 */
public final class Stage18StationIndustrialNode {
    private final String stationId;
    private final String archetypeId;
    private final String locationTag;
    private final List<InstalledFacilityReference> installedFacilities;
    private final Stage18StationStorage storage;
    private final HandlingCapability handlingCapability;

    private Stage18StationIndustrialNode(
            String stationId,
            String archetypeId,
            String locationTag,
            List<InstalledFacilityReference> installedFacilities,
            Stage18StationStorage storage,
            HandlingCapability handlingCapability) {
        this.stationId = stationId;
        this.archetypeId = archetypeId;
        this.locationTag = locationTag;
        this.installedFacilities = List.copyOf(installedFacilities);
        this.storage = storage;
        this.handlingCapability = handlingCapability;
    }

    /**
     * Instantiates one station infrastructure template with empty physical storage.
     *
     * @param stationId stable station/outpost identity
     * @param locationTag physical installation location
     * @param archetype explicit Stage-18F infrastructure composition
     * @param ontology authoritative Stage-18 ontology
     * @param products authoritative manufactured-product registry
     * @return instantiated node with explicit facility references and empty physical inventory
     */
    public static Stage18StationIndustrialNode instantiate(
            String stationId,
            String locationTag,
            StationArchetypeDefinition archetype,
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products) {
        requireText(stationId, "stationId");
        requireText(locationTag, "locationTag");
        Objects.requireNonNull(archetype, "archetype");
        Objects.requireNonNull(ontology, "ontology");
        Objects.requireNonNull(products, "products");
        if (!archetype.allowedLocationTags().contains(locationTag)) {
            throw new IllegalArgumentException(
                    "Station archetype " + archetype.id() + " cannot be installed at " + locationTag);
        }

        List<InstalledFacilityReference> references = new ArrayList<>();
        int index = 0;
        for (String definitionId : archetype.installedFacilityDefinitionIds()) {
            references.add(new InstalledFacilityReference(
                    stationId + ".facility." + index,
                    definitionId));
            index++;
        }
        Stage18StationStorage storage = new Stage18StationStorage(
                ontology,
                products,
                stationId,
                archetype.storageCapacityByClassKg(),
                java.util.Map.of(),
                java.util.Map.of());
        HandlingCapability handling = new HandlingCapability(
                stationId + ".handling",
                archetype.transferStorageClassIds(),
                archetype.transferMassRateKgPerSecond(),
                archetype.maxTransferUnitMassKg());
        return new Stage18StationIndustrialNode(
                stationId,
                archetype.id(),
                locationTag,
                references,
                storage,
                handling);
    }

    /** @return stable station/outpost identity */
    public String stationId() {
        return stationId;
    }

    /** @return Stage-18F infrastructure archetype ID */
    public String archetypeId() {
        return archetypeId;
    }

    /** @return physical station/outpost location tag */
    public String locationTag() {
        return locationTag;
    }

    /** @return explicit installed Stage-18E facility references */
    public List<InstalledFacilityReference> installedFacilities() {
        return installedFacilities;
    }

    /** @return canonical physical Stage-18F station storage */
    public Stage18StationStorage storage() {
        return storage;
    }

    /** @return station cargo-handling capability */
    public HandlingCapability handlingCapability() {
        return handlingCapability;
    }

    /**
     * Stable reference connecting a station composition to one Stage-18E facility definition.
     *
     * @param facilityInstanceId stable installed facility identity
     * @param facilityDefinitionId referenced Stage-18E facility definition ID
     */
    public record InstalledFacilityReference(String facilityInstanceId, String facilityDefinitionId) {
        /**
         * Validates one installed facility reference.
         *
         * @param facilityInstanceId stable installed facility identity
         * @param facilityDefinitionId referenced facility definition ID
         */
        public InstalledFacilityReference {
            facilityInstanceId = requireText(facilityInstanceId, "facilityInstanceId");
            facilityDefinitionId = requireText(facilityDefinitionId, "facilityDefinitionId");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
        return value;
    }
}
