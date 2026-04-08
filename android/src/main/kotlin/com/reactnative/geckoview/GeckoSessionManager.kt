package com.reactnative.geckoview

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings

object GeckoSessionManager {
    private val sessions = mutableMapOf<String, GeckoSession>()
    private val openedSessionKeys = mutableSetOf<String>()
    private var context: Context? = null

    fun init(appContext: Context) {
        context = appContext

        // If any sessions were created before we had a Context (e.g. via module calls),
        // opportunistically open them now.
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
        sessions.remove(key)?.close()
    }

    private fun ensureSessionOpen(key: String, session: GeckoSession) {
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
            Log.w(TAG, "Failed to open GeckoSession for key=$key", e)
        }
    }

    private val views = java.util.WeakHashMap<GeckoSession, org.mozilla.geckoview.GeckoView>()

    fun registerView(session: GeckoSession, view: org.mozilla.geckoview.GeckoView) {
        views[session] = view
    }

    fun getView(session: GeckoSession): org.mozilla.geckoview.GeckoView? {
        return views[session]
    }

    fun getAllViews(): List<org.mozilla.geckoview.GeckoView> {
        return views.values.toList()
    }

    fun getAllSessions() = sessions.toMap()

    private const val TAG = "GeckoSessionManager"
}
