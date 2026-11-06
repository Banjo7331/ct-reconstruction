package dev.ctrecon.core.application.port.out;

import dev.ctrecon.core.domain.Dataset;
import dev.ctrecon.core.domain.DatasetId;
import dev.ctrecon.core.domain.ImageData;

import java.util.List;
import java.util.Optional;

public interface DatasetCatalog {

    List<Dataset> list();

    Optional<Dataset> find(DatasetId id);

    Optional<ImageData> sinogram(DatasetId id);

    Optional<ImageData> reference(DatasetId id);
}
