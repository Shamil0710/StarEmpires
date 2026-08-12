/**
 * Глобальные идентификаторы и числовые параметры симуляции.
 *
 * <p>Товарные и фракционные идентификаторы служат индексами в плотных массивах
 * ECS-компонентов. Код, создающий такие массивы или обходящий их, должен
 * использовать границы из {@link com.spacesim.constants.Constants}, а не
 * числовые литералы. Типизированные метаданные товара доступны через
 * {@link com.spacesim.model.ItemType} и безопасный мост
 * {@link com.spacesim.constants.Constants#getItemType(int)}. Границы общего пространства
 * симуляции задают {@link com.spacesim.constants.Constants#WORLD_WIDTH} и
 * {@link com.spacesim.constants.Constants#WORLD_HEIGHT}; модель, маршрутизация и карта должны
 * использовать именно их.</p>
 */
package com.spacesim.constants;
