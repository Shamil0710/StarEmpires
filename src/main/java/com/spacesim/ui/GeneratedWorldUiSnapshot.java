package com.spacesim.ui;

import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.SpriteBinding;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable read-only presentation state for the generated-world command interface. */
@SuppressWarnings("doclint:missing")
public record GeneratedWorldUiSnapshot(
        long worldSeed,
        long worldTick,
        StarSystemId activeSystemId,
        String activeSystemName,
        GalaxyStrategicMapSnapshot galaxy,
        List<LocalObjectView> localObjects,
        List<FreightView> freight,
        List<MilitaryView> military) {

    /**
     * Validates and freezes one frame of UI projection data.
     *
     * @param worldSeed exact generated-world seed
     * @param worldTick authoritative world tick
     * @param activeSystemId currently materialized system
     * @param activeSystemName player-facing active-system label
     * @param galaxy immutable global-map projection
     * @param localObjects selectable objects in the active system
     * @param freight persistent generated freight projections
     * @param military persistent ordinary military fleet projections
     */
    public GeneratedWorldUiSnapshot {
        Objects.requireNonNull(activeSystemId, "activeSystemId");
        activeSystemName = requireText(activeSystemName, "activeSystemName");
        Objects.requireNonNull(galaxy, "galaxy");
        localObjects = List.copyOf(Objects.requireNonNull(localObjects, "localObjects"));
        freight = List.copyOf(Objects.requireNonNull(freight, "freight"));
        military = List.copyOf(Objects.requireNonNull(military, "military"));
    }

    /** Selectable local-system object families. */
    public enum ObjectKind {
        /** Ordinary physical freight or ECS ship. */ FLEET,
        /** Generated major, independent or industrial station. */ STATION,
        /** Generated extraction outpost bound to a finite source. */ EXTRACTION_OUTPOST,
        /** Generated finite resource without a commissioned outpost. */ RESOURCE,
        /** Generated anomaly, derelict or resource phenomenon. */ SPECIAL_LOCATION,
        /** Generated resource-field navigation anchor. */ RESOURCE_ANCHOR,
        /** Generated jump-arrival navigation anchor. */ JUMP_ANCHOR,
        /** Other ordinary local ECS object. */ LOCAL_ENTITY
    }

    /**
     * One selectable object rendered on the current-system map.
     *
     * @param stableId persistent or canonical identity
     * @param kind presentation object family
     * @param name player-facing primary label
     * @param subtitle concise role/state label
     * @param systemId owning system
     * @param position authoritative local physical position
     * @param factionId stable owner/controller ID, or empty when unknown/unowned
     * @param factionName player-facing owner/controller label
     * @param sprite optional minimum-pack sprite binding
     * @param sections structured inspector content
     */
    public record LocalObjectView(
            String stableId,
            ObjectKind kind,
            String name,
            String subtitle,
            StarSystemId systemId,
            LocalPhysicalPosition position,
            String factionId,
            String factionName,
            SpriteBinding sprite,
            List<InfoSection> sections) implements Comparable<LocalObjectView> {
        /**
         * Validates one selectable object projection.
         *
         * @param stableId persistent or canonical identity
         * @param kind presentation object family
         * @param name player-facing primary label
         * @param subtitle concise role/state label
         * @param systemId owning system
         * @param position authoritative local physical position
         * @param factionId stable owner/controller ID, or empty when unknown/unowned
         * @param factionName player-facing owner/controller label
         * @param sprite optional minimum-pack sprite binding
         * @param sections structured inspector content
         */
        public LocalObjectView {
            stableId = requireText(stableId, "stableId");
            Objects.requireNonNull(kind, "kind");
            name = requireText(name, "name");
            subtitle = requireText(subtitle, "subtitle");
            Objects.requireNonNull(systemId, "systemId");
            Objects.requireNonNull(position, "position");
            factionId = factionId == null ? "" : factionId.strip();
            factionName = factionName == null || factionName.isBlank() ? "Не определена" : factionName.strip();
            sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        }

        @Override
        public int compareTo(LocalObjectView other) {
            LocalObjectView checked = Objects.requireNonNull(other, "other");
            int kindOrder = Integer.compare(kind.ordinal(), checked.kind.ordinal());
            return kindOrder != 0 ? kindOrder : stableId.compareTo(checked.stableId);
        }
    }

    /** One generated freight/order row for the logistics tab. */
    public record FreightView(
            long fleetId,
            String name,
            String factionId,
            String factionName,
            String phase,
            String hullId,
            String fitId,
            double cargoMassKg,
            double cargoCapacityKg,
            String commodityId,
            String sourceName,
            String destinationName,
            List<StarSystemId> route,
            int routeIndex,
            double deliveredMassKg,
            double deliveryDeadlineSeconds,
            long delayedDeliveryCount,
            List<InfoSection> sections) implements Comparable<FreightView> {
        /**
         * Validates one deterministic logistics projection.
         *
         * @param fleetId persistent fleet identity
         * @param name player-facing fleet label
         * @param factionId stable owner identity
         * @param factionName player-facing owner label
         * @param phase authoritative freight lifecycle phase
         * @param hullId canonical hull identity
         * @param fitId canonical fitted-role identity
         * @param cargoMassKg current conserved cargo mass
         * @param cargoCapacityKg physical hold capacity
         * @param commodityId active cargo commodity, or an em dash
         * @param sourceName source endpoint label
         * @param destinationName destination endpoint label
         * @param route ordered neighbor-only route
         * @param routeIndex current route index
         * @param deliveredMassKg conserved delivered order mass
         * @param deliveryDeadlineSeconds physical delivery deadline
         * @param delayedDeliveryCount recorded delayed deliveries
         * @param sections structured inspector content
         */
        public FreightView {
            name = requireText(name, "name");
            factionId = requireText(factionId, "factionId");
            factionName = requireText(factionName, "factionName");
            phase = requireText(phase, "phase");
            hullId = requireText(hullId, "hullId");
            fitId = requireText(fitId, "fitId");
            commodityId = commodityId == null || commodityId.isBlank() ? "—" : commodityId.strip();
            sourceName = sourceName == null || sourceName.isBlank() ? "—" : sourceName.strip();
            destinationName = destinationName == null || destinationName.isBlank() ? "—" : destinationName.strip();
            route = List.copyOf(Objects.requireNonNull(route, "route"));
            sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
            if (fleetId <= 0L || !Double.isFinite(cargoMassKg) || cargoMassKg < 0d
                    || !Double.isFinite(cargoCapacityKg) || cargoCapacityKg <= 0d
                    || !Double.isFinite(deliveredMassKg) || deliveredMassKg < 0d
                    || !Double.isFinite(deliveryDeadlineSeconds) || deliveryDeadlineSeconds < 0d
                    || delayedDeliveryCount < 0L || routeIndex < 0) {
                throw new IllegalArgumentException("Invalid freight presentation state");
            }
        }

        @Override
        public int compareTo(FreightView other) {
            return Long.compare(fleetId, Objects.requireNonNull(other, "other").fleetId);
        }
    }

    /** One ordinary persistent military fleet row for the military-forces tab. */
    public record MilitaryView(
            long fleetId,
            String name,
            String factionId,
            String factionName,
            String status,
            StarSystemId systemId,
            boolean inSystem,
            String hullId,
            String fitId,
            List<InfoSection> sections) implements Comparable<MilitaryView> {
        /**
         * Validates one immutable military presentation projection.
         *
         * @param fleetId ordinary persistent fleet identity
         * @param name display name
         * @param factionId stable owning-faction identity
         * @param factionName owning-faction display name
         * @param status localized current status
         * @param systemId current or destination system identity
         * @param inSystem whether the fleet is locally materialized
         * @param hullId fitted engineering hull identity
         * @param fitId fitted provisional demonstrator identity
         * @param sections inspector sections
         */
        public MilitaryView {
            if (fleetId <= 0L) {
                throw new IllegalArgumentException("Military FleetId must be positive");
            }
            name = requireText(name, "name");
            factionId = requireText(factionId, "factionId");
            factionName = requireText(factionName, "factionName");
            status = requireText(status, "status");
            Objects.requireNonNull(systemId, "systemId");
            hullId = requireText(hullId, "hullId");
            fitId = requireText(fitId, "fitId");
            sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        }

        @Override
        public int compareTo(MilitaryView other) {
            return Long.compare(fleetId, Objects.requireNonNull(other, "other").fleetId);
        }
    }

    /** Inspector section containing compact labelled values. */
    public record InfoSection(String title, List<InfoLine> lines) {
        /**
         * Validates one non-empty inspector section.
         *
         * @param title section heading
         * @param lines immutable label/value rows
         */
        public InfoSection {
            title = requireText(title, "title");
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("Inspector section cannot be empty");
            }
        }

        /**
         * Creates a section from alternating label/value strings.
         *
         * @param title section heading
         * @param labelValues alternating label/value strings
         * @return validated immutable inspector section
         */
        public static InfoSection of(String title, String... labelValues) {
            Objects.requireNonNull(labelValues, "labelValues");
            if (labelValues.length == 0 || (labelValues.length & 1) != 0) {
                throw new IllegalArgumentException("InfoSection requires label/value pairs");
            }
            ArrayList<InfoLine> lines = new ArrayList<>(labelValues.length / 2);
            for (int index = 0; index < labelValues.length; index += 2) {
                lines.add(new InfoLine(labelValues[index], labelValues[index + 1]));
            }
            return new InfoSection(title, lines);
        }
    }

    /** One labelled inspector value. */
    public record InfoLine(String label, String value) {
        /**
         * Validates a complete label/value pair.
         *
         * @param label optional compact value label
         * @param value player-facing value
         */
        public InfoLine {
            label = label == null ? "" : label.strip();
            value = value == null || value.isBlank() ? "—" : value.strip();
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }
}
