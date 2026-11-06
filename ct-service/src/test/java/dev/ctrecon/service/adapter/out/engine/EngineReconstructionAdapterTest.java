package dev.ctrecon.service.adapter.out.engine;

import dev.ctrecon.core.application.port.out.ReconstructionCancelledException;
import dev.ctrecon.core.application.port.out.ReconstructionOutcome;
import dev.ctrecon.core.domain.ConvergencePoint;
import dev.ctrecon.core.domain.DatasetId;
import dev.ctrecon.core.domain.ExecutionMode;
import dev.ctrecon.core.domain.ImageData;
import dev.ctrecon.core.domain.ReconstructionParameters;
import dev.ctrecon.core.domain.Termination;
import dev.ctrecon.service.adapter.out.dataset.FolderDatasetCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EngineReconstructionAdapterTest {

    private static final DatasetId TINY = new DatasetId("tiny/Data8.mat");

    private final FolderDatasetCatalog catalog = new FolderDatasetCatalog(testData());

    private static Path testData() {
        try {
            return Path.of(Objects.requireNonNull(EngineReconstructionAdapterTest.class.getResource("/data")).toURI());
        } catch (URISyntaxException invalid) {
            throw new IllegalStateException(invalid);
        }
    }

    @Test
    void reconstructsTheTinyDatasetAndReportsConvergence() {
        List<ConvergencePoint> points = new ArrayList<>();
        EngineReconstructionAdapter adapter = new EngineReconstructionAdapter(catalog, 1);

        ReconstructionOutcome outcome = adapter.reconstruct(
                new ReconstructionParameters(TINY, 40, 1e-4, ExecutionMode.FORK_JOIN, 2), points::add, () -> false);

        ImageData image = outcome.image();
        assertThat(image.width()).isEqualTo(8);
        assertThat(image.valueAt(4, 4)).isGreaterThan(0.8);
        assertThat(image.valueAt(0, 0)).isLessThan(0.3);
        assertThat(points).isNotEmpty();
        assertThat(points.getLast().relativeMisfit()).isLessThan(0.05);
        assertThat(outcome.termination()).isIn(Termination.CONVERGED, Termination.ITERATION_LIMIT);
    }

    @Test
    void loadsAndPreparesEachDatasetOnlyOnce() {
        AtomicInteger loads = new AtomicInteger();
        EngineReconstructionAdapter adapter = new EngineReconstructionAdapter(id -> {
            loads.incrementAndGet();
            return catalog.load(id);
        }, 1);
        ReconstructionParameters parameters = new ReconstructionParameters(TINY, 2, 10.0, ExecutionMode.SEQUENTIAL, 1);

        adapter.reconstruct(parameters, point -> {
        }, () -> false);
        adapter.reconstruct(parameters, point -> {
        }, () -> false);

        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void reportsCancellationInCoreTerms() {
        EngineReconstructionAdapter adapter = new EngineReconstructionAdapter(catalog, 1);

        assertThatThrownBy(() -> adapter.reconstruct(
                new ReconstructionParameters(TINY, 10, 10.0, ExecutionMode.STATIC_BLOCKS, 2), point -> {
                }, () -> true))
                .isInstanceOf(ReconstructionCancelledException.class);
    }

    @ParameterizedTest
    @EnumSource(ExecutionMode.class)
    void mapsEveryCoreModeToTheEngineModeOfTheSameName(ExecutionMode mode) {
        assertThat(EngineReconstructionAdapter.toEngineMode(mode).name()).isEqualTo(mode.name());
    }
}
