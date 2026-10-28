package com.reactnative.geckoview

import android.content.Context
import android.content.pm.PackageManager
import com.facebook.react.bridge.UiThreadUtil
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

object GeckoSessionManager {
    private val sessions = java.util.concurrent.ConcurrentHashMap<String, GeckoSession>()
    private val openedSessionKeys = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    )
    @Volatile private var context: Context? = null
    @Volatile private var playbackAllowed: Boolean? = null

    fun isPlaybackAllowed(): Boolean = playbackAllowed == true

    /** Called by the host's native per-display UX restriction listener, before JS notification. */
    fun setPlaybackAllowed(allowed: Boolean) {
        playbackAllowed = allowed
        if (!allowed) UiThreadUtil.runOnUiThread {
            closeAllSessions()
        }
        // Clearing UXR never reopens a pooled session or resumes media.
    }

    /**
     * Install the application Context and backfill any sessions that were
     * created before it was known. `@Synchronized` guards the scan so a
     * concurrent `getOrCreateSession` can't mutate the map mid-iteration
     * (previously threw `ConcurrentModificationException` under first-view
     * races).
     */
    @Synchronized
    fun init(appContext: Context) {
        context = appContext
        if (playbackAllowed == null) {
            playbackAllowed = !appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        }
        sessions.forEach { (key, session) ->
            ensureSessionOpen(key, session)
        }
    }

    @Synchronized
    fun getOrCreateSession(key: String, desktopMode: Boolean = true): GeckoSession {
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

    @Synchronized
    fun releaseSession(key: String) {
        openedSessionKeys.remove(key)
        sessions.remove(key)?.let { session ->
            views.remove(session)
            session.close()
        }
    }

    @Synchronized
    fun closeAllSessions() {
        sessions.values.forEach { session ->
            try {
                session.close()
            } catch (e: Throwable) {
                GeckoLog.w(TAG, "Failed to close session", e)
            }
        }
        sessions.clear()
        openedSessionKeys.clear()
        views.clear()
    }

    private fun ensureSessionOpen(key: String, session: GeckoSession) {
        if (!isPlaybackAllowed()) return
        if (openedSessionKeys.contains(key)) {
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

    private const val TAG = "GeckoSessionManager"
}
