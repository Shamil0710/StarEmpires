package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.model.ItemType;
import com.spacesim.model.ShipType;

import java.util.Objects;

/**
 * Функциональная классификация одной корабельной ECS-сущности.
 *
 * <p>Компонент отделён от {@link IdentityComponent.Kind}: идентичность различает крупные классы
 * отображаемых объектов, а {@link #type} задаёт конкретную грузовую либо специальную роль корабля.
 * Поле открыто, чтобы игровой код мог заменить тип при переоборудовании. Временно не заданный
 * {@code null}-тип считается некорректной конфигурацией и безопасно отклоняет новые товары.</p>
 */
public class ShipComponent implements Component {
    /** Тип корабля; до завершения конфигурации временно может быть равен {@code null}. */
    public ShipType type;

    /**
     * Создаёт ещё не классифицированный корабельный компонент.
     *
     * <p>До присваивания {@link #type} оба метода проверки груза возвращают {@code false}.</p>
     */
    public ShipComponent() {
    }

    /**
     * Создаёт корабельный компонент с обязательным типом.
     *
     * @param type функциональный тип корабля; не {@code null}
     * @throws NullPointerException если тип не задан
     */
    public ShipComponent(ShipType type) {
        this.type = Objects.requireNonNull(type, "Тип корабля не должен быть null");
    }

    /**
     * Безопасно проверяет возможность хранить товар по числовому идентификатору.
     *
     * @param itemId идентификатор товара
     * @return {@code true}, если идентификатор существует и товар совместим с текущим типом
     */
    public boolean canCarryItem(int itemId) {
        ItemType item = ItemType.fromId(itemId);
        return type != null && type.canCarry(item);
    }

    /**
     * Безопасно проверяет возможность купить товар для нового торгового маршрута.
     *
     * @param itemId идентификатор товара
     * @return {@code true}, если идентификатор существует и текущему типу разрешена покупка
     */
    public boolean canPurchaseItem(int itemId) {
        ItemType item = ItemType.fromId(itemId);
        return type != null && type.canPurchase(item);
    }
}
