package dev.ctrecon.core.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageDataTest {

    @Test
    void readsValuesRowByRow() {
        ImageData image = new ImageData(3, 2, new double[]{1, 2, 3, 4, 5, 6});

        assertThat(image.valueAt(2, 0)).isEqualTo(3);
        assertThat(image.valueAt(0, 1)).isEqualTo(4);
    }

    @Test
    void computesPercentilesForAutomaticDisplayWindows() {
        double[] values = new double[101];
        for (int i = 0; i <= 100; i++) {
            values[i] = 100 - i;
        }
        ImageData image = new ImageData(101, 1, values);

        assertThat(image.percentile(0.0)).isEqualTo(0);
        assertThat(image.percentile(0.01)).isEqualTo(1);
        assertThat(image.percentile(0.99)).isEqualTo(99);
        assertThat(image.percentile(1.0)).isEqualTo(100);
    }

    @Test
    void copiesItsInputAndValidatesItsSize() {
        double[] values = {1, 2};
        ImageData image = new ImageData(2, 1, values);
        values[0] = 99;

        assertThat(image.valueAt(0, 0)).isEqualTo(1);
        assertThatThrownBy(() -> new ImageData(2, 2, values)).isInstanceOf(IllegalArgumentException.class);
    }
}
