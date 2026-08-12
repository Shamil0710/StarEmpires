package com.spacesim.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.spacesim.components.*;
import com.spacesim.constants.Constants;
import com.spacesim.events.EconomyEvent;
import com.spacesim.events.GlobalEventManager;

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
 * <p>Цена продажи вычисляется по формуле
 * {@code basePrice * (targetStock / max(1, currentStock))^1.2 * eventMultiplier}; цена покупки
 * составляет 90 процентов от неё. Множители всех подходящих событий перемножаются. Для товаров,
 * не разрешённых к торговле, обе цены принудительно равны нулю. После успешного прохода флаг
 * {@code isDirty} очищается.</p>
 */
public class MarketSystem extends EntitySystem {
    /** Живое представление всех сущностей, удовлетворяющих семейству рынка. */
    private ImmutableArray<Entity> entities;
    /** Менеджер событий, влияющих на локальные множители цены. */
    private final GlobalEventManager eventManager;
    /** Ревизия событий, уже учтённая последним полным или частичным пересчётом. */
    private long lastEventRevision = Long.MIN_VALUE;

    /** Быстрый доступ к рассчитываемым параметрам рынка. */
    private ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    /** Быстрый доступ к текущим запасам рынка. */
    private ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    /** Быстрый доступ к позиции рынка для применения пространственных событий. */
    private ComponentMapper<TransformComponent> tm = ComponentMapper.getFor(TransformComponent.class);

    /**
     * Создаёт систему расчёта рыночных цен.
     *
     * @param em менеджер активных экономических событий; во время работы системы не должен быть
     *           {@code null}
     */
    public MarketSystem(GlobalEventManager em) { this.eventManager = em; }

    /**
     * Получает живое представление сущностей, составляющих рынки.
     *
     * <p>Ashley автоматически поддерживает возвращённый массив при добавлении и удалении
     * компонентов, поэтому повторно формировать семейство на каждом кадре не требуется.</p>
     *
     * @param engine движок, к которому добавлена система
     * @throws NullPointerException если {@code engine} равен {@code null}
     */
    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(Family.all(MarketComponent.class, InventoryComponent.class, TransformComponent.class).get());
    }

    /**
     * Пересчитывает только изменившиеся рынки либо все рынки при новой ревизии событий.
     *
     * <p>Начальное значение сохранённой ревизии гарантирует полный расчёт при первом обновлении.
     * Параметр времени на формулу не влияет: цены зависят от текущего снимка запасов и событий.</p>
     *
     * @param deltaTime время кадра в секундах; системой непосредственно не используется
     */
    @Override
    public void update(float deltaTime) {
        long eventRevision = eventManager.getEventRevision();
        boolean eventsChanged = eventRevision != lastEventRevision;

        for (Entity entity : entities) {
            MarketComponent m = mm.get(entity);
            InventoryComponent inv = im.get(entity);
            TransformComponent pos = tm.get(entity);

            if (m.isDirty || eventsChanged) {
                for (int i = 0; i < Constants.MAX_ITEMS; i++) {
                    if (!m.isTradable(i)) {
                        m.sellPrices[i] = 0f;
                        m.buyPrices[i] = 0f;
                        continue;
                    }

                    float ratio = (float)m.targetStock[i] / Math.max(1, inv.stock[i]);
                    float base = Constants.BASE_PRICES[i];
                    float priceMultiplier = getPriceMultiplier(i, pos);

                    m.sellPrices[i] = base * (float)Math.pow(ratio, 1.2) * priceMultiplier;
                    m.buyPrices[i] = m.sellPrices[i] * 0.9f;
                }
                m.isDirty = false;
            }
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
