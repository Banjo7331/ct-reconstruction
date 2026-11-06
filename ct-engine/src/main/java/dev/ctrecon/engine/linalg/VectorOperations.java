package dev.ctrecon.engine.linalg;

import dev.ctrecon.engine.parallel.ParallelLoop;

public final class VectorOperations {

    static final int REDUCTION_CHUNK = 4096;

    private final ParallelLoop loop;

    public VectorOperations(ParallelLoop loop) {
        this.loop = loop;
    }

    public double dot(double[] a, double[] b) {
        requireSameLength(a, b);
        return reduce(a.length, (from, to) -> {
            double sum = 0.0;
            for (int i = from; i < to; i++) {
                sum += a[i] * b[i];
            }
            return sum;
        });
    }

    public double norm(double[] a) {
        return Math.sqrt(dot(a, a));
    }

    public double distance(double[] a, double[] b) {
        requireSameLength(a, b);
        return Math.sqrt(reduce(a.length, (from, to) -> {
            double sum = 0.0;
            for (int i = from; i < to; i++) {
                double difference = a[i] - b[i];
                sum += difference * difference;
            }
            return sum;
        }));
    }

    public void addScaled(double[] target, double factor, double[] source) {
        requireSameLength(target, source);
        loop.forRange(0, target.length, (from, to) -> {
            for (int i = from; i < to; i++) {
                target[i] += factor * source[i];
            }
        });
    }

    public void scaleAndAdd(double[] target, double factor, double[] addend) {
        requireSameLength(target, addend);
        loop.forRange(0, target.length, (from, to) -> {
            for (int i = from; i < to; i++) {
                target[i] = addend[i] + factor * target[i];
            }
        });
    }

    private double reduce(int length, ChunkSum chunkSum) {
        int chunks = Math.ceilDiv(length, REDUCTION_CHUNK);
        double[] partials = new double[chunks];
        loop.forRange(0, chunks, (from, to) -> {
            for (int chunk = from; chunk < to; chunk++) {
                int start = chunk * REDUCTION_CHUNK;
                partials[chunk] = chunkSum.over(start, Math.min(length, start + REDUCTION_CHUNK));
            }
        });
        double total = 0.0;
        for (double partial : partials) {
            total += partial;
        }
        return total;
    }

    private static void requireSameLength(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vector lengths differ: " + a.length + " and " + b.length);
        }
    }

    @FunctionalInterface
    private interface ChunkSum {

        double over(int fromInclusive, int toExclusive);
    }
}
