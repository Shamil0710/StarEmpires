package com.spacesim.controllers;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.*;
import com.spacesim.constants.Constants;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.economy.Money;

import java.util.Objects;

/**
 * Выполняет синхронные атомарные операции покупки и продажи.
 *
 * <p>Основной API Этапа 2 работает с двумя Ashley-сущностями, имеющими
 * {@link InventoryComponent} и {@link WalletComponent}. Обычная сделка физически переносит товар в
 * одну сторону и целочисленные milli-credits в другую; успешная операция фиксируется в
 * {@link EconomicLedger}. До мутации проверяются склады, вместимость, оба кошелька, рынок, цена и
 * переполнение, поэтому отказ не меняет состояние.</p>
 *
 * <p>Legacy API с {@link CreditAccount} и {@link PlayerProfile} временно сохранён для поэтапной
 * миграции существующих тестов и систем. Он не является частью нового денежного инварианта и будет
 * удалён после перевода TradeAI/Mining.</p>
 */
public class TradeController {
    private final ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    private final ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private final ComponentMapper<FactionComponent> fm = ComponentMapper.getFor(FactionComponent.class);
    private final ComponentMapper<WalletComponent> wm = ComponentMapper.getFor(WalletComponent.class);
    private final ComponentMapper<IdentityComponent> identityMapper = ComponentMapper.getFor(IdentityComponent.class);
    private final EconomicLedger ledger;

    /** Создаёт контроллер с собственным диагностическим ledger. */
    public TradeController() {
        this(new EconomicLedger());
    }

    /**
     * Создаёт контроллер, записывающий успешные сделки в общий журнал игровой сессии.
     *
     * @param ledger обязательный журнал экономических движений
     * @throws NullPointerException если журнал не задан
     */
    public TradeController(EconomicLedger ledger) {
        this.ledger = Objects.requireNonNull(ledger, "EconomicLedger не задан");
    }

    /** @return ledger, используемый этим контроллером */
    public EconomicLedger getLedger() {
        return ledger;
    }

    /**
     * Покупает товар у станции и физически переводит деньги покупателя станции.
     *
     * @param station станция-продавец с рынком, складом и кошельком
     * @param buyer сущность-покупатель со складом и кошельком
     * @param itemId идентификатор товара
     * @param amount строго положительное количество
     * @param buyerReputation репутация покупателя или {@code null}
     * @return {@code true}, если товар и деньги полностью переведены; при {@code false} состояние не меняется
     */
    public boolean buyFromStation(
            Entity station,
            Entity buyer,
            int itemId,
            int amount,
            ReputationComponent buyerReputation) {
        if (!isValidWalletTradeRequest(station, buyer, itemId, amount)) {
            return false;
        }

        InventoryComponent stationInventory = im.get(station);
        InventoryComponent buyerInventory = im.get(buyer);
        WalletComponent stationWallet = wm.get(station);
        WalletComponent buyerWallet = wm.get(buyer);
        float unitPrice = getEffectiveSellPrice(station, itemId, buyerReputation);
        long cost = safeTradeValue(unitPrice, amount);

        if (cost <= 0L
                || stationInventory.stock[itemId] < amount
                || getFreeCapacity(buyerInventory) < amount
                || !buyerWallet.canDebit(cost)
                || !stationWallet.canCredit(cost)
                || buyerInventory.stock[itemId] > Integer.MAX_VALUE - amount) {
            return false;
        }

        if (!buyerWallet.transferTo(stationWallet, cost)) {
            return false;
        }
        stationInventory.stock[itemId] -= amount;
        buyerInventory.stock[itemId] += amount;
        mm.get(station).isDirty = true;
        increaseReputation(station, buyerReputation);
        ledger.recordTrade(entityName(buyer), entityName(station), itemId, amount, cost);
        return true;
    }

    /**
     * Покупает товар у станции без репутационной скидки.
     *
     * @param station станция-продавец
     * @param buyer сущность-покупатель
     * @param itemId идентификатор товара
     * @param amount строго положительное количество
     * @return {@code true} при полном успешном transfer
     */
    public boolean buyFromStation(Entity station, Entity buyer, int itemId, int amount) {
        return buyFromStation(station, buyer, itemId, amount, null);
    }

    /**
     * Продаёт товар станции и физически переводит деньги станции продавцу.
     *
     * @param station станция-покупатель с рынком, складом и кошельком
     * @param seller сущность-продавец со складом и кошельком
     * @param itemId идентификатор товара
     * @param amount строго положительное количество
     * @param sellerReputation репутация продавца или {@code null}
     * @return {@code true}, если товар и деньги полностью переведены; при {@code false} состояние не меняется
     */
    public boolean sellToStation(
            Entity station,
            Entity seller,
            int itemId,
            int amount,
            ReputationComponent sellerReputation) {
        if (!isValidWalletTradeRequest(station, seller, itemId, amount)) {
            return false;
        }

        InventoryComponent stationInventory = im.get(station);
        InventoryComponent sellerInventory = im.get(seller);
        WalletComponent stationWallet = wm.get(station);
        WalletComponent sellerWallet = wm.get(seller);
        float unitPrice = getEffectiveBuyPrice(station, itemId, sellerReputation);
        long revenue = safeTradeValue(unitPrice, amount);

        if (revenue <= 0L
                || sellerInventory.stock[itemId] < amount
                || getFreeCapacity(stationInventory) < amount
                || !stationWallet.canDebit(revenue)
                || !sellerWallet.canCredit(revenue)
                || stationInventory.stock[itemId] > Integer.MAX_VALUE - amount) {
            return false;
        }

        if (!stationWallet.transferTo(sellerWallet, revenue)) {
            return false;
        }
        sellerInventory.stock[itemId] -= amount;
        stationInventory.stock[itemId] += amount;
        mm.get(station).isDirty = true;
        increaseReputation(station, sellerReputation);
        ledger.recordTrade(entityName(station), entityName(seller), itemId, amount, revenue);
        return true;
    }

    /**
     * Продаёт товар станции без репутационной надбавки.
     *
     * @param station станция-покупатель
     * @param seller сущность-продавец
     * @param itemId идентификатор товара
     * @param amount строго положительное количество
     * @return {@code true} при полном успешном transfer
     */
    public boolean sellToStation(Entity station, Entity seller, int itemId, int amount) {
        return sellToStation(station, seller, itemId, amount, null);
    }

    /**
     * Упрощённый legacy-профиль игрока для старого сценария покупки.
     */
    public static class PlayerProfile {
        /** Текущий legacy-баланс кредитов. */
        public float credits;
        /** Количество товаров в legacy-грузе игрока. */
        public int[] cargo = new int[20];

        /** Создаёт профиль с нулевым балансом и пустым грузом. */
        public PlayerProfile() {
        }
    }

    /**
     * Legacy-покупка без станционного кошелька. Сохраняется только до завершения миграции Stage 2.
     *
     * @param station станция с компонентами рынка и склада
     * @param itemId идентификатор покупаемого товара
     * @param amount положительное количество единиц товара
     * @param player изменяемый legacy-профиль покупателя
     * @return {@code true}, если старый сценарий покупки выполнен
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
     * Legacy-покупка без учёта репутации.
     *
     * @param station станция-продавец
     * @param buyerInventory склад покупателя
     * @param itemId идентификатор товара
     * @param amount количество
     * @param buyerCredits legacy-счёт
     * @return {@code true} при успехе
     */
    public boolean buyFromStation(
            Entity station,
            InventoryComponent buyerInventory,
            int itemId,
            int amount,
            CreditAccount buyerCredits) {
        return buyFromStation(station, buyerInventory, itemId, amount, buyerCredits, null);
    }

    /**
     * Legacy-покупка с репутацией. Не сохраняет деньги станции и будет удалена после миграции систем.
     *
     * @param station станция-продавец
     * @param buyerInventory склад покупателя
     * @param itemId идентификатор товара
     * @param amount количество
     * @param buyerCredits legacy-счёт
     * @param buyerReputation репутация или {@code null}
     * @return {@code true} при успехе
     */
    public boolean buyFromStation(
            Entity station,
            InventoryComponent buyerInventory,
            int itemId,
            int amount,
            CreditAccount buyerCredits,
            ReputationComponent buyerReputation) {
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
     * Legacy-продажа без учёта репутации.
     *
     * @param station станция-покупатель
     * @param sellerInventory склад продавца
     * @param itemId идентификатор товара
     * @param amount количество
     * @param sellerCredits legacy-счёт
     * @return {@code true} при успехе
     */
    public boolean sellToStation(
            Entity station,
            InventoryComponent sellerInventory,
            int itemId,
            int amount,
            CreditAccount sellerCredits) {
        return sellToStation(station, sellerInventory, itemId, amount, sellerCredits, null);
    }

    /**
     * Legacy-продажа с репутацией. Создаёт деньги участнику и будет удалена после миграции систем.
     *
     * @param station станция-покупатель
     * @param sellerInventory склад продавца
     * @param itemId идентификатор товара
     * @param amount количество
     * @param sellerCredits legacy-счёт
     * @param sellerReputation репутация или {@code null}
     * @return {@code true} при успехе
     */
    public boolean sellToStation(
            Entity station,
            InventoryComponent sellerInventory,
            int itemId,
            int amount,
            CreditAccount sellerCredits,
            ReputationComponent sellerReputation) {
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
     * Рассчитывает эффективную цену продажи станции с учётом положительной репутации.
     *
     * @param station станция
     * @param itemId идентификатор товара
     * @param reputation репутация участника или {@code null}
     * @return цена либо {@link Float#NaN}, если запрос структурно некорректен
     */
    public float getEffectiveSellPrice(Entity station, int itemId, ReputationComponent reputation) {
        if (!isValidPriceRequest(station, itemId)) {
            return Float.NaN;
        }
        MarketComponent market = mm.get(station);
        return market.sellPrices[itemId] * (1f - getReputationPriceBonus(station, reputation));
    }

    /**
     * Рассчитывает эффективную закупочную цену станции с учётом положительной репутации.
     *
     * @param station станция
     * @param itemId идентификатор товара
     * @param reputation репутация участника или {@code null}
     * @return цена либо {@link Float#NaN}, если запрос структурно некорректен
     */
    public float getEffectiveBuyPrice(Entity station, int itemId, ReputationComponent reputation) {
        if (!isValidPriceRequest(station, itemId)) {
            return Float.NaN;
        }
        MarketComponent market = mm.get(station);
        return market.buyPrices[itemId] * (1f + getReputationPriceBonus(station, reputation));
    }

    /**
     * Возвращает доступную вместимость склада.
     *
     * @param inventory склад или {@code null}
     * @return свободная вместимость либо ноль
     */
    public int getFreeCapacity(InventoryComponent inventory) {
        return inventory == null ? 0 : inventory.getFreeCapacity();
    }

    /**
     * Возвращает суммарный запас склада.
     *
     * @param inventory склад или {@code null}
     * @return суммарный запас либо ноль
     */
    public int getTotalStock(InventoryComponent inventory) {
        return inventory == null ? 0 : inventory.getTotalStock();
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

    private boolean isValidWalletTradeRequest(Entity station, Entity participant, int itemId, int amount) {
        return station != null
                && participant != null
                && station != participant
                && amount > 0
                && itemId >= 0
                && itemId < Constants.MAX_ITEMS
                && im.has(station)
                && mm.has(station)
                && wm.has(station)
                && im.has(participant)
                && wm.has(participant)
                && im.get(station) != im.get(participant)
                && wm.get(station) != wm.get(participant)
                && isValidInventory(im.get(station))
                && isValidInventory(im.get(participant))
                && isValidMarket(mm.get(station))
                && mm.get(station).isTradable(itemId);
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

    private long safeTradeValue(float unitPrice, int amount) {
        try {
            return Money.tradeValue(unitPrice, amount);
        } catch (IllegalArgumentException exception) {
            return -1L;
        }
    }

    private String entityName(Entity entity) {
        if (entity != null && identityMapper.has(entity)) {
            String name = identityMapper.get(entity).name;
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return "UNIDENTIFIED";
    }

    /**
     * Минимальный legacy-счёт участника торговли.
     */
    public static class CreditAccount {
        /** Текущий legacy-баланс кредитов. */
        public float credits;

        /**
         * Создаёт legacy-счёт.
         *
         * @param credits конечный неотрицательный баланс
         * @throws IllegalArgumentException если баланс некорректен
         */
        public CreditAccount(float credits) {
            if (!Float.isFinite(credits) || credits < 0f) {
                throw new IllegalArgumentException("Баланс должен быть конечным и неотрицательным");
            }
            this.credits = credits;
        }
    }
}
