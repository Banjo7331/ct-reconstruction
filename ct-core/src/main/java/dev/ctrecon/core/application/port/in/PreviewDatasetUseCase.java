package dev.ctrecon.core.application.port.in;

import dev.ctrecon.core.domain.DatasetId;
import dev.ctrecon.core.domain.ImageData;

public interface PreviewDatasetUseCase {

    ImageData sinogram(DatasetId id);

    ImageData reference(DatasetId id);
}
