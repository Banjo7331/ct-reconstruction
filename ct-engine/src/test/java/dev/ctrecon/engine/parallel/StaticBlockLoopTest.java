package dev.ctrecon.engine.parallel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaticBlockLoopTest {

    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    @AfterEach
    void closeExecutor() {
        executor.close();
    }

    @Test
    void splitsTheRangeIntoEqualContiguousBlocksOnePerThread() {
        List<String> blocks = new CopyOnWriteArrayList<>();

        new StaticBlockLoop(executor, 3).forRange(0, 10, (from, to) -> blocks.add(from + ".." + to));

        assertThat(blocks).containsExactlyInAnyOrder("0..3", "3..6", "6..10");
    }

    @Test
    void neverCreatesEmptyBlocks() {
        List<String> blocks = new CopyOnWriteArrayList<>();

        new StaticBlockLoop(executor, 8).forRange(5, 7, (from, to) -> blocks.add(from + ".." + to));

        assertThat(blocks).containsExactlyInAnyOrder("5..6", "6..7");
    }

    @Test
    void visitsEveryIndexExactlyOnce() {
        LoopAssertions.assertVisitsEveryIndexExactlyOnce(new StaticBlockLoop(executor, 3), 0, 1_001);
    }

    @Test
    void rethrowsTheOriginalRuntimeException() {
        StaticBlockLoop loop = new StaticBlockLoop(executor, 3);

        assertThatThrownBy(() -> loop.forRange(0, 9, (from, to) -> {
            if (from == 3) {
                throw new OperationCancelledException("stop");
            }
        })).isInstanceOf(OperationCancelledException.class).hasMessage("stop");
    }
}
