package com.spacesim.ui;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.model.ShipType;

/**
 * Рисует интерактивную карту станций и торговых флотов средствами libGDX.
 *
 * <p>Фон, сетка, маршруты и геометрические значки формируются кодом без внешних текстур.
 * Станции показаны кругами с цветом фракции, а пять типов кораблей различаются одновременно
 * силуэтом и контрастным цветом. Корабельный маркер направлен к текущей станции назначения.
 * Если цели нет, направление берётся из конечного ненулевого вектора скорости, а затем — вдоль
 * положительной оси {@code X}. Маршрут торгового ИИ окрашен в цвет типа корабля. Имена станций
 * и выбранного флота выводятся переданным шрифтом.</p>
 *
 * <p>Экземпляр владеет внутренними {@link ShapeRenderer} и {@link SpriteBatch}; их освобождает
 * {@link #dispose()}. Переданный {@link BitmapFont} остаётся собственностью VisUI/вызывающего
 * кода и намеренно не освобождается. Создавать экземпляр следует только после инициализации
 * графического контекста libGDX.</p>
 */
public final class WorldMapRenderer {
    private static final float GRID_STEP = 100f;
    private static final float STATION_RADIUS = 9f;
    private static final float FLEET_LENGTH = 12f;
    private static final float FLEET_REAR_OFFSET = 7f;
    private static final float FLEET_HALF_WIDTH = 7f;
    private static final float SELECTION_RADIUS = 17f;

    private static final Color OUTER_BACKGROUND = new Color(0.025f, 0.035f, 0.06f, 1f);
    private static final Color MAP_BACKGROUND = new Color(0.045f, 0.065f, 0.105f, 1f);
    private static final Color GRID_COLOR = new Color(0.13f, 0.2f, 0.29f, 1f);
    private static final Color BORDER_COLOR = new Color(0.28f, 0.48f, 0.68f, 1f);
    private static final Color ROUTE_COLOR = new Color(0.25f, 0.72f, 0.82f, 1f);
    private static final Color LABEL_COLOR = new Color(0.88f, 0.93f, 1f, 1f);
    private static final Color SELECTED_COLOR = new Color(1f, 0.84f, 0.22f, 1f);
    private static final Color NEUTRAL_COLOR = new Color(0.57f, 0.65f, 0.74f, 1f);
    private static final Color TRADE_LEAGUE_COLOR = new Color(0.24f, 0.78f, 0.58f, 1f);
    private static final Color MINERS_COLOR = new Color(0.93f, 0.56f, 0.22f, 1f);
    private static final Color UNKNOWN_FACTION_COLOR = new Color(0.72f, 0.55f, 0.82f, 1f);

    private static final MarkerStyle GENERIC_FLEET_STYLE =
            new MarkerStyle(MarkerShape.GENERIC_ARROW, 0.72f, 0.9f, 1f);
    private static final MarkerStyle FINISHED_GOODS_STYLE =
            new MarkerStyle(MarkerShape.CONTAINER_CARRIER, 0.3f, 0.86f, 0.62f);
    private static final MarkerStyle MATERIAL_STYLE =
            new MarkerStyle(MarkerShape.BULK_CARRIER, 0.72f, 0.61f, 1f);
    private static final MarkerStyle GAS_LIQUID_STYLE =
            new MarkerStyle(MarkerShape.TANKER, 0.29f, 0.7f, 1f);
    private static final MarkerStyle MINING_STYLE =
            new MarkerStyle(MarkerShape.MINING_CLAW, 1f, 0.69f, 0.22f);
    private static final MarkerStyle COMBAT_STYLE =
            new MarkerStyle(MarkerShape.COMBAT_DELTA, 1f, 0.34f, 0.42f);

    private static final ComponentMapper<IdentityComponent> IDENTITIES =
            ComponentMapper.getFor(IdentityComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORMS =
            ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<FactionComponent> FACTIONS =
            ComponentMapper.getFor(FactionComponent.class);
    private static final ComponentMapper<TradeAIComponent> TRADE_AI =
            ComponentMapper.getFor(TradeAIComponent.class);
    private static final ComponentMapper<ShipComponent> SHIPS =
            ComponentMapper.getFor(ShipComponent.class);

    private final BitmapFont font;
    private final GlyphLayout labelLayout = new GlyphLayout();
    private final ShapeRenderer shapeRenderer;
    private final SpriteBatch spriteBatch;
    private final Array<Entity> visibleEntities = new Array<>(false, 16);
    private final Vector2 firstPoint = new Vector2();
    private final Vector2 secondPoint = new Vector2();
    private boolean disposed;

    /**
     * Создаёт ресурсы отрисовщика и сохраняет ссылку на шрифт интерфейса.
     *
     * @param font ненулевой шрифт, обычно полученный из активного скина VisUI
     * @throws NullPointerException если {@code font} равен {@code null}
     */
    public WorldMapRenderer(BitmapFont font) {
        if (font == null) {
            throw new NullPointerException("Шрифт карты не должен быть null");
        }
        this.font = font;
        this.shapeRenderer = new ShapeRenderer();
        this.spriteBatch = new SpriteBatch();
    }

    /**
     * Отрисовывает один кадр карты.
     *
     * <p>Метод безопасно завершается до первого графического вызова, если отрисовщик уже
     * освобождён, матрица, layout или коллекция отсутствуют либо матрица содержит неконечные
     * значения. Отдельные некорректные сущности просто пропускаются. Объект считается видимым,
     * только если имеет {@link IdentityComponent}, конечную позицию и находится в границах
     * фиксированного мира.</p>
     *
     * @param projectionMatrix конечная матрица камеры Scene2D
     * @param entities актуальные сущности мира; коллекция может содержать {@code null}
     * @param layout вычисленная область и масштаб карты
     * @param selected выбранная сущность для жёлтой подсветки либо {@code null}
     */
    public void render(
            Matrix4 projectionMatrix,
            Iterable<Entity> entities,
            WorldMapLayout layout,
            Entity selected) {
        if (disposed
                || !isFiniteMatrix(projectionMatrix)
                || entities == null
                || layout == null) {
            return;
        }

        collectVisibleEntities(entities, layout);
        shapeRenderer.setProjectionMatrix(projectionMatrix);
        drawBackground(layout);
        drawGridAndRoutes(layout);
        drawObjects(layout);
        drawBordersAndSelection(layout, selected);
        drawLabels(projectionMatrix, layout, selected);
    }

    /** Собирает ссылки на сущности, которые имеют корректный визуальный образ на карте. */
    private void collectVisibleEntities(Iterable<Entity> entities, WorldMapLayout layout) {
        visibleEntities.clear();
        for (Entity entity : entities) {
            if (isVisible(entity, layout)) {
                visibleEntities.add(entity);
            }
        }
    }

    /** Рисует непрозрачный фон всего виджета и внутренней области мира. */
    private void drawBackground(WorldMapLayout layout) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(OUTER_BACKGROUND);
        shapeRenderer.rect(layout.getX(), layout.getY(), layout.getWidth(), layout.getHeight());
        shapeRenderer.setColor(MAP_BACKGROUND);
        shapeRenderer.rect(layout.getMapX(), layout.getMapY(), layout.getMapWidth(), layout.getMapHeight());
        shapeRenderer.end();
    }

    /** Рисует сетку с шагом сто единиц мира и активные маршруты флотов. */
    private void drawGridAndRoutes(WorldMapLayout layout) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(GRID_COLOR);
        for (float worldX = 0f; worldX <= WorldMapLayout.WORLD_WIDTH; worldX += GRID_STEP) {
            if (layout.worldToScreen(worldX, 0f, firstPoint)
                    && layout.worldToScreen(worldX, WorldMapLayout.WORLD_HEIGHT, secondPoint)) {
                shapeRenderer.line(firstPoint, secondPoint);
            }
        }
        for (float worldY = 0f; worldY <= WorldMapLayout.WORLD_HEIGHT; worldY += GRID_STEP) {
            if (layout.worldToScreen(0f, worldY, firstPoint)
                    && layout.worldToScreen(WorldMapLayout.WORLD_WIDTH, worldY, secondPoint)) {
                shapeRenderer.line(firstPoint, secondPoint);
            }
        }

        for (Entity entity : visibleEntities) {
            IdentityComponent identity = IDENTITIES.get(entity);
            TradeAIComponent ai = TRADE_AI.get(entity);
            TransformComponent transform = TRANSFORMS.get(entity);
            if (identity.kind != IdentityComponent.Kind.FLEET
                    || ai == null
                    || ai.targetStation == null
                    || !projectPosition(transform, layout, firstPoint)
                    || !projectEntityPosition(ai.targetStation, layout, secondPoint)) {
                continue;
            }
            ShipComponent ship = SHIPS.get(entity);
            if (ship == null || ship.type == null) {
                shapeRenderer.setColor(ROUTE_COLOR);
            } else {
                MarkerStyle style = markerStyle(ship.type);
                shapeRenderer.setColor(style.red(), style.green(), style.blue(), 1f);
            }
            shapeRenderer.line(firstPoint, secondPoint);
        }
        shapeRenderer.end();
    }

    /** Рисует станции кругами, а флоты направленными маркерами своего класса. */
    private void drawObjects(WorldMapLayout layout) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (Entity entity : visibleEntities) {
            IdentityComponent identity = IDENTITIES.get(entity);
            TransformComponent transform = TRANSFORMS.get(entity);
            if (!projectPosition(transform, layout, firstPoint)) {
                continue;
            }

            if (identity.kind == IdentityComponent.Kind.STATION) {
                shapeRenderer.setColor(stationColor(entity));
                shapeRenderer.circle(firstPoint.x, firstPoint.y, STATION_RADIUS, 24);
            } else if (identity.kind == IdentityComponent.Kind.FLEET) {
                ShipComponent ship = SHIPS.get(entity);
                MarkerStyle style = markerStyle(ship == null ? null : ship.type);
                shapeRenderer.setColor(style.red(), style.green(), style.blue(), 1f);
                drawFleetMarker(entity, transform, firstPoint.x, firstPoint.y, style.shape());
            }
        }
        shapeRenderer.end();
    }

    /** Рисует рамку карты и кольцо вокруг выбранного видимого объекта. */
    private void drawBordersAndSelection(WorldMapLayout layout, Entity selected) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(BORDER_COLOR);
        shapeRenderer.rect(layout.getMapX(), layout.getMapY(), layout.getMapWidth(), layout.getMapHeight());

        if (selected != null
                && visibleEntities.contains(selected, true)
                && projectEntityPosition(selected, layout, firstPoint)) {
            shapeRenderer.setColor(SELECTED_COLOR);
            shapeRenderer.circle(firstPoint.x, firstPoint.y, SELECTION_RADIUS, 28);
            shapeRenderer.circle(firstPoint.x, firstPoint.y, SELECTION_RADIUS + 2f, 28);
        }
        shapeRenderer.end();
    }

    /**
     * Выводит имена станций и выбранного флота, удерживая подписи внутри карты.
     *
     * <p>Подписи всех кораблей намеренно не показываются одновременно: при стыковке несколько
     * флотов могут занимать одну позицию и превращать текст в нечитаемое пятно. Имя конкретного
     * корабля появляется после выбора и дублируется в правой карточке.</p>
     */
    private void drawLabels(Matrix4 projectionMatrix, WorldMapLayout layout, Entity selected) {
        float oldRed = font.getColor().r;
        float oldGreen = font.getColor().g;
        float oldBlue = font.getColor().b;
        float oldAlpha = font.getColor().a;

        spriteBatch.setProjectionMatrix(projectionMatrix);
        spriteBatch.begin();
        for (Entity entity : visibleEntities) {
            IdentityComponent identity = IDENTITIES.get(entity);
            if (identity.name == null
                    || identity.kind == IdentityComponent.Kind.FLEET && entity != selected
                    || !projectEntityPosition(entity, layout, firstPoint)) {
                continue;
            }

            labelLayout.setText(font, identity.name);
            float labelX = firstPoint.x + 13f;
            if (labelX + labelLayout.width > layout.getMapX() + layout.getMapWidth() - 4f) {
                labelX = firstPoint.x - labelLayout.width - 13f;
            }
            labelX = Math.max(layout.getMapX() + 4f, labelX);
            float labelY = Math.min(
                    firstPoint.y + 17f,
                    layout.getMapY() + layout.getMapHeight() - 4f);

            font.setColor(entity == selected ? SELECTED_COLOR : LABEL_COLOR);
            font.draw(spriteBatch, identity.name, labelX, labelY);
        }
        spriteBatch.end();
        font.setColor(oldRed, oldGreen, oldBlue, oldAlpha);
    }

    /** Рисует направленный значок флота с учётом цели или последнего вектора скорости. */
    private void drawFleetMarker(
            Entity fleet,
            TransformComponent transform,
            float screenX,
            float screenY,
            MarkerShape shape) {
        float directionX = 0f;
        float directionY = 0f;
        TradeAIComponent ai = TRADE_AI.get(fleet);

        if (ai != null && ai.targetStation != null) {
            TransformComponent targetTransform = TRANSFORMS.get(ai.targetStation);
            if (hasFinitePosition(targetTransform)) {
                directionX = targetTransform.position.x - transform.position.x;
                directionY = targetTransform.position.y - transform.position.y;
            }
        }

        double length = Math.hypot(directionX, directionY);
        if (!Double.isFinite(length) || length <= 0.000001d) {
            if (transform.velocity != null
                    && Float.isFinite(transform.velocity.x)
                    && Float.isFinite(transform.velocity.y)) {
                directionX = transform.velocity.x;
                directionY = transform.velocity.y;
                length = Math.hypot(directionX, directionY);
            }
        }
        if (!Double.isFinite(length) || length <= 0.000001d) {
            directionX = 1f;
            directionY = 0f;
            length = 1d;
        }

        float normalizedX = (float) (directionX / length);
        float normalizedY = (float) (directionY / length);
        float perpendicularX = -normalizedY;
        float perpendicularY = normalizedX;

        switch (shape) {
            case GENERIC_ARROW -> drawLocalTriangle(
                    screenX, screenY, normalizedX, normalizedY, perpendicularX, perpendicularY,
                    FLEET_LENGTH, 0f,
                    -FLEET_REAR_OFFSET, FLEET_HALF_WIDTH,
                    -FLEET_REAR_OFFSET, -FLEET_HALF_WIDTH);
            case CONTAINER_CARRIER -> {
                drawLocalQuad(
                        screenX, screenY, normalizedX, normalizedY, perpendicularX, perpendicularY,
                        4f, 6f, -9f, 6f, -9f, -6f, 4f, -6f);
                drawLocalTriangle(
                        screenX, screenY, normalizedX, normalizedY, perpendicularX, perpendicularY,
                        13f, 0f, 4f, 6f, 4f, -6f);
            }
            case BULK_CARRIER -> {
                drawLocalQuad(
                        screenX, screenY, normalizedX, normalizedY, perpendicularX, perpendicularY,
                        3f, 3f, -8f, 3f, -8f, 8f, 3f, 8f);
                drawLocalQuad(
                        screenX, screenY, normalizedX, normalizedY, perpendicularX, perpendicularY,
                        3f, -3f, -8f, -3f, -8f, -8f, 3f, -8f);
                drawLocalTriangle(
                        screenX, screenY, normalizedX, normalizedY, perpendicularX, perpendicularY,
                        13f, 0f, 0f, 5f, -9f, 0f);
                drawLocalTriangle(
                        screenX, screenY, normalizedX, normalizedY, perpendicularX, perpendicularY,
                        13f, 0f, -9f, 0f, 0f, -5f);
            }
            case TANKER -> {
                drawLocalCircle(
                        screenX, screenY, normalizedX, normalizedY, perpendicularX, perpendicularY,
                        -2f, 0f, 7f);
                drawLocalTriangle(
                        screenX, screenY, normalizedX, normalizedY, perpendicularX, perpendicularY,
                        13f, 0f, 2f, 5f, 2f, -5f);
            }
            case MINING_CLAW -> {
                drawLocalTriangle(
                        screenX, screenY, normalizedX, normalizedY, perpendicularX, perpendicularY,
                        6f, 0f, 0f, 6f, -8f, 0f);
                drawLocalTriangle(
                        screenX, screenY, normalizedX, normalizedY, perpendicularX, perpendicularY,
                        6f, 0f, -8f, 0f, 0f, -6f);
                drawLocalTriangle(
                        screenX, screenY, normalizedX, normalizedY, perpendicularX, perpendicularY,
                        14f, 6f, 3f, 6f, 5f, 2f);
                drawLocalTriangle(
                        screenX, screenY, normalizedX, normalizedY, perpendicularX, perpendicularY,
                        14f, -6f, 5f, -2f, 3f, -6f);
            }
            case COMBAT_DELTA -> {
                drawLocalTriangle(
                        screenX, screenY, normalizedX, normalizedY, perpendicularX, perpendicularY,
                        14f, 0f, -8f, 4f, -8f, -4f);
                drawLocalTriangle(
                        screenX, screenY, normalizedX, normalizedY, perpendicularX, perpendicularY,
                        4f, 2f, -8f, 10f, -5f, 1f);
                drawLocalTriangle(
                        screenX, screenY, normalizedX, normalizedY, perpendicularX, perpendicularY,
                        4f, -2f, -5f, -1f, -8f, -10f);
            }
        }
    }

    /** Рисует треугольник по локальным продольным и поперечным координатам корабля. */
    private void drawLocalTriangle(
            float centerX, float centerY,
            float directionX, float directionY,
            float perpendicularX, float perpendicularY,
            float firstForward, float firstSide,
            float secondForward, float secondSide,
            float thirdForward, float thirdSide) {
        shapeRenderer.triangle(
                localX(centerX, directionX, perpendicularX, firstForward, firstSide),
                localY(centerY, directionY, perpendicularY, firstForward, firstSide),
                localX(centerX, directionX, perpendicularX, secondForward, secondSide),
                localY(centerY, directionY, perpendicularY, secondForward, secondSide),
                localX(centerX, directionX, perpendicularX, thirdForward, thirdSide),
                localY(centerY, directionY, perpendicularY, thirdForward, thirdSide));
    }

    /** Заполняет локальный четырёхугольник двумя треугольниками без временных объектов. */
    private void drawLocalQuad(
            float centerX, float centerY,
            float directionX, float directionY,
            float perpendicularX, float perpendicularY,
            float firstForward, float firstSide,
            float secondForward, float secondSide,
            float thirdForward, float thirdSide,
            float fourthForward, float fourthSide) {
        drawLocalTriangle(
                centerX, centerY, directionX, directionY, perpendicularX, perpendicularY,
                firstForward, firstSide,
                secondForward, secondSide,
                thirdForward, thirdSide);
        drawLocalTriangle(
                centerX, centerY, directionX, directionY, perpendicularX, perpendicularY,
                firstForward, firstSide,
                thirdForward, thirdSide,
                fourthForward, fourthSide);
    }

    /** Рисует круг с центром в локальной системе координат корабля. */
    private void drawLocalCircle(
            float centerX, float centerY,
            float directionX, float directionY,
            float perpendicularX, float perpendicularY,
            float forward, float side, float radius) {
        shapeRenderer.circle(
                localX(centerX, directionX, perpendicularX, forward, side),
                localY(centerY, directionY, perpendicularY, forward, side),
                radius,
                20);
    }

    /** Переводит локальное смещение корабля в экранную координату {@code X}. */
    private float localX(float center, float direction, float perpendicular,
                         float forward, float side) {
        return center + direction * forward + perpendicular * side;
    }

    /** Переводит локальное смещение корабля в экранную координату {@code Y}. */
    private float localY(float center, float direction, float perpendicular,
                         float forward, float side) {
        return center + direction * forward + perpendicular * side;
    }

    /** Возвращает цвет станции по её фракции, не изменяя компонент. */
    private Color stationColor(Entity station) {
        FactionComponent faction = FACTIONS.get(station);
        if (faction == null) {
            return UNKNOWN_FACTION_COLOR;
        }
        return switch (faction.factionId) {
            case Constants.FACTION_NEUTRAL -> NEUTRAL_COLOR;
            case Constants.FACTION_TRADE_LEAGUE -> TRADE_LEAGUE_COLOR;
            case Constants.FACTION_MINERS -> MINERS_COLOR;
            default -> UNKNOWN_FACTION_COLOR;
        };
    }

    /** Проверяет компоненты отображения и границы мира. */
    private boolean isVisible(Entity entity, WorldMapLayout layout) {
        if (entity == null) {
            return false;
        }
        IdentityComponent identity = IDENTITIES.get(entity);
        TransformComponent transform = TRANSFORMS.get(entity);
        return identity != null
                && identity.kind != null
                && hasFinitePosition(transform)
                && layout.containsWorldPoint(transform.position.x, transform.position.y);
    }

    /** Проецирует позицию сущности после полной проверки компонента и границ мира. */
    private boolean projectEntityPosition(Entity entity, WorldMapLayout layout, Vector2 result) {
        if (entity == null) {
            return false;
        }
        return projectPosition(TRANSFORMS.get(entity), layout, result);
    }

    /** Проецирует корректную позицию внутри фиксированного мира. */
    private boolean projectPosition(TransformComponent transform, WorldMapLayout layout, Vector2 result) {
        return hasFinitePosition(transform)
                && layout.containsWorldPoint(transform.position.x, transform.position.y)
                && layout.worldToScreen(transform.position.x, transform.position.y, result);
    }

    /** Проверяет наличие конечного вектора позиции. */
    private boolean hasFinitePosition(TransformComponent transform) {
        return transform != null
                && transform.position != null
                && Float.isFinite(transform.position.x)
                && Float.isFinite(transform.position.y);
    }

    /** Проверяет все элементы матрицы до передачи данных графическим объектам. */
    static boolean isFiniteMatrix(Matrix4 matrix) {
        if (matrix == null || matrix.val == null || matrix.val.length < 16) {
            return false;
        }
        for (float value : matrix.val) {
            if (!Float.isFinite(value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Возвращает неизменяемый визуальный стиль функционального типа корабля.
     *
     * <p>Метод не обращается к OpenGL и возвращает заранее созданные значения, поэтому его можно
     * безопасно использовать в модульных тестах. Отсутствующий тип получает прежний универсальный
     * треугольный маркер для совместимости с legacy-сущностями.</p>
     *
     * @param shipType функциональный тип корабля либо {@code null}
     * @return ненулевой стиль маркера с конечными компонентами цвета
     */
    static MarkerStyle markerStyle(ShipType shipType) {
        if (shipType == null) {
            return GENERIC_FLEET_STYLE;
        }
        return switch (shipType) {
            case FINISHED_GOODS_CARRIER -> FINISHED_GOODS_STYLE;
            case MATERIAL_CARRIER -> MATERIAL_STYLE;
            case GAS_LIQUID_CARRIER -> GAS_LIQUID_STYLE;
            case MINING_SHIP -> MINING_STYLE;
            case COMBAT_SHIP -> COMBAT_STYLE;
        };
    }

    /** Геометрический силуэт корабля, построенный примитивами {@link ShapeRenderer}. */
    enum MarkerShape {
        /** Универсальная стрелка старой сущности без типа. */
        GENERIC_ARROW,
        /** Коробчатый контейнеровоз готовой продукции. */
        CONTAINER_CARRIER,
        /** Широкий балкер с двумя рядами материальных контейнеров. */
        BULK_CARRIER,
        /** Округлый герметичный танкер с направленным носом. */
        TANKER,
        /** Добывающий корпус с двумя передними захватами. */
        MINING_CLAW,
        /** Быстрый боевой корпус с дельтовидными крыльями. */
        COMBAT_DELTA
    }

    /**
     * Чистое описание формы и RGB-цвета маркера.
     *
     * @param shape уникальная геометрическая форма
     * @param red красная компонента цвета в диапазоне {@code [0, 1]}
     * @param green зелёная компонента цвета в диапазоне {@code [0, 1]}
     * @param blue синяя компонента цвета в диапазоне {@code [0, 1]}
     */
    record MarkerStyle(MarkerShape shape, float red, float green, float blue) {
    }

    /**
     * Освобождает принадлежащие экземпляру GPU-ресурсы.
     *
     * <p>Повторный вызов ничего не делает. Переданный в конструктор шрифт не освобождается.</p>
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        visibleEntities.clear();
        shapeRenderer.dispose();
        spriteBatch.dispose();
    }
}
