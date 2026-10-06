package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.*;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import java.util.*;

/** Actual owner or paid civilian physical repair jobs and compatible berth diagnostics. */
public final class GeneratedCampaignRepairUi {
    private GeneratedCampaignRepairUi() { }
    /**
     * Projects owner work or actual docked civilian service and exact persisted progress without mutation.
     * @param campaign actual campaign
     * @return authorized repair rows
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        if (campaign.playerState().isEmpty()) return List.of();
        var rows = new ArrayList<ProductionUiSnapshot.Row>();
        for (var station : campaign.coordinator().runtime().industry().industrial().stations()) {
            var job = campaign.repairQueue().orders().stream().filter(o -> o.stationId().equals(station.stationId())).findFirst().orElse(null);
            boolean own = campaign.ownsProductionStation(station.stationId());
            if (!own && job == null && !campaign.pilotMarketReference(station.stationId())
                    .filter(ref -> ref.equals(campaign.playerState().orElseThrow().dockedAt())).isPresent()) continue;
            boolean access = campaign.hasPersonalRepairAccess(station.stationId());
            var fields = new ArrayList<String>();
            fields.add("Доступ"); fields.add(access ? "Свой корабль у совместимой действующей верфи" : "Нужны стыковка своего корабля и совместимая действующая верфь");
            fields.add("Расходы"); fields.add(own ? "На своей станции расходуются её материалы и работа; денежного начисления нет"
                    : "Тариф: 50 кр./кг материалов и 1 кр./секунду инженерной работы. Деньги резервируются до завершения");
            if (!own) {
                long fee = job != null && job.servicePayment() != null ? job.servicePayment().reservedMilliCredits()
                        : campaign.personalRepairPriceMilliCredits(station.stationId());
                fields.add(job == null ? "Денежный резерв" : "Деньги в резерве");
                fields.add(fee > 0 ? String.format(Locale.ROOT, "%.3f кр.", fee / 1000d) : "Нет доступной котировки ремонта");
            }
            fields.add("Время"); fields.add("Только завершённые игровые такты; на паузе ремонт не выполняется");
            if (job != null) {
                fields.add("Выполнено"); fields.add(String.format(Locale.ROOT, "%.2f %%", 100 * job.completedWorkSeconds() / job.requiredWorkSeconds()));
                fields.add("Материалы в работе"); fields.add(String.format(Locale.ROOT, "%.1f кг; продолжают занимать место", job.reservedCommodityMassByIdKg().values().stream().mapToDouble(Double::doubleValue).sum()));
                fields.add("Отмена"); fields.add(job.servicePayment() == null
                        ? "Вернёт материалы; выполненная работа не возвращается, повреждения не устраняются"
                        : "До начала работы возвращает весь денежный резерв и материал; после начала нужно завершить ремонт");
                fields.add("Корабль"); fields.add(Long.toString(job.fleetId()));
            } else {
                fields.add("Начало"); fields.add("Проверит повреждения и конечные материалы, затем зарезервирует их без мгновенного ремонта");
            }
            String key = job == null ? "pilot-repair|" + station.stationId() + "|"
                    : "pilot-repair-cancel|" + station.stationId() + '|' + job.orderId();
            rows.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW, key), "Ремонт своего корабля",
                    "Работы верфи", job == null ? access ? "Проверить материалы и поставить в очередь" : "Доступ сейчас отсутствует"
                            : access ? "Заказ в работе" : "Заказ ожидает доступа и мощностей",
                    List.of(InfoSection.of("Физический ремонт", fields.toArray(String[]::new))),
                    "Существующий корабль, установленная верфь, сохранённый резерв и фактическая работа",
                    station.systemId(), job == null ? 0 : job.fleetId(), job == null ? 0 : job.startedAtTick()));
        }
        return List.copyOf(rows);
    }
}
