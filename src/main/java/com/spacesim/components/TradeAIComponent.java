package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.persistence.EntityId;

/**
 * Состояние конечного автомата автономного торгового флота.
 *
 * <p>Компонент хранит только сериализуемое поведенческое состояние и план маршрута. Ссылки на
 * станции представлены устойчивыми {@link EntityId}, а не runtime-объектами Ashley. Authoritative
 * денежный баланс вынесен в {@link WalletComponent}; фактический груз хранится в
 * {@link InventoryComponent}. Runtime-система разрешает ID через registry.</p>
 */
public class TradeAIComponent implements Component {
    /** Этап выполнения торгового маршрута. */
    public enum State {
        /** Флот свободен и может искать новый маршрут. */
        IDLE,
        /** Флот движется к станции покупки. */
        TRAVEL_TO_BUY,
        /** Флот достиг станции покупки и должен выполнить сделку. */
        BUYING,
        /** Флот движется к станции продажи. */
        TRAVEL_TO_SELL,
        /** Флот достиг станции продажи и должен выполнить сделку. */
        SELLING
    }

    /** Текущий этап автомата. */
    public State state = State.IDLE;
    /** Persistent ID станции запланированной покупки или {@code null}. */
    public EntityId buyStationId;
    /** Persistent ID станции запланированной продажи или {@code null}. */
    public EntityId sellStationId;
    /** Persistent ID текущей навигационной цели или {@code null}. */
    public EntityId targetStationId;
    /** Идентификатор товара либо {@code -1}, если маршрут не выбран. */
    public int targetItem = -1;
    /** Товарная специализация новых маршрутов; {@code -1} означает любой совместимый товар. */
    public int specializedItem = -1;
    /** Планируемое количество товара. */
    public int targetAmount;
    /** Ограничение AI на количество перевозимого груза. */
    public int cargoSpace = 100;
    /** Линейная скорость движения в мировых единицах в секунду. */
    public float movementSpeed = 100f;
    /** Оценка валовой прибыли текущего маршрута в milli-credits. */
    public long expectedProfitMilliCredits;
    /** Оставшееся время до повторного поиска маршрута. */
    public float routeSearchCooldown;

    /** Создаёт свободный торговый автомат с параметрами по умолчанию. */
    public TradeAIComponent() {
    }

    /**
     * Очищает persistent-план маршрута, не меняя конфигурацию корабля и фактические компоненты
     * склада/кошелька.
     */
    public void resetRoute() {
        buyStationId = null;
        sellStationId = null;
        targetStationId = null;
        targetItem = -1;
        targetAmount = 0;
        expectedProfitMilliCredits = 0L;
    }
}
