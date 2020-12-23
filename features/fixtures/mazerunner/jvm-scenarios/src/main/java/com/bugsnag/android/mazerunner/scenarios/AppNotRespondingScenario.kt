package com.bugsnag.android.mazerunner.scenarios

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.bugsnag.android.Configuration

/**
 * Stops the app from responding for a time period
 */
internal class AppNotRespondingScenario(
    config: Configuration,
    context: Context
) : Scenario(config, context) {
    init {
        config.autoTrackSessions = false
        config.enabledErrorTypes.anrs = true
    }

    override fun run() {
        super.run()
        val main = Handler(Looper.getMainLooper())
        main.postDelayed(
            Runnable {
                Thread.sleep(50000) // FOREVER
            },
            1
        ) // A moment of delay so there is something to 'tap' onscreen
    }
}
