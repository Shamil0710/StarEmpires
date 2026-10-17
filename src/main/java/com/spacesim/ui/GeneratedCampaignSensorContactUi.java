package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.components.SensorKnowledgeComponent;
import com.spacesim.ship.DatalinkState;
import com.spacesim.world.FleetLocationKind;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

/** Personal sensor contacts show measured uncertainty for ships and stations. */
public final class GeneratedCampaignSensorContactUi {
    private GeneratedCampaignSensorContactUi() { }
    /**
     * Shows a personal scan action without performing a pulse or deriving target knowledge.
     * @param campaign owning player authority
     * @return action row when the player has an active ship
     */
    public static List<ProductionUiSnapshot.Row> actions(Stage228CampaignAuthority campaign) {
        var player = campaign.playerState().orElse(null);
        if (player == null || player.activeFleetId() == null) return List.of();
        return List.of(new ProductionUiSnapshot.Row(new GeneratedWorldCommandUiRenderer.UiSelection(
                GeneratedWorldCommandUiRenderer.SelectionKind.SURFACE_ROW, "pilot-scan|" + player.activeFleetId().value()),
                "Активное сканирование", "Личные сенсоры", "Проверить импульс",
                List.of(GeneratedWorldUiSnapshot.InfoSection.of("Импульс радара", "Оборудование", "Установленный активный радар",
                        "Условия", "Можно сканировать при движении и тяге; прыжок, добыча и обслуживание должны быть выключены",
                        "Время", "Один общий такт мира", "Расход", "Энергия и тепло установленного оборудования")),
                "Проверьте и подтвердите один импульс для поиска кораблей и станций. Дальность и качество контакта зависят от установленного радара и сигнала цели.",
                null, 0, 0));
    }
    /**
     * Reads only the active owned ship's local sensor knowledge.
     * @param campaign owning player authority
     * @return personally observed contacts without navigation to true foreign coordinates
     */
    public static List<ProductionUiSnapshot.Row> rows(Stage228CampaignAuthority campaign) {
        var p = campaign.playerState().orElse(null);
        if (p == null || p.activeFleetId() == null) return List.of();
        var world = campaign.coordinator().runtime().world();
        var fleet = world.findFleet(p.activeFleetId()).orElse(null);
        if (fleet == null || fleet.locationKind() != FleetLocationKind.IN_SYSTEM || world.findFleetJump(fleet.id()).isPresent()) return List.of();
        var sensor = world.findSession(fleet.systemId()).orElseThrow().getEntityRegistry().require(fleet.localEntityId())
                .getComponent(SensorKnowledgeComponent.class);
        if (sensor == null) return List.of();
        double now = world.getAuthoritativeWorldTick() * (double) campaign.coordinator().session().fixedStepSeconds();
        var result = new ArrayList<ProductionUiSnapshot.Row>();
        for (var track : sensor.tracks()) {
            double age = Math.max(0, now - track.lastMeasurementSeconds());
            if (age > DatalinkState.local().maxMeasurementAgeSeconds()) continue;
            String quality = switch (track.informationState()) {
                case DETECTED -> "Обнаружен сигнал"; case CLASSIFIED -> "Есть классификационные признаки";
                case TRACKED -> "Сопровождение"; case FIRE_CONTROL -> "Точное сопровождение";
            };
            var measurement = sensor.receivedMeasurements().stream().filter(m -> m.targetId() == track.targetId())
                    .max(java.util.Comparator.comparingDouble(com.spacesim.ship.SensorMeasurement::timestampSeconds)).orElse(null);
            String bearing = measurement == null ? "Нет свежего измерения" : String.format(Locale.ROOT, "%.2f° ± %.2f°",
                    Math.toDegrees(measurement.bearingRad()), Math.toDegrees(Math.sqrt(track.covariance().bearingVarianceRad2())));
            String position = track.positionKnown() ? String.format(Locale.ROOT, "(%.0f, %.0f) м; погрешность %.0f м", track.estimatedXM(),
                    track.estimatedYM(), Math.sqrt(track.covariance().positionVarianceM2())) : "Дальность и координаты не определены";
            result.add(new ProductionUiSnapshot.Row(new GeneratedWorldCommandUiRenderer.UiSelection(
                    GeneratedWorldCommandUiRenderer.SelectionKind.SURFACE_ROW, "personal-sensor-contact:" + fleet.systemId().value() + ':' + track.targetId()),
                    "Контакт " + (result.size() + 1), "Сенсорный контакт", quality,
                    List.of(GeneratedWorldUiSnapshot.InfoSection.of("Личное наблюдение", "Качество", quality, "Направление", bearing,
                            "Оценка положения", position, "Возраст измерения", String.format(Locale.ROOT, "%.1f с", age),
                            "Принадлежность", "Не установлена", "Состояние", age > 1.5 ? "Последнее известное наблюдение" : "Свежее наблюдение")),
                    "Измерения сенсоров установленного оборудования личного корабля. Контакт не раскрывает имя, груз, владельца или точную позицию цели.",
                    measuredPosition(track, measurement) ? fleet.systemId() : null, 0,
                    (long) (track.lastMeasurementSeconds() / campaign.coordinator().session().fixedStepSeconds())));
        }
        return List.copyOf(result);
    }

    /**
     * Projects anonymous map markers from the active ship's received range measurements only.
     * @param runtime live world used to locate the personally owned observer
     * @param player current personal ownership
     * @param active displayed local system
     * @return fresh measured positions; no target identity or truth coordinates
     */
    public static List<GeneratedWorldUiSnapshot.LocalObjectView> localObjects(
            com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            com.spacesim.player.PlayerState player, com.spacesim.world.StarSystemId active) {
        if (player == null || player.activeFleetId() == null
                || !player.ownedFleetIds().contains(player.activeFleetId())) return List.of();
        var world = runtime.world();
        var fleet = world.findFleet(player.activeFleetId()).orElse(null);
        if (fleet == null || fleet.locationKind() != FleetLocationKind.IN_SYSTEM
                || !fleet.systemId().equals(active) || world.findFleetJump(fleet.id()).isPresent()) return List.of();
        var sensor = world.findSession(active).orElseThrow().getEntityRegistry().require(fleet.localEntityId())
                .getComponent(SensorKnowledgeComponent.class);
        if (sensor == null) return List.of();
        double now = world.findSession(active).orElseThrow().getClock().getSimulationTimeSeconds();
        var result = new ArrayList<GeneratedWorldUiSnapshot.LocalObjectView>();
        for (var track : sensor.tracks()) {
            double age = Math.max(0, now - track.lastMeasurementSeconds());
            if (age > DatalinkState.local().maxMeasurementAgeSeconds()) continue;
            var measurement = sensor.receivedMeasurements().stream().filter(m -> m.targetId() == track.targetId())
                    .max(java.util.Comparator.comparingDouble(com.spacesim.ship.SensorMeasurement::timestampSeconds)).orElse(null);
            if (!measuredPosition(track, measurement)) continue;
            result.add(new GeneratedWorldUiSnapshot.LocalObjectView(
                    "personal-sensor-contact:" + active.value() + ':' + track.targetId(),
                    GeneratedWorldUiSnapshot.ObjectKind.LOCAL_ENTITY, "Сенсорный контакт", "Оценка положения",
                    active, com.spacesim.world.LocalPhysicalPosition.origin().translated(track.estimatedXM(), track.estimatedYM()),
                    "", "Не установлена", null,
                    List.of(GeneratedWorldUiSnapshot.InfoSection.of("Личное измерение",
                            "Положение", "Оценка по дальности и направлению; не точная координата",
                            "Погрешность", String.format(Locale.ROOT, "%.0f м (стандартное отклонение)", Math.sqrt(track.covariance().positionVarianceM2())),
                            "Возраст", String.format(Locale.ROOT, "%.1f с", age),
                            "Объект", "Имя, тип и принадлежность не установлены"))));
        }
        return List.copyOf(result);
    }

    private static boolean measuredPosition(com.spacesim.ship.TrackState track, com.spacesim.ship.SensorMeasurement measurement) {
        return track.positionKnown() && measurement != null && measurement.hasRange()
                && measurement.timestampSeconds() == track.lastMeasurementSeconds();
    }
}
