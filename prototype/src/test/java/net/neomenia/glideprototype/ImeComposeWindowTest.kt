package net.neomenia.glideprototype

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImeComposeWindowTest {
    @Test fun serviceRegistersOwnersAboveTheWindowContentRoot() {
        val controller = Robolectric.buildService(GlideInputMethodService::class.java).create()
        val service = controller.get()
        val decor = service.window.window!!.decorView
        try {
            val owner = decor.findViewTreeLifecycleOwner()
            assertNotNull("WindowRecomposer must find an owner above the IME input view", owner)
            assertSame(owner, decor.findViewTreeSavedStateRegistryOwner())
            assertEquals(Lifecycle.State.CREATED, owner!!.lifecycle.currentState)
            controller.destroy()
            assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        } finally {
            if (decor.findViewTreeLifecycleOwner()?.lifecycle?.currentState != Lifecycle.State.DESTROYED) controller.destroy()
        }
    }
}
