package dev.ctrecon.matlab;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

final class BoundedInputStream extends FilterInputStream {

    private long remaining;

    BoundedInputStream(InputStream in, long limit) {
        super(in);
        this.remaining = limit;
    }

    @Override
    public int read() throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int value = super.read();
        if (value >= 0) {
            remaining--;
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int read = super.read(buffer, offset, (int) Math.min(length, remaining));
        if (read > 0) {
            remaining -= read;
        }
        return read;
    }

    @Override
    public long skip(long count) throws IOException {
        long skipped = super.skip(Math.min(count, remaining));
        remaining -= skipped;
        return skipped;
    }

    @Override
    public int available() throws IOException {
        return (int) Math.min(super.available(), remaining);
    }

    void skipRemaining() throws IOException {
        while (remaining > 0) {
            long skipped = skip(remaining);
            if (skipped <= 0 && read() < 0) {
                throw new MatFileFormatException("File ends inside a compressed element");
            }
        }
    }

    @Override
    public void close() {
    }
}
