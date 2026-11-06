package dev.ctrecon.engine.parallel;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

public final class ForkJoinLoop implements ParallelLoop {

    private static final int CHUNKS_PER_WORKER = 4;

    private final ForkJoinPool pool;

    public ForkJoinLoop(ForkJoinPool pool) {
        this.pool = pool;
    }

    @Override
    public void forRange(int fromInclusive, int toExclusive, RangeTask task) {
        int length = toExclusive - fromInclusive;
        if (length <= 0) {
            return;
        }
        int grain = Math.max(1, length / (pool.getParallelism() * CHUNKS_PER_WORKER));
        pool.invoke(new RangeAction(fromInclusive, toExclusive, grain, task));
    }

    @SuppressWarnings("serial")
    private static final class RangeAction extends RecursiveAction {

        private final int fromInclusive;
        private final int toExclusive;
        private final int grain;
        private final RangeTask task;

        private RangeAction(int fromInclusive, int toExclusive, int grain, RangeTask task) {
            this.fromInclusive = fromInclusive;
            this.toExclusive = toExclusive;
            this.grain = grain;
            this.task = task;
        }

        @Override
        protected void compute() {
            if (toExclusive - fromInclusive <= grain) {
                task.run(fromInclusive, toExclusive);
                return;
            }
            int middle = (fromInclusive + toExclusive) >>> 1;
            invokeAll(
                    new RangeAction(fromInclusive, middle, grain, task),
                    new RangeAction(middle, toExclusive, grain, task));
        }
    }
}
