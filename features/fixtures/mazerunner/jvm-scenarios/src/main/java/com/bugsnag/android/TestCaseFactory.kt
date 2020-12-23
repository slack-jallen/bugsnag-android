package com.bugsnag.android

import android.content.Context
import com.bugsnag.android.mazerunner.scenarios.Scenario

class TestCaseFactory {

    fun testCaseForName(eventType: String?, config: Configuration, context: Context): Scenario {
        val clz = Class.forName("com.bugsnag.android.mazerunner.scenarios.$eventType")
        val constructor = clz.constructors[0]
        return constructor.newInstance(config, context) as Scenario
    }
}
