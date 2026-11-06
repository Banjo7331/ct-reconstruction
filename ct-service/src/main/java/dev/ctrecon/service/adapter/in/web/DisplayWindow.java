package dev.ctrecon.service.adapter.in.web;

import dev.ctrecon.core.domain.ImageData;

public record DisplayWindow(double lower, double upper) {

    static final double LOWER_PERCENTILE = 0.01;
    static final double UPPER_PERCENTILE = 0.99;
    private static final int MAX_GRAY = 255;

    public DisplayWindow {
        if (!Double.isFinite(lower) || !Double.isFinite(upper) || lower >= upper) {
            throw new IllegalArgumentException(
                    "Display window needs finite bounds with lower < upper, got [" + lower + ", " + upper + "]");
        }
    }

    public static DisplayWindow of(ImageData image, Double lower, Double upper) {
        if (lower != null && upper != null) {
            return new DisplayWindow(lower, upper);
        }
        double low = lower != null ? lower : image.percentile(LOWER_PERCENTILE);
        double high = upper != null ? upper : image.percentile(UPPER_PERCENTILE);
        if (high <= low) {
            high = low + Math.max(Math.abs(low), 1.0) * 1e-6;
        }
        return new DisplayWindow(low, high);
    }

    public int toGray(double value) {
        double scaled = (value - lower) / (upper - lower);
        return (int) Math.round(Math.clamp(scaled, 0.0, 1.0) * MAX_GRAY);
    }
}
