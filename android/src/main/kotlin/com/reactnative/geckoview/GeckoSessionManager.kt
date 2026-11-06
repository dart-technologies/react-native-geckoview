package com.reactnative.geckoview

import android.content.Context
import android.content.pm.PackageManager
import com.facebook.react.bridge.UiThreadUtil
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

/**
 * Pool of GeckoSessions keyed by the JS `sessionKey`.
 *
 * Threading: sessions are created, opened and closed on the UI thread only,
 * because GeckoRuntime.create, GeckoSession.open and GeckoSession.close assert
 * it. Ref methods arrive on the native-modules thread and only look up a
 * session a view already opened ([getOpenSession]); they never build one.
 *
 * Lock order: this object's monitor may be held while taking
 * [GeckoRuntimeManager]'s (opening a session needs the runtime), never the
 * reverse. [GeckoRuntimeManager.shutdown] relies on that.
 */
object GeckoSessionManager {
    private val sessions = java.util.concurrent.ConcurrentHashMap<String, GeckoSession>()
    private val openedSessionKeys = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )
    @Volatile private var context: Context? = null
    @Volatile private var playbackAllowed: Boolean? = null

    fun isPlaybackAllowed(): Boolean = playbackAllowed == true

    /**
     * Called by the host's native per-display UX restriction listener, before JS notification.
     *
     * Clearing UXR never reopens a pooled session or resumes media. While
     * playback is disallowed the `sessionKey` and `source` props are dropped,
     * not queued, so after `setPlaybackAllowed(true)` the host remounts the
     * GeckoView to bind a fresh session and load its source.
     */
    fun setPlaybackAllowed(allowed: Boolean) {
        playbackAllowed = allowed
        if (!allowed) {
            UiThreadUtil.runOnUiThread {
                closeAllSessions()
            }
            // Settle in-flight bridge evaluations with the restriction's code
            // now; the port disconnects that follow the close would report
            // EVAL_BRIDGE_UNAVAILABLE (or EVAL_TIMEOUT if a post raced them).
            try {
                GeckoRuntimeManager.getWebExtensionController()
                    ?.rejectAllPendingEvals(PLAYBACK_RESTRICTED, PLAYBACK_RESTRICTED_MESSAGE)
            } catch (e: Exception) {
                GeckoLog.w(TAG, "Failed to settle pending evaluations", e)
            }
        }
    }

    /**
     * Install the application Context. Never opens pooled sessions: a session
     * opens only when a view binds it on the UI thread, so one left unopened
     * can't be revived here with no view attached (this used to open them,
     * from whichever thread created the module).
     */
    @Synchronized
    fun init(appContext: Context) {
        context = appContext
        if (playbackAllowed == null) {
            playbackAllowed = !appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        }
    }

    /**
     * UI thread only: view prop setters bind sessions through here. Creates
     * the pooled session for [key] if needed and opens it.
     */
    @Synchronized
    fun getOrCreateSession(key: String, desktopMode: Boolean = true): GeckoSession {
        check(UiThreadUtil.isOnUiThread()) {
            "GeckoSessions are created and opened on the UI thread only"
        }
        val session = sessions.getOrPut(key) {
            GeckoSession(
                GeckoSessionSettings.Builder()
                    .userAgentMode(
                        if (desktopMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                        else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
                    )
                    .viewportMode(
                        if (desktopMode) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                        else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
                    )
                    .build()
            )
        }

        ensureSessionOpen(key, session)
        return session
    }

    /**
     * The pooled session for [key] if a view has opened it, else null. Never
     * creates or opens a session, so it is safe on any thread; ref methods
     * treat null as "session unavailable".
     */
    fun getOpenSession(key: String): GeckoSession? =
        if (openedSessionKeys.contains(key)) sessions[key] else null

    /**
     * UI thread only (GeckoSession.close asserts it). Registered modules drop
     * [key]'s snapshots first, whether or not a session was pooled for it.
     */
    fun releaseSession(key: String) {
        notifySessionsClosing(listOf(key))
        synchronized(this) {
            openedSessionKeys.remove(key)
            sessions.remove(key)?.let { session ->
                views.remove(session)
                releaseAudioFocus(session)
                session.close()
            }
        }
    }

    /**
     * UI thread only (GeckoSession.close asserts it). Shutdown and the UX
     * restriction close sessions here rather than through closeSession, so
     * registered modules drop every session's snapshots first, the same way.
     */
    fun closeAllSessions() {
        // Only the UI thread adds sessions, so none appears before the close.
        notifySessionsClosing(sessions.keys.toList())
        synchronized(this) {
            sessions.values.forEach { session ->
                try {
                    releaseAudioFocus(session)
                    session.close()
                } catch (e: Throwable) {
                    GeckoLog.w(TAG, "Failed to close session", e)
                }
            }
            sessions.clear()
            openedSessionKeys.clear()
            views.clear()
        }
    }

    // Modules that keep per-session state (snapshots) to drop when a session
    // closes. Weak: this process-wide pool must not retain a module from a
    // torn-down React instance.
    private val modules: MutableSet<GeckoViewModule> = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<GeckoViewModule, Boolean>())
    )

    internal fun registerModule(module: GeckoViewModule) {
        modules.add(module)
    }

    internal fun unregisterModuleForTest(module: GeckoViewModule) {
        modules.remove(module)
    }

    /**
     * Before the sessions close, so a save that hasn't registered its file by
     * then never will. Called outside this object's monitor: modules take
     * their own locks.
     */
    private fun notifySessionsClosing(keys: List<String>) {
        if (keys.isEmpty()) return
        val targets = synchronized(modules) { modules.toList() }
        for (module in targets) {
            for (key in keys) {
                try {
                    module.onSessionClosing(key)
                } catch (e: Throwable) {
                    GeckoLog.w(TAG, "Failed to clean up a closing session", e)
                }
            }
        }
    }

    /** A page playing when its session closes never reports onPause/onStop. */
    private fun releaseAudioFocus(session: GeckoSession) {
        try {
            (session.mediaSessionDelegate as? MediaDelegate)?.release()
        } catch (e: Exception) {
            GeckoLog.w(TAG, "Failed to release audio focus", e)
        }
    }

    private fun ensureSessionOpen(key: String, session: GeckoSession) {
        if (!isPlaybackAllowed()) return
        // GeckoView closes a session itself when its content process dies;
        // reopen it for the view binding it rather than hand out a dead page.
        if (openedSessionKeys.contains(key) && session.isOpen) {
            return
        }

        val runtime = context?.let { GeckoRuntimeManager.getRuntime(it) } ?: return

        try {
            session.open(runtime)
            openedSessionKeys.add(key)
        } catch (e: IllegalStateException) {
            // GeckoSession.open throws if already open; treat as success.
            openedSessionKeys.add(key)
        } catch (e: Exception) {
            GeckoLog.w(TAG, "Failed to open GeckoSession", e)
        }
        if (openedSessionKeys.contains(key)) {
            GeckoRuntimeManager.getWebExtensionController()?.bindInternalBridge(session)
        }
    }

    // WeakHashMap is not thread-safe, yet it's written from the UI thread
    // (registerView) and read from the Gecko message thread (getAllViews/getView
    // via WebExtensionController). Wrap it and hold the monitor while iterating.
    private val views: MutableMap<GeckoSession, java.lang.ref.WeakReference<org.mozilla.geckoview.GeckoView>> =
        java.util.Collections.synchronizedMap(java.util.WeakHashMap())

    fun registerView(session: GeckoSession, view: org.mozilla.geckoview.GeckoView) {
        views[session] = java.lang.ref.WeakReference(view)
    }

    /** A pooled session must not retain a dropped view or its Activity. */
    fun unregisterView(view: org.mozilla.geckoview.GeckoView) {
        synchronized(views) { views.entries.removeAll { it.value.get() == null || it.value.get() === view } }
    }

    fun getView(session: GeckoSession): org.mozilla.geckoview.GeckoView? {
        return views[session]?.get()
    }

    fun getAllViews(): List<org.mozilla.geckoview.GeckoView> {
        return synchronized(views) { views.values.mapNotNull { it.get() }.distinct() }
    }

    fun getAllSessions() = sessions.toMap()

    @Synchronized
    fun getSessionKey(session: GeckoSession): String? {
        return sessions.entries.firstOrNull { it.value === session }?.key
    }

    internal const val PLAYBACK_RESTRICTED = "PLAYBACK_RESTRICTED"
    internal const val PLAYBACK_RESTRICTED_MESSAGE = "Media is blocked by vehicle UX restrictions"

    private const val TAG = "GeckoSessionManager"
}
