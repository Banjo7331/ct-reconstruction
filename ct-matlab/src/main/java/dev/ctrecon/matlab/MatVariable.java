package dev.ctrecon.matlab;

public sealed interface MatVariable permits MatNumericArray, MatSparseMatrix {

    String name();

    int rows();

    int columns();
}
