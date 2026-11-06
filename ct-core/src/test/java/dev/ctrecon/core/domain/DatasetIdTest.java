package dev.ctrecon.core.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasetIdTest {

    @ParameterizedTest
    @ValueSource(strings = {"Data164.mat", "walnut/Data164.mat", "lotus-root/v1/Data_128.mat"})
    void acceptsRelativePathsToMatFiles(String value) {
        assertThat(new DatasetId(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "../secret.mat",
            "walnut/../../etc/passwd.mat",
            "/absolute/Data164.mat",
            "walnut/./Data164.mat",
            "Data164.txt",
            "walnut\\Data164.mat",
            "walnut//Data164.mat",
            "name with spaces.mat"
    })
    void rejectsAnythingThatCouldLeaveTheDataFolderOrIsNotAMatFile(String value) {
        assertThatThrownBy(() -> new DatasetId(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
