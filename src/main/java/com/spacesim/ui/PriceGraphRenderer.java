package com.spacesim.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.FloatArray;

/**
 * Отрисовывает историю цены в виде линейного графика средствами libGDX.
 *
 * <p>Масштаб по вертикали вычисляется отдельно для каждого ряда. Минимальный
 * диапазон равен единице, поэтому постоянная цена отображается устойчиво.
 * Неконечные точки, некорректная геометрия и слишком короткие ряды безопасно
 * пропускаются: такие данные не передаются OpenGL.</p>
 *
 * <p>Экземпляр владеет внутренним {@link ShapeRenderer}; после использования
 * необходимо вызвать {@link #dispose()}.</p>
 */
public class PriceGraphRenderer {
    private final ShapeRenderer sr = new ShapeRenderer();

    /** Создаёт отрисовщик и принадлежащий ему {@link ShapeRenderer}. */
    public PriceGraphRenderer() {
    }

    /**
     * Рисует последовательные отрезки истории цены в заданном прямоугольнике.
     *
     * <p>Координаты интерпретируются в системе, заданной матрицей проекции.
     * Первая точка располагается у левой границы, последняя — у правой. Метод
     * ничего не рисует, если матрица отсутствует, ряд содержит менее двух точек,
     * в данных встречается {@code NaN}/бесконечность либо прямоугольник не может
     * быть представлен конечными {@code float}-координатами.</p>
     *
     * @param projectionMatrix матрица камеры Scene2D; при {@code null} отрисовка пропускается
     * @param history значения цены в хронологическом порядке
     * @param x координата левой границы графика
     * @param y координата нижней границы графика
     * @param w ширина графика; должна быть положительной
     * @param h высота графика; должна быть положительной
     */
    public void render(Matrix4 projectionMatrix, FloatArray history, float x, float y, float w, float h) {
        PriceRange priceRange = calculateRange(history);
        if (projectionMatrix == null || priceRange == null || !isValidGeometry(x, y, w, h)) {
            return;
        }

        sr.setProjectionMatrix(projectionMatrix);
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(Color.GREEN);

        double stepX = (double) w / (history.size - 1);

        for (int i = 0; i < history.size - 1; i++) {
            float y1 = projectPrice(history.get(i), y, h, priceRange);
            float y2 = projectPrice(history.get(i + 1), y, h, priceRange);
            float x1 = (float) ((double) x + i * stepX);
            float x2 = (float) ((double) x + (i + 1) * stepX);
            sr.line(x1, y1, x2, y2);
        }
        sr.end();
    }

    /**
     * Вычисляет конечный минимум и безопасный вертикальный диапазон ряда.
     *
     * @return диапазон либо {@code null}, если ряд нельзя отрисовать
     */
    static PriceRange calculateRange(FloatArray history) {
        if (history == null || history.size < 2) {
            return null;
        }

        float min = history.get(0);
        float max = min;
        if (!Float.isFinite(min)) {
            return null;
        }

        for (int index = 1; index < history.size; index++) {
            float value = history.get(index);
            if (!Float.isFinite(value)) {
                return null;
            }
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        double span = Math.max(1d, (double) max - min);
        return new PriceRange(min, span);
    }

    /** Проверяет конечность размеров и вычисленных правой и верхней границ. */
    static boolean isValidGeometry(float x, float y, float width, float height) {
        if (!Float.isFinite(x)
                || !Float.isFinite(y)
                || !Float.isFinite(width)
                || !Float.isFinite(height)
                || width <= 0f
                || height <= 0f) {
            return false;
        }

        double right = (double) x + width;
        double top = (double) y + height;
        return right >= -Float.MAX_VALUE
                && right <= Float.MAX_VALUE
                && top >= -Float.MAX_VALUE
                && top <= Float.MAX_VALUE;
    }

    /** Проецирует цену в вертикальную координату внутри области графика. */
    private float projectPrice(float price, float y, float height, PriceRange range) {
        double normalized = ((double) price - range.min()) / range.span();
        return (float) ((double) y + normalized * height);
    }

    /** Освобождает GPU-ресурсы внутреннего {@link ShapeRenderer}. */
    public void dispose() {
        sr.dispose();
    }

    /** Нормализованный диапазон ряда: минимум и гарантированно положительный размах. */
    record PriceRange(float min, double span) {
    }
}
