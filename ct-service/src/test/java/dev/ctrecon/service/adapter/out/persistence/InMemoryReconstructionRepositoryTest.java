package dev.ctrecon.service.adapter.out.persistence;

import dev.ctrecon.core.application.ReconstructionNotFoundException;
import dev.ctrecon.core.domain.ConvergencePoint;
import dev.ctrecon.core.domain.DatasetId;
import dev.ctrecon.core.domain.ExecutionMode;
import dev.ctrecon.core.domain.Reconstruction;
import dev.ctrecon.core.domain.ReconstructionId;
import dev.ctrecon.core.domain.ReconstructionParameters;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryReconstructionRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-07T12:00:00Z");

    private final InMemoryReconstructionRepository repository = new InMemoryReconstructionRepository();

    @Test
    void savesFindsAndDeletes() {
        Reconstruction reconstruction = queued(10);

        repository.save(reconstruction);

        assertThat(repository.findById(reconstruction.id())).containsSame(reconstruction);
        assertThat(repository.findAll()).containsExactly(reconstruction);
        repository.delete(reconstruction.id());
        assertThat(repository.findById(reconstruction.id())).isEmpty();
    }

    @Test
    void updatingAnUnknownReconstructionFails() {
        assertThatThrownBy(() -> repository.update(ReconstructionId.random(), current -> current))
                .isInstanceOf(ReconstructionNotFoundException.class);
    }

    @Test
    void aFailingChangeLeavesTheStoredValueUntouched() {
        Reconstruction reconstruction = queued(10);
        repository.save(reconstruction);

        assertThatThrownBy(() -> repository.update(reconstruction.id(),
                current -> current.recordIteration(new ConvergencePoint(1, 0.1, 0.1))))
                .isInstanceOf(RuntimeException.class);

        assertThat(repository.findById(reconstruction.id())).containsSame(reconstruction);
    }

    @Test
    void concurrentReadModifyWriteUpdatesAreNeverLost() throws Exception {
        int threads = 8;
        int updatesPerThread = 100;
        Reconstruction reconstruction = queued(threads * updatesPerThread);
        repository.save(reconstruction.start(NOW));
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> workers = new ArrayList<>();
            for (int thread = 0; thread < threads; thread++) {
                workers.add(executor.submit(() -> {
                    start.await();
                    for (int update = 0; update < updatesPerThread; update++) {
                        repository.update(reconstruction.id(), current -> current.recordIteration(
                                new ConvergencePoint(current.completedIterations() + 1, 0.1, 0.1)));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> worker : workers) {
                worker.get();
            }
        }

        assertThat(repository.findById(reconstruction.id()).orElseThrow().completedIterations())
                .isEqualTo(threads * updatesPerThread);
    }

    private static Reconstruction queued(int iterations) {
        return Reconstruction.submit(ReconstructionId.random(), new ReconstructionParameters(
                new DatasetId("walnut/Data82.mat"), iterations, 10.0, ExecutionMode.SEQUENTIAL, 1), NOW);
    }
}
