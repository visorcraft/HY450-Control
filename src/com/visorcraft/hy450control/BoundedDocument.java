package com.visorcraft.hy450control;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class BoundedDocument {
    private static final int MAX_BYTES = 1024 * 1024;

    private BoundedDocument() {}

    static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (count > MAX_BYTES - total) {
                throw new IllegalStateException("Backup exceeds 1 MiB");
            }
            output.write(buffer, 0, count);
            total += count;
        }
        return output.toByteArray();
    }

    static void write(OutputStream output, byte[] value) throws IOException {
        output.write(value);
        output.flush();
    }
}
