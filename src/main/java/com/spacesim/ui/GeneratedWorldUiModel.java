package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.CanonicalRow;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.RuntimeEndpoint;
import com.spacesim.persistence.Stage20IndustrialEntityMaterializer.MaterializedIndustrialStation;
import com.spacesim.persistence.Stage20SourceOutpostMaterializer.MaterializedExtractionOutpost;
import com.spacesim.persistence.Stage20SourceSupplyMaterializer.MaterializedSource;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.ResolvedSprite;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.ShipRole;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.SpriteBinding;
import com.spacesim.ui.GeneratedWorldUiSnapshot.FreightView;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoLine;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.LocalObjectView;
import com.spacesim.ui.GeneratedWorldUiSnapshot.MilitaryView;
import com.spacesim.ui.GeneratedWorldUiSnapshot.ObjectKind;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.Stage20SpecialLocationWorld.LocationKind;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldFactionIdentityState;
import com.spacesim.world.calibration.Stage20StationPhysicalGeometryProfile;
import com.spacesim.ship.DerivedShipCalculator;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Builds selectable generated-world UI projections without owning or mutating simulation state. */
@SuppressWarnings("doclint:missing")
public final class GeneratedWorldUiModel {
    private static final String INFRASTRUCTURE_DOMAIN = "INFRASTRUCTURE_PLACEMENT";
    private static final String SPECIAL_DOMAIN = "SPECIAL_LOCATION";
    private static final ShipEngineeringCatalog MILITARY_ENGINEERING =
            Stage175ICombatTestContentPack.loadDoctrines();
    private static final DerivedShipCalculator MILITARY_CALCULATOR =
            new DerivedShipCalculator(MILITARY_ENGINEERING);

    private final long worldSeed;
    private final LiveRuntime runtime;
    private final ContentCatalog content;
    private final Stage20GeneratedCampaignPersistentState campaign;
    private final Stage20StationPhysicalGeometryProfile stationGeometry;

    /**
     * Creates a live read model and captures only the immutable generated-world authority once.
     *
     * @param worldSeed exact campaign seed
     * @param runtime live Stage-20.5 runtime
     * @param content installed content catalogue
     */
    public GeneratedWorldUiModel(long worldSeed, LiveRuntime runtime, ContentCatalog content) {
        this.worldSeed = worldSeed;
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.content = Objects.requireNonNull(content, "content");
        this.campaign = runtime.captureState().campaign();
        this.stationGeometry = Stage20StationPhysicalGeometryProfile.deriveCurrent();
    }

    /** @return current immutable UI snapshot over the live runtime */
    public GeneratedWorldUiSnapshot capture() {
        StarSystemId active = runtime.world().getActiveSystemId();
        String activeName = runtime.world().getTopology().findSystem(active).orElseThrow().name();
        GalaxyStrategicMapSnapshot galaxy = GalaxyStrategicMapModel.capture(
                runtime.world(), content, active, null);
        List<FreightView> freight = freightViews(galaxy);
        List<MilitaryView> military = militaryViews(galaxy);
        List<LocalObjectView> local = localObjects(active, galaxy, freight, military);
        return new GeneratedWorldUiSnapshot(
                worldSeed,
                runtime.world().getAuthoritativeWorldTick(),
                active,
                activeName,
                galaxy,
                local,
                freight,
                military);
    }

    private List<LocalObjectView> localObjects(
            StarSystemId active,
            GalaxyStrategicMapSnapshot galaxy,
            List<FreightView> freightViews,
            List<MilitaryView> militaryViews) {
        ArrayList<LocalObjectView> result = new ArrayList<>();
        Map<String, MaterializedIndustrialStation> industrial = new HashMap<>();
        runtime.industry().industrial().stations().forEach(value -> industrial.put(value.stationId(), value));
        Map<Long, FreightView> freightByFleet = new HashMap<>();
        freightViews.forEach(value -> freightByFleet.put(value.fleetId(), value));

        for (RuntimeEndpoint endpoint : runtime.infrastructure().endpoints()) {
            if (!endpoint.systemId().equals(active)) {
                continue;
            }
            MaterializedIndustrialStation station = industrial.get(endpoint.stationId());
            String ownerId = station == null ? controller(active, galaxy) : station.stableFactionId();
            boolean yard = station != null && !station.yards().isEmpty();
            ResolvedSprite sprite = Stage20MinimumPlayableSpriteCatalog.resolveStation(
                    endpoint.stationArchetypeId(), yard, stationGeometry);
            ArrayList<InfoSection> sections = new ArrayList<>();
            sections.add(identitySection(
                    endpoint.stationId(),
                    ownerId,
                    factionName(ownerId),
                    endpoint.systemId(),
                    endpoint.position()));
            sections.add(storageSection(endpoint.storage()));
            if (station != null) {
                sections.add(new InfoSection(
                        "Промышленный фит",
                        List.of(
                                new InfoLine("Архетип", station.stationArchetypeId()),
                                new InfoLine("Модули", joinIds(station.facilities().stream()
                                        .map(value -> value.definitionId()).toList())),
                                new InfoLine("Верфи", station.yards().isEmpty() ? "Нет" : joinIds(
                                        station.yards().stream()
                                                .map(value -> value.yardDefinitionId())
                                                .toList())))));
            }
            result.add(new LocalObjectView(
                    "station:" + endpoint.stationId(),
                    ObjectKind.STATION,
                    displayId(endpoint.stationId()),
                    yard ? "Орбитальная верфь" : endpoint.generatedIndustrial()
                            ? "Промышленная станция" : "Станция",
                    active,
                    endpoint.position(),
                    ownerId,
                    factionName(ownerId),
                    sprite.binding(),
                    sections));
        }

        Set<String> commissionedSources = new HashSet<>();
        for (MaterializedExtractionOutpost outpost : runtime.industry().sourceOutposts().outposts()) {
            if (!outpost.site().systemId().equals(active)) {
                continue;
            }
            commissionedSources.add(outpost.source().sourceId());
            String ownerId = controller(active, galaxy);
            SpriteBinding sprite = Stage20MinimumPlayableSpriteCatalog.resolveStation(
                    outpost.stationArchetypeId(), false, stationGeometry).binding();
            result.add(new LocalObjectView(
                    "outpost:" + outpost.site().siteId(),
                    ObjectKind.EXTRACTION_OUTPOST,
                    displayId(outpost.stationId()),
                    "Добывающий аванпост",
                    active,
                    outpost.source().position(),
                    ownerId,
                    factionName(ownerId),
                    sprite,
                    List.of(
                            identitySection(
                                    outpost.site().siteId(), ownerId, factionName(ownerId),
                                    active, outpost.source().position()),
                            InfoSection.of(
                                    "Добыча",
                                    "Ресурс", outpost.source().sourceState().outputCommodityId(),
                                    "Метод", outpost.site().extractionMethodId(),
                                    "Остаток", mass(outpost.source().sourceState().remainingAccessibleMassKg()),
                                    "Фракция извлечения", percent(
                                            outpost.source().sourceState().sourceRecoveryFraction())),
                            storageSection(outpost.storage()),
                            InfoSection.of(
                                    "Оборудование",
                                    "Объект", outpost.facilityState().definitionId(),
                                    "Состояние", outpost.facilityState().enabled() ? "Работает" : "Отключено",
                                    "Целостность", percent(outpost.facilityState().conditionFraction())))));
        }

        for (MaterializedSource source : runtime.industry().sourceOutposts().sources().sources()) {
            if (!source.systemId().equals(active) || commissionedSources.contains(source.sourceId())) {
                continue;
            }
            String type = source.sourceState().sourceTypeId();
            result.add(new LocalObjectView(
                    "resource:" + source.sourceId(),
                    ObjectKind.RESOURCE,
                    displayId(source.sourceId()),
                    "Конечное месторождение",
                    active,
                    source.position(),
                    "",
                    "Не принадлежит фракции",
                    Stage20MinimumPlayableSpriteCatalog.resolveResource(type, 180d, 140d).binding(),
                    List.of(
                            identitySection(source.sourceId(), "", "Не принадлежит фракции", active, source.position()),
                            InfoSection.of(
                                    "Ресурс",
                                    "Тип", type,
                                    "Товар", source.sourceState().outputCommodityId(),
                                    "Начальная масса", mass(source.sourceState().initialAccessibleMassKg()),
                                    "Остаток", mass(source.sourceState().remainingAccessibleMassKg()),
                                    "Содержание", percent(source.sourceState().gradeFraction())))));
        }

        addCanonicalStaticObjects(result, active);
        addFreightObjects(result, active, freightByFleet);
        addMilitaryObjects(result, active, militaryViews);
        addOrdinaryEntities(result, active);
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    private void addMilitaryObjects(
            List<LocalObjectView> result,
            StarSystemId active,
            List<MilitaryView> militaryViews) {
        for (MilitaryView military : militaryViews) {
            if (!military.inSystem() || !military.systemId().equals(active)) {
                continue;
            }
            FleetPlacementState placement = runtime.world()
                    .findFleet(new FleetId(military.fleetId())).orElseThrow();
            LocalPhysicalPosition position = runtime.arrival().materialization(active)
                    .physicalState(placement.localEntityId()).orElseThrow().position();
            SpriteBinding sprite = Stage20MinimumPlayableSpriteCatalog.resolveShip(
                    military.hullId(), ShipRole.MEDIUM_COMBAT, MILITARY_ENGINEERING).binding();
            result.add(new LocalObjectView(
                    "fleet:" + military.fleetId(),
                    ObjectKind.FLEET,
                    military.name(),
                    military.status(),
                    active,
                    position,
                    military.factionId(),
                    military.factionName(),
                    sprite,
                    military.sections()));
        }
    }

    private void addFreightObjects(
            List<LocalObjectView> result,
            StarSystemId active,
            Map<Long, FreightView> freightByFleet) {
        for (FreighterState state : runtime.freight().capture().freighters()) {
            if (!state.operational() || !state.currentSystemId().equals(active)) {
                continue;
            }
            FleetPlacementState placement = runtime.world().findFleet(state.fleetId()).orElse(null);
            if (placement == null || placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
                continue;
            }
            FreightView freight = freightByFleet.get(state.fleetId().value());
            List<InfoSection> sections = freight == null
                    ? List.of(identitySection(
                            state.fleetId().toString(), state.stableFactionId(),
                            factionName(state.stableFactionId()), active, state.physicalState().position()))
                    : freight.sections();
            result.add(new LocalObjectView(
                    "fleet:" + state.fleetId().value(),
                    ObjectKind.FLEET,
                    freight == null ? "Транспорт #" + state.fleetId().value() : freight.name(),
                    localizePhase(state.phase().name()),
                    active,
                    state.physicalState().position(),
                    state.stableFactionId(),
                    factionName(state.stableFactionId()),
                    runtime.freightSprite(state.fleetId()).binding(),
                    sections));
        }
    }

    private void addOrdinaryEntities(List<LocalObjectView> result, StarSystemId active) {
        var session = runtime.world().findSession(active).orElseThrow();
        Set<EntityId> freightEntityIds = runtime.world().getFleetPlacements().stream()
                .filter(value -> value.locationKind() == FleetLocationKind.IN_SYSTEM)
                .filter(value -> value.systemId().equals(active))
                .map(FleetPlacementState::localEntityId)
                .collect(java.util.stream.Collectors.toSet());
        for (Entity entity : session.getEngine().getEntities()) {
            var idComponent = entity.getComponent(com.spacesim.components.EntityIdComponent.class);
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (idComponent == null || identity == null || transform == null
                    || freightEntityIds.contains(idComponent.id)
                    || transform.position == null
                    || !Float.isFinite(transform.position.x)
                    || !Float.isFinite(transform.position.y)) {
                continue;
            }
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            String ownerId = faction == null ? "" : runtime.world()
                    .findFactionStableId(faction.factionId).orElse("");
            ShipComponent ship = entity.getComponent(ShipComponent.class);
            IdentityComponent.Kind identityKind = identity.kind;
            ObjectKind kind = identityKind == IdentityComponent.Kind.FLEET
                    ? ObjectKind.FLEET : ObjectKind.LOCAL_ENTITY;
            SpriteBinding sprite = ship == null
                    ? null
                    : Stage20MinimumPlayableSpriteCatalog.resolvePlayable(ship.type).binding();
            EntityDetailsUI.DetailsText legacy = EntityDetailsUI.describe(entity, session.getEntityRegistry());
            result.add(new LocalObjectView(
                    "entity:" + idComponent.id.value(),
                    kind,
                    legacy.title(),
                    identityKind == null ? "Локальный объект" : localizeIdentityKind(identityKind),
                    active,
                    LocalPhysicalPosition.origin().translated(transform.position.x, transform.position.y),
                    ownerId,
                    factionName(ownerId),
                    sprite,
                    List.of(
                            identitySection(
                                    idComponent.id.toString(), ownerId, factionName(ownerId), active,
                                    LocalPhysicalPosition.origin().translated(
                                            transform.position.x, transform.position.y)),
                            textSection("Состояние", legacy.body()))));
        }
    }

    private void addCanonicalStaticObjects(List<LocalObjectView> result, StarSystemId active) {
        Set<String> endpointIds = runtime.infrastructure().endpoints().stream()
                .map(RuntimeEndpoint::stationId)
                .collect(java.util.stream.Collectors.toSet());
        for (CanonicalRow row : campaign.materializedWorld().worldRows()) {
            if (INFRASTRUCTURE_DOMAIN.equals(row.domain())) {
                addInfrastructureAnchor(result, active, endpointIds, row);
            } else if (SPECIAL_DOMAIN.equals(row.domain())) {
                addSpecialLocation(result, active, row);
            }
        }
    }

    private void addInfrastructureAnchor(
            List<LocalObjectView> result,
            StarSystemId active,
            Set<String> endpointIds,
            CanonicalRow row) {
        List<String> values = row.values();
        if (values.size() != 9) {
            return;
        }
        StarSystemId system = new StarSystemId(Long.parseLong(values.get(0)));
        PlacementKind kind = PlacementKind.valueOf(values.get(1));
        if (!system.equals(active)
                || kind == PlacementKind.MAJOR_HUB_STATION
                || kind == PlacementKind.INDEPENDENT_STATION
                || endpointIds.contains(row.stableId())) {
            return;
        }
        LocalPhysicalPosition position = position(values, 3);
        ObjectKind objectKind = kind == PlacementKind.JUMP_ARRIVAL_ANCHOR
                ? ObjectKind.JUMP_ANCHOR : ObjectKind.RESOURCE_ANCHOR;
        result.add(new LocalObjectView(
                "anchor:" + row.stableId(),
                objectKind,
                displayId(row.stableId().substring(row.stableId().indexOf(':') + 1)),
                objectKind == ObjectKind.JUMP_ANCHOR ? "Зона прибытия" : "Ресурсная область",
                active,
                position,
                "",
                "Навигационная инфраструктура",
                null,
                List.of(
                        identitySection(row.stableId(), "", "Навигационная инфраструктура", active, position),
                        InfoSection.of(
                                "Навигация",
                                "Тип", kind.name(),
                                "Физическая геометрия", "Точечный якорь",
                                "Симуляционная authority", "Stage 20C"))));
    }

    private void addSpecialLocation(List<LocalObjectView> result, StarSystemId active, CanonicalRow row) {
        List<String> values = row.values();
        if (values.size() < 17) {
            return;
        }
        StarSystemId system = new StarSystemId(Long.parseLong(values.get(0)));
        if (!system.equals(active)) {
            return;
        }
        LocalPhysicalPosition position = position(values, 2);
        LocationKind kind = LocationKind.valueOf(values.get(7));
        SpriteBinding sprite = kind == LocationKind.DERELICT
                ? Stage20MinimumPlayableSpriteCatalog.resolveSpecialLocation(kind).binding()
                : null;
        result.add(new LocalObjectView(
                "special:" + row.stableId(),
                ObjectKind.SPECIAL_LOCATION,
                displayId(row.stableId()),
                localizeLocationKind(kind),
                active,
                position,
                "",
                "Не принадлежит фракции",
                sprite,
                List.of(
                        identitySection(row.stableId(), "", "Не принадлежит фракции", active, position),
                        InfoSection.of(
                                "Исследование",
                                "Архетип", values.get(6),
                                "Редкость", values.get(8),
                                "Требование сканирования", values.get(15),
                                "Опасность", values.get(16)),
                        InfoSection.of(
                                "Сигнатура",
                                "Тепловая мощность", power(values.get(9)),
                                "Факел двигателей", power(values.get(10)),
                                "ЭПР", format(Double.parseDouble(values.get(11))) + " м²",
                                "Радиоизлучение", power(values.get(13))))));
    }

    private List<FreightView> freightViews(GalaxyStrategicMapSnapshot galaxy) {
        var state = runtime.freight().capture();
        Map<String, TransportOrderState> orders = new TreeMap<>();
        state.orders().forEach(value -> orders.put(value.orderId(), value));
        Map<Long, Double> lots = new HashMap<>();
        state.cargoLots().forEach(value -> lots.merge(value.fleetId().value(), value.massKg(), Double::sum));
        ArrayList<FreightView> result = new ArrayList<>();
        for (FreighterState fleet : state.freighters()) {
            TransportOrderState order = orders.get(fleet.activeOrderId());
            String owner = factionName(fleet.stableFactionId());
            String commodity = order == null ? "—" : order.commodityId();
            String source = order == null ? "—" : displayId(order.sourceEndpointId());
            String destination = order == null ? "—" : displayId(order.destinationEndpointId());
            List<StarSystemId> route = order == null ? List.of() : order.orderedSystems();
            ArrayList<InfoSection> sections = new ArrayList<>();
            sections.add(InfoSection.of(
                    "Идентификация",
                    "Название", "Транспорт #" + fleet.fleetId().value(),
                    "FleetId", Long.toString(fleet.fleetId().value()),
                    "Фракция", owner,
                    "Фаза", localizePhase(fleet.phase().name())));
            sections.add(InfoSection.of(
                    "Корпус и фит",
                    "Корпус", fleet.hullId(),
                    "Фит", fleet.fitId(),
                    "Груз", mass(fleet.cargoMassKg()),
                    "Вместимость", mass(fleet.cargoCapacityKg()),
                    "Заполнение", percent(fleet.cargoMassKg() / fleet.cargoCapacityKg())));
            if (order != null) {
                sections.add(InfoSection.of(
                        "Маршрут",
                        "Товар", commodity,
                        "Откуда", source,
                        "Куда", destination,
                        "Путь", routeNames(route, galaxy),
                        "Текущий участок", (fleet.routeIndex() + 1) + " / " + route.size(),
                        "Доставлено", mass(order.deliveredMassKg()),
                        "Дедлайн", duration(order.deliveryDeadlineSeconds()),
                        "Просрочки", Long.toString(order.delayedDeliveryCount())));
            }
            result.add(new FreightView(
                    fleet.fleetId().value(),
                    "Транспорт #" + fleet.fleetId().value(),
                    fleet.stableFactionId(),
                    owner,
                    localizePhase(fleet.phase().name()),
                    fleet.hullId(),
                    fleet.fitId(),
                    lots.getOrDefault(fleet.fleetId().value(), fleet.cargoMassKg()),
                    fleet.cargoCapacityKg(),
                    commodity,
                    source,
                    destination,
                    route,
                    fleet.routeIndex(),
                    order == null ? 0d : order.deliveredMassKg(),
                    order == null ? 0d : order.deliveryDeadlineSeconds(),
                    order == null ? 0L : order.delayedDeliveryCount(),
                    sections));
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    private List<MilitaryView> militaryViews(GalaxyStrategicMapSnapshot galaxy) {
        Set<FleetId> freightIds = runtime.freight().capture().freighters().stream()
                .map(FreighterState::fleetId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        ArrayList<MilitaryView> result = new ArrayList<>();
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (freightIds.contains(placement.id())) {
                continue;
            }
            Entity entity = placement.locationKind() == FleetLocationKind.IN_SYSTEM
                    ? runtime.world().findSession(placement.systemId()).orElseThrow()
                            .getEntityRegistry().require(placement.localEntityId())
                    : EntityStateMapper.restore(placement.transitState().entityState());
            ShipComponent ship = entity.getComponent(ShipComponent.class);
            CombatComponent combat = entity.getComponent(CombatComponent.class);
            EngineeringComponent engineering = entity.getComponent(EngineeringComponent.class);
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            if (ship == null || ship.type == null || !ship.type.isCombat()
                    || combat == null || engineering == null
                    || faction == null || identity == null) {
                continue;
            }
            String ownerId = runtime.world().findFactionStableId(faction.factionId).orElseThrow();
            String ownerName = factionName(ownerId);
            StarSystemId displayedSystem = placement.locationKind() == FleetLocationKind.IN_SYSTEM
                    ? placement.systemId() : placement.transitState().destinationSystemId();
            String status = placement.locationKind() == FleetLocationKind.IN_SYSTEM
                    ? "Патруль системы " + systemName(displayedSystem, galaxy)
                    : "Перелёт " + systemName(placement.transitState().originSystemId(), galaxy)
                            + " → " + systemName(placement.transitState().destinationSystemId(), galaxy);
            DerivedShipState derived = MILITARY_CALCULATOR.derive(
                    MILITARY_ENGINEERING.findHull(engineering.fit.hullId()),
                    engineering.fit,
                    engineering.runtimeState.consumables(),
                    engineering.instanceState.damage().moduleDamage());
            String fitId = provisionalFitId(engineering.fit);
            double structuralIntegrity = engineering.instanceState.damage()
                    .compartmentIntegrityById().values().stream()
                    .mapToDouble(Double::doubleValue).average().orElse(1d);
            double shieldReserve = engineering.instanceState.shieldStatesByMount().values().stream()
                    .mapToDouble(value -> value.reserveJ()).sum();
            String modules = engineering.fit.installedModules().stream()
                    .map(value -> displayId(value.mountId()) + ": " + displayId(value.moduleId()))
                    .collect(java.util.stream.Collectors.joining("; "));
            ArrayList<InfoSection> sections = new ArrayList<>();
            sections.add(InfoSection.of(
                    "Идентификация",
                    "Название", identity.name,
                    "FleetId", Long.toString(placement.id().value()),
                    "Фракция", ownerName,
                    "Faction ID", ownerId,
                    "Состояние", status));
            sections.add(InfoSection.of(
                    "Корпус и фит",
                    "Корпус", engineering.fit.hullId(),
                    "Фит", fitId,
                    "Модули", modules,
                    "Масса", mass(derived.totalMassKg()),
                    "Экипаж", derived.crewRequired() + " / " + derived.crewSupported()));
            sections.add(InfoSection.of(
                    "Боевая готовность",
                    "Структура", percent(structuralIntegrity),
                    "Щитовой резерв", format(shieldReserve) + " Дж",
                    "Боеприпасы", derived.ammunitionCount() + " ед. / " + mass(derived.ammunitionMassKg()),
                    "Реактивная масса", mass(derived.reactionMassKg()),
                    "Ускорение", format(derived.accelerationMps2()) + " м/с²",
                    "Delta-v", format(derived.deltaVMps()) + " м/с"));
            sections.add(InfoSection.of(
                    "Назначение",
                    "Текущий приказ", placement.locationKind() == FleetLocationKind.IN_SYSTEM
                            ? "Охрана стартовой системы" : "Межсистемный переход",
                    "Куда направляется", placement.locationKind() == FleetLocationKind.IN_SYSTEM
                            ? "Локальный патруль" : systemName(
                                    placement.transitState().destinationSystemId(), galaxy),
                    "Контент", "Временный Stage 17.5/19; замена доктрин в Stage 22"));
            result.add(new MilitaryView(
                    placement.id().value(),
                    identity.name,
                    ownerId,
                    ownerName,
                    status,
                    displayedSystem,
                    placement.locationKind() == FleetLocationKind.IN_SYSTEM,
                    engineering.fit.hullId(),
                    fitId,
                    sections));
        }
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    private static String provisionalFitId(InstalledFit fit) {
        return Stage175IFleetDoctrineCatalog.all().stream()
                .filter(value -> InstalledFit.fromDemonstrator(
                        MILITARY_ENGINEERING.findDemonstratorFit(value.fitId())).equals(fit))
                .map(Stage175IFleetDoctrineCatalog.Doctrine::fitId)
                .findFirst().orElse("fit.provisional.unknown");
    }

    private static String systemName(
            StarSystemId systemId,
            GalaxyStrategicMapSnapshot galaxy) {
        return galaxy.systems().stream()
                .filter(value -> value.id().equals(systemId))
                .map(GalaxyStrategicMapSnapshot.SystemView::name)
                .findFirst().orElse("#" + systemId.value());
    }

    private String controller(StarSystemId systemId, GalaxyStrategicMapSnapshot galaxy) {
        return galaxy.systems().stream()
                .filter(value -> value.id().equals(systemId))
                .map(GalaxyStrategicMapSnapshot.SystemView::controllerFactionId)
                .filter(Objects::nonNull)
                .findFirst().orElse("");
    }

    private String factionName(String factionId) {
        if (factionId == null || factionId.isBlank()) {
            return "Не определена";
        }
        var authored = content.findFaction(factionId);
        if (authored != null) {
            return authored.displayName();
        }
        return runtime.world().getWorldFactionIdentities().stream()
                .filter(value -> value.stableFactionId().equals(factionId))
                .map(WorldFactionIdentityState::displayName)
                .findFirst().orElse(factionId);
    }

    private static InfoSection identitySection(
            String id,
            String factionId,
            String factionName,
            StarSystemId system,
            LocalPhysicalPosition position) {
        return InfoSection.of(
                "Идентификация",
                "ID", id,
                "Фракция", factionName,
                "Faction ID", factionId == null || factionId.isBlank() ? "—" : factionId,
                "Система", Long.toString(system.value()),
                "Координаты", coordinates(position));
    }

    private static InfoSection storageSection(Stage18StationStorage storage) {
        String inventory = storage.snapshotCommodityMassByIdKg().isEmpty()
                ? "Пусто"
                : storage.snapshotCommodityMassByIdKg().entrySet().stream()
                .map(value -> displayId(value.getKey()) + " " + mass(value.getValue()))
                .collect(java.util.stream.Collectors.joining("; "));
        String capacity = storage.snapshotCapacityByStorageClassKg().entrySet().stream()
                .map(value -> displayId(value.getKey()) + " " + mass(value.getValue()))
                .collect(java.util.stream.Collectors.joining("; "));
        return InfoSection.of(
                "Физическое хранилище",
                "Содержимое", inventory,
                "Вместимость", capacity,
                "Продукты", storage.snapshotProductCountById().isEmpty()
                        ? "Нет" : storage.snapshotProductCountById().toString());
    }

    private static InfoSection textSection(String title, String text) {
        String[] rows = text.split("\\R");
        ArrayList<InfoLine> lines = new ArrayList<>();
        for (String row : rows) {
            String value = row.strip();
            if (!value.isEmpty()) {
                lines.add(new InfoLine("", value));
            }
        }
        return new InfoSection(title, lines.isEmpty() ? List.of(new InfoLine("", "—")) : lines);
    }

    private static LocalPhysicalPosition position(List<String> values, int start) {
        return new LocalPhysicalPosition(
                Long.parseLong(values.get(start)),
                Long.parseLong(values.get(start + 1)),
                Double.parseDouble(values.get(start + 2)),
                Double.parseDouble(values.get(start + 3)));
    }

    private static String coordinates(LocalPhysicalPosition position) {
        return "cell " + position.cellX() + ":" + position.cellY()
                + "  Δ " + format(position.offsetXM()) + ", " + format(position.offsetYM()) + " м";
    }

    private static String routeNames(List<StarSystemId> route, GalaxyStrategicMapSnapshot galaxy) {
        return route.stream().map(id -> galaxy.systems().stream()
                        .filter(value -> value.id().equals(id))
                        .map(GalaxyStrategicMapSnapshot.SystemView::name)
                        .findFirst().orElse("#" + id.value()))
                .collect(java.util.stream.Collectors.joining(" → "));
    }

    private static String joinIds(List<String> ids) {
        return ids.isEmpty() ? "Нет" : ids.stream().map(GeneratedWorldUiModel::displayId)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static String displayId(String id) {
        String value = id == null ? "" : id.strip();
        int split = Math.max(value.lastIndexOf('.'), value.lastIndexOf(':'));
        String tail = split >= 0 && split + 1 < value.length() ? value.substring(split + 1) : value;
        tail = tail.replace('_', ' ').replace('-', ' ').strip();
        return tail.isEmpty() ? value : Character.toUpperCase(tail.charAt(0)) + tail.substring(1);
    }

    private static String localizePhase(String phase) {
        return switch (phase) {
            case "IDLE" -> "Резерв";
            case "AT_SOURCE" -> "У источника";
            case "OUTBOUND" -> "Следует к получателю";
            case "AT_DESTINATION" -> "У получателя";
            case "RETURNING" -> "Возвращается";
            case "DESTROYED" -> "Уничтожен";
            default -> phase;
        };
    }

    private static String localizeLocationKind(LocationKind kind) {
        return switch (kind) {
            case ANOMALY -> "Аномалия";
            case DERELICT -> "Покинутый корабль";
            case RESOURCE_PHENOMENON -> "Ресурсный феномен";
        };
    }

    private static String localizeIdentityKind(IdentityComponent.Kind kind) {
        return switch (kind) {
            case FLEET -> "Корабль / флот";
            case STATION -> "Станция";
            case ASTEROID -> "Астероид";
            case SALVAGE -> "Обломки";
        };
    }

    private static String mass(double kg) {
        if (kg >= 1_000_000d) {
            return format(kg / 1_000_000d) + " тыс. т";
        }
        if (kg >= 1_000d) {
            return format(kg / 1_000d) + " т";
        }
        return format(kg) + " кг";
    }

    private static String power(String value) {
        return format(Double.parseDouble(value)) + " Вт";
    }

    private static String duration(double seconds) {
        if (seconds >= 86_400d) {
            return format(seconds / 86_400d) + " сут.";
        }
        if (seconds >= 3_600d) {
            return format(seconds / 3_600d) + " ч";
        }
        return format(seconds) + " с";
    }

    private static String percent(double fraction) {
        return format(fraction * 100d) + "%";
    }

    private static String format(double value) {
        double normalized = Math.abs(value) < 0.0005d ? 0d : value;
        return String.format(Locale.ROOT, "%,.2f", normalized);
    }
}
