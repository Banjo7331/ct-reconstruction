package dev.ctrecon.engine.parallel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForkJoinLoopTest {

    private final ForkJoinPool pool = new ForkJoinPool(4);
    private final ForkJoinLoop loop = new ForkJoinLoop(pool);

    @AfterEach
    void closePool() {
        pool.close();
    }

    @Test
    void visitsEveryIndexExactlyOnce() {
        LoopAssertions.assertVisitsEveryIndexExactlyOnce(loop, 0, 10_007);
        LoopAssertions.assertVisitsEveryIndexExactlyOnce(loop, 3, 4);
    }

    @Test
    void splitsTheRangeIntoSeveralChunksPerWorker() {
        AtomicInteger chunks = new AtomicInteger();

        loop.forRange(0, 1_000, (from, to) -> chunks.incrementAndGet());

        assertThat(chunks.get()).isGreaterThanOrEqualTo(pool.getParallelism() * 4);
    }

    @Test
    void runsChunksOnPoolWorkers() {
        Set<Boolean> poolThreads = ConcurrentHashMap.newKeySet();

        loop.forRange(0, 64, (from, to) ->
                poolThreads.add(Thread.currentThread() instanceof ForkJoinWorkerThread worker
                        && worker.getPool() == pool));

        assertThat(poolThreads).containsOnly(true);
    }

    @Test
    void propagatesAFailureFromAnyChunk() {
        assertThatThrownBy(() -> loop.forRange(0, 100, (from, to) -> {
            if (from <= 57 && 57 < to) {
                throw new IllegalStateException("boom");
            }
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("boom");
    }
}
