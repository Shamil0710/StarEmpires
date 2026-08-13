package com.spacesim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProcurementPolicyComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.events.EconomyEvent;
import com.spacesim.events.GlobalEventManager;

import java.util.Objects;

/**
 * Рассчитывает цены покупки и продажи на всех активных рынках.
 *
 * <p>Пересчёт выполняется лениво: конкретный рынок обновляется, когда его флаг
 * {@link MarketComponent#isDirty} установлен системой, изменившей запасы или настройки торговли.
 * Кроме того, система сравнивает сохранённую ревизию с
 * {@link GlobalEventManager#getEventRevision() ревизией набора событий}. Изменение ревизии
 * принудительно обновляет все рынки, поскольку активация, отмена или завершение события может
 * изменить пространственный множитель цены даже при неизменном инвентаре.</p>
 *
 * <p>Базовая цена и список существующих runtime item ID берутся из {@link ContentCatalog}, а не
 * из Java-констант. Цена продажи вычисляется по формуле
 * {@code basePrice * (targetStock / max(1, currentStock))^1.2 * eventMultiplier}; цена покупки
 * составляет 90 процентов от неё. Для товаров, не разрешённых к торговле, обе цены равны нулю.</p>
 */
public class MarketSystem extends EntitySystem {
    private ImmutableArray<Entity> entities;
    private final GlobalEventManager eventManager;
    private final ContentCatalog contentCatalog;
    private long lastEventRevision = Long.MIN_VALUE;

    private final ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private final ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    private final ComponentMapper<TransformComponent> tm = ComponentMapper.getFor(TransformComponent.class);
    private final ComponentMapper<ProcurementPolicyComponent> ppm =
            ComponentMapper.getFor(ProcurementPolicyComponent.class);

    /**
     * Создаёт систему на встроенном production content catalog.
     *
     * @param em менеджер активных экономических событий
     * @throws NullPointerException если менеджер не задан
     */
    public MarketSystem(GlobalEventManager em) {
        this(em, ContentCatalogLoader.loadDefault());
    }

    /**
     * Создаёт систему с явно заданным versioned content catalog.
     *
     * @param em менеджер активных экономических событий
     * @param contentCatalog каталог метаданных товаров
     * @throws NullPointerException если зависимость не задана
     */
    public MarketSystem(GlobalEventManager em, ContentCatalog contentCatalog) {
        this.eventManager = Objects.requireNonNull(em, "GlobalEventManager не задан");
        this.contentCatalog = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
    }

    /**
     * Получает живое представление сущностей, составляющих рынки.
     *
     * @param engine движок, к которому добавлена система
     * @throws NullPointerException если {@code engine} равен {@code null}
     */
    @Override
    public void addedToEngine(Engine engine) {
        entities = Objects.requireNonNull(engine, "Engine не задан").getEntitiesFor(
                Family.all(MarketComponent.class, InventoryComponent.class, TransformComponent.class).get());
    }

    /**
     * Пересчитывает только изменившиеся рынки либо все рынки при новой ревизии событий.
     *
     * @param deltaTime время кадра в секундах; системой непосредственно не используется
     */
    @Override
    public void update(float deltaTime) {
        long eventRevision = eventManager.getEventRevision();
        boolean eventsChanged = eventRevision != lastEventRevision;

        for (Entity entity : entities) {
            MarketComponent market = mm.get(entity);
            InventoryComponent inventory = im.get(entity);
            TransformComponent position = tm.get(entity);
            ProcurementPolicyComponent procurement = ppm.get(entity);

            if (!market.isDirty && !eventsChanged) {
                continue;
            }

            for (ContentCatalog.ItemDefinition item : contentCatalog.getItems()) {
                int itemId = item.runtimeId();
                if (!market.isTradable(itemId)) {
                    market.sellPrices[itemId] = 0f;
                    market.buyPrices[itemId] = 0f;
                    continue;
                }

                float ratio = (float) market.targetStock[itemId] / Math.max(1, inventory.stock[itemId]);
                float priceMultiplier = getPriceMultiplier(itemId, position);
                float dynamicSellPrice = item.basePrice()
                        * (float) Math.pow(ratio, 1.2)
                        * priceMultiplier;
                market.sellPrices[itemId] = procurement == null ? dynamicSellPrice : 0f;
                market.buyPrices[itemId] = procurement == null
                        ? dynamicSellPrice * 0.9f
                        : procurement.buyPrice(itemId);
            }
            market.isDirty = false;
        }

        lastEventRevision = eventRevision;
    }

    /**
     * Вычисляет совокупный событийный множитель цены для товара и позиции.
     *
     * @param itemId идентификатор товара
     * @param transform положение рыночной сущности
     * @return произведение множителей всех влияющих событий или {@code 1.0f}, если их нет
     */
    private float getPriceMultiplier(int itemId, TransformComponent transform) {
        float multiplier = 1.0f;
        for (EconomyEvent event : eventManager.getActiveEvents()) {
            if (event.affects(itemId, transform.position)) {
                multiplier *= event.getPriceMultiplier();
            }
        }
        return multiplier;
    }
}
