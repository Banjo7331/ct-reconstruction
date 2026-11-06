package dev.ctrecon.service.adapter.in.web;

import dev.ctrecon.core.domain.ImageData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

public final class PngEncoder {

    public byte[] encode(ImageData image, DisplayWindow window) {
        BufferedImage gray = new BufferedImage(image.width(), image.height(), BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster raster = gray.getRaster();
        for (int row = 0; row < image.height(); row++) {
            for (int column = 0; column < image.width(); column++) {
                raster.setSample(column, row, 0, window.toGray(image.valueAt(column, row)));
            }
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(gray, "png", png)) {
                throw new IllegalStateException("No PNG writer is available in this JVM");
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not encode image as PNG", failure);
        }
        return png.toByteArray();
    }
}
