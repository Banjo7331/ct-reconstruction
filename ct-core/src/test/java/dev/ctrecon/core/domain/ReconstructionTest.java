package dev.ctrecon.core.domain;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconstructionTest {

    private static final Instant SUBMITTED = Instant.parse("2026-11-07T10:00:00Z");
    private static final Instant STARTED = SUBMITTED.plusSeconds(1);
    private static final Instant FINISHED = STARTED.plusSeconds(4);
    private static final ReconstructionParameters PARAMETERS = new ReconstructionParameters(
            new DatasetId("walnut/Data164.mat"), 10, 10.0, ExecutionMode.FORK_JOIN, 4);

    private final Reconstruction queued = Reconstruction.submit(ReconstructionId.random(), PARAMETERS, SUBMITTED);

    private static ConvergencePoint point(int iteration) {
        return new ConvergencePoint(iteration, 1.0 / iteration, 0.1);
    }

    @Test
    void startsQueuedWithoutHistory() {
        assertThat(queued.status()).isEqualTo(ReconstructionStatus.QUEUED);
        assertThat(queued.completedIterations()).isZero();
        assertThat(queued.latest()).isEmpty();
        assertThat(queued.duration()).isEmpty();
    }

    @Test
    void completesTheHappyPathAndKeepsTheConvergenceHistory() {
        Reconstruction completed = queued.start(STARTED)
                .recordIteration(point(1))
                .recordIteration(point(2))
                .complete(Termination.CONVERGED, FINISHED);

        assertThat(completed.status()).isEqualTo(ReconstructionStatus.COMPLETED);
        assertThat(completed.history()).extracting(ConvergencePoint::iteration).containsExactly(1, 2);
        assertThat(completed.termination()).contains(Termination.CONVERGED);
        assertThat(completed.duration()).contains(Duration.ofSeconds(4));
    }

    @Test
    void transitionsLeaveTheOriginalUntouched() {
        Reconstruction running = queued.start(STARTED);

        assertThat(queued.status()).isEqualTo(ReconstructionStatus.QUEUED);
        assertThat(running).isNotSameAs(queued).isEqualTo(queued);
    }

    @Nested
    class Iterations {

        private final Reconstruction running = queued.start(STARTED);

        @Test
        void ignoreALateReportOfAnIterationAlreadyRecorded() {
            Reconstruction updated = running.recordIteration(point(1)).recordIteration(point(2));

            assertThat(updated.recordIteration(point(2))).isSameAs(updated);
            assertThat(updated.completedIterations()).isEqualTo(2);
        }

        @Test
        void cannotExceedTheRequestedNumber() {
            assertThatThrownBy(() -> running.recordIteration(point(11))).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void areOnlyAcceptedWhileRunning() {
            assertThatThrownBy(() -> queued.recordIteration(point(1)))
                    .isInstanceOf(IllegalReconstructionStateException.class);
        }
    }

    @Nested
    class Cancellation {

        @Test
        void cancelsAQueuedReconstructionImmediately() {
            Reconstruction cancelled = queued.cancel(STARTED);

            assertThat(cancelled.status()).isEqualTo(ReconstructionStatus.CANCELLED);
            assertThat(cancelled.finishedAt()).contains(STARTED);
        }

        @Test
        void onlyFlagsARunningOneBecauseTheWorkStopsCooperatively() {
            Reconstruction flagged = queued.start(STARTED).recordIteration(point(1)).cancel(FINISHED);

            assertThat(flagged.status()).isEqualTo(ReconstructionStatus.RUNNING);
            assertThat(flagged.isCancellationRequested()).isTrue();
            assertThat(flagged.cancel(FINISHED)).isSameAs(flagged);
            assertThat(flagged.markCancelled(FINISHED).history()).hasSize(1);
        }

        @Test
        void completionWinsIfTheWorkFinishedBeforeNoticingTheRequest() {
            Reconstruction completed = queued.start(STARTED).cancel(FINISHED).complete(Termination.ITERATION_LIMIT, FINISHED);

            assertThat(completed.status()).isEqualTo(ReconstructionStatus.COMPLETED);
        }

        @Test
        void aFinishedReconstructionCannotBeCancelled() {
            Reconstruction completed = queued.start(STARTED).complete(Termination.CONVERGED, FINISHED);

            assertThatThrownBy(() -> completed.cancel(FINISHED))
                    .isInstanceOf(IllegalReconstructionStateException.class)
                    .hasMessageContaining("COMPLETED");
        }
    }

    @Test
    void failureRecordsTheReasonOnce() {
        Reconstruction failed = queued.start(STARTED).fail("file is corrupt", FINISHED);

        assertThat(failed.failureReason()).contains("file is corrupt");
        assertThatThrownBy(() -> failed.fail("again", FINISHED)).isInstanceOf(IllegalReconstructionStateException.class);
    }

    @Test
    void convergencePointsRejectNegativeOrInfiniteValues() {
        assertThatThrownBy(() -> new ConvergencePoint(0, 0.1, 0.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConvergencePoint(1, -0.1, 0.1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConvergencePoint(1, 0.1, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
