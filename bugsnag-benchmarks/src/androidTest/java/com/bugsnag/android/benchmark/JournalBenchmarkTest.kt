package com.bugsnag.android.benchmark

import android.app.Application
import android.content.Context
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bugsnag.android.*
import com.bugsnag.android.internal.journal.Journal
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
internal class JournalBenchmarkTest {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var client: Client
    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext<Application>()
        client = generateClient(ctx)
    }

    @Test
    fun serializeJournal() {
        val journal = Journal("Bugsnag state",1)
        val entries = listOf(
            Journal.Command("a", "b")
        )

        benchmarkRule.measureRepeated {
            journal.clear()
            val stream = benchmarkRule.scope.runWithTimingDisabled {
                ByteArrayOutputStream()
            }
            stream.use {
                for(entry in entries) {
                    journal.add(entry)
                }
                journal.serialize(stream)
            }
        }
    }
}