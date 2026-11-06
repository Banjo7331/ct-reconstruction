package dev.ctrecon.matlab;

public enum MatClass {
    CELL(1),
    STRUCT(2),
    OBJECT(3),
    CHAR(4),
    SPARSE(5),
    DOUBLE(6),
    SINGLE(7),
    INT8(8),
    UINT8(9),
    INT16(10),
    UINT16(11),
    INT32(12),
    UINT32(13),
    INT64(14),
    UINT64(15),
    FUNCTION(16),
    OPAQUE(17);

    private final int code;

    MatClass(int code) {
        this.code = code;
    }

    public boolean isNumeric() {
        return code >= DOUBLE.code && code <= UINT64.code;
    }

    static MatClass of(int code) throws MatFileFormatException {
        for (MatClass matClass : values()) {
            if (matClass.code == code) {
                return matClass;
            }
        }
        throw new MatFileFormatException("Unknown MATLAB array class " + code);
    }
}
