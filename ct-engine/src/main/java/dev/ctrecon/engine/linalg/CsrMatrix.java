package dev.ctrecon.engine.linalg;

import dev.ctrecon.engine.parallel.ParallelLoop;

import java.util.Objects;

public final class CsrMatrix {

    private final int rows;
    private final int columns;
    private final int[] rowPointers;
    private final int[] columnIndices;
    private final double[] values;

    private CsrMatrix(int rows, int columns, int[] rowPointers, int[] columnIndices, double[] values) {
        this.rows = rows;
        this.columns = columns;
        this.rowPointers = rowPointers;
        this.columnIndices = columnIndices;
        this.values = values;
    }

    public static CsrMatrix fromArrays(int rows, int columns, int[] rowPointers, int[] columnIndices, double[] values) {
        if (rows < 0 || columns < 0) {
            throw new IllegalArgumentException("Dimensions must not be negative, got " + rows + "x" + columns);
        }
        if (rowPointers.length != rows + 1 || rowPointers[0] != 0) {
            throw new IllegalArgumentException("Expected " + (rows + 1) + " row pointers starting at 0");
        }
        int nonZeros = rowPointers[rows];
        if (columnIndices.length != nonZeros || values.length != nonZeros) {
            throw new IllegalArgumentException("Row pointers declare " + nonZeros + " non-zeros, but arrays hold "
                    + columnIndices.length + " indices and " + values.length + " values");
        }
        for (int row = 0; row < rows; row++) {
            if (rowPointers[row] > rowPointers[row + 1]) {
                throw new IllegalArgumentException("Row pointers decrease at row " + row);
            }
        }
        for (int column : columnIndices) {
            if (column < 0 || column >= columns) {
                throw new IllegalArgumentException("Column index " + column + " outside [0, " + columns + ")");
            }
        }
        return new CsrMatrix(rows, columns, rowPointers, columnIndices, values);
    }

    public int rows() {
        return rows;
    }

    public int columns() {
        return columns;
    }

    public int nonZeroCount() {
        return values.length;
    }

    public int rowNonZeros(int row) {
        Objects.checkIndex(row, rows);
        return rowPointers[row + 1] - rowPointers[row];
    }

    public double get(int row, int column) {
        Objects.checkIndex(row, rows);
        Objects.checkIndex(column, columns);
        for (int entry = rowPointers[row]; entry < rowPointers[row + 1]; entry++) {
            if (columnIndices[entry] == column) {
                return values[entry];
            }
        }
        return 0.0;
    }

    public void multiply(double[] x, double[] result, ParallelLoop loop) {
        if (x.length != columns || result.length != rows) {
            throw new IllegalArgumentException("Cannot multiply " + rows + "x" + columns + " by vector of "
                    + x.length + " into vector of " + result.length);
        }
        loop.forRange(0, rows, (from, to) -> {
            for (int row = from; row < to; row++) {
                double sum = 0.0;
                for (int entry = rowPointers[row]; entry < rowPointers[row + 1]; entry++) {
                    sum += values[entry] * x[columnIndices[entry]];
                }
                result[row] = sum;
            }
        });
    }

    public CsrMatrix transpose() {
        int[] transposedPointers = new int[columns + 1];
        for (int column : columnIndices) {
            transposedPointers[column + 1]++;
        }
        for (int column = 0; column < columns; column++) {
            transposedPointers[column + 1] += transposedPointers[column];
        }
        int[] next = transposedPointers.clone();
        int[] transposedIndices = new int[values.length];
        double[] transposedValues = new double[values.length];
        for (int row = 0; row < rows; row++) {
            for (int entry = rowPointers[row]; entry < rowPointers[row + 1]; entry++) {
                int target = next[columnIndices[entry]]++;
                transposedIndices[target] = row;
                transposedValues[target] = values[entry];
            }
        }
        return new CsrMatrix(columns, rows, transposedPointers, transposedIndices, transposedValues);
    }
}
