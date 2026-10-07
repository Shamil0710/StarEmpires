package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import com.spacesim.world.Stage23FreightRouteReport;
import com.spacesim.world.Stage21HPlayerMissionAuthority;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Contact offers and personal historical charts from immutable actual delivery receipts. */
public final class GeneratedCampaignRouteMapUi {
    private GeneratedCampaignRouteMapUi() { }

    /**
     * Lists purchasable completed routes without exposing unpaid system names or coordinates.
     * @param campaign current personal campaign
     * @return immutable actual-contact offers
     */
    public static List<ProductionUiSnapshot.Row> offers(Stage228CampaignAuthority campaign) {
        var result = new ArrayList<ProductionUiSnapshot.Row>();
        var runtime = campaign.coordinator().runtime();
        int ordinal = 0;
        for (var npc : campaign.coordinator().npcMissions().npcs()) {
            if (!npc.npcId().startsWith("npc.stage23b.dispatcher:") || !campaign.canContactNpc(npc.npcId())) continue;
            for (var entry : runtime.discoveryState().knowledgeFor(npc.factionContentId()).entries()) for (var evidence : entry.evidence()) {
                if (Stage23FreightRouteReport.decode(entry.object(), evidence).isEmpty()) continue;
                String key = Stage23FreightRouteReport.offerKey(entry.object(), evidence.provenanceId());
                if (!campaign.canPurchaseRouteMap(npc.npcId(), key)) continue;
                result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,
                        "pilot-route-map|" + npc.npcId() + '|' + key), "Карта пройденного маршрута " + ++ordinal,
                        "Маршрутные карты", "Путь подтверждён доставкой перевозчика",
                        List.of(InfoSection.of("Передача маршрута", "Цена", "100 кредитов из личного кошелька в казну оператора",
                                "Содержимое", "Пройденные системы и архивное положение станции назначения",
                                "История", "Фиксация пути при успешной выгрузке; исходная дата сохраняется",
                                "Безопасность", "Текущих наблюдений угроз и гарантий прохода нет")),
                        "Неизменяемый отчёт фактической доставки; планируемые пути не продаются", null, 0,
                        runtime.world().getAuthoritativeWorldTick()));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Presents paid historical paths with their own original evidence time.
     * @param campaign current personal campaign
     * @return owned charts, without substituting current station observation age or threat intel
     */
    public static List<ProductionUiSnapshot.Row> personal(Stage228CampaignAuthority campaign) {
        var result = new ArrayList<ProductionUiSnapshot.Row>();
        var runtime = campaign.coordinator().runtime();
        for (var entry : runtime.discoveryState().knowledgeFor(Stage21HPlayerMissionAuthority.PLAYER_ACTOR_ID).entries())
            for (var evidence : entry.evidence()) {
                var report = Stage23FreightRouteReport.decodeShared(entry.object(), evidence);
                if (report.isEmpty()) continue;
                String path = String.join(" → ", report.orElseThrow().systems().stream().map(s ->
                        runtime.world().getTopology().findSystem(s).map(node -> node.name()).orElse("Неизвестная система")).toList());
                result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,
                        "personal-route|" + entry.object().objectId() + '|' + evidence.provenanceId()), "Купленная маршрутная карта",
                        "Исторические маршруты", path,
                        List.of(InfoSection.of("Исторический путь", "Системы", path,
                                "Исходное наблюдение", String.format(Locale.ROOT, "%.2f с", evidence.observedAtSeconds()),
                                "Содержание", "Пройденный путь и сохранённая станция назначения",
                                "Перед вылетом", "Проверьте личный маршрут, топливо и известные вам угрозы")),
                        "Оплаченная копия архивной доставки; новых измерений и приказов полёта нет", entry.object().systemId(), 0,
                        runtime.world().getAuthoritativeWorldTick()));
            }
        return List.copyOf(result);
    }
}
