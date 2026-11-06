package dev.ctrecon.core.application;

import dev.ctrecon.core.domain.Dataset;
import dev.ctrecon.core.domain.DatasetId;
import dev.ctrecon.core.domain.ImageData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasetServiceTest {

    private static final DatasetId WALNUT = new DatasetId("walnut/Data164.mat");
    private static final DatasetId LOTUS = new DatasetId("lotus/Data128.mat");

    private final FakeDatasetCatalog catalog = new FakeDatasetCatalog()
            .with(new Dataset(WALNUT, 164, 164, 120, 33_000_000L, true))
            .with(new Dataset(LOTUS, 128, 429, 120, 20_000_000L, false))
            .withReference(WALNUT, new ImageData(1, 1, new double[]{0.5}));
    private final DatasetService service = new DatasetService(catalog);

    @Test
    void listsDatasetsInAlphabeticalOrder() {
        assertThat(service.datasets()).extracting(Dataset::id).containsExactly(LOTUS, WALNUT);
    }

    @Test
    void returnsTheMeasuredSinogram() {
        ImageData sinogram = service.sinogram(LOTUS);

        assertThat(sinogram.width()).isEqualTo(120);
        assertThat(sinogram.height()).isEqualTo(429);
    }

    @Test
    void returnsTheReferenceImageOnlyWhenTheDatasetHasOne() {
        assertThat(service.reference(WALNUT).valueAt(0, 0)).isEqualTo(0.5);
        assertThatThrownBy(() -> service.reference(LOTUS))
                .isInstanceOf(DatasetNotFoundException.class)
                .hasMessageContaining("Reference image");
    }

    @Test
    void reportsAnUnknownDataset() {
        assertThatThrownBy(() -> service.sinogram(new DatasetId("missing.mat")))
                .isInstanceOf(DatasetNotFoundException.class);
    }
}
