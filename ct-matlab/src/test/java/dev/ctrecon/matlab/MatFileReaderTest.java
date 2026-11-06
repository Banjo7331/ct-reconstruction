package dev.ctrecon.matlab;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static dev.ctrecon.matlab.Fixtures.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class MatFileReaderTest {

    @ParameterizedTest
    @ValueSource(strings = {"system-compressed.mat", "system-uncompressed.mat"})
    void listsEveryVariableWithItsClassAndShape(String file) throws IOException {
        List<MatVariableInfo> variables = MatFileReader.listVariables(fixture(file));

        assertThat(variables)
                .extracting(MatVariableInfo::name, MatVariableInfo::matClass, MatVariableInfo::rows, MatVariableInfo::columns)
                .containsExactlyInAnyOrder(
                        tuple("A", MatClass.SPARSE, 6, 4),
                        tuple("m", MatClass.DOUBLE, 3, 2),
                        tuple("normA", MatClass.DOUBLE, 1, 1),
                        tuple("desc", MatClass.CHAR, 1, 35),
                        tuple("meta", MatClass.STRUCT, 1, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"system-compressed.mat", "system-uncompressed.mat"})
    void readsDenseDoubleArraysInColumnMajorOrder(String file) throws IOException {
        MatFile mat = MatFileReader.readAll(fixture(file));

        MatNumericArray measurements = mat.numeric("m").orElseThrow();
        assertThat(measurements.rows()).isEqualTo(3);
        assertThat(measurements.columns()).isEqualTo(2);
        assertThat(measurements.get(2, 0)).isEqualTo(0.3);
        assertThat(measurements.get(0, 1)).isEqualTo(0.4);
        assertThat(measurements.toColumnMajorArray()).containsExactly(0.1, 0.2, 0.3, 0.4, 0.5, 0.6);
        assertThat(mat.numeric("normA").orElseThrow().get(0, 0)).isEqualTo(12.5);
    }

    @Test
    void loadsOnlyTheRequestedVariables() throws IOException {
        MatFile mat = MatFileReader.read(fixture("system-compressed.mat"), Set.of("normA"));

        assertThat(mat.names()).containsExactly("normA");
        assertThat(mat.numeric("m")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"handwritten-little-endian.mat", "handwritten-big-endian.mat"})
    void widensDoublesThatMatlabStoredInNarrowerIntegerTypes(String file) throws IOException {
        MatFile mat = MatFileReader.readAll(fixture(file));

        assertThat(mat.numeric("m").orElseThrow().toColumnMajorArray()).containsExactly(0, 1, 2, 250, 251, 255);
        assertThat(mat.numeric("x").orElseThrow().toColumnMajorArray()).containsExactly(-3, 7, -32768, 32767);
    }

    @Test
    void rejectsAFileThatIsNotAMatFile() {
        assertThatThrownBy(() -> MatFileReader.listVariables(fixture("not-a-mat-file.mat")))
                .isInstanceOf(MatFileFormatException.class)
                .hasMessageContaining("not a MATLAB Level 5 MAT-file");
    }

    @Test
    void explainsThatHdf5BasedVersion73IsNotSupported(@TempDir Path directory) throws IOException {
        byte[] header = new byte[128];
        Arrays.fill(header, (byte) ' ');
        header[124] = 0x00;
        header[125] = 0x02;
        header[126] = 'I';
        header[127] = 'M';
        Path file = Files.write(directory.resolve("v73.mat"), header);

        assertThatThrownBy(() -> MatFileReader.listVariables(file))
                .isInstanceOf(MatFileFormatException.class)
                .hasMessageContaining("7.3")
                .hasMessageContaining("-v7");
    }

    @Test
    void reportsATruncatedFile(@TempDir Path directory) throws IOException {
        byte[] complete = Files.readAllBytes(fixture("system-uncompressed.mat"));
        Path truncated = Files.write(directory.resolve("truncated.mat"), Arrays.copyOf(complete, complete.length - 20));

        assertThatThrownBy(() -> MatFileReader.readAll(truncated))
                .isInstanceOf(MatFileFormatException.class)
                .hasMessageContaining("end of file");
    }
}
