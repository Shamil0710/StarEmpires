package com.spacesim.ui;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Metre-anchored map grid and scale bar for the uniform SI system map.
 *
 * <p>Grid spacing and scale-bar lengths are 1, 2 or 5 times a power of ten metres. Lines are
 * anchored to the projection's metre origin, so they move with pan and zoom exactly like
 * projected objects. Pure presentation geometry: it reads a scale and an origin and never touches
 * simulation state.</p>
 */
final class MetricMapGrid {
    /** Upper bound of lines per axis, guarding degenerate scales. */
    static final int MAX_LINES_PER_AXIS = 400;

    private static final double[] MANTISSAS = {1d, 2d, 5d};

    /**
     * One grid line on one axis.
     *
     * @param screen screen coordinate of the line on its axis
     * @param major true for a major line
     */
    record Line(float screen, boolean major) { }

    /**
     * Grid spacing in metres.
     *
     * @param minorMetres distance between neighbouring lines
     * @param majorMetres distance between major lines, a multiple of the minor spacing
     */
    record Spacing(double minorMetres, double majorMetres) {
        int minorPerMajor() {
            return (int) Math.round(majorMetres / minorMetres);
        }
    }

    /**
     * Scale bar of one physical length.
     *
     * @param lengthMetres physical length shown by the bar
     * @param pixels bar length on screen
     * @param label player-facing length label
     */
    record ScaleBar(double lengthMetres, float pixels, String label) { }

    private MetricMapGrid() {
    }

    /**
     * Chooses the smallest round spacing whose minor lines are at least the given pixels apart.
     *
     * @param pixelsPerMetre current screen scale
     * @param minMinorPixels minimum distance between minor lines on screen
     * @return grid spacing; major lines fall on round values (5 or 10 times the minor mantissa)
     */
    static Spacing spacing(double pixelsPerMetre, double minMinorPixels) {
        requirePositive(pixelsPerMetre, "pixelsPerMetre");
        requirePositive(minMinorPixels, "minMinorPixels");
        double minor = roundAtLeast(minMinorPixels / pixelsPerMetre);
        double mantissa = minor / Math.pow(10d, Math.floor(Math.log10(minor) + 1e-9));
        return new Spacing(minor, minor * (Math.round(mantissa) == 5L ? 2d : 5d));
    }

    /**
     * Lists grid lines of one axis inside a screen interval.
     *
     * @param originScreen screen coordinate of the projection's metre origin on this axis
     * @param pixelsPerMetre current screen scale
     * @param spacing grid spacing
     * @param minScreen lower screen bound, inclusive
     * @param maxScreen upper screen bound, inclusive
     * @return lines in increasing screen order; empty when the scale is unusable
     */
    static List<Line> lines(double originScreen, double pixelsPerMetre, Spacing spacing,
            double minScreen, double maxScreen) {
        if (!Double.isFinite(originScreen) || !(pixelsPerMetre > 0d)
                || !Double.isFinite(pixelsPerMetre) || maxScreen < minScreen) {
            return List.of();
        }
        double stepPixels = spacing.minorMetres() * pixelsPerMetre;
        double count = (maxScreen - minScreen) / stepPixels;
        if (!(count < MAX_LINES_PER_AXIS)) {
            return List.of();
        }
        long first = (long) Math.ceil((minScreen - originScreen) / stepPixels);
        int perMajor = spacing.minorPerMajor();
        List<Line> result = new ArrayList<>();
        for (long index = first; ; index++) {
            double screen = originScreen + index * stepPixels;
            if (screen > maxScreen) {
                break;
            }
            result.add(new Line((float) screen, Math.floorMod(index, perMajor) == 0));
        }
        return List.copyOf(result);
    }

    /**
     * Chooses the longest round length that fits the given bar width.
     *
     * @param pixelsPerMetre current screen scale
     * @param maxPixels longest allowed bar on screen
     * @return scale bar with its exact on-screen length
     */
    static ScaleBar scaleBar(double pixelsPerMetre, double maxPixels) {
        requirePositive(pixelsPerMetre, "pixelsPerMetre");
        requirePositive(maxPixels, "maxPixels");
        double length = roundAtMost(maxPixels / pixelsPerMetre);
        return new ScaleBar(length, (float) (length * pixelsPerMetre), formatLength(length));
    }

    /**
     * Formats a physical length for map labels: metres below 1 km, kilometres below one million
     * kilometres, then millions of kilometres.
     *
     * @param metres non-negative finite length
     * @return label such as {@code 500 м}, {@code 2 км}, {@code 15 000 км} or {@code 2 млн км}
     */
    static String formatLength(double metres) {
        if (!Double.isFinite(metres) || metres < 0d) {
            throw new IllegalArgumentException("length must be finite and non-negative");
        }
        if (metres < 1_000d) {
            return number(metres) + " м";
        }
        if (metres < 1.0e9d) {
            return number(metres / 1_000d) + " км";
        }
        return number(metres / 1.0e9d) + " млн км";
    }

    private static String number(double value) {
        if (value == 0d) {
            return "0";
        }
        String plain = new BigDecimal(value).round(new MathContext(3)).stripTrailingZeros()
                .toPlainString();
        int dot = plain.indexOf('.');
        String integer = dot < 0 ? plain : plain.substring(0, dot);
        String fraction = dot < 0 ? "" : plain.substring(dot);
        StringBuilder grouped = new StringBuilder();
        for (int index = 0; index < integer.length(); index++) {
            if (index > 0 && (integer.length() - index) % 3 == 0) {
                grouped.append(' ');
            }
            grouped.append(integer.charAt(index));
        }
        return grouped + fraction;
    }

    private static double roundAtLeast(double value) {
        double power = Math.pow(10d, Math.floor(Math.log10(value)));
        for (double mantissa : MANTISSAS) {
            double candidate = mantissa * power;
            if (candidate >= value * (1d - 1e-12)) {
                return candidate;
            }
        }
        return 10d * power;
    }

    private static double roundAtMost(double value) {
        double power = Math.pow(10d, Math.floor(Math.log10(value)));
        for (int index = MANTISSAS.length - 1; index >= 0; index--) {
            double candidate = MANTISSAS[index] * power;
            if (candidate <= value * (1d + 1e-12)) {
                return candidate;
            }
        }
        return power;
    }

    private static void requirePositive(double value, String label) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
    }
}
