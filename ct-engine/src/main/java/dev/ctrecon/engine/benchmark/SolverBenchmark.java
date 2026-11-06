package dev.ctrecon.engine.benchmark;

import dev.ctrecon.engine.parallel.CancellationToken;
import dev.ctrecon.engine.parallel.ExecutionMode;
import dev.ctrecon.engine.solver.IterationListener;
import dev.ctrecon.engine.solver.ReconstructionRequest;
import dev.ctrecon.engine.solver.Reconstructor;
import dev.ctrecon.engine.solver.SolverSettings;
import dev.ctrecon.engine.solver.TikhonovProblem;
import dev.ctrecon.engine.synthetic.SyntheticScan;
import dev.ctrecon.engine.synthetic.SyntheticScanner;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class SolverBenchmark {

    private static final int WARM_UP_ROUNDS = 3;
    private static final int MEASURED_ROUNDS = 3;
    private static final double REGULARIZATION = 10.0;

    private final Reconstructor reconstructor = new Reconstructor();
    private final PrintStream out;

    private SolverBenchmark(PrintStream out) {
        this.out = out;
    }

    public static void main(String[] args) {
        int imageSize = argument(args, 0, 192);
        int angleCount = argument(args, 1, 180);
        int iterations = argument(args, 2, 30);
        new SolverBenchmark(System.out).run(imageSize, angleCount, iterations);
    }

    private void run(int imageSize, int angleCount, int iterations) {
        int cores = Runtime.getRuntime().availableProcessors();
        SyntheticScan scan = SyntheticScanner.parallelBeam(imageSize, angleCount);
        TikhonovProblem problem = TikhonovProblem.of(scan.systemMatrix(), scan.measurements(), REGULARIZATION);
        out.printf(Locale.ROOT, "System %d x %d with %d non-zeros, %d iterations, %d available processors%n",
                scan.systemMatrix().rows(), scan.systemMatrix().columns(), scan.systemMatrix().nonZeroCount(),
                iterations, cores);
        SolverSettings settings = new SolverSettings(iterations, 0.0);
        warmUp(problem);

        long baseline = medianMillis(new ReconstructionRequest(problem, settings, ExecutionMode.SEQUENTIAL, 1));
        out.printf(Locale.ROOT, "%-14s %7s %10s %8s%n", "mode", "threads", "time [ms]", "speedup");
        out.printf(Locale.ROOT, "%-14s %7d %10d %8.2f%n", ExecutionMode.SEQUENTIAL, 1, baseline, 1.0);
        for (ExecutionMode mode : List.of(ExecutionMode.STATIC_BLOCKS, ExecutionMode.FORK_JOIN)) {
            for (int threads : threadCounts(cores)) {
                long millis = medianMillis(new ReconstructionRequest(problem, settings, mode, threads));
                out.printf(Locale.ROOT, "%-14s %7d %10d %8.2f%n", mode, threads, millis, (double) baseline / millis);
            }
        }
    }

    private void warmUp(TikhonovProblem problem) {
        SolverSettings settings = new SolverSettings(5, 0.0);
        for (int round = 0; round < WARM_UP_ROUNDS; round++) {
            for (ExecutionMode mode : ExecutionMode.values()) {
                measureMillis(new ReconstructionRequest(problem, settings, mode, 2));
            }
        }
    }

    private long medianMillis(ReconstructionRequest request) {
        long[] samples = new long[MEASURED_ROUNDS];
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            samples[round] = measureMillis(request);
        }
        Arrays.sort(samples);
        return samples[MEASURED_ROUNDS / 2];
    }

    private long measureMillis(ReconstructionRequest request) {
        long start = System.nanoTime();
        reconstructor.reconstruct(request, IterationListener.NONE, CancellationToken.NONE);
        return Math.max(1, (System.nanoTime() - start) / 1_000_000);
    }

    private static List<Integer> threadCounts(int cores) {
        List<Integer> counts = new ArrayList<>();
        for (int threads = 1; threads < cores; threads *= 2) {
            counts.add(threads);
        }
        counts.add(cores);
        return counts;
    }

    private static int argument(String[] args, int index, int defaultValue) {
        return args.length > index ? Integer.parseInt(args[index]) : defaultValue;
    }
}
