package dev.ctrecon.core.application;

import dev.ctrecon.core.application.port.in.ListDatasetsUseCase;
import dev.ctrecon.core.application.port.in.PreviewDatasetUseCase;
import dev.ctrecon.core.application.port.out.DatasetCatalog;
import dev.ctrecon.core.domain.Dataset;
import dev.ctrecon.core.domain.DatasetId;
import dev.ctrecon.core.domain.ImageData;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class DatasetService implements ListDatasetsUseCase, PreviewDatasetUseCase {

    private final DatasetCatalog catalog;

    public DatasetService(DatasetCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @Override
    public List<Dataset> datasets() {
        return catalog.list().stream()
                .sorted(Comparator.comparing(dataset -> dataset.id().value()))
                .toList();
    }

    @Override
    public ImageData sinogram(DatasetId id) {
        return catalog.sinogram(id).orElseThrow(() -> new DatasetNotFoundException(id, "Sinogram"));
    }

    @Override
    public ImageData reference(DatasetId id) {
        return catalog.reference(id).orElseThrow(() -> new DatasetNotFoundException(id, "Reference image"));
    }
}
