package com.reactnative.geckoview

import com.facebook.react.bridge.Arguments
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import java.util.UUID

class GeckoViewPromptDelegate(
    private val module: GeckoViewModule,
    private val viewId: Int,
    private val promptWrapperFactory: PromptWrapperFactory = DefaultPromptWrapperFactory()
) : GeckoSession.PromptDelegate {

    override fun onAlertPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AlertPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val message = prompt.message ?: ""
        if (message.startsWith("__EVAL_RESULT__:") || message.startsWith("__EVAL_ERROR__:")) {
            val parts = message.split(":", limit = 3)
            if (parts.size >= 3) {
                val type = parts[0]
                val uuid = parts[1]
                val payload = parts[2]
                
                if (type == "__EVAL_RESULT__") {
                    module.resolveEval(uuid, payload, null)
                } else {
                    module.resolveEval(uuid, null, payload)
                }
                
                // Use reflection to call confirm() since it's protected
                try {
                    val method = prompt.javaClass.getDeclaredMethod("confirm")
                    method.isAccessible = true
                    val response = method.invoke(prompt) as? GeckoSession.PromptDelegate.PromptResponse
                    return GeckoResult.fromValue(response)
                } catch (e: Exception) {
                    return null
                }
            }
        }
        return handlePrompt(prompt, "onGeckoAlert", message, null)
    }

    override fun onButtonPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.ButtonPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        return handlePrompt(prompt, "onGeckoConfirm", prompt.message, null)
    }

    override fun onTextPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.TextPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        return handlePrompt(prompt, "onGeckoPrompt", prompt.message, prompt.defaultValue)
    }

    override fun onPopupPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.PopupPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val wrapper = promptWrapperFactory.createPopupPromptWrapper(prompt)
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val promptId = UUID.randomUUID().toString()
        
        @Suppress("UNCHECKED_CAST")
        module.storePendingPrompt(promptId, wrapper, result as GeckoResult<Any>)

        val params = Arguments.createMap().apply {
            putString("promptId", promptId)
            putString("targetUri", wrapper.targetUri)
            putString("type", "popup")
        }
        module.sendViewEvent(viewId, "onGeckoPopup", params)
        
        return result
    }

    override fun onBeforeUnloadPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val wrapper = promptWrapperFactory.createBeforeUnloadPromptWrapper(prompt)
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val promptId = UUID.randomUUID().toString()
        
        @Suppress("UNCHECKED_CAST")
        module.storePendingPrompt(promptId, wrapper, result as GeckoResult<Any>)

        val params = Arguments.createMap().apply {
            putString("promptId", promptId)
            putString("type", "beforeUnload")
        }
        module.sendViewEvent(viewId, "onGeckoBeforeUnload", params)
        
        return result
    }

    override fun onAuthPrompt(
        session: GeckoSession,
        prompt: GeckoSession.PromptDelegate.AuthPrompt
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? {
        val wrapper = promptWrapperFactory.createAuthPromptWrapper(prompt)
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val promptId = UUID.randomUUID().toString()
        
        @Suppress("UNCHECKED_CAST")
        module.storePendingPrompt(promptId, wrapper, result as GeckoResult<Any>)

        val params = Arguments.createMap().apply {
            putString("promptId", promptId)
            putString("title", wrapper.title)
            putString("message", wrapper.message)
            putMap("authOptions", Arguments.makeNativeMap(wrapper.authOptions))
            putString("type", "auth")
        }
        module.sendViewEvent(viewId, "onGeckoAuth", params)
        
        return result
    }

    private fun handlePrompt(
        prompt: Any,
        eventName: String,
        message: String?,
        defaultValue: String?
    ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
        val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
        val promptId = UUID.randomUUID().toString()
        
        // Cast result to Any for storage in module
        @Suppress("UNCHECKED_CAST")
        module.storePendingPrompt(promptId, prompt, result as GeckoResult<Any>)

        val params = Arguments.createMap().apply {
            putString("promptId", promptId)
            putString("message", message)
            putString("defaultValue", defaultValue)
        }
        module.sendViewEvent(viewId, eventName, params)
        
        return result
    }
}
