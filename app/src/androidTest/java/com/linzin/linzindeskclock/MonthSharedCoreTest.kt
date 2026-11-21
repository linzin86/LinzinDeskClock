package com.linzin.linzindeskclock

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Rule
import org.junit.Test

class MonthSharedCoreTest {
    @get:Rule val compose = createComposeRule()

    @Test fun modernMonthConsumerDrawsWithSharedGrid() {
        val month = SimpleDateFormat("MMMM", Locale.US).format(Date()).uppercase(Locale.US)
        compose.setContent { SideDateWidget(modifier = Modifier.testTag("month")) }
        compose.onNodeWithTag("month").assertIsDisplayed()
        compose.onNodeWithText(month).assertIsDisplayed()
    }
}
