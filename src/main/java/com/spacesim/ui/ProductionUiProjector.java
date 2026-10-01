package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.economy.Money;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.ObjectKind;
import com.spacesim.ui.ProductionUiSnapshot.Row;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Composes existing Stage-20/21/M22.8 projections without granting player control of a faction. */
@SuppressWarnings("doclint:missing")
public final class ProductionUiProjector {
    private final String viewerFactionId;
    private final Stage21IFinalLivingWorldUiProjector living = new Stage21IFinalLivingWorldUiProjector();

    /**
     * Binds a known actor as a knowledge viewer without transferring control.
     *
     * @param viewerFactionId existing actor whose bounded observations are projected
     */
    public ProductionUiProjector(String viewerFactionId) {
        this.viewerFactionId = Objects.requireNonNull(viewerFactionId).strip();
        if (this.viewerFactionId.isEmpty()) throw new IllegalArgumentException("viewer faction is empty");
    }

    /**
     * Captures all existing read models from one campaign checkpoint.
     *
     * @param campaign accepted composed campaign authority
     * @param world current physical map projection
     * @return immutable presentation with explanations
     */
    public ProductionUiSnapshot capture(Stage228CampaignAuthority campaign, GeneratedWorldUiSnapshot world) {
        Objects.requireNonNull(campaign);
        var checkpoint = campaign.captureState();
        var bounded = living.project(checkpoint.stage21Runtime().stage21HRuntime(), viewerFactionId);
        EnumMap<Tab, List<Row>> rows = compose(world, bounded);
        var craftRows = new ArrayList<>(rows.get(Tab.SHIPS));
        for (var craft : checkpoint.smallCraft().craft()) {
            if (!craft.stableFactionId().equals(viewerFactionId)) continue;
            var assignment = checkpoint.hangars().assignments().stream()
                    .filter(item -> item.craftId().equals(craft.id())).findFirst();
            var missions = checkpoint.operations().missions().stream()
                    .filter(item -> item.craftId().equals(craft.id())).toList();
            craftRows.add(row("craft:" + craft.id().value(), "Малый аппарат «" + label(craft.designId()) + "»",
                    "Авиакрыло", assignment.map(item -> label(item.occupancyState().name())).orElse("Вне ангара"),
                    List.of(InfoSection.of("Аппарат", "Проект", label(craft.designId()),
                            "Ангар", assignment.map(item -> label(item.bayStableId())).orElse("Нет"),
                            "Состояние", assignment.map(item -> label(item.occupancyState().name())).orElse("Вне ангара"),
                            "Задания", join(missions.stream().map(item -> label(item.type().name())
                                    + ": " + label(item.status().name())).toList()))),
                    "M22.8: реестр индивидуальных аппаратов, ангаров и заданий; сведения своей фракции",
                    null, 0, world.worldTick()));
        }
        rows.put(Tab.SHIPS, List.copyOf(craftRows));
        var industry = new ArrayList<>(rows.get(Tab.INDUSTRY));
        for (var demand : campaign.coordinator().recovery().replacementDemands()) {
            if (!demand.factionContentId().equals(viewerFactionId)) continue;
            industry.add(row("replacement:" + demand.id(), "Замещение потерянного корабля",
                    "Замещение", label(demand.status().name()),
                    List.of(InfoSection.of("План замещения", "Состояние", label(demand.status().name()),
                            "Создано", Long.toString(demand.createdTick()),
                            "Обновлено", Long.toString(demand.updatedTick()))),
                    "Stage 21G: реальные потребности замещения своей фракции; без бесплатных кораблей",
                    demand.completedAssetSystemId(), 0, demand.updatedTick()));
        }
        rows.put(Tab.INDUSTRY, List.copyOf(industry));
        var contacts = new ArrayList<>(rows.get(Tab.CONTACTS));
        for (var reputation : campaign.coordinator().npcMissions().reputations()) {
            if (!reputation.subjectActorId().equals(viewerFactionId)) continue;
            contacts.add(row("reputation:" + reputation.ownerId(), "Репутация у «" + label(reputation.ownerId()) + "»",
                    "Репутация", Integer.toString(reputation.derivedValue()),
                    List.of(InfoSection.of("Отношение", "Репутация", Integer.toString(reputation.derivedValue()),
                            "Основание", "Сумма подтверждённых событий с ограничением от −100 до 100",
                            "Подтверждённых событий", Integer.toString(reputation.events().size()))),
                    "Stage 21H: направленная репутация; события о текущем наблюдателе", null, 0, world.worldTick()));
        }
        rows.put(Tab.CONTACTS, List.copyOf(contacts));
        for (Tab tab : List.of(Tab.LOGISTICS, Tab.MILITARY, Tab.SHIPS)) {
            rows.put(tab, rows.get(tab).stream().map(row -> {
                if (row.focusFleet() == 0) return row;
                var placement = campaign.coordinator().runtime().world()
                        .findFleet(new com.spacesim.world.FleetId(row.focusFleet())).orElse(null);
                StarSystemId system = placement != null
                        && placement.locationKind() == com.spacesim.world.FleetLocationKind.IN_SYSTEM
                        ? placement.systemId() : null;
                return new Row(row.selection(), row.name(), row.category(), row.summary(), row.sections(),
                        row.provenance(), system, row.focusFleet(), row.chronologicalTick());
            }).toList());
        }
        return new ProductionUiSnapshot(world.worldTick(), factionName(world, viewerFactionId), rows);
    }

    /**
     * Adapts existing actor-filtered Stage-21 data to consolidated surfaces.
     *
     * @param world physical current-system and fleet projection
     * @param bounded already actor-filtered final Stage-21 projection
     * @return independent surface row lists
     */
    public static EnumMap<Tab, List<Row>> compose(
            GeneratedWorldUiSnapshot world, Stage21ILivingWorldUiSnapshot bounded) {
        Objects.requireNonNull(world);
        Objects.requireNonNull(bounded);
        EnumMap<Tab, List<Row>> result = new EnumMap<>(Tab.class);
        for (Tab tab : Tab.values()) result.put(tab, new ArrayList<>());
        var factions = result.get(Tab.FACTIONS);
        for (var faction : bounded.factions()) {
            var sections = new ArrayList<InfoSection>();
            sections.add(InfoSection.of("Дипломатия", "Отношение", label(faction.relation()),
                    "Договоры", join(faction.treaties()), "Кризисы", join(faction.crises()),
                    "Войны", join(faction.wars()), "Цели", join(faction.goals()),
                    "Причины решений", join(faction.decisionEvidence())));
            if (faction.factionId().equals(bounded.viewerFactionId())) {
                world.galaxy().factions().stream().filter(item -> item.factionId().equals(faction.factionId()))
                        .findFirst().ifPresent(item -> sections.add(InfoSection.of("Экономика и территория",
                                "Казна", credits(item.treasuryMilliCredits()),
                                "Систем под контролем", Integer.toString(item.controlledSystems()),
                                "Налог станций", percent(item.stationTaxBasisPoints()),
                                "Транзитный тариф", percent(item.territorialTariffBasisPoints()),
                                "Таможенный тариф", percent(item.customsTariffBasisPoints()))));
            }
            factions.add(new Row(new UiSelection(SelectionKind.FACTION, faction.factionId()),
                    faction.displayName(), "Фракции", label(faction.relation()), sections,
                    faction.authorityRef() + "; казна — баланс, ставки — базисные пункты / 100, системы — контроль Stage 17",
                    null, 0, world.worldTick()));
        }
        for (var group : bounded.military()) {
            result.get(Tab.MILITARY).add(row("group:" + group.commandGroupId(), group.commandGroupName(),
                    "Командные группы", label(group.order()),
                    List.of(InfoSection.of("Командование", "Приказ", label(group.order()),
                            "Готовность", label(group.readiness()), "Снабжение", label(group.supply()),
                            "Маршрут", join(group.route()), "Операция", label(group.operation()),
                            "Назначение", label(group.destination()))),
                    group.authorityRef() + "; готовность: минимум по участникам; не наблюдаемые сервис/экипаж = 0",
                    null, 0, world.worldTick()));
        }
        for (var fleet : world.military()) {
            if (!fleet.factionId().equals(bounded.viewerFactionId())) continue;
            Row row = new Row(new UiSelection(SelectionKind.MILITARY, Long.toString(fleet.fleetId())),
                    fleet.name(), "Военные корабли", fleet.status(), fleet.sections(),
                    "Stage 20/21D: физический флот, сохранённый фит и состояние; единицы указаны в инспекторе",
                    fleet.inSystem() ? fleet.systemId() : null, fleet.fleetId(), world.worldTick());
            result.get(Tab.MILITARY).add(row);
            result.get(Tab.SHIPS).add(row);
        }
        for (var freight : world.freight()) {
            if (!freight.factionId().equals(bounded.viewerFactionId())) continue;
            Row row = new Row(new UiSelection(SelectionKind.FREIGHT, Long.toString(freight.fleetId())),
                    freight.name(), "Транспорты", freight.phase(), freight.sections(),
                    "Stage 20: транспортный заказ и физический груз; масса в кг; сроки в секундах симуляции",
                    null, freight.fleetId(), world.worldTick());
            result.get(Tab.LOGISTICS).add(row);
            result.get(Tab.SHIPS).add(row);
        }
        for (var object : world.localObjects()) {
            if (object.kind() == ObjectKind.STATION || object.kind() == ObjectKind.EXTRACTION_OUTPOST
                    || object.kind() == ObjectKind.RESOURCE) {
                result.get(Tab.INDUSTRY).add(new Row(
                        new UiSelection(SelectionKind.LOCAL_OBJECT, object.stableId()), object.name(),
                        object.subtitle(), object.factionName(), object.sections(),
                        "Stage 20/18: текущая открытая система; физическое хранилище, источник и оборудование",
                        object.systemId(), 0, world.worldTick()));
            }
        }
        for (var npc : bounded.npcMissions()) {
            result.get(Tab.CONTACTS).add(row("npc:" + npc.npcId() + ":" + npc.missionId(),
                    label(npc.npcNameKey()), npc.missionId().isEmpty() ? "Контакты" : "Задания",
                    label(npc.missionId().isEmpty() ? npc.availability() : npc.missionStatus()),
                    List.of(InfoSection.of("Контакт", "Роль", label(npc.npcRole()),
                            "Доступность", label(npc.availability()), "Известные факты", join(npc.knownFacts())),
                            InfoSection.of("Контракт", "Задание", label(npc.missionTemplate()),
                                    "Статус", label(npc.missionStatus()), "Цель", label(npc.objective()),
                                    "Срок", npc.deadlineTick() < 0 ? "Нет" : npc.deadlineTick() + " такт",
                                    "Эскроу", credits(npc.escrowMilliCredits()))),
                    npc.authorityRef() + "; эскроу = средства контракта, выплата только через обычную миссионную authority",
                    null, 0, world.worldTick()));
        }
        for (var overlay : bounded.overlays()) {
            result.get(Tab.INTELLIGENCE).add(row("overlay:" + overlay.kind() + ":" + overlay.subjectId()
                            + ":" + overlay.actorId() + ":" + overlay.state(),
                    label(overlay.kind()) + " — " + label(overlay.subjectId()), label(overlay.kind()),
                    label(overlay.state()), List.of(InfoSection.of("Наблюдение", "Состояние", label(overlay.state()),
                            "Доступность", label(overlay.visibility()), "Подробности", join(overlay.details()))),
                    overlay.authorityRef() + "; факты отфильтрованы существующей Stage 21 проекцией наблюдателя",
                    null, 0, world.worldTick()));
        }
        int eventIndex = 0;
        for (var event : bounded.timeline()) {
            result.get(Tab.HISTORY).add(row("event:" + event.tick() + ":" + event.actorId()
                            + ":" + event.eventType() + ":" + event.evidenceRef() + ":" + eventIndex++,
                    label(event.eventType()), label(event.visibility()), label(event.summary()),
                    List.of(InfoSection.of("Событие", "Такт", Long.toString(event.tick()),
                            "Событие", label(event.summary()), "Доступность", label(event.visibility()))),
                    event.evidenceRef(), null, 0, event.tick()));
        }
        result.get(Tab.SETTINGS).add(row("session", "Кампания", "Сессия", "Пауза, скорость, сохранение и загрузка",
                List.of(InfoSection.of("Управление", "Навигация", "F1–F7, Tab и Enter; Esc — назад; Ctrl+F — поиск",
                        "Карты", "Колесо — масштаб; СКМ — панорама; Home — обзор; C — вернуться к объекту",
                        "Списки", "Стрелки — выбор; PgUp/PgDn — прокрутка; сортировка и фильтр — кнопки сверху",
                        "Сессия", "Пробел — пауза; 1/2/3/4 — скорость; F8/F9 — сохранение/загрузка")),
                "Клавиши текущего production-клиента; переназначение и локализация принадлежат 23C", null, 0, world.worldTick()));
        result.get(Tab.SETTINGS).add(row("diagnostics", "Сведения о кампании", "Диагностика", "Версия и воспроизводимость",
                List.of(InfoSection.of("Кампания", "Seed", Long.toString(world.worldSeed()),
                        "Такт", Long.toString(world.worldTick()), "Текущая система", world.activeSystemName(),
                        "Формат", "M22.8 campaign v4")),
                "Текущая сохранённая authority; содержимое сохранений и пользовательские пути здесь не показываются",
                null, 0, world.worldTick()));
        return result;
    }

    private static Row row(String id, String name, String category, String summary, List<InfoSection> sections,
            String provenance, StarSystemId system, long fleet, long tick) {
        return new Row(new UiSelection(SelectionKind.SURFACE_ROW, id), name, category, summary,
                sections, provenance, system, fleet, tick);
    }

    private static String credits(long value) {
        return String.format(Locale.ROOT, "%,.2f cr", Money.toCredits(value));
    }

    private static String percent(int bps) { return String.format(Locale.ROOT, "%.2f %%", bps / 100d); }

    private static String factionName(GeneratedWorldUiSnapshot world, String id) {
        return world.galaxy().factions().stream().filter(row -> row.factionId().equals(id))
                .map(GalaxyStrategicMapSnapshot.FactionView::displayName).findFirst().orElse(label(id));
    }

    private static String join(List<String> values) {
        return values.isEmpty() ? "Нет сведений" : String.join("; ", values.stream().map(ProductionUiProjector::label).toList());
    }

    /** Minimal display fallback; stable IDs remain internal selection keys. Full localization is 23C. */
    static String label(String value) {
        if (value == null || value.isBlank()) return "Нет сведений";
        return switch (value) {
            case "SELF" -> "Своя фракция (наблюдение)";
            case "UNKNOWN", "UNOBSERVED_IN_STAGE21H_CHECKPOINT" -> "Нет подтверждённых сведений";
            case "PRIVATE" -> "Только своей фракции";
            case "PUBLIC" -> "Общедоступно";
            case "ALLOWED" -> "Разрешено";
            case "DENIED" -> "Отклонено";
            case "MARKET_ACCESS" -> "Доступ к рынку";
            case "TERRITORIAL_CLAIM" -> "Территориальная претензия";
            case "TERRITORIAL_CONTROL" -> "Контроль территории";
            case "DISCOVERY" -> "Открытие";
            case "NONE" -> "Нет";
            default -> value.replace('_', ' ').replace(':', ' ').replace('.', ' ').strip();
        };
    }
}
