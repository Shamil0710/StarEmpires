package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Time controls projected from the same live campaign as the other menus. */
public final class GeneratedCampaignTimeSkipUi {
    private GeneratedCampaignTimeSkipUi() { }

    /**
     * Adds a lightweight menu row without recapturing the world.
     * @param source current presentation
     * @param campaign owning simulation
     * @param minutes editable whole game minutes
     * @param editing whether numeric input is captured
     * @return immutable presentation with current time controls
     */
    public static ProductionUiSnapshot append(ProductionUiSnapshot source, Stage228CampaignAuthority campaign,
            String minutes, boolean editing) {
        var request = campaign.timeSkip();
        String state = request.active() ? campaign.coordinator().isPaused() ? "Пропуск на паузе" : "Пропуск выполняется"
                : campaign.lastTimeSkipMessage().isEmpty() ? "Пропуск не запущен" : campaign.lastTimeSkipMessage();
        long tick = campaign.coordinator().runtime().world().getAuthoritativeWorldTick();
        double elapsed = request.active() ? (tick - request.startedTick()) * campaign.coordinator().session().fixedStepSeconds() / 60d : 0;
        String mode = !request.active() ? "—" : request.mode() == com.spacesim.simulation.CampaignTimeSkipState.Mode.EVENT
                ? "До следующего личного события или этапа перелёта" : "На " + request.requestedMinutes() + " игровых минут";
        var row = new ProductionUiSnapshot.Row(new GeneratedWorldCommandUiRenderer.UiSelection(
                GeneratedWorldCommandUiRenderer.SelectionKind.SURFACE_ROW, "time-control"),
                "Пропуск времени", "Время", state, List.of(
                GeneratedWorldUiSnapshot.InfoSection.of("Управление временем", "Состояние", state, "Режим", mode,
                        "Игровые минуты", minutes + (editing ? " | ввод: Enter — готово, Esc — отмена" : ""),
                        "Прошло игровых минут", request.active() ? String.format(java.util.Locale.ROOT, "%.2f", elapsed) : "—"),
                GeneratedWorldUiSnapshot.InfoSection.of("Во время пропуска", "Интерфейс", "Меню и просмотр сведений доступны",
                        "Пауза / продолжить", "Пробел", "Остановка", "Отмена, повреждение корабля или новые сведения об угрозе")),
                "Продвигается общая игровая симуляция, включая расход ресурсов и работу остальных кораблей.", null, 0, tick);
        var surfaces = new EnumMap<GeneratedWorldCommandUiRenderer.Tab, List<ProductionUiSnapshot.Row>>(GeneratedWorldCommandUiRenderer.Tab.class);
        surfaces.putAll(source.surfaces());
        var rows = new ArrayList<>(source.rows(GeneratedWorldCommandUiRenderer.Tab.SETTINGS));
        rows.removeIf(r -> r.selection().stableId().equals("time-control")); rows.add(row);
        surfaces.put(GeneratedWorldCommandUiRenderer.Tab.SETTINGS, List.copyOf(rows));
        return new ProductionUiSnapshot(source.tick(), source.viewerName(), surfaces);
    }
}
