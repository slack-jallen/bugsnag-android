package com.bugsnag.android

import com.bugsnag.android.internal.StateObserver
import com.bugsnag.android.internal.journal.JournalKeys
import java.util.Date

internal class JournaledStateObserver(val client: Client, val journal: BugsnagJournal) : StateObserver {
    override fun onStateChange(event: StateEvent) {
        when (event) {
            is StateEvent.JournalSetup -> {
                handleJournalSetup(event)
            }
            StateEvent.DeliverPending -> {
                // Nothing for us to do
            }
            is StateEvent.AddMetadata -> {
                journal.addCommand(
                    "${JournalKeys.pathMetadata}." +
                        "${BugsnagJournal.unspecialMapPath(event.section)}." +
                        BugsnagJournal.unspecialMapPath(event.key!!),
                    event.value
                )
            }
            is StateEvent.ClearMetadataSection -> {
                journal.addCommand(
                    "${JournalKeys.pathMetadata}." +
                        BugsnagJournal.unspecialMapPath(event.section),
                    null
                )
            }
            is StateEvent.ClearMetadataValue -> {
                journal.addCommand(
                    "${JournalKeys.pathMetadata}." +
                        "${BugsnagJournal.unspecialMapPath(event.section)}." +
                        BugsnagJournal.unspecialMapPath(event.key!!),
                    null
                )
            }
            is StateEvent.AddBreadcrumb -> {
                handleAddBreadcrumbEvent(event)
            }
            StateEvent.NotifyHandled -> {
                journal.addCommand(JournalKeys.pathSessionEventsHandled + "+", 1)
            }
            StateEvent.NotifyUnhandled -> {
                journal.addCommand(JournalKeys.pathSessionEventsUnhandled + "+", 1)
            }
            StateEvent.PauseSession -> {
                journal.addCommand(JournalKeys.pathSession, null)
            }
            is StateEvent.StartSession -> {
                handleStartSessionEvent(event)
            }
            is StateEvent.UpdateContext -> {
                journal.addCommand(JournalKeys.pathContext, event.context)
            }
            is StateEvent.UpdateInForeground -> {
                journal.addCommands(
                    Pair(JournalKeys.pathAppInForeground, event.inForeground),
                    Pair(JournalKeys.pathMetadataAppActiveScreen, event.contextActivity)
                )
            }
            is StateEvent.UpdateLastRunInfo -> {
                // This event doesn't seem to actually be used.
            }
            is StateEvent.UpdateIsLaunching -> {
                journal.addCommand(JournalKeys.pathAppIsLaunching, event.isLaunching)
            }
            is StateEvent.UpdateOrientation -> {
                journal.addCommand(JournalKeys.pathDeviceOrientation, event.orientation)
            }
            is StateEvent.UpdateUser -> {
                journal.addCommand(JournalKeys.pathUser, event.user.toJournalSection())
            }
            is StateEvent.UpdateNotifierInfo -> {
                journal.addCommand(JournalKeys.pathNotifier, event.notifier.toJournalSection())
            }
            is StateEvent.UpdateMemoryTrimEvent -> {
                journal.addCommands(
                    Pair(JournalKeys.pathMetadataAppLowMemory, event.isLowMemory),
                    Pair(JournalKeys.pathMetadataAppMemoryTrimLevel, event.memoryTrimLevelDescription)
                )
            }
        }
    }

    private fun makeMetadataJournalSafe(metadata: Map<String, Any?>): MutableMap<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        metadata.forEach { entry ->
            result[BugsnagJournal.unspecialMapPath(entry.key)] = if (entry.value is Map<*, *>) {
                (entry.value as Map<*, *>).mapKeys {
                    if (it.key is String) {
                        BugsnagJournal.unspecialMapPath(it.key as String)
                    } else {
                        it.key
                    }
                }
            } else {
                entry.value
            }
        }
        return result
    }
    private fun handleAddBreadcrumbEvent(event: StateEvent.AddBreadcrumb) {
        journal.addCommand(
            "${JournalKeys.pathBreadcrumbs}.",
            mapOf(
                JournalKeys.keyMetadata to makeMetadataJournalSafe(event.metadata),
                JournalKeys.keyName to event.message,
                JournalKeys.keyTimestamp to event.timestamp,
                JournalKeys.keyType to event.type.toString()
            )
        )
    }

    private fun handleStartSessionEvent(event: StateEvent.StartSession) {
        journal.addCommand(
            JournalKeys.pathSession,
            mapOf(
                JournalKeys.keyId to event.id,
                JournalKeys.keyStartedAt to event.startedAt,
                JournalKeys.keyEvents to mapOf(
                    JournalKeys.keyHandled to event.handledCount,
                    JournalKeys.keyUnhandled to event.unhandledCount
                )
            )
        )
    }

    private fun handleJournalSetup(event: StateEvent.JournalSetup) {
        val appDataCollector = client.appDataCollector
        val appData = appDataCollector.generateAppWithState()
        val deviceDataCollector = client.deviceDataCollector
        val deviceData = deviceDataCollector.generateDeviceWithState(Date().time)
        val device = deviceData.toJournalSection().filter { it.key != "time" }

        journal.addCommands(
            Pair(JournalKeys.pathApiKey, event.apiKey),
            Pair(JournalKeys.pathContext, client.context),
            Pair(JournalKeys.pathApp, appData.toJournalSection()),
            Pair(JournalKeys.pathMetadataApp, appDataCollector.getAppDataMetadata()),
            Pair(JournalKeys.pathDevice, device),
            Pair(JournalKeys.pathMetadataDevice, deviceDataCollector.getDeviceMetadata()),
            Pair(JournalKeys.pathUser, client.getUser().toJournalSection()),
            Pair(JournalKeys.pathProjectPackages, client.config.projectPackages)
        )
    }
}