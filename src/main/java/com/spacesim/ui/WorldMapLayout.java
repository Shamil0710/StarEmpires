package com.spacesim.ui;

import com.badlogic.gdx.math.Vector2;

/**
 * Неизменяемое преобразование между координатами игрового мира и областью карты на экране.
 *
 * <p>Размер моделируемого мира фиксирован и равен {@value #WORLD_WIDTH} на
 * {@value #WORLD_HEIGHT} условных единиц. Содержимое вписывается в переданный экранный
 * прямоугольник с одинаковым масштабом по обеим осям. Свободное место, оставшееся после
 * сохранения пропорций, распределяется поровну по краям. Координата {@code Y} направлена вверх,
 * как в координатах Scene2D после преобразования входного события через viewport.</p>
 *
 * <p>Класс не обращается к OpenGL и потому подходит как для отрисовки, так и для обработки
 * указателя и модульных тестов. Все параметры конструктора проверяются заранее; методы
 * преобразования возвращают {@code false} для неконечных входных значений и не изменяют
 * результирующий вектор.</p>
 */
public final class WorldMapLayout {
    /** Ширина моделируемого мира в условных единицах. */
    public static final float WORLD_WIDTH = 700f;

    /** Высота моделируемого мира в условных единицах. */
    public static final float WORLD_HEIGHT = 550f;

    private final float x;
    private final float y;
    private final float width;
    private final float height;
    private final float padding;
    private final float mapX;
    private final float mapY;
    private final float mapWidth;
    private final float mapHeight;
    private final float scale;

    /**
     * Вычисляет геометрию карты внутри экранного прямоугольника.
     *
     * @param x левая граница всего виджета в экранных координатах
     * @param y нижняя граница всего виджета в экранных координатах
     * @param width положительная ширина виджета
     * @param height положительная высота виджета
     * @param padding неотрицательный минимальный отступ от каждой стороны виджета
     * @throws IllegalArgumentException если координата или размер не конечны, размер не
     *                                  положителен, отступ отрицателен, занимает всю доступную
     *                                  область либо правая/верхняя граница не представима
     *                                  конечным значением {@code float}
     */
    public WorldMapLayout(float x, float y, float width, float height, float padding) {
        validateGeometry(x, y, width, height, padding);

        double availableWidth = (double) width - 2d * padding;
        double availableHeight = (double) height - 2d * padding;
        double calculatedScale = Math.min(availableWidth / WORLD_WIDTH, availableHeight / WORLD_HEIGHT);
        float floatScale = (float) calculatedScale;
        if (!Float.isFinite(floatScale) || floatScale <= 0f) {
            throw new IllegalArgumentException("Масштаб карты должен быть конечным и положительным");
        }

        double calculatedMapWidth = WORLD_WIDTH * calculatedScale;
        double calculatedMapHeight = WORLD_HEIGHT * calculatedScale;
        double calculatedMapX = (double) x + padding + (availableWidth - calculatedMapWidth) / 2d;
        double calculatedMapY = (double) y + padding + (availableHeight - calculatedMapHeight) / 2d;

        if (!isRepresentable(calculatedMapX)
                || !isRepresentable(calculatedMapY)
                || !isRepresentable(calculatedMapWidth)
                || !isRepresentable(calculatedMapHeight)
                || calculatedMapWidth <= 0d
                || calculatedMapHeight <= 0d) {
            throw new IllegalArgumentException("Вычисленная область карты не представима конечными float-координатами");
        }

        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.padding = padding;
        this.mapX = (float) calculatedMapX;
        this.mapY = (float) calculatedMapY;
        this.mapWidth = (float) calculatedMapWidth;
        this.mapHeight = (float) calculatedMapHeight;
        this.scale = floatScale;
    }

    /**
     * Проецирует точку мира на экран.
     *
     * <p>Точки за пределами фиксированного мира также могут быть спроецированы, если результат
     * остаётся конечным. Для решения, следует ли такую точку показывать, предназначен
     * {@link #containsWorldPoint(float, float)}.</p>
     *
     * @param worldX координата точки мира по горизонтали
     * @param worldY координата точки мира по вертикали
     * @param result ненулевой вектор, в который будет записан результат
     * @return {@code true}, если входные и вычисленные координаты конечны; иначе {@code false}
     * @throws NullPointerException если {@code result} равен {@code null}
     */
    public boolean worldToScreen(float worldX, float worldY, Vector2 result) {
        if (result == null) {
            throw new NullPointerException("Вектор результата не должен быть null");
        }
        if (!Float.isFinite(worldX) || !Float.isFinite(worldY)) {
            return false;
        }

        double screenX = (double) mapX + worldX * scale;
        double screenY = (double) mapY + worldY * scale;
        if (!isRepresentable(screenX) || !isRepresentable(screenY)) {
            return false;
        }

        result.set((float) screenX, (float) screenY);
        return true;
    }

    /**
     * Выполняет обратное преобразование экранной точки в координаты мира.
     *
     * <p>Метод не ограничивает точку видимой областью: для координаты в полях или за пределами
     * виджета будет возвращена соответствующая точка продолженной плоскости мира. Проверить
     * попадание именно в карту можно через {@link #containsMapPoint(float, float)}.</p>
     *
     * @param screenX экранная координата по горизонтали
     * @param screenY экранная координата по вертикали
     * @param result ненулевой вектор, в который будет записан результат
     * @return {@code true}, если входные и вычисленные координаты конечны; иначе {@code false}
     * @throws NullPointerException если {@code result} равен {@code null}
     */
    public boolean screenToWorld(float screenX, float screenY, Vector2 result) {
        if (result == null) {
            throw new NullPointerException("Вектор результата не должен быть null");
        }
        if (!Float.isFinite(screenX) || !Float.isFinite(screenY)) {
            return false;
        }

        double worldX = ((double) screenX - mapX) / scale;
        double worldY = ((double) screenY - mapY) / scale;
        if (!isRepresentable(worldX) || !isRepresentable(worldY)) {
            return false;
        }

        result.set((float) worldX, (float) worldY);
        return true;
    }

    /**
     * Проверяет, находится ли конечная мировая точка внутри моделируемого мира, включая границу.
     *
     * @param worldX мировая координата по горизонтали
     * @param worldY мировая координата по вертикали
     * @return результат проверки диапазонов {@code [0, WORLD_WIDTH]} и {@code [0, WORLD_HEIGHT]}
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
     * Проверяет попадание конечной экранной точки в фактическую область карты, включая границу.
     *
     * @param screenX экранная координата по горизонтали
     * @param screenY экранная координата по вертикали
     * @return {@code true}, если точка находится внутри вписанной области мира
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
     * Возвращает заданный минимальный внутренний отступ.
     *
     * @return заданный минимальный внутренний отступ
     */
    public float getPadding() {
        return padding;
    }

    /**
     * Возвращает левую границу вписанной области мира.
     *
     * @return левая граница вписанной области мира
     */
    public float getMapX() {
        return mapX;
    }

    /**
     * Возвращает нижнюю границу вписанной области мира.
     *
     * @return нижняя граница вписанной области мира
     */
    public float getMapY() {
        return mapY;
    }

    /**
     * Возвращает ширину вписанной области мира.
     *
     * @return ширина вписанной области мира
     */
    public float getMapWidth() {
        return mapWidth;
    }

    /**
     * Возвращает высоту вписанной области мира.
     *
     * @return высота вписанной области мира
     */
    public float getMapHeight() {
        return mapHeight;
    }

    /**
     * Возвращает единый масштаб преобразования координат.
     *
     * @return число экранных единиц на одну единицу координат мира
     */
    public float getScale() {
        return scale;
    }

    /** Проверяет исходный прямоугольник и отступ до вычисления масштаба. */
    private static void validateGeometry(float x, float y, float width, float height, float padding) {
        if (!Float.isFinite(x)
                || !Float.isFinite(y)
                || !Float.isFinite(width)
                || !Float.isFinite(height)
                || !Float.isFinite(padding)) {
            throw new IllegalArgumentException("Координаты, размеры и отступ карты должны быть конечными");
        }
        if (width <= 0f || height <= 0f) {
            throw new IllegalArgumentException("Ширина и высота карты должны быть положительными");
        }
        if (padding < 0f || 2d * padding >= width || 2d * padding >= height) {
            throw new IllegalArgumentException("Отступ должен быть неотрицательным и оставлять место для карты");
        }
        if (!isRepresentable((double) x + width) || !isRepresentable((double) y + height)) {
            throw new IllegalArgumentException("Правая и верхняя границы карты должны быть конечными");
        }
    }

    /** Возвращает, можно ли без переполнения сохранить число в {@code float}. */
    private static boolean isRepresentable(double value) {
        return Double.isFinite(value) && value >= -Float.MAX_VALUE && value <= Float.MAX_VALUE;
    }
}
