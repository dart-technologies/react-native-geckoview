package com.reactnative.geckoview

import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [GeckoRuntimeManager.ExtensionPromptHandler] — the deny-by-default
 * `WebExtensionController.PromptDelegate` proxy handler.
 *
 * The handler dispatches purely on `method.name` + `method.returnType`, so we
 * drive it with [Method] objects pulled from a local interface that mirrors the
 * GeckoView signatures. We assert the security-critical decisions:
 *   - install: ALLOW only for allowlisted ids, DENY otherwise.
 *   - update: DENY for unlisted ids, DENY for permission-escalating updates,
 *     ALLOW for permission-identical updates of listed ids.
 *   - optional / debug prompts: always DENY.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExtensionPromptHandlerTest {

    private val allowedId = "spatial-navigation@geckoview.dev"
    private val handler = GeckoRuntimeManager.ExtensionPromptHandler(setOf(allowedId))
    private val proxy = Any()

    /** Mirrors the GeckoView PromptDelegate method shapes the handler dispatches on. */
    @Suppress("unused")
    private interface SignatureSource {
        fun onInstallPrompt(ext: WebExtension): GeckoResult<*>
        fun onUpdatePrompt(
            current: WebExtension,
            updated: WebExtension,
            newPerms: Array<String>,
            newOrigins: Array<String>,
        ): GeckoResult<*>
        fun onOptionalPrompt(ext: WebExtension, perms: Array<String>): GeckoResult<*>
        fun onExtensionDebugPrompt(ext: WebExtension): GeckoResult<*>
    }

    private fun method(name: String) =
        SignatureSource::class.java.declaredMethods.first { it.name == name }

    private fun extWithId(id: String): WebExtension {
        val ext: WebExtension = mockk(relaxed = true)
        TestHelpers.setField(ext, "id", id)
        return ext
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolve(result: Any?): Any? = (result as GeckoResult<Any>).poll(1000)

    // ========== onInstallPrompt ==========

    @Test
    fun `onInstallPrompt allows an allowlisted extension`() {
        val result = handler.invoke(proxy, method("onInstallPrompt"), arrayOf(extWithId(allowedId)))
        assertNotNull(result)
        val value = resolve(result)
        // GeckoResult return type -> PermissionPromptResponse(allow=true,...).
        assertEquals(true, (value as WebExtension.PermissionPromptResponse).isPermissionsGranted)
    }

    @Test
    fun `onInstallPrompt denies an unlisted extension`() {
        val result = handler.invoke(proxy, method("onInstallPrompt"), arrayOf(extWithId("evil@attacker.example")))
        val value = resolve(result)
        assertEquals(false, (value as WebExtension.PermissionPromptResponse).isPermissionsGranted)
    }

    @Test
    fun `onInstallPrompt denies when no extension argument is present`() {
        val result = handler.invoke(proxy, method("onInstallPrompt"), arrayOf<Any?>(null))
        val value = resolve(result)
        assertEquals(false, (value as WebExtension.PermissionPromptResponse).isPermissionsGranted)
    }

    // ========== onUpdatePrompt ==========

    @Test
    fun `onUpdatePrompt allows a permission-identical update of a listed extension`() {
        val current = extWithId(allowedId)
        val updated = extWithId(allowedId)
        // Empty new-permission / new-origin arrays -> no escalation.
        val result = handler.invoke(
            proxy,
            method("onUpdatePrompt"),
            arrayOf(current, updated, emptyArray<String>(), emptyArray<String>()),
        )
        assertEquals(AllowOrDeny.ALLOW, resolve(result))
    }

    @Test
    fun `onUpdatePrompt denies an update for an unlisted extension`() {
        val current = extWithId("evil@attacker.example")
        val updated = extWithId("evil@attacker.example")
        val result = handler.invoke(
            proxy,
            method("onUpdatePrompt"),
            arrayOf(current, updated, emptyArray<String>(), emptyArray<String>()),
        )
        assertEquals(AllowOrDeny.DENY, resolve(result))
    }

    @Test
    fun `onUpdatePrompt denies a permission-escalating update of a listed extension`() {
        val current = extWithId(allowedId)
        val updated = extWithId(allowedId)
        // A non-empty String array signals newly requested permissions/origins.
        val result = handler.invoke(
            proxy,
            method("onUpdatePrompt"),
            arrayOf(current, updated, arrayOf("clipboardWrite"), emptyArray<String>()),
        )
        assertEquals(AllowOrDeny.DENY, resolve(result))
    }

    // ========== optional / debug prompts ==========

    @Test
    fun `onOptionalPrompt always denies`() {
        val result = handler.invoke(
            proxy,
            method("onOptionalPrompt"),
            arrayOf(extWithId(allowedId), arrayOf("clipboardRead")),
        )
        assertEquals(AllowOrDeny.DENY, resolve(result))
    }

    @Test
    fun `onExtensionDebugPrompt always denies`() {
        val result = handler.invoke(proxy, method("onExtensionDebugPrompt"), arrayOf(extWithId(allowedId)))
        assertEquals(AllowOrDeny.DENY, resolve(result))
    }

    // ========== Object method passthroughs ==========

    @Test
    fun `toString returns a stable identifier`() {
        val result = handler.invoke(proxy, Any::class.java.getMethod("toString"), null)
        assertEquals("GeckoRuntimeManager.ExtensionPromptHandler", result)
    }

    @Test
    fun `equals is identity-based on the proxy`() {
        val equalsMethod = Any::class.java.getMethod("equals", Any::class.java)
        assertEquals(true, handler.invoke(proxy, equalsMethod, arrayOf(proxy)))
        assertEquals(false, handler.invoke(proxy, equalsMethod, arrayOf(Any())))
    }

    @Test
    fun `hashCode returns the proxy identity hash`() {
        val result = handler.invoke(proxy, Any::class.java.getMethod("hashCode"), null)
        assertEquals(System.identityHashCode(proxy), result)
    }

    @Test
    fun `a truly unhandled method name returns null`() {
        // Deny-by-default for unknown signatures: the handler returns null,
        // which the proxy surfaces as "no opinion".
        val getClass = Any::class.java.getMethod("getClass")
        assertNull(handler.invoke(proxy, getClass, null))
    }
}
