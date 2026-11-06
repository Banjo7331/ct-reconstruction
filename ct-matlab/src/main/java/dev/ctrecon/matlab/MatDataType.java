package dev.ctrecon.matlab;

enum MatDataType {
    INT8(1, 1),
    UINT8(2, 1),
    INT16(3, 2),
    UINT16(4, 2),
    INT32(5, 4),
    UINT32(6, 4),
    SINGLE(7, 4),
    DOUBLE(9, 8),
    INT64(12, 8),
    UINT64(13, 8),
    MATRIX(14, 1),
    COMPRESSED(15, 1),
    UTF8(16, 1),
    UTF16(17, 2),
    UTF32(18, 4);

    private final int code;
    private final int size;

    MatDataType(int code, int size) {
        this.code = code;
        this.size = size;
    }

    int size() {
        return size;
    }

    boolean isNumeric() {
        return code <= UINT64.code;
    }

    static MatDataType of(int code) throws MatFileFormatException {
        for (MatDataType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new MatFileFormatException("Unknown MAT-file data type " + code);
    }
}
