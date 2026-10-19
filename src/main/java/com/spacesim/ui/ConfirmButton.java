package com.spacesim.ui;

import com.spacesim.economy.Money;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Confirmation button that exists only after a preview and names what will happen.
 *
 * <p>Before a preview there is no button; a rejected preview shows a disabled button pointing to
 * the inspector conditions, which stay visible while the status line changes. Labels are built from the previewed command and its
 * exact wallet change; they never decide whether the command is allowed. Pure presentation.</p>
 */
public final class ConfirmButton {
    /** Disabled label after a rejected preview; it points to the inspector conditions, which stay visible. */
    static final String UNAVAILABLE = "НЕДОСТУПНО — ПРОВЕРЬТЕ УСЛОВИЯ ВЫШЕ";
    private static final String PREFIX = "ПОДТВЕРДИТЬ: ";
    private static final Set<String> KILOGRAMS = Set.of("BUY", "SELL", "LOAD_COMMODITY", "UNLOAD_COMMODITY",
            "LOAD_CONSUMABLE", "BUY_CONSUMABLE", "START_REFINING", "START_COMPONENT_MANUFACTURING");
    private static final Set<String> PIECES = Set.of("LOAD_AMMUNITION", "START_MANUFACTURING",
            "LOAD_PRODUCT", "UNLOAD_PRODUCT", "BUY_PRODUCT");
    private static final Map<String, String> ACTIONS = Map.ofEntries(
            Map.entry("DOCK", "СТЫКОВКА"), Map.entry("UNDOCK", "ОТСТЫКОВКА"), Map.entry("JUMP", "ВЫЛЕТ В ПРЫЖОК"),
            Map.entry("SCAN", "ИМПУЛЬС РАДАРА"), Map.entry("BUY", "ПОКУПКА"), Map.entry("SELL", "ПРОДАЖА"),
            Map.entry("LOAD_COMMODITY", "ПОГРУЗКА"), Map.entry("UNLOAD_COMMODITY", "ВЫГРУЗКА"),
            Map.entry("LOAD_CONSUMABLE", "ЗАГРУЗКА РАСХОДНИКОВ"), Map.entry("BUY_CONSUMABLE", "ПОКУПКА РАСХОДНИКОВ"),
            Map.entry("LOAD_AMMUNITION", "ЗАГРУЗКА БОЕЗАПАСА"), Map.entry("START_MINING", "НАЧАЛО ДОБЫЧИ"),
            Map.entry("STOP_MINING", "ОСТАНОВКА ДОБЫЧИ"), Map.entry("PURCHASE", "ПОКУПКА КОРАБЛЯ"),
            Map.entry("SWITCH", "ПЕРЕХОД НА КОРАБЛЬ"), Map.entry("PURCHASE_STATION", "ПОКУПКА СТАНЦИИ"),
            Map.entry("CAPITALIZE", "ВЗНОС В КАЗНУ"), Map.entry("WITHDRAW", "ВОЗВРАТ ИЗ КАЗНЫ"),
            Map.entry("REPORT_DISCOVERY", "ОТЧЁТ О НАХОДКЕ"), Map.entry("ACKNOWLEDGE_JOURNAL", "ОТМЕТКА ПРОЧТЕНИЯ"),
            Map.entry("BUY_RESOURCE_MAP", "ПОКУПКА КАРТЫ РЕСУРСОВ"), Map.entry("BUY_STATION_MAP", "ПОКУПКА КАРТЫ СТАНЦИИ"),
            Map.entry("BUY_ROUTE_MAP", "ПОКУПКА КАРТЫ МАРШРУТА"), Map.entry("START_MANUFACTURING", "ЗАПУСК ПРОИЗВОДСТВА"),
            Map.entry("CANCEL_MANUFACTURING", "ОТМЕНА ПРОИЗВОДСТВА"), Map.entry("START_REFINING", "ПЕРЕРАБОТКА"),
            Map.entry("START_COMPONENT_MANUFACTURING", "ПРОИЗВОДСТВО КОМПОНЕНТОВ"),
            Map.entry("START_FACILITY_CONSTRUCTION", "НАЧАЛО СТРОИТЕЛЬСТВА"),
            Map.entry("CANCEL_FACILITY_CONSTRUCTION", "ОТМЕНА СТРОИТЕЛЬСТВА"),
            Map.entry("START_YARD_CONSTRUCTION", "СТРОИТЕЛЬСТВО ВЕРФИ"), Map.entry("CANCEL_YARD_CONSTRUCTION", "ОТМЕНА ВЕРФИ"),
            Map.entry("ALLOCATE_FACILITY_RESOURCES", "ВЫДЕЛЕНИЕ РЕСУРСОВ"),
            Map.entry("ALLOCATE_FACILITY_RESOURCES_HALF", "ВЫДЕЛЕНИЕ ПОЛОВИНЫ РЕСУРСОВ"),
            Map.entry("ALLOCATE_YARD_RESOURCES", "РЕСУРСЫ ДЛЯ ВЕРФИ"),
            Map.entry("ALLOCATE_YARD_RESOURCES_AVAILABLE", "ДОСТУПНЫЕ РЕСУРСЫ ДЛЯ ВЕРФИ"),
            Map.entry("START_SMALL_CRAFT_BUILD", "ПОСТРОЙКА АППАРАТА"), Map.entry("CANCEL_SMALL_CRAFT_BUILD", "ОТМЕНА ПОСТРОЙКИ"),
            Map.entry("CLAIM_SMALL_CRAFT_BUILD", "ПРИЁМ АППАРАТА"), Map.entry("START_REPAIR", "РЕМОНТ"),
            Map.entry("CANCEL_REPAIR", "ОТМЕНА РЕМОНТА"), Map.entry("START_REFIT", "ПЕРЕОСНАЩЕНИЕ"),
            Map.entry("START_REFIT_USED", "ПЕРЕОСНАЩЕНИЕ ХРАНИМЫМ МОДУЛЕМ"), Map.entry("CANCEL_REFIT", "ОТМЕНА ПЕРЕОСНАЩЕНИЯ"),
            Map.entry("LOAD_MODULE", "ПОГРУЗКА МОДУЛЯ"), Map.entry("UNLOAD_MODULE", "ВЫГРУЗКА МОДУЛЯ"),
            Map.entry("CANCEL_MODULE_TRANSFER", "ОТМЕНА ОБРАБОТКИ МОДУЛЯ"), Map.entry("BUY_PRODUCT", "ПОКУПКА ОБОРУДОВАНИЯ"),
            Map.entry("LOAD_PRODUCT", "ПОГРУЗКА ОБОРУДОВАНИЯ"), Map.entry("UNLOAD_PRODUCT", "ВЫГРУЗКА ОБОРУДОВАНИЯ"),
            Map.entry("CANCEL_PRODUCT_TRANSFER", "ОТМЕНА ОБРАБОТКИ ОБОРУДОВАНИЯ"));

    /**
     * What the confirmation area shows.
     *
     * @param visible whether a button is drawn at all
     * @param enabled whether it can be pressed
     * @param label button text
     */
    public record State(boolean visible, boolean enabled, String label) {
        /**
         * Validates one state.
         *
         * @param visible whether a button is drawn at all
         * @param enabled whether it can be pressed
         * @param label button text
         */
        public State {
            Objects.requireNonNull(label, "label");
            if (enabled && !visible) throw new IllegalArgumentException("an enabled button must be visible");
        }
    }

    private ConfirmButton() {
    }

    /** @return no button: nothing has been previewed */
    public static State hidden() {
        return new State(false, false, "");
    }

    /**
     * @param allowed whether the previewed command passed validation
     * @param outcome what the command will do, in capitals
     * @return enabled button naming the outcome, or a disabled one pointing to the inspector conditions
     */
    public static State previewed(boolean allowed, String outcome) {
        Objects.requireNonNull(outcome, "outcome");
        return allowed ? new State(true, true, PREFIX + outcome) : new State(true, false, UNAVAILABLE);
    }

    /**
     * @param action previewed physical pilot action
     * @param amount kilograms or pieces sent with the preview
     * @param walletChangeMilliCredits exact previewed wallet change
     * @return outcome such as {@code ПОКУПКА 100 КГ • СПИСАНИЕ 4,800.00 КР}
     */
    public static String physicalOutcome(String action, int amount, long walletChangeMilliCredits) {
        StringBuilder outcome = new StringBuilder(ACTIONS.getOrDefault(action, "ДЕЙСТВИЕ"));
        if (KILOGRAMS.contains(action)) outcome.append(' ').append(amount).append(" КГ");
        if (action.equals("START_MINING")) outcome.append(" ДО ").append(amount).append(" КГ");
        if (PIECES.contains(action)) outcome.append(' ').append(amount).append(" ШТ.");
        return outcome.append(money(walletChangeMilliCredits)).toString();
    }

    /**
     * @param command previewed contract command name: ACCEPT, REJECT or CANCEL
     * @param supplyKilograms supply portion accepted with the contract, or 0
     * @param rewardMilliCredits proposed reward of an accepted supply portion, or 0
     * @return outcome such as {@code ПРИНЯТЬ ПОСТАВКУ 500 КГ • ПРЕМИЯ 1,200.00 КР}
     */
    public static String missionOutcome(String command, int supplyKilograms, long rewardMilliCredits) {
        return switch (command) {
            case "ACCEPT" -> supplyKilograms > 0
                    ? "ПРИНЯТЬ ПОСТАВКУ " + supplyKilograms + " КГ"
                            + (rewardMilliCredits > 0L ? " • ПРЕМИЯ " + credits(rewardMilliCredits) : "")
                    : "ПРИНЯТЬ КОНТРАКТ";
            case "REJECT" -> "ОТКЛОНИТЬ ПРЕДЛОЖЕНИЕ";
            case "CANCEL" -> "ОТМЕНИТЬ КОНТРАКТ";
            default -> "ДЕЙСТВИЕ ПО КОНТРАКТУ";
        };
    }

    private static String money(long walletChangeMilliCredits) {
        if (walletChangeMilliCredits < 0L) return " • СПИСАНИЕ " + credits(-walletChangeMilliCredits);
        if (walletChangeMilliCredits > 0L) return " • ПОСТУПЛЕНИЕ " + credits(walletChangeMilliCredits);
        return "";
    }

    private static String credits(long milliCredits) {
        // Three decimals: wallet changes are exact milli-credits.
        return String.format(Locale.ROOT, "%,.3f КР", Money.toCredits(milliCredits));
    }
}
