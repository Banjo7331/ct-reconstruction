package dev.ctrecon.matlab;

import java.util.List;

public record MatVariableInfo(String name, MatClass matClass, List<Integer> dimensions, boolean complex) {

    public MatVariableInfo {
        dimensions = List.copyOf(dimensions);
    }

    public int rows() {
        return dimensions.getFirst();
    }

    public int columns() {
        int columns = 1;
        for (int axis = 1; axis < dimensions.size(); axis++) {
            columns = Math.multiplyExact(columns, dimensions.get(axis));
        }
        return columns;
    }

    public boolean isMatrix() {
        return dimensions.size() == 2;
    }
}
