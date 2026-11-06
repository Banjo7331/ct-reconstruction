package dev.ctrecon.matlab;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static dev.ctrecon.matlab.Fixtures.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatSparseMatrixTest {

    private static final double[][] EXPECTED = {
            {1.5, 0.0, 0.0, 2.0},
            {0.0, 0.0, 3.25, 0.0},
            {0.0, 4.0, 0.0, 0.0},
            {0.5, 0.0, 0.0, 0.0},
            {0.0, 0.0, 0.0, 0.0},
            {0.0, 6.0, 7.0, 8.0}
    };

    @ParameterizedTest
    @ValueSource(strings = {"system-compressed.mat", "system-uncompressed.mat"})
    void readsTheCompressedColumnStructureWrittenByScipy(String file) throws IOException {
        MatSparseMatrix matrix = MatFileReader.readAll(fixture(file)).sparse("A").orElseThrow();

        assertThat(matrix.rows()).isEqualTo(6);
        assertThat(matrix.columns()).isEqualTo(4);
        assertThat(matrix.nonZeroCount()).isEqualTo(8);
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 4; column++) {
                assertThat(matrix.get(row, column)).as("A(%d,%d)", row, column).isEqualTo(EXPECTED[row][column]);
            }
        }
    }

    @Test
    void exposesEntriesColumnByColumn() throws IOException {
        MatSparseMatrix matrix = MatFileReader.readAll(fixture("system-compressed.mat")).sparse("A").orElseThrow();

        assertThat(matrix.columnStart(1)).isEqualTo(2);
        assertThat(matrix.columnEnd(1)).isEqualTo(4);
        assertThat(matrix.rowIndex(2)).isEqualTo(2);
        assertThat(matrix.value(2)).isEqualTo(4.0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"handwritten-little-endian.mat", "handwritten-big-endian.mat"})
    void readsSparseMatricesInBothByteOrders(String file) throws IOException {
        MatSparseMatrix matrix = MatFileReader.readAll(fixture(file)).sparse("A").orElseThrow();

        assertThat(matrix.get(0, 0)).isEqualTo(0.25);
        assertThat(matrix.get(2, 0)).isEqualTo(-1.0);
        assertThat(matrix.get(1, 1)).isEqualTo(9.5);
        assertThat(matrix.get(0, 1)).isZero();
    }

    @Test
    void rejectsRowIndicesOutsideTheMatrix() {
        assertThatThrownBy(() -> new MatSparseMatrix("bad", 2, 1, new int[]{2}, new int[]{0, 1}, new double[]{1.0}))
                .isInstanceOf(MatFileFormatException.class)
                .hasMessageContaining("row index 2");
    }

    @Test
    void rejectsMalformedColumnPointers() {
        assertThatThrownBy(() -> new MatSparseMatrix("bad", 2, 2, new int[]{0, 1}, new int[]{0, 2, 1}, new double[]{1, 2}))
                .isInstanceOf(MatFileFormatException.class)
                .hasMessageContaining("decreasing");
    }
}
