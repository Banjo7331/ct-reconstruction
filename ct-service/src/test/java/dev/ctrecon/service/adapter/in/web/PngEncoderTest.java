package dev.ctrecon.service.adapter.in.web;

import dev.ctrecon.core.domain.ImageData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PngEncoderTest {

    @Test
    void producesAGrayscalePngOfTheImageSize() throws IOException {
        ImageData image = new ImageData(3, 2, new double[]{0.0, 0.25, 0.5, 1.0, -1.0, 0.125});

        byte[] png = new PngEncoder().encode(image, new DisplayWindow(0.0, 0.5));
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(png));

        assertThat(decoded.getWidth()).isEqualTo(3);
        assertThat(decoded.getHeight()).isEqualTo(2);
        assertThat(decoded.getRaster().getSample(1, 0, 0)).isEqualTo(128);
        assertThat(decoded.getRaster().getSample(0, 1, 0)).isEqualTo(255);
        assertThat(decoded.getRaster().getSample(1, 1, 0)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"-0.1, 0", "0.0, 0", "0.2, 128", "0.4, 255", "0.9, 255"})
    void windowMapsItsRangeOntoTheGrayScaleAndClampsOutside(double value, int gray) {
        assertThat(new DisplayWindow(0.0, 0.4).toGray(value)).isEqualTo(gray);
    }

    @Test
    void automaticWindowIgnoresTheMostExtremeOnePercentOnEachSide() {
        double[] values = new double[1001];
        for (int i = 0; i < values.length; i++) {
            values[i] = i;
        }
        values[0] = -1e9;
        values[1000] = 1e9;

        DisplayWindow window = DisplayWindow.of(new ImageData(1001, 1, values), null, null);

        assertThat(window.lower()).isEqualTo(10.0);
        assertThat(window.upper()).isEqualTo(990.0);
    }

    @Test
    void automaticWindowSurvivesAConstantImage() {
        DisplayWindow window = DisplayWindow.of(new ImageData(2, 1, new double[]{3.0, 3.0}), null, null);

        assertThat(window.upper()).isGreaterThan(window.lower());
    }

    @Test
    void explicitBoundsOverrideTheAutomaticOnes() {
        DisplayWindow window = DisplayWindow.of(new ImageData(1, 1, new double[]{5.0}), 0.0, 1.0);

        assertThat(window).isEqualTo(new DisplayWindow(0.0, 1.0));
    }

    @Test
    void windowNeedsAnIncreasingRange() {
        assertThatThrownBy(() -> new DisplayWindow(0.5, 0.5)).isInstanceOf(IllegalArgumentException.class);
    }
}
