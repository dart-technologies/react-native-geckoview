package com.reactnative.geckoview

import android.content.Context
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap
import com.facebook.react.uimanager.ThemedReactContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.unmockkStatic
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

/**
 * Shared test utilities for GeckoView Android tests.
 *
 * The repo standardized on **MockK** in 2026-Q2. Test files should not pull
 * in `org.mockito.*` directly — use MockK's `mockk()`, `every`, `verify`,
 * `slot`, etc. Anything cross-cutting (e.g. `Arguments` static bootstrap or a
 * field-reflection helper for GeckoView's public-final-field classes) lives
 * in this file so individual tests stay short.
 */
object TestHelpers {

    object Fixtures {
        const val TEST_URL = "https://example.com"
        const val TEST_URL_MOZILLA = "https://mozilla.org"
        const val TEST_SESSION_KEY = "test-session"
        const val TEST_SESSION_KEY_PERSISTENT = "persistent-session"
        const val TEST_USER_AGENT = "Mozilla/5.0 (Test)"
    }

    /**
     * Stubs the static factories on `com.facebook.react.bridge.Arguments` so
     * delegates can call `Arguments.createMap()` in unit tests without a live
     * React context. Paired with [unmockArguments] in `@After`.
     */
    fun mockArguments() {
        mockkStatic(Arguments::class)
        every { Arguments.createMap() } answers { JavaOnlyMap() }
        every { Arguments.createArray() } answers { JavaOnlyArray() }
    }

    fun unmockArguments() {
        try {
            unmockkStatic(Arguments::class)
        } catch (_: Throwable) {
            // mockkStatic may have already been cleared by unmockkAll() in another test
        }
    }

    /**
     * Clears every MockK stub. Call in a global `@AfterClass` or per-test
     * `@After` when a test cross-contaminates otherwise.
     */
    fun clearAllMocks() {
        unmockkAll()
    }

    /** A MockK-backed GeckoSession that reports `isOpen = true`. */
    fun createMockGeckoSession(): GeckoSession {
        val session: GeckoSession = mockk(relaxed = true)
        every { session.isOpen } returns true
        return session
    }

    /** A relaxed MockK-backed GeckoRuntime. */
    fun createMockGeckoRuntime(): GeckoRuntime = mockk(relaxed = true)

    /** A relaxed MockK-backed ThemedReactContext with an application context. */
    fun createMockReactContext(): ThemedReactContext {
        val appContext: Context = mockk(relaxed = true)
        val context: ThemedReactContext = mockk(relaxed = true)
        every { context.applicationContext } returns appContext
        return context
    }

    /**
     * Sets a field on `target` via reflection. GeckoView exposes several
     * prompt/permission fields as public-final (e.g. `AlertPrompt.message`,
     * `ContentPermission.permission`), which MockK/Mockito cannot stub.
     * This walks the class hierarchy until it finds `fieldName`.
     */
    fun setField(target: Any, fieldName: String, value: Any?) {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            try {
                val field = cls.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        throw NoSuchFieldException("$fieldName on ${target.javaClass.name}")
    }
}
