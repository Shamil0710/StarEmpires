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
    private static final com.spacesim.ship.ProductionEngineeringRuntimeResolver PERSONAL_ENGINEERING =
            new com.spacesim.ship.ProductionEngineeringRuntimeResolver();
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
        return capture(campaign, world, false);
    }

    /**
     * Captures the client view with an explicit transient character-creation invitation.
     * Ordinary two-argument capture projects durable world information and remains exact on restore.
     * @param campaign current campaign authority
     * @param world current read-only world projection
     * @param includeNewGameOffer whether the client is showing fresh character creation
     * @return projection with the invitation only while the authoritative new-game gate permits it
     */
    public ProductionUiSnapshot capture(Stage228CampaignAuthority campaign, GeneratedWorldUiSnapshot world,
            boolean includeNewGameOffer) {
        Objects.requireNonNull(campaign);
        var checkpoint = campaign.captureState();
        var bounded = living.project(checkpoint.stage21Runtime().stage21HRuntime(), viewerFactionId);
        EnumMap<Tab, List<Row>> rows = compose(world, bounded);
        var settings = new ArrayList<>(rows.get(Tab.SETTINGS));
        if (includeNewGameOffer && campaign.canStartIndependentPilot()) {
            settings.add(0, row("pilot-start", "Начать независимым пилотом", "Новая игра", "Подтвердите покупку резервного корабля",
                    List.of(InfoSection.of("Условия старта", "Личные сбережения", credits(Stage228CampaignAuthority.PILOT_SAVINGS_MILLI_CREDITS),
                            "Цена корабля", credits(Stage228CampaignAuthority.PILOT_SHIP_PRICE_MILLI_CREDITS),
                            "После покупки", credits(Stage228CampaignAuthority.PILOT_SAVINGS_MILLI_CREDITS - Stage228CampaignAuthority.PILOT_SHIP_PRICE_MILLI_CREDITS),
                            "Владение", "Один существующий резервный грузовик без назначенного рейса",
                            "Статус", "Независимый пилот; казна и другие активы продавца недоступны",
                            "Источник средств", "Ограниченные личные сбережения при подтверждении новой игры",
                            "Рынки новой игры", "Существующие склады; каждому физическому рынку выдаётся 10 000 кредитов конечного оборотного капитала",
                            "Число рынков", Integer.toString(campaign.coordinator().runtime().infrastructure().endpoints().size()),
                            "Оборотный капитал рынков", credits(Math.multiplyExact(Stage228CampaignAuthority.PILOT_MARKET_INITIAL_LIQUIDITY, campaign.coordinator().runtime().infrastructure().endpoints().size())))),
                    "Подтверждённая новая игра: раскрытые личные сбережения, существующий резерв и оплата в казну продавца",
                    null, 0, world.worldTick()));
        }
        campaign.playerState().ifPresent(player -> {
            if (player.factionContentId() == null) settings.add(row("pilot-faction-foundation", "Основать «Содружество пилота»", "Моя фракция", "Независимый пилот",
                    List.of(InfoSection.of("Основание фракции", "Имя", "Содружество пилота",
                            "Казна", "0 кр.", "Территории и новые активы", "Не предоставляются",
                            "Существующие активы", "Личные корабли и кошелёк сохраняются; регистрация корпусов сохраняется",
                            "Полномочия", "Новая собственная фракция; полномочия продавца не передаются")),
                    "Подтверждённое решение пилота; реестр собственной фракции. Деньги, территория и новые активы не выдаются", null, 0, world.worldTick()));
            else {
                var own = campaign.coordinator().runtime().world().findFactionEconomicState(player.factionContentId()).orElseThrow();
                settings.add(row("pilot-faction-finance", "Личные деньги и казна", "Моя фракция", campaign.coordinator().runtime().world().getWorldFactionIdentities().stream()
                                .filter(identity -> identity.stableFactionId().equals(player.factionContentId()))
                                .map(com.spacesim.world.WorldFactionIdentityState::displayName).findFirst().orElse(factionName(world, player.factionContentId())),
                        List.of(InfoSection.of("Обособленные средства", "Личный кошелёк", credits(player.walletMilliCredits()),
                                "Казна фракции", credits(own.treasuryMilliCredits()), "Размер перевода", "1 000 кр.",
                                "Пополнение", "Личный кошелёк → собственная казна", "Возврат", "Собственная казна → личный кошелёк",
                                "Условие", "Полный перевод существующих денег, без кредита и новых источников")),
                        "Личный кошелёк, фактическая казна собственной фракции и журнал выполненных переводов", null, 0, world.worldTick()));
            }
        });
        rows.put(Tab.SETTINGS, List.copyOf(settings));
        var logistics = new ArrayList<>(rows.get(Tab.LOGISTICS));
        campaign.playerState().ifPresent(player -> {
            if (player.activeFleetId() == null) return;
            var fleet = campaign.coordinator().runtime().world().findFleet(player.activeFleetId()).orElse(null);
            if (fleet == null || fleet.locationKind() != com.spacesim.world.FleetLocationKind.IN_SYSTEM) return;
            var runtime = campaign.coordinator().runtime();
            var physical = runtime.arrival().materialization(fleet.systemId()).physicalState(fleet.localEntityId()).orElseThrow();
            var ontology = com.spacesim.content.Stage18ResourceOntologyLoader.loadDefault();
            var hold = runtime.freight().findFreighter(fleet.fleetId()).orElse(null);
            for (var endpoint : runtime.infrastructure().endpoints()) {
                if (!endpoint.systemId().equals(fleet.systemId()) || campaign.pilotMarketReference(endpoint.stationId()).isEmpty()) continue;
                String name = GeneratedWorldUiModel.endpointDisplayName(endpoint);
                var ref = campaign.pilotMarketReference(endpoint.stationId()).orElseThrow();
                boolean docked = ref.equals(player.dockedAt());
                long marketMoney = runtime.world().findSession(ref.systemId()).orElseThrow().getEntityRegistry().require(ref.entityId())
                        .getComponent(com.spacesim.components.WalletComponent.class).getBalanceMilliCredits();
                logistics.add(row("pilot-station|" + endpoint.stationId(), name, "Личные рынки", docked ? "Корабль пристыкован" : "Требуется стыковка",
                        List.of(InfoSection.of("Доступ к рынку", "Расстояние", String.format(Locale.ROOT, "%.1f м", physical.position().distanceTo(endpoint.position())),
                                "Скорость корабля", String.format(Locale.ROOT, "%.2f м/с", Math.hypot(physical.velocityXMps(), physical.velocityYMps())),
                                "Условия стыковки", "Не далее 1 км; скорость не выше 1 м/с",
                                "Управление тягой", "WASD в системе; X — торможение; без тяги — движение по инерции",
                                "Кошелёк", credits(player.walletMilliCredits()))),
                        "Фактическое положение и скорость личного корабля; состояние стыковки и кошелёк станции",
                        endpoint.systemId(), 0, world.worldTick()));
                if (hold == null) continue;
                for (String commodity : List.of("commodity.material.purified_water", "commodity.material.structural_alloy", "commodity.ore.metallic")) {
                    var definition = ontology.findCommodity(commodity);
                    if (definition == null || !hold.cargoStorage().capacityByStorageClassKg().containsKey(definition.storageClassId())
                            || !endpoint.handlingCapability().supportedStorageClassIds().contains(definition.storageClassId())) continue;
                    logistics.add(row("pilot-market|" + endpoint.stationId() + "|" + commodity, switch (commodity) {
                        case "commodity.material.purified_water" -> "Очищенная вода";
                        case "commodity.material.structural_alloy" -> "Конструкционный сплав";
                        default -> "Металлическая руда";
                    }, "Товары личного рынка", name,
                            List.of(InfoSection.of("Физическая сделка", "Запас станции", endpoint.storage().commodityMassKg(commodity) + " кг",
                                    "В трюме", hold.cargoStorage().commodityMassByIdKg().getOrDefault(commodity, 0d) + " кг",
                                    "Свободно на складе", endpoint.storage().remainingCapacityKg(definition.storageClassId()) + " кг",
                                    "Кошелёк рынка", credits(marketMoney),
                                    "Покупка за 1 кг", credits(campaign.pilotCommodityPrice(endpoint.stationId(), commodity, true)),
                                    "Продажа за 1 кг", credits(campaign.pilotCommodityPrice(endpoint.stationId(), commodity, false)),
                                    "Кошелёк", credits(player.walletMilliCredits()), "Стыковка", docked ? "Да" : "Нет",
                                    "Обработка за такт", String.format(Locale.ROOT, "%.2f кг", endpoint.handlingCapability().massRateKgPerSecond() * campaign.coordinator().session().fixedStepSeconds()),
                                    "Лимит времени", "Одна физическая сделка корабля за завершённый такт; после сделки нужно продолжить время")),
                            "Фактические запасы и ёмкость склада; раскрытые рыночные условия за килограмм, доступ и таможенные платежи",
                            null, 0, world.worldTick()));
                }
            }
        });
        rows.put(Tab.LOGISTICS, List.copyOf(logistics));
        var craftRows = new ArrayList<>(rows.get(Tab.SHIPS));
        campaign.playerState().ifPresent(player -> {
            for (var id : player.ownedFleetIds()) {
                var placement = campaign.coordinator().runtime().world().findFleet(id).orElseThrow();
                var personalSections = new ArrayList<InfoSection>();
                personalSections.add(InfoSection.of("Пилот", "Кошелёк", credits(player.walletMilliCredits()),
                        "Принадлежность", player.factionContentId() == null ? "Независимый" : factionName(world, player.factionContentId()),
                        "Физическое владение", "Существующий корабль; новые корпуса при загрузке не создаются",
                        "Регистрация", "Происхождение корпуса не даёт полномочий над фракцией продавца"));
                if (placement.locationKind() == com.spacesim.world.FleetLocationKind.IN_SYSTEM) {
                    var runtime = campaign.coordinator().runtime();
                    var entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                            .getEntityRegistry().require(placement.localEntityId());
                    var fitted = entity.getComponent(com.spacesim.components.EngineeringComponent.class);
                    var physical = runtime.arrival().materialization(placement.systemId()).physicalState(placement.localEntityId()).orElseThrow();
                    personalSections.add(InfoSection.of("Полёт", "Скорость", String.format(Locale.ROOT, "%.2f м/с", Math.hypot(physical.velocityXMps(), physical.velocityYMps())),
                            "Стыковка", player.docked() ? "Да" : "Нет", "Управление", "Откройте корабль на карте: WASD — тяга, X — торможение; без тяги — инерция"));
                    if (fitted != null) {
                        var derived = PERSONAL_ENGINEERING.derive(fitted);
                        personalSections.add(InfoSection.of("Инженерное состояние", "Полная масса", String.format(Locale.ROOT, "%.1f кг", derived.totalMassKg()),
                                "Груз", String.format(Locale.ROOT, "%.1f кг", derived.cargoMassKg()),
                                "Реактивная масса", String.format(Locale.ROOT, "%.1f кг", derived.reactionMassKg()),
                                "Доступная тяга", String.format(Locale.ROOT, "%.1f Н", derived.availableThrustN()),
                                "Ускорение", String.format(Locale.ROOT, "%.4f м/с²", derived.accelerationMps2()),
                                "Запас изменения скорости", String.format(Locale.ROOT, "%.1f м/с", derived.deltaVMps()),
                                "Баланс мощности", String.format(Locale.ROOT, "%+.1f Вт", derived.continuousPowerMarginW()),
                                "Энергия шины", String.format(Locale.ROOT, "%.1f Дж", fitted.runtimeState.sharedBusEnergyJ()),
                                "Накопленное тепло", String.format(Locale.ROOT, "%.1f Дж", fitted.runtimeState.shipHeatStoredJ()),
                                "Боеприпасы", Long.toString(derived.ammunitionCount())));
                    }
                }
                var jump = campaign.coordinator().runtime().world().findFleetJump(id).orElse(null);
                if (jump != null) personalSections.add(InfoSection.of("Межсистемный полёт",
                        "Фаза", switch (jump.phase()) {
                            case MOVING_TO_JUMP -> "Движение к выходу";
                            case JUMP_PENDING -> "Подготовка прыжка";
                            case IN_TRANSIT -> "В пути";
                            case ARRIVING -> "Прибытие";
                        }, "Цель", campaign.coordinator().runtime().world().getTopology().findSystem(jump.destinationSystemId()).orElseThrow().name(),
                        "Граница фазы", "Такт " + jump.phaseEndsTick(), "Время", "Продолжите кампанию: Пробел; полёт сохраняется вместе с миром"));
                if (id.equals(player.activeFleetId()) && placement.locationKind() == com.spacesim.world.FleetLocationKind.IN_SYSTEM
                        && jump == null) {
                    var ordinaryWorld = campaign.coordinator().runtime().world();
                    for (var destination : ordinaryWorld.getTopology().neighbors(placement.systemId())) {
                        var fuel = ordinaryWorld.planFleetRouteFuel(id, List.of(placement.systemId(), destination));
                        craftRows.add(row("pilot-jump|" + destination.value(),
                                ordinaryWorld.getTopology().findSystem(destination).orElseThrow().name(), "Прыжки личного корабля",
                                !player.docked() && fuel.supported() && fuel.feasible() ? "Можно проверить вылет" : "Вылет недоступен",
                                List.of(InfoSection.of("Вылет по прямому ребру", "Отправление", ordinaryWorld.getTopology().findSystem(placement.systemId()).orElseThrow().name(),
                                        "Необходимое изменение скорости", String.format(Locale.ROOT, "%.1f м/с", fuel.requiredDeltaVMps()),
                                        "Расход реактивной массы", String.format(Locale.ROOT, "%.1f кг", fuel.consumedReactionMassKg()),
                                        "Остаток реактивной массы", String.format(Locale.ROOT, "%.1f кг", fuel.remainingReactionMassKg()),
                                        "Условия", "Отстыкованный личный корабль; существующее топливо; движение к выходу, зарядка и перелёт на общих тактах")),
                                "Прямое соединение систем; положение корабля и топливо на борту. Время перелёта идёт вместе с кампанией",
                                null, 0, world.worldTick()));
                    }
                }
                craftRows.add(0, row("personal-ship:" + id.value(), "Личный корабль " + id.value(), "Мои корабли",
                        player.activeFleetId() != null && player.activeFleetId().equals(id) ? "Активный" : "В собственности",
                        personalSections,
                        "Личные права владения и кошелёк пилота; фактическое местоположение существующего корабля",
                        placement.locationKind() == com.spacesim.world.FleetLocationKind.IN_SYSTEM ? placement.systemId() : null,
                        id.value(), world.worldTick()));
            }
        });
        campaign.playerState().ifPresent(player -> {
            if (player.activeFleetId() == null) return;
            var runtime = campaign.coordinator().runtime();
            var active = runtime.world().findFleet(player.activeFleetId()).orElseThrow();
            if (active.locationKind() != com.spacesim.world.FleetLocationKind.IN_SYSTEM) return;
            for (var offered : runtime.freight().capture().freighters()) {
                var placement = runtime.world().findFleet(offered.fleetId()).orElseThrow();
                if (player.ownedFleetIds().contains(offered.fleetId()) || offered.phase() != com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase.IDLE
                        || offered.cargoMassKg() != 0d || placement.locationKind() != com.spacesim.world.FleetLocationKind.IN_SYSTEM
                        || !placement.systemId().equals(active.systemId()) || runtime.world().findFleetJump(offered.fleetId()).isPresent()) continue;
                craftRows.add(row("pilot-reserve|" + offered.fleetId().value(), "Резервный грузовой корабль " + offered.fleetId().value(),
                        "Покупка существующих кораблей", "Требуется местный док продавца",
                        List.of(InfoSection.of("Предложение корпуса", "Цена", credits(Stage228CampaignAuthority.PILOT_SHIP_PRICE_MILLI_CREDITS),
                                "Продавец", factionName(world, offered.stableFactionId()), "Трюм", offered.cargoCapacityKg() + " кг",
                                "Оплата", "Личный кошелёк → казна продавца", "Условия", "Стыковка у местной станции продавца; существующий свободный пустой корпус",
                                "После покупки", "Активный корабль сохраняется; для смены управления отстыкуйтесь и остановитесь")),
                        "Свободный пустой корпус в текущей системе; местный продавец и оплата в его казну",
                        placement.systemId(), offered.fleetId().value(), world.worldTick()));
            }
        });
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
        campaign.playerState().ifPresent(player -> {
            var npcState = campaign.coordinator().npcMissions();
            for (var mission : npcState.missions()) {
                var issuer = npcState.npcs().stream()
                        .filter(npc -> npc.npcId().equals(mission.issuerNpcId())).findFirst().orElseThrow();
                if (mission.status() == com.spacesim.world.Stage21HNpcMissionState.MissionStatus.OFFERED
                        && !player.discoveredSystemIds().contains(issuer.locationSystemId())) continue;
                contacts.add(row("player-mission:" + mission.missionId(), label(mission.template().name()),
                        "Личные контракты", label(mission.status().name()),
                        List.of(InfoSection.of("Контракт игрока", "Выдаёт", label(issuer.nameKey()),
                                "Состояние", label(mission.status().name()),
                                "Цель", missionObjectiveDescription(mission.objective()),
                                "Срок включительно", mission.deadlineTick() + " такт",
                                "Награда", credits(mission.rewardMilliCredits()),
                                "Эскроу", credits(mission.escrowMilliCredits()),
                                "Исход", mission.outcomeCode().isEmpty() ? "Нет" : label(mission.outcomeCode())),
                                InfoSection.of("Участие", "Условие выплаты",
                                        "Подтверждённая цель и участие игрока; чужая работа не оплачивается",
                                        "Проверка действий", "Предпросмотр ставит кампанию на паузу; затем подтвердите действие")),
                        "Stage 21H: существующий funded contract и PlayerState; награда переводится из эскроу, срок — общий simulation tick",
                        null, 0, mission.statusUpdatedTick()));
            }
            for (var reputation : npcState.reputations()) {
                if (!reputation.subjectActorId().equals(com.spacesim.world.Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID)) continue;
                contacts.add(row("player-reputation:" + reputation.ownerId(), "Личная репутация у «" + label(reputation.ownerId()) + "»",
                        "Личная репутация", Integer.toString(reputation.derivedValue()),
                        List.of(InfoSection.of("Отношение к игроку", "Значение", Integer.toString(reputation.derivedValue()),
                                "Подтверждённых событий", Integer.toString(reputation.events().size()))),
                        "Stage 21H: наблюдённые исходы контрактов actor.player; сумма ограничена от −100 до 100",
                        null, 0, world.worldTick()));
            }
        });
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
                        "Формат", "M22.8 campaign v5")),
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

    private static String missionObjectiveDescription(com.spacesim.world.Stage21HNpcMissionState.MissionObjective objective) {
        String target = label(objective.kind().name());
        return switch (objective.kind()) {
            case FREIGHT_ORDER_DELIVERED_KG_AT_LEAST -> "Доставить по назначенному транспортному контракту не менее " + objective.threshold() + " кг";
            case FLEET_REACTION_MASS_KG_AT_LEAST -> "Пополнить реакционную массу назначенного корабля до " + objective.threshold() + " кг";
            case DERELICT_DISCOVERED_AND_SALVAGED_KG_AT_LEAST -> "Обнаружить назначенные обломки и извлечь не менее " + objective.threshold() + " кг";
            case CONSTRUCTION_DELIVERED_UNITS_AT_LEAST -> "Доставить в назначенный строительный проект не менее " + objective.threshold() + " единиц";
            case FACTION_TREASURY_AT_LEAST -> "Обеспечить казну назначенной фракции не менее " + credits(objective.threshold());
            default -> target;
        };
    }

    /** Minimal display fallback; stable IDs remain internal selection keys. Full localization is 23C. */
    static String label(String value) {
        if (value == null || value.isBlank()) return "Нет сведений";
        return switch (value) {
            case "OFFERED" -> "Предложен";
            case "ACCEPTED" -> "Принят";
            case "COMPLETED" -> "Выполнен";
            case "FAILED" -> "Не выполнен";
            case "EXPIRED" -> "Срок истёк";
            case "CANCELLED" -> "Отменён";
            case "REJECTED" -> "Отклонён";
            case "EMERGENCY_SUPPLY_DELIVERY" -> "Срочная доставка снабжения";
            case "ORDINARY_MARKET_PROCUREMENT" -> "Закупка на рынке";
            case "CONVOY_ESCORT" -> "Сопровождение конвоя";
            case "STRANDED_FLEET_RESCUE_REFUEL" -> "Спасение и дозаправка корабля";
            case "SYSTEM_OBJECT_RECONNAISSANCE" -> "Разведка системы или объекта";
            case "DERELICT_INVESTIGATION_RECOVERY" -> "Исследование и разбор обломков";
            case "INTERCEPTION_DEFENSE" -> "Перехват и оборона";
            case "CONSTRUCTION_REPAIR_INPUT_DELIVERY" -> "Доставка для строительства и ремонта";
            case "IMPERIAL_ACCESS_NEGOTIATION" -> "Переговоры о доступе";
            case "FLEET_PRESENT_IN_SYSTEM" -> "Назначенный корабль должен прибыть в целевую систему";
            case "FLEET_ABSENT" -> "Назначенный корабль больше не должен существовать";
            case "ESCORT_FLEETS_PRESENT_IN_SYSTEM" -> "Конвой и корабль сопровождения должны прибыть в целевую систему";
            case "DISCOVERY_AT_LEAST" -> "Получить требуемые сведения о назначенном объекте";
            case "CONSTRUCTION_COMPLETED" -> "Завершить назначенный строительный проект";
            case "MARKET_ACCESS_ALLOWED" -> "Получить законный доступ к назначенному рынку";
            case "OPERATION_STATUS" -> "Довести назначенную операцию до требуемого результата";
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
