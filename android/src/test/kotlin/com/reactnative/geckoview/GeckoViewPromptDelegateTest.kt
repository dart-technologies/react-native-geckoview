package com.reactnative.geckoview

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mozilla.geckoview.GeckoSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeckoViewPromptDelegateTest {

    @Mock private lateinit var module: GeckoViewModule
    @Mock private lateinit var session: GeckoSession
    @Mock private lateinit var factory: PromptWrapperFactory
    
    @Mock private lateinit var popupPrompt: GeckoSession.PromptDelegate.PopupPrompt
    @Mock private lateinit var popupWrapper: PopupPromptWrapper
    
    @Mock private lateinit var beforeUnloadPrompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt
    @Mock private lateinit var beforeUnloadWrapper: BeforeUnloadPromptWrapper
    
    @Mock private lateinit var authPrompt: GeckoSession.PromptDelegate.AuthPrompt
    @Mock private lateinit var authWrapper: AuthPromptWrapper

    private lateinit var promptDelegate: GeckoViewPromptDelegate
    private val testViewId = 202

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        promptDelegate = GeckoViewPromptDelegate(module, testViewId, factory)
        
        whenever(factory.createPopupPromptWrapper(popupPrompt)).thenReturn(popupWrapper)
        whenever(factory.createBeforeUnloadPromptWrapper(beforeUnloadPrompt)).thenReturn(beforeUnloadWrapper)
        whenever(factory.createAuthPromptWrapper(authPrompt)).thenReturn(authWrapper)
        
        TestHelpers.mockArguments()
    }

    @org.junit.After
    fun tearDown() {
        TestHelpers.unmockArguments()
    }

    @Test
    fun `onPopupPrompt emits onGeckoPopup event`() {
        promptDelegate.onPopupPrompt(session, popupPrompt)
        
        verify(module).sendViewEvent(eq(testViewId), eq("onGeckoPopup"), any())
    }

    @Test
    fun `onBeforeUnloadPrompt emits onGeckoBeforeUnload event`() {
        promptDelegate.onBeforeUnloadPrompt(session, beforeUnloadPrompt)
        
        verify(module).sendViewEvent(eq(testViewId), eq("onGeckoBeforeUnload"), any())
    }

    @Test
    fun `onAuthPrompt emits onGeckoAuth event`() {
        val options = mapOf<String, Any?>("username" to "user")
        whenever(authWrapper.authOptions).thenReturn(options)
        
        promptDelegate.onAuthPrompt(session, authPrompt)
        
        verify(module).sendViewEvent(eq(testViewId), eq("onGeckoAuth"), any())
    }
}
