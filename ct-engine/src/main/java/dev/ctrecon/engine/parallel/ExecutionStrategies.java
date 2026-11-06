package dev.ctrecon.engine.parallel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

public final class ExecutionStrategies {

    private ExecutionStrategies() {
    }

    public static ExecutionStrategy create(ExecutionMode mode, int threads) {
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be positive, got " + threads);
        }
        return switch (mode) {
            case SEQUENTIAL -> new Sequential();
            case STATIC_BLOCKS -> new StaticBlocks(threads);
            case FORK_JOIN -> new ForkJoin(threads);
        };
    }

    private static final class Sequential implements ExecutionStrategy {

        @Override
        public ParallelLoop loop() {
            return SequentialLoop.INSTANCE;
        }

        @Override
        public void close() {
        }
    }

    private static final class StaticBlocks implements ExecutionStrategy {

        private final ExecutorService executor;
        private final ParallelLoop loop;

        private StaticBlocks(int threads) {
            this.executor = Executors.newFixedThreadPool(threads, Thread.ofPlatform().name("ct-block-", 1).factory());
            this.loop = new StaticBlockLoop(executor, threads);
        }

        @Override
        public ParallelLoop loop() {
            return loop;
        }

        @Override
        public void close() {
            executor.close();
        }
    }

    private static final class ForkJoin implements ExecutionStrategy {

        private final ForkJoinPool pool;
        private final ParallelLoop loop;

        private ForkJoin(int threads) {
            this.pool = new ForkJoinPool(threads, ForkJoin::newWorker, null, false);
            this.loop = new ForkJoinLoop(pool);
        }

        private static ForkJoinWorkerThread newWorker(ForkJoinPool pool) {
            ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            worker.setName("ct-fj-" + worker.getPoolIndex());
            return worker;
        }

        @Override
        public ParallelLoop loop() {
            return loop;
        }

        @Override
        public void close() {
            pool.close();
        }
    }
}
