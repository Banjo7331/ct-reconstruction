package dev.ctrecon.service.adapter.out.execution;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import dev.ctrecon.core.application.port.out.QueueFullException;
import dev.ctrecon.core.domain.ReconstructionId;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedExecutionQueueTest {

    private final BoundedExecutionQueue queue = new BoundedExecutionQueue(1, 1, Duration.ofSeconds(5));
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void shutDown() {
        release.countDown();
        queue.close();
    }

    @Test
    void runsWorkOnANamedWorkerWithTheIdInTheLoggingContext() throws Exception {
        ReconstructionId id = ReconstructionId.random();
        CompletableFuture<String> observed = new CompletableFuture<>();

        queue.enqueue(id, () -> observed.complete(Thread.currentThread().getName() + " " + MDC.get("reconstructionId")));

        assertThat(observed.get(5, TimeUnit.SECONDS)).isEqualTo("ct-job-1 " + id);
    }

    @Test
    void rejectsWorkWhenEveryWorkerIsBusyAndTheQueueIsFull() throws Exception {
        CountDownLatch running = new CountDownLatch(1);
        queue.enqueue(ReconstructionId.random(), () -> {
            running.countDown();
            awaitRelease();
        });
        assertThat(running.await(5, TimeUnit.SECONDS)).isTrue();
        queue.enqueue(ReconstructionId.random(), () -> {
        });

        assertThat(queue.waitingCount()).isEqualTo(1);
        assertThatThrownBy(() -> queue.enqueue(ReconstructionId.random(), () -> {
        })).isInstanceOf(QueueFullException.class).hasMessageContaining("already waiting");
    }

    @Test
    void keepsTheWorkerAliveWhenAReconstructionCrashes() throws Exception {
        CompletableFuture<Boolean> next = new CompletableFuture<>();

        queue.enqueue(ReconstructionId.random(), () -> {
            throw new IllegalStateException("crash");
        });
        queue.enqueue(ReconstructionId.random(), () -> next.complete(true));

        assertThat(next.get(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void refusesNewWorkAfterShutdown() {
        queue.close();

        assertThatThrownBy(() -> queue.enqueue(ReconstructionId.random(), () -> {
        })).isInstanceOf(QueueFullException.class).hasMessageContaining("shutting down");
    }

    private void awaitRelease() {
        try {
            release.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
