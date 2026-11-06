package dev.ctrecon.service.adapter.out.engine;

import dev.ctrecon.core.application.port.out.CancellationSignal;
import dev.ctrecon.core.application.port.out.IterationProgress;
import dev.ctrecon.core.application.port.out.ReconstructionCancelledException;
import dev.ctrecon.core.application.port.out.ReconstructionEngine;
import dev.ctrecon.core.application.port.out.ReconstructionOutcome;
import dev.ctrecon.core.domain.ConvergencePoint;
import dev.ctrecon.core.domain.DatasetId;
import dev.ctrecon.core.domain.ImageData;
import dev.ctrecon.core.domain.ReconstructionParameters;
import dev.ctrecon.core.domain.Termination;
import dev.ctrecon.engine.linalg.CsrMatrix;
import dev.ctrecon.engine.parallel.ExecutionMode;
import dev.ctrecon.engine.parallel.OperationCancelledException;
import dev.ctrecon.engine.solver.ReconstructionRequest;
import dev.ctrecon.engine.solver.Reconstructor;
import dev.ctrecon.engine.solver.SolverResult;
import dev.ctrecon.engine.solver.SolverSettings;
import dev.ctrecon.engine.solver.StopReason;
import dev.ctrecon.engine.solver.TikhonovProblem;
import dev.ctrecon.service.adapter.out.dataset.ProjectionData;
import dev.ctrecon.service.adapter.out.dataset.ProjectionDataLoader;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EngineReconstructionAdapter implements ReconstructionEngine {

    static final double TOLERANCE = 1e-6;

    private final ProjectionDataLoader loader;
    private final Reconstructor reconstructor = new Reconstructor();
    private final Map<DatasetId, PreparedSystem> cache;

    public EngineReconstructionAdapter(ProjectionDataLoader loader, int cachedSystems) {
        if (cachedSystems <= 0) {
            throw new IllegalArgumentException("cachedSystems must be positive, got " + cachedSystems);
        }
        this.loader = loader;
        this.cache = new LinkedHashMap<>(4, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<DatasetId, PreparedSystem> eldest) {
                return size() > cachedSystems;
            }
        };
    }

    @Override
    public ReconstructionOutcome reconstruct(ReconstructionParameters parameters,
                                             IterationProgress progress,
                                             CancellationSignal cancellation) {
        PreparedSystem system = prepared(parameters.datasetId());
        TikhonovProblem problem = new TikhonovProblem(system.systemMatrix(), system.transposedSystemMatrix(),
                system.measurements(), parameters.regularization());
        ReconstructionRequest request = new ReconstructionRequest(problem,
                new SolverSettings(parameters.iterations(), TOLERANCE),
                toEngineMode(parameters.mode()), parameters.threads());
        try {
            SolverResult result = reconstructor.reconstruct(request,
                    report -> progress.iterationCompleted(new ConvergencePoint(
                            report.iteration(), report.relativeResidual(), report.relativeMisfit())),
                    cancellation::isCancellationRequested);
            return new ReconstructionOutcome(toImage(result.solution(), system.imageSize()),
                    toTermination(result.stopReason()));
        } catch (OperationCancelledException cancelled) {
            throw new ReconstructionCancelledException("Reconstruction stopped on request", cancelled);
        }
    }

    private PreparedSystem prepared(DatasetId id) {
        synchronized (cache) {
            PreparedSystem cached = cache.get(id);
            if (cached != null) {
                return cached;
            }
        }
        PreparedSystem prepared = prepare(loader.load(id));
        synchronized (cache) {
            cache.put(id, prepared);
        }
        return prepared;
    }

    static PreparedSystem prepare(ProjectionData data) {
        int pixels = data.imageSize() * data.imageSize();
        CsrMatrix transposed = CsrMatrix.fromArrays(pixels, data.rays(),
                data.columnPointers(), data.rowIndices(), data.values());
        return new PreparedSystem(data.imageSize(), transposed.transpose(), transposed, data.measurements());
    }

    static ExecutionMode toEngineMode(dev.ctrecon.core.domain.ExecutionMode mode) {
        return switch (mode) {
            case SEQUENTIAL -> ExecutionMode.SEQUENTIAL;
            case STATIC_BLOCKS -> ExecutionMode.STATIC_BLOCKS;
            case FORK_JOIN -> ExecutionMode.FORK_JOIN;
        };
    }

    private static Termination toTermination(StopReason reason) {
        return switch (reason) {
            case CONVERGED -> Termination.CONVERGED;
            case MAX_ITERATIONS -> Termination.ITERATION_LIMIT;
            case NOTHING_TO_SOLVE -> Termination.NOTHING_TO_SOLVE;
        };
    }

    private static ImageData toImage(double[] columnMajor, int size) {
        double[] rowMajor = new double[columnMajor.length];
        for (int column = 0; column < size; column++) {
            for (int row = 0; row < size; row++) {
                rowMajor[row * size + column] = columnMajor[row + column * size];
            }
        }
        return new ImageData(size, size, rowMajor);
    }
}
