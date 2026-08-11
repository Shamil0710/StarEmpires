package com.spacesim.controllers;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.*;
import com.spacesim.constants.Constants;

/**
 * Выполняет синхронные операции покупки и продажи между станцией и участником торговли.
 *
 * <p>Контроллер не хранит состояние конкретной сделки: балансы, склады и рыночные цены передаются
 * через компоненты Ashley и изменяемые объекты-счета. Перед изменением данных методы проверяют
 * идентификатор товара, положительность количества и цены, наличие товара и свободного места,
 * целостность массивов, отсутствие целочисленного переполнения и возможность представить итоговую
 * сумму типом {@code float}. Если проверка не пройдена, операция возвращает {@code false} и при
 * последовательном использовании штатных компонентов не изменяет переданные объекты.</p>
 *
 * <p>Денежные значения хранятся в {@code float}. Поэтому недостаточно математически положительной
 * стоимости: списание или начисление должно строго изменить представимое значение баланса. Например,
 * слишком малая цена на фоне баланса, близкого к {@link Float#MAX_VALUE}, приводит к отказу, а не к
 * бесплатной передаче товара. Общая сумма сначала вычисляется с точностью {@code double}, после чего
 * проверяется на допустимость для {@code float}.</p>
 *
 * <p>Один объект склада нельзя передавать одновременно как склад станции и второй стороны сделки.
 * Упрощённый профиль игрока дополнительно не должен использовать тот же массив груза, что и станция.
 * Эти ограничения предотвращают взаимное погашение списания и зачисления при фактической оплате.</p>
 *
 * <p>Класс рассчитан на вызов из одного потока игрового цикла. Он не синхронизирует состав сущностей
 * и переданные изменяемые компоненты; параллельные сделки с общими объектами должны сериализоваться
 * вызывающим кодом.</p>
 */
public class TradeController {
    private final ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    private final ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private final ComponentMapper<FactionComponent> fm = ComponentMapper.getFor(FactionComponent.class);

    /**
     * Создаёт контроллер без привязки к конкретному движку или набору сущностей.
     */
    public TradeController() {
    }

    /**
     * Упрощённый изменяемый профиль игрока для базового сценария покупки.
     *
     * <p>Профиль не задаёт общую вместимость груза и не учитывает репутацию. Корректное состояние
     * предполагает конечный неотрицательный баланс и неотрицательные количества товаров. Экземпляр
     * не является потокобезопасным.</p>
     */
    public static class PlayerProfile {
        /**
         * Текущий баланс кредитов.
         *
         * <p>Перед покупкой значение должно быть конечным и неотрицательным.</p>
         */
        public float credits;

        /**
         * Количество товаров в грузе игрока по идентификатору товара.
         *
         * <p>Массив должен содержать индекс покупаемого товара и не должен совпадать по ссылке с
         * {@link InventoryComponent#stock} станции. По умолчанию создаётся массив из 20 элементов.</p>
         */
        public int[] cargo = new int[20];

        /**
         * Создаёт профиль с нулевым балансом и пустым массивом груза стандартного размера.
         */
        public PlayerProfile() {
        }
    }

    /**
     * Покупает товар у станции с использованием упрощённого профиля игрока.
     *
     * <p>Цена берётся напрямую из {@link MarketComponent#sellPrices}; репутационная скидка и общая
     * вместимость груза в этом варианте не учитываются. Успешная покупка уменьшает склад станции,
     * увеличивает соответствующую ячейку груза, списывает кредиты и помечает рынок как требующий
     * пересчёта.</p>
     *
     * @param station станция с компонентами рынка и склада
     * @param itemId идентификатор покупаемого товара
     * @param amount положительное количество единиц товара
     * @param player изменяемый профиль покупателя
     * @return {@code true}, если товар и деньги были переданы; {@code false}, если запрос или текущее
     *         состояние не позволяют провести сделку
     */
    public boolean buy(Entity station, int itemId, int amount, PlayerProfile player) {
        if (station == null
                || player == null
                || player.cargo == null
                || !isValidBalance(player.credits)
                || amount <= 0
                || itemId < 0
                || itemId >= Constants.MAX_ITEMS
                || itemId >= player.cargo.length
                || !im.has(station)
                || !mm.has(station)) {
            return false;
        }

        InventoryComponent inv = im.get(station);
        MarketComponent market = mm.get(station);

        if (!isValidInventory(inv)
                || !isValidMarket(market)
                || !market.isTradable(itemId)
                || player.cargo == inv.stock
                || player.cargo[itemId] < 0) {
            return false;
        }

        float unitPrice = market.sellPrices[itemId];
        float cost = calculateTotalPrice(unitPrice, amount);
        float resultingBalance = player.credits - cost;

        if (isValidPrice(unitPrice)
                && isValidPrice(cost)
                && isValidBalance(resultingBalance)
                && resultingBalance < player.credits
                && inv.stock[itemId] >= amount
                && player.credits >= cost
                && player.cargo[itemId] <= Integer.MAX_VALUE - amount) {
            inv.stock[itemId] -= amount;
            player.credits = resultingBalance;
            player.cargo[itemId] += amount;
            market.isDirty = true;
            return true;
        }
        return false;
    }

    /**
     * Покупает товар у станции без учёта репутации покупателя.
     *
     * @param station станция-продавец с компонентами рынка и склада
     * @param buyerInventory склад покупателя; не должен быть складом станции
     * @param itemId идентификатор покупаемого товара
     * @param amount положительное количество единиц товара
     * @param buyerCredits изменяемый счёт покупателя
     * @return {@code true} при полном успешном выполнении сделки, иначе {@code false}
     * @see #buyFromStation(Entity, InventoryComponent, int, int, CreditAccount, ReputationComponent)
     */
    public boolean buyFromStation(Entity station, InventoryComponent buyerInventory, int itemId, int amount, CreditAccount buyerCredits) {
        return buyFromStation(station, buyerInventory, itemId, amount, buyerCredits, null);
    }

    /**
     * Покупает товар у станции с учётом репутации покупателя.
     *
     * <p>Положительная репутация у фракции станции уменьшает отпускную цену, но не более чем на
     * {@link Constants#MAX_REPUTATION_PRICE_BONUS}. Сделка выполняется только целиком: частичная
     * покупка не производится. После успеха рынок помечается как изменённый, а репутация переданной
     * стороны повышается, если у станции задана фракция.</p>
     *
     * @param station станция-продавец с компонентами рынка и склада
     * @param buyerInventory склад покупателя; должен иметь достаточную свободную вместимость и не
     *        совпадать с компонентом склада станции
     * @param itemId идентификатор покупаемого товара из допустимого диапазона
     * @param amount положительное количество единиц товара
     * @param buyerCredits изменяемый счёт с конечным неотрицательным балансом
     * @param buyerReputation репутация покупателя или {@code null}, если скидка и её увеличение не нужны
     * @return {@code true}, если товар, кредиты и связанные признаки были обновлены; {@code false},
     *         если хотя бы один инвариант сделки не выполнен
     */
    public boolean buyFromStation(Entity station, InventoryComponent buyerInventory, int itemId, int amount,
                                  CreditAccount buyerCredits, ReputationComponent buyerReputation) {
        if (!isValidTradeRequest(station, buyerInventory, itemId, amount)
                || buyerCredits == null
                || !isValidBalance(buyerCredits.credits)) {
            return false;
        }

        InventoryComponent stationInventory = im.get(station);
        float unitPrice = getEffectiveSellPrice(station, itemId, buyerReputation);
        float cost = calculateTotalPrice(unitPrice, amount);
        float resultingBalance = buyerCredits.credits - cost;

        if (!isValidPrice(unitPrice)
                || !isValidPrice(cost)
                || !isValidBalance(resultingBalance)
                || resultingBalance >= buyerCredits.credits
                || stationInventory.stock[itemId] < amount
                || buyerCredits.credits < cost
                || getFreeCapacity(buyerInventory) < amount) {
            return false;
        }

        stationInventory.stock[itemId] -= amount;
        buyerInventory.stock[itemId] += amount;
        buyerCredits.credits = resultingBalance;
        mm.get(station).isDirty = true;
        increaseReputation(station, buyerReputation);
        return true;
    }

    /**
     * Продаёт товар станции без учёта репутации продавца.
     *
     * @param station станция-покупатель с компонентами рынка и склада
     * @param sellerInventory склад продавца; не должен быть складом станции
     * @param itemId идентификатор продаваемого товара
     * @param amount положительное количество единиц товара
     * @param sellerCredits изменяемый счёт продавца
     * @return {@code true} при полном успешном выполнении сделки, иначе {@code false}
     * @see #sellToStation(Entity, InventoryComponent, int, int, CreditAccount, ReputationComponent)
     */
    public boolean sellToStation(Entity station, InventoryComponent sellerInventory, int itemId, int amount, CreditAccount sellerCredits) {
        return sellToStation(station, sellerInventory, itemId, amount, sellerCredits, null);
    }

    /**
     * Продаёт товар станции с учётом репутации продавца.
     *
     * <p>Положительная репутация у фракции станции увеличивает закупочную цену, но не более чем на
     * {@link Constants#MAX_REPUTATION_PRICE_BONUS}. Станция должна иметь место для всей партии, а
     * итоговый баланс должен оставаться конечным и строго увеличиваться в представлении
     * {@code float}. После успеха рынок помечается как изменённый и при наличии фракции повышается
     * репутация продавца.</p>
     *
     * @param station станция-покупатель с компонентами рынка и склада
     * @param sellerInventory склад продавца; должен содержать всю партию и не совпадать со складом
     *        станции
     * @param itemId идентификатор продаваемого товара из допустимого диапазона
     * @param amount положительное количество единиц товара
     * @param sellerCredits изменяемый счёт с конечным неотрицательным балансом
     * @param sellerReputation репутация продавца или {@code null}, если надбавка и её увеличение не нужны
     * @return {@code true}, если товар, кредиты и связанные признаки были обновлены; {@code false},
     *         если запрос нельзя выполнить целиком
     */
    public boolean sellToStation(Entity station, InventoryComponent sellerInventory, int itemId, int amount,
                                 CreditAccount sellerCredits, ReputationComponent sellerReputation) {
        if (!isValidTradeRequest(station, sellerInventory, itemId, amount)
                || sellerCredits == null
                || !isValidBalance(sellerCredits.credits)) {
            return false;
        }

        InventoryComponent stationInventory = im.get(station);
        float unitPrice = getEffectiveBuyPrice(station, itemId, sellerReputation);
        float revenue = calculateTotalPrice(unitPrice, amount);
        float resultingBalance = sellerCredits.credits + revenue;

        if (!isValidPrice(unitPrice)
                || !isValidPrice(revenue)
                || !isValidBalance(resultingBalance)
                || resultingBalance <= sellerCredits.credits
                || sellerInventory.stock[itemId] < amount
                || getFreeCapacity(stationInventory) < amount) {
            return false;
        }

        sellerInventory.stock[itemId] -= amount;
        stationInventory.stock[itemId] += amount;
        sellerCredits.credits = resultingBalance;
        mm.get(station).isDirty = true;
        increaseReputation(station, sellerReputation);
        return true;
    }

    /**
     * Рассчитывает цену, по которой станция продаёт одну единицу товара указанному участнику.
     *
     * <p>Для положительной репутации применяется скидка. Метод проверяет наличие и структуру рынка,
     * но не требует, чтобы товар был помечен торгуемым, и не исправляет некорректное содержимое самой
     * ячейки цены.</p>
     *
     * @param station станция, предоставляющая отпускную цену
     * @param itemId идентификатор товара
     * @param reputation репутация участника или {@code null} для цены без скидки
     * @return эффективная цена за единицу либо {@link Float#NaN}, если запрос структурно некорректен;
     *         результат также может быть бесконечным или равным {@code NaN} при некорректной
     *         исходной цене либо переполнении
     */
    public float getEffectiveSellPrice(Entity station, int itemId, ReputationComponent reputation) {
        if (!isValidPriceRequest(station, itemId)) {
            return Float.NaN;
        }
        MarketComponent market = mm.get(station);
        return market.sellPrices[itemId] * (1f - getReputationPriceBonus(station, reputation));
    }

    /**
     * Рассчитывает цену, по которой станция покупает одну единицу товара у указанного участника.
     *
     * <p>Для положительной репутации применяется надбавка. Метод проверяет наличие и структуру рынка,
     * но не требует, чтобы товар был помечен торгуемым; пригодность результата для сделки проверяют
     * методы продажи.</p>
     *
     * @param station станция, предоставляющая закупочную цену
     * @param itemId идентификатор товара
     * @param reputation репутация участника или {@code null} для цены без надбавки
     * @return эффективная цена за единицу либо {@link Float#NaN}, если запрос структурно некорректен;
     *         результат также может быть бесконечным или равным {@code NaN} при некорректной
     *         исходной цене либо переполнении
     */
    public float getEffectiveBuyPrice(Entity station, int itemId, ReputationComponent reputation) {
        if (!isValidPriceRequest(station, itemId)) {
            return Float.NaN;
        }
        MarketComponent market = mm.get(station);
        return market.buyPrices[itemId] * (1f + getReputationPriceBonus(station, reputation));
    }

    private float getReputationPriceBonus(Entity station, ReputationComponent reputation) {
        if (station == null || reputation == null || !fm.has(station)) {
            return 0f;
        }
        float reputationValue = reputation.getReputation(fm.get(station).factionId);
        if (!Float.isFinite(reputationValue)) {
            return 0f;
        }
        float normalized = Math.min(1f, Math.max(0f, reputationValue) / Constants.MAX_REPUTATION);
        return normalized * Constants.MAX_REPUTATION_PRICE_BONUS;
    }

    private void increaseReputation(Entity station, ReputationComponent reputation) {
        if (station != null && reputation != null && fm.has(station)) {
            reputation.addReputation(fm.get(station).factionId, Constants.REPUTATION_TRADE_GAIN);
        }
    }

    /**
     * Возвращает доступную вместимость склада с безопасной обработкой отсутствующего компонента.
     *
     * @param inventory проверяемый склад или {@code null}
     * @return свободная вместимость по правилам {@link InventoryComponent#getFreeCapacity()} либо
     *         {@code 0}, если склад не задан
     */
    public int getFreeCapacity(InventoryComponent inventory) {
        return inventory == null ? 0 : inventory.getFreeCapacity();
    }

    /**
     * Возвращает суммарное количество товаров на складе.
     *
     * @param inventory проверяемый склад или {@code null}
     * @return результат {@link InventoryComponent#getTotalStock()} либо {@code 0}, если склад не задан
     */
    public int getTotalStock(InventoryComponent inventory) {
        return inventory == null ? 0 : inventory.getTotalStock();
    }

    private boolean isValidTradeRequest(Entity station, InventoryComponent participantInventory, int itemId, int amount) {
        return station != null
                && participantInventory != null
                && im.has(station)
                && mm.has(station)
                && itemId >= 0
                && itemId < Constants.MAX_ITEMS
                && amount > 0
                && im.get(station) != participantInventory
                && isValidInventory(im.get(station))
                && isValidInventory(participantInventory)
                && isValidMarket(mm.get(station))
                && mm.get(station).isTradable(itemId);
    }

    private boolean isValidPriceRequest(Entity station, int itemId) {
        return station != null
                && itemId >= 0
                && itemId < Constants.MAX_ITEMS
                && mm.has(station)
                && isValidMarket(mm.get(station));
    }

    private boolean isValidInventory(InventoryComponent inventory) {
        if (inventory == null
                || inventory.stock == null
                || inventory.stock.length < Constants.MAX_ITEMS
                || inventory.capacity < 0) {
            return false;
        }
        for (int amount : inventory.stock) {
            if (amount < 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidMarket(MarketComponent market) {
        return market != null
                && market.sellPrices != null
                && market.buyPrices != null
                && market.sellPrices.length >= Constants.MAX_ITEMS
                && market.buyPrices.length >= Constants.MAX_ITEMS
                && market.targetStock != null
                && market.targetStock.length >= Constants.MAX_ITEMS
                && market.tradableItems != null
                && market.tradableItems.length >= Constants.MAX_ITEMS;
    }

    private boolean isValidBalance(float balance) {
        return Float.isFinite(balance) && balance >= 0f;
    }

    private boolean isValidPrice(float price) {
        return Float.isFinite(price) && price > 0f;
    }

    private float calculateTotalPrice(float unitPrice, int amount) {
        double total = (double) unitPrice * amount;
        if (!Double.isFinite(total) || total <= 0d || total > Float.MAX_VALUE) {
            return Float.NaN;
        }
        return (float) total;
    }

    /**
     * Минимальный изменяемый денежный счёт участника торговли.
     *
     * <p>Поле баланса открыто для простого обмена данными с ECS-компонентами. После ручного изменения
     * вызывающий код обязан сохранять инвариант конечного неотрицательного значения; торговые методы
     * отклоняют повреждённый счёт. Экземпляр не является потокобезопасным.</p>
     */
    public static class CreditAccount {
        /**
         * Текущий баланс кредитов как конечное неотрицательное значение {@code float}.
         */
        public float credits;

        /**
         * Создаёт счёт с указанным начальным балансом.
         *
         * @param credits конечный неотрицательный начальный баланс
         * @throws IllegalArgumentException если баланс отрицателен, бесконечен или равен
         *         {@link Float#NaN}
         */
        public CreditAccount(float credits) {
            if (!Float.isFinite(credits) || credits < 0f) {
                throw new IllegalArgumentException("Баланс должен быть конечным и неотрицательным");
            }
            this.credits = credits;
        }
    }
}
