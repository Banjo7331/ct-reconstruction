package dev.ctrecon.core.domain;

import java.util.Arrays;
import java.util.Objects;

public final class ImageData {

    private final int width;
    private final int height;
    private final double[] rowMajor;

    public ImageData(int width, int height, double[] rowMajor) {
        Objects.requireNonNull(rowMajor, "rowMajor");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Image dimensions must be positive, got " + width + "x" + height);
        }
        if (rowMajor.length != width * height) {
            throw new IllegalArgumentException("Expected " + width * height + " values, got " + rowMajor.length);
        }
        this.width = width;
        this.height = height;
        this.rowMajor = rowMajor.clone();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public double valueAt(int column, int row) {
        Objects.checkIndex(column, width);
        Objects.checkIndex(row, height);
        return rowMajor[row * width + column];
    }

    public double percentile(double fraction) {
        if (!(fraction >= 0.0 && fraction <= 1.0)) {
            throw new IllegalArgumentException("Fraction must be within [0, 1], got " + fraction);
        }
        double[] sorted = rowMajor.clone();
        Arrays.sort(sorted);
        return sorted[(int) Math.round(fraction * (sorted.length - 1))];
    }
}
