package dev.ctrecon.service.adapter.in.web;

import dev.ctrecon.core.domain.DatasetId;
import dev.ctrecon.core.domain.ExecutionMode;
import dev.ctrecon.core.domain.InvalidReconstructionParametersException;
import dev.ctrecon.core.domain.ReconstructionParameters;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public record CreateReconstructionRequest(String datasetId, Integer iterations, Double regularization,
                                          String mode, Integer threads) {

    static final int DEFAULT_ITERATIONS = 20;
    static final double DEFAULT_REGULARIZATION = 10.0;
    static final ExecutionMode DEFAULT_MODE = ExecutionMode.FORK_JOIN;

    public ReconstructionParameters toParameters() {
        return new ReconstructionParameters(
                parseDatasetId(datasetId),
                iterations != null ? iterations : DEFAULT_ITERATIONS,
                regularization != null ? regularization : DEFAULT_REGULARIZATION,
                parseMode(mode),
                threads != null ? threads : defaultThreads());
    }

    static int defaultThreads() {
        return Math.min(Runtime.getRuntime().availableProcessors(), ReconstructionParameters.MAX_THREADS);
    }

    private static DatasetId parseDatasetId(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new DatasetId(value);
        } catch (IllegalArgumentException invalid) {
            throw new InvalidReconstructionParametersException(List.of(invalid.getMessage()));
        }
    }

    private static ExecutionMode parseMode(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_MODE;
        }
        try {
            return ExecutionMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new InvalidReconstructionParametersException(List.of(
                    "mode must be one of " + Arrays.toString(ExecutionMode.values()) + ", got " + value));
        }
    }
}
