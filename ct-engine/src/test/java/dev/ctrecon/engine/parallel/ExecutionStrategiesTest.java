package dev.ctrecon.engine.parallel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionStrategiesTest {

    @Test
    void sequentialRunsOnTheCallingThread() {
        try (ExecutionStrategy strategy = ExecutionStrategies.create(ExecutionMode.SEQUENTIAL, 4)) {
            assertThat(threadNamesUsedBy(strategy.loop())).containsOnly(Thread.currentThread().getName());
        }
    }

    @Test
    void staticBlocksRunOnNamedPoolThreads() {
        try (ExecutionStrategy strategy = ExecutionStrategies.create(ExecutionMode.STATIC_BLOCKS, 2)) {
            assertThat(threadNamesUsedBy(strategy.loop())).allMatch(name -> name.startsWith("ct-block-"));
        }
    }

    @Test
    void forkJoinRunsOnNamedWorkers() {
        try (ExecutionStrategy strategy = ExecutionStrategies.create(ExecutionMode.FORK_JOIN, 2)) {
            assertThat(threadNamesUsedBy(strategy.loop())).allMatch(name -> name.startsWith("ct-fj-"));
        }
    }

    @ParameterizedTest
    @EnumSource(ExecutionMode.class)
    void everyModeCoversTheRangeAndReleasesItsThreadsOnClose(ExecutionMode mode) {
        try (ExecutionStrategy strategy = ExecutionStrategies.create(mode, 3)) {
            LoopAssertions.assertVisitsEveryIndexExactlyOnce(strategy.loop(), 0, 500);
        }

        assertThat(liveEngineThreads()).isEmpty();
    }

    @Test
    void rejectsNonPositiveThreadCount() {
        assertThatThrownBy(() -> ExecutionStrategies.create(ExecutionMode.FORK_JOIN, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Set<String> threadNamesUsedBy(ParallelLoop loop) {
        Set<String> names = ConcurrentHashMap.newKeySet();
        loop.forRange(0, 64, (from, to) -> names.add(Thread.currentThread().getName()));
        return names;
    }

    private static List<String> liveEngineThreads() {
        List<Thread> engineThreads = Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().startsWith("ct-block-") || thread.getName().startsWith("ct-fj-"))
                .toList();
        for (Thread thread : engineThreads) {
            try {
                thread.join(Duration.ofSeconds(5));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return engineThreads.stream().filter(Thread::isAlive).map(Thread::getName).toList();
    }
}
