package dev.ctrecon.core.application;

import dev.ctrecon.core.application.port.out.ReconstructionCancelledException;
import dev.ctrecon.core.application.port.out.ReconstructionEngine;
import dev.ctrecon.core.application.port.out.ReconstructionOutcome;
import dev.ctrecon.core.domain.ConvergencePoint;
import dev.ctrecon.core.domain.DatasetId;
import dev.ctrecon.core.domain.ExecutionMode;
import dev.ctrecon.core.domain.ImageData;
import dev.ctrecon.core.domain.Reconstruction;
import dev.ctrecon.core.domain.ReconstructionId;
import dev.ctrecon.core.domain.ReconstructionParameters;
import dev.ctrecon.core.domain.ReconstructionStatus;
import dev.ctrecon.core.domain.Termination;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReconstructionProcessorTest {

    private static final Instant T0 = Instant.parse("2026-11-07T12:00:00Z");
    private static final ReconstructionParameters PARAMETERS = new ReconstructionParameters(
            new DatasetId("walnut/Data82.mat"), 3, 10.0, ExecutionMode.FORK_JOIN, 2);
    private static final ImageData IMAGE = new ImageData(2, 2, new double[]{0, 1, 2, 3});

    private final FakeReconstructionRepository reconstructions = new FakeReconstructionRepository();
    private final FakeImageStore images = new FakeImageStore();
    private final CancellationRequests cancellations = new CancellationRequests();
    private final MutableClock clock = new MutableClock(T0);
    private final ReconstructionId id = ReconstructionId.random();

    @Test
    void runsAQueuedReconstructionToCompletionAndStoresTheImage() {
        reconstructions.save(Reconstruction.submit(id, PARAMETERS, T0));
        List<Integer> recordedWhileRunning = new ArrayList<>();
        ReconstructionEngine engine = (parameters, progress, cancellation) -> {
            clock.advance(Duration.ofSeconds(2));
            for (int iteration = 1; iteration <= parameters.iterations(); iteration++) {
                progress.iterationCompleted(new ConvergencePoint(iteration, 0.5 / iteration, 0.1));
                recordedWhileRunning.add(reconstructions.findById(id).orElseThrow().completedIterations());
            }
            return new ReconstructionOutcome(IMAGE, Termination.ITERATION_LIMIT);
        };

        processor(engine).process(id);

        Reconstruction done = reconstructions.findById(id).orElseThrow();
        assertThat(done.status()).isEqualTo(ReconstructionStatus.COMPLETED);
        assertThat(done.termination()).contains(Termination.ITERATION_LIMIT);
        assertThat(done.duration()).contains(Duration.ofSeconds(2));
        assertThat(recordedWhileRunning).containsExactly(1, 2, 3);
        assertThat(images.find(id)).containsSame(IMAGE);
        assertThat(cancellations.activeCount()).isZero();
    }

    @Test
    void skipsAReconstructionCancelledWhileItWasQueued() {
        reconstructions.save(Reconstruction.submit(id, PARAMETERS, T0).cancel(T0));
        List<String> engineCalls = new ArrayList<>();

        processor((parameters, progress, cancellation) -> {
            engineCalls.add("called");
            return new ReconstructionOutcome(IMAGE, Termination.CONVERGED);
        }).process(id);

        assertThat(engineCalls).isEmpty();
        assertThat(reconstructions.findById(id).orElseThrow().status()).isEqualTo(ReconstructionStatus.CANCELLED);
    }

    @Test
    void marksTheReconstructionCancelledWhenTheEngineStopsOnRequest() {
        reconstructions.save(Reconstruction.submit(id, PARAMETERS, T0));

        processor((parameters, progress, cancellation) -> {
            progress.iterationCompleted(new ConvergencePoint(1, 0.5, 0.1));
            cancellations.request(id);
            assertThat(cancellation.isCancellationRequested()).isTrue();
            throw new ReconstructionCancelledException("stopped", null);
        }).process(id);

        Reconstruction cancelled = reconstructions.findById(id).orElseThrow();
        assertThat(cancelled.status()).isEqualTo(ReconstructionStatus.CANCELLED);
        assertThat(cancelled.completedIterations()).isEqualTo(1);
        assertThat(images.find(id)).isEmpty();
    }

    @Test
    void recordsAnEngineFailureWithItsReason() {
        reconstructions.save(Reconstruction.submit(id, PARAMETERS, T0));

        processor((parameters, progress, cancellation) -> {
            throw new IllegalStateException("Data82.mat is truncated");
        }).process(id);

        Reconstruction failed = reconstructions.findById(id).orElseThrow();
        assertThat(failed.status()).isEqualTo(ReconstructionStatus.FAILED);
        assertThat(failed.failureReason()).contains("IllegalStateException: Data82.mat is truncated");
        assertThat(cancellations.activeCount()).isZero();
    }

    private ReconstructionProcessor processor(ReconstructionEngine engine) {
        return new ReconstructionProcessor(reconstructions, images, engine, cancellations, clock);
    }
}
