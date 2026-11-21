package com.linzin.legacy;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Debug;
import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicLong;

final class RuntimeSamples implements java.io.Closeable {
    private final AtomicLong peakHeap = new AtomicLong();
    private final long began = SystemClock.elapsedRealtime();
    private final Thread sampler = new Thread(() -> {
        while (!Thread.currentThread().isInterrupted()) {
            Runtime runtime = Runtime.getRuntime();
            long used = runtime.totalMemory() - runtime.freeMemory();
            if (used > peakHeap.get()) peakHeap.set(used);
            try { Thread.sleep(50); }
            catch (InterruptedException error) { return; }
        }
    }, "legacy-test-heap-sampler");
    RuntimeSamples() { sampler.start(); }
    String snapshot(Context context) {
        Debug.MemoryInfo memory = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memory);
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        return " elapsedMs=" + (SystemClock.elapsedRealtime() - began) + " sampledPeakHeapBytes=" + peakHeap.get()
            + " totalPssKiB=" + memory.getTotalPss() + " dalvikPssKiB=" + memory.dalvikPss
            + " nativePssKiB=" + memory.nativePss + " memoryClassMiB=" + manager.getMemoryClass();
    }
    @Override public void close() {
        sampler.interrupt();
        try { sampler.join(2000); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        if (sampler.isAlive()) throw new AssertionError("Heap sampler did not stop");
    }
}
