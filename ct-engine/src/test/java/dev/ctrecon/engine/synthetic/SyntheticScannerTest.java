package dev.ctrecon.engine.synthetic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

class SyntheticScannerTest {

    @Test
    void producesOneRowPerRayAndOneColumnPerPixel() {
        SyntheticScan scan = SyntheticScanner.parallelBeam(32, 20);

        assertThat(scan.detectorCount() % 2).isEqualTo(1);
        assertThat(scan.systemMatrix().rows()).isEqualTo(scan.detectorCount() * 20);
        assertThat(scan.systemMatrix().columns()).isEqualTo(32 * 32);
        assertThat(scan.measurements()).hasSize(scan.systemMatrix().rows());
    }

    @Test
    void everyProjectionCarriesTheTotalMassOfThePhantom() {
        int size = 64;
        SyntheticScan scan = SyntheticScanner.parallelBeam(size, 12);
        double pixel = 2.0 / size;
        double expectedMass = Math.PI * (0.8 * 0.8 - 0.5 * 0.2 * 0.2 + 0.8 * 0.12 * 0.12);

        for (int angle = 0; angle < scan.angleCount(); angle++) {
            double mass = 0.0;
            for (int detector = 0; detector < scan.detectorCount(); detector++) {
                mass += scan.measurements()[detector + angle * scan.detectorCount()] * pixel;
            }
            assertThat(mass).as("angle %d", angle).isCloseTo(expectedMass, withinPercentage(2));
        }
    }

    @Test
    void storesThePhantomInColumnMajorOrderLikeMatlab() {
        int size = 16;
        double[] phantom = SyntheticScanner.phantom(size);

        assertThat(phantom[0]).isZero();
        assertThat(phantom[size / 2 + (size / 2) * size]).isEqualTo(1.0);
    }
}
