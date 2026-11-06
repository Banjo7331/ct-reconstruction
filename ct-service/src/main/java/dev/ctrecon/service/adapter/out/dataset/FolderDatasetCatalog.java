package dev.ctrecon.service.adapter.out.dataset;

import dev.ctrecon.core.application.DatasetNotFoundException;
import dev.ctrecon.core.application.port.out.DatasetCatalog;
import dev.ctrecon.core.domain.Dataset;
import dev.ctrecon.core.domain.DatasetId;
import dev.ctrecon.core.domain.ImageData;
import dev.ctrecon.matlab.MatClass;
import dev.ctrecon.matlab.MatFile;
import dev.ctrecon.matlab.MatFileReader;
import dev.ctrecon.matlab.MatNumericArray;
import dev.ctrecon.matlab.MatSparseMatrix;
import dev.ctrecon.matlab.MatVariableInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class FolderDatasetCatalog implements DatasetCatalog, ProjectionDataLoader {

    static final String SYSTEM_MATRIX = "A";
    static final String SINOGRAM = "m";
    static final String REFERENCE_FILE = "GroundTruthReconstruction.mat";

    private static final Logger LOGGER = LoggerFactory.getLogger(FolderDatasetCatalog.class);
    private static final int MAX_DEPTH = 4;

    private final Path root;
    private final Map<Path, Description> descriptions = new ConcurrentHashMap<>();

    public FolderDatasetCatalog(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    @Override
    public List<Dataset> list() {
        if (!Files.isDirectory(root)) {
            LOGGER.warn("Data folder {} does not exist, no datasets are available", root);
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root, MAX_DEPTH)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".mat"))
                    .filter(file -> !file.getFileName().toString().equals(REFERENCE_FILE))
                    .sorted()
                    .map(this::describe)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot list data folder " + root, failure);
        }
    }

    @Override
    public Optional<Dataset> find(DatasetId id) {
        return resolve(id).flatMap(this::describe);
    }

    @Override
    public Optional<ImageData> sinogram(DatasetId id) {
        return find(id).map(dataset -> {
            MatNumericArray m = readVariables(resolve(id).orElseThrow(), Set.of(SINOGRAM)).numeric(SINOGRAM)
                    .orElseThrow();
            double[] rowMajor = new double[m.rows() * m.columns()];
            for (int detector = 0; detector < m.rows(); detector++) {
                for (int angle = 0; angle < m.columns(); angle++) {
                    rowMajor[detector * m.columns() + angle] = m.get(detector, angle);
                }
            }
            return new ImageData(m.columns(), m.rows(), rowMajor);
        });
    }

    @Override
    public Optional<ImageData> reference(DatasetId id) {
        return find(id).filter(Dataset::hasReference).map(dataset -> {
            Path file = resolve(id).orElseThrow().resolveSibling(REFERENCE_FILE);
            MatNumericArray image = largestSquareMatrix(readAll(file))
                    .orElseThrow(() -> new IllegalStateException(file + " holds no square image"));
            return Resampling.toSquare(image, dataset.imageSize());
        });
    }

    @Override
    public ProjectionData load(DatasetId id) {
        Dataset dataset = find(id).orElseThrow(() -> new DatasetNotFoundException(id, "Measurement data"));
        Path file = resolve(id).orElseThrow();
        long start = System.nanoTime();
        MatFile mat = readVariables(file, Set.of(SYSTEM_MATRIX, SINOGRAM));
        MatSparseMatrix a = mat.sparse(SYSTEM_MATRIX).orElseThrow();
        MatNumericArray m = mat.numeric(SINOGRAM).orElseThrow();
        int columns = a.columns();
        int[] columnPointers = new int[columns + 1];
        for (int column = 0; column < columns; column++) {
            columnPointers[column + 1] = a.columnEnd(column);
        }
        int nonZeros = a.nonZeroCount();
        int[] rowIndices = new int[nonZeros];
        double[] values = new double[nonZeros];
        for (int entry = 0; entry < nonZeros; entry++) {
            rowIndices[entry] = a.rowIndex(entry);
            values[entry] = a.value(entry);
        }
        LOGGER.info("Loaded {}: {} x {} system matrix with {} non-zeros in {} ms", id, a.rows(), columns,
                nonZeros, (System.nanoTime() - start) / 1_000_000);
        return new ProjectionData(id, dataset.imageSize(), dataset.detectorCount(), dataset.angleCount(),
                columnPointers, rowIndices, values, m.toColumnMajorArray());
    }

    private Optional<Path> resolve(DatasetId id) {
        Path file = root.resolve(id.value()).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(file);
    }

    private Optional<Dataset> describe(Path file) {
        try {
            FileTime modified = Files.getLastModifiedTime(file);
            long size = Files.size(file);
            Description cached = descriptions.get(file);
            if (cached != null && cached.modified().equals(modified) && cached.size() == size) {
                return cached.dataset();
            }
            Optional<Dataset> dataset = inspect(file, size);
            descriptions.put(file, new Description(modified, size, dataset));
            return dataset;
        } catch (IOException failure) {
            LOGGER.warn("Skipping {}: {}", file, failure.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Dataset> inspect(Path file, long size) throws IOException {
        List<MatVariableInfo> variables = MatFileReader.listVariables(file);
        Optional<MatVariableInfo> a = variables.stream()
                .filter(variable -> variable.name().equals(SYSTEM_MATRIX) && variable.matClass() == MatClass.SPARSE)
                .findFirst();
        Optional<MatVariableInfo> m = variables.stream()
                .filter(variable -> variable.name().equals(SINOGRAM) && variable.matClass().isNumeric()
                        && variable.isMatrix())
                .findFirst();
        if (a.isEmpty() || m.isEmpty()) {
            return Optional.empty();
        }
        int detectors = m.get().rows();
        int angles = m.get().columns();
        int pixels = a.get().columns();
        int imageSize = (int) Math.round(Math.sqrt(pixels));
        if (a.get().rows() != detectors * angles || imageSize * imageSize != pixels) {
            LOGGER.warn("Skipping {}: A is {} x {} but m is {} x {}", file, a.get().rows(), pixels, detectors, angles);
            return Optional.empty();
        }
        String relative = root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
        try {
            boolean hasReference = Files.isRegularFile(file.resolveSibling(REFERENCE_FILE));
            return Optional.of(new Dataset(new DatasetId(relative), imageSize, detectors, angles, size, hasReference));
        } catch (IllegalArgumentException unsupportedName) {
            LOGGER.warn("Skipping {}: {}", file, unsupportedName.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<MatNumericArray> largestSquareMatrix(MatFile mat) {
        return mat.names().stream()
                .map(mat::numeric)
                .flatMap(Optional::stream)
                .filter(array -> array.rows() == array.columns())
                .max(Comparator.comparingInt(MatNumericArray::rows));
    }

    private static MatFile readVariables(Path file, Set<String> names) {
        try {
            return MatFileReader.read(file, names);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot read " + file, failure);
        }
    }

    private static MatFile readAll(Path file) {
        try {
            return MatFileReader.readAll(file);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot read " + file, failure);
        }
    }

    private record Description(FileTime modified, long size, Optional<Dataset> dataset) {
    }
}
