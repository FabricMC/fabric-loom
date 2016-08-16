package net.fabric.loom.util;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;

public class Checksum {
    public static boolean equals(File file, String checksum) {
        if (file == null) {
            return false;
        }
        try {
            HashCode hash = Files.hash(file, Hashing.sha1());
            StringBuilder builder = new StringBuilder();
            for (Byte hashBytes : hash.asBytes()) {
                builder.append(Integer.toString((hashBytes & 0xFF) + 0x100, 16).substring(1));
            }
            return builder.toString().equals(checksum);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
