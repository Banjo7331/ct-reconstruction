package dev.ctrecon.engine.synthetic;

import dev.ctrecon.engine.linalg.CsrMatrix;

public record SyntheticScan(int imageSize, int detectorCount, int angleCount,
                            CsrMatrix systemMatrix, double[] phantom, double[] measurements) {
}
