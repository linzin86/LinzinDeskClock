package com.linzin.clockcore;

import org.junit.Test;
import static org.junit.Assert.*;

public class ClockLayoutModeTest {
    @Test public void existingStoredLayoutNamesRoundTrip() {
        assertEquals(ClockLayoutMode.SINGLE, ClockLayoutMode.fromPersisted("SINGLE"));
        assertEquals(ClockLayoutMode.DUAL_LEFT_LARGE, ClockLayoutMode.fromPersisted("DUAL_LEFT_LARGE"));
        assertEquals(ClockLayoutMode.DUAL_RIGHT_LARGE, ClockLayoutMode.fromPersisted("DUAL_RIGHT_LARGE"));
    }
    @Test public void corruptOrMissingPreferenceFallsBackToSingle() {
        assertEquals(ClockLayoutMode.SINGLE, ClockLayoutMode.fromPersisted(null));
        assertEquals(ClockLayoutMode.SINGLE, ClockLayoutMode.fromPersisted("unknown"));
    }
}
