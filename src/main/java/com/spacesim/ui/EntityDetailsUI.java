package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.model.Recipe;

import java.util.Locale;
import java.util.Objects;

/**
 * Прокручиваемая правая карточка выбранной станции или торгового корабля.
 *
 * <p>Карточка показывает понятное имя, тип и координаты сущности, а затем
 * добавляет разделы только для фактически установленных ECS-компонентов. Такой
 * подход позволяет безопасно выбирать ещё не полностью сконструированную
 * сущность. Текст можно актуализировать методом {@link #refresh()} после
 * очередного шага симуляции, не меняя сам выбор.</p>
 *
 * <p>Внешняя таблица занимает сцену и закрепляет непрозрачную панель VisUI у
 * правого края. {@link #RECOMMENDED_WIDTH} можно использовать при расчёте
 * доступной области интерактивной карты, чтобы объекты не оказывались под
 * карточкой. Класс не владеет переданным {@link Skin} и не освобождает его.</p>
 */
public class EntityDetailsUI extends Table {
    /** Рекомендуемая ширина карточки и резервируемой под неё области, в пикселях интерфейса. */
    public static final float RECOMMENDED_WIDTH = 380f;

    private static final float PANEL_PADDING = 14f;
    private static final float CONTENT_WIDTH = RECOMMENDED_WIDTH - PANEL_PADDING * 2f - 16f;
    private static final String NO_VALUE = "—";
    private static final String[] LOCALIZED_ITEM_NAMES = {
            "Руда", "Энергия", "Продовольствие", "Сталь", "Вооружение"
    };

    private final Label titleLabel;
    private final Label contentLabel;
    private final ScrollPane scrollPane;
    private Entity selectedEntity;

    /**
     * Создаёт пустую карточку и закрепляет её у правого края сцены.
     *
     * <p>Переданный скин должен быть загруженным скином VisUI: из него берутся
     * подписи, полоса прокрутки и непрозрачный фон {@code window}. До первого
     * выбора карточка содержит краткую инструкцию пользователю.</p>
     *
     * @param skin загруженный скин VisUI; не {@code null}
     * @throws NullPointerException если {@code skin == null}
     */
    public EntityDetailsUI(Skin skin) {
        Skin checkedSkin = Objects.requireNonNull(skin, "Skin must not be null");

        titleLabel = new Label("", checkedSkin);
        titleLabel.setWrap(true);
        titleLabel.setAlignment(Align.left);

        contentLabel = new Label("", checkedSkin);
        contentLabel.setWrap(true);
        contentLabel.setAlignment(Align.topLeft);

        Table textTable = new Table();
        textTable.top().left();
        textTable.add(titleLabel).width(CONTENT_WIDTH).left().growX().row();
        textTable.add(contentLabel).width(CONTENT_WIDTH).padTop(10f).left().top().growX();

        scrollPane = new ScrollPane(textTable, checkedSkin);
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setOverscroll(false, false);

        Table panel = new Table();
        panel.top().left();
        panel.setBackground(checkedSkin.getDrawable("window"));
        panel.pad(PANEL_PADDING);
        panel.add(scrollPane).grow();

        setFillParent(true);
        setTouchable(Touchable.childrenOnly);
        top().right();
        pad(10f);
        add(panel).width(RECOMMENDED_WIDTH).growY();

        refresh();
    }

    /**
     * Выбирает сущность, немедленно перестраивает карточку и возвращает
     * прокрутку к её началу.
     *
     * @param entity выбранная станция или корабль; {@code null} снимает выбор
     */
    public void select(Entity entity) {
        selectedEntity = entity;
        refresh();
        scrollPane.setScrollY(0f);
    }

    /**
     * Обновляет текст из текущего состояния ранее выбранной сущности.
     *
     * <p>Положение прокрутки сохраняется, поэтому метод можно вызывать
     * периодически во время движения корабля и изменения рынка.</p>
     */
    public void refresh() {
        DetailsText details = describe(selectedEntity);
        titleLabel.setText(details.title());
        contentLabel.setText(details.body());
    }

    /**
     * Формирует независимую от OpenGL текстовую модель карточки.
     *
     * @param entity описываемая ECS-сущность либо {@code null}
     * @return ненулевые заголовок и содержимое карточки
     */
    static DetailsText describe(Entity entity) {
        if (entity == null) {
            return new DetailsText(
                    "Объект не выбран",
                    "Нажмите на станцию или корабль на карте, чтобы увидеть его характеристики."
            );
        }

        IdentityComponent identity = entity.getComponent(IdentityComponent.class);
        InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
        MarketComponent market = entity.getComponent(MarketComponent.class);
        ProductionComponent production = entity.getComponent(ProductionComponent.class);
        TradeAIComponent tradeAI = entity.getComponent(TradeAIComponent.class);
        TransformComponent transform = entity.getComponent(TransformComponent.class);
        FactionComponent faction = entity.getComponent(FactionComponent.class);
        ReputationComponent reputation = entity.getComponent(ReputationComponent.class);

        String type = resolveType(identity, market, tradeAI);
        String title = identity == null ? "Безымянный объект" : identity.name;
        StringBuilder body = new StringBuilder();
        body.append("Тип: ").append(type).append('\n');
        appendPosition(body, transform);
        body.append("Фракция: ")
                .append(faction == null ? "не указана" : faction.getFactionName())
                .append('\n');

        appendInventory(body, inventory);
        if (market != null) {
            appendMarket(body, market, inventory);
        }
        if (production != null) {
            appendProduction(body, production);
        }
        if (tradeAI != null) {
            appendShip(body, tradeAI, inventory);
        }
        if (reputation != null) {
            appendReputation(body, reputation);
        }

        return new DetailsText(title, body.toString().stripTrailing());
    }

    /** Добавляет текущие координаты либо явное сообщение об их отсутствии. */
    private static void appendPosition(StringBuilder text, TransformComponent transform) {
        text.append("Позиция: ");
        if (transform == null || transform.position == null) {
            text.append("не задана\n");
            return;
        }
        text.append("x=").append(formatNumber(transform.position.x))
                .append(", y=").append(formatNumber(transform.position.y))
                .append('\n');
    }

    /** Добавляет все ненулевые товарные остатки и общую вместимость склада. */
    private static void appendInventory(StringBuilder text, InventoryComponent inventory) {
        text.append("\nИнвентарь\n");
        if (inventory == null) {
            text.append("  отсутствует\n");
            return;
        }

        boolean hasItems = false;
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            int amount = inventory.stock[itemId];
            if (amount == 0) {
                continue;
            }
            hasItems = true;
            text.append("  ").append(itemName(itemId)).append(": ").append(amount).append(" ед.\n");
        }
        if (!hasItems) {
            text.append("  пусто\n");
        }
        text.append("  Заполнено: ").append(inventory.getTotalStock())
                .append(" / ").append(inventory.capacity).append(" ед.\n");
    }

    /** Добавляет параметры всех товаров, для которых станция разрешает торговлю. */
    private static void appendMarket(StringBuilder text, MarketComponent market,
                                     InventoryComponent inventory) {
        text.append("\nРынок\n");
        boolean hasTradableItems = false;
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            if (!market.isTradable(itemId)) {
                continue;
            }
            hasTradableItems = true;
            text.append("  ").append(itemName(itemId)).append('\n')
                    .append("    Запас: ")
                    .append(inventory == null ? NO_VALUE : inventory.stock[itemId]).append(" ед.\n")
                    .append("    Цель: ").append(market.targetStock[itemId]).append(" ед.\n")
                    .append("    Покупка: ").append(formatMoney(market.buyPrices[itemId])).append('\n')
                    .append("    Продажа: ").append(formatMoney(market.sellPrices[itemId])).append('\n')
                    .append("    Потребление: ").append(formatNumber(market.baseConsumption[itemId]))
                    .append(" ед./с\n");
        }
        if (!hasTradableItems) {
            text.append("  Торгуемые товары отсутствуют\n");
        }
    }

    /** Добавляет активный производственный рецепт и прогресс текущего цикла. */
    private static void appendProduction(StringBuilder text, ProductionComponent production) {
        text.append("\nПроизводство\n");
        Recipe recipe = production.getActiveRecipe();
        if (recipe == null) {
            text.append("  Активный рецепт: не выбран\n");
            return;
        }
        text.append("  Активный рецепт: ").append(recipe.name).append('\n')
                .append("  Прогресс: ").append(formatNumber(production.progressSeconds))
                .append(" / ").append(formatNumber(recipe.durationSeconds)).append(" с\n");
    }

    /** Добавляет оперативные характеристики торгового корабля и его маршрута. */
    private static void appendShip(StringBuilder text, TradeAIComponent tradeAI,
                                   InventoryComponent inventory) {
        text.append("\nКорабль\n")
                .append("  Состояние: ").append(localizeState(tradeAI.state)).append('\n')
                .append("  Скорость: ").append(formatNumber(tradeAI.movementSpeed)).append(" ед./с\n")
                .append("  Кредиты: ").append(formatMoney(tradeAI.credits)).append('\n')
                .append("  Груз: ").append(inventory == null ? NO_VALUE : inventory.getTotalStock())
                .append(" / ").append(tradeAI.cargoSpace).append(" ед.\n")
                .append("  Цель: ").append(targetName(tradeAI.targetStation)).append('\n')
                .append("  Товар: ").append(targetItemName(tradeAI.targetItem)).append('\n')
                .append("  Количество: ").append(tradeAI.targetAmount).append(" ед.\n")
                .append("  Ожидаемая прибыль: ").append(formatMoney(tradeAI.expectedProfit)).append('\n')
                .append("  Новый поиск через: ").append(formatNumber(tradeAI.routeSearchCooldown)).append(" с\n");
    }

    /** Добавляет значения отношения корабля ко всем известным фракциям. */
    private static void appendReputation(StringBuilder text, ReputationComponent reputation) {
        text.append("\nРепутация\n");
        for (int factionId = 0; factionId < Constants.MAX_FACTIONS; factionId++) {
            text.append("  ").append(Constants.FACTION_NAMES[factionId]).append(": ")
                    .append(formatNumber(reputation.getReputation(factionId))).append('\n');
        }
    }

    /** Определяет отображаемый тип, предпочитая явный компонент идентичности. */
    private static String resolveType(IdentityComponent identity, MarketComponent market,
                                      TradeAIComponent tradeAI) {
        if (identity != null) {
            return identity.kind == IdentityComponent.Kind.STATION ? "Станция" : "Торговый корабль";
        }
        if (tradeAI != null) {
            return "Торговый корабль";
        }
        return market == null ? "Космический объект" : "Станция";
    }

    /** Возвращает имя станции назначения без рекурсивного построения её карточки. */
    private static String targetName(Entity target) {
        if (target == null) {
            return "не выбрана";
        }
        IdentityComponent identity = target.getComponent(IdentityComponent.class);
        return identity == null ? "безымянная станция" : identity.name;
    }

    /** Локализует состояние конечного автомата торгового корабля. */
    private static String localizeState(TradeAIComponent.State state) {
        if (state == null) {
            return "неизвестно";
        }
        return switch (state) {
            case IDLE -> "ожидает маршрут";
            case TRAVEL_TO_BUY -> "летит к станции покупки";
            case BUYING -> "выполняет покупку";
            case TRAVEL_TO_SELL -> "летит к станции продажи";
            case SELLING -> "выполняет продажу";
        };
    }

    /** Возвращает русское имя товара либо диагностическое имя ошибочного идентификатора. */
    private static String targetItemName(int itemId) {
        if (itemId == -1) {
            return "не выбран";
        }
        if (itemId < 0 || itemId >= Constants.MAX_ITEMS) {
            return "неизвестный товар (id=" + itemId + ")";
        }
        return itemName(itemId);
    }

    /** Возвращает русское имя товара по корректному идентификатору. */
    private static String itemName(int itemId) {
        return itemId < LOCALIZED_ITEM_NAMES.length
                ? LOCALIZED_ITEM_NAMES[itemId]
                : Constants.ITEM_NAMES[itemId];
    }

    /** Форматирует конечное число с одним десятичным знаком, а ошибочное заменяет прочерком. */
    private static String formatNumber(float value) {
        if (!Float.isFinite(value)) {
            return NO_VALUE;
        }
        float normalized = value == 0f ? 0f : value;
        return String.format(Locale.ROOT, "%.1f", normalized);
    }

    /** Форматирует денежную сумму в кредитах. */
    private static String formatMoney(float value) {
        return formatNumber(value) + (Float.isFinite(value) ? " кр." : "");
    }

    /**
     * Неизменяемая текстовая модель заголовка и прокручиваемого содержимого.
     *
     * @param title короткое отображаемое имя выбранного объекта
     * @param body подробные характеристики либо инструкция при отсутствии выбора
     */
    record DetailsText(String title, String body) {
        DetailsText {
            Objects.requireNonNull(title, "Title must not be null");
            Objects.requireNonNull(body, "Body must not be null");
        }
    }
}
