package dev.ctrecon.service.adapter.in.web;

import dev.ctrecon.core.domain.ImageData;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

final class ImageResponses {

    private static final PngEncoder ENCODER = new PngEncoder();

    private ImageResponses() {
    }

    static ResponseEntity<byte[]> png(ImageData image, Double windowMin, Double windowMax) {
        byte[] png = ENCODER.encode(image, DisplayWindow.of(image, windowMin, windowMax));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                .body(png);
    }
}
