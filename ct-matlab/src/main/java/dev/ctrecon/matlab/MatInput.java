package dev.ctrecon.matlab;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class MatInput {

    private final InputStream in;
    private final ByteOrder order;
    private final byte[] scratch = new byte[8];
    private long position;

    MatInput(InputStream in, ByteOrder order) {
        this.in = in;
        this.order = order;
    }

    long position() {
        return position;
    }

    ByteOrder order() {
        return order;
    }

    void advance(long count) {
        position += count;
    }

    ElementTag readTag() throws IOException {
        ElementTag tag = readTagOrEnd();
        if (tag == null) {
            throw new MatFileFormatException("Unexpected end of file, expected a data element");
        }
        return tag;
    }

    ElementTag readTagOrEnd() throws IOException {
        int first = in.read();
        if (first < 0) {
            return null;
        }
        scratch[0] = (byte) first;
        readFully(scratch, 1, 3);
        position++;
        int word = ByteBuffer.wrap(scratch, 0, 4).order(order).getInt();
        int smallByteCount = word >>> 16;
        if (smallByteCount != 0) {
            return new ElementTag(MatDataType.of(word & 0xFFFF), smallByteCount, true);
        }
        long byteCount = Integer.toUnsignedLong(readInt());
        return new ElementTag(MatDataType.of(word), byteCount, false);
    }

    int readInt() throws IOException {
        readFully(scratch, 0, 4);
        return ByteBuffer.wrap(scratch, 0, 4).order(order).getInt();
    }

    byte[] readBytes(ElementTag tag) throws IOException {
        byte[] bytes = new byte[Math.toIntExact(tag.byteCount())];
        readFully(bytes, 0, bytes.length);
        skip(tag.padding());
        return bytes;
    }

    int[] readIntegers(ElementTag tag) throws IOException {
        if (tag.type() == MatDataType.INT32) {
            return readInt32(tag);
        }
        double[] values = readNumeric(tag);
        int[] integers = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            double value = values[i];
            if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw new MatFileFormatException("Expected integer values, found " + value);
            }
            integers[i] = (int) value;
        }
        return integers;
    }

    double[] readNumeric(ElementTag tag) throws IOException {
        if (!tag.type().isNumeric()) {
            throw new MatFileFormatException("Expected numeric data, found " + tag.type());
        }
        int count = tag.elementCount();
        int itemSize = tag.type().size();
        double[] values = new double[count];
        byte[] buffer = new byte[Math.max(itemSize, Math.min(count * itemSize, 1 << 16))];
        ByteBuffer view = ByteBuffer.wrap(buffer).order(order);
        int index = 0;
        while (index < count) {
            int items = Math.min(count - index, buffer.length / itemSize);
            readFully(buffer, 0, items * itemSize);
            view.clear();
            for (int i = 0; i < items; i++) {
                values[index++] = readValue(view, tag.type());
            }
        }
        skip(tag.byteCount() - (long) count * itemSize + tag.padding());
        return values;
    }

    private int[] readInt32(ElementTag tag) throws IOException {
        int count = tag.elementCount();
        int[] values = new int[count];
        byte[] buffer = new byte[Math.max(Integer.BYTES, Math.min(count * Integer.BYTES, 1 << 16))];
        ByteBuffer view = ByteBuffer.wrap(buffer).order(order);
        int index = 0;
        while (index < count) {
            int items = Math.min(count - index, buffer.length / Integer.BYTES);
            readFully(buffer, 0, items * Integer.BYTES);
            view.clear();
            view.asIntBuffer().get(values, index, items);
            index += items;
        }
        skip(tag.byteCount() - (long) count * Integer.BYTES + tag.padding());
        return values;
    }

    void skipTo(long target) throws IOException {
        if (target < position) {
            throw new MatFileFormatException("Element is longer than its declared size");
        }
        skip(target - position);
    }

    void skip(long count) throws IOException {
        long left = count;
        while (left > 0) {
            long skipped = in.skip(left);
            if (skipped <= 0) {
                if (in.read() < 0) {
                    throw new MatFileFormatException("Unexpected end of file");
                }
                skipped = 1;
            }
            left -= skipped;
        }
        position += count;
    }

    private void readFully(byte[] buffer, int offset, int length) throws IOException {
        int done = 0;
        while (done < length) {
            int read = in.read(buffer, offset + done, length - done);
            if (read < 0) {
                throw new MatFileFormatException("Unexpected end of file");
            }
            done += read;
        }
        position += length;
    }

    private static double readValue(ByteBuffer view, MatDataType type) throws MatFileFormatException {
        return switch (type) {
            case INT8 -> view.get();
            case UINT8 -> Byte.toUnsignedInt(view.get());
            case INT16 -> view.getShort();
            case UINT16 -> Short.toUnsignedInt(view.getShort());
            case INT32 -> view.getInt();
            case UINT32 -> Integer.toUnsignedLong(view.getInt());
            case SINGLE -> view.getFloat();
            case DOUBLE -> view.getDouble();
            case INT64 -> view.getLong();
            case UINT64 -> unsignedToDouble(view.getLong());
            default -> throw new MatFileFormatException("Expected numeric data, found " + type);
        };
    }

    private static double unsignedToDouble(long value) {
        if (value >= 0) {
            return value;
        }
        return ((value >>> 1) | (value & 1)) * 2.0;
    }
}
