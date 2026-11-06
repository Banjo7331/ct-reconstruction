package dev.ctrecon.service.adapter.out.engine;

import dev.ctrecon.engine.linalg.CsrMatrix;

record PreparedSystem(int imageSize, CsrMatrix systemMatrix, CsrMatrix transposedSystemMatrix,
                      double[] measurements) {
}
