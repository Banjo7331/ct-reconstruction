package dev.ctrecon.service.adapter.out.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import dev.ctrecon.core.application.port.out.ExecutionQueue;
import dev.ctrecon.core.application.port.out.QueueFullException;
import dev.ctrecon.core.domain.ReconstructionId;

import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class BoundedExecutionQueue implements ExecutionQueue, AutoCloseable {

    static final String RECONSTRUCTION_ID_KEY = "reconstructionId";

    private static final Logger LOGGER = LoggerFactory.getLogger(BoundedExecutionQueue.class);

    private final ThreadPoolExecutor executor;
    private final int workers;
    private final int capacity;
    private final Duration shutdownTimeout;

    public BoundedExecutionQueue(int workers, int capacity, Duration shutdownTimeout) {
        if (workers <= 0 || capacity <= 0) {
            throw new IllegalArgumentException(
                    "workers and capacity must be positive, got " + workers + " and " + capacity);
        }
        this.workers = workers;
        this.capacity = capacity;
        this.shutdownTimeout = shutdownTimeout;
        this.executor = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity),
                Thread.ofPlatform().name("ct-job-", 1).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public void enqueue(ReconstructionId id, Runnable work) {
        try {
            executor.execute(() -> runWithReconstructionContext(id, work));
        } catch (RejectedExecutionException rejected) {
            String reason = executor.isShutdown()
                    ? "The service is shutting down"
                    : workers + " reconstruction(s) are running and " + capacity + " are already waiting";
            throw new QueueFullException(reason, rejected);
        }
    }

    public int waitingCount() {
        return executor.getQueue().size();
    }

    @SuppressWarnings("try")
    private static void runWithReconstructionContext(ReconstructionId id, Runnable work) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable(RECONSTRUCTION_ID_KEY, id.toString())) {
            long start = System.nanoTime();
            LOGGER.info("Worker picked up reconstruction");
            try {
                work.run();
            } catch (RuntimeException unexpected) {
                LOGGER.error("Reconstruction crashed", unexpected);
            }
            LOGGER.info("Worker released reconstruction after {} ms", (System.nanoTime() - start) / 1_000_000);
        }
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                LOGGER.warn("Reconstructions still running after {}, interrupting workers", shutdownTimeout);
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
