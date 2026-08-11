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
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;

/**
 * Рисует интерактивную карту станций и торговых флотов средствами libGDX.
 *
 * <p>Фон, сетка, маршруты и геометрические значки формируются кодом без внешних текстур.
 * Станции показаны кругами с цветом фракции, флоты — треугольниками, направленными к текущей
 * станции назначения. Если цели нет, направление берётся из конечного ненулевого вектора
 * скорости, а затем — вдоль положительной оси {@code X}. Текущая цель торгового ИИ соединяется
 * с флотом линией. Имена станций и выбранного флота выводятся переданным шрифтом.</p>
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
    private static final float SELECTION_RADIUS = 15f;

    private static final Color OUTER_BACKGROUND = new Color(0.025f, 0.035f, 0.06f, 1f);
    private static final Color MAP_BACKGROUND = new Color(0.045f, 0.065f, 0.105f, 1f);
    private static final Color GRID_COLOR = new Color(0.13f, 0.2f, 0.29f, 1f);
    private static final Color BORDER_COLOR = new Color(0.28f, 0.48f, 0.68f, 1f);
    private static final Color ROUTE_COLOR = new Color(0.25f, 0.72f, 0.82f, 1f);
    private static final Color FLEET_COLOR = new Color(0.72f, 0.9f, 1f, 1f);
    private static final Color LABEL_COLOR = new Color(0.88f, 0.93f, 1f, 1f);
    private static final Color SELECTED_COLOR = new Color(1f, 0.84f, 0.22f, 1f);
    private static final Color NEUTRAL_COLOR = new Color(0.57f, 0.65f, 0.74f, 1f);
    private static final Color TRADE_LEAGUE_COLOR = new Color(0.24f, 0.78f, 0.58f, 1f);
    private static final Color MINERS_COLOR = new Color(0.93f, 0.56f, 0.22f, 1f);
    private static final Color UNKNOWN_FACTION_COLOR = new Color(0.72f, 0.55f, 0.82f, 1f);

    private static final ComponentMapper<IdentityComponent> IDENTITIES =
            ComponentMapper.getFor(IdentityComponent.class);
    private static final ComponentMapper<TransformComponent> TRANSFORMS =
            ComponentMapper.getFor(TransformComponent.class);
    private static final ComponentMapper<FactionComponent> FACTIONS =
            ComponentMapper.getFor(FactionComponent.class);
    private static final ComponentMapper<TradeAIComponent> TRADE_AI =
            ComponentMapper.getFor(TradeAIComponent.class);

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

        shapeRenderer.setColor(ROUTE_COLOR);
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
            shapeRenderer.line(firstPoint, secondPoint);
        }
        shapeRenderer.end();
    }

    /** Рисует станции кругами, а флоты направленными треугольниками. */
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
                shapeRenderer.setColor(FLEET_COLOR);
                drawFleetTriangle(entity, transform, firstPoint.x, firstPoint.y);
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

    /**
     * Вычисляет вершины значка флота с учётом его цели или последнего вектора скорости.
     */
    private void drawFleetTriangle(
            Entity fleet,
            TransformComponent transform,
            float screenX,
            float screenY) {
        float directionX = 1f;
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
        float rearX = screenX - normalizedX * FLEET_REAR_OFFSET;
        float rearY = screenY - normalizedY * FLEET_REAR_OFFSET;
        float perpendicularX = -normalizedY * FLEET_HALF_WIDTH;
        float perpendicularY = normalizedX * FLEET_HALF_WIDTH;

        shapeRenderer.triangle(
                screenX + normalizedX * FLEET_LENGTH,
                screenY + normalizedY * FLEET_LENGTH,
                rearX + perpendicularX,
                rearY + perpendicularY,
                rearX - perpendicularX,
                rearY - perpendicularY);
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
