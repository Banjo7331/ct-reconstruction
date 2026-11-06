package dev.ctrecon.core.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ReconstructionParametersTest {

    private static final DatasetId WALNUT = new DatasetId("walnut/Data164.mat");

    @Test
    void acceptsTheSettingsOfTheDatasetAuthorsExample() {
        ReconstructionParameters parameters = new ReconstructionParameters(WALNUT, 20, 10.0, ExecutionMode.FORK_JOIN, 8);

        assertThat(parameters.iterations()).isEqualTo(20);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "iterations,       0,  10.0, 4",
            "iterations,    1001,  10.0, 4",
            "regularization,  20,  -1.0, 4",
            "regularization,  20,   NaN, 4",
            "regularization,  20, 1.0E7, 4",
            "threads,         20,  10.0, 0",
            "threads,         20,  10.0, 65"
    })
    void rejectsValuesOutsideTheirRange(String field, int iterations, double regularization, int threads) {
        assertThatThrownBy(() -> new ReconstructionParameters(WALNUT, iterations, regularization,
                ExecutionMode.SEQUENTIAL, threads))
                .isInstanceOf(InvalidReconstructionParametersException.class)
                .hasMessageStartingWith(field);
    }

    @Test
    void reportsEveryViolationAtOnce() {
        InvalidReconstructionParametersException exception = catchThrowableOfType(
                InvalidReconstructionParametersException.class,
                () -> new ReconstructionParameters(null, 0, -1, null, 0));

        assertThat(exception.violations()).hasSize(5);
    }
}
