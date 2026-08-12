package com.spacesim.controllers;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.FactionMarketAccessComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.economy.Money;

import java.util.Objects;

/**
 * Выполняет синхронные атомарные операции покупки и продажи.
 *
 * <p>Обычная сделка физически переносит товар в одну сторону и целочисленные milli-credits в
 * другую; успешная операция фиксируется в {@link EconomicLedger}. До мутации проверяются склады,
 * вместимость, оба кошелька, рынок, faction market access, цена и переполнение, поэтому отказ не
 * меняет состояние.</p>
 */
public class TradeController {
    private final ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    private final ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private final ComponentMapper<FactionComponent> fm = ComponentMapper.getFor(FactionComponent.class);
    private final ComponentMapper<FactionMarketAccessComponent> accessMapper =
            ComponentMapper.getFor(FactionMarketAccessComponent.class);
    private final ComponentMapper<WalletComponent> wm = ComponentMapper.getFor(WalletComponent.class);
    private final ComponentMapper<IdentityComponent> identityMapper =
            ComponentMapper.getFor(IdentityComponent.class);
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

    /** @return журнал, используемый этим контроллером */
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
     * @return {@code true}, если товар и деньги полностью переведены
     */
    public boolean buyFromStation(
            Entity station,
            Entity buyer,
            int itemId,
            int amount,
            ReputationComponent buyerReputation) {
        if (!canTradeWithStation(buyer, station)
                || !isValidWalletTradeRequest(station, buyer, itemId, amount)) {
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
     * @return {@code true}, если товар и деньги полностью переведены
     */
    public boolean sellToStation(
            Entity station,
            Entity seller,
            int itemId,
            int amount,
            ReputationComponent sellerReputation) {
        if (!canTradeWithStation(seller, station)
                || !isValidWalletTradeRequest(station, seller, itemId, amount)) {
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
     * Проверяет strategic faction access участника к station market.
     *
     * <p>Отсутствующий {@link FactionMarketAccessComponent} означает unrestricted legacy market.
     * Сущность без {@link FactionComponent} проверяется как unfactioned participant.</p>
     *
     * @param participant участник сделки
     * @param station market station
     * @return {@code true}, если доступ разрешён
     */
    public boolean canTradeWithStation(Entity participant, Entity station) {
        if (participant == null || station == null) {
            return false;
        }
        FactionMarketAccessComponent access = accessMapper.get(station);
        if (access == null) {
            return true;
        }
        FactionComponent faction = fm.get(participant);
        return access.canTrade(faction == null ? -1 : faction.factionId);
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
}
