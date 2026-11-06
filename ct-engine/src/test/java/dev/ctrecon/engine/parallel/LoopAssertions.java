package dev.ctrecon.engine.parallel;

import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.assertj.core.api.Assertions.assertThat;

final class LoopAssertions {

    private LoopAssertions() {
    }

    static void assertVisitsEveryIndexExactlyOnce(ParallelLoop loop, int from, int to) {
        AtomicIntegerArray visits = new AtomicIntegerArray(to);

        loop.forRange(from, to, (start, end) -> {
            for (int index = start; index < end; index++) {
                visits.incrementAndGet(index);
            }
        });

        for (int index = 0; index < to; index++) {
            int expected = index >= from ? 1 : 0;
            assertThat(visits.get(index)).as("visits of index %d", index).isEqualTo(expected);
        }
    }
}
