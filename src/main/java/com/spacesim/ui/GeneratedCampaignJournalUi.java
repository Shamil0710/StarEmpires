package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.player.PlayerJournalState;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import com.spacesim.ui.ProductionUiSnapshot.Row;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Projects only the active player's actual retained commits, regardless of faction viewer. */
public final class GeneratedCampaignJournalUi {
    private GeneratedCampaignJournalUi() { throw new AssertionError("No instances"); }

    /**
     * Personal history and durable unread notifications.
     * @param campaign actual player authority
     * @return immutable rows; empty before player initialization
     */
    public static List<Row> rows(Stage228CampaignAuthority campaign) {
        if (campaign.playerState().isEmpty()) return List.of();
        var journal = campaign.playerJournal();
        var result = new ArrayList<Row>();
        long last = journal.nextSequence() - 1;
        result.add(new Row(new UiSelection(SelectionKind.SURFACE_ROW, "pilot-journal|" + last),
                "Личные уведомления", "Личный журнал", journal.unreadCount() + " непрочитанных",
                List.of(InfoSection.of("Подтверждённая история", "Непрочитано", Long.toString(journal.unreadCount()),
                        "Сохранено записей", Integer.toString(journal.entries().size()),
                        "Отметка прочтения", Long.toString(journal.acknowledgedThroughSequence()),
                        "Объём истории", "Последние " + PlayerJournalState.CAPACITY + " событий; более ранние записи не хранятся",
                        "Условия", "Предпросмотры и загрузка не создают событий. Отметка прочтения сохраняется.")),
                "История подтверждённых действий собственного игрока", null, 0,
                campaign.coordinator().runtime().world().getAuthoritativeWorldTick()));
        for (int i = journal.entries().size() - 1; i >= 0; i--) {
            var e = journal.entries().get(i);
            boolean unread = e.sequence() > journal.acknowledgedThroughSequence();
            String measure = e.action().equals("BUY") || e.action().equals("SELL") || e.action().equals("LOAD_CONSUMABLE")
                    || e.action().equals("YARD_MATERIAL_DELIVERED")
                    ? " кг" : e.action().equals("START_MANUFACTURING") || e.kind() == PlayerJournalState.Kind.MANUFACTURING_COMPLETED
                    || e.action().equals("LOAD_PRODUCT") || e.action().equals("UNLOAD_PRODUCT")
                    || e.action().equals("PRODUCT_TRANSFER_COMPLETED") ? " шт."
                    : java.util.Set.of("REPAIR_SERVICE_SETTLED", "REFIT_SERVICE_SETTLED").contains(e.action()) ? " мкр." : "";
            result.add(new Row(new UiSelection(SelectionKind.SURFACE_ROW, "personal-event:" + e.sequence()),
                    description(e), "Личный журнал", unread ? "Новое событие" : "Прочитано",
                    List.of(InfoSection.of("Фактическое событие", "Такт", Long.toString(e.tick()),
                            "Время кампании", String.format(Locale.ROOT, "%.1f с", e.tick() * campaign.coordinator().session().fixedStepSeconds()),
                            "Событие", description(e), "Изменение кошелька", String.format(Locale.ROOT, "%+.3f кр.", e.walletDeltaMilliCredits() / 1000d),
                            "Количество", e.quantity() == 0 ? "Не применяется" : e.quantity() + measure,
                            "Корабль", e.fleetId() == 0 ? "Не применяется" : Long.toString(e.fleetId()),
                            "Место / получатель", e.endpoint().isEmpty() ? "Не применяется" : e.endpoint(),
                            "Предмет / контракт", e.subject().isEmpty() ? "Не применяется" : e.subject())),
                    "Подтверждённое действие или результат общей физической службы; запись " + e.sequence(),
                    null, e.fleetId(), e.tick()));
        }
        return List.copyOf(result);
    }

    private static String description(PlayerJournalState.Entry e) {
        if (e.kind() == PlayerJournalState.Kind.MANUFACTURING_COMPLETED) return "Изготовление завершено";
        if (e.kind() == PlayerJournalState.Kind.REPAIR_COMPLETED) return "Ремонт завершён";
        if (e.kind() == PlayerJournalState.Kind.REFIT_COMPLETED) return "Переоснащение завершено";
        if (e.kind() == PlayerJournalState.Kind.MODULE_TRANSFER_COMPLETED) return "Перемещение оборудования завершено";
        if (e.kind() == PlayerJournalState.Kind.MINING_STOPPED) return switch (e.action()) {
            case "DIRECT_CONTROL" -> "Добыча остановлена: ручное управление";
            case "CONTACT_UNAVAILABLE" -> "Добыча остановлена: потерян контакт или доступ";
            case "SOURCE_DEPLETED" -> "Добыча остановлена: источник исчерпан";
            default -> "Добыча остановлена: извлечение недоступно";
        };
        if (e.kind() == PlayerJournalState.Kind.MISSION_CHANGED) return switch (e.action()) {
            case "COMPLETED" -> "Контракт выполнен";
            case "FAILED" -> "Контракт завершён неудачей";
            case "EXPIRED" -> "Срок контракта истёк";
            default -> "Статус контракта изменён";
        };
        return switch (e.action()) {
            case "LOAD_PRODUCT" -> "Погрузка готового товара поставлена в очередь";
            case "UNLOAD_PRODUCT" -> "Выгрузка готового товара поставлена в очередь";
            case "CANCEL_PRODUCT_TRANSFER" -> "Обработка товара отменена; резерв освобождён";
            case "PRODUCT_TRANSFER_COMPLETED" -> "Перемещение готового товара завершено";
            case "START_REPAIR" -> "Ремонт поставлен в очередь";
            case "START_REFIT" -> "Переоснащение поставлено в очередь";
            case "START_REFIT_USED" -> "Установка снятого оборудования поставлена в очередь";
            case "CANCEL_REFIT" -> "Переоснащение отменено";
            case "CANCEL_REPAIR" -> "Ремонт отменён; материалы возвращены";
            case "PILOT_START" -> "Начало личной кампании и покупка корабля";
            case "DOCK" -> "Стыковка выполнена";
            case "UNDOCK" -> "Отстыковка выполнена";
            case "BUY" -> "Груз куплен";
            case "SELL" -> "Груз продан";
            case "PURCHASE" -> "Корабль приобретён";
            case "PURCHASE_STATION" -> "Существующая станция приобретена";
            case "SWITCH" -> "Управление кораблём передано";
            case "JUMP" -> "Приказ на межсистемный вылет принят";
            case "CAPITALIZE" -> "Средства внесены в собственную казну";
            case "WITHDRAW" -> "Средства возвращены из собственной казны";
            case "LOAD_CONSUMABLE" -> "Расходник загружен из собственного груза";
            case "START_MINING" -> "Задание на добычу принято";
            case "STOP_MINING" -> "Добыча остановлена игроком";
            case "START_MANUFACTURING" -> "Материалы зарезервированы для изготовления";
            case "START_FACILITY_CONSTRUCTION" -> "Материалы зарезервированы для строительства";
            case "CANCEL_FACILITY_CONSTRUCTION" -> "Строительство отменено; материалы возвращены";
            case "FACILITY_CONSTRUCTION_COMPLETED" -> "Промышленная установка построена";
            case "START_YARD_CONSTRUCTION" -> "Создан заказ верфи; доступные материалы будут доставлены на стройплощадку";
            case "CANCEL_YARD_CONSTRUCTION" -> "Строительство верфи отменено до начала работы";
            case "YARD_CONSTRUCTION_COMPLETED" -> "Структура верфи построена; рабочие ресурсы ещё не выделены";
            case "YARD_MATERIAL_DELIVERED" -> "Материалы перевезены со склада на стройплощадку верфи";
            case "ALLOCATE_YARD_RESOURCES" -> "Существующие ресурсы верфей станции перераспределены";
            case "SELLER_STATION_BRIEFING" -> "Получены координаты гражданских станций из архива продавца корабля";
            case "REPAIR_SERVICE_SETTLED" -> "Денежный резерв ремонта перечислен владельцу верфи";
            case "REFIT_SERVICE_SETTLED" -> "Денежный резерв переоснащения перечислен владельцу верфи";
            case "ALLOCATE_FACILITY_RESOURCES" -> "Ресурсы станции перераспределены между установками";
            case "CANCEL_MANUFACTURING" -> "Изготовление отменено, материалы возвращены";
            case "REPORT_DISCOVERY" -> "Личное открытие передано получателю";
            case "FOUND_FACTION" -> "Собственная фракция основана";
            case "POLICY" -> "Фракционная политика принята";
            case "TREATY" -> "Дипломатическая команда принята";
            case "EMBARGO" -> "Правила доступа к рынку изменены";
            case "AFFILIATE_ASSETS" -> "Собственные активы зарегистрированы во фракции";
            case "FLEET_ORDER" -> "Приказ собственному флоту принят";
            case "FLEET_HOLD" -> "Приказ флоту: удерживать позицию";
            case "FLEET_MOVE" -> "Приказ флоту: перейти в систему";
            case "FLEET_FOLLOW" -> "Приказ флоту: следовать за кораблём";
            case "FLEET_ESCORT" -> "Приказ флоту: сопровождать корабль";
            case "FLEET_PATROL" -> "Приказ флоту: патрулировать маршрут";
            case "TERRITORY_CLAIM" -> "Территориальная претензия заявлена";
            case "TERRITORY_WITHDRAW" -> "Территориальная претензия отозвана";
            case "TERRITORY_RELINQUISH" -> "Территориальный контроль снят";
            case "TERRITORY_RECOGNIZE_CLAIM", "TERRITORY_RECOGNIZE_CONTROL" -> "Территориальное признание изменено";
            case "TERRITORY_GRANT_RIGHT", "TERRITORY_REVOKE_RIGHT" -> "Право строительства изменено";
            default -> "Личное действие выполнено";
        };
    }
}
