package dev.ctrecon.service.adapter.in.web;

import dev.ctrecon.core.domain.Dataset;

public record DatasetResponse(String id, int imageSize, int detectorCount, int angleCount, long fileSizeBytes,
                              boolean hasReference) {

    public static DatasetResponse from(Dataset dataset) {
        return new DatasetResponse(dataset.id().value(), dataset.imageSize(), dataset.detectorCount(),
                dataset.angleCount(), dataset.fileSizeBytes(), dataset.hasReference());
    }
}
