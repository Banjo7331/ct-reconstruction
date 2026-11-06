package dev.ctrecon.core.application;

import dev.ctrecon.core.application.port.out.QueueFullException;
import dev.ctrecon.core.application.port.out.ReconstructionCancelledException;
import dev.ctrecon.core.application.port.out.ReconstructionEngine;
import dev.ctrecon.core.application.port.out.ReconstructionOutcome;
import dev.ctrecon.core.domain.ConvergencePoint;
import dev.ctrecon.core.domain.Dataset;
import dev.ctrecon.core.domain.DatasetId;
import dev.ctrecon.core.domain.ExecutionMode;
import dev.ctrecon.core.domain.IllegalReconstructionStateException;
import dev.ctrecon.core.domain.ImageData;
import dev.ctrecon.core.domain.Reconstruction;
import dev.ctrecon.core.domain.ReconstructionId;
import dev.ctrecon.core.domain.ReconstructionParameters;
import dev.ctrecon.core.domain.ReconstructionStatus;
import dev.ctrecon.core.domain.Termination;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReconstructionServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-07T12:00:00Z");
    private static final DatasetId WALNUT = new DatasetId("walnut/Data82.mat");
    private static final ReconstructionParameters PARAMETERS =
            new ReconstructionParameters(WALNUT, 3, 10.0, ExecutionMode.FORK_JOIN, 2);
    private static final ImageData IMAGE = new ImageData(2, 2, new double[]{0, 1, 2, 3});

    private final FakeReconstructionRepository reconstructions = new FakeReconstructionRepository();
    private final FakeImageStore images = new FakeImageStore();
    private final FakeDatasetCatalog catalog = new FakeDatasetCatalog()
            .with(new Dataset(WALNUT, 82, 82, 120, 8_100_000L, false));
    private final ManualExecutionQueue queue = new ManualExecutionQueue(2);
    private final MutableClock clock = new MutableClock(T0);

    private ReconstructionService serviceWith(ReconstructionEngine engine) {
        return new ReconstructionService(reconstructions, images, catalog, engine, queue, clock,
                ReconstructionId::random);
    }

    private static ReconstructionEngine succeeding() {
        return (parameters, progress, cancellation) -> {
            for (int iteration = 1; iteration <= parameters.iterations(); iteration++) {
                progress.iterationCompleted(new ConvergencePoint(iteration, 0.1 / iteration, 0.05));
            }
            return new ReconstructionOutcome(IMAGE, Termination.ITERATION_LIMIT);
        };
    }

    @Nested
    class Submitting {

        @Test
        void storesTheReconstructionAsQueuedAndHandsItToTheQueue() {
            Reconstruction reconstruction = serviceWith(succeeding()).submit(PARAMETERS);

            assertThat(reconstruction.status()).isEqualTo(ReconstructionStatus.QUEUED);
            assertThat(reconstructions.findById(reconstruction.id())).isPresent();
            assertThat(queue.size()).isEqualTo(1);
        }

        @Test
        void refusesADatasetThatIsNotInTheCatalogue() {
            ReconstructionParameters missing = new ReconstructionParameters(
                    new DatasetId("lotus/Data128.mat"), 3, 10.0, ExecutionMode.SEQUENTIAL, 1);

            assertThatThrownBy(() -> serviceWith(succeeding()).submit(missing))
                    .isInstanceOf(DatasetNotFoundException.class)
                    .hasMessageContaining("lotus/Data128.mat");
            assertThat(queue.size()).isZero();
        }

        @Test
        void completesWhenTheQueueRunsIt() {
            ReconstructionService service = serviceWith(succeeding());
            Reconstruction reconstruction = service.submit(PARAMETERS);

            queue.runNext();

            Reconstruction done = service.get(reconstruction.id());
            assertThat(done.status()).isEqualTo(ReconstructionStatus.COMPLETED);
            assertThat(done.history()).hasSize(3);
            assertThat(service.image(reconstruction.id())).isSameAs(IMAGE);
        }

        @Test
        void leavesNoTraceWhenTheQueueIsFull() {
            ReconstructionService service = serviceWith(succeeding());
            service.submit(PARAMETERS);
            service.submit(PARAMETERS);

            assertThatThrownBy(() -> service.submit(PARAMETERS)).isInstanceOf(QueueFullException.class);
            assertThat(service.list()).hasSize(2);
        }
    }

    @Nested
    class Cancelling {

        @Test
        void cancelsAQueuedReconstructionWithoutEverCallingTheEngine() {
            ReconstructionEngine engine = mock(ReconstructionEngine.class);
            ReconstructionService service = serviceWith(engine);
            Reconstruction reconstruction = service.submit(PARAMETERS);

            Reconstruction cancelled = service.cancel(reconstruction.id());
            queue.runNext();

            assertThat(cancelled.status()).isEqualTo(ReconstructionStatus.CANCELLED);
            verifyNoInteractions(engine);
        }

        @Test
        void signalsARunningReconstructionWhichThenStopsCooperatively() throws Exception {
            CountDownLatch engineStarted = new CountDownLatch(1);
            ReconstructionEngine engine = (parameters, progress, cancellation) -> {
                engineStarted.countDown();
                while (!cancellation.isCancellationRequested()) {
                    Thread.onSpinWait();
                }
                throw new ReconstructionCancelledException("stopped by request", null);
            };
            ReconstructionService service = serviceWith(engine);
            Reconstruction reconstruction = service.submit(PARAMETERS);

            try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
                Future<?> running = worker.submit(queue::runNext);
                assertThat(engineStarted.await(5, TimeUnit.SECONDS)).isTrue();

                Reconstruction flagged = service.cancel(reconstruction.id());
                running.get(5, TimeUnit.SECONDS);

                assertThat(flagged.isCancellationRequested()).isTrue();
            }
            assertThat(service.get(reconstruction.id()).status()).isEqualTo(ReconstructionStatus.CANCELLED);
        }

        @Test
        void refusesToCancelAFinishedReconstruction() {
            ReconstructionService service = serviceWith(succeeding());
            Reconstruction reconstruction = service.submit(PARAMETERS);
            queue.runNext();

            assertThatThrownBy(() -> service.cancel(reconstruction.id()))
                    .isInstanceOf(IllegalReconstructionStateException.class);
        }

        @Test
        void cancelsEverythingStillActiveOnShutdown() {
            ReconstructionService service = serviceWith(succeeding());
            Reconstruction first = service.submit(PARAMETERS);
            Reconstruction second = service.submit(PARAMETERS);

            service.cancelActiveReconstructions();

            assertThat(service.get(first.id()).status()).isEqualTo(ReconstructionStatus.CANCELLED);
            assertThat(service.get(second.id()).status()).isEqualTo(ReconstructionStatus.CANCELLED);
        }
    }

    @Nested
    class Querying {

        @Test
        void failsForAnUnknownReconstruction() {
            assertThatThrownBy(() -> serviceWith(succeeding()).get(ReconstructionId.random()))
                    .isInstanceOf(ReconstructionNotFoundException.class);
        }

        @Test
        void listsTheNewestFirst() {
            ReconstructionService service = serviceWith(succeeding());
            Reconstruction older = service.submit(PARAMETERS);
            clock.advance(Duration.ofMinutes(1));
            Reconstruction newer = service.submit(PARAMETERS);

            assertThat(service.list()).extracting(Reconstruction::id).containsExactly(newer.id(), older.id());
        }

        @Test
        void hasNoImageBeforeCompletion() {
            ReconstructionService service = serviceWith(succeeding());
            Reconstruction reconstruction = service.submit(PARAMETERS);

            assertThatThrownBy(() -> service.image(reconstruction.id()))
                    .isInstanceOf(ImageNotAvailableException.class)
                    .hasMessageContaining("QUEUED");
        }

        @Test
        void hasNoImageOnceItWasEvicted() {
            ReconstructionService service = serviceWith(succeeding());
            Reconstruction reconstruction = service.submit(PARAMETERS);
            queue.runNext();
            images.evict(reconstruction.id());

            assertThatThrownBy(() -> service.image(reconstruction.id()))
                    .isInstanceOf(ImageNotAvailableException.class);
        }
    }

    @Test
    void recordsAFailureReportedByTheEngine() {
        ReconstructionEngine engine = mock(ReconstructionEngine.class);
        when(engine.reconstruct(any(), any(), any())).thenThrow(new IllegalStateException("out of memory"));
        ReconstructionService service = serviceWith(engine);
        Reconstruction reconstruction = service.submit(PARAMETERS);

        queue.runNext();

        assertThat(service.get(reconstruction.id()).failureReason()).contains("IllegalStateException: out of memory");
    }
}
