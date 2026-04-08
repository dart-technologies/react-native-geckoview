package com.reactnative.geckoview

import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoSession

/**
 * Wrapper interfaces for GeckoView prompt types to enable testability.
 * GeckoView's prompt classes are final with package-private constructors,
 * making them difficult to mock in tests.
 */

interface PopupPromptWrapper {
    val targetUri: String?
    fun respond(allow: Boolean): GeckoSession.PromptDelegate.PromptResponse
}

class RealPopupPromptWrapper(
    private val popupPrompt: GeckoSession.PromptDelegate.PopupPrompt
) : PopupPromptWrapper {
    override val targetUri: String?
        get() = popupPrompt.targetUri

    override fun respond(allow: Boolean): GeckoSession.PromptDelegate.PromptResponse {
        val response = if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY
        return popupPrompt.confirm(response)
    }
}

interface BeforeUnloadPromptWrapper {
    fun confirm(allowNavigation: Boolean): GeckoSession.PromptDelegate.PromptResponse
}

class RealBeforeUnloadPromptWrapper(
    private val beforeUnloadPrompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt
) : BeforeUnloadPromptWrapper {
    override fun confirm(allowNavigation: Boolean): GeckoSession.PromptDelegate.PromptResponse {
        val response = if (allowNavigation) AllowOrDeny.ALLOW else AllowOrDeny.DENY
        return beforeUnloadPrompt.confirm(response)
    }
}

interface AuthPromptWrapper {
    val title: String?
    val message: String?
    val authOptions: Map<String, Any?>
    fun confirm(username: String, password: String): GeckoSession.PromptDelegate.PromptResponse
    fun dismiss(): GeckoSession.PromptDelegate.PromptResponse
}

class RealAuthPromptWrapper(
    private val authPrompt: GeckoSession.PromptDelegate.AuthPrompt
) : AuthPromptWrapper {
    override val title: String?
        get() = authPrompt.title
    
    override val message: String?
        get() = authPrompt.message
    
    override val authOptions: Map<String, Any?>
        get() = mapOf(
            "uri" to authPrompt.authOptions.uri,
            "level" to authPrompt.authOptions.level,
            "username" to authPrompt.authOptions.username,
            "flags" to authPrompt.authOptions.flags
        )

    override fun confirm(username: String, password: String): GeckoSession.PromptDelegate.PromptResponse {
        return authPrompt.confirm(username, password)
    }

    override fun dismiss(): GeckoSession.PromptDelegate.PromptResponse {
        return authPrompt.dismiss()
    }
}

interface PromptWrapperFactory {
    fun createPopupPromptWrapper(prompt: GeckoSession.PromptDelegate.PopupPrompt): PopupPromptWrapper
    fun createBeforeUnloadPromptWrapper(prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt): BeforeUnloadPromptWrapper
    fun createAuthPromptWrapper(prompt: GeckoSession.PromptDelegate.AuthPrompt): AuthPromptWrapper
}

class DefaultPromptWrapperFactory : PromptWrapperFactory {
    override fun createPopupPromptWrapper(prompt: GeckoSession.PromptDelegate.PopupPrompt): PopupPromptWrapper {
        return RealPopupPromptWrapper(prompt)
    }

    override fun createBeforeUnloadPromptWrapper(prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt): BeforeUnloadPromptWrapper {
        return RealBeforeUnloadPromptWrapper(prompt)
    }

    override fun createAuthPromptWrapper(prompt: GeckoSession.PromptDelegate.AuthPrompt): AuthPromptWrapper {
        return RealAuthPromptWrapper(prompt)
    }
}
