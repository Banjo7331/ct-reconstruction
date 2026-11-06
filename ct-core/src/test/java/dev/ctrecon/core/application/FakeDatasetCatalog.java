package dev.ctrecon.core.application;

import dev.ctrecon.core.application.port.out.DatasetCatalog;
import dev.ctrecon.core.domain.Dataset;
import dev.ctrecon.core.domain.DatasetId;
import dev.ctrecon.core.domain.ImageData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class FakeDatasetCatalog implements DatasetCatalog {

    private final Map<DatasetId, Dataset> datasets = new LinkedHashMap<>();
    private final Map<DatasetId, ImageData> references = new LinkedHashMap<>();

    FakeDatasetCatalog with(Dataset dataset) {
        datasets.put(dataset.id(), dataset);
        return this;
    }

    FakeDatasetCatalog withReference(DatasetId id, ImageData image) {
        references.put(id, image);
        return this;
    }

    @Override
    public List<Dataset> list() {
        return List.copyOf(datasets.values());
    }

    @Override
    public Optional<Dataset> find(DatasetId id) {
        return Optional.ofNullable(datasets.get(id));
    }

    @Override
    public Optional<ImageData> sinogram(DatasetId id) {
        return find(id).map(dataset -> new ImageData(dataset.angleCount(), dataset.detectorCount(),
                new double[dataset.measurements()]));
    }

    @Override
    public Optional<ImageData> reference(DatasetId id) {
        return Optional.ofNullable(references.get(id));
    }
}
