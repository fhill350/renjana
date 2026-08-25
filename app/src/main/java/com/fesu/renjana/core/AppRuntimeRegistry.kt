package com.fesu.renjana.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import com.fesu.renjana.RenjanaApplication
import com.fesu.renjana.utils.RenjanaLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks which (instance, app) pairs are currently RUNNING inside stub processes.
 *
 * The old model tracked one boolean per instance — wrong the moment an instance
 * hosts multiple apps. An app is "running" exactly while its stub process
 * (:p0-:p9) is alive, so this registry also RECONCILES against the OS process
 * list on every read: apps whose process died (guest crash, system reclaim)
 * disappear from the UI without anyone telling us.
 *
 * Registration points:
 * - InstanceLauncher (main process) right after startActivity succeeds.
 * - InstanceLifecycleService, when a StubActivity reports in.
 */
object AppRuntimeRegistry {

    private const val TAG = "AppRuntime"

    data class RunningApp(
        val instanceId: String,
        val packageName: String,
        val appName: String,
        /** Stub slot hosting this app (StubActivity_0..9). */
        val stubIndex: Int,
        val startedAt: Long
    ) {
        val key: String get() = "$instanceId/$packageName"
    }

    private val _runningApps = MutableStateFlow<Set<RunningApp>>(emptySet())

    /** Live snapshot; reconciled against running processes on each emission. */
    val runningApps: StateFlow<Set<RunningApp>> = _runningApps.asStateFlow()

    /** Timestamp of the last refresh — lets keepers of periodic timers skip work. */
    @Volatile
    var lastRefreshAt: Long = 0L
        private set

    fun register(instanceId: String, packageName: String, appName: String, stubIndex: Int) {
        val app = RunningApp(instanceId, packageName, appName, stubIndex, System.currentTimeMillis())
        _runningApps.value = _runningApps.value.filterNot { it.key == app.key }.toSet() + app
        RenjanaLog.i(TAG, "Registered running app: ${app.appName} (stub=$stubIndex, instance=$instanceId)")
    }

    fun unregister(instanceId: String, packageName: String) {
        val removed = _runningApps.value.filterNot { it.instanceId == instanceId && it.packageName == packageName }
        if (removed != _runningApps.value) {
            _runningApps.value = removed.toSet()
            RenjanaLog.i(TAG, "Unregistered app $packageName (instance=$instanceId)")
        }
    }

    fun unregisterInstance(instanceId: String) {
        _runningApps.value = _runningApps.value.filterNot { it.instanceId == instanceId }.toSet()
    }

    fun isAppRunning(instanceId: String, packageName: String): Boolean {
        refresh()
        return _runningApps.value.any { it.instanceId == instanceId && it.packageName == packageName }
    }

    fun appsForInstance(instanceId: String): List<RunningApp> {
        refresh()
        return _runningApps.value.filter { it.instanceId == instanceId }
    }

    fun isInstanceRunning(instanceId: String): Boolean = appsForInstance(instanceId).isNotEmpty()

    /**
     * Drop entries whose stub process is no longer alive. The stub slot index maps
     * 1:1 to a process name (:p0-:p9) — same trick ActivityStubManager uses.
     */
    fun refresh() {
        val current = _runningApps.value
        if (current.isEmpty()) {
            lastRefreshAt = System.currentTimeMillis()
            return
        }
        val app = try { RenjanaApplication.get() } catch (_: Throwable) { return }
        val alive = try {
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.runningAppProcesses?.map { it.processName }?.toSet()
        } catch (_: Throwable) { null }
        if (alive == null) {
            lastRefreshAt = System.currentTimeMillis()
            return
        }
        val surviving = current.filter { running -> alive.any { it.endsWith(":p${running.stubIndex}") } }
        if (surviving.size != current.size) {
            val dead = current - surviving.toSet()
            _runningApps.value = surviving.toSet()
            dead.forEach { RenjanaLog.i(TAG, "Reaped dead app ${it.appName} (stub=${it.stubIndex})") }
        }
        lastRefreshAt = System.currentTimeMillis()
    }

    /**
     * Bring a running app's task back to the foreground. Targets the exact stub
     * component; REORDER_TO_FRONT + SINGLE_TOP reuses the existing task instead
     * of stacking a duplicate.
     */
    fun openApp(context: Context, app: RunningApp): Boolean {
        refresh()
        if (_runningApps.value.none { it.key == app.key }) {
            RenjanaLog.w(TAG, "openApp: ${app.appName} is not running")
            return false
        }
        val intent = Intent().apply {
            component = ActivityStubManager.stubComponentFor(app.stubIndex)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            RenjanaLog.e(TAG, "openApp failed: ${e.message}")
            false
        }
    }

    /**
     * Close one app. Guest apps occupy MULTIPLE stub tasks over their navigation
     * (splash → onboarding → …), so every task whose baseIntent carries this
     * (instanceId, packageName) pair is finished. Fallback for tasks whose
     * extras can't be read: deliver ACTION_FINISH_GUEST to the current stub —
     * it finishes itself and its process self-destructs.
     */
    fun closeApp(context: Context, app: RunningApp): Boolean {
        var closed = finishTasksFor(context) { instId, pkg ->
            instId == app.instanceId && (pkg == null || pkg == app.packageName)
        }
        if (!closed) {
            closed = sendFinishGuest(context, app.stubIndex)
        }
        unregister(app.instanceId, app.packageName)
        return closed
    }

    /**
     * Stop an entire instance: finish every stub task whose baseIntent carries
     * the instanceId (any app), plus FINISH_GUEST to each known running app stub.
     */
    fun closeInstance(context: Context, instanceId: String) {
        val anyTask = finishTasksFor(context) { instId, _ -> instId == instanceId }
        _runningApps.value.filter { it.instanceId == instanceId }.forEach { app ->
            sendFinishGuest(context, app.stubIndex)
        }
        unregisterInstance(instanceId)
        RenjanaLog.i(TAG, "closeInstance($instanceId): tasksFinished=$anyTask")
    }

    /**
     * Finish every own task whose baseIntent extras identify a stub of interest.
     * The predicate receives (instanceId, packageName?) from the task's
     * baseIntent extras; packageName is null when unavailable.
     * Returns true if at least one task was finished.
     */
    private fun finishTasksFor(context: Context, matches: (String?, String?) -> Boolean): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        var finishedAny = false
        try {
            val tasks = am.appTasks.toList()
            for (task in tasks) {
                try {
                    val base = task.taskInfo.baseIntent ?: continue
                    val comp = base.component ?: continue
                    if (!comp.className.startsWith("com.fesu.renjana.core.StubActivity")) continue
                    val instId = base.getStringExtra(StubActivity.EXTRA_INSTANCE_ID)
                    val pkg = base.getStringExtra(StubActivity.EXTRA_PACKAGE_NAME)
                    if (matches(instId, pkg)) {
                        task.finishAndRemoveTask()
                        finishedAny = true
                        RenjanaLog.i(TAG, "Finished stub task ${comp.className} (instance=$instId pkg=$pkg)")
                    }
                } catch (_: Throwable) {
                    // skip unreadable task
                }
            }
        } catch (e: Exception) {
            RenjanaLog.w(TAG, "finishTasksFor failed: ${e.message}")
        }
        return finishedAny
    }

    private fun sendFinishGuest(context: Context, stubIndex: Int): Boolean {
        return try {
            val finishIntent = Intent().apply {
                component = ActivityStubManager.stubComponentFor(stubIndex)
                action = StubActivity.ACTION_FINISH_GUEST
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            context.startActivity(finishIntent)
            RenjanaLog.i(TAG, "Sent FINISH_GUEST to stub=$stubIndex")
            true
        } catch (e: Exception) {
            RenjanaLog.e(TAG, "sendFinishGuest failed: ${e.message}")
            false
        }
    }
}
