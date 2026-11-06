package dev.ctrecon.engine.linalg;

import dev.ctrecon.engine.parallel.ExecutionMode;
import dev.ctrecon.engine.parallel.ExecutionStrategies;
import dev.ctrecon.engine.parallel.ExecutionStrategy;
import dev.ctrecon.engine.parallel.SequentialLoop;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class CsrMatrixTest {

    private final CsrMatrix matrix = CsrMatrix.fromArrays(3, 4,
            new int[]{0, 2, 2, 5},
            new int[]{0, 3, 1, 2, 3},
            new double[]{1.0, 2.0, -1.0, 4.0, 0.5});

    @Test
    void multipliesByAVector() {
        double[] result = new double[3];

        matrix.multiply(new double[]{1, 2, 3, 4}, result, SequentialLoop.INSTANCE);

        assertThat(result).containsExactly(9.0, 0.0, 12.0);
    }

    @Test
    void transposesWithoutLosingAnEntry() {
        CsrMatrix transposed = matrix.transpose();

        assertThat(transposed.rows()).isEqualTo(4);
        assertThat(transposed.columns()).isEqualTo(3);
        assertThat(transposed.nonZeroCount()).isEqualTo(5);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 4; column++) {
                assertThat(transposed.get(column, row)).isEqualTo(matrix.get(row, column));
            }
        }
        assertThat(transposed.rowNonZeros(3)).isEqualTo(2);
    }

    @Test
    void matchesADenseReferenceOnARandomMatrix() {
        Random random = new Random(7);
        double[][] dense = new double[40][30];
        CsrMatrix sparse = randomSparse(dense, random);
        double[] x = new double[30];
        for (int i = 0; i < x.length; i++) {
            x[i] = random.nextGaussian();
        }

        double[] result = new double[40];
        sparse.multiply(x, result, SequentialLoop.INSTANCE);

        for (int row = 0; row < 40; row++) {
            double expected = 0.0;
            for (int column = 0; column < 30; column++) {
                expected += dense[row][column] * x[column];
            }
            assertThat(result[row]).isCloseTo(expected, within(1e-12));
        }
    }

    @ParameterizedTest
    @EnumSource(ExecutionMode.class)
    void everyExecutionModeGivesTheSameBits(ExecutionMode mode) {
        Random random = new Random(11);
        CsrMatrix sparse = randomSparse(new double[2_000][500], random);
        double[] x = new double[500];
        for (int i = 0; i < x.length; i++) {
            x[i] = random.nextGaussian();
        }
        double[] expected = new double[2_000];
        sparse.multiply(x, expected, SequentialLoop.INSTANCE);

        double[] actual = new double[2_000];
        try (ExecutionStrategy strategy = ExecutionStrategies.create(mode, 3)) {
            sparse.multiply(x, actual, strategy.loop());
        }

        assertThat(actual).containsExactly(expected);
    }

    @Test
    void rejectsInconsistentArrays() {
        assertThatThrownBy(() -> CsrMatrix.fromArrays(2, 2, new int[]{0, 1, 3}, new int[]{0, 1}, new double[]{1, 2}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CsrMatrix.fromArrays(1, 2, new int[]{0, 1}, new int[]{5}, new double[]{1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Column index 5");
    }

    @Test
    void rejectsVectorsOfTheWrongLength() {
        assertThatThrownBy(() -> matrix.multiply(new double[3], new double[3], SequentialLoop.INSTANCE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CsrMatrix randomSparse(double[][] dense, Random random) {
        int rows = dense.length;
        int columns = dense[0].length;
        int[] pointers = new int[rows + 1];
        List<Integer> indices = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (random.nextDouble() < 0.1) {
                    double value = random.nextGaussian();
                    dense[row][column] = value;
                    indices.add(column);
                    values.add(value);
                }
            }
            pointers[row + 1] = indices.size();
        }
        return CsrMatrix.fromArrays(rows, columns, pointers,
                indices.stream().mapToInt(Integer::intValue).toArray(),
                values.stream().mapToDouble(Double::doubleValue).toArray());
    }
}
