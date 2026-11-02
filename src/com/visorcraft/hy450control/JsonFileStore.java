package com.visorcraft.hy450control;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

final class JsonFileStore {
    private JsonFileStore() {}

    static String read(File source) throws Exception {
        byte[] data = new byte[(int) source.length()];
        FileInputStream input = new FileInputStream(source);
        try {
            int offset = 0;
            while (offset < data.length) {
                int count = input.read(data, offset, data.length - offset);
                if (count < 0) throw new IllegalStateException("Truncated file");
                offset += count;
            }
        } finally {
            input.close();
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    static void write(File target, File temporary, String value) throws Exception {
        FileOutputStream output = new FileOutputStream(temporary);
        try {
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        } finally {
            output.close();
        }
        if (!temporary.renameTo(target)) throw new IllegalStateException("File rename failed");
    }
}
