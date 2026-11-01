package com.reactnative.geckoview

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoPreferenceController
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeckoRuntimeManagerTest {

    private lateinit var context: Context
    private lateinit var mockRuntime: GeckoRuntime

    @Before
    fun setUp() {
        GeckoSecurityConfig.resetForTests()
        context = mockk(relaxed = true)
        mockRuntime = mockk(relaxed = true)
        
        every { context.applicationContext } returns context
        
        // Mock static creation of GeckoRuntime
        mockkStatic(GeckoRuntime::class)
        every { GeckoRuntime.create(any(), any()) } returns mockRuntime

        // Runtime prefs go to Gecko through this static API; capture instead.
        mockkStatic(GeckoPreferenceController::class)
        every { GeckoPreferenceController.setGeckoPrefs(any()) } returns GeckoResult<Map<String, Boolean>>()
        
        // Ensure we start fresh
        GeckoRuntimeManager.shutdown()
    }

    @After
    fun tearDown() {
        GeckoRuntimeManager.shutdown()
        GeckoSecurityConfig.resetForTests()
        unmockkAll()
    }

    @Test
    fun `getRuntime creates new runtime when null`() {
        val runtime = GeckoRuntimeManager.getRuntime(context)
        
        assertNotNull(runtime)
        assertSame(mockRuntime, runtime)
        verify { GeckoRuntime.create(context, any()) }
    }

    @Test
    fun `getRuntime returns existing runtime`() {
        val runtime1 = GeckoRuntimeManager.getRuntime(context)
        val runtime2 = GeckoRuntimeManager.getRuntime(context)
        
        assertSame(runtime1, runtime2)
        // Should only be created once
        verify(exactly = 1) { GeckoRuntime.create(any(), any()) }
    }

    @Test
    fun `shutdown clears runtime`() {
        GeckoRuntimeManager.getRuntime(context)
        assertNotNull(GeckoRuntimeManager.getRuntime())
        
        GeckoRuntimeManager.shutdown()
        
        assertNull(GeckoRuntimeManager.getRuntime())
        verify { mockRuntime.shutdown() }
    }
    
    @Test
    fun `shutdown never holds the runtime monitor while it waits for the session monitor`() {
        // GeckoSessionManager.getOrCreateSession holds the session monitor and
        // calls getRuntime (runtime monitor). shutdown used to hold the runtime
        // monitor and call closeAllSessions (session monitor), so a view mounting
        // on the UI thread could deadlock against JS calling shutdown.
        val shutdownThread = Thread { GeckoRuntimeManager.shutdown() }
        // Stand-in for that UI-thread getRuntime: any call that needs the runtime monitor.
        val runtimeMonitorProbe = Thread {
            GeckoRuntimeManager.configureSecurity(GeckoSecurityConfig.current())
        }
        synchronized(GeckoSessionManager) {
            shutdownThread.start()
            // Wait until shutdown is parked on the session monitor this thread holds.
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10)
            while (shutdownThread.state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                Thread.yield()
            }
            assertEquals(Thread.State.BLOCKED, shutdownThread.state)

            runtimeMonitorProbe.start()
            runtimeMonitorProbe.join(5_000)
            assertFalse(
                "shutdown held the runtime monitor while waiting for the session monitor",
                runtimeMonitorProbe.isAlive,
            )
        }
        shutdownThread.join(5_000)
        assertFalse(shutdownThread.isAlive)
    }

    @Test
    fun `getWebExtensionController returns controller after init`() {
        GeckoRuntimeManager.getRuntime(context)
        assertNotNull(GeckoRuntimeManager.getWebExtensionController())
    }

    // ========== Runtime prefs (self-review M2) ==========
    //
    // These used to be `--setpref=` runtime arguments. Gecko has no such switch,
    // so autoplay and EME were never blocked. The tests below assert the prefs
    // GeckoPreferenceController receives when the runtime starts.

    private fun appliedPrefs(): Map<String, GeckoPreferenceController.SetGeckoPreference<*>> {
        val applied = slot<List<GeckoPreferenceController.SetGeckoPreference<*>>>()
        verify(exactly = 1) { GeckoPreferenceController.setGeckoPrefs(capture(applied)) }
        return applied.captured.associateBy { it.pref }
    }

    @Test
    fun `runtime start blocks autoplay and EME by default through Gecko prefs`() {
        GeckoRuntimeManager.getRuntime(context)

        val prefs = appliedPrefs()
        assertEquals("Autoplay must default to BLOCKED (5)", 5, prefs.getValue("media.autoplay.default").value)
        assertEquals(true, prefs.getValue("media.geckoview.autoplay.request").value)
        assertEquals("DRM/EME must be disabled by default", false, prefs.getValue("media.eme.enabled").value)
        prefs.values.forEach {
            assertEquals(GeckoPreferenceController.PREF_BRANCH_DEFAULT, it.branch)
        }
    }

    @Test
    fun `runtime start applies explicit DRM and autoplay opt-ins`() {
        GeckoRuntimeManager.configureSecurity(GeckoSecurityConfig.Options(allowDrm = true, allowAutoplay = true))

        GeckoRuntimeManager.getRuntime(context)

        val prefs = appliedPrefs()
        assertEquals(0, prefs.getValue("media.autoplay.default").value)
        assertEquals(false, prefs.getValue("media.geckoview.autoplay.request").value)
        assertEquals(true, prefs.getValue("media.eme.enabled").value)
    }

    @Test
    fun `runtime prefs are applied once per runtime`() {
        GeckoRuntimeManager.getRuntime(context)
        GeckoRuntimeManager.getRuntime(context)

        verify(exactly = 1) { GeckoPreferenceController.setGeckoPrefs(any()) }
    }

    @Test
    fun `runtime passes no command-line arguments`() {
        // Gecko takes neither pref nor sandbox switches on its command line.
        val settings = slot<GeckoRuntimeSettings>()
        every { GeckoRuntime.create(any(), capture(settings)) } returns mockRuntime

        GeckoRuntimeManager.getRuntime(context)

        assertEquals(0, settings.captured.arguments.size)
    }

    @Test
    fun `runtime prefs never weaken extension signature checks`() {
        for (options in listOf(
            GeckoSecurityConfig.Options(),
            GeckoSecurityConfig.Options(allowDrm = true, allowAutoplay = true),
        )) {
            val prefs = GeckoRuntimeManager.runtimePrefs(options)
            assertFalse(prefs.containsKey("xpinstall.signatures.required"))
            assertFalse(prefs.containsKey("extensions.install.requireSecureOrigin"))
            assertFalse(prefs.containsKey("media.autoplay.enabled"))
        }
    }

    @Test
    fun `a failed pref request does not break runtime start`() {
        every { GeckoPreferenceController.setGeckoPrefs(any()) } returns
            GeckoResult.fromException<Map<String, Boolean>>(IllegalStateException("prefs unavailable"))

        val runtime = GeckoRuntimeManager.getRuntime(context)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertSame(mockRuntime, runtime)
        assertNotNull(GeckoRuntimeManager.getWebExtensionController())
    }

    @Test
    fun `a throwing pref request does not break runtime start`() {
        every { GeckoPreferenceController.setGeckoPrefs(any()) } throws IllegalThreadStateException("no looper")

        assertSame(mockRuntime, GeckoRuntimeManager.getRuntime(context))
        assertNotNull(GeckoRuntimeManager.getWebExtensionController())
    }

    @Test
    fun `configureSecurity fails after runtime initialization`() {
        GeckoRuntimeManager.getRuntime(context)
        val configured = GeckoRuntimeManager.configureSecurity(GeckoSecurityConfig.Options(allowDrm = true))
        assert(!configured)
    }

    // Regression guard: end-to-end propagation from configureSecurity through
    // GeckoSecurityConfig to the static GeckoViewPermissionDelegate flags. The
    // ultrareview originally flagged this chain as broken; this test pins it.
    @Test
    fun `configureSecurity propagates allowDrm and allowAutoplay to permission delegate`() {
        val configured = GeckoRuntimeManager.configureSecurity(
            GeckoSecurityConfig.Options(allowDrm = true, allowAutoplay = true)
        )
        assert(configured) { "configureSecurity must succeed before runtime init" }
        assert(GeckoViewPermissionDelegate.autoGrantDrm)
        assert(GeckoViewPermissionDelegate.autoGrantAutoplay)
    }

    @Test
    fun `configureSecurity with both flags false leaves delegate flags off`() {
        val configured = GeckoRuntimeManager.configureSecurity(
            GeckoSecurityConfig.Options(allowDrm = false, allowAutoplay = false)
        )
        assert(configured)
        assert(!GeckoViewPermissionDelegate.autoGrantDrm)
        assert(!GeckoViewPermissionDelegate.autoGrantAutoplay)
    }

    // ========== ExtensionPromptHandler — deny-by-default prompt dispatch ==========
    //
    // The handler dispatches purely by method NAME and inspects the declared
    // return type to pick a response shape. We drive it directly via its
    // InvocationHandler.invoke, supplying java.lang.reflect.Method objects from
    // purpose-built test interfaces, so no live GeckoView PromptDelegate is
    // needed. AllowOrDeny / GeckoResult are real SDK types under Robolectric.

    private val allowedId = "spatial-navigation@geckoview.dev"

    // Return-type variants the handler branches on.
    private interface GeckoResultReturning {
        fun onInstallPrompt(ext: org.mozilla.geckoview.WebExtension): org.mozilla.geckoview.GeckoResult<*>
        fun onUpdatePrompt(
            ext: org.mozilla.geckoview.WebExtension,
            perms: Array<String>,
        ): org.mozilla.geckoview.GeckoResult<*>
        fun onOptionalPrompt(): org.mozilla.geckoview.GeckoResult<*>
        fun onExtensionDebugPrompt(): org.mozilla.geckoview.GeckoResult<*>
        fun someUnknownPrompt(): org.mozilla.geckoview.GeckoResult<*>
    }

    // A return type carrying a nested `InstallPromptResponse(boolean)` so the
    // findNestedClass + reflective-ctor branch of resolveInstallPromptResponse
    // is exercised (the GeckoView <=128 shape).
    class LegacyInstallReturn {
        @Suppress("unused")
        class InstallPromptResponse(val allow: Boolean)
        fun onInstallPrompt(ext: org.mozilla.geckoview.WebExtension): LegacyInstallReturn = this
    }

    private fun handler() =
        GeckoRuntimeManager.ExtensionPromptHandler(setOf(allowedId))

    private fun method(iface: Class<*>, name: String): java.lang.reflect.Method =
        iface.declaredMethods.first { it.name == name }

    private fun webExtension(id: String): org.mozilla.geckoview.WebExtension {
        val ext: org.mozilla.geckoview.WebExtension = mockk(relaxed = true)
        TestHelpers.setField(ext, "id", id)
        return ext
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveAllowOrDeny(result: Any?): org.mozilla.geckoview.AllowOrDeny {
        // GeckoResult.fromValue is already completed, so poll returns immediately.
        val gr = result as org.mozilla.geckoview.GeckoResult<Any?>
        return gr.poll(1000) as? org.mozilla.geckoview.AllowOrDeny
            ?: throw AssertionError("expected AllowOrDeny, got a different value")
    }

    @Test
    fun `ExtensionPromptHandler allows an install prompt for an allowlisted extension`() {
        val m = method(GeckoResultReturning::class.java, "onInstallPrompt")
        val result = handler().invoke(Any(), m, arrayOf(webExtension(allowedId)))
        // GeckoResult-returning install prompt yields a PermissionPromptResponse.
        @Suppress("UNCHECKED_CAST")
        val value = (result as org.mozilla.geckoview.GeckoResult<Any?>).poll(1000)
        assertNotNull(value)
        assertEquals(true, (value as org.mozilla.geckoview.WebExtension.PermissionPromptResponse).isPermissionsGranted)
    }

    @Test
    fun `ExtensionPromptHandler denies an install prompt for an unlisted extension`() {
        val m = method(GeckoResultReturning::class.java, "onInstallPrompt")
        val result = handler().invoke(Any(), m, arrayOf(webExtension("evil@attacker.example")))
        @Suppress("UNCHECKED_CAST")
        val value = (result as org.mozilla.geckoview.GeckoResult<Any?>).poll(1000)
        assertEquals(false, (value as org.mozilla.geckoview.WebExtension.PermissionPromptResponse).isPermissionsGranted)
    }

    @Test
    fun `ExtensionPromptHandler builds a legacy InstallPromptResponse via the nested-class path`() {
        val m = method(LegacyInstallReturn::class.java, "onInstallPrompt")
        // Non-GeckoResult return type -> findNestedClass locates
        // InstallPromptResponse(boolean) and constructs it reflectively.
        val result = handler().invoke(Any(), m, arrayOf(webExtension(allowedId)))
        @Suppress("UNCHECKED_CAST")
        val value = (result as org.mozilla.geckoview.GeckoResult<Any?>).poll(1000)
        assertNotNull(value)
        assertEquals(LegacyInstallReturn.InstallPromptResponse::class.java, value!!.javaClass)
        assertEquals(true, (value as LegacyInstallReturn.InstallPromptResponse).allow)
    }

    @Test
    fun `ExtensionPromptHandler update prompt allows an allowlisted extension with no new permissions`() {
        val m = method(GeckoResultReturning::class.java, "onUpdatePrompt")
        // Allowed id + an EMPTY new-permissions array -> ALLOW.
        val result = handler().invoke(Any(), m, arrayOf(webExtension(allowedId), emptyArray<String>()))
        assertEquals(org.mozilla.geckoview.AllowOrDeny.ALLOW, resolveAllowOrDeny(result))
    }

    @Test
    fun `ExtensionPromptHandler update prompt denies a permission-escalating update`() {
        val m = method(GeckoResultReturning::class.java, "onUpdatePrompt")
        // Allowed id but the update requests NEW permissions -> DENY.
        val result = handler().invoke(
            Any(), m, arrayOf(webExtension(allowedId), arrayOf("tabs", "<all_urls>")),
        )
        assertEquals(org.mozilla.geckoview.AllowOrDeny.DENY, resolveAllowOrDeny(result))
    }

    @Test
    fun `ExtensionPromptHandler update prompt denies an unlisted extension`() {
        val m = method(GeckoResultReturning::class.java, "onUpdatePrompt")
        val result = handler().invoke(
            Any(), m, arrayOf(webExtension("evil@attacker.example"), emptyArray<String>()),
        )
        assertEquals(org.mozilla.geckoview.AllowOrDeny.DENY, resolveAllowOrDeny(result))
    }

    @Test
    fun `ExtensionPromptHandler denies optional and debug prompts`() {
        val optional = method(GeckoResultReturning::class.java, "onOptionalPrompt")
        val debug = method(GeckoResultReturning::class.java, "onExtensionDebugPrompt")
        assertEquals(org.mozilla.geckoview.AllowOrDeny.DENY, resolveAllowOrDeny(handler().invoke(Any(), optional, null)))
        assertEquals(org.mozilla.geckoview.AllowOrDeny.DENY, resolveAllowOrDeny(handler().invoke(Any(), debug, null)))
    }

    @Test
    fun `ExtensionPromptHandler implements Object methods and denies unknown prompts`() {
        val h = handler()
        val proxy = Any()

        val toString = Any::class.java.getDeclaredMethod("toString")
        assertEquals("GeckoRuntimeManager.ExtensionPromptHandler", h.invoke(proxy, toString, null))

        val hashCode = Any::class.java.getDeclaredMethod("hashCode")
        assertEquals(System.identityHashCode(proxy), h.invoke(proxy, hashCode, null))

        val equals = Any::class.java.getDeclaredMethod("equals", Any::class.java)
        assertEquals(true, h.invoke(proxy, equals, arrayOf(proxy)))
        assertEquals(false, h.invoke(proxy, equals, arrayOf(Any())))

        // An unrecognized prompt method falls through to `else -> null`.
        val unknown = method(GeckoResultReturning::class.java, "someUnknownPrompt")
        assertNull(h.invoke(proxy, unknown, null))
    }
}
