package dev.ctrecon.matlab;

import java.util.Objects;

public final class MatNumericArray implements MatVariable {

    private final String name;
    private final int rows;
    private final int columns;
    private final double[] columnMajor;

    MatNumericArray(String name, int rows, int columns, double[] columnMajor) throws MatFileFormatException {
        if ((long) rows * columns != columnMajor.length) {
            throw new MatFileFormatException("Variable " + name + " declares " + rows + "x" + columns
                    + " but holds " + columnMajor.length + " values");
        }
        this.name = name;
        this.rows = rows;
        this.columns = columns;
        this.columnMajor = columnMajor;
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

    public double get(int row, int column) {
        Objects.checkIndex(row, rows);
        Objects.checkIndex(column, columns);
        return columnMajor[column * rows + row];
    }

    public double[] toColumnMajorArray() {
        return columnMajor.clone();
    }
}
