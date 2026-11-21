package com.linzin.linzindeskclock

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class AnalogClockSharedCoreTest {
    @get:Rule val compose = createComposeRule()

    @Test fun modernAnalogConsumerDrawsWithSharedCore() {
        compose.setContent {
            AnalogClockFace(foldState = "", showCornerDate = false, showHingeAngle = false,
                isUiLocked = true, modifier = Modifier.testTag("analog"))
        }
        compose.onNodeWithTag("analog").assertIsDisplayed()
        compose.waitForIdle()
    }
}
