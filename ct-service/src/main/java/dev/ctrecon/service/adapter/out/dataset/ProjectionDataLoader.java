package dev.ctrecon.service.adapter.out.dataset;

import dev.ctrecon.core.domain.DatasetId;

@FunctionalInterface
public interface ProjectionDataLoader {

    ProjectionData load(DatasetId id);
}
