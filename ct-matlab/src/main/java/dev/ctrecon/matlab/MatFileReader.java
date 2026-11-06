package dev.ctrecon.matlab;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public final class MatFileReader {

    private static final int HEADER_SIZE = 128;
    private static final int VERSION_OFFSET = 124;
    private static final int LEVEL_5_VERSION = 0x0100;
    private static final int HDF5_VERSION = 0x0200;
    private static final int COMPLEX_FLAG = 0x0800;
    private static final int BUFFER_SIZE = 1 << 16;

    private MatFileReader() {
    }

    public static List<MatVariableInfo> listVariables(Path file) throws IOException {
        List<MatVariableInfo> variables = new ArrayList<>();
        parse(file, name -> false, variables, new HashMap<>());
        return List.copyOf(variables);
    }

    public static MatFile read(Path file, Set<String> names) throws IOException {
        Map<String, MatVariable> loaded = new HashMap<>();
        parse(file, names::contains, new ArrayList<>(), loaded);
        return new MatFile(loaded);
    }

    public static MatFile readAll(Path file) throws IOException {
        Map<String, MatVariable> loaded = new HashMap<>();
        parse(file, name -> true, new ArrayList<>(), loaded);
        return new MatFile(loaded);
    }

    private static void parse(Path file, Predicate<String> wanted,
                              List<MatVariableInfo> infos, Map<String, MatVariable> loaded) throws IOException {
        try (InputStream stream = new BufferedInputStream(Files.newInputStream(file), BUFFER_SIZE)) {
            MatInput input = new MatInput(stream, readHeader(stream, file));
            ByteOrder order = input.order();
            for (ElementTag tag = input.readTagOrEnd(); tag != null; tag = input.readTagOrEnd()) {
                switch (tag.type()) {
                    case COMPRESSED -> parseCompressed(stream, input, tag, order, wanted, infos, loaded);
                    case MATRIX -> parseMatrix(input, tag, wanted, infos, loaded, true);
                    default -> input.skip(tag.byteCount() + tag.padding());
                }
            }
        }
    }

    private static ByteOrder readHeader(InputStream stream, Path file) throws IOException {
        byte[] header = stream.readNBytes(HEADER_SIZE);
        if (header.length < HEADER_SIZE) {
            throw new MatFileFormatException(file + " is too short to be a MAT-file");
        }
        ByteOrder order;
        if (header[126] == 'I' && header[127] == 'M') {
            order = ByteOrder.LITTLE_ENDIAN;
        } else if (header[126] == 'M' && header[127] == 'I') {
            order = ByteOrder.BIG_ENDIAN;
        } else {
            throw new MatFileFormatException(file + " is not a MATLAB Level 5 MAT-file");
        }
        int version = order == ByteOrder.LITTLE_ENDIAN
                ? (header[VERSION_OFFSET] & 0xFF) | (header[VERSION_OFFSET + 1] & 0xFF) << 8
                : (header[VERSION_OFFSET] & 0xFF) << 8 | (header[VERSION_OFFSET + 1] & 0xFF);
        if (version == HDF5_VERSION) {
            throw new MatFileFormatException(file + " is a MAT-file version 7.3 (HDF5), which is not supported."
                    + " Save it again in MATLAB with save(..., '-v7').");
        }
        if (version != LEVEL_5_VERSION) {
            throw new MatFileFormatException(file + " has unsupported MAT-file version 0x" + Integer.toHexString(version));
        }
        return order;
    }

    private static void parseCompressed(InputStream stream, MatInput outer, ElementTag tag, ByteOrder order,
                                        Predicate<String> wanted, List<MatVariableInfo> infos,
                                        Map<String, MatVariable> loaded) throws IOException {
        BoundedInputStream compressed = new BoundedInputStream(stream, tag.byteCount());
        Inflater inflater = new Inflater();
        try {
            InputStream inflated = new InflaterInputStream(compressed, inflater, BUFFER_SIZE);
            MatInput inner = new MatInput(inflated, order);
            ElementTag matrix = inner.readTag();
            if (matrix.type() != MatDataType.MATRIX) {
                throw new MatFileFormatException("Compressed element holds " + matrix.type() + " instead of a matrix");
            }
            parseMatrix(inner, matrix, wanted, infos, loaded, false);
            compressed.skipRemaining();
        } finally {
            inflater.end();
        }
        outer.advance(tag.byteCount());
    }

    private static void parseMatrix(MatInput input, ElementTag tag, Predicate<String> wanted,
                                    List<MatVariableInfo> infos, Map<String, MatVariable> loaded,
                                    boolean consumeRemainder) throws IOException {
        long end = input.position() + tag.byteCount();
        if (tag.byteCount() == 0) {
            return;
        }

        ElementTag flagsTag = input.readTag();
        if (flagsTag.type() != MatDataType.UINT32 || flagsTag.byteCount() != 8) {
            throw new MatFileFormatException("Matrix does not start with array flags");
        }
        int flags = input.readInt();
        input.readInt();
        MatClass matClass = MatClass.of(flags & 0xFF);
        boolean complex = (flags & COMPLEX_FLAG) != 0;
        int[] dimensions = input.readIntegers(input.readTag());
        String name = new String(input.readBytes(input.readTag()), StandardCharsets.US_ASCII);
        MatVariableInfo info = new MatVariableInfo(name, matClass,
                Arrays.stream(dimensions).boxed().toList(), complex);
        infos.add(info);

        if (wanted.test(name)) {
            MatVariable variable = readData(input, info);
            if (variable != null) {
                loaded.put(name, variable);
            }
        }
        if (consumeRemainder) {
            input.skipTo(end);
        }
    }

    private static MatVariable readData(MatInput input, MatVariableInfo info) throws IOException {
        if (info.matClass() == MatClass.SPARSE) {
            requireRealMatrix(info);
            int[] rowIndices = input.readIntegers(input.readTag());
            int[] columnPointers = input.readIntegers(input.readTag());
            double[] values = input.readNumeric(input.readTag());
            return new MatSparseMatrix(info.name(), info.rows(), info.columns(), rowIndices, columnPointers, values);
        }
        if (!info.matClass().isNumeric()) {
            return null;
        }
        requireRealMatrix(info);
        double[] values = input.readNumeric(input.readTag());
        return new MatNumericArray(info.name(), info.rows(), info.columns(), values);
    }

    private static void requireRealMatrix(MatVariableInfo info) throws MatFileFormatException {
        if (!info.isMatrix()) {
            throw new MatFileFormatException("Variable " + info.name() + " has " + info.dimensions().size()
                    + " dimensions; only 2-D arrays are supported");
        }
        if (info.complex()) {
            throw new MatFileFormatException("Variable " + info.name() + " is complex; only real data is supported");
        }
    }
}
