package com.spacesim.ui;

import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.ResolvedSprite;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.SpriteBinding;
import com.spacesim.presentation.asset.Stage22ProductionShipSpriteAdapter;
import com.spacesim.presentation.asset.Stage22ProductionShipVisualResolver.RuntimeVisualState;
import com.spacesim.presentation.asset.Stage22RuntimeSpriteEffects;
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
     * @param sprite optional minimum-pack or validated Stage-22 production sprite binding
     * @param sections structured inspector content
     * @param physicalLengthM physical or nominal length in metres
     * @param physicalWidthM physical or nominal width in metres
     * @param headingRad presentation heading in radians; zero points along +X
     * @param propulsionFraction normalized authoritative propulsion activity in [0,1]
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
            List<InfoSection> sections,
            double physicalLengthM,
            double physicalWidthM,
            double headingRad,
            double propulsionFraction) implements Comparable<LocalObjectView> {
        /** Compatibility projection for objects with nominal artwork dimensions only. */
        public LocalObjectView(String stableId, ObjectKind kind, String name, String subtitle,
                StarSystemId systemId, LocalPhysicalPosition position, String factionId,
                String factionName, SpriteBinding sprite, List<InfoSection> sections) {
            this(stableId, kind, name, subtitle, systemId, position, factionId, factionName,
                    sprite, sections, sprite == null ? 0d : sprite.nominalLengthM(),
                    sprite == null ? 0d : sprite.nominalWidthM(), 0d, 0d);
        }

        /** Compatibility projection preserving the historical physical-dimension constructor. */
        public LocalObjectView(String stableId, ObjectKind kind, String name, String subtitle,
                StarSystemId systemId, LocalPhysicalPosition position, String factionId,
                String factionName, SpriteBinding sprite, List<InfoSection> sections,
                double physicalLengthM, double physicalWidthM) {
            this(stableId, kind, name, subtitle, systemId, position, factionId, factionName,
                    sprite, sections, physicalLengthM, physicalWidthM, 0d, 0d);
        }

        /** Compatibility projection preserving the historical explicit-heading constructor. */
        public LocalObjectView(String stableId, ObjectKind kind, String name, String subtitle,
                StarSystemId systemId, LocalPhysicalPosition position, String factionId,
                String factionName, SpriteBinding sprite, List<InfoSection> sections,
                double physicalLengthM, double physicalWidthM, double headingRad) {
            this(stableId, kind, name, subtitle, systemId, position, factionId, factionName,
                    sprite, sections, physicalLengthM, physicalWidthM, headingRad, 0d);
        }

        /**
         * Preserves simulation-authoritative dimensions while passing artwork into the UI.
         * Production artwork receives the same runtime propulsion state as the projection, and the
         * final binding carries the same normalized value as presentation-only effect metadata.
         */
        public LocalObjectView withScale(ResolvedSprite resolved) {
            RuntimeVisualState runtimeState = propulsionFraction > 0d
                    ? RuntimeVisualState.THRUSTING : RuntimeVisualState.IDLE;
            ResolvedSprite selected = Stage22ProductionShipSpriteAdapter.upgradeCoreProjection(
                    stableId,
                    factionId,
                    Objects.requireNonNull(resolved, "resolved"),
                    runtimeState);
            SpriteBinding runtimeBinding = Stage22RuntimeSpriteEffects.withPropulsion(
                    selected.binding(), propulsionFraction);
            return new LocalObjectView(stableId, kind, name, subtitle, systemId, position,
                    factionId, factionName, runtimeBinding, sections,
                    selected.worldLengthM(), selected.worldWidthM(), headingRad, propulsionFraction);
        }

        /** Returns the same immutable projection with a presentation-only ship heading. */
        public LocalObjectView withHeadingRad(double resolvedHeadingRad) {
            return new LocalObjectView(stableId, kind, name, subtitle, systemId, position,
                    factionId, factionName, sprite, sections,
                    physicalLengthM, physicalWidthM, resolvedHeadingRad, propulsionFraction);
        }

        /**
         * Returns the same immutable projection with simulation-authoritative normalized propulsion activity.
         * This value is never inferred from speed or heading.
         */
        public LocalObjectView withPropulsionFraction(double resolvedPropulsionFraction) {
            return new LocalObjectView(stableId, kind, name, subtitle, systemId, position,
                    factionId, factionName, sprite, sections,
                    physicalLengthM, physicalWidthM, headingRad, resolvedPropulsionFraction);
        }

        /** Validates one selectable object projection. */
        public LocalObjectView {
            if (!Double.isFinite(physicalLengthM) || !Double.isFinite(physicalWidthM)
                    || physicalLengthM < 0d || physicalWidthM < 0d
                    || (sprite != null && (physicalLengthM == 0d || physicalWidthM == 0d))) {
                throw new IllegalArgumentException("invalid physical dimensions");
            }
            if (!Double.isFinite(headingRad)) {
                throw new IllegalArgumentException("headingRad must be finite");
            }
            if (!Double.isFinite(propulsionFraction)
                    || propulsionFraction < 0d || propulsionFraction > 1d) {
                throw new IllegalArgumentException("propulsionFraction must be finite and in [0,1]");
            }
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
        public InfoSection {
            title = requireText(title, "title");
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("Inspector section cannot be empty");
            }
        }

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
