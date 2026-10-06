package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.content.ship.Stage22FreightStrategicEngineeringCatalogLoader;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.*;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import java.util.*;

/** Own station refit equipment, actual work and target hold diagnostics. */
public final class GeneratedCampaignRefitUi {
    private GeneratedCampaignRefitUi() { }
    /**
     * Projects real player-owned work and supported same-hull conversion without mutation.
     * @param campaign current campaign
     * @return own physical refit rows
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        var player = campaign.playerState().orElse(null);
        if (player == null || player.activeFleetId() == null) return List.of();
        var freight = campaign.coordinator().runtime().freight().findFreighter(player.activeFleetId()).orElse(null);
        boolean mining = freight != null && freight.fitId().equals(Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT);
        String target = mining ? Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT
                : Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT;
        var rows = new ArrayList<ProductionUiSnapshot.Row>();
        for (var station : campaign.coordinator().runtime().industry().industrial().stations()) {
            var job = campaign.refitQueue().orders().stream().filter(o -> o.stationId().equals(station.stationId())).findFirst().orElse(null);
            boolean own = campaign.ownsProductionStation(station.stationId());
            if (!own && job == null && !campaign.pilotMarketReference(station.stationId()).filter(ref -> ref.equals(player.dockedAt())).isPresent()) continue;
            boolean targetMining = job == null ? !mining : job.targetFit().installedModules().stream()
                    .anyMatch(m -> m.moduleId().equals(com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID));
            String targetId = targetMining ? Stage22FreightStrategicEngineeringCatalogLoader.UNION_MINING_FREIGHT_STRATEGIC_FIT
                    : Stage22FreightStrategicEngineeringCatalogLoader.UNION_FREIGHT_STRATEGIC_FIT;
            boolean access = campaign.hasPersonalRefitAccess(station.stationId(), targetId);
            var fields = new ArrayList<String>();
            Collections.addAll(fields, "Цель", targetMining ? "Шахтёрская секция" : "Грузовая секция",
                    "Трюм", "После установки шахтёрской секции: 8 млн кг руды и отдельно 1 млн кг расходников; грузовая компоновка: 12 млн кг",
                    "Оборудование", "Нужен готовый новый модуль на складе оператора; резерв продолжает занимать место",
                    "Снятая секция", "Сохраняется на станции с её повреждениями и возрастом; не становится новым товаром",
                    "Время", "Работа расходуется только на завершённых тактах из общего бюджета установленной верфи");
            if (!own) Collections.addAll(fields, "Оплата", "Резерв до завершения: 50 кр./кг новых изделий и 1 кр./секунду инженерной работы",
                    "Снятое оборудование", "Остаётся личной собственностью на складе оператора; повреждение и возраст сохраняются");
            if (!own && job == null) {
                long quote = campaign.personalRefitPriceMilliCredits(station.stationId(), targetId);
                Collections.addAll(fields, "Стоимость", quote > 0 ? String.format(Locale.ROOT, "%.3f кр.", quote / 1000d) : "Нет доступной котировки");
            }
            if (job != null && job.servicePayment() != null) Collections.addAll(fields, "Деньги в резерве",
                    String.format(Locale.ROOT, "%.3f кр.", job.servicePayment().reservedMilliCredits() / 1000d),
                    "Возврат денег", "Отмена до начала работы возвращает весь резерв; после начала требуется завершение");
            if (job != null) {
                Collections.addAll(fields, "Выполнено", String.format(Locale.ROOT, "%.2f %%", 100 * job.completedWorkSeconds() / job.requiredWorkSeconds()),
                        "Корабль", Long.toString(job.fleetId()), "Отмена", "Вернёт входящее оборудование; выполненная работа не возвращается");
            }
            String key = job == null ? "pilot-refit|" + station.stationId() + '|' + target
                    : "pilot-refit-cancel|" + station.stationId() + '|' + job.orderId();
            rows.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW, key), "Переоснащение своего корабля",
                    "Работы верфи", job == null ? access ? "Проверить оборудование и поставить в очередь" : "Доступ или целевая вместимость отсутствуют"
                            : access ? "Заказ в работе" : "Заказ ожидает доступа, вместимости или мощностей",
                    List.of(InfoSection.of("Физическое переоснащение", fields.toArray(String[]::new))),
                    "Тот же корабль, конечное оборудование, фактическая работа и сохранение снятой секции",
                    station.systemId(), job == null ? 0 : job.fleetId(), job == null ? 0 : job.startedAtTick()));
        }
        return List.copyOf(rows);
    }
}
