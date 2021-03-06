package org.akvo.rsr.up

import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultListActivityTest {

    @get:Rule
    val rule = activityScenarioRule<ResultListActivity>()

    @Test
    fun activityShouldDisplayCorrectTitle() {
        ScreenRobot.withRobot(ResultListRobot::class.java)
            .provideContext(InstrumentationRegistry.getInstrumentation().targetContext)
            .checkTitleIs(R.string.title_activity_result_list)
    }

    class ResultListRobot : ScreenRobot<ResultListRobot>()
}
