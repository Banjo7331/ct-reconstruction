package dev.ctrecon.matlab;

record ElementTag(MatDataType type, long byteCount, boolean small) {

    private static final int SMALL_ELEMENT_DATA_BYTES = 4;
    private static final int ALIGNMENT = 8;

    int elementCount() throws MatFileFormatException {
        long count = byteCount / type.size();
        if (count > Integer.MAX_VALUE - 8) {
            throw new MatFileFormatException("Array of " + count + " elements is too large for one Java array");
        }
        return (int) count;
    }

    long padding() {
        if (small) {
            return SMALL_ELEMENT_DATA_BYTES - byteCount;
        }
        return (ALIGNMENT - byteCount % ALIGNMENT) % ALIGNMENT;
    }
}
