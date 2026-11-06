package dev.ctrecon.core.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconstructionIdTest {

    @Test
    void roundTripsThroughItsTextForm() {
        ReconstructionId id = ReconstructionId.random();

        assertThat(ReconstructionId.parse(id.toString())).isEqualTo(id);
    }

    @Test
    void rejectsMalformedText() {
        assertThatThrownBy(() -> ReconstructionId.parse("123")).isInstanceOf(IllegalArgumentException.class);
    }
}
