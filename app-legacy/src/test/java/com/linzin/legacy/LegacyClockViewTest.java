package com.linzin.legacy;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class LegacyClockViewTest {
    @Test public void secondHandCrossesTheMinuteBoundaryForward() {
        assertEquals(6f, LegacyClockView.secondHandAdvance(264f, 0), 0.001f);
    }

    @Test public void repeatedTurnsCannotMakeTheSecondHandRunBackward() {
        assertEquals(6f, LegacyClockView.secondHandAdvance(990f, 1), 0.001f);
    }
}
