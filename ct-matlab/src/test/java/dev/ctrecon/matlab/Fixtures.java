package dev.ctrecon.matlab;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

final class Fixtures {

    private Fixtures() {
    }

    static Path fixture(String name) {
        URL resource = Fixtures.class.getResource("/fixtures/" + name);
        if (resource == null) {
            throw new IllegalArgumentException("Missing fixture " + name);
        }
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException invalid) {
            throw new IllegalStateException(invalid);
        }
    }
}
