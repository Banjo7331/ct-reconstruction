package dev.ctrecon.engine.parallel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SequentialLoopTest {

    @Test
    void runsTheWholeRangeAsOneChunkOnTheCallingThread() {
        List<String> calls = new ArrayList<>();

        SequentialLoop.INSTANCE.forRange(3, 10, (from, to) ->
                calls.add(from + ".." + to + "@" + Thread.currentThread().getName()));

        assertThat(calls).containsExactly("3..10@" + Thread.currentThread().getName());
    }

    @Test
    void skipsAnEmptyRange() {
        List<String> calls = new ArrayList<>();

        SequentialLoop.INSTANCE.forRange(5, 5, (from, to) -> calls.add("called"));

        assertThat(calls).isEmpty();
    }

    @Test
    void visitsEveryIndexExactlyOnce() {
        LoopAssertions.assertVisitsEveryIndexExactlyOnce(SequentialLoop.INSTANCE, 2, 50);
    }
}
