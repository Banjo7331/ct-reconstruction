package dev.ctrecon.engine.linalg;

import dev.ctrecon.engine.parallel.ExecutionMode;
import dev.ctrecon.engine.parallel.ExecutionStrategies;
import dev.ctrecon.engine.parallel.ExecutionStrategy;
import dev.ctrecon.engine.parallel.SequentialLoop;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class VectorOperationsTest {

    private final VectorOperations sequential = new VectorOperations(SequentialLoop.INSTANCE);

    @Test
    void computesDotProductNormAndDistance() {
        double[] a = {1, 2, 3};
        double[] b = {4, -5, 6};

        assertThat(sequential.dot(a, b)).isEqualTo(12.0);
        assertThat(sequential.norm(new double[]{3, 4})).isEqualTo(5.0);
        assertThat(sequential.distance(a, b)).isCloseTo(Math.sqrt(9 + 49 + 9), within(1e-12));
    }

    @Test
    void updatesVectorsInPlace() {
        double[] target = {1, 1, 1};

        sequential.addScaled(target, 2.0, new double[]{1, 2, 3});
        assertThat(target).containsExactly(3, 5, 7);

        sequential.scaleAndAdd(target, 0.5, new double[]{1, 1, 1});
        assertThat(target).containsExactly(2.5, 3.5, 4.5);
    }

    @ParameterizedTest
    @EnumSource(value = ExecutionMode.class, names = {"STATIC_BLOCKS", "FORK_JOIN"})
    void parallelReductionsAreBitIdenticalToTheSequentialOnes(ExecutionMode mode) {
        double[] a = randomVector(100_003, 1);
        double[] b = randomVector(100_003, 2);

        try (ExecutionStrategy strategy = ExecutionStrategies.create(mode, 3)) {
            VectorOperations parallel = new VectorOperations(strategy.loop());

            assertThat(Double.doubleToLongBits(parallel.dot(a, b)))
                    .isEqualTo(Double.doubleToLongBits(sequential.dot(a, b)));
            assertThat(Double.doubleToLongBits(parallel.distance(a, b)))
                    .isEqualTo(Double.doubleToLongBits(sequential.distance(a, b)));
        }
    }

    @Test
    void theOrderOfFloatingPointAdditionChangesTheResult() {
        double[] values = randomVector(100_003, 3);
        double forward = 0.0;
        for (double value : values) {
            forward += value;
        }
        double backward = 0.0;
        for (int i = values.length - 1; i >= 0; i--) {
            backward += values[i];
        }

        assertThat(forward).isNotEqualTo(backward);
    }

    @Test
    void rejectsVectorsOfDifferentLength() {
        assertThatThrownBy(() -> sequential.dot(new double[2], new double[3]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static double[] randomVector(int length, long seed) {
        Random random = new Random(seed);
        double[] vector = new double[length];
        for (int i = 0; i < length; i++) {
            vector[i] = (random.nextDouble() - 0.5) * Math.pow(10, random.nextInt(8));
        }
        return vector;
    }
}
