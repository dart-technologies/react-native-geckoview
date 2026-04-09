package com.reactnative.geckoview

import android.content.Context
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.JavaOnlyArray
import com.facebook.react.bridge.JavaOnlyMap
import com.facebook.react.bridge.WritableArray
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.ThemedReactContext
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

/**
 * Shared test utilities for GeckoView Android tests
 * Provides common mocks, fixtures, and helper functions
 */
object TestHelpers {
    private var argumentsMock: MockedStatic<Arguments>? = null

    // Common test constants
    object Fixtures {
        const val TEST_URL = "https://example.com"
        const val TEST_URL_MOZILLA = "https://mozilla.org"
        const val TEST_SESSION_KEY = "test-session"
        const val TEST_SESSION_KEY_PERSISTENT = "persistent-session"
        const val TEST_USER_AGENT = "Mozilla/5.0 (Test)"
    }

    /**
     * Mocks React Native's Arguments utility class
     * Call in @Before, cleanup with unmockArguments() in @After
     */
    fun mockArguments() {
        if (argumentsMock != null) return
        
        argumentsMock = Mockito.mockStatic(Arguments::class.java)
        argumentsMock?.`when`<WritableMap> { Arguments.createMap() }?.thenAnswer { JavaOnlyMap() }
        argumentsMock?.`when`<WritableArray> { Arguments.createArray() }?.thenAnswer { JavaOnlyArray() }
    }

    /**
     * Unmocks Arguments utility
     * Call in @After to cleanup
     */
    fun unmockArguments() {
        argumentsMock?.close()
        argumentsMock = null
    }

    /**
     * Creates a mock GeckoSession with common default behavior
     */
    fun createMockGeckoSession(): GeckoSession {
        val session = mock<GeckoSession>()
        whenever(session.isOpen).thenReturn(true)
        return session
    }

    /**
     * Creates a mock GeckoRuntime with common default behavior
     */
    fun createMockGeckoRuntime(): GeckoRuntime {
        return mock<GeckoRuntime>()
    }

    /**
     * Creates a mock ThemedReactContext with common default configuration
     */
    fun createMockReactContext(): ThemedReactContext {
        val context = mock<ThemedReactContext>()
        val appContext = mock<Context>()
        whenever(context.applicationContext).thenReturn(appContext)
        return context
    }
}

