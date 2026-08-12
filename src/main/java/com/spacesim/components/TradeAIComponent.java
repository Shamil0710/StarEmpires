package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

/**
 * Состояние конечного автомата автономного торгового флота.
 *
 * <p>Компонент хранит только поведенческое состояние и план маршрута. Authoritative денежный
 * баланс вынесен в {@link WalletComponent}; фактический груз хранится в {@link InventoryComponent}.
 * Поэтому AI-состояние больше не может случайно расходиться с общим экономическим балансом сущности.</p>
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
    /** Станция запланированной покупки или {@code null}. */
    public Entity buyStation;
    /** Станция запланированной продажи или {@code null}. */
    public Entity sellStation;
    /** Текущая навигационная цель или {@code null}. */
    public Entity targetStation;
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
     * Очищает оперативный план маршрута, не меняя конфигурацию корабля и фактические компоненты
     * склада/кошелька.
     */
    public void resetRoute() {
        buyStation = null;
        sellStation = null;
        targetStation = null;
        targetItem = -1;
        targetAmount = 0;
        expectedProfitMilliCredits = 0L;
    }
}
