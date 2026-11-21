package com.linzin.clockcore;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class LocationAgeTest {
    @Test public void preservesTheOriginalFixTimeAfterLaterObservations() {
        assertEquals(Long.valueOf(1000), LocationAge.knownFixTime(1000L, 5000L, 10000));
        assertEquals(Long.valueOf(1000), LocationAge.knownFixTime(1000L, 9000L, 10000));
    }
    @Test public void doesNotInventAnExpiryOrObservationForOldRecords() {
        assertEquals(Long.valueOf(1000), LocationAge.knownFixTime(1000L, 2000L, 100L * 86400000));
        assertEquals(Long.valueOf(1000), LocationAge.knownFixTime(1000L, null, 10000));
        assertNull(LocationAge.knownFixTime(null, null, 10000));
    }
    @Test public void invalidAndFutureTimesAreUnknown() {
        for (long fix : new long[] { -1, 0, 10001 }) assertNull(LocationAge.knownFixTime(fix, 10000L, 10000));
        assertNull(LocationAge.knownFixTime(5000L, 10000L, 9000));
        assertNull(LocationAge.knownFixTime(5000L, 4000L, 10000));
    }
}
