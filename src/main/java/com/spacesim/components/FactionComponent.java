package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.constants.Constants;

/**
 * Принадлежность ECS-сущности к одной экономической фракции.
 *
 * <p>Компонент назначается станциям при создании мира. Контроллер торговли
 * читает фракцию станции, чтобы выбрать репутацию участника и вычислить
 * поправку цены; интерфейс использует её для отображения имени. Поле открыто
 * для конфигурации сущности и может быть изменено кодом игрового мира.</p>
 */
public class FactionComponent implements Component {
    /**
     * Идентификатор фракции.
     *
     * <p>Штатное значение находится в диапазоне
     * {@code [0, Constants.MAX_FACTIONS)}. Компонент намеренно допускает
     * произвольное значение; {@link #getFactionName()} безопасно отображает
     * такое состояние как неизвестную фракцию.</p>
     */
    public int factionId;

    /**
     * Создаёт компонент с указанной принадлежностью без проверки диапазона.
     *
     * @param id идентификатор фракции; для штатной фракции следует использовать
     *           одну из констант {@code Constants.FACTION_*}
     */
    public FactionComponent(int id) {
        this.factionId = id;
    }

    /**
     * Возвращает отображаемое имя текущей фракции.
     *
     * @return элемент {@link Constants#FACTION_NAMES} для допустимого
     *         идентификатора либо строка {@code "Неизвестная фракция"}
     */
    public String getFactionName() {
        if (factionId < 0 || factionId >= Constants.FACTION_NAMES.length) {
            return "Неизвестная фракция";
        }
        return Constants.FACTION_NAMES[factionId];
    }
}
