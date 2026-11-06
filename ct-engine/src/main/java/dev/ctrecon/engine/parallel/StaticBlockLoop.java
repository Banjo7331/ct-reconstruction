package dev.ctrecon.engine.parallel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public final class StaticBlockLoop implements ParallelLoop {

    private final ExecutorService executor;
    private final int blocks;

    public StaticBlockLoop(ExecutorService executor, int blocks) {
        if (blocks <= 0) {
            throw new IllegalArgumentException("blocks must be positive, got " + blocks);
        }
        this.executor = executor;
        this.blocks = blocks;
    }

    @Override
    public void forRange(int fromInclusive, int toExclusive, RangeTask task) {
        int length = toExclusive - fromInclusive;
        if (length <= 0) {
            return;
        }
        int count = Math.min(blocks, length);
        List<Callable<Void>> parts = new ArrayList<>(count);
        for (int block = 0; block < count; block++) {
            int from = fromInclusive + (int) ((long) length * block / count);
            int to = fromInclusive + (int) ((long) length * (block + 1) / count);
            parts.add(() -> {
                task.run(from, to);
                return null;
            });
        }
        awaitAll(invokeAll(parts));
    }

    private List<Future<Void>> invokeAll(List<Callable<Void>> parts) {
        try {
            return executor.invokeAll(parts);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new OperationCancelledException("Interrupted while waiting for parallel blocks", interrupted);
        }
    }

    private static void awaitAll(List<Future<Void>> futures) {
        RuntimeException firstFailure = null;
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (ExecutionException failed) {
                if (firstFailure == null) {
                    firstFailure = unwrap(failed);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new OperationCancelledException("Interrupted while waiting for parallel blocks", interrupted);
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private static RuntimeException unwrap(ExecutionException failed) {
        Throwable cause = failed.getCause();
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Parallel block failed", cause);
    }
}
