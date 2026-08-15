package com.spacesim.trade;

import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.model.ShipType;

import java.util.Arrays;

/**
 * Неизменяемый снимок торговых ограничений одного флота для pure route planner.
 *
 * <p>Профиль копирует mutable ECS-массивы и не содержит Ashley {@code Entity}. Благодаря этому
 * planner можно тестировать и в будущем выполнять вне ship FSM.</p>
 */
public final class FleetTradeProfile {
    private final float x;
    private final float y;
    private final float movementSpeed;
    private final long walletBalanceMilliCredits;
    private final int inventoryCapacity;
    private final int totalStock;
    private final int cargoSpace;
    private final int specializedItem;
    private final boolean hasShipComponent;
    private final ShipType shipType;
    private final int factionId;
    private final int[] stock;
    private final float[] reputation;

    /**
     * Создаёт immutable профиль поиска маршрута.
     *
     * @param x текущая координата X
     * @param y текущая координата Y
     * @param movementSpeed скорость движения в мировых единицах в секунду
     * @param walletBalanceMilliCredits текущий баланс
     * @param inventoryCapacity физическая вместимость inventory
     * @param totalStock суммарный фактический груз
     * @param cargoSpace дополнительный AI-лимит груза
     * @param specializedItem runtime ID специализации или {@code -1}
     * @param hasShipComponent существует ли {@code ShipComponent}
     * @param shipType runtime cargo policy; может быть {@code null}, если компонент повреждён
     * @param stock копируемые остатки по runtime item ID
     * @param reputation копируемая репутация по faction ID
     */
    public FleetTradeProfile(
            float x,
            float y,
            float movementSpeed,
            long walletBalanceMilliCredits,
            int inventoryCapacity,
            int totalStock,
            int cargoSpace,
            int specializedItem,
            boolean hasShipComponent,
            ShipType shipType,
            int[] stock,
            float[] reputation) {
        this(x, y, movementSpeed, walletBalanceMilliCredits, inventoryCapacity, totalStock, cargoSpace,
                specializedItem, hasShipComponent, shipType, -1, stock, reputation);
    }

    /**
     * Creates a planning profile with explicit runtime faction membership.
     *
     * @param x current X coordinate
     * @param y current Y coordinate
     * @param movementSpeed movement speed
     * @param walletBalanceMilliCredits wallet balance
     * @param inventoryCapacity physical inventory capacity
     * @param totalStock total physical cargo
     * @param cargoSpace AI cargo limit
     * @param specializedItem specialization item or -1
     * @param hasShipComponent whether ShipComponent exists
     * @param shipType cargo policy or null
     * @param factionId runtime faction ID or -1
     * @param stock copied stock array
     * @param reputation copied reputation array; either legacy authored-faction count or full runtime capacity
     */
    public FleetTradeProfile(
            float x,
            float y,
            float movementSpeed,
            long walletBalanceMilliCredits,
            int inventoryCapacity,
            int totalStock,
            int cargoSpace,
            int specializedItem,
            boolean hasShipComponent,
            ShipType shipType,
            int factionId,
            int[] stock,
            float[] reputation) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("Координаты флота должны быть конечными");
        }
        if (!Float.isFinite(movementSpeed) || movementSpeed < 0f) {
            throw new IllegalArgumentException("Скорость флота должна быть конечной и неотрицательной");
        }
        if (walletBalanceMilliCredits < 0L) {
            throw new IllegalArgumentException("Баланс флота не может быть отрицательным");
        }
        if (inventoryCapacity < 0 || totalStock < 0 || cargoSpace < 0) {
            throw new IllegalArgumentException("Вместимость и количество груза не могут быть отрицательными");
        }
        if (specializedItem < -1 || specializedItem >= Constants.MAX_ITEMS) {
            throw new IllegalArgumentException("Некорректная специализация товара");
        }
        if (factionId < -1 || factionId >= Constants.FACTION_RUNTIME_CAPACITY) {
            throw new IllegalArgumentException("Некорректный runtime faction ID флота");
        }
        if (stock == null || stock.length != Constants.MAX_ITEMS) {
            throw new IllegalArgumentException("stock должен иметь длину Constants.MAX_ITEMS");
        }
        if (reputation == null
                || (reputation.length != Constants.LEGACY_FACTION_COUNT
                && reputation.length != Constants.FACTION_RUNTIME_CAPACITY)) {
            throw new IllegalArgumentException(
                    "reputation должен иметь legacy authored count или runtime faction capacity");
        }
        this.x = x;
        this.y = y;
        this.movementSpeed = movementSpeed;
        this.walletBalanceMilliCredits = walletBalanceMilliCredits;
        this.inventoryCapacity = inventoryCapacity;
        this.totalStock = totalStock;
        this.cargoSpace = cargoSpace;
        this.specializedItem = specializedItem;
        this.hasShipComponent = hasShipComponent;
        this.shipType = shipType;
        this.factionId = factionId;
        this.stock = Arrays.copyOf(stock, stock.length);
        this.reputation = Arrays.copyOf(reputation, Constants.FACTION_RUNTIME_CAPACITY);
    }

    /** @return текущая X-координата */
    public float x() {
        return x;
    }

    /** @return текущая Y-координата */
    public float y() {
        return y;
    }

    /** @return скорость движения */
    public float movementSpeed() {
        return movementSpeed;
    }

    /** @return текущий баланс в milli-credits */
    public long walletBalanceMilliCredits() {
        return walletBalanceMilliCredits;
    }

    /** @return runtime faction ID или {@code -1} */
    public int factionId() {
        return factionId;
    }

    /** @return физически свободная вместимость inventory */
    public int inventoryFreeCapacity() {
        return Math.max(0, inventoryCapacity - totalStock);
    }

    /** @return свободная вместимость с учётом AI cargoSpace */
    public int routeCargoCapacity() {
        return Math.max(0, Math.min(cargoSpace - totalStock, inventoryFreeCapacity()));
    }

    /** @return runtime ID специализации или {@code -1} */
    public int specializedItem() {
        return specializedItem;
    }

    /**
     * Возвращает фактический запас товара на борту.
     *
     * @param itemId runtime ID
     * @return количество либо 0 для некорректного ID
     */
    public int stock(int itemId) {
        return itemId >= 0 && itemId < stock.length ? stock[itemId] : 0;
    }

    /**
     * Возвращает репутацию у фракции.
     *
     * @param factionId runtime faction ID
     * @return репутация либо 0 для некорректного ID
     */
    public float reputation(int factionId) {
        return factionId >= 0 && factionId < reputation.length ? reputation[factionId] : 0f;
    }

    /**
     * Проверяет, разрешена ли покупка товара cargo policy этого флота.
     *
     * @param item data-driven товар
     * @return {@code true}, если specialization и ship policy допускают покупку
     */
    public boolean canPurchase(ContentCatalog.ItemDefinition item) {
        if (item == null || (specializedItem != -1 && specializedItem != item.runtimeId())) {
            return false;
        }
        return !hasShipComponent
                || (shipType != null && shipType.canPurchase(item.category(), item.mineable()));
    }

    /**
     * Сравнивает все входы pure route planner без использования hash/fuzzy equality.
     *
     * <p>Метод предназначен для transient memoization только отрицательного результата planner.
     * Если он возвращает {@code true} и revision рынка не изменился, повторный вызов planner получил
     * бы побитово те же входные данные и обязан вернуть тот же результат.</p>
     *
     * @param other другой immutable профиль
     * @return {@code true}, если все planner-relevant поля и массивы совпадают точно
     */
    public boolean samePlanningState(FleetTradeProfile other) {
        return other != null
                && Float.floatToIntBits(x) == Float.floatToIntBits(other.x)
                && Float.floatToIntBits(y) == Float.floatToIntBits(other.y)
                && Float.floatToIntBits(movementSpeed) == Float.floatToIntBits(other.movementSpeed)
                && walletBalanceMilliCredits == other.walletBalanceMilliCredits
                && inventoryCapacity == other.inventoryCapacity
                && totalStock == other.totalStock
                && cargoSpace == other.cargoSpace
                && specializedItem == other.specializedItem
                && hasShipComponent == other.hasShipComponent
                && shipType == other.shipType
                && factionId == other.factionId
                && Arrays.equals(stock, other.stock)
                && Arrays.equals(reputation, other.reputation);
    }
}
