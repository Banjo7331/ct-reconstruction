package dev.ctrecon.service.adapter.in.web;

import dev.ctrecon.core.domain.ExecutionMode;
import dev.ctrecon.core.domain.InvalidReconstructionParametersException;
import dev.ctrecon.core.domain.ReconstructionParameters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateReconstructionRequestTest {

    @Test
    void defaultsToTheSettingsOfTheDatasetAuthorsExample() {
        ReconstructionParameters parameters =
                new CreateReconstructionRequest("walnut/Data164.mat", null, null, null, null).toParameters();

        assertThat(parameters.iterations()).isEqualTo(20);
        assertThat(parameters.regularization()).isEqualTo(10.0);
        assertThat(parameters.mode()).isEqualTo(ExecutionMode.FORK_JOIN);
        assertThat(parameters.threads()).isEqualTo(CreateReconstructionRequest.defaultThreads());
    }

    @Test
    void acceptsTheModeInAnyCase() {
        ReconstructionParameters parameters =
                new CreateReconstructionRequest("a.mat", 5, 1.0, " static_blocks ", 2).toParameters();

        assertThat(parameters.mode()).isEqualTo(ExecutionMode.STATIC_BLOCKS);
    }

    @Test
    void reportsAMissingDatasetAsAParameterViolation() {
        assertThatThrownBy(() -> new CreateReconstructionRequest(null, 5, 1.0, null, 2).toParameters())
                .isInstanceOf(InvalidReconstructionParametersException.class)
                .hasMessageContaining("datasetId is required");
    }

    @Test
    void reportsAPathTraversalAttemptAsAParameterViolation() {
        assertThatThrownBy(() -> new CreateReconstructionRequest("../../etc/passwd.mat", 5, 1.0, null, 2).toParameters())
                .isInstanceOf(InvalidReconstructionParametersException.class)
                .hasMessageContaining("..");
    }

    @Test
    void reportsAnUnknownMode() {
        assertThatThrownBy(() -> new CreateReconstructionRequest("a.mat", 5, 1.0, "gpu", 2).toParameters())
                .isInstanceOf(InvalidReconstructionParametersException.class)
                .hasMessageContaining("mode must be one of");
    }
}
