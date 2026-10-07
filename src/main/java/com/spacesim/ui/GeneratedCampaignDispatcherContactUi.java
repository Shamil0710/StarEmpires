package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.SelectionKind;
import com.spacesim.ui.GeneratedWorldCommandUiRenderer.UiSelection;
import com.spacesim.ui.GeneratedWorldUiSnapshot.InfoSection;
import java.util.ArrayList;
import java.util.List;

/** Local dispatcher services disclosed only at an actual personal contact. */
public final class GeneratedCampaignDispatcherContactUi {
    private GeneratedCampaignDispatcherContactUi() { }

    /**
     * Projects available conversations without transferring the dispatcher's private knowledge.
     * @param campaign current personal campaign
     * @return actual contacted dispatchers and their available paid reports and funded offers
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        var result = new ArrayList<ProductionUiSnapshot.Row>();
        var state = campaign.coordinator().npcMissions();
        var runtime = campaign.coordinator().runtime();
        for (var npc : state.npcs()) {
            if (!npc.npcId().startsWith("npc.stage23b.dispatcher:") || !campaign.canContactNpc(npc.npcId())) continue;
            long maps = runtime.discoveryState().knowledgeFor(npc.factionContentId()).entries().stream()
                    .filter(e -> campaign.canPurchaseResourceMap(npc.npcId(), e.object().objectId())).count();
            long stations = runtime.discoveryState().knowledgeFor(npc.factionContentId()).entries().stream()
                    .filter(e -> campaign.canPurchaseStationMap(npc.npcId(), e.object().objectId())).count();
            long offers = state.missions().stream().filter(m -> m.issuerNpcId().equals(npc.npcId())
                    && m.status() == com.spacesim.world.Stage21HNpcMissionState.MissionStatus.OFFERED).count();
            result.add(new ProductionUiSnapshot.Row(new UiSelection(SelectionKind.SURFACE_ROW,
                    "dispatcher-contact|" + npc.npcId()), npc.nameKey(), "Общение на станции", "Связь установлена",
                    List.of(InfoSection.of("Услуги диспетчера", "Ресурсные карты", maps == 0
                                    ? "Новых карт для вас нет" : maps + " — выберите карту в списке контактов",
                            "Цена карты", "100 кредитов из личного кошелька",
                            "Карты станций", stations == 0 ? "Новых карт станций для вас нет"
                                    : stations + " — выберите навигационную карту в списке контактов",
                            "Сведения ресурсной карты", "Архивное положение участка и подтверждённый образец; время наблюдения сохраняется",
                            "Предложения работы", offers == 0 ? "Доступных предложений нет"
                                    : offers + " — выберите контракт в списке контактов",
                            "Получение сведений", "Откройте предложение, проверьте условия и подтвердите действие")),
                    "Фактическая стыковка у действующего диспетчера; разговор не передаёт его архив",
                    null, 0, runtime.world().getAuthoritativeWorldTick()));
        }
        return List.copyOf(result);
    }
}
