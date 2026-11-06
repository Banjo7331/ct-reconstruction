package dev.ctrecon.matlab;

public sealed interface MatVariable permits MatNumericArray {

    String name();

    int rows();

    int columns();
}
