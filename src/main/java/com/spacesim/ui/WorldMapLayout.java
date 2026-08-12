package com.spacesim.ui;

import com.badlogic.gdx.math.Vector2;
import com.spacesim.constants.Constants;

/**
 * Неизменяемое состояние вида и преобразование координат интерактивной карты.
 *
 * <p>Полный моделируемый мир имеет размер {@value #WORLD_WIDTH} на
 * {@value #WORLD_HEIGHT} условных единиц. При масштабе {@value #MIN_ZOOM} он целиком
 * вписывается в экранный прямоугольник с сохранением пропорций. Увеличение показывает
 * меньшую часть мира в той же экранной области, а центр обзора автоматически ограничивается
 * так, чтобы за краями карты не появлялось пустое пространство.</p>
 *
 * <p>Класс не обращается к OpenGL и объединяет правила отрисовки, прокрутки и hit-test.
 * Все операции изменения вида возвращают новый экземпляр, поэтому один кадр всегда использует
 * согласованное преобразование. Координата {@code Y} направлена вверх, как в Scene2D после
 * преобразования события через viewport.</p>
 */
public final class WorldMapLayout {
    /** Ширина расширенного моделируемого мира в условных единицах. */
    public static final float WORLD_WIDTH = Constants.WORLD_WIDTH;

    /** Высота расширенного моделируемого мира в условных единицах. */
    public static final float WORLD_HEIGHT = Constants.WORLD_HEIGHT;

    /** Масштаб, при котором на карте виден весь мир. */
    public static final float MIN_ZOOM = 1f;

    /** Максимальное шестикратное увеличение карты. */
    public static final float MAX_ZOOM = 6f;

    /** Множитель одного шага колеса мыши. */
    public static final float ZOOM_STEP = 1.25f;

    private final float x;
    private final float y;
    private final float width;
    private final float height;
    private final float padding;
    private final float mapX;
    private final float mapY;
    private final float mapWidth;
    private final float mapHeight;
    private final float fitScale;
    private final float scale;
    private final float zoom;
    private final float centerWorldX;
    private final float centerWorldY;
    private final float visibleWorldMinX;
    private final float visibleWorldMinY;
    private final float visibleWorldMaxX;
    private final float visibleWorldMaxY;

    /**
     * Создаёт обзор всего мира, вписанный в переданный экранный прямоугольник.
     *
     * @param x левая граница всего виджета в координатах Scene2D
     * @param y нижняя граница всего виджета в координатах Scene2D
     * @param width положительная ширина виджета
     * @param height положительная высота виджета
     * @param padding неотрицательный минимальный отступ от каждой стороны виджета
     * @throws IllegalArgumentException если геометрия некорректна
     */
    public WorldMapLayout(float x, float y, float width, float height, float padding) {
        this(
                x,
                y,
                width,
                height,
                padding,
                WORLD_WIDTH / 2f,
                WORLD_HEIGHT / 2f,
                MIN_ZOOM);
    }

    /**
     * Создаёт вид карты с заданным центром и увеличением.
     *
     * <p>Конечное положительное увеличение ограничивается диапазоном
     * {@code [MIN_ZOOM, MAX_ZOOM]}. Центр также ограничивается доступными границами с учётом
     * видимого размера мира. Такое поведение позволяет безопасно передавать накопленное
     * состояние после изменения размера окна.</p>
     *
     * @param x левая граница всего виджета в координатах Scene2D
     * @param y нижняя граница всего виджета в координатах Scene2D
     * @param width положительная ширина виджета
     * @param height положительная высота виджета
     * @param padding неотрицательный минимальный отступ от каждой стороны виджета
     * @param centerWorldX желаемая мировая координата центра обзора
     * @param centerWorldY желаемая мировая координата центра обзора
     * @param zoom желаемое положительное увеличение относительно полного обзора
     * @throws IllegalArgumentException если геометрия, центр или увеличение некорректны
     */
    public WorldMapLayout(
            float x,
            float y,
            float width,
            float height,
            float padding,
            float centerWorldX,
            float centerWorldY,
            float zoom) {
        validateGeometry(x, y, width, height, padding);
        if (!Float.isFinite(centerWorldX) || !Float.isFinite(centerWorldY)) {
            throw new IllegalArgumentException("Центр карты должен состоять из конечных координат");
        }
        if (!Float.isFinite(zoom) || zoom <= 0f) {
            throw new IllegalArgumentException("Увеличение карты должно быть конечным и положительным");
        }

        double availableWidth = (double) width - 2d * padding;
        double availableHeight = (double) height - 2d * padding;
        double calculatedFitScale = Math.min(
                availableWidth / WORLD_WIDTH,
                availableHeight / WORLD_HEIGHT);
        double calculatedMapWidth = WORLD_WIDTH * calculatedFitScale;
        double calculatedMapHeight = WORLD_HEIGHT * calculatedFitScale;
        double calculatedMapX = (double) x
                + padding
                + (availableWidth - calculatedMapWidth) / 2d;
        double calculatedMapY = (double) y
                + padding
                + (availableHeight - calculatedMapHeight) / 2d;

        float clampedZoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM);
        double calculatedScale = calculatedFitScale * clampedZoom;
        if (!isPositiveRepresentable(calculatedFitScale)
                || !isPositiveRepresentable(calculatedScale)
                || !isPositiveRepresentable(calculatedMapWidth)
                || !isPositiveRepresentable(calculatedMapHeight)
                || !isRepresentable(calculatedMapX)
                || !isRepresentable(calculatedMapY)) {
            throw new IllegalArgumentException(
                    "Вычисленная область или масштаб карты не представимы конечными float-значениями");
        }

        float halfVisibleWorldWidth = WORLD_WIDTH / (2f * clampedZoom);
        float halfVisibleWorldHeight = WORLD_HEIGHT / (2f * clampedZoom);
        float clampedCenterX = clamp(
                centerWorldX,
                halfVisibleWorldWidth,
                WORLD_WIDTH - halfVisibleWorldWidth);
        float clampedCenterY = clamp(
                centerWorldY,
                halfVisibleWorldHeight,
                WORLD_HEIGHT - halfVisibleWorldHeight);

        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.padding = padding;
        this.mapX = (float) calculatedMapX;
        this.mapY = (float) calculatedMapY;
        this.mapWidth = (float) calculatedMapWidth;
        this.mapHeight = (float) calculatedMapHeight;
        this.fitScale = (float) calculatedFitScale;
        this.scale = (float) calculatedScale;
        this.zoom = clampedZoom;
        this.centerWorldX = clampedCenterX;
        this.centerWorldY = clampedCenterY;
        this.visibleWorldMinX = clampedCenterX - halfVisibleWorldWidth;
        this.visibleWorldMinY = clampedCenterY - halfVisibleWorldHeight;
        this.visibleWorldMaxX = clampedCenterX + halfVisibleWorldWidth;
        this.visibleWorldMaxY = clampedCenterY + halfVisibleWorldHeight;
    }

    /**
     * Проецирует мировую точку в координаты Scene2D.
     *
     * <p>Метод разрешает проецировать точки вне видимой области и даже вне мира, если результат
     * остаётся конечным. Для решения об отрисовке предназначен
     * {@link #containsVisibleWorldPoint(float, float)}.</p>
     *
     * @param worldX мировая координата по горизонтали
     * @param worldY мировая координата по вертикали
     * @param result ненулевой вектор для результата
     * @return {@code true}, если входные и вычисленные координаты конечны
     * @throws NullPointerException если {@code result} равен {@code null}
     */
    public boolean worldToScreen(float worldX, float worldY, Vector2 result) {
        if (result == null) {
            throw new NullPointerException("Вектор результата не должен быть null");
        }
        if (!Float.isFinite(worldX) || !Float.isFinite(worldY)) {
            return false;
        }

        double screenX = (double) mapX
                + mapWidth / 2d
                + ((double) worldX - centerWorldX) * scale;
        double screenY = (double) mapY
                + mapHeight / 2d
                + ((double) worldY - centerWorldY) * scale;
        if (!isRepresentable(screenX) || !isRepresentable(screenY)) {
            return false;
        }
        result.set((float) screenX, (float) screenY);
        return true;
    }

    /**
     * Преобразует точку Scene2D в мировые координаты.
     *
     * <p>Результат не ограничивается видимой областью. Попадание указателя в экранный
     * прямоугольник карты отдельно проверяется через {@link #containsMapPoint(float, float)}.</p>
     *
     * @param screenX экранная координата по горизонтали
     * @param screenY экранная координата по вертикали
     * @param result ненулевой вектор для результата
     * @return {@code true}, если входные и вычисленные координаты конечны
     * @throws NullPointerException если {@code result} равен {@code null}
     */
    public boolean screenToWorld(float screenX, float screenY, Vector2 result) {
        if (result == null) {
            throw new NullPointerException("Вектор результата не должен быть null");
        }
        if (!Float.isFinite(screenX) || !Float.isFinite(screenY)) {
            return false;
        }

        double worldX = centerWorldX
                + ((double) screenX - mapX - mapWidth / 2d) / scale;
        double worldY = centerWorldY
                + ((double) screenY - mapY - mapHeight / 2d) / scale;
        if (!isRepresentable(worldX) || !isRepresentable(worldY)) {
            return false;
        }
        result.set((float) worldX, (float) worldY);
        return true;
    }

    /**
     * Возвращает новый вид после шага колеса мыши вокруг указанной экранной точки.
     *
     * <p>Отрицательное значение приближает карту, положительное — отдаляет. Мировая точка под
     * курсором сохраняет экранную позицию, пока ограничение центра у границы мира не требует
     * сдвига обзора. Неконечная величина прокрутки безопасно игнорируется.</p>
     *
     * @param screenX координата курсора по горизонтали
     * @param screenY координата курсора по вертикали
     * @param scrollAmount вертикальная величина прокрутки libGDX
     * @return новый ограниченный вид либо текущий экземпляр для некорректного события
     */
    public WorldMapLayout zoomByScroll(float screenX, float screenY, float scrollAmount) {
        if (!containsMapPoint(screenX, screenY) || !Float.isFinite(scrollAmount)) {
            return this;
        }
        double exponent = clamp(scrollAmount, -32f, 32f);
        double target = zoom * Math.pow(ZOOM_STEP, -exponent);
        float targetZoom;
        if (target >= MAX_ZOOM) {
            targetZoom = MAX_ZOOM;
        } else if (target <= MIN_ZOOM) {
            targetZoom = MIN_ZOOM;
        } else {
            targetZoom = (float) target;
        }
        return zoomAt(screenX, screenY, targetZoom);
    }

    /**
     * Возвращает новый вид с абсолютным увеличением вокруг экранной точки.
     *
     * @param screenX координата неподвижного экранного якоря по горизонтали
     * @param screenY координата неподвижного экранного якоря по вертикали
     * @param targetZoom желаемое конечное положительное увеличение
     * @return новый ограниченный вид
     * @throws IllegalArgumentException если якорь или увеличение некорректны
     */
    public WorldMapLayout zoomAt(float screenX, float screenY, float targetZoom) {
        if (!Float.isFinite(screenX) || !Float.isFinite(screenY)) {
            throw new IllegalArgumentException("Экранный якорь увеличения должен быть конечным");
        }
        if (!Float.isFinite(targetZoom) || targetZoom <= 0f) {
            throw new IllegalArgumentException("Увеличение карты должно быть конечным и положительным");
        }

        float constrainedZoom = clamp(targetZoom, MIN_ZOOM, MAX_ZOOM);
        Vector2 anchor = new Vector2();
        if (!screenToWorld(screenX, screenY, anchor)) {
            return this;
        }
        double newScale = fitScale * constrainedZoom;
        double desiredCenterX = anchor.x
                - ((double) screenX - mapX - mapWidth / 2d) / newScale;
        double desiredCenterY = anchor.y
                - ((double) screenY - mapY - mapHeight / 2d) / newScale;
        if (!isRepresentable(desiredCenterX) || !isRepresentable(desiredCenterY)) {
            return this;
        }
        return new WorldMapLayout(
                x,
                y,
                width,
                height,
                padding,
                (float) desiredCenterX,
                (float) desiredCenterY,
                constrainedZoom);
    }

    /**
     * Возвращает новый вид после перетаскивания карты на экране.
     *
     * <p>Положительный экранный сдвиг двигает содержимое вправо/вверх, поэтому центр камеры
     * перемещается в противоположную сторону. Центр автоматически ограничивается миром.</p>
     *
     * @param screenDeltaX экранный сдвиг указателя по горизонтали
     * @param screenDeltaY экранный сдвиг указателя по вертикали
     * @return новый ограниченный вид; при нулевом или некорректном сдвиге — текущий экземпляр
     */
    public WorldMapLayout panByScreen(float screenDeltaX, float screenDeltaY) {
        if (!Float.isFinite(screenDeltaX)
                || !Float.isFinite(screenDeltaY)
                || screenDeltaX == 0f && screenDeltaY == 0f) {
            return this;
        }
        double desiredCenterX = centerWorldX - screenDeltaX / scale;
        double desiredCenterY = centerWorldY - screenDeltaY / scale;
        if (!isRepresentable(desiredCenterX) || !isRepresentable(desiredCenterY)) {
            return this;
        }
        return new WorldMapLayout(
                x,
                y,
                width,
                height,
                padding,
                (float) desiredCenterX,
                (float) desiredCenterY,
                zoom);
    }

    /**
     * Переносит текущий центр и увеличение в экранный прямоугольник другого размера.
     *
     * @param newX новая левая граница виджета
     * @param newY новая нижняя граница виджета
     * @param newWidth новая положительная ширина виджета
     * @param newHeight новая положительная высота виджета
     * @param newPadding новый неотрицательный отступ
     * @return новый вид с сохранённым мировым состоянием камеры
     */
    public WorldMapLayout resize(
            float newX,
            float newY,
            float newWidth,
            float newHeight,
            float newPadding) {
        return new WorldMapLayout(
                newX,
                newY,
                newWidth,
                newHeight,
                newPadding,
                centerWorldX,
                centerWorldY,
                zoom);
    }

    /**
     * Проверяет принадлежность точки полному миру, включая его границу.
     *
     * @param worldX мировая координата по горизонтали
     * @param worldY мировая координата по вертикали
     * @return результат проверки полного диапазона мира
     */
    public boolean containsWorldPoint(float worldX, float worldY) {
        return Float.isFinite(worldX)
                && Float.isFinite(worldY)
                && worldX >= 0f
                && worldX <= WORLD_WIDTH
                && worldY >= 0f
                && worldY <= WORLD_HEIGHT;
    }

    /**
     * Проверяет, видна ли мировая точка в текущем обзоре, включая границу.
     *
     * @param worldX мировая координата по горизонтали
     * @param worldY мировая координата по вертикали
     * @return {@code true}, если точка принадлежит полному миру и текущему обзору
     */
    public boolean containsVisibleWorldPoint(float worldX, float worldY) {
        return containsWorldPoint(worldX, worldY)
                && worldX >= visibleWorldMinX
                && worldX <= visibleWorldMaxX
                && worldY >= visibleWorldMinY
                && worldY <= visibleWorldMaxY;
    }

    /**
     * Проверяет попадание экранной точки в прямоугольник интерактивной карты.
     *
     * @param screenX координата Scene2D по горизонтали
     * @param screenY координата Scene2D по вертикали
     * @return {@code true}, если точка лежит внутри карты, включая границу
     */
    public boolean containsMapPoint(float screenX, float screenY) {
        return Float.isFinite(screenX)
                && Float.isFinite(screenY)
                && screenX >= mapX
                && screenX <= mapX + mapWidth
                && screenY >= mapY
                && screenY <= mapY + mapHeight;
    }

    /**
     * Возвращает левую границу всего виджета.
     *
     * @return левая граница всего виджета
     */
    public float getX() {
        return x;
    }

    /**
     * Возвращает нижнюю границу всего виджета.
     *
     * @return нижняя граница всего виджета
     */
    public float getY() {
        return y;
    }

    /**
     * Возвращает ширину всего виджета.
     *
     * @return ширина всего виджета
     */
    public float getWidth() {
        return width;
    }

    /**
     * Возвращает высоту всего виджета.
     *
     * @return высота всего виджета
     */
    public float getHeight() {
        return height;
    }

    /**
     * Возвращает минимальный внутренний отступ виджета.
     *
     * @return минимальный внутренний отступ виджета
     */
    public float getPadding() {
        return padding;
    }

    /**
     * Возвращает левую экранную границу интерактивной области карты.
     *
     * @return левая экранная граница интерактивной области карты
     */
    public float getMapX() {
        return mapX;
    }

    /**
     * Возвращает нижнюю экранную границу интерактивной области карты.
     *
     * @return нижняя экранная граница интерактивной области карты
     */
    public float getMapY() {
        return mapY;
    }

    /**
     * Возвращает ширину интерактивной области карты.
     *
     * @return ширина интерактивной области карты
     */
    public float getMapWidth() {
        return mapWidth;
    }

    /**
     * Возвращает высоту интерактивной области карты.
     *
     * @return высота интерактивной области карты
     */
    public float getMapHeight() {
        return mapHeight;
    }

    /**
     * Возвращает текущий экранный масштаб преобразования координат.
     *
     * @return число единиц Scene2D на одну мировую единицу
     */
    public float getScale() {
        return scale;
    }

    /**
     * Возвращает текущее увеличение относительно полного обзора.
     *
     * @return текущее ограниченное увеличение карты
     */
    public float getZoom() {
        return zoom;
    }

    /**
     * Возвращает мировую координату центра обзора по горизонтали.
     *
     * @return мировая координата центра обзора по горизонтали
     */
    public float getCenterWorldX() {
        return centerWorldX;
    }

    /**
     * Возвращает мировую координату центра обзора по вертикали.
     *
     * @return мировая координата центра обзора по вертикали
     */
    public float getCenterWorldY() {
        return centerWorldY;
    }

    /**
     * Возвращает левую мировую границу текущего обзора.
     *
     * @return левая мировая граница текущего обзора
     */
    public float getVisibleWorldMinX() {
        return visibleWorldMinX;
    }

    /**
     * Возвращает нижнюю мировую границу текущего обзора.
     *
     * @return нижняя мировая граница текущего обзора
     */
    public float getVisibleWorldMinY() {
        return visibleWorldMinY;
    }

    /**
     * Возвращает правую мировую границу текущего обзора.
     *
     * @return правая мировая граница текущего обзора
     */
    public float getVisibleWorldMaxX() {
        return visibleWorldMaxX;
    }

    /**
     * Возвращает верхнюю мировую границу текущего обзора.
     *
     * @return верхняя мировая граница текущего обзора
     */
    public float getVisibleWorldMaxY() {
        return visibleWorldMaxY;
    }

    /** Проверяет исходный экранный прямоугольник до вычисления масштаба. */
    private static void validateGeometry(
            float x,
            float y,
            float width,
            float height,
            float padding) {
        if (!Float.isFinite(x)
                || !Float.isFinite(y)
                || !Float.isFinite(width)
                || !Float.isFinite(height)
                || !Float.isFinite(padding)) {
            throw new IllegalArgumentException(
                    "Координаты, размеры и отступ карты должны быть конечными");
        }
        if (width <= 0f || height <= 0f) {
            throw new IllegalArgumentException("Ширина и высота карты должны быть положительными");
        }
        if (padding < 0f || 2d * padding >= width || 2d * padding >= height) {
            throw new IllegalArgumentException(
                    "Отступ должен быть неотрицательным и оставлять место для карты");
        }
        if (!isRepresentable((double) x + width) || !isRepresentable((double) y + height)) {
            throw new IllegalArgumentException("Правая и верхняя границы карты должны быть конечными");
        }
    }

    /** Ограничивает конечное значение включительно заданным диапазоном. */
    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Возвращает, можно ли сохранить положительное число в {@code float}. */
    private static boolean isPositiveRepresentable(double value) {
        float converted = (float) value;
        return value > 0d
                && isRepresentable(value)
                && Float.isFinite(converted)
                && converted > 0f;
    }

    /** Возвращает, можно ли без переполнения сохранить число в {@code float}. */
    private static boolean isRepresentable(double value) {
        return Double.isFinite(value) && value >= -Float.MAX_VALUE && value <= Float.MAX_VALUE;
    }
}
