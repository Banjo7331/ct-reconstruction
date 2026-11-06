package dev.ctrecon.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "ct")
public record CtProperties(Path dataDirectory, Jobs jobs, Storage storage) {

    public CtProperties {
        Objects.requireNonNull(dataDirectory, "ct.data-directory must be configured");
        Objects.requireNonNull(jobs, "ct.jobs must be configured");
        Objects.requireNonNull(storage, "ct.storage must be configured");
    }

    public record Jobs(int workers, int queueCapacity, Duration shutdownTimeout) {

        public Jobs {
            if (workers <= 0) {
                throw new IllegalArgumentException("ct.jobs.workers must be positive, got " + workers);
            }
            if (queueCapacity <= 0) {
                throw new IllegalArgumentException("ct.jobs.queue-capacity must be positive, got " + queueCapacity);
            }
            Objects.requireNonNull(shutdownTimeout, "ct.jobs.shutdown-timeout must be configured");
            if (shutdownTimeout.isNegative()) {
                throw new IllegalArgumentException("ct.jobs.shutdown-timeout must not be negative");
            }
        }
    }

    public record Storage(int maxImages, int cachedSystems) {

        public Storage {
            if (maxImages <= 0) {
                throw new IllegalArgumentException("ct.storage.max-images must be positive, got " + maxImages);
            }
            if (cachedSystems <= 0) {
                throw new IllegalArgumentException("ct.storage.cached-systems must be positive, got " + cachedSystems);
            }
        }
    }
}
