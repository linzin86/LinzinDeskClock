package com.linzin.legacy;

import java.io.File;
import java.io.FileOutputStream;
import java.security.ProviderException;
import org.junit.Test;
import static org.junit.Assert.*;

public class KernelSecureRandomTest {
    @Test public void bytesComeFromTheExplicitEntropySource() throws Exception {
        File source = File.createTempFile("linzin-entropy", ".bin");
        try {
            byte[] expected = new byte[] { 31, 42, 51, 99 };
            try (FileOutputStream output = new FileOutputStream(source)) { output.write(expected); }
            byte[] actual = new byte[expected.length];
            new KernelSecureRandom(source).nextBytes(actual);
            assertArrayEquals(expected, actual);
        } finally { source.delete(); }
    }

    @Test public void unavailableEntropyCannotFallBackToAnotherGenerator() throws Exception {
        File source = File.createTempFile("linzin-entropy", ".bin");
        assertTrue(source.delete());
        try {
            new KernelSecureRandom(source).nextBytes(new byte[32]);
            fail("Missing entropy must fail closed");
        } catch (ProviderException expected) { }
    }
}
