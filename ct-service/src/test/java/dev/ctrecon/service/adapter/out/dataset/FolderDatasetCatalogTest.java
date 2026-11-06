package dev.ctrecon.service.adapter.out.dataset;

import dev.ctrecon.core.application.DatasetNotFoundException;
import dev.ctrecon.core.domain.Dataset;
import dev.ctrecon.core.domain.DatasetId;
import dev.ctrecon.core.domain.ImageData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class FolderDatasetCatalogTest {

    private static final DatasetId TINY = new DatasetId("tiny/Data8.mat");

    private final FolderDatasetCatalog catalog = new FolderDatasetCatalog(testData());

    static Path testData() {
        try {
            return Path.of(Objects.requireNonNull(FolderDatasetCatalogTest.class.getResource("/data")).toURI());
        } catch (URISyntaxException invalid) {
            throw new IllegalStateException(invalid);
        }
    }

    @Test
    void findsOnlyFilesThatHoldASystemMatrixAndAMatchingSinogram() {
        List<Dataset> datasets = catalog.list();

        assertThat(datasets).extracting(Dataset::id).containsExactly(TINY);
        Dataset tiny = datasets.getFirst();
        assertThat(tiny.imageSize()).isEqualTo(8);
        assertThat(tiny.detectorCount()).isEqualTo(13);
        assertThat(tiny.angleCount()).isEqualTo(12);
        assertThat(tiny.hasReference()).isTrue();
    }

    @Test
    void returnsTheSinogramWithDetectorsAsRowsAndAnglesAsColumns() {
        ImageData sinogram = catalog.sinogram(TINY).orElseThrow();

        assertThat(sinogram.width()).isEqualTo(12);
        assertThat(sinogram.height()).isEqualTo(13);
        assertThat(sinogram.valueAt(0, 6)).isPositive();
    }

    @Test
    void scalesTheReferenceReconstructionToTheDatasetResolution() {
        ImageData reference = catalog.reference(TINY).orElseThrow();

        assertThat(reference.width()).isEqualTo(8);
        assertThat(reference.valueAt(0, 0)).isZero();
        assertThat(reference.valueAt(3, 4)).isCloseTo(1.0, within(1e-12));
    }

    @Test
    void loadsTheSystemMatrixInCompressedColumnFormAndTheMeasurementsInRayOrder() {
        ProjectionData data = catalog.load(TINY);

        assertThat(data.columnPointers()).hasSize(65);
        assertThat(data.rowIndices()).hasSameSizeAs(data.values());
        assertThat(data.measurements()).hasSize(13 * 12);
        assertThat(data.rays()).isEqualTo(156);
    }

    @Test
    void cannotBeTrickedIntoReadingOutsideItsFolder(@TempDir Path directory) throws IOException {
        Path inside = Files.createDirectories(directory.resolve("data"));
        Files.copy(testData().resolve("tiny/Data8.mat"), directory.resolve("Data8.mat"));
        FolderDatasetCatalog sandboxed = new FolderDatasetCatalog(inside);

        assertThat(sandboxed.list()).isEmpty();
        assertThatThrownBy(() -> sandboxed.load(new DatasetId("Data8.mat")))
                .isInstanceOf(DatasetNotFoundException.class);
    }

    @Test
    void picksUpAFileReplacedOnDisk(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("Data8.mat");
        Files.copy(testData().resolve("other/notes.mat"), file);
        FolderDatasetCatalog live = new FolderDatasetCatalog(directory);
        assertThat(live.list()).isEmpty();

        Files.copy(testData().resolve("tiny/Data8.mat"), file, StandardCopyOption.REPLACE_EXISTING);
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5_000));

        assertThat(live.list()).hasSize(1);
    }

    @Test
    void reportsNoDatasetsWhenTheFolderIsMissing(@TempDir Path directory) {
        assertThat(new FolderDatasetCatalog(directory.resolve("missing")).list()).isEmpty();
    }
}
