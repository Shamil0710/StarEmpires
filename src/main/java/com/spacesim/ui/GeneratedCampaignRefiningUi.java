package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.economy.Stage18ManufacturingWorkQueue;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Owner-bound refining recipes and reserved physical work; reads never create stock. */
public final class GeneratedCampaignRefiningUi {
    private GeneratedCampaignRefiningUi() { }

    /**
     * Projects real owned stores, authored yields and installed line access.
     * @param campaign current campaign
     * @return refining and cancellation rows
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        if (campaign.playerState().isEmpty()) return List.of();
        var result = new ArrayList<ProductionUiSnapshot.Row>();
        var runtime = campaign.coordinator().runtime();
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        for (var station : runtime.industry().industrial().stations()) {
            if (!campaign.ownsProductionStation(station.stationId())) continue;
            for (var recipe : Stage18RefiningCatalogLoader.loadDefault().getRecipes()) {
                var order = runtime.manufacturingQueue().capture().stream()
                        .filter(o -> o.orderId().startsWith(Stage18ManufacturingWorkQueue.REFINING_PREFIX)
                                && o.stationId().equals(station.stationId()) && o.operationId().equals(recipe.id()))
                        .findFirst().orElse(null);
                var fields = new ArrayList<String>();
                fields.add("Станция"); fields.add(station.stationId());
                fields.add("Масса партии"); fields.add("Кнопки -/+ кг: шаг 1; с Shift — 100; с Ctrl — 10 000. Масса указана на кнопке заказа.");
                fields.add("Линия"); fields.add(campaign.hasRefiningLine(station.stationId(), recipe.id())
                        ? "Есть совместимая действующая линия" : "Нет совместимой действующей линии");
                fields.add("Выход на 1 кг сырья"); fields.add(String.format(Locale.ROOT, "%.3f кг — %s",
                        recipe.outputMassFraction(), ontology.findCommodity(recipe.outputCommodityId()).displayName()));
                fields.add("Отходы на 1 кг сырья"); fields.add(String.format(Locale.ROOT, "%.3f кг; в склад материала не поступают",
                        recipe.discardedMassFraction()));
                for (var input : recipe.inputs()) {
                    fields.add(ontology.findCommodity(input.commodityId()).displayName());
                    fields.add(String.format(Locale.ROOT, "На 1 кг партии: %.3f кг; на складе %.3f кг",
                            input.fractionOfInputMass(), station.storage().commodityMassKg(input.commodityId())));
                }
                if (order != null) {
                    fields.add("Зарезервировано сырья"); fields.add(String.format(Locale.ROOT, "%.3f кг", order.requestedAmount()));
                    fields.add("Работа"); fields.add(String.format(Locale.ROOT, "%.2f %%", order.completedFraction() * 100));
                }
                fields.add("Порядок"); fields.add("Выберите массу партии и подтвердите резерв. Выпуск требует общих игровых тактов; отмена возвращает сырьё.");
                String id = order == null ? "pilot-refining|" + station.stationId() + '|' + recipe.id()
                        : "pilot-manufacturing-cancel|" + station.stationId() + '|' + order.orderId();
                result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW, id),
                        recipe.displayName(), "Переработка сырья", order == null ? "Заказать переработку" : "Работа зарезервированной партии",
                        List.of(InfoSection.of("Физическая переработка", fields.toArray(String[]::new))),
                        "Рецепт, реальные запасы собственной станции и сохраняемая очередь",
                        station.systemId(), 0, runtime.world().getAuthoritativeWorldTick()));
            }
        }
        return List.copyOf(result);
    }
}
