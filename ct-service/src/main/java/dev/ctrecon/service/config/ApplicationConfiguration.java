package dev.ctrecon.service.config;

import dev.ctrecon.core.application.DatasetService;
import dev.ctrecon.core.application.ReconstructionService;
import dev.ctrecon.core.application.port.out.ImageStore;
import dev.ctrecon.core.application.port.out.ReconstructionEngine;
import dev.ctrecon.core.application.port.out.ReconstructionRepository;
import dev.ctrecon.core.domain.ReconstructionId;
import dev.ctrecon.service.adapter.out.dataset.FolderDatasetCatalog;
import dev.ctrecon.service.adapter.out.engine.EngineReconstructionAdapter;
import dev.ctrecon.service.adapter.out.execution.BoundedExecutionQueue;
import dev.ctrecon.service.adapter.out.persistence.InMemoryImageStore;
import dev.ctrecon.service.adapter.out.persistence.InMemoryReconstructionRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CtProperties.class)
public class ApplicationConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public FolderDatasetCatalog datasetCatalog(CtProperties properties) {
        return new FolderDatasetCatalog(properties.dataDirectory());
    }

    @Bean
    public ReconstructionRepository reconstructionRepository() {
        return new InMemoryReconstructionRepository();
    }

    @Bean
    public ImageStore imageStore(CtProperties properties) {
        return new InMemoryImageStore(properties.storage().maxImages());
    }

    @Bean
    public ReconstructionEngine reconstructionEngine(FolderDatasetCatalog datasetCatalog, CtProperties properties) {
        return new EngineReconstructionAdapter(datasetCatalog, properties.storage().cachedSystems());
    }

    @Bean
    public BoundedExecutionQueue executionQueue(CtProperties properties) {
        CtProperties.Jobs jobs = properties.jobs();
        return new BoundedExecutionQueue(jobs.workers(), jobs.queueCapacity(), jobs.shutdownTimeout());
    }

    @Bean
    public DatasetService datasetService(FolderDatasetCatalog datasetCatalog) {
        return new DatasetService(datasetCatalog);
    }

    @Bean
    public ReconstructionService reconstructionService(ReconstructionRepository reconstructionRepository,
                                                       ImageStore imageStore,
                                                       FolderDatasetCatalog datasetCatalog,
                                                       ReconstructionEngine reconstructionEngine,
                                                       BoundedExecutionQueue executionQueue,
                                                       Clock clock) {
        return new ReconstructionService(reconstructionRepository, imageStore, datasetCatalog, reconstructionEngine,
                executionQueue, clock, ReconstructionId::random);
    }

    @Bean
    public ReconstructionShutdownHook reconstructionShutdownHook(ReconstructionService reconstructionService) {
        return new ReconstructionShutdownHook(reconstructionService);
    }
}
