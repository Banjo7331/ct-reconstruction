package dev.ctrecon.service.adapter.out.dataset;

import dev.ctrecon.core.domain.DatasetId;

public record ProjectionData(DatasetId id, int imageSize, int detectorCount, int angleCount,
                             int[] columnPointers, int[] rowIndices, double[] values, double[] measurements) {

    public ProjectionData {
        if (columnPointers.length != imageSize * imageSize + 1) {
            throw new IllegalArgumentException("Expected one column per pixel in the system matrix of " + id);
        }
        if (measurements.length != detectorCount * angleCount) {
            throw new IllegalArgumentException("Expected one measurement per ray in " + id);
        }
    }

    public int rays() {
        return measurements.length;
    }
}
