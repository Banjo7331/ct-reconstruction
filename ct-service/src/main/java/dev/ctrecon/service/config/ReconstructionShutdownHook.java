package dev.ctrecon.service.config;

import dev.ctrecon.core.application.ReconstructionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

public final class ReconstructionShutdownHook {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReconstructionShutdownHook.class);

    private final ReconstructionService reconstructionService;

    public ReconstructionShutdownHook(ReconstructionService reconstructionService) {
        this.reconstructionService = reconstructionService;
    }

    @EventListener(ContextClosedEvent.class)
    public void cancelActiveReconstructions() {
        LOGGER.info("Application is stopping, cancelling reconstructions that are still queued or running");
        reconstructionService.cancelActiveReconstructions();
    }
}
