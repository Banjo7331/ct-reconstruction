package dev.ctrecon.core.domain;

import java.util.ArrayList;
import java.util.List;

public record ReconstructionParameters(DatasetId datasetId, int iterations, double regularization,
                                       ExecutionMode mode, int threads) {

    public static final int MAX_ITERATIONS = 1000;
    public static final double MAX_REGULARIZATION = 1e6;
    public static final int MAX_THREADS = 64;

    public ReconstructionParameters {
        List<String> violations = new ArrayList<>();
        if (datasetId == null) {
            violations.add("datasetId is required");
        }
        if (iterations < 1 || iterations > MAX_ITERATIONS) {
            violations.add("iterations must be between 1 and " + MAX_ITERATIONS + ", got " + iterations);
        }
        if (!(regularization >= 0.0 && regularization <= MAX_REGULARIZATION)) {
            violations.add("regularization must be between 0 and " + MAX_REGULARIZATION + ", got " + regularization);
        }
        if (mode == null) {
            violations.add("mode is required");
        }
        if (threads < 1 || threads > MAX_THREADS) {
            violations.add("threads must be between 1 and " + MAX_THREADS + ", got " + threads);
        }
        if (!violations.isEmpty()) {
            throw new InvalidReconstructionParametersException(violations);
        }
    }
}
