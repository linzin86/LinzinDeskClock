package com.linzin.legacy;

import java.io.File;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.Provider;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.SecureRandomSpi;

final class KernelSecureRandom extends SecureRandom {
    private static final Provider PROVIDER = new Provider("LinzinUrandom", 1.0, "Linux kernel entropy") { };
    KernelSecureRandom() { this(new File("/dev/urandom")); }
    KernelSecureRandom(File source) { super(new Engine(source), PROVIDER); }

    private static final class Engine extends SecureRandomSpi {
        private final File source;
        Engine(File source) { this.source = source; }
        // The kernel owns seeding; callers cannot replace its entropy.
        @Override protected void engineSetSeed(byte[] seed) { }
        @Override protected void engineNextBytes(byte[] bytes) {
            try (DataInputStream input = new DataInputStream(new FileInputStream(source))) { input.readFully(bytes); }
            catch (IOException error) { throw new ProviderException("Kernel entropy is unavailable", error); }
        }
        @Override protected byte[] engineGenerateSeed(int count) {
            byte[] bytes = new byte[count]; engineNextBytes(bytes); return bytes;
        }
    }
}
