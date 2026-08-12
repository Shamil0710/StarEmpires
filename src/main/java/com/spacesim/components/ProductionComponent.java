package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.model.Recipe;
import java.util.ArrayList;
import java.util.List;

/**
 * Производственная конфигурация и прогресс ECS-сущности.
 *
 * <p>Список рецептов обычно заполняется при создании станции.
 * {@link com.spacesim.systems.ProductionSystem} читает активный рецепт,
 * изменяет склад, накапливает {@link #progressSeconds} и сбрасывает прогресс,
 * если ресурсов либо вместимости недостаточно. В один момент времени активен
 * не более чем один рецепт.</p>
 *
 * <p>Компонент не копирует рецепты и не синхронизирует список. Изменять
 * конфигурацию следует на игровом потоке; элементы списка должны быть
 * ненулевыми экземплярами {@link Recipe}.</p>
 */
public class ProductionComponent implements Component {
    /**
     * Упорядоченный изменяемый список доступных рецептов.
     * Индекс в этом списке связывается с {@link #activeRecipeIndex}.
     */
    public final List<Recipe> recipes = new ArrayList<>();

    /**
     * Индекс активного рецепта. Отрицательное значение или индекс за пределами
     * {@link #recipes} означает, что производство выключено.
     */
    public int activeRecipeIndex = 0;

    /**
     * Время, уже отработанное в текущем цикле, в секундах игрового времени.
     * При штатной работе значение конечно, неотрицательно и меньше длительности
     * активного рецепта; производственная система является владельцем поля.
     */
    public float progressSeconds = 0f;

    /**
     * Создаёт компонент без рецептов и накопленного прогресса.
     * Индекс {@code 0} станет активным после добавления первого рецепта; пока
     * список пуст, {@link #getActiveRecipe()} возвращает {@code null}.
     */
    public ProductionComponent() {
    }

    /**
     * Разрешает текущий индекс в объект рецепта.
     *
     * @return активный рецепт либо {@code null}, если список пуст, индекс
     *         отрицателен или выходит за границы списка; если вызывающий код
     *         явно поместил {@code null} в список, возвращается {@code null}
     */
    public Recipe getActiveRecipe() {
        if (activeRecipeIndex < 0 || activeRecipeIndex >= recipes.size()) {
            return null;
        }
        return recipes.get(activeRecipeIndex);
    }
}
