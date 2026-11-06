package dev.ctrecon.matlab;

import java.util.Arrays;
import java.util.Objects;

public final class MatSparseMatrix implements MatVariable {

    private final String name;
    private final int rows;
    private final int columns;
    private final int[] rowIndices;
    private final int[] columnPointers;
    private final double[] values;

    MatSparseMatrix(String name, int rows, int columns, int[] rowIndices, int[] columnPointers, double[] values)
            throws MatFileFormatException {
        if (columnPointers.length != columns + 1 || columnPointers[0] != 0) {
            throw new MatFileFormatException("Sparse matrix " + name + " has malformed column pointers");
        }
        int nonZeros = columnPointers[columns];
        if (nonZeros > rowIndices.length || nonZeros > values.length) {
            throw new MatFileFormatException("Sparse matrix " + name + " declares " + nonZeros
                    + " non-zeros but stores fewer");
        }
        for (int column = 0; column < columns; column++) {
            if (columnPointers[column] > columnPointers[column + 1]) {
                throw new MatFileFormatException("Sparse matrix " + name + " has decreasing column pointers");
            }
        }
        for (int k = 0; k < nonZeros; k++) {
            if (rowIndices[k] < 0 || rowIndices[k] >= rows) {
                throw new MatFileFormatException("Sparse matrix " + name + " has row index " + rowIndices[k]
                        + " outside [0, " + rows + ")");
            }
        }
        this.name = name;
        this.rows = rows;
        this.columns = columns;
        this.rowIndices = rowIndices.length == nonZeros ? rowIndices : Arrays.copyOf(rowIndices, nonZeros);
        this.columnPointers = columnPointers;
        this.values = values.length == nonZeros ? values : Arrays.copyOf(values, nonZeros);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int rows() {
        return rows;
    }

    @Override
    public int columns() {
        return columns;
    }

    public int nonZeroCount() {
        return values.length;
    }

    public int columnStart(int column) {
        return columnPointers[Objects.checkIndex(column, columns)];
    }

    public int columnEnd(int column) {
        return columnPointers[Objects.checkIndex(column, columns) + 1];
    }

    public int rowIndex(int entry) {
        return rowIndices[entry];
    }

    public double value(int entry) {
        return values[entry];
    }

    public double get(int row, int column) {
        Objects.checkIndex(row, rows);
        for (int entry = columnStart(column); entry < columnEnd(column); entry++) {
            if (rowIndices[entry] == row) {
                return values[entry];
            }
        }
        return 0.0;
    }
}
