package dev.ctrecon.engine.synthetic;

import dev.ctrecon.engine.linalg.CsrMatrix;
import dev.ctrecon.engine.parallel.SequentialLoop;

import java.util.Arrays;

public final class SyntheticScanner {

    private static final int SAMPLES_PER_PIXEL = 4;
    private static final double[][] DISKS = {
            {0.0, 0.0, 0.80, 1.0},
            {0.30, 0.20, 0.20, -0.5},
            {-0.35, -0.30, 0.12, 0.8}
    };

    private SyntheticScanner() {
    }

    public static SyntheticScan parallelBeam(int imageSize, int angleCount) {
        if (imageSize < 2 || angleCount < 1) {
            throw new IllegalArgumentException("Need imageSize >= 2 and angleCount >= 1");
        }
        int detectorCount = 2 * (int) Math.ceil(imageSize * Math.sqrt(2.0) / 2.0) + 1;
        CsrMatrix systemMatrix = systemMatrix(imageSize, detectorCount, angleCount);
        double[] phantom = phantom(imageSize);
        double[] measurements = new double[systemMatrix.rows()];
        systemMatrix.multiply(phantom, measurements, SequentialLoop.INSTANCE);
        return new SyntheticScan(imageSize, detectorCount, angleCount, systemMatrix, phantom, measurements);
    }

    static double[] phantom(int imageSize) {
        double pixel = 2.0 / imageSize;
        double[] image = new double[imageSize * imageSize];
        for (int column = 0; column < imageSize; column++) {
            double x = -1.0 + (column + 0.5) * pixel;
            for (int row = 0; row < imageSize; row++) {
                double y = 1.0 - (row + 0.5) * pixel;
                double value = 0.0;
                for (double[] disk : DISKS) {
                    double dx = x - disk[0];
                    double dy = y - disk[1];
                    if (dx * dx + dy * dy <= disk[2] * disk[2]) {
                        value += disk[3];
                    }
                }
                image[row + column * imageSize] = value;
            }
        }
        return image;
    }

    private static CsrMatrix systemMatrix(int imageSize, int detectorCount, int angleCount) {
        double pixel = 2.0 / imageSize;
        double step = pixel / SAMPLES_PER_PIXEL;
        double reach = Math.sqrt(2.0);
        int rays = detectorCount * angleCount;
        double[] weights = new double[imageSize * imageSize];
        int[] touched = new int[imageSize * imageSize];
        int[] rowPointers = new int[rays + 1];
        IntList indices = new IntList();
        DoubleList values = new DoubleList();

        for (int angle = 0; angle < angleCount; angle++) {
            double theta = Math.PI * angle / angleCount;
            double cos = Math.cos(theta);
            double sin = Math.sin(theta);
            for (int detector = 0; detector < detectorCount; detector++) {
                double offset = (detector - (detectorCount - 1) / 2.0) * pixel;
                int touchedCount = 0;
                for (double along = -reach; along <= reach; along += step) {
                    double x = offset * cos - along * sin;
                    double y = offset * sin + along * cos;
                    int column = (int) Math.floor((x + 1.0) / pixel);
                    int row = (int) Math.floor((1.0 - y) / pixel);
                    if (column < 0 || column >= imageSize || row < 0 || row >= imageSize) {
                        continue;
                    }
                    int index = row + column * imageSize;
                    if (weights[index] == 0.0) {
                        touched[touchedCount++] = index;
                    }
                    weights[index] += step;
                }
                Arrays.sort(touched, 0, touchedCount);
                for (int i = 0; i < touchedCount; i++) {
                    indices.add(touched[i]);
                    values.add(weights[touched[i]]);
                    weights[touched[i]] = 0.0;
                }
                rowPointers[detector + angle * detectorCount + 1] = indices.size();
            }
        }
        return CsrMatrix.fromArrays(rays, imageSize * imageSize, rowPointers, indices.toArray(), values.toArray());
    }

    private static final class IntList {

        private int[] items = new int[1024];
        private int size;

        void add(int value) {
            if (size == items.length) {
                items = Arrays.copyOf(items, items.length * 2);
            }
            items[size++] = value;
        }

        int size() {
            return size;
        }

        int[] toArray() {
            return Arrays.copyOf(items, size);
        }
    }

    private static final class DoubleList {

        private double[] items = new double[1024];
        private int size;

        void add(double value) {
            if (size == items.length) {
                items = Arrays.copyOf(items, items.length * 2);
            }
            items[size++] = value;
        }

        double[] toArray() {
            return Arrays.copyOf(items, size);
        }
    }
}
