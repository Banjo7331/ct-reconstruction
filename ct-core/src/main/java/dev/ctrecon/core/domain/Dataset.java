package dev.ctrecon.core.domain;

import java.util.Objects;

public record Dataset(DatasetId id, int imageSize, int detectorCount, int angleCount, long fileSizeBytes,
                      boolean hasReference) {

    public Dataset {
        Objects.requireNonNull(id, "id");
        if (imageSize <= 0 || detectorCount <= 0 || angleCount <= 0) {
            throw new IllegalArgumentException("Dataset " + id + " has non-positive dimensions");
        }
    }

    public int unknowns() {
        return imageSize * imageSize;
    }

    public int measurements() {
        return detectorCount * angleCount;
    }
}
