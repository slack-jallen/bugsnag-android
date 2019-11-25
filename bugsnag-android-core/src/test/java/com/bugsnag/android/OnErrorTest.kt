package com.bugsnag.android

import com.bugsnag.android.BugsnagTestUtils.generateImmutableConfig

import org.junit.Assert.assertEquals

import org.junit.Test

class OnErrorTest {

    private val config = generateImmutableConfig()

    @Test
    fun testRunModifiesError() {
        val context = "new-context"

        val onError = OnError {
            it.context = context
            false
        }

        val handledState = HandledState.newInstance(HandledState.REASON_HANDLED_EXCEPTION)
        val error = Event(RuntimeException("Test"), config, handledState)
        onError.run(error)
        assertEquals(context, error.context)
    }
}
