/**
 * Интерактивная карта, Scene2D-панели и вспомогательная отрисовка экономического интерфейса.
 *
 * <p>{@link com.spacesim.ui.WorldMapLayout} проецирует фиксированный игровой мир на экран,
 * {@link com.spacesim.ui.EntityPicker} выполняет независимый от OpenGL hit-test, а
 * {@link com.spacesim.ui.WorldMapRenderer} рисует станции, корабли и их маршруты. Панели пакета
 * только представляют состояние модели: изменение запасов, цен и торговых маршрутов выполняют
 * Ashley-системы.</p>
 */
package com.spacesim.ui;
