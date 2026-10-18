package com.spacesim.ui;

import com.spacesim.ui.GeneratedWorldCommandUiRenderer.Tab;

/**
 * Context controls shown at the right of the status bar, instead of inside map titles.
 *
 * <p>The status message keeps priority: hints are shown only when the message still has a
 * readable width beside them. Pure presentation text and geometry.</p>
 */
final class ControlHints {
    private static final String SEPARATOR = "  •  ";
    /** Zoom and pan work on both maps; Home resets only the system map, so it is not listed here. */
    private static final String MAP = String.join(SEPARATOR, "Колесо — масштаб", "СКМ — панорама");
    private static final String LIST = String.join(SEPARATOR, "Стрелки — выбор", "PgUp/PgDn — прокрутка",
            "Tab — фокус", "Enter — действие", "Esc — назад");

    /**
     * Status-bar split between the message and the hints.
     *
     * @param statusWidth width available to the status message
     * @param hintsShown whether the hints are drawn at the right
     */
    record Split(float statusWidth, boolean hintsShown) { }

    private ControlHints() {
    }

    /**
     * @param tab active top-level tab
     * @param flying whether the player's active ship is undocked in a system (manual thrust)
     * @return hints for the tab
     */
    static String forTab(Tab tab, boolean flying) {
        return switch (tab) {
            case SYSTEM -> String.join(SEPARATOR, MAP, "Home — обзор", "двойной клик — сопровождение", "C — к выбранному")
                    + (flying ? SEPARATOR + "WASD — тяга" + SEPARATOR + "X — торможение" : "");
            case GALAXY -> MAP;
            default -> LIST;
        };
    }

    /**
     * Splits the status bar so the message stays readable. A message that fits beside the hints
     * is shown whole; a longer one is truncated beside them only while it keeps at least
     * {@code minStatus}, otherwise the hints are hidden and the message gets the whole bar.
     *
     * @param available total width of the status bar content
     * @param statusTextWidth full width of the current message
     * @param hintsWidth width of the hints text
     * @param gap gap between message and hints
     * @param minStatus narrowest message width worth keeping beside the hints
     * @return the message width and whether the hints fit
     */
    static Split split(float available, float statusTextWidth, float hintsWidth, float gap, float minStatus) {
        float beside = available - gap - hintsWidth;
        if (statusTextWidth <= beside || beside >= minStatus) {
            return new Split(Math.max(0f, beside), true);
        }
        return new Split(Math.max(0f, available), false);
    }
}
