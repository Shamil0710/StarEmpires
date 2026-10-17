package com.spacesim.ui;

import com.spacesim.campaign.Stage228CampaignAuthority;
import com.spacesim.world.SmallCraftId;
import com.spacesim.world.SmallCraftMissionState.MissionStatus;
import java.util.List;

/** Personal commands for existing individual craft; spectator selection confers no authority. */
public final class GeneratedCampaignCraftUi {
    private GeneratedCampaignCraftUi() { }

    /**
     * @param campaign actual player composition
     * @param selection current craft row or map identity
     * @return pending launch cancellation only for current personally owned craft
     */
    public static List<GeneratedCampaignFactionUi.Action> actions(Stage228CampaignAuthority campaign, String selection) {
        var id = craftId(selection);
        if (id == null || !GeneratedCampaignCarrierUi.ownedCraftIds(campaign).contains(id)) return List.of();
        if (campaign.carrierWings().stream().noneMatch(w -> w.craftIds().contains(id)
                && campaign.playerState().orElseThrow().ownedFleetIds().contains(w.carrierFleetId()))) return List.of();
        return campaign.missions().activeMissionFor(id).filter(m -> m.status() == MissionStatus.LAUNCH_QUEUED).isPresent()
                ? List.of(new GeneratedCampaignFactionUi.Action("craft-cancel-launch", "ОТМЕНИТЬ ЗАПУСК")) : List.of();
    }

    /**
     * @param campaign actual player composition
     * @param selection selected individual craft
     * @param action selected command verb
     * @return pure exact-checkpoint confirmation through common physical authority
     */
    public static Stage228CampaignAuthority.PlayerFactionCommandPreview preview(Stage228CampaignAuthority campaign,
            String selection, String action) {
        if (actions(campaign, selection).stream().noneMatch(a -> a.id().equals(action)))
            throw new IllegalArgumentException("Unavailable personal craft command");
        return campaign.previewPlayerCraftLaunchCancellation(craftId(selection));
    }

    private static SmallCraftId craftId(String selection) {
        if (selection == null || (!selection.startsWith("craft:") && !selection.startsWith("small-craft:"))) return null;
        try { return new SmallCraftId(Long.parseLong(selection.substring(selection.indexOf(':') + 1))); }
        catch (IllegalArgumentException exception) { return null; }
    }

    /**
     * @param designId accepted design identity
     * @return human-readable project name without internal IDs
     */
    public static String designName(String designId) {
        return switch (designId) {
            case "fit.empire.small_craft.interceptor_v1" -> "Перехватчик Империи";
            case "fit.empire.small_craft.defence_v1" -> "Аппарат обороны Империи";
            case "fit.empire.small_craft.strike_v1" -> "Ударный аппарат Империи";
            case "fit.industrial_union.small_craft.interceptor_v1" -> "Перехватчик Союза";
            case "fit.industrial_union.small_craft.defence_v1" -> "Аппарат обороны Союза";
            case "fit.industrial_union.small_craft.strike_v1" -> "Ударный аппарат Союза";
            default -> "Индивидуальный проект аппарата";
        };
    }

    /**
     * @param state actual occupancy
     * @return human-readable physical handling state
     */
    public static String occupancyName(com.spacesim.world.SmallCraftHangarCapacity.OccupancyState state) {
        return switch (state) {
            case PARKED -> "На стоянке"; case SERVICING -> "Обслуживание"; case READY -> "Готов к запуску";
            case LAUNCHING -> "Подготовка запуска"; case RECOVERING -> "Приём в ангар";
        };
    }

    /**
     * @param type existing mission family
     * @return human-readable mission name
     */
    public static String missionName(com.spacesim.world.SmallCraftMissionState.MissionType type) {
        return switch (type) {
            case CAP -> "Оборонительное патрулирование"; case QRA -> "Срочный вылет";
            case INTERCEPTION -> "Перехват"; case ESCORT -> "Сопровождение"; case ANTI_SHIP_STRIKE -> "Удар по кораблю";
            case RECONNAISSANCE -> "Разведка"; case EW_SUPPORT -> "Радиоэлектронная поддержка";
            case RETURN -> "Возвращение"; case RECOVER -> "Посадка"; case DIVERT -> "Уход к запасному месту приёма";
        };
    }

    /**
     * @param status existing mission lifecycle
     * @return human-readable mission status
     */
    public static String missionStatus(MissionStatus status) {
        return switch (status) {
            case LAUNCH_QUEUED -> "Запуск в очереди"; case ACTIVE -> "Выполняется";
            case RETURNING -> "Возвращается"; case RECOVERY_PENDING -> "Ожидает приёма";
            case COMPLETE -> "Завершено"; case CANCELLED -> "Отменено"; case FAILED -> "Не выполнено";
        };
    }
}
