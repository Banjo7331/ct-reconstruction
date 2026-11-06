package dev.ctrecon.engine.solver;

import dev.ctrecon.engine.linalg.CsrMatrix;

public record TikhonovProblem(CsrMatrix systemMatrix, CsrMatrix transposedSystemMatrix,
                              double[] measurements, double regularization) {

    public TikhonovProblem {
        if (transposedSystemMatrix.rows() != systemMatrix.columns()
                || transposedSystemMatrix.columns() != systemMatrix.rows()) {
            throw new IllegalArgumentException("The transposed matrix does not match the system matrix");
        }
        if (measurements.length != systemMatrix.rows()) {
            throw new IllegalArgumentException("Expected " + systemMatrix.rows() + " measurements, got "
                    + measurements.length);
        }
        if (!Double.isFinite(regularization) || regularization < 0.0) {
            throw new IllegalArgumentException("Regularization must be finite and non-negative, got " + regularization);
        }
    }

    public static TikhonovProblem of(CsrMatrix systemMatrix, double[] measurements, double regularization) {
        return new TikhonovProblem(systemMatrix, systemMatrix.transpose(), measurements, regularization);
    }

    public int unknowns() {
        return systemMatrix.columns();
    }
}
